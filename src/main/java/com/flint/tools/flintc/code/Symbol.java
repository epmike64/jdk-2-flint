/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Inherited;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.flint.tools.flintc.comp.Attr;
import com.flint.tools.flintc.comp.AttrContext;
import com.flint.tools.flintc.comp.Env;
import com.flint.tools.flintc.jvm.Code;
import com.flint.tools.flintc.jvm.Gen;
import com.flint.tools.flintc.jvm.Pool;
import com.flint.tools.flintc.code.ClassFinder.BadEnclosingMethodAttr;
import com.flint.tools.flintc.code.Kinds.Kind;
import com.flint.tools.flintc.comp.Annotate.AnnotationTypeMetadata;
import com.flint.tools.flintc.code.Scope.WriteableScope;
import com.flint.tools.flintc.code.Type.*;
import com.flint.tools.flintc.tree.JCTree.JCFieldAccess;
import com.flint.tools.flintc.tree.JCTree.JCVariableDecl;
import com.flint.tools.flintc.tree.JCTree.JCTreeTag;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.Name;

import static com.flint.tools.flintc.code.Flags.*;
import static com.flint.tools.flintc.code.Kinds.*;
import static com.flint.tools.flintc.code.Kinds.Kind.*;
import static com.flint.tools.flintc.code.Scope.LookupKind.NON_RECURSIVE;
import static com.flint.tools.flintc.code.TypeTag.CLASS;
import static com.flint.tools.flintc.code.TypeTag.FORALL;
import static com.flint.tools.flintc.code.TypeTag.TYPEVAR;
import static com.flint.tools.flintc.jvm.ByteCodes.iadd;
import static com.flint.tools.flintc.jvm.ByteCodes.ishll;
import static com.flint.tools.flintc.jvm.ByteCodes.lushrl;
import static com.flint.tools.flintc.jvm.ByteCodes.lxor;
import static com.flint.tools.flintc.jvm.ByteCodes.string_add;

/** Root class for Java symbols. It contains subclasses
 *  for specific sorts of symbols, such as variables, methods and operators,
 *  types, packages. Each subclass is represented as a static inner class
 *  inside Symbol.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Symbol extends AnnoConstruct implements Element {

    /** The kind of this symbol.
     *  @see Kinds
     */
    public Kind kind;

    /** The flags of this symbol.
     */
    public long flags_field;

    /** An accessor method for the flags of this symbol.
     *  Flags of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public long flags() { return flags_field; }

    /** The name of this symbol in Utf8 representation.
     */
    public Name name;

    /** The type of this symbol.
     */
    public Type type;

    /** The owner of this symbol.
     */
    public Symbol owner;

    /** The completer of this symbol.
     * This should never equal null (NULL_COMPLETER should be used instead).
     */
    public Completer completer;

    /** A cache for the type erasure of this symbol.
     */
    public Type erasure_field;

    // <editor-fold defaultstate="collapsed" desc="annotations">

    /** The attributes of this symbol are contained in this
     * SymbolMetadata. The SymbolMetadata instance is NOT immutable.
     */
    protected SymbolMetadata metadata;


    /** An accessor method for the attributes of this symbol.
     *  Attributes of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public JCList<Attribute.Compound> getRawAttributes() {
        return (metadata == null)
                ? JCList.nil()
                : metadata.getDeclarationAttributes();
    }

    /** An accessor method for the type attributes of this symbol.
     *  Attributes of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public JCList<Attribute.TypeCompound> getRawTypeAttributes() {
        return (metadata == null)
                ? JCList.nil()
                : metadata.getTypeAttributes();
    }

    /** Fetch a particular annotation from a symbol. */
    public Attribute.Compound attribute(Symbol anno) {
        for (Attribute.Compound a : getRawAttributes()) {
            if (a.type.tsym == anno) return a;
        }
        return null;
    }

    public boolean annotationsPendingCompletion() {
        return metadata == null ? false : metadata.pendingCompletion();
    }

    public void appendAttributes(JCList<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedMetadata().append(l);
        }
    }

    public void appendClassInitTypeAttributes(JCList<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendClassInitTypeAttributes(l);
        }
    }

    public void appendInitTypeAttributes(JCList<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendInitTypeAttributes(l);
        }
    }

    public void appendUniqueTypeAttributes(JCList<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendUniqueTypes(l);
        }
    }

    public JCList<Attribute.TypeCompound> getClassInitTypeAttributes() {
        return (metadata == null)
                ? JCList.nil()
                : metadata.getClassInitTypeAttributes();
    }

    public JCList<Attribute.TypeCompound> getInitTypeAttributes() {
        return (metadata == null)
                ? JCList.nil()
                : metadata.getInitTypeAttributes();
    }

    public void setInitTypeAttributes(JCList<Attribute.TypeCompound> l) {
        initedMetadata().setInitTypeAttributes(l);
    }

    public void setClassInitTypeAttributes(JCList<Attribute.TypeCompound> l) {
        initedMetadata().setClassInitTypeAttributes(l);
    }

    public JCList<Attribute.Compound> getDeclarationAttributes() {
        return (metadata == null)
                ? JCList.nil()
                : metadata.getDeclarationAttributes();
    }

    public boolean hasAnnotations() {
        return (metadata != null && !metadata.isEmpty());
    }

    public boolean hasTypeAnnotations() {
        return (metadata != null && !metadata.isTypesEmpty());
    }

    public boolean isCompleted() {
        return completer.isTerminal();
    }

    public void prependAttributes(JCList<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedMetadata().prepend(l);
        }
    }

    public void resetAnnotations() {
        initedMetadata().reset();
    }

    public void setAttributes(Symbol other) {
        if (metadata != null || other.metadata != null) {
            initedMetadata().setAttributes(other.metadata);
        }
    }

    public void setDeclarationAttributes(JCList<Attribute.Compound> a) {
        if (metadata != null || a.nonEmpty()) {
            initedMetadata().setDeclarationAttributes(a);
        }
    }

    public void setTypeAttributes(JCList<Attribute.TypeCompound> a) {
        if (metadata != null || a.nonEmpty()) {
            if (metadata == null)
                metadata = new SymbolMetadata(this);
            metadata.setTypeAttributes(a);
        }
    }

    private SymbolMetadata initedMetadata() {
        if (metadata == null)
            metadata = new SymbolMetadata(this);
        return metadata;
    }

    /** This method is intended for debugging only. */
    public SymbolMetadata getMetadata() {
        return metadata;
    }

    // </editor-fold>

    /** Construct a symbol with given kind, flags, name, type and owner.
     */
    public Symbol(Kind kind, long flags, Name name, Type type, Symbol owner) {
        this.kind = kind;
        this.flags_field = flags;
        this.type = type;
        this.owner = owner;
        this.completer = Completer.NULL_COMPLETER;
        this.erasure_field = null;
        this.name = name;
    }

    /** Clone this symbol with new owner.
     *  Legal only for fields and methods.
     */
    public Symbol clone(Symbol newOwner) {
        throw new AssertionError();
    }

    public <R, P> R accept(Visitor<R, P> v, P p) {
        return v.visitSymbol(this, p);
    }

    /** The Java source which this symbol represents.
     *  A description of this symbol; overrides Object.
     */
    public String toString() {
        return name.toString();
    }

    /** A Java source description of the location of this symbol; used for
     *  error reporting.
     *
     * @return null if the symbol is a package or a toplevel class defined in
     * the default package; otherwise, the owner symbol is returned
     */
    public Symbol location() {
        if (owner.name == null || (owner.name.isEmpty() &&
                                   (owner.flags() & BLOCK) == 0 &&
                                   owner.kind != PCK &&
                                   owner.kind != TYP)) {
            return null;
        }
        return owner;
    }

    public Symbol location(Type site, Types types) {
        if (owner.name == null || owner.name.isEmpty()) {
            return location();
        }
        if (owner.type.hasTag(CLASS)) {
            Type ownertype = types.asOuterSuper(site, owner);
            if (ownertype != null) return ownertype.tsym;
        }
        return owner;
    }

    public Symbol baseSymbol() {
        return this;
    }

    /** The symbol's erased type.
     */
    public Type erasure(Types types) {
        if (erasure_field == null)
            erasure_field = types.erasure(type);
        return erasure_field;
    }

    /** The external type of a symbol. This is the symbol's erased type
     *  except for constructors of inner classes which get the enclosing
     *  instance class added as first argument.
     */
    public Type externalType(Types types) {
        Type t = erasure(types);
        if (name == name.table.names.init && owner.hasOuterInstance()) {
            Type outerThisType = types.erasure(owner.type.getEnclosingType());
            return new MethodType(t.getParameterTypes().prepend(outerThisType),
                                  t.getReturnType(),
                                  t.getThrownTypes(),
                                  t.tsym);
        } else {
            return t;
        }
    }

    public boolean isDeprecated() {
        return (flags_field & DEPRECATED) != 0;
    }

    public boolean hasDeprecatedAnnotation() {
        return (flags_field & DEPRECATED_ANNOTATION) != 0;
    }

    public boolean isDeprecatedForRemoval() {
        return (flags_field & DEPRECATED_REMOVAL) != 0;
    }

    public boolean isDeprecatableViaAnnotation() {
        switch (getKind()) {
            case LOCAL_VARIABLE:
            case PACKAGE:
            case PARAMETER:
            case RESOURCE_VARIABLE:
            case EXCEPTION_PARAMETER:
                return false;
            default:
                return true;
        }
    }

    public boolean isStatic() {
        return
            (flags() & STATIC) != 0 ||
            (owner.flags() & INTERFACE) != 0 && kind != MTH &&
             name != name.table.names._this;
    }

    public boolean isInterface() {
        return (flags() & INTERFACE) != 0;
    }

    public boolean isPrivate() {
        return (flags_field & Flags.AccessFlags) == PRIVATE;
    }

    public boolean isEnum() {
        return (flags() & ENUM) != 0;
    }

    /** Is this symbol declared (directly or indirectly) local
     *  to a method or variable initializer?
     *  Also includes fields of inner classes which are in
     *  turn local to a method or variable initializer.
     */
    public boolean isLocal() {
        return
            (owner.kind.matches(KindSelector.VAL_MTH) ||
             (owner.kind == TYP && owner.isLocal()));
    }

    /** Has this symbol an empty name? This includes anonymous
     *  inner classes.
     */
    public boolean isAnonymous() {
        return name.isEmpty();
    }

    /** Is this symbol a constructor?
     */
    public boolean isConstructor() {
        return name == name.table.names.init;
    }

    /** The fully qualified name of this symbol.
     *  This is the same as the symbol's name except for class symbols,
     *  which are handled separately.
     */
    public Name getQualifiedName() {
        return name;
    }

    /** The fully qualified name of this symbol after converting to flat
     *  representation. This is the same as the symbol's name except for
     *  class symbols, which are handled separately.
     */
    public Name flatName() {
        return getQualifiedName();
    }

    /** If this is a class or package, its members, otherwise null.
     */
    public WriteableScope members() {
        return null;
    }

    /** A class is an inner class if it it has an enclosing instance class.
     */
    public boolean isInner() {
        return kind == TYP && type.getEnclosingType().hasTag(CLASS);
    }

    /** An inner class has an outer instance if it is not an interface
     *  it has an enclosing instance class which might be referenced from the class.
     *  Nested classes can see instance members of their enclosing class.
     *  Their constructors carry an additional this$n parameter, inserted
     *  implicitly by the compiler.
     *
     *  @see #isInner
     */
    public boolean hasOuterInstance() {
        return
            type.getEnclosingType().hasTag(CLASS) && (flags() & (INTERFACE | NOOUTERTHIS)) == 0;
    }

    /** The closest enclosing class of this symbol's declaration.
     *  Warning: this (misnamed) method returns the receiver itself
     *  when the receiver is a class (as opposed to its enclosing
     *  class as one may be misled to believe.)
     */
    public ClassSymbol enclClass() {
        Symbol c = this;
        while (c != null &&
               (!c.kind.matches(KindSelector.TYP) || !c.type.hasTag(CLASS))) {
            c = c.owner;
        }
        return (ClassSymbol)c;
    }

    /** The outermost class which indirectly owns this symbol.
     */
    public ClassSymbol outermostClass() {
        Symbol sym = this;
        Symbol prev = null;
        while (sym.kind != PCK) {
            prev = sym;
            sym = sym.owner;
        }
        return (ClassSymbol) prev;
    }

    /** The package which indirectly owns this symbol.
     */
    public PackageSymbol packge() {
        Symbol sym = this;
        while (sym.kind != PCK) {
            sym = sym.owner;
        }
        return (PackageSymbol) sym;
    }

    /** Is this symbol a subclass of `base'? Only defined for ClassSymbols.
     */
    public boolean isSubClass(Symbol base, Types types) {
        throw new AssertionError("isSubClass " + this);
    }

    /** Fully check membership: hierarchy, protection, and hiding.
     *  Does not exclude methods not inherited due to overriding.
     */
    public boolean isMemberOf(TypeSymbol clazz, Types types) {
        return
            owner == clazz ||
            clazz.isSubClass(owner, types) &&
            isInheritedIn(clazz, types) &&
            !hiddenIn((ClassSymbol)clazz, types);
    }

    /** Is this symbol the same as or enclosed by the given class? */
    public boolean isEnclosedBy(ClassSymbol clazz) {
        for (Symbol sym = this; sym.kind != PCK; sym = sym.owner)
            if (sym == clazz) return true;
        return false;
    }

    private boolean hiddenIn(ClassSymbol clazz, Types types) {
        Symbol sym = hiddenInInternal(clazz, types);
        Assert.check(sym != null, "the result of hiddenInInternal() can't be null");
        /* If we find the current symbol then there is no symbol hiding it
         */
        return sym != this;
    }

    /** This method looks in the supertypes graph that has the current class as the
     * initial node, till it finds the current symbol or another symbol that hides it.
     * If the current class has more than one supertype (extends one class and
     * implements one or more interfaces) then null can be returned, meaning that
     * a wrong path in the supertypes graph was selected. Null can only be returned
     * as a temporary value, as a result of the recursive call.
     */
    private Symbol hiddenInInternal(ClassSymbol currentClass, Types types) {
        if (currentClass == owner) {
            return this;
        }
        for (Symbol sym : currentClass.members().getSymbolsByName(name)) {
            if (sym.kind == kind &&
                    (kind != MTH ||
                    (sym.flags() & STATIC) != 0 &&
                    types.isSubSignature(sym.type, type))) {
                return sym;
            }
        }
        Symbol hiddenSym = null;
        for (Type st : types.interfaces(currentClass.type)
                .prepend(types.supertype(currentClass.type))) {
            if (st != null && (st.hasTag(CLASS))) {
                Symbol sym = hiddenInInternal((ClassSymbol)st.tsym, types);
                if (sym == this) {
                    return this;
                } else if (sym != null) {
                    hiddenSym = sym;
                }
            }
        }
        return hiddenSym;
    }

    /** Is this symbol inherited into a given class?
     *  PRE: If symbol's owner is a interface,
     *       it is already assumed that the interface is a superinterface
     *       of given class.
     *  @param clazz  The class for which we want to establish membership.
     *                This must be a subclass of the member's owner.
     */
    public boolean isInheritedIn(Symbol clazz, Types types) {
        switch ((int)(flags_field & Flags.AccessFlags)) {
        default: // error recovery
        case PUBLIC:
            return true;
        case PRIVATE:
            return this.owner == clazz;
        case PROTECTED:
            // we model interfaces as extending Object
            return (clazz.flags() & INTERFACE) == 0;
        case 0:
            PackageSymbol thisPackage = this.packge();
            for (Symbol sup = clazz;
                 sup != null && sup != this.owner;
                 sup = types.supertype(sup.type).tsym) {
                while (sup.type.hasTag(TYPEVAR))
                    sup = sup.type.getUpperBound().tsym;
                if (sup.type.isErroneous())
                    return true; // error recovery
                if ((sup.flags() & COMPOUND) != 0)
                    continue;
                if (sup.packge() != thisPackage)
                    return false;
            }
            return (clazz.flags() & INTERFACE) == 0;
        }
    }

    /** The (variable or method) symbol seen as a member of given
     *  class type`site' (this might change the symbol's type).
     *  This is used exclusively for producing diagnostics.
     */
    public Symbol asMemberOf(Type site, Types types) {
        throw new AssertionError();
    }

    /** Does this method symbol override `other' symbol, when both are seen as
     *  members of class `origin'?  It is assumed that _other is a member
     *  of origin.
     *
     *  It is assumed that both symbols have the same name.  The static
     *  modifier is ignored for this test.
     *
     *  See JLS 8.4.6.1 (without transitivity) and 8.4.6.4
     */
    public boolean overrides(Symbol _other, TypeSymbol origin, Types types, boolean checkResult) {
        return false;
    }

    /** Complete the elaboration of this symbol's definition.
     */
    public void complete() throws CompletionFailure {
        if (completer != Completer.NULL_COMPLETER) {
            Completer c = completer;
            completer = Completer.NULL_COMPLETER;
            c.complete(this);
        }
    }

    /** True if the symbol represents an entity that exists.
     */
    public boolean exists() {
        return true;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Type asType() {
        return type;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Symbol getEnclosingElement() {
        return owner;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public ElementKind getKind() {
        return ElementKind.OTHER;       // most unkind
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Set<Modifier> getModifiers() {
        return Flags.asModifierSet(flags());
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Name getSimpleName() {
        return name;
    }

    /**
     * This is the implementation for {@code
     * javax.lang.model.element.Element.getAnnotationMirrors()}.
     */
    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public JCList<Attribute.Compound> getAnnotationMirrors() {
        return getRawAttributes();
    }


    // TODO: getEnclosedElements should return a javac List, fix in FilteredMemberList
    @DefinedBy(Api.LANGUAGE_MODEL)
    public java.util.List<Symbol> getEnclosedElements() {
        return JCList.nil();
    }

    public JCList<TypeVariableSymbol> getTypeParameters() {
        ListBuffer<TypeVariableSymbol> l = new ListBuffer<>();
        for (Type t : type.getTypeArguments()) {
            Assert.check(t.tsym.getKind() == ElementKind.TYPE_PARAMETER);
            l.append((TypeVariableSymbol)t.tsym);
        }
        return l.toList();
    }

    public static class DelegatedSymbol<T extends Symbol> extends Symbol {
        protected T other;
        public DelegatedSymbol(T other) {
            super(other.kind, other.flags_field, other.name, other.type, other.owner);
            this.other = other;
        }
        public String toString() { return other.toString(); }
        public Symbol location() { return other.location(); }
        public Symbol location(Type site, Types types) { return other.location(site, types); }
        public Symbol baseSymbol() { return other; }
        public Type erasure(Types types) { return other.erasure(types); }
        public Type externalType(Types types) { return other.externalType(types); }
        public boolean isLocal() { return other.isLocal(); }
        public boolean isConstructor() { return other.isConstructor(); }
        public Name getQualifiedName() { return other.getQualifiedName(); }
        public Name flatName() { return other.flatName(); }
        public WriteableScope members() { return other.members(); }
        public boolean isInner() { return other.isInner(); }
        public boolean hasOuterInstance() { return other.hasOuterInstance(); }
        public Symbol.ClassSymbol enclClass() { return other.enclClass(); }
        public Symbol.ClassSymbol outermostClass() { return other.outermostClass(); }
        public Symbol.PackageSymbol packge() { return other.packge(); }
        public boolean isSubClass(Symbol base, Types types) { return other.isSubClass(base, types); }
        public boolean isMemberOf(Symbol.TypeSymbol clazz, Types types) { return other.isMemberOf(clazz, types); }
        public boolean isEnclosedBy(Symbol.ClassSymbol clazz) { return other.isEnclosedBy(clazz); }
        public boolean isInheritedIn(Symbol clazz, Types types) { return other.isInheritedIn(clazz, types); }
        public Symbol asMemberOf(Type site, Types types) { return other.asMemberOf(site, types); }
        public void complete() throws Symbol.CompletionFailure { other.complete(); }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return other.accept(v, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitSymbol(other, p);
        }

        public T getUnderlyingSymbol() {
            return other;
        }
    }

    /** A base class for Symbols representing types.
     */
    public static abstract class TypeSymbol extends Symbol {
        public TypeSymbol(Kind kind, long flags, Name name, Type type, Symbol owner) {
            super(kind, flags, name, type, owner);
        }
        /** form a fully qualified name from a name and an owner
         */
        static public Name formFullName(Name name, Symbol owner) {
            if (owner == null) return name;
            if ((owner.kind != ERR) &&
                (owner.kind.matches(KindSelector.VAL_MTH) ||
                 (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
                 )) return name;
            Name prefix = owner.getQualifiedName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append('.', name);
        }

        /** form a fully qualified name from a name and an owner, after
         *  converting to flat representation
         */
        static public Name formFlatName(Name name, Symbol owner) {
            if (owner == null || owner.kind.matches(KindSelector.VAL_MTH) ||
                (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
                ) return name;
            char sep = owner.kind == TYP ? '$' : '.';
            Name prefix = owner.flatName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append(sep, name);
        }

        /**
         * A partial ordering between type symbols that refines the
         * class inheritance graph.
         *
         * Type variables always precede other kinds of symbols.
         */
        public final boolean precedes(Symbol.TypeSymbol that, Types types) {
            if (this == that)
                return false;
            if (type.hasTag(that.type.getTag())) {
                if (type.hasTag(CLASS)) {
                    return
                        types.rank(that.type) < types.rank(this.type) ||
                        types.rank(that.type) == types.rank(this.type) &&
                        that.getQualifiedName().compareTo(this.getQualifiedName()) < 0;
                } else if (type.hasTag(TYPEVAR)) {
                    return types.isSubtype(this.type, that.type);
                }
            }
            return type.hasTag(TYPEVAR);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<Symbol> getEnclosedElements() {
            JCList<Symbol> list = JCList.nil();
            if (kind == TYP && type.hasTag(TYPEVAR)) {
                return list;
            }
            for (Symbol sym : members().getSymbols(NON_RECURSIVE)) {
                try {
                    if (sym != null && (sym.flags() & SYNTHETIC) == 0 && sym.owner == this) {
                        list = list.prepend(sym);
                    }
                } catch (BadEnclosingMethodAttr badEnclosingMethod) {
                    // ignore the exception
                }
            }
            return list;
        }

        public AnnotationTypeMetadata getAnnotationTypeMetadata() {
            Assert.error("Only on ClassSymbol");
            return null; //unreachable
        }

        public boolean isAnnotationType() { return false; }

        @Override
        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitTypeSymbol(this, p);
        }
    }

    /**
     * Type variables are represented by instances of this class.
     */
    public static class TypeVariableSymbol
            extends TypeSymbol implements TypeParameterElement {

        public TypeVariableSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            return ElementKind.TYPE_PARAMETER;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Symbol getGenericElement() {
            return owner;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getBounds() {
            TypeVar t = (TypeVar)type;
            Type bound = t.getUpperBound();
            if (!bound.isCompound())
                return JCList.of(bound);
            ClassType ct = (ClassType)bound;
            if (!ct.tsym.erasure_field.isInterface()) {
                return ct.interfaces_field.prepend(ct.supertype_field);
            } else {
                // No superclass was given in bounds.
                // In this case, supertype is Object, erasure is first interface.
                return ct.interfaces_field;
            }
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Attribute.Compound> getAnnotationMirrors() {
            // Declaration annotations on type variables are stored in type attributes
            // on the owner of the TypeVariableSymbol
            JCList<Attribute.TypeCompound> candidates = owner.getRawTypeAttributes();
            int index = owner.getTypeParameters().indexOf(this);
            JCList<Attribute.Compound> res = JCList.nil();
            for (Attribute.TypeCompound a : candidates) {
                if (isCurrentSymbolsAnnotation(a, index))
                    res = res.prepend(a);
            }

            return res.reverse();
        }

        // Helper to getAnnotation[s]
        @Override
        public <A extends Annotation> Attribute.Compound getAttribute(Class<A> annoType) {
            String name = annoType.getName();

            // Declaration annotations on type variables are stored in type attributes
            // on the owner of the TypeVariableSymbol
            JCList<Attribute.TypeCompound> candidates = owner.getRawTypeAttributes();
            int index = owner.getTypeParameters().indexOf(this);
            for (Attribute.TypeCompound anno : candidates)
                if (isCurrentSymbolsAnnotation(anno, index) &&
                    name.contentEquals(anno.type.tsym.flatName()))
                    return anno;

            return null;
        }
            //where:
            boolean isCurrentSymbolsAnnotation(Attribute.TypeCompound anno, int index) {
                return (anno.position.type == TargetType.CLASS_TYPE_PARAMETER ||
                        anno.position.type == TargetType.METHOD_TYPE_PARAMETER) &&
                        anno.position.parameter_index == index;
            }


        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitTypeParameter(this, p);
        }
    }
    /** A class for module symbols.
     */
    public static class ModuleSymbol extends TypeSymbol
            implements ModuleElement {

        public Name version;
        public JavaFileManager.Location sourceLocation;
        public JavaFileManager.Location classLocation;
        public JavaFileManager.Location patchLocation;
        public JavaFileManager.Location patchOutputLocation;

        /** All directives, in natural order. */
        public JCList<com.flint.tools.flintc.code.Directive> directives;
        public JCList<com.flint.tools.flintc.code.Directive.RequiresDirective> requires;
        public JCList<com.flint.tools.flintc.code.Directive.ExportsDirective> exports;
        public JCList<com.flint.tools.flintc.code.Directive.OpensDirective> opens;
        public JCList<com.flint.tools.flintc.code.Directive.ProvidesDirective> provides;
        public JCList<com.flint.tools.flintc.code.Directive.UsesDirective> uses;

        public Symbol.ClassSymbol module_info;

        public Symbol.PackageSymbol unnamedPackage;
        public Map<Name, Symbol.PackageSymbol> visiblePackages;
        public Set<Symbol.ModuleSymbol> readModules;
        public JCList<Symbol> enclosedPackages = JCList.nil();

        public Symbol.Completer usesProvidesCompleter = Symbol.Completer.NULL_COMPLETER;
        public final Set<Symbol.ModuleFlags> flags = EnumSet.noneOf(Symbol.ModuleFlags.class);
        public final Set<Symbol.ModuleResolutionFlags> resolutionFlags = EnumSet.noneOf(Symbol.ModuleResolutionFlags.class);

        /**
         * Create a ModuleSymbol with an associated module-info ClassSymbol.
         */
        public static Symbol.ModuleSymbol create(Name name, Name module_info) {
            Symbol.ModuleSymbol msym = new Symbol.ModuleSymbol(name, null);
            Symbol.ClassSymbol info = new Symbol.ClassSymbol(Flags.MODULE, module_info, msym);
            info.fullname = formFullName(module_info, msym);
            info.flatname = info.fullname;
            info.members_field = WriteableScope.create(info);
            msym.module_info = info;
            return msym;
        }

        public ModuleSymbol(Name name, Symbol owner) {
            super(MDL, 0, name, null, owner);
            Assert.checkNonNull(name);
            this.type = new ModuleType(this);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Name getSimpleName() {
            Name fullName = getQualifiedName();
            int lastPeriod = fullName.lastIndexOf((byte)'.');
            if (lastPeriod == -1) {
                return fullName;
            } else {
                return fullName.subName(lastPeriod + 1, fullName.length());
            }
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isOpen() {
            return flags.contains(Symbol.ModuleFlags.OPEN);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isUnnamed() {
            return name.isEmpty() && owner == null;
        }

        @Override
        public boolean isDeprecated() {
            return hasDeprecatedAnnotation();
        }

        public boolean isNoModule() {
            return false;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            return ElementKind.MODULE;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<Directive> getDirectives() {
            complete();
            completeUsesProvides();
            return Collections.unmodifiableList(directives);
        }

        public void completeUsesProvides() {
            if (usesProvidesCompleter != Symbol.Completer.NULL_COMPLETER) {
                Symbol.Completer c = usesProvidesCompleter;
                usesProvidesCompleter = Symbol.Completer.NULL_COMPLETER;
                c.complete(this);
            }
        }

        @Override
        public Symbol.ClassSymbol outermostClass() {
            return null;
        }

        @Override
        public String toString() {
            // TODO: the following strings should be localized
            // Do this with custom anon subtypes in Symtab
            String n = (name == null) ? "<unknown>"
                    : (name.isEmpty()) ? "<unnamed>"
                    : String.valueOf(name);
            return n;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitModule(this, p);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Symbol> getEnclosedElements() {
            JCList<Symbol> list = JCList.nil();
            for (Symbol sym : enclosedPackages) {
                if (sym.members().anyMatch(m -> m.kind == TYP))
                    list = list.prepend(sym);
            }
            return list;
        }

        public void reset() {
            this.directives = null;
            this.requires = null;
            this.exports = null;
            this.provides = null;
            this.uses = null;
            this.visiblePackages = null;
        }

    }

    public enum ModuleFlags {
        OPEN(0x0020),
        SYNTHETIC(0x1000),
        MANDATED(0x8000);

        public static int value(Set<ModuleFlags> s) {
            int v = 0;
            for (ModuleFlags f: s)
                v |= f.value;
            return v;
        }

        private ModuleFlags(int value) {
            this.value = value;
        }

        public final int value;
    }

    public enum ModuleResolutionFlags {
        DO_NOT_RESOLVE_BY_DEFAULT(0x0001),
        WARN_DEPRECATED(0x0002),
        WARN_DEPRECATED_REMOVAL(0x0004),
        WARN_INCUBATING(0x0008);

        public static int value(Set<ModuleResolutionFlags> s) {
            int v = 0;
            for (ModuleResolutionFlags f: s)
                v |= f.value;
            return v;
        }

        private ModuleResolutionFlags(int value) {
            this.value = value;
        }

        public final int value;
    }

    /** A class for package symbols
     */
    public static class PackageSymbol extends TypeSymbol
        implements PackageElement {

        public WriteableScope members_field;
        public Name fullname;
        public Symbol.ClassSymbol package_info; // see bug 6443073
        public Symbol.ModuleSymbol modle;
        // the file containing the documentation comments for the package
        public JavaFileObject sourcefile;

        public PackageSymbol(Name name, Type type, Symbol owner) {
            super(PCK, 0, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
        }

        public PackageSymbol(Name name, Symbol owner) {
            this(name, null, owner);
            this.type = new PackageType(this);
        }

        public String toString() {
            return fullname.toString();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Name getQualifiedName() {
            return fullname;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isUnnamed() {
            return name.isEmpty() && owner != null;
        }

        public WriteableScope members() {
            complete();
            return members_field;
        }

        public long flags() {
            complete();
            return flags_field;
        }

        @Override
        public JCList<Attribute.Compound> getRawAttributes() {
            complete();
            if (package_info != null) {
                package_info.complete();
                mergeAttributes();
            }
            return super.getRawAttributes();
        }

        private void mergeAttributes() {
            if (metadata == null &&
                package_info.metadata != null) {
                metadata = new SymbolMetadata(this);
                metadata.setAttributes(package_info.metadata);
            }
        }

        /** A package "exists" if a type or package that exists has
         *  been seen within it.
         */
        public boolean exists() {
            return (flags_field & EXISTS) != 0;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Symbol getEnclosingElement() {
            return modle != null && !modle.isNoModule() ? modle : null;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitPackage(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitPackageSymbol(this, p);
        }

        /**Resets the Symbol into the state good for next round of annotation processing.*/
        public void reset() {
            metadata = null;
        }

    }

    /** A class for class symbols
     */
    public static class ClassSymbol extends TypeSymbol implements TypeElement {

        /** a scope for all class members; variables, methods and inner classes
         *  type parameters are not part of this scope
         */
        public WriteableScope members_field;

        /** the fully qualified name of the class, i.e. pck.outer.inner.
         *  null for anonymous classes
         */
        public Name fullname;

        /** the fully qualified name of the class after converting to flat
         *  representation, i.e. pck.outer$inner,
         *  set externally for local and anonymous classes
         */
        public Name flatname;

        /** the sourcefile where the class came from
         */
        public JavaFileObject sourcefile;

        /** the classfile from where to load this class
         *  this will have extension .class or .java
         */
        public JavaFileObject classfile;

        /** the list of translated local classes (used for generating
         * InnerClasses attribute)
         */
        public JCList<ClassSymbol> trans_local;

        /** the constant pool of the class
         */
        public Pool pool;

        /** the annotation metadata attached to this class */
        private AnnotationTypeMetadata annotationTypeMetadata;

        public ClassSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
            this.flatname = formFlatName(name, owner);
            this.sourcefile = null;
            this.classfile = null;
            this.pool = null;
            this.annotationTypeMetadata = AnnotationTypeMetadata.notAnAnnotationType();
        }

        public ClassSymbol(long flags, Name name, Symbol owner) {
            this(
                flags,
                name,
                new ClassType(Type.noType, null, null),
                owner);
            this.type.tsym = this;
        }

        /** The Java source which this symbol represents.
         */
        public String toString() {
            return className();
        }

        public long flags() {
            complete();
            return flags_field;
        }

        public WriteableScope members() {
            complete();
            return members_field;
        }

        @Override
        public JCList<Attribute.Compound> getRawAttributes() {
            complete();
            return super.getRawAttributes();
        }

        @Override
        public JCList<Attribute.TypeCompound> getRawTypeAttributes() {
            complete();
            return super.getRawTypeAttributes();
        }

        public Type erasure(Types types) {
            if (erasure_field == null)
                erasure_field = new ClassType(types.erasure(type.getEnclosingType()),
                                              JCList.nil(), this,
                                              type.getMetadata());
            return erasure_field;
        }

        public String className() {
            if (name.isEmpty())
                return
                    Log.getLocalizedString("anonymous.class", flatname);
            else
                return fullname.toString();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Name getQualifiedName() {
            return fullname;
        }

        public Name flatName() {
            return flatname;
        }

        public boolean isSubClass(Symbol base, Types types) {
            if (this == base) {
                return true;
            } else if ((base.flags() & INTERFACE) != 0) {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    for (JCList<Type> is = types.interfaces(t);
                         is.nonEmpty();
                         is = is.tail)
                        if (is.head.tsym.isSubClass(base, types)) return true;
            } else {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    if (t.tsym == base) return true;
            }
            return false;
        }

        /** Complete the elaboration of this symbol's definition.
         */
        public void complete() throws Symbol.CompletionFailure {
            try {
                super.complete();
            } catch (Symbol.CompletionFailure ex) {
                // quiet error recovery
                flags_field |= (PUBLIC|STATIC);
                this.type = new ErrorType(this, Type.noType);
                throw ex;
            }
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getInterfaces() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType)type;
                if (t.interfaces_field == null) // FIXME: shouldn't be null
                    t.interfaces_field = JCList.nil();
                if (t.all_interfaces_field != null)
                    return Type.getModelTypes(t.all_interfaces_field);
                return t.interfaces_field;
            } else {
                return JCList.nil();
            }
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getSuperclass() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType)type;
                if (t.supertype_field == null) // FIXME: shouldn't be null
                    t.supertype_field = Type.noType;
                // An interface has no superclass; its supertype is Object.
                return t.isInterface()
                    ? Type.noType
                    : t.supertype_field.getModelType();
            } else {
                return Type.noType;
            }
        }

        /**
         * Returns the next class to search for inherited annotations or {@code null}
         * if the next class can't be found.
         */
        private Symbol.ClassSymbol getSuperClassToSearchForAnnotations() {

            Type sup = getSuperclass();

            if (!sup.hasTag(CLASS) || sup.isErroneous())
                return null;

            return (Symbol.ClassSymbol) sup.tsym;
        }


        @Override
        protected <A extends Annotation> A[] getInheritedAnnotations(Class<A> annoType) {

            Symbol.ClassSymbol sup = getSuperClassToSearchForAnnotations();

            return sup == null ? super.getInheritedAnnotations(annoType)
                               : sup.getAnnotationsByType(annoType);
        }


        @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            long flags = flags();
            if ((flags & ANNOTATION) != 0)
                return ElementKind.ANNOTATION_TYPE;
            else if ((flags & INTERFACE) != 0)
                return ElementKind.INTERFACE;
            else if ((flags & ENUM) != 0)
                return ElementKind.ENUM;
            else
                return ElementKind.CLASS;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<Modifier> getModifiers() {
            long flags = flags();
            return Flags.asModifierSet(flags & ~DEFAULT);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public NestingKind getNestingKind() {
            complete();
            if (owner.kind == PCK)
                return NestingKind.TOP_LEVEL;
            else if (name.isEmpty())
                return NestingKind.ANONYMOUS;
            else if (owner.kind == MTH)
                return NestingKind.LOCAL;
            else
                return NestingKind.MEMBER;
        }


        @Override
        protected <A extends Annotation> Attribute.Compound getAttribute(final Class<A> annoType) {

            Attribute.Compound attrib = super.getAttribute(annoType);

            boolean inherited = annoType.isAnnotationPresent(Inherited.class);
            if (attrib != null || !inherited)
                return attrib;

            // Search supertypes
            Symbol.ClassSymbol superType = getSuperClassToSearchForAnnotations();
            return superType == null ? null
                                     : superType.getAttribute(annoType);
        }




        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitType(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitClassSymbol(this, p);
        }

        public void markAbstractIfNeeded(Types types) {
            if (types.enter.getEnv(this) != null &&
                (flags() & ENUM) != 0 && types.supertype(type).tsym == types.syms.enumSym &&
                (flags() & (FINAL | ABSTRACT)) == 0) {
                if (types.firstUnimplementedAbstract(this) != null)
                    // add the ABSTRACT flag to an enum
                    flags_field |= ABSTRACT;
            }
        }

        /**Resets the Symbol into the state good for next round of annotation processing.*/
        public void reset() {
            kind = TYP;
            erasure_field = null;
            members_field = null;
            flags_field = 0;
            if (type instanceof ClassType) {
                ClassType t = (ClassType)type;
                t.setEnclosingType(Type.noType);
                t.rank_field = -1;
                t.typarams_field = null;
                t.allparams_field = null;
                t.supertype_field = null;
                t.interfaces_field = null;
                t.all_interfaces_field = null;
            }
            clearAnnotationMetadata();
        }

        public void clearAnnotationMetadata() {
            metadata = null;
            annotationTypeMetadata = AnnotationTypeMetadata.notAnAnnotationType();
        }

        @Override
        public AnnotationTypeMetadata getAnnotationTypeMetadata() {
            return annotationTypeMetadata;
        }

        @Override
        public boolean isAnnotationType() {
            return (flags_field & Flags.ANNOTATION) != 0;
        }

        public void setAnnotationTypeMetadata(AnnotationTypeMetadata a) {
            Assert.checkNonNull(a);
            Assert.check(!annotationTypeMetadata.isMetadataForAnnotationType());
            this.annotationTypeMetadata = a;
        }
    }


    /** A class for variable symbols
     */
    public static class VarSymbol extends Symbol implements VariableElement {

        /** The variable's declaration position.
         */
        public int pos = Position.NOPOS;

        /** The variable's address. Used for different purposes during
         *  flow analysis, translation and code generation.
         *  Flow analysis:
         *    If this is a blank final or local variable, its sequence number.
         *  Translation:
         *    If this is a private field, its access number.
         *  Code generation:
         *    If this is a local variable, its logical slot number.
         */
        public int adr = -1;

        /** Construct a variable symbol, given its flags, name, type and owner.
         */
        public VarSymbol(long flags, Name name, Type type, Symbol owner) {
            super(VAR, flags, name, type, owner);
        }

        /** Clone this symbol with new owner.
         */
        public Symbol.VarSymbol clone(Symbol newOwner) {
            Symbol.VarSymbol v = new Symbol.VarSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return Symbol.VarSymbol.this;
                }
            };
            v.pos = pos;
            v.adr = adr;
            v.data = data;
//          System.out.println("clone " + v + " in " + newOwner);//DEBUG
            return v;
        }

        public String toString() {
            return name.toString();
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new Symbol.VarSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            long flags = flags();
            if ((flags & PARAMETER) != 0) {
                if (isExceptionParameter())
                    return ElementKind.EXCEPTION_PARAMETER;
                else
                    return ElementKind.PARAMETER;
            } else if ((flags & ENUM) != 0) {
                return ElementKind.ENUM_CONSTANT;
            } else if (owner.kind == TYP || owner.kind == ERR) {
                return ElementKind.FIELD;
            } else if (isResourceVariable()) {
                return ElementKind.RESOURCE_VARIABLE;
            } else {
                return ElementKind.LOCAL_VARIABLE;
            }
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitVariable(this, p);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Object getConstantValue() { // Mirror API
            return Constants.decode(getConstValue(), type);
        }

        public void setLazyConstValue(final Env<AttrContext> env,
                                      final Attr attr,
                                      final JCVariableDecl variable)
        {
            setData((Callable<Object>)() -> attr.attribLazyConstantValue(env, variable, type));
        }

        /**
         * The variable's constant value, if this is a constant.
         * Before the constant value is evaluated, it points to an
         * initializer environment.  If this is not a constant, it can
         * be used for other stuff.
         */
        private Object data;

        public boolean isExceptionParameter() {
            return data == ElementKind.EXCEPTION_PARAMETER;
        }

        public boolean isResourceVariable() {
            return data == ElementKind.RESOURCE_VARIABLE;
        }

        public Object getConstValue() {
            // TODO: Consider if getConstValue and getConstantValue can be collapsed
            if (data == ElementKind.EXCEPTION_PARAMETER ||
                data == ElementKind.RESOURCE_VARIABLE) {
                return null;
            } else if (data instanceof Callable<?>) {
                // In this case, this is a final variable, with an as
                // yet unevaluated initializer.
                Callable<?> eval = (Callable<?>)data;
                data = null; // to make sure we don't evaluate this twice.
                try {
                    data = eval.call();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
            return data;
        }

        public void setData(Object data) {
            Assert.check(!(data instanceof Env<?>), this);
            this.data = data;
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitVarSymbol(this, p);
        }
    }

    /** A class for method symbols.
     */
    public static class MethodSymbol extends Symbol implements ExecutableElement {

        /** The code of the method. */
        public Code code = null;

        /** The extra (synthetic/mandated) parameters of the method. */
        public JCList<VarSymbol> extraParams = JCList.nil();

        /** The captured local variables in an anonymous class */
        public JCList<VarSymbol> capturedLocals = JCList.nil();

        /** The parameters of the method. */
        public JCList<VarSymbol> params = null;

        /** The names of the parameters */
        public JCList<Name> savedParameterNames;

        /** For an annotation type element, its default value if any.
         *  The value is null if none appeared in the method
         *  declaration.
         */
        public Attribute defaultValue = null;

        /** Construct a method symbol, given its flags, name, type and owner.
         */
        public MethodSymbol(long flags, Name name, Type type, Symbol owner) {
            super(MTH, flags, name, type, owner);
            if (owner.type.hasTag(TYPEVAR)) Assert.error(owner + "." + name);
        }

        /** Clone this symbol with new owner.
         */
        public Symbol.MethodSymbol clone(Symbol newOwner) {
            Symbol.MethodSymbol m = new Symbol.MethodSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return Symbol.MethodSymbol.this;
                }
            };
            m.code = code;
            return m;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<Modifier> getModifiers() {
            long flags = flags();
            return Flags.asModifierSet((flags & DEFAULT) != 0 ? flags & ~ABSTRACT : flags);
        }

        /** The Java source which this symbol represents.
         */
        public String toString() {
            if ((flags() & BLOCK) != 0) {
                return owner.name.toString();
            } else {
                String s = (name == name.table.names.init)
                    ? owner.name.toString()
                    : name.toString();
                if (type != null) {
                    if (type.hasTag(FORALL))
                        s = "<" + ((ForAll)type).getTypeArguments() + ">" + s;
                    s += "(" + type.argtypes((flags() & VARARGS) != 0) + ")";
                }
                return s;
            }
        }

        public boolean isDynamic() {
            return false;
        }

        /** find a symbol that this (proxy method) symbol implements.
         *  @param    c       The class whose members are searched for
         *                    implementations
         */
        public Symbol implemented(Symbol.TypeSymbol c, Types types) {
            Symbol impl = null;
            for (JCList<Type> is = types.interfaces(c.type);
                 impl == null && is.nonEmpty();
                 is = is.tail) {
                Symbol.TypeSymbol i = is.head.tsym;
                impl = implementedIn(i, types);
                if (impl == null)
                    impl = implemented(i, types);
            }
            return impl;
        }

        public Symbol implementedIn(Symbol.TypeSymbol c, Types types) {
            Symbol impl = null;
            for (Symbol sym : c.members().getSymbolsByName(name)) {
                if (this.overrides(sym, (Symbol.TypeSymbol)owner, types, true) &&
                    // FIXME: I suspect the following requires a
                    // subst() for a parametric return type.
                    types.isSameType(type.getReturnType(),
                                     types.memberType(owner.type, sym).getReturnType())) {
                    impl = sym;
                }
            }
            return impl;
        }

        /** Will the erasure of this method be considered by the VM to
         *  override the erasure of the other when seen from class `origin'?
         */
        public boolean binaryOverrides(Symbol _other, Symbol.TypeSymbol origin, Types types) {
            if (isConstructor() || _other.kind != MTH) return false;

            if (this == _other) return true;
            Symbol.MethodSymbol other = (Symbol.MethodSymbol)_other;

            // check for a direct implementation
            if (other.isOverridableIn((Symbol.TypeSymbol)owner) &&
                types.asSuper(owner.type, other.owner) != null &&
                types.isSameType(erasure(types), other.erasure(types)))
                return true;

            // check for an inherited implementation
            return
                (flags() & ABSTRACT) == 0 &&
                other.isOverridableIn(origin) &&
                this.isMemberOf(origin, types) &&
                types.isSameType(erasure(types), other.erasure(types));
        }

        /** The implementation of this (abstract) symbol in class origin,
         *  from the VM's point of view, null if method does not have an
         *  implementation in class.
         *  @param origin   The class of which the implementation is a member.
         */
        public Symbol.MethodSymbol binaryImplementation(Symbol.ClassSymbol origin, Types types) {
            for (Symbol.TypeSymbol c = origin; c != null; c = types.supertype(c.type).tsym) {
                for (Symbol sym : c.members().getSymbolsByName(name)) {
                    if (sym.kind == MTH &&
                        ((Symbol.MethodSymbol)sym).binaryOverrides(this, origin, types))
                        return (Symbol.MethodSymbol)sym;
                }
            }
            return null;
        }

        /** Does this symbol override `other' symbol, when both are seen as
         *  members of class `origin'?  It is assumed that _other is a member
         *  of origin.
         *
         *  It is assumed that both symbols have the same name.  The static
         *  modifier is ignored for this test.
         *
         *  A quirk in the works is that if the receiver is a method symbol for
         *  an inherited abstract method we answer false summarily all else being
         *  immaterial. Abstract "own" methods (i.e `this' is a direct member of
         *  origin) don't get rejected as summarily and are put to test against the
         *  suitable criteria.
         *
         *  See JLS 8.4.6.1 (without transitivity) and 8.4.6.4
         */
        public boolean overrides(Symbol _other, Symbol.TypeSymbol origin, Types types, boolean checkResult) {
            return overrides(_other, origin, types, checkResult, true);
        }

        /** Does this symbol override `other' symbol, when both are seen as
         *  members of class `origin'?  It is assumed that _other is a member
         *  of origin.
         *
         *  Caveat: If `this' is an abstract inherited member of origin, it is
         *  deemed to override `other' only when `requireConcreteIfInherited'
         *  is false.
         *
         *  It is assumed that both symbols have the same name.  The static
         *  modifier is ignored for this test.
         *
         *  See JLS 8.4.6.1 (without transitivity) and 8.4.6.4
         */
        public boolean overrides(Symbol _other, Symbol.TypeSymbol origin, Types types, boolean checkResult,
                                 boolean requireConcreteIfInherited) {
            if (isConstructor() || _other.kind != MTH) return false;

            if (this == _other) return true;
            Symbol.MethodSymbol other = (Symbol.MethodSymbol)_other;

            // check for a direct implementation
            if (other.isOverridableIn((Symbol.TypeSymbol)owner) &&
                types.asSuper(owner.type, other.owner) != null) {
                Type mt = types.memberType(owner.type, this);
                Type ot = types.memberType(owner.type, other);
                if (types.isSubSignature(mt, ot)) {
                    if (!checkResult)
                        return true;
                    if (types.returnTypeSubstitutable(mt, ot))
                        return true;
                }
            }

            // check for an inherited implementation
            if (((flags() & ABSTRACT) != 0 && requireConcreteIfInherited) ||
                    ((other.flags() & ABSTRACT) == 0 && (other.flags() & DEFAULT) == 0) ||
                    !other.isOverridableIn(origin) ||
                    !this.isMemberOf(origin, types))
                return false;

            // assert types.asSuper(origin.type, other.owner) != null;
            Type mt = types.memberType(origin.type, this);
            Type ot = types.memberType(origin.type, other);
            return
                types.isSubSignature(mt, ot) &&
                (!checkResult || types.resultSubtype(mt, ot, types.noWarnings));
        }

        private boolean isOverridableIn(Symbol.TypeSymbol origin) {
            // JLS 8.4.6.1
            switch ((int)(flags_field & Flags.AccessFlags)) {
            case Flags.PRIVATE:
                return false;
            case Flags.PUBLIC:
                return !this.owner.isInterface() ||
                        (flags_field & STATIC) == 0;
            case Flags.PROTECTED:
                return (origin.flags() & INTERFACE) == 0;
            case 0:
                // for package private: can only override in the same
                // package
                return
                    this.packge() == origin.packge() &&
                    (origin.flags() & INTERFACE) == 0;
            default:
                return false;
            }
        }

        @Override
        public boolean isInheritedIn(Symbol clazz, Types types) {
            switch ((int)(flags_field & Flags.AccessFlags)) {
                case PUBLIC:
                    return !this.owner.isInterface() ||
                            clazz == owner ||
                            (flags_field & STATIC) == 0;
                default:
                    return super.isInheritedIn(clazz, types);
            }
        }

        public boolean isLambdaMethod() {
            return (flags() & LAMBDA_METHOD) == LAMBDA_METHOD;
        }

        /** The implementation of this (abstract) symbol in class origin;
         *  null if none exists. Synthetic methods are not considered
         *  as possible implementations.
         */
        public Symbol.MethodSymbol implementation(Symbol.TypeSymbol origin, Types types, boolean checkResult) {
            return implementation(origin, types, checkResult, implementation_filter);
        }
        // where
            public static final Filter<Symbol> implementation_filter = s ->
                    s.kind == MTH && (s.flags() & SYNTHETIC) == 0;

        public Symbol.MethodSymbol implementation(Symbol.TypeSymbol origin, Types types, boolean checkResult, Filter<Symbol> implFilter) {
            Symbol.MethodSymbol res = types.implementation(this, origin, checkResult, implFilter);
            if (res != null)
                return res;
            // if origin is derived from a raw type, we might have missed
            // an implementation because we do not know enough about instantiations.
            // in this case continue with the supertype as origin.
            if (types.isDerivedRaw(origin.type) && !origin.isInterface())
                return implementation(types.supertype(origin.type).tsym, types, checkResult);
            else
                return null;
        }

        public JCList<VarSymbol> params() {
            owner.complete();
            if (params == null) {
                // If ClassReader.saveParameterNames has been set true, then
                // savedParameterNames will be set to a list of names that
                // matches the types in type.getParameterTypes().  If any names
                // were not found in the class file, those names in the list will
                // be set to the empty name.
                // If ClassReader.saveParameterNames has been set false, then
                // savedParameterNames will be null.
                JCList<Name> paramNames = savedParameterNames;
                savedParameterNames = null;
                // discard the provided names if the list of names is the wrong size.
                if (paramNames == null || paramNames.size() != type.getParameterTypes().size()) {
                    paramNames = JCList.nil();
                }
                ListBuffer<Symbol.VarSymbol> buf = new ListBuffer<>();
                JCList<Name> remaining = paramNames;
                // assert: remaining and paramNames are both empty or both
                // have same cardinality as type.getParameterTypes()
                int i = 0;
                for (Type t : type.getParameterTypes()) {
                    Name paramName;
                    if (remaining.isEmpty()) {
                        // no names for any parameters available
                        paramName = createArgName(i, paramNames);
                    } else {
                        paramName = remaining.head;
                        remaining = remaining.tail;
                        if (paramName.isEmpty()) {
                            // no name for this specific parameter
                            paramName = createArgName(i, paramNames);
                        }
                    }
                    buf.append(new Symbol.VarSymbol(PARAMETER, paramName, t, this));
                    i++;
                }
                params = buf.toList();
            }
            return params;
        }

        // Create a name for the argument at position 'index' that is not in
        // the exclude list. In normal use, either no names will have been
        // provided, in which case the exclude list is empty, or all the names
        // will have been provided, in which case this method will not be called.
        private Name createArgName(int index, JCList<Name> exclude) {
            String prefix = "arg";
            while (true) {
                Name argName = name.table.fromString(prefix + index);
                if (!exclude.contains(argName))
                    return argName;
                prefix += "$";
            }
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new Symbol.MethodSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public ElementKind getKind() {
            if (name == name.table.names.init)
                return ElementKind.CONSTRUCTOR;
            else if (name == name.table.names.clinit)
                return ElementKind.STATIC_INIT;
            else if ((flags() & BLOCK) != 0)
                return isStatic() ? ElementKind.STATIC_INIT : ElementKind.INSTANCE_INIT;
            else
                return ElementKind.METHOD;
        }

        public boolean isStaticOrInstanceInit() {
            return getKind() == ElementKind.STATIC_INIT ||
                    getKind() == ElementKind.INSTANCE_INIT;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Attribute getDefaultValue() {
            return defaultValue;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<VarSymbol> getParameters() {
            return params();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isVarArgs() {
            return (flags() & VARARGS) != 0;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isDefault() {
            return (flags() & DEFAULT) != 0;
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitMethodSymbol(this, p);
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getReceiverType() {
            return asType().getReceiverType();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public Type getReturnType() {
            return asType().getReturnType();
        }

        @DefinedBy(Api.LANGUAGE_MODEL)
        public JCList<Type> getThrownTypes() {
            return asType().getThrownTypes();
        }
    }

    /** A class for invokedynamic method calls.
     */
    public static class DynamicMethodSymbol extends MethodSymbol {

        public Object[] staticArgs;
        public Symbol bsm;
        public int bsmKind;

        public DynamicMethodSymbol(Name name, Symbol owner, int bsmKind, Symbol.MethodSymbol bsm, Type type, Object[] staticArgs) {
            super(0, name, type, owner);
            this.bsm = bsm;
            this.bsmKind = bsmKind;
            this.staticArgs = staticArgs;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    /** A class for predefined operators.
     */
    public static class OperatorSymbol extends MethodSymbol {

        public int opcode;
        private int accessCode = Integer.MIN_VALUE;

        public OperatorSymbol(Name name, Type type, int opcode, Symbol owner) {
            super(PUBLIC | STATIC, name, type, owner);
            this.opcode = opcode;
        }

        @Override
        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitOperatorSymbol(this, p);
        }

        public int getAccessCode(JCTreeTag tag) {
            if (accessCode != Integer.MIN_VALUE && !tag.isIncOrDecUnaryOp()) {
                return accessCode;
            }
            accessCode = AccessCode.from(tag, opcode);
            return accessCode;
        }

        /** Access codes for dereferencing, assignment,
         *  and pre/post increment/decrement.

         *  All access codes for accesses to the current class are even.
         *  If a member of the superclass should be accessed instead (because
         *  access was via a qualified super), add one to the corresponding code
         *  for the current class, making the number odd.
         *  This numbering scheme is used by the backend to decide whether
         *  to issue an invokevirtual or invokespecial call.
         *
         *  @see Gen#visitSelect(JCFieldAccess tree)
         */
        public enum AccessCode {
            UNKNOWN(-1, JCTreeTag.NO_TAG),
            DEREF(0, JCTreeTag.NO_TAG),
            ASSIGN(2, JCTreeTag.ASSIGN),
            PREINC(4, JCTreeTag.PREINC),
            PREDEC(6, JCTreeTag.PREDEC),
            POSTINC(8, JCTreeTag.POSTINC),
            POSTDEC(10, JCTreeTag.POSTDEC),
            FIRSTASGOP(12, JCTreeTag.NO_TAG);

            public final int code;
            public final JCTreeTag tag;
            public static final int numberOfAccessCodes = (lushrl - ishll + lxor + 2 - iadd) * 2 + FIRSTASGOP.code + 2;

            AccessCode(int code, JCTreeTag tag) {
                this.code = code;
                this.tag = tag;
            }

            static public AccessCode getFromCode(int code) {
                for (AccessCode aCodes : AccessCode.values()) {
                    if (aCodes.code == code) {
                        return aCodes;
                    }
                }
                return UNKNOWN;
            }

            static int from(JCTreeTag tag, int opcode) {
                /** Map bytecode of binary operation to access code of corresponding
                *  assignment operation. This is always an even number.
                */
                switch (tag) {
                    case PREINC:
                        return AccessCode.PREINC.code;
                    case PREDEC:
                        return AccessCode.PREDEC.code;
                    case POSTINC:
                        return AccessCode.POSTINC.code;
                    case POSTDEC:
                        return AccessCode.POSTDEC.code;
                }
                if (iadd <= opcode && opcode <= lxor) {
                    return (opcode - iadd) * 2 + FIRSTASGOP.code;
                } else if (opcode == string_add) {
                    return (lxor + 1 - iadd) * 2 + FIRSTASGOP.code;
                } else if (ishll <= opcode && opcode <= lushrl) {
                    return (opcode - ishll + lxor + 2 - iadd) * 2 + FIRSTASGOP.code;
                }
                return -1;
            }
        }
    }

    /** Symbol completer interface.
     */
    public static interface Completer {

        /** Dummy completer to be used when the symbol has been completed or
         * does not need completion.
         */
        public final static Completer NULL_COMPLETER = new Completer() {
            public void complete(Symbol sym) { }
            public boolean isTerminal() { return true; }
        };

        void complete(Symbol sym) throws CompletionFailure;

        /** Returns true if this completer is <em>terminal</em>. A terminal
         * completer is used as a place holder when the symbol is completed.
         * Calling complete on a terminal completer will not affect the symbol.
         *
         * The dummy NULL_COMPLETER and the GraphDependencies completer are
         * examples of terminal completers.
         *
         * @return true iff this completer is terminal
         */
        default boolean isTerminal() {
            return false;
        }
    }

    public static class CompletionFailure extends RuntimeException {
        private static final long serialVersionUID = 0;
        public Symbol sym;

        /** A diagnostic object describing the failure
         */
        public JCDiagnostic diag;

        /** A localized string describing the failure.
         * @deprecated Use {@code getDetail()} or {@code getMessage()}
         */
        @Deprecated
        public String errmsg;

        public CompletionFailure(Symbol sym, String errmsg) {
            this.sym = sym;
            this.errmsg = errmsg;
//          this.printStackTrace();//DEBUG
        }

        public CompletionFailure(Symbol sym, JCDiagnostic diag) {
            this.sym = sym;
            this.diag = diag;
//          this.printStackTrace();//DEBUG
        }

        public JCDiagnostic getDiagnostic() {
            return diag;
        }

        @Override
        public String getMessage() {
            if (diag != null)
                return diag.getMessage(null);
            else
                return errmsg;
        }

        public Object getDetailValue() {
            return (diag != null ? diag : errmsg);
        }

        @Override
        public CompletionFailure initCause(Throwable cause) {
            super.initCause(cause);
            return this;
        }

    }

    /**
     * A visitor for symbols.  A visitor is used to implement operations
     * (or relations) on symbols.  Most common operations on types are
     * binary relations and this interface is designed for binary
     * relations, that is, operations on the form
     * Symbol&nbsp;&times;&nbsp;P&nbsp;&rarr;&nbsp;R.
     * <!-- In plain text: Type x P -> R -->
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <P> the type of the second argument (the first being the
     * symbol itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public interface Visitor<R,P> {
        R visitClassSymbol(ClassSymbol s, P arg);
        R visitMethodSymbol(MethodSymbol s, P arg);
        R visitPackageSymbol(PackageSymbol s, P arg);
        R visitOperatorSymbol(OperatorSymbol s, P arg);
        R visitVarSymbol(VarSymbol s, P arg);
        R visitTypeSymbol(TypeSymbol s, P arg);
        R visitSymbol(Symbol s, P arg);
    }
}
