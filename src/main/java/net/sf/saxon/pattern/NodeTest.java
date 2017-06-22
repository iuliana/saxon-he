////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntUniversalSet;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;

import java.io.Serializable;

/**
  * A NodeTest is a simple kind of pattern that enables a context-free test of whether
  * a node matches a given node kind and name. There are several kinds of node test: a full name test, a prefix test, and an
  * "any node of a given type" test, an "any node of any type" test, a "no nodes"
  * test (used, e.g. for "@comment()").
  *
  * <p>As well as being used to support XSLT pattern matching, NodeTests act as predicates in
  * axis steps, and also act as item types for type matching.</p>
  *
  * <p>For use in user-written application calling {@link NodeInfo#iterateAxis(byte, NodeTest)},
 * it is possible to write a user-defined subclass of <code>NodeTest</code> that implements
 * a single method, {@link #matches(int, NodeName, int)}</p>
  *
  * @author Michael H. Kay
  */

public abstract class NodeTest implements ItemType, Serializable {

    public boolean matches(Item item, /*@NotNull*/ XPathContext context) {
        return matchesItem(item, false, context.getConfiguration());
    }

    /**
     * Test whether a given item conforms to this type. This implements a method of the ItemType interface.
     * @param item The item to be tested
     * @param allowURIPromotion true of promotion of anyURI to string is permitted
     * @param config the Saxon Configuration
     * @return true if the item is an instance of this type; false otherwise
    */

    public boolean matchesItem(Item item, boolean allowURIPromotion, Configuration config) {
        return item instanceof NodeInfo && matches((NodeInfo)item);
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
        return AnyNodeTest.getInstance();
        // overridden for AnyNodeTest itself
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    /*@NotNull*/ public ItemType getPrimitiveItemType() {
        int p = getPrimitiveType();
        if (p == Type.NODE) {
            return AnyNodeTest.getInstance();
        } else {
            return NodeKindTest.makeNodeKindTest(p);
        }
    }

    /**
     * Get the basic kind of object that this ItemType matches: for a NodeTest, this is the kind of node,
     * or Type.Node if it matches different kinds of nodes.
     * @return the node kind matched by this node test
     */

    public int getPrimitiveType() {
        return Type.NODE;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return -1 if the node test matches nodes of more than one name
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return false: this is not ANY_ATOMIC_TYPE or a subtype thereof
     */

    public boolean isPlainType() {
        return false;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/ public AtomicType getAtomizedItemType() {
        // This is overridden for a ContentTypeTest
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        // This is overridden for a ContentTypeTest
        return true;
    }

    /**
     * Test whether this node test is satisfied by a given node on a TinyTree. The node
     * must be a document, element, text, comment, or processing instruction node.
     * This method is provided so that when navigating a TinyTree a node can be rejected without
     * actually instantiating a NodeInfo object. The default implementation instantiates the node
     * and then calls the method {@link #matches(NodeInfo)}
     * @param tree the TinyTree containing the node
     * @param nodeNr the number of the node within the TinyTree
     * @return true if the node matches the NodeTest, otherwise false
     *
    */

    public boolean matches(/*@NotNull*/ TinyTree tree, int nodeNr) {
        return matches(tree.getNode(nodeNr));
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
     * @param nodeKind The kind of node to be matched
     * @param name identifies the expanded name of the node to be matched.
     *  The value should be null for a node with no name.
     * @param annotation The actual content type of the node
     * @return true if the node matches this node tst
     *
    */

    public abstract boolean matches(int nodeKind, NodeName name, int annotation);

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes. The default implementation calls the method
     * {@link #matches(int, NodeName, int)}
     * @param node the node to be matched
     * @return true if the node test is satisfied by the supplied node, false otherwise
     */

    public boolean matches(/*@NotNull*/ NodeInfo node) {
        return matches(node.getNodeKind(), new NameOfNode(node), node.getTypeAnnotation());
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on. The default
     * implementation indicates that nodes of all kinds are matched.
     * @return a combination of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on
     */

    public int getNodeKindMask() {
        return 1<<Type.ELEMENT | 1<<Type.TEXT | 1<<Type.COMMENT | 1<<Type.PROCESSING_INSTRUCTION |
                1<<Type.ATTRIBUTE | 1<<Type.NAMESPACE | 1<<Type.DOCUMENT;
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     * @return the type annotation that all nodes matching this NodeTest must satisfy
     */

    public SchemaType getContentType() {
        int m = getNodeKindMask();
        switch (m) {
            case 1<<Type.DOCUMENT:
                return AnyType.getInstance();
            case 1<<Type.ELEMENT:
                return AnyType.getInstance();
            case 1<<Type.ATTRIBUTE:
                return AnySimpleType.getInstance();
            case 1<<Type.COMMENT:
                return BuiltInAtomicType.STRING;
            case 1<<Type.TEXT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case 1<<Type.PROCESSING_INSTRUCTION:
                return BuiltInAtomicType.STRING;
            case 1<<Type.NAMESPACE:
                return BuiltInAtomicType.STRING;
            default:
                return AnyType.getInstance();
        }
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * If all names are permitted (i.e. there are no constraints on the node name), returns IntUniversalSet.getInstance().
     * The default implementation returns the universal set.
     * @return the set of integer fingerprints of the node names that this node test can match.
     */

    /*@NotNull*/ public IntSet getRequiredNodeNames() {
        return IntUniversalSet.getInstance();
    }

    /**
     * Determine whether the content type (if present) is nillable
     * @return true if the content test (when present) can match nodes that are nilled
     */

    public boolean isNillable() {
        return true;
    }

    /**
      * Visit all the schema components used in this ItemType definition
      * @param visitor the visitor class to be called when each component is visited
      */

     public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
         // no action
     }


}

