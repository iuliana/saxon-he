////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
  * TeeOutputter: a SequenceReceiver that duplicates received events to two different destinations
  */

public class TeeOutputter extends SequenceReceiver {

    Receiver seq1;
    Receiver seq2;

    public TeeOutputter(Receiver seq1, Receiver seq2) {
        super(seq1.getPipelineConfiguration());
        this.seq1 = seq1;
        this.seq2 = seq2;
    }

    /**
     * Set the first destination
     * @param seq1 the first output destination
     */

    protected void setFirstDestination(Receiver seq1) {
        this.seq1 = seq1;
    }

    /**
     * Set the second destination
     * @param seq2 the second output destination
     */

    protected void setSecondDestination(Receiver seq2) {
        this.seq2 = seq2;
    }

    /**
     * Get the first destination
     * @return the first output destination
     */

    protected Receiver getFirstDestination() {
        return seq1;
    }

    /**
     * Get the second destination
     * @return the second output destination
     */

    protected Receiver getSecondDestination() {
        return seq2;
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
        if (seq1 instanceof SequenceReceiver) {
            ((SequenceReceiver)seq1).append(item, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            throw new UnsupportedOperationException("append() not supported");
        }
        if (seq2 instanceof SequenceReceiver) {
            ((SequenceReceiver)seq2).append(item, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            throw new UnsupportedOperationException("append() not supported");
        }
    }

    @Override
    public void open() throws XPathException {
        super.open();
        seq1.open();
        seq2.open();
    }

    /**
     * Notify the start of a document node
     */

    public void startDocument(int properties) throws XPathException {
        seq1.startDocument(properties);
        seq2.startDocument(properties);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        seq1.endDocument();
        seq2.endDocument();
    }

    /**
     * Notify the start of an element
     *
     * @param nameCode    integer code identifying the name of the element within the name pool.
     * @param typeCode    integer code identifying the element's type within the name pool. The value -1
*                    indicates the default type, xs:untyped.
     * @param locationId  an integer which can be interpreted using a LocationMap to return
*                    information such as line number and system ID. If no location information is available,
*                    the value zero is supplied.
     * @param properties  bit-significant properties of the element node. If there are no revelant
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        seq1.startElement(nameCode, typeCode, locationId, properties);
        seq2.startElement(nameCode, typeCode, locationId, properties);
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding the prefix/uri pair
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        seq1.namespace(namespaceBinding, properties);
        seq2.namespace(namespaceBinding, properties);
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dt>DISABLE_ESCAPING</dt>    <dd>Disable escaping for this attribute</dd>
     *                   <dt>NO_SPECIAL_CHARACTERS</dt>      <dd>Attribute value contains no special characters</dd>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        seq1.attribute(nameCode, typeCode, value, locationId, properties);
        seq2.attribute(nameCode, typeCode, value, locationId, properties);
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */

    public void startContent() throws XPathException {
        seq1.startContent();
        seq2.startContent();
    }

    /**
     * Notify the end of an element. The receiver must maintain a stack if it needs to know which
     * element is ending.
     */

    public void endElement() throws XPathException {
        seq1.endElement();
        seq2.endElement();
    }

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     *
     * @param chars      The characters
     * @param locationId an integer which can be interpreted using a LocationMap to return
*                   information such as line number and system ID. If no location information is available,
*                   the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
*                   <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
*                   <dt>USE_CDATA</dt>                  <dd>Output as a CDATA section</dd>
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        seq1.characters(chars, locationId, properties);
        seq2.characters(chars, locationId, properties);
    }

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the PI. The following bits are
     *                   defined:
     *                   <dt>CHECKED</dt>    <dd>Data is known to be legal (e.g. doesn't contain "?>")</dd>
     * @throws IllegalArgumentException: the content is invalid for an XML processing instruction
     */

    public void processingInstruction(String name, CharSequence data, int locationId, int properties) throws XPathException {
        seq1.processingInstruction(name, data, locationId, properties);
        seq2.processingInstruction(name, data, locationId, properties);
    }

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the comment. The following bits are
     *                   defined:
     *                   <dt>CHECKED</dt>    <dd>Comment is known to be legal (e.g. doesn't contain "--")</dd>
     * @throws IllegalArgumentException: the content is invalid for an XML comment
     */

    public void comment(CharSequence content, int locationId, int properties) throws XPathException {
        seq1.comment(content, locationId, properties);
        seq2.comment(content, locationId, properties);
    }

    /**
     * Notify the end of the event stream
     */

    public void close() throws XPathException {
        seq1.close();
        seq2.close();
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return seq1.usesTypeAnnotations() || seq2.usesTypeAnnotations();
    }
}

