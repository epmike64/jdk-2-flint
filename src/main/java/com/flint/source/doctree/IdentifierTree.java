/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.source.doctree;

import javax.lang.model.element.Name;

/**
 * An identifier in a documentation comment.
 *
 * <p>
 * name
 *
 * @since 1.8
 */
public interface IdentifierTree extends DocTree {
    /**
     * Returns the name of the identifier.
     * @return the name
     */
    Name getName();
}
