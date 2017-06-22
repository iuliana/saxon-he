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
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

/**
 * ValueTailIterator iterates over a base sequence starting at an element other than the first.
 * It is used in the case where the base sequence is "grounded", that is, it exists in memory and
 * supports efficient direct addressing.
 */

public class ValueTailIterator<T extends Item>
        implements SequenceIterator<T>, GroundedIterator<T>, LookaheadIterator<T> {

    private GroundedValue baseValue;
    private int start;  // zero-based
    private int pos = 0;

    /**
     * Construct a ValueTailIterator
     * @param base   The items to be filtered
     * @param start    The position of the first item to be included (zero-based)
     */

    public ValueTailIterator(GroundedValue base, int start) {
        baseValue = base;
        this.start = start;
        pos = 0;
    }

    public T next() throws XPathException {
        return (T)baseValue.itemAt(start + pos++);
    }

    public T current() {
        return (T)baseValue.itemAt(start + pos - 1);
    }

    public int position() {
        return pos;
    }

    public boolean hasNext() {
        return baseValue.itemAt(start + pos) != null;
    }

    public void close() {
    }

    /*@NotNull*/
    public ValueTailIterator<T> getAnother() throws XPathException {
        return new ValueTailIterator<T>(baseValue, start);
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    public GroundedValue materialize() {
        if (start == 0) {
            return baseValue;
        } else {
            return baseValue.subsequence(start, Integer.MAX_VALUE);
        }
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
        return GROUNDED | LOOKAHEAD;
    }
}

