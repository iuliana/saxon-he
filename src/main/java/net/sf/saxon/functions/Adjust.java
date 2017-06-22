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
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.DayTimeDurationValue;
import net.sf.saxon.value.EmptySequence;

/**
* This class implements the XPath 2.0 functions
 * adjust-date-to-timezone(), adjust-time-timezone(), and adjust-dateTime-timezone().
*/


public class Adjust extends SystemFunctionCall implements Callable {

    /**
    * Evaluate in a general context
    */

    /*@Nullable*/ public CalendarValue evaluateItem(XPathContext context) throws XPathException {
        CalendarValue in = (CalendarValue)argument[0].evaluateItem(context);
        if (in==null) {
            return null;
        }
        int nargs = argument.length;
        if (nargs==1) {
            return in.adjustTimezone(context.getImplicitTimezone());
        } else {
            AtomicValue av2 = (AtomicValue)argument[1].evaluateItem(context);
            if (av2==null) {
                return in.removeTimezone();
            }
            return adjustToExplicitTimezone(in, (DayTimeDurationValue)av2, context);
        }
    }

    private CalendarValue adjustToExplicitTimezone(CalendarValue in, DayTimeDurationValue tz, XPathContext context) throws XPathException {
        long microseconds = tz.getLengthInMicroseconds();
        if (microseconds%60000000 != 0) {
            XPathException err = new XPathException("Timezone is not an integral number of minutes", "FODT0003");
            err.setLocator(this);
            err.setXPathContext(context);
            throw err;
        }
        int tzminutes = (int)(microseconds / 60000000);
        if (Math.abs(tzminutes) > 14*60) {
            XPathException err = new XPathException("Timezone out of range (-14:00 to +14:00)", "FODT0003");
            err.setLocator(this);
            err.setXPathContext(context);
            throw err;
        }
        return in.adjustTimezone(tzminutes);
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
        CalendarValue in = (CalendarValue)arguments[0].head();
        if (in == null) {
            return EmptySequence.getInstance();
        }
        if (arguments.length == 1) {
            return in.adjustTimezone(context.getImplicitTimezone());
        } else {
            DayTimeDurationValue tz = (DayTimeDurationValue)arguments[1].head();
            if (tz == null) {
                return in.removeTimezone();
            } else {
                return adjustToExplicitTimezone(in, tz, context);
            }
        }
    }
}

