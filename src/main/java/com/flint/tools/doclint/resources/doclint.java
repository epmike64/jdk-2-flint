package com.flint.tools.doclint.resources;

public final class doclint extends java.util.ListResourceBundle {
    protected final Object[][] getContents() {
        return new Object[][] {
            { "dc.anchor.already.defined", "anchor already defined: \"{0}\"" },
            { "dc.anchor.value.missing", "no value given for anchor" },
            { "dc.attr.lacks.value", "attribute lacks value" },
            { "dc.attr.not.number", "attribute value is not a number" },
            { "dc.attr.not.supported.html4", "attribute not supported in HTML4: {0}" },
            { "dc.attr.not.supported.html5", "attribute not supported in HTML5: {0}" },
            { "dc.attr.obsolete", "attribute obsolete: {0}" },
            { "dc.attr.obsolete.use.css", "attribute obsolete, use CSS instead: {0}" },
            { "dc.attr.repeated", "repeated attribute: {0}" },
            { "dc.attr.table.border.html5", "attribute border for table only accepts \"\" or \"1\", use CSS instead: {0}" },
            { "dc.attr.unknown", "unknown attribute: {0}" },
            { "dc.bad.option", "bad option: {0}" },
            { "dc.bad.value.for.option", "bad value for option: {0} {1}" },
            { "dc.empty", "no description for @{0}" },
            { "dc.entity.invalid", "invalid entity &{0};" },
            { "dc.exception.not.thrown", "exception not thrown: {0}" },
            { "dc.exists.param", "@param \"{0}\" has already been specified" },
            { "dc.exists.return", "@return has already been specified" },
            { "dc.invalid.anchor", "invalid name for anchor: \"{0}\"" },
            { "dc.invalid.param", "invalid use of @param" },
            { "dc.invalid.provides", "invalid use of @provides" },
            { "dc.invalid.return", "invalid use of @return" },
            { "dc.invalid.throws", "invalid use of @throws" },
            { "dc.invalid.uri", "invalid uri: \"{0}\"" },
            { "dc.invalid.uses", "invalid use of @uses" },
            { "dc.main.ioerror", "IO error: {0}" },
            { "dc.main.no.files.given", "No files given" },
            { "dc.main.usage", "Usage:\n    doclint [options] source-files...\n\nOptions:\n  -Xmsgs  \n    Same as -Xmsgs:all\n  -Xmsgs:values\n    Specify categories of issues to be checked, where ''values''\n    is a comma-separated list of any of the following:\n      reference      show places where comments contain incorrect\n                     references to Java source code elements\n      syntax         show basic syntax errors within comments\n      html           show issues with HTML tags and attributes\n      accessibility  show issues for accessibility\n      missing        show issues with missing documentation\n      all            all of the above\n    Precede a value with ''-'' to negate it\n    Categories may be qualified by one of:\n      /public /protected /package /private\n    For positive categories (not beginning with ''-'')\n    the qualifier applies to that access level and above.\n    For negative categories (beginning with ''-'')\n    the qualifier applies to that access level and below.\n    If a qualifier is missing, the category applies to\n    all access levels.\n    For example, -Xmsgs:all,-syntax/private\n    This will enable all messages, except syntax errors\n    in the doc comments of private methods.\n    If no -Xmsgs options are provided, the default is\n    equivalent to -Xmsgs:all/protected, meaning that\n    all messages are reported for protected and public\n    declarations only. \n  -XcheckPackage:<packages>\n    Enable or disable checks in specific packages.\n    <packages> is a comma separated list of package specifiers.\n    Package specifier is either a qualified name of a package\n    or a package name prefix followed by ''.*'', which expands to\n    all sub-packages of the given package. Prefix the package specifier\n    with ''-'' to disable checks for the specified packages.\n  -stats\n    Report statistics on the reported issues.\n  -h -help --help -usage -?\n    Show this message.\n\nThe following javac options are also supported\n  -bootclasspath, -classpath, -cp, -sourcepath, -Xmaxerrs, -Xmaxwarns\n\nTo run doclint on part of a project, put the compiled classes for your\nproject on the classpath (or bootclasspath), then specify the source files\nto be checked on the command line." },
            { "dc.missing.comment", "no comment" },
            { "dc.missing.param", "no @param for {0}" },
            { "dc.missing.return", "no @return" },
            { "dc.missing.throws", "no @throws for {0}" },
            { "dc.no.alt.attr.for.image", "no \"alt\" attribute for image" },
            { "dc.no.summary.or.caption.for.table", "no summary or caption for table" },
            { "dc.param.name.not.found", "@param name not found" },
            { "dc.ref.not.found", "reference not found" },
            { "dc.service.not.found", "service-type not found" },
            { "dc.tag.code.within.code", "'{@code'} within <code>" },
            { "dc.tag.empty", "empty <{0}> tag" },
            { "dc.tag.end.not.permitted", "invalid end tag: </{0}>" },
            { "dc.tag.end.unexpected", "unexpected end tag: </{0}>" },
            { "dc.tag.header.sequence.1", "header used out of sequence: <{0}>" },
            { "dc.tag.header.sequence.2", "header used out of sequence: <{0}>" },
            { "dc.tag.nested.not.allowed", "nested tag not allowed: <{0}>" },
            { "dc.tag.not.allowed", "element not allowed in documentation comments: <{0}>" },
            { "dc.tag.not.allowed.here", "tag not allowed here: <{0}>" },
            { "dc.tag.not.allowed.inline.element", "block element not allowed within inline element <{1}>: {0}" },
            { "dc.tag.not.allowed.inline.other", "block element not allowed here: {0}" },
            { "dc.tag.not.allowed.inline.tag", "block element not allowed within @{1}: {0}" },
            { "dc.tag.not.closed", "element not closed: {0}" },
            { "dc.tag.not.supported", "tag not supported in the generated HTML version: {0}" },
            { "dc.tag.p.in.pre", "unexpected use of <p> inside <pre> element" },
            { "dc.tag.requires.heading", "heading not found for </{0}>" },
            { "dc.tag.self.closing", "self-closing element not allowed" },
            { "dc.tag.start.unmatched", "end tag missing: </{0}>" },
            { "dc.tag.unknown", "unknown tag: {0}" },
            { "dc.text.not.allowed", "text not allowed in <{0}> element" },
            { "dc.type.arg.not.allowed", "type arguments not allowed here" },
            { "dc.unexpected.comment", "documentation comment not expected here" },
            { "dc.value.not.a.constant", "value does not refer to a constant" },
            { "dc.value.not.allowed.here", "'{@value}' not allowed here" },
        };
    }
}
