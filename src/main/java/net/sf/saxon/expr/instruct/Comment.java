////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;


/**
 * An instruction representing an xsl:comment element in the stylesheet.
 */

public final class Comment extends SimpleNodeConstructor {

    /**
     * Construct the instruction
     */

    public Comment() {
    }

    /**
     * Get the instruction name, for diagnostics and tracing
     * return the string "xsl:comment"
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_COMMENT;
    }

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return NodeKindTest.COMMENT;
    }

    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Comment exp = new Comment();
        exp.setSelect(select.copy(), getExecutable().getConfiguration());
        return exp;
    }

    public void localTypeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        // Do early checking of content if known statically

        if (select instanceof Literal) {
            String s = ((Literal) select).getValue().getStringValue();
            String s2 = checkContent(s, visitor.getStaticContext().makeEarlyEvaluationContext());
            if (!s2.equals(s)) {
                setSelect(new StringLiteral(s2), visitor.getConfiguration());
            }
        }
    }


    /**
     * Process the value of the node, to create the new node.
     *
     * @param value   the string value of the new node
     * @param context the dynamic evaluation context
     * @throws XPathException
     */

    public void processValue(CharSequence value, XPathContext context) throws XPathException {
        //String comment = expandChildren(context).toString();
        String comment = checkContent(value.toString(), context);
        SequenceReceiver out = context.getReceiver();
        out.comment(comment, locationId, 0);
    }



    /**
     * Check the content of the node, and adjust it if necessary
     *
     * @param comment the supplied content
     * @param context the dynamic context
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    @Override
    protected String checkContent(String comment, XPathContext context) throws XPathException {
        if (isXSLT()) {
            return checkContentXSLT(comment);
        } else {
            try {
                return checkContentXQuery(comment);
            } catch (XPathException err) {
                err.setXPathContext(context);
                err.setLocator(this);
                throw err;
            }
        }
    }

    /**
     * Check the content of the comment according to the XSLT rules (which fix it if it is wrong)
     * @param comment the proposed text of the comment
     * @return the adjusted text of the comment
     */

    public static String checkContentXSLT(String comment) {
        int hh;
        while ((hh = comment.indexOf("--")) >= 0) {
            comment = comment.substring(0, hh + 1) + ' ' + comment.substring(hh + 1);
        }
        if (comment.length() > 0 && comment.charAt(comment.length() - 1) == '-') {
            comment = comment + ' ';
        }
        return comment;
    }

    /**
     * Check the content of the comment according to the XQuery rules (which throw an error if it is wrong)
     * @param comment the proposed text of the comment
     * @return the adjusted text of the comment (always the same as the original if there is no error)
     * @throws net.sf.saxon.trans.XPathException if the content is invalid
     */

    public static String checkContentXQuery(String comment) throws XPathException {
        if (comment.contains("--")) {
            throw new XPathException("Invalid characters (--) in comment", "XQDY0072");
        }
        if (comment.length() > 0 && comment.charAt(comment.length() - 1) == '-') {
            throw new XPathException("Comment cannot end in '-'", "XQDY0072");
        }
        return comment;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("comment");
        getContentExpression().explain(out);
        out.endElement();
    }

}
