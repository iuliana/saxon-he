////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.net.URI;

/**
 * An <code>XdmDestination</code> is a {@link Destination} in which an {@link XdmNode}
 * is constructed to hold the output of a query or transformation:
 * that is, a tree using Saxon's implementation of the XDM data model
 *
 * <p>No data needs to be supplied to the <code>XdmDestination</code> object. The query or transformation
 * populates an <code>XdmNode</code>, which may then be retrieved using the <code>getXdmNode</code>
 * method.</p>
 *
 * <p>An <code>XdmDestination</code> is designed to hold a single tree rooted at a document or element node.
 * It should therefore not be used as the destination of a query that produces multiple
 * documents, multiple elements, nodes other than elements and documents, or atomic values. If the query
 * does produce such a result, an exception will be thrown.</p>
 *
 * <p>An XdmDestination can be reused to hold the results of a second query or transformation only
 * if the {@link #reset} method is first called to reset its state.</p>
 *
 * <p>If an XDM tree is to be built from a lexical XML document, or programmatically from the application
 * by writing a sequence of events, the recommended mechanism is to use a {@link DocumentBuilder} rather
 * than this class.</p>
 */

public class XdmDestination implements Destination {

    TreeModel treeModel = TreeModel.TINY_TREE;
    URI baseURI;
    Builder builder;

    public XdmDestination() {
        //builder = new TinyBuilder();
    }

    /**
     * Set the base URI for the document node that will be created when the XdmDestination is written to.
     * This method must be called before writing to the destination; it has no effect on an XdmNode that
     * has already been constructed.
     * @param baseURI the base URI for the node that will be constructed when the XdmDestination is written to.
     *                This must be an absolute URI
     * @throws IllegalArgumentException if the baseURI supplied is not an absolute URI
     * @since 9.1
     */

    public void setBaseURI(URI baseURI) {
        if (!baseURI.isAbsolute()) {
            throw new IllegalArgumentException("Supplied base URI must be absolute");
        }
        //builder.setBaseURI(baseURI.toString());
        this.baseURI = baseURI;
    }

    /**
     * Get the base URI that will be used for the document node when the XdmDestination is written to.
     * @return the base URI that will be used for the node that is constructed when the XdmDestination is written to.
     * @since 9.1
     */

    public URI getBaseURI() {
        return baseURI;
    }

    /**
     * Set the tree model to be used for documents constructed using this XdmDestination.
     * By default, the TinyTree is used.
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *              a user-defined tree model can be used.
     * @since 9.2
     */

    public void setTreeModel(TreeModel model) {
        this.treeModel = model;
    }

    /**
     * Get the tree model to be used for documents constructed using this XdmDestination.
     * By default, the TinyTree is used.
     * @return the tree model in use: typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *         {@link net.sf.saxon.om.TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *         a user-defined tree model can be used.
     * @since 9.2
     */

    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     * @param config The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @return the Receiver to which events are to be sent.
     * @throws net.sf.saxon.s9api.SaxonApiException
     *          if the Receiver cannot be created
     */

    public Receiver getReceiver(Configuration config) throws SaxonApiException {
        TreeModel model = treeModel;
        if (model == null) {
            model = TreeModel.getTreeModel(config.getTreeModel());
        }
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        builder = model.makeBuilder(pipe);
        if (baseURI != null) {
            builder.setBaseURI(baseURI.toString());
        }
        return new TreeProtector(builder);
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    public void close() throws SaxonApiException {
        // no action
    }

    /**
     * Return the node at the root of the tree, after it has been constructed.
     * <p/>
     * <p>This method should not be called while the tree is under construction.</p>
     * @return the root node of the tree (always a document or element node); or null if
     * nothing is written to the tree (for example, the result of a query that returns the
     * empty sequence)
     * @throws IllegalStateException if called during the execution of the process that
     * is writing the tree.
     */

    public XdmNode getXdmNode() {
        if (builder == null) {
            throw new IllegalStateException("The document has not yet been built");
        }
        NodeInfo node = builder.getCurrentRoot();
        return (node == null ? null : (XdmNode)XdmValue.wrap(node));
    }

    /**
     * Allow the <code>XdmDestination</code> to be reused, without resetting other properties
     * of the destination.
     */

    public void reset() {
        builder = null;
    }

    /**
     * TreeProtector is a filter that ensures that the events reaching the Builder constitute a single
     * tree rooted at an element or document node (because anything else will crash the builder)
     */

    private static class TreeProtector extends ProxyReceiver {

        private int level = 0;
        private boolean ended = false;

        public TreeProtector(Receiver next) {
            super(next);
        }

        @Override
        public void startDocument(int properties) throws XPathException {
            if (ended) {
                throw new XPathException("Only a single document can be written to an XdmDestination");
            }
            super.startDocument(properties);
            level++;
        }

        @Override
        public void endDocument() throws XPathException {
            super.endDocument();
            level--;
            if (level == 0) {
                ended = true;
            }
        }

        @Override
        public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
            if (ended) {
                throw new XPathException("Only a single root node can be written to an XdmDestination");
            }
            super.startElement(nameCode, typeCode, locationId, properties);
            level++;
        }

        @Override
        public void endElement() throws XPathException {
            super.endElement();
            level--;
            if (level == 0) {
                ended = true;
            }
        }

        @Override
        public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
            if (level == 0) {
                throw new XPathException("When writing to an XdmDestination, text nodes are only allowed within a document or element node");
            }
            super.characters(chars, locationId, properties);
        }

        @Override
        public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
            if (level == 0) {
                throw new XPathException("When writing to an XdmDestination, processing instructions are only allowed within a document or element node");
            }
            super.processingInstruction(target, data, locationId, properties);
        }

        @Override
        public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
            if (level == 0) {
                throw new XPathException("When writing to an XdmDestination, comment nodes are only allowed within a document or element node");
            }
            super.comment(chars, locationId, properties);
        }

        @Override
        public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
            if (level == 0) {
                throw new XPathException("When writing to an XdmDestination, atomic values are only allowed within a document or element node");
            }
            super.append(item, locationId, copyNamespaces);
        }

    }
}

