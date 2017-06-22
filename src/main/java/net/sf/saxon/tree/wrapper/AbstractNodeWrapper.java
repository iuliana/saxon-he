////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.<P>
 * This is the implementation of the NodeInfo interface used as a wrapper for JDOM nodes.
 *
 * @author Michael H. Kay
 */

public abstract class AbstractNodeWrapper implements NodeInfo, VirtualNode {

    /**
     * To implement {@link net.sf.saxon.om.Sequence}, this method returns the item itself
     *
     * @return this item
     */

    public final NodeInfo head() {
        return this;
    }

    /**
     * To implement {@link net.sf.saxon.om.Sequence}, this method returns a singleton iterator
     * that delivers this item in the form of a sequence
     *
     * @return a singleton iterator that returns this item
     */

    public SequenceIterator<NodeInfo> iterate() {
        return SingletonIterator.makeIterator((NodeInfo)this);
    }

    /**
     * Get the node underlying this virtual node. If this is a VirtualNode the method
     * will automatically drill down through several layers of wrapping.
     *
     * @return The underlying node.
     */

    public final Object getRealNode() {
        return getUnderlyingNode();
    }

    /**
     * Get the configuration
     */

    public Configuration getConfiguration() {
        return getDocumentRoot().getConfiguration();
    }

    /**
     * Get the name pool for this node
     *
     * @return the NamePool
     */

    public NamePool getNamePool() {
        return getConfiguration().getNamePool();
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. This will either be a single AtomicValue or a value whose items are
     *         atomic values.
     * @since 8.5 - signature changed in 9.5
     */

    public AtomicSequence atomize() {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(getStringValueCS());
            default:
                return new UntypedAtomicValue(getStringValueCS());
        }
    }

    /**
     * Get the type annotation
     */

    public int getTypeAnnotation() {
        SchemaType st = getSchemaType();
        return (st == null ? -1 : st.getFingerprint());
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
        if (getNodeKind() == Type.ATTRIBUTE) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        } else {
            return Untyped.getInstance();
        }
    }

    /**
     * Determine whether this is the same node as another node. <br />
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean isSameNodeInfo(NodeInfo other) {
        if (!(other instanceof AbstractNodeWrapper)) {
            return false;
        }
        AbstractNodeWrapper ow = (AbstractNodeWrapper) other;
        return getUnderlyingNode().equals(ow.getUnderlyingNode());
    }

    /**
     * The equals() method compares nodes for identity. It is defined to give the same result
     * as isSameNodeInfo().
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     * @since 8.7 Previously, the effect of the equals() method was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics. It is safer to use isSameNodeInfo() for this reason.
     *        The equals() method has been defined because it is useful in contexts such as a Java Set or HashMap.
     */

    public boolean equals(Object other) {
        return other instanceof NodeInfo && isSameNodeInfo((NodeInfo) other);
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    public int hashCode() {
        return getUnderlyingNode().hashCode();
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

    /**
     * Set the system ID. Required because NodeInfo implements the JAXP Source interface
     * @param uri the system ID.
     */

    public void setSystemId(String uri) {
        getDocumentRoot().setSystemId(uri);
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node.
     * @return the base URI of the node, taking into account xml:base attributes if present
     */

    public String getBaseURI() {
        if (getNodeKind() == Type.NAMESPACE) {
            return null;
        }
        NodeInfo n = this;
        if (getNodeKind() != Type.ELEMENT) {
            n = getParent();
        }
        // Look for an xml:base attribute
        while (n != null) {
            String xmlbase = n.getAttributeValue(NamespaceConstant.XML, "base");
            if (xmlbase != null) {
                return xmlbase;
            }
            n = n.getParent();
        }
        // if not found, return the base URI of the document node
        return getDocumentRoot().getBaseURI();
    }

    /**
     * Get line number
     *
     * @return the line number of the node in its original source document; or -1 if not available.
     *         Always returns -1 in this implementation.
     */

    public int getLineNumber() {
        return -1;
    }

    /**
     * Get column number
     *
     * @return the column number of the node in its original source document; or -1 if not available
     */

    public int getColumnNumber() {
        return -1;
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
        return Navigator.comparePosition(this, other);
    }

    /**
     * Return the string value of the node. The interpretation of this depends on the type
     * of node. For an element it is the accumulated character content of the element,
     * including descendant elements.
     *
     * @return the string value of the node
     */

    public String getStringValue() {
        return getStringValueCS().toString();
    }


    /**
     * Get name code. The name code is a coded form of the node name: two nodes
     * with the same name code have the same namespace URI, the same local name,
     * and the same prefix. By masking the name code with &0xfffff, you get a
     * fingerprint: two nodes with the same fingerprint have the same local name
     * and namespace URI.
     *
     * @see net.sf.saxon.om.NamePool#allocate allocate
     */

    public int getNameCode() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return getNamePool().allocate(getPrefix(),
                        getURI(),
                        getLocalPart());
            default:
                return -1;
        }
    }

    /**
     * Get fingerprint. The fingerprint is a coded form of the expanded name
     * of the node: two nodes
     * with the same name code have the same namespace URI and the same local name.
     * A fingerprint of -1 should be returned for a node with no name.
     */

    public int getFingerprint() {
        int nc = getNameCode();
        if (nc == -1) {
            return -1;
        } else {
            return nc & 0xfffff;
        }
    }


    /**
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     *         empty string.
     */

    public String getDisplayName() {
        String prefix = getPrefix();
        String local = getLocalPart();
        if (prefix.length() == 0) {
            return local;
        } else {
            return prefix + ":" + local;
        }
    }

    /**
     * Get the string value of a given attribute of this node.
     *
     * <p>The default implementation is suitable for nodes other than elements.</p>
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */
    public String getAttributeValue(String uri, String local) {
        return null;
    }



    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be used
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    public AxisIterator iterateAxis(byte axisNumber) {
        return iterateAxis(axisNumber, AnyNodeTest.getInstance());
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node.
     *
     * <p>This superclass provides implementations of the ancestor, ancestor-or-self,
     * following, namespace, parent, preceding, self, and preceding-or-ancestor axes.
     * The other axes are implemented by calling methods iterateAttributes(),
     * iterateChildren(), iterateDescendants(), and iterateSiblings(), which must
     * be provided in a subclass.</p>
     *
     * @param axisNumber the axis to be used
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    public AxisIterator iterateAxis(byte axisNumber, NodeTest nodeTest) {
        int nodeKind = getNodeKind();
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
                if (nodeKind == Type.DOCUMENT) {
                    return EmptyAxisIterator.emptyAxisIterator();
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, false),
                        nodeTest);

            case AxisInfo.ANCESTOR_OR_SELF:
                if (nodeKind == Type.DOCUMENT) {
                    return Navigator.filteredSingleton(this, nodeTest);
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, true),
                        nodeTest);

            case AxisInfo.ATTRIBUTE:
                if (nodeKind != Type.ELEMENT) {
                    return EmptyAxisIterator.emptyAxisIterator();
                }
                return iterateAttributes(nodeTest);

            case AxisInfo.CHILD:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateChildren(nodeTest);
                } else {
                    return EmptyAxisIterator.emptyAxisIterator();
                }

            case AxisInfo.DESCENDANT:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateDescendants(nodeTest, false);
                } else {
                    return EmptyAxisIterator.emptyAxisIterator();
                }

            case AxisInfo.DESCENDANT_OR_SELF:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateDescendants(nodeTest, true);
                } else {
                    return Navigator.filteredSingleton(this, nodeTest);
                }

            case AxisInfo.FOLLOWING:
                return new Navigator.AxisFilter(
                        new Navigator.FollowingEnumeration(this),
                        nodeTest);

            case AxisInfo.FOLLOWING_SIBLING:
                switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyAxisIterator.emptyAxisIterator();
                    default:
                        return iterateSiblings(nodeTest, true);
                }

            case AxisInfo.NAMESPACE:
                if (nodeKind != Type.ELEMENT) {
                    return EmptyAxisIterator.emptyAxisIterator();
                }
                return NamespaceNode.makeIterator(this, nodeTest);

            case AxisInfo.PARENT:
                return Navigator.filteredSingleton(getParent(), nodeTest);

            case AxisInfo.PRECEDING:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, false),
                        nodeTest);

            case AxisInfo.PRECEDING_SIBLING:
                switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyAxisIterator.emptyAxisIterator();
                    default:
                        return iterateSiblings(nodeTest, false);
                }

            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);

            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, true),
                        nodeTest);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Return an iterator over the attributes of this element node.
     * This method is only called after checking that the node is an element.
     *
     * @param nodeTest a test that the returned attributes must satisfy
     * @return an iterator over the attribute nodes. The order of the result,
     *         although arbitrary, must be consistent with document order.
     */

    protected abstract AxisIterator<NodeInfo> iterateAttributes(NodeTest nodeTest);

    /**
     * Return an iterator over the children of this node.
     * This method is only called after checking that the node is an element or document.
     *
     * @param nodeTest a test that the returned attributes must satisfy
     * @return an iterator over the child nodes, in document order.
     */

    protected abstract AxisIterator<NodeInfo> iterateChildren(NodeTest nodeTest);

    /**
     * Return an iterator over the siblings of this node.
     * This method is only called after checking that the node is an element, text, comment, or PI node.
     *
     * @param nodeTest a test that the returned siblings must satisfy
     * @param forwards true for following siblings, false for preceding siblings
     * @return an iterator over the sibling nodes, in axis order.
     */

    protected abstract AxisIterator<NodeInfo> iterateSiblings(NodeTest nodeTest, boolean forwards);

    /**
     * Return an iterator over the descendants of this node.
     * This method is only called after checking that the node is an element or document node.
     *
     * @param nodeTest    a test that the returned descendants must satisfy
     * @param includeSelf true if this node is to be included in the result
     * @return an iterator over the sibling nodes, in axis order.
     */

    protected abstract AxisIterator<NodeInfo> iterateDescendants(NodeTest nodeTest, boolean includeSelf);
//    {
//        if (nodeTest == AnyNodeTest.getInstance()) {
//            return new Navigator.DescendantEnumeration(this, includeSelf, true);
//        } else {
//            return new Navigator.AxisFilter(
//                    new Navigator.DescendantEnumeration(this, includeSelf, true),
//                    nodeTest);
//        }
//    }


    /**
     * Get all namespace declarations and undeclarations defined on this element.
     *
     * <p>This method is intended primarily for internal use. User applications needing
     * information about the namespace context of a node should use <code>iterateAxis(Axis.NAMESPACE)</code>.
     * (However, not all implementations support the namespace axis, whereas all implementations are
     * required to support this method.)</p>
     *
     * <p>This implementation of the method is suitable for all nodes other than elements; it returns
     * an empty array.</p>
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
     *         <p>
     *         For a node other than an element, the method returns null.</p>
     */
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return new NamespaceBinding[0];
    }

    /**
     * Get the root node - always a document node with this tree implementation
     *
     * @return the NodeInfo representing the containing document
     */

    public NodeInfo getRoot() {
        return getDocumentRoot();
    }


    /**
     * Determine whether the node has any children.
     * This implementation calls iterateAxis, so the subclass implementation of iterateAxis
     * must avoid calling this method.
     */

    public boolean hasChildNodes() {
        switch (getNodeKind()) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                return iterateAxis(AxisInfo.CHILD).moveNext();
            default:
                return false;
        }
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
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID. This implementation always returns false
     */

    public boolean isId() {
        return false;
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute.
     *         This implementation always returns false
     */

    public boolean isIdref() {
        return false;
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property.
     *         This implementation always returns false
     */

    public boolean isNilled() {
        return false;
    }

}

