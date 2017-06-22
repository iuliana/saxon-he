////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * The class is an EventIterator that handles the events arising from an element constructor:
 * that is, the start/end event pair for the element node, bracketing a sequence of events for the
 * content of the element.
 *
 * <p>This class does not normalize the content (for example by merging adjacent text nodes). That is the job
 * of the {@link ComplexContentProcessor}.</p>
 *
 * <p>The event stream consumed by a BracketedElementIterator may contain freestanding attribute and namespace nodes.
 * The event stream delivered by a BracketedElementIterator, however, packages all attributes and namespaces as
 * part of the startElement event.</p>
 */
public class BracketedElementIterator implements EventIterator {

    private StartElementEvent start;
    private EventIterator content;
    private PullEvent pendingContent;
    private EndElementEvent  end;
    private int state = INITIAL_STATE;

    private static final int INITIAL_STATE = 0;
    private static final int PROCESSING_FIRST_CHILD = 1;
    private static final int PROCESSING_REMAINING_CHILDREN = 2;
    private static final int REACHED_END_TAG = 3;
    private static final int EXHAUSTED = 4;

    /**
     * Constructor
     * @param start the StartElementEvent object
     * @param content iterator that delivers the content of the element
     * @param end the EndElementEvent object
     */

    public BracketedElementIterator(StartElementEvent start, EventIterator content, EndElementEvent end) {
        this.start = start;
        this.content = EventStackIterator.flatten(content);
        this.end = end;
        state = 0;
    }

    /**
     * Get the next event in the sequence
     * @return the next event, or null when the sequence is exhausted
     * @throws XPathException if a dynamic evaluation error occurs
     */

    /*@Nullable*/ public PullEvent next() throws XPathException {

        switch (state) {
            case INITIAL_STATE:
                while (true) {
                    PullEvent pe = content.next();
                    if (pe == null) {
                        pendingContent = null;
                        state = REACHED_END_TAG;
                        break;
                    } else if (pe instanceof NodeInfo) {
                        int k = ((NodeInfo)pe).getNodeKind();
                        if (k == Type.NAMESPACE) {
                            NamespaceBinding nscode = new NamespaceBinding(
                                    ((NodeInfo)pe).getLocalPart(), ((NodeInfo)pe).getStringValue());
                            start.addNamespace(nscode);
                            continue;
                        } else if (k == Type.ATTRIBUTE) {
                            start.addAttribute((NodeInfo)pe);
                            continue;
                        } else if (k == Type.TEXT && ((NodeInfo)pe).getStringValueCS().length() == 0) {
                            // ignore a zero-length text node
                            continue;
                        }
                    }
                    pendingContent = pe;
                    state = PROCESSING_FIRST_CHILD;
                    break;
                }
                start.namespaceFixup();
                return start;

            case PROCESSING_FIRST_CHILD:
                state = PROCESSING_REMAINING_CHILDREN;
                return pendingContent;

            case PROCESSING_REMAINING_CHILDREN:
                PullEvent pe = content.next();
                if (pe == null) {
                    state = EXHAUSTED;
                    return end;
                } else {
                    return pe;
                }

            case REACHED_END_TAG:
                state = EXHAUSTED;
                return end;

            case EXHAUSTED:
                return null;

            default:
                throw new AssertionError("BracketedEventIterator state " + state);
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

