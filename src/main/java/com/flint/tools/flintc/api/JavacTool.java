/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.flint.tools.flintc.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.*;

import com.flint.tools.flintc.file.BaseFileManager;
import com.flint.tools.flintc.file.CacheFSInfo;
import com.flint.tools.flintc.file.JavacFileManager;
import com.flint.tools.flintc.jvm.Target;
import com.flint.tools.flintc.main.Arguments;
import com.flint.tools.flintc.main.Option;
import com.flint.source.util.JavacTask;
import com.flint.tools.flintc.util.ClientCodeException;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.PropagatedException;

/**
 * TODO: describe com.flint.tools.flintc.api.Tool
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah\u00e9
 */
public final class JavacTool implements JavaCompiler {
    /**
     * Constructor used by service provider mechanism.  The recommended way to
     * obtain an instance of this class is by using {@link #create} or the
     * service provider mechanism.
     * @see JavaCompiler
     * @see ToolProvider
     * @see #create
     */
    @Deprecated
    public JavacTool() {}

    // @Override // can't add @Override until bootstrap JDK provides Tool.name()
    @DefinedBy(Api.COMPILER)
    public String name() {
        return "javac";
    }

    /**
     * Static factory method for creating new instances of this tool.
     * @return new instance of this tool
     */
    public static JavacTool create() {
        return new JavacTool();
    }

    @Override @DefinedBy(Api.COMPILER)
    public JavacFileManager getStandardFileManager(
        DiagnosticListener<? super JavaFileObject> diagnosticListener,
        Locale locale,
        Charset charset) {
        Context context = new Context();
        context.put(Locale.class, locale);
        if (diagnosticListener != null)
            context.put(DiagnosticListener.class, diagnosticListener);
        PrintWriter pw = (charset == null)
                ? new PrintWriter(System.err, true)
                : new PrintWriter(new OutputStreamWriter(System.err, charset), true);
        context.put(Log.errKey, pw);
        CacheFSInfo.preRegister(context);
        return new JavacFileManager(context, true, charset);
    }

    @Override @DefinedBy(Api.COMPILER)
    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits) {
        Context context = new Context();
        return getTask(out, fileManager, diagnosticListener,
                options, classes, compilationUnits,
                context);
    }

    /* Internal version of getTask, allowing context to be provided. */
    public JavacTask getTask(Writer out,
                             JavaFileManager fileManager,
                             DiagnosticListener<? super JavaFileObject> diagnosticListener,
                             Iterable<String> options,
                             Iterable<String> classes,
                             Iterable<? extends JavaFileObject> compilationUnits,
                             Context context)
    {
        try {
            ClientCodeWrapper ccw = ClientCodeWrapper.instance(context);

            if (options != null) {
                for (String option : options)
                    Objects.requireNonNull(option);
            }

            if (classes != null) {
                for (String cls : classes) {
                    int sep = cls.indexOf('/'); // implicit null check
                    if (sep > 0) {
                        String mod = cls.substring(0, sep);
                        if (!SourceVersion.isName(mod))
                            throw new IllegalArgumentException("Not a valid module name: " + mod);
                        cls = cls.substring(sep + 1);
                    }
                    if (!SourceVersion.isName(cls))
                        throw new IllegalArgumentException("Not a valid class name: " + cls);
                }
            }

            if (compilationUnits != null) {
                compilationUnits = ccw.wrapJavaFileObjects(compilationUnits); // implicit null check
                for (JavaFileObject cu : compilationUnits) {
                    if (cu.getKind() != JavaFileObject.Kind.SOURCE) {
                        String kindMsg = "Compilation unit is not of SOURCE kind: "
                                + "\"" + cu.getName() + "\"";
                        throw new IllegalArgumentException(kindMsg);
                    }
                }
            }

            if (diagnosticListener != null)
                context.put(DiagnosticListener.class, ccw.wrap(diagnosticListener));

            if (out == null)
                context.put(Log.errKey, new PrintWriter(System.err, true));
            else
                context.put(Log.errKey, new PrintWriter(out, true));

            if (fileManager == null) {
                fileManager = getStandardFileManager(diagnosticListener, null, null);
                if (fileManager instanceof BaseFileManager) {
                    ((BaseFileManager) fileManager).autoClose = true;
                }
            }
            fileManager = ccw.wrap(fileManager);

            context.put(JavaFileManager.class, fileManager);

            Arguments args = Arguments.instance(context);
            args.init("javac", options, classes, compilationUnits);

            // init multi-release jar handling
            if (fileManager.isSupportedOption(Option.MULTIRELEASE.primaryName) == 1) {
                Target target = Target.instance(context);
                JCList<String> list = JCList.of(target.multiReleaseValue());
                fileManager.handleOption(Option.MULTIRELEASE.primaryName, list.iterator());
            }

            return new JavacTaskImpl(context);
        } catch (PropagatedException ex) {
            throw ex.getCause();
        } catch (ClientCodeException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    @Override @DefinedBy(Api.COMPILER)
    public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
        if (err == null)
            err = System.err;
        for (String argument : arguments)
            Objects.requireNonNull(argument);
        return com.flint.tools.flintc.Main.compile(arguments, new PrintWriter(err, true));
    }

    @Override @DefinedBy(Api.COMPILER)
    public Set<SourceVersion> getSourceVersions() {
        return Collections.unmodifiableSet(EnumSet.range(SourceVersion.RELEASE_3,
                                                         SourceVersion.latest()));
    }

    @Override @DefinedBy(Api.COMPILER)
    public int isSupportedOption(String option) {
        Set<Option> recognizedOptions = Option.getJavacToolOptions();
        for (Option o : recognizedOptions) {
            if (o.matches(option)) {
                return o.hasArg() ? 1 : 0;
            }
        }
        return -1;
    }

}
