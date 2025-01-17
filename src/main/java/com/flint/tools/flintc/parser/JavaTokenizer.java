/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.CharBuffer;

import static com.flint.tools.flintc.parser.Tokens.*;
import static com.flint.tools.flintc.util.LayoutCharacters.*;

/** The lexical analyzer maps an input stream consisting of
 *  ASCII characters and Unicode escapes into a token sequence.
 */
public class JavaTokenizer {

    private static final boolean scannerDebug = false;

    /** Allow binary literals.
     */
    private boolean allowBinaryLiterals;

    /** Allow underscores in literals.
     */
    private boolean allowUnderscoresInLiterals;

    /** The source language setting.
     */
//    private Source source;

    /** The token factory. */
    private final Tokens tokens;

    /** The token kind, set by nextToken().
     */
    protected TokenKind tk;

    /** The token's radix, set by nextToken().
     */
    protected int radix;

    /** The token's name, set by nextToken().
     */
    protected Name name;

    /** The position where a lexical error occurred;
     */
    protected int errPos = Position.NOPOS;

    /** The Unicode reader (low-level stream reader).
     */
    protected UnicodeReader reader;

    protected ScannerFactory fac;

    private static final boolean hexFloatsWork = hexFloatsWork();
    private static boolean hexFloatsWork() {
        try {
            Float.valueOf("0x1.0p1");
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Create a scanner from the input array.  This method might
     * modify the array.  To avoid copying the input array, ensure
     * that {@code inputLength < input.length} or
     * {@code input[input.length -1]} is a white space character.
     *
     * @param fac the factory which created this Scanner
     * @param buf the input, might be modified
     * Must be positive and less than or equal to input.length.
     */
    protected JavaTokenizer(ScannerFactory fac, CharBuffer buf) {
        this(fac, new UnicodeReader(fac, buf));
    }

    protected JavaTokenizer(ScannerFactory fac, char[] buf, int inputLength) {
        this(fac, new UnicodeReader(fac, buf, inputLength));
    }

    protected JavaTokenizer(ScannerFactory fac, UnicodeReader reader) {
        this.fac = fac;
        this.tokens = fac.tokens;
//        this.source = fac.source;
        this.reader = reader;
        this.allowBinaryLiterals = false; //source.allowBinaryLiterals();
        this.allowUnderscoresInLiterals = false; //source.allowUnderscoresInLiterals();
    }

      /** Read next token.
       * Entry point for the scanner.
      */
    public Token readToken() {

        reader.sp = 0;
        name = null;
        radix = 0;

        int pos = 0;
        int endPos = 0;


        try {
            loop: while (true) {
                pos = reader.bp;
                switch (reader.ch) {
                    case ' ': // (Spec 3.6)
                    case '\t': // (Spec 3.6)
                    case FF: // (Spec 3.6)
                        do {
                            reader.scanChar();
                        } while (reader.ch == ' ' || reader.ch == '\t' || reader.ch == FF);
                        processWhiteSpace(pos, reader.bp);
                        break;
                    case LF: // (Spec 3.4)
                        reader.scanChar();
                        processLineTerminator(pos, reader.bp);
                        break;
                    case CR: // (Spec 3.4)
                        reader.scanChar();
                        if (reader.ch == LF) {
                            reader.scanChar();
                        }
                        processLineTerminator(pos, reader.bp);
                        break;
                    case 'A': case 'B': case 'C': case 'D': case 'E':
                    case 'F': case 'G': case 'H': case 'I': case 'J':
                    case 'K': case 'L': case 'M': case 'N': case 'O':
                    case 'P': case 'Q': case 'R': case 'S': case 'T':
                    case 'U': case 'V': case 'W': case 'X': case 'Y':
                    case 'Z':
                    case 'a': case 'b': case 'c': case 'd': case 'e':
                    case 'f': case 'g': case 'h': case 'i': case 'j':
                    case 'k': case 'l': case 'm': case 'n': case 'o':
                    case 'p': case 'q': case 'r': case 's': case 't':
                    case 'u': case 'v': case 'w': case 'x': case 'y':
                    case 'z':
                    case '$': case '_':
                        scanIdent();
                        break loop;
                    case '0':
                        reader.scanChar();
                        if (reader.ch == 'x' || reader.ch == 'X') {
                            reader.scanChar();
                            skipIllegalUnderscores();
                            scanNumber(pos, 16);
                        } else if (reader.ch == 'b' || reader.ch == 'B') {
                            if (!allowBinaryLiterals) {
                                lexError(pos, "unsupported.binary.lit");
                                allowBinaryLiterals = true;
                            }
                            reader.scanChar();
                            skipIllegalUnderscores();
                            scanNumber(pos, 2);
                        } else {
                            reader.putChar('0');
                            if (reader.ch == '_') {
                                int savePos = reader.bp;
                                do {
                                    reader.scanChar();
                                } while (reader.ch == '_');
                                if (reader.digit(pos, 10) < 0) {
                                    lexError(savePos, "illegal.underscore");
                                }
                            }
                            scanNumber(pos, 8);
                        }
                        break loop;
                    case '1': case '2': case '3': case '4':
                    case '5': case '6': case '7': case '8': case '9':
                        scanNumber(pos, 10);
                        break loop;
                    case '.':
                        reader.scanChar();
                        if (reader.digit(pos, 10) >= 0) {
                            reader.putChar('.');
                            scanFractionAndSuffix(pos);
                        } else if (reader.ch == '.') {
                            int savePos = reader.bp;
                            reader.putChar('.'); reader.putChar('.', true);
                            if (reader.ch == '.') {
                                reader.scanChar();
                                reader.putChar('.');
                                tk = TokenKind.ELLIPSIS;
                            } else {
                                lexError(savePos, "illegal.dot");
                            }
                        } else {
                            tk = TokenKind.DOT;
                        }
                        break loop;
                    case ',':
                        reader.scanChar(); tk = TokenKind.COMMA; break loop;
                    case ';':
                        reader.scanChar(); tk = TokenKind.SEMI; break loop;
                    case '(':
                        reader.scanChar(); tk = TokenKind.LPAREN; break loop;
                    case ')':
                        reader.scanChar(); tk = TokenKind.RPAREN; break loop;
                    case '[':
                        reader.scanChar(); tk = TokenKind.LBRACKET; break loop;
                    case ']':
                        reader.scanChar(); tk = TokenKind.RBRACKET; break loop;
                    case '{':
                        reader.scanChar(); tk = TokenKind.LBRACE; break loop;
                    case '}':
                        reader.scanChar(); tk = TokenKind.RBRACE; break loop;
                    case '/':
                        reader.scanChar();
                        if (reader.ch == '/') {
                            do {
                                reader.scanCommentChar();
                            } while (reader.ch != CR && reader.ch != LF && reader.bp < reader.buflen);
                            if (reader.bp < reader.buflen) {
                                //comments = addComment(comments, processComment(pos, reader.bp, CommentStyle.LINE));
                            }
                            break;
                        } else if (reader.ch == '*') {
                            boolean isEmpty = false;
                            reader.scanChar();
                            //CommentStyle style;
                            if (reader.ch == '*') {
                                //style = CommentStyle.JAVADOC;
                                reader.scanCommentChar();
                                if (reader.ch == '/') {
                                    isEmpty = true;
                                }
                            } else {
                               // style = CommentStyle.BLOCK;
                            }
                            while (!isEmpty && reader.bp < reader.buflen) {
                                if (reader.ch == '*') {
                                    reader.scanChar();
                                    if (reader.ch == '/') break;
                                } else {
                                    reader.scanCommentChar();
                                }
                            }
                            if (reader.ch == '/') {
                                reader.scanChar();
                                //comments = addComment(comments, processComment(pos, reader.bp, style));
                                break;
                            } else {
                                lexError(pos, "unclosed.comment");
                                break loop;
                            }
                        } else if (reader.ch == '=') {
                            tk = TokenKind.SLASHEQ;
                            reader.scanChar();
                        } else {
                            tk = TokenKind.SLASH;
                        }
                        break loop;
                    case '\'':
                        reader.scanChar();
                        if (reader.ch == '\'') {
                            lexError(pos, "empty.char.lit");
                            reader.scanChar();
                        } else {
                            if (reader.ch == CR || reader.ch == LF)
                                lexError(pos, "illegal.line.end.in.char.lit");
                            scanLitChar(pos);
                            if (reader.ch == '\'') {
                                reader.scanChar();
                                tk = TokenKind.CHARLITERAL;
                            } else {
                                lexError(pos, "unclosed.char.lit");
                            }
                        }
                        break loop;
                    case '\"':
                        reader.scanChar();
                        while (reader.ch != '\"' && reader.ch != CR && reader.ch != LF && reader.bp < reader.buflen)
                            scanLitChar(pos);
                        if (reader.ch == '\"') {
                            tk = TokenKind.STRINGLITERAL;
                            reader.scanChar();
                        } else {
                            lexError(pos, "unclosed.str.lit");
                        }
                        break loop;
                    default:
                        if (isSpecial(reader.ch)) {
                            scanOperator();
                        } else {
                            boolean isJavaIdentifierStart;
                            int codePoint = -1;
                            if (reader.ch < '\u0080') {
                                // all ASCII range chars already handled, above
                                isJavaIdentifierStart = false;
                            } else {
                                codePoint = reader.peekSurrogates();
                                if (codePoint >= 0) {
                                    if (isJavaIdentifierStart = Character.isJavaIdentifierStart(codePoint)) {
                                        reader.putChar(true);
                                    }
                                } else {
                                    isJavaIdentifierStart = Character.isJavaIdentifierStart(reader.ch);
                                }
                            }
                            if (isJavaIdentifierStart) {
                                scanIdent();
                            } else if (reader.digit(pos, 10) >= 0) {
                                scanNumber(pos, 10);
                            } else if (reader.bp == reader.buflen || reader.ch == EOI && reader.bp + 1 == reader.buflen) { // JLS 3.5
                                tk = TokenKind.EOF;
                                pos = reader.buflen;
                            } else {
                                String arg;

                                if (codePoint >= 0) {
                                    char high = reader.ch;
                                    reader.scanChar();
                                    arg = String.format("\\u%04x\\u%04x", (int) high, (int)reader.ch);
                                } else {
                                    arg = (32 < reader.ch && reader.ch < 127) ?
                                          String.format("%s", reader.ch) :
                                          String.format("\\u%04x", (int)reader.ch);
                                }
                                lexError(pos, "illegal.char", arg);
                                reader.scanChar();
                            }
                        }
                        break loop;
                }
            }
            endPos = reader.bp;
            switch (tk.tag) {
                case DEFAULT: return new Token(tk, pos, endPos);
                case NAMED: return new NamedToken(tk, pos, endPos, name);
                case STRING: return new StringToken(tk, pos, endPos, reader.chars());
                case NUMERIC: return new NumericToken(tk, pos, endPos, reader.chars(), radix);
                default: throw new AssertionError();
            }
        }
        finally {
            if (scannerDebug) {
                System.out.println("nextToken(" + pos
                      + "," + endPos + ")=|" +
                      new String(reader.getRawCharacters(pos, endPos))
                      + "|");
            }
        }
    }
    /** Report an error at the given position using the provided arguments.
     */
    protected void lexError(int pos, String key, Object... args) {
        tk = TokenKind.ERROR;
        errPos = pos;
    }

    /** Read next character in character or string literal and copy into sbuf.
     */
    private void scanLitChar(int pos) {
        if (reader.ch == '\\') {
            if (reader.peekChar() == '\\' && !reader.isUnicode()) {
                reader.skipChar();
                reader.putChar('\\', true);
            } else {
                reader.scanChar();
                switch (reader.ch) {
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    char leadch = reader.ch;
                    int oct = reader.digit(pos, 8);
                    reader.scanChar();
                    if ('0' <= reader.ch && reader.ch <= '7') {
                        oct = oct * 8 + reader.digit(pos, 8);
                        reader.scanChar();
                        if (leadch <= '3' && '0' <= reader.ch && reader.ch <= '7') {
                            oct = oct * 8 + reader.digit(pos, 8);
                            reader.scanChar();
                        }
                    }
                    reader.putChar((char)oct);
                    break;
                case 'b':
                    reader.putChar('\b', true); break;
                case 't':
                    reader.putChar('\t', true); break;
                case 'n':
                    reader.putChar('\n', true); break;
                case 'f':
                    reader.putChar('\f', true); break;
                case 'r':
                    reader.putChar('\r', true); break;
                case '\'':
                    reader.putChar('\'', true); break;
                case '\"':
                    reader.putChar('\"', true); break;
                case '\\':
                    reader.putChar('\\', true); break;
                default:
                    lexError(reader.bp, "illegal.esc.char");
                }
            }
        } else if (reader.bp != reader.buflen) {
            reader.putChar(true);
        }
    }

    private void scanDigits(int pos, int digitRadix) {
        char saveCh;
        int savePos;
        do {
            if (reader.ch != '_') {
                reader.putChar(false);
            } else {
                if (!allowUnderscoresInLiterals) {
                    lexError(pos, "unsupported.underscore.lit");
                    allowUnderscoresInLiterals = true;
                }
            }
            saveCh = reader.ch;
            savePos = reader.bp;
            reader.scanChar();
        } while (reader.digit(pos, digitRadix) >= 0 || reader.ch == '_');
        if (saveCh == '_')
            lexError(savePos, "illegal.underscore");
    }

    /** Read fractional part of hexadecimal floating point number.
     */
    private void scanHexExponentAndSuffix(int pos) {
        if (reader.ch == 'p' || reader.ch == 'P') {
            reader.putChar(true);
            skipIllegalUnderscores();
            if (reader.ch == '+' || reader.ch == '-') {
                reader.putChar(true);
            }
            skipIllegalUnderscores();
            if (reader.digit(pos, 10) >= 0) {
                scanDigits(pos, 10);
                if (!hexFloatsWork)
                    lexError(pos, "unsupported.cross.fp.lit");
            } else
                lexError(pos, "malformed.fp.lit");
        } else {
            lexError(pos, "malformed.fp.lit");
        }
        if (reader.ch == 'f' || reader.ch == 'F') {
            reader.putChar(true);
            tk = TokenKind.FLOATLITERAL;
            radix = 16;
        } else {
            if (reader.ch == 'd' || reader.ch == 'D') {
                reader.putChar(true);
            }
            tk = TokenKind.DOUBLELITERAL;
            radix = 16;
        }
    }

    /** Read fractional part of floating point number.
     */
    private void scanFraction(int pos) {
        skipIllegalUnderscores();
        if (reader.digit(pos, 10) >= 0) {
            scanDigits(pos, 10);
        }
        int sp1 = reader.sp;
        if (reader.ch == 'e' || reader.ch == 'E') {
            reader.putChar(true);
            skipIllegalUnderscores();
            if (reader.ch == '+' || reader.ch == '-') {
                reader.putChar(true);
            }
            skipIllegalUnderscores();
            if (reader.digit(pos, 10) >= 0) {
                scanDigits(pos, 10);
                return;
            }
            lexError(pos, "malformed.fp.lit");
            reader.sp = sp1;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanFractionAndSuffix(int pos) {
        radix = 10;
        scanFraction(pos);
        if (reader.ch == 'f' || reader.ch == 'F') {
            reader.putChar(true);
            tk = TokenKind.FLOATLITERAL;
        } else {
            if (reader.ch == 'd' || reader.ch == 'D') {
                reader.putChar(true);
            }
            tk = TokenKind.DOUBLELITERAL;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanHexFractionAndSuffix(int pos, boolean seendigit) {
        radix = 16;
        Assert.check(reader.ch == '.');
        reader.putChar(true);
        skipIllegalUnderscores();
        if (reader.digit(pos, 16) >= 0) {
            seendigit = true;
            scanDigits(pos, 16);
        }
        if (!seendigit)
            lexError(pos, "invalid.hex.number");
        else
            scanHexExponentAndSuffix(pos);
    }

    private void skipIllegalUnderscores() {
        if (reader.ch == '_') {
            lexError(reader.bp, "illegal.underscore");
            while (reader.ch == '_')
                reader.scanChar();
        }
    }

    /** Read a number.
     *  @param radix  The radix of the number; one of 2, 8, 10, 16.
     */
    private void scanNumber(int pos, int radix) {
        // for octal, allow base-10 digit in case it's a float literal
        this.radix = radix;
        int digitRadix = (radix == 8 ? 10 : radix);
        int firstDigit = reader.digit(pos, Math.max(10, digitRadix));
        boolean seendigit = firstDigit >= 0;
        boolean seenValidDigit = firstDigit >= 0 && firstDigit < digitRadix;
        if (seendigit) {
            scanDigits(pos, digitRadix);
        }
        if (radix == 16 && reader.ch == '.') {
            scanHexFractionAndSuffix(pos, seendigit);
        } else if (seendigit && radix == 16 && (reader.ch == 'p' || reader.ch == 'P')) {
            scanHexExponentAndSuffix(pos);
        } else if (digitRadix == 10 && reader.ch == '.') {
            reader.putChar(true);
            scanFractionAndSuffix(pos);
        } else if (digitRadix == 10 &&
                   (reader.ch == 'e' || reader.ch == 'E' ||
                    reader.ch == 'f' || reader.ch == 'F' ||
                    reader.ch == 'd' || reader.ch == 'D')) {
            scanFractionAndSuffix(pos);
        } else {
            if (!seenValidDigit) {
                switch (radix) {
                case 2:
                    lexError(pos, "invalid.binary.number");
                    break;
                case 16:
                    lexError(pos, "invalid.hex.number");
                    break;
                }
            }
            if (reader.ch == 'l' || reader.ch == 'L') {
                reader.scanChar();
                tk = TokenKind.LONGLITERAL;
            } else {
                tk = TokenKind.INTLITERAL;
            }
        }
    }

    /** Read an identifier.
     */
    private void scanIdent() {
        boolean isJavaIdentifierPart;
        char high;
        reader.putChar(true);
        do {
            switch (reader.ch) {
            case 'A': case 'B': case 'C': case 'D': case 'E':
            case 'F': case 'G': case 'H': case 'I': case 'J':
            case 'K': case 'L': case 'M': case 'N': case 'O':
            case 'P': case 'Q': case 'R': case 'S': case 'T':
            case 'U': case 'V': case 'W': case 'X': case 'Y':
            case 'Z':
            case 'a': case 'b': case 'c': case 'd': case 'e':
            case 'f': case 'g': case 'h': case 'i': case 'j':
            case 'k': case 'l': case 'm': case 'n': case 'o':
            case 'p': case 'q': case 'r': case 's': case 't':
            case 'u': case 'v': case 'w': case 'x': case 'y':
            case 'z':
            case '$': case '_':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                break;
            case '\u0000': case '\u0001': case '\u0002': case '\u0003':
            case '\u0004': case '\u0005': case '\u0006': case '\u0007':
            case '\u0008': case '\u000E': case '\u000F': case '\u0010':
            case '\u0011': case '\u0012': case '\u0013': case '\u0014':
            case '\u0015': case '\u0016': case '\u0017':
            case '\u0018': case '\u0019': case '\u001B':
            case '\u007F':
                reader.scanChar();
                continue;
            case '\u001A': // EOI is also a legal identifier part
                if (reader.bp >= reader.buflen) {
                    name = reader.name();
                    tk = tokens.lookupKind(name);
                    return;
                }
                reader.scanChar();
                continue;
            default:
                if (reader.ch < '\u0080') {
                    // all ASCII range chars already handled, above
                    isJavaIdentifierPart = false;
                } else {
                    if (Character.isIdentifierIgnorable(reader.ch)) {
                        reader.scanChar();
                        continue;
                    } else {
                        int codePoint = reader.peekSurrogates();
                        if (codePoint >= 0) {
                            if (isJavaIdentifierPart = Character.isJavaIdentifierPart(codePoint)) {
                                reader.putChar(true);
                            }
                        } else {
                            isJavaIdentifierPart = Character.isJavaIdentifierPart(reader.ch);
                        }
                    }
                }
                if (!isJavaIdentifierPart) {
                    name = reader.name();
                    tk = tokens.lookupKind(name);
                    return;
                }
            }
            reader.putChar(true);
        } while (true);
    }

    /** Return true if reader.ch can be part of an operator.
     */
    private boolean isSpecial(char ch) {
        switch (ch) {
        case '!': case '%': case '&': case '*': case '?':
        case '+': case '-': case ':': case '<': case '=':
        case '>': case '^': case '|': case '~':
        case '@':
            return true;
        default:
            return false;
        }
    }

    /** Read longest possible sequence of special characters and convert
     *  to token.
     */
    private void scanOperator() {
        while (true) {
            reader.putChar(false);
            Name newname = reader.name();
            TokenKind tk1 = tokens.lookupKind(newname);
            if (tk1 == TokenKind.IDENTIFIER) {
                reader.sp--;
                break;
            }
            tk = tk1;
            reader.scanChar();
            if (!isSpecial(reader.ch)) break;
        }
    }

    /** Return the position where a lexical error occurred;
     */
    public int errPos() {
        return errPos;
    }

    /** Set the position where a lexical error occurred;
     */
    public void errPos(int pos) {
        errPos = pos;
    }


    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     */
    protected void processWhiteSpace(int pos, int endPos) {
        if (scannerDebug)
            System.out.println("processWhitespace(" + pos
                               + "," + endPos + ")=|" +
                               new String(reader.getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a line terminator has been processed.
     */
    protected void processLineTerminator(int pos, int endPos) {
        if (scannerDebug)
            System.out.println("processTerminator(" + pos
                               + "," + endPos + ")=|" +
                               new String(reader.getRawCharacters(pos, endPos))
                               + "|");
    }

    /** Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap */
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(reader.getRawCharacters(), reader.buflen, false);
    }
}
