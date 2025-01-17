/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.code;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.DeclaredType;
import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;

/** An annotation value.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Attribute implements AnnotationValue {

    /** The type of the annotation element. */
    public Type type;

    public Attribute(Type type) {
        this.type = type;
    }

    public abstract void accept(Visitor v);

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Object getValue() {
        throw new UnsupportedOperationException();
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
        throw new UnsupportedOperationException();
    }

    public boolean isSynthesized() {
        return false;
    }

    public TypeAnnotationPosition getPosition() { return null; }

    /** The value for an annotation element of primitive type or String. */
    public static class Constant extends Attribute {
        public final Object value;
        public void accept(Attribute.Visitor v) { v.visitConstant(this); }
        public Constant(Type type, Object value) {
            super(type);
            this.value = value;
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return Constants.format(value, type);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public Object getValue() {
            return Constants.decode(value, type);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            if (value instanceof String)
                return v.visitString((String) value, p);
            if (value instanceof Integer) {
                int i = (Integer) value;
                switch (type.getTag()) {
                case BOOLEAN:   return v.visitBoolean(i != 0, p);
                case CHAR:      return v.visitChar((char) i, p);
                case BYTE:      return v.visitByte((byte) i, p);
                case SHORT:     return v.visitShort((short) i, p);
                case INT:       return v.visitInt(i, p);
                }
            }
            switch (type.getTag()) {
            case LONG:          return v.visitLong((Long) value, p);
            case FLOAT:         return v.visitFloat((Float) value, p);
            case DOUBLE:        return v.visitDouble((Double) value, p);
            }
            throw new AssertionError("Bad annotation element value: " + value);
        }
    }

    /** The value for an annotation element of type java.lang.Class,
     *  represented as a ClassSymbol.
     */
    public static class Class extends Attribute {
        public final Type classType;
        public void accept(Attribute.Visitor v) { v.visitClass(this); }
        public Class(Types types, Type type) {
            super(makeClassType(types, type));
            this.classType = type;
        }
        static Type makeClassType(Types types, Type type) {
            Type arg = type.isPrimitive()
                ? types.boxedClass(type).type
                : types.erasure(type);
            return new Type.ClassType(types.syms.classType.getEnclosingType(),
                                      JCList.of(arg),
                                      types.syms.classType.tsym);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return classType + ".class";
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getValue() {
            return classType;
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitType(classType, p);
        }
    }

    /** A compound annotation element value, the type of which is an
     *  attribute interface.
     */
    public static class Compound extends Attribute implements AnnotationMirror {
        /** The attributes values, as pairs.  Each pair contains a
         *  reference to the accessing method in the attribute interface
         *  and the value to be returned when that method is called to
         *  access this attribute.
         */
        public final JCList<Pair<MethodSymbol,Attribute>> values;
        public TypeAnnotationPosition position;

        private boolean synthesized = false;

        @Override
        public boolean isSynthesized() {
            return synthesized;
        }

        public void setSynthesized(boolean synthesized) {
            this.synthesized = synthesized;
        }

        public Compound(Type type,
                        JCList<Pair<MethodSymbol,Attribute>> values,
                        TypeAnnotationPosition position) {
            super(type);
            this.values = values;
            this.position = position;
        }

        public Compound(Type type,
                        JCList<Pair<MethodSymbol,Attribute>> values) {
            this(type, values, null);
        }

        @Override
        public TypeAnnotationPosition getPosition() {
            if (hasUnknownPosition()) {
                if (values.size() != 0) {
                    Name valueName = values.head.fst.name.table.names.value;
                    Pair<MethodSymbol, Attribute> res = getElemPair(valueName);
                    position = res == null ? null : res.snd.getPosition();
                }
            }
            return position;
        }

        public boolean isContainerTypeCompound() {
            if (isSynthesized() && values.size() == 1)
                return getFirstEmbeddedTC() != null;
            return false;
        }

        private Attribute.Compound getFirstEmbeddedTC() {
            if (values.size() == 1) {
                Pair<MethodSymbol, Attribute> val = values.get(0);
                if (val.fst.getSimpleName().contentEquals("value")
                        && val.snd instanceof Attribute.Array) {
                    Attribute.Array arr = (Attribute.Array) val.snd;
                    if (arr.values.length != 0
                            && arr.values[0] instanceof TypeCompound)
                        return (TypeCompound) arr.values[0];
                }
            }
            return null;
        }

        public boolean tryFixPosition() {
            if (!isContainerTypeCompound())
                return false;

            Attribute.Compound from = getFirstEmbeddedTC();
            if (from != null && from.position != null &&
                    from.position.type != TargetType.UNKNOWN) {
                position = from.position;
                return true;
            }
            return false;
        }

        public boolean hasUnknownPosition() {
            return position.type == TargetType.UNKNOWN;
        }

        public void accept(Attribute.Visitor v) { v.visitCompound(this); }

        /**
         * Returns a string representation of this annotation.
         * String is of one of the forms:
         * <pre>
         *     {@code @com.example.foo(name1=val1, name2=val2)}
         *     {@code @com.example.foo(val)}
         *     {@code @com.example.foo}
         * </pre>
         * Omit parens for marker annotations, and omit "value=" when allowed.
         */
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("@");
            buf.append(type);
            int len = values.length();
            if (len > 0) {
                buf.append('(');
                boolean first = true;
                for (Pair<MethodSymbol, Attribute> value : values) {
                    if (!first) buf.append(", ");
                    first = false;

                    Name name = value.fst.name;
                    if (len > 1 || name != name.table.names.value) {
                        buf.append(name);
                        buf.append('=');
                    }
                    buf.append(value.snd);
                }
                buf.append(')');
            }
            return buf.toString();
        }

        public Attribute member(Name member) {
            Pair<MethodSymbol,Attribute> res = getElemPair(member);
            return res == null ? null : res.snd;
        }

        private Pair<MethodSymbol, Attribute> getElemPair(Name member) {
            for (Pair<MethodSymbol,Attribute> pair : values)
                if (pair.fst.name == member) return pair;
            return null;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Compound getValue() {
            return this;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitAnnotation(this, p);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public DeclaredType getAnnotationType() {
            return (DeclaredType) type;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Map<MethodSymbol, Attribute> getElementValues() {
            Map<MethodSymbol, Attribute> valmap = new LinkedHashMap<>();
            for (Pair<MethodSymbol, Attribute> value : values)
                valmap.put(value.fst, value.snd);
            return valmap;
        }
    }

    public static class TypeCompound extends Compound {
        public TypeCompound(Attribute.Compound compound,
                            TypeAnnotationPosition position) {
            super(compound.type, compound.values, position);
        }

        public TypeCompound(Type type,
                            JCList<Pair<MethodSymbol,Attribute>> values,
                            TypeAnnotationPosition position) {
            super(type, values, position);
        }
    }

    /** The value for an annotation element of an array type.
     */
    public static class Array extends Attribute {
        public final Attribute[] values;
        public Array(Type type, Attribute[] values) {
            super(type);
            this.values = values;
        }

        public Array(Type type, JCList<Attribute> values) {
            super(type);
            this.values = values.toArray(new Attribute[values.size()]);
        }

        public void accept(Attribute.Visitor v) { v.visitArray(this); }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append('{');
            boolean first = true;
            for (Attribute value : values) {
                if (!first)
                    buf.append(", ");
                first = false;
                buf.append(value);
            }
            buf.append('}');
            return buf.toString();
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Attribute> getValue() {
            return JCList.from(values);
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitArray(getValue(), p);
        }

        @Override
        public TypeAnnotationPosition getPosition() {
            if (values.length != 0)
                return values[0].getPosition();
            else
                return null;
        }
    }

    /** The value for an annotation element of an enum type.
     */
    public static class Enum extends Attribute {
        public VarSymbol value;
        public Enum(Type type, VarSymbol value) {
            super(type);
            this.value = Assert.checkNonNull(value);
        }
        public void accept(Attribute.Visitor v) { v.visitEnum(this); }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return value.enclClass() + "." + value;     // qualified name
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public VarSymbol getValue() {
            return value;
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitEnumConstant(value, p);
        }
    }

    public static class Error extends Attribute {
        public Error(Type type) {
            super(type);
        }
        public void accept(Attribute.Visitor v) { v.visitError(this); }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "<error>";
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String getValue() {
            return toString();
        }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitString(toString(), p);
        }
    }

    public static class UnresolvedClass extends Error {
        public Type classType;
        public UnresolvedClass(Type type, Type classType) {
            super(type);
            this.classType = classType;
        }
    }

    /** A visitor type for dynamic dispatch on the kind of attribute value. */
    public static interface Visitor {
        void visitConstant(Constant value);
        void visitClass(Class clazz);
        void visitCompound(Compound compound);
        void visitArray(Array array);
        void visitEnum(Enum e);
        void visitError(Error e);
    }

    /** A mirror of java.lang.annotation.RetentionPolicy. */
    public static enum RetentionPolicy {
        SOURCE,
        CLASS,
        RUNTIME
    }
}
