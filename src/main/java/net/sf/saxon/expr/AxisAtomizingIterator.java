////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.value.AtomicValue;

/**
* This iterator returns a sequence of atomic values, the result of atomizing the sequence
 * of nodes returned by an underlying AxisIterator.
*/

public final class AxisAtomizingIterator implements SequenceIterator<AtomicValue> {

    private AxisIterator base;
    /*@Nullable*/ private SequenceIterator results = null;
    private AtomicValue current = null;
    private int position = 0;

    /**
    * Construct an atomizing iterator
    * @param base the base iterator (whose nodes are to be atomized)
     */

    public AxisAtomizingIterator(AxisIterator base) {
        this.base = base;
    }

    public AtomicValue next() throws XPathException {
        AtomicValue nextItem;
        while (true) {
            if (results != null) {
                nextItem = (AtomicValue)results.next();
                if (nextItem != null) {
                    break;
                } else {
                    results = null;
                }
            }
            // Avoid calling next() to materialize the NodeInfo object
            if (base.moveNext()) {
                Sequence atomized = base.atomize();
                if (atomized instanceof AtomicValue) {
                    // common case (the atomized value of the node is a single atomic value)
                    results = null;
                    nextItem = (AtomicValue)atomized;
                    break;
                } else {
                    results = atomized.iterate();
                    nextItem = (AtomicValue)results.next();
                    if (nextItem == null) {
                        results = null;
                    } else {
                        break;
                    }
                }
                // now go round the loop to get the next item from the base sequence
            } else {
                results = null;
                current = null;
                position = -1;
                return null;
            }
        }

        current = nextItem;
        position++;
        return nextItem;
    }

    public AtomicValue current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        base.close();
    }

    /*@NotNull*/
    public SequenceIterator<AtomicValue> getAnother() {
        // System.err.println(this + " getAnother() ");
        AxisIterator newBase = base.getAnother();
        return new AxisAtomizingIterator(newBase);
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

}

