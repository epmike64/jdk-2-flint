/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.jvm;

import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Symtab;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.Types;
import com.flint.tools.flintc.comp.Resolve;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.tree.TreeInfo;
import com.flint.tools.flintc.tree.TreeMaker;
import com.flint.tools.flintc.util.*;

import static com.flint.tools.flintc.code.Kinds.Kind.MTH;
import static com.flint.tools.flintc.code.TypeTag.*;
import static com.flint.tools.flintc.jvm.ByteCodes.*;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.PLUS;
import com.flint.tools.flintc.jvm.Items.*;

import java.util.HashMap;
import java.util.Map;

/** This lowers the String concatenation to something that JVM can understand.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class StringConcat {

    /**
     * Maximum number of slots for String Concat call.
     * JDK's StringConcatFactory does not support more than that.
     */
    private static final int MAX_INDY_CONCAT_ARG_SLOTS = 200;
    private static final char TAG_ARG   = '\u0001';
    private static final char TAG_CONST = '\u0002';

    protected final Gen gen;
    protected final Symtab syms;
    protected final Names names;
    protected final TreeMaker make;
    protected final Types types;
    protected final Map<Type, Symbol> sbAppends;
    protected final Resolve rs;

    protected static final Context.Key<StringConcat> concatKey = new Context.Key<>();

    public static StringConcat instance(Context context) {
        StringConcat instance = context.get(concatKey);
        if (instance == null) {
            instance = makeConcat(context);
        }
        return instance;
    }

    private static StringConcat makeConcat(Context context) {
        Target target = Target.instance(context);
        String opt = Options.instance(context).get("stringConcat");
        if (target.hasStringConcatFactory()) {
            if (opt == null) {
                opt = "indyWithConstants";
            }
        } else {
            if (opt != null && !"inline".equals(opt)) {
                Assert.error("StringConcatFactory-based string concat is requested on a platform that does not support it.");
            }
            opt = "inline";
        }

        switch (opt) {
            case "inline":
                return new Inline(context);
            case "indy":
                return new IndyPlain(context);
            case "indyWithConstants":
                return new IndyConstants(context);
            default:
                Assert.error("Unknown stringConcat: " + opt);
                throw new IllegalStateException("Unknown stringConcat: " + opt);
        }
    }

    protected StringConcat(Context context) {
        context.put(concatKey, this);
        gen = Gen.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        names = Names.instance(context);
        make = TreeMaker.instance(context);
        rs = Resolve.instance(context);
        sbAppends = new HashMap<>();
    }

    public abstract Item makeConcat(JCTree.JCAssignOp tree);
    public abstract Item makeConcat(JCTree.JCBinary tree);

    protected JCList<JCTree> collectAll(JCTree tree) {
        return collect(tree, JCList.nil());
    }

    protected JCList<JCTree> collectAll(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return JCList.<JCTree>nil()
                .appendList(collectAll(lhs))
                .appendList(collectAll(rhs));
    }

    private JCList<JCTree> collect(JCTree tree, JCList<JCTree> res) {
        tree = TreeInfo.skipParens(tree);
        if (tree.hasTag(PLUS) && tree.type.constValue() == null) {
            JCTree.JCBinary op = (JCTree.JCBinary) tree;
            if (op.operator.kind == MTH && op.operator.opcode == string_add) {
                return res
                        .appendList(collect(op.lhs, res))
                        .appendList(collect(op.rhs, res));
            }
        }
        return res.append(tree);
    }

    /**
     * If the type is not accessible from current context, try to figure out the
     * sharpest accessible supertype.
     *
     * @param originalType type to sharpen
     * @return sharped type
     */
    Type sharpestAccessible(Type originalType) {
        if (originalType.hasTag(ARRAY)) {
            return types.makeArrayType(sharpestAccessible(types.elemtype(originalType)));
        }

        Type type = originalType;
        while (!rs.isAccessible(gen.getAttrEnv(), type.asElement())) {
            type = types.supertype(type);
        }
        return type;
    }

    /**
     * "Legacy" bytecode flavor: emit the StringBuilder.append chains for string
     * concatenation.
     */
    private static class Inline extends StringConcat {
        public Inline(Context context) {
            super(context);
        }

        @Override
        public Item makeConcat(JCTree.JCAssignOp tree) {
            // Generate code to make a string builder
            JCDiagnostic.DiagnosticPosition pos = tree.pos();

            // Create a string builder.
            newStringBuilder(tree);

            // Generate code for first string, possibly save one
            // copy under builder
            Item l = gen.genExpr(tree.lhs, tree.lhs.type);
            if (l.width() > 0) {
                gen.getCode().emitop0(dup_x1 + 3 * (l.width() - 1));
            }

            // Load first string and append to builder.
            l.load();
            appendString(tree.lhs);

            // Append all other strings to builder.
            JCList<JCTree> args = collectAll(tree.rhs);
            for (JCTree t : args) {
                gen.genExpr(t, t.type).load();
                appendString(t);
            }

            // Convert builder to string.
            builderToString(pos);

            return l;
        }

        @Override
        public Item makeConcat(JCTree.JCBinary tree) {
            JCDiagnostic.DiagnosticPosition pos = tree.pos();

            // Create a string builder.
            newStringBuilder(tree);

            // Append all strings to builder.
            JCList<JCTree> args = collectAll(tree);
            for (JCTree t : args) {
                gen.genExpr(t, t.type).load();
                appendString(t);
            }

            // Convert builder to string.
            builderToString(pos);

            return gen.getItems().makeStackItem(syms.stringType);
        }

        private JCDiagnostic.DiagnosticPosition newStringBuilder(JCTree tree) {
            JCDiagnostic.DiagnosticPosition pos = tree.pos();
            gen.getCode().emitop2(new_, gen.makeRef(pos, syms.stringBuilderType));
            gen.getCode().emitop0(dup);
            gen.callMethod(pos, syms.stringBuilderType, names.init, JCList.nil(), false);
            return pos;
        }

        private void appendString(JCTree tree) {
            Type t = tree.type.baseType();
            if (!t.isPrimitive() && t.tsym != syms.stringType.tsym) {
                t = syms.objectType;
            }

            Assert.checkNull(t.constValue());
            Symbol method = sbAppends.get(t);
            if (method == null) {
                method = rs.resolveInternalMethod(tree.pos(), gen.getAttrEnv(), syms.stringBuilderType, names.append, JCList.of(t), null);
                sbAppends.put(t, method);
            }

            gen.getItems().makeMemberItem(method, false).invoke();
        }

        private void builderToString(JCDiagnostic.DiagnosticPosition pos) {
            gen.callMethod(pos, syms.stringBuilderType, names.toString, JCList.nil(), false);
        }
    }

    /**
     * Base class for indified concatenation bytecode flavors.
     */
    private static abstract class Indy extends StringConcat {
        public Indy(Context context) {
            super(context);
        }

        @Override
        public Item makeConcat(JCTree.JCAssignOp tree) {
            JCList<JCTree> args = collectAll(tree.lhs, tree.rhs);
            Item l = gen.genExpr(tree.lhs, tree.lhs.type);
            emit(args, tree.type, tree.pos());
            return l;
        }

        @Override
        public Item makeConcat(JCTree.JCBinary tree) {
            JCList<JCTree> args = collectAll(tree.lhs, tree.rhs);
            emit(args, tree.type, tree.pos());
            return gen.getItems().makeStackItem(syms.stringType);
        }

        protected abstract void emit(JCList<JCTree> args, Type type, JCDiagnostic.DiagnosticPosition pos);

        /** Peel the argument list into smaller chunks. */
        protected JCList<JCList<JCTree>> split(JCList<JCTree> args) {
            ListBuffer<JCList<JCTree>> splits = new ListBuffer<>();

            int slots = 0;

            // Need to peel, so that neither call has more than acceptable number
            // of slots for the arguments.
            ListBuffer<JCTree> cArgs = new ListBuffer<>();
            for (JCTree t : args) {
                int needSlots = (t.type.getTag() == LONG || t.type.getTag() == DOUBLE) ? 2 : 1;
                if (slots + needSlots >= MAX_INDY_CONCAT_ARG_SLOTS) {
                    splits.add(cArgs.toList());
                    cArgs.clear();
                    slots = 0;
                }
                cArgs.add(t);
                slots += needSlots;
            }

            // Flush the tail slice
            if (!cArgs.isEmpty()) {
                splits.add(cArgs.toList());
            }

            return splits.toList();
        }
    }

    /**
     * Emits the invokedynamic call to JDK java.lang.invoke.StringConcatFactory,
     * without handling constants specially.
     *
     * We bypass empty strings, because they have no meaning at this level. This
     * captures the Java language trick to force String concat with e.g. ("" + int)-like
     * expression. Down here, we already know we are in String concat business, and do
     * not require these markers.
     */
    private static class IndyPlain extends Indy {
        public IndyPlain(Context context) {
            super(context);
        }

        /** Emit the indy concat for all these arguments, possibly peeling along the way */
        protected void emit(JCList<JCTree> args, Type type, JCDiagnostic.DiagnosticPosition pos) {
            JCList<JCList<JCTree>> split = split(args);

            for (JCList<JCTree> t : split) {
                Assert.check(!t.isEmpty(), "Arguments list is empty");

                ListBuffer<Type> dynamicArgs = new ListBuffer<>();
                for (JCTree arg : t) {
                    Object constVal = arg.type.constValue();
                    if ("".equals(constVal)) continue;
                    if (arg.type == syms.botType) {
                        dynamicArgs.add(types.boxedClass(syms.voidType).type);
                    } else {
                        dynamicArgs.add(sharpestAccessible(arg.type));
                    }
                    gen.genExpr(arg, arg.type).load();
                }

                doCall(type, pos, dynamicArgs.toList());
            }

            // More that one peel slice produced: concatenate the results
            if (split.size() > 1) {
                ListBuffer<Type> argTypes = new ListBuffer<>();
                for (int c = 0; c < split.size(); c++) {
                    argTypes.append(syms.stringType);
                }
                doCall(type, pos, argTypes.toList());
            }
        }

        /** Produce the actual invokedynamic call to StringConcatFactory */
        private void doCall(Type type, JCDiagnostic.DiagnosticPosition pos, JCList<Type> dynamicArgTypes) {
            Type.MethodType indyType = new Type.MethodType(dynamicArgTypes,
                    type,
                    JCList.nil(),
                    syms.methodClass);

            int prevPos = make.pos;
            try {
                make.at(pos);

                JCList<Type> bsm_staticArgs = JCList.of(syms.methodHandleLookupType,
                        syms.stringType,
                        syms.methodTypeType);

                Symbol bsm = rs.resolveInternalMethod(pos,
                        gen.getAttrEnv(),
                        syms.stringConcatFactory,
                        names.makeConcat,
                        bsm_staticArgs,
                        null);

                Symbol.DynamicMethodSymbol dynSym = new Symbol.DynamicMethodSymbol(names.makeConcat,
                        syms.noSymbol,
                        ClassFile.REF_invokeStatic,
                        (Symbol.MethodSymbol)bsm,
                        indyType,
                        JCList.nil().toArray());

                Items.Item item = gen.getItems().makeDynamicItem(dynSym);
                item.invoke();
            } finally {
                make.at(prevPos);
            }
        }
    }

    /**
     * Emits the invokedynamic call to JDK java.lang.invoke.StringConcatFactory.
     * This code concatenates all known constants into the recipe, possibly escaping
     * some constants separately.
     *
     * We also bypass empty strings, because they have no meaning at this level. This
     * captures the Java language trick to force String concat with e.g. ("" + int)-like
     * expression. Down here, we already know we are in String concat business, and do
     * not require these markers.
     */
    private static final class IndyConstants extends Indy {
        public IndyConstants(Context context) {
            super(context);
        }

        @Override
        protected void emit(JCList<JCTree> args, Type type, JCDiagnostic.DiagnosticPosition pos) {
            JCList<JCList<JCTree>> split = split(args);

            for (JCList<JCTree> t : split) {
                Assert.check(!t.isEmpty(), "Arguments list is empty");

                StringBuilder recipe = new StringBuilder(t.size());
                ListBuffer<Type> dynamicArgs = new ListBuffer<>();
                ListBuffer<Object> staticArgs = new ListBuffer<>();

                for (JCTree arg : t) {
                    Object constVal = arg.type.constValue();
                    if ("".equals(constVal)) continue;
                    if (arg.type == syms.botType) {
                        // Concat the null into the recipe right away
                        recipe.append((String) null);
                    } else if (constVal != null) {
                        // Concat the String representation of the constant, except
                        // for the case it contains special tags, which requires us
                        // to expose it as detached constant.
                        String a = arg.type.stringValue();
                        if (a.indexOf(TAG_CONST) != -1 || a.indexOf(TAG_ARG) != -1) {
                            recipe.append(TAG_CONST);
                            staticArgs.add(a);
                        } else {
                            recipe.append(a);
                        }
                    } else {
                        // Ordinary arguments come through the dynamic arguments.
                        recipe.append(TAG_ARG);
                        dynamicArgs.add(sharpestAccessible(arg.type));
                        gen.genExpr(arg, arg.type).load();
                    }
                }

                doCall(type, pos, recipe.toString(), staticArgs.toList(), dynamicArgs.toList());
            }

            // More that one peel slice produced: concatenate the results
            // All arguments are assumed to be non-constant Strings.
            if (split.size() > 1) {
                ListBuffer<Type> argTypes = new ListBuffer<>();
                StringBuilder recipe = new StringBuilder();
                for (int c = 0; c < split.size(); c++) {
                    argTypes.append(syms.stringType);
                    recipe.append(TAG_ARG);
                }
                doCall(type, pos, recipe.toString(), JCList.nil(), argTypes.toList());
            }
        }

        /** Produce the actual invokedynamic call to StringConcatFactory */
        private void doCall(Type type, JCDiagnostic.DiagnosticPosition pos, String recipe, JCList<Object> staticArgs, JCList<Type> dynamicArgTypes) {
            Type.MethodType indyType = new Type.MethodType(dynamicArgTypes,
                    type,
                    JCList.nil(),
                    syms.methodClass);

            int prevPos = make.pos;
            try {
                make.at(pos);

                ListBuffer<Type> constTypes = new ListBuffer<>();
                ListBuffer<Object> constants = new ListBuffer<>();
                for (Object t : staticArgs) {
                    constants.add(t);
                    constTypes.add(syms.stringType);
                }

                JCList<Type> bsm_staticArgs = JCList.of(syms.methodHandleLookupType,
                        syms.stringType,
                        syms.methodTypeType)
                        .append(syms.stringType)
                        .appendList(constTypes);

                Symbol bsm = rs.resolveInternalMethod(pos,
                        gen.getAttrEnv(),
                        syms.stringConcatFactory,
                        names.makeConcatWithConstants,
                        bsm_staticArgs,
                        null);

                Symbol.DynamicMethodSymbol dynSym = new Symbol.DynamicMethodSymbol(names.makeConcatWithConstants,
                        syms.noSymbol,
                        ClassFile.REF_invokeStatic,
                        (Symbol.MethodSymbol)bsm,
                        indyType,
                        JCList.<Object>of(recipe).appendList(constants).toArray());

                Items.Item item = gen.getItems().makeDynamicItem(dynSym);
                item.invoke();
            } finally {
                make.at(prevPos);
            }
        }
    }

}
