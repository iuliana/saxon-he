////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.trans.XPathException;

import java.util.Stack;

/**
 * An EventStackIterator is an EventIterator that delivers a flat sequence of PullEvents
 * containing no nested EventIterators
 */
public class EventStackIterator implements EventIterator {

    private Stack<EventIterator> eventStack = new Stack<EventIterator>();

    /**
     * Factory method to create an iterator that flattens the sequence of PullEvents received
     * from a base iterator, that is, it returns an EventIterator that will never return any
     * nested iterators.
     * @param base the base iterator. Any nested EventIterator returned by the base iterator
     * will be flattened, recursively.
     */

    public static EventIterator flatten(EventIterator base) {
        if (base.isFlatSequence()) {
            return base;
        }
        return new EventStackIterator(base);
    }

    /**
     * Create a EventStackIterator that flattens the sequence of PullEvents received
     * from a base iterator
     * @param base the base iterator. Any nested EventIterator returned by the base iterator
     * will be flattened, recursively.
     */

    private EventStackIterator(EventIterator base) {
        eventStack.push(base);
    }

    /**
     * Get the next event in the sequence. This will never be an EventIterator.
     *
     * @return the next event, or null when the sequence is exhausted
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */

    /*@Nullable*/ public PullEvent next() throws XPathException {
        if (eventStack.isEmpty()) {
            return null;
        }
        EventIterator iter = eventStack.peek();
        PullEvent next = iter.next();
        if (next == null) {
            eventStack.pop();
            return next();
        } else if (next instanceof EventIterator) {
            eventStack.push((EventIterator)next);
            return next();
        } else {
            return next;
        }
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

