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
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.EmptySequence;

/**
 * This class implements the fn:base-uri() function in XPath 2.0
 */

public class BaseURI extends SystemFunctionCall implements Callable {

    /**
     * Simplify and validate.
     * This is a pure function so it can be simplified in advance if the arguments are known
     *
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        useContextItemAsDefault(visitor);
        return simplifyArguments(visitor);
    }

    /**
     * Evaluate the function at run-time
     */

    /*@Nullable*/
    public AnyURIValue evaluateItem(XPathContext c) throws XPathException {
        NodeInfo node = (NodeInfo) argument[0].evaluateItem(c);
        if (node == null) {
            return null;
        }
        String s = node.getBaseURI();
        if (s == null) {
            return null;
        }
        return new AnyURIValue(s);
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo node  = getDefaultArgumentNode(context, arguments, "fn:base-uri()");

        if (node == null) {
            return EmptySequence.getInstance();
        }

        String s = node.getBaseURI();
        if (s == null) {
            return EmptySequence.getInstance();
        }
        return new AnyURIValue(s);

    }

   

}

