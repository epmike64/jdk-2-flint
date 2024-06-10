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

package com.flint.tools.flintc.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.BreakIterator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingFileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import com.flint.tools.flintc.code.*;
import com.flint.tools.flintc.comp.*;
import com.flint.tools.flintc.file.BaseFileManager;
import com.flint.tools.flintc.model.JavacElements;
import com.flint.tools.flintc.parser.Tokens;
import com.flint.tools.flintc.processing.JavacProcessingEnvironment;
import com.flint.tools.flintc.tree.*;
import com.flint.source.doctree.DocCommentTree;
import com.flint.source.doctree.DocTree;
import com.flint.source.tree.CatchTree;
import com.flint.source.tree.CompilationUnitTree;
import com.flint.source.tree.Scope;
import com.flint.source.tree.Tree;
import com.flint.source.util.DocSourcePositions;
import com.flint.source.util.DocTreePath;
import com.flint.source.util.DocTreeScanner;
import com.flint.source.util.DocTrees;
import com.flint.source.util.JavacTask;
import com.flint.source.util.TreePath;
import com.flint.tools.flintc.code.Scope.NamedImportScope;
import com.flint.tools.flintc.code.Scope.StarImportScope;
import com.flint.tools.flintc.code.Scope.WriteableScope;
import com.flint.tools.flintc.code.Symbol.ClassSymbol;
import com.flint.tools.flintc.code.Symbol.MethodSymbol;
import com.flint.tools.flintc.code.Symbol.ModuleSymbol;
import com.flint.tools.flintc.code.Symbol.PackageSymbol;
import com.flint.tools.flintc.code.Symbol.TypeSymbol;
import com.flint.tools.flintc.code.Symbol.VarSymbol;
import com.flint.tools.flintc.code.Type.ArrayType;
import com.flint.tools.flintc.code.Type.ClassType;
import com.flint.tools.flintc.code.Type.ErrorType;
import com.flint.tools.flintc.code.Type.UnionClassType;
import com.flint.tools.flintc.code.Types.TypeRelation;
import com.flint.tools.flintc.parser.DocCommentParser;
import com.flint.tools.flintc.parser.ParserFactory;
import com.flint.tools.flintc.parser.Tokens.Comment;
import com.flint.tools.flintc.tree.JCTree.JCBlock;
import com.flint.tools.flintc.tree.JCTree.JCCatch;
import com.flint.tools.flintc.tree.JCTree.JCClassDecl;
import com.flint.tools.flintc.tree.JCTree.JCCompilationUnit;
import com.flint.tools.flintc.tree.JCTree.JCExpression;
import com.flint.tools.flintc.tree.JCTree.JCIdent;
import com.flint.tools.flintc.tree.JCTree.JCMethodDecl;
import com.flint.tools.flintc.tree.JCTree.JCVariableDecl;
import com.flint.tools.flintc.util.Abort;
import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.DiagnosticSource;
import com.flint.tools.flintc.util.JCDiagnostic;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticFlag;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.ListBuffer;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.Name;
import com.flint.tools.flintc.util.Names;
import com.flint.tools.flintc.util.Pair;
import com.flint.tools.flintc.util.Position;

import static com.flint.tools.flintc.code.Kinds.Kind.*;
import static com.flint.tools.flintc.code.TypeTag.*;
import com.flint.tools.flintc.tree.DCTree.DCParam;
import com.flint.tools.flintc.tree.DCTree.DCDocComment;
/**
 * Provides an implementation of Trees.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah&eacute;
 */
public class JavacTrees extends DocTrees {

    // in a world of a single context per compilation, these would all be final
    private Modules modules;
    private Resolve resolve;
    private Enter enter;
    private Log log;
    private MemberEnter memberEnter;
    private Attr attr;
    private TreeMaker treeMaker;
    private JavacElements elements;
    private JavacTaskImpl javacTaskImpl;
    private Names names;
    private Types types;
    private DocTreeMaker docTreeMaker;
    private BreakIterator breakIterator;
    private JavaFileManager fileManager;
    private ParserFactory parser;
    private Symtab syms;

    // called reflectively from Trees.instance(CompilationTask task)
    public static JavacTrees instance(JavaCompiler.CompilationTask task) {
        if (!(task instanceof BasicJavacTask))
            throw new IllegalArgumentException();
        return instance(((BasicJavacTask)task).getContext());
    }

    // called reflectively from Trees.instance(ProcessingEnvironment env)
    public static JavacTrees instance(ProcessingEnvironment env) {
        if (!(env instanceof JavacProcessingEnvironment))
            throw new IllegalArgumentException();
        return instance(((JavacProcessingEnvironment)env).getContext());
    }

    public static JavacTrees instance(Context context) {
        JavacTrees instance = context.get(JavacTrees.class);
        if (instance == null)
            instance = new JavacTrees(context);
        return instance;
    }

    protected JavacTrees(Context context) {
        this.breakIterator = null;
        context.put(JavacTrees.class, this);
        init(context);
    }

    public void updateContext(Context context) {
        init(context);
    }

    private void init(Context context) {
        modules = Modules.instance(context);
        attr = Attr.instance(context);
        enter = Enter.instance(context);
        elements = JavacElements.instance(context);
        log = Log.instance(context);
        resolve = Resolve.instance(context);
        treeMaker = TreeMaker.instance(context);
        memberEnter = MemberEnter.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        docTreeMaker = DocTreeMaker.instance(context);
        parser = ParserFactory.instance(context);
        syms = Symtab.instance(context);
        fileManager = context.get(JavaFileManager.class);
        JavacTask t = context.get(JavacTask.class);
        if (t instanceof JavacTaskImpl)
            javacTaskImpl = (JavacTaskImpl) t;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public BreakIterator getBreakIterator() {
        return breakIterator;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocSourcePositions getSourcePositions() {
        return new DocSourcePositions() {
                @Override @DefinedBy(Api.COMPILER_TREE)
                public long getStartPosition(CompilationUnitTree file, Tree tree) {
                    return TreeInfo.getStartPos((JCTree) tree);
                }

                @Override @DefinedBy(Api.COMPILER_TREE)
                public long getEndPosition(CompilationUnitTree file, Tree tree) {
                    EndPosTable endPosTable = ((JCCompilationUnit) file).endPositions;
                    return TreeInfo.getEndPos((JCTree) tree, endPosTable);
                }

                @Override @DefinedBy(Api.COMPILER_TREE)
                public long getStartPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree) {
                    return -1;
                }
                @Override  @DefinedBy(Api.COMPILER_TREE) @SuppressWarnings("fallthrough")
                public long getEndPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree) {
                   throw new RuntimeException();
                }
            };
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocTreeMaker getDocTreeFactory() {
        return docTreeMaker;
    }

    private DocTree getLastChild(DocTree tree) {
        final DocTree[] last = new DocTree[] {null};

        tree.accept(new DocTreeScanner<Void, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void scan(DocTree node, Void p) {
                if (node != null) last[0] = node;
                return null;
            }
        }, null);

        return last[0];
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JCClassDecl getTree(TypeElement element) {
        return (JCClassDecl) getTree((Element) element);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JCMethodDecl getTree(ExecutableElement method) {
        return (JCMethodDecl) getTree((Element) method);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JCTree getTree(Element element) {
        return getTree(element, null);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JCTree getTree(Element e, AnnotationMirror a) {
        return getTree(e, a, null);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JCTree getTree(Element e, AnnotationMirror a, AnnotationValue v) {
        Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return treeTopLevel.fst;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TreePath getPath(CompilationUnitTree unit, Tree node) {
        return TreePath.getPath(unit, node);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TreePath getPath(Element e) {
        return getPath(e, null, null);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TreePath getPath(Element e, AnnotationMirror a) {
        return getPath(e, a, null);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TreePath getPath(Element e, AnnotationMirror a, AnnotationValue v) {
        final Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return TreePath.getPath(treeTopLevel.snd, treeTopLevel.fst);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Symbol getElement(TreePath path) {
        JCTree tree = (JCTree) path.getLeaf();
        Symbol sym = TreeInfo.symbolFor(tree);
        if (sym == null) {
            for (TreePath p = path; p != null; p = p.getParentPath()) {
                JCTree t = (JCTree) p.getLeaf();
                if (t.hasTag(JCTree.JCTreeTag.CLASSDEF)) {
                    JCClassDecl ct = (JCClassDecl) t;
                    if (ct.sym != null) {
                        if ((ct.sym.flags_field & Flags.UNATTRIBUTED) != 0) {
                            attr.attribClass(ct.pos(), ct.sym);
                            sym = TreeInfo.symbolFor(tree);
                        }
                        break;
                    }
                }
            }
        }
        return sym;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public Element getElement(DocTreePath path) {
        return null;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public java.util.List<DocTree> getFirstSentence(java.util.List<? extends DocTree> list) {
        return docTreeMaker.getFirstSentence(list);
    }

    private Symbol attributeDocReference(TreePath path, DCTree.DCReference ref) {
        Env<AttrContext> env = getAttrContext(path);
        if (env == null) return null;

        Log.DeferredDiagnosticHandler deferredDiagnosticHandler =
                new Log.DeferredDiagnosticHandler(log);
        try {
            final TypeSymbol tsym;
            final Name memberName;
            if (ref.qualifierExpression == null) {
                tsym = env.enclClass.sym;
                memberName = (Name) ref.memberName;
            } else {
                // newSeeTree if the qualifierExpression is a type or package name.
                // javac does not provide the exact method required, so
                // we first check if qualifierExpression identifies a type,
                // and if not, then we check to see if it identifies a package.
                Type t = attr.attribType(ref.qualifierExpression, env);
                if (t.isErroneous()) {
                    JCCompilationUnit toplevel =
                        treeMaker.TopLevel(JCList.nil());
                    final ModuleSymbol msym = modules.getDefaultModule();
                    toplevel.modle = msym;
                    toplevel.packge = msym.unnamedPackage;
                    Symbol sym = attr.attribIdent(ref.qualifierExpression, toplevel);

                    sym.complete();

                    if ((sym.kind == PCK || sym.kind == TYP) && sym.exists()) {
                        tsym = (TypeSymbol) sym;
                        memberName = (Name) ref.memberName;
                        if (sym.kind == PCK && memberName != null) {
                            //cannot refer to a package "member"
                            return null;
                        }
                    } else {
                        if (ref.qualifierExpression.hasTag(JCTree.JCTreeTag.IDENT)) {
                            // fixup:  allow "identifier" instead of "#identifier"
                            // for compatibility with javadoc
                            tsym = env.enclClass.sym;
                            memberName = ((JCIdent) ref.qualifierExpression).name;
                        } else {
                            return null;
                        }
                    }
                } else {
                    tsym = t.tsym;
                    memberName = (Name) ref.memberName;
                }
            }

            if (memberName == null)
                return tsym;

            final JCList<Type> paramTypes;
            if (ref.paramTypes == null)
                paramTypes = null;
            else {
                ListBuffer<Type> lb = new ListBuffer<>();
                for (JCList<JCTree> l = (JCList<JCTree>) ref.paramTypes; l.nonEmpty(); l = l.tail) {
                    JCTree tree = l.head;
                    Type t = attr.attribType(tree, env);
                    lb.add(t);
                }
                paramTypes = lb.toList();
            }

            ClassSymbol sym = (ClassSymbol) types.skipTypeVars(tsym.type, false).tsym;

            Symbol msym = (memberName == sym.name)
                    ? findConstructor(sym, paramTypes)
                    : findMethod(sym, memberName, paramTypes);
            if (paramTypes != null) {
                // explicit (possibly empty) arg list given, so cannot be a field
                return msym;
            }

            VarSymbol vsym = (ref.paramTypes != null) ? null : findField(sym, memberName);
            // prefer a field over a method with no parameters
            if (vsym != null &&
                    (msym == null ||
                        types.isSubtypeUnchecked(vsym.enclClass().asType(), msym.enclClass().asType()))) {
                return vsym;
            } else {
                return msym;
            }
        } catch (Abort e) { // may be thrown by Check.completionError in case of bad class file
            return null;
        } finally {
            log.popDiagnosticHandler(deferredDiagnosticHandler);
        }
    }

    private Symbol attributeParamIdentifier(TreePath path, DCParam ptag) {
        Symbol javadocSymbol = getElement(path);
        if (javadocSymbol == null)
            return null;
        ElementKind kind = javadocSymbol.getKind();
        JCList<? extends Symbol> params = JCList.nil();
        if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            MethodSymbol ee = (MethodSymbol) javadocSymbol;
            params = ptag.isTypeParameter()
                    ? ee.getTypeParameters()
                    : ee.getParameters();
        } else if (kind.isClass() || kind.isInterface()) {
            ClassSymbol te = (ClassSymbol) javadocSymbol;
            params = te.getTypeParameters();
        }

        for (Symbol param : params) {
            if (param.getSimpleName() == ptag.getName().getName()) {
                return param;
            }
        }
        return null;
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl#findField */
    private VarSymbol findField(ClassSymbol tsym, Name fieldName) {
        return searchField(tsym, fieldName, new HashSet<>());
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl#searchField */
    private VarSymbol searchField(ClassSymbol tsym, Name fieldName, Set<ClassSymbol> searched) {
        if (searched.contains(tsym)) {
            return null;
        }
        searched.add(tsym);

        for (Symbol sym : tsym.members().getSymbolsByName(fieldName)) {
            if (sym.kind == VAR) {
                return (VarSymbol)sym;
            }
        }

        //### If we found a VarSymbol above, but which did not pass
        //### the modifier filter, we should return failure here!

        ClassSymbol encl = tsym.owner.enclClass();
        if (encl != null) {
            VarSymbol vsym = searchField(encl, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }

        // search superclass
        Type superclass = tsym.getSuperclass();
        if (superclass.tsym != null) {
            VarSymbol vsym = searchField((ClassSymbol) superclass.tsym, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }

        // search interfaces
        JCList<Type> intfs = tsym.getInterfaces();
        for (JCList<Type> l = intfs; l.nonEmpty(); l = l.tail) {
            Type intf = l.head;
            if (intf.isErroneous()) continue;
            VarSymbol vsym = searchField((ClassSymbol) intf.tsym, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }

        return null;
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl#findConstructor */
    MethodSymbol findConstructor(ClassSymbol tsym, JCList<Type> paramTypes) {
        for (Symbol sym : tsym.members().getSymbolsByName(names.init)) {
            if (sym.kind == MTH) {
                if (hasParameterTypes((MethodSymbol) sym, paramTypes)) {
                    return (MethodSymbol) sym;
                }
            }
        }
        return null;
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl#findMethod */
    private MethodSymbol findMethod(ClassSymbol tsym, Name methodName, JCList<Type> paramTypes) {
        return searchMethod(tsym, methodName, paramTypes, new HashSet<>());
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl#searchMethod */
    private MethodSymbol searchMethod(ClassSymbol tsym, Name methodName,
                                      JCList<Type> paramTypes, Set<ClassSymbol> searched) {
        //### Note that this search is not necessarily what the compiler would do!

        // do not match constructors
        if (methodName == names.init)
            return null;

        if (searched.contains(tsym))
            return null;
        searched.add(tsym);

        // search current class

        //### Using modifier filter here isn't really correct,
        //### but emulates the old behavior.  Instead, we should
        //### apply the normal rules of visibility and inheritance.

        if (paramTypes == null) {
            // If no parameters specified, we are allowed to return
            // any method with a matching name.  In practice, the old
            // code returned the first method, which is now the last!
            // In order to provide textually identical results, we
            // attempt to emulate the old behavior.
            MethodSymbol lastFound = null;
            for (Symbol sym : tsym.members().getSymbolsByName(methodName)) {
                if (sym.kind == MTH) {
                    if (sym.name == methodName) {
                        lastFound = (MethodSymbol)sym;
                    }
                }
            }
            if (lastFound != null) {
                return lastFound;
            }
        } else {
            for (Symbol sym : tsym.members().getSymbolsByName(methodName)) {
                if (sym != null &&
                    sym.kind == MTH) {
                    if (hasParameterTypes((MethodSymbol) sym, paramTypes)) {
                        return (MethodSymbol) sym;
                    }
                }
            }
        }

        //### If we found a MethodSymbol above, but which did not pass
        //### the modifier filter, we should return failure here!

        // search superclass
        Type superclass = tsym.getSuperclass();
        if (superclass.tsym != null) {
            MethodSymbol msym = searchMethod((ClassSymbol) superclass.tsym, methodName, paramTypes, searched);
            if (msym != null) {
                return msym;
            }
        }

        // search interfaces
        JCList<Type> intfs = tsym.getInterfaces();
        for (JCList<Type> l = intfs; l.nonEmpty(); l = l.tail) {
            Type intf = l.head;
            if (intf.isErroneous()) continue;
            MethodSymbol msym = searchMethod((ClassSymbol) intf.tsym, methodName, paramTypes, searched);
            if (msym != null) {
                return msym;
            }
        }

        // search enclosing class
        ClassSymbol encl = tsym.owner.enclClass();
        if (encl != null) {
            MethodSymbol msym = searchMethod(encl, methodName, paramTypes, searched);
            if (msym != null) {
                return msym;
            }
        }

        return null;
    }

    /** @see com.flint.tools.javadoc.ClassDocImpl */
    private boolean hasParameterTypes(MethodSymbol method, JCList<Type> paramTypes) {
        if (paramTypes == null)
            return true;

        if (method.params().size() != paramTypes.size())
            return false;

        JCList<Type> methodParamTypes = types.erasureRecursive(method.asType()).getParameterTypes();

        return (Type.isErroneous(paramTypes))
            ? fuzzyMatch(paramTypes, methodParamTypes)
            : types.isSameTypes(paramTypes, methodParamTypes);
    }

    boolean fuzzyMatch(JCList<Type> paramTypes, JCList<Type> methodParamTypes) {
        JCList<Type> l1 = paramTypes;
        JCList<Type> l2 = methodParamTypes;
        while (l1.nonEmpty()) {
            if (!fuzzyMatch(l1.head, l2.head))
                return false;
            l1 = l1.tail;
            l2 = l2.tail;
        }
        return true;
    }

    boolean fuzzyMatch(Type paramType, Type methodParamType) {
        Boolean b = fuzzyMatcher.visit(paramType, methodParamType);
        return (b == Boolean.TRUE);
    }

    TypeRelation fuzzyMatcher = new TypeRelation() {
        @Override
        public Boolean visitType(Type t, Type s) {
            if (t == s)
                return true;

            if (s.isPartial())
                return visit(s, t);

            switch (t.getTag()) {
            case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
            case DOUBLE: case BOOLEAN: case VOID: case BOT: case NONE:
                return t.hasTag(s.getTag());
            default:
                throw new AssertionError("fuzzyMatcher " + t.getTag());
            }
        }

        @Override
        public Boolean visitArrayType(ArrayType t, Type s) {
            if (t == s)
                return true;

            if (s.isPartial())
                return visit(s, t);

            return s.hasTag(ARRAY)
                && visit(t.elemtype, types.elemtype(s));
        }

        @Override
        public Boolean visitClassType(ClassType t, Type s) {
            if (t == s)
                return true;

            if (s.isPartial())
                return visit(s, t);

            return t.tsym == s.tsym;
        }

        @Override
        public Boolean visitErrorType(ErrorType t, Type s) {
            return s.hasTag(CLASS)
                    && t.tsym.name == ((ClassType) s).tsym.name;
        }
    };

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TypeMirror getTypeMirror(TreePath path) {
        Tree t = path.getLeaf();
        Type ty = ((JCTree)t).type;
        return ty == null ? null : ty.stripMetadataIfNeeded();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public JavacScope getScope(TreePath path) {
        return JavacScope.create(getAttrContext(path));
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public String getDocComment(TreePath path) {
        CompilationUnitTree t = path.getCompilationUnit();
        Tree leaf = path.getLeaf();
        if (t instanceof JCTree.JCCompilationUnit && leaf instanceof JCTree) {
            JCCompilationUnit cu = (JCCompilationUnit) t;
//            if (cu.docComments != null) {
//                return cu.docComments.getCommentText((JCTree) leaf);
//            }
        }
        return null;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocCommentTree getDocCommentTree(TreePath path) {
        CompilationUnitTree t = path.getCompilationUnit();
        Tree leaf = path.getLeaf();
        if (t instanceof JCTree.JCCompilationUnit && leaf instanceof JCTree) {
            JCCompilationUnit cu = (JCCompilationUnit) t;
            if (cu.docComments != null) {
                return cu.docComments.getCommentTree((JCTree) leaf);
            }
        }
        return null;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocCommentTree getDocCommentTree(Element e) {
        TreePath path = getPath(e);
        if (path == null) {
            return null;
        }
        return getDocCommentTree(path);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocCommentTree getDocCommentTree(Element e, String relativeFileName) throws IOException {
        PackageElement pkg = elements.getPackageOf(e);
        FileObject fileForInput = fileManager.getFileForInput(StandardLocation.SOURCE_PATH,
                pkg.getQualifiedName().toString(), relativeFileName);

        if (fileForInput == null) {
            throw new FileNotFoundException(relativeFileName);
        }
        return getDocCommentTree(fileForInput);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public boolean isAccessible(Scope scope, TypeElement type) {
        if (scope instanceof JavacScope && type instanceof ClassSymbol) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (ClassSymbol)type, true);
        } else
            return false;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public boolean isAccessible(Scope scope, Element member, DeclaredType type) {
        if (scope instanceof JavacScope
                && member instanceof Symbol
                && type instanceof Type) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (Type)type, (Symbol)member, true);
        } else
            return false;
    }

    private Env<AttrContext> getAttrContext(TreePath path) {
        if (!(path.getLeaf() instanceof JCTree))  // implicit null-check
            throw new IllegalArgumentException();

        // if we're being invoked from a Tree API client via parse/enter/analyze,
        // we need to make sure all the classes have been entered;
        // if we're being invoked from JSR 199 or JSR 269, then the classes
        // will already have been entered.
        if (javacTaskImpl != null) {
            javacTaskImpl.enter(null);
        }

        JCCompilationUnit unit = (JCCompilationUnit) path.getCompilationUnit();
        Copier copier = createCopier(treeMaker.forToplevel(unit));

        Env<AttrContext> env = null;
        JCMethodDecl method = null;
        JCVariableDecl field = null;

        JCList<Tree> l = JCList.nil();
        TreePath p = path;
        while (p != null) {
            l = l.prepend(p.getLeaf());
            p = p.getParentPath();
        }

        for ( ; l.nonEmpty(); l = l.tail) {
            Tree tree = l.head;
            switch (tree.getKind()) {
                case COMPILATION_UNIT:
//                    System.err.println("COMP: " + ((JCCompilationUnit)tree).sourcefile);
                    env = enter.getTopLevelEnv((JCCompilationUnit)tree);
                    break;
                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
//                    System.err.println("CLASS: " + ((JCClassDecl)tree).sym.getSimpleName());
                    env = enter.getClassEnv(((JCClassDecl)tree).sym);
                    if (env == null) return null;
                    break;
                case METHOD:
//                    System.err.println("METHOD: " + ((JCMethodDecl)tree).sym.getSimpleName());
                    method = (JCMethodDecl)tree;
                    env = memberEnter.getMethodEnv(method, env);
                    break;
                case VARIABLE:
//                    System.err.println("FIELD: " + ((JCVariableDecl)tree).sym.getSimpleName());
                    field = (JCVariableDecl)tree;
                    break;
                case BLOCK: {
//                    System.err.println("BLOCK: ");
                    if (method != null) {
                        try {
                            Assert.check(method.body == tree);
                            method.body = copier.copy((JCBlock)tree, (JCTree) path.getLeaf());
                            env = attribStatToTree(method.body, env, copier.leafCopy);
                        } finally {
                            method.body = (JCBlock) tree;
                        }
                    } else {
                        JCBlock body = copier.copy((JCBlock)tree, (JCTree) path.getLeaf());
                        env = attribStatToTree(body, env, copier.leafCopy);
                    }
                    return env;
                }
                default:
//                    System.err.println("DEFAULT: " + tree.getKind());
                    if (field != null && field.getInitializer() == tree) {
                        env = memberEnter.getInitEnv(field, env);
                        JCExpression expr = copier.copy((JCExpression)tree, (JCTree) path.getLeaf());
                        env = attribExprToTree(expr, env, copier.leafCopy);
                        return env;
                    }
            }
        }
        return (field != null) ? memberEnter.getInitEnv(field, env) : env;
    }

    private Env<AttrContext> attribStatToTree(JCTree stat, Env<AttrContext>env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribStatToTree(stat, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    private Env<AttrContext> attribExprToTree(JCExpression expr, Env<AttrContext>env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribExprToTree(expr, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    static JavaFileObject asJavaFileObject(FileObject fileObject) {
        JavaFileObject jfo = null;

        if (fileObject instanceof JavaFileObject) {
            jfo = (JavaFileObject) fileObject;
            checkHtmlKind(fileObject, Kind.HTML);
            return jfo;
        }

        checkHtmlKind(fileObject);
        jfo = new HtmlFileObject(fileObject);
        return jfo;
    }

    private static void checkHtmlKind(FileObject fileObject) {
        checkHtmlKind(fileObject, BaseFileManager.getKind(fileObject.getName()));
    }

    private static void checkHtmlKind(FileObject fileObject, Kind kind) {
        if (kind != Kind.HTML) {
            throw new IllegalArgumentException("HTML file expected:" + fileObject.getName());
        }
    }

    private static class HtmlFileObject extends ForwardingFileObject<FileObject>
            implements JavaFileObject {

        public HtmlFileObject(FileObject fileObject) {
            super(fileObject);
        }

        @Override @DefinedBy(Api.COMPILER)
        public Kind getKind() {
            return BaseFileManager.getKind(fileObject.getName());
        }

        @Override @DefinedBy(Api.COMPILER)
        public boolean isNameCompatible(String simpleName, Kind kind) {
            return false;
        }

        @Override @DefinedBy(Api.COMPILER)
        public NestingKind getNestingKind() {
            return null;
        }

        @Override @DefinedBy(Api.COMPILER)
        public Modifier getAccessLevel() {
            return null;
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocCommentTree getDocCommentTree(FileObject fileObject) {
        JavaFileObject jfo = asJavaFileObject(fileObject);
        DiagnosticSource diagSource = new DiagnosticSource(jfo, log);

        final Tokens.Comment comment = new Tokens.Comment() {
            int offset = 0;
            @Override
            public String getText() {
                try {
                    CharSequence rawDoc = fileObject.getCharContent(true);
                    Pattern bodyPat =
                            Pattern.compile("(?is).*?<body\\b[^>]*>(.*)</body\\b.*");
                    Matcher m = bodyPat.matcher(rawDoc);
                    if (m.matches()) {
                        offset = m.end(1);
                        return m.group(1);
                    } else {
                        // Assume doclint will do the right thing.
                        return "";
                    }
                } catch (IOException ignore) {
                    // do nothing
                }
                return "";
            }

            @Override
            public int getSourcePos(int index) {
                return offset + index;
            }

            @Override
            public CommentStyle getStyle() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isDeprecated() {
                throw new UnsupportedOperationException();
            }
        };

        return new DocCommentParser(parser, diagSource, comment).parse();
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocTreePath getDocTreePath(FileObject fileObject, PackageElement packageElement) {
        JavaFileObject jfo = asJavaFileObject(fileObject);
        DocCommentTree docCommentTree = getDocCommentTree(jfo);
        TreePath treePath = makeTreePath((PackageSymbol)packageElement, jfo, docCommentTree);
        return new DocTreePath(treePath, docCommentTree);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public void setBreakIterator(BreakIterator breakiterator) {
        this.breakIterator = breakiterator;
    }

    /**
     * Makes a copy of a tree, noting the value resulting from copying a particular leaf.
     **/
    protected static class Copier extends TreeCopier<JCTree> {
        JCTree leafCopy = null;

        protected Copier(TreeMaker M) {
            super(M);
        }

        @Override
        public <T extends JCTree> T copy(T t, JCTree leaf) {
            T t2 = super.copy(t, leaf);
            if (t == leaf)
                leafCopy = t2;
            return t2;
        }
    }

    protected Copier createCopier(TreeMaker maker) {
        return new Copier(maker);
    }

    /**
     * Returns the original type from the ErrorType object.
     * @param errorType The errorType for which we want to get the original type.
     * @return TypeMirror corresponding to the original type, replaced by the ErrorType.
     *         noType (type.tag == NONE) is returned if there is no original type.
     */
    @Override @DefinedBy(Api.COMPILER_TREE)
    public TypeMirror getOriginalType(javax.lang.model.type.ErrorType errorType) {
        if (errorType instanceof Type.ErrorType) {
            return ((Type.ErrorType)errorType).getOriginalType();
        }

        return Type.noType;
    }

    /**
     * Prints a message of the specified kind at the location of the
     * tree within the provided compilation unit
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param t    the tree to use as a position hint
     * @param root the compilation unit that contains tree
     */
    @Override @DefinedBy(Api.COMPILER_TREE)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
            Tree t,
            CompilationUnitTree root) {
        printMessage(kind, msg, ((JCTree) t).pos(), root);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
            DocTree t,
            DocCommentTree c,
            CompilationUnitTree root) {
        printMessage(kind, msg, ((DCTree) t).pos((DCDocComment) c), root);
    }

    private void printMessage(Diagnostic.Kind kind, CharSequence msg,
            JCDiagnostic.DiagnosticPosition pos,
            CompilationUnitTree root) {
        JavaFileObject oldSource = null;
        JavaFileObject newSource = null;

        newSource = root.getSourceFile();
        if (newSource == null) {
            pos = null;
        } else {
            oldSource = log.useSource(newSource);
        }

        try {
            switch (kind) {
            case ERROR:
                log.error(DiagnosticFlag.MULTIPLE, pos, "proc.messager", msg.toString());
                break;

            case WARNING:
                log.warning(pos, "proc.messager", msg.toString());
                break;

            case MANDATORY_WARNING:
                log.mandatoryWarning(pos, "proc.messager", msg.toString());
                break;

            default:
                log.note(pos, "proc.messager", msg.toString());
            }
        } finally {
            if (oldSource != null)
                log.useSource(oldSource);
        }
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public TypeMirror getLub(CatchTree tree) {
        JCCatch ct = (JCCatch) tree;
        JCVariableDecl v = ct.param;
        if (v.type != null && v.type.getKind() == TypeKind.UNION) {
            UnionClassType ut = (UnionClassType) v.type;
            return ut.getLub();
        } else {
            return v.type;
        }
    }

    private TreePath makeTreePath(final PackageSymbol psym, final JavaFileObject jfo,
            DocCommentTree dcTree) {
        JCCompilationUnit jcCompilationUnit = new JCCompilationUnit(JCList.nil()) {
            public int getPos() {
                return Position.FIRSTPOS;
            }

            public JavaFileObject getSourcefile() {
                return jfo;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Position.LineMap getLineMap() {
                try {
                    CharSequence content = jfo.getCharContent(true);
                    String s = content.toString();
                    return Position.makeLineMap(s.toCharArray(), s.length(), true);
                } catch (IOException ignore) {}
                return null;
            }
        };

        jcCompilationUnit.docComments = new DocCommentTable() {
            @Override
            public boolean hasComment(JCTree tree) {
                return false;
            }

            @Override
            public Comment getComment(JCTree tree) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getCommentText(JCTree tree) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DCDocComment getCommentTree(JCTree tree) {
                return (DCDocComment)dcTree;
            }

            @Override
            public void putComment(JCTree tree, Comment c) {
                throw new UnsupportedOperationException();
            }

        };
        jcCompilationUnit.lineMap = jcCompilationUnit.getLineMap();
        jcCompilationUnit.modle = psym.modle;
        jcCompilationUnit.sourcefile = jfo;
        jcCompilationUnit.namedImportScope = new NamedImportScope(psym, jcCompilationUnit.toplevelScope);
        jcCompilationUnit.packge = psym;
        jcCompilationUnit.starImportScope = new StarImportScope(psym);
        jcCompilationUnit.toplevelScope = WriteableScope.create(psym);
        return new TreePath(jcCompilationUnit);
    }
}
