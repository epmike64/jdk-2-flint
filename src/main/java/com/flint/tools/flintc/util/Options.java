/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.main.Option;

import java.util.LinkedHashMap;
import java.util.Set;

import static com.flint.tools.flintc.main.Option.XLINT;
import static com.flint.tools.flintc.main.Option.XLINT_CUSTOM;

/** A table of all command-line options.
 *  If an option has an argument, the option name is mapped to the argument.
 *  If a set option has no argument, it is mapped to itself.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Options {
    private static final long serialVersionUID = 0;

    /** The context key for the options. */
    public static final com.flint.tools.flintc.util.Context.Key<Options> optionsKey = new com.flint.tools.flintc.util.Context.Key<>();

    private LinkedHashMap<String,String> values;

    /** Get the Options instance for this context. */
    public static Options instance(com.flint.tools.flintc.util.Context context) {
        Options instance = context.get(optionsKey);
        if (instance == null)
            instance = new Options(context);
        return instance;
    }

    protected Options(Context context) {
// DEBUGGING -- Use LinkedHashMap for reproducability
        values = new LinkedHashMap<>();
        context.put(optionsKey, this);
    }

    /**
     * Get the value for an undocumented option.
     */
    public String get(String name) {
        return values.get(name);
    }

    /**
     * Get the value for an option.
     */
    public String get(Option option) {
        return values.get(option.primaryName);
    }

    /**
     * Get the boolean value for an option, patterned after Boolean.getBoolean,
     * essentially will return true, iff the value exists and is set to "true".
     */
    public boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
     * Get the boolean with a default value if the option is not set.
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        String value = get(name);
        return (value == null) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * Check if the value for an undocumented option has been set.
     */
    public boolean isSet(String name) {
        return (values.get(name) != null);
    }

    /**
     * Check if the value for an option has been set.
     */
    public boolean isSet(Option option) {
        return (values.get(option.primaryName) != null);
    }

    /**
     * Check if the value for a choice option has been set to a specific value.
     */
    public boolean isSet(Option option, String value) {
        return (values.get(option.primaryName + value) != null);
    }

    /** Check if the value for a lint option has been explicitly set, either with -Xlint:opt
     *  or if all lint options have enabled and this one not disabled with -Xlint:-opt.
     */
    public boolean isLintSet(String s) {
        // return true if either the specific option is enabled, or
        // they are all enabled without the specific one being
        // disabled
        return
            isSet(XLINT_CUSTOM, s) ||
            (isSet(XLINT) || isSet(XLINT_CUSTOM, "all")) && isUnset(XLINT_CUSTOM, "-" + s);
    }

    /**
     * Check if the value for an undocumented option has not been set.
     */
    public boolean isUnset(String name) {
        return (values.get(name) == null);
    }

    /**
     * Check if the value for an option has not been set.
     */
    public boolean isUnset(Option option) {
        return (values.get(option.primaryName) == null);
    }

    /**
     * Check if the value for a choice option has not been set to a specific value.
     */
    public boolean isUnset(Option option, String value) {
        return (values.get(option.primaryName + value) == null);
    }

    public void put(String name, String value) {
        values.put(name, value);
    }

    public void put(Option option, String value) {
        values.put(option.primaryName, value);
    }

    public void putAll(Options options) {
        values.putAll(options.values);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    // light-weight notification mechanism

    private JCList<Runnable> listeners = JCList.nil();

    public void addListener(Runnable listener) {
        listeners = listeners.prepend(listener);
    }

    public void notifyListeners() {
        for (Runnable r: listeners)
            r.run();
    }
}
