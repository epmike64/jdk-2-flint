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

package com.flint.tools.flintc.main;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.lang.model.SourceVersion;

import com.flint.tools.flintc.code.Lint;
import com.flint.tools.flintc.code.Source;
import com.flint.tools.flintc.code.Type;
import com.flint.tools.flintc.jvm.Profile;
import com.flint.tools.flintc.jvm.Target;
import com.flint.tools.flintc.platform.PlatformProvider;
import com.flint.tools.flintc.processing.JavacProcessingEnvironment;
//import com.flint.tools.doclint.DocLint;
import com.flint.tools.flintc.code.Lint.LintCategory;
import com.flint.tools.flintc.util.Assert;
import com.flint.tools.flintc.util.JDK9Wrappers;
import com.flint.tools.flintc.util.Log;
import com.flint.tools.flintc.util.Log.PrefixKind;
import com.flint.tools.flintc.util.Log.WriterKind;
import com.flint.tools.flintc.util.Options;
import com.flint.tools.flintc.util.StringUtils;

import static com.flint.tools.flintc.main.Option.ChoiceKind.*;
import static com.flint.tools.flintc.main.Option.OptionGroup.*;
import static com.flint.tools.flintc.main.Option.OptionKind.*;

/**
 * Options for javac.
 * The specific Option to handle a command-line option can be found by calling
 * {@link #lookup}, which search some or all of the members of this enum in order,
 * looking for the first {@link #matches match}.
 * The action for an Option is performed {@link #handleOption}, which determines
 * whether an argument is needed and where to find it;
 * {@code handleOption} then calls {@link #process process} providing a suitable
 * {@link OptionHelper} to provide access the compiler state.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public enum Option {
    G("-g", "opt.g", STANDARD, BASIC),

    G_NONE("-g:none", "opt.g.none", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) {
            helper.put("-g:", "none");
        }
    },

    G_CUSTOM("-g:",  "opt.g.lines.vars.source",
            STANDARD, BASIC, ANYOF, "lines", "vars", "source"),

    XLINT("-Xlint", "opt.Xlint", EXTENDED, BASIC),

    XLINT_CUSTOM("-Xlint:", "opt.arg.Xlint", "opt.Xlint.custom", EXTENDED, BASIC, ANYOF, getXLintChoices()) {
        private final String LINT_KEY_FORMAT = LARGE_INDENT + "  %-" +
                (DEFAULT_SYNOPSIS_WIDTH + SMALL_INDENT.length() - LARGE_INDENT.length() - 2) + "s %s";
        @Override
        protected void help(Log log) {
            super.help(log);
            log.printRawLines(WriterKind.STDOUT,
                              String.format(LINT_KEY_FORMAT,
                                            "all",
                                            log.localize(PrefixKind.JAVAC, "opt.Xlint.all")));
            for (LintCategory lc : LintCategory.values()) {
                log.printRawLines(WriterKind.STDOUT,
                                  String.format(LINT_KEY_FORMAT,
                                                lc.option,
                                                log.localize(PrefixKind.JAVAC,
                                                             "opt.Xlint.desc." + lc.option)));
            }
            log.printRawLines(WriterKind.STDOUT,
                              String.format(LINT_KEY_FORMAT,
                                            "none",
                                            log.localize(PrefixKind.JAVAC, "opt.Xlint.none")));
        }
    },

    XDOCLINT("-Xdoclint", "opt.Xdoclint", EXTENDED, BASIC),

    XDOCLINT_CUSTOM("-Xdoclint:", "opt.Xdoclint.subopts", "opt.Xdoclint.custom", EXTENDED, BASIC) {
        @Override
        public boolean matches(String option) {
            return false;// DocLint.isValidOption(
//                    option.replace(XDOCLINT_CUSTOM.primaryName, DocLint.XMSGS_CUSTOM_PREFIX));
        }

        @Override
        public void process(OptionHelper helper, String option) {
            String prev = helper.get(XDOCLINT_CUSTOM);
            String next = (prev == null) ? option : (prev + " " + option);
            helper.put(XDOCLINT_CUSTOM.primaryName, next);
        }
    },

    XDOCLINT_PACKAGE("-Xdoclint/package:", "opt.Xdoclint.package.args", "opt.Xdoclint.package.desc", EXTENDED, BASIC) {
        @Override
        public boolean matches(String option) {
//            return DocLint.isValidOption(
//                    option.replace(XDOCLINT_PACKAGE.primaryName, DocLint.XCHECK_PACKAGE));
            return false;
        }

        @Override
        public void process(OptionHelper helper, String option) {
            String prev = helper.get(XDOCLINT_PACKAGE);
            String next = (prev == null) ? option : (prev + " " + option);
            helper.put(XDOCLINT_PACKAGE.primaryName, next);
        }
    },

    DOCLINT_FORMAT("--doclint-format", "opt.doclint.format", EXTENDED, BASIC, ONEOF, "html4", "html5"),

    // -nowarn is retained for command-line backward compatibility
    NOWARN("-nowarn", "opt.nowarn", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) {
            helper.put("-Xlint:none", option);
        }
    },

    VERBOSE("-verbose", "opt.verbose", STANDARD, BASIC),

    // -deprecation is retained for command-line backward compatibility
    DEPRECATION("-deprecation", "opt.deprecation", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) {
            helper.put("-Xlint:deprecation", option);
        }
    },

    CLASS_PATH("--class-path -classpath -cp", "opt.arg.path", "opt.classpath", STANDARD, FILEMANAGER),

    SOURCE_PATH("--source-path -sourcepath", "opt.arg.path", "opt.sourcepath", STANDARD, FILEMANAGER),

    MODULE_SOURCE_PATH("--module-source-path", "opt.arg.mspath", "opt.modulesourcepath", STANDARD, FILEMANAGER),

    MODULE_PATH("--module-path -p", "opt.arg.path", "opt.modulepath", STANDARD, FILEMANAGER),

    UPGRADE_MODULE_PATH("--upgrade-module-path", "opt.arg.path", "opt.upgrademodulepath", STANDARD, FILEMANAGER),

    SYSTEM("--system", "opt.arg.jdk", "opt.system", STANDARD, FILEMANAGER),

    PATCH_MODULE("--patch-module", "opt.arg.patch", "opt.patch", EXTENDED, FILEMANAGER) {
        // The deferred filemanager diagnostics mechanism assumes a single value per option,
        // but --patch-module can be used multiple times, once per module. Therefore we compose
        // a value for the option containing the last value specified for each module, and separate
        // the the module=path pairs by an invalid path character, NULL.
        // The standard file manager code knows to split apart the NULL-separated components.
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                String prev = helper.get(PATCH_MODULE);
                if (prev == null) {
                    super.process(helper, option, arg);
                } else {
                    String argModulePackage = arg.substring(0, arg.indexOf('='));
                    boolean isRepeated = Arrays.stream(prev.split("\0"))
                            .map(s -> s.substring(0, s.indexOf('=')))
                            .collect(Collectors.toSet())
                            .contains(argModulePackage);
                    if (isRepeated) {
                        throw helper.newInvalidValueException("err.repeated.value.for.patch.module", argModulePackage);
                    } else {
                        super.process(helper, option, prev + '\0' + arg);
                    }
                }
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile("([^/]+)=(,*[^,].*)");
        }
    },

    BOOT_CLASS_PATH("--boot-class-path -bootclasspath", "opt.arg.path", "opt.bootclasspath", STANDARD, FILEMANAGER) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            helper.remove("-Xbootclasspath/p:");
            helper.remove("-Xbootclasspath/a:");
            super.process(helper, option, arg);
        }
    },

    XBOOTCLASSPATH_PREPEND("-Xbootclasspath/p:", "opt.arg.path", "opt.Xbootclasspath.p", EXTENDED, FILEMANAGER),

    XBOOTCLASSPATH_APPEND("-Xbootclasspath/a:", "opt.arg.path", "opt.Xbootclasspath.a", EXTENDED, FILEMANAGER),

    XBOOTCLASSPATH("-Xbootclasspath:", "opt.arg.path", "opt.bootclasspath", EXTENDED, FILEMANAGER) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            helper.remove("-Xbootclasspath/p:");
            helper.remove("-Xbootclasspath/a:");
            super.process(helper, "-bootclasspath", arg);
        }
    },

    EXTDIRS("-extdirs", "opt.arg.dirs", "opt.extdirs", STANDARD, FILEMANAGER),

    DJAVA_EXT_DIRS("-Djava.ext.dirs=", "opt.arg.dirs", "opt.extdirs", EXTENDED, FILEMANAGER) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            EXTDIRS.process(helper, "-extdirs", arg);
        }
    },

    ENDORSEDDIRS("-endorseddirs", "opt.arg.dirs", "opt.endorseddirs", STANDARD, FILEMANAGER),

    DJAVA_ENDORSED_DIRS("-Djava.endorsed.dirs=", "opt.arg.dirs", "opt.endorseddirs", EXTENDED, FILEMANAGER) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            ENDORSEDDIRS.process(helper, "-endorseddirs", arg);
        }
    },

    PROC("-proc:", "opt.proc.none.only", STANDARD, BASIC,  ONEOF, "none", "only"),

    PROCESSOR("-processor", "opt.arg.class.list", "opt.processor", STANDARD, BASIC),

    PROCESSOR_PATH("--processor-path -processorpath", "opt.arg.path", "opt.processorpath", STANDARD, FILEMANAGER),

    PROCESSOR_MODULE_PATH("--processor-module-path", "opt.arg.path", "opt.processormodulepath", STANDARD, FILEMANAGER),

    PARAMETERS("-parameters","opt.parameters", STANDARD, BASIC),

    D("-d", "opt.arg.directory", "opt.d", STANDARD, FILEMANAGER),

    S("-s", "opt.arg.directory", "opt.sourceDest", STANDARD, FILEMANAGER),

    H("-h", "opt.arg.directory", "opt.headerDest", STANDARD, FILEMANAGER),

    IMPLICIT("-implicit:", "opt.implicit", STANDARD, BASIC, ONEOF, "none", "class"),

    ENCODING("-encoding", "opt.arg.encoding", "opt.encoding", STANDARD, FILEMANAGER),

    SOURCE("-source", "opt.arg.release", "opt.source", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String operand) throws Option.InvalidValueException {
            Source source = Source.lookup(operand);
            if (source == null) {
                throw helper.newInvalidValueException("err.invalid.source", operand);
            }
            super.process(helper, option, operand);
        }
    },

    TARGET("-target", "opt.arg.release", "opt.target", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String operand) throws Option.InvalidValueException {
            Target target = Target.lookup(operand);
            if (target == null) {
                throw helper.newInvalidValueException("err.invalid.target", operand);
            }
            super.process(helper, option, operand);
        }
    },

    RELEASE("--release", "opt.arg.release", "opt.release", STANDARD, BASIC) {
        @Override
        protected void help(Log log) {
            Iterable<PlatformProvider> providers =
                    ServiceLoader.load(PlatformProvider.class, Arguments.class.getClassLoader());
            Set<String> platforms = StreamSupport.stream(providers.spliterator(), false)
                                                 .flatMap(provider -> StreamSupport.stream(provider.getSupportedPlatformNames()
                                                                                                   .spliterator(),
                                                                                           false))
                                                 .collect(Collectors.toCollection(TreeSet :: new));

            StringBuilder targets = new StringBuilder();
            String delim = "";
            for (String platform : platforms) {
                targets.append(delim);
                targets.append(platform);
                delim = ", ";
            }

            super.help(log, log.localize(PrefixKind.JAVAC, descrKey, targets.toString()));
        }
    },

    PROFILE("-profile", "opt.arg.profile", "opt.profile", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String operand) throws Option.InvalidValueException {
            Profile profile = Profile.lookup(operand);
            if (profile == null) {
                throw helper.newInvalidValueException("err.invalid.profile", operand);
            }
            super.process(helper, option, operand);
        }
    },

    VERSION("--version -version", "opt.version", STANDARD, INFO) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(WriterKind.STDOUT, PrefixKind.JAVAC, "version", ownName,  JavaCompiler.version());
            super.process(helper, option);
        }
    },

    FULLVERSION("--full-version -fullversion", null, HIDDEN, INFO) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(WriterKind.STDOUT, PrefixKind.JAVAC, "fullVersion", ownName,  JavaCompiler.fullVersion());
            super.process(helper, option);
        }
    },

    // Note: -h is already taken for "native header output directory".
    HELP("--help -help", "opt.help", STANDARD, INFO) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(WriterKind.STDOUT, PrefixKind.JAVAC, "msg.usage.header", ownName);
            showHelp(log, Option.OptionKind.STANDARD);
            log.printNewline(WriterKind.STDOUT);
            super.process(helper, option);
        }
    },

    A("-A", "opt.arg.key.equals.value", "opt.A", STANDARD, BASIC, ArgKind.ADJACENT) {
        @Override
        public boolean matches(String arg) {
            return arg.startsWith("-A");
        }

        @Override
        public boolean hasArg() {
            return false;
        }
        // Mapping for processor options created in
        // JavacProcessingEnvironment
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            int argLength = option.length();
            if (argLength == 2) {
                throw helper.newInvalidValueException("err.empty.A.argument");
            }
            int sepIndex = option.indexOf('=');
            String key = option.substring(2, (sepIndex != -1 ? sepIndex : argLength) );
            if (!JavacProcessingEnvironment.isValidOptionName(key)) {
                throw helper.newInvalidValueException("err.invalid.A.key", option);
            }
            helper.put(option, option);
        }
    },

    DEFAULT_MODULE_FOR_CREATED_FILES("--default-module-for-created-files",
                                     "opt.arg.default.module.for.created.files",
                                     "opt.default.module.for.created.files", EXTENDED, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            String prev = helper.get(DEFAULT_MODULE_FOR_CREATED_FILES);
            if (prev != null) {
                throw helper.newInvalidValueException("err.option.too.many",
                                                      DEFAULT_MODULE_FOR_CREATED_FILES.primaryName);
            } else if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                helper.put(DEFAULT_MODULE_FOR_CREATED_FILES.primaryName, arg);
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile("[^,].*");
        }
    },

    X("--help-extra -X", "opt.X", STANDARD, INFO) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Log log = helper.getLog();
            showHelp(log, Option.OptionKind.EXTENDED);
            log.printNewline(WriterKind.STDOUT);
            log.printLines(WriterKind.STDOUT, PrefixKind.JAVAC, "msg.usage.nonstandard.footer");
            super.process(helper, option);
        }
    },

    // This option exists only for the purpose of documenting itself.
    // It's actually implemented by the launcher.
    J("-J", "opt.arg.flag", "opt.J", STANDARD, INFO, ArgKind.ADJACENT) {
        @Override
        public void process(OptionHelper helper, String option) {
            throw new AssertionError("the -J flag should be caught by the launcher.");
        }
    },

    MOREINFO("-moreinfo", null, HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Type.moreInfo = true;
            super.process(helper, option);
        }
    },

    // treat warnings as errors
    WERROR("-Werror", "opt.Werror", STANDARD, BASIC),

    // prompt after each error
    // new Option("-prompt",                                        "opt.prompt"),
    PROMPT("-prompt", null, HIDDEN, BASIC),

    // dump stack on error
    DOE("-doe", null, HIDDEN, BASIC),

    // output source after type erasure
    PRINTSOURCE("-printsource", null, HIDDEN, BASIC),

    // display warnings for generic unchecked operations
    WARNUNCHECKED("-warnunchecked", null, HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) {
            helper.put("-Xlint:unchecked", option);
        }
    },

    XMAXERRS("-Xmaxerrs", "opt.arg.number", "opt.maxerrs", EXTENDED, BASIC),

    XMAXWARNS("-Xmaxwarns", "opt.arg.number", "opt.maxwarns", EXTENDED, BASIC),

    XSTDOUT("-Xstdout", "opt.arg.file", "opt.Xstdout", EXTENDED, INFO) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            try {
                Log log = helper.getLog();
                log.setWriters(new PrintWriter(new FileWriter(arg), true));
            } catch (java.io.IOException e) {
                throw helper.newInvalidValueException("err.error.writing.file", arg, e);
            }
            super.process(helper, option, arg);
        }
    },

    XPRINT("-Xprint", "opt.print", EXTENDED, BASIC),

    XPRINTROUNDS("-XprintRounds", "opt.printRounds", EXTENDED, BASIC),

    XPRINTPROCESSORINFO("-XprintProcessorInfo", "opt.printProcessorInfo", EXTENDED, BASIC),

    XPREFER("-Xprefer:", "opt.prefer", EXTENDED, BASIC, ONEOF, "source", "newer"),

    XXUSERPATHSFIRST("-XXuserPathsFirst", "opt.userpathsfirst", HIDDEN, BASIC),

    // see enum PkgInfo
    XPKGINFO("-Xpkginfo:", "opt.pkginfo", EXTENDED, BASIC, ONEOF, "always", "legacy", "nonempty"),

    /* -O is a no-op, accepted for backward compatibility. */
    O("-O", null, HIDDEN, BASIC),

    /* -Xjcov produces tables to support the code coverage tool jcov. */
    XJCOV("-Xjcov", null, HIDDEN, BASIC),

    PLUGIN("-Xplugin:", "opt.arg.plugin", "opt.plugin", EXTENDED, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) {
            String p = option.substring(option.indexOf(':') + 1).trim();
            String prev = helper.get(PLUGIN);
            helper.put(PLUGIN.primaryName, (prev == null) ? p : prev + '\0' + p);
        }
    },

    XDIAGS("-Xdiags:", "opt.diags", EXTENDED, BASIC, ONEOF, "compact", "verbose"),

    DEBUG("--debug:", null, HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Option.HiddenGroup.DEBUG.process(helper, option);
        }
    },

    SHOULDSTOP("--should-stop:", null, HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Option.HiddenGroup.SHOULDSTOP.process(helper, option);
        }
    },

    DIAGS("--diags:", null, HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            Option.HiddenGroup.DIAGS.process(helper, option);
        }
    },

    /* This is a back door to the compiler's option table.
     * -XDx=y sets the option x to the value y.
     * -XDx sets the option x to the value x.
     */
    XD("-XD", null, HIDDEN, BASIC) {
        @Override
        public boolean matches(String s) {
            return s.startsWith(primaryName);
        }
        @Override
        public void process(OptionHelper helper, String option) {
            process(helper, option, option.substring(primaryName.length()));
        }

        @Override
        public void process(OptionHelper helper, String option, String arg) {
            int eq = arg.indexOf('=');
            String key = (eq < 0) ? arg : arg.substring(0, eq);
            String value = (eq < 0) ? arg : arg.substring(eq+1);
            helper.put(key, value);
        }
    },

    ADD_EXPORTS("--add-exports", "opt.arg.addExports", "opt.addExports", EXTENDED, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                String prev = helper.get(ADD_EXPORTS);
                helper.put(ADD_EXPORTS.primaryName, (prev == null) ? arg : prev + '\0' + arg);
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile("([^/]+)/([^=]+)=(,*[^,].*)");
        }
    },

    ADD_OPENS("--add-opens", null, null, HIDDEN, BASIC),

    ADD_READS("--add-reads", "opt.arg.addReads", "opt.addReads", EXTENDED, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                String prev = helper.get(ADD_READS);
                helper.put(ADD_READS.primaryName, (prev == null) ? arg : prev + '\0' + arg);
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile("([^=]+)=(,*[^,].*)");
        }
    },

    MODULE("--module -m", "opt.arg.m", "opt.m", STANDARD, BASIC),

    ADD_MODULES("--add-modules", "opt.arg.addmods", "opt.addmods", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                String prev = helper.get(ADD_MODULES);
                // since the individual values are simple names, we can simply join the
                // values of multiple --add-modules options with ','
                helper.put(ADD_MODULES.primaryName, (prev == null) ? arg : prev + ',' + arg);
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile(",*[^,].*");
        }
    },

    LIMIT_MODULES("--limit-modules", "opt.arg.limitmods", "opt.limitmods", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else if (getPattern().matcher(arg).matches()) {
                helper.put(LIMIT_MODULES.primaryName, arg); // last one wins
            } else {
                throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
            }
        }

        @Override
        public Pattern getPattern() {
            return Pattern.compile(",*[^,].*");
        }
    },

    MODULE_VERSION("--module-version", "opt.arg.module.version", "opt.module.version", STANDARD, BASIC) {
        @Override
        public void process(OptionHelper helper, String option, String arg) throws Option.InvalidValueException {
            if (arg.isEmpty()) {
                throw helper.newInvalidValueException("err.no.value.for.option", option);
            } else {
                try {
                    Class.forName(JDK9Wrappers.ModuleDescriptor.Version.CLASSNAME);
                    // use official parser if available
                    try {
                        JDK9Wrappers.ModuleDescriptor.Version.parse(arg);
                    } catch (IllegalArgumentException e) {
                        throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
                    }
                } catch (ClassNotFoundException ex) {
                    // fall-back to simplistic rules when running on older platform
                    if (!(arg.charAt(0) >= '0' && arg.charAt(0) <= '9') ||
                        arg.endsWith("-") ||
                        arg.endsWith("+")) {
                        throw helper.newInvalidValueException("err.bad.value.for.option", option, arg);
                    }
                }
            }
            super.process(helper, option, arg);
        }
    },

    // This option exists only for the purpose of documenting itself.
    // It's actually implemented by the CommandLine class.
    AT("@", "opt.arg.file", "opt.AT", STANDARD, INFO, ArgKind.ADJACENT) {
        @Override
        public void process(OptionHelper helper, String option) {
            throw new AssertionError("the @ flag should be caught by CommandLine.");
        }
    },

    // Standalone positional argument: source file or type name.
    SOURCEFILE("sourcefile", null, HIDDEN, INFO) {
        @Override
        public boolean matches(String s) {
            if (s.endsWith(".java"))  // Java source file
                return true;
            int sep = s.indexOf('/');
            if (sep != -1) {
                return SourceVersion.isName(s.substring(0, sep))
                        && SourceVersion.isName(s.substring(sep + 1));
            } else {
                return SourceVersion.isName(s);   // Legal type name
            }
        }
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            if (option.endsWith(".java") ) {
                Path p = Paths.get(option);
                if (!Files.exists(p)) {
                    throw helper.newInvalidValueException("err.file.not.found", p);
                }
                if (!Files.isRegularFile(p)) {
                    throw helper.newInvalidValueException("err.file.not.file", p);
                }
                helper.addFile(p);
            } else {
                helper.addClassName(option);
            }
        }
    },

    MULTIRELEASE("--multi-release", "opt.arg.multi-release", "opt.multi-release", HIDDEN, FILEMANAGER),

    INHERIT_RUNTIME_ENVIRONMENT("--inherit-runtime-environment", "opt.inherit_runtime_environment",
            HIDDEN, BASIC) {
        @Override
        public void process(OptionHelper helper, String option) throws Option.InvalidValueException {
            try {
                Class.forName(JDK9Wrappers.VMHelper.CLASSNAME);
                String[] runtimeArgs = JDK9Wrappers.VMHelper.getRuntimeArguments();
                for (String arg : runtimeArgs) {
                    // Handle any supported runtime options; ignore all others.
                    // The runtime arguments always use the single token form, e.g. "--name=value".
                    for (Option o : getSupportedRuntimeOptions()) {
                        if (o.matches(arg)) {
                            switch (o) {
                                case ADD_MODULES:
                                    int eq = arg.indexOf('=');
                                    Assert.check(eq > 0, () -> ("invalid runtime option:" + arg));
                                    // --add-modules=ALL-DEFAULT is not supported at compile-time
                                    // so remove it from list, and only process the rest
                                    // if the set is non-empty.
                                    // Note that --add-modules=ALL-DEFAULT is automatically added
                                    // by the standard javac launcher.
                                    String mods = Arrays.stream(arg.substring(eq + 1).split(","))
                                            .filter(s -> !s.isEmpty() && !s.equals("ALL-DEFAULT"))
                                            .collect(Collectors.joining(","));
                                    if (!mods.isEmpty()) {
                                        String updatedArg = arg.substring(0, eq + 1) + mods;
                                        o.handleOption(helper, updatedArg, Collections.emptyIterator());
                                    }
                                    break;
                                default:
                                    o.handleOption(helper, arg, Collections.emptyIterator());
                                    break;
                            }
                            break;
                        }
                    }
                }
            } catch (ClassNotFoundException | SecurityException e) {
                throw helper.newInvalidValueException("err.cannot.access.runtime.env");
            }
        }

        private Option[] getSupportedRuntimeOptions() {
            Option[] supportedRuntimeOptions = {
                ADD_EXPORTS,
                ADD_MODULES,
                LIMIT_MODULES,
                MODULE_PATH,
                UPGRADE_MODULE_PATH,
                PATCH_MODULE
            };
            return supportedRuntimeOptions;
        }
    };

    /**
     * This exception is thrown when an invalid value is given for an option.
     * The detail string gives a detailed, localized message, suitable for use
     * in error messages reported to the user.
     */
    public static class InvalidValueException extends Exception {
        private static final long serialVersionUID = -1;

        public InvalidValueException(String msg) {
            super(msg);
        }

        public InvalidValueException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * The kind of argument, if any, accepted by this option. The kind is augmented
     * by characters in the name of the option.
     */
    public enum ArgKind {
        /** This option does not take any argument. */
        NONE,

// Not currently supported
//        /**
//         * This option takes an optional argument, which may be provided directly after an '='
//         * separator, or in the following argument position if that word does not itself appear
//         * to be the name of an option.
//         */
//        OPTIONAL,

        /**
         * This option takes an argument.
         * If the name of option ends with ':' or '=', the argument must be provided directly
         * after that separator.
         * Otherwise, it may appear after an '=' or in the following argument position.
         */
        REQUIRED,

        /**
         * This option takes an argument immediately after the option name, with no separator
         * character.
         */
        ADJACENT
    }

    /**
     * The kind of an Option. This is used by the -help and -X options.
     */
    public enum OptionKind {
        /** A standard option, documented by -help. */
        STANDARD,
        /** An extended option, documented by -X. */
        EXTENDED,
        /** A hidden option, not documented. */
        HIDDEN,
    }

    /**
     * The group for an Option. This determines the situations in which the
     * option is applicable.
     */
    enum OptionGroup {
        /** A basic option, available for use on the command line or via the
         *  Compiler API. */
        BASIC,
        /** An option for javac's standard JavaFileManager. Other file managers
         *  may or may not support these options. */
        FILEMANAGER,
        /** A command-line option that requests information, such as -help. */
        INFO,
        /** A command-line "option" representing a file or class name. */
        OPERAND
    }

    /**
     * The kind of choice for "choice" options.
     */
    enum ChoiceKind {
        /** The expected value is exactly one of the set of choices. */
        ONEOF,
        /** The expected value is one of more of the set of choices. */
        ANYOF
    }

    enum HiddenGroup {
        DIAGS("diags"),
        DEBUG("debug"),
        SHOULDSTOP("should-stop");

        static final Set<String> skipSet = new java.util.HashSet<>(
                Arrays.asList("--diags:", "--debug:", "--should-stop:"));

        final String text;

        HiddenGroup(String text) {
            this.text = text;
        }

        public void process(OptionHelper helper, String option) throws InvalidValueException {
            String p = option.substring(option.indexOf(':') + 1).trim();
            String[] subOptions = p.split(";");
            for (String subOption : subOptions) {
                subOption = text + "." + subOption.trim();
                XD.process(helper, subOption, subOption);
            }
        }

        static boolean skip(String name) {
            return skipSet.contains(name);
        }
    }

    /**
     * The "primary name" for this option.
     * This is the name that is used to put values in the {@link Options} table.
     */
    public final String primaryName;

    /**
     * The set of names (primary name and aliases) for this option.
     * Note that some names may end in a separator, to indicate that an argument must immediately
     * follow the separator (and cannot appear in the following argument position.
     */
    public final String[] names;

    /** Documentation key for arguments. */
    protected final String argsNameKey;

    /** Documentation key for description.
     */
    protected final String descrKey;

    /** The kind of this option. */
    private final OptionKind kind;

    /** The group for this option. */
    private final OptionGroup group;

    /** The kind of argument for this option. */
    private final ArgKind argKind;

    /** The kind of choices for this option, if any. */
    private final ChoiceKind choiceKind;

    /** The choices for this option, if any. */
    private final Set<String> choices;

    /**
     * Looks up the first option matching the given argument in the full set of options.
     * @param arg the argument to be matches
     * @return the first option that matches, or null if none.
     */
    public static Option lookup(String arg) {
        return lookup(arg, EnumSet.allOf(Option.class));
    }

    /**
     * Looks up the first option matching the given argument within a set of options.
     * @param arg the argument to be matched
     * @param options the set of possible options
     * @return the first option that matches, or null if none.
     */
    public static Option lookup(String arg, Set<Option> options) {
        for (Option option: options) {
            if (option.matches(arg))
                return option;
        }
        return null;
    }

    /**
     * Writes the "command line help" for given kind of option to the log.
     * @param log the log
     * @param kind  the kind of options to select
     */
    private static void showHelp(Log log, OptionKind kind) {
        Comparator<Option> comp = new Comparator<Option>() {
            final Collator collator = Collator.getInstance(Locale.US);
            { collator.setStrength(Collator.PRIMARY); }

            @Override
            public int compare(Option o1, Option o2) {
                return collator.compare(o1.primaryName, o2.primaryName);
            }
        };

        getJavaCompilerOptions()
                .stream()
                .filter(o -> o.kind == kind)
                .sorted(comp)
                .forEach(o -> {
                    o.help(log);
                });
    }

    Option(String text, String descrKey,
            OptionKind kind, OptionGroup group) {
        this(text, null, descrKey, kind, group, null, null, ArgKind.NONE);
    }

    Option(String text, String argsNameKey, String descrKey,
            OptionKind kind, OptionGroup group) {
        this(text, argsNameKey, descrKey, kind, group, null, null, ArgKind.REQUIRED);
    }

    Option(String text, String argsNameKey, String descrKey,
            OptionKind kind, OptionGroup group, ArgKind ak) {
        this(text, argsNameKey, descrKey, kind, group, null, null, ak);
    }

    Option(String text, String argsNameKey, String descrKey, OptionKind kind, OptionGroup group,
            ChoiceKind choiceKind, Set<String> choices) {
        this(text, argsNameKey, descrKey, kind, group, choiceKind, choices, ArgKind.REQUIRED);
    }

    Option(String text, String descrKey,
            OptionKind kind, OptionGroup group,
            ChoiceKind choiceKind, String... choices) {
        this(text, null, descrKey, kind, group, choiceKind,
                new LinkedHashSet<>(Arrays.asList(choices)), ArgKind.REQUIRED);
    }

    private Option(String text, String argsNameKey, String descrKey,
            OptionKind kind, OptionGroup group,
            ChoiceKind choiceKind, Set<String> choices,
            ArgKind argKind) {
        this.names = text.trim().split("\\s+");
        Assert.check(names.length >= 1);
        this.primaryName = names[0];
        this.argsNameKey = argsNameKey;
        this.descrKey = descrKey;
        this.kind = kind;
        this.group = group;
        this.choiceKind = choiceKind;
        this.choices = choices;
        this.argKind = argKind;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public OptionKind getKind() {
        return kind;
    }

    public ArgKind getArgKind() {
        return argKind;
    }

    public boolean hasArg() {
        return (argKind != ArgKind.NONE);
    }

    public boolean matches(String option) {
        for (String name: names) {
            if (matches(option, name))
                return true;
        }
        return false;
    }

    private boolean matches(String option, String name) {
        if (name.startsWith("--") && !HiddenGroup.skip(name)) {
            return option.equals(name)
                    || hasArg() && option.startsWith(name + "=");
        }

        boolean hasSuffix = (argKind == ArgKind.ADJACENT)
                || name.endsWith(":") || name.endsWith("=");

        if (!hasSuffix)
            return option.equals(name);

        if (!option.startsWith(name))
            return false;

        if (choices != null) {
            String arg = option.substring(name.length());
            if (choiceKind == ChoiceKind.ONEOF)
                return choices.contains(arg);
            else {
                for (String a: arg.split(",+")) {
                    if (!choices.contains(a))
                        return false;
                }
            }
        }

        return true;
    }

    /**
     * Handles an option.
     * If an argument for the option is required, depending on spec of the option, it will be found
     * as part of the current arg (following ':' or '=') or in the following argument.
     * This is the recommended way to handle an option directly, instead of calling the underlying
     * {@link #process process} methods.
     * @param helper a helper to provide access to the environment
     * @param arg the arg string that identified this option
     * @param rest the remaining strings to be analysed
     * @throws InvalidValueException if the value of the option was invalid
     * @implNote The return value is the opposite of that used by {@link #process}.
     */
    public void handleOption(OptionHelper helper, String arg, Iterator<String> rest) throws InvalidValueException {
        if (hasArg()) {
            String option;
            String operand;
            int sep = findSeparator(arg);
            if (getArgKind() == ArgKind.ADJACENT) {
                option = primaryName; // aliases not supported
                operand = arg.substring(primaryName.length());
            } else if (sep > 0) {
                option = arg.substring(0, sep);
                operand = arg.substring(sep + 1);
            } else {
                if (!rest.hasNext()) {
                    throw helper.newInvalidValueException("err.req.arg", arg);
                }
                option = arg;
                operand = rest.next();
            }
            process(helper, option, operand);
        } else {
            process(helper, arg);
        }
    }

    /**
     * Processes an option that either does not need an argument,
     * or which contains an argument within it, following a separator.
     * @param helper a helper to provide access to the environment
     * @param option the option to be processed
     * @throws InvalidValueException if an error occurred
     */
    public void process(OptionHelper helper, String option) throws InvalidValueException {
        if (argKind == ArgKind.NONE) {
            process(helper, primaryName, option);
        } else {
            int sep = findSeparator(option);
            process(helper, primaryName, option.substring(sep + 1));
        }
    }

    /**
     * Processes an option by updating the environment via a helper object.
     * @param helper a helper to provide access to the environment
     * @param option the option to be processed
     * @param arg the value to associate with the option, or a default value
     *  to be used if the option does not otherwise take an argument.
     * @throws InvalidValueException if an error occurred
     */
    public void process(OptionHelper helper, String option, String arg) throws InvalidValueException {
        if (choices != null) {
            if (choiceKind == ChoiceKind.ONEOF) {
                // some clients like to see just one of option+choice set
                for (String s : choices)
                    helper.remove(primaryName + s);
                String opt = primaryName + arg;
                helper.put(opt, opt);
                // some clients like to see option (without trailing ":")
                // set to arg
                String nm = primaryName.substring(0, primaryName.length() - 1);
                helper.put(nm, arg);
            } else {
                // set option+word for each word in arg
                for (String a: arg.split(",+")) {
                    String opt = primaryName + a;
                    helper.put(opt, opt);
                }
            }
        }
        helper.put(primaryName, arg);
        if (group == OptionGroup.FILEMANAGER)
            helper.handleFileManagerOption(this, arg);
    }

    /**
     * Returns a pattern to analyze the value for an option.
     * @return the pattern
     * @throws UnsupportedOperationException if an option does not provide a pattern.
     */
    public Pattern getPattern() {
        throw new UnsupportedOperationException();
    }

    /**
     * Scans a word to find the first separator character, either colon or equals.
     * @param word the word to be scanned
     * @return the position of the first':' or '=' character in the word,
     *  or -1 if none found
     */
    private static int findSeparator(String word) {
        for (int i = 0; i < word.length(); i++) {
            switch (word.charAt(i)) {
                case ':': case '=':
                    return i;
            }
        }
        return -1;
    }

    /** The indent for the option synopsis. */
    private static final String SMALL_INDENT = "  ";
    /** The automatic indent for the description. */
    private static final String LARGE_INDENT = "        ";
    /** The space allowed for the synopsis, if the description is to be shown on the same line. */
    private static final int DEFAULT_SYNOPSIS_WIDTH = 28;
    /** The nominal maximum line length, when seeing if text will fit on a line. */
    private static final int DEFAULT_MAX_LINE_LENGTH = 80;
    /** The format for a single-line help entry. */
    private static final String COMPACT_FORMAT = SMALL_INDENT + "%-" + DEFAULT_SYNOPSIS_WIDTH + "s %s";

    /**
     * Writes help text for this option to the log.
     * @param log the log
     */
    protected void help(Log log) {
        help(log, log.localize(PrefixKind.JAVAC, descrKey));
    }

    protected void help(Log log, String descr) {
        String synopses = Arrays.stream(names)
                .map(s -> helpSynopsis(s, log))
                .collect(Collectors.joining(", "));

        // If option synopses and description fit on a single line of reasonable length,
        // display using COMPACT_FORMAT
        if (synopses.length() < DEFAULT_SYNOPSIS_WIDTH
                && !descr.contains("\n")
                && (SMALL_INDENT.length() + DEFAULT_SYNOPSIS_WIDTH + 1 + descr.length() <= DEFAULT_MAX_LINE_LENGTH)) {
            log.printRawLines(WriterKind.STDOUT, String.format(COMPACT_FORMAT, synopses, descr));
            return;
        }

        // If option synopses fit on a single line of reasonable length, show that;
        // otherwise, show 1 per line
        if (synopses.length() <= DEFAULT_MAX_LINE_LENGTH) {
            log.printRawLines(WriterKind.STDOUT, SMALL_INDENT + synopses);
        } else {
            for (String name: names) {
                log.printRawLines(WriterKind.STDOUT, SMALL_INDENT + helpSynopsis(name, log));
            }
        }

        // Finally, show the description
        log.printRawLines(WriterKind.STDOUT, LARGE_INDENT + descr.replace("\n", "\n" + LARGE_INDENT));
    }

    /**
     * Composes the initial synopsis of one of the forms for this option.
     * @param name the name of this form of the option
     * @param log the log used to localize the description of the arguments
     * @return  the synopsis
     */
    private String helpSynopsis(String name, Log log) {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (argsNameKey == null) {
            if (choices != null) {
                if (!name.endsWith(":"))
                    sb.append(" ");
                String sep = "{";
                for (String choice : choices) {
                    sb.append(sep);
                    sb.append(choice);
                    sep = ",";
                }
                sb.append("}");
            }
        } else {
            if (!name.matches(".*[=:]$") && argKind != ArgKind.ADJACENT)
                sb.append(" ");
            sb.append(log.localize(PrefixKind.JAVAC, argsNameKey));
        }

        return sb.toString();
    }

    // For -XpkgInfo:value
    public enum PkgInfo {
        /**
         * Always generate package-info.class for every package-info.java file.
         * The file may be empty if there annotations with a RetentionPolicy
         * of CLASS or RUNTIME.  This option may be useful in conjunction with
         * build systems (such as Ant) that expect javac to generate at least
         * one .class file for every .java file.
         */
        ALWAYS,
        /**
         * Generate a package-info.class file if package-info.java contains
         * annotations. The file may be empty if all the annotations have
         * a RetentionPolicy of SOURCE.
         * This value is just for backwards compatibility with earlier behavior.
         * Either of the other two values are to be preferred to using this one.
         */
        LEGACY,
        /**
         * Generate a package-info.class file if and only if there are annotations
         * in package-info.java to be written into it.
         */
        NONEMPTY;

        public static PkgInfo get(Options options) {
            String v = options.get(XPKGINFO);
            return (v == null
                    ? PkgInfo.LEGACY
                    : PkgInfo.valueOf(StringUtils.toUpperCase(v)));
        }
    }

    private static Set<String> getXLintChoices() {
        Set<String> choices = new LinkedHashSet<>();
        choices.add("all");
        for (Lint.LintCategory c : Lint.LintCategory.values()) {
            choices.add(c.option);
            choices.add("-" + c.option);
        }
        choices.add("none");
        return choices;
    }

    /**
     * Returns the set of options supported by the command line tool.
     * @return the set of options.
     */
    static Set<Option> getJavaCompilerOptions() {
        return EnumSet.allOf(Option.class);
    }

    /**
     * Returns the set of options supported by the built-in file manager.
     * @return the set of options.
     */
    public static Set<Option> getJavacFileManagerOptions() {
        return getOptions(FILEMANAGER);
    }

    /**
     * Returns the set of options supported by this implementation of
     * @return the set of options.
     */
    public static Set<Option> getJavacToolOptions() {
        return getOptions(BASIC);
    }

    private static Set<Option> getOptions(OptionGroup group) {
        return Arrays.stream(Option.values())
                .filter(o -> o.group == group)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Option.class)));
    }

}
