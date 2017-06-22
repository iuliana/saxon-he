////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

/**
 * This interface represents an XDM Value, that is, a sequence of items.
 *
 * Note that different implementations of Sequence might have very different
 * With some sequences, calling iterate() may trigger evaluation of the logic
 * that computes the sequence, and calling iterate() again may cause re-evaluation.
 *
 * Users should avoid assuming that a sequence of length one will always
 * be represented as an instance of Item. If you are confident that the sequence
 * will be of length one, call the head() function to get the first item.
 *
 * @since 9.5
 */
public interface Sequence {

    /**
     * Get the first item in the sequence.
     * @return the first item in the sequence if there is one, or null if the sequence
     * is empty
     * @throws XPathException in the situation where the sequence is evaluated lazily, and
     * evaluation of the first item causes a dynamic error.
     */

    public Item head() throws XPathException;

    /**
     * Get an iterator
 * performance characteristics, though all should exhibit the same behaviour.over all the items in the sequence
     * @return an iterator over all the items
     * @throws XPathException in the situation where the sequence is evaluated lazily, and
     * constructing an iterator over the items causes a dynamic error.
     */

    public SequenceIterator<? extends Item> iterate() throws XPathException;
}

