/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.source.tree.Tree.Kind;

import javax.lang.model.type.TypeKind;

import static com.flint.tools.flintc.code.TypeTag.NumericClasses.*;

/** An interface for type tag values, which distinguish between different
 *  sorts of types.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum TypeTag {
    /** The tag of the basic type `byte'.
     */
    BYTE(BYTE_CLASS, BYTE_SUPERCLASSES, true),

    /** The tag of the basic type `char'.
     */
    CHAR(CHAR_CLASS, CHAR_SUPERCLASSES, true),

    /** The tag of the basic type `short'.
     */
    SHORT(SHORT_CLASS, SHORT_SUPERCLASSES, true),

    /** The tag of the basic type `long'.
     */
    LONG(LONG_CLASS, LONG_SUPERCLASSES, true),

    /** The tag of the basic type `float'.
     */
    FLOAT(FLOAT_CLASS, FLOAT_SUPERCLASSES, true),
    /** The tag of the basic type `int'.
     */
    INT(INT_CLASS, INT_SUPERCLASSES, true),
    /** The tag of the basic type `double'.
     */
    DOUBLE(DOUBLE_CLASS, DOUBLE_CLASS, true),
    /** The tag of the basic type `boolean'.
     */
    BOOLEAN(0, 0, true),

    /** The tag of the type `void'.
     */
    VOID,

    /** The tag of all class and interface types.
     */
    CLASS,

    /** The tag of all array types.
     */
    ARRAY,

    /** The tag of all (monomorphic) method types.
     */
    METHOD,

    /** The tag of all package "types".
     */
    PACKAGE,

    /** The tag of all module "types".
     */
    MODULE,

    /** The tag of all (source-level) type variables.
     */
    TYPEVAR,

    /** The tag of all type arguments.
     */
    WILDCARD,

    /** The tag of all polymorphic (method-) types.
     */
    FORALL,

    /** The tag of deferred expression types in method context
     */
    DEFERRED,

    /** The tag of the bottom type {@code <null>}.
     */
    BOT,

    /** The tag of a missing type.
     */
    NONE,

    /** The tag of the error type.
     */
    ERROR,

    /** The tag of an unknown type
     */
    UNKNOWN,

    /** The tag of all instantiatable type variables.
     */
    UNDETVAR,

    /** Pseudo-types, these are special tags
     */
    UNINITIALIZED_THIS,

    UNINITIALIZED_OBJECT;

    final int superClasses;
    final int numericClass;
    final boolean isPrimitive;

    private TypeTag() {
        this(0, 0, false);
    }

    private TypeTag(int numericClass, int superClasses, boolean isPrimitive) {
        this.superClasses = superClasses;
        this.numericClass = numericClass;
        this.isPrimitive = isPrimitive;
    }

    public static class NumericClasses {
        public static final int BYTE_CLASS = 1;
        public static final int CHAR_CLASS = 2;
        public static final int SHORT_CLASS = 4;
        public static final int INT_CLASS = 8;
        public static final int LONG_CLASS = 16;
        public static final int FLOAT_CLASS = 32;
        public static final int DOUBLE_CLASS = 64;

        static final int BYTE_SUPERCLASSES = BYTE_CLASS | SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int CHAR_SUPERCLASSES = CHAR_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int SHORT_SUPERCLASSES = SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int INT_SUPERCLASSES = INT_CLASS | LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int LONG_SUPERCLASSES = LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int FLOAT_SUPERCLASSES = FLOAT_CLASS | DOUBLE_CLASS;
    }

    public boolean isStrictSubRangeOf(TypeTag tag) {
        /*  Please don't change the implementation of this method to call method
         *  isSubRangeOf. Both methods are called from hotspot code, the current
         *  implementation is better performance-wise than the commented modification.
         */
        return (this.superClasses & tag.numericClass) != 0 && this != tag;
    }

    public boolean isSubRangeOf(TypeTag tag) {
        return (this.superClasses & tag.numericClass) != 0;
    }

    /** Returns the number of type tags.
     */
    public static int getTypeTagCount() {
        // last two tags are not included in the total as long as they are pseudo-types
        return (UNDETVAR.ordinal() + 1);
    }

    public Kind getKindLiteral() {
        switch (this) {
        case INT:
            return Kind.INT_LITERAL;
        case LONG:
            return Kind.LONG_LITERAL;
        case FLOAT:
            return Kind.FLOAT_LITERAL;
        case DOUBLE:
            return Kind.DOUBLE_LITERAL;
        case BOOLEAN:
            return Kind.BOOLEAN_LITERAL;
        case CHAR:
            return Kind.CHAR_LITERAL;
        case CLASS:
            return Kind.STRING_LITERAL;
        case BOT:
            return Kind.NULL_LITERAL;
        default:
            throw new AssertionError("unknown literal kind " + this);
        }
    }

    public TypeKind getPrimitiveTypeKind() {
        switch (this) {
        case BOOLEAN:
            return TypeKind.BOOLEAN;
        case BYTE:
            return TypeKind.BYTE;
        case SHORT:
            return TypeKind.SHORT;
        case INT:
            return TypeKind.INT;
        case LONG:
            return TypeKind.LONG;
        case CHAR:
            return TypeKind.CHAR;
        case FLOAT:
            return TypeKind.FLOAT;
        case DOUBLE:
            return TypeKind.DOUBLE;
        case VOID:
            return TypeKind.VOID;
        default:
            throw new AssertionError("unknown primitive type " + this);
        }
    }

    /** Returns true if the given value is within the allowed range for this type. */
    public boolean checkRange(int value) {
        switch (this) {
            case BOOLEAN:
                return 0 <= value && value <= 1;
            case BYTE:
                return Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE;
            case CHAR:
                return Character.MIN_VALUE <= value && value <= Character.MAX_VALUE;
            case SHORT:
                return Short.MIN_VALUE <= value && value <= Short.MAX_VALUE;
            case INT:
                return true;
            default:
                throw new AssertionError();
        }
    }
}
