////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import org.apache.axiom.om.*;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A node in the XDM tree; specifically, a node that wraps an Axiom document node or element node.
 * If the wrapped node is a document node,
 *
 * @author Michael H. Kay
 */

public class AxiomElementNodeWrapper extends AxiomParentNodeWrapper {

    private AxiomParentNodeWrapper parent; // null means unknown

    protected AxiomDocumentWrapper docWrapper;

    protected int index; // -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node       The Axiom node to be wrapped
     * @param docWrapper The wrapper around the document node at the root of the tree
     * @param parent     The NodeWrapper that wraps the parent of this node
     * @param index      Position of this node among its siblings
     */
    protected AxiomElementNodeWrapper(OMElement node, AxiomDocumentWrapper docWrapper, AxiomParentNodeWrapper parent, int index) {
        super(node);
        this.parent = parent;
        this.docWrapper = docWrapper;
        this.index = index;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    public int getNodeKind() {
        return Type.ELEMENT;
    }

    /**
     * Get the type annotation
     */

    public int getTypeAnnotation() {
        return StandardNames.XS_UNTYPED;
    }

    /**
     * Get the type annotation of this node, if any. The type annotation is represented as
     * SchemaType object.
     * <p/>
     * <p>Types derived from a DTD are not reflected in the result of this method.</p>
     *
     * @return For element and attribute nodes: the type annotation derived from schema
     *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
     *         validation). For comments, text nodes, processing instructions, and namespaces: null.
     *         For document nodes, either xs:untyped if the document has not been validated, or
     *         xs:anyType if it has.
     * @since 9.4
     */

    public SchemaType getSchemaType() {
        return Untyped.getInstance();
    }


    /**
     * Determine the relative position of this node and another node, in
     * document order. The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     *         other node, or 0 if they are the same node. (In this case,
     *         isSameNode() will always return true, and the two nodes will
     *         produce the same result for generateId())
     */

    public int compareOrder(NodeInfo other) {
        if (other instanceof AxiomDocumentWrapper) {
            return +1;
        } else if (other instanceof AxiomAttributeWrapper) {
            if (other.getParent() == this) {
                return -1;
            } else {
                return compareOrder(other.getParent());
            }
        } else if (other instanceof NamespaceNode) {
            return -other.compareOrder(this);
        } else {
            return Navigator.compareOrder(this, (SiblingCountingNode) other);
        }
    }

    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    public String getLocalPart() {
        return ((OMElement) node).getLocalName();
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    public String getPrefix() {
        String prefix = ((OMElement) node).getPrefix();
        return (prefix == null ? "" : prefix);
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     *         for a node with an empty prefix, return an empty string.
     */

    public String getURI() {
        String uri = ((OMElement) node).getNamespaceURI();
        return (uri == null ? "" : uri);
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    public AxiomParentNodeWrapper getParent() {
        if (parent == null) {
            OMContainer rawParent = ((OMElement) node).getParent();
            if (rawParent instanceof OMDocument) {
                parent = docWrapper;
            } else {
                parent = (AxiomElementNodeWrapper) AxiomDocumentWrapper.makeWrapper(((OMElement) rawParent), docWrapper, null, -1);
            }
        }
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */

    public int getSiblingPosition() {
        if (index != -1) {
            return index;
        }

        OMContainer p = getParent().node;
        int ix = 0;
        for (Iterator kids = p.getChildren(); kids.hasNext(); ) {
            if (kids.next() == node) {
                return (index = ix);
            }
            ix++;
        }
        throw new IllegalStateException("Bad child/parent relationship in Axiom tree");
    }


    @Override
    protected AxisIterator<NodeInfo> iterateAttributes(NodeTest nodeTest) {
        if (!((OMElement) node).getAllAttributes().hasNext()) {
            return EmptyAxisIterator.emptyAxisIterator();
        } else if (nodeTest instanceof NameTest) {
            String uri = ((NameTest) nodeTest).getNamespaceURI();
            String local = ((NameTest) nodeTest).getLocalPart();
            OMAttribute att = ((OMElement) node).getAttribute(new QName(uri, local));
            if (att == null) {
                return EmptyAxisIterator.emptyAxisIterator();
            } else {
                return SingleNodeIterator.makeIterator(new AxiomAttributeWrapper(att, this, -1));
            }
        } else {
            return new AttributeAxisIterator(this, nodeTest);
        }
    }

    @Override
    protected AxisIterator<NodeInfo> iterateSiblings(NodeTest nodeTest, boolean forwards) {
        if (forwards) {
            if (nodeTest instanceof AnyNodeTest) {
                return new AxiomDocumentWrapper.FollowingSiblingIterator((OMElement) node, parent, docWrapper);
            } else {
                return new Navigator.AxisFilter(
                        new AxiomDocumentWrapper.FollowingSiblingIterator((OMElement) node, parent, docWrapper), nodeTest);
            }
        } else {
            if (nodeTest instanceof AnyNodeTest) {
                return new AxiomDocumentWrapper.PrecedingSiblingIterator((OMElement) node, parent, docWrapper);
            } else {
                return new Navigator.AxisFilter(
                        new AxiomDocumentWrapper.PrecedingSiblingIterator((OMElement) node, parent, docWrapper), nodeTest);
            }
        }
    }

    /**
     * Get the string value of a given attribute of this node
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */

    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
        return ((OMElement) node).getAttributeValue(new javax.xml.namespace.QName(uri, local, ""));
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node
     */

    public NodeInfo getRoot() {
        return docWrapper;
    }

    /**
     * Get the root node, if it is a document node.
     *
     * @return the DocumentInfo representing the containing document.
     */

    public DocumentInfo getDocumentRoot() {
        if (docWrapper.node instanceof OMDocument) {
            return docWrapper;
        } else {
            return null;
        }
    }

    /**
     * Get the document number of the document containing this node. For a
     * free-standing orphan node, just return the hashcode.
     */

    public long getDocumentNumber() {
        return docWrapper.getDocumentNumber();
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
     *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to -1.
     *         <p/>
     *         <p>For a node other than an element, the method returns null.</p>
     */

    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        OMElement elem = (OMElement) node;
        List<NamespaceBinding> list = new ArrayList<NamespaceBinding>();
        for (Iterator iter = elem.getAllDeclaredNamespaces(); iter.hasNext(); ) {
            OMNamespace ns = (OMNamespace) iter.next();
            NamespaceBinding nb = new NamespaceBinding(ns.getPrefix(), ns.getNamespaceURI());
            list.add(nb);
        }
        NamespaceBinding[] array = new NamespaceBinding[list.size()];
        return list.toArray(array);
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////


    /**
     * Handles the attribute axis
     */
    private final class AttributeAxisIterator implements AxisIterator<NodeInfo> {

        private AxiomElementNodeWrapper element;
        private Iterator base;

        private NodeInfo current;
        private int index;

        private NodeTest nodeTest;
        private int position;

        public AttributeAxisIterator(AxiomElementNodeWrapper element, NodeTest test) {
            this.element = element;
            if (test == AnyNodeTest.getInstance() || test == NodeKindTest.ATTRIBUTE) {
                test = null;
            }
            base = ((OMElement) element.node).getAllAttributes();
            nodeTest = test;
            position = 0;
            index = 0;
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


        public NodeInfo next() {
            NodeInfo curr;
            do { // until we find a match
                curr = advance();
            } while (curr != null && nodeTest != null && (!nodeTest.matches(curr)));

            if (curr != null) {
                position++;
            }
            current = curr;
            return curr;
        }

        private NodeInfo advance() {
            if (base.hasNext()) {
                OMAttribute next = (OMAttribute) base.next();
                index++;
                return new AxiomAttributeWrapper(next, element, index);
            } else {
                return null;
            }
        }

        public NodeInfo current() {
            return current;
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
        public AxisIterator getAnother() {
            return new AttributeAxisIterator(element, nodeTest);
        }

        public int getProperties() {
            return 0;
        }

    } // end of class AttributeAxisIterator

//
//    public AxiomNodeWrapper getNextSibling() {
//        return AxiomDocumentWrapper.makeWrapper(((OMNode) node).getNextOMSibling(), docWrapper, null, -1);
//    }
//
//    public AxiomNodeWrapper getNextSiblingElement(String uri, String local) {
//        OMNode currNode = ((OMNode)node).getNextOMSibling();
//        while (currNode != null) {
//            if (currNode.getType() == OMNode.ELEMENT_NODE &&
//                    (uri == null || uri.equals(((OMElement)currNode).getNamespaceURI())) &&
//                    (local == null || local.equals(((OMElement)currNode).getLocalName()))) {
//                return AxiomDocumentWrapper.makeWrapper(currNode, docWrapper, null, -1);
//            }
//            currNode = currNode.getNextOMSibling();
//        }
//        return null;
//
//    }
//
//    public AxiomNodeWrapper getPreviousSibling() {
//        return AxiomDocumentWrapper.makeWrapper(((OMNode) node).getPreviousOMSibling(), docWrapper, null, -1);
//    }
//
//    public AxiomNodeWrapper getPreviousSiblingElement(String uri, String local) {
//        OMNode currNode = ((OMNode)node).getPreviousOMSibling();
//        while (currNode != null) {
//            if (currNode.getType() == OMNode.ELEMENT_NODE &&
//                    (uri == null || uri.equals(((OMElement)currNode).getNamespaceURI())) &&
//                    (local == null || local.equals(((OMElement)currNode).getLocalName()))) {
//                return AxiomDocumentWrapper.makeWrapper(currNode, docWrapper, null, -1);
//            }
//            currNode = currNode.getPreviousOMSibling();
//        }
//        return null;
//    }
//
//    public AxiomNodeWrapper getFirstChild() {
//        OMNode child = node.getFirstOMChild();
//        if (child == null) {
//            return null;
//        }
//        return AxiomDocumentWrapper.makeWrapper(child, docWrapper, this, 0);
//    }
//
//    public AxiomNodeWrapper getFirstChildElement(String uri, String local) {
//        OMNode child = node.getFirstOMChild();
//        if (child == null) {
//            return null;
//        }
//        while (child != null) {
//            if (child.getType() == OMNode.ELEMENT_NODE &&
//                    (uri == null || uri.equals(((OMElement)child).getNamespaceURI())) &&
//                    (local == null || local.equals(((OMElement)child).getLocalName()))) {
//                return AxiomDocumentWrapper.makeWrapper(child, docWrapper, null, -1);
//            }
//            child = child.getNextOMSibling();
//        }
//        return null;
//    }
//
//    public AxiomNodeWrapper getFollowingElement(AxiomNodeWrapper anchor, String uri, String local) {
//        Object stop = (anchor == null ? null : ((AbstractNodeWrapper)anchor).getUnderlyingNode());
//        OMNode next = (OMNode)node;
//        do {
//            next = getFollowingNode(next, stop);
//        } while (next != null &&
//                 !(next.getType() == OMNode.ELEMENT_NODE &&
//                  (uri == null || uri.equals(((OMElement)next).getNamespaceURI())) &&
//                  (local == null || local.equals(((OMElement)next).getLocalName()))));
//        if (next == null) {
//            return null;
//        } else {
//            return AxiomDocumentWrapper.makeWrapper(next, docWrapper, null, -1);
//        }
//    }

}

