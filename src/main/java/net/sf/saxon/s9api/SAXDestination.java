////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ContentHandlerProxy;
import net.sf.saxon.event.Receiver;
import org.xml.sax.ContentHandler;


/**
 * This class represents a Destination (for example, the destination of the output of a transformation)
 * in which events representing the XML document are sent to a user-supplied SAX2 ContentHandler, as
 * if the ContentHandler were receiving the document directly from an XML parser.
 */

public class SAXDestination implements Destination {

    private ContentHandler contentHandler;

    /**
     * Create a SAXDestination, supplying a SAX ContentHandler to which
     * events will be routed
     * @param handler the SAX ContentHandler that is to receive the output. If the
     * ContentHandler is also a {@link org.xml.sax.ext.LexicalHandler} then it will also receive
     * notification of events such as comments.
     */

    public SAXDestination(ContentHandler handler) {
        contentHandler = handler;
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     *
     * @param config The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @return the Receiver to which events are to be sent.
     * @throws net.sf.saxon.s9api.SaxonApiException
     *          if the Receiver cannot be created
     */

    /*@NotNull*/ public Receiver getReceiver(Configuration config) throws SaxonApiException {
        ContentHandlerProxy chp = new ContentHandlerProxy();
        chp.setUnderlyingContentHandler(contentHandler);
        chp.setPipelineConfiguration(config.makePipelineConfiguration());
        return chp;
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    public void close() throws SaxonApiException {
        // no action
    }
}

