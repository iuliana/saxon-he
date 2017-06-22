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
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;


/**
* The XPath 2.0 index-of() function
*/


public class IndexOf extends CollatingFunction implements Callable {

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
        return new IntegerValue[]{Int64Value.PLUS_ONE, MAX_SEQUENCE_LENGTH};
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
    * Evaluate the function to return an iteration of selected nodes.
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer == null) {
            comparer = getAtomicComparer(getCollator(context), context);
        }
        SequenceIterator seq = argument[0].iterate(context);
        AtomicValue val = (AtomicValue)argument[1].evaluateItem(context);
        return new IndexIterator(seq, val, comparer);
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
        StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
        GenericAtomicComparer comparer = new GenericAtomicComparer(collator, context);
        SequenceIterator seq = arguments[0].iterate();
        AtomicValue val = (AtomicValue)arguments[1].head();
        return SequenceTool.toLazySequence(new IndexIterator(seq, val, comparer));
    }

    /**
     * Iterator to return the index positions of selected items in a sequence
     */

    public static class IndexIterator implements SequenceIterator {

        private SequenceIterator base;
        private AtomicValue value;
        private AtomicComparer comparer;
        private int index = 0;
        private int position = 0;
        /*@Nullable*/ private Item current = null;
        private BuiltInAtomicType primitiveTypeRequired;

        /**
         * Get an iterator returning the index positions of selected items in a sequence
         * @param base The sequence to be searched
         * @param value The value being sought
         * @param comparer Comparer used to determine whether values match
         */

        public IndexIterator(SequenceIterator base, AtomicValue value, AtomicComparer comparer) {
            this.base = base;
            this.value = value;
            this.comparer = comparer;
            primitiveTypeRequired = value.getPrimitiveType();
        }

        public Item next() throws XPathException {
            while (true) {
                AtomicValue i = (AtomicValue)base.next();
                if (i==null) break;
                index++;
                if (Type.isGuaranteedComparable(primitiveTypeRequired,
                        i.getPrimitiveType(), false)) {
                    try {
                        if (comparer.comparesEqual(i, value)) {
                            current = Int64Value.makeIntegerValue(index);
                            position++;
                            return current;
                        }
                    } catch (ClassCastException err) {
                        // non-comparable values are treated as not equal
                        // Exception shouldn't happen but we catch it anyway
                    }
                }
            }
            current = null;
            position = -1;
            return null;
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

        /*@NotNull*/
        public SequenceIterator getAnother() throws XPathException {
            return new IndexIterator(base.getAnother(), value, comparer);
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
    }



}

