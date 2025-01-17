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

package com.flint.tools.flintc.code;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.flint.tools.flintc.comp.Annotate;
import com.flint.tools.flintc.file.JRTIndex;
import com.flint.tools.flintc.file.JavacFileManager;
import com.flint.tools.flintc.jvm.ClassReader;
import com.flint.tools.flintc.jvm.Profile;
import com.flint.tools.flintc.main.Option;
import com.flint.tools.flintc.platform.PlatformDescription;
import com.flint.tools.flintc.code.Scope.WriteableScope;
import com.flint.tools.flintc.code.Symbol.ClassSymbol;
import com.flint.tools.flintc.code.Symbol.Completer;
import com.flint.tools.flintc.code.Symbol.CompletionFailure;
import com.flint.tools.flintc.code.Symbol.ModuleSymbol;
import com.flint.tools.flintc.code.Symbol.PackageSymbol;
import com.flint.tools.flintc.code.Symbol.TypeSymbol;
import com.flint.tools.flintc.util.*;

import static javax.tools.StandardLocation.*;

import static com.flint.tools.flintc.code.Flags.*;
import static com.flint.tools.flintc.code.Kinds.Kind.*;

import com.flint.tools.flintc.util.Dependencies.CompletionCause;

/**
 *  This class provides operations to locate class definitions
 *  from the source and class files on the paths provided to javac.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassFinder {
    /** The context key for the class finder. */
    protected static final Context.Key<ClassFinder> classFinderKey = new Context.Key<>();

    ClassReader reader;

    private final Annotate annotate;

    /** Switch: verbose output.
     */
    boolean verbose;

    /**
     * Switch: cache completion failures unless -XDdev is used
     */
    private boolean cacheCompletionFailure;

    /**
     * Switch: prefer source files instead of newer when both source
     * and class are available
     **/
    protected boolean preferSource;

    /**
     * Switch: Search classpath and sourcepath for classes before the
     * bootclasspath
     */
    protected boolean userPathsFirst;

    /**
     * Switch: should read OTHER classfiles (.sig files) from PLATFORM_CLASS_PATH.
     */
    private boolean allowSigFiles;

    /** The log to use for verbose output
     */
    final Log log;

    /** The symbol table. */
    Symtab syms;

    /** The name table. */
    final Names names;

    /** Force a completion failure on this name
     */
    final Name completionFailureName;

    /** Access to files
     */
    private final JavaFileManager fileManager;

    /** Dependency tracker
     */
    private final Dependencies dependencies;

    /** Factory for diagnostics
     */
    JCDiagnostic.Factory diagFactory;

    /** Can be reassigned from outside:
     *  the completer to be used for ".java" files. If this remains unassigned
     *  ".java" files will not be loaded.
     */
    public Completer sourceCompleter = Completer.NULL_COMPLETER;

    /** The path name of the class file currently being read.
     */
    protected JavaFileObject currentClassFile = null;

    /** The class or method currently being read.
     */
    protected Symbol currentOwner = null;

    /**
     * The currently selected profile.
     */
    private final Profile profile;

    /**
     * Use direct access to the JRTIndex to access the temporary
     * replacement for the info that used to be in ct.sym.
     * In time, this will go away and be replaced by the module system.
     */
    private final JRTIndex jrtIndex;

    /**
     * Completer that delegates to the complete-method of this class.
     */
    private final Completer thisCompleter = this::complete;

    public Completer getCompleter() {
        return thisCompleter;
    }

    /** Get the ClassFinder instance for this invocation. */
    public static ClassFinder instance(Context context) {
        ClassFinder instance = context.get(classFinderKey);
        if (instance == null)
            instance = new ClassFinder(context);
        return instance;
    }

    /** Construct a new class finder. */
    protected ClassFinder(Context context) {
        context.put(classFinderKey, this);
        reader = ClassReader.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        fileManager = context.get(JavaFileManager.class);
        dependencies = Dependencies.instance(context);
        if (fileManager == null)
            throw new AssertionError("FileManager initialization error");
        diagFactory = JCDiagnostic.Factory.instance(context);

        log = Log.instance(context);
        annotate = Annotate.instance(context);

        Options options = Options.instance(context);
        verbose = options.isSet(Option.VERBOSE);
        cacheCompletionFailure = options.isUnset("dev");
        preferSource = "source".equals(options.get("-Xprefer"));
        userPathsFirst = options.isSet(Option.XXUSERPATHSFIRST);
        allowSigFiles = context.get(PlatformDescription.class) != null;

        completionFailureName =
            options.isSet("failcomplete")
            ? names.fromString(options.get("failcomplete"))
            : null;

        // Temporary, until more info is available from the module system.
        boolean useCtProps;
        JavaFileManager fm = context.get(JavaFileManager.class);
        if (fm instanceof JavacFileManager) {
            JavacFileManager jfm = (JavacFileManager) fm;
            useCtProps = jfm.isDefaultBootClassPath() && jfm.isSymbolFileEnabled();
        } else if (fm.getClass().getName().equals("com.flint.tools.sjavac.comp.SmartFileManager")) {
            useCtProps = !options.isSet("ignore.symbol.file");
        } else {
            useCtProps = false;
        }
        jrtIndex = useCtProps && JRTIndex.isAvailable() ? JRTIndex.getSharedInstance() : null;

        profile = Profile.instance(context);
    }


/************************************************************************
 * Temporary ct.sym replacement
 *
 * The following code is a temporary substitute for the ct.sym mechanism
 * used in JDK 6 thru JDK 8.
 * This mechanism will eventually be superseded by the Jigsaw module system.
 ***********************************************************************/

    /**
     * Returns any extra flags for a class symbol.
     * This information used to be provided using private annotations
     * in the class file in ct.sym; in time, this information will be
     * available from the module system.
     */
    long getSupplementaryFlags(ClassSymbol c) {
        if (jrtIndex == null || !jrtIndex.isInJRT(c.classfile) || c.name == names.module_info) {
            return 0;
        }

        if (supplementaryFlags == null) {
            supplementaryFlags = new HashMap<>();
        }

        Long flags = supplementaryFlags.get(c.packge());
        if (flags == null) {
            long newFlags = 0;
            try {
                JRTIndex.CtSym ctSym = jrtIndex.getCtSym(c.packge().flatName());
                Profile minProfile = Profile.DEFAULT;
                if (ctSym.proprietary)
                    newFlags |= PROPRIETARY;
                if (ctSym.minProfile != null)
                    minProfile = Profile.lookup(ctSym.minProfile);
                if (profile != Profile.DEFAULT && minProfile.value > profile.value) {
                    newFlags |= NOT_IN_PROFILE;
                }
            } catch (IOException ignore) {
            }
            supplementaryFlags.put(c.packge(), flags = newFlags);
        }
        return flags;
    }

    private Map<PackageSymbol, Long> supplementaryFlags;

/************************************************************************
 * Loading Classes
 ***********************************************************************/

    /** Completion for classes to be loaded. Before a class is loaded
     *  we make sure its enclosing class (if any) is loaded.
     */
    private void complete(Symbol sym) throws CompletionFailure {
        if (sym.kind == TYP) {
            try {
                ClassSymbol c = (ClassSymbol) sym;
                dependencies.push(c, CompletionCause.CLASS_READER);
                annotate.blockAnnotations();
                c.members_field = new Scope.ErrorScope(c); // make sure it's always defined
                completeOwners(c.owner);
                completeEnclosing(c);
                fillIn(c);
            } finally {
                annotate.unblockAnnotationsNoFlush();
                dependencies.pop();
            }
        } else if (sym.kind == PCK) {
            PackageSymbol p = (PackageSymbol)sym;
            try {
                fillIn(p);
            } catch (IOException ex) {
                throw new CompletionFailure(sym, ex.getLocalizedMessage()).initCause(ex);
            }
        }
        if (!reader.filling)
            annotate.flush(); // finish attaching annotations
    }

    /** complete up through the enclosing package. */
    private void completeOwners(Symbol o) {
        if (o.kind != PCK) completeOwners(o.owner);
        o.complete();
    }

    /**
     * Tries to complete lexically enclosing classes if c looks like a
     * nested class.  This is similar to completeOwners but handles
     * the situation when a nested class is accessed directly as it is
     * possible with the Tree API or javax.lang.model.*.
     */
    private void completeEnclosing(ClassSymbol c) {
        if (c.owner.kind == PCK) {
            Symbol owner = c.owner;
            for (Name name : Convert.enclosingCandidates(Convert.shortName(c.name))) {
                Symbol encl = owner.members().findFirst(name);
                if (encl == null)
                    encl = syms.getClass(c.packge().modle, TypeSymbol.formFlatName(name, owner));
                if (encl != null)
                    encl.complete();
            }
        }
    }

    /** Fill in definition of class `c' from corresponding class or
     *  source file.
     */
    void fillIn(ClassSymbol c) {
        if (completionFailureName == c.fullname) {
            throw new CompletionFailure(c, "user-selected completion failure by class name");
        }
        currentOwner = c;
        JavaFileObject classfile = c.classfile;
        if (classfile != null) {
            JavaFileObject previousClassFile = currentClassFile;
            try {
                if (reader.filling) {
                    Assert.error("Filling " + classfile.toUri() + " during " + previousClassFile);
                }
                currentClassFile = classfile;
                if (verbose) {
                    log.printVerbose("loading", currentClassFile.getName());
                }
                if (classfile.getKind() == Kind.CLASS ||
                    classfile.getKind() == Kind.OTHER) {
                    reader.readClassFile(c);
                    c.flags_field |= getSupplementaryFlags(c);
                } else {
                    if (!sourceCompleter.isTerminal()) {
                        sourceCompleter.complete(c);
                    } else {
                        throw new IllegalStateException("Source completer required to read "
                                                        + classfile.toUri());
                    }
                }
            } finally {
                currentClassFile = previousClassFile;
            }
        } else {
            throw classFileNotFound(c);
        }
    }
    // where
        private CompletionFailure classFileNotFound(ClassSymbol c) {
            JCDiagnostic diag =
                diagFactory.fragment("class.file.not.found", c.flatname);
            return newCompletionFailure(c, diag);
        }
        /** Static factory for CompletionFailure objects.
         *  In practice, only one can be used at a time, so we share one
         *  to reduce the expense of allocating new exception objects.
         */
        private CompletionFailure newCompletionFailure(TypeSymbol c,
                                                       JCDiagnostic diag) {
            if (!cacheCompletionFailure) {
                // log.warning("proc.messager",
                //             Log.getLocalizedString("class.file.not.found", c.flatname));
                // c.debug.printStackTrace();
                return new CompletionFailure(c, diag);
            } else {
                CompletionFailure result = cachedCompletionFailure;
                result.sym = c;
                result.diag = diag;
                return result;
            }
        }
        private final CompletionFailure cachedCompletionFailure =
            new CompletionFailure(null, (JCDiagnostic) null);
        {
            cachedCompletionFailure.setStackTrace(new StackTraceElement[0]);
        }


    /** Load a toplevel class with given fully qualified name
     *  The class is entered into `classes' only if load was successful.
     */
    public ClassSymbol loadClass(ModuleSymbol msym, Name flatname) throws CompletionFailure {
        Assert.checkNonNull(msym);
        Name packageName = Convert.packagePart(flatname);
        PackageSymbol ps = syms.lookupPackage(msym, packageName);

        Assert.checkNonNull(ps.modle, () -> "msym=" + msym + "; flatName=" + flatname);

        boolean absent = syms.getClass(ps.modle, flatname) == null;
        ClassSymbol c = syms.enterClass(ps.modle, flatname);

        if (c.members_field == null) {
            try {
                c.complete();
            } catch (CompletionFailure ex) {
                if (absent) syms.removeClass(ps.modle, flatname);
                throw ex;
            }
        }
        return c;
    }

/************************************************************************
 * Loading Packages
 ***********************************************************************/

    /** Include class corresponding to given class file in package,
     *  unless (1) we already have one the same kind (.class or .java), or
     *         (2) we have one of the other kind, and the given class file
     *             is older.
     */
    protected void includeClassFile(PackageSymbol p, JavaFileObject file) {
        if ((p.flags_field & EXISTS) == 0)
            for (Symbol q = p; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;
        Kind kind = file.getKind();
        int seen;
        if (kind == Kind.CLASS || kind == Kind.OTHER)
            seen = CLASS_SEEN;
        else
            seen = SOURCE_SEEN;
        String binaryName = fileManager.inferBinaryName(currentLoc, file);
        int lastDot = binaryName.lastIndexOf(".");
        Name classname = names.fromString(binaryName.substring(lastDot + 1));
        boolean isPkgInfo = classname == names.package_info;
        ClassSymbol c = isPkgInfo
            ? p.package_info
            : (ClassSymbol) p.members_field.findFirst(classname);
        if (c == null) {
            c = syms.enterClass(p.modle, classname, p);
            if (c.classfile == null) // only update the file if's it's newly created
                c.classfile = file;
            if (isPkgInfo) {
                p.package_info = c;
            } else {
                if (c.owner == p)  // it might be an inner class
                    p.members_field.enter(c);
            }
        } else if (!preferCurrent && c.classfile != null && (c.flags_field & seen) == 0) {
            // if c.classfile == null, we are currently compiling this class
            // and no further action is necessary.
            // if (c.flags_field & seen) != 0, we have already encountered
            // a file of the same kind; again no further action is necessary.
            if ((c.flags_field & (CLASS_SEEN | SOURCE_SEEN)) != 0)
                c.classfile = preferredFileObject(file, c.classfile);
        }
        c.flags_field |= seen;
    }

    /** Implement policy to choose to derive information from a source
     *  file or a class file when both are present.  May be overridden
     *  by subclasses.
     */
    protected JavaFileObject preferredFileObject(JavaFileObject a,
                                           JavaFileObject b) {

        if (preferSource)
            return (a.getKind() == Kind.SOURCE) ? a : b;
        else {
            long adate = a.getLastModified();
            long bdate = b.getLastModified();
            // 6449326: policy for bad lastModifiedTime in ClassReader
            //assert adate >= 0 && bdate >= 0;
            return (adate > bdate) ? a : b;
        }
    }

    /**
     * specifies types of files to be read when filling in a package symbol
     */
    // Note: overridden by JavadocClassFinder
    protected EnumSet<Kind> getPackageFileKinds() {
        return EnumSet.of(Kind.CLASS, Kind.SOURCE);
    }

    /**
     * this is used to support javadoc
     */
    protected void extraFileActions(PackageSymbol pack, JavaFileObject fe) {
    }

    protected Location currentLoc; // FIXME

    private boolean verbosePath = true;

    // Set to true when the currently selected file should be kept
    private boolean preferCurrent;

    /** Load directory of package into members scope.
     */
    private void fillIn(PackageSymbol p) throws IOException {
        if (p.members_field == null)
            p.members_field = WriteableScope.create(p);

        ModuleSymbol msym = p.modle;

        Assert.checkNonNull(msym, p::toString);

        msym.complete();

        if (msym == syms.noModule) {
            preferCurrent = false;
            if (userPathsFirst) {
                scanUserPaths(p, true);
                preferCurrent = true;
                scanPlatformPath(p);
            } else {
                scanPlatformPath(p);
                scanUserPaths(p, true);
            }
        } else if (msym.classLocation == StandardLocation.CLASS_PATH) {
            scanUserPaths(p, msym.sourceLocation == StandardLocation.SOURCE_PATH);
        } else {
            scanModulePaths(p, msym);
        }
    }

    // TODO: for now, this is a much simplified form of scanUserPaths
    // and (deliberately) does not default sourcepath to classpath.
    // But, we need to think about retaining existing behavior for
    // -classpath and -sourcepath for single module mode.
    // One plausible solution is to detect if the module's sourceLocation
    // is the same as the module's classLocation.
    private void scanModulePaths(PackageSymbol p, ModuleSymbol msym) throws IOException {
        Set<Kind> kinds = getPackageFileKinds();

        Set<Kind> classKinds = EnumSet.copyOf(kinds);
        classKinds.remove(Kind.SOURCE);
        boolean wantClassFiles = !classKinds.isEmpty();

        Set<Kind> sourceKinds = EnumSet.copyOf(kinds);
        sourceKinds.remove(Kind.CLASS);
        boolean wantSourceFiles = !sourceKinds.isEmpty();

        String packageName = p.fullname.toString();

        Location classLocn = msym.classLocation;
        Location sourceLocn = msym.sourceLocation;
        Location patchLocn = msym.patchLocation;
        Location patchOutLocn = msym.patchOutputLocation;

        boolean prevPreferCurrent = preferCurrent;

        try {
            preferCurrent = false;
            if (wantClassFiles && (patchOutLocn != null)) {
                fillIn(p, patchOutLocn,
                       list(patchOutLocn,
                            p,
                            packageName,
                            classKinds));
            }
            if ((wantClassFiles || wantSourceFiles) && (patchLocn != null)) {
                Set<Kind> combined = EnumSet.noneOf(Kind.class);
                combined.addAll(classKinds);
                combined.addAll(sourceKinds);
                fillIn(p, patchLocn,
                       list(patchLocn,
                            p,
                            packageName,
                            combined));
            }
            preferCurrent = true;
            if (wantClassFiles && (classLocn != null)) {
                fillIn(p, classLocn,
                       list(classLocn,
                            p,
                            packageName,
                            classKinds));
            }
            if (wantSourceFiles && (sourceLocn != null)) {
                fillIn(p, sourceLocn,
                       list(sourceLocn,
                            p,
                            packageName,
                            sourceKinds));
            }
        } finally {
            preferCurrent = prevPreferCurrent;
        }
    }

    /**
     * Scans class path and source path for files in given package.
     */
    private void scanUserPaths(PackageSymbol p, boolean includeSourcePath) throws IOException {
        Set<Kind> kinds = getPackageFileKinds();

        Set<Kind> classKinds = EnumSet.copyOf(kinds);
        classKinds.remove(Kind.SOURCE);
        boolean wantClassFiles = !classKinds.isEmpty();

        Set<Kind> sourceKinds = EnumSet.copyOf(kinds);
        sourceKinds.remove(Kind.CLASS);
        boolean wantSourceFiles = !sourceKinds.isEmpty();

        boolean haveSourcePath = includeSourcePath && fileManager.hasLocation(SOURCE_PATH);

        if (verbose && verbosePath) {
            if (fileManager instanceof StandardJavaFileManager) {
                StandardJavaFileManager fm = (StandardJavaFileManager)fileManager;
                if (haveSourcePath && wantSourceFiles) {
                    JCList<Path> path = JCList.nil();
                    for (Path sourcePath : fm.getLocationAsPaths(SOURCE_PATH)) {
                        path = path.prepend(sourcePath);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                } else if (wantSourceFiles) {
                    JCList<Path> path = JCList.nil();
                    for (Path classPath : fm.getLocationAsPaths(CLASS_PATH)) {
                        path = path.prepend(classPath);
                    }
                    log.printVerbose("sourcepath", path.reverse().toString());
                }
                if (wantClassFiles) {
                    JCList<Path> path = JCList.nil();
                    for (Path platformPath : fm.getLocationAsPaths(PLATFORM_CLASS_PATH)) {
                        path = path.prepend(platformPath);
                    }
                    for (Path classPath : fm.getLocationAsPaths(CLASS_PATH)) {
                        path = path.prepend(classPath);
                    }
                    log.printVerbose("classpath",  path.reverse().toString());
                }
            }
        }

        String packageName = p.fullname.toString();
        if (wantSourceFiles && !haveSourcePath) {
            fillIn(p, CLASS_PATH,
                   list(CLASS_PATH,
                        p,
                        packageName,
                        kinds));
        } else {
            if (wantClassFiles)
                fillIn(p, CLASS_PATH,
                       list(CLASS_PATH,
                            p,
                            packageName,
                            classKinds));
            if (wantSourceFiles)
                fillIn(p, SOURCE_PATH,
                       list(SOURCE_PATH,
                            p,
                            packageName,
                            sourceKinds));
        }
    }

    /**
     * Scans platform class path for files in given package.
     */
    private void scanPlatformPath(PackageSymbol p) throws IOException {
        fillIn(p, PLATFORM_CLASS_PATH,
               list(PLATFORM_CLASS_PATH,
                    p,
                    p.fullname.toString(),
                    allowSigFiles ? EnumSet.of(Kind.CLASS,
                                               Kind.OTHER)
                                  : EnumSet.of(Kind.CLASS)));
    }
    // where
        @SuppressWarnings("fallthrough")
        private void fillIn(PackageSymbol p,
                            Location location,
                            Iterable<JavaFileObject> files)
        {
            currentLoc = location;
            for (JavaFileObject fo : files) {
                switch (fo.getKind()) {
                case OTHER:
                    if (!isSigFile(location, fo)) {
                        extraFileActions(p, fo);
                        break;
                    }
                    //intentional fall-through:
                case CLASS:
                case SOURCE: {
                    // TODO pass binaryName to includeClassFile
                    String binaryName = fileManager.inferBinaryName(currentLoc, fo);
                    String simpleName = binaryName.substring(binaryName.lastIndexOf(".") + 1);
                    if (SourceVersion.isIdentifier(simpleName) ||
                        simpleName.equals("package-info"))
                        includeClassFile(p, fo);
                    break;
                }
                default:
                    extraFileActions(p, fo);
                    break;
                }
            }
        }

        boolean isSigFile(Location location, JavaFileObject fo) {
            return location == PLATFORM_CLASS_PATH &&
                   allowSigFiles &&
                   fo.getName().endsWith(".sig");
        }

        Iterable<JavaFileObject> list(Location location,
                                      PackageSymbol p,
                                      String packageName,
                                      Set<Kind> kinds) throws IOException {
            Iterable<JavaFileObject> listed = fileManager.list(location,
                                                               packageName,
                                                               EnumSet.allOf(Kind.class),
                                                               false);
            return () -> new Iterator<JavaFileObject>() {
                private final Iterator<JavaFileObject> original = listed.iterator();
                private JavaFileObject next;
                @Override
                public boolean hasNext() {
                    if (next == null) {
                        while (original.hasNext()) {
                            JavaFileObject fo = original.next();

                            if (fo.getKind() != Kind.CLASS &&
                                fo.getKind() != Kind.SOURCE &&
                                !isSigFile(currentLoc, fo)) {
                                p.flags_field |= Flags.HAS_RESOURCE;
                            }

                            if (kinds.contains(fo.getKind())) {
                                next = fo;
                                break;
                            }
                        }
                    }
                    return next != null;
                }

                @Override
                public JavaFileObject next() {
                    if (!hasNext())
                        throw new NoSuchElementException();
                    JavaFileObject result = next;
                    next = null;
                    return result;
                }

            };
        }

    /**
     * Used for bad class definition files, such as bad .class files or
     * for .java files with unexpected package or class names.
     */
    public static class BadClassFile extends CompletionFailure {
        private static final long serialVersionUID = 0;

        public BadClassFile(TypeSymbol sym, JavaFileObject file, JCDiagnostic diag,
                JCDiagnostic.Factory diagFactory) {
            super(sym, createBadClassFileDiagnostic(file, diag, diagFactory));
        }
        // where
        private static JCDiagnostic createBadClassFileDiagnostic(
                JavaFileObject file, JCDiagnostic diag, JCDiagnostic.Factory diagFactory) {
            String key = (file.getKind() == Kind.SOURCE
                        ? "bad.source.file.header" : "bad.class.file.header");
            return diagFactory.fragment(key, file, diag);
        }
    }

    public static class BadEnclosingMethodAttr extends BadClassFile {
        private static final long serialVersionUID = 0;

        public BadEnclosingMethodAttr(TypeSymbol sym, JavaFileObject file, JCDiagnostic diag,
                JCDiagnostic.Factory diagFactory) {
            super(sym, file, diag, diagFactory);
        }
    }
}
