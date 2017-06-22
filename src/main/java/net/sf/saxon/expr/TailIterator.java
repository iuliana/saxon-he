////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.EmptySequence;

/**
 * TailIterator iterates over a base sequence starting at an element other than the first.
 * The base sequence is represented by an iterator which is consumed in the process
 */

public class TailIterator<T extends Item>
        implements SequenceIterator<T>, LastPositionFinder<T>, LookaheadIterator<T> {

    private SequenceIterator<T> base;
    private int start;

    /**
     * Private constructor: external callers should use the public factory method.
     * Create a TailIterator, an iterator that starts at position N in a sequence and iterates
     * to the end of the sequence
     * @param base the base sequence of which we want to select the tail. Unusually, this iterator
     * should be supplied pre-positioned so that the next call on next() returns the first item to
     * be returned by the TailIterator
     * @param start the index of the first required item in the sequence, starting from one. To
     * include all items in the sequence except the first, set start = 2. This value is used only
     * when cloning the iterator or when calculating the value of last().
     */

    private TailIterator(SequenceIterator<T> base, int start) {
        this.base = base;
        this.start = start;
    }

    /**
     * Static factory method. Creates a TailIterator, unless the base Iterator is an
     * ArrayIterator, in which case it optimizes by creating a new ArrayIterator directly over the
     * underlying array. This optimization is important when doing recursion over a node-set using
     * repeated calls of $nodes[position()>1]
     * @param base   An iteration of the items to be filtered
     * @param start  The position of the first item to be included (base 1). If <= 1, the whole of the
     * base sequence is returned
     * @return an iterator over the items in the sequence from the start item to the end of the sequence.
     * The returned iterator will not necessarily be an instance of this class.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */

    public static <T extends Item> SequenceIterator<T> make(SequenceIterator<T> base, int start) throws XPathException {
        if (start <= 1) {
            return base;
        } else if (base instanceof ArrayIterator) {
            return ((ArrayIterator<T>)base).makeSliceIterator(start, Integer.MAX_VALUE);
        } else if ((base.getProperties() & SequenceIterator.GROUNDED) != 0) {
            GroundedValue value = ((GroundedIterator<T>)base).materialize();
            if (value == EmptySequence.getInstance()) {
                return EmptyIterator.emptyIterator();
            } else {
                return new ValueTailIterator<T>(value, start-1);
            }
        } else {
            // discard the first n-1 items from the underlying iterator
            for (int i=0; i < start-1; i++) {
                Item b = base.next();
                if (b == null) {
                    return EmptyIterator.emptyIterator();
                }
            }
            return new TailIterator<T>(base, start);
        }
    }


    public T next() throws XPathException {
        return base.next();
    }

    public T current() {
        return base.current();
    }

    public int position() {
        int bp = base.position();
        return (bp > 0 ? (base.position() - start + 1) : bp);
    }

    public boolean hasNext() {
        return ((LookaheadIterator)base).hasNext();
    }

    public int getLength() throws XPathException {
        int bl = ((LastPositionFinder)base).getLength() - start + 1;
        return (bl > 0 ? bl : 0);
    }

    public void close() {
        base.close();
    }

    /*@NotNull*/
    public SequenceIterator<T> getAnother() throws XPathException {
        return make(base.getAnother(), start);
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
        return base.getProperties() & (LAST_POSITION_FINDER | LOOKAHEAD);
    }
}

