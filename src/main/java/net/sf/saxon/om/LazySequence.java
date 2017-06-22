////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

/**
 * A sequence that wraps an iterator, without being materialized. If used more than once, the input
 * iterator is re-evaluated (which can cause problem if the client is dependent on node identity).
 */
public class LazySequence implements Sequence {

    SequenceIterator iterator;
    boolean used = false;

    public LazySequence(SequenceIterator iterator) {
        this.iterator = iterator;
    }

    public Item head() throws XPathException {
        return iterate().next();
    }

    public synchronized SequenceIterator iterate() throws XPathException {
        if (used) {
            return iterator.getAnother();
        } else {
            used = true;
            return iterator;
        }
    }
}

