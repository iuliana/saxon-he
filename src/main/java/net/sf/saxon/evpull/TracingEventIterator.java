////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.trans.XPathException;

/**
 * This class is a filter for a sequence of pull events; it returns the input sequence unchanged,
 * but traces execution to System.err
 */
public class TracingEventIterator implements EventIterator {

    private EventIterator base;

    /**
     * Create an event iterator that traces all events received from the base sequence, and returns
     * them unchanged
     * @param base the iterator over the base sequence
     */

    public TracingEventIterator(EventIterator base) {
        this.base = base;
    }


    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence() {
        return base.isFlatSequence();
    }

    /**
     * Get the next event in the sequence
     *
     * @return the next event, or null when the sequence is exhausted. Note that since an EventIterator is
     *         itself a PullEvent, this method may return a nested iterator.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */

    /*@Nullable*/ public PullEvent next() throws XPathException {
        PullEvent next = base.next();
        if (next == null) {
            System.err.println("EVPULL end-of-sequence");
        } else {
            System.err.println("EVPULL " + next.getClass().getName());
        }
        return next;
    }
}

