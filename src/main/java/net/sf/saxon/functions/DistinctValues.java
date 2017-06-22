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
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.ComparisonKey;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;

import java.util.HashSet;

/**
* The XPath 2.0 distinct-values() function
*/

public class DistinctValues extends CollatingFunction implements Callable {

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 1;
    }

    @Override
    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType itemType = argument[0].getItemType(th);
        if (itemType instanceof AtomicType) {
            preAllocateComparer((AtomicType)itemType, (AtomicType)itemType, visitor.getStaticContext(), true);
        }
    }

    /**
     * Get the AtomicComparer allocated at compile time.
     * @return the AtomicComparer if one has been allocated at compile time; return null
     * if the collation is not known until run-time
     */

    public AtomicComparer getAtomicComparer() {
        return getPreAllocatedAtomicComparer();
    }



    /**
    * Evaluate the function to return an iteration of selected values or nodes.
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer != null) {
            comparer = comparer.provideContext(context);
        } else {
            int type = argument[0].getItemType(context.getConfiguration().getTypeHierarchy()).getPrimitiveType();
            comparer = AtomicSortComparer.makeSortComparer(getCollator(context), type, context);
        }
        SequenceIterator iter = argument[0].iterate(context);
        return new DistinctIterator(iter, comparer);
    }

    /**
     * Iterator class to return the distinct values in a sequence
     */

    public static class DistinctIterator implements SequenceIterator {

        private SequenceIterator base;
        private AtomicComparer comparer;
        private int position;
        /*@Nullable*/ private AtomicValue current;
        private HashSet<ComparisonKey> lookup = new HashSet<ComparisonKey>(40);

        /**
         * Create an iterator over the distinct values in a sequence
         * @param base the input sequence. This must return atomic values only.
         * @param comparer The comparer used to obtain comparison keys from each value;
         * these comparison keys are themselves compared using equals().
         */

        public DistinctIterator(SequenceIterator base, AtomicComparer comparer) {
            this.base = base;
            this.comparer = comparer;
            position = 0;
        }

        /**
         * Get the next item in the sequence. <BR>
         *
         * @return the next item, or null if there are no more items.
         * @throws net.sf.saxon.trans.XPathException
         *          if an error occurs retrieving the next item
         */

        public Item next() throws XPathException {
            while (true) {
                AtomicValue nextBase = (AtomicValue)base.next();
                if (nextBase==null) {
                    current = null;
                    position = -1;
                    return null;
                }
                ComparisonKey key = comparer.getComparisonKey(nextBase);
                if (lookup.add(key)) {
                    // returns true if newly added (if not, keep looking)
                    current = nextBase;
                    position++;
                    return nextBase;
                }
            }
        }

        /**
         * Get the current value in the sequence (the one returned by the
         * most recent call on next()). This will be null before the first
         * call of next().
         *
         * @return the current item, the one most recently returned by a call on
         *         next(); or null, if next() has not been called, or if the end
         *         of the sequence has been reached.
         */

        public Item current() {
            return current;
        }

        /**
         * Get the current position. This will be zero before the first call
         * on next(), otherwise it will be the number of times that next() has
         * been called.
         *
         * @return the current position, the position of the item returned by the
         *         most recent call of next()
         */

        public int position() {
            return position;
        }

        public void close() {
            base.close();
        }

        /**
         * Get another SequenceIterator that iterates over the same items as the original,
         * but which is repositioned at the start of the sequence.
         *
         * @return a SequenceIterator that iterates over the same items,
         *         positioned before the first item
         * @throws net.sf.saxon.trans.XPathException
         *          if any error occurs
         */

        /*@NotNull*/
        public SequenceIterator getAnother() throws XPathException {
            return new DistinctIterator(base.getAnother(), comparer);
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
         *         and {@link #LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return 0;
        }
    }

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringCollator collator = getCollatorFromLastArgument(arguments, 1, context);
		AtomicComparer comp = AtomicSortComparer.makeSortComparer(collator, StandardNames.XS_ANY_ATOMIC_TYPE, context);
        return SequenceTool.toLazySequence(new DistinctIterator(arguments[0].iterate(), comp));
	}

}

