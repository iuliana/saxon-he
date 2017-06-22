////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

/**
 * SequenceReceiver: this extension of the Receiver interface is used when processing
 * a sequence constructor. It differs from the Receiver in allowing items (atomic values or
 * nodes) to be added to the sequence, not just tree-building events.
 */

public abstract class SequenceReceiver implements Receiver {

    protected boolean previousAtomic = false;
    /*@NotNull*/
    protected PipelineConfiguration pipelineConfiguration;
    /*@Nullable*/
    protected String systemId = null;

    /**
     * Create a SequenceReceiver
     * @param pipe the pipeline configuration
     */

    public SequenceReceiver(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipelineConfiguration = pipe;
    }

    /*@NotNull*/
    public final PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipelineConfiguration) {
        this.pipelineConfiguration = pipelineConfiguration;
    }

    /**
     * Get the Saxon Configuration
     * @return the Configuration
     */

    public final Configuration getConfiguration() {
        return pipelineConfiguration.getConfiguration();
    }

    /**
     * Set the system ID
     * @param systemId the URI used to identify the tree being passed across this interface
     */

    public void setSystemId(/*@Nullable*/ String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system ID
     * @return the system ID that was supplied using the setSystemId() method
     */

    /*@Nullable*/ public String getSystemId() {
        return systemId;
    }

    /**
     * Notify an unparsed entity URI.
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
    }

    /**
     * Start the output process
     */

    public void open() throws XPathException {
        previousAtomic = false;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
     *                       need to be copied. Values are {@link net.sf.saxon.om.NodeInfo#ALL_NAMESPACES},
     *                       {@link net.sf.saxon.om.NodeInfo#LOCAL_NAMESPACES}, {@link net.sf.saxon.om.NodeInfo#NO_NAMESPACES}
     * @throws net.sf.saxon.trans.XPathException if the operation fails
     */

    public abstract void append(Item item, int locationId, int copyNamespaces) throws XPathException;

    /**
     * Append an item (node or atomic value) to the output
     * @param item the item to be appended
     * @throws net.sf.saxon.trans.XPathException if the operation fails
     */

    public void append(Item item) throws XPathException {
        append(item, -1, NodeInfo.ALL_NAMESPACES);
    }

    /**
     * Get the name pool
     * @return the Name Pool that was supplied using the setConfiguration() method
     */

    public NamePool getNamePool() {
        return pipelineConfiguration.getConfiguration().getNamePool();
    }
}

