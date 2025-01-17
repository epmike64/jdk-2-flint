/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.JCList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/**
 * TypeMetadata is essentially an immutable {@code EnumMap<Entry.Kind, <? extends Entry>>}
 *
 * A metadata class represented by a subtype of Entry can express a property on a Type instance.
 * Thers should be at most one instance of an Entry per Entry.Kind on any given Type instance.
 *
 * Metadata classes of a specific kind are responsible for how they combine themselvs.
 *
 * @implNote {@code Entry:combine} need not be commutative.
 */
public class TypeMetadata {
    public static final TypeMetadata EMPTY = new TypeMetadata();

    private final EnumMap<Entry.Kind, Entry> contents;

    /**
     * Create a new empty TypeMetadata map.
     */
    private TypeMetadata() {
        contents = new EnumMap<>(Entry.Kind.class);
    }

    /**
     * Create a new TypeMetadata map containing the Entry {@code elem}.
     *
     * @param elem the sole contents of this map
     */
    public TypeMetadata(Entry elem) {
        this();
        Assert.checkNonNull(elem);
        contents.put(elem.kind(), elem);
    }

    /**
     * Creates a copy of TypeMetadata {@code other} with a shallow copy the other's metadata contents.
     *
     * @param other the TypeMetadata to copy contents from.
     */
    public TypeMetadata(TypeMetadata other) {
        Assert.checkNonNull(other);
        contents = other.contents.clone();
    }

    /**
     * Return a copy of this TypeMetadata with the metadata entry for {@code elem.kind()} combined
     * with {@code elem}.
     *
     * @param elem the new value
     * @return a new TypeMetadata updated with {@code Entry elem}
     */
    public TypeMetadata combine(Entry elem) {
        Assert.checkNonNull(elem);

        TypeMetadata out = new TypeMetadata(this);
        Entry.Kind key = elem.kind();
        if (contents.containsKey(key)) {
            out.add(key, this.contents.get(key).combine(elem));
        } else {
            out.add(key, elem);
        }
        return out;
    }

    /**
     * Return a copy of this TypeMetadata with the metadata entry for all kinds from {@code other}
     * combined with the same kind from this.
     *
     * @param other the TypeMetadata to combine with this
     * @return a new TypeMetadata updated with all entries from {@code other}
     */
    public TypeMetadata combineAll(TypeMetadata other) {
        Assert.checkNonNull(other);

        TypeMetadata out = new TypeMetadata();
        Set<Entry.Kind> keys = new HashSet<>(contents.keySet());
        keys.addAll(other.contents.keySet());

        for(Entry.Kind key : keys) {
            if (contents.containsKey(key)) {
                if (other.contents.containsKey(key)) {
                    out.add(key, contents.get(key).combine(other.contents.get(key)));
                } else {
                    out.add(key, contents.get(key));
                }
            } else if (other.contents.containsKey(key)) {
                out.add(key, other.contents.get(key));
            }
        }
        return out;
    }

    /**
     * Return a TypeMetadata with the metadata entry for {@code kind} removed.
     *
     * This may be the same instance or a new TypeMetadata.
     *
     * @param kind the {@code Kind} to remove metadata for
     * @return a new TypeMetadata without {@code Kind kind}
     */
    public TypeMetadata without(Entry.Kind kind) {
        if (this == EMPTY || contents.get(kind) == null)
            return this;

        TypeMetadata out = new TypeMetadata(this);
        out.contents.remove(kind);
        return out.contents.isEmpty() ? EMPTY : out;
    }

    public Entry get(Entry.Kind kind) {
        return contents.get(kind);
    }

    private void add(Entry.Kind kind, Entry elem) {
        contents.put(kind, elem);
    }

    public interface Entry {

        public enum Kind {
            ANNOTATIONS
        }

        /**
         * Get the kind of metadata this object represents
         */
        public Kind kind();

        /**
         * Combine this type metadata with another metadata of the
         * same kind.
         *
         * @param other The metadata with which to combine this one.
         * @return The combined metadata.
         */
        public Entry combine(Entry other);
    }

    /**
     * A type metadata object holding type annotations.
     */
    public static class Annotations implements Entry {
        private JCList<Attribute.TypeCompound> annos;

        public static final JCList<Attribute.TypeCompound> TO_BE_SET = JCList.nil();

        public Annotations(JCList<Attribute.TypeCompound> annos) {
            this.annos = annos;
        }

        /**
         * Get the type annotations contained in this metadata.
         *
         * @return The annotations.
         */
        public JCList<Attribute.TypeCompound> getAnnotations() {
            return annos;
        }

        @Override
        public Annotations combine(Entry other) {
            Assert.check(annos == TO_BE_SET);
            annos = ((Annotations)other).annos;
            return this;
        }

        @Override
        public TypeMetadata.Entry.Kind kind() { return TypeMetadata.Entry.Kind.ANNOTATIONS; }

        @Override
        public String toString() { return "ANNOTATIONS [ " + annos + " ]"; }
    }
}
