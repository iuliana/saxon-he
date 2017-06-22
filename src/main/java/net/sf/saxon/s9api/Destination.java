////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;

/**
 * A Destination represents a place where XML can be sent. It is used, for example,
 * to define the output of a transformation or query.
 *
 * <p>In general a <code>Destination</code> is designed to hold a single document.
 * It should therefore not be used as the destination of a query that produces multiple
 * documents. The effect of sending multiple documents to a <code>Destination</code>
 * depends on the kind of <code>Destination</code>.</p>
 *
 * <p>The interface <code>Destination</code> has some similarities with the JAXP
 * {@link javax.xml.transform.Result} class. It differs, however, in that implementations
 * of this interface can be written by users or third parties to define new kinds of
 * destination, and any such implementation can be supplied to the Saxon methods that
 * take a <code>Destination</code> as an argument.</p>
 *
 * <p>Implementing a new <code>Destination</code>, however, will generally require access
 * to implementation-level classes and methods within the Saxon product. The only method that
 * needs to be supplied is {@link #getReceiver}, which returns an instance of
 * {@link net.sf.saxon.event.Receiver}, and unless you use an existing implementation of
 * <code>Receiver</code>, you will need to handle internal Saxon concepts such as name codes
 * and name pools.</p>
 *
 * <p>In general a Destination is not thread-safe (cannot be used from more than one thread),
 * and is not serially reusable. The {@link #close} method is called by the system when
 * it finishes writing the document, and this causes all resources held by the Destination
 * to be released.</p>
 */
public interface Destination {

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document. The method is intended
     * primarily for internal use, and may give poor diagnostics if used incorrectly.
     * @param config The Saxon configuration. This is supplied so that the destination can
     * use information from the configuration (for example, a reference to the name pool)
     * to construct or configure the returned Receiver.
     * @return the Receiver to which events are to be sent. It is the caller's responsibility to
     * initialize this Receiver with a {@link net.sf.saxon.event.PipelineConfiguration} before calling
     * its <code>open()</code> method. The caller is also responsible for ensuring that the sequence
     * of events sent to the Receiver represents a well-formed document: in particular the event
     * stream must include namespace events corresponding exactly to the namespace declarations
     * that are required. If the calling code cannot guarantee this, it should insert a
     * {@link net.sf.saxon.event.NamespaceReducer} into the pipeline in front of the returned
     * Receiver.
     * @throws SaxonApiException if the Receiver cannot be created
     */

    public Receiver getReceiver(Configuration config) throws SaxonApiException;

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     *
     * <p>The close() method should not cause any adverse effects if it is called more than
     * once. If any other method is called after the close() call, the results are undefined.
     * This means that a Destination is not, in general, serially reusable.</p>
     * @throws SaxonApiException if any failure occurs
     */

    public void close() throws SaxonApiException;
}

