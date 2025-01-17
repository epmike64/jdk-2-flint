/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.code.Flags;
import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Symtab;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.tree.TreeCopier;
import com.flint.tools.flintc.tree.TreeInfo;
import com.flint.source.tree.LambdaExpressionTree.BodyKind;
import com.flint.tools.flintc.code.Types.FunctionDescriptorLookupError;
import com.flint.tools.flintc.comp.Attr.ResultInfo;
import com.flint.tools.flintc.comp.Attr.TargetInfo;
import com.flint.tools.flintc.comp.Check.CheckContext;
import com.flint.tools.flintc.comp.DeferredAttr.AttrMode;
import com.flint.tools.flintc.comp.DeferredAttr.DeferredAttrContext;
import com.flint.tools.flintc.comp.DeferredAttr.DeferredType;
import com.flint.tools.flintc.comp.DeferredAttr.DeferredTypeCompleter;
import com.flint.tools.flintc.comp.DeferredAttr.LambdaReturnScanner;
import com.flint.tools.flintc.comp.Infer.PartiallyInferredMethodType;
import com.flint.tools.flintc.comp.Resolve.MethodResolutionPhase;
import com.flint.tools.flintc.tree.JCTree.JCConditional;
import com.flint.tools.flintc.tree.JCTree.JCExpression;
import com.flint.tools.flintc.tree.JCTree.JCLambda;
import com.flint.tools.flintc.tree.JCTree.JCLambda.ParameterKind;
import com.flint.tools.flintc.tree.JCTree.JCMemberReference;
import com.flint.tools.flintc.tree.JCTree.JCMethodInvocation;
import com.flint.tools.flintc.tree.JCTree.JCNewClass;
import com.flint.tools.flintc.tree.JCTree.JCParens;
import com.flint.tools.flintc.tree.JCTree.JCReturn;
import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.DiagnosticSource;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticPosition;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.ListBuffer;
import com.flint.tools.flintc.util.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.flint.tools.flintc.code.TypeTag.ARRAY;
import static com.flint.tools.flintc.code.TypeTag.DEFERRED;
import static com.flint.tools.flintc.code.TypeTag.FORALL;
import static com.flint.tools.flintc.code.TypeTag.METHOD;
import static com.flint.tools.flintc.code.TypeTag.VOID;

/**
 * This class performs attribution of method/constructor arguments when target-typing is enabled
 * (source >= 8); for each argument that is potentially a poly expression, this class builds
 * a rich representation (see {@link ArgumentType} which can then be used for performing fast overload
 * checks without requiring multiple attribution passes over the same code.
 *
 * The attribution strategy for a given method/constructor argument A is as follows:
 *
 * - if A is potentially a poly expression (i.e. diamond instance creation expression), a speculative
 * pass over A is performed; the results of such speculative attribution are then saved in a special
 * type, so that enclosing overload resolution can be carried by simply checking compatibility against the
 * type determined during this speculative pass.
 *
 * - if A is a standalone expression, regular atributtion takes place.
 *
 * To minimize the speculative work, a cache is used, so that already computed argument types
 * associated with a given unique source location are never recomputed multiple times.
 */
public class ArgumentAttr extends JCTree.Visitor {

    protected static final Context.Key<ArgumentAttr> methodAttrKey = new Context.Key<>();

    private final DeferredAttr deferredAttr;
    private final Attr attr;
    private final Symtab syms;
    private final Log log;

    /** Attribution environment to be used. */
    private Env<AttrContext> env;

    /** Result of method attribution. */
    Type result;

    /** Cache for argument types; behavior is influences by the currrently selected cache policy. */
    Map<UniquePos, ArgumentType<?>> argumentTypeCache = new LinkedHashMap<>();

    public static ArgumentAttr instance(Context context) {
        ArgumentAttr instance = context.get(methodAttrKey);
        if (instance == null)
            instance = new ArgumentAttr(context);
        return instance;
    }

    protected ArgumentAttr(Context context) {
        context.put(methodAttrKey, this);
        deferredAttr = DeferredAttr.instance(context);
        attr = Attr.instance(context);
        syms = Symtab.instance(context);
        log = Log.instance(context);
    }

    /**
     * Set the results of method attribution.
     */
    void setResult(JCExpression tree, Type type) {
        result = type;
        if (env.info.isSpeculative) {
            //if we are in a speculative branch we can save the type in the tree itself
            //as there's no risk of polluting the original tree.
            tree.type = result;
        }
    }

    /**
     * Checks a type in the speculative tree against a given result; the type can be either a plain
     * type or an argument type,in which case a more complex check is required.
     */
    Type checkSpeculative(JCExpression expr, ResultInfo resultInfo) {
        return checkSpeculative(expr, expr.type, resultInfo);
    }

    /**
     * Checks a type in the speculative tree against a given result; the type can be either a plain
     * type or an argument type,in which case a more complex check is required.
     */
    Type checkSpeculative(DiagnosticPosition pos, Type t, ResultInfo resultInfo) {
        if (t.hasTag(DEFERRED)) {
            return ((DeferredType)t).check(resultInfo);
        } else {
            return resultInfo.check(pos, t);
        }
    }

    /**
     * Returns a local caching context in which argument types can safely be cached without
     * the risk of polluting enclosing contexts. This is useful when attempting speculative
     * attribution of potentially erroneous expressions, which could end up polluting the cache.
     */
    LocalCacheContext withLocalCacheContext() {
        return new LocalCacheContext();
    }

    /**
     * Local cache context; this class keeps track of the previous cache and reverts to it
     * when the {@link LocalCacheContext#leave()} method is called.
     */
    class LocalCacheContext {
        Map<UniquePos, ArgumentType<?>> prevCache;

        public LocalCacheContext() {
            this.prevCache = argumentTypeCache;
            argumentTypeCache = new HashMap<>();
        }

        public void leave() {
            argumentTypeCache = prevCache;
        }
    }

    /**
     * Main entry point for attributing an argument with given tree and attribution environment.
     */
    Type attribArg(JCTree tree, Env<AttrContext> env) {
        Env<AttrContext> prevEnv = this.env;
        try {
            this.env = env;
            tree.accept(this);
            return result;
        } finally {
            this.env = prevEnv;
        }
    }

    @Override
    public void visitTree(JCTree that) {
        //delegates to Attr
        that.accept(attr);
        result = attr.result;
    }

    /**
     * Process a method argument; this method takes care of performing a speculative pass over the
     * argument tree and calling a well-defined entry point to build the argument type associated
     * with such tree.
     */
    @SuppressWarnings("unchecked")
    <T extends JCExpression, Z extends ArgumentType<T>> void processArg(T that, Function<T, Z> argumentTypeFactory) {
        UniquePos pos = new UniquePos(that);
        processArg(that, () -> {
            T speculativeTree = (T)deferredAttr.attribSpeculative(that, env, attr.new MethodAttrInfo() {
                @Override
                protected boolean needsArgumentAttr(JCTree tree) {
                    return !new UniquePos(tree).equals(pos);
                }
            });
            return argumentTypeFactory.apply(speculativeTree);
        });
    }

    /**
     * Process a method argument; this method allows the caller to specify a custom speculative attribution
     * logic (this is used e.g. for lambdas).
     */
    @SuppressWarnings("unchecked")
    <T extends JCExpression, Z extends ArgumentType<T>> void processArg(T that, Supplier<Z> argumentTypeFactory) {
        UniquePos pos = new UniquePos(that);
        Z cached = (Z)argumentTypeCache.get(pos);
        if (cached != null) {
            //dup existing speculative type
            setResult(that, cached.dup(that, env));
        } else {
            Z res = argumentTypeFactory.get();
            argumentTypeCache.put(pos, res);
            setResult(that, res);
        }
    }

    @Override
    public void visitParens(JCParens that) {
        processArg(that, speculativeTree -> new ParensType(that, env, speculativeTree));
    }

    @Override
    public void visitConditional(JCConditional that) {
        processArg(that, speculativeTree -> new ConditionalType(that, env, speculativeTree));
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        //perform arity-based check
        Env<AttrContext> localEnv = env.dup(tree);
        JCExpression exprTree;
        exprTree = (JCExpression)deferredAttr.attribSpeculative(tree.getQualifierExpression(), localEnv,
                attr.memberReferenceQualifierResult(tree),
                withLocalCacheContext());
        JCMemberReference mref2 = new TreeCopier<Void>(attr.make).copy(tree);
        mref2.expr = exprTree;
        Symbol lhsSym = TreeInfo.symbol(exprTree);
        localEnv.info.selectSuper = lhsSym != null && lhsSym.name == lhsSym.name.table.names._super;
        Symbol res =
                attr.rs.getMemberReference(tree, localEnv, mref2,
                        exprTree.type, tree.name);
        if (!res.kind.isResolutionError()) {
            tree.sym = res;
        }
        if (res.kind.isResolutionTargetError() ||
                res.type != null && res.type.hasTag(FORALL) ||
                (res.flags() & Flags.VARARGS) != 0 ||
                (TreeInfo.isStaticSelector(exprTree, tree.name.table.names) &&
                exprTree.type.isRaw() && !exprTree.type.hasTag(ARRAY))) {
            tree.setOverloadKind(JCMemberReference.OverloadKind.OVERLOADED);
        } else {
            tree.setOverloadKind(JCMemberReference.OverloadKind.UNOVERLOADED);
        }
        //return a plain old deferred type for this
        setResult(tree, deferredAttr.new DeferredType(tree, env));
    }

    @Override
    public void visitLambda(JCLambda that) {
        if (that.paramKind == ParameterKind.EXPLICIT) {
            //if lambda is explicit, we can save info in the corresponding argument type
            processArg(that, () -> {
                JCLambda speculativeLambda =
                        deferredAttr.attribSpeculativeLambda(that, env, attr.methodAttrInfo);
                return new ExplicitLambdaType(that, env, speculativeLambda);
            });
        } else {
            //otherwise just use a deferred type
            setResult(that, deferredAttr.new DeferredType(that, env));
        }
    }

    @Override
    public void visitApply(JCMethodInvocation that) {
        if (that.getTypeArguments().isEmpty()) {
            processArg(that, speculativeTree -> new ResolvedMethodType(that, env, speculativeTree));
        } else {
            //not a poly expression, just call Attr
            setResult(that, attr.attribTree(that, env, attr.unknownExprInfo));
        }
    }

    @Override
    public void visitNewClass(JCNewClass that) {
        if (TreeInfo.isDiamond(that)) {
            processArg(that, speculativeTree -> new ResolvedConstructorType(that, env, speculativeTree));
        } else {
            //not a poly expression, just call Attr
            setResult(that, attr.attribTree(that, env, attr.unknownExprInfo));
        }
    }

    /**
     * An argument type is similar to a plain deferred type; the most important difference is that
     * the completion logic associated with argument types allows speculative attribution to be skipped
     * during overload resolution - that is, an argument type always has enough information to
     * perform an overload check without the need of calling back to Attr. This extra information
     * is typically stored in the form of a speculative tree.
     */
    abstract class ArgumentType<T extends JCExpression> extends DeferredType implements DeferredTypeCompleter {

        /** The speculative tree carrying type information. */
        T speculativeTree;

        /** Types associated with this argument (one type per possible target result). */
        Map<ResultInfo, Type> speculativeTypes;

        public ArgumentType(JCExpression tree, Env<AttrContext> env, T speculativeTree, Map<ResultInfo, Type> speculativeTypes) {
            deferredAttr.super(tree, env);
            this.speculativeTree = speculativeTree;
            this.speculativeTypes = speculativeTypes;
        }

        @Override
        final DeferredTypeCompleter completer() {
            return this;
        }

        @Override
        final public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            Assert.check(dt == this);
            if (deferredAttrContext.mode == AttrMode.SPECULATIVE) {
                Type t = (resultInfo.pt == Type.recoveryType) ?
                        deferredAttr.basicCompleter.complete(dt, resultInfo, deferredAttrContext) :
                        overloadCheck(resultInfo, deferredAttrContext);
                speculativeTypes.put(resultInfo, t);
                return t;
            } else {
                if (!env.info.isSpeculative) {
                    argumentTypeCache.remove(new UniquePos(dt.tree));
                }
                return deferredAttr.basicCompleter.complete(dt, resultInfo, deferredAttrContext);
            }
        }

        @Override
        Type speculativeType(Symbol msym, MethodResolutionPhase phase) {
            if (pertinentToApplicability) {
                for (Map.Entry<ResultInfo, Type> _entry : speculativeTypes.entrySet()) {
                    DeferredAttrContext deferredAttrContext = _entry.getKey().checkContext.deferredAttrContext();
                    if (deferredAttrContext.phase == phase && deferredAttrContext.msym == msym) {
                        return _entry.getValue();
                    }
                }
                return Type.noType;
            } else {
                return super.speculativeType(msym, phase);
            }
        }

        @Override
        JCTree speculativeTree(DeferredAttrContext deferredAttrContext) {
            return pertinentToApplicability ? speculativeTree : super.speculativeTree(deferredAttrContext);
        }

        /**
         * Performs an overload check against a given target result.
         */
        abstract Type overloadCheck(ResultInfo resultInfo, DeferredAttrContext deferredAttrContext);

        /**
         * Creates a copy of this argument type with given tree and environment.
         */
        abstract ArgumentType<T> dup(T tree, Env<AttrContext> env);
    }

    /**
     * Argument type for parenthesized expression.
     */
    class ParensType extends ArgumentType<JCParens> {
        ParensType(JCExpression tree, Env<AttrContext> env, JCParens speculativeParens) {
            this(tree, env, speculativeParens, new HashMap<>());
        }

        ParensType(JCExpression tree, Env<AttrContext> env, JCParens speculativeParens, Map<ResultInfo, Type> speculativeTypes) {
           super(tree, env, speculativeParens, speculativeTypes);
        }

        @Override
        Type overloadCheck(ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            return checkSpeculative(speculativeTree.expr, resultInfo);
        }

        @Override
        ArgumentType<JCParens> dup(JCParens tree, Env<AttrContext> env) {
            return new ParensType(tree, env, speculativeTree, speculativeTypes);
        }
    }

    /**
     * Argument type for conditionals.
     */
    class ConditionalType extends ArgumentType<JCConditional> {
        ConditionalType(JCExpression tree, Env<AttrContext> env, JCConditional speculativeCond) {
            this(tree, env, speculativeCond, new HashMap<>());
        }

        ConditionalType(JCExpression tree, Env<AttrContext> env, JCConditional speculativeCond, Map<ResultInfo, Type> speculativeTypes) {
           super(tree, env, speculativeCond, speculativeTypes);
        }

        @Override
        Type overloadCheck(ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            ResultInfo localInfo = resultInfo.dup(attr.conditionalContext(resultInfo.checkContext));
            if (speculativeTree.isStandalone()) {
                return localInfo.check(speculativeTree, speculativeTree.type);
            } else if (resultInfo.pt.hasTag(VOID)) {
                //this means we are returning a poly conditional from void-compatible lambda expression
                resultInfo.checkContext.report(tree, attr.diags.fragment("conditional.target.cant.be.void"));
                return attr.types.createErrorType(resultInfo.pt);
            } else {
                //poly
                checkSpeculative(speculativeTree.truepart, localInfo);
                checkSpeculative(speculativeTree.falsepart, localInfo);
                return localInfo.pt;
            }
        }

        @Override
        ArgumentType<JCConditional> dup(JCConditional tree, Env<AttrContext> env) {
            return new ConditionalType(tree, env, speculativeTree, speculativeTypes);
        }
    }

    /**
     * Argument type for explicit lambdas.
     */
    class ExplicitLambdaType extends ArgumentType<JCLambda> {

        /** List of argument types (lazily populated). */
        Optional<JCList<Type>> argtypes = Optional.empty();

        /** List of return expressions (lazily populated). */
        Optional<JCList<JCReturn>> returnExpressions = Optional.empty();

        ExplicitLambdaType(JCLambda originalLambda, Env<AttrContext> env, JCLambda speculativeLambda) {
            this(originalLambda, env, speculativeLambda, new HashMap<>());
        }

        ExplicitLambdaType(JCLambda originalLambda, Env<AttrContext> env, JCLambda speculativeLambda, Map<ResultInfo, Type> speculativeTypes) {
            super(originalLambda, env, speculativeLambda, speculativeTypes);
        }

        /** Compute argument types (if needed). */
        JCList<Type> argtypes() {
            return argtypes.orElseGet(() -> {
                JCList<Type> res = TreeInfo.types(speculativeTree.params);
                argtypes = Optional.of(res);
                return res;
            });
        }

        /** Compute return expressions (if needed). */
        JCList<JCReturn> returnExpressions() {
            return returnExpressions.orElseGet(() -> {
                final JCList<JCReturn> res;
                if (speculativeTree.getBodyKind() == BodyKind.EXPRESSION) {
                    res = JCList.of(attr.make.Return((JCExpression)speculativeTree.body));
                } else {
                    ListBuffer<JCReturn> returnExpressions = new ListBuffer<>();
                    new LambdaReturnScanner() {
                        @Override
                        public void visitReturn(JCReturn tree) {
                            returnExpressions.add(tree);
                        }
                    }.scan(speculativeTree.body);
                    res = returnExpressions.toList();
                }
                returnExpressions = Optional.of(res);
                return res;
            });
        }

        @Override
        Type overloadCheck(ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            try {
                //compute target-type; this logic could be shared with Attr
                TargetInfo targetInfo = attr.getTargetInfo(speculativeTree, resultInfo, argtypes());
                Type lambdaType = targetInfo.descriptor;
                Type currentTarget = targetInfo.target;
                //check compatibility
                checkLambdaCompatible(lambdaType, resultInfo);
                return currentTarget;
            } catch (FunctionDescriptorLookupError ex) {
                resultInfo.checkContext.report(null, ex.getDiagnostic());
                return null; //cannot get here
            }
        }

        /** Check lambda against given target result */
        private void checkLambdaCompatible(Type descriptor, ResultInfo resultInfo) {
            CheckContext checkContext = resultInfo.checkContext;
            ResultInfo bodyResultInfo = attr.lambdaBodyResult(speculativeTree, descriptor, resultInfo);
            for (JCReturn ret : returnExpressions()) {
                Type t = getReturnType(ret);
                if (speculativeTree.getBodyKind() == BodyKind.EXPRESSION || !t.hasTag(VOID)) {
                    checkSpeculative(ret.expr, t, bodyResultInfo);
                }
            }

            attr.checkLambdaCompatible(speculativeTree, descriptor, checkContext);
        }

        /** Get the type associated with given return expression. */
        Type getReturnType(JCReturn ret) {
            if (ret.expr == null) {
                return syms.voidType;
            } else {
                return ret.expr.type;
            }
        }

        @Override
        ArgumentType<JCLambda> dup(JCLambda tree, Env<AttrContext> env) {
            return new ExplicitLambdaType(tree, env, speculativeTree, speculativeTypes);
        }
    }

    /**
     * Argument type for methods/constructors.
     */
    abstract class ResolvedMemberType<E extends JCExpression> extends ArgumentType<E> {

        public ResolvedMemberType(JCExpression tree, Env<AttrContext> env, E speculativeMethod, Map<ResultInfo, Type> speculativeTypes) {
            super(tree, env, speculativeMethod, speculativeTypes);
        }

        @Override
        Type overloadCheck(ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            Type mtype = methodType();
            ResultInfo localInfo = resultInfo(resultInfo);
            if (mtype != null && mtype.hasTag(METHOD) && mtype.isPartial()) {
                Type t = ((PartiallyInferredMethodType)mtype).check(localInfo);
                if (!deferredAttrContext.inferenceContext.free(localInfo.pt)) {
                    speculativeTypes.put(localInfo, t);
                    return localInfo.check(tree.pos(), t);
                } else {
                    return t;
                }
            } else {
                Type t = localInfo.check(tree.pos(), speculativeTree.type);
                speculativeTypes.put(localInfo, t);
                return t;
            }
        }

        /**
         * Get the result info to be used for performing an overload check.
         */
        abstract ResultInfo resultInfo(ResultInfo resultInfo);

        /**
         * Get the method type to be used for performing an overload check.
         */
        abstract Type methodType();
    }

    /**
     * Argument type for methods.
     */
    class ResolvedMethodType extends ResolvedMemberType<JCMethodInvocation> {

        public ResolvedMethodType(JCExpression tree, Env<AttrContext> env, JCMethodInvocation speculativeTree) {
            this(tree, env, speculativeTree, new HashMap<>());
        }

        public ResolvedMethodType(JCExpression tree, Env<AttrContext> env, JCMethodInvocation speculativeTree, Map<ResultInfo, Type> speculativeTypes) {
            super(tree, env, speculativeTree, speculativeTypes);
        }

        @Override
        ResultInfo resultInfo(ResultInfo resultInfo) {
            return resultInfo;
        }

        @Override
        Type methodType() {
            return speculativeTree.meth.type;
        }

        @Override
        ArgumentType<JCMethodInvocation> dup(JCMethodInvocation tree, Env<AttrContext> env) {
            return new ResolvedMethodType(tree, env, speculativeTree, speculativeTypes);
        }
    }

    /**
     * Argument type for constructors.
     */
    class ResolvedConstructorType extends ResolvedMemberType<JCNewClass> {

        public ResolvedConstructorType(JCExpression tree, Env<AttrContext> env, JCNewClass speculativeTree) {
            this(tree, env, speculativeTree, new HashMap<>());
        }

        public ResolvedConstructorType(JCExpression tree, Env<AttrContext> env, JCNewClass speculativeTree, Map<ResultInfo, Type> speculativeTypes) {
            super(tree, env, speculativeTree, speculativeTypes);
        }

        @Override
        ResultInfo resultInfo(ResultInfo resultInfo) {
            return resultInfo.dup(attr.diamondContext(speculativeTree, speculativeTree.clazz.type.tsym, resultInfo.checkContext));
        }

        @Override
        Type methodType() {
            return (speculativeTree.constructorType != null) ?
                    speculativeTree.constructorType.baseType() : syms.errType;
        }

        @Override
        ArgumentType<JCNewClass> dup(JCNewClass tree, Env<AttrContext> env) {
            return new ResolvedConstructorType(tree, env, speculativeTree, speculativeTypes);
        }
    }

    /**
     * An instance of this class represents a unique position in a compilation unit. A unique
     * position is made up of (i) a unique position in a source file (char offset) and (ii)
     * a source file info.
     */
    class UniquePos {

        /** Char offset. */
        int pos;

        /** Source info. */
        DiagnosticSource source;

        UniquePos(JCTree tree) {
            this.pos = tree.pos;
            this.source = log.currentSource();
        }

        @Override
        public int hashCode() {
            return pos << 16 + source.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UniquePos) {
                UniquePos that = (UniquePos)obj;
                return pos == that.pos && source == that.source;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return source.getFile().getName() + " @ " + source.getLineNumber(pos);
        }
    }
}
