/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;

/** A class for generic linked lists. Links are supposed to be
 *  immutable, the only exception being the incremental construction of
 *  lists via ListBuffers.  List is the main container class in
 *  GJC. Most data structures and algorithms in GJC use lists rather
 *  than arrays.
 *
 *  <p>Lists are always trailed by a sentinel element, whose head and tail
 *  are both null.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JCList<A> extends AbstractCollection<A> implements java.util.List<A> {

    /** The first element of the list, supposed to be immutable.
     */
    public A head;

    /** The remainder of the list except for its first element, supposed
     *  to be immutable.
     */
    //@Deprecated
    public JCList<A> tail;

    /** Construct a list given its head and tail.
     */
    JCList(A head, JCList<A> tail) {
        this.tail = tail;
        this.head = head;
    }

    /** Construct an empty list.
     */
    @SuppressWarnings("unchecked")
    public static <A> JCList<A> nil() {
        return (JCList<A>)EMPTY_LIST;
    }

    private static final JCList<?> EMPTY_LIST = new JCList<Object>(null,null) {
        public JCList<Object> setTail(JCList<Object> tail) {
            throw new UnsupportedOperationException();
        }
        public boolean isEmpty() {
            return true;
        }
    };

    /** Returns the list obtained from 'l' after removing all elements 'elem'
     */
    public static <A> JCList<A> filter(JCList<A> l, A elem) {
        Assert.checkNonNull(elem);
        JCList<A> res = JCList.nil();
        for (A a : l) {
            if (a != null && !a.equals(elem)) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    public JCList<A> intersect(JCList<A> that) {
        com.flint.tools.flintc.util.ListBuffer<A> buf = new com.flint.tools.flintc.util.ListBuffer<>();
        for (A el : this) {
            if (that.contains(el)) {
                buf.append(el);
            }
        }
        return buf.toList();
    }

    public JCList<A> diff(JCList<A> that) {
        com.flint.tools.flintc.util.ListBuffer<A> buf = new com.flint.tools.flintc.util.ListBuffer<>();
        for (A el : this) {
            if (!that.contains(el)) {
                buf.append(el);
            }
        }
        return buf.toList();
    }

    /**
     * Create a new list from the first {@code n} elements of this list
     */
    public JCList<A> take(int n) {
        com.flint.tools.flintc.util.ListBuffer<A> buf = new com.flint.tools.flintc.util.ListBuffer<>();
        int count = 0;
        for (A el : this) {
            if (count++ == n) break;
            buf.append(el);
        }
        return buf.toList();
    }

    /** Construct a list consisting of given element.
     */
    public static <A> JCList<A> of(A x1) {
        return new JCList<>(x1, JCList.nil());
    }

    /** Construct a list consisting of given elements.
     */
    public static <A> JCList<A> of(A x1, A x2) {
        return new JCList<>(x1, of(x2));
    }

    /** Construct a list consisting of given elements.
     */
    public static <A> JCList<A> of(A x1, A x2, A x3) {
        return new JCList<>(x1, of(x2, x3));
    }

    /** Construct a list consisting of given elements.
     */
    @SuppressWarnings({"varargs", "unchecked"})
    public static <A> JCList<A> of(A x1, A x2, A x3, A... rest) {
        return new JCList<>(x1, new JCList<>(x2, new JCList<>(x3, from(rest))));
    }

    /**
     * Construct a list consisting all elements of given array.
     * @param array an array; if {@code null} return an empty list
     */
    public static <A> JCList<A> from(A[] array) {
        JCList<A> xs = nil();
        if (array != null)
            for (int i = array.length - 1; i >= 0; i--)
                xs = new JCList<>(array[i], xs);
        return xs;
    }

    public static <A> JCList<A> from(Iterable<? extends A> coll) {
        com.flint.tools.flintc.util.ListBuffer<A> xs = new com.flint.tools.flintc.util.ListBuffer<>();
        for (A a : coll) {
            xs.append(a);
        }
        return xs.toList();
    }

    /** Construct a list consisting of a given number of identical elements.
     *  @param len    The number of elements in the list.
     *  @param init   The value of each element.
     */
    @Deprecated
    public static <A> JCList<A> fill(int len, A init) {
        JCList<A> l = nil();
        for (int i = 0; i < len; i++) l = new JCList<>(init, l);
        return l;
    }

    /** Does list have no elements?
     */
    @Override
    public boolean isEmpty() {
        return tail == null;
    }

    /** Does list have elements?
     */
    //@Deprecated
    public boolean nonEmpty() {
        return tail != null;
    }

    /** Return the number of elements in this list.
     */
    //@Deprecated
    public int length() {
        JCList<A> l = this;
        int len = 0;
        while (l.tail != null) {
            l = l.tail;
            len++;
        }
        return len;
    }
    @Override
    public int size() {
        return length();
    }

    public JCList<A> setTail(JCList<A> tail) {
        this.tail = tail;
        return tail;
    }

    /** Prepend given element to front of list, forming and returning
     *  a new list.
     */
    public JCList<A> prepend(A x) {
        return new JCList<>(x, this);
    }

    /** Prepend given list of elements to front of list, forming and returning
     *  a new list.
     */
    public JCList<A> prependList(JCList<A> xs) {
        if (this.isEmpty()) return xs;
        if (xs.isEmpty()) return this;
        if (xs.tail.isEmpty()) return prepend(xs.head);
        // return this.prependList(xs.tail).prepend(xs.head);
        JCList<A> result = this;
        JCList<A> rev = xs.reverse();
        Assert.check(rev != xs);
        // since xs.reverse() returned a new list, we can reuse the
        // individual List objects, instead of allocating new ones.
        while (rev.nonEmpty()) {
            JCList<A> h = rev;
            rev = rev.tail;
            h.setTail(result);
            result = h;
        }
        return result;
    }

    /** Reverse list.
     * If the list is empty or a singleton, then the same list is returned.
     * Otherwise a new list is formed.
     */
    public JCList<A> reverse() {
        // if it is empty or a singleton, return itself
        if (isEmpty() || tail.isEmpty())
            return this;

        JCList<A> rev = nil();
        for (JCList<A> l = this; l.nonEmpty(); l = l.tail)
            rev = new JCList<>(l.head, rev);
        return rev;
    }

    /** Append given element at length, forming and returning
     *  a new list.
     */
    public JCList<A> append(A x) {
        return of(x).prependList(this);
    }

    /** Append given list at length, forming and returning
     *  a new list.
     */
    public JCList<A> appendList(JCList<A> x) {
        return x.prependList(this);
    }

    /**
     * Append given list buffer at length, forming and returning a new
     * list.
     */
    public JCList<A> appendList(com.flint.tools.flintc.util.ListBuffer<A> x) {
        return appendList(x.toList());
    }

    /** Copy successive elements of this list into given vector until
     *  list is exhausted or end of vector is reached.
     */
    @Override @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] vec) {
        int i = 0;
        JCList<A> l = this;
        Object[] dest = vec;
        while (l.nonEmpty() && i < vec.length) {
            dest[i] = l.head;
            l = l.tail;
            i++;
        }
        if (l.isEmpty()) {
            if (i < vec.length)
                vec[i] = null;
            return vec;
        }

        vec = (T[])Array.newInstance(vec.getClass().getComponentType(), size());
        return toArray(vec);
    }

    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    /** Form a string listing all elements with given separator character.
     */
    public String toString(String sep) {
        if (isEmpty()) {
            return "";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(head);
            for (JCList<A> l = tail; l.nonEmpty(); l = l.tail) {
                buf.append(sep);
                buf.append(l.head);
            }
            return buf.toString();
        }
    }

    /** Form a string listing all elements with comma as the separator character.
     */
    @Override
    public String toString() {
        return toString(",");
    }

    /** Compute a hash code, overrides Object
     *  @see java.util.List#hashCode
     */
    @Override
    public int hashCode() {
        JCList<A> l = this;
        int h = 1;
        while (l.tail != null) {
            h = h * 31 + (l.head == null ? 0 : l.head.hashCode());
            l = l.tail;
        }
        return h;
    }

    /** Is this list the same as other list?
     *  @see java.util.List#equals
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof JCList<?>)
            return equals(this, (JCList<?>)other);
        if (other instanceof java.util.List<?>) {
            JCList<A> t = this;
            Iterator<?> oIter = ((java.util.List<?>) other).iterator();
            while (t.tail != null && oIter.hasNext()) {
                Object o = oIter.next();
                if ( !(t.head == null ? o == null : t.head.equals(o)))
                    return false;
                t = t.tail;
            }
            return (t.isEmpty() && !oIter.hasNext());
        }
        return false;
    }

    /** Are the two lists the same?
     */
    public static boolean equals(JCList<?> xs, JCList<?> ys) {
        while (xs.tail != null && ys.tail != null) {
            if (xs.head == null) {
                if (ys.head != null) return false;
            } else {
                if (!xs.head.equals(ys.head)) return false;
            }
            xs = xs.tail;
            ys = ys.tail;
        }
        return xs.tail == null && ys.tail == null;
    }

    /** Does the list contain the specified element?
     */
    @Override
    public boolean contains(Object x) {
        JCList<A> l = this;
        while (l.tail != null) {
            if (x == null) {
                if (l.head == null) return true;
            } else {
                if (l.head.equals(x)) return true;
            }
            l = l.tail;
        }
        return false;
    }

    /** The last element in the list, if any, or null.
     */
    public A last() {
        A last = null;
        JCList<A> t = this;
        while (t.tail != null) {
            last = t.head;
            t = t.tail;
        }
        return last;
    }

    @SuppressWarnings("unchecked")
    public <Z> JCList<Z> map(Function<A, Z> mapper) {
        boolean changed = false;
        com.flint.tools.flintc.util.ListBuffer<Z> buf = new com.flint.tools.flintc.util.ListBuffer<>();
        for (A a : this) {
            Z z = mapper.apply(a);
            buf.append(z);
            changed |= (z != a);
        }
        return changed ? buf.toList() : (JCList<Z>)this;
    }

    @SuppressWarnings("unchecked")
    public static <T> JCList<T> convert(Class<T> klass, JCList<?> list) {
        if (list == null)
            return null;
        for (Object o : list)
            klass.cast(o);
        return (JCList<T>)list;
    }

    private static final Iterator<?> EMPTYITERATOR = new Iterator<Object>() {
            public boolean hasNext() {
                return false;
            }
            public Object next() {
                throw new NoSuchElementException();
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };

    @SuppressWarnings("unchecked")
    private static <A> Iterator<A> emptyIterator() {
        return (Iterator<A>)EMPTYITERATOR;
    }

    @Override
    public Iterator<A> iterator() {
        if (tail == null)
            return emptyIterator();
        return new Iterator<A>() {
            JCList<A> elems = JCList.this;
            public boolean hasNext() {
                return elems.tail != null;
            }
            public A next() {
                if (elems.tail == null)
                    throw new NoSuchElementException();
                A result = elems.head;
                elems = elems.tail;
                return result;
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public A get(int index) {
        if (index < 0)
            throw new IndexOutOfBoundsException(String.valueOf(index));

        JCList<A> l = this;
        for (int i = index; i-- > 0 && !l.isEmpty(); l = l.tail)
            ;

        if (l.isEmpty())
            throw new IndexOutOfBoundsException("Index: " + index + ", " +
                                                "Size: " + size());
        return l.head;
    }

    public boolean addAll(int index, Collection<? extends A> c) {
        if (c.isEmpty())
            return false;
        throw new UnsupportedOperationException();
    }

    public A set(int index, A element) {
        throw new UnsupportedOperationException();
    }

    public void add(int index, A element) {
        throw new UnsupportedOperationException();
    }

    public A remove(int index) {
        throw new UnsupportedOperationException();
    }

    public int indexOf(Object o) {
        int i = 0;
        for (JCList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (l.head == null ? o == null : l.head.equals(o))
                return i;
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        int last = -1;
        int i = 0;
        for (JCList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (l.head == null ? o == null : l.head.equals(o))
                last = i;
        }
        return last;
    }

    public ListIterator<A> listIterator() {
        return Collections.unmodifiableList(new ArrayList<A>(this)).listIterator();
    }

    public ListIterator<A> listIterator(int index) {
        return Collections.unmodifiableList(new ArrayList<A>(this)).listIterator(index);
    }

    public java.util.List<A> subList(int fromIndex, int toIndex) {
        if  (fromIndex < 0 || toIndex > size() || fromIndex > toIndex)
            throw new IllegalArgumentException();

        ArrayList<A> a = new ArrayList<>(toIndex - fromIndex);
        int i = 0;
        for (JCList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (i == toIndex)
                break;
            if (i >= fromIndex)
                a.add(l.head);
        }

        return Collections.unmodifiableList(a);
    }

    /**
     * Collect elements into a new list (using a @code{ListBuffer})
     */
    public static <Z> Collector<Z, com.flint.tools.flintc.util.ListBuffer<Z>, JCList<Z>> collector() {
        return Collector.of(com.flint.tools.flintc.util.ListBuffer::new,
                com.flint.tools.flintc.util.ListBuffer::add,
                (buf1, buf2)-> { buf1.addAll(buf2); return buf1; },
                ListBuffer::toList);
    }
}
