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

import com.flint.tools.flintc.code.Kinds.Kind;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BiConsumer;

import com.flint.tools.flintc.code.Symbol.CompletionFailure;
import com.flint.tools.flintc.code.Symbol.TypeSymbol;
import com.flint.tools.flintc.tree.JCTree.JCImport;
import com.flint.tools.flintc.util.*;

import static com.flint.tools.flintc.code.Scope.LookupKind.NON_RECURSIVE;
import static com.flint.tools.flintc.code.Scope.LookupKind.RECURSIVE;
import static com.flint.tools.flintc.util.Iterators.createCompoundIterator;
import static com.flint.tools.flintc.util.Iterators.createFilterIterator;

/** A scope represents an area of visibility in a Java program. The
 *  Scope class is a container for symbols which provides
 *  efficient access to symbols given their names. Scopes are implemented
 *  as hash tables with "open addressing" and "double hashing".
 *  Scopes can be nested. Nested scopes can share their hash tables.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Scope {

    /** The scope's owner.
     */
    public final Symbol owner;

    protected Scope(Symbol owner) {
        this.owner = owner;
    }

    /**Returns all Symbols in this Scope. Symbols from outward Scopes are included.
     */
    public final Iterable<Symbol> getSymbols() {
        return getSymbols(noFilter);
    }

    /**Returns Symbols that match the given filter. Symbols from outward Scopes are included.
     */
    public final Iterable<Symbol> getSymbols(Filter<Symbol> sf) {
        return getSymbols(sf, RECURSIVE);
    }

    /**Returns all Symbols in this Scope. Symbols from outward Scopes are included
     * iff lookupKind == RECURSIVE.
     */
    public final Iterable<Symbol> getSymbols(LookupKind lookupKind) {
        return getSymbols(noFilter, lookupKind);
    }

    /**Returns Symbols that match the given filter. Symbols from outward Scopes are included
     * iff lookupKind == RECURSIVE.
     */
    public abstract Iterable<Symbol> getSymbols(Filter<Symbol> sf, LookupKind lookupKind);

    /**Returns Symbols with the given name. Symbols from outward Scopes are included.
     */
    public final Iterable<Symbol> getSymbolsByName(Name name) {
        return getSymbolsByName(name, RECURSIVE);
    }

    /**Returns Symbols with the given name that match the given filter.
     * Symbols from outward Scopes are included.
     */
    public final Iterable<Symbol> getSymbolsByName(final Name name, final Filter<Symbol> sf) {
        return getSymbolsByName(name, sf, RECURSIVE);
    }

    /**Returns Symbols with the given name. Symbols from outward Scopes are included
     * iff lookupKind == RECURSIVE.
     */
    public final Iterable<Symbol> getSymbolsByName(Name name, LookupKind lookupKind) {
        return getSymbolsByName(name, noFilter, lookupKind);
    }

    /**Returns Symbols with the given name that match the given filter.
     * Symbols from outward Scopes are included iff lookupKind == RECURSIVE.
     */
    public abstract Iterable<Symbol> getSymbolsByName(final Name name, final Filter<Symbol> sf,
                                                      final LookupKind lookupKind);

    /** Return the first Symbol from this or outward scopes with the given name.
     * Returns null if none.
     */
    public final Symbol findFirst(Name name) {
        return findFirst(name, noFilter);
    }

    /** Return the first Symbol from this or outward scopes with the given name that matches the
     *  given filter. Returns null if none.
     */
    public Symbol findFirst(Name name, Filter<Symbol> sf) {
        Iterator<Symbol> it = getSymbolsByName(name, sf).iterator();
        return it.hasNext() ? it.next() : null;
    }

    /** Returns true iff there are is at least one Symbol in this scope matching the given filter.
     *  Does not inspect outward scopes.
     */
    public boolean anyMatch(Filter<Symbol> filter) {
        return getSymbols(filter, NON_RECURSIVE).iterator().hasNext();
    }

    /** Returns true iff the given Symbol is in this scope or any outward scope.
     */
    public boolean includes(final Symbol sym) {
        return includes(sym, RECURSIVE);
    }

    /** Returns true iff the given Symbol is in this scope, optionally checking outward scopes.
     */
    public boolean includes(final Symbol sym, LookupKind lookupKind) {
        return getSymbolsByName(sym.name, t -> t == sym, lookupKind).iterator().hasNext();
    }

    /** Returns true iff this scope does not contain any Symbol. Does not inspect outward scopes.
     */
    public boolean isEmpty() {
        return !getSymbols(NON_RECURSIVE).iterator().hasNext();
    }

    /** Returns the Scope from which the givins Symbol originates in this scope.
     */
    public abstract Scope getOrigin(Symbol byName);

    /** Returns true iff the given Symbol is part of this scope due to a static import.
     */
    public abstract boolean isStaticallyImported(Symbol byName);

    private static final Filter<Symbol> noFilter = null;

    /** A list of scopes to be notified if items are to be removed from this scope.
     */
    ScopeListenerList listeners = new ScopeListenerList();

    public interface ScopeListener {
        void symbolAdded(Symbol sym, Scope s);
        void symbolRemoved(Symbol sym, Scope s);
    }

    /**
     * A list of scope listeners; listeners are stored in weak references, to avoid memory leaks.
     * When the listener list is scanned (upon notification), elements corresponding to GC-ed
     * listeners are removed so that the listener list size is kept in check.
     */
    public static class ScopeListenerList {

        JCList<WeakReference<ScopeListener>> listeners = JCList.nil();

        void add(ScopeListener sl) {
            listeners = listeners.prepend(new WeakReference<>(sl));
        }

        void symbolAdded(Symbol sym, Scope scope) {
            walkReferences(sym, scope, false);
        }

        void symbolRemoved(Symbol sym, Scope scope) {
            walkReferences(sym, scope, true);
        }

        private void walkReferences(Symbol sym, Scope scope, boolean isRemove) {
            ListBuffer<WeakReference<ScopeListener>> newListeners = new ListBuffer<>();
            for (WeakReference<ScopeListener> wsl : listeners) {
                ScopeListener sl = wsl.get();
                if (sl != null) {
                    if (isRemove) {
                        sl.symbolRemoved(sym, scope);
                    } else {
                        sl.symbolAdded(sym, scope);
                    }
                    newListeners.add(wsl);
                }
            }
            listeners = newListeners.toList();
        }
    }

    public enum LookupKind {
        RECURSIVE,
        NON_RECURSIVE;
    }

    /**A scope into which Symbols can be added.*/
    public abstract static class WriteableScope extends Scope {

        public WriteableScope(Symbol owner) {
            super(owner);
        }

        /** Enter the given Symbol into this scope.
         */
        public abstract void enter(Symbol c);
        /** Enter symbol sym in this scope if not already there.
         */
        public abstract void enterIfAbsent(Symbol c);

        public abstract void remove(Symbol c);

        /** Construct a fresh scope within this scope, with same owner. The new scope may
         *  shares internal structures with the this scope. Used in connection with
         *  method leave if scope access is stack-like in order to avoid allocation
         *  of fresh tables.
         */
        public final Scope.WriteableScope dup() {
            return dup(this.owner);
        }

        /** Construct a fresh scope within this scope, with new owner. The new scope may
         *  shares internal structures with the this scope. Used in connection with
         *  method leave if scope access is stack-like in order to avoid allocation
         *  of fresh tables.
         */
        public abstract Scope.WriteableScope dup(Symbol newOwner);

        /** Must be called on dup-ed scopes to be able to work with the outward scope again.
         */
        public abstract Scope.WriteableScope leave();

        /** Construct a fresh scope within this scope, with same owner. The new scope
         *  will not share internal structures with this scope.
         */
        public final Scope.WriteableScope dupUnshared() {
            return dupUnshared(owner);
        }

        /** Construct a fresh scope within this scope, with new owner. The new scope
         *  will not share internal structures with this scope.
         */
        public abstract Scope.WriteableScope dupUnshared(Symbol newOwner);

        /** Create a new WriteableScope.
         */
        public static Scope.WriteableScope create(Symbol owner) {
            return new Scope.ScopeImpl(owner);
        }

    }

    private static class ScopeImpl extends WriteableScope {
        /** The number of scopes that share this scope's hash table.
         */
        private int shared;

        /** Next enclosing scope (with whom this scope may share a hashtable)
         */
        public Scope.ScopeImpl next;

        /** A hash table for the scope's entries.
         */
        Scope.Entry[] table;

        /** Mask for hash codes, always equal to (table.length - 1).
         */
        int hashMask;

        /** A linear list that also contains all entries in
         *  reverse order of appearance (i.e later entries are pushed on top).
         */
        public Scope.Entry elems;

        /** The number of elements in this scope.
         * This includes deleted elements, whose value is the sentinel.
         */
        int nelems = 0;

        int removeCount = 0;

        /** Use as a "not-found" result for lookup.
         * Also used to mark deleted entries in the table.
         */
        private static final Scope.Entry sentinel = new Scope.Entry(null, null, null, null);

        /** The hash table's initial size.
         */
        private static final int INITIAL_SIZE = 0x10;

        /** Construct a new scope, within scope next, with given owner, using
         *  given table. The table's length must be an exponent of 2.
         */
        private ScopeImpl(Scope.ScopeImpl next, Symbol owner, Scope.Entry[] table) {
            super(owner);
            this.next = next;
            Assert.check(owner != null);
            this.table = table;
            this.hashMask = table.length - 1;
        }

        /** Convenience constructor used for dup and dupUnshared. */
        private ScopeImpl(Scope.ScopeImpl next, Symbol owner, Scope.Entry[] table, int nelems) {
            this(next, owner, table);
            this.nelems = nelems;
        }

        /** Construct a new scope, within scope next, with given owner,
         *  using a fresh table of length INITIAL_SIZE.
         */
        public ScopeImpl(Symbol owner) {
            this(null, owner, new Scope.Entry[INITIAL_SIZE]);
        }

        /** Construct a fresh scope within this scope, with new owner,
         *  which shares its table with the outer scope. Used in connection with
         *  method leave if scope access is stack-like in order to avoid allocation
         *  of fresh tables.
         */
        public Scope.WriteableScope dup(Symbol newOwner) {
            Scope.ScopeImpl result = new Scope.ScopeImpl(this, newOwner, this.table, this.nelems);
            shared++;
            // System.out.println("====> duping scope " + this.hashCode() + " owned by " + newOwner + " to " + result.hashCode());
            // new Error().printStackTrace(System.out);
            return result;
        }

        /** Construct a fresh scope within this scope, with new owner,
         *  with a new hash table, whose contents initially are those of
         *  the table of its outer scope.
         */
        public Scope.WriteableScope dupUnshared(Symbol newOwner) {
            if (shared > 0) {
                //The nested Scopes might have already added something to the table, so all items
                //that don't originate in this Scope or any of its outer Scopes need to be cleared:
                Set<Scope> acceptScopes = Collections.newSetFromMap(new IdentityHashMap<>());
                Scope.ScopeImpl c = this;
                while (c != null) {
                    acceptScopes.add(c);
                    c = c.next;
                }
                int n = 0;
                Scope.Entry[] oldTable = this.table;
                Scope.Entry[] newTable = new Scope.Entry[this.table.length];
                for (int i = 0; i < oldTable.length; i++) {
                    Scope.Entry e = oldTable[i];
                    while (e != null && e != sentinel && !acceptScopes.contains(e.scope)) {
                        e = e.shadowed;
                    }
                    if (e != null) {
                        n++;
                        newTable[i] = e;
                    }
                }
                return new Scope.ScopeImpl(this, newOwner, newTable, n);
            } else {
                return new Scope.ScopeImpl(this, newOwner, this.table.clone(), this.nelems);
            }
        }

        /** Remove all entries of this scope from its table, if shared
         *  with next.
         */
        public Scope.WriteableScope leave() {
            Assert.check(shared == 0);
            if (table != next.table) return next;
            while (elems != null) {
                int hash = getIndex(elems.sym.name);
                Scope.Entry e = table[hash];
                Assert.check(e == elems, elems.sym);
                table[hash] = elems.shadowed;
                elems = elems.sibling;
            }
            Assert.check(next.shared > 0);
            next.shared--;
            next.nelems = nelems;
            // System.out.println("====> leaving scope " + this.hashCode() + " owned by " + this.owner + " to " + next.hashCode());
            // new Error().printStackTrace(System.out);
            return next;
        }

        /** Double size of hash table.
         */
        private void dble() {
            Assert.check(shared == 0);
            Scope.Entry[] oldtable = table;
            Scope.Entry[] newtable = new Scope.Entry[oldtable.length * 2];
            for (Scope.ScopeImpl s = this; s != null; s = s.next) {
                if (s.table == oldtable) {
                    Assert.check(s == this || s.shared != 0);
                    s.table = newtable;
                    s.hashMask = newtable.length - 1;
                }
            }
            int n = 0;
            for (int i = oldtable.length; --i >= 0; ) {
                Scope.Entry e = oldtable[i];
                if (e != null && e != sentinel) {
                    table[getIndex(e.sym.name)] = e;
                    n++;
                }
            }
            // We don't need to update nelems for shared inherited scopes,
            // since that gets handled by leave().
            nelems = n;
        }

        /** Enter symbol sym in this scope.
         */
        public void enter(Symbol sym) {
            Assert.check(shared == 0);
            if (nelems * 3 >= hashMask * 2)
                dble();
            int hash = getIndex(sym.name);
            Scope.Entry old = table[hash];
            if (old == null) {
                old = sentinel;
                nelems++;
            }
            Scope.Entry e = new Scope.Entry(sym, old, elems, this);
            table[hash] = e;
            elems = e;

            //notify listeners
            listeners.symbolAdded(sym, this);
        }

        /** Remove symbol from this scope.
         */
        public void remove(Symbol sym) {
            Assert.check(shared == 0);
            Scope.Entry e = lookup(sym.name, candidate -> candidate == sym);
            if (e.scope == null) return;

            // remove e from table and shadowed list;
            int i = getIndex(sym.name);
            Scope.Entry te = table[i];
            if (te == e)
                table[i] = e.shadowed;
            else while (true) {
                if (te.shadowed == e) {
                    te.shadowed = e.shadowed;
                    break;
                }
                te = te.shadowed;
            }

            // remove e from elems and sibling list
            te = elems;
            if (te == e)
                elems = e.sibling;
            else while (true) {
                if (te.sibling == e) {
                    te.sibling = e.sibling;
                    break;
                }
                te = te.sibling;
            }

            removeCount++;

            //notify listeners
            listeners.symbolRemoved(sym, this);
        }

        /** Enter symbol sym in this scope if not already there.
         */
        public void enterIfAbsent(Symbol sym) {
            Assert.check(shared == 0);
            Scope.Entry e = lookup(sym.name);
            while (e.scope == this && e.sym.kind != sym.kind) e = e.next();
            if (e.scope != this) enter(sym);
        }

        /** Given a class, is there already a class with same fully
         *  qualified name in this (import) scope?
         */
        public boolean includes(Symbol c) {
            for (Entry e = lookup(c.name);
                 e.scope == this;
                 e = e.next()) {
                if (e.sym == c) return true;
            }
            return false;
        }

        /** Return the entry associated with given name, starting in
         *  this scope and proceeding outwards. If no entry was found,
         *  return the sentinel, which is characterized by having a null in
         *  both its scope and sym fields, whereas both fields are non-null
         *  for regular entries.
         */
        protected Scope.Entry lookup(Name name) {
            return lookup(name, noFilter);
        }

        protected Scope.Entry lookup(Name name, Filter<Symbol> sf) {
            Scope.Entry e = table[getIndex(name)];
            if (e == null || e == sentinel)
                return sentinel;
            while (e.scope != null && (e.sym.name != name || (sf != null && !sf.accepts(e.sym))))
                e = e.shadowed;
            return e;
        }

        public Symbol findFirst(Name name, Filter<Symbol> sf) {
            return lookup(name, sf).sym;
        }

        /*void dump (java.io.PrintStream out) {
            out.println(this);
            for (int l=0; l < table.length; l++) {
                Entry le = table[l];
                out.print("#"+l+": ");
                if (le==sentinel) out.println("sentinel");
                else if(le == null) out.println("null");
                else out.println(""+le+" s:"+le.sym);
            }
        }*/

        /** Look for slot in the table.
         *  We use open addressing with double hashing.
         */
        int getIndex (Name name) {
            int h = name.hashCode();
            int i = h & hashMask;
            // The expression below is always odd, so it is guaranteed
            // to be mutually prime with table.length, a power of 2.
            int x = hashMask - ((h + (h >> 16)) << 1);
            int d = -1; // Index of a deleted item.
            for (;;) {
                Scope.Entry e = table[i];
                if (e == null)
                    return d >= 0 ? d : i;
                if (e == sentinel) {
                    // We have to keep searching even if we see a deleted item.
                    // However, remember the index in case we fail to find the name.
                    if (d < 0)
                        d = i;
                } else if (e.sym.name == name)
                    return i;
                i = (i + x) & hashMask;
            }
        }

        public boolean anyMatch(Filter<Symbol> sf) {
            return getSymbols(sf, NON_RECURSIVE).iterator().hasNext();
        }

        public Iterable<Symbol> getSymbols(final Filter<Symbol> sf,
                                           final Scope.LookupKind lookupKind) {
            return () -> new Iterator<Symbol>() {
                private Scope.ScopeImpl currScope = Scope.ScopeImpl.this;
                private Scope.Entry currEntry = elems;
                private int seenRemoveCount = currScope.removeCount;
                {
                    update();
                }

                public boolean hasNext() {
                    if (seenRemoveCount != currScope.removeCount &&
                        currEntry != null &&
                        !currEntry.scope.includes(currEntry.sym)) {
                        doNext(); //skip entry that is no longer in the Scope
                        seenRemoveCount = currScope.removeCount;
                    }
                    return currEntry != null;
                }

                public Symbol next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    return doNext();
                }
                private Symbol doNext() {
                    Symbol sym = (currEntry == null ? null : currEntry.sym);
                    if (currEntry != null) {
                        currEntry = currEntry.sibling;
                    }
                    update();
                    return sym;
                }

                private void update() {
                    skipToNextMatchingEntry();
                    if (lookupKind == RECURSIVE) {
                        while (currEntry == null && currScope.next != null) {
                            currScope = currScope.next;
                            currEntry = currScope.elems;
                            seenRemoveCount = currScope.removeCount;
                            skipToNextMatchingEntry();
                        }
                    }
                }

                void skipToNextMatchingEntry() {
                    while (currEntry != null && sf != null && !sf.accepts(currEntry.sym)) {
                        currEntry = currEntry.sibling;
                    }
                }
            };
        }

        public Iterable<Symbol> getSymbolsByName(final Name name,
                                                 final Filter<Symbol> sf,
                                                 final Scope.LookupKind lookupKind) {
            return () -> new Iterator<Symbol>() {
               Scope.Entry currentEntry = lookup(name, sf);
               int seenRemoveCount = currentEntry.scope != null ?
                       currentEntry.scope.removeCount : -1;

               public boolean hasNext() {
                   if (currentEntry.scope != null &&
                       seenRemoveCount != currentEntry.scope.removeCount &&
                       !currentEntry.scope.includes(currentEntry.sym)) {
                       doNext(); //skip entry that is no longer in the Scope
                   }
                   return currentEntry.scope != null &&
                           (lookupKind == RECURSIVE ||
                            currentEntry.scope == Scope.ScopeImpl.this);
               }
               public Symbol next() {
                   if (!hasNext()) {
                       throw new NoSuchElementException();
                   }
                   return doNext();
               }
               private Symbol doNext() {
                   Scope.Entry prevEntry = currentEntry;
                   currentEntry = currentEntry.next(sf);
                   return prevEntry.sym;
               }
               public void remove() {
                   throw new UnsupportedOperationException();
               }
           };
        }

        public Scope getOrigin(Symbol s) {
            for (Entry e = lookup(s.name); e.scope != null ; e = e.next()) {
                if (e.sym == s) {
                    return this;
                }
            }
            return null;
        }

        @Override
        public boolean isStaticallyImported(Symbol s) {
            return false;
        }

        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("Scope[");
            for (Scope.ScopeImpl s = this; s != null ; s = s.next) {
                if (s != this) result.append(" | ");
                for (Scope.Entry e = s.elems; e != null; e = e.sibling) {
                    if (e != s.elems) result.append(", ");
                    result.append(e.sym);
                }
            }
            result.append("]");
            return result.toString();
        }
    }

    /** A class for scope entries.
     */
    private static class Entry {

        /** The referenced symbol.
         *  sym == null   iff   this == sentinel
         */
        public Symbol sym;

        /** An entry with the same hash code, or sentinel.
         */
        private Entry shadowed;

        /** Next entry in same scope.
         */
        public Entry sibling;

        /** The entry's scope.
         *  scope == null   iff   this == sentinel
         */
        public ScopeImpl scope;

        public Entry(Symbol sym, Entry shadowed, Entry sibling, ScopeImpl scope) {
            this.sym = sym;
            this.shadowed = shadowed;
            this.sibling = sibling;
            this.scope = scope;
        }

        /** Return next entry with the same name as this entry, proceeding
         *  outwards if not found in this scope.
         */
        public Entry next() {
            return shadowed;
        }

        public Entry next(Filter<Symbol> sf) {
            if (shadowed.sym == null || sf == null || sf.accepts(shadowed.sym)) return shadowed;
            else return shadowed.next(sf);
        }

    }

    public static class ImportScope extends CompoundScope {

        public ImportScope(Symbol owner) {
            super(owner);
        }

        /**Finalize the content of the ImportScope to speed-up future lookups.
         * No further changes to class hierarchy or class content will be reflected.
         */
        public void finalizeScope() {
            for (JCList<Scope> scopes = this.subScopes; scopes.nonEmpty(); scopes = scopes.tail) {
                Scope impScope = scopes.head;

                if (impScope instanceof Scope.FilterImportScope && impScope.owner.kind == Kind.TYP) {
                    Scope.WriteableScope finalized = Scope.WriteableScope.create(impScope.owner);

                    for (Symbol sym : impScope.getSymbols()) {
                        finalized.enter(sym);
                    }

                    finalized.listeners.add(new Scope.ScopeListener() {
                        @Override
                        public void symbolAdded(Symbol sym, Scope s) {
                            Assert.error("The scope is sealed.");
                        }

                        @Override
                        public void symbolRemoved(Symbol sym, Scope s) {
                            Assert.error("The scope is sealed.");
                        }
                    });

                    scopes.head = finalized;
                }
            }
        }

    }

    public static class NamedImportScope extends ImportScope {

        public NamedImportScope(Symbol owner, Scope currentFileScope) {
            super(owner);
            prependSubScope(currentFileScope);
        }

        public Scope importByName(Types types, Scope origin, Name name, Scope.ImportFilter filter, JCImport imp, BiConsumer<JCImport, CompletionFailure> cfHandler) {
            return appendScope(new Scope.FilterImportScope(types, origin, name, filter, imp, cfHandler));
        }

        public Scope importType(Scope delegate, Scope origin, Symbol sym) {
            return appendScope(new SingleEntryScope(delegate.owner, sym, origin));
        }

        private Scope appendScope(Scope newScope) {
            JCList<Scope> existingScopes = this.subScopes.reverse();
            subScopes = JCList.of(existingScopes.head);
            subScopes = subScopes.prepend(newScope);
            for (Scope s : existingScopes.tail) {
                subScopes = subScopes.prepend(s);
            }
            return newScope;
        }

        private static class SingleEntryScope extends Scope {

            private final Symbol sym;
            private final JCList<Symbol> content;
            private final Scope origin;

            public SingleEntryScope(Symbol owner, Symbol sym, Scope origin) {
                super(owner);
                this.sym = sym;
                this.content = JCList.of(sym);
                this.origin = origin;
            }

            @Override
            public Iterable<Symbol> getSymbols(Filter<Symbol> sf, Scope.LookupKind lookupKind) {
                return sf == null || sf.accepts(sym) ? content : Collections.emptyList();
            }

            @Override
            public Iterable<Symbol> getSymbolsByName(Name name,
                                                     Filter<Symbol> sf,
                                                     Scope.LookupKind lookupKind) {
                return sym.name == name &&
                       (sf == null || sf.accepts(sym)) ? content : Collections.emptyList();
            }

            @Override
            public Scope getOrigin(Symbol byName) {
                return sym == byName ? origin : null;
            }

            @Override
            public boolean isStaticallyImported(Symbol byName) {
                return false;
            }

        }
    }

    public static class StarImportScope extends ImportScope {

        public StarImportScope(Symbol owner) {
            super(owner);
        }

        public void importAll(Types types, Scope origin,
                              Scope.ImportFilter filter,
                              JCImport imp,
                              BiConsumer<JCImport, CompletionFailure> cfHandler) {
            for (Scope existing : subScopes) {
                Assert.check(existing instanceof Scope.FilterImportScope);
                Scope.FilterImportScope fis = (Scope.FilterImportScope) existing;
                if (fis.origin == origin && fis.filter == filter &&
                    fis.imp.staticImport == imp.staticImport)
                    return ; //avoid entering the same scope twice
            }
            prependSubScope(new Scope.FilterImportScope(types, origin, null, filter, imp, cfHandler));
        }

        public boolean isFilled() {
            return subScopes.nonEmpty();
        }

    }

    public interface ImportFilter {
        public boolean accepts(Scope origin, Symbol sym);
    }

    private static class FilterImportScope extends Scope {

        private final Types types;
        private final Scope origin;
        private final Name  filterName;
        private final Scope.ImportFilter filter;
        private final JCImport imp;
        private final BiConsumer<JCImport, CompletionFailure> cfHandler;

        public FilterImportScope(Types types,
                                 Scope origin,
                                 Name  filterName,
                                 Scope.ImportFilter filter,
                                 JCImport imp,
                                 BiConsumer<JCImport, CompletionFailure> cfHandler) {
            super(origin.owner);
            this.types = types;
            this.origin = origin;
            this.filterName = filterName;
            this.filter = filter;
            this.imp = imp;
            this.cfHandler = cfHandler;
        }

        @Override
        public Iterable<Symbol> getSymbols(final Filter<Symbol> sf, final Scope.LookupKind lookupKind) {
            if (filterName != null)
                return getSymbolsByName(filterName, sf, lookupKind);
            try {
                SymbolImporter si = new SymbolImporter(imp.staticImport) {
                    @Override
                    Iterable<Symbol> doLookup(TypeSymbol tsym) {
                        return tsym.members().getSymbols(sf, lookupKind);
                    }
                };
                JCList<Iterable<Symbol>> results =
                        si.importFrom((TypeSymbol) origin.owner, JCList.nil());
                return () -> createFilterIterator(createCompoundIterator(results,
                                                                         Iterable::iterator),
                                                  s -> filter.accepts(origin, s));
            } catch (CompletionFailure cf) {
                cfHandler.accept(imp, cf);
                return Collections.emptyList();
            }
        }

        @Override
        public Iterable<Symbol> getSymbolsByName(final Name name,
                                                 final Filter<Symbol> sf,
                                                 final Scope.LookupKind lookupKind) {
            if (filterName != null && filterName != name)
                return Collections.emptyList();
            try {
                SymbolImporter si = new SymbolImporter(imp.staticImport) {
                    @Override
                    Iterable<Symbol> doLookup(TypeSymbol tsym) {
                        return tsym.members().getSymbolsByName(name, sf, lookupKind);
                    }
                };
                JCList<Iterable<Symbol>> results =
                        si.importFrom((TypeSymbol) origin.owner, JCList.nil());
                return () -> createFilterIterator(createCompoundIterator(results,
                                                                         Iterable::iterator),
                                                  s -> filter.accepts(origin, s));
            } catch (CompletionFailure cf) {
                cfHandler.accept(imp, cf);
                return Collections.emptyList();
            }
        }

        @Override
        public Scope getOrigin(Symbol byName) {
            return origin;
        }

        @Override
        public boolean isStaticallyImported(Symbol byName) {
            return imp.staticImport;
        }

        abstract class SymbolImporter {
            Set<Symbol> processed = new HashSet<>();
            JCList<Iterable<Symbol>> delegates = JCList.nil();
            final boolean inspectSuperTypes;
            public SymbolImporter(boolean inspectSuperTypes) {
                this.inspectSuperTypes = inspectSuperTypes;
            }
            JCList<Iterable<Symbol>> importFrom(TypeSymbol tsym, JCList<Iterable<Symbol>> results) {
                if (tsym == null || !processed.add(tsym))
                    return results;


                if (inspectSuperTypes) {
                    // also import inherited names
                    results = importFrom(types.supertype(tsym.type).tsym, results);
                    for (Type t : types.interfaces(tsym.type))
                        results = importFrom(t.tsym, results);
                }

                return results.prepend(doLookup(tsym));
            }
            abstract Iterable<Symbol> doLookup(TypeSymbol tsym);
        }

    }

    /** A class scope adds capabilities to keep track of changes in related
     *  class scopes - this allows client to realize whether a class scope
     *  has changed, either directly (because a new member has been added/removed
     *  to this scope) or indirectly (i.e. because a new member has been
     *  added/removed into a supertype scope)
     */
    public static class CompoundScope extends Scope implements ScopeListener {

        JCList<Scope> subScopes = JCList.nil();
        private int mark = 0;

        public CompoundScope(Symbol owner) {
            super(owner);
        }

        public void prependSubScope(Scope that) {
           if (that != null) {
                subScopes = subScopes.prepend(that);
                that.listeners.add(this);
                mark++;
                listeners.symbolAdded(null, this);
           }
        }

        public void symbolAdded(Symbol sym, Scope s) {
            mark++;
            listeners.symbolAdded(sym, s);
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            mark++;
            listeners.symbolRemoved(sym, s);
        }

        public int getMark() {
            return mark;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("CompoundScope{");
            String sep = "";
            for (Scope s : subScopes) {
                buf.append(sep);
                buf.append(s);
                sep = ",";
            }
            buf.append("}");
            return buf.toString();
        }

        @Override
        public Iterable<Symbol> getSymbols(final Filter<Symbol> sf,
                                           final Scope.LookupKind lookupKind) {
            return () -> Iterators.createCompoundIterator(subScopes,
                                                          scope -> scope.getSymbols(sf,
                                                                                    lookupKind)
                                                                        .iterator());
        }

        @Override
        public Iterable<Symbol> getSymbolsByName(final Name name,
                                                 final Filter<Symbol> sf,
                                                 final Scope.LookupKind lookupKind) {
            return () -> Iterators.createCompoundIterator(subScopes,
                                                          scope -> scope.getSymbolsByName(name,
                                                                                          sf,
                                                                                          lookupKind)
                                                                        .iterator());
        }

        @Override
        public Scope getOrigin(Symbol sym) {
            for (Scope delegate : subScopes) {
                if (delegate.includes(sym))
                    return delegate.getOrigin(sym);
            }

            return null;
        }

        @Override
        public boolean isStaticallyImported(Symbol sym) {
            for (Scope delegate : subScopes) {
                if (delegate.includes(sym))
                    return delegate.isStaticallyImported(sym);
            }

            return false;
        }

    }

    /** An error scope, for which the owner should be an error symbol. */
    public static class ErrorScope extends ScopeImpl {
        ErrorScope(Scope.ScopeImpl next, Symbol errSymbol, Scope.Entry[] table) {
            super(next, /*owner=*/errSymbol, table);
        }
        public ErrorScope(Symbol errSymbol) {
            super(errSymbol);
        }
        public Scope.WriteableScope dup(Symbol newOwner) {
            return new Scope.ErrorScope(this, newOwner, table);
        }
        public Scope.WriteableScope dupUnshared(Symbol newOwner) {
            return new Scope.ErrorScope(this, newOwner, table.clone());
        }
        public Scope.Entry lookup(Name name) {
            Scope.Entry e = super.lookup(name);
            if (e.scope == null)
                return new Scope.Entry(owner, null, null, null);
            else
                return e;
        }
    }
}
