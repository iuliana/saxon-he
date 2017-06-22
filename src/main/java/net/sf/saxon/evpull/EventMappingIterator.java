////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * MappingIterator merges a sequence of sequences into a single sequence.
 * It takes as inputs an iteration, and a mapping function to be
 * applied to each Item returned by that iteration. The mapping function itself
 * returns another iteration. The result is an iteration of iterators. To convert this
 * int a single flat iterator over a uniform sequence of events, the result must be wrapped
 * in an {@link EventStackIterator}<p>
*/

public final class EventMappingIterator implements EventIterator {

    private SequenceIterator base;
    private EventMappingFunction action;

    /**
     * Construct a MappingIterator that will apply a specified MappingFunction to
     * each Item returned by the base iterator.
     * @param base the base iterator
     * @param action the mapping function to be applied
     */

    public EventMappingIterator(SequenceIterator base, EventMappingFunction action) {
        this.base = base;
        this.action = action;
    }


    /*@Nullable*/ public PullEvent next() throws XPathException {
        Item nextSource = base.next();
        return (nextSource == null ? null : action.map(nextSource));
    }

    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence() {
        return false;
    }
}

