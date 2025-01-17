/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.flint.tools.flintc.tree;

import com.flint.source.doctree.*;
import com.flint.tools.flintc.parser.Tokens.Comment;
import com.flint.tools.flintc.util.*;
import com.flint.tools.flintc.util.DefinedBy.Api;
import com.flint.tools.flintc.util.JCDiagnostic.SimpleDiagnosticPosition;

import javax.lang.model.element.Name;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public abstract class DCTree implements DocTree {

    /**
     * The position in the comment string.
     * Use {@link #getSourcePosition getSourcePosition} to convert
     * it to a position in the source file.
     *
     * TODO: why not simply translate all these values into
     * source file positions? Is it useful to have string-offset
     * positions as well?
     */
    public int pos;

    public long getSourcePosition(DCDocComment dc) {
        return dc.comment.getSourcePos(pos);
    }

    public JCDiagnostic.DiagnosticPosition pos(DCDocComment dc) {
        return new SimpleDiagnosticPosition(dc.comment.getSourcePos(pos));
    }

    /** Convert a tree to a pretty-printed string. */
    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new DocPretty(s).print(this);
        }
        catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    public static abstract class DCEndPosTree<T extends DCEndPosTree<T>> extends DCTree {

        private int endPos = Position.NOPOS;

        public int getEndPos(DCTree.DCDocComment dc) {
            return dc.comment.getSourcePos(endPos);
        }

        @SuppressWarnings("unchecked")
        public T setEndPos(int endPos) {
            this.endPos = endPos;
            return (T) this;
        }

    }

    public static class DCDocComment extends DCTree implements DocCommentTree {
        public final Comment comment; // required for the implicit source pos table

        public final List<DCTree> fullBody;
        public final List<DCTree> firstSentence;
        public final List<DCTree> body;
        public final List<DCTree> tags;

        public DCDocComment(Comment comment,
                            List<DCTree> fullBody,
                            List<DCTree> firstSentence,
                            List<DCTree> body,
                            List<DCTree> tags) {
            this.comment = comment;
            this.firstSentence = firstSentence;
            this.fullBody = fullBody;
            this.body = body;
            this.tags = tags;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DOC_COMMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocComment(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getFirstSentence() {
            return firstSentence;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getFullBody() {
            return fullBody;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBlockTags() {
            return tags;
        }

    }

    public static abstract class DCBlockTag extends DCTree implements BlockTagTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return getKind().tagName;
        }
    }

    public static abstract class DCInlineTag extends DCEndPosTree<DCInlineTag> implements InlineTagTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return getKind().tagName;
        }
    }

    public static class DCAttribute extends DCTree implements AttributeTree {
        public final Name name;
        public final ValueKind vkind;
        public final List<DCTree> value;

        DCAttribute(Name name, ValueKind vkind, List<DCTree> value) {
            Assert.check((vkind == ValueKind.EMPTY) ? (value == null) : (value != null));
            this.name = name;
            this.vkind = vkind;
            this.value = value;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ATTRIBUTE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAttribute(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ValueKind getValueKind() {
            return vkind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<DCTree> getValue() {
            return value;
        }
    }

    public static class DCAuthor extends DCBlockTag implements AuthorTree {
        public final List<DCTree> name;

        DCAuthor(List<DCTree> name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.AUTHOR;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAuthor(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getName() {
            return name;
        }
    }

    public static class DCComment extends DCTree implements CommentTree {
        public final String body;

        DCComment(String body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.COMMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitComment(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return body;
        }
    }

    public static class DCDeprecated extends DCBlockTag implements DeprecatedTree {
        public final List<DCTree> body;

        DCDeprecated(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DEPRECATED;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDeprecated(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCDocRoot extends DCInlineTag implements DocRootTree {

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DOC_ROOT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocRoot(this, d);
        }
    }

    public static class DCEndElement extends DCTree implements EndElementTree {
        public final Name name;

        DCEndElement(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.END_ELEMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEndElement(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCEntity extends DCTree implements EntityTree {
        public final Name name;

        DCEntity(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ENTITY;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEntity(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCErroneous extends DCTree implements ErroneousTree, JCDiagnostic.DiagnosticPosition {
        public final String body;
        public final JCDiagnostic diag;

        DCErroneous(String body, JCDiagnostic.Factory diags, DiagnosticSource diagSource, String code, Object... args) {
            this.body = body;
            this.diag = diags.error(null, diagSource, this, code, args);
        }

        DCErroneous(String body, JCDiagnostic diag) {
            this.body = body;
            this.diag = diag;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ERRONEOUS;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitErroneous(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Diagnostic<JavaFileObject> getDiagnostic() {
            return diag;
        }

        @Override
        public com.flint.tools.flintc.tree.JCTree getTree() {
            return null;
        }

        @Override
        public int getStartPosition() {
            return pos;
        }

        @Override
        public int getPreferredPosition() {
            return pos + body.length() - 1;
        }

        @Override
        public int getEndPosition(EndPosTable endPosTable) {
            return pos + body.length();
        }

    }

    public static class DCHidden extends DCBlockTag implements HiddenTree {
        public final List<DCTree> body;

        DCHidden(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.HIDDEN;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitHidden(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCIdentifier extends DCTree implements IdentifierTree {
        public final Name name;

        DCIdentifier(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.IDENTIFIER;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitIdentifier(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCIndex extends DCInlineTag implements IndexTree {
        public final DCTree term;
        public final List<DCTree> description;

        DCIndex(DCTree term, List<DCTree> description) {
            this.term = term;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.INDEX;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitIndex(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public DocTree getSearchTerm() {
            return term;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCInheritDoc extends DCInlineTag implements InheritDocTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.INHERIT_DOC;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitInheritDoc(this, d);
        }
    }

    public static class DCLink extends DCInlineTag implements LinkTree {
        public final Kind kind;
        public final DCTree.DCReference ref;
        public final List<DCTree> label;

        DCLink(Kind kind, DCTree.DCReference ref, List<DCTree> label) {
            Assert.check(kind == Kind.LINK || kind == Kind.LINK_PLAIN);
            this.kind = kind;
            this.ref = ref;
            this.label = label;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLink(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getReference() {
            return ref;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getLabel() {
            return label;
        }
    }

    public static class DCLiteral extends DCInlineTag implements LiteralTree {
        public final Kind kind;
        public final DCTree.DCText body;

        DCLiteral(Kind kind, DCTree.DCText body) {
            Assert.check(kind == Kind.CODE || kind == Kind.LITERAL);
            this.kind = kind;
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLiteral(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public DCTree.DCText getBody() {
            return body;
        }
    }

    public static class DCParam extends DCBlockTag implements ParamTree {
        public final boolean isTypeParameter;
        public final DCTree.DCIdentifier name;
        public final List<DCTree> description;

        DCParam(boolean isTypeParameter, DCTree.DCIdentifier name, List<DCTree> description) {
            this.isTypeParameter = isTypeParameter;
            this.name = name;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PARAM;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitParam(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isTypeParameter() {
            return isTypeParameter;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public IdentifierTree getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCProvides extends DCBlockTag implements ProvidesTree {
        public final DCTree.DCReference serviceType;
        public final List<DCTree> description;

        DCProvides(DCTree.DCReference serviceType, List<DCTree> description) {
            this.serviceType = serviceType;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PROVIDES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitProvides(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getServiceType() {
            return serviceType;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCReference extends DCEndPosTree<DCReference> implements ReferenceTree {
        public final String signature;

        // The following are not directly exposed through ReferenceTree
        // use DocTrees.getElement(DocTreePath)
        public final com.flint.tools.flintc.tree.JCTree qualifierExpression;
        public final Name memberName;
        public final List<com.flint.tools.flintc.tree.JCTree> paramTypes;


        DCReference(String signature, com.flint.tools.flintc.tree.JCTree qualExpr, Name member, List<JCTree> paramTypes) {
            this.signature = signature;
            qualifierExpression = qualExpr;
            memberName = member;
            this.paramTypes = paramTypes;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.REFERENCE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReference(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getSignature() {
            return signature;
        }
    }

    public static class DCReturn extends DCBlockTag implements ReturnTree {
        public final List<DCTree> description;

        DCReturn(List<DCTree> description) {
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.RETURN;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReturn(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSee extends DCBlockTag implements SeeTree {
        public final List<DCTree> reference;

        DCSee(List<DCTree> reference) {
            this.reference = reference;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SEE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSee(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getReference() {
            return reference;
        }
    }

    public static class DCSerial extends DCBlockTag implements SerialTree {
        public final List<DCTree> description;

        DCSerial(List<DCTree> description) {
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerial(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSerialData extends DCBlockTag implements SerialDataTree {
        public final List<DCTree> description;

        DCSerialData(List<DCTree> description) {
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL_DATA;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialData(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSerialField extends DCBlockTag implements SerialFieldTree {
        public final DCTree.DCIdentifier name;
        public final DCTree.DCReference type;
        public final List<DCTree> description;

        DCSerialField(DCTree.DCIdentifier name, DCTree.DCReference type, List<DCTree> description) {
            this.description = description;
            this.name = name;
            this.type = type;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL_FIELD;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialField(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public IdentifierTree getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getType() {
            return type;
        }
    }

    public static class DCSince extends DCBlockTag implements SinceTree {
        public final List<DCTree> body;

        DCSince(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SINCE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSince(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCStartElement extends DCEndPosTree<DCStartElement> implements StartElementTree {
        public final Name name;
        public final List<DCTree> attrs;
        public final boolean selfClosing;

        DCStartElement(Name name, List<DCTree> attrs, boolean selfClosing) {
            this.name = name;
            this.attrs = attrs;
            this.selfClosing = selfClosing;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.START_ELEMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitStartElement(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getAttributes() {
            return attrs;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isSelfClosing() {
            return selfClosing;
        }
    }

    public static class DCText extends DCTree implements TextTree {
        public final String text;

        DCText(String text) {
            this.text = text;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.TEXT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitText(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return text;
        }
    }

    public static class DCThrows extends DCBlockTag implements ThrowsTree {
        public final Kind kind;
        public final DCTree.DCReference name;
        public final List<DCTree> description;

        DCThrows(Kind kind, DCTree.DCReference name, List<DCTree> description) {
            Assert.check(kind == Kind.EXCEPTION || kind == Kind.THROWS);
            this.kind = kind;
            this.name = name;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitThrows(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getExceptionName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCUnknownBlockTag extends DCBlockTag implements UnknownBlockTagTree {
        public final Name name;
        public final List<DCTree> content;

        DCUnknownBlockTag(Name name, List<DCTree> content) {
            this.name = name;
            this.content = content;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.UNKNOWN_BLOCK_TAG;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownBlockTag(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return name.toString();
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getContent() {
            return content;
        }
    }

    public static class DCUnknownInlineTag extends DCInlineTag implements UnknownInlineTagTree {
        public final Name name;
        public final List<DCTree> content;

        DCUnknownInlineTag(Name name, List<DCTree> content) {
            this.name = name;
            this.content = content;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.UNKNOWN_INLINE_TAG;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownInlineTag(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return name.toString();
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getContent() {
            return content;
        }
    }

    public static class DCUses extends DCBlockTag implements UsesTree {
        public final DCTree.DCReference serviceType;
        public final List<DCTree> description;

        DCUses(DCTree.DCReference serviceType, List<DCTree> description) {
            this.serviceType = serviceType;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.USES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUses(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getServiceType() {
            return serviceType;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCValue extends DCInlineTag implements ValueTree {
        public final DCTree.DCReference ref;

        DCValue(DCTree.DCReference ref) {
            this.ref = ref;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.VALUE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitValue(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getReference() {
            return ref;
        }
    }

    public static class DCVersion extends DCBlockTag implements VersionTree {
        public final List<DCTree> body;

        DCVersion(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.VERSION;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitVersion(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

}
