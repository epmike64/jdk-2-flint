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

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.*;

import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.comp.Attr;
import com.flint.tools.flintc.comp.AttrContext;
import com.flint.tools.flintc.comp.Env;
import com.flint.tools.flintc.file.BaseFileManager;
import com.flint.tools.flintc.main.Arguments;
import com.flint.tools.flintc.main.Main;
import com.flint.tools.flintc.processing.AnnotationProcessingError;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.tree.TreeInfo;
import com.flint.source.tree.*;
import com.flint.tools.flintc.code.Symbol.ClassSymbol;
import com.flint.tools.flintc.parser.Parser;
import com.flint.tools.flintc.parser.ParserFactory;
import com.flint.tools.flintc.tree.JCTree.JCClassDecl;
import com.flint.tools.flintc.tree.JCTree.JCCompilationUnit;
import com.flint.tools.flintc.tree.JCTree.JCModuleDecl;
import com.flint.tools.flintc.tree.JCTree.JCTreeTag;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.Log.PrefixKind;
import com.flint.tools.flintc.util.Log.WriterKind;
import com.flint.tools.flintc.main.JavaCompiler;
/**
 * Provides access to functionality specific to the JDK Java Compiler, javac.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 */
public class JavacTaskImpl extends BasicJavacTask {
    private final Arguments args;
    private JavaCompiler compiler;
    private JavaFileManager fileManager;
    private Locale locale;
    private Map<JavaFileObject, JCCompilationUnit> notYetEntered;
    private ListBuffer<Env<AttrContext>> genList;
    private final AtomicBoolean used = new AtomicBoolean();
    private Iterable<? extends Processor> processors;
    private ListBuffer<String> addModules = new ListBuffer<>();

    protected JavacTaskImpl(Context context) {
        super(context, true);
        args = Arguments.instance(context);
        fileManager = context.get(JavaFileManager.class);
    }

    @Override @DefinedBy(Api.COMPILER)
    public Boolean call() {
        return doCall().isOK();
    }

    /* Internal version of call exposing Main.Result. */
    public Main.Result doCall() {
        try {
            return handleExceptions(() -> {
                prepareCompiler(false);
                if (compiler.errorCount() > 0)
                    return Main.Result.ERROR;
                compiler.compile(args.getFileObjects(), args.getClassNames(), processors, addModules);
                return (compiler.errorCount() > 0) ? Main.Result.ERROR : Main.Result.OK; // FIXME?
            }, Main.Result.SYSERR, Main.Result.ABNORMAL);
        } finally {
            try {
                cleanup();
            } catch (ClientCodeException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    @Override @DefinedBy(Api.COMPILER)
    public void addModules(Iterable<String> moduleNames) {
        Objects.requireNonNull(moduleNames);
        // not mt-safe
        if (used.get())
            throw new IllegalStateException();
        for (String m : moduleNames) {
            Objects.requireNonNull(m);
            addModules.add(m);
        }
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setProcessors(Iterable<? extends Processor> processors) {
        Objects.requireNonNull(processors);
        // not mt-safe
        if (used.get())
            throw new IllegalStateException();
        this.processors = processors;
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setLocale(Locale locale) {
        if (used.get())
            throw new IllegalStateException();
        this.locale = locale;
    }

    private <T> T handleExceptions(Callable<T> c, T sysErrorResult, T abnormalErrorResult) {
        try {
            return c.call();
        } catch (FatalError ex) {
            Log log = Log.instance(context);
            Options options = Options.instance(context);
            log.printRawLines(ex.getMessage());
            if (ex.getCause() != null && options.isSet("dev")) {
                ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
            }
            return sysErrorResult;
        } catch (AnnotationProcessingError | ClientCodeException e) {
            // AnnotationProcessingError is thrown from JavacProcessingEnvironment,
            // to forward errors thrown from an annotation processor
            // ClientCodeException is thrown from ClientCodeWrapper,
            // to forward errors thrown from user-supplied code for Compiler API
            // as specified by javax.tools.JavaCompiler#getTask
            // and javax.tools.JavaCompiler.CompilationTask#call
            throw new RuntimeException(e.getCause());
        } catch (PropagatedException e) {
            throw e.getCause();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception | Error ex) {
            // Nasty.  If we've already reported an error, compensate
            // for buggy compiler error recovery by swallowing thrown
            // exceptions.
            if (compiler == null || compiler.errorCount() == 0
                    || Options.instance(context).isSet("dev")) {
                Log log = Log.instance(context);
                log.printLines(PrefixKind.JAVAC, "msg.bug", JavaCompiler.version());
                ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
            }
            return abnormalErrorResult;
        }
    }

    private void prepareCompiler(boolean forParse) {
        if (used.getAndSet(true)) {
            if (compiler == null)
                throw new PropagatedException(new IllegalStateException());
        } else {
            args.validate();

            //initialize compiler's default locale
            context.put(Locale.class, locale);

            // hack
            JavacMessages messages = context.get(JavacMessages.messagesKey);
            if (messages != null && !messages.getCurrentLocale().equals(locale))
                messages.setCurrentLocale(locale);

            initPlugins(args.getPluginOpts());
            initDocLint(args.getDocLintOpts());

            // init JavaCompiler and queues
            compiler = JavaCompiler.instance(context);

            compiler.genEndPos = true;
            notYetEntered = new HashMap<>();
            if (forParse) {
                compiler.initProcessAnnotations(processors, args.getFileObjects(), args.getClassNames());
                for (JavaFileObject file: args.getFileObjects())
                    notYetEntered.put(file, null);
                genList = new ListBuffer<>();
            }
        }
    }

    <T> String toString(Iterable<T> items, String sep) {
        String currSep = "";
        StringBuilder sb = new StringBuilder();
        for (T item: items) {
            sb.append(currSep);
            sb.append(item.toString());
            currSep = sep;
        }
        return sb.toString();
    }

    void cleanup() {
        if (compiler != null)
            compiler.close();
        if (fileManager instanceof BaseFileManager && ((BaseFileManager) fileManager).autoClose) {
            try {
                fileManager.close();
            } catch (IOException ignore) {
            }
        }
        compiler = null;
        context = null;
        notYetEntered = null;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends CompilationUnitTree> parse() {
        return handleExceptions(this::parseInternal, JCList.nil(), JCList.nil());
    }

    private Iterable<? extends CompilationUnitTree> parseInternal() {
        try {
            prepareCompiler(true);
            JCList<JCCompilationUnit> units = compiler.parseFiles(args.getFileObjects());
            for (JCCompilationUnit unit: units) {
                JavaFileObject file = unit.getSourceFile();
                if (notYetEntered.containsKey(file))
                    notYetEntered.put(file, unit);
            }
            return units;
        }
        finally {
            parsed = true;
            if (compiler != null && compiler.log != null)
                compiler.log.flush();
        }
    }

    private boolean parsed = false;

    /**
     * Translate all the abstract syntax trees to elements.
     *
     * @return a list of elements corresponding to the top level
     * classes in the abstract syntax trees
     */
    public Iterable<? extends Element> enter() {
        return enter(null);
    }

    /**
     * Translate the given abstract syntax trees to elements.
     *
     * @param trees a list of abstract syntax trees.
     * @return a list of elements corresponding to the top level
     * classes in the abstract syntax trees
     */
    public Iterable<? extends Element> enter(Iterable<? extends CompilationUnitTree> trees)
    {
        if (trees == null && notYetEntered != null && notYetEntered.isEmpty())
            return JCList.nil();

        boolean wasInitialized = compiler != null;

        prepareCompiler(true);

        ListBuffer<JCCompilationUnit> roots = null;

        if (trees == null) {
            // If there are still files which were specified to be compiled
            // (i.e. in fileObjects) but which have not yet been entered,
            // then we make sure they have been parsed and add them to the
            // list to be entered.
            if (notYetEntered.size() > 0) {
                if (!parsed)
                    parseInternal(); // TODO would be nice to specify files needed to be parsed
                for (JavaFileObject file: args.getFileObjects()) {
                    JCCompilationUnit unit = notYetEntered.remove(file);
                    if (unit != null) {
                        if (roots == null)
                            roots = new ListBuffer<>();
                        roots.append(unit);
                    }
                }
                notYetEntered.clear();
            }
        }
        else {
            for (CompilationUnitTree cu : trees) {
                if (cu instanceof JCCompilationUnit) {
                    if (roots == null)
                        roots = new ListBuffer<>();
                    roots.append((JCCompilationUnit)cu);
                    notYetEntered.remove(cu.getSourceFile());
                }
                else
                    throw new IllegalArgumentException(cu.toString());
            }
        }

        if (roots == null) {
            if (trees == null && !wasInitialized) {
                compiler.initModules(JCList.nil());
            }
            return JCList.nil();
        }

        JCList<JCCompilationUnit> units = compiler.initModules(roots.toList());

        try {
            units = compiler.enterTrees(units);

            if (notYetEntered.isEmpty())
                compiler.processAnnotations(units);

            ListBuffer<Element> elements = new ListBuffer<>();
            for (JCCompilationUnit unit : units) {
                boolean isPkgInfo = unit.sourcefile.isNameCompatible("package-info",
                                                                     JavaFileObject.Kind.SOURCE);
                if (isPkgInfo) {
                    elements.append(unit.packge);
                } else {
                    for (JCTree node : unit.defs) {
                        if (node.hasTag(JCTree.JCTreeTag.CLASSDEF)) {
                            JCClassDecl cdef = (JCClassDecl) node;
                            if (cdef.sym != null) // maybe null if errors in anno processing
                                elements.append(cdef.sym);
                        } else if (node.hasTag(JCTree.JCTreeTag.MODULEDEF)) {
                            JCModuleDecl mdef = (JCModuleDecl) node;
                            if (mdef.sym != null)
                                elements.append(mdef.sym);
                        }
                    }
                }
            }
            return elements.toList();
        }
        finally {
            compiler.log.flush();
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends Element> analyze() {
        return handleExceptions(() -> analyze(null), JCList.nil(), JCList.nil());
    }

    /**
     * Complete all analysis on the given classes.
     * This can be used to ensure that all compile time errors are reported.
     * The classes must have previously been returned from {@link #enter}.
     * If null is specified, all outstanding classes will be analyzed.
     *
     * @param classes a list of class elements
     * @return the elements that were analyzed
     */
    // This implementation requires that we open up privileges on JavaCompiler.
    // An alternative implementation would be to move this code to JavaCompiler and
    // wrap it here
    public Iterable<? extends Element> analyze(Iterable<? extends Element> classes) {
        enter(null);  // ensure all classes have been entered

        final ListBuffer<Element> results = new ListBuffer<>();
        try {
            if (classes == null) {
                handleFlowResults(compiler.flow(compiler.attribute(compiler.todo)), results);
            } else {
                Filter f = new Filter() {
                    @Override
                    public void process(Env<AttrContext> env) {
                        handleFlowResults(compiler.flow(compiler.attribute(env)), results);
                    }
                };
                f.run(compiler.todo, classes);
            }
        } finally {
            compiler.log.flush();
        }
        return results;
    }
    // where
        private void handleFlowResults(Queue<Env<AttrContext>> queue, ListBuffer<Element> elems) {
            for (Env<AttrContext> env: queue) {
                switch (env.tree.getTag()) {
                    case CLASSDEF:
                        JCClassDecl cdef = (JCClassDecl) env.tree;
                        if (cdef.sym != null)
                            elems.append(cdef.sym);
                        break;
                    case MODULEDEF:
                        JCModuleDecl mod = (JCModuleDecl) env.tree;
                        if (mod.sym != null)
                            elems.append(mod.sym);
                        break;
                    case PACKAGEDEF:
                        JCCompilationUnit unit = env.toplevel;
                        if (unit.packge != null)
                            elems.append(unit.packge);
                        break;
                }
            }
            genList.addAll(queue);
        }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Iterable<? extends JavaFileObject> generate() {
        return handleExceptions(() -> generate(null), JCList.nil(), JCList.nil());
    }

    /**
     * Generate code corresponding to the given classes.
     * The classes must have previously been returned from {@link #enter}.
     * If there are classes outstanding to be analyzed, that will be done before
     * any classes are generated.
     * If null is specified, code will be generated for all outstanding classes.
     *
     * @param classes a list of class elements
     * @return the files that were generated
     */
    public Iterable<? extends JavaFileObject> generate(Iterable<? extends Element> classes) {
        final ListBuffer<JavaFileObject> results = new ListBuffer<>();
        try {
            analyze(null);  // ensure all classes have been parsed, entered, and analyzed

            if (classes == null) {
                compiler.generate(compiler.desugar(genList), results);
                genList.clear();
            }
            else {
                Filter f = new Filter() {
                        @Override
                        public void process(Env<AttrContext> env) {
                            compiler.generate(compiler.desugar(ListBuffer.of(env)), results);
                        }
                    };
                f.run(genList, classes);
            }
            if (genList.isEmpty()) {
                compiler.reportDeferredDiagnostics();
                cleanup();
            }
        }
        finally {
            if (compiler != null)
                compiler.log.flush();
        }
        return results;
    }

    public Iterable<? extends Tree> pathFor(CompilationUnitTree unit, Tree node) {
        return TreeInfo.pathFor((JCTree) node, (JCTree.JCCompilationUnit) unit).reverse();
    }

    public void ensureEntered() {
        args.allowEmpty();
        enter(null);
    }

    abstract class Filter {
        void run(Queue<Env<AttrContext>> list, Iterable<? extends Element> elements) {
            Set<Element> set = new HashSet<>();
            for (Element item: elements) {
                set.add(item);
            }

            ListBuffer<Env<AttrContext>> defer = new ListBuffer<>();
            while (list.peek() != null) {
                Env<AttrContext> env = list.remove();
                Symbol test = null;

                if (env.tree.hasTag(JCTreeTag.MODULEDEF)) {
                    test = ((JCModuleDecl) env.tree).sym;
                } else if (env.tree.hasTag(JCTreeTag.PACKAGEDEF)) {
                    test = env.toplevel.packge;
                } else {
                    ClassSymbol csym = env.enclClass.sym;
                    if (csym != null)
                        test = csym.outermostClass();
                }
                if (test != null && set.contains(test))
                    process(env);
                else
                    defer = defer.append(env);
            }

            list.addAll(defer);
        }

        abstract void process(Env<AttrContext> env);
    }

    /**
     * For internal use only.  This method will be
     * removed without warning.
     * @param expr the type expression to be analyzed
     * @param scope the scope in which to analyze the type expression
     * @return the type
     * @throws IllegalArgumentException if the type expression of null or empty
     */
    public Type parseType(String expr, TypeElement scope) {
        if (expr == null || expr.equals(""))
            throw new IllegalArgumentException();
        compiler = JavaCompiler.instance(context);
        JavaFileObject prev = compiler.log.useSource(null);
        ParserFactory parserFactory = ParserFactory.instance(context);
        Attr attr = Attr.instance(context);
        try {
            CharBuffer buf = CharBuffer.wrap((expr+"\u0000").toCharArray(), 0, expr.length());
            Parser parser = parserFactory.newParser(buf, false, false, false);
            JCTree tree = parser.parseType();
            return attr.attribType(tree, (Symbol.TypeSymbol)scope);
        } finally {
            compiler.log.useSource(prev);
        }
    }

}
