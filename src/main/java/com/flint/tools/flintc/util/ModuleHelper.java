/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.util;

import com.flint.tools.flintc.util.JDK9Wrappers;

public class ModuleHelper {

    private static final String[] javacInternalPackages = new String[] {
            "com.flint.tools.flintc.api",
            "com.flint.tools.flintc.code",
            "com.flint.tools.flintc.comp",
            "com.flint.tools.flintc.file",
            "com.flint.tools.flintc.jvm",
            "com.flint.tools.flintc.main",
            "com.flint.tools.flintc.model",
            "com.flint.tools.flintc.parser",
            "com.flint.tools.flintc.platform",
            "com.flint.tools.flintc.processing",
            "com.flint.tools.flintc.tree",
            "com.flint.tools.flintc.util",

            "com.flint.tools.doclint",
    };

    public static void addExports(JDK9Wrappers.Module  from, JDK9Wrappers.Module  to) {
        for (String pack: javacInternalPackages) {
            from.addExports(pack, to);
        }
    }
}

