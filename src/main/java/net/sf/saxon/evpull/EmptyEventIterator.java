////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

/**
 * This class is an EventIterator over an empty sequence. It is a singleton class.
 */
public class EmptyEventIterator implements EventIterator {

    private static EmptyEventIterator THE_INSTANCE = new EmptyEventIterator();

    /**
     * Get the singular instance of this class
     * @return the singular instance
     */

    public static EmptyEventIterator getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Get the next event in the sequence
     * @return null (there is never a next event)
     */

    /*@Nullable*/ public PullEvent next() {
        return null;
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

