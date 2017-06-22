////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.ErrorIterator;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.AtomicValue;

/**
* Class to do a sorted iteration
*/

public class SortedIterator implements SequenceIterator, LastPositionFinder, LookaheadIterator, Sortable {

    // the items to be sorted
    protected SequenceIterator base;

    // the call-back function used to evaluate sort keys
    protected SortKeyEvaluator sortKeyEvaluator;

    // the comparators corresponding to these sort keys
    protected AtomicComparer[] comparators;

    // The items and keys are read into an array (nodeKeys) for sorting. This
    // array contains one "record" representing each node: the "record" contains
    // first, the Item itself, then an entry for each of its sort keys, in turn;
    // the last sort key is the position of the Item in the original sequence.
    protected ObjectToBeSorted[] values;

    // The number of items to be sorted. -1 means not yet known.
    protected int count = -1;

    // The next item to be delivered from the sorted iteration
    protected int position = 0;

    // The context for the evaluation of sort keys
    protected XPathContext context;

    // The host language (XSLT, XQuery, XPath). Used only to decide which error code to use on dynamic errors.
    private int hostLanguage;

    private SortedIterator(){}

    /**
     * Create a sorted iterator
     * @param context the dynamic XPath evaluation context
     * @param base an iterator over the sequence to be sorted
     * @param sortKeyEvaluator an object that allows the n'th sort key for a given item to be evaluated
     * @param comparators an array of AtomicComparers, one for each sort key, for comparing sort key values
     * @param createNewContext
     */

    public SortedIterator(XPathContext context, SequenceIterator base,
                          SortKeyEvaluator sortKeyEvaluator, AtomicComparer[] comparators, boolean createNewContext) {
        if (createNewContext) {
            this.context = context.newMinorContext();
            this.context.setCurrentIterator(base);
        } else {
            this.context = context;
        }
        this.base = base;
        this.sortKeyEvaluator = sortKeyEvaluator;
        this.comparators = new AtomicComparer[comparators.length];
        for (int n=0; n<comparators.length; n++) {
            this.comparators[n] = comparators[n].provideContext(context);
        }

        // Avoid doing the sort until the user wants the first item. This is because
        // sometimes the user only wants to know whether the collection is empty.
    }

    /**
     * Set the host language
     * @param language the host language (for example {@link Configuration#XQUERY})
     */

    public void setHostLanguage(int language) {
        hostLanguage = language;
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     * <p/>
     * This method must not be called unless the result of getProperties() on the iterator
     * includes the bit setting {@link net.sf.saxon.om.SequenceIterator#LOOKAHEAD}
     *
     * @return true if there are more items in the sequence
     */

    public boolean hasNext() {
        if (position < 0) {
            return false;
        }
        if (count < 0) {
            // haven't started sorting yet
            if (base instanceof LookaheadIterator) {
                return ((LookaheadIterator)base).hasNext();
            } else {
                try {
                    doSort();
                    return count > 0;
                } catch (XPathException err) {
                    // can't return the exception now; but we can rely on the fact that
                    // (a) it wouldn't have failed unless there was something to sort, and
                    // (b) it's going to fail again when next() is called
                    count = -1;
                    base = new ErrorIterator(err);
                    return true;
                }
            }
        } else {
            return (position < count);
        }
    }

    /**
    * Get the next item, in sorted order
    */

    /*@Nullable*/ public Item next() throws XPathException {
        if (position < 0) {
            return null;
        }
        if (count<0) {
            doSort();
        }
        if (position < count) {
            return (Item)values[(position++)].value;
        } else {
            position = -1;
            return null;
        }
    }

    public Item current() {
        if (position < 1) {
            return null;
        }
        return (Item)values[position-1].value;
    }

    public int position() {
        return position;
    }

    public int getLength() throws XPathException {
        if (count<0) {
            doSort();
        }
        return count;
    }

    public void close() {
    }

    /*@NotNull*/
    public SequenceIterator getAnother() throws XPathException {
        // make sure the sort has been done, so that multiple iterators over the
        // same sorted data only do the sorting once.
        if (count<0) {
            doSort();
        }
        SortedIterator s = new SortedIterator();
        // the new iterator is the same as the old ...
        s.base = base.getAnother();
        s.sortKeyEvaluator = sortKeyEvaluator;
        s.comparators = comparators;
        s.values = values;
        s.count = count;
        s.context = context;
        s.position = 0;
        return s;
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
        return LAST_POSITION_FINDER;
    }

    /**
     * Create an array holding the items to be sorted and the values of their sort keys
     * @throws XPathException
     */

    protected void buildArray() throws XPathException {
        int allocated;
        if ((base.getProperties() & SequenceIterator.LAST_POSITION_FINDER) != 0) {
            allocated = ((LastPositionFinder)base).getLength();
        } else {
            allocated = 100;
        }

        values = new ItemToBeSorted[allocated];
        count = 0;

        // initialise the array with data

        while (true) {
            Item item = base.next();
            if (item == null) {
                break;
            }
            if (count==allocated) {
                allocated *= 2;
                ItemToBeSorted[] nk2 = new ItemToBeSorted[allocated];
                System.arraycopy(values, 0, nk2, 0, count);
                values = nk2;
            }
            ItemToBeSorted itbs = new ItemToBeSorted(comparators.length);
            values[count] = itbs;
            itbs.value = item;
            // TODO: delay evaluating the sort keys until we know they are needed. Often the 2nd and subsequent
            // sort key values will never be used. The only problem is with sort keys that depend on position().
            for (int n=0; n<comparators.length; n++) {
                itbs.sortKeyValues[n] = sortKeyEvaluator.evaluateSortKey(n, context);
            }
            // make the sort stable by adding the record number
            itbs.originalPosition = count++;
        }

        // If there's lots of unused space, reclaim it

        if (allocated * 2 < count || (allocated - count) > 2000) {
            ObjectToBeSorted[] nk2 = new ObjectToBeSorted[count];
            System.arraycopy(values, 0, nk2, 0, count);
            values = nk2;
        }
    }

    private void doSort() throws XPathException {
        buildArray();
        if (count<2) return;

        // sort the array

        //QuickSort.sort(this, 0, count-1);
        try {
            GenericSorter.quickSort(0, count, this);
        } catch (ClassCastException e) {
            //e.printStackTrace();
            XPathException err = new XPathException("Non-comparable types found while sorting: " + e.getMessage());
            if (hostLanguage == Configuration.XSLT) {
                err.setErrorCode("XTDE1030");
            } else {
                err.setErrorCode("XPTY0004");
            }
            throw err;
        }
        //GenericSorter.mergeSort(0, count, this);
    }

    /**
    * Compare two items in sorted sequence
    * (needed to implement the Sortable interface)
    * @return <0 if obj[a]<obj[b], 0 if obj[a]=obj[b], >0 if obj[a]>obj[b]
    */

    public int compare(int a, int b) {
        try {
            for (int i=0; i<comparators.length; i++) {
                int comp = comparators[i].compareAtomicValues(
                        (AtomicValue)values[a].sortKeyValues[i], (AtomicValue)values[b].sortKeyValues[i]);
                if (comp != 0) {
                    // we have found a difference, so we can return
                    return comp;
                }
            }
        } catch (NoDynamicContextException e) {
            throw new AssertionError("Sorting without dynamic context: " + e.getMessage());
        }

        // all sort keys equal: return the items in their original order

        return values[a].originalPosition - values[b].originalPosition;
    }

    /**
    * Swap two items (needed to implement the Sortable interface)
    */

    public void swap(int a, int b) {
        ObjectToBeSorted temp = values[a];
        values[a] = values[b];
        values[b] = temp;
    }

}

