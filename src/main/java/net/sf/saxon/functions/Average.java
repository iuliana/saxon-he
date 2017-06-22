////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.*;

import static net.sf.saxon.functions.Sum.SUM_EX_CODE;

/**
 * Implementation of the fn:avg function
 */
public class Average extends Aggregate implements Callable {

    public int getImplementationMethod() {
        return super.getImplementationMethod() | ITEM_FEED_METHOD;
    }
    

    /**
     * Determine the item type of the value returned by the function
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        ItemType base = Atomizer.getAtomizedItemType(argument[0], false, th);
        if (base.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            return BuiltInAtomicType.DOUBLE;
        } else if (base.getPrimitiveType() == StandardNames.XS_INTEGER) {
            return BuiltInAtomicType.DECIMAL;
        } else {
            return base;
        }
    }

    /**
     * Determine the cardinality of the function.
     */

    public int computeCardinality() {
        if (!Cardinality.allowsZero(argument[0].getCardinality())) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return super.computeCardinality();
        }
    }



    /**
    * Evaluate the function
    */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        try {
            return average(argument[0].iterate(context), context);
        } catch (XPathException err) {
            err.maybeSetContext(context);
            err.maybeSetLocation(this);
            throw err;
        }
    }


    /**
     * Calculate average
     * @param iter iterator over the items to be totalled
     * @param context the XPath dynamic context
     * @return the average of the values
     * @throws XPathException in the event of a dynamic error, for example if the input sequence contains
     * a mix of numbers and durations
    */

    /*@Nullable*/ public static AtomicValue average(SequenceIterator iter, XPathContext context)
            throws XPathException {
        ConversionRules rules = context.getConfiguration().getConversionRules();
        StringConverter toDouble = rules.getStringConverter(BuiltInAtomicType.DOUBLE);
        int count = 0;
        AtomicValue item = (AtomicValue)iter.next();
        if (item == null) {
            // the sequence is empty
            return null;
        }
        count++;
        if (item instanceof UntypedAtomicValue) {
            item = toDouble.convert(item).asAtomic();
        }
        if (item instanceof NumericValue) {
            while (true) {
                AtomicValue next = (AtomicValue)iter.next();
                if (next == null) {
                    //return ((NumericValue)item).arithmetic(Token.DIV, new Int64Value(count), context);
                    return ArithmeticExpression.compute(item, Calculator.DIV, new Int64Value(count), context);
                }
                count++;
                if (next instanceof UntypedAtomicValue) {
                    next = toDouble.convert(next).asAtomic();
                } else if (!(next instanceof NumericValue)) {
                    throw new XPathException(
                            "Input to avg() contains a mix of numeric and non-numeric values", SUM_EX_CODE);
                }
                //item = ((NumericValue)item).arithmetic(Token.PLUS, (NumericValue)next, context);
                item = ArithmeticExpression.compute(item, Calculator.PLUS, next, context);
                if (item.isNaN() && item instanceof DoubleValue) {
                    // take an early bath, once we've got a double NaN it's not going to change
                    return item;
                }
            }
        } else if (item instanceof DurationValue) {
            while (true) {
                AtomicValue next = (AtomicValue)iter.next();
                if (next == null) {
                    return ((DurationValue)item).multiply(1.0/count);
                }
                count++;
                if (!(next instanceof DurationValue)) {
                    throw new XPathException(
                            "Input to avg() contains a mix of duration and non-duration values", SUM_EX_CODE);
                }
                item = ((DurationValue)item).add((DurationValue)next);
            }
        } else {
            throw new XPathException(
                    "Input to avg() contains a value that is neither numeric, nor a duration", SUM_EX_CODE);
        }
    }


	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        try {
		    return average(arguments[0].iterate(), context);
        } catch (XPathException err) {
            err.maybeSetContext(context);
            err.maybeSetLocation(this);
            throw err;
        }
	}

}

