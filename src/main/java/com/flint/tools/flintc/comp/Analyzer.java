/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.comp;

import com.flint.tools.flintc.code.Source;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.Types;
import com.flint.tools.flintc.tree.*;
import com.sun.source.tree.LambdaExpressionTree;
import com.flint.tools.flintc.tree.JCTree.JCBlock;
import com.flint.tools.flintc.tree.JCTree.JCClassDecl;
import com.flint.tools.flintc.tree.JCTree.JCDoWhileLoop;
import com.flint.tools.flintc.tree.JCTree.JCEnhancedForLoop;
import com.flint.tools.flintc.tree.JCTree.JCForLoop;
import com.flint.tools.flintc.tree.JCTree.JCIf;
import com.flint.tools.flintc.tree.JCTree.JCLambda;
import com.flint.tools.flintc.tree.JCTree.JCLambda.ParameterKind;
import com.flint.tools.flintc.tree.JCTree.JCMethodDecl;
import com.flint.tools.flintc.tree.JCTree.JCMethodInvocation;
import com.flint.tools.flintc.tree.JCTree.JCNewClass;
import com.flint.tools.flintc.tree.JCTree.JCStatement;
import com.flint.tools.flintc.tree.JCTree.JCSwitch;
import com.flint.tools.flintc.tree.JCTree.JCTypeApply;
import com.flint.tools.flintc.tree.JCTree.JCVariableDecl;
import com.flint.tools.flintc.tree.JCTree.JCWhileLoop;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCDiagnostic;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticType;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.ListBuffer;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.Names;
import com.flint.tools.flintc.util.Options;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.flint.tools.flintc.code.Flags.GENERATEDCONSTR;
import static com.flint.tools.flintc.code.Flags.SYNTHETIC;
import static com.flint.tools.flintc.code.TypeTag.CLASS;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.APPLY;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.METHODDEF;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.NEWCLASS;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.TYPEAPPLY;

/**
 * Helper class for defining custom code analysis, such as finding instance creation expression
 * that can benefit from diamond syntax.
 */
public class Analyzer {
    protected static final Context.Key<Analyzer> analyzerKey = new Context.Key<>();

    final Types types;
    final Log log;
    final Attr attr;
    final DeferredAttr deferredAttr;
    final ArgumentAttr argumentAttr;
    final TreeMaker make;
    final Names names;
    private final boolean allowDiamondWithAnonymousClassCreation;

    final EnumSet<AnalyzerMode> analyzerModes;

    public static Analyzer instance(Context context) {
        Analyzer instance = context.get(analyzerKey);
        if (instance == null)
            instance = new Analyzer(context);
        return instance;
    }

    protected Analyzer(Context context) {
        context.put(analyzerKey, this);
        types = Types.instance(context);
        log = Log.instance(context);
        attr = Attr.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        argumentAttr = ArgumentAttr.instance(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        Options options = Options.instance(context);
        String findOpt = options.get("find");
        //parse modes
        Source source = Source.instance(context);
        allowDiamondWithAnonymousClassCreation = source.allowDiamondWithAnonymousClassCreation();
        analyzerModes = AnalyzerMode.getAnalyzerModes(findOpt, source);
    }

    /**
     * This enum defines supported analyzer modes, as well as defining the logic for decoding
     * the {@code -XDfind} option.
     */
    enum AnalyzerMode {
        DIAMOND("diamond", Source::allowDiamond),
        LAMBDA("lambda", Source::allowLambda),
        METHOD("method", Source::allowGraphInference);

        final String opt;
        final Predicate<Source> sourceFilter;

        AnalyzerMode(String opt, Predicate<Source> sourceFilter) {
            this.opt = opt;
            this.sourceFilter = sourceFilter;
        }

        /**
         * This method is used to parse the {@code find} option.
         * Possible modes are separated by colon; a mode can be excluded by
         * prepending '-' to its name. Finally, the special mode 'all' can be used to
         * add all modes to the resulting enum.
         */
        static EnumSet<AnalyzerMode> getAnalyzerModes(String opt, Source source) {
            if (opt == null) {
                return EnumSet.noneOf(AnalyzerMode.class);
            }
            JCList<String> modes = JCList.from(opt.split(","));
            EnumSet<AnalyzerMode> res = EnumSet.noneOf(AnalyzerMode.class);
            if (modes.contains("all")) {
                res = EnumSet.allOf(AnalyzerMode.class);
            }
            for (AnalyzerMode mode : values()) {
                if (modes.contains(mode.opt)) {
                    res.add(mode);
                } else if (modes.contains("-" + mode.opt) || !mode.sourceFilter.test(source)) {
                    res.remove(mode);
                }
            }
            return res;
        }
    }

    /**
     * A statement analyzer is a work-unit that matches certain AST nodes (of given type {@code S}),
     * rewrites them to different AST nodes (of type {@code T}) and then generates some meaningful
     * messages in case the analysis has been successful.
     */
    abstract class StatementAnalyzer<S extends JCTree, T extends JCTree> {

        AnalyzerMode mode;
        JCTree.JCTreeTag tag;

        StatementAnalyzer(AnalyzerMode mode, JCTreeTag tag) {
            this.mode = mode;
            this.tag = tag;
        }

        /**
         * Is this analyzer allowed to run?
         */
        boolean isEnabled() {
            return analyzerModes.contains(mode);
        }

        /**
         * Should this analyzer be rewriting the given tree?
         */
        abstract boolean match(S tree);

        /**
         * Rewrite a given AST node into a new one
         */
        abstract T map(S oldTree, S newTree);

        /**
         * Entry-point for comparing results and generating diagnostics.
         */
        abstract void process(S oldTree, T newTree, boolean hasErrors);

    }

    /**
     * This analyzer checks if generic instance creation expression can use diamond syntax.
     */
    class DiamondInitializer extends StatementAnalyzer<JCNewClass, JCNewClass> {

        DiamondInitializer() {
            super(AnalyzerMode.DIAMOND, NEWCLASS);
        }

        @Override
        boolean match(JCNewClass tree) {
            return tree.clazz.hasTag(TYPEAPPLY) &&
                    !TreeInfo.isDiamond(tree) &&
                    (tree.def == null || allowDiamondWithAnonymousClassCreation);
        }

        @Override
        JCNewClass map(JCNewClass oldTree, JCNewClass newTree) {
            if (newTree.clazz.hasTag(TYPEAPPLY)) {
                ((JCTypeApply)newTree.clazz).arguments = JCList.nil();
            }
            return newTree;
        }

        @Override
        void process(JCNewClass oldTree, JCNewClass newTree, boolean hasErrors) {
            if (!hasErrors) {
                JCList<Type> inferredArgs, explicitArgs;
                if (oldTree.def != null) {
                    inferredArgs = newTree.def.implementing.nonEmpty()
                                      ? newTree.def.implementing.get(0).type.getTypeArguments()
                                      : newTree.def.extending.type.getTypeArguments();
                    explicitArgs = oldTree.def.implementing.nonEmpty()
                                      ? oldTree.def.implementing.get(0).type.getTypeArguments()
                                      : oldTree.def.extending.type.getTypeArguments();
                } else {
                    inferredArgs = newTree.type.getTypeArguments();
                    explicitArgs = oldTree.type.getTypeArguments();
                }
                for (Type t : inferredArgs) {
                    if (!types.isSameType(t, explicitArgs.head)) {
                        return;
                    }
                    explicitArgs = explicitArgs.tail;
                }
                //exact match
                log.warning(oldTree.clazz, "diamond.redundant.args");
            }
        }
    }

    /**
     * This analyzer checks if anonymous instance creation expression can replaced by lambda.
     */
    class LambdaAnalyzer extends StatementAnalyzer<JCNewClass, JCLambda> {

        LambdaAnalyzer() {
            super(AnalyzerMode.LAMBDA, NEWCLASS);
        }

        @Override
        boolean match (JCNewClass tree){
            Type clazztype = tree.clazz.type;
            return tree.def != null &&
                    clazztype.hasTag(CLASS) &&
                    types.isFunctionalInterface(clazztype.tsym) &&
                    decls(tree.def).length() == 1;
        }
        //where
            private JCList<JCTree> decls(JCClassDecl decl) {
                ListBuffer<JCTree> decls = new ListBuffer<>();
                for (JCTree t : decl.defs) {
                    if (t.hasTag(METHODDEF)) {
                        JCMethodDecl md = (JCMethodDecl)t;
                        if ((md.getModifiers().flags & GENERATEDCONSTR) == 0) {
                            decls.add(md);
                        }
                    } else {
                        decls.add(t);
                    }
                }
                return decls.toList();
            }

        @Override
        JCLambda map (JCNewClass oldTree, JCNewClass newTree){
            JCMethodDecl md = (JCMethodDecl)decls(newTree.def).head;
            JCList<JCVariableDecl> params = md.params;
            JCBlock body = md.body;
            return make.Lambda(params, body);
        }
        @Override
        void process (JCNewClass oldTree, JCLambda newTree, boolean hasErrors){
            if (!hasErrors) {
                log.warning(oldTree.def, "potential.lambda.found");
            }
        }
    }

    /**
     * This analyzer checks if generic method call has redundant type arguments.
     */
    class RedundantTypeArgAnalyzer extends StatementAnalyzer<JCMethodInvocation, JCMethodInvocation> {

        RedundantTypeArgAnalyzer() {
            super(AnalyzerMode.METHOD, APPLY);
        }

        @Override
        boolean match (JCMethodInvocation tree){
            return tree.typeargs != null &&
                    tree.typeargs.nonEmpty();
        }
        @Override
        JCMethodInvocation map (JCMethodInvocation oldTree, JCMethodInvocation newTree){
            newTree.typeargs = JCList.nil();
            return newTree;
        }
        @Override
        void process (JCMethodInvocation oldTree, JCMethodInvocation newTree, boolean hasErrors){
            if (!hasErrors) {
                //exact match
                log.warning(oldTree, "method.redundant.typeargs");
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    StatementAnalyzer<JCTree, JCTree>[] analyzers = new StatementAnalyzer[] {
            new DiamondInitializer(),
            new LambdaAnalyzer(),
            new RedundantTypeArgAnalyzer()
    };

    /**
     * Analyze an AST node if needed.
     */
    void analyzeIfNeeded(JCTree tree, Env<AttrContext> env) {
        if (!analyzerModes.isEmpty() &&
                !env.info.isSpeculative &&
                TreeInfo.isStatement(tree)) {
            JCStatement stmt = (JCStatement)tree;
            analyze(stmt, env);
        }
    }

    /**
     * Analyze an AST node; this involves collecting a list of all the nodes that needs rewriting,
     * and speculatively type-check the rewritten code to compare results against previously attributed code.
     */
    void analyze(JCStatement statement, Env<AttrContext> env) {
        AnalysisContext context = new AnalysisContext();
        StatementScanner statementScanner = new StatementScanner(context);
        statementScanner.scan(statement);

        if (!context.treesToAnalyzer.isEmpty()) {

            //add a block to hoist potential dangling variable declarations
            JCBlock fakeBlock = make.Block(SYNTHETIC, JCList.of(statement));

            TreeMapper treeMapper = new TreeMapper(context);
            //TODO: to further refine the analysis, try all rewriting combinations
            deferredAttr.attribSpeculative(fakeBlock, env, attr.statInfo, treeMapper,
                    t -> new AnalyzeDeferredDiagHandler(context),
                    argumentAttr.withLocalCacheContext());
            context.treeMap.entrySet().forEach(e -> {
                context.treesToAnalyzer.get(e.getKey())
                        .process(e.getKey(), e.getValue(), context.errors.nonEmpty());
            });
        }
    }

    /**
     * Simple deferred diagnostic handler which filters out all messages and keep track of errors.
     */
    class AnalyzeDeferredDiagHandler extends Log.DeferredDiagnosticHandler {
        AnalysisContext context;

        public AnalyzeDeferredDiagHandler(AnalysisContext context) {
            super(log, d -> {
                if (d.getType() == DiagnosticType.ERROR) {
                    context.errors.add(d);
                }
                return true;
            });
            this.context = context;
        }
    }

    /**
     * This class is used to pass around contextual information bewteen analyzer classes, such as
     * trees to be rewritten, errors occurred during the speculative attribution step, etc.
     */
    class AnalysisContext {
        /** Map from trees to analyzers. */
        Map<JCTree, StatementAnalyzer<JCTree, JCTree>> treesToAnalyzer = new HashMap<>();

        /** Map from original AST nodes to rewritten AST nodes */
        Map<JCTree, JCTree> treeMap = new HashMap<>();

        /** Errors in rewritten tree */
        ListBuffer<JCDiagnostic> errors = new ListBuffer<>();
    }

    /**
     * Subclass of {@link TreeScanner} which visit AST-nodes w/o crossing
     * statement boundaries.
     */
    class StatementScanner extends TreeScanner {

        /** context */
        AnalysisContext context;

        StatementScanner(AnalysisContext context) {
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void scan(JCTree tree) {
            if (tree != null) {
                for (StatementAnalyzer<JCTree, JCTree> analyzer : analyzers) {
                    if (analyzer.isEnabled() &&
                            tree.hasTag(analyzer.tag) &&
                            analyzer.match(tree)) {
                        context.treesToAnalyzer.put(tree, analyzer);
                        break; //TODO: cover cases where multiple matching analyzers are found
                    }
                }
            }
            super.scan(tree);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitBlock(JCBlock tree) {
            //do nothing (prevents seeing same stuff twice
        }

        @Override
        public void visitSwitch(JCSwitch tree) {
            scan(tree.getExpression());
        }

        @Override
        public void visitForLoop(JCForLoop tree) {
            scan(tree.getInitializer());
            scan(tree.getCondition());
            scan(tree.getUpdate());
        }

        @Override
        public void visitForeachLoop(JCEnhancedForLoop tree) {
            scan(tree.getExpression());
        }

        @Override
        public void visitWhileLoop(JCWhileLoop tree) {
            scan(tree.getCondition());
        }

        @Override
        public void visitDoLoop(JCDoWhileLoop tree) {
            scan(tree.getCondition());
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.getCondition());
        }
    }

    /**
     * Subclass of TreeCopier that maps nodes matched by analyzers onto new AST nodes.
     */
    class TreeMapper extends TreeCopier<Void> {

        AnalysisContext context;

        TreeMapper(AnalysisContext context) {
            super(make);
            this.context = context;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Z extends JCTree> Z copy(Z tree, Void _unused) {
            Z newTree = super.copy(tree, _unused);
            StatementAnalyzer<JCTree, JCTree> analyzer = context.treesToAnalyzer.get(tree);
            if (analyzer != null) {
                newTree = (Z)analyzer.map(tree, newTree);
                context.treeMap.put(tree, newTree);
            }
            return newTree;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree visitLambdaExpression(LambdaExpressionTree node, Void _unused) {
            JCLambda oldLambda = (JCLambda)node;
            JCLambda newLambda = (JCLambda)super.visitLambdaExpression(node, _unused);
            if (oldLambda.paramKind == ParameterKind.IMPLICIT) {
                //reset implicit lambda parameters (whose type might have been set during attr)
                newLambda.paramKind = ParameterKind.IMPLICIT;
                newLambda.params.forEach(p -> p.vartype = null);
            }
            return newLambda;
        }
    }
}
