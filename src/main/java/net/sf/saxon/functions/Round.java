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
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.NumericValue;

/**
 * This class supports the round() functions
 */

public final class Round extends SystemFunctionCall implements Callable {

    /**
     * Evaluate the function
     */

    /*@Nullable*/
    public NumericValue evaluateItem(XPathContext context) throws XPathException {

        NumericValue val0 = (NumericValue) argument[0].evaluateItem(context);
        if (val0 == null) {
            return null;
        }

        int scaleRnd = 0;
        if (argument.length == 2) {
            NumericValue scaleVal = (NumericValue) argument[1].evaluateItem(context);
            scaleRnd = (int) scaleVal.longValue();
        }
        return val0.round(scaleRnd);
    }

    /**
     * Determine the cardinality of the function.
     */

    public int computeCardinality() {
        return argument[0].getCardinality();
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
        NumericValue val0 = (NumericValue) arguments[0].head();
        if (val0 == null) {
            return EmptySequence.getInstance();
        }

        int scaleRnd = 0;
        if (arguments.length == 2) {
            NumericValue scaleVal = (NumericValue) arguments[1].head();
            scaleRnd = (int) scaleVal.longValue();
        }
        return val0.round(scaleRnd);

    }



}

