////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;

/**
 * A Transmitter is a source of events sent to a Receiver.  An implementation of this interface
 * can be used in any Saxon interface allowing a {@link Source} to be supplied, and allows
 * an input document to be created programmatically in the form of a stream of "push" events
 * send to the supplied {@link Receiver}.
 */
public abstract class Transmitter implements Source {

    private String systemId;

    /**
     * Send events to a supplied Receiver
     * @param receiver the Receiver to which events should be sent.
     *
     * <p>The pipelineConfiguration property of this Receiver is guaranteed
     * to be initialized, providing access to objects such as the Saxon Configuration
     * and NamePool.</p>
     *
     * <p>The implementation of this class does not necessarily need to construct Receiver
     * events directly. It can do so, for example, via the {@link StreamWriterToReceiver}
     * class, which translates {@link javax.xml.stream.XMLStreamWriter} events to Receiver events,
     * or via the {@link ReceivingContentHandler} class, which translates SAX
     * {@link org.xml.sax.ContentHandler} events to Receiver events.</p>
     *
     * @throws net.sf.saxon.trans.XPathException if any failure occurs
     */

    public abstract void transmit(Receiver receiver) throws XPathException;

    /**
     * Set the system identifier for this Source.
     * <p/>
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId, or null
     *         if setSystemId was not called.
     */
    public String getSystemId() {
        return systemId;
    }
}

