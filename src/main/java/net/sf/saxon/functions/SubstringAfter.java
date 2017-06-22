////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.RuleBasedSubstringMatcher;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.text.RuleBasedCollator;

/**
 * Implements the fn:substring-after() function
 */
public class SubstringAfter extends CollatingFunction implements Callable {

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 2;
    }

    /**
     * Evaluate the function
     */

    /*@Nullable*/ public StringValue evaluateItem(XPathContext context) throws XPathException {
        StringValue arg1 = (StringValue)argument[0].evaluateItem(context);
        StringValue arg2 = (StringValue)argument[1].evaluateItem(context);
        StringCollator collator = getCollator(context);
        return substringAfter(context, arg1, arg2, collator);
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
        StringValue arg1 = (StringValue)arguments[0].head();
        StringValue arg2 = (StringValue)arguments[1].head();
        StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
        return substringAfter(context, arg1, arg2, collator);
    }

    private StringValue substringAfter(XPathContext context, StringValue arg1, StringValue arg2, StringCollator collator) throws XPathException {
        if (arg1 == null) {
            arg1 = StringValue.EMPTY_STRING;
        }
        if (arg2 == null) {
            arg2 = StringValue.EMPTY_STRING;
        }
        if (arg2.isZeroLength()) {
            return arg1;
        }
        if (arg1.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        String s1 = arg1.getStringValue();
        String s2 = arg2.getStringValue();

        String result = null;
        if (collator instanceof CodepointCollator) {
            // fast path for this common case
            int i = s1.indexOf(s2);
            if (i < 0) {
                result = "";
            } else {
                result = s1.substring(i + s2.length());
            }

        } else {
            if (collator instanceof SimpleCollation &&
                    ((SimpleCollation)collator).getCollation() instanceof RuleBasedCollator) {
                collator = new RuleBasedSubstringMatcher((RuleBasedCollator)((SimpleCollation)collator).getCollation());
            }

            if (collator instanceof SubstringMatcher) {
                result = ((SubstringMatcher)collator).substringAfter(s1, s2);
            } else {
                dynamicError("The collation requested for " + getDisplayName() +
                        " does not support substring matching", "FOCH0004", context);
            }
        }
        StringValue s = StringValue.makeStringValue(result);
        if (arg1.isKnownToContainNoSurrogates()) {
            s.setContainsNoSurrogates();
        }
        return s;
    }



}