////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;

/**
  * ParentNodeImpl is an implementation of a non-leaf node (specifically, an Element node
  * or a Document node)
  * @author Michael H. Kay
  */


abstract class ParentNodeImpl extends NodeImpl {

    /*@Nullable*/ private Object children = null;       // null for no children
                                          // a NodeImpl for a single child
                                          // a NodeImpl[] for >1 child

    private int sequence;               // sequence number allocated during original tree creation.
                                          // set to -1 for nodes added subsequently by XQuery update

    /**
     * Get the node sequence number (in document order). Sequence numbers are monotonic but not
     * consecutive. In the current implementation, parent nodes (elements and document nodes) have a zero
     * least-significant word, while namespaces, attributes, text nodes, comments, and PIs have
     * the top word the same as their owner and the bottom half reflecting their relative position.
     * For nodes added by XQUery Update, the sequence number is -1L
     * @return the sequence number if there is one, or -1L otherwise.
    */

    protected final long getSequenceNumber() {
        return (getRawSequenceNumber() == -1 ? -1L : ((long)getRawSequenceNumber())<<32);
    }

    protected final int getRawSequenceNumber() {
        return sequence;
    }

    protected final void setRawSequenceNumber(int seq) {
        sequence = seq;
    }

    /**
     * Set the children of this node
     * @param children null if there are no children, a single NodeInfo if there is one child, an array of NodeInfo
     * if there are multiple children
     */

    protected final void setChildren(Object children) {
        this.children = children;
    }

    /**
    * Determine if the node has any children.
    */

    public final boolean hasChildNodes() {
        return (children!=null);
    }

    /**
     * Determine how many children the node has
     * @return the number of children of this parent node
     */

    public final int getNumberOfChildren() {
        if (children == null) {
            return 0;
        } else if (children instanceof NodeImpl) {
            return 1;
        } else {
            return ((NodeInfo[])children).length;
        }
    }

    /**
     * Get an enumeration of the children of this node
     * @param test A NodeTest to be satisfied by the child nodes, or null
     * if all child node are to be returned
     * @return an iterator over the children of this node
    */

    protected final AxisIterator iterateChildren(/*@Nullable*/ NodeTest test) {
        if (children==null) {
            return EmptyAxisIterator.emptyAxisIterator();
        } else if (children instanceof NodeImpl) {
            NodeImpl child = (NodeImpl)children;
            if (test == null || test == AnyNodeTest.getInstance()) {
                return SingleNodeIterator.makeIterator(child);
            } else {
                return Navigator.filteredSingleton(child, test);
            }
        } else {
            if (test == null || test == AnyNodeTest.getInstance()) {
                return new AxisIteratorOverSequence<NodeImpl>(
                        new ArrayIterator<NodeImpl>((NodeImpl[])children));
            } else {
                return new ChildEnumeration(this, test);
            }
        }
    }


    /**
    * Get the first child node of the element
    * @return the first child node of the required type, or null if there are no children
    */

    /*@Nullable*/ public final NodeImpl getFirstChild() {
        if (children==null) return null;
        if (children instanceof NodeImpl) return (NodeImpl)children;
        return ((NodeImpl[])children)[0];
    }

    /**
    * Get the last child node of the element
    * @return the last child of the element, or null if there are no children
    */

    /*@Nullable*/ public final NodeImpl getLastChild() {
        if (children==null) return null;
        if (children instanceof NodeImpl) return (NodeImpl)children;
        NodeImpl[] n = (NodeImpl[])children;
        return n[n.length-1];
    }

    /**
     * Get the nth child node of the element (numbering from 0)
     * @param n identifies the required child
     * @return the last child of the element, or null if there is no n'th child
    */

    /*@Nullable*/ protected final NodeImpl getNthChild(int n) {
        if (children==null) return null;
        if (children instanceof NodeImpl) {
            return (n==0 ? (NodeImpl)children : null);
        }
        NodeImpl[] nodes = (NodeImpl[])children;
        if (n<0 || n>=nodes.length) return null;
        return nodes[n];
    }

    /**
     * Remove a given child
     * @param child the child to be removed
     */

    protected void removeChild(NodeImpl child) {
        if (children == null) {
            return;
        }
        if (children == child) {
            children = null;
            return;
        }
        NodeImpl[] nodes = (NodeImpl[])children;
        for (int i=0; i<nodes.length; i++) {
            if (nodes[i] == child) {
                if (nodes.length == 2) {
                    children = nodes[1-i];
                } else {
                    NodeImpl[] n2 = new NodeImpl[nodes.length - 1];
                    if (i > 0) {
                        System.arraycopy(nodes, 0, n2, 0, i);
                    }
                    if (i < nodes.length - 1) {
                        System.arraycopy(nodes, i+1, n2, i, nodes.length-i-1);
                    }
                    children = cleanUpChildren(n2);
                }
                break;
            }
        }
    }

    /**
     * Tidy up the children of the node. Merge adjacent text nodes; remove zero-length text nodes;
     * reallocate index numbers to each of the children
     * @param children the existing children
     * @return the replacement array of children
     */

    /*@NotNull*/ private NodeImpl[] cleanUpChildren(/*@NotNull*/ NodeImpl[] children) {
        boolean prevText = false;
        int j = 0;
        NodeImpl[] c2 = new NodeImpl[children.length];
        for (int i=0; i<children.length; i++) {
            NodeImpl node = children[i];
            if (node instanceof TextImpl) {
                if (prevText) {
                    TextImpl prev = ((TextImpl)c2[j-1]);
                    prev.replaceStringValue(prev.getStringValue() + node.getStringValue());
                } else if (node.getStringValue().length() > 0) {
                    prevText = true;
                    node.setSiblingPosition(j);
                    c2[j++] = node;
                }
            } else {
                node.setSiblingPosition(j);
                c2[j++] = node;
                prevText = false;
            }
        }
        if (j == c2.length) {
            return c2;
        } else {
            NodeImpl[] c3 = new NodeImpl[j];
            System.arraycopy(c2, 0, c3, 0, j);
            return c3;
        }
    }


    /**
    * Return the string-value of the node, that is, the concatenation
    * of the character content of all descendent elements and text nodes.
    * @return the accumulated character content of the element, including descendant elements.
    */

    public String getStringValue() {
        return getStringValueCS().toString();
    }


    public CharSequence getStringValueCS() {
        FastStringBuffer sb = null;

        NodeImpl next = (NodeImpl)getFirstChild();
        while (next!=null) {
            if (next instanceof TextImpl) {
                if (sb==null) {
                    sb = new FastStringBuffer(FastStringBuffer.SMALL);
                }
                sb.append(next.getStringValueCS());
            }
            next = next.getNextInDocument(this);
        }
        if (sb==null) return "";
        return sb.condense();
    }

    /**
     * Add a child node to this node. For system use only. Note: normalizing adjacent text nodes
     * is the responsibility of the caller.
     * @param node the node to be added as a child of this node. This must be an instance of
     * {@link net.sf.saxon.tree.linked.NodeImpl}. It will be modified as a result of this call (by setting its
     * parent property and sibling position)
     * @param index the position where the child is to be added
    */

    protected synchronized void addChild(/*@NotNull*/ NodeImpl node, int index) {
        NodeImpl[] c;
        if (children == null) {
            c = new NodeImpl[10];
        } else if (children instanceof NodeImpl) {
            c = new NodeImpl[10];
            c[0] = (NodeImpl)children;
        } else {
            c = (NodeImpl[])children;
        }
        if (index >= c.length) {
            NodeImpl[] kids = new NodeImpl[c.length * 2];
            System.arraycopy(c, 0, kids, 0, c.length);
            c = kids;
        }
        c[index] = node;
        node.setRawParent(this);
        node.setSiblingPosition(index);
        children = c;
    }


     /**
      * Insert a sequence of nodes as children of this node.
      *
      * <p>This method takes no action unless the target node is a document node or element node. It also
      * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
      * processing instructions.</p>
      *
      * <p>The supplied nodes will form the new children. Adjacent text nodes will be merged, and
      * zero-length text nodes removed. The supplied nodes may be modified in situ, for example to change their
      * parent property and to add namespace bindings, or they may be copied, at the discretion of
      * the implementation.</p>
      *
      * @param source the nodes to be inserted. The implementation determines what implementation classes
      * of node it will accept; this implementation will accept text, comment, and processing instruction
      * nodes belonging to any implementation, but elements must be instances of {@link net.sf.saxon.tree.linked.ElementImpl}.
      * The supplied nodes will be modified in situ, for example
      * to change their parent property and to add namespace bindings, if they are instances of
      * {@link net.sf.saxon.tree.linked.ElementImpl}; otherwise they will be copied. If the nodes are copied, then on return
      * the supplied source array will contain the copy rather than the original.
      * @param atStart true if the new nodes are to be inserted before existing children; false if they are
      * to be inserted after existing children
      * @param inherit true if the inserted nodes are to inherit the namespaces of their new parent; false
      * if such namespaces are to be undeclared
      * @throws IllegalArgumentException if the supplied nodes use a node implementation that this
      * implementation does not accept.
      */

    public void insertChildren(/*@NotNull*/ NodeInfo[] source, boolean atStart, boolean inherit) {
        if (atStart) {
            insertChildrenAt(source, 0, inherit);
        } else {
            insertChildrenAt(source, getNumberOfChildren(), inherit);
        }
    }

    /**
     * Insert children before or after a given existing child
     * @param source the children to be inserted. We allow any kind of text, comment, or processing instruction
     * node, but element nodes must be instances of this NodeInfo implementation.
     * @param index the position before which they are to be inserted: 0 indicates insertion before the
     * first child, 1 insertion before the second child, and so on.
     * @param inherit true if the inserted nodes are to inherit the namespaces that are in-scope for their
     * new parent; false if such namespaces should be undeclared on the children
     */

    protected synchronized void insertChildrenAt(/*@NotNull*/ NodeInfo[] source, int index, boolean inherit) {
        if (source.length == 0) {
            return;
        }
        for (int i=0; i<source.length; i++) {
            source[i] = convertForeignNode(source[i]);
            NodeImpl child = (NodeImpl)source[i];
            child.setRawParent(this);
            if (child instanceof ElementImpl) {
                // If the child has no xmlns="xxx" declaration, then add an xmlns="" to prevent false inheritance
                // from the new parent
                ((ElementImpl)child).fixupInsertedNamespaces(inherit);
            }
        }
        if (children == null) {
            if (source.length == 1) {
                children = source[0];
            } else {
                NodeImpl[] n2 = new NodeImpl[source.length];
                System.arraycopy(source, 0, n2, 0, source.length);
                children = n2;
            }
        } else if (children instanceof NodeImpl) {
            int adjacent = (index==0 ? 0 : source.length - 1);
            if (children instanceof TextImpl && source[adjacent] instanceof TextImpl) {
                if (index == 0) {
                    ((TextImpl)source[adjacent]).replaceStringValue(
                            source[adjacent].getStringValue() + ((TextImpl)children).getStringValue());
                } else {
                    ((TextImpl)source[adjacent]).replaceStringValue(
                            ((TextImpl)children).getStringValue() + source[adjacent].getStringValue());
                }
                NodeImpl[] n2 = new NodeImpl[source.length];
                System.arraycopy(source, 0, n2, 0, source.length);
                children = n2;
            } else {
                NodeImpl[] n2 = new NodeImpl[source.length + 1];
                if (index == 0) {
                    System.arraycopy(source, 0, n2, 0, source.length);
                    n2[source.length] = (NodeImpl)children;
                } else {
                    n2[0] = (NodeImpl)children;
                    System.arraycopy(source, 0, n2, 1, source.length);
                }
                children = cleanUpChildren(n2);
            }
        } else {
            NodeImpl[] n0 = (NodeImpl[])children;
            NodeImpl[] n2 = new NodeImpl[n0.length + source.length];
            System.arraycopy(n0, 0, n2, 0, index);
            System.arraycopy(source, 0, n2, index, source.length);
            System.arraycopy(n0, index, n2, index+source.length, n0.length - index);
            children = cleanUpChildren(n2);
        }
    }

    /*@NotNull*/ private NodeImpl convertForeignNode(/*@NotNull*/ NodeInfo source) {
        if (!(source instanceof NodeImpl)) {
            int kind = source.getNodeKind();
            switch (kind) {
                case Type.TEXT:
                    return new TextImpl(source.getStringValue());
                case Type.COMMENT:
                    return new CommentImpl(source.getStringValue());
                case Type.PROCESSING_INSTRUCTION:
                    return new ProcInstImpl(source.getNameCode(), source.getStringValue());
                case Type.ELEMENT:
                    throw new IllegalArgumentException(
                            "Cannot insert an element node unless it is an instance of net.sf.saxon.om.tree.ElementImpl");
                default:
                    throw new IllegalArgumentException(
                            "Cannot insert a node unless it is an element, comment, text node, or processing instruction");
            }
        }
        return (NodeImpl)source;
    }

    /**
     * Replace child at a given index by new children
     * @param source the children to be inserted
     * @param index the position at which they are to be inserted: 0 indicates replacement of the
     * first child, replacement of the second child, and so on. The effect is undefined if index
     * is out of range
     * @param inherit set to true if the new child elements are to inherit the in-scope namespaces
     * of their new parent
     * @throws IllegalArgumentException if any of the replacement nodes is not an element, text,
     * comment, or processing instruction node
     */

    protected synchronized void replaceChildrenAt(/*@NotNull*/ NodeInfo[] source, int index, boolean inherit) {
        if (children == null) {
            return;
        }
        for (int i=0; i<source.length; i++) {
            source[i] = convertForeignNode(source[i]);
            NodeImpl child = (NodeImpl)source[i];
            child.setRawParent(this);
            if (child instanceof ElementImpl) {
                // If the child has no xmlns="xxx" declaration, then add an xmlns="" to prevent false inheritance
                // from the new parent
                ((ElementImpl)child).fixupInsertedNamespaces(inherit);
            }
        }
        if (children instanceof NodeImpl) {
            if (source.length == 0) {
                children = null;
            } else if (source.length == 1) {
                children = source[0];
            } else {
                NodeImpl[] n2 = new NodeImpl[source.length];
                System.arraycopy(source, 0, n2, 0, source.length);
                children = cleanUpChildren(n2);
            }
        } else {
            NodeImpl[] n0 = (NodeImpl[])children;
            NodeImpl[] n2 = new NodeImpl[n0.length + source.length - 1];
            System.arraycopy(n0, 0, n2, 0, index);
            System.arraycopy(source, 0, n2, index, source.length);
            System.arraycopy(n0, index + 1, n2, index+source.length, n0.length - index - 1);
            children = cleanUpChildren(n2);
        }
    }


    /**
     * Compact the space used by this node
     * @param size the number of actual children
     */

    public synchronized void compact(int size) {
        if (size==0) {
            children = null;
        } else if (size==1) {
            if (children instanceof NodeImpl[]) {
                children = ((NodeImpl[])children)[0];
            }
        } else {
            NodeImpl[] kids = new NodeImpl[size];
            System.arraycopy(children, 0, kids, 0, size);
            children = kids;
        }
    }



}

