/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.doclint;


import com.flint.source.doctree.DocCommentTree;
import com.flint.source.tree.CompilationUnitTree;
import com.flint.source.util.DocTrees;
import com.flint.source.util.JavacTask;
import com.flint.source.util.SourcePositions;
import com.flint.source.util.TreePath;
import com.flint.tools.flintc.model.JavacTypes;
import com.flint.tools.flintc.tree.JCTree;
import com.flint.tools.flintc.util.MatchingUtils;
import com.flint.tools.flintc.util.StringUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility container for current execution environment,
 * providing the current declaration and its doc comment.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Env {
    /**
     * Access kinds for declarations.
     */
    public enum AccessKind {
        PRIVATE,
        PACKAGE,
        PROTECTED,
        PUBLIC;

        static boolean accepts(String opt) {
            for (AccessKind g: values())
                if (opt.equals(StringUtils.toLowerCase(g.name()))) return true;
            return false;
        }

        static AccessKind of(Set<Modifier> mods) {
            if (mods.contains(Modifier.PUBLIC))
                return AccessKind.PUBLIC;
            else if (mods.contains(Modifier.PROTECTED))
                return AccessKind.PROTECTED;
            else if (mods.contains(Modifier.PRIVATE))
                return AccessKind.PRIVATE;
            else
                return AccessKind.PACKAGE;
        }
    }

    /** Message handler. */
    final Messages messages;

    int implicitHeaderLevel = 0;

    Set<String> customTags;

    Set<Pattern> includePackages;
    Set<Pattern> excludePackages;

    HtmlVersion htmlVersion = HtmlVersion.HTML4;

    // Utility classes
    DocTrees trees;
    Elements elements;
    Types types;

    // Types used when analysing doc comments.
    TypeMirror java_lang_Error;
    TypeMirror java_lang_RuntimeException;
    TypeMirror java_lang_Throwable;
    TypeMirror java_lang_Void;

    /** The path for the declaration containing the comment currently being analyzed. */
    TreePath currPath;
    /** The element for the declaration containing the comment currently being analyzed. */
    Element currElement;
    /** The comment current being analyzed. */
    DocCommentTree currDocComment;
    /**
     * The access kind of the declaration containing the comment currently being analyzed.
     * This is the minimum (most restrictive) access kind of the declaration itself
     * and that of its containers. For example, a public method in a private class is
     * noted as private.
     */
    AccessKind currAccess;
    /** The set of methods, if any, that the current declaration overrides. */
    Set<? extends ExecutableElement> currOverriddenMethods;

    Env() {
        messages = new Messages(this);
    }

    void init(JavacTask task) {
        init(DocTrees.instance(task), task.getElements(), task.getTypes());
    }

    void init(DocTrees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
    }

    void initTypes() {
        if (java_lang_Error != null)
            return ;

        java_lang_Error = elements.getTypeElement("java.lang.Error").asType();
        java_lang_RuntimeException = elements.getTypeElement("java.lang.RuntimeException").asType();
        java_lang_Throwable = elements.getTypeElement("java.lang.Throwable").asType();
        java_lang_Void = elements.getTypeElement("java.lang.Void").asType();
    }

    void setImplicitHeaders(int n) {
        implicitHeaderLevel = n;
    }

    void setCustomTags(String cTags) {
        customTags = new LinkedHashSet<>();
        for (String s : cTags.split(DocLint.SEPARATOR)) {
            if (!s.isEmpty())
                customTags.add(s);
        }
    }

    void setCheckPackages(String packages) {
        includePackages = new HashSet<>();
        excludePackages = new HashSet<>();
        for (String pack : packages.split(DocLint.SEPARATOR)) {
            boolean excluded = false;
            if (pack.startsWith("-")) {
                pack = pack.substring(1);
                excluded = true;
            }
            if (pack.isEmpty())
                continue;
            Pattern pattern = MatchingUtils.validImportStringToPattern(pack);
            if (excluded) {
                excludePackages.add(pattern);
            } else {
                includePackages.add(pattern);
            }
        }
    }

    static boolean validatePackages(String packages) {
        for (String pack : packages.split(DocLint.SEPARATOR)) {
            if (pack.startsWith("-")) {
                pack = pack.substring(1);
            }
            if (!pack.isEmpty() && !MatchingUtils.isValidImportString(pack))
                return false;
        }
        return true;
    }

    void setHtmlVersion(HtmlVersion version) {
        htmlVersion = version;
    }

    /** Set the current declaration and its doc comment. */
    void setCurrent(TreePath path, DocCommentTree comment) {
        currPath = path;
        currDocComment = comment;
        currElement = trees.getElement(currPath);
        currOverriddenMethods = ((JavacTypes) types).getOverriddenMethods(currElement);

        AccessKind ak = AccessKind.PUBLIC;
        for (TreePath p = path; p != null; p = p.getParentPath()) {
            Element e = trees.getElement(p);
            if (e != null && e.getKind() != ElementKind.PACKAGE && e.getKind() != ElementKind.MODULE) {
                ak = min(ak, AccessKind.of(e.getModifiers()));
            }
        }
        currAccess = ak;
    }

    AccessKind getAccessKind() {
        return currAccess;
    }

    long getPos(TreePath p) {
        return ((JCTree) p.getLeaf()).pos;
    }

    long getStartPos(TreePath p) {
        SourcePositions sp = trees.getSourcePositions();
        return sp.getStartPosition(p.getCompilationUnit(), p.getLeaf());
    }

    boolean shouldCheck(CompilationUnitTree unit) {
        if (includePackages == null)
            return true;

        String packageName =   unit.getPackageName() != null
                             ? unit.getPackageName().toString()
                             : "";

        if (!includePackages.isEmpty()) {
            boolean included = false;
            for (Pattern pack : includePackages) {
                if (pack.matcher(packageName).matches()) {
                    included = true;
                    break;
                }
            }
            if (!included)
                return false;
        }

        for (Pattern pack : excludePackages) {
            if (pack.matcher(packageName).matches()) {
                return false;
            }
        }

        return true;
    }

    private <T extends Comparable<T>> T min(T item1, T item2) {
        return (item1 == null) ? item2
                : (item2 == null) ? item1
                : item1.compareTo(item2) <= 0 ? item1 : item2;
    }
}
