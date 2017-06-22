////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;

/**
 * This class wraps any SequenceIterator as an AxisIterator. It must only be used if it
 * is known that the underlying SequenceIterator will always return nodes, and will never
 * throw an exception.
 */

public class AxisIteratorOverSequence<T extends NodeInfo> implements AxisIterator<T> {

    private SequenceIterator<T> base;

    public AxisIteratorOverSequence(SequenceIterator<T> base) {
        this.base = base;
    }

    public T next() {
        try {
            return base.next();
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    /*@NotNull*/
    public AxisIterator<T> getAnother() {
        try {
            return new AxisIteratorOverSequence<T>(base.getAnother());
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    /**
     * Move to the next node, without returning it. Returns true if there is
     * a next node, false if the end of the sequence has been reached. After
     * calling this method, the current node may be retrieved using the
     * current() function.
     */

    public boolean moveNext() {
        return (next() != null);
    }

    /**
     * Get the current node in the sequence.
     * @return the node returned by the most recent call on next()
     */

    /*@Nullable*/ public T current() {
        return base.current();
    }

    /**
     * Get the current position
     * @return the position of the most recent node returned by next()
     */

    public final int position() {
        return base.position();
    }

    public void close() {
        base.close();
    }

    /**
     * Return an iterator over an axis, starting at the current node.
     *
     * @param axis the axis to iterate over, using a constant such as
     *             {@link net.sf.saxon.om.AxisInfo#CHILD}
     * @param test a predicate to apply to the nodes before returning them.
     */

    public AxisIterator iterateAxis(byte axis, NodeTest test) {
        //noinspection ConstantConditions
        return current().iterateAxis(axis, test);
    }

    /**
     * Return the atomized value of the current node.
     *
     * @return the atomized value.
     * @throws NullPointerException if there is no current node
     */

    public Sequence atomize() throws XPathException {
        //noinspection ConstantConditions
        return current().atomize();
    }

    /**
     * Return the string value of the current node.
     *
     * @return the string value, as an instance of CharSequence.
     * @throws NullPointerException if there is no current node
     */

    public CharSequence getStringValue() {
        //noinspection ConstantConditions
        return current().getStringValueCS();
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
     *         and {@link #LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

}

