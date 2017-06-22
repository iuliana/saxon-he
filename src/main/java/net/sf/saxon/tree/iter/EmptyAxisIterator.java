////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;

/**
* An AxisIterator over an empty sequence
*/
public class EmptyAxisIterator<T extends NodeInfo>
        extends EmptyIterator<T> implements AxisIterator<T> {

    /*@NotNull*/
    private static EmptyAxisIterator<NodeInfo> theInstance =
            new EmptyAxisIterator<NodeInfo>();


    public static <T extends NodeInfo> net.sf.saxon.tree.iter.EmptyAxisIterator<T> emptyAxisIterator() {
        return (EmptyAxisIterator<T>) theInstance;
    }

    /**
     * Return an iterator over an axis, starting at the current node.
     *
     * @param axis the axis to iterate over, using a constant such as
     *             {@link net.sf.saxon.om.AxisInfo#CHILD}
     * @param test a predicate to apply to the nodes before returning them.
     * @throws NullPointerException if there is no current node
     */
    public AxisIterator iterateAxis(byte axis, NodeTest test) {
        throw new NullPointerException();
    }

    /**
     * Get another iterator over the same items, positioned at the start.
     *
     * @return another iterator over an empty sequence (in practice, it
     *         returns the same iterator each time)
     */
    /*@NotNull*/
    @Override
    public net.sf.saxon.tree.iter.EmptyAxisIterator<T> getAnother() {
        return this;
    }

    /**
     * Move to the next node, without returning it. Returns true if there is
     * a next node, false if the end of the sequence has been reached. After
     * calling this method, the current node may be retrieved using the
     * current() function.
     */

    public boolean moveNext() {
        return false;
    }
}

