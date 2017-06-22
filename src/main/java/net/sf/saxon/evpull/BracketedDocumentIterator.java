////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.trans.XPathException;

/**
 * The class is an EventIterator that handles the events arising from a document node constructor:
 * that is, the start/end event pair for the document node, bracketing a sequence of events for the
 * content of the document.
 *
 * <p>This class does not normalize the content (for example by merging adjacent text nodes). That is the job
 * of the {@link ComplexContentProcessor}.</p>
 *
 */
public class BracketedDocumentIterator implements EventIterator {

    private EventIterator content;
    private int state = INITIAL_STATE;

    private static final int INITIAL_STATE = 0;
    private static final int PROCESSING_CHILDREN = 1;
    private static final int EXHAUSTED = 2;

    /**
     * Constructor
     * @param content iterator that delivers the content of the document
     */

    public BracketedDocumentIterator(EventIterator content) {
        this.content = EventStackIterator.flatten(content);
        state = 0;
    }

    /**
     * Get the next event in the sequence
     * @return the next event, or null when the sequence is exhausted
     * @throws net.sf.saxon.trans.XPathException if a dynamic evaluation error occurs
     */

    /*@Nullable*/ public PullEvent next() throws XPathException {

        switch (state) {
            case INITIAL_STATE:
                state = PROCESSING_CHILDREN;
                return StartDocumentEvent.getInstance();

            case PROCESSING_CHILDREN:
                PullEvent pe = content.next();
                if (pe == null) {
                    state = EXHAUSTED;
                    return EndDocumentEvent.getInstance();
                } else {
                    return pe;
                }

            case EXHAUSTED:
                return null;

            default:
                throw new AssertionError("BracketedDocumentIterator state " + state);
        }
    }


    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */

    public boolean isFlatSequence() {
        return true;
    }
}

