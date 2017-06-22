////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * EveryItemMappingIterator applies a mapping function to each item in a sequence.
 * The mapping function always returns a single item (never null)
 * <p/>
 * This is a specialization of the more general MappingIterator class, for use
 * in cases where a single input item always maps to exactly one output item
 */

public final class EveryItemMappingIterator implements SequenceIterator {

    private SequenceIterator base;
    private ItemMappingFunction action;
    /*@Nullable*/ private Item current = null;

    /**
     * Construct an ItemMappingIterator that will apply a specified DummyItemMappingFunction to
     * each Item returned by the base iterator.
     *
     * @param base   the base iterator
     * @param action the mapping function to be applied
     */

    public EveryItemMappingIterator(SequenceIterator base, ItemMappingFunction action) {
        this.base = base;
        this.action = action;
    }

    public Item next() throws XPathException {
        Item nextSource = base.next();
        if (nextSource == null) {
            current = null;
            return null;
        }
        // Call the supplied mapping function
        current = action.mapItem(nextSource);
        return current;
    }

    public Item current() {
        return current;
    }

    public int position() {
        return base.position();
    }

    public void close() {
        base.close();
    }

    /*@NotNull*/
    public SequenceIterator getAnother() throws XPathException {
        return new EveryItemMappingIterator(base.getAnother(), action);
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator#GROUNDED},
     *         {@link net.sf.saxon.om.SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }
}

