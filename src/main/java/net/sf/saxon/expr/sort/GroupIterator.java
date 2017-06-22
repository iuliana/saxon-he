////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * A GroupIterator is an iterator that iterates over a sequence of groups.
 * The normal methods such as next() and current() always deliver the leading item
 * of the group. Additional methods are available to get the grouping key for the
 * current group (only applicable to group-by and group-adjacent), and to get all the
 * members of the current group.
 */

public interface GroupIterator extends SequenceIterator {

    /**
     * Set a local variable slot to hold the value of the current group
     * @param groupSlot the stack frame position of the variable holding the current group
     */

    public void setGroupSlot(int groupSlot);

    /**
     * Set a local variable slot to hold the value of the current grouping key
     * @param keySlot the stack frame position of the variable holding the current grouping key
     */

    public void setKeySlot(int keySlot);

    /**
     * Get the grouping key of the current group
     * @return the current grouping key in the case of group-by or group-adjacent,
     * or null in the case of group-starting-with and group-ending-with
     */

    /*@Nullable*/ public AtomicSequence getCurrentGroupingKey();

    /**
     * Get an iterator over the members of the current group, in population
     * order. This must always be a clean iterator, that is, an iterator that
     * starts at the first item of the group.
     * @return an iterator over all the members of the current group, in population
     * order.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */

    public SequenceIterator<? extends Item> iterateCurrentGroup() throws XPathException;

    /**
     * Ask whether this iterator has a current group
     * @return false if the controlling xsl:for-each-group element binds a group variable, true if it
     * delivers results using the current-group() function
     */

    public boolean hasCurrentGroup();

    /**
     * Ask whether this iterator has a current grouping key
     * @return false if the controlling xsl:for-each-group element binds a grouping-key variable, true if it
     * delivers results using the current-grouping-key() function
     */

    public boolean hasCurrentGroupingKey();

}

