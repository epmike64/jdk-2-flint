/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.api.DiagnosticFormatter.Configuration.DiagnosticPart;
import com.flint.tools.flintc.api.Formattable;
import com.flint.tools.flintc.file.PathFileObject;
import com.flint.tools.flintc.tree.JCTree.JCExpression;

import javax.tools.JavaFileObject;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;

import static com.flint.tools.flintc.api.DiagnosticFormatter.PositionKind.COLUMN;
import static com.flint.tools.flintc.api.DiagnosticFormatter.PositionKind.LINE;

/**
 * A raw formatter for diagnostic messages.
 * The raw formatter will format a diagnostic according to one of two format patterns, depending on whether
 * or not the source name and position are set. This formatter provides a standardized, localize-independent
 * implementation of a diagnostic formatter; as such, this formatter is best suited for testing purposes.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public final class RawDiagnosticFormatter extends AbstractDiagnosticFormatter {

    /**
     * Create a formatter based on the supplied options.
     * @param options
     */
    public RawDiagnosticFormatter(Options options) {
        super(null, new SimpleConfiguration(options,
                EnumSet.of(DiagnosticPart.SUMMARY,
                        DiagnosticPart.DETAILS,
                        DiagnosticPart.SUBDIAGNOSTICS)));
    }

    //provide common default formats
    public String formatDiagnostic(com.flint.tools.flintc.util.JCDiagnostic d, Locale l) {
        try {
            StringBuilder buf = new StringBuilder();
            if (d.getPosition() != Position.NOPOS) {
                buf.append(formatSource(d, false, null));
                buf.append(':');
                buf.append(formatPosition(d, LINE, null));
                buf.append(':');
                buf.append(formatPosition(d, COLUMN, null));
                buf.append(':');
            }
            else if (d.getSource() != null && d.getSource().getKind() == JavaFileObject.Kind.CLASS) {
                buf.append(formatSource(d, false, null));
                buf.append(":-:-:");
            }
            else
                buf.append('-');
            buf.append(' ');
            buf.append(formatMessage(d, null));
            if (displaySource(d)) {
                buf.append("\n");
                buf.append(formatSourceLine(d, 0));
            }
            return buf.toString();
        }
        catch (Exception e) {
            //e.printStackTrace();
            return null;
        }
    }

    public String formatMessage(com.flint.tools.flintc.util.JCDiagnostic d, Locale l) {
        StringBuilder buf = new StringBuilder();
        Collection<String> args = formatArguments(d, l);
        buf.append(localize(null, d.getCode(), args.toArray()));
        if (d.isMultiline() && getConfiguration().getVisible().contains(DiagnosticPart.SUBDIAGNOSTICS)) {
            JCList<String> subDiags = formatSubdiagnostics(d, null);
            if (subDiags.nonEmpty()) {
                String sep = "";
                buf.append(",{");
                for (String sub : formatSubdiagnostics(d, null)) {
                    buf.append(sep);
                    buf.append("(");
                    buf.append(sub);
                    buf.append(")");
                    sep = ",";
                }
                buf.append('}');
            }
        }
        return buf.toString();
    }

    @Override
    protected String formatArgument(com.flint.tools.flintc.util.JCDiagnostic diag, Object arg, Locale l) {
        String s;
        if (arg instanceof Formattable) {
            s = arg.toString();
        } else if (arg instanceof JCExpression) {
            JCExpression tree = (JCExpression)arg;
            s = "@" + tree.getStartPosition();
        } else if (arg instanceof PathFileObject) {
            s = ((PathFileObject) arg).getShortName();
        } else {
            s = super.formatArgument(diag, arg, null);
        }
        return (arg instanceof JCDiagnostic) ? "(" + s + ")" : s;
    }

    @Override
    protected String localize(Locale l, String key, Object... args) {
        StringBuilder buf = new StringBuilder();
        buf.append(key);
        String sep = ": ";
        for (Object o : args) {
            buf.append(sep);
            buf.append(o);
            sep = ", ";
        }
        return buf.toString();
    }

    @Override
    public boolean isRaw() {
        return true;
    }
}
