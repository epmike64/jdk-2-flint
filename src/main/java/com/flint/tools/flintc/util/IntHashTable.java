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

package com.flint.tools.flintc.util;

/**
 * A hash table that maps Object to int.
 *
 * This is a custom hash table optimised for the Object {@literal ->} int
 * maps. This is done to avoid unnecessary object allocation in the image set.
 *
 * @author Charles Turner
 * @author Per Bothner
 */
public class IntHashTable {
    private static final int DEFAULT_INITIAL_SIZE = 64;
    protected Object[] objs; // the domain set
    protected int[] ints; // the image set
    protected int mask; // used to clip int's into the domain
    protected int num_bindings; // the number of mappings (including DELETED)
    private final static Object DELETED = new Object();

    /**
     * Construct an Object {@literal ->} int hash table.
     *
     * The default size of the hash table is 64 mappings.
     */
    public IntHashTable() {
        objs = new Object[DEFAULT_INITIAL_SIZE];
        ints = new int[DEFAULT_INITIAL_SIZE];
        mask = DEFAULT_INITIAL_SIZE - 1;
    }

    /**
     * Construct an Object {@literal ->} int hash table with a specified amount of mappings.
     * @param capacity The number of default mappings in this hash table.
     */
    public IntHashTable(int capacity) {
        int log2Size = 4;
        while (capacity > (1 << log2Size)) {
            log2Size++;
        }
        capacity = 1 << log2Size;
        objs = new Object[capacity];
        ints = new int[capacity];
        mask = capacity - 1;
    }

    /**
     * Compute the hash code of a given object.
     *
     * @param key The object whose hash code is to be computed.
     * @return zero if the object is null, otherwise the identityHashCode
     */
    public int hash(Object key) {
        return System.identityHashCode(key);
    }

    /**
     * Find either the index of a key's value, or the index of an available space.
     *
     * @param key The key to whose value you want to find.
     * @param hash The hash code of this key.
     * @return Either the index of the key's value, or an index pointing to
     * unoccupied space.
     */
    public int lookup(Object key, int hash) {
        Object node;
        int hash1 = hash ^ (hash >>> 15);
        int hash2 = (hash ^ (hash << 6)) | 1; //ensure coprimeness
        int deleted = -1;
        for (int i = hash1 & mask;; i = (i + hash2) & mask) {
            node = objs[i];
            if (node == key)
                return i;
            if (node == null)
                return deleted >= 0 ? deleted : i;
            if (node == DELETED && deleted < 0)
                deleted = i;
        }
    }

    /**
     * Lookup a given key's value in the hash table.
     *
     * @param key The key whose value you want to find.
     * @return Either the index of the key's value, or an index pointing to
     * unoccupied space.
     */
    public int lookup(Object key) {
        return lookup(key, hash(key));
    }

    /**
     * Return the value stored at the specified index in the table.
     *
     * @param index The index to inspect, as returned from {@link #lookup}
     * @return A non-negative integer if the index contains a non-null
     *         value, or -1 if it does.
     */
    public int getFromIndex(int index) {
        Object node = objs[index];
        return node == null || node == DELETED ? -1 : ints[index];
    }

    /**
     * Associates the specified key with the specified value in this map.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @param index the index at which to place this binding, as returned
     *              from {@link #lookup}.
     * @return previous value associated with specified key, or -1 if there was
     * no mapping for key.
     */
    public int putAtIndex(Object key, int value, int index) {
        Object old = objs[index];
        if (old == null || old == DELETED) {
            objs[index] = key;
            ints[index] = value;
            if (old != DELETED)
                num_bindings++;
            if (3 * num_bindings >= 2 * objs.length)
                rehash();
            return -1;
        } else { // update existing mapping
            int oldValue = ints[index];
            ints[index] = value;
            return oldValue;
        }
    }

    public int remove(Object key) {
        int index = lookup(key);
        Object old = objs[index];
        if (old == null || old == DELETED)
            return -1;
        objs[index] = DELETED;
        return ints[index];
    }

    /**
     * Expand the hash table when it exceeds the load factor.
     *
     * Rehash the existing objects.
     */
    protected void rehash() {
        Object[] oldObjsTable = objs;
        int[] oldIntsTable = ints;
        int oldCapacity = oldObjsTable.length;
        int newCapacity = oldCapacity << 1;
        Object[] newObjTable = new Object[newCapacity];
        int[] newIntTable = new int[newCapacity];
        int newMask = newCapacity - 1;
        objs = newObjTable;
        ints = newIntTable;
        mask = newMask;
        num_bindings = 0; // this is recomputed below
        Object key;
        for (int i = oldIntsTable.length; --i >= 0;) {
            key = oldObjsTable[i];
            if (key != null && key != DELETED)
                putAtIndex(key, oldIntsTable[i], lookup(key, hash(key)));
        }
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        for (int i = objs.length; --i >= 0;) {
            objs[i] = null;
        }
        num_bindings = 0;
    }
}
