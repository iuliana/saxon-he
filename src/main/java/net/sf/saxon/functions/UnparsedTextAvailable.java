////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;


public class UnparsedTextAvailable extends UnparsedText implements Callable {

    // TODO: There is now a requirement that the results should be stable

    /**
     * This method handles evaluation of the function:
     * it returns a StringValue in the case of unparsed-text(), or a BooleanValue
     * in the case of unparsed-text-available(). In the case of unparsed-text-lines()
     * this shouldn't be called, but we deal with it anyway.
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        StringValue hrefVal = (StringValue)argument[0].evaluateItem(context);
        String encoding = (getNumberOfArguments() == 2 ? argument[1].evaluateItem(context).getStringValue() : null);
        return BooleanValue.get(evalUnparsedTextAvailable(hrefVal, encoding, context));
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
        StringValue hrefVal = (StringValue)arguments[0].head();
        String encoding = (getNumberOfArguments() == 2 ? arguments[1].head().getStringValue() : null);
        return BooleanValue.get(
                        evalUnparsedTextAvailable(hrefVal, encoding, context));
    }

    public boolean evalUnparsedTextAvailable(/*@Nullable*/ StringValue hrefVal, String encoding, XPathContext context) throws XPathException {
        try {
            if (hrefVal == null) {
                return false;
            }
            String href = hrefVal.getStringValue();

            readFile(href, expressionBaseURI, encoding, context);
        } catch (XPathException err) {
            return false;
        }
        return true;
    }


}

