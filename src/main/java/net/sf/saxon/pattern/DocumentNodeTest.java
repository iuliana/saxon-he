////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.Type;

/**
  * A DocumentNodeTest implements the test document-node(element(~,~))
  */

// This is messy because the standard interface for a NodeTest does not allow
// any navigation from the node in question - it only tests for the node kind,
// node name, and type annotation of the node.

public class DocumentNodeTest extends NodeTest {


	private NodeTest elementTest;

	public DocumentNodeTest(NodeTest elementTest) {
        this.elementTest = elementTest;
	}

    /**
     * Test whether this node test is satisfied by a given node. This method is only
     * fully supported for a subset of NodeTests, because it doesn't provide all the information
     * needed to evaluate all node tests. In particular (a) it can't be used to evaluate a node
     * test of the form element(N,T) or schema-element(E) where it is necessary to know whether the
     * node is nilled, and (b) it can't be used to evaluate a node test of the form
     * document-node(element(X)). This in practice means that it is used (a) to evaluate the
     * simple node tests found in the XPath 1.0 subset used in XML Schema, and (b) to evaluate
     * node tests where the node kind is known to be an attribute.
     *
     * @param nodeKind   The kind of node to be matched
     * @param name       identifies the expanded name of the node to be matched.
     *                   The value should be null for a node with no name.
     * @param annotation The actual content type of the node
     */
    @Override
    public boolean matches(int nodeKind, NodeName name, int annotation) {
        throw new UnsupportedOperationException("DocumentNodeTest doesn't support this method");
    }

    /**
     * Test whether this node test is satisfied by a given node on a TinyTree. The node
     * must be a document, element, text, comment, or processing instruction node.
     * This method is provided
     * so that when navigating a TinyTree a node can be rejected without
     * actually instantiating a NodeInfo object.
     *
     * @param tree   the TinyTree containing the node
     * @param nodeNr the number of the node within the TinyTree
     * @return true if the node matches the NodeTest, otherwise false
     */

    public boolean matches(TinyTree tree, int nodeNr) {
        if (tree.getNodeKind(nodeNr) != Type.DOCUMENT) {
            return false;
        }
        return matches(tree.getNode(nodeNr));
    }

    /**
    * Determine whether this Pattern matches the given Node.
    * @param node The NodeInfo representing the Element or other node to be tested against the Pattern
    * uses variables, or contains calls on functions such as document() or key().
    * @return true if the node matches the Pattern, false otherwise
    */

    public boolean matches(NodeInfo node) {
        if (node.getNodeKind() != Type.DOCUMENT) {
            return false;
        }
        AxisIterator iter = node.iterateAxis(AxisInfo.CHILD);
        // The match is true if there is exactly one element node child, no text node
        // children, and the element node matches the element test.
        boolean found = false;
        while (true) {
            NodeInfo n = (NodeInfo)iter.next();
            if (n==null) {
                return found;
            }
            int kind = n.getNodeKind();
            if (kind==Type.TEXT) {
                return false;
            } else if (kind==Type.ELEMENT) {
                if (found) {
                    return false;
                }
                if (elementTest.matches(n)) {
                    found = true;
                } else {
                    return false;
                }
            }
        }
    }

    /**
    * Determine the default priority of this node test when used on its own as a Pattern
    */

    public final double getDefaultPriority() {
    	return elementTest.getDefaultPriority();
    }

    /**
    * Determine the types of nodes to which this pattern applies. Used for optimisation.
    * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
    */

    public int getPrimitiveType() {
        return Type.DOCUMENT;
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        return 1<<Type.DOCUMENT;
    }

    /**
     * Get the element test contained within this document test
     * @return the contained element test
     */

    public NodeTest getElementTest() {
        return elementTest;
    }

    public String toString() {
        return "document-node(" + elementTest.toString() + ')';
    }

    /**
      * Returns a hash code value for the object.
      */

     public int hashCode() {
         return elementTest.hashCode()^12345;
     }

     public boolean equals(/*@NotNull*/ Object other) {
         return other instanceof DocumentNodeTest &&
                 ((DocumentNodeTest)other).elementTest.equals(elementTest);
     }

}

