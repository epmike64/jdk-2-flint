/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.source.util;

import java.lang.reflect.Method;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler.CompilationTask;

import com.flint.source.tree.*;

/**
 * Bridges JSR 199, JSR 269, and the Tree API.
 *
 * @author Peter von der Ah&eacute;
 */
public abstract class Trees {
    /**
     * Returns a Trees object for a given CompilationTask.
     * @param task the compilation task for which to get the Trees object
     * @throws IllegalArgumentException if the task does not support the Trees API.
     * @return the Trees object
     */
    public static Trees instance(CompilationTask task) {
        String taskClassName = task.getClass().getName();
        if (!taskClassName.equals("com.flint.tools.flintc.api.JavacTaskImpl")
                && !taskClassName.equals("com.flint.tools.flintc.api.BasicJavacTask"))
            throw new IllegalArgumentException();
        return getJavacTrees(CompilationTask.class, task);
    }

    /**
     * Returns a Trees object for a given ProcessingEnvironment.
     * @param env the processing environment for which to get the Trees object
     * @throws IllegalArgumentException if the env does not support the Trees API.
     * @return the Trees object
     */
    public static Trees instance(ProcessingEnvironment env) {
        if (!env.getClass().getName().equals("com.flint.tools.flintc.processing.JavacProcessingEnvironment"))
            throw new IllegalArgumentException();
        return getJavacTrees(ProcessingEnvironment.class, env);
    }

    static Trees getJavacTrees(Class<?> argType, Object arg) {
        try {
            ClassLoader cl = arg.getClass().getClassLoader();
            Class<?> c = Class.forName("com.flint.tools.flintc.api.JavacTrees", false, cl);
            argType = Class.forName(argType.getName(), false, cl);
            Method m = c.getMethod("instance", argType);
            return (Trees) m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a utility object for obtaining source positions.
     * @return the utility object for obtaining source positions
     */
    public abstract SourcePositions getSourcePositions();

    /**
     * Returns the Tree node for a given Element.
     * Returns {@code null} if the node can not be found.
     * @param element the element
     * @return the tree node
     */
    public abstract Tree getTree(Element element);

    /**
     * Returns the ClassTree node for a given TypeElement.
     * Returns {@code null} if the node can not be found.
     * @param element the element
     * @return the class tree node
     */
    public abstract ClassTree getTree(TypeElement element);

    /**
     * Returns the MethodTree node for a given ExecutableElement.
     * Returns {@code null} if the node can not be found.
     * @param method the executable element
     * @return the method tree node
     */
    public abstract MethodTree getTree(ExecutableElement method);

    /**
     * Returns the Tree node for an AnnotationMirror on a given Element.
     * Returns {@code null} if the node can not be found.
     * @param e the element
     * @param a the annotation mirror
     * @return the tree node
     */
    public abstract Tree getTree(Element e, AnnotationMirror a);

    /**
     * Returns the Tree node for an AnnotationValue for an AnnotationMirror on a given Element.
     * Returns {@code null} if the node can not be found.
     * @param e the element
     * @param a the annotation mirror
     * @param v the annotation value
     * @return the tree node
     */
    public abstract Tree getTree(Element e, AnnotationMirror a, AnnotationValue v);

    /**
     * Returns the path to tree node within the specified compilation unit.
     * @param unit the compilation unit
     * @param node the tree node
     * @return the tree path
     */
    public abstract TreePath getPath(CompilationUnitTree unit, Tree node);

    /**
     * Returns the TreePath node for a given Element.
     * Returns {@code null} if the node can not be found.
     * @param e the element
     * @return the tree path
     */
    public abstract TreePath getPath(Element e);

    /**
     * Returns the TreePath node for an AnnotationMirror on a given Element.
     * Returns {@code null} if the node can not be found.
     * @param e the element
     * @param a the annotation mirror
     * @return the tree path
     */
    public abstract TreePath getPath(Element e, AnnotationMirror a);

    /**
     * Returns the TreePath node for an AnnotationValue for an AnnotationMirror on a given Element.
     * Returns {@code null} if the node can not be found.
     * @param e the element
     * @param a the annotation mirror
     * @param v the annotation value
     * @return the tree path
     */
    public abstract TreePath getPath(Element e, AnnotationMirror a, AnnotationValue v);

    /**
     * Returns the Element for the Tree node identified by a given TreePath.
     * Returns {@code null} if the element is not available.
     * @param path the tree path
     * @return the element
     * @throws IllegalArgumentException is the TreePath does not identify
     * a Tree node that might have an associated Element.
     */
    public abstract Element getElement(TreePath path);

    /**
     * Returns the TypeMirror for the Tree node identified by a given TreePath.
     * Returns {@code null} if the TypeMirror is not available.
     * @param path the tree path
     * @return the type mirror
     * @throws IllegalArgumentException is the TreePath does not identify
     * a Tree node that might have an associated TypeMirror.
     */
    public abstract TypeMirror getTypeMirror(TreePath path);

    /**
     * Returns the Scope for the Tree node identified by a given TreePath.
     * Returns {@code null} if the Scope is not available.
     * @param path the tree path
     * @return the scope
     */
    public abstract Scope getScope(TreePath path);

    /**
     * Returns the doc comment, if any, for the Tree node identified by a given TreePath.
     * Returns {@code null} if no doc comment was found.
     * @see DocTrees#getDocCommentTree(TreePath)
     * @param path the tree path
     * @return the doc comment
     */
    public abstract String getDocComment(TreePath path);

    /**
     * Checks whether a given type is accessible in a given scope.
     * @param scope the scope to be checked
     * @param type the type to be checked
     * @return true if {@code type} is accessible
     */
    public abstract boolean isAccessible(Scope scope, TypeElement type);

    /**
     * Checks whether the given element is accessible as a member of the given
     * type in a given scope.
     * @param scope the scope to be checked
     * @param member the member to be checked
     * @param type the type for which to check if the member is accessible
     * @return true if {@code member} is accessible in {@code type}
     */
    public abstract boolean isAccessible(Scope scope, Element member, DeclaredType type);

    /**
      * Returns the original type from the ErrorType object.
      * @param errorType The errorType for which we want to get the original type.
      * @return javax.lang.model.type.TypeMirror corresponding to the original type, replaced by the ErrorType.
      */
    public abstract TypeMirror getOriginalType(ErrorType errorType);

    /**
     * Prints a message of the specified kind at the location of the
     * tree within the provided compilation unit
     *
     * @param kind the kind of message
     * @param msg  the message, or an empty string if none
     * @param t    the tree to use as a position hint
     * @param root the compilation unit that contains tree
     */
    public abstract void printMessage(Diagnostic.Kind kind, CharSequence msg,
            Tree t,
            CompilationUnitTree root);

    /**
     * Returns the lub of an exception parameter declared in a catch clause.
     * @param tree the tree for the catch clause
     * @return The lub of the exception parameter
     */
    public abstract TypeMirror getLub(CatchTree tree);
}
