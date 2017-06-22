////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.trans.XPathException;

/**
 * This class represents an EventIterator over a sequence containing a single pull event.
 */
public class SingletonEventIterator implements EventIterator {

    /*@Nullable*/ private PullEvent event;

    /**
     * Create an iterator over a sequence containing a single pull event
     * @param event the single event. This must not be an EventIterator
     */

    public SingletonEventIterator(PullEvent event) {
        this.event = event;
        if (event instanceof EventIterator) {
            throw new IllegalArgumentException("event");
        }
    }

    /**
     * Get the next event in the sequence
     *
     * @return the next event, or null when the sequence is exhausted
     * @throws XPathException if a dynamic evaluation error occurs
     */

    public PullEvent next() throws XPathException {
        PullEvent next = event;
        event = null;
        return next;
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

