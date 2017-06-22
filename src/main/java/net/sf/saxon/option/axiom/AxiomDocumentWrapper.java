////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorImpl;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.type.Type;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * The root node of an XPath tree. (Or equivalently, the tree itself).
 * <P>
 * This class is used not only for a document, but also for the root
 * of a document-less tree fragment.
 *
 * @author Michael H. Kay
 */

public class AxiomDocumentWrapper extends AxiomParentNodeWrapper implements DocumentInfo {

	protected Configuration config;
	protected String baseURI;
	protected long documentNumber;
    private HashMap<String, NodeInfo> idIndex;
    private HashMap<String, Object> userData;

    /**
	 * Create a Saxon wrapper for an Axiom document node
	 *
	 * @param root
	 *            The Axiom root node
	 * @param baseURI
	 *            The base URI for all the nodes in the tree
	 * @param config
	 *            The configuration which defines the name pool used for all
	 *            names in this tree
	 */
	public AxiomDocumentWrapper(OMDocument root, String baseURI, Configuration config) {
        super(root);
		this.baseURI = baseURI;
        setConfiguration(config);
	}

	/**
	 * Wrap a node in the Axiom document.
	 *
	 * @param node
	 *            The node to be wrapped. This must be a node in the same
	 *            document (the system does not check for this).
	 * @return the wrapping NodeInfo object
	 */

	public NodeInfo wrap(OMNode node) {
        return node == this.node ? this : makeWrapper(node, this, null, -1);
    }

	/**
	 * Wrap a node whose parent and sibling position are known in the Axiom document.
	 *
	 * @param node The node to be wrapped. This must be a node in the same
	 *            document (the system does not check for this).
     * @param parent the (wrapper of the) parent node; null if unknown
     * @param index the position of this node among its siblings; -1 if unknown
	 * @return the wrapping NodeInfo object
	 */

    public NodeInfo wrap(OMNode node, AxiomParentNodeWrapper parent, int index) {
        return node == this.node ? this : makeWrapper(node, this, parent, index);
    }

    /**
     * Factory method to wrap an Axiom node with a wrapper that implements the
     * Saxon NodeInfo interface.
     *
     * @param node       The Axiom node (an element, text, processing-instruction, or comment node)
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the Axiom node. May be null if not known.
     * @param index      The position of this node relative to its siblings. May be -1 if not known
     * @return The new wrapper for the supplied node
     */

    protected static NodeInfo makeWrapper(OMNode node, AxiomDocumentWrapper docWrapper,
                                            AxiomParentNodeWrapper parent, int index) {
        if (node == docWrapper.node) {
            return docWrapper;
        }
        if (node instanceof OMElement) {
            return new AxiomElementNodeWrapper(((OMElement)node), docWrapper, parent, index);
        } else {
            return new AxiomLeafNodeWrapper(node, docWrapper, parent, index);
        }
    }

	/**
	 * Set the configuration, which defines the name pool used for all names in
	 * this document. This is always called after a new document has been
	 * created. The implementation must register the name pool with the
	 * document, so that it can be retrieved using getNamePool(). It must also
	 * call NamePool.allocateDocumentNumber(), and return the relevant document
	 * number when getDocumentNumber() is subsequently called.
	 *
	 * @param config The configuration to be used
	 */

	public void setConfiguration(Configuration config) {
		this.config = config;
        documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
	}

    /**
	 * Get the configuration previously set using setConfiguration
	 */

	public Configuration getConfiguration() {
		return config;
	}

	/**
	 * Get the name pool used for the names in this document
	 *
	 * @return the name pool in which all the names used in this document are
	 *         registered
	 */

	public NamePool getNamePool() {
		return config.getNamePool();
	}

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return false;
    }

    /**
	 * Get the unique document number for this document (the number is unique
	 * for all documents within a NamePool)
	 *
	 * @return the unique number identifying this document within the name pool
	 */

	public long getDocumentNumber() {
		return documentNumber;
	}

    /**
     * Get the index position of this node among its siblings (starting from 0)
     *
     * @return 0 for the first child, 1 for the second child, etc.
     */
    public int getSiblingPosition() {
        return 0;
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
        return -1;
    }

    /**
     * Get the kind of node. This will be a value such as {@link net.sf.saxon.type.Type#ELEMENT}
     * or {@link net.sf.saxon.type.Type#ATTRIBUTE}. There are seven kinds of node: documents, elements, attributes,
     * text, comments, processing-instructions, and namespaces.
     *
     * @return an integer identifying the kind of node. These integer values are the
     *         same as those used in the DOM
     * @see net.sf.saxon.type.Type
     * @since 8.4
     */
    public int getNodeKind() {
        return Type.DOCUMENT;
    }

    /**
     * Determine whether this is the same node as another node.
     * <p/>
     * Note that two different NodeInfo instances can represent the same conceptual node.
     * Therefore the "==" operator should not be used to test node identity. The equals()
     * method should give the same result as isSameNodeInfo(), but since this rule was introduced
     * late it might not apply to all implementations.
     * <p/>
     * Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).
     * <p/>
     * This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     */
    public boolean isSameNodeInfo(NodeInfo other) {
        return other == this;
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node.
     */
    @Override
    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Get the System ID for the node. Note this is not the
     * same as the base URI: the base URI can be modified by xml:base, but
     * the system ID cannot. The base URI is used primarily for resolving
     * relative URIs within the content of the document. The system ID is
     * used primarily in conjunction with a line number, for identifying the
     * location of elements within the source XML, in particular when errors
     * are found. For a document node, the System ID represents the value of
     * the document-uri property as defined in the XDM data model.
     *
     * @return the System Identifier of the entity in the source document
     *         containing the node, or null if not known or not applicable.
     * @since 8.4
     */
    public String getSystemId() {
        return baseURI;
    }

    /**
     * Set the system identifier for this Source.
     * <p/>
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    public void setSystemId(String systemId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Get fingerprint. The fingerprint is a coded form of the expanded name of
     * the node: two nodes with the same name code have the same namespace URI
     * and the same local name. A fingerprint of -1 should be returned for a
     * node with no name.
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    public NodeInfo getParent() {
        return null;
    }


    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    public String getLocalPart() {
        return "";
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    public String getPrefix() {
        return "";
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     *         for a node with an empty prefix, return an empty string.
     */

    public String getURI() {
        return "";
    }

    /**
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     *         empty string.
     */

    public String getDisplayName() {
        return "";
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node. If this node has no parent,
     *         then the method returns this node.
     * @since 8.4
     */
    public NodeInfo getRoot() {
        return this;
    }

    @Override
    protected AxisIterator<NodeInfo> iterateAttributes(NodeTest nodeTest) {
        return EmptyAxisIterator.emptyAxisIterator();
    }

    @Override
    protected AxisIterator<NodeInfo> iterateSiblings(NodeTest nodeTest, boolean forwards) {
        return EmptyAxisIterator.emptyAxisIterator();
    }

    /**
     * Get the root node, if it is a document node.
     *
     * @return the DocumentInfo representing the containing document. If this
     *         node is part of a tree that does not have a document node as its
     *         root, returns null.
     * @since 8.4
     */
    public DocumentInfo getDocumentRoot() {
        return this;
    }

    /**
	 * Get the element with a given ID, if any
	 *
	 * @param id the required ID value
	 * @param getParent true if the parent of the selected node is required (for element-with-id)
     * @return the element with the given ID, or null if there is no such ID
	 *         present (or if the parser has not notified attributes as being of
	 *         type ID).
	 */

	/*@Nullable*/ public NodeInfo selectID(String id, boolean getParent) {
		if (idIndex == null) {
			idIndex = new HashMap<String, NodeInfo>(50);
			buildIDIndex(((OMDocument)node).getOMDocumentElement());
		}
		return idIndex.get(id);
	}


	private void buildIDIndex(OMElement elem) {
		for (Iterator kids = elem.getChildElements(); kids.hasNext();) {
			buildIDIndex((OMElement)kids.next());
		}
		for (Iterator atts = elem.getAllAttributes(); atts.hasNext();) {
			OMAttribute att = (OMAttribute)atts.next();
			if ("ID".equals(att.getAttributeType()) ||
                    ("id".equals(att.getLocalName()) && NamespaceConstant.XML.equals(att.getNamespaceURI()))) {
                String val = att.getAttributeValue();
                if (idIndex.get(val) == null) {
                    // if ID's aren't unique, the first one wins
				    idIndex.put(val, wrap(elem));
                }
			}
		}
	}

    /**
     * Get the list of unparsed entities defined in this document
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator<String> getUnparsedEntityNames() {
        List<String> ll = Collections.emptyList();
        return ll.iterator();
    }

    /**
	 * Get the unparsed entity with a given name
	 *
	 * @param name the name of the entity
	 * @return null: Axiom does not provide access to unparsed entities
	 */

	public String[] getUnparsedEntity(String name) {
		return null;
	}

    /**
     * Get the type annotation of this node, if any. Returns -1 for kinds of
     * nodes that have no annotation, and for elements annotated as untyped, and
     * attributes annotated as untypedAtomic.
     * @return the type annotation of the node.
     * @see net.sf.saxon.type.Type
     */

    public int getTypeAnnotation() {
        return StandardNames.XS_UNTYPED;
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     *              removes the existing value for the property.
     */

    public void setUserData(String key, Object value) {
        if (userData == null) {
            userData = new HashMap<String, Object>(4);
        }
        if (value == null) {
            userData.remove(key);
        } else {
            userData.put(key, value);
        }
    }

    /**
     * Get user data held in the document node. This retrieves properties previously set using
     * {@link #setUserData}
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */

    public Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
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
        return (other == this ? 0 : -1);
    }

    protected static class FollowingSiblingIterator extends AxisIteratorImpl {

        private OMNode start;
        private OMNode currentOMNode;
        private AxiomParentNodeWrapper commonParent;
        private AxiomDocumentWrapper docWrapper;

        public FollowingSiblingIterator(OMNode start, AxiomParentNodeWrapper commonParent, AxiomDocumentWrapper docWrapper) {
            this.start = start;
            this.currentOMNode = start;
            this.commonParent = commonParent;
            this.docWrapper = docWrapper;
        }
        public NodeInfo next() {
            if (currentOMNode == null) {
                return null;
            }
            currentOMNode = currentOMNode.getNextOMSibling();
            if (currentOMNode == null) {
                current = null;
                position = -1;
                return null;
            } else {
                position++;
                return (current = makeWrapper(currentOMNode, docWrapper, commonParent, -1));
            }
        }

        public AxisIterator<NodeInfo> getAnother() {
            return new FollowingSiblingIterator(start, commonParent, docWrapper);
        }
    }

    protected static class PrecedingSiblingIterator extends AxisIteratorImpl {

        private OMNode start;
        private OMNode currentOMNode;
        private AxiomParentNodeWrapper commonParent;
        private AxiomDocumentWrapper docWrapper;

        public PrecedingSiblingIterator(OMNode start, AxiomParentNodeWrapper commonParent, AxiomDocumentWrapper docWrapper) {
            this.start = start;
            this.currentOMNode = start;
            this.commonParent = commonParent;
            this.docWrapper = docWrapper;
        }
        public NodeInfo next() {
            if (currentOMNode == null) {
                return null;
            }
            currentOMNode = currentOMNode.getPreviousOMSibling();
            if (currentOMNode == null) {
                current = null;
                position = -1;
                return null;
            } else {
                position++;
                return (current = makeWrapper(currentOMNode, docWrapper, commonParent, -1));
            }
        }

        public AxisIterator<NodeInfo> getAnother() {
            return new FollowingSiblingIterator(start, commonParent, docWrapper);
        }
    }
}

