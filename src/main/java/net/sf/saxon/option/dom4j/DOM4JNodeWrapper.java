////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.dom4j;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.Type;
import org.dom4j.*;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.<P>
 * This is the implementation of the NodeInfo interface used as a wrapper for DOM4J nodes.
 *
 * @author Michael H. Kay
 */

// History: this started life as the NodeWrapper for JDOM nodes; it was then modified by the
// Orbeon team to act as a wrapper for DOM4J nodes, and was shipped with the Orbeon product;
// it has now been absorbed back into Saxon.

public class DOM4JNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode, SteppingNode {

    protected Node node;
    protected short nodeKind;
    /*@Nullable*/ private DOM4JNodeWrapper parent;     // null means unknown
    protected DOM4JDocumentWrapper docWrapper;
    // Beware: with dom4j, this is an index over the result of content(), which may contain Namespace nodes
    protected int index;            // -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node   The DOM4J node to be wrapped
     * @param parent The NodeWrapper that wraps the parent of this node
     * @param index  Position of this node among its siblings
     */
    protected DOM4JNodeWrapper(Node node, DOM4JNodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM4J node
     * @param docWrapper The wrapper for the Document containing this node
     * @return The new wrapper for the supplied node
     */
    protected DOM4JNodeWrapper makeWrapper(Node node, DOM4JDocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM4J node
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the DOM4J node
     * @param index      The position of this node relative to its siblings
     * @return The new wrapper for the supplied node
     */

    protected DOM4JNodeWrapper makeWrapper(Node node, DOM4JDocumentWrapper docWrapper,
                                           DOM4JNodeWrapper parent, int index) {
        DOM4JNodeWrapper wrapper;
        Short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ELEMENT_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.ELEMENT;
                break;
            case Node.ATTRIBUTE_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.ATTRIBUTE;
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.DOCUMENT_NODE:
                return docWrapper;
            case Node.COMMENT_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.COMMENT;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.PROCESSING_INSTRUCTION;
                break;
            case Node.NAMESPACE_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.NAMESPACE;
                break;
            default:
                throw new IllegalArgumentException("Bad node type in dom4j! " + node.getClass() + " instance " + node.toString());
        }
        wrapper.docWrapper = docWrapper;
        return wrapper;
    }

    /**
     * Get the underlying DOM4J node, to implement the VirtualNode interface
     */

    public Object getUnderlyingNode() {
        return node;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document containing the node,
     *         or null if not known. Note this is not the same as the base URI: the base URI can be
     *         modified by xml:base, but the system ID cannot.
     */

    public String getSystemId() {
        return getDocumentRoot().getSystemId();
    }

    public void setSystemId(String uri) {
        getDocumentRoot().setSystemId(uri);
    }

    /**
     * Determine the relative position of this node and another node, in document order.
     * The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     *         node, or 0 if they are the same node. (In this case, isSameNode() will always
     *         return true, and the two nodes will produce the same result for generateId())
     */

    public int compareOrder(NodeInfo other) {
        return Navigator.compareOrder(this, (SiblingCountingNode) other);
    }

    public CharSequence getStringValueCS() {
        return getStringValue(node);
    }

    private static String getStringValue(Node node) {

        Short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ELEMENT_NODE:
            case Node.DOCUMENT_NODE:
                return node.getStringValue();
            case Node.ATTRIBUTE_NODE:
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
            case Node.COMMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                return node.getText();
            case Node.NAMESPACE_NODE:
                return ((Namespace) node).getURI();
            default:
                return "";
        }
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    public String getLocalPart() {
        switch (nodeKind) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return node.getName();
            case Type.TEXT:
            case Type.COMMENT:
            case Type.DOCUMENT:
                return "";
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction) node).getTarget();
            case Type.NAMESPACE:
                return ((Namespace) node).getPrefix();
            default:
                return null;
        }
    }

    /**
     * Get the prefix part of the name of this node. This is the name before the ":" if any.
     * (Note, this method isn't required as part of the NodeInfo interface.)
     *
     * @return the prefix part of the name. For an unnamed node, return an empty string.
     */

    public String getPrefix() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getNamespacePrefix();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getNamespacePrefix();
            default:
                return "";
        }
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, return null.
     *         For a node with an empty prefix, return an empty string.
     */

    public String getURI() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getNamespaceURI();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getNamespaceURI();
            default:
                return "";
        }
    }


    /**
     * Get the display name of this node. For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node.
     *         For a node with no name, return an empty string.
     */

    public String getDisplayName() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getQualifiedName();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getQualifiedName();
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return getLocalPart();
            default:
                return "";

        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    public DOM4JNodeWrapper getParent() {
        if (parent == null) {
            Branch parenti = getInternalParent(node, docWrapper);
            if (parenti != null) {
                return parent = makeWrapper(parenti, docWrapper);
            }
        }
        return parent;
    }

    private static Branch getInternalParent(Node node, DOM4JNodeWrapper container) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return null;
        }
        Element e = node.getParent();
        if (e != null) {
            return e;
        }
        Document d = node.getDocument();
        if (d != null) {
            return d;
        }
        return DOM4JDocumentWrapper.searchForParent((Branch)container.node, node);
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */
    public int getSiblingPosition() {
        if (index == -1) {
            int ix = 0;
            getParent();
            AxisIterator iter;
            switch (nodeKind) {
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
//                    iter = parent.iterateAxis(Axis.ATTRIBUTE);
//                    break;
                {
                    final DOM4JNodeWrapper parent = getParent();
                    final List children;
                    if (parent.getNodeKind() == Type.DOCUMENT) {
                        children = ((Document) parent.node).content();
                    } else {
                        // Beware: dom4j content() contains Namespace nodes (which is broken)!
                        children = ((Element) parent.node).content();
                    }
                    for (final Object n : children) {
                        if (n == node) {
                            index = ix;
                            return index;
                        }
                        ix++;
                    }
                    throw new IllegalStateException("DOM4J node not linked to parent node");
                }
                case Type.ATTRIBUTE:
                    iter = parent.iterateAxis(AxisInfo.ATTRIBUTE);
                    break;
                case Type.NAMESPACE:
                    iter = parent.iterateAxis(AxisInfo.NAMESPACE);
                    break;
                default:
                    index = 0;
                    return index;
            }
            while (true) {
                NodeInfo n = iter.next();
                if (n == null) {
                    break;
                }
                if (n.isSameNodeInfo(this)) {
                    index = ix;
                    return index;
                }
                ix++;
            }
            throw new IllegalStateException("DOM4J node not linked to parent node");
        }
        return index;
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     * <p/>
     * //     * @param axisNumber the axis to be used
     *
     * @param nodeTest A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

//    public AxisIterator iterateAxis(byte axisNumber, NodeTest nodeTest) {
//        switch (axisNumber) {
//            case AxisInfo.ANCESTOR:
//                if (nodeKind == Type.DOCUMENT) {
//                    return EmptyAxisIterator.emptyAxisIterator();
//                }
//                return new Navigator.AxisFilter(
//                        new Navigator.AncestorEnumeration(this, false),
//                        nodeTest);
//
//            case AxisInfo.ANCESTOR_OR_SELF:
//                if (nodeKind == Type.DOCUMENT) {
//                    return Navigator.filteredSingleton(this, nodeTest);
//                }
//                return new Navigator.AxisFilter(
//                        new Navigator.AncestorEnumeration(this, true),
//                        nodeTest);
//
//            case AxisInfo.ATTRIBUTE:
//                if (nodeKind != Type.ELEMENT) {
//                    return EmptyAxisIterator.emptyAxisIterator();
//                }
//                return new Navigator.AxisFilter(
//                        new AttributeEnumeration(this),
//                        nodeTest);
//
//            case AxisInfo.CHILD:
//                if (hasChildNodes()) {
//                    return new Navigator.AxisFilter(
//                            new ChildEnumeration(this, true, true),
//                            nodeTest);
//                } else {
//                    return EmptyAxisIterator.emptyAxisIterator();
//                }
//
//            case AxisInfo.DESCENDANT:
//                if (hasChildNodes()) {
//                    return new Navigator.AxisFilter(
//                            (new TreeWalker()).newDescendantAxisIterator(this, false, nodeTest),//new Navigator.DescendantEnumeration(this, false, true),
//                            nodeTest);
//                } else {
//                    return EmptyAxisIterator.emptyAxisIterator();
//                }
//
//            case AxisInfo.DESCENDANT_OR_SELF:
//                return new Navigator.AxisFilter(
//                        (new TreeWalker()).newDescendantAxisIterator(this, true, nodeTest),//new Navigator.DescendantEnumeration(this, true, true),
//                        nodeTest);
//
//            case AxisInfo.FOLLOWING:
//                return new Navigator.AxisFilter(
//                        new Navigator.FollowingEnumeration(this),
//                        nodeTest);
//
//            case AxisInfo.FOLLOWING_SIBLING:
//                switch (nodeKind) {
//                    case Type.DOCUMENT:
//                    case Type.ATTRIBUTE:
//                    case Type.NAMESPACE:
//                        return EmptyAxisIterator.emptyAxisIterator();
//                    default:
//                        return new Navigator.AxisFilter(
//                                new ChildEnumeration(this, false, true),
//                                nodeTest);
//                }
//
//            case AxisInfo.NAMESPACE:
//                if (nodeKind != Type.ELEMENT) {
//                    return EmptyAxisIterator.emptyAxisIterator();
//                }
//                return NamespaceNode.makeIterator(this, nodeTest);
//
//            case AxisInfo.PARENT:
//                getParent();
//                return Navigator.filteredSingleton(parent, nodeTest);
//
//            case AxisInfo.PRECEDING:
//                return new Navigator.AxisFilter(
//                        new Navigator.PrecedingEnumeration(this, false),
//                        nodeTest);
//
//            case AxisInfo.PRECEDING_SIBLING:
//                switch (nodeKind) {
//                    case Type.DOCUMENT:
//                    case Type.ATTRIBUTE:
//                    case Type.NAMESPACE:
//                        return EmptyAxisIterator.emptyAxisIterator();
//                    default:
//                        return new Navigator.AxisFilter(
//                                new ChildEnumeration(this, false, false),
//                                nodeTest);
//                }
//
//            case AxisInfo.SELF:
//                return Navigator.filteredSingleton(this, nodeTest);
//
//            case AxisInfo.PRECEDING_OR_ANCESTOR:
//                return new Navigator.AxisFilter(
//                        new Navigator.PrecedingEnumeration(this, true),
//                        nodeTest);
//
//            default:
//                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
//        }
//    }
    @Override
    protected AxisIterator<NodeInfo> iterateAttributes(NodeTest nodeTest) {
        return new Navigator.AxisFilter(
                new AttributeEnumeration(this),
                nodeTest);
    }

    @Override
    protected AxisIterator<NodeInfo> iterateChildren(NodeTest nodeTest) {
        if (hasChildNodes()) {
            return new Navigator.AxisFilter(
                    new ChildEnumeration(this, true, true),
                    nodeTest);
        } else {
            return EmptyAxisIterator.emptyAxisIterator();
        }
    }

    @Override
    protected AxisIterator<NodeInfo> iterateSiblings(NodeTest nodeTest, boolean forwards) {
        return new Navigator.AxisFilter(
                new ChildEnumeration(this, false, forwards),
                nodeTest);
    }

    @Override
    protected AxisIterator<NodeInfo> iterateDescendants(NodeTest nodeTest, boolean includeSelf) {
        if (includeSelf) {
            return new SteppingNavigator.DescendantAxisIterator(this, true, nodeTest);

        } else {
            if (hasChildNodes()) {
                return new SteppingNavigator.DescendantAxisIterator(this, false, nodeTest);
            } else {
                return EmptyAxisIterator.emptyAxisIterator();
            }

        }
    }

    /**
     * Get the value of a given attribute of this node
     *
     * @param fingerprint The fingerprint of the attribute name
     * @return the attribute value if it exists or null if not
     */

    public String getAttributeValue(int fingerprint) {
        if (nodeKind == Type.ELEMENT) {
            Iterator list = ((Element) node).attributes().iterator();
            NamePool pool = docWrapper.getNamePool();
            while (list.hasNext()) {
                Attribute att = (Attribute) list.next();
                int nameCode = pool.allocate(att.getNamespacePrefix(),
                        att.getNamespaceURI(),
                        att.getName());
                if (fingerprint == (nameCode & 0xfffff)) {
                    return att.getValue();
                }
            }
        }
        return null;
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
        if (nodeKind == Type.ELEMENT) {
            for (Object o : ((Element) node).attributes()) {
                Attribute att = (Attribute) o;
                if (att.getName().equals(local) && att.getNamespaceURI().equals(uri)) {
                    return att.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Get the root node - always a document node with this tree implementation
     *
     * @return the NodeInfo representing the containing document
     */

    public NodeInfo getRoot() {
        return docWrapper;
    }

    /**
     * Get the root (document) node
     *
     * @return the DocumentInfo representing the containing document
     */

    public DocumentInfo getDocumentRoot() {
        return docWrapper;
    }

    /**
     * Determine whether the node has any children. <br />
     * Note: the result is equivalent to <br />
     * getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()
     */

    public boolean hasChildNodes() {
        switch (nodeKind) {
            case Type.DOCUMENT:
                return true;
            case Type.ELEMENT:
                // Beware: dom4j content() contains Namespace nodes (which is broken)!
                return ((Branch) node).nodeCount() > 0;
            default:
                return false;
        }
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a Buffer to contain a string that uniquely identifies this node, across all
     *               documents
     */

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
        //buffer.append(Navigator.getSequentialKey(this));
    }

    /**
     * Get the document number of the document containing this node. For a free-standing
     * orphan node, just return the hashcode.
     */

    public long getDocumentNumber() {
        return getParent().getDocumentNumber();
    }

    /**
     * Copy this node to a given outputter (deep copy)
     */

    public void copy(Receiver out, int copyOptions, int locationId) throws XPathException {
        Navigator.copy(this, out, copyOptions, locationId);
    }

    public DOM4JNodeWrapper getNextSibling() {
        Branch parenti = (Branch)getParent().node;
        int count = parenti.nodeCount();
        int i = parenti.indexOf(node);
        i++;
        if (i < count) {
            return makeWrapper(parenti.node(i), docWrapper);
        }
        return null;
    }


    public DOM4JNodeWrapper getPreviousSibling() {
        Branch parenti = (Branch)getParent().node;
        int i = parenti.indexOf(node);
        i--;
        if (i >= 0) {
            return makeWrapper(parenti.node(i), docWrapper);
        }
        return null;
    }


    public DOM4JNodeWrapper getFirstChild() {
        Node nodei = node;
        if (nodei.hasContent()) {
            Node child;
            int count = ((Branch) nodei).nodeCount();
            for (int i = 0; i < count; i++) {
                child = ((Branch) nodei).node(i);
                if (child.getNodeType() != Node.NAMESPACE_NODE && child.getNodeType() != Node.ATTRIBUTE_NODE) {
                    return makeWrapper(child, docWrapper, this, 0);
                }
            }
        }
        return null;
    }

    public SteppingNode getSuccessorElement(SteppingNode anchor, String uri, String local) {
        Node stop = (anchor == null ? null : ((DOM4JNodeWrapper) anchor).node);
        Node next = node;
        do {
            next = getFollowingNode(next, stop, docWrapper);
        } while (next != null &&
                !(next.getNodeType() == Node.ELEMENT_NODE &&
                        (uri == null || uri.equals(((Element) next).getNamespaceURI())) &&
                        (local == null || local.equals(next.getName()))));
        if (next == null) {
            return null;
        } else {
            return makeWrapper(next, docWrapper);
        }
    }

    /**
     * Get the following node in an iteration of descendants
     *
     * @param start  the start node
     * @param anchor the node marking the root of the subtree within which navigation takes place (may be null)
     * @return the next node in document order after the start node, excluding attributes and namespaces
     */

    private static Node getFollowingNode(Node start, Node anchor, DOM4JDocumentWrapper container) {
        if (start.hasContent()) {
            Node child;
            int count = ((Branch) start).nodeCount();
            for (int i = 0; i < count; i++) {
                child = ((Branch) start).node(i);
                if (child.getNodeType() != Node.NAMESPACE_NODE && child.getNodeType() != Node.ATTRIBUTE_NODE) {
                    return child;
                }
            }
        }
        if (start == anchor) {
            return null;
        }
        Node p = start;
        while (true) {
            Branch q = getInternalParent(p, container);
            if (q == null) {
                return null;
            }
            int i = q.indexOf(p) + 1;   // TODO: inefficient if a node has a large number of children
            if (i < q.nodeCount()) {
                return q.node(i);
            }
            if (q == anchor) {
                return null;
            }
            p = q;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////


    private final class AttributeEnumeration extends Navigator.BaseEnumeration {

        private Iterator<Node> atts;
        private int ix = 0;
        private DOM4JNodeWrapper start;

        public AttributeEnumeration(DOM4JNodeWrapper start) {
            this.start = start;
            atts = ((Element) start.node).attributes().iterator();
        }

        public void advance() {
            if (atts.hasNext()) {
                current = makeWrapper(atts.next(), docWrapper, start, ix++);
            } else {
                current = null;
            }
        }

        /*@NotNull*/
        public AxisIterator getAnother() {
            return new AttributeEnumeration(start);
        }

    }  // end of class AttributeEnumeration


    /**
     * The class ChildEnumeration handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the children
     * of the start node in reverse order, something that is needed to support the
     * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
     */

    private final class ChildEnumeration extends Navigator.BaseEnumeration {

        private DOM4JNodeWrapper start;
        private DOM4JNodeWrapper commonParent;
        private ListIterator<Node> children;
        private int ix = 0;
        private boolean downwards;  // iterate children of start node (not siblings)
        private boolean forwards;   // iterate in document order (not reverse order)

        public ChildEnumeration(DOM4JNodeWrapper start,
                                boolean downwards, boolean forwards) {
            this.start = start;
            this.downwards = downwards;
            this.forwards = forwards;

            if (downwards) {
                commonParent = start;
            } else {
                commonParent = start.getParent();
            }

            if (commonParent.getNodeKind() == Type.DOCUMENT) {
                children = ((Document) commonParent.node).content().listIterator();
            } else {
                children = ((Element) commonParent.node).content().listIterator();
            }

            if (downwards) {
                if (!forwards) {
                    // backwards enumeration: go to the end
                    while (children.hasNext()) {
                        children.next();
                        ix++;
                    }
                }
            } else {
                ix = start.getSiblingPosition();
                // find the start node among the list of siblings
                if (forwards) {
                    for (int i = 0; i <= ix; i++) {
                        children.next();
                    }
                    ix++;
                } else {
                    for (int i = 0; i < ix; i++) {
                        children.next();
                    }
                    ix--;
                }
            }
        }


        public void advance() {
            if (forwards) {
                if (children.hasNext()) {
                    Node nextChild = children.next();
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix++; // increment anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
                    } else {
                        current = makeWrapper(nextChild, docWrapper, commonParent, ix++);
                    }
                } else {
                    current = null;
                }
            } else {    // backwards
                if (children.hasPrevious()) {
                    Node nextChild = children.previous();
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix--; // decrement anyway so that makeWrapper() passes the correct index)
                        advance();
                        return;
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
                    } else {
                        current = makeWrapper(nextChild, docWrapper, commonParent, ix--);
                    }
                } else {
                    current = null;
                }
            }
        }

        /*@NotNull*/
        public AxisIterator getAnother() {
            return new ChildEnumeration(start, downwards, forwards);
        }

    } // end of class ChildEnumeration


    /**
     * Determine whether this is the same node as another node.
     * Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).
     * This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     */

    public boolean isSameNodeInfo(NodeInfo other) {
        if (!(other instanceof DOM4JNodeWrapper)) {
            return false;
        }
        DOM4JNodeWrapper ow = (DOM4JNodeWrapper) other;
        if (node instanceof Namespace) {
            return getLocalPart().equals(ow.getLocalPart()) && getParent().isSameNodeInfo(ow.getParent());
        }
        return node.equals(ow.node);
    }

    public Configuration getConfiguration() {
        return docWrapper.getConfiguration();
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
        if (node instanceof Element) {
            final Element elem = (Element) node;
            final List namespaces = elem.declaredNamespaces();

            if (namespaces == null || namespaces.isEmpty()) {
                return NamespaceBinding.EMPTY_ARRAY;
            }
            final int count = namespaces.size();
            if (count == 0) {
                return NamespaceBinding.EMPTY_ARRAY;
            } else {
                NamespaceBinding[] result = (buffer == null || count > buffer.length ? new NamespaceBinding[count] : buffer);
                int n = 0;
                for (Iterator i = namespaces.iterator(); i.hasNext(); ) {
                    final Namespace namespace = (Namespace) i.next();
                    final String prefix = namespace.getPrefix();
                    final String uri = namespace.getURI();

                    result[n++] = new NamespaceBinding(prefix, uri);
                }
                if (count < result.length) {
                    result[count] = null;
                }
                return result;
            }
        } else {
            return null;
        }
    }

}

