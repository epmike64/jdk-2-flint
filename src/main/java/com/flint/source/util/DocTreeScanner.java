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

package com.flint.source.util;

import com.flint.source.doctree.*;


/**
 * A TreeVisitor that visits all the child tree nodes.
 * To visit nodes of a particular type, just override the
 * corresponding visitXYZ method.
 * Inside your method, call super.visitXYZ to visit descendant
 * nodes.
 *
 * <p>The default implementation of the visitXYZ methods will determine
 * a result as follows:
 * <ul>
 * <li>If the node being visited has no children, the result will be {@code null}.
 * <li>If the node being visited has one child, the result will be the
 * result of calling {@code scan} on that child. The child may be a simple node
 * or itself a list of nodes.
 * <li> If the node being visited has more than one child, the result will
 * be determined by calling {@code scan} each child in turn, and then combining the
 * result of each scan after the first with the cumulative result
 * so far, as determined by the {@link #reduce} method. Each child may be either
 * a simple node of a list of nodes. The default behavior of the {@code reduce}
 * method is such that the result of the visitXYZ method will be the result of
 * the last child scanned.
 * </ul>
 *
 * <p>Here is an example to count the number of erroneous nodes in a tree:
 * <pre>
 *   class CountErrors extends DocTreeScanner&lt;Integer,Void&gt; {
 *      {@literal @}Override
 *      public Integer visitErroneous(ErroneousTree node, Void p) {
 *          return 1;
 *      }
 *      {@literal @}Override
 *      public Integer reduce(Integer r1, Integer r2) {
 *          return (r1 == null ? 0 : r1) + (r2 == null ? 0 : r2);
 *      }
 *   }
 * </pre>
 *
 * @since 1.8
 */
public class DocTreeScanner<R,P> implements DocTreeVisitor<R,P> {

    /**
     * Scans a single node.
     * @param node the node to be scanned
     * @param p a parameter value passed to the visit method
     * @return the result value from the visit method
     */
    public R scan(DocTree node, P p) {
        return (node == null) ? null : node.accept(this, p);
    }

    private R scanAndReduce(DocTree node, P p, R r) {
        return reduce(scan(node, p), r);
    }

    /**
     * Scans a sequence of nodes.
     * @param nodes the nodes to be scanned
     * @param p a parameter value to be passed to the visit method for each node
     * @return the combined return value from the visit methods.
     *      The values are combined using the {@link #reduce reduce} method.
     */
    public R scan(Iterable<? extends DocTree> nodes, P p) {
        R r = null;
        if (nodes != null) {
            boolean first = true;
            for (DocTree node : nodes) {
                r = (first ? scan(node, p) : scanAndReduce(node, p, r));
                first = false;
            }
        }
        return r;
    }

    private R scanAndReduce(Iterable<? extends DocTree> nodes, P p, R r) {
        return reduce(scan(nodes, p), r);
    }

    /**
     * Reduces two results into a combined result.
     * The default implementation is to return the first parameter.
     * The general contract of the method is that it may take any action whatsoever.
     * @param r1 the first of the values to be combined
     * @param r2 the second of the values to be combined
     * @return the result of combining the two parameters
     */
    public R reduce(R r1, R r2) {
        return r1;
    }


/* ***************************************************************************
 * Visitor methods
 ****************************************************************************/

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitAttribute(AttributeTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitAuthor(AuthorTree node, P p) {
        return scan(node.getName(), p);
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitComment(CommentTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitDeprecated(DeprecatedTree node, P p) {
        return scan(node.getBody(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitDocComment(DocCommentTree node, P p) {
        R r = scan(node.getFirstSentence(), p);
        r = scanAndReduce(node.getBody(), p, r);
        r = scanAndReduce(node.getBlockTags(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitDocRoot(DocRootTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitEndElement(EndElementTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitEntity(EntityTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitErroneous(ErroneousTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitHidden(HiddenTree node, P p) {
        return scan(node.getBody(), p);
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitIdentifier(IdentifierTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitIndex(IndexTree node, P p) {
        R r = scan(node.getSearchTerm(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitInheritDoc(InheritDocTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitLink(LinkTree node, P p) {
        R r = scan(node.getReference(), p);
        r = scanAndReduce(node.getLabel(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitLiteral(LiteralTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitParam(ParamTree node, P p) {
        R r = scan(node.getName(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitProvides(ProvidesTree node, P p) {
        R r = scan(node.getServiceType(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitReference(ReferenceTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitReturn(ReturnTree node, P p) {
        return scan(node.getDescription(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitSee(SeeTree node, P p) {
        return scan(node.getReference(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitSerial(SerialTree node, P p) {
        return scan(node.getDescription(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitSerialData(SerialDataTree node, P p) {
        return scan(node.getDescription(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitSerialField(SerialFieldTree node, P p) {
        R r = scan(node.getName(), p);
        r = scanAndReduce(node.getType(), p, r);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitSince(SinceTree node, P p) {
        return scan(node.getBody(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitStartElement(StartElementTree node, P p) {
        return scan(node.getAttributes(), p);
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitText(TextTree node, P p) {
        return null;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitThrows(ThrowsTree node, P p) {
        R r = scan(node.getExceptionName(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitUnknownBlockTag(UnknownBlockTagTree node, P p) {
        return scan(node.getContent(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitUnknownInlineTag(UnknownInlineTagTree node, P p) {
        return scan(node.getContent(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitUses(UsesTree node, P p) {
        R r = scan(node.getServiceType(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitValue(ValueTree node, P p) {
        return scan(node.getReference(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the children in left to right order.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitVersion(VersionTree node, P p) {
        return scan(node.getBody(), p);
    }

    /**
     * {@inheritDoc} This implementation returns {@code null}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of scanning
     */
    @Override
    public R visitOther(DocTree node, P p) {
        return null;
    }

}
