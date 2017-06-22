////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;

/**
 * This class supports the get_X_from_Y functions defined in XPath 2.0
 */

public class Component extends SystemFunctionCall {

    public static final int YEAR = 1;
    public static final int MONTH = 2;
    public static final int DAY = 3;
    public static final int HOURS = 4;
    public static final int MINUTES = 5;
    public static final int SECONDS = 6;
    public static final int TIMEZONE = 7;
    public static final int LOCALNAME = 8;
    public static final int NAMESPACE = 9;
    public static final int PREFIX = 10;
    public static final int MICROSECONDS = 11;   // internal use only
    public static final int WHOLE_SECONDS = 12;  // internal use only
    public static final int YEAR_ALLOWING_ZERO = 13;  // internal use only

    int component;

    /**
     * Set the details of this type of function
     *
     * @param entry information giving details of the function signature
     */
    @Override
    public void setDetails(StandardFunction.Entry entry) {
        super.setDetails(entry);
        component = (operation >> 16) & 0xffff;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        switch (component) {
            case YEAR:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-100000), Int64Value.makeIntegerValue(+100000)};
            case MONTH:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-11), Int64Value.makeIntegerValue(+11)};
            case DAY:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-31), Int64Value.makeIntegerValue(+31)};
            case HOURS:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-24), Int64Value.makeIntegerValue(+24)};
            case MINUTES:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-59), Int64Value.makeIntegerValue(+59)};
            case SECONDS:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-59), Int64Value.makeIntegerValue(+59)};
            default:
                return null;
        }
    }

    /**
     * Get the required component
     */

    public int getRequiredComponent() {
        return component;
    }

    /**
     * Get the required component name as a string
     */

    public String getRequiredComponentAsString() {
        String[] components = {"", "YEAR", "MONTH", "DAY", "HOURS", "MINUTES", "SECONDS",
                               "TIMEZONE", "LOCALNAME", "NAMESPACE", "PREFIX", "MICROSECONDS",
                               "WHOLE_SECONDS", "YEAR_ALLOWING_ZERO"};
        return components[component];
    }

    /**
     * Evaluate the expression
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue arg = (AtomicValue)argument[0].evaluateItem(context);

        if (arg == null) {
            return null;
        }

        return arg.getComponent(component);

    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Component c = (Component)super.copy();
        c.component = (c.operation >> 16) & 0xffff;
        return c;
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
        return ((AtomicValue)arguments[0].head()).getComponent(component);
    }



}

