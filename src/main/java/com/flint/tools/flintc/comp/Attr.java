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

package com.flint.tools.flintc.comp;

import java.util.*;

import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;

import com.flint.tools.flintc.code.Attribute;
import com.flint.tools.flintc.code.BoundKind;
import com.flint.tools.flintc.code.DeferredLintHandler;
import com.flint.tools.flintc.code.Flags;
import com.flint.tools.flintc.code.Kinds;
import com.flint.tools.flintc.code.Lint;
import com.flint.tools.flintc.code.Source;
import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Symtab;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.TypeAnnotations;
import com.flint.tools.flintc.code.TypeTag;
import com.flint.tools.flintc.code.Types;
import com.flint.tools.flintc.jvm.ByteCodes;
import com.flint.tools.flintc.jvm.Target;
import com.flint.tools.flintc.tree.EndPosTable;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.tree.TreeInfo;
import com.flint.tools.flintc.tree.TreeMaker;
import com.flint.tools.flintc.tree.TreeScanner;
import com.flint.tools.flintc.tree.TreeTranslator;
import com.flint.source.tree.IdentifierTree;
import com.flint.source.tree.MemberReferenceTree.ReferenceMode;
import com.flint.source.tree.MemberSelectTree;
import com.flint.source.tree.TreeVisitor;
import com.flint.source.util.SimpleTreeVisitor;
import com.flint.tools.flintc.code.Lint.LintCategory;
import com.flint.tools.flintc.code.Scope.WriteableScope;
import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.code.Type.*;
import com.flint.tools.flintc.code.TypeMetadata.Annotations;
import com.flint.tools.flintc.code.Types.FunctionDescriptorLookupError;
import com.flint.tools.flintc.comp.ArgumentAttr.LocalCacheContext;
import com.flint.tools.flintc.comp.Check.CheckContext;
import com.flint.tools.flintc.comp.DeferredAttr.AttrMode;

import static com.flint.tools.flintc.resources.CompilerProperties.Fragments.Diamond;
import static com.flint.tools.flintc.resources.CompilerProperties.Fragments.DiamondInvalidArg;
import static com.flint.tools.flintc.resources.CompilerProperties.Fragments.DiamondInvalidArgs;
import com.flint.tools.flintc.resources.CompilerProperties.Errors;
import com.flint.tools.flintc.resources.CompilerProperties.Fragments;
import com.flint.tools.flintc.tree.JCTree.*;
import com.flint.tools.flintc.tree.JCTree.JCPolyExpression.*;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticPosition;
import com.flint.tools.flintc.util.JCDiagnostic.Fragment;

import static com.flint.tools.flintc.code.Flags.*;
import static com.flint.tools.flintc.code.Flags.ANNOTATION;
import static com.flint.tools.flintc.code.Flags.BLOCK;
import static com.flint.tools.flintc.code.Kinds.*;
import static com.flint.tools.flintc.code.Kinds.Kind.*;
import static com.flint.tools.flintc.code.TypeTag.*;
import static com.flint.tools.flintc.code.TypeTag.WILDCARD;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.*;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticFlag;

/** This is the main context-dependent analysis phase in GJC. It
 *  encompasses name resolution, type checking and constant folding as
 *  subtasks. Some subtasks involve auxiliary classes.
 *  @see Check
 *  @see Resolve
 *  @see ConstFold
 *  @see Infer
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Attr extends JCTree.Visitor {
    protected static final Context.Key<Attr> attrKey = new Context.Key<>();

    final Names names;
    final Log log;
    final Symtab syms;
    final Resolve rs;
    final Operators operators;
    final Infer infer;
    final Analyzer analyzer;
    final DeferredAttr deferredAttr;
    final Check chk;
    final Flow flow;
    final MemberEnter memberEnter;
    final TypeEnter typeEnter;
    final TreeMaker make;
    final ConstFold cfolder;
    final Enter enter;
    final Target target;
    final Types types;
    final JCDiagnostic.Factory diags;
    final TypeAnnotations typeAnnotations;
    final DeferredLintHandler deferredLintHandler;
    final TypeEnvs typeEnvs;
    final Dependencies dependencies;
    final Annotate annotate;
    final ArgumentAttr argumentAttr;

    public static Attr instance(Context context) {
        Attr instance = context.get(attrKey);
        if (instance == null)
            instance = new Attr(context);
        return instance;
    }

    protected Attr(Context context) {
        context.put(attrKey, this);

        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        operators = Operators.instance(context);
        chk = Check.instance(context);
        flow = Flow.instance(context);
        memberEnter = MemberEnter.instance(context);
        typeEnter = TypeEnter.instance(context);
        make = TreeMaker.instance(context);
        enter = Enter.instance(context);
        infer = Infer.instance(context);
        analyzer = Analyzer.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        cfolder = ConstFold.instance(context);
        target = Target.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        annotate = Annotate.instance(context);
        typeAnnotations = TypeAnnotations.instance(context);
        deferredLintHandler = DeferredLintHandler.instance(context);
        typeEnvs = TypeEnvs.instance(context);
        dependencies = Dependencies.instance(context);
        argumentAttr = ArgumentAttr.instance(context);

        Options options = Options.instance(context);

        Source source = Source.instance(context);
        allowStringsInSwitch = source.allowStringsInSwitch();
        allowPoly = source.allowPoly();
        allowTypeAnnos = source.allowTypeAnnotations();
        allowLambda = source.allowLambda();
        allowDefaultMethods = source.allowDefaultMethods();
        allowStaticInterfaceMethods = source.allowStaticInterfaceMethods();
        sourceName = source.name;
        useBeforeDeclarationWarning = options.isSet("useBeforeDeclarationWarning");

        statInfo = new ResultInfo(KindSelector.NIL, Type.noType);
        varAssignmentInfo = new ResultInfo(KindSelector.ASG, Type.noType);
        unknownExprInfo = new ResultInfo(KindSelector.VAL, Type.noType);
        methodAttrInfo = new MethodAttrInfo();
        unknownTypeInfo = new ResultInfo(KindSelector.TYP, Type.noType);
        unknownTypeExprInfo = new ResultInfo(KindSelector.VAL_TYP, Type.noType);
        recoveryInfo = new RecoveryInfo(deferredAttr.emptyDeferredAttrContext);
    }

    /** Switch: support target-typing inference
     */
    boolean allowPoly;

    /** Switch: support type annotations.
     */
    boolean allowTypeAnnos;

    /** Switch: support lambda expressions ?
     */
    boolean allowLambda;

    /** Switch: support default methods ?
     */
    boolean allowDefaultMethods;

    /** Switch: static interface methods enabled?
     */
    boolean allowStaticInterfaceMethods;

    /**
     * Switch: warn about use of variable before declaration?
     * RFE: 6425594
     */
    boolean useBeforeDeclarationWarning;

    /**
     * Switch: allow strings in switch?
     */
    boolean allowStringsInSwitch;

    /**
     * Switch: name of source level; used for error reporting.
     */
    String sourceName;

    /** Check kind and type of given tree against protokind and prototype.
     *  If check succeeds, store type in tree and return it.
     *  If check fails, store errType in tree and return it.
     *  No checks are performed if the prototype is a method type.
     *  It is not necessary in this case since we know that kind and type
     *  are correct.
     *
     *  @param tree     The tree whose kind and type is checked
     *  @param found    The computed type of the tree
     *  @param ownkind  The computed kind of the tree
     *  @param resultInfo  The expected result of the tree
     */
    Type check(final JCTree tree,
               final Type found,
               final KindSelector ownkind,
               final ResultInfo resultInfo) {
        InferenceContext inferenceContext = resultInfo.checkContext.inferenceContext();
        Type owntype;
        boolean shouldCheck = !found.hasTag(ERROR) &&
                !resultInfo.pt.hasTag(METHOD) &&
                !resultInfo.pt.hasTag(FORALL);
        if (shouldCheck && !ownkind.subset(resultInfo.pkind)) {
            log.error(tree.pos(), "unexpected.type",
            resultInfo.pkind.kindNames(),
            ownkind.kindNames());
            owntype = types.createErrorType(found);
        } else if (allowPoly && inferenceContext.free(found)) {
            //delay the check if there are inference variables in the found type
            //this means we are dealing with a partially inferred poly expression
            owntype = shouldCheck ? resultInfo.pt : found;
            if (resultInfo.checkMode.installPostInferenceHook()) {
                inferenceContext.addFreeTypeListener(JCList.of(found),
                        instantiatedContext -> {
                            ResultInfo pendingResult =
                                    resultInfo.dup(inferenceContext.asInstType(resultInfo.pt));
                            check(tree, inferenceContext.asInstType(found), ownkind, pendingResult);
                        });
            }
        } else {
            owntype = shouldCheck ?
            resultInfo.check(tree, found) :
            found;
        }
        if (resultInfo.checkMode.updateTreeType()) {
            tree.type = owntype;
        }
        return owntype;
    }

    /** Is given blank final variable assignable, i.e. in a scope where it
     *  may be assigned to even though it is final?
     *  @param v      The blank final variable.
     *  @param env    The current environment.
     */
    boolean isAssignableAsBlankFinal(VarSymbol v, Env<AttrContext> env) {
        Symbol owner = env.info.scope.owner;
           // owner refers to the innermost variable, method or
           // initializer block declaration at this point.
        return
            v.owner == owner
            ||
            ((owner.name == names.init ||    // i.e. we are in a constructor
              owner.kind == VAR ||           // i.e. we are in a variable initializer
              (owner.flags() & BLOCK) != 0)  // i.e. we are in an initializer block
             &&
             v.owner == owner.owner
             &&
             ((v.flags() & STATIC) != 0) == Resolve.isStatic(env));
    }

    /** Check that variable can be assigned to.
     *  @param pos    The current source code position.
     *  @param v      The assigned variable
     *  @param base   If the variable is referred to in a Select, the part
     *                to the left of the `.', null otherwise.
     *  @param env    The current environment.
     */
    void checkAssignable(DiagnosticPosition pos, VarSymbol v, JCTree base, Env<AttrContext> env) {
        if (v.name == names._this) {
            //log.error(pos, Errors.CantAssignValToThis);
        } else if ((v.flags() & FINAL) != 0 &&
            ((v.flags() & HASINIT) != 0
             ||
             !((base == null ||
               (base.hasTag(IDENT) && TreeInfo.name(base) == names._this)) &&
               isAssignableAsBlankFinal(v, env)))) {
            if (v.isResourceVariable()) { //TWR resource
                log.error(pos, "try.resource.may.not.be.assigned", v);
            } else {
                log.error(pos, "cant.assign.val.to.final.var", v);
            }
        }
    }

    /** Does tree represent a static reference to an identifier?
     *  It is assumed that tree is either a SELECT or an IDENT.
     *  We have to weed out selects from non-type names here.
     *  @param tree    The candidate tree.
     */
    boolean isStaticReference(JCTree tree) {
        if (tree.hasTag(SELECT)) {
            Symbol lsym = TreeInfo.symbol(((JCFieldAccess) tree).selected);
            if (lsym == null || lsym.kind != TYP) {
                return false;
            }
        }
        return true;
    }

    /** Is this symbol a type?
     */
    static boolean isType(Symbol sym) {
        return sym != null && sym.kind == TYP;
    }

    /** The current `this' symbol.
     *  @param env    The current environment.
     */
    Symbol thisSym(DiagnosticPosition pos, Env<AttrContext> env) {
        return rs.resolveSelf(pos, env, env.enclClass.sym, names._this);
    }

    /** Attribute a parsed identifier.
     * @param tree Parsed identifier name
     * @param topLevel The toplevel to use
     */
    public Symbol attribIdent(JCTree tree, JCCompilationUnit topLevel) {
        Env<AttrContext> localEnv = enter.topLevelEnv(topLevel);
        localEnv.enclClass = make.ClassDef(make.Modifiers(0),
                                           syms.errSymbol.name,
                                           null, null, null, null);
        localEnv.enclClass.sym = syms.errSymbol;
        return attribIdent(tree, localEnv);
    }

    /** Attribute a parsed identifier.
     * @param tree Parsed identifier name
     * @param env The env to use
     */
    public Symbol attribIdent(JCTree tree, Env<AttrContext> env) {
        return tree.accept(identAttributer, env);
    }
    // where
        private TreeVisitor<Symbol,Env<AttrContext>> identAttributer = new IdentAttributer();
        private class IdentAttributer extends SimpleTreeVisitor<Symbol,Env<AttrContext>> {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Symbol visitMemberSelect(MemberSelectTree node, Env<AttrContext> env) {
                Symbol site = visit(node.getExpression(), env);
                if (site.kind == ERR || site.kind == ABSENT_TYP || site.kind == HIDDEN)
                    return site;
                Name name = (Name)node.getIdentifier();
                if (site.kind == PCK) {
                    env.toplevel.packge = (PackageSymbol)site;
                    return rs.findIdentInPackage(env, (TypeSymbol)site, name,
                            KindSelector.TYP_PCK);
                } else {
                    env.enclClass.sym = (ClassSymbol)site;
                    return rs.findMemberType(env, site.asType(), name, (TypeSymbol)site);
                }
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Symbol visitIdentifier(IdentifierTree node, Env<AttrContext> env) {
                return rs.findIdent(env, (Name)node.getName(), KindSelector.TYP_PCK);
            }
        }

    public Type coerce(Type etype, Type ttype) {
        return cfolder.coerce(etype, ttype);
    }

    public Type attribType(JCTree node, TypeSymbol sym) {
        Env<AttrContext> env = typeEnvs.get(sym);
        Env<AttrContext> localEnv = env.dup(node, env.info.dup());
        return attribTree(node, localEnv, unknownTypeInfo);
    }

    public Type attribImportQualifier(JCImport tree, Env<AttrContext> env) {
        // Attribute qualifying package or class.
        JCFieldAccess s = (JCFieldAccess)tree.qualid;
        return attribTree(s.selected, env,
                          new ResultInfo(tree.staticImport ?
                                         KindSelector.TYP : KindSelector.TYP_PCK,
                       Type.noType));
    }

    public Env<AttrContext> attribExprToTree(JCTree expr, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            attribExpr(expr, env);
        } catch (BreakAttr b) {
            return b.env;
        } catch (AssertionError ae) {
            if (ae.getCause() instanceof BreakAttr) {
                return ((BreakAttr)(ae.getCause())).env;
            } else {
                throw ae;
            }
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }

    public Env<AttrContext> attribStatToTree(JCTree stmt, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            attribStat(stmt, env);
        } catch (BreakAttr b) {
            return b.env;
        } catch (AssertionError ae) {
            if (ae.getCause() instanceof BreakAttr) {
                return ((BreakAttr)(ae.getCause())).env;
            } else {
                throw ae;
            }
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }

    private JCTree breakTree = null;

    private static class BreakAttr extends RuntimeException {
        static final long serialVersionUID = -6924771130405446405L;
        private Env<AttrContext> env;
        private BreakAttr(Env<AttrContext> env) {
            this.env = env;
        }
    }

    /**
     * Mode controlling behavior of Attr.Check
     */
    enum CheckMode {

        NORMAL,

        /**
         * Mode signalling 'fake check' - skip tree update. A side-effect of this mode is
         * that the captured var cache in {@code InferenceContext} will be used in read-only
         * mode when performing inference checks.
         */
        NO_TREE_UPDATE {
            @Override
            public boolean updateTreeType() {
                return false;
            }
        },
        /**
         * Mode signalling that caller will manage free types in tree decorations.
         */
        NO_INFERENCE_HOOK {
            @Override
            public boolean installPostInferenceHook() {
                return false;
            }
        };

        public boolean updateTreeType() {
            return true;
        }
        public boolean installPostInferenceHook() {
            return true;
        }
    }


    class ResultInfo {
        final KindSelector pkind;
        final Type pt;
        final CheckContext checkContext;
        final CheckMode checkMode;

        ResultInfo(KindSelector pkind, Type pt) {
            this(pkind, pt, chk.basicHandler, CheckMode.NORMAL);
        }

        ResultInfo(KindSelector pkind, Type pt, CheckMode checkMode) {
            this(pkind, pt, chk.basicHandler, checkMode);
        }

        protected ResultInfo(KindSelector pkind,
                             Type pt, CheckContext checkContext) {
            this(pkind, pt, checkContext, CheckMode.NORMAL);
        }

        protected ResultInfo(KindSelector pkind,
                             Type pt, CheckContext checkContext, CheckMode checkMode) {
            this.pkind = pkind;
            this.pt = pt;
            this.checkContext = checkContext;
            this.checkMode = checkMode;
        }

        /**
         * Should {@link Attr#attribTree} use the {@ArgumentAttr} visitor instead of this one?
         * @param tree The tree to be type-checked.
         * @return true if {@ArgumentAttr} should be used.
         */
        protected boolean needsArgumentAttr(JCTree tree) { return false; }

        protected Type check(final DiagnosticPosition pos, final Type found) {
            return chk.checkType(pos, found, pt, checkContext);
        }

        protected ResultInfo dup(Type newPt) {
            return new ResultInfo(pkind, newPt, checkContext, checkMode);
        }

        protected ResultInfo dup(CheckContext newContext) {
            return new ResultInfo(pkind, pt, newContext, checkMode);
        }

        protected ResultInfo dup(Type newPt, CheckContext newContext) {
            return new ResultInfo(pkind, newPt, newContext, checkMode);
        }

        protected ResultInfo dup(Type newPt, CheckContext newContext, CheckMode newMode) {
            return new ResultInfo(pkind, newPt, newContext, newMode);
        }

        protected ResultInfo dup(CheckMode newMode) {
            return new ResultInfo(pkind, pt, checkContext, newMode);
        }

        @Override
        public String toString() {
            if (pt != null) {
                return pt.toString();
            } else {
                return "";
            }
        }
    }

    class MethodAttrInfo extends ResultInfo {
        public MethodAttrInfo() {
            this(chk.basicHandler);
        }

        public MethodAttrInfo(CheckContext checkContext) {
            super(KindSelector.VAL, Infer.anyPoly, checkContext);
        }

        @Override
        protected boolean needsArgumentAttr(JCTree tree) {
            return true;
        }

        protected ResultInfo dup(Type newPt) {
            throw new IllegalStateException();
        }

        protected ResultInfo dup(CheckContext newContext) {
            return new MethodAttrInfo(newContext);
        }

        protected ResultInfo dup(Type newPt, CheckContext newContext) {
            throw new IllegalStateException();
        }

        protected ResultInfo dup(Type newPt, CheckContext newContext, CheckMode newMode) {
            throw new IllegalStateException();
        }

        protected ResultInfo dup(CheckMode newMode) {
            throw new IllegalStateException();
        }
    }

    class RecoveryInfo extends ResultInfo {

        public RecoveryInfo(final DeferredAttr.DeferredAttrContext deferredAttrContext) {
            super(KindSelector.VAL, Type.recoveryType,
                  new Check.NestedCheckContext(chk.basicHandler) {
                @Override
                public DeferredAttr.DeferredAttrContext deferredAttrContext() {
                    return deferredAttrContext;
                }
                @Override
                public boolean compatible(Type found, Type req, Warner warn) {
                    return true;
                }
                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    chk.basicHandler.report(pos, details);
                }
            });
        }
    }

    final ResultInfo statInfo;
    final ResultInfo varAssignmentInfo;
    final ResultInfo methodAttrInfo;
    final ResultInfo unknownExprInfo;
    final ResultInfo unknownTypeInfo;
    final ResultInfo unknownTypeExprInfo;
    final ResultInfo recoveryInfo;

    Type pt() {
        return resultInfo.pt;
    }

    KindSelector pkind() {
        return resultInfo.pkind;
    }

/* ************************************************************************
 * Visitor methods
 *************************************************************************/

    /** Visitor argument: the current environment.
     */
    Env<AttrContext> env;

    /** Visitor argument: the currently expected attribution result.
     */
    ResultInfo resultInfo;

    /** Visitor result: the computed type.
     */
    Type result;

    /** Visitor method: attribute a tree, catching any completion failure
     *  exceptions. Return the tree's type.
     *
     *  @param tree    The tree to be visited.
     *  @param env     The environment visitor argument.
     *  @param resultInfo   The result info visitor argument.
     */
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        Env<AttrContext> prevEnv = this.env;
        ResultInfo prevResult = this.resultInfo;
        try {
            this.env = env;
            this.resultInfo = resultInfo;
            if (resultInfo.needsArgumentAttr(tree)) {
                result = argumentAttr.attribArg(tree, env);
            } else {
                tree.accept(this);
            }
            if (tree == breakTree &&
                    resultInfo.checkContext.deferredAttrContext().mode == AttrMode.CHECK) {
                throw new BreakAttr(copyEnv(env));
            }
            return result;
        } catch (CompletionFailure ex) {
            tree.type = syms.errType;
            return chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
            this.resultInfo = prevResult;
        }
    }

    Env<AttrContext> copyEnv(Env<AttrContext> env) {
        Env<AttrContext> newEnv =
                env.dup(env.tree, env.info.dup(copyScope(env.info.scope)));
        if (newEnv.outer != null) {
            newEnv.outer = copyEnv(newEnv.outer);
        }
        return newEnv;
    }

    WriteableScope copyScope(WriteableScope sc) {
        WriteableScope newScope = WriteableScope.create(sc.owner);
        JCList<Symbol> elemsList = JCList.nil();
        for (Symbol sym : sc.getSymbols()) {
            elemsList = elemsList.prepend(sym);
        }
        for (Symbol s : elemsList) {
            newScope.enter(s);
        }
        return newScope;
    }

    /** Derived visitor method: attribute an expression tree.
     */
    public Type attribExpr(JCTree tree, Env<AttrContext> env, Type pt) {
        return attribTree(tree, env, new ResultInfo(KindSelector.VAL, !pt.hasTag(ERROR) ? pt : Type.noType));
    }

    /** Derived visitor method: attribute an expression tree with
     *  no constraints on the computed type.
     */
    public Type attribExpr(JCTree tree, Env<AttrContext> env) {
        return attribTree(tree, env, unknownExprInfo);
    }

    /** Derived visitor method: attribute a type tree.
     */
    public Type attribType(JCTree tree, Env<AttrContext> env) {
        Type result = attribType(tree, env, Type.noType);
        return result;
    }

    /** Derived visitor method: attribute a type tree.
     */
    Type attribType(JCTree tree, Env<AttrContext> env, Type pt) {
        Type result = attribTree(tree, env, new ResultInfo(KindSelector.TYP, pt));
        return result;
    }

    /** Derived visitor method: attribute a statement or definition tree.
     */
    public Type attribStat(JCTree tree, Env<AttrContext> env) {
        Env<AttrContext> analyzeEnv =
                env.dup(tree, env.info.dup(env.info.scope.dupUnshared(env.info.scope.owner)));
        try {
            return attribTree(tree, env, statInfo);
        } finally {
            analyzer.analyzeIfNeeded(tree, analyzeEnv);
        }
    }

    /** Attribute a list of expressions, returning a list of types.
     */
    JCList<Type> attribExprs(JCList<JCExpression> trees, Env<AttrContext> env, Type pt) {
        ListBuffer<Type> ts = new ListBuffer<>();
        for (JCList<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            ts.append(attribExpr(l.head, env, pt));
        return ts.toList();
    }

    /** Attribute a list of statements, returning nothing.
     */
    <T extends JCTree> void attribStats(JCList<T> trees, Env<AttrContext> env) {
        for (JCList<T> l = trees; l.nonEmpty(); l = l.tail)
            attribStat(l.head, env);
    }

    /** Attribute the arguments in a method call, returning the method kind.
     */
    KindSelector attribArgs(KindSelector initialKind, JCList<JCExpression> trees, Env<AttrContext> env, ListBuffer<Type> argtypes) {
        KindSelector kind = initialKind;
        for (JCExpression arg : trees) {
            Type argtype = chk.checkNonVoid(arg, attribTree(arg, env, allowPoly ? methodAttrInfo : unknownExprInfo));
            if (argtype.hasTag(DEFERRED)) {
                kind = KindSelector.of(KindSelector.POLY, kind);
            }
            argtypes.append(argtype);
        }
        return kind;
    }

    /** Attribute a type argument list, returning a list of types.
     *  Caller is responsible for calling checkRefTypes.
     */
    JCList<Type> attribAnyTypes(JCList<JCExpression> trees, Env<AttrContext> env) {
        ListBuffer<Type> argtypes = new ListBuffer<>();
        for (JCList<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            argtypes.append(attribType(l.head, env));
        return argtypes.toList();
    }

    /** Attribute a type argument list, returning a list of types.
     *  Check that all the types are references.
     */
    JCList<Type> attribTypes(JCList<JCExpression> trees, Env<AttrContext> env) {
        JCList<Type> types = attribAnyTypes(trees, env);
        return chk.checkRefTypes(trees, types);
    }

    /**
     * Attribute type variables (of generic classes or methods).
     * Compound types are attributed later in attribBounds.
     * @param typarams the type variables to enter
     * @param env      the current environment
     */
    void attribTypeVariables(JCList<JCTypeParameter> typarams, Env<AttrContext> env) {
        for (JCTypeParameter tvar : typarams) {
            TypeVar a = (TypeVar)tvar.type;
            a.tsym.flags_field |= UNATTRIBUTED;
            a.bound = Type.noType;
            if (!tvar.bounds.isEmpty()) {
                JCList<Type> bounds = JCList.of(attribType(tvar.bounds.head, env));
                for (JCExpression bound : tvar.bounds.tail)
                    bounds = bounds.prepend(attribType(bound, env));
                types.setBounds(a, bounds.reverse());
            } else {
                // if no bounds are given, assume a single bound of
                // java.lang.Object.
                types.setBounds(a, JCList.of(syms.objectType));
            }
            a.tsym.flags_field &= ~UNATTRIBUTED;
        }
        for (JCTypeParameter tvar : typarams) {
            chk.checkNonCyclic(tvar.pos(), (TypeVar)tvar.type);
        }
    }

    /**
     * Attribute the type references in a list of annotations.
     */
    void attribAnnotationTypes(JCList<JCAnnotation> annotations,
                               Env<AttrContext> env) {
        for (JCList<JCAnnotation> al = annotations; al.nonEmpty(); al = al.tail) {
            JCAnnotation a = al.head;
            attribType(a.annotationType, env);
        }
    }

    /**
     * Attribute a "lazy constant value".
     *  @param env         The env for the const value
     *  @param variable    The initializer for the const value
     *  @param type        The expected type, or null
     *  @see VarSymbol#setLazyConstValue
     */
    public Object attribLazyConstantValue(Env<AttrContext> env,
                                      JCVariableDecl variable,
                                      Type type) {

        DiagnosticPosition prevLintPos
                = deferredLintHandler.setPos(variable.pos());

        final JavaFileObject prevSource = log.useSource(env.toplevel.sourcefile);
        try {
            Type itype = attribExpr(variable.init, env, type);
            if (itype.constValue() != null) {
                return coerce(itype, type).constValue();
            } else {
                return null;
            }
        } finally {
            log.useSource(prevSource);
            deferredLintHandler.setPos(prevLintPos);
        }
    }

    /** Attribute type reference in an `extends' or `implements' clause.
     *  Supertypes of anonymous inner classes are usually already attributed.
     *
     *  @param tree              The tree making up the type reference.
     *  @param env               The environment current at the reference.
     *  @param classExpected     true if only a class is expected here.
     *  @param interfaceExpected true if only an interface is expected here.
     */
    Type attribBase(JCTree tree,
                    Env<AttrContext> env,
                    boolean classExpected,
                    boolean interfaceExpected,
                    boolean checkExtensible) {
        Type t = tree.type != null ?
            tree.type :
            attribType(tree, env);
        return checkBase(t, tree, env, classExpected, interfaceExpected, checkExtensible);
    }
    Type checkBase(Type t,
                   JCTree tree,
                   Env<AttrContext> env,
                   boolean classExpected,
                   boolean interfaceExpected,
                   boolean checkExtensible) {
        final DiagnosticPosition pos = tree.hasTag(TYPEAPPLY) ?
                (((JCTypeApply) tree).clazz).pos() : tree.pos();
        if (t.tsym.isAnonymous()) {
            log.error(pos, "cant.inherit.from.anon");
            return types.createErrorType(t);
        }
        if (t.isErroneous())
            return t;
        if (t.hasTag(TYPEVAR) && !classExpected && !interfaceExpected) {
            // check that type variable is already visible
            if (t.getUpperBound() == null) {
                log.error(pos, "illegal.forward.ref");
                return types.createErrorType(t);
            }
        } else {
            t = chk.checkClassType(pos, t, checkExtensible);
        }
        if (interfaceExpected && (t.tsym.flags() & INTERFACE) == 0) {
            log.error(pos, "intf.expected.here");
            // return errType is necessary since otherwise there might
            // be undetected cycles which cause attribution to loop
            return types.createErrorType(t);
        } else if (checkExtensible &&
                   classExpected &&
                   (t.tsym.flags() & INTERFACE) != 0) {
            log.error(pos, "no.intf.expected.here");
            return types.createErrorType(t);
        }
        if (checkExtensible &&
            ((t.tsym.flags() & FINAL) != 0)) {
            log.error(pos,
                      "cant.inherit.from.final", t.tsym);
        }
        chk.checkNonCyclic(pos, t);
        return t;
    }

    Type attribIdentAsEnumType(Env<AttrContext> env, JCIdent id) {
        Assert.check((env.enclClass.sym.flags() & ENUM) != 0);
        id.type = env.info.scope.owner.enclClass().type;
        id.sym = env.info.scope.owner.enclClass();
        return id.type;
    }

    public void visitClassDef(JCClassDecl tree) {
        Optional<ArgumentAttr.LocalCacheContext> localCacheContext =
                Optional.ofNullable(env.info.isSpeculative ?
                        argumentAttr.withLocalCacheContext() : null);
        try {
            // Local and anonymous classes have not been entered yet, so we need to
            // do it now.
            if (env.info.scope.owner.kind.matches(KindSelector.VAL_MTH)) {
                enter.classEnter(tree, env);
            } else {
                // If this class declaration is part of a class level annotation,
                // as in @MyAnno(new Object() {}) class MyClass {}, enter it in
                // order to simplify later steps and allow for sensible error
                // messages.
                if (env.tree.hasTag(NEWCLASS) && TreeInfo.isInAnnotation(env, tree))
                    enter.classEnter(tree, env);
            }

            ClassSymbol c = tree.sym;
            if (c == null) {
                // exit in case something drastic went wrong during enter.
                result = null;
            } else {
                // make sure class has been completed:
                c.complete();

                // If this class appears as an anonymous class
                // in a superclass constructor call
                // disable implicit outer instance from being passed.
                // (This would be an illegal access to "this before super").
                if (env.info.isSelfCall &&
                        env.tree.hasTag(NEWCLASS)) {
                    c.flags_field |= NOOUTERTHIS;
                }
                attribClass(tree.pos(), c);
                result = tree.type = c.type;
            }
        } finally {
            localCacheContext.ifPresent(LocalCacheContext::leave);
        }
    }

    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol m = tree.sym;
        boolean isDefaultMethod = (m.flags() & DEFAULT) != 0;

        Lint lint = env.info.lint.augment(m);
        Lint prevLint = chk.setLint(lint);
        MethodSymbol prevMethod = chk.setMethod(m);
        try {
            deferredLintHandler.flush(tree.pos());
            chk.checkDeprecatedAnnotation(tree.pos(), m);


            // Create a new environment with local scope
            // for attributing the method.
            Env<AttrContext> localEnv = memberEnter.methodEnv(tree, env);
            localEnv.info.lint = lint;

            attribStats(tree.typarams, localEnv);

            // If we override any other methods, check that we do so properly.
            // JLS ???
            if (m.isStatic()) {
                chk.checkHideClashes(tree.pos(), env.enclClass.type, m);
            } else {
                chk.checkOverrideClashes(tree.pos(), env.enclClass.type, m);
            }
            chk.checkOverride(env, tree, m);

            if (isDefaultMethod && types.overridesObjectMethod(m.enclClass(), m)) {
                log.error(tree, "default.overrides.object.member", m.name, Kinds.kindName(m.location()), m.location());
            }

            // Enter all type parameters into the local method scope.
            for (JCList<JCTypeParameter> l = tree.typarams; l.nonEmpty(); l = l.tail)
                localEnv.info.scope.enterIfAbsent(l.head.type.tsym);

            ClassSymbol owner = env.enclClass.sym;
            if ((owner.flags() & ANNOTATION) != 0 &&
                    (tree.params.nonEmpty() ||
                    tree.recvparam != null))
                log.error(tree.params.nonEmpty() ?
                        tree.params.head.pos() :
                        tree.recvparam.pos(),
                        "intf.annotation.members.cant.have.params");

            // Attribute all value parameters.
            for (JCList<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                attribStat(l.head, localEnv);
            }

            chk.checkVarargsMethodDecl(localEnv, tree);

            // Check that type parameters are well-formed.
            chk.validate(tree.typarams, localEnv);

            // Check that result type is well-formed.
            if (tree.restype != null && !tree.restype.type.hasTag(VOID))
                chk.validate(tree.restype, localEnv);

            // Check that receiver type is well-formed.
            if (tree.recvparam != null) {
                // Use a new environment to check the receiver parameter.
                // Otherwise I get "might not have been initialized" errors.
                // Is there a better way?
                Env<AttrContext> newEnv = memberEnter.methodEnv(tree, env);
                attribType(tree.recvparam, newEnv);
                chk.validate(tree.recvparam, newEnv);
            }

            // annotation method checks
            if ((owner.flags() & ANNOTATION) != 0) {
                // annotation method cannot have throws clause
                if (tree.thrown.nonEmpty()) {
                    log.error(tree.thrown.head.pos(),
                            "throws.not.allowed.in.intf.annotation");
                }
                // annotation method cannot declare type-parameters
                if (tree.typarams.nonEmpty()) {
                    log.error(tree.typarams.head.pos(),
                            "intf.annotation.members.cant.have.type.params");
                }
                // validate annotation method's return type (could be an annotation type)
                chk.validateAnnotationType(tree.restype);
                // ensure that annotation method does not clash with members of Object/Annotation
                chk.validateAnnotationMethod(tree.pos(), m);
            }

            for (JCList<JCExpression> l = tree.thrown; l.nonEmpty(); l = l.tail)
                chk.checkType(l.head.pos(), l.head.type, syms.throwableType);

            if (tree.body == null) {
                // Empty bodies are only allowed for
                // abstract, native, or interface methods, or for methods
                // in a retrofit signature class.
                if (tree.defaultValue != null) {
                    if ((owner.flags() & ANNOTATION) == 0)
                        log.error(tree.pos(),
                                  "default.allowed.in.intf.annotation.member");
                }
                if (isDefaultMethod || (tree.sym.flags() & (ABSTRACT | NATIVE)) == 0)
                    log.error(tree.pos(), "missing.meth.body.or.decl.abstract");
            } else if ((tree.sym.flags() & (ABSTRACT|DEFAULT|PRIVATE)) == ABSTRACT) {
                if ((owner.flags() & INTERFACE) != 0) {
                    log.error(tree.body.pos(), "intf.meth.cant.have.body");
                } else {
                    log.error(tree.pos(), "abstract.meth.cant.have.body");
                }
            } else if ((tree.mods.flags & NATIVE) != 0) {
                log.error(tree.pos(), "native.meth.cant.have.body");
            } else {
                // Add an implicit super() call unless an explicit call to
                // super(...) or this(...) is given
                // or we are compiling class java.lang.Object.
                if (tree.name == names.init && owner.type != syms.objectType) {
                    JCBlock body = tree.body;
                    if (body.stats.isEmpty() ||
                            !TreeInfo.isSelfCall(body.stats.head)) {
                        body.stats = body.stats.
                                prepend(typeEnter.SuperCall(make.at(body.pos),
                                        JCList.nil(),
                                        JCList.nil(),
                                        false));
                    } else if ((env.enclClass.sym.flags() & ENUM) != 0 &&
                            (tree.mods.flags & GENERATEDCONSTR) == 0 &&
                            TreeInfo.isSuperCall(body.stats.head)) {
                        // enum constructors are not allowed to call super
                        // directly, so make sure there aren't any super calls
                        // in enum constructors, except in the compiler
                        // generated one.
                        log.error(tree.body.stats.head.pos(),
                                "call.to.super.not.allowed.in.enum.ctor",
                                env.enclClass.sym);
                    }
                }

                // Attribute all type annotations in the body
                annotate.queueScanTreeAndTypeAnnotate(tree.body, localEnv, m, null);
                annotate.flush();

                // Attribute method body.
                attribStat(tree.body, localEnv);
            }

            localEnv.info.scope.leave();
            result = tree.type = m.type;
        } finally {
            chk.setLint(prevLint);
            chk.setMethod(prevMethod);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        // Local variables have not been entered yet, so we need to do it now:
        if (env.info.scope.owner.kind == MTH) {
            if (tree.sym != null) {
                // parameters have already been entered
                env.info.scope.enter(tree.sym);
            } else {
                try {
                    annotate.blockAnnotations();
                    memberEnter.memberEnter(tree, env);
                } finally {
                    annotate.unblockAnnotations();
                }
            }
        } else {
            if (tree.init != null) {
                // Field initializer expression need to be entered.
                annotate.queueScanTreeAndTypeAnnotate(tree.init, env, tree.sym, tree.pos());
                annotate.flush();
            }
        }

        VarSymbol v = tree.sym;
        Lint lint = env.info.lint.augment(v);
        Lint prevLint = chk.setLint(lint);

        // Check that the variable's declared type is well-formed.
        boolean isImplicitLambdaParameter = env.tree.hasTag(LAMBDA) &&
                ((JCLambda)env.tree).paramKind == JCLambda.ParameterKind.IMPLICIT &&
                (tree.sym.flags() & PARAMETER) != 0;
        chk.validate(tree.vartype, env, !isImplicitLambdaParameter);

        try {
            v.getConstValue(); // ensure compile-time constant initializer is evaluated
            deferredLintHandler.flush(tree.pos());
            chk.checkDeprecatedAnnotation(tree.pos(), v);

            if (tree.init != null) {
                if ((v.flags_field & FINAL) == 0 ||
                    !memberEnter.needsLazyConstValue(tree.init)) {
                    // Not a compile-time constant
                    // Attribute initializer in a new environment
                    // with the declared variable as owner.
                    // Check that initializer conforms to variable's declared type.
                    Env<AttrContext> initEnv = memberEnter.initEnv(tree, env);
                    initEnv.info.lint = lint;
                    // In order to catch self-references, we set the variable's
                    // declaration position to maximal possible value, effectively
                    // marking the variable as undefined.
                    initEnv.info.enclVar = v;
                    attribExpr(tree.init, initEnv, v.type);
                }
            }
            result = tree.type = v.type;
        }
        finally {
            chk.setLint(prevLint);
        }
    }

    public void visitSkip(JCSkip tree) {
        result = null;
    }

    public void visitBlock(JCBlock tree) {
        if (env.info.scope.owner.kind == TYP) {
            // Block is a static or instance initializer;
            // let the owner of the environment be a freshly
            // created BLOCK-method.
            Symbol fakeOwner =
                new MethodSymbol(tree.flags | BLOCK |
                    env.info.scope.owner.flags() & STRICTFP, names.empty, null,
                    env.info.scope.owner);
            final Env<AttrContext> localEnv =
                env.dup(tree, env.info.dup(env.info.scope.dupUnshared(fakeOwner)));

            if ((tree.flags & STATIC) != 0) localEnv.info.staticLevel++;
            // Attribute all type annotations in the block
            annotate.queueScanTreeAndTypeAnnotate(tree, localEnv, localEnv.info.scope.owner, null);
            annotate.flush();
            attribStats(tree.stats, localEnv);

            {
                // Store init and clinit type annotations with the ClassSymbol
                // to allow output in Gen.normalizeDefs.
                ClassSymbol cs = (ClassSymbol)env.info.scope.owner;
                JCList<Attribute.TypeCompound> tas = localEnv.info.scope.owner.getRawTypeAttributes();
                if ((tree.flags & STATIC) != 0) {
                    cs.appendClassInitTypeAttributes(tas);
                } else {
                    cs.appendInitTypeAttributes(tas);
                }
            }
        } else {
            // Create a new local environment with a local scope.
            Env<AttrContext> localEnv =
                env.dup(tree, env.info.dup(env.info.scope.dup()));
            try {
                attribStats(tree.stats, localEnv);
            } finally {
                localEnv.info.scope.leave();
            }
        }
        result = null;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        attribStat(tree.body, env.dup(tree));
        attribExpr(tree.cond, env, syms.booleanType);
        result = null;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitForLoop(JCForLoop tree) {
        Env<AttrContext> loopEnv =
            env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        try {
            attribStats(tree.init, loopEnv);
            if (tree.cond != null) attribExpr(tree.cond, loopEnv, syms.booleanType);
            loopEnv.tree = tree; // before, we were not in loop!
            attribStats(tree.step, loopEnv);
            attribStat(tree.body, loopEnv);
            result = null;
        }
        finally {
            loopEnv.info.scope.leave();
        }
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        Env<AttrContext> loopEnv =
            env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        try {
            //the Formal Parameter of a for-each loop is not in the scope when
            //attributing the for-each expression; we mimick this by attributing
            //the for-each expression first (against original scope).
            Type exprType = types.cvarUpperBound(attribExpr(tree.expr, loopEnv));
            attribStat(tree.var, loopEnv);
            chk.checkNonVoid(tree.pos(), exprType);
            Type elemtype = types.elemtype(exprType); // perhaps expr is an array?
            if (elemtype == null) {
                // or perhaps expr implements Iterable<T>?
                Type base = types.asSuper(exprType, syms.iterableType.tsym);
                if (base == null) {
                    log.error(tree.expr.pos(),
                            "foreach.not.applicable.to.type",
                            exprType,
                            diags.fragment("type.req.array.or.iterable"));
                    elemtype = types.createErrorType(exprType);
                } else {
                    JCList<Type> iterableParams = base.allparams();
                    elemtype = iterableParams.isEmpty()
                        ? syms.objectType
                        : types.wildUpperBound(iterableParams.head);
                }
            }
            chk.checkType(tree.expr.pos(), elemtype, tree.var.sym.type);
            loopEnv.tree = tree; // before, we were not in loop!
            attribStat(tree.body, loopEnv);
            result = null;
        }
        finally {
            loopEnv.info.scope.leave();
        }
    }

    public void visitLabelled(JCLabeledStatement tree) {
        // Check that label is not used in an enclosing statement
        Env<AttrContext> env1 = env;
        while (env1 != null && !env1.tree.hasTag(CLASSDEF)) {
            if (env1.tree.hasTag(LABELLED) &&
                ((JCLabeledStatement) env1.tree).label == tree.label) {
                log.error(tree.pos(), "label.already.in.use",
                          tree.label);
                break;
            }
            env1 = env1.next;
        }

        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitSwitch(JCSwitch tree) {
        Type seltype = attribExpr(tree.selector, env);

        Env<AttrContext> switchEnv =
            env.dup(tree, env.info.dup(env.info.scope.dup()));

        try {

            boolean enumSwitch = (seltype.tsym.flags() & Flags.ENUM) != 0;
            boolean stringSwitch = types.isSameType(seltype, syms.stringType);
            if (stringSwitch && !allowStringsInSwitch) {
                log.error(DiagnosticFlag.SOURCE_LEVEL, tree.selector.pos(), "string.switch.not.supported.in.source", sourceName);
            }
            if (!enumSwitch && !stringSwitch)
                seltype = chk.checkType(tree.selector.pos(), seltype, syms.intType);

            // Attribute all cases and
            // check that there are no duplicate case labels or default clauses.
            Set<Object> labels = new HashSet<>(); // The set of case labels.
            boolean hasDefault = false;      // Is there a default label?
            for (JCList<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                JCCase c = l.head;
                if (c.pat != null) {
                    if (enumSwitch) {
                        Symbol sym = enumConstant(c.pat, seltype);
                        if (sym == null) {
                            log.error(c.pat.pos(), "enum.label.must.be.unqualified.enum");
                        } else if (!labels.add(sym)) {
                            log.error(c.pos(), "duplicate.case.label");
                        }
                    } else {
                        Type pattype = attribExpr(c.pat, switchEnv, seltype);
                        if (!pattype.hasTag(ERROR)) {
                            if (pattype.constValue() == null) {
                                log.error(c.pat.pos(),
                                          (stringSwitch ? "string.const.req" : "const.expr.req"));
                            } else if (!labels.add(pattype.constValue())) {
                                log.error(c.pos(), "duplicate.case.label");
                            }
                        }
                    }
                } else if (hasDefault) {
                    log.error(c.pos(), "duplicate.default.label");
                } else {
                    hasDefault = true;
                }
                Env<AttrContext> caseEnv =
                    switchEnv.dup(c, env.info.dup(switchEnv.info.scope.dup()));
                try {
                    attribStats(c.stats, caseEnv);
                } finally {
                    caseEnv.info.scope.leave();
                    addVars(c.stats, switchEnv.info.scope);
                }
            }

            result = null;
        }
        finally {
            switchEnv.info.scope.leave();
        }
    }
    // where
        /** Add any variables defined in stats to the switch scope. */
        private static void addVars(JCList<JCStatement> stats, WriteableScope switchScope) {
            for (;stats.nonEmpty(); stats = stats.tail) {
                JCTree stat = stats.head;
                if (stat.hasTag(VARDEF))
                    switchScope.enter(((JCVariableDecl) stat).sym);
            }
        }
    // where
    /** Return the selected enumeration constant symbol, or null. */
    private Symbol enumConstant(JCTree tree, Type enumType) {
        if (tree.hasTag(IDENT)) {
            JCIdent ident = (JCIdent)tree;
            Name name = ident.name;
            for (Symbol sym : enumType.tsym.members().getSymbolsByName(name)) {
                if (sym.kind == VAR) {
                    Symbol s = ident.sym = sym;
                    ((VarSymbol)s).getConstValue(); // ensure initializer is evaluated
                    ident.type = s.type;
                    return ((s.flags_field & Flags.ENUM) == 0)
                        ? null : s;
                }
            }
        }
        return null;
    }

    public void visitSynchronized(JCSynchronized tree) {
        chk.checkRefType(tree.pos(), attribExpr(tree.lock, env));
        attribStat(tree.body, env);
        result = null;
    }

    public void visitTry(JCTry tree) {
        // Create a new local environment with a local
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup(env.info.scope.dup()));
        try {
            boolean isTryWithResource = tree.resources.nonEmpty();
            // Create a nested environment for attributing the try block if needed
            Env<AttrContext> tryEnv = isTryWithResource ?
                env.dup(tree, localEnv.info.dup(localEnv.info.scope.dup())) :
                localEnv;
            try {
                // Attribute resource declarations
                for (JCTree resource : tree.resources) {
                    CheckContext twrContext = new Check.NestedCheckContext(resultInfo.checkContext) {
                        @Override
                        public void report(DiagnosticPosition pos, JCDiagnostic details) {
                            chk.basicHandler.report(pos, diags.fragment("try.not.applicable.to.type", details));
                        }
                    };
                    ResultInfo twrResult =
                        new ResultInfo(KindSelector.VAR,
                                       syms.autoCloseableType,
                                       twrContext);
                    if (resource.hasTag(VARDEF)) {
                        attribStat(resource, tryEnv);
                        twrResult.check(resource, resource.type);

                        //check that resource type cannot throw InterruptedException
                        checkAutoCloseable(resource.pos(), localEnv, resource.type);

                        VarSymbol var = ((JCVariableDecl) resource).sym;
                        var.setData(ElementKind.RESOURCE_VARIABLE);
                    } else {
                        attribTree(resource, tryEnv, twrResult);
                    }
                }
                // Attribute body
                attribStat(tree.body, tryEnv);
            } finally {
                if (isTryWithResource)
                    tryEnv.info.scope.leave();
            }

            // Attribute catch clauses
            for (JCList<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCCatch c = l.head;
                Env<AttrContext> catchEnv =
                    localEnv.dup(c, localEnv.info.dup(localEnv.info.scope.dup()));
                try {
                    Type ctype = attribStat(c.param, catchEnv);
                    if (TreeInfo.isMultiCatch(c)) {
                        //multi-catch parameter is implicitly marked as final
                        c.param.sym.flags_field |= FINAL | UNION;
                    }
                    if (c.param.sym.kind == VAR) {
                        c.param.sym.setData(ElementKind.EXCEPTION_PARAMETER);
                    }
                    chk.checkType(c.param.vartype.pos(),
                                  chk.checkClassType(c.param.vartype.pos(), ctype),
                                  syms.throwableType);
                    attribStat(c.body, catchEnv);
                } finally {
                    catchEnv.info.scope.leave();
                }
            }

            // Attribute finalizer
            if (tree.finalizer != null) attribStat(tree.finalizer, localEnv);
            result = null;
        }
        finally {
            localEnv.info.scope.leave();
        }
    }

    void checkAutoCloseable(DiagnosticPosition pos, Env<AttrContext> env, Type resource) {
        if (!resource.isErroneous() &&
            types.asSuper(resource, syms.autoCloseableType.tsym) != null &&
            !types.isSameType(resource, syms.autoCloseableType)) { // Don't emit warning for AutoCloseable itself
            Symbol close = syms.noSymbol;
            Log.DiagnosticHandler discardHandler = new Log.DiscardDiagnosticHandler(log);
            try {
                close = rs.resolveQualifiedMethod(pos,
                        env,
                        types.skipTypeVars(resource, false),
                        names.close,
                        JCList.nil(),
                        JCList.nil());
            }
            finally {
                log.popDiagnosticHandler(discardHandler);
            }
            if (close.kind == MTH &&
                    close.overrides(syms.autoCloseableClose, resource.tsym, types, true) &&
                    chk.isHandled(syms.interruptedExceptionType, types.memberType(resource, close).getThrownTypes()) &&
                    env.info.lint.isEnabled(LintCategory.TRY)) {
                log.warning(LintCategory.TRY, pos, "try.resource.throws.interrupted.exc", resource);
            }
        }
    }

    public void visitConditional(JCConditional tree) {
        Type condtype = attribExpr(tree.cond, env, syms.booleanType);

        tree.polyKind = (!allowPoly ||
                pt().hasTag(NONE) && pt() != Type.recoveryType && pt() != Infer.anyPoly ||
                isBooleanOrNumeric(env, tree)) ?
                PolyKind.STANDALONE : PolyKind.POLY;

        if (tree.polyKind == PolyKind.POLY && resultInfo.pt.hasTag(VOID)) {
            //this means we are returning a poly conditional from void-compatible lambda expression
            resultInfo.checkContext.report(tree, diags.fragment("conditional.target.cant.be.void"));
            result = tree.type = types.createErrorType(resultInfo.pt);
            return;
        }

        ResultInfo condInfo = tree.polyKind == PolyKind.STANDALONE ?
                unknownExprInfo :
                resultInfo.dup(conditionalContext(resultInfo.checkContext));

        Type truetype = attribTree(tree.truepart, env, condInfo);
        Type falsetype = attribTree(tree.falsepart, env, condInfo);

        Type owntype = (tree.polyKind == PolyKind.STANDALONE) ? condType(tree, truetype, falsetype) : pt();
        if (condtype.constValue() != null &&
                truetype.constValue() != null &&
                falsetype.constValue() != null &&
                !owntype.hasTag(NONE)) {
            //constant folding
            owntype = cfolder.coerce(condtype.isTrue() ? truetype : falsetype, owntype);
        }
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
    }
    //where
        private boolean isBooleanOrNumeric(Env<AttrContext> env, JCExpression tree) {
            switch (tree.getTag()) {
                case LITERAL: return ((JCLiteral)tree).typetag.isSubRangeOf(DOUBLE) ||
                              ((JCLiteral)tree).typetag == BOOLEAN ||
                              ((JCLiteral)tree).typetag == BOT;
                case LAMBDA: case REFERENCE: return false;
                case PARENS: return isBooleanOrNumeric(env, ((JCParens)tree).expr);
                case CONDEXPR:
                    JCConditional condTree = (JCConditional)tree;
                    return isBooleanOrNumeric(env, condTree.truepart) &&
                            isBooleanOrNumeric(env, condTree.falsepart);
                case APPLY:
                    JCMethodInvocation speculativeMethodTree =
                            (JCMethodInvocation)deferredAttr.attribSpeculative(
                                    tree, env, unknownExprInfo,
                                    argumentAttr.withLocalCacheContext());
                    Symbol msym = TreeInfo.symbol(speculativeMethodTree.meth);
                    Type receiverType = speculativeMethodTree.meth.hasTag(IDENT) ?
                            env.enclClass.type :
                            ((JCFieldAccess)speculativeMethodTree.meth).selected.type;
                    Type owntype = types.memberType(receiverType, msym).getReturnType();
                    return primitiveOrBoxed(owntype);
                case NEWCLASS:
                    JCExpression className =
                            removeClassParams.translate(((JCNewClass)tree).clazz);
                    JCExpression speculativeNewClassTree =
                            (JCExpression)deferredAttr.attribSpeculative(
                                    className, env, unknownTypeInfo,
                                    argumentAttr.withLocalCacheContext());
                    return primitiveOrBoxed(speculativeNewClassTree.type);
                default:
                    Type speculativeType = deferredAttr.attribSpeculative(tree, env, unknownExprInfo,
                            argumentAttr.withLocalCacheContext()).type;
                    return primitiveOrBoxed(speculativeType);
            }
        }
        //where
            boolean primitiveOrBoxed(Type t) {
                return (!t.hasTag(TYPEVAR) && types.unboxedTypeOrType(t).isPrimitive());
            }

            TreeTranslator removeClassParams = new TreeTranslator() {
                @Override
                public void visitTypeApply(JCTypeApply tree) {
                    result = translate(tree.clazz);
                }
            };

        CheckContext conditionalContext(CheckContext checkContext) {
            return new Check.NestedCheckContext(checkContext) {
                //this will use enclosing check context to check compatibility of
                //subexpression against target type; if we are in a method check context,
                //depending on whether boxing is allowed, we could have incompatibilities
                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    enclosingContext.report(pos, diags.fragment("incompatible.type.in.conditional", details));
                }
            };
        }

        /** Compute the type of a conditional expression, after
         *  checking that it exists.  See JLS 15.25. Does not take into
         *  account the special case where condition and both arms
         *  are constants.
         *
         *  @param pos      The source position to be used for error
         *                  diagnostics.
         *  @param thentype The type of the expression's then-part.
         *  @param elsetype The type of the expression's else-part.
         */
        Type condType(DiagnosticPosition pos,
                               Type thentype, Type elsetype) {
            // If same type, that is the result
            if (types.isSameType(thentype, elsetype))
                return thentype.baseType();

            Type thenUnboxed = (thentype.isPrimitive())
                ? thentype : types.unboxedType(thentype);
            Type elseUnboxed = (elsetype.isPrimitive())
                ? elsetype : types.unboxedType(elsetype);

            // Otherwise, if both arms can be converted to a numeric
            // type, return the least numeric type that fits both arms
            // (i.e. return larger of the two, or return int if one
            // arm is short, the other is char).
            if (thenUnboxed.isPrimitive() && elseUnboxed.isPrimitive()) {
                // If one arm has an integer subrange type (i.e., byte,
                // short, or char), and the other is an integer constant
                // that fits into the subrange, return the subrange type.
                if (thenUnboxed.getTag().isStrictSubRangeOf(INT) &&
                    elseUnboxed.hasTag(INT) &&
                    types.isAssignable(elseUnboxed, thenUnboxed)) {
                    return thenUnboxed.baseType();
                }
                if (elseUnboxed.getTag().isStrictSubRangeOf(INT) &&
                    thenUnboxed.hasTag(INT) &&
                    types.isAssignable(thenUnboxed, elseUnboxed)) {
                    return elseUnboxed.baseType();
                }

                for (TypeTag tag : primitiveTags) {
                    Type candidate = syms.typeOfTag[tag.ordinal()];
                    if (types.isSubtype(thenUnboxed, candidate) &&
                        types.isSubtype(elseUnboxed, candidate)) {
                        return candidate;
                    }
                }
            }

            // Those were all the cases that could result in a primitive
            if (thentype.isPrimitive())
                thentype = types.boxedClass(thentype).type;
            if (elsetype.isPrimitive())
                elsetype = types.boxedClass(elsetype).type;

            if (types.isSubtype(thentype, elsetype))
                return elsetype.baseType();
            if (types.isSubtype(elsetype, thentype))
                return thentype.baseType();

            if (thentype.hasTag(VOID) || elsetype.hasTag(VOID)) {
                log.error(pos, "neither.conditional.subtype",
                          thentype, elsetype);
                return thentype.baseType();
            }

            // both are known to be reference types.  The result is
            // lub(thentype,elsetype). This cannot fail, as it will
            // always be possible to infer "Object" if nothing better.
            return types.lub(thentype.baseType(), elsetype.baseType());
        }

    final static TypeTag[] primitiveTags = new TypeTag[]{
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
    };

    public void visitIf(JCIf tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.thenpart, env);
        if (tree.elsepart != null)
            attribStat(tree.elsepart, env);
        chk.checkEmptyIf(tree);
        result = null;
    }

    public void visitExec(JCExpressionStatement tree) {
        //a fresh environment is required for 292 inference to work properly ---
        //see Infer.instantiatePolymorphicSignatureInstance()
        Env<AttrContext> localEnv = env.dup(tree);
        attribExpr(tree.expr, localEnv);
        result = null;
    }

    public void visitBreak(JCBreak tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }

    public void visitContinue(JCContinue tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }
    //where
        /** Return the target of a break or continue statement, if it exists,
         *  report an error if not.
         *  Note: The target of a labelled break or continue is the
         *  (non-labelled) statement tree referred to by the label,
         *  not the tree representing the labelled statement itself.
         *
         *  @param pos     The position to be used for error diagnostics
         *  @param tag     The tag of the jump statement. This is either
         *                 Tree.BREAK or Tree.CONTINUE.
         *  @param label   The label of the jump statement, or null if no
         *                 label is given.
         *  @param env     The environment current at the jump statement.
         */
        private JCTree findJumpTarget(DiagnosticPosition pos,
                                    JCTree.JCTreeTag tag,
                                    Name label,
                                    Env<AttrContext> env) {
            // Search environments outwards from the point of jump.
            Env<AttrContext> env1 = env;
            LOOP:
            while (env1 != null) {
                switch (env1.tree.getTag()) {
                    case LABELLED:
                        JCLabeledStatement labelled = (JCLabeledStatement)env1.tree;
                        if (label == labelled.label) {
                            // If jump is a continue, check that target is a loop.
                            if (tag == CONTINUE) {
                                if (!labelled.body.hasTag(DOLOOP) &&
                                        !labelled.body.hasTag(WHILELOOP) &&
                                        !labelled.body.hasTag(FORLOOP) &&
                                        !labelled.body.hasTag(FOREACHLOOP))
                                    log.error(pos, "not.loop.label", label);
                                // Found labelled statement target, now go inwards
                                // to next non-labelled tree.
                                return TreeInfo.referencedStatement(labelled);
                            } else {
                                return labelled;
                            }
                        }
                        break;
                    case DOLOOP:
                    case WHILELOOP:
                    case FORLOOP:
                    case FOREACHLOOP:
                        if (label == null) return env1.tree;
                        break;
                    case SWITCH:
                        if (label == null && tag == BREAK) return env1.tree;
                        break;
                    case LAMBDA:
                    case METHODDEF:
                    case CLASSDEF:
                        break LOOP;
                    default:
                }
                env1 = env1.next;
            }
            if (label != null)
                log.error(pos, "undef.label", label);
            else if (tag == CONTINUE)
                log.error(pos, "cont.outside.loop");
            else
                log.error(pos, "break.outside.switch.loop");
            return null;
        }

    public void visitReturn(JCReturn tree) {
        // Check that there is an enclosing method which is
        // nested within than the enclosing class.
        if (env.info.returnResult == null) {
            log.error(tree.pos(), "ret.outside.meth");
        } else {
            // Attribute return expression, if it exists, and check that
            // it conforms to result type of enclosing method.
            if (tree.expr != null) {
                if (env.info.returnResult.pt.hasTag(VOID)) {
                    env.info.returnResult.checkContext.report(tree.expr.pos(),
                              diags.fragment("unexpected.ret.val"));
                }
                attribTree(tree.expr, env, env.info.returnResult);
            } else if (!env.info.returnResult.pt.hasTag(VOID) &&
                    !env.info.returnResult.pt.hasTag(NONE)) {
                env.info.returnResult.checkContext.report(tree.pos(),
                              diags.fragment("missing.ret.val"));
            }
        }
        result = null;
    }

    public void visitThrow(JCThrow tree) {
        Type owntype = attribExpr(tree.expr, env, allowPoly ? Type.noType : syms.throwableType);
        if (allowPoly) {
            chk.checkType(tree, owntype, syms.throwableType);
        }
        result = null;
    }

    public void visitAssert(JCAssert tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        if (tree.detail != null) {
            chk.checkNonVoid(tree.detail.pos(), attribExpr(tree.detail, env));
        }
        result = null;
    }

     /** Visitor method for method invocations.
     *  NOTE: The method part of an application will have in its type field
     *        the return type of the method, not the method's type itself!
     */
    public void visitApply(JCMethodInvocation tree) {
        // The local environment of a method application is
        // a new environment nested in the current one.
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());

        // The types of the actual method arguments.
        JCList<Type> argtypes;

        // The types of the actual method type arguments.
        JCList<Type> typeargtypes = null;

        Name methName = TreeInfo.name(tree.meth);

        boolean isConstructorCall =
            methName == names._this || methName == names._super;

        ListBuffer<Type> argtypesBuf = new ListBuffer<>();
        if (isConstructorCall) {
            // We are seeing a ...this(...) or ...super(...) call.
            // Check that this is the first statement in a constructor.
            if (checkFirstConstructorStat(tree, env)) {

                // Record the fact
                // that this is a constructor call (using isSelfCall).
                localEnv.info.isSelfCall = true;

                // Attribute arguments, yielding list of argument types.
                KindSelector kind = attribArgs(KindSelector.MTH, tree.args, localEnv, argtypesBuf);
                argtypes = argtypesBuf.toList();
                typeargtypes = attribTypes(tree.typeargs, localEnv);

                // Variable `site' points to the class in which the called
                // constructor is defined.
                Type site = env.enclClass.sym.type;
                if (methName == names._super) {
                    if (site == syms.objectType) {
                        log.error(tree.meth.pos(), "no.superclass", site);
                        site = types.createErrorType(syms.objectType);
                    } else {
                        site = types.supertype(site);
                    }
                }

                if (site.hasTag(CLASS)) {
                    Type encl = site.getEnclosingType();
                    while (encl != null && encl.hasTag(TYPEVAR))
                        encl = encl.getUpperBound();
                    if (encl.hasTag(CLASS)) {
                        // we are calling a nested class

                        if (tree.meth.hasTag(SELECT)) {
                            JCTree qualifier = ((JCFieldAccess) tree.meth).selected;

                            // We are seeing a prefixed call, of the form
                            //     <expr>.super(...).
                            // Check that the prefix expression conforms
                            // to the outer instance type of the class.
                            chk.checkRefType(qualifier.pos(),
                                             attribExpr(qualifier, localEnv,
                                                        encl));
                        } else if (methName == names._super) {
                            // qualifier omitted; check for existence
                            // of an appropriate implicit qualifier.
                            rs.resolveImplicitThis(tree.meth.pos(),
                                                   localEnv, site, true);
                        }
                    } else if (tree.meth.hasTag(SELECT)) {
                        log.error(tree.meth.pos(), "illegal.qual.not.icls",
                                  site.tsym);
                    }

                    // if we're calling a java.lang.Enum constructor,
                    // prefix the implicit String and int parameters
                    if (site.tsym == syms.enumSym)
                        argtypes = argtypes.prepend(syms.intType).prepend(syms.stringType);

                    // Resolve the called constructor under the assumption
                    // that we are referring to a superclass instance of the
                    // current instance (JLS ???).
                    boolean selectSuperPrev = localEnv.info.selectSuper;
                    localEnv.info.selectSuper = true;
                    localEnv.info.pendingResolutionPhase = null;
                    Symbol sym = rs.resolveConstructor(
                        tree.meth.pos(), localEnv, site, argtypes, typeargtypes);
                    localEnv.info.selectSuper = selectSuperPrev;

                    // Set method symbol to resolved constructor...
                    TreeInfo.setSymbol(tree.meth, sym);

                    // ...and check that it is legal in the current context.
                    // (this will also set the tree's type)
                    Type mpt = newMethodTemplate(resultInfo.pt, argtypes, typeargtypes);
                    checkId(tree.meth, site, sym, localEnv,
                            new ResultInfo(kind, mpt));
                }
                // Otherwise, `site' is an error type and we do nothing
            }
            result = tree.type = syms.voidType;
        } else {
            // Otherwise, we are seeing a regular method call.
            // Attribute the arguments, yielding list of argument types, ...
            KindSelector kind = attribArgs(KindSelector.VAL, tree.args, localEnv, argtypesBuf);
            argtypes = argtypesBuf.toList();
            typeargtypes = attribAnyTypes(tree.typeargs, localEnv);

            // ... and attribute the method using as a prototype a methodtype
            // whose formal argument types is exactly the list of actual
            // arguments (this will also set the method symbol).
            Type mpt = newMethodTemplate(resultInfo.pt, argtypes, typeargtypes);
            localEnv.info.pendingResolutionPhase = null;
            Type mtype = attribTree(tree.meth, localEnv, new ResultInfo(kind, mpt, resultInfo.checkContext));

            // Compute the result type.
            Type restype = mtype.getReturnType();
            if (restype.hasTag(WILDCARD))
                throw new AssertionError(mtype);

            Type qualifier = (tree.meth.hasTag(SELECT))
                    ? ((JCFieldAccess) tree.meth).selected.type
                    : env.enclClass.sym.type;
            Symbol msym = TreeInfo.symbol(tree.meth);
            restype = adjustMethodReturnType(msym, qualifier, methName, argtypes, restype);

            chk.checkRefTypes(tree.typeargs, typeargtypes);

            // Check that value of resulting type is admissible in the
            // current context.  Also, capture the return type
            Type capturedRes = resultInfo.checkContext.inferenceContext().cachedCapture(tree, restype, true);
            result = check(tree, capturedRes, KindSelector.VAL, resultInfo);
        }
        chk.validate(tree.typeargs, localEnv);
    }
    //where
        Type adjustMethodReturnType(Symbol msym, Type qualifierType, Name methodName, JCList<Type> argtypes, Type restype) {
            if (msym != null &&
                    msym.owner == syms.objectType.tsym &&
                    methodName == names.getClass &&
                    argtypes.isEmpty()) {
                // as a special case, x.getClass() has type Class<? extends |X|>
                return new ClassType(restype.getEnclosingType(),
                        JCList.of(new WildcardType(types.erasure(qualifierType),
                                BoundKind.EXTENDS,
                                syms.boundClass)),
                        restype.tsym,
                        restype.getMetadata());
            } else if (msym != null &&
                    msym.owner == syms.arrayClass &&
                    methodName == names.clone &&
                    types.isArray(qualifierType)) {
                // as a special case, array.clone() has a result that is
                // the same as static type of the array being cloned
                return qualifierType;
            } else {
                return restype;
            }
        }

        /** Check that given application node appears as first statement
         *  in a constructor call.
         *  @param tree   The application node
         *  @param env    The environment current at the application.
         */
        boolean checkFirstConstructorStat(JCMethodInvocation tree, Env<AttrContext> env) {
            JCMethodDecl enclMethod = env.enclMethod;
            if (enclMethod != null && enclMethod.name == names.init) {
                JCBlock body = enclMethod.body;
                if (body.stats.head.hasTag(EXEC) &&
                    ((JCExpressionStatement) body.stats.head).expr == tree)
                    return true;
            }
            log.error(tree.pos(),"call.must.be.first.stmt.in.ctor",
                      TreeInfo.name(tree.meth));
            return false;
        }

        /** Obtain a method type with given argument types.
         */
        Type newMethodTemplate(Type restype, JCList<Type> argtypes, JCList<Type> typeargtypes) {
            MethodType mt = new MethodType(argtypes, restype, JCList.nil(), syms.methodClass);
            return (typeargtypes == null) ? mt : (Type)new ForAll(typeargtypes, mt);
        }

    public void visitNewClass(final JCNewClass tree) {
        Type owntype = types.createErrorType(tree.type);

        // The local environment of a class creation is
        // a new environment nested in the current one.
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());

        // The anonymous inner class definition of the new expression,
        // if one is defined by it.
        JCClassDecl cdef = tree.def;

        // If enclosing class is given, attribute it, and
        // complete class name to be fully qualified
        JCExpression clazz = tree.clazz; // Class field following new
        JCExpression clazzid;            // Identifier in class field
        JCAnnotatedType annoclazzid;     // Annotated type enclosing clazzid
        annoclazzid = null;

        if (clazz.hasTag(TYPEAPPLY)) {
            clazzid = ((JCTypeApply) clazz).clazz;
            if (clazzid.hasTag(ANNOTATED_TYPE)) {
                annoclazzid = (JCAnnotatedType) clazzid;
                clazzid = annoclazzid.underlyingType;
            }
        } else {
            if (clazz.hasTag(ANNOTATED_TYPE)) {
                annoclazzid = (JCAnnotatedType) clazz;
                clazzid = annoclazzid.underlyingType;
            } else {
                clazzid = clazz;
            }
        }

        JCExpression clazzid1 = clazzid; // The same in fully qualified form

        if (tree.encl != null) {
            // We are seeing a qualified new, of the form
            //    <expr>.new C <...> (...) ...
            // In this case, we let clazz stand for the name of the
            // allocated class C prefixed with the type of the qualifier
            // expression, so that we can
            // resolve it with standard techniques later. I.e., if
            // <expr> has type T, then <expr>.new C <...> (...)
            // yields a clazz T.C.
            Type encltype = chk.checkRefType(tree.encl.pos(),
                                             attribExpr(tree.encl, env));
            // TODO 308: in <expr>.new C, do we also want to add the type annotations
            // from expr to the combined type, or not? Yes, do this.
            clazzid1 = make.at(clazz.pos).Select(make.Type(encltype),
                                                 ((JCIdent) clazzid).name);

            EndPosTable endPosTable = this.env.toplevel.endPositions;
            endPosTable.storeEnd(clazzid1, tree.getEndPosition(endPosTable));
            if (clazz.hasTag(ANNOTATED_TYPE)) {
                JCAnnotatedType annoType = (JCAnnotatedType) clazz;
                JCList<JCAnnotation> annos = annoType.annotations;

                if (annoType.underlyingType.hasTag(TYPEAPPLY)) {
                    clazzid1 = make.at(tree.pos).
                        TypeApply(clazzid1,
                                  ((JCTypeApply) clazz).arguments);
                }

                clazzid1 = make.at(tree.pos).
                    AnnotatedType(annos, clazzid1);
            } else if (clazz.hasTag(TYPEAPPLY)) {
                clazzid1 = make.at(tree.pos).
                    TypeApply(clazzid1,
                              ((JCTypeApply) clazz).arguments);
            }

            clazz = clazzid1;
        }

        // Attribute clazz expression and store
        // symbol + type back into the attributed tree.
        Type clazztype;

        try {
            env.info.isNewClass = true;
            clazztype = TreeInfo.isEnumInit(env.tree) ?
                attribIdentAsEnumType(env, (JCIdent)clazz) :
                attribType(clazz, env);
        } finally {
            env.info.isNewClass = false;
        }

        clazztype = chk.checkDiamond(tree, clazztype);
        chk.validate(clazz, localEnv);
        if (tree.encl != null) {
            // We have to work in this case to store
            // symbol + type back into the attributed tree.
            tree.clazz.type = clazztype;
            TreeInfo.setSymbol(clazzid, TreeInfo.symbol(clazzid1));
            clazzid.type = ((JCIdent) clazzid).sym.type;
            if (annoclazzid != null) {
                annoclazzid.type = clazzid.type;
            }
            if (!clazztype.isErroneous()) {
                if (cdef != null && clazztype.tsym.isInterface()) {
                    log.error(tree.encl.pos(), "anon.class.impl.intf.no.qual.for.new");
                } else if (clazztype.tsym.isStatic()) {
                    log.error(tree.encl.pos(), "qualified.new.of.static.class", clazztype.tsym);
                }
            }
        } else if (!clazztype.tsym.isInterface() &&
                   clazztype.getEnclosingType().hasTag(CLASS)) {
            // Check for the existence of an apropos outer instance
            rs.resolveImplicitThis(tree.pos(), env, clazztype);
        }

        // Attribute constructor arguments.
        ListBuffer<Type> argtypesBuf = new ListBuffer<>();
        final KindSelector pkind =
            attribArgs(KindSelector.VAL, tree.args, localEnv, argtypesBuf);
        JCList<Type> argtypes = argtypesBuf.toList();
        JCList<Type> typeargtypes = attribTypes(tree.typeargs, localEnv);

        // If we have made no mistakes in the class type...
        if (clazztype.hasTag(CLASS)) {
            // Enums may not be instantiated except implicitly
            if ((clazztype.tsym.flags_field & Flags.ENUM) != 0 &&
                (!env.tree.hasTag(VARDEF) ||
                 (((JCVariableDecl) env.tree).mods.flags & Flags.ENUM) == 0 ||
                 ((JCVariableDecl) env.tree).init != tree))
                log.error(tree.pos(), "enum.cant.be.instantiated");

            boolean isSpeculativeDiamondInferenceRound = TreeInfo.isDiamond(tree) &&
                    resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.SPECULATIVE;
            boolean skipNonDiamondPath = false;
            // Check that class is not abstract
            if (cdef == null && !isSpeculativeDiamondInferenceRound && // class body may be nulled out in speculative tree copy
                (clazztype.tsym.flags() & (ABSTRACT | INTERFACE)) != 0) {
                log.error(tree.pos(), "abstract.cant.be.instantiated",
                          clazztype.tsym);
                skipNonDiamondPath = true;
            } else if (cdef != null && clazztype.tsym.isInterface()) {
                // Check that no constructor arguments are given to
                // anonymous classes implementing an interface
                if (!argtypes.isEmpty())
                    log.error(tree.args.head.pos(), "anon.class.impl.intf.no.args");

                if (!typeargtypes.isEmpty())
                    log.error(tree.typeargs.head.pos(), "anon.class.impl.intf.no.typeargs");

                // Error recovery: pretend no arguments were supplied.
                argtypes = JCList.nil();
                typeargtypes = JCList.nil();
                skipNonDiamondPath = true;
            }
            if (TreeInfo.isDiamond(tree)) {
                ClassType site = new ClassType(clazztype.getEnclosingType(),
                            clazztype.tsym.type.getTypeArguments(),
                                               clazztype.tsym,
                                               clazztype.getMetadata());

                Env<AttrContext> diamondEnv = localEnv.dup(tree);
                diamondEnv.info.selectSuper = cdef != null;
                diamondEnv.info.pendingResolutionPhase = null;

                //if the type of the instance creation expression is a class type
                //apply method resolution inference (JLS 15.12.2.7). The return type
                //of the resolved constructor will be a partially instantiated type
                Symbol constructor = rs.resolveDiamond(tree.pos(),
                            diamondEnv,
                            site,
                            argtypes,
                            typeargtypes);
                tree.constructor = constructor.baseSymbol();

                final TypeSymbol csym = clazztype.tsym;
                ResultInfo diamondResult = new ResultInfo(pkind, newMethodTemplate(resultInfo.pt, argtypes, typeargtypes),
                        diamondContext(tree, csym, resultInfo.checkContext), CheckMode.NO_TREE_UPDATE);
                Type constructorType = tree.constructorType = types.createErrorType(clazztype);
                constructorType = checkId(tree, site,
                        constructor,
                        diamondEnv,
                        diamondResult);

                tree.clazz.type = types.createErrorType(clazztype);
                if (!constructorType.isErroneous()) {
                    tree.clazz.type = clazz.type = constructorType.getReturnType();
                    tree.constructorType = types.createMethodTypeWithReturn(constructorType, syms.voidType);
                }
                clazztype = chk.checkClassType(tree.clazz, tree.clazz.type, true);
            }

            // Resolve the called constructor under the assumption
            // that we are referring to a superclass instance of the
            // current instance (JLS ???).
            else if (!skipNonDiamondPath) {
                //the following code alters some of the fields in the current
                //AttrContext - hence, the current context must be dup'ed in
                //order to avoid downstream failures
                Env<AttrContext> rsEnv = localEnv.dup(tree);
                rsEnv.info.selectSuper = cdef != null;
                rsEnv.info.pendingResolutionPhase = null;
                tree.constructor = rs.resolveConstructor(
                    tree.pos(), rsEnv, clazztype, argtypes, typeargtypes);
                if (cdef == null) { //do not check twice!
                    tree.constructorType = checkId(tree,
                            clazztype,
                            tree.constructor,
                            rsEnv,
                            new ResultInfo(pkind, newMethodTemplate(syms.voidType, argtypes, typeargtypes), CheckMode.NO_TREE_UPDATE));
                    if (rsEnv.info.lastResolveVarargs())
                        Assert.check(tree.constructorType.isErroneous() || tree.varargsElement != null);
                }
            }

            if (cdef != null) {
                visitAnonymousClassDefinition(tree, clazz, clazztype, cdef, localEnv, argtypes, typeargtypes, pkind);
                return;
            }

            if (tree.constructor != null && tree.constructor.kind == MTH)
                owntype = clazztype;
        }
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
        InferenceContext inferenceContext = resultInfo.checkContext.inferenceContext();
        if (tree.constructorType != null && inferenceContext.free(tree.constructorType)) {
            //we need to wait for inference to finish and then replace inference vars in the constructor type
            inferenceContext.addFreeTypeListener(JCList.of(tree.constructorType),
                    instantiatedContext -> {
                        tree.constructorType = instantiatedContext.asInstType(tree.constructorType);
                    });
        }
        chk.validate(tree.typeargs, localEnv);
    }

        // where
        private void visitAnonymousClassDefinition(JCNewClass tree, JCExpression clazz, Type clazztype,
                                                   JCClassDecl cdef, Env<AttrContext> localEnv,
                                                   JCList<Type> argtypes, JCList<Type> typeargtypes,
                                                   KindSelector pkind) {
            // We are seeing an anonymous class instance creation.
            // In this case, the class instance creation
            // expression
            //
            //    E.new <typeargs1>C<typargs2>(args) { ... }
            //
            // is represented internally as
            //
            //    E . new <typeargs1>C<typargs2>(args) ( class <empty-name> { ... } )  .
            //
            // This expression is then *transformed* as follows:
            //
            // (1) add an extends or implements clause
            // (2) add a constructor.
            //
            // For instance, if C is a class, and ET is the type of E,
            // the expression
            //
            //    E.new <typeargs1>C<typargs2>(args) { ... }
            //
            // is translated to (where X is a fresh name and typarams is the
            // parameter list of the super constructor):
            //
            //   new <typeargs1>X(<*nullchk*>E, args) where
            //     X extends C<typargs2> {
            //       <typarams> X(ET e, args) {
            //         e.<typeargs1>super(args)
            //       }
            //       ...
            //     }
            InferenceContext inferenceContext = resultInfo.checkContext.inferenceContext();
            final boolean isDiamond = TreeInfo.isDiamond(tree);
            if (isDiamond
                    && ((tree.constructorType != null && inferenceContext.free(tree.constructorType))
                    || (tree.clazz.type != null && inferenceContext.free(tree.clazz.type)))) {
                final ResultInfo resultInfoForClassDefinition = this.resultInfo;
                inferenceContext.addFreeTypeListener(JCList.of(tree.constructorType, tree.clazz.type),
                        instantiatedContext -> {
                            tree.constructorType = instantiatedContext.asInstType(tree.constructorType);
                            tree.clazz.type = clazz.type = instantiatedContext.asInstType(clazz.type);
                            ResultInfo prevResult = this.resultInfo;
                            try {
                                this.resultInfo = resultInfoForClassDefinition;
                                visitAnonymousClassDefinition(tree, clazz, clazz.type, cdef,
                                                            localEnv, argtypes, typeargtypes, pkind);
                            } finally {
                                this.resultInfo = prevResult;
                            }
                        });
            } else {
                if (isDiamond && clazztype.hasTag(CLASS)) {
                    JCList<Type> invalidDiamondArgs = chk.checkDiamondDenotable((ClassType)clazztype);
                    if (!clazztype.isErroneous() && invalidDiamondArgs.nonEmpty()) {
                        // One or more types inferred in the previous steps is non-denotable.
                        Fragment fragment = Diamond(clazztype.tsym);
//                        log.error(tree.clazz.pos(),
//                                Errors.CantApplyDiamond1(
//                                        fragment,
//                                        invalidDiamondArgs.size() > 1 ?
//                                                DiamondInvalidArgs(invalidDiamondArgs, fragment) :
//                                                DiamondInvalidArg(invalidDiamondArgs, fragment)));
                    }
                    // For <>(){}, inferred types must also be accessible.
                    for (Type t : clazztype.getTypeArguments()) {
                        rs.checkAccessibleType(env, t);
                    }
                }

                // If we already errored, be careful to avoid a further avalanche. ErrorType answers
                // false for isInterface call even when the original type is an interface.
                boolean implementing = clazztype.tsym.isInterface() ||
                        clazztype.isErroneous() && clazztype.getOriginalType().tsym.isInterface();

                if (implementing) {
                    cdef.implementing = JCList.of(clazz);
                } else {
                    cdef.extending = clazz;
                }

                if (resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK &&
                    isSerializable(clazztype)) {
                    localEnv.info.isSerializable = true;
                }

                attribStat(cdef, localEnv);

                JCList<Type> finalargtypes;
                // If an outer instance is given,
                // prefix it to the constructor arguments
                // and delete it from the new expression
                if (tree.encl != null && !clazztype.tsym.isInterface()) {
                    tree.args = tree.args.prepend(makeNullCheck(tree.encl));
                    finalargtypes = argtypes.prepend(tree.encl.type);
                    tree.encl = null;
                } else {
                    finalargtypes = argtypes;
                }

                // Reassign clazztype and recompute constructor. As this necessarily involves
                // another attribution pass for deferred types in the case of <>, replicate
                // them. Original arguments have right decorations already.
                if (isDiamond && pkind.contains(KindSelector.POLY)) {
                    finalargtypes = finalargtypes.map(deferredAttr.deferredCopier);
                }

                clazztype = cdef.sym.type;
                Symbol sym = tree.constructor = rs.resolveConstructor(
                        tree.pos(), localEnv, clazztype, finalargtypes, typeargtypes);
                Assert.check(!sym.kind.isResolutionError());
                tree.constructor = sym;
                tree.constructorType = checkId(tree,
                        clazztype,
                        tree.constructor,
                        localEnv,
                        new ResultInfo(pkind, newMethodTemplate(syms.voidType, finalargtypes, typeargtypes), CheckMode.NO_TREE_UPDATE));
            }
            Type owntype = (tree.constructor != null && tree.constructor.kind == MTH) ?
                                clazztype : types.createErrorType(tree.type);
            result = check(tree, owntype, KindSelector.VAL, resultInfo.dup(CheckMode.NO_INFERENCE_HOOK));
            chk.validate(tree.typeargs, localEnv);
        }

        CheckContext diamondContext(JCNewClass clazz, TypeSymbol tsym, CheckContext checkContext) {
            return new Check.NestedCheckContext(checkContext) {
                @Override
                public void report(DiagnosticPosition _unused, JCDiagnostic details) {
                    enclosingContext.report(clazz.clazz,
                            diags.fragment("cant.apply.diamond.1", diags.fragment("diamond", tsym), details));
                }
            };
        }

    /** Make an attributed null check tree.
     */
    public JCExpression makeNullCheck(JCExpression arg) {
        // optimization: new Outer() can never be null; skip null check
        if (arg.getTag() == NEWCLASS)
            return arg;
        // optimization: X.this is never null; skip null check
        Name name = TreeInfo.name(arg);
        if (name == names._this || name == names._super) return arg;

        JCTree.JCTreeTag optag = NULLCHK;
        JCUnary tree = make.at(arg.pos).Unary(optag, arg);
        tree.operator = operators.resolveUnary(arg, optag, arg.type);
        tree.type = arg.type;
        return tree;
    }

    public void visitNewArray(JCNewArray tree) {
        Type owntype = types.createErrorType(tree.type);
        Env<AttrContext> localEnv = env.dup(tree);
        Type elemtype;
        if (tree.elemtype != null) {
            elemtype = attribType(tree.elemtype, localEnv);
            chk.validate(tree.elemtype, localEnv);
            owntype = elemtype;
            for (JCList<JCExpression> l = tree.dims; l.nonEmpty(); l = l.tail) {
                attribExpr(l.head, localEnv, syms.intType);
                owntype = new ArrayType(owntype, syms.arrayClass);
            }
        } else {
            // we are seeing an untyped aggregate { ... }
            // this is allowed only if the prototype is an array
            if (pt().hasTag(ARRAY)) {
                elemtype = types.elemtype(pt());
            } else {
                if (!pt().hasTag(ERROR)) {
                    log.error(tree.pos(), "illegal.initializer.for.type",
                              pt());
                }
                elemtype = types.createErrorType(pt());
            }
        }
        if (tree.elems != null) {
            attribExprs(tree.elems, localEnv, elemtype);
            owntype = new ArrayType(elemtype, syms.arrayClass);
        }
        if (!types.isReifiable(elemtype))
            log.error(tree.pos(), "generic.array.creation");
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
    }

    /*
     * A lambda expression can only be attributed when a target-type is available.
     * In addition, if the target-type is that of a functional interface whose
     * descriptor contains inference variables in argument position the lambda expression
     * is 'stuck' (see DeferredAttr).
     */
    @Override
    public void visitLambda(final JCLambda that) {
        if (pt().isErroneous() || (pt().hasTag(NONE) && pt() != Type.recoveryType)) {
            if (pt().hasTag(NONE)) {
                //lambda only allowed in assignment or method invocation/cast context
                log.error(that.pos(), "unexpected.lambda");
            }
            result = that.type = types.createErrorType(pt());
            return;
        }
        //create an environment for attribution of the lambda expression
        final Env<AttrContext> localEnv = lambdaEnv(that, env);
        boolean needsRecovery =
                resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK;
        try {
            if (needsRecovery && isSerializable(pt())) {
                localEnv.info.isSerializable = true;
                localEnv.info.isLambda = true;
            }
            JCList<Type> explicitParamTypes = null;
            if (that.paramKind == JCLambda.ParameterKind.EXPLICIT) {
                //attribute lambda parameters
                attribStats(that.params, localEnv);
                explicitParamTypes = TreeInfo.types(that.params);
            }

            TargetInfo targetInfo = getTargetInfo(that, resultInfo, explicitParamTypes);
            Type currentTarget = targetInfo.target;
            Type lambdaType = targetInfo.descriptor;

            if (currentTarget.isErroneous()) {
                result = that.type = currentTarget;
                return;
            }

            setFunctionalInfo(localEnv, that, pt(), lambdaType, currentTarget, resultInfo.checkContext);

            if (lambdaType.hasTag(FORALL)) {
                //lambda expression target desc cannot be a generic method
                resultInfo.checkContext.report(that, diags.fragment("invalid.generic.lambda.target",
                        lambdaType, kindName(currentTarget.tsym), currentTarget.tsym));
                result = that.type = types.createErrorType(pt());
                return;
            }

            if (that.paramKind == JCLambda.ParameterKind.IMPLICIT) {
                //add param type info in the AST
                JCList<Type> actuals = lambdaType.getParameterTypes();
                JCList<JCVariableDecl> params = that.params;

                boolean arityMismatch = false;

                while (params.nonEmpty()) {
                    if (actuals.isEmpty()) {
                        //not enough actuals to perform lambda parameter inference
                        arityMismatch = true;
                    }
                    //reset previously set info
                    Type argType = arityMismatch ?
                            syms.errType :
                            actuals.head;
                    params.head.vartype = make.at(params.head).Type(argType);
                    params.head.sym = null;
                    actuals = actuals.isEmpty() ?
                            actuals :
                            actuals.tail;
                    params = params.tail;
                }

                //attribute lambda parameters
                attribStats(that.params, localEnv);

                if (arityMismatch) {
                    resultInfo.checkContext.report(that, diags.fragment("incompatible.arg.types.in.lambda"));
                        result = that.type = types.createErrorType(currentTarget);
                        return;
                }
            }

            //from this point on, no recovery is needed; if we are in assignment context
            //we will be able to attribute the whole lambda body, regardless of errors;
            //if we are in a 'check' method context, and the lambda is not compatible
            //with the target-type, it will be recovered anyway in Attr.checkId
            needsRecovery = false;

            ResultInfo bodyResultInfo = localEnv.info.returnResult =
                    lambdaBodyResult(that, lambdaType, resultInfo);

            if (that.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                attribTree(that.getBody(), localEnv, bodyResultInfo);
            } else {
                JCBlock body = (JCBlock)that.body;
                attribStats(body.stats, localEnv);
            }

            result = check(that, currentTarget, KindSelector.VAL, resultInfo);

            boolean isSpeculativeRound =
                    resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.SPECULATIVE;

            preFlow(that);
            flow.analyzeLambda(env, that, make, isSpeculativeRound);

            that.type = currentTarget; //avoids recovery at this stage
            checkLambdaCompatible(that, lambdaType, resultInfo.checkContext);

            if (!isSpeculativeRound) {
                //add thrown types as bounds to the thrown types free variables if needed:
                if (resultInfo.checkContext.inferenceContext().free(lambdaType.getThrownTypes())) {
                    JCList<Type> inferredThrownTypes = flow.analyzeLambdaThrownTypes(env, that, make);
                    JCList<Type> thrownTypes = resultInfo.checkContext.inferenceContext().asUndetVars(lambdaType.getThrownTypes());

                    chk.unhandled(inferredThrownTypes, thrownTypes);

                    //18.2.5: "In addition, for all j (1 <= j <= n), the constraint reduces to the bound throws Ej"
                    thrownTypes.stream()
                            .filter(t -> t.hasTag(UNDETVAR))
                            .forEach(t -> ((UndetVar)t).setThrow());
                }

                checkAccessibleTypes(that, localEnv, resultInfo.checkContext.inferenceContext(), lambdaType, currentTarget);
            }
            result = check(that, currentTarget, KindSelector.VAL, resultInfo);
        } catch (Types.FunctionDescriptorLookupError ex) {
            JCDiagnostic cause = ex.getDiagnostic();
            resultInfo.checkContext.report(that, cause);
            result = that.type = types.createErrorType(pt());
            return;
        } catch (Throwable t) {
            //when an unexpected exception happens, avoid attempts to attribute the same tree again
            //as that would likely cause the same exception again.
            needsRecovery = false;
            throw t;
        } finally {
            localEnv.info.scope.leave();
            if (needsRecovery) {
                attribTree(that, env, recoveryInfo);
            }
        }
    }
    //where
        class TargetInfo {
            Type target;
            Type descriptor;

            public TargetInfo(Type target, Type descriptor) {
                this.target = target;
                this.descriptor = descriptor;
            }
        }

        TargetInfo getTargetInfo(JCPolyExpression that, ResultInfo resultInfo, JCList<Type> explicitParamTypes) {
            Type lambdaType;
            Type currentTarget = resultInfo.pt;
            if (resultInfo.pt != Type.recoveryType) {
                /* We need to adjust the target. If the target is an
                 * intersection type, for example: SAM & I1 & I2 ...
                 * the target will be updated to SAM
                 */
                currentTarget = targetChecker.visit(currentTarget, that);
                if (explicitParamTypes != null) {
                    currentTarget = infer.instantiateFunctionalInterface(that,
                            currentTarget, explicitParamTypes, resultInfo.checkContext);
                }
                currentTarget = types.removeWildcards(currentTarget);
                lambdaType = types.findDescriptorType(currentTarget);
            } else {
                currentTarget = Type.recoveryType;
                lambdaType = fallbackDescriptorType(that);
            }
            if (that.hasTag(LAMBDA) && lambdaType.hasTag(FORALL)) {
                //lambda expression target desc cannot be a generic method
                resultInfo.checkContext.report(that, diags.fragment("invalid.generic.lambda.target",
                        lambdaType, kindName(currentTarget.tsym), currentTarget.tsym));
                currentTarget = types.createErrorType(pt());
            }
            return new TargetInfo(currentTarget, lambdaType);
        }

        void preFlow(JCLambda tree) {
            new PostAttrAnalyzer() {
                @Override
                public void scan(JCTree tree) {
                    if (tree == null ||
                            (tree.type != null &&
                            tree.type == Type.stuckType)) {
                        //don't touch stuck expressions!
                        return;
                    }
                    super.scan(tree);
                }
            }.scan(tree);
        }

        Types.MapVisitor<DiagnosticPosition> targetChecker = new Types.MapVisitor<DiagnosticPosition>() {

            @Override
            public Type visitClassType(ClassType t, DiagnosticPosition pos) {
                return t.isIntersection() ?
                        visitIntersectionClassType((IntersectionClassType)t, pos) : t;
            }

            public Type visitIntersectionClassType(IntersectionClassType ict, DiagnosticPosition pos) {
                Symbol desc = types.findDescriptorSymbol(makeNotionalInterface(ict));
                Type target = null;
                for (Type bound : ict.getExplicitComponents()) {
                    TypeSymbol boundSym = bound.tsym;
                    if (types.isFunctionalInterface(boundSym) &&
                            types.findDescriptorSymbol(boundSym) == desc) {
                        target = bound;
                    } else if (!boundSym.isInterface() || (boundSym.flags() & ANNOTATION) != 0) {
                        //bound must be an interface
                        reportIntersectionError(pos, "not.an.intf.component", boundSym);
                    }
                }
                return target != null ?
                        target :
                        ict.getExplicitComponents().head; //error recovery
            }

            private TypeSymbol makeNotionalInterface(IntersectionClassType ict) {
                ListBuffer<Type> targs = new ListBuffer<>();
                ListBuffer<Type> supertypes = new ListBuffer<>();
                for (Type i : ict.interfaces_field) {
                    if (i.isParameterized()) {
                        targs.appendList(i.tsym.type.allparams());
                    }
                    supertypes.append(i.tsym.type);
                }
                IntersectionClassType notionalIntf = types.makeIntersectionType(supertypes.toList());
                notionalIntf.allparams_field = targs.toList();
                notionalIntf.tsym.flags_field |= INTERFACE;
                return notionalIntf.tsym;
            }

            private void reportIntersectionError(DiagnosticPosition pos, String key, Object... args) {
                resultInfo.checkContext.report(pos, diags.fragment("bad.intersection.target.for.functional.expr",
                        diags.fragment(key, args)));
            }
        };

        private Type fallbackDescriptorType(JCExpression tree) {
            switch (tree.getTag()) {
                case LAMBDA:
                    JCLambda lambda = (JCLambda)tree;
                    JCList<Type> argtypes = JCList.nil();
                    for (JCVariableDecl param : lambda.params) {
                        argtypes = param.vartype != null ?
                                argtypes.append(param.vartype.type) :
                                argtypes.append(syms.errType);
                    }
                    return new MethodType(argtypes, Type.recoveryType,
                            JCList.of(syms.throwableType), syms.methodClass);
                case REFERENCE:
                    return new MethodType(JCList.nil(), Type.recoveryType,
                            JCList.of(syms.throwableType), syms.methodClass);
                default:
                    Assert.error("Cannot get here!");
            }
            return null;
        }

        private void checkAccessibleTypes(final DiagnosticPosition pos, final Env<AttrContext> env,
                final InferenceContext inferenceContext, final Type... ts) {
            checkAccessibleTypes(pos, env, inferenceContext, JCList.from(ts));
        }

        private void checkAccessibleTypes(final DiagnosticPosition pos, final Env<AttrContext> env,
                final InferenceContext inferenceContext, final JCList<Type> ts) {
            if (inferenceContext.free(ts)) {
                inferenceContext.addFreeTypeListener(ts,
                        solvedContext -> checkAccessibleTypes(pos, env, solvedContext, solvedContext.asInstTypes(ts)));
            } else {
                for (Type t : ts) {
                    rs.checkAccessibleType(env, t);
                }
            }
        }

        /**
         * Lambda/method reference have a special check context that ensures
         * that i.e. a lambda return type is compatible with the expected
         * type according to both the inherited context and the assignment
         * context.
         */
        class FunctionalReturnContext extends Check.NestedCheckContext {

            FunctionalReturnContext(CheckContext enclosingContext) {
                super(enclosingContext);
            }

            @Override
            public boolean compatible(Type found, Type req, Warner warn) {
                //return type must be compatible in both current context and assignment context
                return chk.basicHandler.compatible(inferenceContext().asUndetVar(found), inferenceContext().asUndetVar(req), warn);
            }

            @Override
            public void report(DiagnosticPosition pos, JCDiagnostic details) {
                enclosingContext.report(pos, diags.fragment("incompatible.ret.type.in.lambda", details));
            }
        }

        class ExpressionLambdaReturnContext extends FunctionalReturnContext {

            JCExpression expr;
            boolean expStmtExpected;

            ExpressionLambdaReturnContext(JCExpression expr, CheckContext enclosingContext) {
                super(enclosingContext);
                this.expr = expr;
            }

            @Override
            public void report(DiagnosticPosition pos, JCDiagnostic details) {
                if (expStmtExpected) {
                    enclosingContext.report(pos, diags.fragment(Fragments.StatExprExpected));
                } else {
                    super.report(pos, details);
                }
            }

            @Override
            public boolean compatible(Type found, Type req, Warner warn) {
                //a void return is compatible with an expression statement lambda
                if (req.hasTag(VOID)) {
                    expStmtExpected = true;
                    return TreeInfo.isExpressionStatement(expr);
                } else {
                    return super.compatible(found, req, warn);
                }
            }
        }

        ResultInfo lambdaBodyResult(JCLambda that, Type descriptor, ResultInfo resultInfo) {
            FunctionalReturnContext funcContext = that.getBodyKind() == JCLambda.BodyKind.EXPRESSION ?
                    new ExpressionLambdaReturnContext((JCExpression)that.getBody(), resultInfo.checkContext) :
                    new FunctionalReturnContext(resultInfo.checkContext);

            return descriptor.getReturnType() == Type.recoveryType ?
                    recoveryInfo :
                    new ResultInfo(KindSelector.VAL,
                            descriptor.getReturnType(), funcContext);
        }

        /**
        * Lambda compatibility. Check that given return types, thrown types, parameter types
        * are compatible with the expected functional interface descriptor. This means that:
        * (i) parameter types must be identical to those of the target descriptor; (ii) return
        * types must be compatible with the return type of the expected descriptor.
        */
        void checkLambdaCompatible(JCLambda tree, Type descriptor, CheckContext checkContext) {
            Type returnType = checkContext.inferenceContext().asUndetVar(descriptor.getReturnType());

            //return values have already been checked - but if lambda has no return
            //values, we must ensure that void/value compatibility is correct;
            //this amounts at checking that, if a lambda body can complete normally,
            //the descriptor's return type must be void
            if (tree.getBodyKind() == JCLambda.BodyKind.STATEMENT && tree.canCompleteNormally &&
                    !returnType.hasTag(VOID) && returnType != Type.recoveryType) {
                checkContext.report(tree, diags.fragment("incompatible.ret.type.in.lambda",
                        diags.fragment("missing.ret.val", returnType)));
            }

            JCList<Type> argTypes = checkContext.inferenceContext().asUndetVars(descriptor.getParameterTypes());
            if (!types.isSameTypes(argTypes, TreeInfo.types(tree.params))) {
                checkContext.report(tree, diags.fragment("incompatible.arg.types.in.lambda"));
            }
        }

        /* Map to hold 'fake' clinit methods. If a lambda is used to initialize a
         * static field and that lambda has type annotations, these annotations will
         * also be stored at these fake clinit methods.
         *
         * LambdaToMethod also use fake clinit methods so they can be reused.
         * Also as LTM is a phase subsequent to attribution, the methods from
         * clinits can be safely removed by LTM to save memory.
         */
        private Map<ClassSymbol, MethodSymbol> clinits = new HashMap<>();

        public MethodSymbol removeClinit(ClassSymbol sym) {
            return clinits.remove(sym);
        }

        /* This method returns an environment to be used to attribute a lambda
         * expression.
         *
         * The owner of this environment is a method symbol. If the current owner
         * is not a method, for example if the lambda is used to initialize
         * a field, then if the field is:
         *
         * - an instance field, we use the first constructor.
         * - a static field, we create a fake clinit method.
         */
        public Env<AttrContext> lambdaEnv(JCLambda that, Env<AttrContext> env) {
            Env<AttrContext> lambdaEnv;
            Symbol owner = env.info.scope.owner;
            if (owner.kind == VAR && owner.owner.kind == TYP) {
                //field initializer
                ClassSymbol enclClass = owner.enclClass();
                Symbol newScopeOwner = env.info.scope.owner;
                /* if the field isn't static, then we can get the first constructor
                 * and use it as the owner of the environment. This is what
                 * LTM code is doing to look for type annotations so we are fine.
                 */
                if ((owner.flags() & STATIC) == 0) {
                    for (Symbol s : enclClass.members_field.getSymbolsByName(names.init)) {
                        newScopeOwner = s;
                        break;
                    }
                } else {
                    /* if the field is static then we need to create a fake clinit
                     * method, this method can later be reused by LTM.
                     */
                    MethodSymbol clinit = clinits.get(enclClass);
                    if (clinit == null) {
                        Type clinitType = new MethodType(JCList.nil(),
                                syms.voidType, JCList.nil(), syms.methodClass);
                        clinit = new MethodSymbol(STATIC | SYNTHETIC | PRIVATE,
                                names.clinit, clinitType, enclClass);
                        clinit.params = JCList.nil();
                        clinits.put(enclClass, clinit);
                    }
                    newScopeOwner = clinit;
                }
                lambdaEnv = env.dup(that, env.info.dup(env.info.scope.dupUnshared(newScopeOwner)));
            } else {
                lambdaEnv = env.dup(that, env.info.dup(env.info.scope.dup()));
            }
            return lambdaEnv;
        }

    @Override
    public void visitReference(final JCMemberReference that) {
        if (pt().isErroneous() || (pt().hasTag(NONE) && pt() != Type.recoveryType)) {
            if (pt().hasTag(NONE)) {
                //method reference only allowed in assignment or method invocation/cast context
                log.error(that.pos(), "unexpected.mref");
            }
            result = that.type = types.createErrorType(pt());
            return;
        }
        final Env<AttrContext> localEnv = env.dup(that);
        try {
            //attribute member reference qualifier - if this is a constructor
            //reference, the expected kind must be a type
            Type exprType = attribTree(that.expr, env, memberReferenceQualifierResult(that));

            if (that.getMode() == ReferenceMode.NEW) {
                exprType = chk.checkConstructorRefType(that.expr, exprType);
                if (!exprType.isErroneous() &&
                    exprType.isRaw() &&
                    that.typeargs != null) {
                    log.error(that.expr.pos(), "invalid.mref", Kinds.kindName(that.getMode()),
                        diags.fragment("mref.infer.and.explicit.params"));
                    exprType = types.createErrorType(exprType);
                }
            }

            if (exprType.isErroneous()) {
                //if the qualifier expression contains problems,
                //give up attribution of method reference
                result = that.type = exprType;
                return;
            }

            if (TreeInfo.isStaticSelector(that.expr, names)) {
                //if the qualifier is a type, validate it; raw warning check is
                //omitted as we don't know at this stage as to whether this is a
                //raw selector (because of inference)
                chk.validate(that.expr, env, false);
            } else {
                Symbol lhsSym = TreeInfo.symbol(that.expr);
                localEnv.info.selectSuper = lhsSym != null && lhsSym.name == names._super;
            }
            //attrib type-arguments
            JCList<Type> typeargtypes = JCList.nil();
            if (that.typeargs != null) {
                typeargtypes = attribTypes(that.typeargs, localEnv);
            }

            boolean isTargetSerializable =
                    resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK &&
                    isSerializable(pt());
            TargetInfo targetInfo = getTargetInfo(that, resultInfo, null);
            Type currentTarget = targetInfo.target;
            Type desc = targetInfo.descriptor;

            setFunctionalInfo(localEnv, that, pt(), desc, currentTarget, resultInfo.checkContext);
            JCList<Type> argtypes = desc.getParameterTypes();
            Resolve.MethodCheck referenceCheck = rs.resolveMethodCheck;

            if (resultInfo.checkContext.inferenceContext().free(argtypes)) {
                referenceCheck = rs.new MethodReferenceCheck(resultInfo.checkContext.inferenceContext());
            }

            Pair<Symbol, Resolve.ReferenceLookupHelper> refResult = null;
            JCList<Type> saved_undet = resultInfo.checkContext.inferenceContext().save();
            try {
                refResult = rs.resolveMemberReference(localEnv, that, that.expr.type,
                        that.name, argtypes, typeargtypes, referenceCheck,
                        resultInfo.checkContext.inferenceContext(), rs.basicReferenceChooser);
            } finally {
                resultInfo.checkContext.inferenceContext().rollback(saved_undet);
            }

            Symbol refSym = refResult.fst;
            Resolve.ReferenceLookupHelper lookupHelper = refResult.snd;

            /** this switch will need to go away and be replaced by the new RESOLUTION_TARGET testing
             *  JDK-8075541
             */
            if (refSym.kind != MTH) {
                boolean targetError;
                switch (refSym.kind) {
                    case ABSENT_MTH:
                    case MISSING_ENCL:
                        targetError = false;
                        break;
                    case WRONG_MTH:
                    case WRONG_MTHS:
                    case AMBIGUOUS:
                    case HIDDEN:
                    case STATICERR:
                        targetError = true;
                        break;
                    default:
                        Assert.error("unexpected result kind " + refSym.kind);
                        targetError = false;
                }

                JCDiagnostic detailsDiag = ((Resolve.ResolveError)refSym.baseSymbol()).getDiagnostic(JCDiagnostic.DiagnosticType.FRAGMENT,
                                that, exprType.tsym, exprType, that.name, argtypes, typeargtypes);

                JCDiagnostic.DiagnosticType diagKind = targetError ?
                        JCDiagnostic.DiagnosticType.FRAGMENT : JCDiagnostic.DiagnosticType.ERROR;

                JCDiagnostic diag = diags.create(diagKind, log.currentSource(), that,
                        "invalid.mref", Kinds.kindName(that.getMode()), detailsDiag);

                if (targetError && currentTarget == Type.recoveryType) {
                    //a target error doesn't make sense during recovery stage
                    //as we don't know what actual parameter types are
                    result = that.type = currentTarget;
                    return;
                } else {
                    if (targetError) {
                        resultInfo.checkContext.report(that, diag);
                    } else {
                        log.report(diag);
                    }
                    result = that.type = types.createErrorType(currentTarget);
                    return;
                }
            }

            that.sym = refSym.baseSymbol();
            that.kind = lookupHelper.referenceKind(that.sym);
            that.ownerAccessible = rs.isAccessible(localEnv, that.sym.enclClass());

            if (desc.getReturnType() == Type.recoveryType) {
                // stop here
                result = that.type = currentTarget;
                return;
            }

            if (!env.info.isSpeculative && that.getMode() == ReferenceMode.NEW) {
                Type enclosingType = exprType.getEnclosingType();
                if (enclosingType != null && enclosingType.hasTag(CLASS)) {
                    // Check for the existence of an apropriate outer instance
                    rs.resolveImplicitThis(that.pos(), env, exprType);
                }
            }

            if (resultInfo.checkContext.deferredAttrContext().mode == AttrMode.CHECK) {

                if (that.getMode() == ReferenceMode.INVOKE &&
                        TreeInfo.isStaticSelector(that.expr, names) &&
                        that.kind.isUnbound() &&
                        !desc.getParameterTypes().head.isParameterized()) {
                    chk.checkRaw(that.expr, localEnv);
                }

                if (that.sym.isStatic() && TreeInfo.isStaticSelector(that.expr, names) &&
                        exprType.getTypeArguments().nonEmpty()) {
                    //static ref with class type-args
                    log.error(that.expr.pos(), "invalid.mref", Kinds.kindName(that.getMode()),
                            diags.fragment("static.mref.with.targs"));
                    result = that.type = types.createErrorType(currentTarget);
                    return;
                }

                if (!refSym.isStatic() && that.kind == JCMemberReference.ReferenceKind.SUPER) {
                    // Check that super-qualified symbols are not abstract (JLS)
                    rs.checkNonAbstract(that.pos(), that.sym);
                }

                if (isTargetSerializable) {
                    chk.checkAccessFromSerializableElement(that, true);
                }
            }

            ResultInfo checkInfo =
                    resultInfo.dup(newMethodTemplate(
                        desc.getReturnType().hasTag(VOID) ? Type.noType : desc.getReturnType(),
                        that.kind.isUnbound() ? argtypes.tail : argtypes, typeargtypes),
                        new FunctionalReturnContext(resultInfo.checkContext), CheckMode.NO_TREE_UPDATE);

            Type refType = checkId(that, lookupHelper.site, refSym, localEnv, checkInfo);

            if (that.kind.isUnbound() &&
                    resultInfo.checkContext.inferenceContext().free(argtypes.head)) {
                //re-generate inference constraints for unbound receiver
                if (!types.isSubtype(resultInfo.checkContext.inferenceContext().asUndetVar(argtypes.head), exprType)) {
                    //cannot happen as this has already been checked - we just need
                    //to regenerate the inference constraints, as that has been lost
                    //as a result of the call to inferenceContext.save()
                    Assert.error("Can't get here");
                }
            }

            if (!refType.isErroneous()) {
                refType = types.createMethodTypeWithReturn(refType,
                        adjustMethodReturnType(refSym, lookupHelper.site, that.name, checkInfo.pt.getParameterTypes(), refType.getReturnType()));
            }

            //go ahead with standard method reference compatibility check - note that param check
            //is a no-op (as this has been taken care during method applicability)
            boolean isSpeculativeRound =
                    resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.SPECULATIVE;

            that.type = currentTarget; //avoids recovery at this stage
            checkReferenceCompatible(that, desc, refType, resultInfo.checkContext, isSpeculativeRound);
            if (!isSpeculativeRound) {
                checkAccessibleTypes(that, localEnv, resultInfo.checkContext.inferenceContext(), desc, currentTarget);
            }
            result = check(that, currentTarget, KindSelector.VAL, resultInfo);
        } catch (Types.FunctionDescriptorLookupError ex) {
            JCDiagnostic cause = ex.getDiagnostic();
            resultInfo.checkContext.report(that, cause);
            result = that.type = types.createErrorType(pt());
            return;
        }
    }
    //where
        ResultInfo memberReferenceQualifierResult(JCMemberReference tree) {
            //if this is a constructor reference, the expected kind must be a type
            return new ResultInfo(tree.getMode() == ReferenceMode.INVOKE ?
                                  KindSelector.VAL_TYP : KindSelector.TYP,
                                  Type.noType);
        }


    @SuppressWarnings("fallthrough")
    void checkReferenceCompatible(JCMemberReference tree, Type descriptor, Type refType, CheckContext checkContext, boolean speculativeAttr) {
        InferenceContext inferenceContext = checkContext.inferenceContext();
        Type returnType = inferenceContext.asUndetVar(descriptor.getReturnType());

        Type resType;
        switch (tree.getMode()) {
            case NEW:
                if (!tree.expr.type.isRaw()) {
                    resType = tree.expr.type;
                    break;
                }
            default:
                resType = refType.getReturnType();
        }

        Type incompatibleReturnType = resType;

        if (returnType.hasTag(VOID)) {
            incompatibleReturnType = null;
        }

        if (!returnType.hasTag(VOID) && !resType.hasTag(VOID)) {
            if (resType.isErroneous() ||
                    new FunctionalReturnContext(checkContext).compatible(resType, returnType,
                            checkContext.checkWarner(tree, resType, returnType))) {
                incompatibleReturnType = null;
            }
        }

        if (incompatibleReturnType != null) {
            checkContext.report(tree, diags.fragment("incompatible.ret.type.in.mref",
                    diags.fragment("inconvertible.types", resType, descriptor.getReturnType())));
        } else {
            if (inferenceContext.free(refType)) {
                // we need to wait for inference to finish and then replace inference vars in the referent type
                inferenceContext.addFreeTypeListener(JCList.of(refType),
                        instantiatedContext -> {
                            tree.referentType = instantiatedContext.asInstType(refType);
                        });
            } else {
                tree.referentType = refType;
            }
        }

        if (!speculativeAttr) {
            JCList<Type> thrownTypes = inferenceContext.asUndetVars(descriptor.getThrownTypes());
            if (chk.unhandled(refType.getThrownTypes(), thrownTypes).nonEmpty()) {
                log.error(tree, "incompatible.thrown.types.in.mref", refType.getThrownTypes());
            }
            //18.2.5: "In addition, for all j (1 <= j <= n), the constraint reduces to the bound throws Ej"
            thrownTypes.stream()
                    .filter(t -> t.hasTag(UNDETVAR))
                    .forEach(t -> ((UndetVar)t).setThrow());
        }
    }

    /**
     * Set functional type info on the underlying AST. Note: as the target descriptor
     * might contain inference variables, we might need to register an hook in the
     * current inference context.
     */
    private void setFunctionalInfo(final Env<AttrContext> env, final JCFunctionalExpression fExpr,
            final Type pt, final Type descriptorType, final Type primaryTarget, final CheckContext checkContext) {
        if (checkContext.inferenceContext().free(descriptorType)) {
            checkContext.inferenceContext().addFreeTypeListener(JCList.of(pt, descriptorType),
                    inferenceContext -> setFunctionalInfo(env, fExpr, pt, inferenceContext.asInstType(descriptorType),
                    inferenceContext.asInstType(primaryTarget), checkContext));
        } else {
            ListBuffer<Type> targets = new ListBuffer<>();
            if (pt.hasTag(CLASS)) {
                if (pt.isCompound()) {
                    targets.append(types.removeWildcards(primaryTarget)); //this goes first
                    for (Type t : ((IntersectionClassType)pt()).interfaces_field) {
                        if (t != primaryTarget) {
                            targets.append(types.removeWildcards(t));
                        }
                    }
                } else {
                    targets.append(types.removeWildcards(primaryTarget));
                }
            }
            fExpr.targets = targets.toList();
            if (checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK &&
                    pt != Type.recoveryType) {
                //check that functional interface class is well-formed
                try {
                    /* Types.makeFunctionalInterfaceClass() may throw an exception
                     * when it's executed post-inference. See the listener code
                     * above.
                     */
                    ClassSymbol csym = types.makeFunctionalInterfaceClass(env,
                            names.empty, JCList.of(fExpr.targets.head), ABSTRACT);
                    if (csym != null) {
                        chk.checkImplementations(env.tree, csym, csym);
                        try {
                            //perform an additional functional interface check on the synthetic class,
                            //as there may be spurious errors for raw targets - because of existing issues
                            //with membership and inheritance (see JDK-8074570).
                            csym.flags_field |= INTERFACE;
                            types.findDescriptorType(csym.type);
                        } catch (FunctionDescriptorLookupError err) {
                            resultInfo.checkContext.report(fExpr,
                                    diags.fragment(Fragments.NoSuitableFunctionalIntfInst(fExpr.targets.head)));
                        }
                    }
                } catch (Types.FunctionDescriptorLookupError ex) {
                    JCDiagnostic cause = ex.getDiagnostic();
                    resultInfo.checkContext.report(env.tree, cause);
                }
            }
        }
    }

    public void visitParens(JCParens tree) {
        Type owntype = attribTree(tree.expr, env, resultInfo);
        result = check(tree, owntype, pkind(), resultInfo);
        Symbol sym = TreeInfo.symbol(tree);
        if (sym != null && sym.kind.matches(KindSelector.TYP_PCK))
            log.error(tree.pos(), "illegal.start.of.type");
    }

    public void visitAssign(JCAssign tree) {
        Type owntype = attribTree(tree.lhs, env.dup(tree), varAssignmentInfo);
        Type capturedType = capture(owntype);
        attribExpr(tree.rhs, env, owntype);
        result = check(tree, capturedType, KindSelector.VAL, resultInfo);
    }

    public void visitAssignop(JCAssignOp tree) {
        // Attribute arguments.
        Type owntype = attribTree(tree.lhs, env, varAssignmentInfo);
        Type operand = attribExpr(tree.rhs, env);
        // Find operator.
        Symbol operator = tree.operator = operators.resolveBinary(tree, tree.getTag().noAssignOp(), owntype, operand);
        if (operator != operators.noOpSymbol &&
                !owntype.isErroneous() &&
                !operand.isErroneous()) {
            chk.checkDivZero(tree.rhs.pos(), operator, operand);
            chk.checkCastable(tree.rhs.pos(),
                              operator.type.getReturnType(),
                              owntype);
        }
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
    }

    public void visitUnary(JCUnary tree) {
        // Attribute arguments.
        Type argtype = (tree.getTag().isIncOrDecUnaryOp())
            ? attribTree(tree.arg, env, varAssignmentInfo)
            : chk.checkNonVoid(tree.arg.pos(), attribExpr(tree.arg, env));

        // Find operator.
        Symbol operator = tree.operator = operators.resolveUnary(tree, tree.getTag(), argtype);
        Type owntype = types.createErrorType(tree.type);
        if (operator != operators.noOpSymbol &&
                !argtype.isErroneous()) {
            owntype = (tree.getTag().isIncOrDecUnaryOp())
                ? tree.arg.type
                : operator.type.getReturnType();
            int opc = ((OperatorSymbol)operator).opcode;

            // If the argument is constant, fold it.
            if (argtype.constValue() != null) {
                Type ctype = cfolder.fold1(opc, argtype);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);
                }
            }
        }
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
    }

    public void visitBinary(JCBinary tree) {
        // Attribute arguments.
        Type left = chk.checkNonVoid(tree.lhs.pos(), attribExpr(tree.lhs, env));
        Type right = chk.checkNonVoid(tree.rhs.pos(), attribExpr(tree.rhs, env));
        // Find operator.
        Symbol operator = tree.operator = operators.resolveBinary(tree, tree.getTag(), left, right);
        Type owntype = types.createErrorType(tree.type);
        if (operator != operators.noOpSymbol &&
                !left.isErroneous() &&
                !right.isErroneous()) {
            owntype = operator.type.getReturnType();
            int opc = ((OperatorSymbol)operator).opcode;
            // If both arguments are constants, fold them.
            if (left.constValue() != null && right.constValue() != null) {
                Type ctype = cfolder.fold2(opc, left, right);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);
                }
            }

            // Check that argument types of a reference ==, != are
            // castable to each other, (JLS 15.21).  Note: unboxing
            // comparisons will not have an acmp* opc at this point.
            if ((opc == ByteCodes.if_acmpeq || opc == ByteCodes.if_acmpne)) {
                if (!types.isCastable(left, right, new Warner(tree.pos()))) {
                    log.error(tree.pos(), "incomparable.types", left, right);
                }
            }

            chk.checkDivZero(tree.rhs.pos(), operator, right);
        }
        result = check(tree, owntype, KindSelector.VAL, resultInfo);
    }

    public void visitTypeCast(final JCTypeCast tree) {
        Type clazztype = attribType(tree.clazz, env);
        chk.validate(tree.clazz, env, false);
        //a fresh environment is required for 292 inference to work properly ---
        //see Infer.instantiatePolymorphicSignatureInstance()
        Env<AttrContext> localEnv = env.dup(tree);
        //should we propagate the target type?
        final ResultInfo castInfo;
        JCExpression expr = TreeInfo.skipParens(tree.expr);
        boolean isPoly = allowPoly && (expr.hasTag(LAMBDA) || expr.hasTag(REFERENCE));
        if (isPoly) {
            //expression is a poly - we need to propagate target type info
            castInfo = new ResultInfo(KindSelector.VAL, clazztype,
                                      new Check.NestedCheckContext(resultInfo.checkContext) {
                @Override
                public boolean compatible(Type found, Type req, Warner warn) {
                    return types.isCastable(found, req, warn);
                }
            });
        } else {
            //standalone cast - target-type info is not propagated
            castInfo = unknownExprInfo;
        }
        Type exprtype = attribTree(tree.expr, localEnv, castInfo);
        Type owntype = isPoly ? clazztype : chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        if (exprtype.constValue() != null)
            owntype = cfolder.coerce(exprtype, owntype);
        result = check(tree, capture(owntype), KindSelector.VAL, resultInfo);
        if (!isPoly)
            chk.checkRedundantCast(localEnv, tree);
    }

    public void visitTypeTest(JCInstanceOf tree) {
        Type exprtype = chk.checkNullOrRefType(
                tree.expr.pos(), attribExpr(tree.expr, env));
        Type clazztype = attribType(tree.clazz, env);
        if (!clazztype.hasTag(TYPEVAR)) {
            clazztype = chk.checkClassOrArrayType(tree.clazz.pos(), clazztype);
        }
        if (!clazztype.isErroneous() && !types.isReifiable(clazztype)) {
            log.error(tree.clazz.pos(), "illegal.generic.type.for.instof");
            clazztype = types.createErrorType(clazztype);
        }
        chk.validate(tree.clazz, env, false);
        chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        result = check(tree, syms.booleanType, KindSelector.VAL, resultInfo);
    }

    public void visitIndexed(JCArrayAccess tree) {
        Type owntype = types.createErrorType(tree.type);
        Type atype = attribExpr(tree.indexed, env);
        attribExpr(tree.index, env, syms.intType);
        if (types.isArray(atype))
            owntype = types.elemtype(atype);
        else if (!atype.hasTag(ERROR))
            log.error(tree.pos(), "array.req.but.found", atype);
        if (!pkind().contains(KindSelector.VAL))
            owntype = capture(owntype);
        result = check(tree, owntype, KindSelector.VAR, resultInfo);
    }

    public void visitIdent(JCIdent tree) {
        Symbol sym;

        // Find symbol
        if (pt().hasTag(METHOD) || pt().hasTag(FORALL)) {
            // If we are looking for a method, the prototype `pt' will be a
            // method type with the type of the call's arguments as parameters.
            env.info.pendingResolutionPhase = null;
            sym = rs.resolveMethod(tree.pos(), env, tree.name, pt().getParameterTypes(), pt().getTypeArguments());
        } else if (tree.sym != null && tree.sym.kind != VAR) {
            sym = tree.sym;
        } else {
            sym = rs.resolveIdent(tree.pos(), env, tree.name, pkind());
        }
        tree.sym = sym;

        // (1) Also find the environment current for the class where
        //     sym is defined (`symEnv').
        // Only for pre-tiger versions (1.4 and earlier):
        // (2) Also determine whether we access symbol out of an anonymous
        //     class in a this or super call.  This is illegal for instance
        //     members since such classes don't carry a this$n link.
        //     (`noOuterThisPath').
        Env<AttrContext> symEnv = env;
        boolean noOuterThisPath = false;
        if (env.enclClass.sym.owner.kind != PCK && // we are in an inner class
            sym.kind.matches(KindSelector.VAL_MTH) &&
            sym.owner.kind == TYP &&
            tree.name != names._this && tree.name != names._super) {

            // Find environment in which identifier is defined.
            while (symEnv.outer != null &&
                   !sym.isMemberOf(symEnv.enclClass.sym, types)) {
                if ((symEnv.enclClass.sym.flags() & NOOUTERTHIS) != 0)
                    noOuterThisPath = false;
                symEnv = symEnv.outer;
            }
        }

        // If symbol is a variable, ...
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol)sym;

            // ..., evaluate its initializer, if it has one, and check for
            // illegal forward reference.
            checkInit(tree, env, v, false);

            // If we are expecting a variable (as opposed to a value), check
            // that the variable is assignable in the current environment.
            if (KindSelector.ASG.subset(pkind()))
                checkAssignable(tree.pos(), v, null, env);
        }

        // In a constructor body,
        // if symbol is a field or instance method, check that it is
        // not accessed before the supertype constructor is called.
        if ((symEnv.info.isSelfCall || noOuterThisPath) &&
            sym.kind.matches(KindSelector.VAL_MTH) &&
            sym.owner.kind == TYP &&
            (sym.flags() & STATIC) == 0) {
            chk.earlyRefError(tree.pos(), sym.kind == VAR ?
                                          sym : thisSym(tree.pos(), env));
        }
        Env<AttrContext> env1 = env;
        if (sym.kind != ERR && sym.kind != TYP &&
            sym.owner != null && sym.owner != env1.enclClass.sym) {
            // If the found symbol is inaccessible, then it is
            // accessed through an enclosing instance.  Locate this
            // enclosing instance:
            while (env1.outer != null && !rs.isAccessible(env, env1.enclClass.sym.type, sym))
                env1 = env1.outer;
        }

        if (env.info.isSerializable) {
            chk.checkAccessFromSerializableElement(tree, env.info.isLambda);
        }

        result = checkId(tree, env1.enclClass.sym.type, sym, env, resultInfo);
    }

    public void visitSelect(JCFieldAccess tree) {
        // Determine the expected kind of the qualifier expression.
        KindSelector skind = KindSelector.NIL;
        if (tree.name == names._this || tree.name == names._super ||
                tree.name == names._class)
        {
            skind = KindSelector.TYP;
        } else {
            if (pkind().contains(KindSelector.PCK))
                skind = KindSelector.of(skind, KindSelector.PCK);
            if (pkind().contains(KindSelector.TYP))
                skind = KindSelector.of(skind, KindSelector.TYP, KindSelector.PCK);
            if (pkind().contains(KindSelector.VAL_MTH))
                skind = KindSelector.of(skind, KindSelector.VAL, KindSelector.TYP);
        }

        // Attribute the qualifier expression, and determine its symbol (if any).
        Type site = attribTree(tree.selected, env, new ResultInfo(skind, Type.noType));
        if (!pkind().contains(KindSelector.TYP_PCK))
            site = capture(site); // Capture field access

        // don't allow T.class T[].class, etc
        if (skind == KindSelector.TYP) {
            Type elt = site;
            while (elt.hasTag(ARRAY))
                elt = ((ArrayType)elt).elemtype;
            if (elt.hasTag(TYPEVAR)) {
                log.error(tree.pos(), "type.var.cant.be.deref");
                result = tree.type = types.createErrorType(tree.name, site.tsym, site);
                tree.sym = tree.type.tsym;
                return ;
            }
        }

        // If qualifier symbol is a type or `super', assert `selectSuper'
        // for the selection. This is relevant for determining whether
        // protected symbols are accessible.
        Symbol sitesym = TreeInfo.symbol(tree.selected);
        boolean selectSuperPrev = env.info.selectSuper;
        env.info.selectSuper =
            sitesym != null &&
            sitesym.name == names._super;

        // Determine the symbol represented by the selection.
        env.info.pendingResolutionPhase = null;
        Symbol sym = selectSym(tree, sitesym, site, env, resultInfo);
        if (sym.kind == VAR && sym.name != names._super && env.info.defaultSuperCallSite != null) {
            log.error(tree.selected.pos(), "not.encl.class", site.tsym);
            sym = syms.errSymbol;
        }
        if (sym.exists() && !isType(sym) && pkind().contains(KindSelector.TYP_PCK)) {
            site = capture(site);
            sym = selectSym(tree, sitesym, site, env, resultInfo);
        }
        boolean varArgs = env.info.lastResolveVarargs();
        tree.sym = sym;

        if (site.hasTag(TYPEVAR) && !isType(sym) && sym.kind != ERR) {
            site = types.skipTypeVars(site, true);
        }

        // If that symbol is a variable, ...
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol)sym;

            // ..., evaluate its initializer, if it has one, and check for
            // illegal forward reference.
            checkInit(tree, env, v, true);

            // If we are expecting a variable (as opposed to a value), check
            // that the variable is assignable in the current environment.
            if (KindSelector.ASG.subset(pkind()))
                checkAssignable(tree.pos(), v, tree.selected, env);
        }

        if (sitesym != null &&
                sitesym.kind == VAR &&
                ((VarSymbol)sitesym).isResourceVariable() &&
                sym.kind == MTH &&
                sym.name.equals(names.close) &&
                sym.overrides(syms.autoCloseableClose, sitesym.type.tsym, types, true) &&
                env.info.lint.isEnabled(LintCategory.TRY)) {
            log.warning(LintCategory.TRY, tree, "try.explicit.close.call");
        }

        // Disallow selecting a type from an expression
        if (isType(sym) && (sitesym == null || !sitesym.kind.matches(KindSelector.TYP_PCK))) {
            tree.type = check(tree.selected, pt(),
                              sitesym == null ?
                                      KindSelector.VAL : sitesym.kind.toSelector(),
                              new ResultInfo(KindSelector.TYP_PCK, pt()));
        }

        if (isType(sitesym)) {
            if (sym.name == names._this) {
                // If `C' is the currently compiled class, check that
                // C.this' does not appear in a call to a super(...)
                if (env.info.isSelfCall &&
                    site.tsym == env.enclClass.sym) {
                    chk.earlyRefError(tree.pos(), sym);
                }
            } else {
                // Check if type-qualified fields or methods are static (JLS)
                if ((sym.flags() & STATIC) == 0 &&
                    sym.name != names._super &&
                    (sym.kind == VAR || sym.kind == MTH)) {
                    rs.accessBase(rs.new StaticError(sym),
                              tree.pos(), site, sym.name, true);
                }
            }
            if (!allowStaticInterfaceMethods && sitesym.isInterface() &&
                    sym.isStatic() && sym.kind == MTH) {
                log.error(DiagnosticFlag.SOURCE_LEVEL, tree.pos(), "static.intf.method.invoke.not.supported.in.source", sourceName);
            }
        } else if (sym.kind != ERR &&
                   (sym.flags() & STATIC) != 0 &&
                   sym.name != names._class) {
            // If the qualified item is not a type and the selected item is static, report
            // a warning. Make allowance for the class of an array type e.g. Object[].class)
            chk.warnStatic(tree, "static.not.qualified.by.type",
                           sym.kind.kindName(), sym.owner);
        }

        // If we are selecting an instance member via a `super', ...
        if (env.info.selectSuper && (sym.flags() & STATIC) == 0) {

            // Check that super-qualified symbols are not abstract (JLS)
            rs.checkNonAbstract(tree.pos(), sym);

            if (site.isRaw()) {
                // Determine argument types for site.
                Type site1 = types.asSuper(env.enclClass.sym.type, site.tsym);
                if (site1 != null) site = site1;
            }
        }

        if (env.info.isSerializable) {
            chk.checkAccessFromSerializableElement(tree, env.info.isLambda);
        }

        env.info.selectSuper = selectSuperPrev;
        result = checkId(tree, site, sym, env, resultInfo);
    }
    //where
        /** Determine symbol referenced by a Select expression,
         *
         *  @param tree   The select tree.
         *  @param site   The type of the selected expression,
         *  @param env    The current environment.
         *  @param resultInfo The current result.
         */
        private Symbol selectSym(JCFieldAccess tree,
                                 Symbol location,
                                 Type site,
                                 Env<AttrContext> env,
                                 ResultInfo resultInfo) {
            DiagnosticPosition pos = tree.pos();
            Name name = tree.name;
            switch (site.getTag()) {
            case PACKAGE:
                return rs.accessBase(
                    rs.findIdentInPackage(env, site.tsym, name, resultInfo.pkind),
                    pos, location, site, name, true);
            case ARRAY:
            case CLASS:
                if (resultInfo.pt.hasTag(METHOD) || resultInfo.pt.hasTag(FORALL)) {
                    return rs.resolveQualifiedMethod(
                        pos, env, location, site, name, resultInfo.pt.getParameterTypes(), resultInfo.pt.getTypeArguments());
                } else if (name == names._this || name == names._super) {
                    return rs.resolveSelf(pos, env, site.tsym, name);
                } else if (name == names._class) {
                    // In this case, we have already made sure in
                    // visitSelect that qualifier expression is a type.
                    Type t = syms.classType;
                    JCList<Type> typeargs = JCList.of(types.erasure(site));
                    t = new ClassType(t.getEnclosingType(), typeargs, t.tsym);
                    return new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    // We are seeing a plain identifier as selector.
                    Symbol sym = rs.findIdentInType(env, site, name, resultInfo.pkind);
                        sym = rs.accessBase(sym, pos, location, site, name, true);
                    return sym;
                }
            case WILDCARD:
                throw new AssertionError(tree);
            case TYPEVAR:
                // Normally, site.getUpperBound() shouldn't be null.
                // It should only happen during memberEnter/attribBase
                // when determining the super type which *must* beac
                // done before attributing the type variables.  In
                // other words, we are seeing this illegal program:
                // class B<T> extends A<T.foo> {}
                Symbol sym = (site.getUpperBound() != null)
                    ? selectSym(tree, location, capture(site.getUpperBound()), env, resultInfo)
                    : null;
                if (sym == null) {
                    log.error(pos, "type.var.cant.be.deref");
                    return syms.errSymbol;
                } else {
                    Symbol sym2 = (sym.flags() & Flags.PRIVATE) != 0 ?
                        rs.new AccessError(env, site, sym) :
                                sym;
                    rs.accessBase(sym2, pos, location, site, name, true);
                    return sym;
                }
            case ERROR:
                // preserve identifier names through errors
                return types.createErrorType(name, site.tsym, site).tsym;
            default:
                // The qualifier expression is of a primitive type -- only
                // .class is allowed for these.
                if (name == names._class) {
                    // In this case, we have already made sure in Select that
                    // qualifier expression is a type.
                    Type t = syms.classType;
                    Type arg = types.boxedClass(site).type;
                    t = new ClassType(t.getEnclosingType(), JCList.of(arg), t.tsym);
                    return new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    log.error(pos, "cant.deref", site);
                    return syms.errSymbol;
                }
            }
        }

        /** Determine type of identifier or select expression and check that
         *  (1) the referenced symbol is not deprecated
         *  (2) the symbol's type is safe (@see checkSafe)
         *  (3) if symbol is a variable, check that its type and kind are
         *      compatible with the prototype and protokind.
         *  (4) if symbol is an instance field of a raw type,
         *      which is being assigned to, issue an unchecked warning if its
         *      type changes under erasure.
         *  (5) if symbol is an instance method of a raw type, issue an
         *      unchecked warning if its argument types change under erasure.
         *  If checks succeed:
         *    If symbol is a constant, return its constant type
         *    else if symbol is a method, return its result type
         *    otherwise return its type.
         *  Otherwise return errType.
         *
         *  @param tree       The syntax tree representing the identifier
         *  @param site       If this is a select, the type of the selected
         *                    expression, otherwise the type of the current class.
         *  @param sym        The symbol representing the identifier.
         *  @param env        The current environment.
         *  @param resultInfo    The expected result
         */
        Type checkId(JCTree tree,
                     Type site,
                     Symbol sym,
                     Env<AttrContext> env,
                     ResultInfo resultInfo) {
            return (resultInfo.pt.hasTag(FORALL) || resultInfo.pt.hasTag(METHOD)) ?
                    checkMethodId(tree, site, sym, env, resultInfo) :
                    checkIdInternal(tree, site, sym, resultInfo.pt, env, resultInfo);
        }

        Type checkMethodId(JCTree tree,
                     Type site,
                     Symbol sym,
                     Env<AttrContext> env,
                     ResultInfo resultInfo) {
            boolean isPolymorhicSignature =
                (sym.baseSymbol().flags() & SIGNATURE_POLYMORPHIC) != 0;
            return isPolymorhicSignature ?
                    checkSigPolyMethodId(tree, site, sym, env, resultInfo) :
                    checkMethodIdInternal(tree, site, sym, env, resultInfo);
        }

        Type checkSigPolyMethodId(JCTree tree,
                     Type site,
                     Symbol sym,
                     Env<AttrContext> env,
                     ResultInfo resultInfo) {
            //recover original symbol for signature polymorphic methods
            checkMethodIdInternal(tree, site, sym.baseSymbol(), env, resultInfo);
            env.info.pendingResolutionPhase = Resolve.MethodResolutionPhase.BASIC;
            return sym.type;
        }

        Type checkMethodIdInternal(JCTree tree,
                     Type site,
                     Symbol sym,
                     Env<AttrContext> env,
                     ResultInfo resultInfo) {
            if (resultInfo.pkind.contains(KindSelector.POLY)) {
                Type pt = resultInfo.pt.map(deferredAttr.new RecoveryDeferredTypeMap(AttrMode.SPECULATIVE, sym, env.info.pendingResolutionPhase));
                Type owntype = checkIdInternal(tree, site, sym, pt, env, resultInfo);
                resultInfo.pt.map(deferredAttr.new RecoveryDeferredTypeMap(AttrMode.CHECK, sym, env.info.pendingResolutionPhase));
                return owntype;
            } else {
                return checkIdInternal(tree, site, sym, resultInfo.pt, env, resultInfo);
            }
        }

        Type checkIdInternal(JCTree tree,
                     Type site,
                     Symbol sym,
                     Type pt,
                     Env<AttrContext> env,
                     ResultInfo resultInfo) {
            if (pt.isErroneous()) {
                return types.createErrorType(site);
            }
            Type owntype; // The computed type of this identifier occurrence.
            switch (sym.kind) {
            case TYP:
                // For types, the computed type equals the symbol's type,
                // except for two situations:
                owntype = sym.type;
                if (owntype.hasTag(CLASS)) {
                    chk.checkForBadAuxiliaryClassAccess(tree.pos(), env, (ClassSymbol)sym);
                    Type ownOuter = owntype.getEnclosingType();

                    // (a) If the symbol's type is parameterized, erase it
                    // because no type parameters were given.
                    // We recover generic outer type later in visitTypeApply.
                    if (owntype.tsym.type.getTypeArguments().nonEmpty()) {
                        owntype = types.erasure(owntype);
                    }

                    // (b) If the symbol's type is an inner class, then
                    // we have to interpret its outer type as a superclass
                    // of the site type. Example:
                    //
                    // class Tree<A> { class Visitor { ... } }
                    // class PointTree extends Tree<Point> { ... }
                    // ...PointTree.Visitor...
                    //
                    // Then the type of the last expression above is
                    // Tree<Point>.Visitor.
                    else if (ownOuter.hasTag(CLASS) && site != ownOuter) {
                        Type normOuter = site;
                        if (normOuter.hasTag(CLASS)) {
                            normOuter = types.asEnclosingSuper(site, ownOuter.tsym);
                        }
                        if (normOuter == null) // perhaps from an import
                            normOuter = types.erasure(ownOuter);
                        if (normOuter != ownOuter)
                            owntype = new ClassType(
                                normOuter, JCList.nil(), owntype.tsym,
                                owntype.getMetadata());
                    }
                }
                break;
            case VAR:
                VarSymbol v = (VarSymbol)sym;
                // Test (4): if symbol is an instance field of a raw type,
                // which is being assigned to, issue an unchecked warning if
                // its type changes under erasure.
                if (KindSelector.ASG.subset(pkind()) &&
                    v.owner.kind == TYP &&
                    (v.flags() & STATIC) == 0 &&
                    (site.hasTag(CLASS) || site.hasTag(TYPEVAR))) {
                    Type s = types.asOuterSuper(site, v.owner);
                    if (s != null &&
                        s.isRaw() &&
                        !types.isSameType(v.type, v.erasure(types))) {
                        chk.warnUnchecked(tree.pos(),
                                          "unchecked.assign.to.var",
                                          v, s);
                    }
                }
                // The computed type of a variable is the type of the
                // variable symbol, taken as a member of the site type.
                owntype = (sym.owner.kind == TYP &&
                           sym.name != names._this && sym.name != names._super)
                    ? types.memberType(site, sym)
                    : sym.type;

                // If the variable is a constant, record constant value in
                // computed type.
                if (v.getConstValue() != null && isStaticReference(tree))
                    owntype = owntype.constType(v.getConstValue());

                if (resultInfo.pkind == KindSelector.VAL) {
                    owntype = capture(owntype); // capture "names as expressions"
                }
                break;
            case MTH: {
                owntype = checkMethod(site, sym,
                        new ResultInfo(resultInfo.pkind, resultInfo.pt.getReturnType(), resultInfo.checkContext, resultInfo.checkMode),
                        env, TreeInfo.args(env.tree), resultInfo.pt.getParameterTypes(),
                        resultInfo.pt.getTypeArguments());
                break;
            }
            case PCK: case ERR:
                owntype = sym.type;
                break;
            default:
                throw new AssertionError("unexpected kind: " + sym.kind +
                                         " in tree " + tree);
            }

            // Emit a `deprecation' warning if symbol is deprecated.
            // (for constructors (but not for constructor references), the error
            // was given when the constructor was resolved)

            if (sym.name != names.init || tree.hasTag(REFERENCE)) {
                chk.checkDeprecated(tree.pos(), env.info.scope.owner, sym);
                chk.checkSunAPI(tree.pos(), sym);
                chk.checkProfile(tree.pos(), sym);
            }

            // If symbol is a variable, check that its type and
            // kind are compatible with the prototype and protokind.
            return check(tree, owntype, sym.kind.toSelector(), resultInfo);
        }

        /** Check that variable is initialized and evaluate the variable's
         *  initializer, if not yet done. Also check that variable is not
         *  referenced before it is defined.
         *  @param tree    The tree making up the variable reference.
         *  @param env     The current environment.
         *  @param v       The variable's symbol.
         */
        private void checkInit(JCTree tree,
                               Env<AttrContext> env,
                               VarSymbol v,
                               boolean onlyWarning) {
            // A forward reference is diagnosed if the declaration position
            // of the variable is greater than the current tree position
            // and the tree and variable definition occur in the same class
            // definition.  Note that writes don't count as references.
            // This check applies only to class and instance
            // variables.  Local variables follow different scope rules,
            // and are subject to definite assignment checking.
            Env<AttrContext> initEnv = enclosingInitEnv(env);
            if (initEnv != null &&
                (initEnv.info.enclVar == v || v.pos > tree.pos) &&
                v.owner.kind == TYP &&
                v.owner == env.info.scope.owner.enclClass() &&
                ((v.flags() & STATIC) != 0) == Resolve.isStatic(env) &&
                (!env.tree.hasTag(ASSIGN) ||
                 TreeInfo.skipParens(((JCAssign) env.tree).lhs) != tree)) {
                String suffix = (initEnv.info.enclVar == v) ?
                                "self.ref" : "forward.ref";
                if (!onlyWarning || isStaticEnumField(v)) {
                    log.error(tree.pos(), "illegal." + suffix);
                } else if (useBeforeDeclarationWarning) {
                    log.warning(tree.pos(), suffix, v);
                }
            }

            v.getConstValue(); // ensure initializer is evaluated

            checkEnumInitializer(tree, env, v);
        }

        /**
         * Returns the enclosing init environment associated with this env (if any). An init env
         * can be either a field declaration env or a static/instance initializer env.
         */
        Env<AttrContext> enclosingInitEnv(Env<AttrContext> env) {
            while (true) {
                switch (env.tree.getTag()) {
                    case VARDEF:
                        JCVariableDecl vdecl = (JCVariableDecl)env.tree;
                        if (vdecl.sym.owner.kind == TYP) {
                            //field
                            return env;
                        }
                        break;
                    case BLOCK:
                        if (env.next.tree.hasTag(CLASSDEF)) {
                            //instance/static initializer
                            return env;
                        }
                        break;
                    case METHODDEF:
                    case CLASSDEF:
                    case TOPLEVEL:
                        return null;
                }
                Assert.checkNonNull(env.next);
                env = env.next;
            }
        }

        /**
         * Check for illegal references to static members of enum.  In
         * an enum type, constructors and initializers may not
         * reference its static members unless they are constant.
         *
         * @param tree    The tree making up the variable reference.
         * @param env     The current environment.
         * @param v       The variable's symbol.
         * @jls  section 8.9 Enums
         */
        private void checkEnumInitializer(JCTree tree, Env<AttrContext> env, VarSymbol v) {
            // JLS:
            //
            // "It is a compile-time error to reference a static field
            // of an enum type that is not a compile-time constant
            // (15.28) from constructors, instance initializer blocks,
            // or instance variable initializer expressions of that
            // type. It is a compile-time error for the constructors,
            // instance initializer blocks, or instance variable
            // initializer expressions of an enum constant e to refer
            // to itself or to an enum constant of the same type that
            // is declared to the right of e."
            if (isStaticEnumField(v)) {
                ClassSymbol enclClass = env.info.scope.owner.enclClass();

                if (enclClass == null || enclClass.owner == null)
                    return;

                // See if the enclosing class is the enum (or a
                // subclass thereof) declaring v.  If not, this
                // reference is OK.
                if (v.owner != enclClass && !types.isSubtype(enclClass.type, v.owner.type))
                    return;

                // If the reference isn't from an initializer, then
                // the reference is OK.
                if (!Resolve.isInitializer(env))
                    return;

                log.error(tree.pos(), "illegal.enum.static.ref");
            }
        }

        /** Is the given symbol a static, non-constant field of an Enum?
         *  Note: enum literals should not be regarded as such
         */
        private boolean isStaticEnumField(VarSymbol v) {
            return Flags.isEnum(v.owner) &&
                   Flags.isStatic(v) &&
                   !Flags.isConstant(v) &&
                   v.name != names._class;
        }

    /**
     * Check that method arguments conform to its instantiation.
     **/
    public Type checkMethod(Type site,
                            final Symbol sym,
                            ResultInfo resultInfo,
                            Env<AttrContext> env,
                            final JCList<JCExpression> argtrees,
                            JCList<Type> argtypes,
                            JCList<Type> typeargtypes) {
        // Test (5): if symbol is an instance method of a raw type, issue
        // an unchecked warning if its argument types change under erasure.
        if ((sym.flags() & STATIC) == 0 &&
            (site.hasTag(CLASS) || site.hasTag(TYPEVAR))) {
            Type s = types.asOuterSuper(site, sym.owner);
            if (s != null && s.isRaw() &&
                !types.isSameTypes(sym.type.getParameterTypes(),
                                   sym.erasure(types).getParameterTypes())) {
                chk.warnUnchecked(env.tree.pos(),
                                  "unchecked.call.mbr.of.raw.type",
                                  sym, s);
            }
        }

        if (env.info.defaultSuperCallSite != null) {
            for (Type sup : types.interfaces(env.enclClass.type).prepend(types.supertype((env.enclClass.type)))) {
                if (!sup.tsym.isSubClass(sym.enclClass(), types) ||
                        types.isSameType(sup, env.info.defaultSuperCallSite)) continue;
                JCList<MethodSymbol> icand_sup =
                        types.interfaceCandidates(sup, (MethodSymbol)sym);
                if (icand_sup.nonEmpty() &&
                        icand_sup.head != sym &&
                        icand_sup.head.overrides(sym, icand_sup.head.enclClass(), types, true)) {
                    log.error(env.tree.pos(), "illegal.default.super.call", env.info.defaultSuperCallSite,
                        diags.fragment("overridden.default", sym, sup));
                    break;
                }
            }
            env.info.defaultSuperCallSite = null;
        }

        if (sym.isStatic() && site.isInterface() && env.tree.hasTag(APPLY)) {
            JCMethodInvocation app = (JCMethodInvocation)env.tree;
            if (app.meth.hasTag(SELECT) &&
                    !TreeInfo.isStaticSelector(((JCFieldAccess)app.meth).selected, names)) {
                log.error(env.tree.pos(), "illegal.static.intf.meth.call", site);
            }
        }

        // Compute the identifier's instantiated type.
        // For methods, we need to compute the instance type by
        // Resolve.instantiate from the symbol's type as well as
        // any type arguments and value arguments.
        Warner noteWarner = new Warner();
        try {
            Type owntype = rs.checkMethod(
                    env,
                    site,
                    sym,
                    resultInfo,
                    argtypes,
                    typeargtypes,
                    noteWarner);

            DeferredAttr.DeferredTypeMap checkDeferredMap =
                deferredAttr.new DeferredTypeMap(DeferredAttr.AttrMode.CHECK, sym, env.info.pendingResolutionPhase);

            argtypes = argtypes.map(checkDeferredMap);

            if (noteWarner.hasNonSilentLint(LintCategory.UNCHECKED)) {
                chk.warnUnchecked(env.tree.pos(),
                        "unchecked.meth.invocation.applied",
                        kindName(sym),
                        sym.name,
                        rs.methodArguments(sym.type.getParameterTypes()),
                        rs.methodArguments(argtypes.map(checkDeferredMap)),
                        kindName(sym.location()),
                        sym.location());
                if (resultInfo.pt != Infer.anyPoly ||
                        !owntype.hasTag(METHOD) ||
                        !owntype.isPartial()) {
                    //if this is not a partially inferred method type, erase return type. Otherwise,
                    //erasure is carried out in PartiallyInferredMethodType.check().
                    owntype = new MethodType(owntype.getParameterTypes(),
                            types.erasure(owntype.getReturnType()),
                            types.erasure(owntype.getThrownTypes()),
                            syms.methodClass);
                }
            }

            PolyKind pkind = (sym.type.hasTag(FORALL) &&
                 sym.type.getReturnType().containsAny(((ForAll)sym.type).tvars)) ?
                 PolyKind.POLY : PolyKind.STANDALONE;
            TreeInfo.setPolyKind(env.tree, pkind);

            return (resultInfo.pt == Infer.anyPoly) ?
                    owntype :
                    chk.checkMethod(owntype, sym, env, argtrees, argtypes, env.info.lastResolveVarargs(),
                            resultInfo.checkContext.inferenceContext());
        } catch (Infer.InferenceException ex) {
            //invalid target type - propagate exception outwards or report error
            //depending on the current check context
            resultInfo.checkContext.report(env.tree.pos(), ex.getDiagnostic());
            return types.createErrorType(site);
        } catch (Resolve.InapplicableMethodException ex) {
            final JCDiagnostic diag = ex.getDiagnostic();
            Resolve.InapplicableSymbolError errSym = rs.new InapplicableSymbolError(null) {
                @Override
                protected Pair<Symbol, JCDiagnostic> errCandidate() {
                    return new Pair<>(sym, diag);
                }
            };
            JCList<Type> argtypes2 = argtypes.map(
                    rs.new ResolveDeferredRecoveryMap(AttrMode.CHECK, sym, env.info.pendingResolutionPhase));
            JCDiagnostic errDiag = errSym.getDiagnostic(JCDiagnostic.DiagnosticType.ERROR,
                    env.tree, sym, site, sym.name, argtypes2, typeargtypes);
            log.report(errDiag);
            return types.createErrorType(site);
        }
    }

    public void visitLiteral(JCLiteral tree) {
        result = check(tree, litType(tree.typetag).constType(tree.value),
                KindSelector.VAL, resultInfo);
    }
    //where
    /** Return the type of a literal with given type tag.
     */
    Type litType(TypeTag tag) {
        return (tag == CLASS) ? syms.stringType : syms.typeOfTag[tag.ordinal()];
    }

    public void visitTypeIdent(JCPrimitiveTypeTree tree) {
        result = check(tree, syms.typeOfTag[tree.typetag.ordinal()], KindSelector.TYP, resultInfo);
    }

    public void visitTypeArray(JCArrayTypeTree tree) {
        Type etype = attribType(tree.elemtype, env);
        Type type = new ArrayType(etype, syms.arrayClass);
        result = check(tree, type, KindSelector.TYP, resultInfo);
    }

    /** Visitor method for parameterized types.
     *  Bound checking is left until later, since types are attributed
     *  before supertype structure is completely known
     */
    public void visitTypeApply(JCTypeApply tree) {
        Type owntype = types.createErrorType(tree.type);

        // Attribute functor part of application and make sure it's a class.
        Type clazztype = chk.checkClassType(tree.clazz.pos(), attribType(tree.clazz, env));

        // Attribute type parameters
        JCList<Type> actuals = attribTypes(tree.arguments, env);

        if (clazztype.hasTag(CLASS)) {
            JCList<Type> formals = clazztype.tsym.type.getTypeArguments();
            if (actuals.isEmpty()) //diamond
                actuals = formals;

            if (actuals.length() == formals.length()) {
                JCList<Type> a = actuals;
                JCList<Type> f = formals;
                while (a.nonEmpty()) {
                    a.head = a.head.withTypeVar(f.head);
                    a = a.tail;
                    f = f.tail;
                }
                // Compute the proper generic outer
                Type clazzOuter = clazztype.getEnclosingType();
                if (clazzOuter.hasTag(CLASS)) {
                    Type site;
                    JCExpression clazz = TreeInfo.typeIn(tree.clazz);
                    if (clazz.hasTag(IDENT)) {
                        site = env.enclClass.sym.type;
                    } else if (clazz.hasTag(SELECT)) {
                        site = ((JCFieldAccess) clazz).selected.type;
                    } else throw new AssertionError(""+tree);
                    if (clazzOuter.hasTag(CLASS) && site != clazzOuter) {
                        if (site.hasTag(CLASS))
                            site = types.asOuterSuper(site, clazzOuter.tsym);
                        if (site == null)
                            site = types.erasure(clazzOuter);
                        clazzOuter = site;
                    }
                }
                owntype = new ClassType(clazzOuter, actuals, clazztype.tsym,
                                        clazztype.getMetadata());
            } else {
                if (formals.length() != 0) {
                    log.error(tree.pos(), "wrong.number.type.args",
                              Integer.toString(formals.length()));
                } else {
                    log.error(tree.pos(), "type.doesnt.take.params", clazztype.tsym);
                }
                owntype = types.createErrorType(tree.type);
            }
        }
        result = check(tree, owntype, KindSelector.TYP, resultInfo);
    }

    public void visitTypeUnion(JCTypeUnion tree) {
        ListBuffer<Type> multicatchTypes = new ListBuffer<>();
        ListBuffer<Type> all_multicatchTypes = null; // lazy, only if needed
        for (JCExpression typeTree : tree.alternatives) {
            Type ctype = attribType(typeTree, env);
            ctype = chk.checkType(typeTree.pos(),
                          chk.checkClassType(typeTree.pos(), ctype),
                          syms.throwableType);
            if (!ctype.isErroneous()) {
                //check that alternatives of a union type are pairwise
                //unrelated w.r.t. subtyping
                if (chk.intersects(ctype,  multicatchTypes.toList())) {
                    for (Type t : multicatchTypes) {
                        boolean sub = types.isSubtype(ctype, t);
                        boolean sup = types.isSubtype(t, ctype);
                        if (sub || sup) {
                            //assume 'a' <: 'b'
                            Type a = sub ? ctype : t;
                            Type b = sub ? t : ctype;
                            log.error(typeTree.pos(), "multicatch.types.must.be.disjoint", a, b);
                        }
                    }
                }
                multicatchTypes.append(ctype);
                if (all_multicatchTypes != null)
                    all_multicatchTypes.append(ctype);
            } else {
                if (all_multicatchTypes == null) {
                    all_multicatchTypes = new ListBuffer<>();
                    all_multicatchTypes.appendList(multicatchTypes);
                }
                all_multicatchTypes.append(ctype);
            }
        }
        Type t = check(tree, types.lub(multicatchTypes.toList()),
                KindSelector.TYP, resultInfo.dup(CheckMode.NO_TREE_UPDATE));
        if (t.hasTag(CLASS)) {
            JCList<Type> alternatives =
                ((all_multicatchTypes == null) ? multicatchTypes : all_multicatchTypes).toList();
            t = new UnionClassType((ClassType) t, alternatives);
        }
        tree.type = result = t;
    }

    public void visitTypeIntersection(JCTypeIntersection tree) {
        attribTypes(tree.bounds, env);
        tree.type = result = checkIntersection(tree, tree.bounds);
    }

    public void visitTypeParameter(JCTypeParameter tree) {
        TypeVar typeVar = (TypeVar) tree.type;

        if (tree.annotations != null && tree.annotations.nonEmpty()) {
            annotate.annotateTypeParameterSecondStage(tree, tree.annotations);
        }

        if (!typeVar.bound.isErroneous()) {
            //fixup type-parameter bound computed in 'attribTypeVariables'
            typeVar.bound = checkIntersection(tree, tree.bounds);
        }
    }

    Type checkIntersection(JCTree tree, JCList<JCExpression> bounds) {
        Set<Type> boundSet = new HashSet<>();
        if (bounds.nonEmpty()) {
            // accept class or interface or typevar as first bound.
            bounds.head.type = checkBase(bounds.head.type, bounds.head, env, false, false, false);
            boundSet.add(types.erasure(bounds.head.type));
            if (bounds.head.type.isErroneous()) {
                return bounds.head.type;
            }
            else if (bounds.head.type.hasTag(TYPEVAR)) {
                // if first bound was a typevar, do not accept further bounds.
                if (bounds.tail.nonEmpty()) {
                    log.error(bounds.tail.head.pos(),
                              "type.var.may.not.be.followed.by.other.bounds");
                    return bounds.head.type;
                }
            } else {
                // if first bound was a class or interface, accept only interfaces
                // as further bounds.
                for (JCExpression bound : bounds.tail) {
                    bound.type = checkBase(bound.type, bound, env, false, true, false);
                    if (bound.type.isErroneous()) {
                        bounds = JCList.of(bound);
                    }
                    else if (bound.type.hasTag(CLASS)) {
                        chk.checkNotRepeated(bound.pos(), types.erasure(bound.type), boundSet);
                    }
                }
            }
        }

        if (bounds.length() == 0) {
            return syms.objectType;
        } else if (bounds.length() == 1) {
            return bounds.head.type;
        } else {
            Type owntype = types.makeIntersectionType(TreeInfo.types(bounds));
            // ... the variable's bound is a class type flagged COMPOUND
            // (see comment for TypeVar.bound).
            // In this case, generate a class tree that represents the
            // bound class, ...
            JCExpression extending;
            JCList<JCExpression> implementing;
            if (!bounds.head.type.isInterface()) {
                extending = bounds.head;
                implementing = bounds.tail;
            } else {
                extending = null;
                implementing = bounds;
            }
            JCClassDecl cd = make.at(tree).ClassDef(
                make.Modifiers(PUBLIC | ABSTRACT),
                names.empty, JCList.nil(),
                extending, implementing, JCList.nil());

            ClassSymbol c = (ClassSymbol)owntype.tsym;
            Assert.check((c.flags() & COMPOUND) != 0);
            cd.sym = c;
            c.sourcefile = env.toplevel.sourcefile;

            // ... and attribute the bound class
            c.flags_field |= UNATTRIBUTED;
            Env<AttrContext> cenv = enter.classEnv(cd, env);
            typeEnvs.put(c, cenv);
            attribClass(c);
            return owntype;
        }
    }

    public void visitWildcard(JCWildcard tree) {
        //- System.err.println("visitWildcard("+tree+");");//DEBUG
        Type type = (tree.kind.kind == BoundKind.UNBOUND)
            ? syms.objectType
            : attribType(tree.inner, env);
        result = check(tree, new WildcardType(chk.checkRefType(tree.pos(), type),
                                              tree.kind.kind,
                                              syms.boundClass),
                KindSelector.TYP, resultInfo);
    }

    public void visitAnnotation(JCAnnotation tree) {
        Assert.error("should be handled in annotate");
    }

    public void visitAnnotatedType(JCAnnotatedType tree) {
        attribAnnotationTypes(tree.annotations, env);
        Type underlyingType = attribType(tree.underlyingType, env);
        Type annotatedType = underlyingType.annotatedType(Annotations.TO_BE_SET);

        if (!env.info.isNewClass)
            annotate.annotateTypeSecondStage(tree, tree.annotations, annotatedType);
        result = tree.type = annotatedType;
    }

    public void visitErroneous(JCErroneous tree) {
        if (tree.errs != null)
            for (JCTree err : tree.errs)
                attribTree(err, env, new ResultInfo(KindSelector.ERR, pt()));
        result = tree.type = syms.errType;
    }

    /** Default visitor method for all other trees.
     */
    public void visitTree(JCTree tree) {
        throw new AssertionError();
    }

    /**
     * Attribute an env for either a top level tree or class or module declaration.
     */
    public void attrib(Env<AttrContext> env) {
        switch (env.tree.getTag()) {
            case MODULEDEF:
                attribModule(env.tree.pos(), ((JCModuleDecl)env.tree).sym);
                break;
            case TOPLEVEL:
                attribTopLevel(env);
                break;
            case PACKAGEDEF:
                attribPackage(env.tree.pos(), ((JCPackageDecl) env.tree).packge);
                break;
            default:
                attribClass(env.tree.pos(), env.enclClass.sym);
        }
    }

    /**
     * Attribute a top level tree. These trees are encountered when the
     * package declaration has annotations.
     */
    public void attribTopLevel(Env<AttrContext> env) {
        JCCompilationUnit toplevel = env.toplevel;
        try {
            annotate.flush();
        } catch (CompletionFailure ex) {
            chk.completionError(toplevel.pos(), ex);
        }
    }

    public void attribPackage(DiagnosticPosition pos, PackageSymbol p) {
        try {
            annotate.flush();
            attribPackage(p);
        } catch (CompletionFailure ex) {
            chk.completionError(pos, ex);
        }
    }

    void attribPackage(PackageSymbol p) {
        Env<AttrContext> env = typeEnvs.get(p);
        chk.checkDeprecatedAnnotation(((JCPackageDecl) env.tree).pid.pos(), p);
    }

    public void attribModule(DiagnosticPosition pos, ModuleSymbol m) {
        try {
            annotate.flush();
            attribModule(m);
        } catch (CompletionFailure ex) {
            chk.completionError(pos, ex);
        }
    }

    void attribModule(ModuleSymbol m) {
        // Get environment current at the point of module definition.
        Env<AttrContext> env = enter.typeEnvs.get(m);
        attribStat(env.tree, env);
    }

    /** Main method: attribute class definition associated with given class symbol.
     *  reporting completion failures at the given position.
     *  @param pos The source position at which completion errors are to be
     *             reported.
     *  @param c   The class symbol whose definition will be attributed.
     */
    public void attribClass(DiagnosticPosition pos, ClassSymbol c) {
        try {
            annotate.flush();
            attribClass(c);
        } catch (CompletionFailure ex) {
            chk.completionError(pos, ex);
        }
    }

    /** Attribute class definition associated with given class symbol.
     *  @param c   The class symbol whose definition will be attributed.
     */
    void attribClass(ClassSymbol c) throws CompletionFailure {
        if (c.type.hasTag(ERROR)) return;

        // Check for cycles in the inheritance graph, which can arise from
        // ill-formed class files.
        chk.checkNonCyclic(null, c.type);

        Type st = types.supertype(c.type);
        if ((c.flags_field & Flags.COMPOUND) == 0) {
            // First, attribute superclass.
            if (st.hasTag(CLASS))
                attribClass((ClassSymbol)st.tsym);

            // Next attribute owner, if it is a class.
            if (c.owner.kind == TYP && c.owner.type.hasTag(CLASS))
                attribClass((ClassSymbol)c.owner);
        }

        // The previous operations might have attributed the current class
        // if there was a cycle. So we test first whether the class is still
        // UNATTRIBUTED.
        if ((c.flags_field & UNATTRIBUTED) != 0) {
            c.flags_field &= ~UNATTRIBUTED;

            // Get environment current at the point of class definition.
            Env<AttrContext> env = typeEnvs.get(c);

            // The info.lint field in the envs stored in typeEnvs is deliberately uninitialized,
            // because the annotations were not available at the time the env was created. Therefore,
            // we look up the environment chain for the first enclosing environment for which the
            // lint value is set. Typically, this is the parent env, but might be further if there
            // are any envs created as a result of TypeParameter nodes.
            Env<AttrContext> lintEnv = env;
            while (lintEnv.info.lint == null)
                lintEnv = lintEnv.next;

            // Having found the enclosing lint value, we can initialize the lint value for this class
            env.info.lint = lintEnv.info.lint.augment(c);

            Lint prevLint = chk.setLint(env.info.lint);
            JavaFileObject prev = log.useSource(c.sourcefile);
            ResultInfo prevReturnRes = env.info.returnResult;

            try {
                deferredLintHandler.flush(env.tree);
                env.info.returnResult = null;
                // java.lang.Enum may not be subclassed by a non-enum
                if (st.tsym == syms.enumSym &&
                    ((c.flags_field & (Flags.ENUM|Flags.COMPOUND)) == 0))
                    log.error(env.tree.pos(), "enum.no.subclassing");

                // Enums may not be extended by source-level classes
                if (st.tsym != null &&
                    ((st.tsym.flags_field & Flags.ENUM) != 0) &&
                    ((c.flags_field & (Flags.ENUM | Flags.COMPOUND)) == 0)) {
                    log.error(env.tree.pos(), "enum.types.not.extensible");
                }

                if (isSerializable(c.type)) {
                    env.info.isSerializable = true;
                }

                attribClassBody(env, c);

                chk.checkDeprecatedAnnotation(env.tree.pos(), c);
                chk.checkClassOverrideEqualsAndHashIfNeeded(env.tree.pos(), c);
                chk.checkFunctionalInterface((JCClassDecl) env.tree, c);
                chk.checkLeaksNotAccessible(env, (JCClassDecl) env.tree);
            } finally {
                env.info.returnResult = prevReturnRes;
                log.useSource(prev);
                chk.setLint(prevLint);
            }

        }
    }

    public void visitImport(JCImport tree) {
        // nothing to do
    }

    public void visitModuleDef(JCModuleDecl tree) {
        tree.sym.completeUsesProvides();
        ModuleSymbol msym = tree.sym;
        Lint lint = env.outer.info.lint = env.outer.info.lint.augment(msym);
        Lint prevLint = chk.setLint(lint);
        chk.checkModuleName(tree);
        chk.checkDeprecatedAnnotation(tree, msym);

        try {
            deferredLintHandler.flush(tree.pos());
        } finally {
            chk.setLint(prevLint);
        }
    }

    /** Finish the attribution of a class. */
    private void attribClassBody(Env<AttrContext> env, ClassSymbol c) {
        JCClassDecl tree = (JCClassDecl)env.tree;
        Assert.check(c == tree.sym);

        // Validate type parameters, supertype and interfaces.
        attribStats(tree.typarams, env);
        if (!c.isAnonymous()) {
            //already checked if anonymous
            chk.validate(tree.typarams, env);
            chk.validate(tree.extending, env);
            chk.validate(tree.implementing, env);
        }

        c.markAbstractIfNeeded(types);

        // If this is a non-abstract class, check that it has no abstract
        // methods or unimplemented methods of an implemented interface.
        if ((c.flags() & (ABSTRACT | INTERFACE)) == 0) {
            chk.checkAllDefined(tree.pos(), c);
        }

        if ((c.flags() & ANNOTATION) != 0) {
            if (tree.implementing.nonEmpty())
                log.error(tree.implementing.head.pos(),
                          "cant.extend.intf.annotation");
            if (tree.typarams.nonEmpty())
                log.error(tree.typarams.head.pos(),
                          "intf.annotation.cant.have.type.params");

            // If this annotation type has a @Repeatable, validate
            Attribute.Compound repeatable = c.getAnnotationTypeMetadata().getRepeatable();
            // If this annotation type has a @Repeatable, validate
            if (repeatable != null) {
                // get diagnostic position for error reporting
                DiagnosticPosition cbPos = getDiagnosticPosition(tree, repeatable.type);
                Assert.checkNonNull(cbPos);

                chk.validateRepeatable(c, repeatable, cbPos);
            }
        } else {
            // Check that all extended classes and interfaces
            // are compatible (i.e. no two define methods with same arguments
            // yet different return types).  (JLS 8.4.6.3)
            chk.checkCompatibleSupertypes(tree.pos(), c.type);
            if (allowDefaultMethods) {
                chk.checkDefaultMethodClashes(tree.pos(), c.type);
            }
        }

        // Check that class does not import the same parameterized interface
        // with two different argument lists.
        chk.checkClassBounds(tree.pos(), c.type);

        tree.type = c.type;

        for (JCList<JCTypeParameter> l = tree.typarams;
             l.nonEmpty(); l = l.tail) {
             Assert.checkNonNull(env.info.scope.findFirst(l.head.name));
        }

        // Check that a generic class doesn't extend Throwable
        if (!c.type.allparams().isEmpty() && types.isSubtype(c.type, syms.throwableType))
            log.error(tree.extending.pos(), "generic.throwable");

        // Check that all methods which implement some
        // method conform to the method they implement.
        chk.checkImplementations(tree);

        //check that a resource implementing AutoCloseable cannot throw InterruptedException
        checkAutoCloseable(tree.pos(), env, c.type);

        for (JCList<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            // Attribute declaration
            attribStat(l.head, env);
            // Check that declarations in inner classes are not static (JLS 8.1.2)
            // Make an exception for static constants.
            if (c.owner.kind != PCK &&
                ((c.flags() & STATIC) == 0 || c.name == names.empty) &&
                (TreeInfo.flags(l.head) & (STATIC | INTERFACE)) != 0) {
                Symbol sym = null;
                if (l.head.hasTag(VARDEF)) sym = ((JCVariableDecl) l.head).sym;
                if (sym == null ||
                    sym.kind != VAR ||
                    ((VarSymbol) sym).getConstValue() == null)
                    log.error(l.head.pos(), "icls.cant.have.static.decl", c);
            }
        }

        // Check for cycles among non-initial constructors.
        chk.checkCyclicConstructors(tree);

        // Check for cycles among annotation elements.
        chk.checkNonCyclicElements(tree);

        // Check for proper use of serialVersionUID
        if (env.info.lint.isEnabled(LintCategory.SERIAL)
                && isSerializable(c.type)
                && (c.flags() & Flags.ENUM) == 0
                && !c.isAnonymous()
                && checkForSerial(c)) {
            checkSerialVersionUID(tree, c);
        }
        if (allowTypeAnnos) {
            // Correctly organize the postions of the type annotations
            typeAnnotations.organizeTypeAnnotationsBodies(tree);

            // Check type annotations applicability rules
            validateTypeAnnotations(tree, false);
        }
    }
        // where
        boolean checkForSerial(ClassSymbol c) {
            if ((c.flags() & ABSTRACT) == 0) {
                return true;
            } else {
                return c.members().anyMatch(anyNonAbstractOrDefaultMethod);
            }
        }

        public static final Filter<Symbol> anyNonAbstractOrDefaultMethod = s ->
                s.kind == MTH && (s.flags() & (DEFAULT | ABSTRACT)) != ABSTRACT;

        /** get a diagnostic position for an attribute of Type t, or null if attribute missing */
        private DiagnosticPosition getDiagnosticPosition(JCClassDecl tree, Type t) {
            for(JCList<JCAnnotation> al = tree.mods.annotations; !al.isEmpty(); al = al.tail) {
                if (types.isSameType(al.head.annotationType.type, t))
                    return al.head.pos();
            }

            return null;
        }

        /** check if a type is a subtype of Serializable, if that is available. */
        boolean isSerializable(Type t) {
            try {
                syms.serializableType.complete();
            }
            catch (CompletionFailure e) {
                return false;
            }
            return types.isSubtype(t, syms.serializableType);
        }

        /** Check that an appropriate serialVersionUID member is defined. */
        private void checkSerialVersionUID(JCClassDecl tree, ClassSymbol c) {

            // check for presence of serialVersionUID
            VarSymbol svuid = null;
            for (Symbol sym : c.members().getSymbolsByName(names.serialVersionUID)) {
                if (sym.kind == VAR) {
                    svuid = (VarSymbol)sym;
                    break;
                }
            }

            if (svuid == null) {
                log.warning(LintCategory.SERIAL,
                        tree.pos(), "missing.SVUID", c);
                return;
            }

            // check that it is static final
            if ((svuid.flags() & (STATIC | FINAL)) !=
                (STATIC | FINAL))
                log.warning(LintCategory.SERIAL,
                        TreeInfo.diagnosticPositionFor(svuid, tree), "improper.SVUID", c);

            // check that it is long
            else if (!svuid.type.hasTag(LONG))
                log.warning(LintCategory.SERIAL,
                        TreeInfo.diagnosticPositionFor(svuid, tree), "long.SVUID", c);

            // check constant
            else if (svuid.getConstValue() == null)
                log.warning(LintCategory.SERIAL,
                        TreeInfo.diagnosticPositionFor(svuid, tree), "constant.SVUID", c);
        }

    private Type capture(Type type) {
        return types.capture(type);
    }

    public void validateTypeAnnotations(JCTree tree, boolean sigOnly) {
        tree.accept(new TypeAnnotationsValidator(sigOnly));
    }
    //where
    private final class TypeAnnotationsValidator extends TreeScanner {

        private final boolean sigOnly;
        public TypeAnnotationsValidator(boolean sigOnly) {
            this.sigOnly = sigOnly;
        }

        public void visitAnnotation(JCAnnotation tree) {
            chk.validateTypeAnnotation(tree, false);
            super.visitAnnotation(tree);
        }
        public void visitAnnotatedType(JCAnnotatedType tree) {
            if (!tree.underlyingType.type.isErroneous()) {
                super.visitAnnotatedType(tree);
            }
        }
        public void visitTypeParameter(JCTypeParameter tree) {
            chk.validateTypeAnnotations(tree.annotations, true);
            scan(tree.bounds);
            // Don't call super.
            // This is needed because above we call validateTypeAnnotation with
            // false, which would forbid annotations on type parameters.
            // super.visitTypeParameter(tree);
        }
        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.recvparam != null &&
                    !tree.recvparam.vartype.type.isErroneous()) {
                checkForDeclarationAnnotations(tree.recvparam.mods.annotations,
                        tree.recvparam.vartype.type.tsym);
            }
            if (tree.restype != null && tree.restype.type != null) {
                validateAnnotatedType(tree.restype, tree.restype.type);
            }
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.restype);
                scan(tree.typarams);
                scan(tree.recvparam);
                scan(tree.params);
                scan(tree.thrown);
            } else {
                scan(tree.defaultValue);
                scan(tree.body);
            }
        }
        public void visitVarDef(final JCVariableDecl tree) {
            //System.err.println("validateTypeAnnotations.visitVarDef " + tree);
            if (tree.sym != null && tree.sym.type != null)
                validateAnnotatedType(tree.vartype, tree.sym.type);
            scan(tree.mods);
            scan(tree.vartype);
            if (!sigOnly) {
                scan(tree.init);
            }
        }
        public void visitTypeCast(JCTypeCast tree) {
            if (tree.clazz != null && tree.clazz.type != null)
                validateAnnotatedType(tree.clazz, tree.clazz.type);
            super.visitTypeCast(tree);
        }
        public void visitTypeTest(JCInstanceOf tree) {
            if (tree.clazz != null && tree.clazz.type != null)
                validateAnnotatedType(tree.clazz, tree.clazz.type);
            super.visitTypeTest(tree);
        }
        public void visitNewClass(JCNewClass tree) {
            if (tree.clazz != null && tree.clazz.type != null) {
                if (tree.clazz.hasTag(ANNOTATED_TYPE)) {
                    checkForDeclarationAnnotations(((JCAnnotatedType) tree.clazz).annotations,
                            tree.clazz.type.tsym);
                }
                if (tree.def != null) {
                    checkForDeclarationAnnotations(tree.def.mods.annotations, tree.clazz.type.tsym);
                }

                validateAnnotatedType(tree.clazz, tree.clazz.type);
            }
            super.visitNewClass(tree);
        }
        public void visitNewArray(JCNewArray tree) {
            if (tree.elemtype != null && tree.elemtype.type != null) {
                if (tree.elemtype.hasTag(ANNOTATED_TYPE)) {
                    checkForDeclarationAnnotations(((JCAnnotatedType) tree.elemtype).annotations,
                            tree.elemtype.type.tsym);
                }
                validateAnnotatedType(tree.elemtype, tree.elemtype.type);
            }
            super.visitNewArray(tree);
        }
        public void visitClassDef(JCClassDecl tree) {
            //System.err.println("validateTypeAnnotations.visitClassDef " + tree);
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.typarams);
                scan(tree.extending);
                scan(tree.implementing);
            }
            for (JCTree member : tree.defs) {
                if (member.hasTag(JCTreeTag.CLASSDEF)) {
                    continue;
                }
                scan(member);
            }
        }
        public void visitBlock(JCBlock tree) {
            if (!sigOnly) {
                scan(tree.stats);
            }
        }

        /* I would want to model this after
         * com.flint.tools.flintc.comp.Check.Validator.visitSelectInternal(JCFieldAccess)
         * and override visitSelect and visitTypeApply.
         * However, we only set the annotated type in the top-level type
         * of the symbol.
         * Therefore, we need to override each individual location where a type
         * can occur.
         */
        private void validateAnnotatedType(final JCTree errtree, final Type type) {
            //System.err.println("Attr.validateAnnotatedType: " + errtree + " type: " + type);

            if (type.isPrimitiveOrVoid()) {
                return;
            }

            JCTree enclTr = errtree;
            Type enclTy = type;

            boolean repeat = true;
            while (repeat) {
                if (enclTr.hasTag(TYPEAPPLY)) {
                    JCList<Type> tyargs = enclTy.getTypeArguments();
                    JCList<JCExpression> trargs = ((JCTypeApply)enclTr).getTypeArguments();
                    if (trargs.length() > 0) {
                        // Nothing to do for diamonds
                        if (tyargs.length() == trargs.length()) {
                            for (int i = 0; i < tyargs.length(); ++i) {
                                validateAnnotatedType(trargs.get(i), tyargs.get(i));
                            }
                        }
                        // If the lengths don't match, it's either a diamond
                        // or some nested type that redundantly provides
                        // type arguments in the tree.
                    }

                    // Look at the clazz part of a generic type
                    enclTr = ((JCTree.JCTypeApply)enclTr).clazz;
                }

                if (enclTr.hasTag(SELECT)) {
                    enclTr = ((JCTree.JCFieldAccess)enclTr).getExpression();
                    if (enclTy != null &&
                            !enclTy.hasTag(NONE)) {
                        enclTy = enclTy.getEnclosingType();
                    }
                } else if (enclTr.hasTag(ANNOTATED_TYPE)) {
                    JCAnnotatedType at = (JCTree.JCAnnotatedType) enclTr;
                    if (enclTy == null || enclTy.hasTag(NONE)) {
                        if (at.getAnnotations().size() == 1) {
                            log.error(at.underlyingType.pos(), "cant.type.annotate.scoping.1", at.getAnnotations().head.attribute);
                        } else {
                            ListBuffer<Attribute.Compound> comps = new ListBuffer<>();
                            for (JCAnnotation an : at.getAnnotations()) {
                                comps.add(an.attribute);
                            }
                            log.error(at.underlyingType.pos(), "cant.type.annotate.scoping", comps.toList());
                        }
                        repeat = false;
                    }
                    enclTr = at.underlyingType;
                    // enclTy doesn't need to be changed
                } else if (enclTr.hasTag(IDENT)) {
                    repeat = false;
                } else if (enclTr.hasTag(JCTree.JCTreeTag.WILDCARD)) {
                    JCWildcard wc = (JCWildcard) enclTr;
                    if (wc.getKind() == JCTree.Kind.EXTENDS_WILDCARD) {
                        validateAnnotatedType(wc.getBound(), ((WildcardType)enclTy).getExtendsBound());
                    } else if (wc.getKind() == JCTree.Kind.SUPER_WILDCARD) {
                        validateAnnotatedType(wc.getBound(), ((WildcardType)enclTy).getSuperBound());
                    } else {
                        // Nothing to do for UNBOUND
                    }
                    repeat = false;
                } else if (enclTr.hasTag(TYPEARRAY)) {
                    JCArrayTypeTree art = (JCArrayTypeTree) enclTr;
                    validateAnnotatedType(art.getType(), ((ArrayType)enclTy).getComponentType());
                    repeat = false;
                } else if (enclTr.hasTag(TYPEUNION)) {
                    JCTypeUnion ut = (JCTypeUnion) enclTr;
                    for (JCTree t : ut.getTypeAlternatives()) {
                        validateAnnotatedType(t, t.type);
                    }
                    repeat = false;
                } else if (enclTr.hasTag(TYPEINTERSECTION)) {
                    JCTypeIntersection it = (JCTypeIntersection) enclTr;
                    for (JCTree t : it.getBounds()) {
                        validateAnnotatedType(t, t.type);
                    }
                    repeat = false;
                } else if (enclTr.getKind() == JCTree.Kind.PRIMITIVE_TYPE ||
                           enclTr.getKind() == JCTree.Kind.ERRONEOUS) {
                    repeat = false;
                } else {
                    Assert.error("Unexpected tree: " + enclTr + " with kind: " + enclTr.getKind() +
                            " within: "+ errtree + " with kind: " + errtree.getKind());
                }
            }
        }

        private void checkForDeclarationAnnotations(JCList<? extends JCAnnotation> annotations,
                Symbol sym) {
            // Ensure that no declaration annotations are present.
            // Note that a tree type might be an AnnotatedType with
            // empty annotations, if only declaration annotations were given.
            // This method will raise an error for such a type.
            for (JCAnnotation ai : annotations) {
                if (!ai.type.isErroneous() &&
                        typeAnnotations.annotationTargetType(ai.attribute, sym) == TypeAnnotations.AnnotationType.DECLARATION) {
                 //   log.error(ai.pos(), Errors.AnnotationTypeNotApplicableToType(ai.type));
                }
            }
        }
    }

    // <editor-fold desc="post-attribution visitor">

    /**
     * Handle missing types/symbols in an AST. This routine is useful when
     * the compiler has encountered some errors (which might have ended up
     * terminating attribution abruptly); if the compiler is used in fail-over
     * mode (e.g. by an IDE) and the AST contains semantic errors, this routine
     * prevents NPE to be progagated during subsequent compilation steps.
     */
    public void postAttr(JCTree tree) {
        new PostAttrAnalyzer().scan(tree);
    }

    class PostAttrAnalyzer extends TreeScanner {

        private void initTypeIfNeeded(JCTree that) {
            if (that.type == null) {
                if (that.hasTag(METHODDEF)) {
                    that.type = dummyMethodType((JCMethodDecl)that);
                } else {
                    that.type = syms.unknownType;
                }
            }
        }

        /* Construct a dummy method type. If we have a method declaration,
         * and the declared return type is void, then use that return type
         * instead of UNKNOWN to avoid spurious error messages in lambda
         * bodies (see:JDK-8041704).
         */
        private Type dummyMethodType(JCMethodDecl md) {
            Type restype = syms.unknownType;
            if (md != null && md.restype.hasTag(TYPEIDENT)) {
                JCPrimitiveTypeTree prim = (JCPrimitiveTypeTree)md.restype;
                if (prim.typetag == VOID)
                    restype = syms.voidType;
            }
            return new MethodType(JCList.nil(), restype,
                                  JCList.nil(), syms.methodClass);
        }
        private Type dummyMethodType() {
            return dummyMethodType(null);
        }

        @Override
        public void scan(JCTree tree) {
            if (tree == null) return;
            if (tree instanceof JCExpression) {
                initTypeIfNeeded(tree);
            }
            super.scan(tree);
        }

        @Override
        public void visitIdent(JCIdent that) {
            if (that.sym == null) {
                that.sym = syms.unknownSymbol;
            }
        }

        @Override
        public void visitSelect(JCFieldAccess that) {
            if (that.sym == null) {
                that.sym = syms.unknownSymbol;
            }
            super.visitSelect(that);
        }

        @Override
        public void visitClassDef(JCClassDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new ClassSymbol(0, that.name, that.type, syms.noSymbol);
            }
            super.visitClassDef(that);
        }

        @Override
        public void visitMethodDef(JCMethodDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new MethodSymbol(0, that.name, that.type, syms.noSymbol);
            }
            super.visitMethodDef(that);
        }

        @Override
        public void visitVarDef(JCVariableDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new VarSymbol(0, that.name, that.type, syms.noSymbol);
                that.sym.adr = 0;
            }
            if (that.vartype == null) {
                that.vartype = make.Erroneous();
            }
            super.visitVarDef(that);
        }

        @Override
        public void visitNewClass(JCNewClass that) {
            if (that.constructor == null) {
                that.constructor = new MethodSymbol(0, names.init,
                        dummyMethodType(), syms.noSymbol);
            }
            if (that.constructorType == null) {
                that.constructorType = syms.unknownType;
            }
            super.visitNewClass(that);
        }

        @Override
        public void visitAssignop(JCAssignOp that) {
            if (that.operator == null) {
                that.operator = new OperatorSymbol(names.empty, dummyMethodType(),
                        -1, syms.noSymbol);
            }
            super.visitAssignop(that);
        }

        @Override
        public void visitBinary(JCBinary that) {
            if (that.operator == null) {
                that.operator = new OperatorSymbol(names.empty, dummyMethodType(),
                        -1, syms.noSymbol);
            }
            super.visitBinary(that);
        }

        @Override
        public void visitUnary(JCUnary that) {
            if (that.operator == null) {
                that.operator = new OperatorSymbol(names.empty, dummyMethodType(),
                        -1, syms.noSymbol);
            }
            super.visitUnary(that);
        }

        @Override
        public void visitLambda(JCLambda that) {
            super.visitLambda(that);
            if (that.targets == null) {
                that.targets = JCList.nil();
            }
        }

        @Override
        public void visitReference(JCMemberReference that) {
            super.visitReference(that);
            if (that.sym == null) {
                that.sym = new MethodSymbol(0, names.empty, dummyMethodType(),
                        syms.noSymbol);
            }
            if (that.targets == null) {
                that.targets = JCList.nil();
            }
        }
    }
    // </editor-fold>

    public void setPackageSymbols(JCExpression pid, Symbol pkg) {
        new TreeScanner() {
            Symbol packge = pkg;
            @Override
            public void visitIdent(JCIdent that) {
                that.sym = packge;
            }

            @Override
            public void visitSelect(JCFieldAccess that) {
                that.sym = packge;
                packge = packge.owner;
                super.visitSelect(that);
            }
        }.scan(pid);
    }

}
