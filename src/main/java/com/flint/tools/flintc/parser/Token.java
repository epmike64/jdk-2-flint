package com.flint.tools.flintc.parser;

import com.flint.tools.flintc.util.Name;

/**
 * This is the class representing a javac token. Each token has several fields
 * that are set by the javac lexer (i.e. start/end position, string value, etc).
 */
public class Token {


	/** The token kind */
	public final TokenKind kind;

	/** The start position of this token */
	public final int pos;

	/** The end position of this token */
	public final int endPos;

	Token(TokenKind kind, int pos, int endPos) {
		this.kind = kind;
		this.pos = pos;
		this.endPos = endPos;
		checkKind();
	}

	Token[] split(Tokens tokens) {
		if (kind.name.length() < 2 || kind.tag != TokenTag.DEFAULT) {
			throw new AssertionError("Cant split" + kind);
		}

		TokenKind t1 = tokens.lookupKind(kind.name.substring(0, 1));
		TokenKind t2 = tokens.lookupKind(kind.name.substring(1));

		if (t1 == null || t2 == null) {
			throw new AssertionError("Cant split - bad subtokens");
		}
		return new Token[] {
				new Token(t1, pos, pos + t1.name.length()),
				new Token(t2, pos + t1.name.length(), endPos)
		};
	}

	protected void checkKind() {
		if (kind.tag != TokenTag.DEFAULT) {
			throw new AssertionError("Bad token kind - expected " + TokenTag.STRING);
		}
	}

	public Name name() {
		throw new UnsupportedOperationException();
	}

	public String stringVal() {
		throw new UnsupportedOperationException();
	}

	public int radix() {
		throw new UnsupportedOperationException();
	}
}