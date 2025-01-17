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

import com.flint.source.tree.CompilationUnitTree;

import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * Provides details about work that has been done by the JDK Java Compiler, javac.
 *
 * @author Jonathan Gibbons
 * @since 1.6
 */
public final class TaskEvent
{
    /**
     * Kind of task event.
     * @since 1.6
     */
    public enum Kind {
        /**
         * For events related to the parsing of a file.
         */
        PARSE,
        /**
         * For events relating to elements being entered.
         **/
        ENTER,
        /**
         * For events relating to elements being analyzed for errors.
         **/
        ANALYZE,
        /**
         * For events relating to class files being generated.
         **/
        GENERATE,
        /**
         * For events relating to overall annotation processing.
         **/
        ANNOTATION_PROCESSING,
        /**
         * For events relating to an individual annotation processing round.
         **/
        ANNOTATION_PROCESSING_ROUND,
        /**
         * Sent before parsing first source file, and after writing the last output file.
         * This event is not sent when using {@link JavacTask#parse()},
         * {@link JavacTask#analyze()} or {@link JavacTask#generate()}.
         *
         * @since 9
         */
        COMPILATION,
    }

    /**
     * Creates a task event for a given kind.
     * The source file, compilation unit and type element
     * are all set to {@code null}.
     * @param kind the kind of the event
     */
    public TaskEvent(Kind kind) {
        this(kind, null, null, null);
    }

    /**
     * Creates a task event for a given kind and source file.
     * The compilation unit and type element are both set to {@code null}.
     * @param kind the kind of the event
     * @param sourceFile the source file
     */
    public TaskEvent(Kind kind, JavaFileObject sourceFile) {
        this(kind, sourceFile, null, null);
    }

    /**
     * Creates a task event for a given kind and compilation unit.
     * The source file is set from the compilation unit,
     * and the type element is set to {@code null}.
     * @param kind the kind of the event
     * @param unit the compilation unit
     */
    public TaskEvent(Kind kind, CompilationUnitTree unit) {
        this(kind, unit.getSourceFile(), unit, null);
    }

    /**
     * Creates a task event for a given kind, compilation unit
     * and type element.
     * The source file is set from the compilation unit.
     * @param kind the kind of the event
     * @param unit the compilation unit
     * @param clazz the type element
     */
    public TaskEvent(Kind kind, CompilationUnitTree unit, TypeElement clazz) {
        this(kind, unit.getSourceFile(), unit, clazz);
    }

    private TaskEvent(Kind kind, JavaFileObject file, CompilationUnitTree unit, TypeElement clazz) {
        this.kind = kind;
        this.file = file;
        this.unit = unit;
        this.clazz = clazz;
    }

    /**
     * Returns the kind for this event.
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the source file for this event.
     * May be {@code null}.
     * @return the source file
     */
    public JavaFileObject getSourceFile() {
        return file;
    }

    /**
     * Returns the compilation unit for this event.
     * May be {@code null}.
     * @return the compilation unit
     */
    public CompilationUnitTree getCompilationUnit() {
        return unit;
    }

    /**
     * Returns the type element for this event.
     * May be {@code null}.
     * @return the type element
     */
    public TypeElement getTypeElement() {
        return clazz;
    }

    @Override
    public String toString() {
        return "TaskEvent["
            + kind + ","
            + file + ","
            // the compilation unit is identified by the file
            + clazz + "]";
    }

    private Kind kind;
    private JavaFileObject file;
    private CompilationUnitTree unit;
    private TypeElement clazz;
}
