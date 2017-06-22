////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;
import net.sf.saxon.om.*;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;

/**
  * NodeTest is an interface that enables a test of whether a node has a particular
  * name and type. A NameTest matches the node kind and the namespace URI and the local
  * name.
  *
  * @author Michael H. Kay
  */

public class NameTest extends NodeTest implements QNameTest {

	private int nodeKind;
	private int fingerprint;
    private NamePool namePool;
    /*@Nullable*/ private String uri = null;  // the URI corresponding to the fingerprint - computed lazily
    /*@Nullable*/ private String localName = null; //the local name corresponding to the fingerprint - computed lazily

    /**
     * Create a NameTest to match nodes by name
     * @param nodeKind the kind of node, for example {@link Type#ELEMENT}
     * @param uri the namespace URI of the required nodes. Supply "" to match nodes that are in
     * no namespace
     * @param localName the local name of the required nodes. Supply "" to match unnamed nodes
     * @param namePool the namePool holding the name codes
     * @since 9.0
     */

	public NameTest(int nodeKind, String uri, String localName, NamePool namePool) {
		this.nodeKind = nodeKind;
		this.fingerprint = namePool.allocate("", uri, localName) & NamePool.FP_MASK;
        this.namePool = namePool;
	}

    /**
     * Create a NameTest to match nodes by their nameCode allocated from the NamePool
     * @param nodeKind the kind of node, for example {@link Type#ELEMENT}
     * @param nameCode the nameCode representing the name of the node
     * @param namePool the namePool holding the name codes
     * @since 8.4
     */

	public NameTest(int nodeKind, int nameCode, NamePool namePool) {
		this.nodeKind = nodeKind;
		this.fingerprint = nameCode & 0xfffff;
        this.namePool = namePool;
	}

    /**
     * Create a NameTest to match nodes by name
     * @param nodeKind the kind of node, for example {@link Type#ELEMENT}
     * @param name the name of the nodes that this NameTest will match
     * @param pool the namePool holding the name codes
     * @since 9.4
     */

	public NameTest(int nodeKind, NodeName name, NamePool pool) {
		this.nodeKind = nodeKind;
		this.fingerprint = name.allocateNameCode(pool) & NamePool.FP_MASK;
        this.namePool = pool;
	}

	/**
	 * Create a NameTest for nodes of the same type and name as a given node
     * @param node the node whose node kind and node name will form the basis of the NameTest
	*/

	public NameTest(NodeInfo node) {
		this.nodeKind = node.getNodeKind();
		this.fingerprint = node.getFingerprint();
        this.namePool = node.getNamePool();
	}

    /**
     * Get the NamePool associated with this NameTest
     * @return the NamePool
     */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
     * Get the node kind that this name test matches
     * @return the matching node kind
     */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Get the name of the nodes that this name test matches
     * @return the matching node name
     */

    public NodeName getNodeName() {
        return new CodedName(fingerprint, namePool);
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
        if (nodeKind != this.nodeKind) {
            return false;
        }
        if (name.hasFingerprint()) {
            return (name.getFingerprint() == this.fingerprint);
        } else {
            computeUriAndLocal();
            return name.isInNamespace(uri) && name.getLocalPart().equals(localName);
        }
    }

    /**
     * Test whether this node test is satisfied by a given node on a TinyTree. The node
     * must be a document, element, text, comment, or processing instruction node.
     * This method is provided so that when navigating a TinyTree a node can be rejected without
     * actually instantiating a NodeInfo object.
     *
     * @param tree   the TinyTree containing the node
     * @param nodeNr the number of the node within the TinyTree
     * @return true if the node matches the NodeTest, otherwise false
     */

    public boolean matches(TinyTree tree, int nodeNr) {
        return ((tree.getNameCode(nodeNr)&0xfffff) == this.fingerprint && tree.getNodeKind(nodeNr) == this.nodeKind);
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     * @param node the node to be matched
     */

    public boolean matches(NodeInfo node) {
        if (node.getNodeKind() != nodeKind) {
            return false;
        }

        // Two different algorithms are used for name matching. If the fingerprint of the node is readily
        // available, we use it to do an integer comparison. Otherwise, we do string comparisons on the URI
        // and local name. In practice, Saxon's native node implementations use fingerprint matching, while
        // DOM and JDOM nodes use string comparison of names

        if (node instanceof FingerprintedNode) {
            return node.getFingerprint() == fingerprint;
        } else {
            computeUriAndLocal();
            return localName.equals(node.getLocalPart()) && uri.equals(node.getURI());
        }
    }

    private void computeUriAndLocal() {
        if (uri == null) {
            uri = namePool.getURI(fingerprint);
        }
        if (localName == null) {
            localName = namePool.getLocalName(fingerprint);
        }
    }

    /**
     * Test whether the NameTest matches a given QName
     * @param qname the QName to be matched
     * @return true if the name matches
     */

    public boolean matches(StructuredQName qname) {
        computeUriAndLocal();
        return qname.getLocalPart().equals(localName) && qname.getURI().equals(uri);
    }

    /**
    * Determine the default priority of this node test when used on its own as a Pattern
    */

    public final double getDefaultPriority() {
    	return 0.0;
    }

	/**
	* Get the fingerprint required
	*/

	public int getFingerprint() {
		return fingerprint;
	}

    /**
    * Determine the types of nodes to which this pattern applies. Used for optimisation.
    * For patterns that match nodes of several types, return Type.NODE
    * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
    */

    public int getPrimitiveType() {
        return nodeKind;
    }

    /**
     * Get the type from which this item type is derived by restriction. This
     * is the supertype in the XPath type heirarchy, as distinct from the Schema
     * base type: this means that the supertype of xs:boolean is xs:anyAtomicType,
     * whose supertype is item() (rather than xs:anySimpleType).
     * <p>
     * In fact the concept of "supertype" is not really well-defined, because the types
     * form a lattice rather than a hierarchy. The only real requirement on this function
     * is that it returns a type that strictly subsumes this type, ideally as narrowly
     * as possible.
     * @return the supertype, or null if this type is item()
     * @param th the type hierarchy cache
     */

    public ItemType getSuperType(TypeHierarchy th) {
        return NodeKindTest.makeNodeKindTest(nodeKind);
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        return 1<<nodeKind;
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * A null value indicates that all names are permitted (i.e. that there are no constraints on the node name.
     * The default implementation returns null.
     */

    /*@NotNull*/
    public IntSet getRequiredNodeNames() {
        return new IntSingletonSet(fingerprint);
    }

    /**
     * Get the namespace URI matched by this nametest
     * @return the namespace URI (using "" for the "null namepace")
     */

    public String getNamespaceURI() {
        computeUriAndLocal();
        return uri;
    }

    /**
     * Get the local name matched by this nametest
     * @return the local name
     */

    public String getLocalPart() {
        computeUriAndLocal();
        return localName;
    }

    public String toString() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return "element(" + namePool.getEQName(fingerprint) + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + namePool.getEQName(fingerprint) + ")";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction(" + namePool.getDisplayName(fingerprint) + ')';
            case Type.NAMESPACE:
                return "namespace-node(" + namePool.getDisplayName(fingerprint) + ')';
        }
        return namePool.getDisplayName(fingerprint);
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodeKind<<20 ^ fingerprint;
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof NameTest &&
                ((NameTest)other).namePool == namePool &&
                ((NameTest)other).nodeKind == nodeKind &&
                ((NameTest)other).fingerprint == fingerprint;
    }


}

