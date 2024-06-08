package com.flint.tools.flintc.tree;

import com.sun.source.doctree.ErroneousTree;
import com.flint.tools.flintc.parser.Tokens.Comment;
import com.flint.tools.flintc.tree.DCTree.DCDocComment;
import com.flint.tools.flintc.tree.JCTree;

/**
 * A table giving the doc comment, if any, for any tree node.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 */
public interface DocCommentTable {
	/**
	 * Check if a tree node has a corresponding doc comment.
	 */
	public boolean hasComment(com.flint.tools.flintc.tree.JCTree tree);

	/**
	 * Get the Comment token containing the doc comment, if any, for a tree node.
	 */
	public Comment getComment(com.flint.tools.flintc.tree.JCTree tree);

	/**
	 * Get the plain text of the doc comment, if any, for a tree node.
	 */
	public String getCommentText(com.flint.tools.flintc.tree.JCTree tree);

	/**
	 * Get the parsed form of the doc comment as a DocTree. If any errors
	 * are detected during parsing, they will be reported via
	 * {@link ErroneousTree ErroneousTree} nodes within the resulting tree.
	 */
	public DCDocComment getCommentTree(com.flint.tools.flintc.tree.JCTree tree);

	/**
	 * Set the Comment to be associated with a tree node.
	 */
	public void putComment(JCTree tree, Comment c);
}
