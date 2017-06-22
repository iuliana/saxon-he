////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
* Abtract class representing a function call that is always rewritten at compile time:
* it can never be executed
*/

public abstract class CompileTimeFunction extends SystemFunctionCall {

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing.
     * (this is because the default implementation of preEvaluate() calls evaluate() which
     * is not available for these functions)
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    /**
    * Evaluate as a single item
    */

    public final Item evaluateItem(XPathContext c) throws XPathException {
        throw new IllegalStateException("Function " + getName(c) + " should have been resolved at compile time");
    }

    /**
    * Iterate over the results of the function
    */

    /*@NotNull*/
    public final SequenceIterator iterate(XPathContext c) {
        throw new IllegalStateException("Function " + getName(c) + " should have been resolved at compile time");
    }

    /*@NotNull*/ private String getName(XPathContext c) {
        return getDisplayName();
    }

}

