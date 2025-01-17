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

package com.flint.tools.flintc.util;

import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaFileObject;

import com.flint.tools.flintc.code.Lint.LintCategory;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticFlag;
import com.flint.tools.flintc.util.JCDiagnostic.Error;
import com.flint.tools.flintc.util.JCDiagnostic.Note;
import com.flint.tools.flintc.util.JCDiagnostic.Warning;
import com.flint.tools.flintc.util.JCDiagnostic.DiagnosticPosition;
import com.flint.tools.flintc.util.JCDiagnostic.SimpleDiagnosticPosition;


/**
 *  A base class for error logs. Reports errors and warnings, and
 *  keeps track of error numbers and positions.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class AbstractLog {
    /** Factory for diagnostics
     */
    protected JCDiagnostic.Factory diags;

    /** The file that's currently being translated.
     */
    protected DiagnosticSource source;

    /** A cache of lightweight DiagnosticSource objects.
     */
    protected Map<JavaFileObject, DiagnosticSource> sourceMap;

    AbstractLog(JCDiagnostic.Factory diags) {
        this.diags = diags;
        sourceMap = new HashMap<>();
    }

    /** Re-assign source, returning previous setting.
     */
    public JavaFileObject useSource(JavaFileObject file) {
        JavaFileObject prev = (source == null ? null : source.getFile());
        source = getSource(file);
        return prev;
    }

    protected DiagnosticSource getSource(JavaFileObject file) {
        if (file == null)
            return DiagnosticSource.NO_SOURCE;
        DiagnosticSource s = sourceMap.get(file);
        if (s == null) {
            s = new DiagnosticSource(file, this);
            sourceMap.put(file, s);
        }
        return s;
    }

    /** Return the underlying diagnostic source
     */
    public DiagnosticSource currentSource() {
        return source;
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(String key, Object ... args) {
        error(diags.errorKey(key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param errorKey    The key for the localized error message.
     */
    public void error(Error errorKey) {
        report(diags.error(null, source, null, errorKey));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(DiagnosticPosition pos, String key, Object... args) {
        error(pos, diags.errorKey(key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param errorKey    The key for the localized error message.
     */
    public void error(DiagnosticPosition pos, Error errorKey) {
        report(diags.error(null, source, pos, errorKey));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param flag   A flag to set on the diagnostic
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(DiagnosticFlag flag, DiagnosticPosition pos, String key, Object ... args) {
        error(flag, pos, diags.errorKey(key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param flag   A flag to set on the diagnostic
     *  @param pos    The source position at which to report the error.
     *  @param errorKey    The key for the localized error message.
     */
    public void error(DiagnosticFlag flag, DiagnosticPosition pos, Error errorKey) {
        report(diags.error(flag, source, pos, errorKey));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(int pos, String key, Object ... args) {
        error(pos, diags.errorKey(key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param pos    The source position at which to report the error.
     *  @param errorKey    The key for the localized error message.
     */
    public void error(int pos, Error errorKey) {
        report(diags.error(null, source, wrap(pos), errorKey));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param flag   A flag to set on the diagnostic
     *  @param pos    The source position at which to report the error.
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     */
    public void error(DiagnosticFlag flag, int pos, String key, Object ... args) {
        error(flag, pos, diags.errorKey(key, args));
    }

    /** Report an error, unless another error was already reported at same
     *  source position.
     *  @param flag   A flag to set on the diagnostic
     *  @param pos    The source position at which to report the error.
     *  @param errorKey    The key for the localized error message.
     */
    public void error(DiagnosticFlag flag, int pos, Error errorKey) {
        report(diags.error(flag, source, wrap(pos), errorKey));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(String key, Object ... args) {
        warning(diags.warningKey(key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param warningKey    The key for the localized warning message.
     */
    public void warning(Warning warningKey) {
        report(diags.warning(null, source, null, warningKey));
    }

    /** Report a lint warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param lc     The lint category for the diagnostic
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(LintCategory lc, String key, Object ... args) {
        warning(lc, diags.warningKey(key, args));
    }

    /** Report a lint warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param lc     The lint category for the diagnostic
     *  @param warningKey    The key for the localized warning message.
     */
    public void warning(LintCategory lc, Warning warningKey) {
        report(diags.warning(lc, null, null, warningKey));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(DiagnosticPosition pos, String key, Object ... args) {
        warning(pos, diags.warningKey(key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param warningKey    The key for the localized warning message.
     */
    public void warning(DiagnosticPosition pos, Warning warningKey) {
        report(diags.warning(null, source, pos, warningKey));
    }

    /** Report a lint warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param lc     The lint category for the diagnostic
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(LintCategory lc, DiagnosticPosition pos, String key, Object ... args) {
        warning(lc, pos, diags.warningKey(key, args));
    }

    /** Report a lint warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param lc     The lint category for the diagnostic
     *  @param pos    The source position at which to report the warning.
     *  @param warningKey    The key for the localized warning message.
     */
    public void warning(LintCategory lc, DiagnosticPosition pos, Warning warningKey) {
        report(diags.warning(lc, source, pos, warningKey));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void warning(int pos, String key, Object ... args) {
        warning(pos, diags.warningKey(key, args));
    }

    /** Report a warning, unless suppressed by the  -nowarn option or the
     *  maximum number of warnings has been reached.
     *  @param pos    The source position at which to report the warning.
     *  @param warningKey    The key for the localized warning message.
     */
    public void warning(int pos, Warning warningKey) {
        report(diags.warning(null, source, wrap(pos), warningKey));
    }

    /** Report a warning.
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void mandatoryWarning(DiagnosticPosition pos, String key, Object ... args) {
        mandatoryWarning(pos, diags.warningKey(key, args));
    }

    /** Report a warning.
     *  @param pos    The source position at which to report the warning.
     *  @param warningKey    The key for the localized warning message.
     */
    public void mandatoryWarning(DiagnosticPosition pos, Warning warningKey) {
        report(diags.mandatoryWarning(null, source, pos, warningKey));
    }

    /** Report a warning.
     *  @param lc     The lint category for the diagnostic
     *  @param pos    The source position at which to report the warning.
     *  @param key    The key for the localized warning message.
     *  @param args   Fields of the warning message.
     */
    public void mandatoryWarning(LintCategory lc, DiagnosticPosition pos, String key, Object ... args) {
        mandatoryWarning(lc, pos, diags.warningKey(key, args));
    }

    /** Report a warning.
     *  @param lc     The lint category for the diagnostic
     *  @param pos    The source position at which to report the warning.
     *  @param warningKey    The key for the localized warning message.
     */
    public void mandatoryWarning(LintCategory lc, DiagnosticPosition pos, Warning warningKey) {
        report(diags.mandatoryWarning(lc, source, pos, warningKey));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notint an error or warning message:
     */
    public void note(String key, Object ... args) {
        note(diags.noteKey(key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param noteKey    The key for the localized notification message.
     */
    public void note(Note noteKey) {
        report(diags.note(source, null, noteKey));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(DiagnosticPosition pos, String key, Object ... args) {
        note(pos, diags.noteKey(key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param noteKey    The key for the localized notification message.
     */
    public void note(DiagnosticPosition pos, Note noteKey) {
        report(diags.note(source, pos, noteKey));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(int pos, String key, Object ... args) {
        note(pos, diags.noteKey(key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param noteKey    The key for the localized notification message.
     */
    public void note(int pos, Note noteKey) {
        report(diags.note(source, wrap(pos), noteKey));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void note(JavaFileObject file, String key, Object ... args) {
        note(file, diags.noteKey(key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param noteKey    The key for the localized notification message.
     */
    public void note(JavaFileObject file, Note noteKey) {
        report(diags.note(getSource(file), null, noteKey));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param key    The key for the localized notification message.
     *  @param args   Fields of the notification message.
     */
    public void mandatoryNote(final JavaFileObject file, String key, Object ... args) {
        mandatoryNote(file, diags.noteKey(key, args));
    }

    /** Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *  @param noteKey    The key for the localized notification message.
     */
    public void mandatoryNote(final JavaFileObject file, Note noteKey) {
        report(diags.mandatoryNote(getSource(file), noteKey));
    }

    protected abstract void report(JCDiagnostic diagnostic);

    protected abstract void directError(String key, Object... args);

    private DiagnosticPosition wrap(int pos) {
        return (pos == Position.NOPOS ? null : new SimpleDiagnosticPosition(pos));
    }
}
