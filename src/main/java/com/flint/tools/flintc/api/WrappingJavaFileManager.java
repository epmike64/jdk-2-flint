/*
 * Copyright (c) 2006, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.api;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

import com.flint.tools.flintc.util.DefinedBy;
import com.flint.tools.flintc.util.DefinedBy.Api;

/**
 * Wraps all calls to a given file manager.  Subclasses of this class
 * might override some of these methods and might also provide
 * additional fields and methods.
 *
 * <p>This class might be moved to {@link javax.tools} in a future
 * release.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @param <M> the type of file manager wrapped to by this object
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public class WrappingJavaFileManager<M extends JavaFileManager> extends ForwardingJavaFileManager<M> {

    /**
     * Creates a new instance of WrappingJavaFileManager.
     * @param fileManager file manager to be wrapped
     */
    protected WrappingJavaFileManager(M fileManager) {
        super(fileManager);
    }

    /**
     * This implementation returns the given file object.  Subclasses
     * may override this behavior.
     *
     * @param fileObject a file object
     */
    protected FileObject wrap(FileObject fileObject) {
        return fileObject;
    }

    /**
     * This implementation forwards to {@link #wrap(FileObject)}.
     * Subclasses may override this behavior.
     *
     * @param fileObject a file object
     * @throws ClassCastException if the file object returned from the
     * forwarded call is not a subtype of {@linkplain JavaFileObject}
     */
    protected JavaFileObject wrap(JavaFileObject fileObject) {
        return (JavaFileObject)wrap((FileObject)fileObject);
    }

    /**
     * This implementation returns the given file object.  Subclasses
     * may override this behavior.
     *
     * @param fileObject a file object
     */
    protected FileObject unwrap(FileObject fileObject) {
        return fileObject;
    }

    /**
     * This implementation forwards to {@link #unwrap(FileObject)}.
     * Subclasses may override this behavior.
     *
     * @param fileObject a file object
     * @throws ClassCastException if the file object returned from the
     * forwarded call is not a subtype of {@linkplain JavaFileObject}
     */
    protected JavaFileObject unwrap(JavaFileObject fileObject) {
        return (JavaFileObject)unwrap((FileObject)fileObject);
    }

    /**
     * This implementation maps the given list of file objects by
     * calling wrap on each.  Subclasses may override this behavior.
     *
     * @param fileObjects a list of file objects
     * @return the mapping
     */
    protected Iterable<JavaFileObject> wrap(Iterable<JavaFileObject> fileObjects) {
        List<JavaFileObject> mapped = new ArrayList<>();
        for (JavaFileObject fileObject : fileObjects)
            mapped.add(wrap(fileObject));
        return Collections.unmodifiableList(mapped);
    }

    /**
     * This implementation returns the given URI.  Subclasses may
     * override this behavior.
     *
     * @param uri a URI
     */
    protected URI unwrap(URI uri) {
        return uri;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        return wrap(super.list(location, packageName, kinds, recurse));
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public String inferBinaryName(Location location, JavaFileObject file) {
        return super.inferBinaryName(location, unwrap(file));
    }

    /**
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public JavaFileObject getJavaFileForInput(Location location,
                                              String className,
                                              Kind kind)
        throws IOException
    {
        return wrap(super.getJavaFileForInput(location, className, kind));
    }

    /**
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               Kind kind,
                                               FileObject sibling)
        throws IOException
    {
        return wrap(super.getJavaFileForOutput(location, className, kind, unwrap(sibling)));
    }

    /**
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName)
        throws IOException
    {
        return wrap(super.getFileForInput(location, packageName, relativeName));
    }

    /**
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @DefinedBy(Api.COMPILER)
    public FileObject getFileForOutput(Location location,
                                       String packageName,
                                       String relativeName,
                                       FileObject sibling)
        throws IOException
    {
        return wrap(super.getFileForOutput(location,
                                           packageName,
                                           relativeName,
                                           unwrap(sibling)));
    }

    @Override @DefinedBy(Api.COMPILER)
    public boolean contains(Location location, FileObject file) throws IOException {
        return super.contains(location, unwrap(file));
    }

}
