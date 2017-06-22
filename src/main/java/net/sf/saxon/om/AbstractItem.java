////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.EmptySequence;

/**
 * Abstract superclass for items
 */
public abstract class AbstractItem implements Item, GroundedValue {

    /**
     * Get the n'th item in the value, counting from 0
     *
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */
    public Item itemAt(int n) {
        return (n==0 ? this : null);
    }

    /**
     * Get a subsequence of the value
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence. If min is
     */
    public final GroundedValue subsequence(int start, int length) {
        return ((start <= 0 && (start+length) > 0) ? this : EmptySequence.getInstance());
    }

    /**
     * Get the size of the value (the number of items)
     *
     * @return the number of items in the sequence
     */
    public final int getLength() {
        return 1;
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     * @throws net.sf.saxon.trans.XPathException
     *          in the situation where the sequence is evaluated lazily, and
     *          evaluation of the first item causes a dynamic error.
     */
    public Item head() throws XPathException {
        return this;
    }

    /**
     * Get an iterator over all the items in the sequence
     *
     * @return an iterator over all the items
     * @throws net.sf.saxon.trans.XPathException
     *          in the situation where the sequence is evaluated lazily, and
     *          constructing an iterator over the items causes a dynamic error.
     */
    public SequenceIterator<? extends Item> iterate() throws XPathException {
        return SingletonIterator.makeIterator((Item)this);
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of SingletonItem. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    public GroundedValue reduce() {
        return this;
    }
}

