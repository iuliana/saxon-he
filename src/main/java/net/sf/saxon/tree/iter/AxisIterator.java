////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;


/**
 * A SequenceIterator is used to iterate over a sequence. An AxisIterator
 * is a SequenceIterator that throws no exceptions, and that always returns
 * nodes. The nodes should all be in the same document (though there are
 * some cases, such as PrependIterator, where this is the responsibility of the
 * user of the class and is not enforced.)
 */

public interface AxisIterator<T extends NodeInfo> extends UnfailingIterator<T> {

    /**
     * Move to the next node, without returning it. Returns true if there is
     * a next node, false if the end of the sequence has been reached. After
     * calling this method, the current node may be retrieved using the
     * current() function.
     * @return true if there is a next node, false if the end of the sequence has been reached
     */

    boolean moveNext();

    /*@Nullable*/ T next();

    /*@Nullable*/ T current();

    /*@NotNull*/
    AxisIterator<T> getAnother();

    /**
     * Return an iterator over an axis, starting at the current node.
     * @param axis the axis to iterate over, using a constant such as
     * {@link net.sf.saxon.om.AxisInfo#CHILD}
     * @param test a predicate to apply to the nodes before returning them.
     * @return an iterator over an axis, starting at the current node
     * @throws NullPointerException if there is no current node
     */

    public AxisIterator iterateAxis(byte axis, NodeTest test);

    /**
     * Return the atomized value of the current node.
     * @return the atomized value.
     * @throws NullPointerException if there is no current node
     * @throws net.sf.saxon.trans.XPathException if the current node
     * cannot be atomized, for example because it is an element node with
     * element-only content.
     */

    public Sequence atomize() throws XPathException;

    /**
     * Return the string value of the current node.
     * @return the string value, as an instance of CharSequence.
     * @throws NullPointerException if there is no current node
     */

    public CharSequence getStringValue();




}

