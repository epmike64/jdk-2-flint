/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.parser;

import com.flint.tools.flintc.parser.Token;
import com.flint.tools.flintc.util.Position.LineMap;

/**
 * The lexical analyzer maps an input stream consisting of ASCII
 * characters and Unicode escapes into a token sequence.

 */
public interface Lexer {

    /**
     * Consume the next token.
     */
    void nextToken();

    /**
     * Return current token.
     */
    Token token();

    /**
     * Return token with given lookahead.
     */
    Token token(int lookahead);

    /**
     * Return the last character position of the previous token.
     */
    Token prevToken();

    /**
     * Splits the current token in two and return the first (splitted) token.
     * For instance {@literal '<<<'} is split into two tokens
     * {@literal '<'} and {@literal '<<'} respectively,
     * and the latter is returned.
     */
    Token split();

    /**
     * Return the position where a lexical error occurred;
     */
    int errPos();

    /**
     * Set the position where a lexical error occurred;
     */
    void errPos(int pos);

    /**
     * Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap
     */
    LineMap getLineMap();
}
