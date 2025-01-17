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

package com.flint.tools.flintc.tree;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.flint.tools.flintc.code.Attribute;
import com.flint.tools.flintc.code.BoundKind;
import com.flint.tools.flintc.code.Flags;
import com.flint.tools.flintc.code.Scope;
import com.flint.tools.flintc.code.Symbol;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.code.TypeTag;
import com.flint.tools.flintc.code.Types;
import com.flint.source.tree.*;
import com.flint.tools.flintc.code.Directive.RequiresDirective;
import com.flint.tools.flintc.code.Scope.*;
import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticPosition;

import static com.flint.tools.flintc.tree.JCTree.JCTreeTag.*;

import javax.tools.JavaFileManager.Location;

import com.flint.source.tree.ModuleTree.ModuleKind;
import com.flint.tools.flintc.code.Directive.ExportsDirective;
import com.flint.tools.flintc.code.Directive.OpensDirective;
import com.flint.tools.flintc.code.Type.ModuleType;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.tree.DocCommentTable;

/**
 * Root class for abstract syntax tree nodes. It provides definitions
 * for specific tree nodes as subclasses nested inside.
 *
 * <p>Each subclass is highly standardized.  It generally contains
 * only tree fields for the syntactic subcomponents of the node.  Some
 * classes that represent identifier uses or definitions also define a
 * Symbol field that denotes the represented identifier.  Classes for
 * non-local jumps also carry the jump target as a field.  The root
 * class Tree itself defines fields for the tree's type and position.
 * No other fields are kept in a tree node; instead parameters are
 * passed to methods accessing the node.
 *
 * <p>Except for the methods defined by com.flint.source, the only
 * method defined in subclasses is `visit' which applies a given
 * visitor to the tree. The actual tree processing is done by visitor
 * classes in other packages. The abstract class Visitor, as well as
 * an Factory interface for trees, are defined as inner classes in
 * Tree.
 *
 * <p>To avoid ambiguities with the Tree API in com.flint.source all sub
 * classes should, by convention, start with JC (javac).
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 *
 * @see TreeMaker
 * @see TreeInfo
 * @see TreeTranslator
 * @see Pretty
 */
public abstract class JCTree implements Tree, Cloneable, DiagnosticPosition {

    /* Tree tag values, identifying kinds of trees */
    public enum JCTreeTag {
        /** For methods that return an invalid tag if a given condition is not met
         */
        NO_TAG,

        /** Toplevel nodes, of type TopLevel, representing entire source files.
        */
        TOPLEVEL,

        /** Package level definitions.
         */
        PACKAGEDEF,

        /** Import clauses, of type Import.
         */
        IMPORT,

        /** Class definitions, of type ClassDef.
         */
        CLASSDEF,

        /** Method definitions, of type MethodDef.
         */
        METHODDEF,

        /** Variable definitions, of type VarDef.
         */
        VARDEF,

        /** The no-op statement ";", of type Skip
         */
        SKIP,

        /** Blocks, of type Block.
         */
        BLOCK,

        /** Do-while loops, of type DoLoop.
         */
        DOLOOP,

        /** While-loops, of type WhileLoop.
         */
        WHILELOOP,

        /** For-loops, of type ForLoop.
         */
        FORLOOP,

        /** Foreach-loops, of type ForeachLoop.
         */
        FOREACHLOOP,

        /** Labelled statements, of type Labelled.
         */
        LABELLED,

        /** Switch statements, of type Switch.
         */
        SWITCH,

        /** Case parts in switch statements, of type Case.
         */
        CASE,

        /** Synchronized statements, of type Synchonized.
         */
        SYNCHRONIZED,

        /** Try statements, of type Try.
         */
        TRY,

        /** Catch clauses in try statements, of type Catch.
         */
        CATCH,

        /** Conditional expressions, of type Conditional.
         */
        CONDEXPR,

        /** Conditional statements, of type If.
         */
        IF,

        /** Expression statements, of type Exec.
         */
        EXEC,

        /** Break statements, of type Break.
         */
        BREAK,

        /** Continue statements, of type Continue.
         */
        CONTINUE,

        /** Return statements, of type Return.
         */
        RETURN,

        /** Throw statements, of type Throw.
         */
        THROW,

        /** Assert statements, of type Assert.
         */
        ASSERT,

        /** Method invocation expressions, of type Apply.
         */
        APPLY,

        /** Class instance creation expressions, of type NewClass.
         */
        NEWCLASS,

        /** Array creation expressions, of type NewArray.
         */
        NEWARRAY,

        /** Lambda expression, of type Lambda.
         */
        LAMBDA,

        /** Parenthesized subexpressions, of type Parens.
         */
        PARENS,

        /** Assignment expressions, of type Assign.
         */
        ASSIGN,

        /** Type cast expressions, of type TypeCast.
         */
        TYPECAST,

        /** Type test expressions, of type TypeTest.
         */
        TYPETEST,

        /** Indexed array expressions, of type Indexed.
         */
        INDEXED,

        /** Selections, of type Select.
         */
        SELECT,

        /** Member references, of type Reference.
         */
        REFERENCE,

        /** Simple identifiers, of type Ident.
         */
        IDENT,

        /** Literals, of type Literal.
         */
        LITERAL,

        /** Basic type identifiers, of type TypeIdent.
         */
        TYPEIDENT,

        /** Array types, of type TypeArray.
         */
        TYPEARRAY,

        /** Parameterized types, of type TypeApply.
         */
        TYPEAPPLY,

        /** Union types, of type TypeUnion.
         */
        TYPEUNION,

        /** Intersection types, of type TypeIntersection.
         */
        TYPEINTERSECTION,

        /** Formal type parameters, of type TypeParameter.
         */
        TYPEPARAMETER,

        /** Type argument.
         */
        WILDCARD,

        /** Bound kind: extends, super, exact, or unbound
         */
        TYPEBOUNDKIND,

        /** metadata: Annotation.
         */
        ANNOTATION,

        /** metadata: Type annotation.
         */
        TYPE_ANNOTATION,

        /** metadata: Modifiers
         */
        MODIFIERS,

        /** An annotated type tree.
         */
        ANNOTATED_TYPE,

        /** Error trees, of type Erroneous.
         */
        ERRONEOUS,

        /** Unary operators, of type Unary.
         */
        POS,                             // +
        NEG,                             // -
        NOT,                             // !
        COMPL,                           // ~
        PREINC,                          // ++ _
        PREDEC,                          // -- _
        POSTINC,                         // _ ++
        POSTDEC,                         // _ --

        /** unary operator for null reference checks, only used internally.
         */
        NULLCHK,

        /** Binary operators, of type Binary.
         */
        OR,                              // ||
        AND,                             // &&
        BITOR,                           // |
        BITXOR,                          // ^
        BITAND,                          // &
        EQ,                              // ==
        NE,                              // !=
        LT,                              // <
        GT,                              // >
        LE,                              // <=
        GE,                              // >=
        SL,                              // <<
        SR,                              // >>
        USR,                             // >>>
        PLUS,                            // +
        MINUS,                           // -
        MUL,                             // *
        DIV,                             // /
        MOD,                             // %

        /** Assignment operators, of type Assignop.
         */
        BITOR_ASG(BITOR),                // |=
        BITXOR_ASG(BITXOR),              // ^=
        BITAND_ASG(BITAND),              // &=

        SL_ASG(SL),                      // <<=
        SR_ASG(SR),                      // >>=
        USR_ASG(USR),                    // >>>=
        PLUS_ASG(PLUS),                  // +=
        MINUS_ASG(MINUS),                // -=
        MUL_ASG(MUL),                    // *=
        DIV_ASG(DIV),                    // /=
        MOD_ASG(MOD),                    // %=

        MODULEDEF,
        EXPORTS,
        OPENS,
        PROVIDES,
        REQUIRES,
        USES,

        /** A synthetic let expression, of type LetExpr.
         */
        LETEXPR;                         // ala scheme

        private final JCTreeTag noAssignTag;

        private static final int numberOfOperators = MOD.ordinal() - POS.ordinal() + 1;

        private JCTreeTag(JCTreeTag noAssignTag) {
            this.noAssignTag = noAssignTag;
        }

        private JCTreeTag() {
            this(null);
        }

        public static int getNumberOfOperators() {
            return numberOfOperators;
        }

        public JCTreeTag noAssignOp() {
            if (noAssignTag != null)
                return noAssignTag;
            throw new AssertionError("noAssignOp() method is not available for non assignment tags");
        }

        public boolean isPostUnaryOp() {
            return (this == POSTINC || this == POSTDEC);
        }

        public boolean isIncOrDecUnaryOp() {
            return (this == PREINC || this == PREDEC || this == POSTINC || this == POSTDEC);
        }

        public boolean isAssignop() {
            return noAssignTag != null;
        }

        public int operatorIndex() {
            return (this.ordinal() - POS.ordinal());
        }
    }

    /* The (encoded) position in the source file. @see util.Position.
     */
    public int pos;

    /* The type of this node.
     */
    public Type type;

    /* The tag of this node -- one of the constants declared above.
     */
    public abstract JCTreeTag getTag();

    /* Returns true if the tag of this node is equals to tag.
     */
    public boolean hasTag(JCTreeTag tag) {
        return tag == getTag();
    }

    /** Convert a tree to a pretty-printed string. */
    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new Pretty(s, false).printExpr(this);
        }
        catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    /** Set position field and return this tree.
     */
    public JCTree setPos(int pos) {
        this.pos = pos;
        return this;
    }

    /** Set type field and return this tree.
     */
    public JCTree setType(Type type) {
        this.type = type;
        return this;
    }

    /** Visit this tree with a given visitor.
     */
    public abstract void accept(Visitor v);

    @DefinedBy(Api.COMPILER_TREE)
    public abstract <R,D> R accept(TreeVisitor<R,D> v, D d);

    /** Return a shallow copy of this tree.
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a default position for this tree node.
     */
    public DiagnosticPosition pos() {
        return this;
    }

    // for default DiagnosticPosition
    public JCTree getTree() {
        return this;
    }

    // for default DiagnosticPosition
    public int getStartPosition() {
        return TreeInfo.getStartPos(this);
    }

    // for default DiagnosticPosition
    public int getPreferredPosition() {
        return pos;
    }

    // for default DiagnosticPosition
    public int getEndPosition(EndPosTable endPosTable) {
        return TreeInfo.getEndPos(this, endPosTable);
    }

    /**
     * Everything in one source file is kept in a {@linkplain JCTree.JCCompilationUnit} structure.
     */
    public static class JCCompilationUnit extends JCTree implements CompilationUnitTree {
        /** All definitions in this file (ClassDef, Import, and Skip) */
        public JCList<JCTree> defs;
        /** The source file name. */
        public JavaFileObject sourcefile;
        /** The module to which this compilation unit belongs. */
        public ModuleSymbol modle;
        /** The location in which this compilation unit was found. */
        public Location locn;
        /** The package to which this compilation unit belongs. */
        public PackageSymbol packge;
        /** A scope containing top level classes. */
        public WriteableScope toplevelScope;
        /** A scope for all named imports. */
        public NamedImportScope namedImportScope;
        /** A scope for all import-on-demands. */
        public StarImportScope starImportScope;
        /** Line starting positions, defined only if option -g is set. */
        public Position.LineMap lineMap = null;
        /** A table that stores all documentation comments indexed by the tree
         * nodes they refer to. defined only if option -s is set. */
        public com.flint.tools.flintc.tree.DocCommentTable docComments = null;
        /* An object encapsulating ending positions of source ranges indexed by
         * the tree nodes they belong to. Defined only if option -Xjcov is set. */
        public EndPosTable endPositions = null;
        protected JCCompilationUnit(JCList<JCTree> defs) {
            this.defs = defs;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTopLevel(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.COMPILATION_UNIT; }

        public JCTree.JCModuleDecl getModuleDecl() {
            for (JCTree tree : defs) {
                if (tree.hasTag(MODULEDEF)) {
                    return (JCTree.JCModuleDecl) tree;
                }
            }

            return null;
        }

        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCPackageDecl getPackage() {
            // PackageDecl must be the first entry if it exists
            if (!defs.isEmpty() && defs.head.hasTag(PACKAGEDEF))
                return (JCTree.JCPackageDecl)defs.head;
            return null;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getPackageAnnotations() {
            JCTree.JCPackageDecl pd = getPackage();
            return pd != null ? pd.getAnnotations() : JCList.nil();
        }
        @DefinedBy(Api.COMPILER_TREE)
        public ExpressionTree getPackageName() {
            JCTree.JCPackageDecl pd = getPackage();
            return pd != null ? pd.getPackageName() : null;
        }

        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCImport> getImports() {
            ListBuffer<JCTree.JCImport> imports = new ListBuffer<>();
            for (JCTree tree : defs) {
                if (tree.hasTag(IMPORT))
                    imports.append((JCTree.JCImport)tree);
                else if (!tree.hasTag(PACKAGEDEF) && !tree.hasTag(SKIP))
                    break;
            }
            return imports.toList();
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JavaFileObject getSourceFile() {
            return sourcefile;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public Position.LineMap getLineMap() {
            return lineMap;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCTree> getTypeDecls() {
            JCList<JCTree> typeDefs;
            for (typeDefs = defs; !typeDefs.isEmpty(); typeDefs = typeDefs.tail)
                if (!typeDefs.head.hasTag(PACKAGEDEF) && !typeDefs.head.hasTag(IMPORT))
                    break;
            return typeDefs;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompilationUnit(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return TOPLEVEL;
        }
    }

    /**
     * Package definition.
     */
    public static class JCPackageDecl extends JCTree implements PackageTree {
        public JCList<JCAnnotation> annotations;
        /** The tree representing the package clause. */
        public JCTree.JCExpression pid;
        public PackageSymbol packge;
        public JCPackageDecl(JCList<JCAnnotation> annotations, JCTree.JCExpression pid) {
            this.annotations = annotations;
            this.pid = pid;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitPackageDef(this); }
        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PACKAGE;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getPackageName() {
            return pid;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitPackage(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return PACKAGEDEF;
        }
    }

    /**
     * An import clause.
     */
    public static class JCImport extends JCTree implements ImportTree {
        public boolean staticImport;
        /** The imported class(es). */
        public JCTree qualid;
        public Scope importScope;
        protected JCImport(JCTree qualid, boolean importStatic) {
            this.qualid = qualid;
            this.staticImport = importStatic;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitImport(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public boolean isStatic() { return staticImport; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getQualifiedIdentifier() { return qualid; }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.IMPORT; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitImport(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return IMPORT;
        }
    }

    public static abstract class JCStatement extends JCTree implements StatementTree {
        @Override
        public JCTree.JCStatement setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCTree.JCStatement setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    public static abstract class JCExpression extends JCTree implements ExpressionTree {
        @Override
        public JCTree.JCExpression setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCTree.JCExpression setPos(int pos) {
            super.setPos(pos);
            return this;
        }

        public boolean isPoly() { return false; }
        public boolean isStandalone() { return true; }
    }

    /**
     * Common supertype for all poly expression trees (lambda, method references,
     * conditionals, method and constructor calls)
     */
    public static abstract class JCPolyExpression extends JCExpression {

        /**
         * A poly expression can only be truly 'poly' in certain contexts
         */
        public enum PolyKind {
            /** poly expression to be treated as a standalone expression */
            STANDALONE,
            /** true poly expression */
            POLY
        }

        /** is this poly expression a 'true' poly expression? */
        public PolyKind polyKind;

        @Override public boolean isPoly() { return polyKind == PolyKind.POLY; }
        @Override public boolean isStandalone() { return polyKind == PolyKind.STANDALONE; }
    }

    /**
     * Common supertype for all functional expression trees (lambda and method references)
     */
    public static abstract class JCFunctionalExpression extends JCPolyExpression {

        public JCFunctionalExpression() {
            //a functional expression is always a 'true' poly
            polyKind = JCTree.JCPolyExpression.PolyKind.POLY;
        }

        /** list of target types inferred for this functional expression. */
        public JCList<Type> targets;

        public Type getDescriptorType(Types types) {
            return targets.nonEmpty() ? types.findDescriptorType(targets.head) : types.createErrorType(null);
        }
    }

    /**
     * A class definition.
     */
    public static class JCClassDecl extends JCStatement implements ClassTree {
        /** the modifiers */
        public JCTree.JCModifiers mods;
        /** the name of the class */
        public Name name;
        /** formal class parameters */
        public JCList<JCTypeParameter> typarams;
        /** the classes this class extends */
        public JCTree.JCExpression extending;
        /** the interfaces implemented by this class */
        public JCList<JCExpression> implementing;
        /** all variables and methods defined in this class */
        public JCList<JCTree> defs;
        /** the symbol */
        public ClassSymbol sym;
        protected JCClassDecl(JCTree.JCModifiers mods,
                              Name name,
                              JCList<JCTypeParameter> typarams,
                              JCTree.JCExpression extending,
                              JCList<JCExpression> implementing,
                              JCList<JCTree> defs,
                              ClassSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.typarams = typarams;
            this.extending = extending;
            this.implementing = implementing;
            this.defs = defs;
            this.sym = sym;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitClassDef(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            if ((mods.flags & Flags.ANNOTATION) != 0)
                return Kind.ANNOTATION_TYPE;
            else if ((mods.flags & Flags.INTERFACE) != 0)
                return Kind.INTERFACE;
            else if ((mods.flags & Flags.ENUM) != 0)
                return Kind.ENUM;
            else
                return Kind.CLASS;
        }

        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCModifiers getModifiers() { return mods; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getSimpleName() { return name; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExtendsClause() { return extending; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getImplementsClause() {
            return implementing;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCTree> getMembers() {
            return defs;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitClass(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return CLASSDEF;
        }
    }

    /**
     * A method definition.
     */
    public static class JCMethodDecl extends JCTree implements MethodTree {
        /** method modifiers */
        public JCTree.JCModifiers mods;
        /** method name */
        public Name name;
        /** type of method return value */
        public JCTree.JCExpression restype;
        /** type parameters */
        public JCList<JCTypeParameter> typarams;
        /** receiver parameter */
        public JCTree.JCVariableDecl recvparam;
        /** value parameters */
        public JCList<JCVariableDecl> params;
        /** exceptions thrown by this method */
        public JCList<JCExpression> thrown;
        /** statements in the method */
        public JCTree.JCBlock body;
        /** default value, for annotation types */
        public JCTree.JCExpression defaultValue;
        /** method symbol */
        public MethodSymbol sym;
        protected JCMethodDecl(JCTree.JCModifiers mods,
                               Name name,
                               JCTree.JCExpression restype,
                               JCList<JCTypeParameter> typarams,
                               JCTree.JCVariableDecl recvparam,
                               JCList<JCVariableDecl> params,
                               JCList<JCExpression> thrown,
                               JCTree.JCBlock body,
                               JCTree.JCExpression defaultValue,
                               MethodSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.restype = restype;
            this.typarams = typarams;
            this.params = params;
            this.recvparam = recvparam;
            // TODO: do something special if the given type is null?
            // receiver != null ? receiver : List.<JCTypeAnnotation>nil());
            this.thrown = thrown;
            this.body = body;
            this.defaultValue = defaultValue;
            this.sym = sym;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitMethodDef(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.METHOD; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCModifiers getModifiers() { return mods; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getName() { return name; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getReturnType() { return restype; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCVariableDecl> getParameters() {
            return params;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCVariableDecl getReceiverParameter() { return recvparam; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getThrows() {
            return thrown;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCBlock getBody() { return body; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getDefaultValue() { // for annotation types
            return defaultValue;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethod(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return METHODDEF;
        }
  }

    /**
     * A variable definition.
     */
    public static class JCVariableDecl extends JCStatement implements VariableTree {
        /** variable modifiers */
        public JCTree.JCModifiers mods;
        /** variable name */
        public Name name;
        /** variable name expression */
        public JCTree.JCExpression nameexpr;
        /** type of the variable */
        public JCTree.JCExpression vartype;
        /** variable's initial value */
        public JCTree.JCExpression init;
        /** symbol */
        public VarSymbol sym;

        protected JCVariableDecl(JCTree.JCModifiers mods,
                                 Name name,
                                 JCTree.JCExpression vartype,
                                 JCTree.JCExpression init,
                                 VarSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.vartype = vartype;
            this.init = init;
            this.sym = sym;
        }

        protected JCVariableDecl(JCTree.JCModifiers mods,
                                 JCTree.JCExpression nameexpr,
                                 JCTree.JCExpression vartype) {
            this(mods, null, vartype, null, null);
            this.nameexpr = nameexpr;
            if (nameexpr.hasTag(JCTreeTag.IDENT)) {
                this.name = ((JCTree.JCIdent)nameexpr).name;
            } else {
                // Only other option is qualified name x.y.this;
                this.name = ((JCTree.JCFieldAccess)nameexpr).name;
            }
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitVarDef(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.VARIABLE; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCModifiers getModifiers() { return mods; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getName() { return name; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getNameExpression() { return nameexpr; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getType() { return vartype; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getInitializer() {
            return init;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitVariable(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return VARDEF;
        }
    }

    /**
     * A no-op statement ";".
     */
    public static class JCSkip extends JCStatement implements EmptyStatementTree {
        protected JCSkip() {
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitSkip(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.EMPTY_STATEMENT; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEmptyStatement(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return SKIP;
        }
    }

    /**
     * A statement block.
     */
    public static class JCBlock extends JCStatement implements BlockTree {
        /** flags */
        public long flags;
        /** statements */
        public JCList<JCStatement> stats;
        /** Position of closing brace, optional. */
        public int endpos = Position.NOPOS;
        protected JCBlock(long flags, JCList<JCStatement> stats) {
            this.stats = stats;
            this.flags = flags;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitBlock(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.BLOCK; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCStatement> getStatements() {
            return stats;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public boolean isStatic() { return (flags & Flags.STATIC) != 0; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBlock(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return BLOCK;
        }
    }

    /**
     * A do loop
     */
    public static class JCDoWhileLoop extends JCStatement implements DoWhileLoopTree {
        public JCTree.JCStatement body;
        public JCTree.JCExpression cond;
        protected JCDoWhileLoop(JCTree.JCStatement body, JCTree.JCExpression cond) {
            this.body = body;
            this.cond = cond;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitDoLoop(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.DO_WHILE_LOOP; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getStatement() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitDoWhileLoop(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return DOLOOP;
        }
    }

    /**
     * A while loop
     */
    public static class JCWhileLoop extends JCStatement implements WhileLoopTree {
        public JCTree.JCExpression cond;
        public JCTree.JCStatement body;
        protected JCWhileLoop(JCTree.JCExpression cond, JCTree.JCStatement body) {
            this.cond = cond;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitWhileLoop(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.WHILE_LOOP; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getStatement() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWhileLoop(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return WHILELOOP;
        }
    }

    /**
     * A for loop.
     */
    public static class JCForLoop extends JCStatement implements ForLoopTree {
        public JCList<JCStatement> init;
        public JCTree.JCExpression cond;
        public JCList<JCExpressionStatement> step;
        public JCTree.JCStatement body;
        protected JCForLoop(JCList<JCStatement> init,
                            JCTree.JCExpression cond,
                            JCList<JCExpressionStatement> update,
                            JCTree.JCStatement body)
        {
            this.init = init;
            this.cond = cond;
            this.step = update;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitForLoop(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.FOR_LOOP; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getStatement() { return body; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCStatement> getInitializer() {
            return init;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpressionStatement> getUpdate() {
            return step;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitForLoop(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return FORLOOP;
        }
    }

    /**
     * The enhanced for loop.
     */
    public static class JCEnhancedForLoop extends JCStatement implements EnhancedForLoopTree {
        public JCTree.JCVariableDecl var;
        public JCTree.JCExpression expr;
        public JCTree.JCStatement body;
        protected JCEnhancedForLoop(JCTree.JCVariableDecl var, JCTree.JCExpression expr, JCTree.JCStatement body) {
            this.var = var;
            this.expr = expr;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitForeachLoop(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ENHANCED_FOR_LOOP; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCVariableDecl getVariable() { return var; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getStatement() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEnhancedForLoop(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return FOREACHLOOP;
        }
    }

    /**
     * A labelled expression or statement.
     */
    public static class JCLabeledStatement extends JCStatement implements LabeledStatementTree {
        public Name label;
        public JCTree.JCStatement body;
        protected JCLabeledStatement(Name label, JCTree.JCStatement body) {
            this.label = label;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitLabelled(this); }
        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.LABELED_STATEMENT; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getLabel() { return label; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getStatement() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLabeledStatement(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return LABELLED;
        }
    }

    /**
     * A "switch ( ) { }" construction.
     */
    public static class JCSwitch extends JCStatement implements SwitchTree {
        public JCTree.JCExpression selector;
        public JCList<JCCase> cases;
        protected JCSwitch(JCTree.JCExpression selector, JCList<JCCase> cases) {
            this.selector = selector;
            this.cases = cases;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitSwitch(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.SWITCH; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return selector; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCCase> getCases() { return cases; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSwitch(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return SWITCH;
        }
    }

    /**
     * A "case  :" of a switch.
     */
    public static class JCCase extends JCStatement implements CaseTree {
        public JCTree.JCExpression pat;
        public JCList<JCStatement> stats;
        protected JCCase(JCTree.JCExpression pat, JCList<JCStatement> stats) {
            this.pat = pat;
            this.stats = stats;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitCase(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.CASE; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return pat; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCStatement> getStatements() { return stats; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCase(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return CASE;
        }
    }

    /**
     * A synchronized block.
     */
    public static class JCSynchronized extends JCStatement implements SynchronizedTree {
        public JCTree.JCExpression lock;
        public JCTree.JCBlock body;
        protected JCSynchronized(JCTree.JCExpression lock, JCTree.JCBlock body) {
            this.lock = lock;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitSynchronized(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.SYNCHRONIZED; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return lock; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCBlock getBlock() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSynchronized(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return SYNCHRONIZED;
        }
    }

    /**
     * A "try { } catch ( ) { } finally { }" block.
     */
    public static class JCTry extends JCStatement implements TryTree {
        public JCTree.JCBlock body;
        public JCList<JCCatch> catchers;
        public JCTree.JCBlock finalizer;
        public JCList<JCTree> resources;
        public boolean finallyCanCompleteNormally;
        protected JCTry(JCList<JCTree> resources,
                        JCTree.JCBlock body,
                        JCList<JCCatch> catchers,
                        JCTree.JCBlock finalizer) {
            this.body = body;
            this.catchers = catchers;
            this.finalizer = finalizer;
            this.resources = resources;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTry(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.TRY; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCBlock getBlock() { return body; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCCatch> getCatches() {
            return catchers;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCBlock getFinallyBlock() { return finalizer; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTry(this, d);
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCTree> getResources() {
            return resources;
        }
        @Override
        public JCTreeTag getTag() {
            return TRY;
        }
    }

    /**
     * A catch block.
     */
    public static class JCCatch extends JCTree implements CatchTree {
        public JCTree.JCVariableDecl param;
        public JCTree.JCBlock body;
        protected JCCatch(JCTree.JCVariableDecl param, JCTree.JCBlock body) {
            this.param = param;
            this.body = body;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitCatch(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.CATCH; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCVariableDecl getParameter() { return param; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCBlock getBlock() { return body; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCatch(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return CATCH;
        }
    }

    /**
     * A ( ) ? ( ) : ( ) conditional expression
     */
    public static class JCConditional extends JCPolyExpression implements ConditionalExpressionTree {
        public JCTree.JCExpression cond;
        public JCTree.JCExpression truepart;
        public JCTree.JCExpression falsepart;
        protected JCConditional(JCTree.JCExpression cond,
                                JCTree.JCExpression truepart,
                                JCTree.JCExpression falsepart)
        {
            this.cond = cond;
            this.truepart = truepart;
            this.falsepart = falsepart;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitConditional(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.CONDITIONAL_EXPRESSION; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getTrueExpression() { return truepart; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getFalseExpression() { return falsepart; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitConditionalExpression(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return CONDEXPR;
        }
    }

    /**
     * An "if ( ) { } else { }" block
     */
    public static class JCIf extends JCStatement implements IfTree {
        public JCTree.JCExpression cond;
        public JCTree.JCStatement thenpart;
        public JCTree.JCStatement elsepart;
        protected JCIf(JCTree.JCExpression cond,
                       JCTree.JCStatement thenpart,
                       JCTree.JCStatement elsepart)
        {
            this.cond = cond;
            this.thenpart = thenpart;
            this.elsepart = elsepart;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitIf(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.IF; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getThenStatement() { return thenpart; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCStatement getElseStatement() { return elsepart; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIf(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return IF;
        }
    }

    /**
     * an expression statement
     */
    public static class JCExpressionStatement extends JCStatement implements ExpressionStatementTree {
        /** expression structure */
        public JCTree.JCExpression expr;
        protected JCExpressionStatement(JCTree.JCExpression expr)
        {
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitExec(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.EXPRESSION_STATEMENT; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitExpressionStatement(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return EXEC;
        }

        /** Convert a expression-statement tree to a pretty-printed string. */
        @Override
        public String toString() {
            StringWriter s = new StringWriter();
            try {
                new Pretty(s, false).printStat(this);
            }
            catch (IOException e) {
                // should never happen, because StringWriter is defined
                // never to throw any IOExceptions
                throw new AssertionError(e);
            }
            return s.toString();
        }
    }

    /**
     * A break from a loop or switch.
     */
    public static class JCBreak extends JCStatement implements BreakTree {
        public Name label;
        public JCTree target;
        protected JCBreak(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitBreak(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.BREAK; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getLabel() { return label; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBreak(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return BREAK;
        }
    }

    /**
     * A continue of a loop.
     */
    public static class JCContinue extends JCStatement implements ContinueTree {
        public Name label;
        public JCTree target;
        protected JCContinue(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitContinue(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.CONTINUE; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getLabel() { return label; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitContinue(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return CONTINUE;
        }
    }

    /**
     * A return statement.
     */
    public static class JCReturn extends JCStatement implements ReturnTree {
        public JCTree.JCExpression expr;
        protected JCReturn(JCTree.JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitReturn(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.RETURN; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitReturn(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return RETURN;
        }
    }

    /**
     * A throw statement.
     */
    public static class JCThrow extends JCStatement implements ThrowTree {
        public JCTree.JCExpression expr;
        protected JCThrow(JCTree.JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitThrow(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.THROW; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitThrow(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return THROW;
        }
    }

    /**
     * An assert statement.
     */
    public static class JCAssert extends JCStatement implements AssertTree {
        public JCTree.JCExpression cond;
        public JCTree.JCExpression detail;
        protected JCAssert(JCTree.JCExpression cond, JCTree.JCExpression detail) {
            this.cond = cond;
            this.detail = detail;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitAssert(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ASSERT; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getCondition() { return cond; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getDetail() { return detail; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssert(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return ASSERT;
        }
    }

    /**
     * A method invocation
     */
    public static class JCMethodInvocation extends JCPolyExpression implements MethodInvocationTree {
        public JCList<JCExpression> typeargs;
        public JCTree.JCExpression meth;
        public JCList<JCExpression> args;
        public Type varargsElement;
        protected JCMethodInvocation(JCList<JCExpression> typeargs,
                                     JCTree.JCExpression meth,
                                     JCList<JCExpression> args)
        {
            this.typeargs = (typeargs == null) ? JCList.nil()
                                               : typeargs;
            this.meth = meth;
            this.args = args;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitApply(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.METHOD_INVOCATION; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getTypeArguments() {
            return typeargs;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getMethodSelect() { return meth; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getArguments() {
            return args;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethodInvocation(this, d);
        }
        @Override
        public JCTree.JCMethodInvocation setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCTreeTag getTag() {
            return(APPLY);
        }
    }

    /**
     * A new(...) operation.
     */
    public static class JCNewClass extends JCPolyExpression implements NewClassTree {
        public JCTree.JCExpression encl;
        public JCList<JCExpression> typeargs;
        public JCTree.JCExpression clazz;
        public JCList<JCExpression> args;
        public JCTree.JCClassDecl def;
        public Symbol constructor;
        public Type varargsElement;
        public Type constructorType;
        protected JCNewClass(JCTree.JCExpression encl,
                             JCList<JCExpression> typeargs,
                             JCTree.JCExpression clazz,
                             JCList<JCExpression> args,
                             JCTree.JCClassDecl def)
        {
            this.encl = encl;
            this.typeargs = (typeargs == null) ? JCList.nil()
                                               : typeargs;
            this.clazz = clazz;
            this.args = args;
            this.def = def;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitNewClass(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.NEW_CLASS; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getEnclosingExpression() { // expr.new C< ... > ( ... )
            return encl;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getTypeArguments() {
            return typeargs;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getIdentifier() { return clazz; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getArguments() {
            return args;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCClassDecl getClassBody() { return def; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewClass(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return NEWCLASS;
        }
    }

    /**
     * A new[...] operation.
     */
    public static class JCNewArray extends JCExpression implements NewArrayTree {
        public JCTree.JCExpression elemtype;
        public JCList<JCExpression> dims;
        // type annotations on inner-most component
        public JCList<JCAnnotation> annotations;
        // type annotations on dimensions
        public JCList<JCList<JCAnnotation>> dimAnnotations;
        public JCList<JCExpression> elems;
        protected JCNewArray(JCTree.JCExpression elemtype,
                             JCList<JCExpression> dims,
                             JCList<JCExpression> elems)
        {
            this.elemtype = elemtype;
            this.dims = dims;
            this.annotations = JCList.nil();
            this.dimAnnotations = JCList.nil();
            this.elems = elems;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitNewArray(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.NEW_ARRAY; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getType() { return elemtype; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getDimensions() {
            return dims;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getInitializers() {
            return elems;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewArray(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return NEWARRAY;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getAnnotations() {
            return annotations;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCList<JCAnnotation>> getDimAnnotations() {
            return dimAnnotations;
        }
    }

    /**
     * A lambda expression.
     */
    public static class JCLambda extends JCFunctionalExpression implements LambdaExpressionTree {

        public enum ParameterKind {
            IMPLICIT,
            EXPLICIT
        }

        public JCList<JCVariableDecl> params;
        public JCTree body;
        public boolean canCompleteNormally = true;
        public ParameterKind paramKind;

        public JCLambda(JCList<JCVariableDecl> params,
                        JCTree body) {
            this.params = params;
            this.body = body;
            if (params.isEmpty() ||
                params.head.vartype != null) {
                paramKind = ParameterKind.EXPLICIT;
            } else {
                paramKind = ParameterKind.IMPLICIT;
            }
        }
        @Override
        public JCTreeTag getTag() {
            return LAMBDA;
        }
        @Override
        public void accept(JCTree.Visitor v) {
            v.visitLambda(this);
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitLambdaExpression(this, d);
        }
        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.LAMBDA_EXPRESSION;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getBody() {
            return body;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public java.util.List<? extends VariableTree> getParameters() {
            return params;
        }
        @Override
        public JCTree.JCLambda setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public BodyKind getBodyKind() {
            return body.hasTag(BLOCK) ?
                    BodyKind.STATEMENT :
                    BodyKind.EXPRESSION;
        }
    }

    /**
     * A parenthesized subexpression ( ... )
     */
    public static class JCParens extends JCExpression implements ParenthesizedTree {
        public JCTree.JCExpression expr;
        protected JCParens(JCTree.JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitParens(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.PARENTHESIZED; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParenthesized(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return PARENS;
        }
    }

    /**
     * A assignment with "=".
     */
    public static class JCAssign extends JCExpression implements AssignmentTree {
        public JCTree.JCExpression lhs;
        public JCTree.JCExpression rhs;
        protected JCAssign(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitAssign(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ASSIGNMENT; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getVariable() { return lhs; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return rhs; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssignment(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return ASSIGN;
        }
    }

    public static abstract class JCOperatorExpression extends JCExpression {
        public enum OperandPos {
            LEFT,
            RIGHT
        }

        protected JCTreeTag opcode;
        public OperatorSymbol operator;

        public OperatorSymbol getOperator() {
            return operator;
        }

        @Override
        public JCTreeTag getTag() {
            return opcode;
        }

        public abstract JCTree.JCExpression getOperand(OperandPos pos);
    }

    /**
     * An assignment with "+=", "|=" ...
     */
    public static class JCAssignOp extends JCOperatorExpression implements CompoundAssignmentTree {
        public JCTree.JCExpression lhs;
        public JCTree.JCExpression rhs;
        protected JCAssignOp(JCTreeTag opcode, JCTree lhs, JCTree rhs, OperatorSymbol operator) {
            this.opcode = opcode;
            this.lhs = (JCTree.JCExpression)lhs;
            this.rhs = (JCTree.JCExpression)rhs;
            this.operator = operator;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitAssignop(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getVariable() { return lhs; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return rhs; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompoundAssignment(this, d);
        }
        @Override
        public JCTree.JCExpression getOperand(JCTree.JCOperatorExpression.OperandPos pos) {
            return pos == JCTree.JCOperatorExpression.OperandPos.LEFT ? lhs : rhs;
        }
    }

    /**
     * A unary operation.
     */
    public static class JCUnary extends JCOperatorExpression implements UnaryTree {
        public JCTree.JCExpression arg;
        protected JCUnary(JCTreeTag opcode, JCTree.JCExpression arg) {
            this.opcode = opcode;
            this.arg = arg;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitUnary(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return arg; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitUnary(this, d);
        }
        public void setTag(JCTreeTag tag) {
            opcode = tag;
        }
        @Override
        public JCTree.JCExpression getOperand(JCTree.JCOperatorExpression.OperandPos pos) {
            return arg;
        }
    }

    /**
     * A binary operation.
     */
    public static class JCBinary extends JCOperatorExpression implements BinaryTree {
        public JCTree.JCExpression lhs;
        public JCTree.JCExpression rhs;
        protected JCBinary(JCTreeTag opcode,
                           JCTree.JCExpression lhs,
                           JCTree.JCExpression rhs,
                           OperatorSymbol operator) {
            this.opcode = opcode;
            this.lhs = lhs;
            this.rhs = rhs;
            this.operator = operator;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitBinary(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getLeftOperand() { return lhs; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getRightOperand() { return rhs; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBinary(this, d);
        }
        @Override
        public JCTree.JCExpression getOperand(JCTree.JCOperatorExpression.OperandPos pos) {
            return pos == JCTree.JCOperatorExpression.OperandPos.LEFT ? lhs : rhs;
        }
    }

    /**
     * A type cast.
     */
    public static class JCTypeCast extends JCExpression implements TypeCastTree {
        public JCTree clazz;
        public JCTree.JCExpression expr;
        protected JCTypeCast(JCTree clazz, JCTree.JCExpression expr) {
            this.clazz = clazz;
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeCast(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.TYPE_CAST; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getType() { return clazz; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeCast(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPECAST;
        }
    }

    /**
     * A type test.
     */
    public static class JCInstanceOf extends JCExpression implements InstanceOfTree {
        public JCTree.JCExpression expr;
        public JCTree clazz;
        protected JCInstanceOf(JCTree.JCExpression expr, JCTree clazz) {
            this.expr = expr;
            this.clazz = clazz;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeTest(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.INSTANCE_OF; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getType() { return clazz; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitInstanceOf(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPETEST;
        }
    }

    /**
     * An array selection
     */
    public static class JCArrayAccess extends JCExpression implements ArrayAccessTree {
        public JCTree.JCExpression indexed;
        public JCTree.JCExpression index;
        protected JCArrayAccess(JCTree.JCExpression indexed, JCTree.JCExpression index) {
            this.indexed = indexed;
            this.index = index;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitIndexed(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ARRAY_ACCESS; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return indexed; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getIndex() { return index; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayAccess(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return INDEXED;
        }
    }

    /**
     * Selects through packages and classes
     */
    public static class JCFieldAccess extends JCExpression implements MemberSelectTree {
        /** selected Tree hierarchy */
        public JCTree.JCExpression selected;
        /** name of field to select thru */
        public Name name;
        /** symbol of the selected class */
        public Symbol sym;
        protected JCFieldAccess(JCTree.JCExpression selected, Name name, Symbol sym) {
            this.selected = selected;
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitSelect(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.MEMBER_SELECT; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getExpression() { return selected; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMemberSelect(this, d);
        }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getIdentifier() { return name; }
        @Override
        public JCTreeTag getTag() {
            return SELECT;
        }
    }

    /**
     * Selects a member expression.
     */
    public static class JCMemberReference extends JCFunctionalExpression implements MemberReferenceTree {

        public ReferenceMode mode;
        public ReferenceKind kind;
        public Name name;
        public JCTree.JCExpression expr;
        public JCList<JCExpression> typeargs;
        public Symbol sym;
        public Type varargsElement;
        public JCTree.JCPolyExpression.PolyKind refPolyKind;
        public boolean ownerAccessible;
        private OverloadKind overloadKind;
        public Type referentType;

        public enum OverloadKind {
            OVERLOADED,
            UNOVERLOADED
        }

        /**
         * Javac-dependent classification for member references, based
         * on relevant properties w.r.t. code-generation
         */
        public enum ReferenceKind {
            /** super # instMethod */
            SUPER(ReferenceMode.INVOKE, false),
            /** Type # instMethod */
            UNBOUND(ReferenceMode.INVOKE, true),
            /** Type # staticMethod */
            STATIC(ReferenceMode.INVOKE, false),
            /** Expr # instMethod */
            BOUND(ReferenceMode.INVOKE, false),
            /** Inner # new */
            IMPLICIT_INNER(ReferenceMode.NEW, false),
            /** Toplevel # new */
            TOPLEVEL(ReferenceMode.NEW, false),
            /** ArrayType # new */
            ARRAY_CTOR(ReferenceMode.NEW, false);

            final ReferenceMode mode;
            final boolean unbound;

            private ReferenceKind(ReferenceMode mode, boolean unbound) {
                this.mode = mode;
                this.unbound = unbound;
            }

            public boolean isUnbound() {
                return unbound;
            }
        }

        public JCMemberReference(ReferenceMode mode, Name name, JCTree.JCExpression expr, JCList<JCExpression> typeargs) {
            this.mode = mode;
            this.name = name;
            this.expr = expr;
            this.typeargs = typeargs;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitReference(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.MEMBER_REFERENCE; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceMode getMode() { return mode; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getQualifierExpression() { return expr; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() { return name; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getTypeArguments() { return typeargs; }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMemberReference(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return REFERENCE;
        }
        public boolean hasKind(ReferenceKind kind) {
            return this.kind == kind;
        }

        /**
         * @return the overloadKind
         */
        public OverloadKind getOverloadKind() {
            return overloadKind;
        }

        /**
         * @param overloadKind the overloadKind to set
         */
        public void setOverloadKind(OverloadKind overloadKind) {
            this.overloadKind = overloadKind;
        }
    }

    /**
     * An identifier
     */
    public static class JCIdent extends JCExpression implements IdentifierTree {
        /** the name */
        public Name name;
        /** the symbol */
        public Symbol sym;
        protected JCIdent(Name name, Symbol sym) {
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitIdent(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.IDENTIFIER; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getName() { return name; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIdentifier(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return IDENT;
        }
    }

    /**
     * A constant value given literally.
     */
    public static class JCLiteral extends JCExpression implements LiteralTree {
        public TypeTag typetag;
        /** value representation */
        public Object value;
        protected JCLiteral(TypeTag typetag, Object value) {
            this.typetag = typetag;
            this.value = value;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitLiteral(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return typetag.getKindLiteral();
        }

        @DefinedBy(Api.COMPILER_TREE)
        public Object getValue() {
            switch (typetag) {
                case BOOLEAN:
                    int bi = (Integer) value;
                    return (bi != 0);
                case CHAR:
                    int ci = (Integer) value;
                    char c = (char) ci;
                    if (c != ci)
                        throw new AssertionError("bad value for char literal");
                    return c;
                default:
                    return value;
            }
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLiteral(this, d);
        }
        @Override
        public JCTree.JCLiteral setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCTreeTag getTag() {
            return LITERAL;
        }
    }

    /**
     * Identifies a basic type.
     * @see TypeTag
     */
    public static class JCPrimitiveTypeTree extends JCExpression implements PrimitiveTypeTree {
        /** the basic type id */
        public TypeTag typetag;
        protected JCPrimitiveTypeTree(TypeTag typetag) {
            this.typetag = typetag;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeIdent(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.PRIMITIVE_TYPE; }
        @DefinedBy(Api.COMPILER_TREE)
        public TypeKind getPrimitiveTypeKind() {
            return typetag.getPrimitiveTypeKind();
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitPrimitiveType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEIDENT;
        }
    }

    /**
     * An array type, A[]
     */
    public static class JCArrayTypeTree extends JCExpression implements ArrayTypeTree {
        public JCTree.JCExpression elemtype;
        protected JCArrayTypeTree(JCTree.JCExpression elemtype) {
            this.elemtype = elemtype;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeArray(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ARRAY_TYPE; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getType() { return elemtype; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEARRAY;
        }
    }

    /**
     * A parameterized type, {@literal T<...>}
     */
    public static class JCTypeApply extends JCExpression implements ParameterizedTypeTree {
        public JCTree.JCExpression clazz;
        public JCList<JCExpression> arguments;
        protected JCTypeApply(JCTree.JCExpression clazz, JCList<JCExpression> arguments) {
            this.clazz = clazz;
            this.arguments = arguments;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeApply(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.PARAMETERIZED_TYPE; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getType() { return clazz; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getTypeArguments() {
            return arguments;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParameterizedType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEAPPLY;
        }
    }

    /**
     * A union type, T1 | T2 | ... Tn (used in multicatch statements)
     */
    public static class JCTypeUnion extends JCExpression implements UnionTypeTree {

        public JCList<JCExpression> alternatives;

        protected JCTypeUnion(JCList<JCExpression> components) {
            this.alternatives = components;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeUnion(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.UNION_TYPE; }

        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getTypeAlternatives() {
            return alternatives;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitUnionType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEUNION;
        }
    }

    /**
     * An intersection type, {@code T1 & T2 & ... Tn} (used in cast expressions)
     */
    public static class JCTypeIntersection extends JCExpression implements IntersectionTypeTree {

        public JCList<JCExpression> bounds;

        protected JCTypeIntersection(JCList<JCExpression> bounds) {
            this.bounds = bounds;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeIntersection(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.INTERSECTION_TYPE; }

        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getBounds() {
            return bounds;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIntersectionType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEINTERSECTION;
        }
    }

    /**
     * A formal class parameter.
     */
    public static class JCTypeParameter extends JCTree implements TypeParameterTree {
        /** name */
        public Name name;
        /** bounds */
        public JCList<JCExpression> bounds;
        /** type annotations on type parameter */
        public JCList<JCAnnotation> annotations;
        protected JCTypeParameter(Name name, JCList<JCExpression> bounds, JCList<JCAnnotation> annotations) {
            this.name = name;
            this.bounds = bounds;
            this.annotations = annotations;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeParameter(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.TYPE_PARAMETER; }
        @DefinedBy(Api.COMPILER_TREE)
        public Name getName() { return name; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getBounds() {
            return bounds;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeParameter(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEPARAMETER;
        }
    }

    public static class JCWildcard extends JCExpression implements WildcardTree {
        public JCTree.TypeBoundKind kind;
        public JCTree inner;
        protected JCWildcard(JCTree.TypeBoundKind kind, JCTree inner) {
            this.kind = Assert.checkNonNull(kind);
            this.inner = inner;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitWildcard(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            switch (kind.kind) {
            case UNBOUND:
                return Kind.UNBOUNDED_WILDCARD;
            case EXTENDS:
                return Kind.EXTENDS_WILDCARD;
            case SUPER:
                return Kind.SUPER_WILDCARD;
            default:
                throw new AssertionError("Unknown wildcard bound " + kind);
            }
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getBound() { return inner; }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWildcard(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return JCTreeTag.WILDCARD;
        }
    }

    public static class TypeBoundKind extends JCTree {
        public BoundKind kind;
        protected TypeBoundKind(BoundKind kind) {
            this.kind = kind;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitTypeBoundKind(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override
        public JCTreeTag getTag() {
            return TYPEBOUNDKIND;
        }
    }

    public static class JCAnnotation extends JCExpression implements AnnotationTree {
        // Either Tag.ANNOTATION or Tag.TYPE_ANNOTATION
        private JCTreeTag tag;

        public JCTree annotationType;
        public JCList<JCExpression> args;
        public Attribute.Compound attribute;

        protected JCAnnotation(JCTreeTag tag, JCTree annotationType, JCList<JCExpression> args) {
            this.tag = tag;
            this.annotationType = annotationType;
            this.args = args;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitAnnotation(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }

        @DefinedBy(Api.COMPILER_TREE)
        public JCTree getAnnotationType() { return annotationType; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getArguments() {
            return args;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAnnotation(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return tag;
        }
    }

    public static class JCModifiers extends JCTree implements ModifiersTree {
        public long flags;
        public JCList<JCAnnotation> annotations;
        protected JCModifiers(long flags, JCList<JCAnnotation> annotations) {
            this.flags = flags;
            this.annotations = annotations;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitModifiers(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.MODIFIERS; }
        @DefinedBy(Api.COMPILER_TREE)
        public Set<Modifier> getFlags() {
            return Flags.asModifierSet(flags);
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitModifiers(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return MODIFIERS;
        }
    }

    public static class JCAnnotatedType extends JCExpression implements AnnotatedTypeTree {
        // type annotations
        public JCList<JCAnnotation> annotations;
        public JCTree.JCExpression underlyingType;

        protected JCAnnotatedType(JCList<JCAnnotation> annotations, JCTree.JCExpression underlyingType) {
            Assert.check(annotations != null && annotations.nonEmpty());
            this.annotations = annotations;
            this.underlyingType = underlyingType;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitAnnotatedType(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ANNOTATED_TYPE; }
        @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getUnderlyingType() {
            return underlyingType;
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAnnotatedType(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return ANNOTATED_TYPE;
        }
    }

    public static abstract class JCDirective extends JCTree
        implements DirectiveTree {
    }

    public static class JCModuleDecl extends JCTree implements ModuleTree {
        public JCTree.JCModifiers mods;
        public ModuleType type;
        private final ModuleKind kind;
        public JCTree.JCExpression qualId;
        public JCList<JCDirective> directives;
        public ModuleSymbol sym;

        protected JCModuleDecl(JCTree.JCModifiers mods, ModuleKind kind,
                               JCTree.JCExpression qualId, JCList<JCDirective> directives) {
            this.mods = mods;
            this.kind = kind;
            this.qualId = qualId;
            this.directives = directives;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitModuleDef(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.MODULE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<? extends AnnotationTree> getAnnotations() {
            return mods.annotations;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ModuleKind getModuleType() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getName() {
            return qualId;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCDirective> getDirectives() {
            return directives;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitModule(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return MODULEDEF;
        }
    }

    public static class JCExports extends JCDirective
            implements ExportsTree {
        public JCTree.JCExpression qualid;
        public JCList<JCExpression> moduleNames;
        public ExportsDirective directive;

        protected JCExports(JCTree.JCExpression qualId, JCList<JCExpression> moduleNames) {
            this.qualid = qualId;
            this.moduleNames = moduleNames;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitExports(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.EXPORTS;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getPackageName() {
            return qualid;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getModuleNames() {
            return moduleNames;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitExports(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return JCTreeTag.EXPORTS;
        }
    }

    public static class JCOpens extends JCDirective
            implements OpensTree {
        public JCTree.JCExpression qualid;
        public JCList<JCExpression> moduleNames;
        public OpensDirective directive;

        protected JCOpens(JCTree.JCExpression qualId, JCList<JCExpression> moduleNames) {
            this.qualid = qualId;
            this.moduleNames = moduleNames;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitOpens(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.OPENS;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getPackageName() {
            return qualid;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getModuleNames() {
            return moduleNames;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitOpens(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return JCTreeTag.OPENS;
        }
    }

    public static class JCProvides extends JCDirective
            implements ProvidesTree {
        public JCTree.JCExpression serviceName;
        public JCList<JCExpression> implNames;

        protected JCProvides(JCTree.JCExpression serviceName, JCList<JCExpression> implNames) {
            this.serviceName = serviceName;
            this.implNames = implNames;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitProvides(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PROVIDES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitProvides(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getServiceName() {
            return serviceName;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCList<JCExpression> getImplementationNames() {
            return implNames;
        }

        @Override
        public JCTreeTag getTag() {
            return PROVIDES;
        }
    }

    public static class JCRequires extends JCDirective
            implements RequiresTree {
        public boolean isTransitive;
        public boolean isStaticPhase;
        public JCTree.JCExpression moduleName;
        public RequiresDirective directive;

        protected JCRequires(boolean isTransitive, boolean isStaticPhase, JCTree.JCExpression moduleName) {
            this.isTransitive = isTransitive;
            this.isStaticPhase = isStaticPhase;
            this.moduleName = moduleName;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitRequires(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.REQUIRES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitRequires(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isTransitive() {
            return isTransitive;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isStatic() {
            return isStaticPhase;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getModuleName() {
            return moduleName;
        }

        @Override
        public JCTreeTag getTag() {
            return REQUIRES;
        }
    }

    public static class JCUses extends JCDirective
            implements UsesTree {
        public JCTree.JCExpression qualid;

        protected JCUses(JCTree.JCExpression qualId) {
            this.qualid = qualId;
        }

        @Override
        public void accept(JCTree.Visitor v) { v.visitUses(this); }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.USES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public JCTree.JCExpression getServiceName() {
            return qualid;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitUses(this, d);
        }

        @Override
        public JCTreeTag getTag() {
            return USES;
        }
    }

    public static class JCErroneous extends JCExpression
            implements ErroneousTree {
        public JCList<? extends JCTree> errs;
        protected JCErroneous(JCList<? extends JCTree> errs) {
            this.errs = errs;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitErroneous(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() { return Kind.ERRONEOUS; }

        @DefinedBy(Api.COMPILER_TREE)
        public JCList<? extends JCTree> getErrorTrees() {
            return errs;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitErroneous(this, d);
        }
        @Override
        public JCTreeTag getTag() {
            return ERRONEOUS;
        }
    }

    /** (let int x = 3; in x+2) */
    public static class LetExpr extends JCExpression {
        public JCList<JCVariableDecl> defs;
        public JCTree.JCExpression expr;
        protected LetExpr(JCList<JCVariableDecl> defs, JCTree.JCExpression expr) {
            this.defs = defs;
            this.expr = expr;
        }
        @Override
        public void accept(JCTree.Visitor v) { v.visitLetExpr(this); }

        @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override
        public JCTreeTag getTag() {
            return LETEXPR;
        }
    }

    /** An interface for tree factories
     */
    public interface Factory {
        JCCompilationUnit TopLevel(JCList<JCTree> defs);
        JCPackageDecl PackageDecl(JCList<JCAnnotation> annotations,
                                  JCExpression pid);
        JCImport Import(JCTree qualid, boolean staticImport);
        JCClassDecl ClassDef(JCModifiers mods,
                          Name name,
                          JCList<JCTypeParameter> typarams,
                          JCExpression extending,
                          JCList<JCExpression> implementing,
                          JCList<JCTree> defs);
        JCMethodDecl MethodDef(JCModifiers mods,
                            Name name,
                            JCExpression restype,
                            JCList<JCTypeParameter> typarams,
                            JCVariableDecl recvparam,
                            JCList<JCVariableDecl> params,
                            JCList<JCExpression> thrown,
                            JCBlock body,
                            JCExpression defaultValue);
        JCVariableDecl VarDef(JCModifiers mods,
                      Name name,
                      JCExpression vartype,
                      JCExpression init);
        JCSkip Skip();
        JCBlock Block(long flags, JCList<JCStatement> stats);
        JCDoWhileLoop DoLoop(JCStatement body, JCExpression cond);
        JCWhileLoop WhileLoop(JCExpression cond, JCStatement body);
        JCForLoop ForLoop(JCList<JCStatement> init,
                          JCExpression cond,
                          JCList<JCExpressionStatement> step,
                          JCStatement body);
        JCEnhancedForLoop ForeachLoop(JCVariableDecl var, JCExpression expr, JCStatement body);
        JCLabeledStatement Labelled(Name label, JCStatement body);
        JCSwitch Switch(JCExpression selector, JCList<JCCase> cases);
        JCCase Case(JCExpression pat, JCList<JCStatement> stats);
        JCSynchronized Synchronized(JCExpression lock, JCBlock body);
        JCTry Try(JCBlock body, JCList<JCCatch> catchers, JCBlock finalizer);
        JCTry Try(JCList<JCTree> resources,
                  JCBlock body,
                  JCList<JCCatch> catchers,
                  JCBlock finalizer);
        JCCatch Catch(JCVariableDecl param, JCBlock body);
        JCConditional Conditional(JCExpression cond,
                                JCExpression thenpart,
                                JCExpression elsepart);
        JCIf If(JCExpression cond, JCStatement thenpart, JCStatement elsepart);
        JCExpressionStatement Exec(JCExpression expr);
        JCBreak Break(Name label);
        JCContinue Continue(Name label);
        JCReturn Return(JCExpression expr);
        JCThrow Throw(JCExpression expr);
        JCAssert Assert(JCExpression cond, JCExpression detail);
        JCMethodInvocation Apply(JCList<JCExpression> typeargs,
                                 JCExpression fn,
                                 JCList<JCExpression> args);
        JCNewClass NewClass(JCExpression encl,
                          JCList<JCExpression> typeargs,
                          JCExpression clazz,
                          JCList<JCExpression> args,
                          JCClassDecl def);
        JCNewArray NewArray(JCExpression elemtype,
                          JCList<JCExpression> dims,
                          JCList<JCExpression> elems);
        JCParens Parens(JCExpression expr);
        JCAssign Assign(JCExpression lhs, JCExpression rhs);
        JCAssignOp Assignop(JCTreeTag opcode, JCTree lhs, JCTree rhs);
        JCUnary Unary(JCTreeTag opcode, JCExpression arg);
        JCBinary Binary(JCTreeTag opcode, JCExpression lhs, JCExpression rhs);
        JCTypeCast TypeCast(JCTree expr, JCExpression type);
        JCInstanceOf TypeTest(JCExpression expr, JCTree clazz);
        JCArrayAccess Indexed(JCExpression indexed, JCExpression index);
        JCFieldAccess Select(JCExpression selected, Name selector);
        JCIdent Ident(Name idname);
        JCLiteral Literal(TypeTag tag, Object value);
        JCPrimitiveTypeTree TypeIdent(TypeTag typetag);
        JCArrayTypeTree TypeArray(JCExpression elemtype);
        JCTypeApply TypeApply(JCExpression clazz, JCList<JCExpression> arguments);
        JCTypeParameter TypeParameter(Name name, JCList<JCExpression> bounds);
        JCWildcard Wildcard(TypeBoundKind kind, JCTree type);
        TypeBoundKind TypeBoundKind(BoundKind kind);
        JCAnnotation Annotation(JCTree annotationType, JCList<JCExpression> args);
        JCModifiers Modifiers(long flags, JCList<JCAnnotation> annotations);
        JCErroneous Erroneous(JCList<? extends JCTree> errs);
        JCModuleDecl ModuleDef(JCModifiers mods, ModuleKind kind, JCExpression qualId, JCList<JCDirective> directives);
        JCExports Exports(JCExpression qualId, JCList<JCExpression> moduleNames);
        JCOpens Opens(JCExpression qualId, JCList<JCExpression> moduleNames);
        JCProvides Provides(JCExpression serviceName, JCList<JCExpression> implNames);
        JCRequires Requires(boolean isTransitive, boolean isStaticPhase, JCExpression qualId);
        JCUses Uses(JCExpression qualId);
        LetExpr LetExpr(JCList<JCVariableDecl> defs, JCExpression expr);
    }

    /** A generic visitor class for trees.
     */
    public static abstract class Visitor {
        public void visitTopLevel(JCCompilationUnit that)    { visitTree(that); }
        public void visitPackageDef(JCPackageDecl that)      { visitTree(that); }
        public void visitImport(JCImport that)               { visitTree(that); }
        public void visitClassDef(JCClassDecl that)          { visitTree(that); }
        public void visitMethodDef(JCMethodDecl that)        { visitTree(that); }
        public void visitVarDef(JCVariableDecl that)         { visitTree(that); }
        public void visitSkip(JCSkip that)                   { visitTree(that); }
        public void visitBlock(JCBlock that)                 { visitTree(that); }
        public void visitDoLoop(JCDoWhileLoop that)          { visitTree(that); }
        public void visitWhileLoop(JCWhileLoop that)         { visitTree(that); }
        public void visitForLoop(JCForLoop that)             { visitTree(that); }
        public void visitForeachLoop(JCEnhancedForLoop that) { visitTree(that); }
        public void visitLabelled(JCLabeledStatement that)   { visitTree(that); }
        public void visitSwitch(JCSwitch that)               { visitTree(that); }
        public void visitCase(JCCase that)                   { visitTree(that); }
        public void visitSynchronized(JCSynchronized that)   { visitTree(that); }
        public void visitTry(JCTry that)                     { visitTree(that); }
        public void visitCatch(JCCatch that)                 { visitTree(that); }
        public void visitConditional(JCConditional that)     { visitTree(that); }
        public void visitIf(JCIf that)                       { visitTree(that); }
        public void visitExec(JCExpressionStatement that)    { visitTree(that); }
        public void visitBreak(JCBreak that)                 { visitTree(that); }
        public void visitContinue(JCContinue that)           { visitTree(that); }
        public void visitReturn(JCReturn that)               { visitTree(that); }
        public void visitThrow(JCThrow that)                 { visitTree(that); }
        public void visitAssert(JCAssert that)               { visitTree(that); }
        public void visitApply(JCMethodInvocation that)      { visitTree(that); }
        public void visitNewClass(JCNewClass that)           { visitTree(that); }
        public void visitNewArray(JCNewArray that)           { visitTree(that); }
        public void visitLambda(JCLambda that)               { visitTree(that); }
        public void visitParens(JCParens that)               { visitTree(that); }
        public void visitAssign(JCAssign that)               { visitTree(that); }
        public void visitAssignop(JCAssignOp that)           { visitTree(that); }
        public void visitUnary(JCUnary that)                 { visitTree(that); }
        public void visitBinary(JCBinary that)               { visitTree(that); }
        public void visitTypeCast(JCTypeCast that)           { visitTree(that); }
        public void visitTypeTest(JCInstanceOf that)         { visitTree(that); }
        public void visitIndexed(JCArrayAccess that)         { visitTree(that); }
        public void visitSelect(JCFieldAccess that)          { visitTree(that); }
        public void visitReference(JCMemberReference that)   { visitTree(that); }
        public void visitIdent(JCIdent that)                 { visitTree(that); }
        public void visitLiteral(JCLiteral that)             { visitTree(that); }
        public void visitTypeIdent(JCPrimitiveTypeTree that) { visitTree(that); }
        public void visitTypeArray(JCArrayTypeTree that)     { visitTree(that); }
        public void visitTypeApply(JCTypeApply that)         { visitTree(that); }
        public void visitTypeUnion(JCTypeUnion that)         { visitTree(that); }
        public void visitTypeIntersection(JCTypeIntersection that)  { visitTree(that); }
        public void visitTypeParameter(JCTypeParameter that) { visitTree(that); }
        public void visitWildcard(JCWildcard that)           { visitTree(that); }
        public void visitTypeBoundKind(TypeBoundKind that)   { visitTree(that); }
        public void visitAnnotation(JCAnnotation that)       { visitTree(that); }
        public void visitModifiers(JCModifiers that)         { visitTree(that); }
        public void visitAnnotatedType(JCAnnotatedType that) { visitTree(that); }
        public void visitErroneous(JCErroneous that)         { visitTree(that); }
        public void visitModuleDef(JCModuleDecl that)        { visitTree(that); }
        public void visitExports(JCExports that)             { visitTree(that); }
        public void visitOpens(JCOpens that)                 { visitTree(that); }
        public void visitProvides(JCProvides that)           { visitTree(that); }
        public void visitRequires(JCRequires that)           { visitTree(that); }
        public void visitUses(JCUses that)                   { visitTree(that); }
        public void visitLetExpr(LetExpr that)               { visitTree(that); }

        public void visitTree(JCTree that)                   { Assert.error(); }
    }

}
