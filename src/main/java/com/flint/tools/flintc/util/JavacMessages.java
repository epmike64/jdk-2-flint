/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.flint.tools.flintc.api.Messages;

import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.util.*;

/**
 *  Support for formatted localized messages.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavacMessages implements Messages {
    /** The context key for the JavacMessages object. */
    public static final com.flint.tools.flintc.util.Context.Key<JavacMessages> messagesKey = new com.flint.tools.flintc.util.Context.Key<>();

    /** Get the JavacMessages instance for this context. */
    public static JavacMessages instance(com.flint.tools.flintc.util.Context context) {
        JavacMessages instance = context.get(messagesKey);
        if (instance == null)
            instance = new JavacMessages(context);
        return instance;
    }

    private Map<Locale, SoftReference<JCList<ResourceBundle>>> bundleCache;

    private JCList<ResourceBundleHelper> bundleHelpers;

    private Locale currentLocale;
    private JCList<ResourceBundle> currentBundles;

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public void setCurrentLocale(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        this.currentBundles = getBundles(locale);
        this.currentLocale = locale;
    }

    /** Creates a JavacMessages object.
     */
    public JavacMessages(Context context) {
        this(defaultBundleName, context.get(Locale.class));
        context.put(messagesKey, this);
    }

    /** Creates a JavacMessages object.
     * @param bundleName the name to identify the resource bundle of localized messages.
     */
    public JavacMessages(String bundleName) throws MissingResourceException {
        this(bundleName, null);
    }

    /** Creates a JavacMessages object.
     * @param bundleName the name to identify the resource bundle of localized messages.
     */
    public JavacMessages(String bundleName, Locale locale) throws MissingResourceException {
        bundleHelpers = JCList.nil();
        bundleCache = new HashMap<>();
        add(bundleName);
        setCurrentLocale(locale);
    }

    public JavacMessages() throws MissingResourceException {
        this(defaultBundleName);
    }

    @Override
    public void add(String bundleName) throws MissingResourceException {
        add(locale -> ResourceBundle.getBundle(bundleName, locale));
    }

    public void add(ResourceBundleHelper ma) {
        bundleHelpers = bundleHelpers.prepend(ma);
        if (!bundleCache.isEmpty())
            bundleCache.clear();
        currentBundles = null;
    }

    public JCList<ResourceBundle> getBundles(Locale locale) {
        if (locale == currentLocale && currentBundles != null)
            return currentBundles;
        SoftReference<JCList<ResourceBundle>> bundles = bundleCache.get(locale);
        JCList<ResourceBundle> bundleList = bundles == null ? null : bundles.get();
        if (bundleList == null) {
            bundleList = JCList.nil();
            for (ResourceBundleHelper helper : bundleHelpers) {
                try {
                    ResourceBundle rb = helper.getResourceBundle(locale);
                    bundleList = bundleList.prepend(rb);
                } catch (MissingResourceException e) {
                    throw new InternalError("Cannot find requested resource bundle for locale " +
                            locale, e);
                }
            }
            bundleCache.put(locale, new SoftReference<>(bundleList));
        }
        return bundleList;
    }

    /** Gets the localized string corresponding to a key, formatted with a set of args.
     */
    public String getLocalizedString(String key, Object... args) {
        return getLocalizedString(currentLocale, key, args);
    }

    @Override
    public String getLocalizedString(Locale l, String key, Object... args) {
        if (l == null)
            l = getCurrentLocale();
        return getLocalizedString(getBundles(l), key, args);
    }

    /* Static access:
     * javac has a firmly entrenched notion of a default message bundle
     * which it can access from any static context. This is used to get
     * easy access to simple localized strings.
     */

    private static final String defaultBundleName = "com.flint.tools.flintc.resources.compiler";
    private static ResourceBundle defaultBundle;
    private static JavacMessages defaultMessages;


    /**
     * Returns a localized string from the compiler's default bundle.
     */
    // used to support legacy Log.getLocalizedString
    static String getDefaultLocalizedString(String key, Object... args) {
        return getLocalizedString(JCList.of(getDefaultBundle()), key, args);
    }

    // used to support legacy static Diagnostic.fragment
    @Deprecated
    static JavacMessages getDefaultMessages() {
        if (defaultMessages == null)
            defaultMessages = new JavacMessages(defaultBundleName);
        return defaultMessages;
    }

    public static ResourceBundle getDefaultBundle() {
        try {
            if (defaultBundle == null)
                defaultBundle = ResourceBundle.getBundle(defaultBundleName);
            return defaultBundle;
        }
        catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for compiler is missing", e);
        }
    }

    private static String getLocalizedString(JCList<ResourceBundle> bundles,
                                             String key,
                                             Object... args) {
       String msg = null;
       for (JCList<ResourceBundle> l = bundles; l.nonEmpty() && msg == null; l = l.tail) {
           ResourceBundle rb = l.head;
           try {
               msg = rb.getString(key);
           }
           catch (MissingResourceException e) {
               // ignore, try other bundles in list
           }
       }
       if (msg == null) {
           msg = "compiler message file broken: key=" + key +
               " arguments={0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}";
       }
       return MessageFormat.format(msg, args);
    }

    /**
     * This provides a way for the JavacMessager to retrieve a
     * ResourceBundle from another module such as jdk.javadoc.
     */
    public interface ResourceBundleHelper {
        /**
         * Gets the ResourceBundle.
         * @param locale the requested bundle's locale
         * @return ResourceBundle
         */
        ResourceBundle getResourceBundle(Locale locale);
    }
}
