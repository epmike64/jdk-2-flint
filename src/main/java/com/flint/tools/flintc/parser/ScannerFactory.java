/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

//import com.flint.tools.flintc.code.Source;
import com.flint.tools.flintc.util.Context;
//import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.Names;

import java.nio.CharBuffer;


/**
 * A factory for creating scanners.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 */
public class ScannerFactory {
    /** The context key for the scanner factory. */
    public static final Context.Key<ScannerFactory> scannerFactoryKey = new Context.Key<>();

    /** Get the Factory instance for this context. */
    public static ScannerFactory instance(Context context) {
        ScannerFactory instance = context.get(scannerFactoryKey);
        if (instance == null)
            instance = new ScannerFactory(context);
        return instance;
    }

//    final Log log;
    final Names names;
//    final Source source;
    final Tokens tokens;

    /** Create a new scanner factory. */
    protected ScannerFactory(Context context) {
        context.put(scannerFactoryKey, this);
//        this.log = Log.instance(context);
        this.names = Names.instance(context);
//        this.source = Source.instance(context);
        this.tokens = Tokens.instance(context);
    }

    public Scanner newScanner(CharSequence input) {
        if (input instanceof CharBuffer) {
            CharBuffer buf = (CharBuffer) input;
                return new Scanner(this, buf);
        } else {
            char[] array = input.toString().toCharArray();
            return newScanner(array, array.length);
        }
    }

    public Scanner newScanner(char[] input, int inputLength) {
        return new Scanner(this, input, inputLength);
    }
}
