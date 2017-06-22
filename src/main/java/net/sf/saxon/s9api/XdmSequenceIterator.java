////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

import java.util.Iterator;

/**
 * An iterator over an XPath sequence.
 *
 * <p>This class implements the standard Java Iterator interface.</p>
 *
 * <p>Because the <code>Iterator</code> interface does not define any checked
 * exceptions, the <code>hasNext()</code> method of this iterator throws an unchecked
 * exception if a dynamic error occurs while evaluating the expression. Applications
 * wishing to control error handling should take care to catch this exception.</p>
 */
public class XdmSequenceIterator implements Iterator<XdmItem> {

    /*@Nullable*/ private XdmItem next = null;
    private int state = BEFORE_ITEM;
    private SequenceIterator base;

    private final static int BEFORE_ITEM = 0;
    private final static int ON_ITEM = 1;
    private final static int FINISHED = 2;

    protected XdmSequenceIterator(SequenceIterator base) {
        this.base = base;
        this.state = BEFORE_ITEM;
    }

    /**
     * Returns <tt>true</tt> if the iteration has more elements. (In other
     * words, returns <tt>true</tt> if <tt>next</tt> would return an element
     * rather than throwing an exception.)
     *
     * @return <tt>true</tt> if the iterator has more elements.
     *
     * @throws SaxonApiUncheckedException if a dynamic error occurs during XPath evaluation that
     * is detected at this point.
     */
    public boolean hasNext() throws SaxonApiUncheckedException {
        switch (state) {
            case ON_ITEM:
                return true;
            case FINISHED:
                return false;
            case BEFORE_ITEM:
                try {
                    next = XdmItem.wrapItem(base.next());
                    if (next == null) {
                        state = FINISHED;
                        return false;
                    } else {
                        state = ON_ITEM;
                        return true;
                    }
                } catch (XPathException err) {
                    throw new SaxonApiUncheckedException(err);
                }
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Returns the next element in the iteration.  Calling this method
     * repeatedly until the {@link #hasNext()} method returns false will
     * return each element in the underlying collection exactly once.
     *
     * @return the next element in the iteration.
     * @throws java.util.NoSuchElementException
     *          iteration has no more elements.
     */
    public XdmItem next() {
        switch (state) {
            case ON_ITEM:
                state = BEFORE_ITEM;
                return next;
            case FINISHED:
                throw new java.util.NoSuchElementException();
            case BEFORE_ITEM:
                if (hasNext()) {
                    state = BEFORE_ITEM;
                    return next;
                } else {
                    throw new java.util.NoSuchElementException();
                }
            default:
                throw new IllegalStateException();
        }

    }

    /**
     * Not supported on this implementation.
     *
     * @throws UnsupportedOperationException always
     */

    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * The close() method should be called to release resources if the caller wants to stop reading
     * data before reaching the end. This is particularly relevant if the query uses saxon:stream()
     * to read its input, since there will then be another thread supplying data, which will be left
     * in suspended animation if no-one is consuming the data.
     * @since 9.5.1.5 (see bug 2016)
     */

    public void close() {
        base.close();
    }
}

