////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

/**
* EventMappingFunction is an interface that must be satisfied by an object passed to an
* EventMappingIterator. It represents an object which, given an Item, can return an
* EventIterator that delivers a sequence of zero or more PullEvents.
*/

public interface EventMappingFunction {

    /**
    * Map one item to a sequence of pull events.
    * @param item The item to be mapped.
    * @return one of the following: (a) an EventIterator over the sequence of items that the supplied input
    * item maps to, or (b) null if it maps to an empty sequence.
    */

    public EventIterator map(Item item) throws XPathException;

}

