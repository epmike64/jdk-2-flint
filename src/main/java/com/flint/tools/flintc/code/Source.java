/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

import com.flint.tools.flintc.jvm.Target;
import com.flint.tools.flintc.util.*;
import static com.flint.tools.flintc.main.Option.*;

/** The source language version accepted.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum Source {
    /** 1.0 had no inner classes, and so could not pass the JCK. */
    // public static final Source JDK1_0 =              new Source("1.0");

    /** 1.1 did not have strictfp, and so could not pass the JCK. */
    // public static final Source JDK1_1 =              new Source("1.1");

    /** 1.2 introduced strictfp. */
    JDK1_2("1.2"),

    /** 1.3 is the same language as 1.2. */
    JDK1_3("1.3"),

    /** 1.4 introduced assert. */
    JDK1_4("1.4"),

    /** 1.5 introduced generics, attributes, foreach, boxing, static import,
     *  covariant return, enums, varargs, et al. */
    JDK1_5("1.5"),

    /** 1.6 reports encoding problems as errors instead of warnings. */
    JDK1_6("1.6"),

    /** 1.7 introduced try-with-resources, multi-catch, string switch, etc. */
    JDK1_7("1.7"),

    /** 1.8 lambda expressions and default methods. */
    JDK1_8("1.8"),

    /** 1.9 covers the to be determined language features that will be added in JDK 9. */
    JDK1_9("1.9");

    private static final Context.Key<Source> sourceKey = new Context.Key<>();

    public static Source instance(Context context) {
        Source instance = context.get(sourceKey);
        if (instance == null) {
            Options options = Options.instance(context);
            String sourceString = options.get(SOURCE);
            if (sourceString != null) instance = lookup(sourceString);
            if (instance == null) instance = DEFAULT;
            context.put(sourceKey, instance);
        }
        return instance;
    }

    public final String name;

    private static final Map<String,Source> tab = new HashMap<>();
    static {
        for (Source s : values()) {
            tab.put(s.name, s);
        }
        tab.put("5", JDK1_5); // Make 5 an alias for 1.5
        tab.put("6", JDK1_6); // Make 6 an alias for 1.6
        tab.put("7", JDK1_7); // Make 7 an alias for 1.7
        tab.put("8", JDK1_8); // Make 8 an alias for 1.8
        tab.put("9", JDK1_9); // Make 9 an alias for 1.9
    }

    private Source(String name) {
        this.name = name;
    }

    public static final Source MIN = Source.JDK1_6;

    private static final Source MAX = values()[values().length - 1];

    public static final Source DEFAULT = MAX;

    public static Source lookup(String name) {
        return tab.get(name);
    }

    public Target requiredTarget() {
        if (this.compareTo(JDK1_9) >= 0) return Target.JDK1_9;
        if (this.compareTo(JDK1_8) >= 0) return Target.JDK1_8;
        if (this.compareTo(JDK1_7) >= 0) return Target.JDK1_7;
        if (this.compareTo(JDK1_6) >= 0) return Target.JDK1_6;
        if (this.compareTo(JDK1_5) >= 0) return Target.JDK1_5;
        if (this.compareTo(JDK1_4) >= 0) return Target.JDK1_4;
        return Target.JDK1_1;
    }

    public boolean allowDiamond() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowMulticatch() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowImprovedRethrowAnalysis() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowImprovedCatchAnalysis() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowModules() {
        return compareTo(JDK1_9) >= 0;
    }
    public boolean allowTryWithResources() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowEffectivelyFinalVariablesInTryWithResources() {
        return compareTo(JDK1_9) >= 0;
    }
    public boolean allowBinaryLiterals() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowUnderscoresInLiterals() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowStringsInSwitch() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowDeprecationOnImport() {
        return compareTo(JDK1_9) < 0;
    }
    public boolean allowSimplifiedVarargs() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowObjectToPrimitiveCast() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean enforceThisDotInit() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowPoly() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowLambda() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowMethodReferences() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowDefaultMethods() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowStaticInterfaceMethods() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowStrictMethodClashCheck() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowEffectivelyFinalInInnerClasses() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowTypeAnnotations() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowAnnotationsAfterTypeParams() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowRepeatedAnnotations() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowIntersectionTypesInCast() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowGraphInference() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowFunctionalInterfaceMostSpecific() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean allowPostApplicabilityVarargsAccessCheck() {
        return compareTo(JDK1_8) >= 0;
    }
    public boolean mapCapturesToBounds() {
        return compareTo(JDK1_8) < 0;
    }
    public boolean allowPrivateSafeVarargs() {
        return compareTo(JDK1_9) >= 0;
    }
    public boolean allowDiamondWithAnonymousClassCreation() {
        return compareTo(JDK1_9) >= 0;
    }
    public boolean allowUnderscoreIdentifier() {
        return compareTo(JDK1_8) <= 0;
    }
    public boolean allowPrivateInterfaceMethods() { return compareTo(JDK1_9) >= 0; }
    public static SourceVersion toSourceVersion(Source source) {
        switch(source) {
        case JDK1_2:
            return RELEASE_2;
        case JDK1_3:
            return RELEASE_3;
        case JDK1_4:
            return RELEASE_4;
        case JDK1_5:
            return RELEASE_5;
        case JDK1_6:
            return RELEASE_6;
        case JDK1_7:
            return RELEASE_7;
        case JDK1_8:
            return RELEASE_8;
        case JDK1_9:
            return RELEASE_9;
        default:
            return null;
        }
    }
}
