package com.flint.tools.flintc.parser;

import com.flint.tools.flintc.util.Filter;

/**
 * This enum defines all tokens used by the javac scanner. A token is
 * optionally associated with a name.
 */
public enum TokenKind implements Filter<TokenKind> {
	EOF(),
	ERROR(),
	IDENTIFIER(TokenTag.NAMED),
	ABSTRACT("abstract"),
	ASSERT("assert", TokenTag.NAMED),
	BOOLEAN("boolean", TokenTag.NAMED),
	BREAK("break"),
	BYTE("byte", TokenTag.NAMED),
	CASE("case"),
	CATCH("catch"),
	CHAR("char", TokenTag.NAMED),
	CLASS("class"),
	CONST("const"),
	CONTINUE("continue"),
	DEFAULT("default"),
	DO("do"),
	DOUBLE("double", TokenTag.NAMED),
	ELSE("else"),
	ENUM("enum", TokenTag.NAMED),
	EXTENDS("extends"),
	FINAL("final"),
	FINALLY("finally"),
	FLOAT("float", TokenTag.NAMED),
	FOR("for"),
	GOTO("goto"),
	IF("if"),
	IMPLEMENTS("implements"),
	IMPORT("import"),
	INSTANCEOF("instanceof"),
	INT("int", TokenTag.NAMED),
	INTERFACE("interface"),
	LONG("long", TokenTag.NAMED),
	NATIVE("native"),
	NEW("new"),
	PACKAGE("package"),
	PRIVATE("private"),
	PROTECTED("protected"),
	PUBLIC("public"),
	RETURN("return"),
	SHORT("short", TokenTag.NAMED),
	STATIC("static"),
	STRICTFP("strictfp"),
	SUPER("super", TokenTag.NAMED),
	SWITCH("switch"),
	SYNCHRONIZED("synchronized"),
	THIS("this", TokenTag.NAMED),
	THROW("throw"),
	THROWS("throws"),
	TRANSIENT("transient"),
	TRY("try"),
	VOID("void", TokenTag.NAMED),
	VOLATILE("volatile"),
	WHILE("while"),
	INTLITERAL(TokenTag.NUMERIC),
	LONGLITERAL(TokenTag.NUMERIC),
	FLOATLITERAL(TokenTag.NUMERIC),
	DOUBLELITERAL(TokenTag.NUMERIC),
	CHARLITERAL(TokenTag.NUMERIC),
	STRINGLITERAL(TokenTag.STRING),
	TRUE("true", TokenTag.NAMED),
	FALSE("false", TokenTag.NAMED),
	NULL("null", TokenTag.NAMED),
	UNDERSCORE("_", TokenTag.NAMED),
	ARROW("->"),
	COLCOL("::"),
	LPAREN("("),
	RPAREN(")"),
	LBRACE("{"),
	RBRACE("}"),
	LBRACKET("["),
	RBRACKET("]"),
	SEMI(";"),
	COMMA(","),
	DOT("."),
	ELLIPSIS("..."),
	EQ("="),
	GT(">"),
	LT("<"),
	BANG("!"),
	TILDE("~"),
	QUES("?"),
	COLON(":"),
	EQEQ("=="),
	LTEQ("<="),
	GTEQ(">="),
	BANGEQ("!="),
	AMPAMP("&&"),
	BARBAR("||"),
	PLUSPLUS("++"),
	SUBSUB("--"),
	PLUS("+"),
	SUB("-"),
	STAR("*"),
	SLASH("/"),
	AMP("&"),
	BAR("|"),
	CARET("^"),
	PERCENT("%"),
	LTLT("<<"),
	GTGT(">>"),
	GTGTGT(">>>"),
	PLUSEQ("+="),
	SUBEQ("-="),
	STAREQ("*="),
	SLASHEQ("/="),
	AMPEQ("&="),
	BAREQ("|="),
	CARETEQ("^="),
	PERCENTEQ("%="),
	LTLTEQ("<<="),
	GTGTEQ(">>="),
	GTGTGTEQ(">>>="),
	MONKEYS_AT("@"),
	CUSTOM;

	public final String name;
	public final TokenTag tag;

	TokenKind() {
		this(null, TokenTag.DEFAULT);
	}

	TokenKind(String name) {
		this(name, TokenTag.DEFAULT);
	}

	TokenKind(TokenTag tag) {
		this(null, tag);
	}

	TokenKind(String name, TokenTag tag) {
		this.name = name;
		this.tag = tag;
	}

	public String toString() {
		switch (this) {
			case IDENTIFIER:
				return "token.identifier";
			case CHARLITERAL:
				return "token.character";
			case STRINGLITERAL:
				return "token.string";
			case INTLITERAL:
				return "token.integer";
			case LONGLITERAL:
				return "token.long-integer";
			case FLOATLITERAL:
				return "token.float";
			case DOUBLELITERAL:
				return "token.double";
			case ERROR:
				return "token.bad-symbol";
			case EOF:
				return "token.end-of-input";
			case DOT: case COMMA: case SEMI: case LPAREN: case RPAREN:
			case LBRACKET: case RBRACKET: case LBRACE: case RBRACE:
				return "'" + name + "'";
			default:
				return name;
		}
	}

	public String getKind() {
		return "Token";
	}

//        public String toString(Locale locale, Messages messages) {
//            return name != null ? toString() : messages.getLocalizedString(locale, "compiler.misc." + toString());
//        }

	@Override
	public boolean accepts(TokenKind that) {
		return this == that;
	}
}
