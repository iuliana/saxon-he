////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;


/**
  * A node in the XML parse tree representing an attribute. Note that this is
  * generated only "on demand", when the attribute is selected by a select pattern.<P>
  * @author Michael H. Kay
  */

final class TinyAttributeImpl extends TinyNodeImpl {

    public TinyAttributeImpl(TinyTree tree, int nodeNr) {
        this.tree = tree;
        this.nodeNr = nodeNr;
    }


    public void setSystemId(String uri) {
        // no action: an attribute has the same base URI as its parent
    }

    /**
    * Get the parent node
    */

    public NodeInfo getParent() {
        return tree.getNode(tree.attParent[nodeNr]);
    }

    /**
     * Get the root node of the tree (not necessarily a document node)
     *
     * @return the NodeInfo representing the root of this tree
     */

    public NodeInfo getRoot() {
        NodeInfo parent = getParent();
        if (parent == null) {
            return this;    // doesn't happen - parentless attributes are represented by the Orphan class
        } else {
            return parent.getRoot();
        }
    }

    /**
    * Get the node sequence number (in document order). Sequence numbers are monotonic but not
    * consecutive. In this implementation, elements have a zero
    * least-significant word, while attributes and namespaces use the same value in the top word as
    * the containing element, and use the bottom word to hold
    * a sequence number, which numbers namespaces first and then attributes.
    */

    protected long getSequenceNumber() {
        //noinspection ConstantConditions
        return
            ((TinyNodeImpl)getParent()).getSequenceNumber()
            + 0x8000 +
            (nodeNr - tree.alpha[tree.attParent[nodeNr]]);
        // note the 0x8000 is to leave room for namespace nodes
    }

    /**
    * Return the type of node.
    * @return Node.ATTRIBUTE
    */

    public final int getNodeKind() {
        return Type.ATTRIBUTE;
    }

    /**
    * Return the string value of the node.
    * @return the attribute value
    */

    public CharSequence getStringValueCS() {
        return tree.attValue[nodeNr];
    }

    /**
    * Return the string value of the node.
    * @return the attribute value
    */

    public String getStringValue() {
        return tree.attValue[nodeNr].toString();
    }

	/**
	* Get the fingerprint of the node, used for matching names
	*/

	public int getFingerprint() {
		return tree.attCode[nodeNr] & 0xfffff;
	}

	/**
	* Get the name code of the node, used for finding names in the name pool
	*/

	public int getNameCode() {
		return tree.attCode[nodeNr];
	}

    /**
    * Get the prefix part of the name of this node. This is the name before the ":" if any.
    * @return the prefix part of the name. For an unnamed node, return null.
    */

    public String getPrefix() {
    	int code = tree.attCode[nodeNr];
    	if (!NamePool.isPrefixed(code)) {
            return "";
        }
    	return tree.getNamePool().getPrefix(code);
    }

    /**
    * Get the display name of this node. For elements and attributes this is [prefix:]localname.
    * For unnamed nodes, it is an empty string.
    * @return The display name of this node.
    * For a node with no name, return an empty string.
    */

    public String getDisplayName() {
        return tree.getNamePool().getDisplayName(tree.attCode[nodeNr]);
    }


    /**
    * Get the local name of this node.
    * @return The local name of this node.
    * For a node with no name, return an empty string.
    */

    public String getLocalPart() {
        return tree.getNamePool().getLocalName(tree.attCode[nodeNr]);
    }

    /**
    * Get the URI part of the name of this node.
    * @return The URI of the namespace of this node. For the default namespace, return an
    * empty string
    */

    public final String getURI() {
        return tree.getNamePool().getURI(tree.attCode[nodeNr]);
    }

    /**
    * Get the type annotation of this node
    * Returns UNTYPED_ATOMIC if there is no type annotation
    */

    public int getTypeAnnotation() {
        return tree.getAttributeAnnotation(nodeNr);
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
        if (tree.attTypeCode == null) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }
        return tree.getConfiguration().getSchemaType(tree.getAttributeAnnotation(nodeNr));
    }

    /**
     * Get the typed value.
     * @return the typed value. This will either be a single AtomicValue or a Value whose items are
     *         atomic values.
     * @since 8.5
     */

    public AtomicSequence atomize() throws XPathException {
        return tree.getTypedValueOfAttribute(this, nodeNr);
    }

    /**
    * Generate id. Returns key of owning element with the attribute namecode as a suffix
     * @param buffer Buffer to contain the generated ID value
     */

    public void generateId(FastStringBuffer buffer) {
        getParent().generateId(buffer);
        buffer.append("a");
        buffer.append(Integer.toString(tree.attCode[nodeNr]));
        // we previously used the attribute name. But this breaks the requirement
        // that the result of generate-id consists entirely of alphanumeric ASCII
        // characters
    }

    /**
    * Copy this node to a given outputter
    */

    public void copy(/*@NotNull*/ Receiver out, int copyOptions, int locationId) throws XPathException {
		SimpleType typeCode = (CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS) ?
                (SimpleType)getSchemaType() : BuiltInAtomicType.UNTYPED_ATOMIC);
        out.attribute(new NameOfNode(this), typeCode, getStringValue(), locationId, 0);
    }

    /**
    * Get the line number of the node within its source document entity
    */

    public int getLineNumber() {
        return getParent().getLineNumber();
    }

    /**
    * Get the column number of the node within its source document entity
    */

    public int getColumnNumber() {
        return getParent().getColumnNumber();
    }

    /**
     * Determine whether the node has the is-nilled property
     * @return true if the node has the is-nilled property
     */

    public boolean isNilled() {
        return false;
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        return tree.isIdAttribute(nodeNr);
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    public boolean isIdref() {
        return tree.isIdrefAttribute(nodeNr);
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
        return (((int)(tree.getDocumentNumber() & 0x3ff)) << 20) ^ nodeNr ^ 7<<17;
    }

}

