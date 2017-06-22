////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Nilled;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;

/**
 * NodeTest is an interface that enables a test of whether a node matches particular
 * conditions. ContentTypeTest tests for an element or attribute node with a particular
 * type annotation.
  *
  * @author Michael H. Kay
  */

public class ContentTypeTest extends NodeTest {

	private int kind;          // element or attribute
    private SchemaType schemaType;
    private int schemaTypeFingerprint;
    private Configuration config;
    private boolean nillable = false;

    /**
     * Create a ContentTypeTest
     * @param nodeKind the kind of nodes to be matched: always elements or attributes
     * @param schemaType the required type annotation, as a simple or complex schema type
     * @param config the Configuration, supplied because this KindTest needs access to schema information
     * @param nillable indicates whether an element with xsi:nil=true satisifies the test
     */

	public ContentTypeTest(int nodeKind, SchemaType schemaType, Configuration config, boolean nillable) {
		this.kind = nodeKind;
        this.schemaType = schemaType;
        this.schemaTypeFingerprint = schemaType.getFingerprint();
        this.config = config;
        this.nillable = nillable;
	}

    /**
     * Indicate whether nilled elements should be matched (the default is false)
     * @param nillable true if nilled elements should be matched
     */
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    /**
     * The test is nillable if a question mark was specified as the occurrence indicator
     * @return true if the test is nillable
     */

    public boolean isNillable() {
        return nillable;
    }

    public SchemaType getSchemaType() {
        return schemaType;
    }

    public int getNodeKind() {
        return kind;
    }

    public ItemType getSuperType(TypeHierarchy th) {
        return NodeKindTest.makeNodeKindTest(kind);
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
        return kind == nodeKind && matchesAnnotation(annotation);
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

    public boolean matches(/*@NotNull*/ TinyTree tree, int nodeNr) {
        return kind == tree.getNodeKind(nodeNr) &&
                matchesAnnotation(tree.getTypeAnnotation(nodeNr)) &&
                (nillable || !tree.isNilled(nodeNr));
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     * @param node the node to be matched
     */

    public boolean matches(/*@NotNull*/ NodeInfo node) {
        return node.getNodeKind() == kind &&
                matchesAnnotation(node.getTypeAnnotation())
                && (nillable || !Nilled.isNilled(node));
    }

    private boolean matchesAnnotation(int annotation) {
        if (schemaTypeFingerprint == StandardNames.XS_ANY_TYPE) {
            return true;
        }

        if (annotation == schemaTypeFingerprint) {
            return true;
        }

        // see if the type annotation is a subtype of the required type


        try {
            SchemaType type = config.getSchemaType(annotation).getBaseType();
            if (type == null) {
                // only true if annotation = XS_ANY_TYPE
                return false;
            }
            ItemType actual = new ContentTypeTest(kind, type, config, false);
            return config.getTypeHierarchy().isSubType(actual, this);
        } catch (UnresolvedReferenceException e) {
            throw new IllegalStateException(e.getMessage());
        }
        //return false;
    }

    /**
    * Determine the default priority of this node test when used on its own as a Pattern
    */

    public final double getDefaultPriority() {
    	return 0;
    }

    /**
    * Determine the types of nodes to which this pattern applies. Used for optimisation.
    * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
    */

    public int getPrimitiveType() {
        return kind;
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        return 1<<kind;
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    public SchemaType getContentType() {
        return schemaType;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        SchemaType type = schemaType;
        if (type.isAtomicType()) {
            return (AtomicType)type;
        } else if (type instanceof ListType) {
            SimpleType mem = ((ListType)type).getItemType();
            if (mem.isAtomicType()) {
                return (AtomicType)mem;
            }
        } else if (type instanceof ComplexType && ((ComplexType)type).isSimpleContent()) {
            SimpleType ctype = ((ComplexType)type).getSimpleContentType();
            assert ctype != null;
            if (ctype.isAtomicType()) {
                return (AtomicType)ctype;
            } else if (ctype instanceof ListType) {
                SimpleType mem = ((ListType)ctype).getItemType();
                if (mem.isAtomicType()) {
                    return (AtomicType)mem;
                }
            }
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        return !(schemaType.isComplexType() &&
                ((ComplexType)schemaType).getVariety() == ComplexType.VARIETY_ELEMENT_ONLY);
    }

    /**
     * Visit all the schema components used in this ItemType definition
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        visitor.visitSchemaComponent(schemaType);
    }

    public String toString() {
        return (kind == Type.ELEMENT ? "element(*, " : "attribute(*, ") +
                        schemaType.getDescription() + ')';
    }

    /**
      * Returns a hash code value for the object.
      */

     public int hashCode() {
         return kind<<20 ^ schemaTypeFingerprint;
     }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof ContentTypeTest &&
                ((ContentTypeTest)other).kind == kind &&
                ((ContentTypeTest)other).schemaType == schemaType &&
                ((ContentTypeTest)other).schemaTypeFingerprint == schemaTypeFingerprint &&
                ((ContentTypeTest)other).nillable == nillable;
    }

}

