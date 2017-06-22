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
* MappingFunction is an interface that must be satisfied by an object passed to a
* MappingIterator. It represents an object which, given an Item, can return a
* SequenceIterator that delivers a sequence of zero or more Items.
 *
 * This is a generic interface, where F represents the type of the input to the mapping
 * function, and T represents the type of the result
*/

public interface MappingFunction<F extends Item, T extends Item> {

    /**
     * Map one item to a sequence.
     * @param item The item to be mapped.
     * @return one of the following: (a) a SequenceIterator over the sequence of items that the supplied input
     * item maps to, or (b) null if it maps to an empty sequence.
     * @throws XPathException if a dynamic error occurs
    */

    /*@Nullable*/ public SequenceIterator<? extends T> map(F item) throws XPathException;

}

