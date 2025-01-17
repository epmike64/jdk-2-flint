/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.main;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import javax.tools.JavaFileManager;

import com.flint.tools.flintc.api.BasicJavacTask;
import com.flint.tools.flintc.file.BaseFileManager;
import com.flint.tools.flintc.file.CacheFSInfo;
import com.flint.tools.flintc.file.JavacFileManager;
import com.flint.tools.flintc.jvm.Target;
import com.flint.tools.flintc.platform.PlatformDescription;
import com.flint.tools.flintc.processing.AnnotationProcessingError;
import com.flint.tools.flintc.main.CommandLine.UnmatchedQuote;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.Log.PrefixKind;
import com.flint.tools.flintc.util.Log.WriterKind;

/** This class provides a command line interface to the javac compiler.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Main {

    /** The name of the compiler, for use in diagnostics.
     */
    String ownName;

    /** The writer to use for normal output.
     */
    PrintWriter stdOut;

    /** The writer to use for diagnostic output.
     */
    PrintWriter stdErr;

    /** The log to use for diagnostic output.
     */
    public Log log;

    /**
     * If true, certain errors will cause an exception, such as command line
     * arg errors, or exceptions in user provided code.
     */
    boolean apiMode;

    private static final String ENV_OPT_NAME = "JDK_JAVAC_OPTIONS";

    /** Result codes.
     */
    public enum Result {
        OK(0),        // Compilation completed with no errors.
        ERROR(1),     // Completed but reported errors.
        CMDERR(2),    // Bad command-line arguments
        SYSERR(3),    // System error or resource exhaustion.
        ABNORMAL(4);  // Compiler terminated abnormally

        Result(int exitCode) {
            this.exitCode = exitCode;
        }

        public boolean isOK() {
            return (exitCode == 0);
        }

        public final int exitCode;
    }

    /**
     * Construct a compiler instance.
     * @param name the name of this tool
     */
    public Main(String name) {
        this.ownName = name;
    }

    /**
     * Construct a compiler instance.
     * @param name the name of this tool
     * @param out a stream to which to write messages
     */
    public Main(String name, PrintWriter out) {
        this.ownName = name;
        this.stdOut = this.stdErr = out;
    }

    /**
     * Construct a compiler instance.
     * @param name the name of this tool
     * @param out a stream to which to write expected output
     * @param err a stream to which to write diagnostic output
     */
    public Main(String name, PrintWriter out, PrintWriter err) {
        this.ownName = name;
        this.stdOut = out;
        this.stdErr = err;
    }

    /** Report a usage error.
     */
    void error(String key, Object... args) {
        if (apiMode) {
            String msg = log.localize(PrefixKind.JAVAC, key, args);
            throw new PropagatedException(new IllegalStateException(msg));
        }
        warning(key, args);
        log.printLines(PrefixKind.JAVAC, "msg.usage", ownName);
    }

    /** Report a warning.
     */
    void warning(String key, Object... args) {
        log.printRawLines(ownName + ": " + log.localize(PrefixKind.JAVAC, key, args));
    }


    /**
     * Programmatic interface for main function.
     * @param args  the command line parameters
     * @return the result of the compilation
     */
    public Result compile(String[] args) {
        Context context = new Context();
        JavacFileManager.preRegister(context); // can't create it until Log has been set up
        Result result = compile(args, context);
        if (fileManager instanceof JavacFileManager) {
            try {
                // A fresh context was created above, so jfm must be a JavacFileManager
                ((JavacFileManager)fileManager).close();
            } catch (IOException ex) {
                bugMessage(ex);
            }
        }
        return result;
    }

    /**
     * Internal version of compile, allowing context to be provided.
     * Note that the context needs to have a file manager set up.
     * @param argv  the command line parameters
     * @param context the context
     * @return the result of the compilation
     */
    public Result compile(String[] argv, Context context) {
        if (stdOut != null) {
            context.put(Log.outKey, stdOut);
        }

        if (stdErr != null) {
            context.put(Log.errKey, stdErr);
        }

        log = Log.instance(context);

        if (argv.length == 0) {
            OptionHelper h = new OptionHelper.GrumpyHelper(log) {
                @Override
                public String getOwnName() { return ownName; }
                @Override
                public void put(String name, String value) { }
            };
            try {
                Option.HELP.process(h, "-help");
            } catch (Option.InvalidValueException ignore) {
            }
            return Result.CMDERR;
        }

        // prefix argv with contents of environment variable and expand @-files
        try {
            argv = CommandLine.parse(ENV_OPT_NAME, argv);
        } catch (UnmatchedQuote ex) {
            error("err.unmatched.quote", ex.variableName);
            return Result.CMDERR;
        } catch (FileNotFoundException | NoSuchFileException e) {
            warning("err.file.not.found", e.getMessage());
            return Result.SYSERR;
        } catch (IOException ex) {
            log.printLines(PrefixKind.JAVAC, "msg.io");
            ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
            return Result.SYSERR;
        }

        Arguments args = Arguments.instance(context);
        args.init(ownName, argv);

        if (log.nerrors > 0)
            return Result.CMDERR;

        Options options = Options.instance(context);

        // init Log
        boolean forceStdOut = options.isSet("stdout");
        if (forceStdOut) {
            log.flush();
            log.setWriters(new PrintWriter(System.out, true));
        }

        // init CacheFSInfo
        // allow System property in following line as a Mustang legacy
        boolean batchMode = (options.isUnset("nonBatchMode")
                    && System.getProperty("nonBatchMode") == null);
        if (batchMode)
            CacheFSInfo.preRegister(context);

        boolean ok = true;

        // init file manager
        fileManager = context.get(JavaFileManager.class);
        if (fileManager instanceof BaseFileManager) {
            ((BaseFileManager) fileManager).setContext(context); // reinit with options
            ok &= ((BaseFileManager) fileManager).handleOptions(args.getDeferredFileManagerOptions());
        }

        // handle this here so it works even if no other options given
        String showClass = options.get("showClass");
        if (showClass != null) {
            if (showClass.equals("showClass")) // no value given for option
                showClass = "com.flint.tools.flintc.Main";
            showClass(showClass);
        }

        ok &= args.validate();
        if (!ok || log.nerrors > 0)
            return Result.CMDERR;

        if (args.isEmpty())
            return Result.OK;

        // init Dependencies
        if (options.isSet("debug.completionDeps")) {
            Dependencies.GraphDependencies.preRegister(context);
        }

        // init plugins
        Set<JCList<String>> pluginOpts = args.getPluginOpts();
        if (!pluginOpts.isEmpty() || context.get(PlatformDescription.class) != null) {
            BasicJavacTask t = (BasicJavacTask) BasicJavacTask.instance(context);
            t.initPlugins(pluginOpts);
        }

        // init multi-release jar handling
        if (fileManager.isSupportedOption(Option.MULTIRELEASE.primaryName) == 1) {
            Target target = Target.instance(context);
            JCList<String> list = JCList.of(target.multiReleaseValue());
            fileManager.handleOption(Option.MULTIRELEASE.primaryName, list.iterator());
        }

        // init JavaCompiler
        JavaCompiler comp = JavaCompiler.instance(context);

        // init doclint
        JCList<String> docLintOpts = args.getDocLintOpts();
        if (!docLintOpts.isEmpty()) {
            BasicJavacTask t = (BasicJavacTask) BasicJavacTask.instance(context);
            t.initDocLint(docLintOpts);
        }

        if (options.get(Option.XSTDOUT) != null) {
            // Stdout reassigned - ask compiler to close it when it is done
            comp.closeables = comp.closeables.prepend(log.getWriter(WriterKind.NOTICE));
        }

        try {
            comp.compile(args.getFileObjects(), args.getClassNames(), null, JCList.nil());

            if (log.expectDiagKeys != null) {
                if (log.expectDiagKeys.isEmpty()) {
                    log.printRawLines("all expected diagnostics found");
                    return Result.OK;
                } else {
                    log.printRawLines("expected diagnostic keys not found: " + log.expectDiagKeys);
                    return Result.ERROR;
                }
            }

            return (comp.errorCount() == 0) ? Result.OK : Result.ERROR;

        } catch (OutOfMemoryError | StackOverflowError ex) {
            resourceMessage(ex);
            return Result.SYSERR;
        } catch (FatalError ex) {
            feMessage(ex, options);
            return Result.SYSERR;
        } catch (AnnotationProcessingError ex) {
            apMessage(ex);
            return Result.SYSERR;
        } catch (PropagatedException ex) {
            // TODO: what about errors from plugins?   should not simply rethrow the error here
            throw ex.getCause();
        } catch (Throwable ex) {
            // Nasty.  If we've already reported an error, compensate
            // for buggy compiler error recovery by swallowing thrown
            // exceptions.
            if (comp == null || comp.errorCount() == 0 || options.isSet("dev"))
                bugMessage(ex);
            return Result.ABNORMAL;
        } finally {
            if (comp != null) {
                try {
                    comp.close();
                } catch (ClientCodeException ex) {
                    throw new RuntimeException(ex.getCause());
                }
            }
        }
    }

    /** Print a message reporting an internal error.
     */
    void bugMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.bug", JavaCompiler.version());
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting a fatal error.
     */
    void feMessage(Throwable ex, Options options) {
        log.printRawLines(ex.getMessage());
        if (ex.getCause() != null && options.isSet("dev")) {
            ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
        }
    }

    /** Print a message reporting an input/output error.
     */
    void ioMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.io");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an out-of-resources error.
     */
    void resourceMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.resource");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an uncaught exception from an
     * annotation processor.
     */
    void apMessage(AnnotationProcessingError ex) {
        log.printLines(PrefixKind.JAVAC, "msg.proc.annotation.uncaught.exception");
        ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an uncaught exception from an
     * annotation processor.
     */
    void pluginMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.plugin.uncaught.exception");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Display the location and checksum of a class. */
    void showClass(String className) {
        PrintWriter pw = log.getWriter(WriterKind.NOTICE);
        pw.println("javac: show class: " + className);

        URL url = getClass().getResource('/' + className.replace('.', '/') + ".class");
        if (url != null) {
            pw.println("  " + url);
        }

        try (InputStream in = getClass().getResourceAsStream('/' + className.replace('.', '/') + ".class")) {
            final String algorithm = "MD5";
            byte[] digest;
            MessageDigest md = MessageDigest.getInstance(algorithm);
            try (DigestInputStream din = new DigestInputStream(in, md)) {
                byte[] buf = new byte[8192];
                int n;
                do { n = din.read(buf); } while (n > 0);
                digest = md.digest();
            }
            StringBuilder sb = new StringBuilder();
            for (byte b: digest)
                sb.append(String.format("%02x", b));
            pw.println("  " + algorithm + " checksum: " + sb);
        } catch (NoSuchAlgorithmException | IOException e) {
            pw.println("  cannot compute digest: " + e);
        }
    }

    // TODO: update this to JavacFileManager
    private JavaFileManager fileManager;

    /* ************************************************************************
     * Internationalization
     *************************************************************************/

    public static final String javacBundleName =
        "com.flint.tools.flintc.resources.javac";
}
