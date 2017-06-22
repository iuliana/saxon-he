////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DOMWriter;
import net.sf.saxon.event.Receiver;

/**
 * This class represents a Destination (for example, the destination of the output of a transformation)
 * in which the results are written to a newly constructed DOM tree in memory. The caller must supply
 * a Document node, which will be used as the root of the constructed tree
 */

public class DOMDestination implements Destination {

    private DOMWriter domWriter;

    /**
     * Create a DOMDestination, supplying the root of a DOM document to which the
     * content of the result tree will be appended.
     * @param root the root node for the new tree.
     */

    public DOMDestination(org.w3c.dom.Document root) {
        domWriter = new DOMWriter();
        domWriter.setNode(root);
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     *
     * @param config The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @return the Receiver to which events are to be sent.
     * @throws SaxonApiException
     *          if the Receiver cannot be created
     */

    public Receiver getReceiver(/*@NotNull*/ Configuration config) throws SaxonApiException {
        domWriter.setPipelineConfiguration(config.makePipelineConfiguration());
        return domWriter;
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    public void close() throws SaxonApiException {
        // no action
    }
}
