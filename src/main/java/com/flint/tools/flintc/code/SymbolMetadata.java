/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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


import com.flint.tools.flintc.code.Attribute.TypeCompound;
import com.flint.tools.flintc.code.Kinds.Kind;
import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.ListBuffer;

/**
 * Container for all annotations (attributes in javac) on a Symbol.
 *
 * This class is explicitly mutable. Its contents will change when attributes
 * are annotated onto the Symbol. However this class depends on the facts that
 * List (in javac) is immutable.
 *
 * An instance of this class can be in one of three states:
 *
 * NOT_STARTED indicates that the Symbol this instance belongs to has not been
 * annotated (yet). Specifically if the declaration is not annotated this
 * instance will never move past NOT_STARTED. You can never go back to
 * NOT_STARTED.
 *
 * IN_PROGRESS annotations have been found on the declaration. Will be processed
 * later. You can reset to IN_PROGRESS. While IN_PROGRESS you can set the list
 * of attributes (and this moves out of the IN_PROGRESS state).
 *
 * "unnamed" this SymbolMetadata contains some attributes, possibly the final set.
 * While in this state you can only prepend or append to the attributes not set
 * it directly. You can also move back to the IN_PROGRESS state using reset().
 *
 * <p><b>This is NOT part of any supported API. If you write code that depends
 * on this, you do so at your own risk. This code and its internal interfaces
 * are subject to change or deletion without notice.</b>
 */
public class SymbolMetadata {

    private static final JCList<Attribute.Compound> DECL_NOT_STARTED = JCList.of(null);
    private static final JCList<Attribute.Compound> DECL_IN_PROGRESS = JCList.of(null);

    /*
     * This field should never be null
     */
    private JCList<Attribute.Compound> attributes = DECL_NOT_STARTED;

    /*
     * Type attributes for this symbol.
     * This field should never be null.
     */
    private JCList<TypeCompound> type_attributes = JCList.nil();

    /*
     * Type attributes of initializers in this class.
     * Unused if the current symbol is not a ClassSymbol.
     */
    private JCList<TypeCompound> init_type_attributes = JCList.nil();

    /*
     * Type attributes of class initializers in this class.
     * Unused if the current symbol is not a ClassSymbol.
     */
    private JCList<TypeCompound> clinit_type_attributes = JCList.nil();

    /*
     * The Symbol this SymbolMetadata instance belongs to
     */
    private final Symbol sym;

    public SymbolMetadata(Symbol sym) {
        this.sym = sym;
    }

    public JCList<Attribute.Compound> getDeclarationAttributes() {
        return filterDeclSentinels(attributes);
    }

    public JCList<TypeCompound> getTypeAttributes() {
        return type_attributes;
    }

    public JCList<TypeCompound> getInitTypeAttributes() {
        return init_type_attributes;
    }

    public JCList<TypeCompound> getClassInitTypeAttributes() {
        return clinit_type_attributes;
    }

    public void setDeclarationAttributes(JCList<Attribute.Compound> a) {
        Assert.check(pendingCompletion() || !isStarted());
        if (a == null) {
            throw new NullPointerException();
        }
        attributes = a;
    }

    public void setTypeAttributes(JCList<TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        type_attributes = a;
    }

    public void setInitTypeAttributes(JCList<TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        init_type_attributes = a;
    }

    public void setClassInitTypeAttributes(JCList<TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        clinit_type_attributes = a;
    }

    public void setAttributes(SymbolMetadata other) {
        if (other == null) {
            throw new NullPointerException();
        }
        setDeclarationAttributes(other.getDeclarationAttributes());
        if ((sym.flags() & Flags.BRIDGE) != 0) {
            Assert.check(other.sym.kind == Kind.MTH);
            ListBuffer<TypeCompound> typeAttributes = new ListBuffer<>();
            for (TypeCompound tc : other.getTypeAttributes()) {
                // Carry over only contractual type annotations: i.e nothing interior to method body.
                if (!tc.position.type.isLocal())
                    typeAttributes.append(tc);
            }
            setTypeAttributes(typeAttributes.toList());
        } else {
            setTypeAttributes(other.getTypeAttributes());
        }
        if (sym.kind == Kind.TYP) {
            setInitTypeAttributes(other.getInitTypeAttributes());
            setClassInitTypeAttributes(other.getClassInitTypeAttributes());
        }
    }

    public SymbolMetadata reset() {
        attributes = DECL_IN_PROGRESS;
        return this;
    }

    public boolean isEmpty() {
        return !isStarted()
                || pendingCompletion()
                || attributes.isEmpty();
    }

    public boolean isTypesEmpty() {
        return type_attributes.isEmpty();
    }

    public boolean pendingCompletion() {
        return attributes == DECL_IN_PROGRESS;
    }

    public SymbolMetadata append(JCList<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);

        if (l.isEmpty()) {
            // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata appendUniqueTypes(JCList<TypeCompound> l) {
        if (l.isEmpty()) {
            // no-op
        } else if (type_attributes.isEmpty()) {
            type_attributes = l;
        } else {
            // TODO: in case we expect a large number of annotations, this
            // might be inefficient.
            for (Attribute.TypeCompound tc : l) {
                if (!type_attributes.contains(tc))
                    type_attributes = type_attributes.append(tc);
            }
        }
        return this;
    }

    public SymbolMetadata appendInitTypeAttributes(JCList<TypeCompound> l) {
        if (l.isEmpty()) {
            // no-op
        } else if (init_type_attributes.isEmpty()) {
            init_type_attributes = l;
        } else {
            init_type_attributes = init_type_attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata appendClassInitTypeAttributes(JCList<TypeCompound> l) {
        if (l.isEmpty()) {
            // no-op
        } else if (clinit_type_attributes.isEmpty()) {
            clinit_type_attributes = l;
        } else {
            clinit_type_attributes = clinit_type_attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata prepend(JCList<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);

        if (l.isEmpty()) {
            // no-op
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.prependList(l);
        }
        return this;
    }

    private JCList<Attribute.Compound> filterDeclSentinels(JCList<Attribute.Compound> a) {
        return (a == DECL_IN_PROGRESS || a == DECL_NOT_STARTED)
                ? JCList.nil()
                : a;
    }

    private boolean isStarted() {
        return attributes != DECL_NOT_STARTED;
    }
}
