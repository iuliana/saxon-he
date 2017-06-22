////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.om.FingerprintedNode;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;


/**
 * The SteppingNavigator is a utility class containing methods to assist with navigating a tree whose nodes
 * implement the {@link SteppingNode} interface
 */

public class SteppingNavigator {

    /**
     * Get the next following node after a given node
     * @param start the starting node
     * @param anchor the node whose descendants are being scanned; the scan terminates when
     * the anchor node is reached
     * @return the next node in document order after the starting node, excluding attributes and namespaces;
     * or null if no such node is found
     */

    public static SteppingNode getFollowingNode(SteppingNode start, SteppingNode anchor) {
        SteppingNode nodei = start.getFirstChild();
        if (nodei != null) {
            return nodei;
        }
        if (start.isSameNodeInfo(anchor)) {
            return null;
        }
        nodei = start;
        SteppingNode parenti = start.getParent();
        do {
            nodei = nodei.getNextSibling();
            if (nodei != null) {
                return nodei;
            } else if (parenti.isSameNodeInfo(anchor)) {
                return null;
            }
            nodei = parenti;
            parenti = parenti.getParent();
        } while (parenti != null);

        return null;
    }

    /**
     * Interface representing a function to step from one node to another within a tree
     */

    private interface Stepper {
        /**
         * Step from one node to another
         * @param node the start node
         * @return the end node
         */
        SteppingNode step(SteppingNode node);
    }

    /**
     * Stepper that steps from one node in a document to the next node in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached.
     */

    private static class FollowingNodeStepper implements Stepper {

        SteppingNode anchor;

        /**
         * Create a stepper to step successively through all nodes in a subtree
         * @param anchor the root of the subtree, marking the end point of the iteration
         */

        public FollowingNodeStepper(SteppingNode anchor) {
            this.anchor = anchor;
        }

        public SteppingNode step(SteppingNode node) {
            return getFollowingNode(node, anchor);
        }
    }

    /**
     * Stepper that steps from one node in a document to the next node in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only nodes that match a specified node test
     */

    private static class FollowingFilteredNodeStepper implements Stepper {

        SteppingNode anchor;
        NodeTest test;

        /**
         * Create a stepper to step successively through selected nodes in a subtree
         * @param anchor the root of the subtree, marking the end point of the iteration
         * @param test the test that returned nodes must satisfy
         */

        public FollowingFilteredNodeStepper(SteppingNode anchor, NodeTest test) {
            this.anchor = anchor;
            this.test = test;
        }

        public SteppingNode step(SteppingNode node) {
            do {
                node = getFollowingNode(node, anchor);
            } while (node != null && !test.matches(node));
            return node;
        }
    }

    /**
     * Stepper that steps from one element in a document to the next element in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only elements, with optional constraints on the namespace URI
     * and/or local name.
     */

    private static class FollowingElementStepper implements Stepper {

        SteppingNode anchor;
        String uri;
        String local;

        /**
         * Create a stepper to step successively through selected elements in a subtree
         * @param anchor the root of the subtree, marking the end point of the iteration
         * @param uri either null, or a namespace URI which the selected elements must match
         * @param local either null, or a local name which the selected elements must match
         */

        public FollowingElementStepper(SteppingNode anchor, String uri, String local) {
            this.anchor = anchor;
            this.uri = uri;
            this.local = local;
        }

        public SteppingNode step(SteppingNode node) {
            return node.getSuccessorElement(anchor, uri, local);
        }
    }

    /**
     * Stepper that steps from one element in a document to the next element in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only elements, with a constraint on the fingerprint of the element
     */

    private static class FollowingFingerprintedElementStepper implements Stepper {

        SteppingNode anchor;
        int fingerprint;

        /**
         * Create a stepper to step successively through selected elements in a subtree
         * @param anchor the root of the subtree, marking the end point of the iteration
         * @param fingerprint a fingerprint which selected elements must match
         */

        public FollowingFingerprintedElementStepper(SteppingNode anchor, int fingerprint) {
            this.anchor = anchor;
            this.fingerprint = fingerprint;
        }

        public SteppingNode step(SteppingNode node) {
            do {
                node = getFollowingNode(node, anchor);
            } while (node != null && node.getFingerprint() != fingerprint);
            return node;
        }
    }


    /**
     * An iterator over the descendant or descendant-or-self axis
     */

    public static class DescendantAxisIterator implements AxisIterator<NodeInfo> {

        private SteppingNode start;
        private boolean includeSelf;
        private NodeTest test;

        private SteppingNode current;
        private int position;

        private Stepper stepper;

        /**
         * Create an iterator over the descendant or descendant-or-self axis
         * @param start the root of the subtree whose descendants are required
         * @param includeSelf true if this is the descendant-or-self axis
         * @param test the node-test that selected nodes must satisfy
         */

        public DescendantAxisIterator(SteppingNode start, boolean includeSelf, NodeTest test) {
            this.start = start;
            this.includeSelf = includeSelf;
            this.test = test;

            if (!(includeSelf && test.matches(start))) {
                // initialize currNode to the start node if and only if this is NOT a descendant-or-self scan
                current = start;
            }

            if (test == null || test == AnyNodeTest.getInstance()) {
                stepper = new FollowingNodeStepper(start);
            } else if (test instanceof NameTest) {
                if (test.getPrimitiveType() == Type.ELEMENT) {
                    NameTest nt = (NameTest)test;
                    if (start instanceof FingerprintedNode) {
                        stepper = new FollowingFingerprintedElementStepper(start, nt.getFingerprint());
                    } else {
                        stepper = new FollowingElementStepper(start, nt.getNamespaceURI(), nt.getLocalPart());
                    }
                } else {
                    stepper = new FollowingFilteredNodeStepper(start, test);
                }
            } else if (test instanceof NodeKindTest) {
                if (test.getPrimitiveType() == Type.ELEMENT) {
                    stepper = new FollowingElementStepper(start, null, null);
                } else {
                    stepper = new FollowingFilteredNodeStepper(start, test);
                }
            } else if (test instanceof LocalNameTest) {
                if (test.getPrimitiveType() == Type.ELEMENT) {
                    LocalNameTest nt = (LocalNameTest)test;
                    stepper = new FollowingElementStepper(start, null, nt.getLocalName());
                } else {
                    stepper = new FollowingFilteredNodeStepper(start, test);
                }
            } else if (test instanceof NamespaceTest) {
                if (test.getPrimitiveType() == Type.ELEMENT) {
                    NamespaceTest nt = (NamespaceTest)test;
                    stepper = new FollowingElementStepper(start, nt.getNamespaceURI(), null);
                } else {
                    stepper = new FollowingFilteredNodeStepper(start, test);
                }
            } else {
                stepper = new FollowingFilteredNodeStepper(start, test);
            }
            position = 0;
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


        public SteppingNode next() {
            if (current == null) {
                // implies includeSelf: first time round, return the start node
                current = start;
                position  = 1;
                return start;
            }
            SteppingNode curr = stepper.step(current);

            if (curr != null) {
                position++;
            } else {
                position = -1;
            }
            return (current = curr);
        }

        public SteppingNode current() {
            return (position()==0 ? null : current);
        }

        public int position() {
            return position;
        }

        public void close() {
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
            return current.iterateAxis(axis, test);
        }

        /**
         * Return the atomized value of the current node.
         *
         * @return the atomized value.
         * @throws NullPointerException if there is no current node
         */

        public Sequence atomize() throws XPathException {
            return current.atomize();
        }

        /**
         * Return the string value of the current node.
         *
         * @return the string value, as an instance of CharSequence.
         * @throws NullPointerException if there is no current node
         */

        public CharSequence getStringValue() {
            return current.getStringValue();
        }

        /*@NotNull*/
        public AxisIterator<NodeInfo> getAnother() {
            return new DescendantAxisIterator(start, includeSelf, test);
        }

        public int getProperties() {
            return 0;
        }
    }
}

