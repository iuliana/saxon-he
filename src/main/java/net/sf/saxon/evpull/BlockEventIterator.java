////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * Iterate over the instructions in a Block, concatenating the result of each instruction
 * into a single combined sequence.
 */

public class BlockEventIterator implements EventIterator {

    private Expression[] children;
    private int i = 0;
    private EventIterator child;
    private XPathContext context;

    /**
     * Create an EventIterator over the results of evaluating a Block
     * @param children the sequence of instructions comprising the Block
     * @param context the XPath dynamic context
     */

    public BlockEventIterator(Expression[] children, XPathContext context) {
        this.children = children;
        this.context = context;
    }

    /**
     * Get the next item in the sequence.
     *
     * @return the next item, or null if there are no more items.
     * @throws XPathException if an error occurs retrieving the next item
     */

    /*@Nullable*/ public PullEvent next() throws XPathException {
        while (true) {
            if (child == null) {
                child = children[i++].iterateEvents(context);
            }
            PullEvent current = child.next();
            if (current != null) {
                return current;
            }
            child = null;
            if (i >= children.length) {
                return null;
            }
        }
    }


    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence() {
        return false;
    }
}

