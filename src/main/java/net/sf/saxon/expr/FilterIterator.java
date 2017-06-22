////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;

/**
* A CompiledFilterIterator filters an input sequence using a filter expression. Note that a CompiledFilterIterator
* is not used where the filter is a constant number (PositionFilter is used for this purpose instead),
* so this class does no optimizations for numeric predicates.
*/

public class FilterIterator implements SequenceIterator {

    protected SequenceIterator base;
    protected Expression filter;
    private int position = 0;
    /*@Nullable*/ private Item current = null;
    protected XPathContext filterContext;

    /**
    * Constructor
    * @param base An iteration of the items to be filtered
    * @param filter The expression defining the filter predicate
    * @param context The context in which the expression is being evaluated
    */

    public FilterIterator(SequenceIterator base, Expression filter,
                            XPathContext context) {
        this.base = base;
        this.filter = filter;
        filterContext = context.newMinorContext();
        filterContext.setCurrentIterator(base);
    }

    /**
     * Set the base iterator
     * @param base the iterator over the sequence to be filtered
     * @param context the context in which the (outer) filter expression is evaluated
     */

    public void setSequence(SequenceIterator base, XPathContext context) {
        this.base = base;
        filterContext = context.newMinorContext();
        filterContext.setCurrentIterator(base);
    }

    /**
    * Get the next item if there is one
    */

    public Item next() throws XPathException {
        current = getNextMatchingItem();
        if (current == null) {
            position = -1;
        } else {
            position++;
        }
        return current;
    }

    /**
     * Get the next item in the base sequence that matches the filter predicate
     * if there is such an item, or null if not.
     * @return the next item that matches the predicate
    */

    protected Item getNextMatchingItem() throws XPathException {
        while (true) {
            Item next = base.next();
            if (next == null) {
                return null;
            }
            if (matches()) {
                return next;
            }
        }
    }

    /**
    * Determine whether the context item matches the filter predicate
     * @return true if the context item matches
    */

    protected boolean matches() throws XPathException {

        // This code is carefully designed to avoid reading more items from the
        // iteration of the filter expression than are absolutely essential.

        // The code is almost identical to the code in ExpressionTool#effectiveBooleanValue
        // except for the handling of a numeric result

        SequenceIterator iterator = filter.iterate(filterContext);
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof NodeInfo) {
            return true;
        } else {
            if (first instanceof BooleanValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("sequence of two or more items starting with a boolean");
                }
                return ((BooleanValue)first).getBooleanValue();
            } else if (first instanceof StringValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("sequence of two or more items starting with a string");
                }
                return (first.getStringValueCS().length()!=0);
            } else if (first instanceof Int64Value) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("sequence of two or more items starting with a numeric value");
                }
                return ((Int64Value)first).longValue() == base.position();

            } else if (first instanceof NumericValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("sequence of two or more items starting with a numeric value");
                }
                return ((NumericValue)first).compareTo(base.position()) == 0;
            } else {
                ExpressionTool.ebvError("sequence starting with an atomic value other than a boolean, number, or string");
                return false;
            }
        }
    }

    public Item current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        base.close();
    }

    /**
    * Get another iterator to return the same nodes
    */

    /*@NotNull*/
    public SequenceIterator getAnother() throws XPathException {
        return new FilterIterator(base.getAnother(), filter,
                                    filterContext);
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link SequenceIterator#GROUNDED}, {@link SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

    /**
    * Subclass to handle the common special case where it is statically known
    * that the filter cannot return a numeric value
    */

    public static final class NonNumeric extends FilterIterator {

        /**
         * Create a CompiledFilterIterator for the situation where it is known that the filter
         * expression will never evaluate to a number value. For this case we can simply
         * use the effective boolean value of the predicate
         * @param base iterator over the sequence to be filtered
         * @param filter the filter expression
         * @param context the current context (for evaluating the filter expression as a whole).
         * A new context will be created to evaluate the predicate.
         */

        public NonNumeric(SequenceIterator base, Expression filter,
                            XPathContext context) {
            super(base, filter, context);
        }

        /**
        * Determine whether the context item matches the filter predicate
        */

        protected boolean matches() throws XPathException {
            return filter.effectiveBooleanValue(filterContext);
        }

        /**
        * Get another iterator to return the same nodes
        */

        /*@NotNull*/
        public SequenceIterator getAnother() throws XPathException {
            return new FilterIterator.NonNumeric(base.getAnother(), filter, filterContext);
        }
    }

}

