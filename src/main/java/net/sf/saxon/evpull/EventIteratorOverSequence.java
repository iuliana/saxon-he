////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * This class maps a SequenceIterator to an EventIterator, by simply returning the items in the sequence
 * as PullEvents.
 */
public class EventIteratorOverSequence implements EventIterator {

    SequenceIterator base;

    /**
     * Create an EventIterator that wraps a given SequenceIterator
     * @param base the SequenceIterator to be wrapped
     */

    public EventIteratorOverSequence(SequenceIterator base) {
        this.base = base;
    }

    /**
     * Get the next PullEvent in the sequence
     * @return the next PullEvent
     * @throws XPathException in case of a dynamic error
     */

    public PullEvent next() throws XPathException {
        return base.next();
    }


    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence() {
        return true;
    }
}

