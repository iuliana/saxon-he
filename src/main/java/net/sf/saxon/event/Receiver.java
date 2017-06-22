////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import javax.xml.transform.Result;
/**
  * Receiver: This interface represents a recipient of XML tree-walking (push) events. It is
  * based on SAX2's ContentHandler, but adapted to handle additional events, and
  * to use Saxon's name pool. Namespaces and Attributes are handled by separate events
  * following the startElement event. Schema types can be defined for elements and attributes.
  * <p>
  * The Receiver interface is an important internal interface within Saxon, and provides a powerful
  * mechanism for integrating Saxon with other applications. It has been designed with extensibility
  * and stability in mind. However, it should be considered as an interface designed primarily for
  * internal use, and not as a completely stable part of the public Saxon API.
  * <p>
  * @author Michael H. Kay
  */

public interface Receiver extends Result {

    /**
     * Set the pipeline configuration
     * @param pipe the pipeline configuration
     */

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe);

    /**
     * Get the pipeline configuration
     * @return the pipeline configuration
     */

    /*@NotNull*/
    public PipelineConfiguration getPipelineConfiguration();

	/**
	 * Set the System ID of the tree represented by this event stream
     * @param systemId the system ID (which is used as the base URI of the nodes
     * if there is no xml:base attribute)
	*/

	public void setSystemId(String systemId);

    /**
    * Notify the start of the event stream
     * @throws XPathException if an error occurs
     */

    public void open() throws XPathException;

    /**
     * Notify the start of a document node
     * @param properties bit-significant integer indicating properties of the document node.
     * The definitions of the bits are in class {@link ReceiverOptions}
     * @throws XPathException if an error occurs
     */

    public void startDocument(int properties) throws XPathException;

    /**
     * Notify the end of a document node
     * @throws XPathException if an error occurs
     */

    public void endDocument() throws XPathException;

    /**
    * Notify an unparsed entity URI.
    * @param name The name of the unparsed entity
    * @param systemID The system identifier of the unparsed entity
    * @param publicID The public identifier of the unparsed entity
     * @throws XPathException if an error occurs
    */

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException;

    /**
     * Notify the start of an element
     *
     * @param elemName the name of the element.
     * @param typeCode the type annotation of the element.
     * @param locationId an integer which can be interpreted using a {@link LocationProvider} to return
     * information such as line number and system ID. If no location information is available,
     * the value zero is supplied.
     * @param properties bit-significant properties of the element node. If there are no revelant
     * properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOptions}
     * @throws XPathException if an error occurs
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties)
            throws XPathException;

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element. The events represent namespace
     * declarations and undeclarations rather than in-scope namespace nodes: an undeclaration is represented
     * by a namespace code of zero. If the sequence of namespace events contains two
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     * @param namespaceBinding contains the namespace prefix and namespace URI
     * @param properties The most important property is REJECT_DUPLICATES. If this property is set, the
     * namespace declaration will be rejected if it conflicts with a previous declaration of the same
     * prefix. If the property is not set, the namespace declaration will be ignored if it conflicts
     * with a previous declaration. This reflects the fact that when copying a tree, namespaces for child
     * elements are emitted before the namespaces of their parent element. Unfortunately this conflicts
     * with the XSLT rule for complex content construction, where the recovery action in the event of
     * conflicts is to take the namespace that comes last. XSLT therefore doesn't recover from this error:
     * @throws XPathException if an error occurs
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException;

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     * @param attName The name of the attribute
     * @param typeCode The type of the attribute, as held in the name pool. The additional bit
     * NodeInfo.IS_DTD_TYPE may be set to indicate a DTD-derived type.
     * @param value the string value of the attribute
     * @param locationId an integer which can be interpreted using a {@link net.sf.saxon.event.LocationProvider} to return
     * information such as line number and system ID. If no location information is available,
     * the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
     *        <dt>DISABLE_ESCAPING</dt>    <dd>Disable escaping for this attribute</dd>
     *        <dt>NO_SPECIAL_CHARACTERS</dt>      <dd>Attribute value contains no special characters</dd>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     * start tag
     * @throws XPathException if an error occurs
    */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties)
            throws XPathException;

    /**
    * Notify the start of the content, that is, the completion of all attributes and namespaces.
    * Note that the initial receiver of output from XSLT instructions will not receive this event,
    * it has to detect it itself. Note that this event is reported for every element even if it has
    * no attributes, no namespaces, and no content.
     * @throws XPathException if an error occurs
     */

    public void startContent() throws XPathException;

    /**
    * Notify the end of an element. The receiver must maintain a stack if it needs to know which
    * element is ending.
     * @throws XPathException if an error occurs
     */

    public void endElement() throws XPathException;

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     * @param chars The characters
     * @param locationId an integer which can be interpreted using a {@link net.sf.saxon.event.LocationProvider}
     * to return information such as line number and system ID. If no location information is available,
     * the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
     *        <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
     *        <dt>USE_CDATA</dt>                  <dd>Output as a CDATA section</dd>
     * @throws XPathException if an error occurs
     */

    public void characters(CharSequence chars, int locationId, int properties)
            throws XPathException;

    /**
     * Output a processing instruction
     * @param name The PI name. This must be a legal name (it will not be checked).
     * @param data The data portion of the processing instruction
     * @param locationId an integer which can be interpreted using a {@link LocationProvider} to return
     * information such as line number and system ID. If no location information is available,
     * the value zero is supplied.
     * @param properties Additional information about the PI.
     * @throws IllegalArgumentException: the content is invalid for an XML processing instruction
     * @throws XPathException if an error occurs
     */

    public void processingInstruction(String name, CharSequence data, int locationId, int properties)
            throws XPathException;

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     * @param content The content of the comment
     * @param locationId an integer which can be interpreted using a {@link LocationProvider} to return
     * information such as line number and system ID. If no location information is available,
     * the value zero is supplied.
     * @param properties Additional information about the comment.
     * @throws IllegalArgumentException: the content is invalid for an XML comment
     * @throws XPathException if an error occurs
     */

    public void comment(CharSequence content, int locationId, int properties) throws XPathException;

    /**
    * Notify the end of the event stream
     * @throws XPathException if an error occurs
     */

    public void close() throws XPathException;

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     * may supply untyped nodes instead of supplying the type annotation (or conversely, it may
     * avoid stripping unwanted type annotations)
     */

    public boolean usesTypeAnnotations();



}

