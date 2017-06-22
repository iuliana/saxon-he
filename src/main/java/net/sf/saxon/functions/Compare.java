////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the XPath 2.0 fn:compare() function
 */

// Supports string comparison using a collation

public class Compare extends CollatingFunction implements Callable {

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 2;
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
        return new IntegerValue[] {Int64Value.MINUS_ONE, Int64Value.PLUS_ONE};
    }

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType type0 = argument[0].getItemType(th);
        ItemType type1 = argument[1].getItemType(th);
        if (type0 instanceof AtomicType && type1 instanceof AtomicType) {
            preAllocateComparer((AtomicType)type0, (AtomicType)type1, visitor.getStaticContext(), false);
        }
    }

    /**
    * Evaluate the expression
    */

    /*@Nullable*/ public Int64Value evaluateItem(XPathContext context) throws XPathException {
        StringValue arg0 = (StringValue)argument[0].evaluateItem(context);
        StringValue arg1 = (StringValue)argument[1].evaluateItem(context);
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer == null) {
            comparer = getAtomicComparer(getCollator(context), context);
        }
        return compare(arg0, arg1, comparer);
    }

    private static Int64Value compare(StringValue s1, StringValue s2, AtomicComparer comparer) throws XPathException {
        if (s1==null || s2==null) {
            return null;
        }
        int result = comparer.compareAtomicValues(s1, s2);
        if (result < 0) {
            return Int64Value.MINUS_ONE;
        } else if (result > 0) {
            return Int64Value.PLUS_ONE;
        } else {
            return Int64Value.ZERO;
        }
    }

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		StringValue arg0 = (StringValue)arguments[0].head();
        StringValue arg1 = (StringValue)arguments[1].head();
        StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
        GenericAtomicComparer comparer = new GenericAtomicComparer(collator, context);
        return compare(arg0, arg1, comparer);
    }

}

