////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
* This class supports the XPath 2.0 functions exactly-one(), one-or-more(), zero-or-one().
* Because Saxon doesn't do strict static type checking, these are essentially identity
* functions; the run-time type checking is done as part of the function call mechanism
*/

public class TreatFn extends SystemFunctionCall implements Callable {

    /**
     * Return the error code to be used for type errors
     */

    public String getErrorCodeForTypeErrors() {
        switch (operation) {
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return "FORG0003";
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return "FORG0004";
            case StaticProperty.EXACTLY_ONE:
                return "FORG0005";
            default:
                return "XPTY0004";
        }
    }

    /**
    * Evaluate the function
    */

    /*@Nullable*/ public Item evaluateItem(XPathContext context) throws XPathException {
        return argument[0].evaluateItem(context);
    }

    /**
    * Iterate over the results of the function
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
       return argument[0].iterate(context);
    }

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		return arguments[0];
	}

}

