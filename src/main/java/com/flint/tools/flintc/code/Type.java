/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.lang.model.type.*;

import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.code.TypeMetadata.Entry;
import com.flint.tools.flintc.code.Types.TypeMapping;
import com.flint.tools.flintc.comp.Infer.IncorporationAction;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;

import static com.flint.tools.flintc.code.BoundKind.*;
import static com.flint.tools.flintc.code.Flags.*;
import static com.flint.tools.flintc.code.Kinds.Kind.*;
import static com.flint.tools.flintc.code.TypeTag.*;

/** This class represents Java types. The class itself defines the behavior of
 *  the following types:
 *  <pre>
 *  base types (tags: BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN),
 *  type `void' (tag: VOID),
 *  the bottom type (tag: BOT),
 *  the missing type (tag: NONE).
 *  </pre>
 *  <p>The behavior of the following types is defined in subclasses, which are
 *  all static inner classes of this class:
 *  <pre>
 *  class types (tag: CLASS, class: ClassType),
 *  array types (tag: ARRAY, class: ArrayType),
 *  method types (tag: METHOD, class: MethodType),
 *  package types (tag: PACKAGE, class: PackageType),
 *  type variables (tag: TYPEVAR, class: TypeVar),
 *  type arguments (tag: WILDCARD, class: WildcardType),
 *  generic method types (tag: FORALL, class: ForAll),
 *  the error type (tag: ERROR, class: ErrorType).
 *  </pre>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 *  @see TypeTag
 */
public abstract class Type extends AnnoConstruct implements TypeMirror {

    /**
     * Type metadata,  Should be {@code null} for the default value.
     *
     * Note: it is an invariant that for any {@code TypeMetadata}
     * class, a given {@code Type} may have at most one metadata array
     * entry of that class.
     */
    protected final TypeMetadata metadata;

    public TypeMetadata getMetadata() {
        return metadata;
    }

    public Entry getMetadataOfKind(final Entry.Kind kind) {
        return metadata != null ? metadata.get(kind) : null;
    }

    /** Constant type: no type at all. */
    public static final JCNoType noType = new JCNoType() {
        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "none";
        }
    };

    /** Constant type: special type to be used during recovery of deferred expressions. */
    public static final JCNoType recoveryType = new JCNoType(){
        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "recovery";
        }
    };

    /** Constant type: special type to be used for marking stuck trees. */
    public static final JCNoType stuckType = new JCNoType() {
        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return "stuck";
        }
    };

    /** If this switch is turned on, the names of type variables
     *  and anonymous classes are printed with hashcodes appended.
     */
    public static boolean moreInfo = false;

    /** The defining class / interface / package / type variable.
     */
    public TypeSymbol tsym;

    /**
     * Checks if the current type tag is equal to the given tag.
     * @return true if tag is equal to the current type tag.
     */
    public boolean hasTag(TypeTag tag) {
        return tag == getTag();
    }

    /**
     * Returns the current type tag.
     * @return the value of the current type tag.
     */
    public abstract TypeTag getTag();

    public boolean isNumeric() {
        return false;
    }

    public boolean isIntegral() {
        return false;
    }

    public boolean isPrimitive() {
        return false;
    }

    public boolean isPrimitiveOrVoid() {
        return false;
    }

    public boolean isReference() {
        return false;
    }

    public boolean isNullOrReference() {
        return false;
    }

    public boolean isPartial() {
        return false;
    }

    /**
     * The constant value of this type, null if this type does not
     * have a constant value attribute. Only primitive types and
     * strings (ClassType) can have a constant value attribute.
     * @return the constant value attribute of this type
     */
    public Object constValue() {
        return null;
    }

    /** Is this a constant type whose value is false?
     */
    public boolean isFalse() {
        return false;
    }

    /** Is this a constant type whose value is true?
     */
    public boolean isTrue() {
        return false;
    }

    /**
     * Get the representation of this type used for modelling purposes.
     * By default, this is itself. For ErrorType, a different value
     * may be provided.
     */
    public Type getModelType() {
        return this;
    }

    public static JCList<Type> getModelTypes(JCList<Type> ts) {
        ListBuffer<Type> lb = new ListBuffer<>();
        for (Type t: ts)
            lb.append(t.getModelType());
        return lb.toList();
    }

    /**For ErrorType, returns the original type, otherwise returns the type itself.
     */
    public Type getOriginalType() {
        return this;
    }

    public <R,S> R accept(Visitor<R,S> v, S s) { return v.visitType(this, s); }

    /** Define a type given its tag, type symbol, and type annotations
     */

    public Type(TypeSymbol tsym, TypeMetadata metadata) {
        Assert.checkNonNull(metadata);
        this.tsym = tsym;
        this.metadata = metadata;
    }

    /**
     * A subclass of {@link Types.TypeMapping} which applies a mapping recursively to the subterms
     * of a given type expression. This mapping returns the original type is no changes occurred
     * when recursively mapping the original type's subterms.
     */
    public static abstract class StructuralTypeMapping<S> extends Types.TypeMapping<S> {

        @Override
        public Type visitClassType(ClassType t, S s) {
            Type outer = t.getEnclosingType();
            Type outer1 = visit(outer, s);
            JCList<Type> typarams = t.getTypeArguments();
            JCList<Type> typarams1 = visit(typarams, s);
            if (outer1 == outer && typarams1 == typarams) return t;
            else return new ClassType(outer1, typarams1, t.tsym, t.metadata) {
                @Override
                protected boolean needsStripping() {
                    return true;
                }
            };
        }

        @Override
        public Type visitWildcardType(WildcardType wt, S s) {
            Type t = wt.type;
            if (t != null)
                t = visit(t, s);
            if (t == wt.type)
                return wt;
            else
                return new WildcardType(t, wt.kind, wt.tsym, wt.bound, wt.metadata) {
                    @Override
                    protected boolean needsStripping() {
                        return true;
                    }
                };
        }

        @Override
        public Type visitArrayType(ArrayType t, S s) {
            Type elemtype = t.elemtype;
            Type elemtype1 = visit(elemtype, s);
            if (elemtype1 == elemtype) return t;
            else return new ArrayType(elemtype1, t.tsym, t.metadata) {
                @Override
                protected boolean needsStripping() {
                    return true;
                }
            };
        }

        @Override
        public Type visitMethodType(MethodType t, S s) {
            JCList<Type> argtypes = t.argtypes;
            Type restype = t.restype;
            JCList<Type> thrown = t.thrown;
            JCList<Type> argtypes1 = visit(argtypes, s);
            Type restype1 = visit(restype, s);
            JCList<Type> thrown1 = visit(thrown, s);
            if (argtypes1 == argtypes &&
                restype1 == restype &&
                thrown1 == thrown) return t;
            else return new MethodType(argtypes1, restype1, thrown1, t.tsym) {
                @Override
                protected boolean needsStripping() {
                    return true;
                }
            };
        }

        @Override
        public Type visitForAll(ForAll t, S s) {
            return visit(t.qtype, s);
        }
    }

    /** map a type function over all immediate descendants of this type
     */
    public <Z> Type map(TypeMapping<Z> mapping, Z arg) {
        return mapping.visit(this, arg);
    }

    /** map a type function over all immediate descendants of this type (no arg version)
     */
    public <Z> Type map(TypeMapping<Z> mapping) {
        return mapping.visit(this, null);
    }

    /** Define a constant type, of the same kind as this type
     *  and with given constant value
     */
    public Type constType(Object constValue) {
        throw new AssertionError();
    }

    /**
     * If this is a constant type, return its underlying type.
     * Otherwise, return the type itself.
     */
    public Type baseType() {
        return this;
    }

    /**
     * Returns the original version of this type, before metadata were added. This routine is meant
     * for internal use only (i.e. {@link Type#equalsIgnoreMetadata(Type)}, {@link Type#stripMetadata});
     * it should not be used outside this class.
     */
    protected Type typeNoMetadata() {
        return metadata == TypeMetadata.EMPTY ? this : baseType();
    }

    /**
     * Create a new copy of this type but with the specified TypeMetadata.
     */
    public abstract Type cloneWithMetadata(TypeMetadata metadata);

    /**
     * Does this type require annotation stripping for API clients?
     */
    protected boolean needsStripping() {
        return false;
    }

    /**
     * Strip all metadata associated with this type - this could return a new clone of the type.
     * This routine is only used to present the correct annotated types back to the users when types
     * are accessed through compiler APIs; it should not be used anywhere in the compiler internals
     * as doing so might result in performance penalties.
     */
    public Type stripMetadataIfNeeded() {
        return needsStripping() ?
                accept(stripMetadata, null) :
                this;
    }

    public Type stripMetadata() {
        return accept(stripMetadata, null);
    }
    //where
        private final static TypeMapping<Void> stripMetadata = new StructuralTypeMapping<Void>() {
            @Override
            public Type visitClassType(ClassType t, Void aVoid) {
                return super.visitClassType((ClassType)t.typeNoMetadata(), aVoid);
            }

            @Override
            public Type visitArrayType(ArrayType t, Void aVoid) {
                return super.visitArrayType((ArrayType)t.typeNoMetadata(), aVoid);
            }

            @Override
            public Type visitTypeVar(TypeVar t, Void aVoid) {
                return super.visitTypeVar((TypeVar)t.typeNoMetadata(), aVoid);
            }

            @Override
            public Type visitWildcardType(WildcardType wt, Void aVoid) {
                return super.visitWildcardType((WildcardType)wt.typeNoMetadata(), aVoid);
            }
        };

    public Type annotatedType(final JCList<Attribute.TypeCompound> annos) {
        final Entry annoMetadata = new TypeMetadata.Annotations(annos);
        return cloneWithMetadata(metadata.combine(annoMetadata));
    }

    public boolean isAnnotated() {
        final TypeMetadata.Annotations metadata =
            (TypeMetadata.Annotations)getMetadataOfKind(Entry.Kind.ANNOTATIONS);

        return null != metadata && !metadata.getAnnotations().isEmpty();
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public JCList<Attribute.TypeCompound> getAnnotationMirrors() {
        final TypeMetadata.Annotations metadata =
            (TypeMetadata.Annotations)getMetadataOfKind(Entry.Kind.ANNOTATIONS);

        return metadata == null ? JCList.nil() : metadata.getAnnotations();
    }


    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return null;
    }


    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        @SuppressWarnings("unchecked")
        A[] tmp = (A[]) java.lang.reflect.Array.newInstance(annotationType, 0);
        return tmp;
    }

    /** Return the base types of a list of types.
     */
    public static JCList<Type> baseTypes(JCList<Type> ts) {
        if (ts.nonEmpty()) {
            Type t = ts.head.baseType();
            JCList<Type> baseTypes = baseTypes(ts.tail);
            if (t != ts.head || baseTypes != ts.tail)
                return baseTypes.prepend(t);
        }
        return ts;
    }

    protected void appendAnnotationsString(StringBuilder sb,
                                         boolean prefix) {
        if (isAnnotated()) {
            if (prefix) {
                sb.append(" ");
            }
            sb.append(getAnnotationMirrors());
            sb.append(" ");
        }
    }

    protected void appendAnnotationsString(StringBuilder sb) {
        appendAnnotationsString(sb, false);
    }

    /** The Java source which this type represents.
     */
    @DefinedBy(Api.LANGUAGE_MODEL)
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendAnnotationsString(sb);
        if (tsym == null || tsym.name == null) {
            sb.append("<none>");
        } else {
            sb.append(tsym.name);
        }
        if (moreInfo && hasTag(TYPEVAR)) {
            sb.append(hashCode());
        }
        return sb.toString();
    }

    /**
     * The Java source which this type list represents.  A List is
     * represented as a comma-spearated listing of the elements in
     * that list.
     */
    public static String toString(JCList<Type> ts) {
        if (ts.isEmpty()) {
            return "";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(ts.head.toString());
            for (JCList<Type> l = ts.tail; l.nonEmpty(); l = l.tail)
                buf.append(",").append(l.head.toString());
            return buf.toString();
        }
    }

    /**
     * The constant value of this type, converted to String
     */
    public String stringValue() {
        Object cv = Assert.checkNonNull(constValue());
        return cv.toString();
    }

    /**
     * Override this method with care. For most Type instances this should behave as ==.
     */
    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public boolean equals(Object t) {
        return this == t;
    }

    public boolean equalsIgnoreMetadata(Type t) {
        return typeNoMetadata().equals(t.typeNoMetadata());
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public int hashCode() {
        return super.hashCode();
    }

    public String argtypes(boolean varargs) {
        JCList<Type> args = getParameterTypes();
        if (!varargs) return args.toString();
        StringBuilder buf = new StringBuilder();
        while (args.tail.nonEmpty()) {
            buf.append(args.head);
            args = args.tail;
            buf.append(',');
        }
        if (args.head.hasTag(ARRAY)) {
            buf.append(((ArrayType)args.head).elemtype);
            if (args.head.getAnnotationMirrors().nonEmpty()) {
                buf.append(args.head.getAnnotationMirrors());
            }
            buf.append("...");
        } else {
            buf.append(args.head);
        }
        return buf.toString();
    }

    /** Access methods.
     */
    public JCList<Type> getTypeArguments()  { return JCList.nil(); }
    public Type              getEnclosingType()  { return null; }
    public JCList<Type> getParameterTypes() { return JCList.nil(); }
    public Type              getReturnType()     { return null; }
    public Type              getReceiverType()   { return null; }
    public JCList<Type> getThrownTypes()    { return JCList.nil(); }
    public Type              getUpperBound()     { return null; }
    public Type              getLowerBound()     { return null; }

    /** Navigation methods, these will work for classes, type variables,
     *  foralls, but will return null for arrays and methods.
     */

   /** Return all parameters of this type and all its outer types in order
    *  outer (first) to inner (last).
    */
    public JCList<Type> allparams() { return JCList.nil(); }

    /** Does this type contain "error" elements?
     */
    public boolean isErroneous() {
        return false;
    }

    public static boolean isErroneous(JCList<Type> ts) {
        for (JCList<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (l.head.isErroneous()) return true;
        return false;
    }

    /** Is this type parameterized?
     *  A class type is parameterized if it has some parameters.
     *  An array type is parameterized if its element type is parameterized.
     *  All other types are not parameterized.
     */
    public boolean isParameterized() {
        return false;
    }

    /** Is this type a raw type?
     *  A class type is a raw type if it misses some of its parameters.
     *  An array type is a raw type if its element type is raw.
     *  All other types are not raw.
     *  Type validation will ensure that the only raw types
     *  in a program are types that miss all their type variables.
     */
    public boolean isRaw() {
        return false;
    }

    /**
     * A compound type is a special class type whose supertypes are used to store a list
     * of component types. There are two kinds of compound types: (i) intersection types
     * {@see IntersectionClassType} and (ii) union types {@see UnionClassType}.
     */
    public boolean isCompound() {
        return false;
    }

    public boolean isIntersection() {
        return false;
    }

    public boolean isUnion() {
        return false;
    }

    public boolean isInterface() {
        return (tsym.flags() & INTERFACE) != 0;
    }

    public boolean isFinal() {
        return (tsym.flags() & FINAL) != 0;
    }

    /**
     * Does this type contain occurrences of type t?
     */
    public boolean contains(Type t) {
        return t.equalsIgnoreMetadata(this);
    }

    public static boolean contains(JCList<Type> ts, Type t) {
        for (JCList<Type> l = ts;
             l.tail != null /*inlined: l.nonEmpty()*/;
             l = l.tail)
            if (l.head.contains(t)) return true;
        return false;
    }

    /** Does this type contain an occurrence of some type in 'ts'?
     */
    public boolean containsAny(JCList<Type> ts) {
        for (Type t : ts)
            if (this.contains(t)) return true;
        return false;
    }

    public static boolean containsAny(JCList<Type> ts1, JCList<Type> ts2) {
        for (Type t : ts1)
            if (t.containsAny(ts2)) return true;
        return false;
    }

    public static JCList<Type> filter(JCList<Type> ts, Filter<Type> tf) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            if (tf.accepts(t)) {
                buf.append(t);
            }
        }
        return buf.toList();
    }

    public boolean isSuperBound() { return false; }
    public boolean isExtendsBound() { return false; }
    public boolean isUnbound() { return false; }
    public Type withTypeVar(Type t) { return this; }

    /** The underlying method type of this type.
     */
    public MethodType asMethodType() { throw new AssertionError(); }

    /** Complete loading all classes in this type.
     */
    public void complete() {}

    public TypeSymbol asElement() {
        return tsym;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public TypeKind getKind() {
        return TypeKind.OTHER;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        throw new AssertionError();
    }

    public static class JCPrimitiveType extends Type
            implements PrimitiveType {

        TypeTag tag;

        public JCPrimitiveType(TypeTag tag, TypeSymbol tsym) {
            this(tag, tsym, TypeMetadata.EMPTY);
        }

        private JCPrimitiveType(TypeTag tag, TypeSymbol tsym, TypeMetadata metadata) {
            super(tsym, metadata);
            this.tag = tag;
            Assert.check(tag.isPrimitive);
        }

        @Override
        public Type.JCPrimitiveType cloneWithMetadata(TypeMetadata md) {
            return new Type.JCPrimitiveType(tag, tsym, md) {
                @Override
                public Type baseType() { return Type.JCPrimitiveType.this.baseType(); }
            };
        }

        @Override
        public boolean isNumeric() {
            return tag != BOOLEAN;
        }

        @Override
        public boolean isIntegral() {
            switch (tag) {
                case CHAR:
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean isPrimitive() {
            return true;
        }

        @Override
        public TypeTag getTag() {
            return tag;
        }

        @Override
        public boolean isPrimitiveOrVoid() {
            return true;
        }

        /** Define a constant type, of the same kind as this type
         *  and with given constant value
         */
        @Override
        public Type constType(Object constValue) {
            final Object value = constValue;
            return new Type.JCPrimitiveType(tag, tsym, metadata) {
                    @Override
                    public Object constValue() {
                        return value;
                    }
                    @Override
                    public Type baseType() {
                        return tsym.type;
                    }
                };
        }

        /**
         * The constant value of this type, converted to String
         */
        @Override
        public String stringValue() {
            Object cv = Assert.checkNonNull(constValue());
            if (tag == BOOLEAN) {
                return ((Integer) cv).intValue() == 0 ? "false" : "true";
            }
            else if (tag == CHAR) {
                return String.valueOf((char) ((Integer) cv).intValue());
            }
            else {
                return cv.toString();
            }
        }

        /** Is this a constant type whose value is false?
         */
        @Override
        public boolean isFalse() {
            return
                tag == BOOLEAN &&
                constValue() != null &&
                ((Integer)constValue()).intValue() == 0;
        }

        /** Is this a constant type whose value is true?
         */
        @Override
        public boolean isTrue() {
            return
                tag == BOOLEAN &&
                constValue() != null &&
                ((Integer)constValue()).intValue() != 0;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitPrimitive(this, p);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            switch (tag) {
                case BYTE:      return TypeKind.BYTE;
                case CHAR:      return TypeKind.CHAR;
                case SHORT:     return TypeKind.SHORT;
                case INT:       return TypeKind.INT;
                case LONG:      return TypeKind.LONG;
                case FLOAT:     return TypeKind.FLOAT;
                case DOUBLE:    return TypeKind.DOUBLE;
                case BOOLEAN:   return TypeKind.BOOLEAN;
            }
            throw new AssertionError();
        }

    }

    public static class WildcardType extends Type
            implements javax.lang.model.type.WildcardType {

        public Type type;
        public BoundKind kind;
        public Type.TypeVar bound;

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitWildcardType(this, s);
        }

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym) {
            this(type, kind, tsym, null, TypeMetadata.EMPTY);
        }

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym,
                            TypeMetadata metadata) {
            this(type, kind, tsym, null, metadata);
        }

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym,
                            Type.TypeVar bound) {
            this(type, kind, tsym, bound, TypeMetadata.EMPTY);
        }

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym,
                            Type.TypeVar bound, TypeMetadata metadata) {
            super(tsym, metadata);
            this.type = Assert.checkNonNull(type);
            this.kind = kind;
            this.bound = bound;
        }

        @Override
        public WildcardType cloneWithMetadata(TypeMetadata md) {
            return new WildcardType(type, kind, tsym, bound, md) {
                @Override
                public Type baseType() { return WildcardType.this.baseType(); }
            };
        }

        @Override
        public TypeTag getTag() {
            return WILDCARD;
        }

        @Override
        public boolean contains(Type t) {
            return kind != UNBOUND && type.contains(t);
        }

        public boolean isSuperBound() {
            return kind == SUPER ||
                kind == UNBOUND;
        }
        public boolean isExtendsBound() {
            return kind == EXTENDS ||
                kind == UNBOUND;
        }
        public boolean isUnbound() {
            return kind == UNBOUND;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        @Override
        public Type withTypeVar(Type t) {
            //-System.err.println(this+".withTypeVar("+t+");");//DEBUG
            if (bound == t)
                return this;
            bound = (Type.TypeVar)t;
            return this;
        }

        boolean isPrintingBound = false;
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder s = new StringBuilder();
            appendAnnotationsString(s);
            s.append(kind.toString());
            if (kind != UNBOUND)
                s.append(type);
            if (moreInfo && bound != null && !isPrintingBound)
                try {
                    isPrintingBound = true;
                    s.append("{:").append(bound.bound).append(":}");
                } finally {
                    isPrintingBound = false;
                }
            return s.toString();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getExtendsBound() {
            if (kind == EXTENDS)
                return type;
            else
                return null;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getSuperBound() {
            if (kind == SUPER)
                return type;
            else
                return null;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.WILDCARD;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitWildcard(this, p);
        }
    }

    public static class ClassType extends Type implements DeclaredType {

        /** The enclosing type of this type. If this is the type of an inner
         *  class, outer_field refers to the type of its enclosing
         *  instance class, in all other cases it refers to noType.
         */
        private Type outer_field;

        /** The type parameters of this type (to be set once class is loaded).
         */
        public JCList<Type> typarams_field;

        /** A cache variable for the type parameters of this type,
         *  appended to all parameters of its enclosing class.
         *  @see #allparams
         */
        public JCList<Type> allparams_field;

        /** The supertype of this class (to be set once class is loaded).
         */
        public Type supertype_field;

        /** The interfaces of this class (to be set once class is loaded).
         */
        public JCList<Type> interfaces_field;

        /** All the interfaces of this class, including missing ones.
         */
        public JCList<Type> all_interfaces_field;

        public ClassType(Type outer, JCList<Type> typarams, TypeSymbol tsym) {
            this(outer, typarams, tsym, TypeMetadata.EMPTY);
        }

        public ClassType(Type outer, JCList<Type> typarams, TypeSymbol tsym,
                         TypeMetadata metadata) {
            super(tsym, metadata);
            this.outer_field = outer;
            this.typarams_field = typarams;
            this.allparams_field = null;
            this.supertype_field = null;
            this.interfaces_field = null;
        }

        @Override
        public Type.ClassType cloneWithMetadata(TypeMetadata md) {
            return new Type.ClassType(outer_field, typarams_field, tsym, md) {
                @Override
                public Type baseType() { return Type.ClassType.this.baseType(); }
            };
        }

        @Override
        public TypeTag getTag() {
            return CLASS;
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitClassType(this, s);
        }

        public Type constType(Object constValue) {
            final Object value = constValue;
            return new Type.ClassType(getEnclosingType(), typarams_field, tsym, metadata) {
                    @Override
                    public Object constValue() {
                        return value;
                    }
                    @Override
                    public Type baseType() {
                        return tsym.type;
                    }
                };
        }

        /** The Java source which this type represents.
         */
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder buf = new StringBuilder();
            if (getEnclosingType().hasTag(CLASS) && tsym.owner.kind == TYP) {
                buf.append(getEnclosingType().toString());
                buf.append(".");
                appendAnnotationsString(buf);
                buf.append(className(tsym, false));
            } else {
                appendAnnotationsString(buf);
                buf.append(className(tsym, true));
            }

            if (getTypeArguments().nonEmpty()) {
                buf.append('<');
                buf.append(getTypeArguments().toString());
                buf.append(">");
            }
            return buf.toString();
        }
//where
            private String className(Symbol sym, boolean longform) {
                if (sym.name.isEmpty() && (sym.flags() & COMPOUND) != 0) {
                    StringBuilder s = new StringBuilder(supertype_field.toString());
                    for (JCList<Type> is = interfaces_field; is.nonEmpty(); is = is.tail) {
                        s.append("&");
                        s.append(is.head.toString());
                    }
                    return s.toString();
                } else if (sym.name.isEmpty()) {
                    String s;
                    Type.ClassType norm = (Type.ClassType) tsym.type;
                    if (norm == null) {
                        s = Log.getLocalizedString("anonymous.class", (Object)null);
                    } else if (norm.interfaces_field != null && norm.interfaces_field.nonEmpty()) {
                        s = Log.getLocalizedString("anonymous.class",
                                                   norm.interfaces_field.head);
                    } else {
                        s = Log.getLocalizedString("anonymous.class",
                                                   norm.supertype_field);
                    }
                    if (moreInfo)
                        s += String.valueOf(sym.hashCode());
                    return s;
                } else if (longform) {
                    return sym.getQualifiedName().toString();
                } else {
                    return sym.name.toString();
                }
            }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getTypeArguments() {
            if (typarams_field == null) {
                complete();
                if (typarams_field == null)
                    typarams_field = JCList.nil();
            }
            return typarams_field;
        }

        public boolean hasErasedSupertypes() {
            return isRaw();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getEnclosingType() {
            return outer_field;
        }

        public void setEnclosingType(Type outer) {
            outer_field = outer;
        }

        public JCList<Type> allparams() {
            if (allparams_field == null) {
                allparams_field = getTypeArguments().prependList(getEnclosingType().allparams());
            }
            return allparams_field;
        }

        public boolean isErroneous() {
            return
                getEnclosingType().isErroneous() ||
                isErroneous(getTypeArguments()) ||
                this != tsym.type && tsym.type.isErroneous();
        }

        public boolean isParameterized() {
            return allparams().tail != null;
            // optimization, was: allparams().nonEmpty();
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        /** A cache for the rank. */
        int rank_field = -1;

        /** A class type is raw if it misses some
         *  of its type parameter sections.
         *  After validation, this is equivalent to:
         *  {@code allparams.isEmpty() && tsym.type.allparams.nonEmpty(); }
         */
        public boolean isRaw() {
            return
                this != tsym.type && // necessary, but not sufficient condition
                tsym.type.allparams().nonEmpty() &&
                allparams().isEmpty();
        }

        public boolean contains(Type elem) {
            return
                elem.equalsIgnoreMetadata(this)
                || (isParameterized()
                    && (getEnclosingType().contains(elem) || contains(getTypeArguments(), elem)))
                || (isCompound()
                    && (supertype_field.contains(elem) || contains(interfaces_field, elem)));
        }

        public void complete() {
            tsym.complete();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitDeclared(this, p);
        }
    }

    public static class ErasedClassType extends ClassType {
        public ErasedClassType(Type outer, TypeSymbol tsym,
                               TypeMetadata metadata) {
            super(outer, JCList.nil(), tsym, metadata);
        }

        @Override
        public boolean hasErasedSupertypes() {
            return true;
        }
    }

    // a clone of a ClassType that knows about the alternatives of a union type.
    public static class UnionClassType extends ClassType implements UnionType {
        final JCList<? extends Type> alternatives_field;

        public UnionClassType(Type.ClassType ct, JCList<? extends Type> alternatives) {
            // Presently no way to refer to this type directly, so we
            // cannot put annotations directly on it.
            super(ct.outer_field, ct.typarams_field, ct.tsym);
            allparams_field = ct.allparams_field;
            supertype_field = ct.supertype_field;
            interfaces_field = ct.interfaces_field;
            all_interfaces_field = ct.interfaces_field;
            alternatives_field = alternatives;
        }

        @Override
        public Type.UnionClassType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a union type");
        }

        public Type getLub() {
            return tsym.type;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<? extends TypeMirror> getAlternatives() {
            return Collections.unmodifiableList(alternatives_field);
        }

        @Override
        public boolean isUnion() {
            return true;
        }

        @Override
        public boolean isCompound() {
            return getLub().isCompound();
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.UNION;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitUnion(this, p);
        }

        public Iterable<? extends Type> getAlternativeTypes() {
            return alternatives_field;
        }
    }

    // a clone of a ClassType that knows about the bounds of an intersection type.
    public static class IntersectionClassType extends ClassType implements IntersectionType {

        public boolean allInterfaces;

        public IntersectionClassType(JCList<Type> bounds, ClassSymbol csym, boolean allInterfaces) {
            // Presently no way to refer to this type directly, so we
            // cannot put annotations directly on it.
            super(Type.noType, JCList.nil(), csym);
            this.allInterfaces = allInterfaces;
            Assert.check((csym.flags() & COMPOUND) != 0);
            supertype_field = bounds.head;
            interfaces_field = bounds.tail;
            Assert.check(!supertype_field.tsym.isCompleted() ||
                    !supertype_field.isInterface(), supertype_field);
        }

        @Override
        public Type.IntersectionClassType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to an intersection type");
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<? extends TypeMirror> getBounds() {
            return Collections.unmodifiableList(getExplicitComponents());
        }

        @Override
        public boolean isCompound() {
            return true;
        }

        public JCList<Type> getComponents() {
            return interfaces_field.prepend(supertype_field);
        }

        @Override
        public boolean isIntersection() {
            return true;
        }

        public JCList<Type> getExplicitComponents() {
            return allInterfaces ?
                    interfaces_field :
                    getComponents();
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.INTERSECTION;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitIntersection(this, p);
        }
    }

    public static class ArrayType extends Type
            implements javax.lang.model.type.ArrayType {

        public Type elemtype;

        public ArrayType(Type elemtype, TypeSymbol arrayClass) {
            this(elemtype, arrayClass, TypeMetadata.EMPTY);
        }

        public ArrayType(Type elemtype, TypeSymbol arrayClass,
                         TypeMetadata metadata) {
            super(arrayClass, metadata);
            this.elemtype = elemtype;
        }

        public ArrayType(ArrayType that) {
            //note: type metadata is deliberately shared here, as we want side-effects from annotation
            //processing to flow from original array to the cloned array.
            this(that.elemtype, that.tsym, that.getMetadata());
        }

        @Override
        public ArrayType cloneWithMetadata(TypeMetadata md) {
            return new ArrayType(elemtype, tsym, md) {
                @Override
                public Type baseType() { return ArrayType.this.baseType(); }
            };
        }

        @Override
        public TypeTag getTag() {
            return ARRAY;
        }

        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitArrayType(this, s);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // First append root component type
            Type t = elemtype;
            while (t.getKind() == TypeKind.ARRAY)
                t = ((ArrayType) t).getComponentType();
            sb.append(t);

            // then append @Anno[] @Anno[] ... @Anno[]
            t = this;
            do {
                t.appendAnnotationsString(sb, true);
                sb.append("[]");
                t = ((ArrayType) t).getComponentType();
            } while (t.getKind() == TypeKind.ARRAY);

            return sb.toString();
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean equals(Object obj) {
            if (obj instanceof ArrayType) {
                ArrayType that = (ArrayType)obj;
                return this == that ||
                        elemtype.equals(that.elemtype);
            }

            return false;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public int hashCode() {
            return (ARRAY.ordinal() << 5) + elemtype.hashCode();
        }

        public boolean isVarargs() {
            return false;
        }

        public JCList<Type> allparams() { return elemtype.allparams(); }

        public boolean isErroneous() {
            return elemtype.isErroneous();
        }

        public boolean isParameterized() {
            return elemtype.isParameterized();
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        public boolean isRaw() {
            return elemtype.isRaw();
        }

        public ArrayType makeVarargs() {
            return new ArrayType(elemtype, tsym, metadata) {
                @Override
                public boolean isVarargs() {
                    return true;
                }
            };
        }

        public boolean contains(Type elem) {
            return elem.equalsIgnoreMetadata(this) || elemtype.contains(elem);
        }

        public void complete() {
            elemtype.complete();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getComponentType() {
            return elemtype;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.ARRAY;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitArray(this, p);
        }
    }

    public static class MethodType extends Type implements ExecutableType {

        public JCList<Type> argtypes;
        public Type restype;
        public JCList<Type> thrown;

        /** The type annotations on the method receiver.
         */
        public Type recvtype;

        public MethodType(JCList<Type> argtypes,
                          Type restype,
                          JCList<Type> thrown,
                          TypeSymbol methodClass) {
            // Presently no way to refer to a method type directly, so
            // we cannot put type annotations on it.
            super(methodClass, TypeMetadata.EMPTY);
            this.argtypes = argtypes;
            this.restype = restype;
            this.thrown = thrown;
        }

        @Override
        public Type.MethodType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a method type");
        }

        @Override
        public TypeTag getTag() {
            return METHOD;
        }

        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitMethodType(this, s);
        }

        /** The Java source which this type represents.
         *
         *  XXX 06/09/99 iris This isn't correct Java syntax, but it probably
         *  should be.
         */
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendAnnotationsString(sb);
            sb.append('(');
            sb.append(argtypes);
            sb.append(')');
            sb.append(restype);
            return sb.toString();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getParameterTypes() { return argtypes; }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type              getReturnType()     { return restype; }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type              getReceiverType()   { return recvtype; }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getThrownTypes()    { return thrown; }

        public boolean isErroneous() {
            return
                isErroneous(argtypes) ||
                restype != null && restype.isErroneous();
        }

        public boolean contains(Type elem) {
            return elem.equalsIgnoreMetadata(this) || contains(argtypes, elem) || restype.contains(elem) || contains(thrown, elem);
        }

        public Type.MethodType asMethodType() { return this; }

        public void complete() {
            for (JCList<Type> l = argtypes; l.nonEmpty(); l = l.tail)
                l.head.complete();
            restype.complete();
            recvtype.complete();
            for (JCList<Type> l = thrown; l.nonEmpty(); l = l.tail)
                l.head.complete();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<TypeVar> getTypeVariables() {
            return JCList.nil();
        }

        public TypeSymbol asElement() {
            return null;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.EXECUTABLE;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }
    }

    public static class PackageType extends Type implements NoType {

        PackageType(PackageSymbol tsym) {
            // Package types cannot be annotated
            super(tsym, TypeMetadata.EMPTY);
        }

        @Override
        public Type.PackageType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a package type");
        }

        @Override
        public TypeTag getTag() {
            return PACKAGE;
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitPackageType(this, s);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return tsym.getQualifiedName().toString();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.PACKAGE;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }
    }

    public static class ModuleType extends Type implements NoType {

        ModuleType(ModuleSymbol tsym) {
            // Module types cannot be annotated
            super(tsym, TypeMetadata.EMPTY);
        }

        @Override
        public Type.ModuleType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a module type");
        }

        @Override
        public Type.ModuleType annotatedType(JCList<Attribute.TypeCompound> annos) {
            throw new AssertionError("Cannot annotate a module type");
        }

        @Override
        public TypeTag getTag() {
            return TypeTag.MODULE;
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitModuleType(this, s);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            return tsym.getQualifiedName().toString();
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.MODULE;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }
    }

    public static class TypeVar extends Type implements TypeVariable {

        /** The upper bound of this type variable; set from outside.
         *  Must be nonempty once it is set.
         *  For a bound, `bound' is the bound type itself.
         *  Multiple bounds are expressed as a single class type which has the
         *  individual bounds as superclass, respectively interfaces.
         *  The class type then has as `tsym' a compiler generated class `c',
         *  which has a flag COMPOUND and whose owner is the type variable
         *  itself. Furthermore, the erasure_field of the class
         *  points to the first class or interface bound.
         */
        public Type bound = null;

        /** The lower bound of this type variable.
         *  TypeVars don't normally have a lower bound, so it is normally set
         *  to syms.botType.
         *  Subtypes, such as CapturedType, may provide a different value.
         */
        public Type lower;

        public TypeVar(Name name, Symbol owner, Type lower) {
            super(null, TypeMetadata.EMPTY);
            tsym = new TypeVariableSymbol(0, name, this, owner);
            this.bound = null;
            this.lower = lower;
        }

        public TypeVar(TypeSymbol tsym, Type bound, Type lower) {
            this(tsym, bound, lower, TypeMetadata.EMPTY);
        }

        public TypeVar(TypeSymbol tsym, Type bound, Type lower,
                       TypeMetadata metadata) {
            super(tsym, metadata);
            this.bound = bound;
            this.lower = lower;
        }

        @Override
        public Type.TypeVar cloneWithMetadata(TypeMetadata md) {
            return new Type.TypeVar(tsym, bound, lower, md) {
                @Override
                public Type baseType() { return Type.TypeVar.this.baseType(); }
            };
        }

        @Override
        public TypeTag getTag() {
            return TYPEVAR;
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitTypeVar(this, s);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getUpperBound() {
            if ((bound == null || bound.hasTag(NONE)) && this != tsym.type) {
                bound = tsym.type.getUpperBound();
            }
            return bound;
        }

        int rank_field = -1;

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getLowerBound() {
            return lower;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.TYPEVAR;
        }

        public boolean isCaptured() {
            return false;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitTypeVariable(this, p);
        }
    }

    /** A captured type variable comes from wildcards which can have
     *  both upper and lower bound.  CapturedType extends TypeVar with
     *  a lower bound.
     */
    public static class CapturedType extends TypeVar {

        public WildcardType wildcard;

        public CapturedType(Name name,
                            Symbol owner,
                            Type upper,
                            Type lower,
                            WildcardType wildcard) {
            super(name, owner, lower);
            this.lower = Assert.checkNonNull(lower);
            this.bound = upper;
            this.wildcard = wildcard;
        }

        public CapturedType(TypeSymbol tsym,
                            Type bound,
                            Type upper,
                            Type lower,
                            WildcardType wildcard,
                            TypeMetadata metadata) {
            super(tsym, bound, lower, metadata);
            this.wildcard = wildcard;
        }

        @Override
        public Type.CapturedType cloneWithMetadata(TypeMetadata md) {
            return new Type.CapturedType(tsym, bound, bound, lower, wildcard, md) {
                @Override
                public Type baseType() { return Type.CapturedType.this.baseType(); }
            };
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitCapturedType(this, s);
        }

        @Override
        public boolean isCaptured() {
            return true;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendAnnotationsString(sb);
            sb.append("capture#");
            sb.append((hashCode() & 0xFFFFFFFFL) % Printer.PRIME);
            sb.append(" of ");
            sb.append(wildcard);
            return sb.toString();
        }
    }

    public static abstract class DelegatedType extends Type {
        public Type qtype;
        public TypeTag tag;

        public DelegatedType(TypeTag tag, Type qtype) {
            this(tag, qtype, TypeMetadata.EMPTY);
        }

        public DelegatedType(TypeTag tag, Type qtype,
                             TypeMetadata metadata) {
            super(qtype.tsym, metadata);
            this.tag = tag;
            this.qtype = qtype;
        }

        public TypeTag getTag() { return tag; }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() { return qtype.toString(); }
        public JCList<Type> getTypeArguments() { return qtype.getTypeArguments(); }
        public Type getEnclosingType() { return qtype.getEnclosingType(); }
        public JCList<Type> getParameterTypes() { return qtype.getParameterTypes(); }
        public Type getReturnType() { return qtype.getReturnType(); }
        public Type getReceiverType() { return qtype.getReceiverType(); }
        public JCList<Type> getThrownTypes() { return qtype.getThrownTypes(); }
        public JCList<Type> allparams() { return qtype.allparams(); }
        public Type getUpperBound() { return qtype.getUpperBound(); }
        public boolean isErroneous() { return qtype.isErroneous(); }
    }

    /**
     * The type of a generic method type. It consists of a method type and
     * a list of method type-parameters that are used within the method
     * type.
     */
    public static class ForAll extends DelegatedType implements ExecutableType {
        public JCList<Type> tvars;

        public ForAll(JCList<Type> tvars, Type qtype) {
            super(FORALL, (Type.MethodType)qtype);
            this.tvars = tvars;
        }

        @Override
        public Type.ForAll cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a forall type");
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitForAll(this, s);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendAnnotationsString(sb);
            sb.append('<');
            sb.append(tvars);
            sb.append('>');
            sb.append(qtype);
            return sb.toString();
        }

        public JCList<Type> getTypeArguments()   { return tvars; }

        public boolean isErroneous()  {
            return qtype.isErroneous();
        }

        public boolean contains(Type elem) {
            return qtype.contains(elem);
        }

        public Type.MethodType asMethodType() {
            return (Type.MethodType)qtype;
        }

        public void complete() {
            for (JCList<Type> l = tvars; l.nonEmpty(); l = l.tail) {
                ((Type.TypeVar)l.head).bound.complete();
            }
            qtype.complete();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<TypeVar> getTypeVariables() {
            return JCList.convert(Type.TypeVar.class, getTypeArguments());
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.EXECUTABLE;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }
    }

    /** A class for inference variables, for use during method/diamond type
     *  inference. An inference variable has upper/lower bounds and a set
     *  of equality constraints. Such bounds are set during subtyping, type-containment,
     *  type-equality checks, when the types being tested contain inference variables.
     *  A change listener can be attached to an inference variable, to receive notifications
     *  whenever the bounds of an inference variable change.
     */
    public static class UndetVar extends DelegatedType {

        enum Kind {
            NORMAL,
            CAPTURED,
            THROWS;
        }

        /** Inference variable change listener. The listener method is called
         *  whenever a change to the inference variable's bounds occurs
         */
        public interface UndetVarListener {
            /** called when some inference variable bounds (of given kinds ibs) change */
            void varBoundChanged(Type.UndetVar uv, InferenceBound ib, Type bound, boolean update);
            /** called when the inferred type is set on some inference variable */
            default void varInstantiated(Type.UndetVar uv) { Assert.error(); }
        }

        /**
         * Inference variable bound kinds
         */
        public enum InferenceBound {
            /** lower bounds */
            LOWER {
                public InferenceBound complement() { return UPPER; }
            },
            /** equality constraints */
            EQ {
                public InferenceBound complement() { return EQ; }
            },
            /** upper bounds */
            UPPER {
                public InferenceBound complement() { return LOWER; }
            };

            public abstract InferenceBound complement();

            public boolean lessThan(InferenceBound that) {
                if (that == this) {
                    return false;
                } else {
                    switch (that) {
                        case UPPER: return true;
                        case LOWER: return false;
                        case EQ: return (this != UPPER);
                        default:
                            Assert.error("Cannot get here!");
                            return false;
                    }
                }
            }
        }

        /** list of incorporation actions (used by the incorporation engine). */
        public ArrayDeque<IncorporationAction> incorporationActions = new ArrayDeque<>();

        /** inference variable bounds */
        protected Map<InferenceBound, JCList<Type>> bounds;

        /** inference variable's inferred type (set from Infer.java) */
        private Type inst = null;

        /** number of declared (upper) bounds */
        public int declaredCount;

        /** inference variable's change listener */
        public UndetVarListener listener = null;

        Kind kind;

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitUndetVar(this, s);
        }

        public UndetVar(Type.TypeVar origin, UndetVarListener listener, Types types) {
            // This is a synthesized internal type, so we cannot annotate it.
            super(UNDETVAR, origin);
            this.kind = origin.isCaptured() ?
                    Kind.CAPTURED :
                    Kind.NORMAL;
            this.listener = listener;
            bounds = new EnumMap<>(InferenceBound.class);
            JCList<Type> declaredBounds = types.getBounds(origin);
            declaredCount = declaredBounds.length();
            bounds.put(InferenceBound.UPPER, JCList.nil());
            bounds.put(InferenceBound.LOWER, JCList.nil());
            bounds.put(InferenceBound.EQ, JCList.nil());
            for (Type t : declaredBounds.reverse()) {
                //add bound works in reverse order
                addBound(InferenceBound.UPPER, t, types, true);
            }
            if (origin.isCaptured() && !origin.lower.hasTag(BOT)) {
                //add lower bound if needed
                addBound(InferenceBound.LOWER, origin.lower, types, true);
            }
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendAnnotationsString(sb);
            if (inst == null) {
                sb.append(qtype);
                sb.append('?');
            } else {
                sb.append(inst);
            }
            return sb.toString();
        }

        public String debugString() {
            String result = "inference var = " + qtype + "\n";
            if (inst != null) {
                result += "inst = " + inst + '\n';
            }
            for (InferenceBound bound: InferenceBound.values()) {
                JCList<Type> aboundList = bounds.get(bound);
                if (aboundList.size() > 0) {
                    result += bound + " = " + aboundList + '\n';
                }
            }
            return result;
        }

        public void setThrow() {
            if (this.kind == Kind.CAPTURED) {
                //invalid state transition
                throw new IllegalStateException();
            }
            this.kind = Kind.THROWS;
        }

        /**
         * Returns a new copy of this undet var.
         */
        public Type.UndetVar dup(Types types) {
            Type.UndetVar uv2 = new Type.UndetVar((Type.TypeVar)qtype, listener, types);
            dupTo(uv2, types);
            return uv2;
        }

        /**
         * Dumps the contents of this undet var on another undet var.
         */
        public void dupTo(Type.UndetVar uv2, Types types) {
            uv2.listener = null;
            uv2.bounds.clear();
            for (InferenceBound ib : InferenceBound.values()) {
                uv2.bounds.put(ib, JCList.nil());
                for (Type t : getBounds(ib)) {
                    uv2.addBound(ib, t, types, true);
                }
            }
            uv2.inst = inst;
            uv2.listener = listener;
            uv2.incorporationActions = new ArrayDeque<>();
            for (IncorporationAction action : incorporationActions) {
                uv2.incorporationActions.add(action.dup(uv2));
            }
        }

        @Override
        public Type.UndetVar cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to an UndetVar type");
        }

        @Override
        public boolean isPartial() {
            return true;
        }

        @Override
        public Type baseType() {
            return (inst == null) ? this : inst.baseType();
        }

        public Type getInst() {
            return inst;
        }

        public void setInst(Type inst) {
            this.inst = inst;
            if (listener != null) {
                listener.varInstantiated(this);
            }
        }

        /** get all bounds of a given kind */
        public JCList<Type> getBounds(InferenceBound... ibs) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (InferenceBound ib : ibs) {
                buf.appendList(bounds.get(ib));
            }
            return buf.toList();
        }

        /** get the list of declared (upper) bounds */
        public JCList<Type> getDeclaredBounds() {
            ListBuffer<Type> buf = new ListBuffer<>();
            int count = 0;
            for (Type b : getBounds(InferenceBound.UPPER)) {
                if (count++ == declaredCount) break;
                buf.append(b);
            }
            return buf.toList();
        }

        /** internal method used to override an undetvar bounds */
        public void setBounds(InferenceBound ib, JCList<Type> newBounds) {
            bounds.put(ib, newBounds);
        }

        /** add a bound of a given kind - this might trigger listener notification */
        public final void addBound(InferenceBound ib, Type bound, Types types) {
            // Per JDK-8075793: in pre-8 sources, follow legacy javac behavior
            // when capture variables are inferred as bounds: for lower bounds,
            // map to the capture variable's upper bound; for upper bounds,
            // if the capture variable has a lower bound, map to that type
            if (types.mapCapturesToBounds) {
                switch (ib) {
                    case LOWER:
                        bound = types.cvarUpperBound(bound);
                        break;
                    case UPPER:
                        Type altBound = types.cvarLowerBound(bound);
                        if (!altBound.hasTag(TypeTag.BOT)) bound = altBound;
                        break;
                }
            }
            addBound(ib, bound, types, false);
        }

        @SuppressWarnings("fallthrough")
        private void addBound(InferenceBound ib, Type bound, Types types, boolean update) {
            if (kind == Kind.CAPTURED && !update) {
                //Captured inference variables bounds must not be updated during incorporation,
                //except when some inference variable (beta) has been instantiated in the
                //right-hand-side of a 'C<alpha> = capture(C<? extends/super beta>) constraint.
                if (bound.hasTag(UNDETVAR) && !((Type.UndetVar)bound).isCaptured()) {
                    //If the new incoming bound is itself a (regular) inference variable,
                    //then we are allowed to propagate this inference variable bounds to it.
                    ((Type.UndetVar)bound).addBound(ib.complement(), this, types, false);
                }
            } else {
                Type bound2 = bound.map(toTypeVarMap).baseType();
                JCList<Type> prevBounds = bounds.get(ib);
                if (bound == qtype) return;
                for (Type b : prevBounds) {
                    //check for redundancy - use strict version of isSameType on tvars
                    //(as the standard version will lead to false positives w.r.t. clones ivars)
                    if (types.isSameType(b, bound2, true)) return;
                }
                bounds.put(ib, prevBounds.prepend(bound2));
                notifyBoundChange(ib, bound2, false);
            }
        }
        //where
            TypeMapping<Void> toTypeVarMap = new Type.StructuralTypeMapping<Void>() {
                @Override
                public Type visitUndetVar(Type.UndetVar uv, Void _unused) {
                    return uv.inst != null ? uv.inst : uv.qtype;
                }
            };

        /** replace types in all bounds - this might trigger listener notification */
        public void substBounds(JCList<Type> from, JCList<Type> to, Types types) {
            final ListBuffer<Pair<InferenceBound, Type>>  boundsChanged = new ListBuffer<>();
            UndetVarListener prevListener = listener;
            try {
                //setup new listener for keeping track of changed bounds
                listener = (uv, ib, t, _ignored) -> {
                    Assert.check(uv == Type.UndetVar.this);
                    boundsChanged.add(new Pair<>(ib, t));
                };
                for (Map.Entry<InferenceBound, JCList<Type>> _entry : bounds.entrySet()) {
                    InferenceBound ib = _entry.getKey();
                    JCList<Type> prevBounds = _entry.getValue();
                    ListBuffer<Type> newBounds = new ListBuffer<>();
                    ListBuffer<Type> deps = new ListBuffer<>();
                    //step 1 - re-add bounds that are not dependent on ivars
                    for (Type t : prevBounds) {
                        if (!t.containsAny(from)) {
                            newBounds.append(t);
                        } else {
                            deps.append(t);
                        }
                    }
                    //step 2 - replace bounds
                    bounds.put(ib, newBounds.toList());
                    //step 3 - for each dependency, add new replaced bound
                    for (Type dep : deps) {
                        addBound(ib, types.subst(dep, from, to), types, true);
                    }
                }
            } finally {
                listener = prevListener;
                for (Pair<InferenceBound, Type> boundUpdate : boundsChanged) {
                    notifyBoundChange(boundUpdate.fst, boundUpdate.snd, true);
                }
            }
        }

        private void notifyBoundChange(InferenceBound ib, Type bound, boolean update) {
            if (listener != null) {
                listener.varBoundChanged(this, ib, bound, update);
            }
        }

        public final boolean isCaptured() {
            return kind == Kind.CAPTURED;
        }

        public final boolean isThrows() {
            return kind == Kind.THROWS;
        }
    }

    /** Represents NONE.
     */
    public static class JCNoType extends Type implements NoType {
        public JCNoType() {
            // Need to use List.nil(), because JCNoType constructor
            // gets called in static initializers in Type, where
            // noAnnotations is also defined.
            super(null, TypeMetadata.EMPTY);
        }

        @Override
        public Type.JCNoType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a JCNoType");
        }

        @Override
        public TypeTag getTag() {
            return NONE;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.NONE;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }

        @Override
        public boolean isCompound() { return false; }
    }

    /** Represents VOID.
     */
    public static class JCVoidType extends Type implements NoType {

        public JCVoidType() {
            // Void cannot be annotated
            super(null, TypeMetadata.EMPTY);
        }

        @Override
        public Type.JCVoidType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a void type");
        }

        @Override
        public TypeTag getTag() {
            return VOID;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.VOID;
        }

        @Override
        public boolean isCompound() { return false; }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }

        @Override
        public boolean isPrimitiveOrVoid() {
            return true;
        }
    }

    static class BottomType extends Type implements NullType {
        public BottomType() {
            // Bottom is a synthesized internal type, so it cannot be annotated
            super(null, TypeMetadata.EMPTY);
        }

        @Override
        public Type.BottomType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to a bottom type");
        }

        @Override
        public TypeTag getTag() {
            return BOT;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.NULL;
        }

        @Override
        public boolean isCompound() { return false; }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNull(this, p);
        }

        @Override
        public Type constType(Object value) {
            return this;
        }

        @Override
        public String stringValue() {
            return "null";
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

    }

    public static class ErrorType extends ClassType
            implements javax.lang.model.type.ErrorType {

        private Type originalType = null;

        public ErrorType(ClassSymbol c, Type originalType) {
            this(originalType, c);
            c.type = this;
            c.kind = ERR;
            c.members_field = new Scope.ErrorScope(c);
        }

        public ErrorType(Type originalType, TypeSymbol tsym) {
            super(noType, JCList.nil(), null);
            this.tsym = tsym;
            this.originalType = (originalType == null ? noType : originalType);
        }

        private ErrorType(Type originalType, TypeSymbol tsym,
                          TypeMetadata metadata) {
            super(noType, JCList.nil(), null, metadata);
            this.tsym = tsym;
            this.originalType = (originalType == null ? noType : originalType);
        }

        @Override
        public ErrorType cloneWithMetadata(TypeMetadata md) {
            return new ErrorType(originalType, tsym, md) {
                @Override
                public Type baseType() { return ErrorType.this.baseType(); }
            };
        }

        @Override
        public TypeTag getTag() {
            return ERROR;
        }

        @Override
        public boolean isPartial() {
            return true;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        public ErrorType(Name name, TypeSymbol container, Type originalType) {
            this(new ClassSymbol(PUBLIC|STATIC|ACYCLIC, name, null, container), originalType);
        }

        @Override
        public <R,S> R accept(Visitor<R,S> v, S s) {
            return v.visitErrorType(this, s);
        }

        public Type constType(Object constValue) { return this; }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getEnclosingType()           { return this; }
        public Type getReturnType()              { return this; }
        public Type asSub(Symbol sym)            { return this; }

        public boolean isGenType(Type t)         { return true; }
        public boolean isErroneous()             { return true; }
        public boolean isCompound()              { return false; }
        public boolean isInterface()             { return false; }

        public JCList<Type> allparams()            { return JCList.nil(); }
        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getTypeArguments()     { return JCList.nil(); }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeKind getKind() {
            return TypeKind.ERROR;
        }

        public Type getOriginalType() {
            return originalType;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitError(this, p);
        }
    }

    public static class UnknownType extends Type {

        public UnknownType() {
            // Unknown is a synthesized internal type, so it cannot be
            // annotated.
            super(null, TypeMetadata.EMPTY);
        }

        @Override
        public Type.UnknownType cloneWithMetadata(TypeMetadata md) {
            throw new AssertionError("Cannot add metadata to an unknown type");
        }

        @Override
        public TypeTag getTag() {
            return UNKNOWN;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitUnknown(this, p);
        }

        @Override
        public boolean isPartial() {
            return true;
        }
    }

    /**
     * A visitor for types.  A visitor is used to implement operations
     * (or relations) on types.  Most common operations on types are
     * binary relations and this interface is designed for binary
     * relations, that is, operations of the form
     * Type&nbsp;&times;&nbsp;S&nbsp;&rarr;&nbsp;R.
     * <!-- In plain text: Type x S -> R -->
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * type itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public interface Visitor<R,S> {
        R visitClassType(ClassType t, S s);
        R visitWildcardType(WildcardType t, S s);
        R visitArrayType(ArrayType t, S s);
        R visitMethodType(MethodType t, S s);
        R visitPackageType(PackageType t, S s);
        R visitModuleType(ModuleType t, S s);
        R visitTypeVar(TypeVar t, S s);
        R visitCapturedType(CapturedType t, S s);
        R visitForAll(ForAll t, S s);
        R visitUndetVar(UndetVar t, S s);
        R visitErrorType(ErrorType t, S s);
        R visitType(Type t, S s);
    }
}
