////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyAxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;


/**
 * A StrippedNode is a view of a node, in a virtual tree that has whitespace
 * text nodes stripped from it. All operations on the node produce the same result
 * as operations on the real underlying node, except that iterations over the axes
 * take care to skip whitespace-only text nodes that are supposed to be stripped.
 * Note that this class is only used in cases where a pre-built tree is supplied as
 * the input to a transformation, and where the stylesheet does whitespace stripping;
 * if a SAXSource or StreamSource is supplied, whitespace is stripped as the tree
 * is built.
*/

public class SpaceStrippedNode extends AbstractVirtualNode implements WrappingFunction {

    protected SpaceStrippedNode() {}

    /**
     * This constructor is protected: nodes should be created using the makeWrapper
     * factory method
     * @param node    The node to be wrapped
     * @param parent  The StrippedNode that wraps the parent of this node
     */

    protected SpaceStrippedNode(NodeInfo node, SpaceStrippedNode parent) {
        this.node = node;
        this.parent = parent;
    }

    /**
     * Factory method to wrap a node with a wrapper that implements the Saxon
     * NodeInfo interface.
     * @param node        The underlying node
     * @param docWrapper  The wrapper for the document node (must be supplied)
     * @param parent      The wrapper for the parent of the node (null if unknown)
     * @return            The new wrapper for the supplied node
     */

    /*@NotNull*/ protected static SpaceStrippedNode makeWrapper(NodeInfo node,
                                       SpaceStrippedDocument docWrapper,
                                       SpaceStrippedNode parent) {
        SpaceStrippedNode wrapper = new SpaceStrippedNode(node, parent);
        wrapper.docWrapper = docWrapper;
        return wrapper;
    }

    /**
     * Factory method to wrap a node within the same document as this node with a VirtualNode
     * @param node        The underlying node
     * @param parent      The wrapper for the parent of the node (null if unknown)
     * @return            The new wrapper for the supplied node
     */

    /*@NotNull*/ public VirtualNode makeWrapper(NodeInfo node, VirtualNode parent) {
        SpaceStrippedNode wrapper = new SpaceStrippedNode(node, (SpaceStrippedNode)parent);
        wrapper.docWrapper = this.docWrapper;
        return wrapper;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value.
     * @since 8.5
     */

    public AtomicSequence atomize() throws XPathException {
        if (getNodeKind() == Type.ELEMENT) {
            return getSchemaType().atomize(this);
        } else {
            return node.atomize();
        }
    }

    /**
    * Determine whether this is the same node as another node. <br />
    * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
    * @return true if this Node object and the supplied Node object represent the
    * same node in the tree.
    */

    public boolean isSameNodeInfo(/*@NotNull*/ NodeInfo other) {
        if (other instanceof SpaceStrippedNode) {
            return node.isSameNodeInfo(((SpaceStrippedNode)other).node);
        } else {
            return node.isSameNodeInfo(other);
        }
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
        if (other instanceof SpaceStrippedNode) {
            return node.compareOrder(((SpaceStrippedNode)other).node);
        } else {
            return node.compareOrder(other);
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
    @Override
    public int comparePosition(NodeInfo other) {
        if (other instanceof SpaceStrippedNode) {
            return node.comparePosition(((SpaceStrippedNode)other).node);
        } else {
            return node.comparePosition(other);
        }
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public CharSequence getStringValueCS() {
        // Might not be the same as the string value of the underlying node because of space stripping
        switch (getNodeKind()) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                AxisIterator iter = iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.makeNodeKindTest(Type.TEXT));
                FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
                while(true) {
                    NodeInfo it = iter.next();
                    if (it == null) {
                        break;
                    }
                    sb.append(it.getStringValueCS());
                }
                return sb.condense();
            default:
                return node.getStringValueCS();
        }
    }

    /**
    * Get the NodeInfo object representing the parent of this node
    */

    /*@Nullable*/ public NodeInfo getParent() {
        if (parent==null) {
            NodeInfo realParent = node.getParent();
            if (realParent != null) {
                parent = makeWrapper(realParent, (SpaceStrippedDocument)docWrapper, null);
            }
        }
        return parent;
    }

    /**
    * Return an iteration over the nodes reached by the given axis from this node
    * @param axisNumber the axis to be used
    * @return a SequenceIterator that scans the nodes reached by the axis in turn.
    */

    /*@Nullable*/ public AxisIterator iterateAxis(byte axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.NAMESPACE:
                return new WrappingIterator(node.iterateAxis(axisNumber), this, this);
            case AxisInfo.CHILD:
                return new StrippingIterator(node.iterateAxis(axisNumber), this);
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.PRECEDING_SIBLING:
                SpaceStrippedNode parent = (SpaceStrippedNode)getParent();
                if (parent == null) {
                    return EmptyAxisIterator.emptyAxisIterator();
                } else {
                    return new StrippingIterator(node.iterateAxis(axisNumber), parent);
                }
            default:
                return new StrippingIterator(node.iterateAxis(axisNumber), null);
        }
    }

    /**
    * Copy this node to a given outputter (deep copy)
    */

    public void copy(Receiver out, int copyOptions, int locationId) throws XPathException {
        // The underlying code does not do whitespace stripping. So we need to interpose
        // a stripper.
        Stripper stripper = new Stripper(((SpaceStrippedDocument)docWrapper).getStrippingRule(), out);
        node.copy(stripper, copyOptions, locationId);
    }


    /**
     * A StrippingIterator delivers wrappers for the nodes delivered
     * by its underlying iterator. It is used when whitespace stripping
     * may be needed, e.g. for the child axis. It examines all text nodes
     * encountered to see if they need to be stripped, and if so, it
     * skips them.
     */

    private final class StrippingIterator implements AxisIterator {

        AxisIterator base;
        SpaceStrippedNode parent;
        NodeInfo currentVirtualNode;
        int position;

        /**
         * Create a StrippingIterator
         * @param base The underlying iterator
         * @param parent If all the nodes to be wrapped have the same parent,
         * it can be specified here. Otherwise specify null.
         */

        public StrippingIterator(AxisIterator base, SpaceStrippedNode parent) {
            this.base = base;
            this.parent = parent;
            position = 0;
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


        /*@Nullable*/ public NodeInfo next() {
            NodeInfo nextRealNode;
            while (true) {
                nextRealNode = (NodeInfo)base.next();
                if (nextRealNode==null) {
                    return null;
                }
                if (isPreserved(nextRealNode)) {
                    break;
                }
                // otherwise skip this whitespace text node
            }

            currentVirtualNode = makeWrapper(nextRealNode,(SpaceStrippedDocument)docWrapper, parent);
            position++;
            return currentVirtualNode;
        }

        private boolean isPreserved(/*@NotNull*/ NodeInfo nextRealNode) {
            if (nextRealNode.getNodeKind() != Type.TEXT) {
                return true;
            }
            if (!Whitespace.isWhite(nextRealNode.getStringValueCS())) {
                return true;
            }
            NodeInfo actualParent =
                    (parent==null ? nextRealNode.getParent() : parent.node);

            // if the node has a simple type annotation, it is preserved
            SchemaType type = actualParent.getSchemaType();
            if (type.isSimpleType() || ((ComplexType)type).isSimpleContent()) {
                return true;
            }

            // if there is an ancestor with xml:space="preserve", it is preserved
            if (((SpaceStrippedDocument)docWrapper).containsPreserveSpace()) {
                NodeInfo p = actualParent;
                // the document contains one or more xml:space="preserve" attributes, so we need to see
                // if one of them is on an ancestor of this node
                while (p.getNodeKind() == Type.ELEMENT) {
                    String val = p.getAttributeValue(NamespaceConstant.XML, "space");
                    if (val != null) {
                        if ("preserve".equals(val)) {
                            return true;
                        } else if ("default".equals(val)) {
                            break;
                        }
                    }
                    p = p.getParent();
                }
            }

            // if there is an ancestor whose type has an assertion, it is preserved
            if (((SpaceStrippedDocument)docWrapper).containsAssertions()) {
                NodeInfo p = actualParent;
                // the document contains one or more xml:space="preserve" attributes, so we need to see
                // if one of them is on an ancestor of this node
                while (p.getNodeKind() == Type.ELEMENT) {
                    SchemaType t = p.getSchemaType();
                    if (t instanceof ComplexType && ((ComplexType)t).hasAssertions()) {
                        return true;
                    }
                    p = p.getParent();
                }
            }

            // otherwise it depends on xsl:strip-space
            try {
                byte preserve = ((SpaceStrippedDocument)docWrapper).getStrippingRule().isSpacePreserving(new NameOfNode(actualParent));
                return preserve == Stripper.ALWAYS_PRESERVE;
            } catch (XPathException e) {
                // Ambiguity between strip-space and preserve-space. Because we're in an axis iterator,
                // we don't get an opportunity to fail, so take the recovery action.
                return true;
            }
        }

        public NodeInfo current() {
            return currentVirtualNode;
        }

        public int position() {
            return position;
        }

        public void close() {
            base.close();
        }

        /**
         * Return an iterator over an axis, starting at the current node.
         *
         * @param axis the axis to iterate over, using a constant such as
         *             {@link net.sf.saxon.om.AxisInfo#CHILD}
         * @param test a predicate to apply to the nodes before returning them.
         * @throws NullPointerException if there is no current node
         */

        public AxisIterator iterateAxis(byte axis, NodeTest test) {
            return currentVirtualNode.iterateAxis(axis, test);
        }

        /**
         * Return the atomized value of the current node.
         *
         * @return the atomized value.
         * @throws NullPointerException if there is no current node
         */

        public Sequence atomize() throws XPathException {
            return currentVirtualNode.atomize();
        }

        /**
         * Return the string value of the current node.
         *
         * @return the string value, as an instance of CharSequence.
         * @throws NullPointerException if there is no current node
         */

        public CharSequence getStringValue() {
            return currentVirtualNode.getStringValue();
        }

        /*@NotNull*/ public AxisIterator getAnother() {
            return new StrippingIterator(base.getAnother(), parent);
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
            return 0;
        }

    }  // end of class StrippingIterator



}

