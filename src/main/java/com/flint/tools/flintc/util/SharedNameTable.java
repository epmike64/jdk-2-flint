/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.util.DefinedBy.Api;

import java.lang.ref.SoftReference;

/**
 * Implementation of Name.Table that stores all names in a single shared
 * byte array, expanding it as needed. This avoids the overhead incurred
 * by using an array of bytes for each name.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SharedNameTable extends com.flint.tools.flintc.util.Name.Table {
    // maintain a freelist of recently used name tables for reuse.
    private static JCList<SoftReference<SharedNameTable>> freelist = JCList.nil();

    static public synchronized SharedNameTable create(Names names) {
        while (freelist.nonEmpty()) {
            SharedNameTable t = freelist.head.get();
            freelist = freelist.tail;
            if (t != null) {
                return t;
            }
        }
        return new SharedNameTable(names);
    }

    static private synchronized void dispose(SharedNameTable t) {
        freelist = freelist.prepend(new SoftReference<>(t));
    }

    /** The hash table for names.
     */
    private NameImpl[] hashes;

    /** The shared byte array holding all encountered names.
     */
    public byte[] bytes;

    /** The mask to be used for hashing
     */
    private int hashMask;

    /** The number of filled bytes in `names'.
     */
    private int nc = 0;

    /** Allocator
     *  @param names The main name table
     *  @param hashSize the (constant) size to be used for the hash table
     *                  needs to be a power of two.
     *  @param nameSize the initial size of the name table.
     */
    public SharedNameTable(Names names, int hashSize, int nameSize) {
        super(names);
        hashMask = hashSize - 1;
        hashes = new NameImpl[hashSize];
        bytes = new byte[nameSize];

    }

    public SharedNameTable(Names names) {
        this(names, 0x8000, 0x20000);
    }

    @Override
    public com.flint.tools.flintc.util.Name fromChars(char[] cs, int start, int len) {
        int nc = this.nc;
        byte[] bytes = this.bytes = ArrayUtils.ensureCapacity(this.bytes, nc + len * 3);
        int nbytes = Convert.chars2utf(cs, start, bytes, nc, len) - nc;
        int h = hashValue(bytes, nc, nbytes) & hashMask;
        NameImpl n = hashes[h];
        while (n != null &&
                (n.getByteLength() != nbytes ||
                !equals(bytes, n.index, bytes, nc, nbytes))) {
            n = n.next;
        }
        if (n == null) {
            n = new NameImpl(this);
            n.index = nc;
            n.length = nbytes;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + nbytes;
            if (nbytes == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public com.flint.tools.flintc.util.Name fromUtf(byte[] cs, int start, int len) {
        int h = hashValue(cs, start, len) & hashMask;
        NameImpl n = hashes[h];
        byte[] names = this.bytes;
        while (n != null &&
                (n.getByteLength() != len || !equals(names, n.index, cs, start, len))) {
            n = n.next;
        }
        if (n == null) {
            int nc = this.nc;
            names = this.bytes = ArrayUtils.ensureCapacity(names, nc + len);
            System.arraycopy(cs, start, names, nc, len);
            n = new NameImpl(this);
            n.index = nc;
            n.length = len;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + len;
            if (len == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public void dispose() {
        dispose(this);
    }

    static class NameImpl extends com.flint.tools.flintc.util.Name {
        /** The next name occupying the same hash bucket.
         */
        NameImpl next;

        /** The index where the bytes of this name are stored in the global name
         *  buffer `byte'.
         */
        int index;

        /** The number of bytes in this name.
         */
        int length;

        NameImpl(SharedNameTable table) {
            super(table);
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public int getByteLength() {
            return length;
        }

        @Override
        public byte getByteAt(int i) {
            return getByteArray()[index + i];
        }

        @Override
        public byte[] getByteArray() {
            return ((SharedNameTable) table).bytes;
        }

        @Override
        public int getByteOffset() {
            return index;
        }

        /** Return the hash value of this name.
         */
        @DefinedBy(Api.LANGUAGE_MODEL)
        public int hashCode() {
            return index;
        }

        /** Is this name equal to other?
         */
        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean equals(Object other) {
            if (other instanceof com.flint.tools.flintc.util.Name)
                return
                    table == ((com.flint.tools.flintc.util.Name)other).table && index == ((Name) other).getIndex();
            else return false;
        }

    }

}
