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
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.text.RuleBasedCollator;

/**
 * Implements the fn:ends-with() function
 */
public class EndsWith extends CollatingFunction implements Callable {

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 2;
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        StringValue arg0 = (StringValue)argument[0].evaluateItem(context);
        StringValue arg1 = (StringValue)argument[1].evaluateItem(context);
        try {
            return endsWith(arg0, arg1, getCollator(context));
        } catch (XPathException err) {
            err.maybeSetContext(context);
            err.maybeSetLocation(this);
            throw err;
        }
    }


    public static boolean endsWith(StringValue arg0, StringValue arg1, StringCollator collator) throws XPathException {
        if (arg1==null || arg1.isZeroLength()) {
            return true;
        }
        if (arg0==null || arg0.isZeroLength()) {
            return false;
        }

        String s0 = arg0.getStringValue();
        String s1 = arg1.getStringValue();

        if (collator instanceof CodepointCollator) {
            // fast path for this common case
            return s0.endsWith(s1);
        } else {
            if (collator instanceof SimpleCollation &&
                    ((SimpleCollation)collator).getCollation() instanceof RuleBasedCollator) {
                collator = new RuleBasedSubstringMatcher((RuleBasedCollator)((SimpleCollation)collator).getCollation());
            }

            if (collator instanceof SubstringMatcher) {
                return ((SubstringMatcher)collator).endsWith(s0, s1);
            } else {
                throw new XPathException("The collation requested for ends-with() does not support substring matching", "FOCH0004");
            }
        }
    }

    /**
     * Evaluate the function
     */

    public BooleanValue evaluateItem(/*@NotNull*/ XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
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
        try {
            StringValue s0 = (StringValue)arguments[0].head();
            StringValue s1 = (StringValue)arguments[1].head();
            StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
            return BooleanValue.get(endsWith(s0, s1, collator));
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }
    }



}

