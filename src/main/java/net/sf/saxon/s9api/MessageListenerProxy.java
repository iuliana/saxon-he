////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * This class implements a Receiver that can receive xsl:message output and send it to a
 * user-supplied MessageListener.
 */

class MessageListenerProxy extends SequenceWriter {

    private MessageListener listener;
    private boolean terminate;
    private int locationId = -1;

    protected MessageListenerProxy(MessageListener listener, PipelineConfiguration pipe) {
        super(pipe);
        // See bug 2104. We use the Linked Tree model because the TinyTree can use excessive memory. This
        // is because the initial size allocation is based on the size of source documents, which might be large;
        // also because we store several messages in a single TinyTree; and because we fail to condense the tree.
        setTreeModel(TreeModel.LINKED_TREE);
        this.listener = listener;
    }

    /**
     * Get the wrapped MessageListener
     * @return the wrapped MessageListener
     */

    public MessageListener getMessageListener() {
        return listener;
    }


    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        terminate = (properties & ReceiverOptions.TERMINATE) != 0;
        locationId = -1;
        super.startDocument(properties);
    }


    /**
     * Output an element start tag.
     *
     * @param nameCode   The element name code - a code held in the Name Pool
     * @param typeCode   Integer code identifying the type of this element. Zero identifies the default
     *                   type, that is xs:anyType
     * @param properties bit-significant flags indicating any special information
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (this.locationId == -1) {
            this.locationId = locationId;
        }
        super.startElement(nameCode, typeCode, locationId, properties);
    }

    /**
     * Produce text content output. <BR>
     *
     * @param s          The String to be output
     * @param properties bit-significant flags for extra information, e.g. disable-output-escaping
     * @throws net.sf.saxon.trans.XPathException
     *          for any failure
     */

    public void characters(CharSequence s, int locationId, int properties) throws XPathException {
        if (this.locationId == -1) {
            this.locationId = locationId;
        }
        super.characters(s, locationId, properties);
    }


    /**
     * Append an item to the sequence, performing any necessary type-checking and conversion
     */

    public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
        if (this.locationId == -1) {
            this.locationId = locationId;
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     */

    public void write(Item item) throws XPathException {
        ExpressionLocation loc = new ExpressionLocation();
        if (locationId != -1) {
            LocationProvider provider = getPipelineConfiguration().getLocationProvider();
            loc.setSystemId(provider.getSystemId(locationId));
            loc.setLineNumber(provider.getLineNumber(locationId));
        }
        listener.message(new XdmNode((NodeInfo)item), terminate, loc);
    }
}

