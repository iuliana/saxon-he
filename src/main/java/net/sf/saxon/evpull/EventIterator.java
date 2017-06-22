////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.trans.XPathException;

/**
 * An iterator over a sequence of events
 */
public interface EventIterator extends PullEvent {

    /**
     * Get the next event in the sequence
     * @return the next event, or null when the sequence is exhausted. Note that since an EventIterator is
     * itself a PullEvent, this method may return a nested iterator.
     * @throws XPathException if a dynamic evaluation error occurs
     */

    /*@Nullable*/ public PullEvent next() throws XPathException;

    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence();
}

