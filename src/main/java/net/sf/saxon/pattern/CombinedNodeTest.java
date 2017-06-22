////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntSet;

/**
 * A CombinedNodeTest combines two nodetests using one of the operators
 * union (=or), intersect (=and), difference (= "and not"). This arises
 * when optimizing a union (etc) of two path expressions using the same axis.
 * A CombinedNodeTest is also used to support constructs such as element(N,T),
 * which can be expressed as (element(N,*) AND element(*,T))
 *
 * @author Michael H. Kay
 */

public class CombinedNodeTest extends NodeTest {

    private NodeTest nodetest1;
    private NodeTest nodetest2;
    private int operator;

    /**
     * Create a NodeTest that combines two other node tests
     *
     * @param nt1      the first operand. Note that if the defaultPriority of the pattern
     *                 is required, it will be taken from that of the first operand.
     * @param operator one of Token.UNION, Token.INTERSECT, Token.EXCEPT
     * @param nt2      the second operand
     */

    public CombinedNodeTest(NodeTest nt1, int operator, NodeTest nt2) {
        nodetest1 = nt1;
        this.operator = operator;
        nodetest2 = nt2;
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
        switch (operator) {
            case Token.UNION:
                return nodetest1 == null ||
                        nodetest2 == null ||
                        nodetest1.matches(nodeKind, name, annotation) ||
                        nodetest2.matches(nodeKind, name, annotation);
            case Token.INTERSECT:
                return (nodetest1 == null || nodetest1.matches(nodeKind, name, annotation)) &&
                        (nodetest2 == null || nodetest2.matches(nodeKind, name, annotation));
            case Token.EXCEPT:
                return (nodetest1 == null || nodetest1.matches(nodeKind, name, annotation)) &&
                        !(nodetest2 == null || nodetest2.matches(nodeKind, name, annotation));
            default:
                throw new IllegalArgumentException("Unknown operator in Combined Node Test");
        }
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
        switch (operator) {
            case Token.UNION:
                return nodetest1 == null ||
                        nodetest2 == null ||
                        nodetest1.matches(tree, nodeNr) ||
                        nodetest2.matches(tree, nodeNr);
            case Token.INTERSECT:
                return (nodetest1 == null || nodetest1.matches(tree, nodeNr)) &&
                        (nodetest2 == null || nodetest2.matches(tree, nodeNr));
            case Token.EXCEPT:
                return (nodetest1 == null || nodetest1.matches(tree, nodeNr)) &&
                        !(nodetest2 == null || nodetest2.matches(tree, nodeNr));
            default:
                throw new IllegalArgumentException("Unknown operator in Combined Node Test");
        }
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    public boolean matches(/*@NotNull*/ NodeInfo node) {
        switch (operator) {
            case Token.UNION:
                return nodetest1 == null ||
                        nodetest2 == null ||
                        nodetest1.matches(node) ||
                        nodetest2.matches(node);
            case Token.INTERSECT:
                return (nodetest1 == null || nodetest1.matches(node)) &&
                        (nodetest2 == null || nodetest2.matches(node));
            case Token.EXCEPT:
                return (nodetest1 == null || nodetest1.matches(node)) &&
                        !(nodetest2 == null || nodetest2.matches(node));
            default:
                throw new IllegalArgumentException("Unknown operator in Combined Node Test");
        }
    }

    public String toString() {
        if (nodetest1 instanceof NameTest && operator == Token.INTERSECT) {
            int kind = nodetest1.getPrimitiveType();
            String skind = (kind == Type.ELEMENT ? "element(" : "attribute(");
            String content = "";
            if (nodetest2 instanceof ContentTypeTest) {
                final SchemaType schemaType = ((ContentTypeTest) nodetest2).getSchemaType();
                content = ", " + schemaType.getDisplayName();
            }
            String name;
            if (nodetest1 instanceof NameTest) {
                name = ((NameTest)nodetest1).getNodeName().getDisplayName();
            } else {
                name = nodetest1.toString();
            }
            return skind + name + content + ')';
        } else {
            String nt1 = (nodetest1 == null ? "true()" : nodetest1.toString());
            String nt2 = (nodetest2 == null ? "true()" : nodetest2.toString());
            return '(' + nt1 + ' ' + Token.tokens[operator] + ' ' + nt2 + ')';
        }
    }


    /**
     * Get the supertype of this type. This isn't actually a well-defined concept: the types
     * form a lattice rather than a strict hierarchy.
     *
     * @param th the type hierarchy cache
     */

    public ItemType getSuperType(TypeHierarchy th) {
        switch (operator) {
            case Token.UNION:
                return Type.getCommonSuperType(nodetest1, nodetest2, th);
            case Token.INTERSECT:
                return nodetest1;
            case Token.EXCEPT:
                return nodetest1;
            default:
                throw new IllegalArgumentException("Unknown operator in Combined Node Test");
        }
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1&lt;&lt;Type.ELEMENT for element nodes, 1&lt;&lt;Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        switch (operator) {
            case Token.UNION:
                return nodetest1.getNodeKindMask() | nodetest2.getNodeKindMask();
            case Token.INTERSECT:
                return nodetest1.getNodeKindMask() & nodetest2.getNodeKindMask();
            case Token.EXCEPT:
                return nodetest1.getNodeKindMask();
            default:
                return 0;
        }

    }

    /**
     * Get the basic kind of object that this ItemType matches: for a NodeTest, this is the kind of node,
     * or Type.Node if it matches different kinds of nodes.
     *
     * @return the node kind matched by this node test
     */

    public int getPrimitiveType() {
        int mask = getNodeKindMask();
        if (mask == (1 << Type.ELEMENT)) {
            return Type.ELEMENT;
        }
        if (mask == (1 << Type.ATTRIBUTE)) {
            return Type.ATTRIBUTE;
        }
        if (mask == (1 << Type.DOCUMENT)) {
            return Type.DOCUMENT;
        }
        return Type.NODE;
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * A null value indicates that all names are permitted (i.e. that there are no constraints on the node name).
     * The default implementation returns null.
     */

    /*@NotNull*/
    public IntSet getRequiredNodeNames() {
        IntSet s1 = nodetest1.getRequiredNodeNames();
        IntSet s2 = nodetest2.getRequiredNodeNames();
        switch (operator) {
            case Token.UNION: {
                return s1.union(s2);
            }
            case Token.INTERSECT: {
                return s1.intersect(s2);
            }
            case Token.EXCEPT: {
                return s1.except(s2);
            }
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    public SchemaType getContentType() {
        SchemaType type1 = nodetest1.getContentType();
        SchemaType type2 = nodetest2.getContentType();
        if (type1.isSameType(type2)) return type1;
        if (operator == Token.INTERSECT) {
            if (type2 instanceof AnyType || (type2 instanceof AnySimpleType && type1.isSimpleType())) {
                return type1;
            }
            if (type1 instanceof AnyType || (type1 instanceof AnySimpleType && type2.isSimpleType())) {
                return type2;
            }
        }
        return AnyType.getInstance();
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        AtomicType type1 = nodetest1.getAtomizedItemType();
        AtomicType type2 = nodetest2.getAtomizedItemType();
        if (type1.isSameType(type2)) return type1;
        if (operator == Token.INTERSECT) {
            if (type2.equals(BuiltInAtomicType.ANY_ATOMIC)) {
                return type1;
            }
            if (type1.equals(BuiltInAtomicType.ANY_ATOMIC)) {
                return type2;
            }
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        return nodetest1.isAtomizable() && nodetest2.isAtomizable();
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return -1 if the node test matches nodes of more than one name
     */

    public int getFingerprint() {
        int fp1 = nodetest1.getFingerprint();
        int fp2 = nodetest2.getFingerprint();
        if (fp1 == fp2) return fp1;
        if (fp2 == -1 && operator == Token.INTERSECT) return fp1;
        if (fp1 == -1 && operator == Token.INTERSECT) return fp2;
        return -1;
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */

    public boolean isNillable() {
        // this should err on the safe side
        return nodetest1.isNillable() && nodetest2.isNillable();
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodetest1.hashCode() ^ nodetest2.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof CombinedNodeTest &&
                ((CombinedNodeTest) other).nodetest1.equals(nodetest1) &&
                ((CombinedNodeTest) other).nodetest2.equals(nodetest2) &&
                ((CombinedNodeTest) other).operator == operator;
    }

    /**
     * Get the default priority of this nodeTest when used as a pattern. In the case of a union, this will always
     * be (arbitrarily) the default priority of the first operand. In other cases, again somewhat arbitrarily, it
     * is 0.25, reflecting the common usage of an intersection to represent the pattern element(E, T).
     */

    public double getDefaultPriority() {
        if (operator == Token.UNION) {
            return nodetest1.getDefaultPriority();
        } else {
            // typically it's element(E, T)
            return 0.25;
        }
    }

    /**
     * Get the two parts of the combined node test
     *
     * @return the two operands
     */

    /*@NotNull*/
    public NodeTest[] getComponentNodeTests() {
        return new NodeTest[]{nodetest1, nodetest2};
    }

    /**
     * Get the operator used to combine the two node tests: one of {@link net.sf.saxon.expr.parser.Token#UNION},
     * {@link net.sf.saxon.expr.parser.Token#INTERSECT}, {@link net.sf.saxon.expr.parser.Token#EXCEPT},
     *
     * @return the operator
     */

    public int getOperator() {
        return operator;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        nodetest1.visitNamedSchemaComponents(visitor);
        nodetest2.visitNamedSchemaComponents(visitor);
    }
}

