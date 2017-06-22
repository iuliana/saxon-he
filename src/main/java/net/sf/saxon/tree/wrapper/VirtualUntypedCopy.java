////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * This class represents a virtual copy of a node with type annotations stripped
 */
public class VirtualUntypedCopy extends VirtualCopy {

    // TODO: need a VirtualUntypedDocumentCopy for the document node??

    /**
     * Public factory method: create an untyped virtual copy of a node
     * @param original the node to be copied
     * @param root the root of the tree
     * @return the virtual copy.
     */

    public static VirtualCopy makeVirtualUntypedCopy(NodeInfo original, NodeInfo root) {
        VirtualCopy vc;
        // Don't allow copies of copies of copies: define the new copy in terms of the original
        while (original instanceof VirtualUntypedCopy) {
            original = ((VirtualUntypedCopy)original).original;
        }
        while (root instanceof VirtualUntypedCopy) {
            root = ((VirtualUntypedCopy)root).original;
        }
        if (original.getNodeKind() == Type.DOCUMENT) {
            vc = new VirtualDocumentCopy((DocumentInfo)original);
        } else {
            vc = new VirtualUntypedCopy(original);
        }
        vc.root = root;
        return vc;
    }

    /**
     * Protected constructor: create a virtual copy of a node
     *
     * @param base the node to be copied
     */

    protected VirtualUntypedCopy(NodeInfo base) {
        super(base);
    }

    /**
     * Get the type annotation of this node, if any.
     *
     * @return the type annotation of the node.
     * @see net.sf.saxon.type.Type
     */

    public int getTypeAnnotation() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return StandardNames.XS_UNTYPED;
            case Type.ATTRIBUTE:
                return StandardNames.XS_UNTYPED_ATOMIC;
            default:
                return super.getTypeAnnotation();
        }
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
    @Override
    public SchemaType getSchemaType() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return Untyped.getInstance();
            case Type.ATTRIBUTE:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            default:
                return super.getSchemaType();
        }
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
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return new UntypedAtomicValue(getStringValueCS());
            default:
                return super.atomize();
        }
    }


    public void copy(Receiver out, int copyOptions, int locationId) throws XPathException {
        super.copy(out, copyOptions & ~CopyOptions.TYPE_ANNOTATIONS, locationId);
    }


    /**
     * Create an iterator that makes and returns virtual copies of nodes on the original tree
     *
     * @param axis      the axis to be navigated
     * @param newParent the parent of the nodes in the new virtual tree (may be null)
     * @param root      the root of the virtual tree
     */

    protected VirtualCopy.VirtualCopier makeCopier(AxisIterator axis, VirtualCopy newParent, NodeInfo root) {
        return new VirtualUntypedCopier(axis, newParent, root);
    }

    protected class VirtualUntypedCopier extends VirtualCopy.VirtualCopier {


        public VirtualUntypedCopier(AxisIterator base, VirtualCopy parent, NodeInfo subtreeRoot) {
            super(base, parent, subtreeRoot);
        }

        /**
         * Method to create the virtual copy of a node encountered when navigating. This method
         * is separated out so that it can be overridden in a subclass.
         */

        protected VirtualCopy createCopy(NodeInfo node, NodeInfo root) {
            return VirtualUntypedCopy.makeVirtualUntypedCopy(node, root);
        }

        /**
         * Get another iterator over the same sequence of items, positioned at the
         * start of the sequence
         *
         * @return a new iterator over the same sequence
         */

        /*@NotNull*/ public AxisIterator getAnother() {
            return new VirtualUntypedCopier(base.getAnother(), parent, subtreeRoot);
        }


    }
}

