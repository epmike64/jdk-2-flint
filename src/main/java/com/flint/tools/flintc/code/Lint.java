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

package com.flint.tools.flintc.code;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.flint.tools.flintc.main.Option;
import com.flint.tools.flintc.code.Symbol.*;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.JCList;
import com.flint.tools.flintc.util.Options;
import com.flint.tools.flintc.util.Pair;

/**
 * A class for handling -Xlint suboptions and @SuppresssWarnings.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Lint
{
    /** The context key for the root Lint object. */
    protected static final Context.Key<Lint> lintKey = new Context.Key<>();

    /** Get the root Lint instance. */
    public static Lint instance(Context context) {
        Lint instance = context.get(lintKey);
        if (instance == null)
            instance = new Lint(context);
        return instance;
    }

    /**
     * Returns the result of combining the values in this object with
     * the given annotation.
     */
    public Lint augment(Attribute.Compound attr) {
        return augmentor.augment(this, attr);
    }


    /**
     * Returns the result of combining the values in this object with
     * the metadata on the given symbol.
     */
    public Lint augment(Symbol sym) {
        Lint l = augmentor.augment(this, sym.getDeclarationAttributes());
        if (sym.isDeprecated()) {
            if (l == this)
                l = new Lint(this);
            l.values.remove(LintCategory.DEPRECATION);
            l.suppressedValues.add(LintCategory.DEPRECATION);
        }
        return l;
    }

    /**
     * Returns a new Lint that has the given LintCategorys suppressed.
     * @param lc one or more categories to be suppressed
     */
    public Lint suppress(LintCategory... lc) {
        Lint l = new Lint(this);
        l.values.removeAll(Arrays.asList(lc));
        l.suppressedValues.addAll(Arrays.asList(lc));
        return l;
    }

    private final AugmentVisitor augmentor;

    private final EnumSet<LintCategory> values;
    private final EnumSet<LintCategory> suppressedValues;

    private static final Map<String, LintCategory> map = new ConcurrentHashMap<>(20);

    protected Lint(Context context) {
        // initialize values according to the lint options
        Options options = Options.instance(context);

        if (options.isSet(Option.XLINT) || options.isSet(Option.XLINT_CUSTOM, "all")) {
            // If -Xlint or -Xlint:all is given, enable all categories by default
            values = EnumSet.allOf(LintCategory.class);
        } else if (options.isSet(Option.XLINT_CUSTOM, "none")) {
            // if -Xlint:none is given, disable all categories by default
            values = EnumSet.noneOf(LintCategory.class);
        } else {
            // otherwise, enable on-by-default categories
            values = EnumSet.noneOf(LintCategory.class);

            Source source = Source.instance(context);
            if (source.compareTo(Source.JDK1_9) >= 0) {
                values.add(LintCategory.DEP_ANN);
            }
            values.add(LintCategory.REQUIRES_TRANSITIVE_AUTOMATIC);
            values.add(LintCategory.OPENS);
            values.add(LintCategory.MODULE);
            values.add(LintCategory.REMOVAL);
        }

        // Look for specific overrides
        for (LintCategory lc : LintCategory.values()) {
            if (options.isSet(Option.XLINT_CUSTOM, lc.option)) {
                values.add(lc);
            } else if (options.isSet(Option.XLINT_CUSTOM, "-" + lc.option)) {
                values.remove(lc);
            }
        }

        suppressedValues = EnumSet.noneOf(LintCategory.class);

        context.put(lintKey, this);
        augmentor = new AugmentVisitor(context);
    }

    protected Lint(Lint other) {
        this.augmentor = other.augmentor;
        this.values = other.values.clone();
        this.suppressedValues = other.suppressedValues.clone();
    }

    @Override
    public String toString() {
        return "Lint:[values" + values + " suppressedValues" + suppressedValues + "]";
    }

    /**
     * Categories of warnings that can be generated by the compiler.
     */
    public enum LintCategory {
        /**
         * Warn when code refers to a auxiliary class that is hidden in a source file (ie source file name is
         * different from the class name, and the type is not properly nested) and the referring code
         * is not located in the same source file.
         */
        AUXILIARYCLASS("auxiliaryclass"),

        /**
         * Warn about use of unnecessary casts.
         */
        CAST("cast"),

        /**
         * Warn about issues related to classfile contents
         */
        CLASSFILE("classfile"),

        /**
         * Warn about use of deprecated items.
         */
        DEPRECATION("deprecation"),

        /**
         * Warn about items which are documented with an {@code @deprecated} JavaDoc
         * comment, but which do not have {@code @Deprecated} annotation.
         */
        DEP_ANN("dep-ann"),

        /**
         * Warn about division by constant integer 0.
         */
        DIVZERO("divzero"),

        /**
         * Warn about empty statement after if.
         */
        EMPTY("empty"),

        /**
         * Warn about issues regarding module exports.
         */
        EXPORTS("exports"),

        /**
         * Warn about falling through from one case of a switch statement to the next.
         */
        FALLTHROUGH("fallthrough"),

        /**
         * Warn about finally clauses that do not terminate normally.
         */
        FINALLY("finally"),

        /**
         * Warn about module system related issues.
         */
        MODULE("module"),

        /**
         * Warn about issues regarding module opens.
         */
        OPENS("opens"),

        /**
         * Warn about issues relating to use of command line options
         */
        OPTIONS("options"),

        /**
         * Warn about issues regarding method overloads.
         */
        OVERLOADS("overloads"),

        /**
         * Warn about issues regarding method overrides.
         */
        OVERRIDES("overrides"),

        /**
         * Warn about invalid path elements on the command line.
         * Such warnings cannot be suppressed with the SuppressWarnings
         * annotation.
         */
        PATH("path"),

        /**
         * Warn about issues regarding annotation processing.
         */
        PROCESSING("processing"),

        /**
         * Warn about unchecked operations on raw types.
         */
        RAW("rawtypes"),

        /**
         * Warn about use of deprecated-for-removal items.
         */
        REMOVAL("removal"),

        /**
         * Warn about use of automatic modules in the requires clauses.
         */
        REQUIRES_AUTOMATIC("requires-automatic"),

        /**
         * Warn about automatic modules in requires transitive.
         */
        REQUIRES_TRANSITIVE_AUTOMATIC("requires-transitive-automatic"),

        /**
         * Warn about Serializable classes that do not provide a serial version ID.
         */
        SERIAL("serial"),

        /**
         * Warn about issues relating to use of statics
         */
        STATIC("static"),

        /**
         * Warn about issues relating to use of try blocks (i.e. try-with-resources)
         */
        TRY("try"),

        /**
         * Warn about unchecked operations on raw types.
         */
        UNCHECKED("unchecked"),

        /**
         * Warn about potentially unsafe vararg methods
         */
        VARARGS("varargs");

        LintCategory(String option) {
            this(option, false);
        }

        LintCategory(String option, boolean hidden) {
            this.option = option;
            this.hidden = hidden;
            map.put(option, this);
        }

        static LintCategory get(String option) {
            return map.get(option);
        }

        public final String option;
        public final boolean hidden;
    }

    /**
     * Checks if a warning category is enabled. A warning category may be enabled
     * on the command line, or by default, and can be temporarily disabled with
     * the SuppressWarnings annotation.
     */
    public boolean isEnabled(LintCategory lc) {
        return values.contains(lc);
    }

    /**
     * Checks is a warning category has been specifically suppressed, by means
     * of the SuppressWarnings annotation, or, in the case of the deprecated
     * category, whether it has been implicitly suppressed by virtue of the
     * current entity being itself deprecated.
     */
    public boolean isSuppressed(LintCategory lc) {
        return suppressedValues.contains(lc);
    }

    protected static class AugmentVisitor implements Attribute.Visitor {
        private final Context context;
        private Symtab syms;
        private Lint parent;
        private Lint lint;

        AugmentVisitor(Context context) {
            // to break an ugly sequence of initialization dependencies,
            // we defer the initialization of syms until it is needed
            this.context = context;
        }

        Lint augment(Lint parent, Attribute.Compound attr) {
            initSyms();
            this.parent = parent;
            lint = null;
            attr.accept(this);
            return (lint == null ? parent : lint);
        }

        Lint augment(Lint parent, JCList<Attribute.Compound> attrs) {
            initSyms();
            this.parent = parent;
            lint = null;
            for (Attribute.Compound a: attrs) {
                a.accept(this);
            }
            return (lint == null ? parent : lint);
        }

        private void initSyms() {
            if (syms == null)
                syms = Symtab.instance(context);
        }

        private void suppress(LintCategory lc) {
            if (lint == null)
                lint = new Lint(parent);
            lint.suppressedValues.add(lc);
            lint.values.remove(lc);
        }

        public void visitConstant(Attribute.Constant value) {
            if (value.type.tsym == syms.stringType.tsym) {
                LintCategory lc = LintCategory.get((String) (value.value));
                if (lc != null)
                    suppress(lc);
            }
        }

        public void visitClass(Attribute.Class clazz) {
        }

        // If we find a @SuppressWarnings annotation, then we continue
        // walking the tree, in order to suppress the individual warnings
        // specified in the @SuppressWarnings annotation.
        public void visitCompound(Attribute.Compound compound) {
            if (compound.type.tsym == syms.suppressWarningsType.tsym) {
                for (JCList<Pair<MethodSymbol,Attribute>> v = compound.values;
                     v.nonEmpty(); v = v.tail) {
                    Pair<MethodSymbol,Attribute> value = v.head;
                    if (value.fst.name.toString().equals("value"))
                        value.snd.accept(this);
                }

            }
        }

        public void visitArray(Attribute.Array array) {
            for (Attribute value : array.values)
                value.accept(this);
        }

        public void visitEnum(Attribute.Enum e) {
        }

        public void visitError(Attribute.Error e) {
        }
    }
}
