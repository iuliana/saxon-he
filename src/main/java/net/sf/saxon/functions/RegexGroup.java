////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;


public class RegexGroup extends SystemFunctionCall {

    /**
    * Evaluate in a general context
    */

    public StringValue evaluateItem(XPathContext c) throws XPathException {
        AtomicValue gp0 = (AtomicValue)argument[0].evaluateItem(c);
        int gp = (int)((NumericValue)gp0).longValue();
        return StringValue.makeStringValue(getGroup(gp, c));
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
        NumericValue gp0 = (NumericValue)arguments[0].head();
        int gp = (int)(gp0.longValue());
        return StringValue.makeStringValue(getGroup(gp, context));
    }

    /*@Nullable*/ public static String getGroup(int gp, XPathContext c) {
        RegexIterator iter = c.getCurrentRegexIterator();
        if (iter == null) {
            return "";
        }
        String s = iter.getRegexGroup(gp);
        if (s == null) {
            return "";
        }
        return s;
    }

    /**
    * Determine the dependencies
    */

    public int getIntrinsicDependencies() {
       return StaticProperty.DEPENDS_ON_REGEX_GROUP;
    }



}

