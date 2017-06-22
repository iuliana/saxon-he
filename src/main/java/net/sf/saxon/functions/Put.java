////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.PendingUpdateList;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implements the fn:put() function in XQuery Update 1.0.
 */
public class Put extends SystemFunctionCall {

    /*@Nullable*/ String expressionBaseURI = null;

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        if (expressionBaseURI == null) {
            super.checkArguments(visitor);
            expressionBaseURI = visitor.getStaticContext().getBaseURI();
            if (expressionBaseURI == null && argument.length == 1) {
                XPathException de = new XPathException("Base URI in static context of put() is unknown");
                de.setErrorCode("FONS0005");
                throw de;
            }
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Put d = (Put)super.copy();
        d.expressionBaseURI = expressionBaseURI;
        return d;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof Put) &&
                super.equals(o) &&
                equalOrNull(expressionBaseURI, (((Put)o).expressionBaseURI));
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     * @return true if this is an updating expression
     */

    public boolean isUpdatingExpression() {
       return true;
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        NodeInfo node = (NodeInfo)argument[0].evaluateItem(context);
        int kind = node.getNodeKind();
        if (kind != Type.ELEMENT && kind != Type.DOCUMENT) {
            dynamicError("Node in put() must be a document or element node",
                    "FOUP0001", context);
        }
        String relative = argument[1].evaluateItem(context).getStringValue();
        String abs;
        try {
            URI resolved = ResolveURI.makeAbsolute(relative,  expressionBaseURI);
            abs = resolved.toString();
        } catch (URISyntaxException err) {
            dynamicError("Base URI " + Err.wrap(expressionBaseURI) + " is invalid: " + err.getMessage(),
                    "FOUP0002", context);
            abs = null;
        }
        pul.addPutAction(node, abs, this);
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        throw new XPathException("Dynamic evaluation of fn:put() is not supported");
    }


}

