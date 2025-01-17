/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.tree.DocTreeMaker;
import com.flint.tools.flintc.tree.TreeMaker;
import com.flint.tools.flintc.util.Context;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.Names;
import com.flint.tools.flintc.util.Options;

import java.util.Locale;

/**
 * A factory for creating parsers.
 */
public class ParserFactory {

    /** The context key for the parser factory. */
    protected static final Context.Key<ParserFactory> parserFactoryKey = new Context.Key<>();

    public static ParserFactory instance(Context context) {
        ParserFactory instance = context.get(parserFactoryKey);
        if (instance == null) {
            instance = new ParserFactory(context);
        }
        return instance;
    }

    final TreeMaker F;
    final DocTreeMaker docTreeMaker;
    final Log log;
    final Tokens tokens;
//    final Source source;
    final Names names;
    final Options options;
    final ScannerFactory scannerFactory;
    final Locale locale;

    protected ParserFactory(Context context) {
        super();
        context.put(parserFactoryKey, this);
        this.F = TreeMaker.instance(context);
        this.docTreeMaker = DocTreeMaker.instance(context);
        this.log = Log.instance(context);
        this.names = Names.instance(context);
        this.tokens = Tokens.instance(context);

        this.options = Options.instance(context);
        this.scannerFactory = ScannerFactory.instance(context);
        this.locale = context.get(Locale.class);
    }

    public JavacParser newParser(CharSequence input, boolean keepDocComments, boolean keepEndPos, boolean keepLineMap) {
        return newParser(input, keepDocComments, keepEndPos, keepLineMap, false);
    }

    public JavacParser newParser(CharSequence input, boolean keepDocComments, boolean keepEndPos, boolean keepLineMap, boolean parseModuleInfo) {
        Lexer lexer = scannerFactory.newScanner(input);
        return new JavacParser(this, lexer, keepDocComments, keepLineMap, keepEndPos, parseModuleInfo);
    }
}
