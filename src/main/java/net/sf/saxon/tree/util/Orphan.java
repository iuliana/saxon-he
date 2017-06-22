////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * A node (implementing the NodeInfo interface) representing an attribute, text node,
 * comment, processing instruction, or namespace that has no parent (and of course no children).
 * Exceptionally it is also used (during whitespace stripping) to represent a standalone element.
 *
 * <p>In general this class does not impose constraints defined in the data model: that is the responsibility
 * of the client. For example, the class does not prevent you from creating a comment or text node that has
 * a name or a non-trivial type annotation.</p>
 *
 * @author Michael H. Kay
 */

public final class Orphan implements MutableNodeInfo {

    private short kind;
    /*@Nullable*/ private NodeName nodeName = null;
    private CharSequence stringValue;
    private SchemaType typeAnnotation = null;
    private Configuration config;
    private String systemId;
    private boolean isId;
    private boolean isIdref;

    /**
     * Create an Orphan node
     * @param config the Saxon configuration
     */

    public Orphan(Configuration config) {
        this.config = config;
    }

    /**
      * To implement {@link Sequence}, this method returns the item itself
      * @return this item
      */

     public Item head() {
         return this;
     }

     /**
      * To implement {@link Sequence}, this method returns a singleton iterator
      * that delivers this item in the form of a sequence
      * @return a singleton iterator that returns this item
      */

     public SequenceIterator iterate() {
         return SingletonIterator.makeIterator(this);
     }


    /**
     * Set the node kind
     * @param kind the kind of node, for example {@link Type#ELEMENT} or {@link Type#ATTRIBUTE}
     */

    public void setNodeKind(short kind) {
        this.kind = kind;
    }

    /**
     * Set the name of the node
     * @param nodeName the name of the node. May be null for unnamed nodes such as text and comment nodes
     */

    public void setNodeName(/*@Nullable*/ NodeName nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * Set the string value of the node
     * @param stringValue the string value of the node
     */

    public void setStringValue(CharSequence stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * Set the type annotation of the node
     * @param typeAnnotation the type annotation
     */

    public void setTypeAnnotation(SchemaType typeAnnotation) {
        this.typeAnnotation = typeAnnotation;
    }

    /**
     * Set the type annotation of the node
     * @param typeAnnotation the type annotation
     */

    public void setTypeAnnotation(int typeAnnotation) {
        this.typeAnnotation = getConfiguration().getSchemaType(typeAnnotation);
    }

    /**
     * Set the base URI of the node
     * @param systemId the base URI of the node
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Set the isId property
     * @param id the isId property
     */

    public void setIsId(boolean id) {
        this.isId = id;
    }

    /**
     * Set the isIdref property
     * @param idref the isIdref property
     */

    public void setIsIdref(boolean idref) {
        this.isIdref = idref;
    }

    /**
    * Return the kind of node.
    * @return one of the values Type.ELEMENT, Type.TEXT, Type.ATTRIBUTE, etc.
    */

    public int getNodeKind() {
        return kind;
    }

    /**
     * Get the typed value. The result of this method will always be consistent with the method
     * {@link net.sf.saxon.om.Item#getTypedValue()}. However, this method is often more convenient and may be
     * more efficient, especially in the common case where the value is expected to be a singleton.
     *
     * @return the typed value. If requireSingleton is set to true, the result will always be an
     *         AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
     *         values.
     * @since 8.5
     */

    public AtomicSequence atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(stringValue);
            case Type.TEXT:
            case Type.DOCUMENT:
            case Type.NAMESPACE:
                return new UntypedAtomicValue(stringValue);
            default:
                if (typeAnnotation == null || typeAnnotation == Untyped.getInstance() ||
                        typeAnnotation == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return new UntypedAtomicValue(stringValue);
                } else {
                    return typeAnnotation.atomize(this);
                }
        }
    }

    /**
     * Get the configuration
     * @return the Saxon configuration object
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the name pool
     */

    public NamePool getNamePool() {
        return config.getNamePool();
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
        if (typeAnnotation == null) {
            if (kind == Type.ELEMENT) {
                return Untyped.getInstance();
            } else if (kind == Type.ATTRIBUTE) {
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            }
        }
        return typeAnnotation;
    }

    /**
    * Determine whether this is the same node as another node. <br />
    * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
    * @return true if this Node object and the supplied Node object represent the
    * same node in the tree.
    */

    public boolean isSameNodeInfo(NodeInfo other) {
        return this==other;
    }

    /**
      * The equals() method compares nodes for identity. It is defined to give the same result
      * as isSameNodeInfo().
      * @param other the node to be compared with this node
      * @return true if this NodeInfo object and the supplied NodeInfo object represent
      *      the same node in the tree.
      * @since 8.7 Previously, the effect of the equals() method was not defined. Callers
      * should therefore be aware that third party implementations of the NodeInfo interface may
      * not implement the correct semantics. It is safer to use isSameNodeInfo() for this reason.
      * The equals() method has been defined because it is useful in contexts such as a Java Set or HashMap.
      */

     public boolean equals(Object other) {
        return other instanceof NodeInfo && isSameNodeInfo((NodeInfo)other);
    }

     /**
      * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
      * (represent the same node) then they must have the same hashCode()
      * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
      * should therefore be aware that third party implementations of the NodeInfo interface may
      * not implement the correct semantics.
      */

     public int hashCode() {
         return super.hashCode();
     }


    /**
    * Get the System ID for the node.
    * @return the System Identifier of the entity in the source document containing the node,
    * or null if not known. Note this is not the same as the base URI: the base URI can be
    * modified by xml:base, but the system ID cannot.
    */

    public String getSystemId() {
        return systemId;
    }

    /**
    * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
    * in the node. This will be the same as the System ID unless xml:base has been used.
    */

    /*@Nullable*/ public String getBaseURI() {
        if (kind == Type.PROCESSING_INSTRUCTION) {
            return systemId;
        } else {
            return null;
        }
    }

    /**
    * Get line number
    * @return the line number of the node in its original source document; or -1 if not available
    */

    public int getLineNumber() {
        return -1;
    }

   /**
     * Get column number
     * @return the column number of the node in its original source document; or -1 if not available
     */

    public int getColumnNumber() {
        return -1;
    }

    /**
    * Determine the relative position of this node and another node, in document order.
    * The other node will always be in the same document.
    * @param other The other node, whose position is to be compared with this node
    * @return -1 if this node precedes the other node, +1 if it follows the other
    * node, or 0 if they are the same node. (In this case, isSameNode() will always
    * return true, and the two nodes will produce the same result for generateId())
    */

    public int compareOrder(/*@NotNull*/ NodeInfo other) {

        // are they the same node?
        if (this.isSameNodeInfo(other)) {
            return 0;
        }
        return (this.hashCode() < other.hashCode() ? -1 : +1);
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
        if (kind == Type.ATTRIBUTE || kind == Type.NAMESPACE) {
            throw new UnsupportedOperationException();
        }
        if (this.isSameNodeInfo(other)) {
            return AxisInfo.SELF;
        }
        throw new UnsupportedOperationException();
    }

    /**
    * Return the string value of the node.
    * @return the string value of the node
    */

    public String getStringValue() {
        return stringValue.toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public CharSequence getStringValueCS() {
        return stringValue;
    }

    /**
	* Get name code. The name code is a coded form of the node name: two nodes
	* with the same name code have the same namespace URI, the same local name,
	* and the same prefix. By masking the name code with &0xfffff, you get a
	* fingerprint: two nodes with the same fingerprint have the same local name
	* and namespace URI.
    * @see net.sf.saxon.om.NamePool#allocate allocate
	*/

	public int getNameCode() {
        NodeName nn = nodeName;
        if (nn == null) {
            return -1;
        } else {
            return nn.allocateNameCode(getNamePool());
        }
	}

	/**
	* Get fingerprint. The fingerprint is a coded form of the expanded name
	* of the node: two nodes
	* with the same name code have the same namespace URI and the same local name.
	* A fingerprint of -1 should be returned for a node with no name.
	*/

	public int getFingerprint() {
        if (nodeName == null) {
            return -1;
        } else {
	        return getNameCode() & NamePool.FP_MASK;
        }
	}

    /**
    * Get the local part of the name of this node. This is the name after the ":" if any.
    * @return the local part of the name. For an unnamed node, returns "".
    */

    public String getLocalPart() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getLocalPart();
        }
    }

    /**
    * Get the URI part of the name of this node. This is the URI corresponding to the
    * prefix, or the URI of the default namespace if appropriate.
    * @return The URI of the namespace of this node. For an unnamed node, return null.
    * For a node with an empty prefix, return an empty string.
    */

    public String getURI() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getURI();
        }
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    public String getPrefix() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getPrefix();
        }
    }

    /**
    * Get the display name of this node. For elements and attributes this is [prefix:]localname.
    * For unnamed nodes, it is an empty string.
    * @return The display name of this node.
    * For a node with no name, return an empty string.
    */

    public String getDisplayName() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getDisplayName();
        }
    }

    /**
    * Get the NodeInfo object representing the parent of this node
     * @return null - an Orphan has no parent.
    */

    /*@Nullable*/ public NodeInfo getParent() {
        return null;
    }

    /**
    * Return an iteration over the nodes reached by the given axis from this node
    * @param axisNumber the axis to be searched, e.g. Axis.CHILD or Axis.ANCESTOR
    * @return a SequenceIterator that scans the nodes reached by the axis in turn.
    */

    /*@NotNull*/ public AxisIterator iterateAxis(byte axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR_OR_SELF:
            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.SELF:
                return SingleNodeIterator.makeIterator(this);
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyAxisIterator.emptyAxisIterator();
            default:
                 throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }


    /**
    * Return an iteration over the nodes reached by the given axis from this node
    * @param axisNumber the axis to be searched, e.g. Axis.CHILD or Axis.ANCESTOR
    * @param nodeTest A pattern to be matched by the returned nodes
    * @return a SequenceIterator that scans the nodes reached by the axis in turn.
    */

    /*@NotNull*/ public AxisIterator iterateAxis(byte axisNumber, /*@NotNull*/ NodeTest nodeTest) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR_OR_SELF:
            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyAxisIterator.emptyAxisIterator();
            default:
                 throw new IllegalArgumentException("Unknown axis number " + axisNumber);
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
        return null;
    }

    /**
    * Get the root node of this tree (not necessarily a document node).
    * Always returns this node in the case of an Orphan node.
    */

    /*@NotNull*/ public NodeInfo getRoot() {
        return this;
    }

    /**
    * Get the root (document) node
    * @return the DocumentInfo representing the containing document, or null if the
    * node is not part of a document. Always null for an Orphan node.
    */

    /*@Nullable*/ public DocumentInfo getDocumentRoot() {
        return null;
    }

    /**
    * Determine whether the node has any children.
    * @return false - an orphan node never has any children
    */

    public boolean hasChildNodes() {
        return false;
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     * @param buffer a buffer, into which will be placed
     * a string that uniquely identifies this node, within this
     * document. The calling code prepends information to make the result
     * unique across all documents.
     */

    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.append('Q');
        buffer.append(Integer.toString(hashCode()));
    }

    /**
     * Get the document number of the document containing this node. For a free-standing
     * orphan node, just return the hashcode.
     */

    public long getDocumentNumber() {
        return hashCode() & 0xffffff;
        // lose the top bits because we need to subtract these values for comparison
    }

    /**
    * Copy this node to a given outputter (deep copy)
    */

    public void copy(/*@NotNull*/ Receiver out, int copyOptions, int locationId) throws XPathException {
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

    /*@Nullable*/ public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return null;
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        return isId || (kind == Type.ATTRIBUTE && nodeName.equals(StandardNames.XML_ID_NAME));
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    public boolean isIdref() {
        return isIdref;
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    public boolean isNilled() {
        return false;
    }

    /**
     * Insert copies of a sequence of nodes as children of this node.
     * <p/>
     * <p>This method takes no action unless the target node is a document node or element node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p/>
     * <p>The supplied nodes will be copied to form the new children. Adjacent text nodes will be merged, and
     * zero-length text nodes removed.</p>
     * @param source  the nodes to be inserted
     * @param atStart true if the new nodes are to be inserted before existing children; false if they are
     * @param inherit true if the insert nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     */

    public void insertChildren(NodeInfo[] source, boolean atStart, boolean inherit) {
        // no action: node is not a document or element node
    }

    /**
     * Insert copies of a sequence of nodes as siblings of this node.
     * <p/>
     * <p>This method takes no action unless the target node is an element, text node, comment, or
     * processing instruction, and one that has a parent node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p/>
     * <p>The supplied nodes must use the same data model implementation as the tree into which they
     * will be inserted.</p>
     * @param source  the nodes to be inserted
     * @param before  true if the new nodes are to be inserted before the target node; false if they are
     * @param inherit true if the insert nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     */

    public void insertSiblings(NodeInfo[] source, boolean before, boolean inherit) {
        // no action: node has no parent
    }

    /**
     * Remove an attribute from this element node
     * <p/>
     * <p>If this node is not an element, or if it has no attribute with the specified name,
     * this method takes no action.</p>
     * <p/>
     * <p>The attribute node itself is not modified in any way.</p>
     * @param attribute the attribute node to be removed
     */

    public void removeAttribute(NodeInfo attribute) {
        // no action: node is not an element
    }

    /**
     * Add an attribute to this element node.
     * <p/>
     * <p>If this node is not an element, or if the supplied node is not an attribute, the method
     * takes no action. If the element already has an attribute with this name, the existing attribute
     * is replaced.</p>
     * @param nameCode   the name of the new attribute
     * @param attType   the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     */

    public void addAttribute(NodeName nameCode, SimpleType attType, CharSequence value, int properties) {
        // no action: node is not an element
    }

    /**
     * Delete this node (that is, detach it from its parent).
     * <p>If this node has preceding and following siblings that are both text nodes,
     * the two text nodes will be joined into a single text node (the identity of this node
     * with respect to its predecessors is undefined).</p>
     */

    public void delete() {
        // no action other than to mark it deleted: node has no parent from which it can be detached
        kind = -1;
    }

    /**
     * Test whether this MutableNodeInfo object represents a node that has been deleted.
     * Generally, such a node is unusable, and any attempt to use it will result in an exception
     * being thrown
     * @return true if this node has been deleted
     */

    public boolean isDeleted() {
        return kind == -1;
    }

    /**
     * Replace this node with a given sequence of nodes
     * @param replacement the replacement nodes
     * @param inherit     true if the replacement nodes are to inherit the namespaces of their new parent; false
     *                    if such namespaces are to be undeclared
     * @throws IllegalArgumentException if any of the replacement nodes is of the wrong kind. When replacing
     *          a child node, the replacement nodes must all be elements, text, comment, or PI nodes; when replacing
     *          an attribute, the replacement nodes must all be attributes.
     * @throws IllegalStateException if this node is deleted or if it has no parent node.
     */

    public void replace(NodeInfo[] replacement, boolean inherit) {
        throw new IllegalStateException("Cannot replace a parentless node");
    }

    /**
     * Replace the string-value of this node. If applied to an element or document node, this
     * causes all existing children to be deleted, and replaced with a new text node
     * whose string value is the value supplied. The caller is responsible for checking
     * that the value is valid, for example that comments do not contain a double hyphen; the
     * implementation is not required to check for such conditions.
     * @param stringValue the new string value
     */

    public void replaceStringValue(CharSequence stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * Rename this node.
     * <p>This call has no effect if applied to a nameless node, such as a text node or comment.</p>
     * <p>If necessary, a new namespace binding will be added to the target element, or to the element
     * parent of the target attribute</p>
     *
     * @param newNameCode the namecode of the new name in the name pool
     * @throws IllegalArgumentException if the new name code is not present in the name pool, or if
     *         it has a (prefix, uri) pair in which the
     *         prefix is the same as that of an existing in-scope namespace binding and the uri is different from that
     *         namespace binding.
     */

    public void rename(NodeName newNameCode) {
        if (kind==Type.ATTRIBUTE || kind==Type.PROCESSING_INSTRUCTION) {
            nodeName = newNameCode;
        }
    }

    /**
     * Add a namespace binding (that is, a namespace node) to this element. This call has no effect if applied
     * to a node other than an element.
     * @param nscode  The namespace code representing the (prefix, uri) pair of the namespace binding to be
     *                added. If the target element already has a namespace binding with this (prefix, uri) pair, the call has
     *                no effect. If the target element currently has a namespace binding with this prefix and a different URI, an
     *                exception is raised.
     * @param inherit If true, the new namespace binding will be inherited by any children of the target element
     *                that do not already have a namespace binding for the specified prefix, recursively.
     *                If false, the new namespace binding will not be inherited.
     * @throws IllegalArgumentException if the namespace code is not present in the namepool, or if the target
     *                                  element already has a namespace binding for this prefix
     */

    public void addNamespace(NamespaceBinding nscode, boolean inherit) {
        // no action: node is not an element
    }

    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification.
     * (Note: the caller is responsible for updating the set of nodes marked for revalidation)
     */

    public void removeTypeAnnotation() {
        typeAnnotation = BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Get a Builder suitable for building nodes that can be attached to this document.
     * This implementation always throws an exception: the method should only be called on a document or element
     * node when creating new children.
     */

    /*@NotNull*/ public Builder newBuilder() {
        throw new UnsupportedOperationException("Cannot create children for an Orphan node");
    }
}

