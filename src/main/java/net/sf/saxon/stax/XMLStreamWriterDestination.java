////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.stax;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.SaxonApiException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * StAxResultHandler is a helper class
 */
public class XMLStreamWriterDestination implements Destination{

    private XMLStreamWriter writer;

    public XMLStreamWriterDestination(XMLStreamWriter writer) {
        this.writer = writer;
    }

    public Receiver getReceiver(Configuration config) throws SaxonApiException {
        return  new ReceiverToXMLStreamWriter(writer);
    }

    public void close() throws SaxonApiException {
        try {
            writer.close();
        } catch (XMLStreamException e) {
            throw new SaxonApiException(e);
        }
    }
}
