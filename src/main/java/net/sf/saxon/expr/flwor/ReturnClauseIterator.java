////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * This iterator applies the return expression of a FLWOR expression to each
 * of the tuples in a supplied tuple stream, returning the result as an iterator
 */

public class ReturnClauseIterator implements SequenceIterator<Item> {

    private TuplePull base;
    private FLWORExpression flwor;
    /*@Nullable*/ private Expression action;
    private XPathContext context;
    private SequenceIterator results = null;
    private Item current = null;
    private int position = 0;

    /**
     * Construct an iterator over the results of the FLWOR expression.
     * @param base the base iterator
     * @param flwor the FLWOR expression
     * @param context the XPath dynamic context
     */

    public ReturnClauseIterator(TuplePull base, FLWORExpression flwor, XPathContext context) {
        this.base = base;
        this.flwor = flwor;
        this.action = flwor.getReturnClause();
        this.context = context;
    }

    public Item next() throws XPathException {
        Item nextItem;
        while (true) {
            if (results != null) {
                nextItem = results.next();
                if (nextItem != null) {
                    break;
                } else {
                    results = null;
                }
            }
            if (base.nextTuple(context)) {
                // Call the supplied return expression
                results = action.iterate(context);
                nextItem = results.next();
                if (nextItem == null) {
                    results = null;
                } else {
                    break;
                }
                // now go round the loop to get the next item from the base sequence
            } else {
                results = null;
                current = null;
                position = -1;
                return null;
            }
        }

        current = nextItem;
        position++;
        return nextItem;
    }

    public Item current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        if (results != null) {
            results.close();
        }
        base.close();
    }

    /*@NotNull*/
    public SequenceIterator getAnother() throws XPathException {
        return flwor.iterate(context);
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

}

