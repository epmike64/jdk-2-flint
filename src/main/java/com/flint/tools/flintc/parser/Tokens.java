/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.flint.tools.flintc.util.*;



/** A class that defines codes/utilities for Java source tokens
 *  returned from lexical analysis.
 */
public class Tokens {

    private final Names names;

    /**
     * Keyword array. Maps name indices to Token.
     */
    private final TokenKind[] key;

    /**  The number of the last entered keyword.
     */
    private int maxKey = 0;

    /** The names of all tokens.
     */
    private Name[] tokenName = new Name[TokenKind.values().length];

    public static final Context.Key<Tokens> tokensKey = new Context.Key<>();

    public static Tokens instance(Context context) {
        Tokens instance = context.get(tokensKey);
        if (instance == null)
            instance = new Tokens(context);
        return instance;
    }

    public interface Comment {

        enum CommentStyle {
            LINE,
            BLOCK,
            JAVADOC,
        }

        String getText();
        int getSourcePos(int index);
        CommentStyle getStyle();
        boolean isDeprecated();
    }

    protected Tokens(Context context) {
        context.put(tokensKey, this);
        names = Names.instance(context);
        for (TokenKind t : TokenKind.values()) {
            if (t.name != null)
                enterKeyword(t.name, t);
            else
                tokenName[t.ordinal()] = null;
        }

        key = new TokenKind[maxKey+1];
        for (int i = 0; i <= maxKey; i++) key[i] = TokenKind.IDENTIFIER;
        for (TokenKind t : TokenKind.values()) {
            if (t.name != null)
            key[tokenName[t.ordinal()].getIndex()] = t;
        }
    }

    private void enterKeyword(String s, TokenKind token) {
        Name n = names.fromString(s);
        tokenName[token.ordinal()] = n;
        if (n.getIndex() > maxKey) maxKey = n.getIndex();
    }

    /**
     * Create a new token given a name; if the name corresponds to a token name,
     * a new token of the corresponding kind is returned; otherwise, an
     * identifier token is returned.
     */
    TokenKind lookupKind(Name name) {
        return (name.getIndex() > maxKey) ? TokenKind.IDENTIFIER : key[name.getIndex()];
    }

    TokenKind lookupKind(String name) {
        return lookupKind(names.fromString(name));
    }


    final static class NamedToken extends Token {
        /** The name of this token */
        public final Name name;

        public NamedToken(TokenKind kind, int pos, int endPos, Name name) {
            super(kind, pos, endPos);
            this.name = name;
        }

        protected void checkKind() {
            if (kind.tag != TokenTag.NAMED) {
                throw new AssertionError("Bad token kind - expected " + com.flint.tools.flintc.parser.TokenTag.NAMED);
            }
        }

        @Override
        public Name name() {
            return name;
        }
    }

    static class StringToken extends Token {
        /** The string value of this token */
        public final String stringVal;

        public StringToken(TokenKind kind, int pos, int endPos, String stringVal) {
            super(kind, pos, endPos);
            this.stringVal = stringVal;
        }

        protected void checkKind() {
            if (kind.tag != TokenTag.STRING) {
                throw new AssertionError("Bad token kind - expected " + com.flint.tools.flintc.parser.TokenTag.STRING);
            }
        }

        @Override
        public String stringVal() {
            return stringVal;
        }
    }

    final static class NumericToken extends StringToken {
        /** The 'radix' value of this token */
        public final int radix;

        public NumericToken(TokenKind kind, int pos, int endPos, String stringVal, int radix) {
            super(kind, pos, endPos, stringVal);
            this.radix = radix;
        }

        protected void checkKind() {
            if (kind.tag != TokenTag.NUMERIC) {
                throw new AssertionError("Bad token kind - expected " + com.flint.tools.flintc.parser.TokenTag.NUMERIC);
            }
        }

        @Override
        public int radix() {
            return radix;
        }
    }

    public static final Token DUMMY =
                new Token(TokenKind.ERROR, 0, 0);
}
