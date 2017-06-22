////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

/**
 * Implement the XPath function fn:true()
 */

public class True extends SystemFunctionCall implements Callable {

    @Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        return Literal.makeLiteral(BooleanValue.TRUE);
    }

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.TRUE;
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.TRUE;
    }
}

