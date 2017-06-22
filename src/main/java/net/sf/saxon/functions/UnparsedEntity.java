////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
* Implements the unparsed-entity-uri() function defined in XSLT 1.0
* and the unparsed-entity-public-id() function defined in XSLT 2.0
*/


public class UnparsedEntity extends SystemFunctionCall implements Callable {

    public static final int URI = 0;
    public static final int PUBLIC_ID = 1;

    /**
    * Simplify: add a second implicit argument, the context document
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        UnparsedEntity f = (UnparsedEntity)super.simplify(visitor);
        f.addContextDocumentArgument(1, (operation==URI ? "unparsed-entity-uri_9999_": "unparsed-entity-public-id_9999_"));
        return f;
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        try {
            return super.typeCheck(visitor, contextItemType);
        } catch (XPathException err) {
            if ("XPDY0002".equals(err.getErrorCodeLocalPart())) {
                if (operation == URI) {
                    XPathException e = new XPathException("Cannot call the unparsed-entity-uri()" +
                            " function when there is no context node");
                    e.setErrorCode("XTDE1370");
                    throw e;
                } else {
                    XPathException e = new XPathException("Cannot call the unparsed-entity-public-id()" +
                            " function when there is no context node");
                    e.setErrorCode("XTDE1380");
                    e.setLocator(this);
                    throw e;
                }
            }
            throw err;
        }
    }

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
    * Evaluate the expression
    */

    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg0 = argument[0].evaluateItem(context).getStringValue();
        NodeInfo doc = null;
        try {
            doc = (NodeInfo)argument[1].evaluateItem(context);
        } catch (XPathException err) {
            String code = err.getErrorCodeLocalPart();
            if ("XPDY0002".equals(code)) {
                if (operation == URI) {
                    XPathException e = new XPathException("Cannot call the unparsed-entity-uri()" +
                            " function when there is no context node");
                    e.setErrorCode("XTDE1370");
                    throw e;
                } else {
                    XPathException e = new XPathException("Cannot call the unparsed-entity-public-id()" +
                            " function when there is no context node");
                    e.setErrorCode("XTDE1380");
                    e.setLocator(this);
                    throw e;
                }
            } else if ("XPDY0050".equals(code)) {
                if (operation == URI) {
                    XPathException e = new XPathException("Can only call the unparsed-entity-uri()" +
                            " function when the context node is in a tree rooted at a document node");
                    e.setErrorCode("XTDE1370");
                    e.setLocator(this);
                    throw e;
                } else {
                    XPathException e = new XPathException("Can only call the unparsed-entity-public-id()" +
                            " function when the context node is in a tree rooted at a document node");
                    e.setErrorCode("XTDE1380");
                    e.setLocator(this);
                    throw e;
                }
            }
        }
        if (doc != null && doc.getNodeKind() != Type.DOCUMENT) {
            String code = (operation==URI ? "XTDE1370" : "XTDE1380");
            dynamicError("In function " + getDisplayName() +
                            ", the context node must be in a tree whose root is a document node", code, context);
        }
        String[] ids = doc != null ? ((DocumentInfo)doc).getUnparsedEntity(arg0) : null;
        if (ids==null) return StringValue.EMPTY_STRING;
        return new StringValue(ids[operation]);
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        String arg0 = arguments[0].head().getStringValue();
        NodeInfo doc = (NodeInfo)arguments[1].head();
        if (doc.getNodeKind() != Type.DOCUMENT) {
            String code = (operation==URI ? "XTDE1370" : "XTDE1380");
            dynamicError("In function " + getDisplayName() +
                            ", the context node must be in a tree whose root is a document node", code, context);
        }
        String[] ids = ((DocumentInfo)doc).getUnparsedEntity(arg0);
        return (ids==null ? StringValue.EMPTY_STRING : new StringValue(ids[operation]));
    }
}

