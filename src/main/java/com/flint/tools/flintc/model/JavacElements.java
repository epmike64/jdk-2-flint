/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

import static javax.lang.model.util.ElementFilter.methodsIn;

import com.flint.tools.flintc.api.JavacTaskImpl;
import com.flint.tools.flintc.code.Attribute;
import com.flint.tools.flintc.code.Flags;
import com.flint.tools.flintc.code.Source;
import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Symtab;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.Types;
import com.flint.tools.flintc.comp.*;
import com.flint.tools.flintc.main.JavaCompiler;
import com.flint.tools.flintc.processing.PrintingProcessor;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.tree.TreeInfo;
import com.flint.tools.flintc.tree.TreeScanner;
import com.flint.source.util.JavacTask;
import com.flint.tools.flintc.code.Attribute.Compound;
import com.flint.tools.flintc.code.Directive.ExportsDirective;
import com.flint.tools.flintc.code.Directive.ExportsFlag;
import com.flint.tools.flintc.code.Directive.OpensDirective;
import com.flint.tools.flintc.code.Directive.OpensFlag;
import com.flint.tools.flintc.code.Directive.RequiresDirective;
import com.flint.tools.flintc.code.Directive.RequiresFlag;
import com.flint.tools.flintc.code.Scope.WriteableScope;
import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.tree.JCTree.*;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.Name;
import static com.flint.tools.flintc.code.Kinds.Kind.*;
import static com.flint.tools.flintc.code.Scope.LookupKind.NON_RECURSIVE;
import static com.flint.tools.flintc.code.TypeTag.CLASS;

import com.flint.tools.flintc.resources.CompilerProperties.Notes;
import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.*;

/**
 * Utility methods for operating on program elements.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class JavacElements implements Elements {

    private final JavaCompiler javaCompiler;
    private final Symtab syms;
    private final Modules modules;
    private final Names names;
    private final Types types;
    private final Enter enter;
    private final Resolve resolve;
    private final JavacTaskImpl javacTaskImpl;
    private final Log log;
    private final boolean allowModules;

    public static JavacElements instance(Context context) {
        JavacElements instance = context.get(JavacElements.class);
        if (instance == null)
            instance = new JavacElements(context);
        return instance;
    }

    protected JavacElements(Context context) {
        context.put(JavacElements.class, this);
        javaCompiler = JavaCompiler.instance(context);
        syms = Symtab.instance(context);
        modules = Modules.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        enter = Enter.instance(context);
        resolve = Resolve.instance(context);
        JavacTask t = context.get(JavacTask.class);
        javacTaskImpl = t instanceof JavacTaskImpl ? (JavacTaskImpl) t : null;
        log = Log.instance(context);
        Source source = Source.instance(context);
        allowModules = source.allowModules();
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Set<? extends ModuleElement> getAllModuleElements() {
        if (allowModules)
            return Collections.unmodifiableSet(modules.allModules());
        else
            return Collections.emptySet();
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public ModuleSymbol getModuleElement(CharSequence name) {
        ensureEntered("getModuleElement");
        if (modules.getDefaultModule() == syms.noModule)
            return null;
        String strName = name.toString();
        if (strName.equals(""))
            return syms.unnamedModule;
        return modules.getObservableModule(names.fromString(strName));
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public PackageSymbol getPackageElement(CharSequence name) {
        return doGetPackageElement(null, name);
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public PackageSymbol getPackageElement(ModuleElement module, CharSequence name) {
        module.getClass();
        return doGetPackageElement(module, name);
    }

    private PackageSymbol doGetPackageElement(ModuleElement module, CharSequence name) {
        ensureEntered("getPackageElement");
        return doGetElement(module, "getPackageElement", name, PackageSymbol.class);
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public ClassSymbol getTypeElement(CharSequence name) {
        return doGetTypeElement(null, name);
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public ClassSymbol getTypeElement(ModuleElement module, CharSequence name) {
        module.getClass();

        return doGetTypeElement(module, name);
    }

    private ClassSymbol doGetTypeElement(ModuleElement module, CharSequence name) {
        ensureEntered("getTypeElement");
        return doGetElement(module, "getTypeElement", name, ClassSymbol.class);
    }

    private <S extends Symbol> S doGetElement(ModuleElement module, String methodName,
                                              CharSequence name, Class<S> clazz) {
        String strName = name.toString();
        if (!SourceVersion.isName(strName) && (!strName.isEmpty() || clazz == ClassSymbol.class)) {
            return null;
        }
        if (module == null) {
            return unboundNameToSymbol(methodName, strName, clazz);
        } else {
            return nameToSymbol((ModuleSymbol) module, strName, clazz);
        }
    }

    private final Set<String> alreadyWarnedDuplicates = new HashSet<>();

    private <S extends Symbol> S unboundNameToSymbol(String methodName,
                                                     String nameStr,
                                                     Class<S> clazz) {
        if (modules.getDefaultModule() == syms.noModule) { //not a modular mode:
            return nameToSymbol(syms.noModule, nameStr, clazz);
        }

        Set<S> found = new LinkedHashSet<>();

        for (ModuleSymbol msym : modules.allModules()) {
            S sym = nameToSymbol(msym, nameStr, clazz);

            if (sym != null) {
                if (!allowModules || clazz == ClassSymbol.class || !sym.members().isEmpty()) {
                    //do not add packages without members:
                    found.add(sym);
                }
            }
        }

        if (found.size() == 1) {
            return found.iterator().next();
        } else if (found.size() > 1) {
            //more than one element found, produce a note:
            if (alreadyWarnedDuplicates.add(methodName + ":" + nameStr)) {
                String moduleNames = found.stream()
                                          .map(s -> s.packge().modle)
                                          .map(m -> m.toString())
                                          .collect(Collectors.joining(", "));
                log.note(Notes.MultipleElements(methodName, nameStr, moduleNames));
            }
            return null;
        } else {
            //not found, or more than one element found:
            return null;
        }
    }

    /**
     * Returns a symbol given the type's or package's canonical name,
     * or null if the name isn't found.
     */
    private <S extends Symbol> S nameToSymbol(ModuleSymbol module, String nameStr, Class<S> clazz) {
        Name name = names.fromString(nameStr);
        // First check cache.
        Symbol sym = (clazz == ClassSymbol.class)
                    ? syms.getClass(module, name)
                    : syms.lookupPackage(module, name);

        try {
            if (sym == null)
                sym = javaCompiler.resolveIdent(module, nameStr);

            sym.complete();

            return (sym.kind != ERR &&
                    sym.exists() &&
                    clazz.isInstance(sym) &&
                    name.equals(sym.getQualifiedName()))
                ? clazz.cast(sym)
                : null;
        } catch (CompletionFailure e) {
            return null;
        }
    }

    /**
     * Returns the tree for an annotation given the annotated element
     * and the element's own tree.  Returns null if the tree cannot be found.
     */
    private JCTree matchAnnoToTree(AnnotationMirror findme,
                                   Element e, JCTree tree) {
        Symbol sym = cast(Symbol.class, e);
        class Vis extends JCTree.Visitor {
            JCList<JCAnnotation> result = null;
            public void visitPackageDef(JCPackageDecl tree) {
                result = tree.annotations;
            }
            public void visitClassDef(JCClassDecl tree) {
                result = tree.mods.annotations;
            }
            public void visitMethodDef(JCMethodDecl tree) {
                result = tree.mods.annotations;
            }
            public void visitVarDef(JCVariableDecl tree) {
                result = tree.mods.annotations;
            }
            @Override
            public void visitTypeParameter(JCTypeParameter tree) {
                result = tree.annotations;
            }
        }
        Vis vis = new Vis();
        tree.accept(vis);
        if (vis.result == null)
            return null;

        JCList<Compound> annos = sym.getAnnotationMirrors();
        return matchAnnoToTree(cast(Attribute.Compound.class, findme),
                               annos,
                               vis.result);
    }

    /**
     * Returns the tree for an annotation given a list of annotations
     * in which to search (recursively) and their corresponding trees.
     * Returns null if the tree cannot be found.
     */
    private JCTree matchAnnoToTree(Attribute.Compound findme,
                                   JCList<Compound> annos,
                                   JCList<JCAnnotation> trees) {
        for (Attribute.Compound anno : annos) {
            for (JCAnnotation tree : trees) {
                if (tree.type.tsym != anno.type.tsym)
                    continue;
                JCTree match = matchAttributeToTree(findme, anno, tree);
                if (match != null)
                    return match;
            }
        }
        return null;
    }

    /**
     * Returns the tree for an attribute given an enclosing attribute to
     * search (recursively) and the enclosing attribute's corresponding tree.
     * Returns null if the tree cannot be found.
     */
    private JCTree matchAttributeToTree(final Attribute findme,
                                        final Attribute attr,
                                        final JCTree tree) {
        if (attr == findme)
            return tree;

        class Vis implements Attribute.Visitor {
            JCTree result = null;
            public void visitConstant(Attribute.Constant value) {
            }
            public void visitClass(Attribute.Class clazz) {
            }
            public void visitCompound(Attribute.Compound anno) {
                for (Pair<MethodSymbol, Attribute> pair : anno.values) {
                    JCExpression expr = scanForAssign(pair.fst, tree);
                    if (expr != null) {
                        JCTree match = matchAttributeToTree(findme, pair.snd, expr);
                        if (match != null) {
                            result = match;
                            return;
                        }
                    }
                }
            }
            public void visitArray(Attribute.Array array) {
                if (tree.hasTag(NEWARRAY)) {
                    JCList<JCExpression> elems = ((JCNewArray)tree).elems;
                    for (Attribute value : array.values) {
                        JCTree match = matchAttributeToTree(findme, value, elems.head);
                        if (match != null) {
                            result = match;
                            return;
                        }
                        elems = elems.tail;
                    }
                } else if (array.values.length == 1) {
                    // the tree may not be a NEWARRAY for single-element array initializers
                    result = matchAttributeToTree(findme, array.values[0], tree);
                }
            }
            public void visitEnum(Attribute.Enum e) {
            }
            public void visitError(Attribute.Error e) {
            }
        }
        Vis vis = new Vis();
        attr.accept(vis);
        return vis.result;
    }

    /**
     * Scans for a JCAssign node with a LHS matching a given
     * symbol, and returns its RHS.  Does not scan nested JCAnnotations.
     */
    private JCExpression scanForAssign(final MethodSymbol sym,
                                       final JCTree tree) {
        class TS extends TreeScanner {
            JCExpression result = null;
            public void scan(JCTree t) {
                if (t != null && result == null)
                    t.accept(this);
            }
            public void visitAnnotation(JCAnnotation t) {
                if (t == tree)
                    scan(t.args);
            }
            public void visitAssign(JCAssign t) {
                if (t.lhs.hasTag(IDENT)) {
                    JCIdent ident = (JCIdent) t.lhs;
                    if (ident.sym == sym)
                        result = t.rhs;
                }
            }
        }
        TS scanner = new TS();
        tree.accept(scanner);
        return scanner.result;
    }

    /**
     * Returns the tree node corresponding to this element, or null
     * if none can be found.
     */
    public JCTree getTree(Element e) {
        Pair<JCTree, ?> treeTop = getTreeAndTopLevel(e);
        return (treeTop != null) ? treeTop.fst : null;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public String getDocComment(Element e) {
        // Our doc comment is contained in a map in our toplevel,
        // indexed by our tree.  Find our enter environment, which gives
        // us our toplevel.  It also gives us a tree that contains our
        // tree:  walk it to find our tree.  This is painful.
        Pair<JCTree, JCCompilationUnit> treeTop = getTreeAndTopLevel(e);
        if (treeTop == null)
            return null;
        JCTree tree = treeTop.fst;
        JCCompilationUnit toplevel = treeTop.snd;
//        if (toplevel.docComments == null)
//            return null;
//        return toplevel.docComments.getCommentText(tree);
        return "";
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public PackageElement getPackageOf(Element e) {
        return cast(Symbol.class, e).packge();
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public ModuleElement getModuleOf(Element e) {
        Symbol sym = cast(Symbol.class, e);
        if (modules.getDefaultModule() == syms.noModule)
            return null;
        return (sym.kind == MDL) ? ((ModuleElement) e) : sym.packge().modle;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public boolean isDeprecated(Element e) {
        Symbol sym = cast(Symbol.class, e);
        sym.complete();
        return sym.isDeprecated();
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Origin getOrigin(Element e) {
        Symbol sym = cast(Symbol.class, e);
        if ((sym.flags() & Flags.GENERATEDCONSTR) != 0)
            return Origin.MANDATED;
        //TypeElement.getEnclosedElements does not return synthetic elements,
        //and most synthetic elements are not read from the classfile anyway:
        return Origin.EXPLICIT;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Origin getOrigin(AnnotatedConstruct c, AnnotationMirror a) {
        Compound ac = cast(Compound.class, a);
        if (ac.isSynthesized())
            return Origin.MANDATED;
        return Origin.EXPLICIT;
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public Origin getOrigin(ModuleElement m, ModuleElement.Directive directive) {
        switch (directive.getKind()) {
            case REQUIRES:
                RequiresDirective rd = cast(RequiresDirective.class, directive);
                if (rd.flags.contains(RequiresFlag.MANDATED))
                    return Origin.MANDATED;
                if (rd.flags.contains(RequiresFlag.SYNTHETIC))
                    return Origin.SYNTHETIC;
                return Origin.EXPLICIT;
            case EXPORTS:
                ExportsDirective ed = cast(ExportsDirective.class, directive);
                if (ed.flags.contains(ExportsFlag.MANDATED))
                    return Origin.MANDATED;
                if (ed.flags.contains(ExportsFlag.SYNTHETIC))
                    return Origin.SYNTHETIC;
                return Origin.EXPLICIT;
            case OPENS:
                OpensDirective od = cast(OpensDirective.class, directive);
                if (od.flags.contains(OpensFlag.MANDATED))
                    return Origin.MANDATED;
                if (od.flags.contains(OpensFlag.SYNTHETIC))
                    return Origin.SYNTHETIC;
                return Origin.EXPLICIT;
        }
        return Origin.EXPLICIT;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Name getBinaryName(TypeElement type) {
        return cast(TypeSymbol.class, type).flatName();
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Map<MethodSymbol, Attribute> getElementValuesWithDefaults(
                                                        AnnotationMirror a) {
        Attribute.Compound anno = cast(Attribute.Compound.class, a);
        DeclaredType annotype = a.getAnnotationType();
        Map<MethodSymbol, Attribute> valmap = anno.getElementValues();

        for (ExecutableElement ex :
                 methodsIn(annotype.asElement().getEnclosedElements())) {
            MethodSymbol meth = (MethodSymbol) ex;
            Attribute defaultValue = meth.getDefaultValue();
            if (defaultValue != null && !valmap.containsKey(meth)) {
                valmap.put(meth, defaultValue);
            }
        }
        return valmap;
    }

    /**
     * {@inheritDoc}
     */
    @DefinedBy(Api.LANGUAGE_MODEL)
    public FilteredMemberList getAllMembers(TypeElement element) {
        Symbol sym = cast(Symbol.class, element);
        WriteableScope scope = sym.members().dupUnshared();
        JCList<Type> closure = types.closure(sym.asType());
        for (Type t : closure)
            addMembers(scope, t);
        return new FilteredMemberList(scope);
    }
    // where
        private void addMembers(WriteableScope scope, Type type) {
            members:
            for (Symbol e : type.asElement().members().getSymbols(NON_RECURSIVE)) {
                for (Symbol overrider : scope.getSymbolsByName(e.getSimpleName())) {
                    if (overrider.kind == e.kind && (overrider.flags() & Flags.SYNTHETIC) == 0) {
                        if (overrider.getKind() == ElementKind.METHOD &&
                                overrides((ExecutableElement)overrider, (ExecutableElement)e, (TypeElement)type.asElement())) {
                            continue members;
                        }
                    }
                }
                boolean derived = e.getEnclosingElement() != scope.owner;
                ElementKind kind = e.getKind();
                boolean initializer = kind == ElementKind.CONSTRUCTOR
                    || kind == ElementKind.INSTANCE_INIT
                    || kind == ElementKind.STATIC_INIT;
                if (!derived || (!initializer && e.isInheritedIn(scope.owner, types)))
                    scope.enter(e);
            }
        }

    /**
     * Returns all annotations of an element, whether
     * inherited or directly present.
     *
     * @param e  the element being examined
     * @return all annotations of the element
     */
    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public JCList<Compound> getAllAnnotationMirrors(Element e) {
        Symbol sym = cast(Symbol.class, e);
        JCList<Compound> annos = sym.getAnnotationMirrors();
        while (sym.getKind() == ElementKind.CLASS) {
            Type sup = ((ClassSymbol) sym).getSuperclass();
            if (!sup.hasTag(CLASS) || sup.isErroneous() ||
                    sup.tsym == syms.objectType.tsym) {
                break;
            }
            sym = sup.tsym;
            JCList<Compound> oldAnnos = annos;
            JCList<Compound> newAnnos = sym.getAnnotationMirrors();
            for (Attribute.Compound anno : newAnnos) {
                if (isInherited(anno.type) &&
                        !containsAnnoOfType(oldAnnos, anno.type)) {
                    annos = annos.prepend(anno);
                }
            }
        }
        return annos;
    }

    /**
     * Tests whether an annotation type is @Inherited.
     */
    private boolean isInherited(Type annotype) {
        return annotype.tsym.attribute(syms.inheritedType.tsym) != null;
    }

    /**
     * Tests whether a list of annotations contains an annotation
     * of a given type.
     */
    private static boolean containsAnnoOfType(JCList<Compound> annos,
                                              Type type) {
        for (Attribute.Compound anno : annos) {
            if (anno.type.tsym == type.tsym)
                return true;
        }
        return false;
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public boolean hides(Element hiderEl, Element hideeEl) {
        Symbol hider = cast(Symbol.class, hiderEl);
        Symbol hidee = cast(Symbol.class, hideeEl);

        // Fields only hide fields; methods only methods; types only types.
        // Names must match.  Nothing hides itself (just try it).
        if (hider == hidee ||
                hider.kind != hidee.kind ||
                hider.name != hidee.name) {
            return false;
        }

        // Only static methods can hide other methods.
        // Methods only hide methods with matching signatures.
        if (hider.kind == MTH) {
            if (!hider.isStatic() ||
                        !types.isSubSignature(hider.type, hidee.type)) {
                return false;
            }
        }

        // Hider must be in a subclass of hidee's class.
        // Note that if M1 hides M2, and M2 hides M3, and M3 is accessible
        // in M1's class, then M1 and M2 both hide M3.
        ClassSymbol hiderClass = hider.owner.enclClass();
        ClassSymbol hideeClass = hidee.owner.enclClass();
        if (hiderClass == null || hideeClass == null ||
                !hiderClass.isSubClass(hideeClass, types)) {
            return false;
        }

        // Hidee must be accessible in hider's class.
        // The method isInheritedIn is poorly named:  it checks only access.
        return hidee.isInheritedIn(hiderClass, types);
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public boolean overrides(ExecutableElement riderEl,
                             ExecutableElement rideeEl, TypeElement typeEl) {
        MethodSymbol rider = cast(MethodSymbol.class, riderEl);
        MethodSymbol ridee = cast(MethodSymbol.class, rideeEl);
        ClassSymbol origin = cast(ClassSymbol.class, typeEl);

        return rider.name == ridee.name &&

               // not reflexive as per JLS
               rider != ridee &&

               // we don't care if ridee is static, though that wouldn't
               // compile
               !rider.isStatic() &&

               // Symbol.overrides assumes the following
               ridee.isMemberOf(origin, types) &&

               // check access and signatures; don't check return types
               rider.overrides(ridee, origin, types, false);
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public String getConstantExpression(Object value) {
        return Constants.format(value);
    }

    /**
     * Print a representation of the elements to the given writer in
     * the specified order.  The main purpose of this method is for
     * diagnostics.  The exact format of the output is <em>not</em>
     * specified and is subject to change.
     *
     * @param w the writer to print the output to
     * @param elements the elements to print
     */
    @DefinedBy(Api.LANGUAGE_MODEL)
    public void printElements(java.io.Writer w, Element... elements) {
        for (Element element : elements)
            (new PrintingProcessor.PrintingElementVisitor(w, this)).visit(element).flush();
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    public Name getName(CharSequence cs) {
        return names.fromString(cs.toString());
    }

    @Override @DefinedBy(Api.LANGUAGE_MODEL)
    public boolean isFunctionalInterface(TypeElement element) {
        if (element.getKind() != ElementKind.INTERFACE)
            return false;
        else {
            TypeSymbol tsym = cast(TypeSymbol.class, element);
            return types.isFunctionalInterface(tsym);
        }
    }

    /**
     * Returns the tree node and compilation unit corresponding to this
     * element, or null if they can't be found.
     */
    private Pair<JCTree, JCCompilationUnit> getTreeAndTopLevel(Element e) {
        Symbol sym = cast(Symbol.class, e);
        Env<AttrContext> enterEnv = getEnterEnv(sym);
        if (enterEnv == null)
            return null;
        JCTree tree = TreeInfo.declarationFor(sym, enterEnv.tree);
        if (tree == null || enterEnv.toplevel == null)
            return null;
        return new Pair<>(tree, enterEnv.toplevel);
    }

    /**
     * Returns the best approximation for the tree node and compilation unit
     * corresponding to the given element, annotation and value.
     * If the element is null, null is returned.
     * If the annotation is null or cannot be found, the tree node and
     * compilation unit for the element is returned.
     * If the annotation value is null or cannot be found, the tree node and
     * compilation unit for the annotation is returned.
     */
    public Pair<JCTree, JCCompilationUnit> getTreeAndTopLevel(
                      Element e, AnnotationMirror a, AnnotationValue v) {
        if (e == null)
            return null;

        Pair<JCTree, JCCompilationUnit> elemTreeTop = getTreeAndTopLevel(e);
        if (elemTreeTop == null)
            return null;

        if (a == null)
            return elemTreeTop;

        JCTree annoTree = matchAnnoToTree(a, e, elemTreeTop.fst);
        if (annoTree == null)
            return elemTreeTop;

        if (v == null)
            return new Pair<>(annoTree, elemTreeTop.snd);

        JCTree valueTree = matchAttributeToTree(
                cast(Attribute.class, v), cast(Attribute.class, a), annoTree);
        if (valueTree == null)
            return new Pair<>(annoTree, elemTreeTop.snd);

        return new Pair<>(valueTree, elemTreeTop.snd);
    }

    /**
     * Returns a symbol's enter environment, or null if it has none.
     */
    private Env<AttrContext> getEnterEnv(Symbol sym) {
        // Get enclosing class of sym, or sym itself if it is a class
        // package, or module.
        TypeSymbol ts = null;
        switch (sym.kind) {
            case PCK:
                ts = (PackageSymbol)sym;
                break;
            case MDL:
                ts = (ModuleSymbol)sym;
                break;
            default:
                ts = sym.enclClass();
        }
        return (ts != null)
                ? enter.getEnv(ts)
                : null;
    }

    private void ensureEntered(String methodName) {
        if (javacTaskImpl != null) {
            javacTaskImpl.ensureEntered();
        }
        if (!javaCompiler.isEnterDone()) {
            throw new IllegalStateException("Cannot use Elements." + methodName + " before the TaskEvent.Kind.ENTER finished event.");
        }
    }

    /**
     * Returns an object cast to the specified type.
     * @throws NullPointerException if the object is {@code null}
     * @throws IllegalArgumentException if the object is of the wrong type
     */
    private static <T> T cast(Class<T> clazz, Object o) {
        if (! clazz.isInstance(o))
            throw new IllegalArgumentException(o.toString());
        return clazz.cast(o);
    }
}
