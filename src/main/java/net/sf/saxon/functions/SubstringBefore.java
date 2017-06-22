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
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.text.RuleBasedCollator;


/**
 * Implements the fn:substring-before() function
 */
public class SubstringBefore extends CollatingFunction implements Callable {

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

    public Item evaluateItem(/*@NotNull*/ XPathContext context) throws XPathException {
        StringValue arg1 = (StringValue)argument[1].evaluateItem(context);
        if (arg1==null || arg1.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        StringValue arg0 = (StringValue)argument[0].evaluateItem(context);
        if (arg0==null || arg0.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }
        StringCollator collator = getCollator(context);
        return substringBefore(context, arg0, arg1, collator);
    }

    private StringValue substringBefore(XPathContext context, StringValue arg0, StringValue arg1, StringCollator collator) throws XPathException {
        String s0 = arg0.getStringValue();
        String s1 = arg1.getStringValue();
        StringValue result;
        if (collator instanceof CodepointCollator) {
            // fast path for this common case
            int j = s0.indexOf(s1);
            if (j<0) {
                result = StringValue.EMPTY_STRING;
            } else {
                result = new StringValue(s0.substring(0, j));
            }

        } else {
            if (collator instanceof SimpleCollation &&
                    ((SimpleCollation)collator).getCollation() instanceof RuleBasedCollator) {
                collator = new RuleBasedSubstringMatcher((RuleBasedCollator)((SimpleCollation)collator).getCollation());
            }

            if (collator instanceof SubstringMatcher) {
                result = new StringValue(((SubstringMatcher)collator).substringBefore(s0, s1));
            } else {
                dynamicError("The collation requested for " + getDisplayName() +
                        " does not support substring matching", "FOCH0004", context);
                return null;
            }
        }

        if (arg0.isKnownToContainNoSurrogates()) {
            result.setContainsNoSurrogates();
        }
        return result;
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
        StringValue arg1 = (StringValue)arguments[1].head();
        if (arg1==null || arg1.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        StringValue arg0 = (StringValue)arguments[0].head();
        if (arg0==null || arg0.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
        return substringBefore(context, arg0, arg1, collator);
    }



}

