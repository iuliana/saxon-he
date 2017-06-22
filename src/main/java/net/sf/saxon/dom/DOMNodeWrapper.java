////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorImpl;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.Type;
import org.w3c.dom.*;

import java.util.ArrayList;


/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.<P>
 * This is the implementation of the NodeInfo interface used as a wrapper for DOM nodes.
 *
 * <p>Because the DOM is not thread-safe even when reading, and because Saxon-EE can spawn multiple
 * threads that access the same input tree, all methods that invoke DOM methods are synchronized
 * on the DocumentWrapper object. (This still relies on the user not allocating two DocumentWrappers
 * around the same DOM).</p>
 */

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class DOMNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode, SteppingNode {

    protected Node node;
    private int namecode = -1;
    protected short nodeKind;
    private DOMNodeWrapper parent;     // null means unknown
    protected DocumentWrapper docWrapper; // effectively final
    protected int index;            // -1 means unknown
    protected int span = 1;         // the number of adjacent text nodes wrapped by this NodeWrapper.
    // If span>1, node will always be the first of a sequence of adjacent text nodes
    private NamespaceBinding[] localNamespaces = null;

    /**
     * This constructor is protected: nodes should be created using the makeWrapper
     * factory method
     *
     * @param node   The DOM node to be wrapped
     * @param docWrapper The wrapper for the Document node at the root of the DOM tree. Never null
     * except in the case where we are creating the DocumentWrapper itself (which is a subclass).
     * @param parent The DOMNodeWrapper that wraps the parent of this node. May be null if unknown.
     * @param index  Position of this node among its siblings, 0-based. May be -1 if unknown.
     */
    protected DOMNodeWrapper(Node node, DocumentWrapper docWrapper, /*@Nullable*/ DOMNodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
        this.docWrapper = docWrapper;
    }

    /**
     * Factory method to wrap a DOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM node
     * @param docWrapper The wrapper for the containing Document node
     * @return The new wrapper for the supplied node
     * @throws NullPointerException if the node or the document wrapper are null
     */
    protected DOMNodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper) {
        if (node == null) {
            throw new NullPointerException("NodeWrapper#makeWrapper: Node must not be null");
        }
        if (docWrapper == null) {
            throw new NullPointerException("NodeWrapper#makeWrapper: DocumentWrapper must not be null");
        }
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a DOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM node
     * @param docWrapper The wrapper for the containing Document node     *
     * @param parent     The wrapper for the parent of the JDOM node
     * @param index      The position of this node relative to its siblings
     * @return The new wrapper for the supplied node
     */

    protected DOMNodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper,
                                         /*@Nullable*/ DOMNodeWrapper parent, int index) {
        DOMNodeWrapper wrapper;
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                return docWrapper;
            case Node.ELEMENT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.ELEMENT;
                break;
            case Node.ATTRIBUTE_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.ATTRIBUTE;
                break;
            case Node.TEXT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.CDATA_SECTION_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.COMMENT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.COMMENT;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.PROCESSING_INSTRUCTION;
                break;
            case Node.ENTITY_REFERENCE_NODE:
                throw new IllegalStateException("DOM contains entity reference nodes, which Saxon does not support. " +
                        "The DOM should be built using the expandEntityReferences() option");
            default:
                throw new IllegalArgumentException("Unsupported node type in DOM! " + node.getNodeType() + " instance " + node.toString());
        }
        return wrapper;
    }

    /**
     * Get the underlying DOM node, to implement the VirtualNode interface
     */

    public Object getUnderlyingNode() {
        return node;
    }

    /**
     * Return the kind of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Determine whether this is the same node as another node. <br />
     * Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b)
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean isSameNodeInfo(NodeInfo other) {
        if (!(other instanceof DOMNodeWrapper)) {
            return false;
        }
        if (docWrapper.domLevel3) {
            synchronized (docWrapper) {
                return node.isSameNode(((DOMNodeWrapper) other).node);
            }
        } else {
            DOMNodeWrapper ow = (DOMNodeWrapper) other;
            return getNodeKind() == ow.getNodeKind() &&
                    getNameCode() == ow.getNameCode() &&  // redundant, but gives a quick exit
                    getSiblingPosition() == ow.getSiblingPosition() &&
                    getParent().isSameNodeInfo(ow.getParent());
        }
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
        // Use the DOM Level-3 compareDocumentPosition() method
        if (other instanceof DOMNodeWrapper && docWrapper.domLevel3) {
            if (isSameNodeInfo(other)) {
                return 0;
            }
            try {
                synchronized (docWrapper) {
                    short relationship = node.compareDocumentPosition(((DOMNodeWrapper) other).node);
                    if ((relationship &
                            (Node.DOCUMENT_POSITION_PRECEDING | Node.DOCUMENT_POSITION_CONTAINS)) != 0) {
                        return +1;
                    } else if ((relationship &
                            (Node.DOCUMENT_POSITION_FOLLOWING | Node.DOCUMENT_POSITION_CONTAINED_BY)) != 0) {
                        return -1;
                    }
                }
                // otherwise use fallback implementation (e.g. nodes in different documents)
            } catch (DOMException e) {
                // can happen if nodes are from different DOM implementations.
                // use fallback implementation
            }
        }

        if (other instanceof SiblingCountingNode) {
            return Navigator.compareOrder(this, (SiblingCountingNode) other);
        } else {
            // it's presumably a Namespace Node
            return -other.compareOrder(this);
        }
    }

    /**
     * Determine the relative position of this node and another node, in document order,
     * distinguishing whether the first node is a preceding, following, descendant, ancestor,
     * or the same node as the second.
     * <p/>
     * The other node must always be in the same tree; the effect of calling this method
     * when the two nodes are in different trees is undefined. If either node is a namespace
     * or attribute node, the method should throw UnsupportedOperationException.
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return {@link net.sf.saxon.om.AxisInfo#PRECEDING} if this node is on the preceding axis of the other node;
     *         {@link net.sf.saxon.om.AxisInfo#FOLLOWING} if it is on the following axis; {@link net.sf.saxon.om.AxisInfo#ANCESTOR} if the first node is an
     *         ancestor of the second; {@link net.sf.saxon.om.AxisInfo#DESCENDANT} if the first is a descendant of the second;
     *         {@link net.sf.saxon.om.AxisInfo#SELF} if they are the same node.
     * @throws UnsupportedOperationException if either node is an attribute or namespace
     * @since 9.5
     */
    public int comparePosition(NodeInfo other) {
// Use the DOM Level-3 compareDocumentPosition() method
        if (other instanceof DOMNodeWrapper && docWrapper.domLevel3) {
            if (isSameNodeInfo(other)) {
                return AxisInfo.SELF;
            }
            try {
                synchronized (docWrapper) {
                    short relationship = node.compareDocumentPosition(((DOMNodeWrapper) other).node);
                    if ((relationship & Node.DOCUMENT_POSITION_PRECEDING) != 0) {
                        return AxisInfo.FOLLOWING;
                    } else if ((relationship & Node.DOCUMENT_POSITION_FOLLOWING) != 0) {
                        return AxisInfo.PRECEDING;
                    } else if ((relationship & Node.DOCUMENT_POSITION_CONTAINS) != 0) {
                        return AxisInfo.ANCESTOR;
                    } else if ((relationship & Node.DOCUMENT_POSITION_CONTAINED_BY) != 0) {
                        return AxisInfo.DESCENDANT;
                    }
                }
                // otherwise use fallback implementation (e.g. nodes in different documents)
            } catch (DOMException e) {
                //
            }
        }
        return Navigator.comparePosition(this, other);
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public CharSequence getStringValueCS() {
        synchronized (docWrapper) {
            switch (nodeKind) {
                case Type.DOCUMENT:
                case Type.ELEMENT:
                    NodeList children1 = node.getChildNodes();
                    FastStringBuffer sb1 = new FastStringBuffer(16);
                    expandStringValue(children1, sb1);
                    return sb1;

                case Type.ATTRIBUTE:
                    return emptyIfNull(((Attr) node).getValue());

                case Type.TEXT:
                    if (span == 1) {
                        return emptyIfNull(node.getNodeValue());
                    } else {
                        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
                        Node textNode = node;
                        for (int i = 0; i < span; i++) {
                            fsb.append(emptyIfNull(textNode.getNodeValue()));
                            textNode = textNode.getNextSibling();
                        }
                        return fsb.condense();
                    }

                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    return emptyIfNull(node.getNodeValue());

                default:
                    return "";
            }
        }
    }

    /**
     * Treat a node value of null as an empty string.
     *
     * @param s the node value
     * @return a zero-length string if s is null, otherwise s
     */

    private static String emptyIfNull(String s) {
        return (s == null ? "" : s);
    }

    private static void expandStringValue(NodeList list, FastStringBuffer sb) {
        final int len = list.getLength();
        for (int i = 0; i < len; i++) {
            Node child = list.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    expandStringValue(child.getChildNodes(), sb);
                    break;
                case Node.COMMENT_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE:
                    break;
                case Node.DOCUMENT_TYPE_NODE:
                    break;
                default:
                    sb.append(child.getNodeValue());
            }
        }
    }

    /**
     * Get name code. The name code is a coded form of the node name: two nodes
     * with the same name code have the same namespace URI, the same local name,
     * and the same prefix. By masking the name code with &0xfffff, you get a
     * fingerprint: two nodes with the same fingerprint have the same local name
     * and namespace URI.
     *
     * @see NamePool#allocate allocate
     */

    public int getNameCode() {
        if (namecode != -1) {
            // this is a memo function
            return namecode;
        }
        int nodeKind = getNodeKind();
        if (nodeKind == Type.ELEMENT || nodeKind == Type.ATTRIBUTE) {
            String prefix = getPrefix();
            if (prefix == null) {
                prefix = "";
            }
            namecode = docWrapper.getNamePool().allocate(prefix, getURI(), getLocalPart());
            return namecode;
        } else if (nodeKind == Type.PROCESSING_INSTRUCTION) {
            namecode = docWrapper.getNamePool().allocate("", "", getLocalPart());
            return namecode;
        } else {
            return -1;
        }
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns null, except for
     *         un unnamed namespace node, which returns "".
     */

    public String getLocalPart() {
        synchronized (docWrapper) {
            switch (getNodeKind()) {
                case Type.ELEMENT:
                case Type.ATTRIBUTE:
                    return getLocalName(node);
                case Type.PROCESSING_INSTRUCTION:
                    return node.getNodeName();
                default:
                    return null;
            }
        }
    }

    /**
     * Get the local name of a DOM element or attribute node.
     * @param node the DOM element or attribute node
     * @return the local name as defined in XDM
     */

    public static String getLocalName(Node node) {
        String s = node.getLocalName();
        if (s == null) {
            // true if the node was created using a DOM level 1 method
            String n = node.getNodeName();
            int colon = n.indexOf(':');
            if (colon >= 0) {
                return n.substring(colon + 1);
            }
            return n;
        } else {
            return s;
        }
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node,
     *         or for a node with an empty prefix, return an empty
     *         string.
     */

    public String getURI() {
        synchronized (docWrapper) {
            if (nodeKind == Type.ELEMENT) {
                return getElementURI((Element) node);
            } else if (nodeKind == Type.ATTRIBUTE) {
                return getAttributeURI((Attr) node);
            }
            return "";
        }
    }

    public static String getElementURI(Element element) {

        // The DOM methods getPrefix() and getNamespaceURI() do not always
        // return the prefix and the URI; they both return null, unless the
        // prefix and URI have been explicitly set in the node by using DOM
        // level 2 interfaces. There's no obvious way of deciding whether
        // an element whose name has no prefix is in the default namespace,
        // other than searching for a default namespace declaration. So we have to
        // be prepared to search.

        // If getPrefix() and getNamespaceURI() are non-null, however,
        // we can use the values.

        String uri = element.getNamespaceURI();
        if (uri != null) {
            return uri;
        }

        // Otherwise we have to work it out the hard way...

        String displayName = element.getNodeName();
        int colon = displayName.indexOf(':');
        String attName = (colon < 0 ? "xmlns" : "xmlns:" + displayName.substring(0, colon));

        if (attName.equals("xmlns:xml")) {
            return NamespaceConstant.XML;
        }

        Node node = element;
        do {
            if (((Element)node).hasAttribute(attName)) {
                return ((Element) node).getAttribute(attName);
            }
            node = node.getParentNode();
        } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

        if (colon < 0) {
            return "";
        } else {
            throw new IllegalStateException("Undeclared namespace prefix in element name " + displayName + " in DOM input");
        }

    }


    public static String getAttributeURI(Attr attr) {

        String uri = attr.getNamespaceURI();
        if (uri != null) {
            return uri;
        }

        // Otherwise we have to work it out the hard way...

        String displayName = attr.getNodeName();
        int colon = displayName.indexOf(':');
        if (colon < 0) {
            return "";
        }
        String attName = "xmlns:" + displayName.substring(0, colon);

        if (attName.equals("xmlns:xml")) {
            return NamespaceConstant.XML;
        }

        Node node = attr.getOwnerElement();
        do {
            if (((Element) node).hasAttribute(attName)) {
                return ((Element) node).getAttribute(attName);
            }
            node = node.getParentNode();
        } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

        throw new IllegalStateException("Undeclared namespace prefix in attribute name " + displayName + " in DOM input");

    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    public String getPrefix() {
        synchronized (docWrapper) {
            int kind = getNodeKind();
            if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
                String name = node.getNodeName();
                int colon = name.indexOf(':');
                if (colon < 0) {
                    return "";
                } else {
                    return name.substring(0, colon);
                }
            }
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
            case Type.ATTRIBUTE:
            case Type.PROCESSING_INSTRUCTION:
                synchronized (docWrapper) {
                    return node.getNodeName();
                }
            default:
                return "";

        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    public DOMNodeWrapper getParent() {
        if (parent == null) {
            synchronized (docWrapper) {
                switch (getNodeKind()) {
                    case Type.ATTRIBUTE:
                        parent = makeWrapper(((Attr) node).getOwnerElement(), docWrapper);
                        break;
                    default:
                        Node p = node.getParentNode();
                        if (p == null) {
                            return null;
                        } else {
                            parent = makeWrapper(p, docWrapper);
                        }
                }
            }
        }
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0).
     * In the case of a text node that maps to several adjacent siblings in the DOM,
     * the numbering actually refers to the position of the underlying DOM nodes;
     * thus the sibling position for the text node is that of the first DOM node
     * to which it relates, and the numbering of subsequent XPath nodes is not necessarily
     * consecutive.
     *
     * <p>Despite the name, this method also returns a meaningful result for attribute
     * nodes; it returns the position of the attribute among the attributes of its
     * parent element, when they are listed in document order.</p>
     */

    public int getSiblingPosition() {
        if (index == -1) {
            synchronized (docWrapper) {
                switch (nodeKind) {
                    case Type.ELEMENT:
                    case Type.TEXT:
                    case Type.COMMENT:
                    case Type.PROCESSING_INSTRUCTION:
                        int ix = 0;
                        Node start = node;
                        while (true) {
                            start = start.getPreviousSibling();
                            if (start == null) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }
                    case Type.ATTRIBUTE:
                        ix = 0;
                        int fp = getFingerprint();
                        AxisIterator iter = parent.iterateAxis(AxisInfo.ATTRIBUTE);
                        while (true) {
                            NodeInfo n = iter.next();
                            if (n == null || n.getFingerprint() == fp) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }

                    case Type.NAMESPACE:
                        ix = 0;
                        fp = getFingerprint();
                        iter = parent.iterateAxis(AxisInfo.NAMESPACE);
                        while (true) {
                            NodeInfo n = iter.next();
                            if (n == null || n.getFingerprint() == fp) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }
                    default:
                        index = 0;
                        return index;
                }
            }
        }
        return index;
    }

    @Override
    protected AxisIterator<NodeInfo> iterateAttributes(NodeTest nodeTest) {
        AxisIterator<NodeInfo> iter = new AttributeEnumeration(this);
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    @Override
    protected AxisIterator<NodeInfo> iterateChildren(NodeTest nodeTest) {
        boolean elementOnly = nodeTest.getNodeKindMask() == 1 << Type.ELEMENT;
        AxisIterator<NodeInfo> iter = new Navigator.EmptyTextFilter(
                new ChildEnumeration(this, true, true, elementOnly));
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    @Override
    protected AxisIterator<NodeInfo> iterateSiblings(NodeTest nodeTest, boolean forwards) {
        boolean elementOnly = nodeTest.getNodeKindMask() == 1 << Type.ELEMENT;
        AxisIterator<NodeInfo> iter = new Navigator.EmptyTextFilter(
                new ChildEnumeration(this, false, forwards, elementOnly));
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    @Override
    protected AxisIterator<NodeInfo> iterateDescendants(NodeTest nodeTest, boolean includeSelf) {
        return new SteppingNavigator.DescendantAxisIterator(this, includeSelf, nodeTest);
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
        NameTest test = new NameTest(Type.ATTRIBUTE, uri, local, getNamePool());
        AxisIterator iterator = iterateAxis(AxisInfo.ATTRIBUTE, test);
        NodeInfo attribute = iterator.next();
        if (attribute == null) {
            return null;
        } else {
            return attribute.getStringValue();
        }
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
        // An attribute node has child text nodes
        synchronized (docWrapper) {
            return node.getNodeType() != Node.ATTRIBUTE_NODE && node.hasChildNodes();
        }
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all
     *               documents
     */

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
    }

    /**
     * Get the document number of the document containing this node. For a free-standing
     * orphan node, just return the hashcode.
     */

    public long getDocumentNumber() {
        return getDocumentRoot().getDocumentNumber();
    }

    /**
     * Copy this node to a given outputter (deep copy)
     */

    public void copy(Receiver out, int copyOptions, int locationId) throws XPathException {
        Navigator.copy(this, out, copyOptions, locationId);
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
        synchronized (docWrapper) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (localNamespaces != null) {
                    return localNamespaces;
                }
                Element elem = (Element) node;
                NamedNodeMap atts = elem.getAttributes();

                if (atts == null) {
                    localNamespaces = NamespaceBinding.EMPTY_ARRAY;
                    return NamespaceBinding.EMPTY_ARRAY;
                }
                int count = 0;
                final int attsLen = atts.getLength();
                for (int i = 0; i < attsLen; i++) {
                    Attr att = (Attr) atts.item(i);
                    String attName = att.getName();
                    if (attName.equals("xmlns")) {
                        count++;
                    } else if (attName.startsWith("xmlns:")) {
                        count++;
                    }
                }
                if (count == 0) {
                    localNamespaces = NamespaceBinding.EMPTY_ARRAY;
                    return NamespaceBinding.EMPTY_ARRAY;
                } else {
                    NamespaceBinding[] result = (buffer == null || count > buffer.length ? new NamespaceBinding[count] : buffer);
                    int n = 0;
                    for (int i = 0; i < attsLen; i++) {
                        Attr att = (Attr) atts.item(i);
                        String attName = att.getName();
                        if (attName.equals("xmlns")) {
                            String prefix = "";
                            String uri = att.getValue();
                            result[n++] = new NamespaceBinding(prefix, uri);
                        } else if (attName.startsWith("xmlns:")) {
                            String prefix = attName.substring(6);
                            String uri = att.getValue();
                            result[n++] = new NamespaceBinding(prefix, uri);
                        }
                    }
                    if (count < result.length) {
                        result[count] = null;
                    }
                    localNamespaces = new NamespaceBinding[result.length];
                    System.arraycopy(result, 0, localNamespaces, 0, result.length);
                    return result;
                }
            } else {
                return null;
            }
        }
    }


    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        synchronized (docWrapper) {
            return (node instanceof Attr) && ((Attr) node).isId();
        }
    }

    public DOMNodeWrapper getNextSibling() {
        synchronized (docWrapper) {
            Node currNode = node.getNextSibling();
            if (currNode != null) {
                if (currNode.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    currNode = currNode.getNextSibling();
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }


    public DOMNodeWrapper getFirstChild() {
        synchronized (docWrapper) {
            Node currNode = node.getFirstChild();
            if (currNode != null) {
                if (currNode.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    currNode = currNode.getNextSibling();
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }

    public DOMNodeWrapper getPreviousSibling() {
        synchronized (docWrapper) {
            Node currNode = node.getPreviousSibling();
            if (currNode != null) {
                if (currNode.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    return null;
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }

    public SteppingNode getSuccessorElement(SteppingNode anchor, String uri, String local) {
        synchronized (docWrapper) {
            Node stop = (anchor == null ? null : ((DOMNodeWrapper) anchor).node);
            Node next = node;
            do {
                next = getSuccessorNode(next, stop);
            } while (next != null &&
                    !(next.getNodeType() == Node.ELEMENT_NODE &&
                            (local == null || local.equals(getLocalName(next))) &&
                            (uri == null || uri.equals(getElementURI((Element) next)))));
            if (next == null) {
                return null;
            } else {
                return makeWrapper(next, docWrapper);
            }
        }
    }

    /**
     * Get the following DOM node in an iteration of a subtree
     *
     * @param start  the start DOM node
     * @param anchor the DOM node marking the root of the subtree within which navigation takes place (may be null)
     * @return the next DOM node in document order after the start node, excluding attributes and namespaces
     */

    private static Node getSuccessorNode(Node start, Node anchor) {
        if (start.hasChildNodes()) {
            return start.getFirstChild();
        }
        if ((anchor != null && start.isSameNode(anchor))) {
            return null;
        }
        Node p = start;
        while (true) {
            Node s = p.getNextSibling();
            if (s != null) {
                return s;
            }
            p = p.getParentNode();
            if (p == null || (anchor != null && p.isSameNode(anchor))) {
                return null;
            }
        }
    }

    private final class AttributeEnumeration implements AxisIterator<NodeInfo>, LookaheadIterator<NodeInfo> {

        private ArrayList<Node> attList = new ArrayList<Node>(10);
        private int ix = 0;
        private DOMNodeWrapper start;
        private DOMNodeWrapper current;

        public AttributeEnumeration(DOMNodeWrapper start) {
            synchronized (start.docWrapper) {
                this.start = start;
                NamedNodeMap atts = start.node.getAttributes();
                if (atts != null) {
                    final int attsLen = atts.getLength();
                    for (int i = 0; i < attsLen; i++) {
                        String name = atts.item(i).getNodeName();
                        if (!(name.startsWith("xmlns") &&
                                (name.length() == 5 || name.charAt(5) == ':'))) {
                            attList.add(atts.item(i));
                        }
                    }
                }
                ix = 0;
            }
        }

        public boolean hasNext() {
            return ix < attList.size();
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
            if (ix >= attList.size()) {
                return null;
            }
            current = start.makeWrapper(
                    attList.get(ix), docWrapper, start, ix);
            ix++;
            return current;
        }

        public NodeInfo current() {
            return current;
        }

        public int position() {
            return ix + 1;
        }

        public void close() {
        }

        /**
         * Return an iterator over an axis, starting at the current node.
         *
         * @param axis the axis to iterate over, using a constant such as
         *             {@link net.sf.saxon.om.AxisInfo#CHILD}
         * @param test a predicate to apply to the nodes before returning them.
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
            return current.getStringValueCS();
        }


        /*@NotNull*/
        public AxisIterator<NodeInfo> getAnother() {
            return new AttributeEnumeration(start);
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
         *         and {@link #LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return LOOKAHEAD;
        }
    }


    /**
     * The class ChildEnumeration handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the children
     * of the start node in reverse order, something that is needed to support the
     * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
     */

    private final class ChildEnumeration extends AxisIteratorImpl implements LookaheadIterator<NodeInfo> {

        private DOMNodeWrapper start;
        private DOMNodeWrapper commonParent;
        private boolean downwards;  // iterate children of start node (not siblings)
        private boolean forwards;   // iterate in document order (not reverse order)
        private boolean elementsOnly;
        NodeList childNodes;
        private int childNodesLength;
        private int ix;             // index of the current DOM node within childNodes;
        // in the case of adjacent text nodes, index of the first in the group
        private int currentSpan;    // number of DOM nodes mapping to the current XPath node

        /**
         * Create an iterator over the children or siblings of a given node
         *
         * @param start        the start node for the iteration
         * @param downwards    if true, iterate over the children of the start node; if false, iterate
         *                     over the following or preceding siblings
         * @param forwards     if true, iterate in forwards document order; if false, iterate in reverse
         *                     document order
         * @param elementsOnly if true, retrieve element nodes only; if false, retrieve all nodes
         */
        public ChildEnumeration(DOMNodeWrapper start,
                                boolean downwards, boolean forwards, boolean elementsOnly) {
            synchronized (start.docWrapper) {
                this.start = start;
                this.downwards = downwards;
                this.forwards = forwards;
                this.elementsOnly = elementsOnly;
                position = 0;
                currentSpan = 1;

                if (downwards) {
                    commonParent = start;
                } else {
                    commonParent = start.getParent();
                }

                childNodes = commonParent.node.getChildNodes();
                childNodesLength = childNodes.getLength();
                if (downwards) {
                    currentSpan = 1;
                    if (forwards) {
                        ix = -1;                        // just before first
                    } else {
                        ix = childNodesLength;          // just after last
                    }
                } else {
                    ix = start.getSiblingPosition();    // at current node
                    currentSpan = start.span;
                }
            }
        }

        /**
         * Starting with ix positioned at a node, which in the last in a span, calculate the length
         * of the span, that is the number of DOM nodes mapped to this XPath node.
         *
         * @return the number of nodes spanned
         */

        private int skipPrecedingTextNodes() {
            int count = 0;
            while (ix >= count) {
                Node node = childNodes.item(ix - count);
                short kind = node.getNodeType();
                if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                    count++;
                } else {
                    break;
                }
            }
            return (count == 0 ? 1 : count);
        }

        /**
         * Starting with ix positioned at a node, which in the first in a span, calculate the length
         * of the span, that is the number of DOM nodes mapped to this XPath node.
         *
         * @return the number of nodes spanned
         */

        private int skipFollowingTextNodes() {
            int count = 0;
            int pos = ix;
            final int len = childNodesLength;
            while (pos < len) {
                Node node = childNodes.item(pos);
                short kind = node.getNodeType();
                if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                    pos++;
                    count++;
                } else {
                    break;
                }
            }
            return (count == 0 ? 1 : count);
        }

        public boolean hasNext() {
            if (forwards) {
                return ix + currentSpan < childNodesLength;
            } else {
                return ix > 0;
            }
        }

        /*@Nullable*/
        public NodeInfo next() {
            synchronized (start.docWrapper) {
                while (true) {
                    if (forwards) {
                        ix += currentSpan;
                        if (ix >= childNodesLength) {
                            position = -1;
                            return null;
                        } else {
                            currentSpan = skipFollowingTextNodes();
                            Node currentDomNode = childNodes.item(ix);
                            switch (currentDomNode.getNodeType()) {
                                case Node.DOCUMENT_TYPE_NODE:
                                    continue;
                                case Node.ELEMENT_NODE:
                                    break;
                                default:
                                    if (elementsOnly) {
                                        continue;
                                    } else {
                                        break;
                                    }
                            }
                            DOMNodeWrapper wrapper = makeWrapper(currentDomNode, docWrapper, commonParent, ix);
                            wrapper.span = currentSpan;
                            position++;
                            return current = wrapper;
                        }
                    } else {
                        ix--;
                        if (ix < 0) {
                            position = -1;
                            return null;
                        } else {
                            currentSpan = skipPrecedingTextNodes();
                            ix -= (currentSpan - 1);
                            Node currentDomNode = childNodes.item(ix);
                            switch (currentDomNode.getNodeType()) {
                                case Node.DOCUMENT_TYPE_NODE:
                                    continue;
                                case Node.ELEMENT_NODE:
                                    break;
                                default:
                                    if (elementsOnly) {
                                        continue;
                                    } else {
                                        break;
                                    }
                            }
                            DOMNodeWrapper wrapper = makeWrapper(currentDomNode, docWrapper, commonParent, ix);
                            wrapper.span = currentSpan;
                            position++;
                            return current = wrapper;
                        }
                    }
                }
            }
        }

        /*@NotNull*/
        public AxisIterator<NodeInfo> getAnother() {
            return new ChildEnumeration(start, downwards, forwards, elementsOnly);
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
         *         and {@link #LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return LOOKAHEAD;
        }

    } // end of class ChildEnumeration


}

