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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.TypeTag;
import com.flint.tools.flintc.code.Types;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.code.Type.ArrayType;
import com.flint.tools.flintc.code.Type.ClassType;
import com.flint.tools.flintc.code.Type.TypeVar;
import com.flint.tools.flintc.code.Type.UndetVar;
import com.flint.tools.flintc.code.Type.UndetVar.InferenceBound;
import com.flint.tools.flintc.code.Type.WildcardType;
import com.flint.tools.flintc.comp.Infer.FreeTypeListener;
import com.flint.tools.flintc.comp.Infer.GraphSolver;
import com.flint.tools.flintc.comp.Infer.GraphStrategy;
import com.flint.tools.flintc.comp.Infer.InferenceException;
import com.flint.tools.flintc.comp.Infer.InferenceStep;
import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.Filter;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.ListBuffer;
import com.flint.tools.flintc.util.Warner;

/**
 * An inference context keeps track of the set of variables that are free
 * in the current context. It provides utility methods for opening/closing
 * types to their corresponding free/closed forms. It also provide hooks for
 * attaching deferred post-inference action (see PendingCheck). Finally,
 * it can be used as an entry point for performing upper/lower bound inference
 * (see InferenceKind).
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class InferenceContext {

    /** list of inference vars as undet vars */
    JCList<Type> undetvars;

    Type update(Type t) {
        return t;
    }

    /** list of inference vars in this context */
    JCList<Type> inferencevars;

    Map<FreeTypeListener, JCList<Type>> freeTypeListeners = new LinkedHashMap<>();

    Types types;
    Infer infer;

    public InferenceContext(Infer infer, JCList<Type> inferencevars) {
        this(infer, inferencevars, inferencevars.map(infer.fromTypeVarFun));
    }

    public InferenceContext(Infer infer, JCList<Type> inferencevars, JCList<Type> undetvars) {
        this.inferencevars = inferencevars;
        this.undetvars = undetvars;
        this.infer = infer;
        this.types = infer.types;
    }

    /**
     * add a new inference var to this inference context
     */
    void addVar(TypeVar t) {
        this.undetvars = this.undetvars.prepend(infer.fromTypeVarFun.apply(t));
        this.inferencevars = this.inferencevars.prepend(t);
    }

    /**
     * returns the list of free variables (as type-variables) in this
     * inference context
     */
    JCList<Type> inferenceVars() {
        return inferencevars;
    }

    /**
     * returns the list of undetermined variables in this inference context
     */
    public JCList<Type> undetVars() {
        return undetvars;
    }

    /**
     * returns the list of uninstantiated variables (as type-variables) in this
     * inference context
     */
    JCList<Type> restvars() {
        return filterVars(uv -> uv.getInst() == null);
    }

    /**
     * returns the list of instantiated variables (as type-variables) in this
     * inference context
     */
    JCList<Type> instvars() {
        return filterVars(uv -> uv.getInst() != null);
    }

    /**
     * Get list of bounded inference variables (where bound is other than
     * declared bounds).
     */
    final JCList<Type> boundedVars() {
        return filterVars(uv -> uv.getBounds(InferenceBound.UPPER)
                 .diff(uv.getDeclaredBounds())
                 .appendList(uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)).nonEmpty());
    }

    /* Returns the corresponding inference variables.
     */
    private JCList<Type> filterVars(Filter<UndetVar> fu) {
        ListBuffer<Type> res = new ListBuffer<>();
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            if (fu.accepts(uv)) {
                res.append(uv.qtype);
            }
        }
        return res.toList();
    }

    /**
     * is this type free?
     */
    final boolean free(Type t) {
        return t.containsAny(inferencevars);
    }

    final boolean free(JCList<Type> ts) {
        for (Type t : ts) {
            if (free(t)) return true;
        }
        return false;
    }

    /**
     * Returns a list of free variables in a given type
     */
    final JCList<Type> freeVarsIn(Type t) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type iv : inferenceVars()) {
            if (t.contains(iv)) {
                buf.add(iv);
            }
        }
        return buf.toList();
    }

    final JCList<Type> freeVarsIn(JCList<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.appendList(freeVarsIn(t));
        }
        ListBuffer<Type> buf2 = new ListBuffer<>();
        for (Type t : buf) {
            if (!buf2.contains(t)) {
                buf2.add(t);
            }
        }
        return buf2.toList();
    }

    /**
     * Replace all free variables in a given type with corresponding
     * undet vars (used ahead of subtyping/compatibility checks to allow propagation
     * of inference constraints).
     */
    public final Type asUndetVar(Type t) {
        return types.subst(t, inferencevars, undetvars);
    }

    final JCList<Type> asUndetVars(JCList<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.append(asUndetVar(t));
        }
        return buf.toList();
    }

    JCList<Type> instTypes() {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            buf.append(uv.getInst() != null ? uv.getInst() : uv.qtype);
        }
        return buf.toList();
    }

    /**
     * Replace all free variables in a given type with corresponding
     * instantiated types - if one or more free variable has not been
     * fully instantiated, it will still be available in the resulting type.
     */
    Type asInstType(Type t) {
        return types.subst(t, inferencevars, instTypes());
    }

    JCList<Type> asInstTypes(JCList<Type> ts) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            buf.append(asInstType(t));
        }
        return buf.toList();
    }

    /**
     * Add custom hook for performing post-inference action
     */
    void addFreeTypeListener(JCList<Type> types, FreeTypeListener ftl) {
        freeTypeListeners.put(ftl, freeVarsIn(types));
    }

    /**
     * Mark the inference context as complete and trigger evaluation
     * of all deferred checks.
     */
    void notifyChange() {
        notifyChange(inferencevars.diff(restvars()));
    }

    void notifyChange(JCList<Type> inferredVars) {
        InferenceException thrownEx = null;
        for (Map.Entry<FreeTypeListener, JCList<Type>> entry :
                new LinkedHashMap<>(freeTypeListeners).entrySet()) {
            if (!Type.containsAny(entry.getValue(), inferencevars.diff(inferredVars))) {
                try {
                    entry.getKey().typesInferred(this);
                    freeTypeListeners.remove(entry.getKey());
                } catch (InferenceException ex) {
                    if (thrownEx == null) {
                        thrownEx = ex;
                    }
                }
            }
        }
        //inference exception multiplexing - present any inference exception
        //thrown when processing listeners as a single one
        if (thrownEx != null) {
            throw thrownEx;
        }
    }

    /**
     * Save the state of this inference context
     */
    public JCList<Type> save() {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : undetvars) {
            buf.add(((UndetVar)t).dup(infer.types));
        }
        return buf.toList();
    }

    /** Restore the state of this inference context to the previous known checkpoint.
    *  Consider that the number of saved undetermined variables can be different to the current
    *  amount. This is because new captured variables could have been added.
    */
    public void rollback(JCList<Type> saved_undet) {
        Assert.check(saved_undet != null);
        //restore bounds (note: we need to preserve the old instances)
        ListBuffer<Type> newUndetVars = new ListBuffer<>();
        ListBuffer<Type> newInferenceVars = new ListBuffer<>();
        while (saved_undet.nonEmpty() && undetvars.nonEmpty()) {
            UndetVar uv = (UndetVar)undetvars.head;
            UndetVar uv_saved = (UndetVar)saved_undet.head;
            if (uv.qtype == uv_saved.qtype) {
                uv_saved.dupTo(uv, types);
                undetvars = undetvars.tail;
                saved_undet = saved_undet.tail;
                newUndetVars.add(uv);
                newInferenceVars.add(uv.qtype);
            } else {
                undetvars = undetvars.tail;
            }
        }
        undetvars = newUndetVars.toList();
        inferencevars = newInferenceVars.toList();
    }

    /**
     * Copy variable in this inference context to the given context
     */
    void dupTo(final InferenceContext that) {
        dupTo(that, false);
    }

    void dupTo(final InferenceContext that, boolean clone) {
        that.inferencevars = that.inferencevars.appendList(inferencevars.diff(that.inferencevars));
        JCList<Type> undetsToPropagate = clone ? save() : undetvars;
        that.undetvars = that.undetvars.appendList(undetsToPropagate.diff(that.undetvars)); //propagate cloned undet!!
        //set up listeners to notify original inference contexts as
        //propagated vars are inferred in new context
        for (Type t : inferencevars) {
            that.freeTypeListeners.put(inferenceContext -> InferenceContext.this.notifyChange(), JCList.of(t));
        }
    }

    InferenceContext min(JCList<Type> roots, boolean shouldSolve, Warner warn) {
        if (roots.length() == inferencevars.length()) {
            return this;
        }
        ReachabilityVisitor rv = new ReachabilityVisitor();
        rv.scan(roots);
        if (rv.min.size() == inferencevars.length()) {
            return this;
        }

        JCList<Type> minVars = JCList.from(rv.min);
        JCList<Type> redundantVars = inferencevars.diff(minVars);

        //compute new undet variables (bounds associated to redundant variables are dropped)
        ListBuffer<Type> minUndetVars = new ListBuffer<>();
        for (Type minVar : minVars) {
            UndetVar uv = (UndetVar)asUndetVar(minVar);
            Assert.check(uv.incorporationActions.isEmpty());
            UndetVar uv2 = uv.dup(types);
            for (InferenceBound ib : InferenceBound.values()) {
                JCList<Type> newBounds = uv.getBounds(ib).stream()
                        .filter(b -> !redundantVars.contains(b))
                        .collect(JCList.collector());
                uv2.setBounds(ib, newBounds);
            }
            minUndetVars.add(uv2);
        }

        //compute new minimal inference context
        InferenceContext minContext = new InferenceContext(infer, minVars, minUndetVars.toList());
        for (Type t : minContext.inferencevars) {
            //add listener that forwards notifications to original context
            minContext.addFreeTypeListener(JCList.of(t), (inferenceContext) -> {
                ((UndetVar)asUndetVar(t)).setInst(inferenceContext.asInstType(t));
                infer.doIncorporation(inferenceContext, warn);
                solve(JCList.from(rv.minMap.get(t)), warn);
                notifyChange();
            });
        }
        if (shouldSolve) {
            //solve definitively unreachable variables
            JCList<Type> unreachableVars = redundantVars.diff(JCList.from(rv.equiv));
            minContext.addFreeTypeListener(minVars, (inferenceContext) -> {
                solve(unreachableVars, warn);
                notifyChange();
            });
        }
        return minContext;
    }

    class ReachabilityVisitor extends Types.UnaryVisitor<Void> {

        Set<Type> equiv = new HashSet<>();
        Set<Type> min = new HashSet<>();
        Map<Type, Set<Type>> minMap = new HashMap<>();

        void scan(JCList<Type> roots) {
            roots.stream().forEach(this::visit);
        }

        @Override
        public Void visitType(Type t, Void _unused) {
            return null;
        }

        @Override
        public Void visitUndetVar(UndetVar t, Void _unused) {
            if (min.add(t.qtype)) {
                Set<Type> deps = minMap.getOrDefault(t.qtype, new HashSet<>(Collections.singleton(t.qtype)));
                for (InferenceBound boundKind : InferenceBound.values()) {
                    for (Type b : t.getBounds(boundKind)) {
                        Type undet = asUndetVar(b);
                        if (!undet.hasTag(TypeTag.UNDETVAR)) {
                            visit(undet);
                        } else if (isEquiv(t, b, boundKind)) {
                            deps.add(b);
                            equiv.add(b);
                        } else {
                            visit(undet);
                        }
                    }
                }
                minMap.put(t.qtype, deps);
            }
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType t, Void _unused) {
            return visit(t.type);
        }

        @Override
        public Void visitTypeVar(TypeVar t, Void aVoid) {
            Type undet = asUndetVar(t);
            if (undet.hasTag(TypeTag.UNDETVAR)) {
                visitUndetVar((UndetVar)undet, null);
            }
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType t, Void _unused) {
            return visit(t.elemtype);
        }

        @Override
        public Void visitClassType(ClassType t, Void _unused) {
            visit(t.getEnclosingType());
            for (Type targ : t.getTypeArguments()) {
                visit(targ);
            }
            return null;
        }

        boolean isEquiv(UndetVar from, Type t, InferenceBound boundKind) {
            UndetVar uv = (UndetVar)asUndetVar(t);
            for (InferenceBound ib : InferenceBound.values()) {
                JCList<Type> b1 = from.getBounds(ib);
                if (ib == boundKind) {
                    b1 = b1.diff(JCList.of(t));
                }
                JCList<Type> b2 = uv.getBounds(ib);
                if (ib == boundKind.complement()) {
                    b2 = b2.diff(JCList.of(from.qtype));
                }
                if (!b1.containsAll(b2) || !b2.containsAll(b1)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Solve with given graph strategy.
     */
    private void solve(GraphStrategy ss, Warner warn) {
        GraphSolver s = infer.new GraphSolver(this, warn);
        s.solve(ss);
    }

    /**
     * Solve all variables in this context.
     */
    public void solve(Warner warn) {
        solve(infer.new LeafSolver() {
            public boolean done() {
                return restvars().isEmpty();
            }
        }, warn);
    }

    /**
     * Solve all variables in the given list.
     */
    public void solve(final JCList<Type> vars, Warner warn) {
        solve(infer.new BestLeafSolver(vars) {
            public boolean done() {
                return !free(asInstTypes(vars));
            }
        }, warn);
    }

    /**
     * Solve at least one variable in given list.
     */
    public void solveAny(JCList<Type> varsToSolve, Warner warn) {
        solve(infer.new BestLeafSolver(varsToSolve.intersect(restvars())) {
            public boolean done() {
                return instvars().intersect(varsToSolve).nonEmpty();
            }
        }, warn);
    }

    /**
     * Apply a set of inference steps
     */
    private JCList<Type> solveBasic(EnumSet<InferenceStep> steps) {
        return solveBasic(inferencevars, steps);
    }

    JCList<Type> solveBasic(JCList<Type> varsToSolve, EnumSet<InferenceStep> steps) {
        ListBuffer<Type> solvedVars = new ListBuffer<>();
        for (Type t : varsToSolve.intersect(restvars())) {
            UndetVar uv = (UndetVar)asUndetVar(t);
            for (InferenceStep step : steps) {
                if (step.accepts(uv, this)) {
                    uv.setInst(step.solve(uv, this));
                    solvedVars.add(uv.qtype);
                    break;
                }
            }
        }
        return solvedVars.toList();
    }

    /**
     * Instantiate inference variables in legacy mode (JLS 15.12.2.7, 15.12.2.8).
     * During overload resolution, instantiation is done by doing a partial
     * inference process using eq/lower bound instantiation. During check,
     * we also instantiate any remaining vars by repeatedly using eq/upper
     * instantiation, until all variables are solved.
     */
    public void solveLegacy(boolean partial, Warner warn, EnumSet<InferenceStep> steps) {
        while (true) {
            JCList<Type> solvedVars = solveBasic(steps);
            if (restvars().isEmpty() || partial) {
                //all variables have been instantiated - exit
                break;
            } else if (solvedVars.isEmpty()) {
                //some variables could not be instantiated because of cycles in
                //upper bounds - provide a (possibly recursive) default instantiation
                infer.instantiateAsUninferredVars(restvars(), this);
                break;
            } else {
                //some variables have been instantiated - replace newly instantiated
                //variables in remaining upper bounds and continue
                for (Type t : undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.substBounds(solvedVars, asInstTypes(solvedVars), types);
                }
            }
        }
        infer.doIncorporation(this, warn);
    }

    @Override
    public String toString() {
        return "Inference vars: " + inferencevars + '\n' +
               "Undet vars: " + undetvars;
    }

    /* Method Types.capture() generates a new type every time it's applied
     * to a wildcard parameterized type. This is intended functionality but
     * there are some cases when what you need is not to generate a new
     * captured type but to check that a previously generated captured type
     * is correct. There are cases when caching a captured type for later
     * reuse is sound. In general two captures from the same AST are equal.
     * This is why the tree is used as the key of the map below. This map
     * stores a Type per AST.
     */
    Map<JCTree, Type> captureTypeCache = new HashMap<>();

    Type cachedCapture(JCTree tree, Type t, boolean readOnly) {
        Type captured = captureTypeCache.get(tree);
        if (captured != null) {
            return captured;
        }

        Type result = types.capture(t);
        if (result != t && !readOnly) { // then t is a wildcard parameterized type
            captureTypeCache.put(tree, result);
        }
        return result;
    }
}
