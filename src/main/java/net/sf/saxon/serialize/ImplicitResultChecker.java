////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * This filter is inserted into the serializer pipeline when serializing an implicit XSLT result tree, that
 * is, one that is created without use of xsl:result-document. Its main purpose is to check, if and only if
 * the result destination is actually written to, that it does not conflict with an explicit result destination
 * with the same URI. It also ensures that the output destination is opened before it is first written to.
 */
public class ImplicitResultChecker extends ProxyReceiver {

    private boolean clean = true;
    private boolean open = false;
    private Controller controller;

    /**
     * Create an ImplicitResultChecker. This is a filter on the output pipeline.
     * @param next the next receiver on the pipeline
     * @param controller the controller of the XSLT transformation
     */

    public ImplicitResultChecker(Receiver next, Controller controller) {
        super(next);
        this.controller = controller;
    }

    public void open() throws XPathException {
        super.open();
        open = true;
    }

    public synchronized void startDocument(int properties) throws XPathException {
        if (!open) {
            open();
        }
        nextReceiver.startDocument(properties);
    }

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (clean) {
            firstContent();
        }
        nextReceiver.startElement(nameCode, typeCode, locationId, properties);
    }

    public synchronized void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (clean) {
            firstContent();
        }
        nextReceiver.characters(chars, locationId, properties);
    }

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (clean) {
            firstContent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        if (clean) {
            firstContent();
        }
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * This method does the real work. It is called when the first output is written to the implicit output
     * destination, and checks that no explicit result document has been written to the same URI
     * as the implicit result document
     * @throws XPathException
     */

    private synchronized void firstContent() throws XPathException {
        controller.checkImplicitResultTree();
        if (!open) {
            open();
            startDocument(0);
        }
        clean = false;
    }

    public void close() throws XPathException {
        // If we haven't written any output, do the close only if no explicit result document has been written.
        // This will cause a file to be created and perhaps an XML declaration to be written
        if (!clean || !controller.hasThereBeenAnExplicitResultDocument()) {
            if (!open) {
                open();
            }
            nextReceiver.close();
        }
    }
}

