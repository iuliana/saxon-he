////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * A ProxyReceiver is an Receiver that filters data before passing it to another
 * underlying Receiver.
 */

public abstract class ProxyReceiver extends SequenceReceiver {

    /*@NotNull*/
    protected Receiver nextReceiver;

    public ProxyReceiver(/*@NotNull*/ Receiver nextReceiver) {
        super(nextReceiver.getPipelineConfiguration());
        setUnderlyingReceiver(nextReceiver);
        setPipelineConfiguration(nextReceiver.getPipelineConfiguration());
    }

    public void setSystemId(String systemId) {
        //noinspection StringEquality
        if (systemId != this.systemId) {
            // use of == rather than equals() is deliberate, since this is only an optimization
            this.systemId = systemId;
            nextReceiver.setSystemId(systemId);
        }
    }

     /**
      * Set the underlying receiver. This call is mandatory before using the Receiver.
      * @param receiver the underlying receiver, the one that is to receive events after processing
      * by this filter.
     */

    public void setUnderlyingReceiver(/*@NotNull*/ Receiver receiver) {
        if (receiver != nextReceiver) {
            nextReceiver = receiver;
        }
    }

    /**
     * Get the underlying Receiver (that is, the next one in the pipeline)
     * @return the underlying Receiver (that is, the next one in the pipeline)
     */

    /*@Nullable*/ public Receiver getUnderlyingReceiver() {
        return nextReceiver;
    }


    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver.getPipelineConfiguration() != pipe) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    /**
     * Get the namepool for this configuration
     */

    public NamePool getNamePool() {
        return pipelineConfiguration.getConfiguration().getNamePool();
    }

    /**
     * Start of event stream
     */

    public void open() throws XPathException {
        nextReceiver.open();
    }

    /**
     * End of output. Note that closing this receiver also closes the rest of the
     * pipeline.
     */

    public void close() throws XPathException {
        // Note: It's wrong to assume that because we've finished writing to this
        // receiver, then we've also finished writing to other receivers in the pipe.
        // In the case where the rest of the pipe is to stay open, the caller should
        // either avoid doing the close(), or should first set the underlying receiver
        // to null.
        nextReceiver.close();
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        nextReceiver.endDocument();
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        nextReceiver.startElement(elemName, typeCode, locationId, properties);
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding the prefix/uri pair representing the namespace binding
     * @param properties any special properties to be passed on this call
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        nextReceiver.namespace(namespaceBinding, properties);
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
            throws XPathException {
        nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */


    public void startContent() throws XPathException {
        nextReceiver.startContent();
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        nextReceiver.endElement();
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        nextReceiver.characters(chars, locationId, properties);
    }


    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        nextReceiver.comment(chars, locationId, properties);
    }


    /**
     * Set the URI for an unparsed entity in the document.
     */

    public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
        nextReceiver.setUnparsedEntity(name, uri, publicId);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link net.sf.saxon.om.NodeInfo#ALL_NAMESPACES},
*                       {@link net.sf.saxon.om.NodeInfo#LOCAL_NAMESPACES}, {@link net.sf.saxon.om.NodeInfo#NO_NAMESPACES}
     */

    public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
        if (nextReceiver instanceof SequenceReceiver) {
            ((SequenceReceiver)nextReceiver).append(item, locationId, copyNamespaces);
        } else {
            throw new UnsupportedOperationException("append() method is not supported in this class");
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }
}

