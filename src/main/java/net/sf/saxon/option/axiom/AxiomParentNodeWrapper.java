////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.UntypedAtomicValue;
import org.apache.axiom.om.OMContainer;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;

import java.util.Iterator;

/**
 * A node in the XDM tree; specifically, a node that wraps an Axiom document node or element node.

 * @author Michael H. Kay
 */
public abstract class AxiomParentNodeWrapper extends AbstractNodeWrapper
        implements SiblingCountingNode {

    protected OMContainer node;

    protected AxiomParentNodeWrapper(OMContainer node) {
        this.node = node;
    }

    /**
     * Get the underlying Axiom node, to implement the VirtualNode interface
     */

    public Object getUnderlyingNode() {
        return node;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result
     *         will always be an AtomicValue. In other cases it may be a Value
     *         representing a sequence whose items are atomic values.
     */

    public AtomicSequence atomize() {
        return new UntypedAtomicValue(getStringValueCS());
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public CharSequence getStringValueCS() {
        FastStringBuffer buff = new FastStringBuffer((FastStringBuffer.MEDIUM));
        for (Iterator iter = node.getDescendants(false); iter.hasNext(); ) {
            OMNode next = (OMNode) iter.next();
            if (next instanceof OMText) {
                buff.append(((OMText) next).getText());
            }
        }
        return buff.condense();
    }

    /**
     * Determine whether the node has any children. <br />
     * Note: the result is equivalent to <br />
     * getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()
     */

    public boolean hasChildNodes() {
        return node.getFirstOMChild() != null;
    }

    /**
     * Get a character string that uniquely identifies this node. Note:
     * a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all documents
     */

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
    }

    @Override
    protected final AxisIterator<NodeInfo> iterateChildren(NodeTest nodeTest) {
        return new ChildWrappingIterator(this, nodeTest);
    }

    @Override
    protected AxisIterator<NodeInfo> iterateDescendants(NodeTest nodeTest, boolean includeSelf) {
        // Note: for unknown reasons, this method is really slow. See XMark test q7.
        return new DescendantWrappingIterator(this, nodeTest, includeSelf);
    }

    /**
     * Abstract iterator that takes an iterator over nodes in the Axiom tree and
     * wraps it with an implementation of Saxon's AxisIterator that wraps each
     * successive node as it is found.
     */

    private abstract class AxiomWrappingIterator extends AxisIteratorImpl {
        Iterator base;
        NodeTest nodeTest;

        public AxiomWrappingIterator(Iterator base, NodeTest nodeTest) {
            this.base = base;
            this.nodeTest = nodeTest;
        }

        public NodeInfo next() {
            while (true) {
                if (base.hasNext()) {
                    Object node = base.next();
                    NodeInfo wrapper = wrap(node);
                    if (nodeTest.matches(wrapper)) {
                        position++;
                        return (current = wrapper);
                    }
                } else {
                    position = -1;
                    return (current = null);
                }
            }
        }

        protected abstract NodeInfo wrap(Object node);
    }

    /**
     * Iterator over the descendants of a supplied node (optionally including the node itself)
     */

    protected class DescendantWrappingIterator extends AxiomWrappingIterator {
        AxiomDocumentWrapper docWrapper;
        boolean includeSelf;

        public DescendantWrappingIterator(AxiomParentNodeWrapper parentWrapper, NodeTest nodeTest, boolean includeSelf) {
            super(node.getDescendants(includeSelf), nodeTest);
            this.includeSelf = includeSelf;
            if (parentWrapper instanceof AxiomDocumentWrapper) {
                docWrapper = (AxiomDocumentWrapper)parentWrapper;
            } else {
                docWrapper = ((AxiomElementNodeWrapper)parentWrapper).docWrapper;
            }
        }

        public AxisIterator<NodeInfo> getAnother() {
            return new DescendantWrappingIterator(docWrapper, nodeTest, includeSelf);
        }

        protected NodeInfo wrap(Object node) {
            if (node instanceof OMDocument) {
                return docWrapper;
            } else {
                return AxiomDocumentWrapper.makeWrapper((OMNode)node, docWrapper, null, -1);
            }
        }
    }

    /**
     * Iterator over the children of a supplied node
     */

    protected class ChildWrappingIterator extends AxiomWrappingIterator {

        AxiomParentNodeWrapper commonParent;
        AxiomDocumentWrapper docWrapper;
        int index = 0;

        public ChildWrappingIterator(AxiomParentNodeWrapper commonParent, NodeTest nodeTest) {
            super(node.getChildren(), nodeTest);
            this.commonParent = commonParent;
            this.nodeTest = nodeTest;
            if (commonParent instanceof AxiomDocumentWrapper) {
                docWrapper = (AxiomDocumentWrapper)commonParent;
            } else {
                docWrapper = ((AxiomElementNodeWrapper)commonParent).docWrapper;
            }
        }

        public AxisIterator<NodeInfo> getAnother() {
            return new ChildWrappingIterator(commonParent, nodeTest);
        }

        protected NodeInfo wrap(Object node) {
            return AxiomDocumentWrapper.makeWrapper((OMNode)node, docWrapper, commonParent, index++);
        }
    }



}

