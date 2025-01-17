/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import com.flint.tools.flintc.main.JavaCompiler;
import com.flint.tools.flintc.model.JavacElements;
import com.flint.tools.flintc.model.JavacTypes;
import com.flint.tools.flintc.platform.PlatformDescription;
import com.flint.tools.flintc.processing.JavacProcessingEnvironment;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.source.tree.CompilationUnitTree;
import com.flint.source.tree.Tree;
import com.flint.source.util.JavacTask;
import com.flint.source.util.Plugin;
import com.flint.source.util.TaskListener;
//import com.flint.tools.doclint.DocLint;
import com.flint.tools.flintc.platform.PlatformDescription.PluginInfo;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.PropagatedException;

/**
 * Provides basic functionality for implementations of JavacTask.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class BasicJavacTask extends JavacTask {
    protected Context context;
    private TaskListener taskListener;

    public static JavacTask instance(Context context) {
        JavacTask instance = context.get(JavacTask.class);
        if (instance == null)
            instance = new BasicJavacTask(context, true);
        return instance;
    }

    public BasicJavacTask(Context c, boolean register) {
        context = c;
        if (register)
            context.put(JavacTask.class, this);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends CompilationUnitTree> parse() {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends Element> analyze() {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends JavaFileObject> generate() {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public void setTaskListener(TaskListener tl) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        if (taskListener != null)
            mtl.remove(taskListener);
        if (tl != null)
            mtl.add(tl);
        taskListener = tl;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public void addTaskListener(TaskListener taskListener) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.add(taskListener);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public void removeTaskListener(TaskListener taskListener) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.remove(taskListener);
    }

    public Collection<TaskListener> getTaskListeners() {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        return mtl.getTaskListeners();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TypeMirror getTypeMirror(Iterable<? extends Tree> path) {
        // TODO: Should complete attribution if necessary
        Tree last = null;
        for (Tree node : path) {
            last = Objects.requireNonNull(node);
        }
        if (last == null) {
            throw new IllegalArgumentException("empty path");
        }
        return ((JCTree) last).type;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Elements getElements() {
        if (context == null)
            throw new IllegalStateException();
        return JavacElements.instance(context);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Types getTypes() {
        if (context == null)
            throw new IllegalStateException();
        return JavacTypes.instance(context);
    }

    @Override @DefinedBy(Api.COMPILER)
    public void addModules(Iterable<String> moduleNames) {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setProcessors(Iterable<? extends Processor> processors) {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setLocale(Locale locale) {
        throw new IllegalStateException();
    }

    @Override @DefinedBy(Api.COMPILER)
    public Boolean call() {
        throw new IllegalStateException();
    }

    /**
     * For internal use only.
     * This method will be removed without warning.
     * @return the context
     */
    public Context getContext() {
        return context;
    }

    public void initPlugins(Set<JCList<String>> pluginOpts) {
        PlatformDescription platformProvider = context.get(PlatformDescription.class);

        if (platformProvider != null) {
            for (PluginInfo<Plugin> pluginDesc : platformProvider.getPlugins()) {
                java.util.List<String> options =
                        pluginDesc.getOptions().entrySet().stream()
                                                          .map(e -> e.getKey() + "=" + e.getValue())
                                                          .collect(Collectors.toList());
                try {
                    pluginDesc.getPlugin().init(this, options.toArray(new String[options.size()]));
                } catch (RuntimeException ex) {
                    throw new PropagatedException(ex);
                }
            }
        }

        if (pluginOpts.isEmpty())
            return;

        Set<JCList<String>> pluginsToCall = new LinkedHashSet<>(pluginOpts);
        JavacProcessingEnvironment pEnv = JavacProcessingEnvironment.instance(context);
        ServiceLoader<Plugin> sl = pEnv.getServiceLoader(Plugin.class);
        for (Plugin plugin : sl) {
            for (JCList<String> p : pluginsToCall) {
                if (plugin.getName().equals(p.head)) {
                    pluginsToCall.remove(p);
                    try {
                        plugin.init(this, p.tail.toArray(new String[p.tail.size()]));
                    } catch (RuntimeException ex) {
                        throw new PropagatedException(ex);
                    }
                }
            }
        }
        for (JCList<String> p: pluginsToCall) {
            Log.instance(context).error("plugin.not.found", p.head);
        }
    }

    public void initDocLint(JCList<String> docLintOpts) {
        if (docLintOpts.isEmpty())
            return;

//        new DocLint().init(this, docLintOpts.toArray(new String[docLintOpts.size()]));
        JavaCompiler.instance(context).keepComments = true;
    }
}
