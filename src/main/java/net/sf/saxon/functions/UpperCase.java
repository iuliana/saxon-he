////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
* This class implements the fn:upper-case() function
*/

public class UpperCase extends SystemFunctionCall implements Callable {

    /**
    * Evaluate in a general context
    */

    public StringValue evaluateItem(XPathContext c) throws XPathException {
        StringValue sv = (StringValue)argument[0].evaluateItem(c);
        if (sv==null) {
            return StringValue.EMPTY_STRING;
        } else {
            return StringValue.makeStringValue(sv.getStringValue().toUpperCase());
        }
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		StringValue sv = (StringValue)arguments[0].head();
        return (sv == null ? StringValue.EMPTY_STRING : StringValue.makeStringValue(sv.getStringValue().toUpperCase()));
	}




}

