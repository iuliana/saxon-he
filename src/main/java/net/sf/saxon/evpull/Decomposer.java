////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NameOfNode;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.tiny.TinyTreeEventIterator;
import net.sf.saxon.type.Type;

/**
 * This class takes a sequence of pull events and turns it into fully-decomposed form, that is, it
 * takes and document and element nodes in the sequence and turns them into a subsequence consisting of a
 * start element|document event, a content sequence, and an end element|document event, recursively.
 *
 * <p>The resulting sequence is decomposed, but not flat (it will contain nested EventIterators). To flatten
 * it, use {@link EventStackIterator#flatten(EventIterator)} </p>
 */
public class Decomposer implements EventIterator {

    private EventIterator base;
    private PipelineConfiguration pipe;

    /**
     * Create a Decomposer, which turns an event sequence into fully decomposed form
     * @param base the base sequence, which may be fully composed, fully decomposed, or
     * anything in between
     * @param pipe the Saxon pipeline configuration
     */

    public Decomposer(EventIterator base, PipelineConfiguration pipe) {
        this.pipe = pipe;
        this.base = EventStackIterator.flatten(base);
    }

    /**
     * Create a Decomposer which returns the sequence of events corresponding to
     * a particular node
     * @param node the node to be decomposed
     * @param pipe the Saxon pipeline configuration
     */

    public Decomposer(NodeInfo node, PipelineConfiguration pipe) {
        this.pipe = pipe;
        base = new SingletonEventIterator(node);
    }

    /**
     * Get the next event in the sequence
     *
     * @return the next event, or null when the sequence is exhausted. Note that since an EventIterator is
     *         itself a PullEvent, this method may return a nested iterator.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */

    public PullEvent next() throws XPathException {
        PullEvent pe = base.next();
        if (pe instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)pe;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT: {
                    if (node instanceof TinyNodeImpl) {
                        return new TinyTreeEventIterator(((TinyNodeImpl)node), pipe);
                    } else {
                        SequenceIterator content = node.iterateAxis(AxisInfo.CHILD);
                        EventIterator contentEvents = new EventIteratorOverSequence(content);
                        return new BracketedDocumentIterator(
                                new Decomposer(contentEvents, pipe));
                    }
                }
                case Type.ELEMENT: {
                    if (node instanceof TinyNodeImpl) {
                        return new TinyTreeEventIterator(((TinyNodeImpl)node), pipe);
                    } else {
                        SequenceIterator content = node.iterateAxis(AxisInfo.CHILD);
                        EventIterator contentEvents = new EventIteratorOverSequence(content);
                        StartElementEvent see = new StartElementEvent(pipe);
                        see.setElementName(new NameOfNode(node));
                        see.setTypeCode(node.getSchemaType());
                        see.setLocalNamespaces(node.getDeclaredNamespaces(null));
                        AxisIterator atts = node.iterateAxis(AxisInfo.ATTRIBUTE);
                        while (true) {
                            NodeInfo att = atts.next();
                            if (att == null) {
                                break;
                            }
                            see.addAttribute(att);
                        }
                        return new BracketedElementIterator(
                                see,
                                new Decomposer(contentEvents, pipe),
                                EndElementEvent.getInstance());
                    }
                }
                default:
                    return node;
            }
        } else {
            return pe;
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

