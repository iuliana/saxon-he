////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

/**
 * A Serializer takes a tree representation of XML and turns it into lexical XML markup.
 * <p/>
 * <p><i>Note that this is XML serialization in the sense of the W3C XSLT and XQuery specifications.
 * This has nothing to do with the serialization of Java objects, or the {@link java.io.Serializable}
 * interface.</i></p>
 * <p/>
 * <p>The serialization may be influenced by a number of serialization parameters. A parameter has a name,
 * which is an instance of {@link Serializer.Property}, and a value, which is expressed as a string.
 * The effect of most of the properties is as described in the W3C specification
 * <a href="http://www.w3.org/TR/xslt-xquery-serialization/">XSLT 2.0 and XQuery 1.0 Serialization</a>.
 * Saxon supports all the serialization parameters defined in that specification, together with some
 * additional parameters, whose property names are prefixed "SAXON_".
 * <p/>
 * <p>Serialization parameters defined via this interface take precedence over any serialization parameters
 * defined within the source of the query or stylesheet.
 */
@SuppressWarnings({"ForeachStatement"})
public class Serializer implements Destination {

    private Configuration config; // Beware: this will often be null
    private Map<Property, String> properties = new HashMap<Property, String>(10);
    private StreamResult result = new StreamResult();
    private boolean mustClose = false;

    /**
     * Enumerator over the defined serialization properties
     */

    public enum Property {
        /**
         * Serialization method: xml, html, xhtml, or text
         */
        METHOD(OutputKeys.METHOD),
        /**
         * Version of output method, for example "1.0" or "1.1" for XML
         */
        VERSION(OutputKeys.VERSION),
        /**
         * Character encoding of output stream
         */
        ENCODING(OutputKeys.ENCODING),
        /**
         * Set to "yes" if the XML declaration is to be omitted from the output file
         */
        OMIT_XML_DECLARATION(OutputKeys.OMIT_XML_DECLARATION),
        /**
         * Set to "yes", "no", or "omit" to indicate the required value of the standalone attribute
         * in the XML declaration of the output file
         */
        STANDALONE(OutputKeys.STANDALONE),
        /**
         * Set to any string to indicate that the output is to include a DOCTYPE declaration with this public id
         */
        DOCTYPE_PUBLIC(OutputKeys.DOCTYPE_PUBLIC),
        /**
         * Set to any string to indicate that the output is to include a DOCTYPE declaration with this system id
         */
        DOCTYPE_SYSTEM(OutputKeys.DOCTYPE_SYSTEM),
        /**
         * Space-separated list of QNames (in Clark form) of elements
         * whose content is to be wrapped in CDATA sections
         */
        CDATA_SECTION_ELEMENTS(OutputKeys.CDATA_SECTION_ELEMENTS),
        /**
         * Set to "yes" or "no" to indicate whether indentation is required
         */
        INDENT(OutputKeys.INDENT),
        /**
         * Set to indicate the media type (MIME type) of the output
         */
        MEDIA_TYPE(OutputKeys.MEDIA_TYPE),
        /**
         * List of names of character maps to be used. Character maps can only be specified in an XSLT
         * stylesheet.
         */
        USE_CHARACTER_MAPS(SaxonOutputKeys.USE_CHARACTER_MAPS),
        /**
         * For HTML and XHTML, set to "yes" or "no" to indicate whether a &lt;meta&gt; element is to be
         * written to indicate the content type and encoding
         */
        INCLUDE_CONTENT_TYPE(SaxonOutputKeys.INCLUDE_CONTENT_TYPE),
        /**
         * Set to "yes" or "no" to indicate (for XML 1.1) whether namespace that go out of scope should
         * be undeclared
         */
        UNDECLARE_PREFIXES(SaxonOutputKeys.UNDECLARE_PREFIXES),
        /**
         * Set to "yes" or "no" to indicate (for HTML and XHTML) whether URI-valued attributes should be
         * percent-encoded
         */
        ESCAPE_URI_ATTRIBUTES(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES),
        /**
         * Set to "yes" or "no" to indicate whether a byte order mark is to be written
         */
        BYTE_ORDER_MARK(SaxonOutputKeys.BYTE_ORDER_MARK),
        /**
         * Set to the name of a Unicode normalization form: "NFC", "NFD", "NFKC", or "NFKD", or
         * "none" to indicate no normalization
         */
        NORMALIZATION_FORM(SaxonOutputKeys.NORMALIZATION_FORM),

        /**
         * Saxon extension: set to an integer (represented as a string) giving the number of spaces
         * by which each level of nesting should be indented. Default is 3.
         */
        SAXON_INDENT_SPACES(SaxonOutputKeys.INDENT_SPACES),
        /**
         * Saxon extension: set to an integer (represented as a string) giving the desired maximum
         * length of lines when indenting. Default is 80.
         */
        SAXON_LINE_LENGTH(SaxonOutputKeys.LINE_LENGTH),
        /**
         * Saxon extension: set to a space-separated list of attribute names, in Clark notation,
         * indicating that attributes present in the list should be serialized in the order
         * indicated, followed by attributes not present in the list (these are sorted first
         * by namespace, then by local name).
         */
        SAXON_ATTRIBUTE_ORDER(SaxonOutputKeys.ATTRIBUTE_ORDER),
        /**
         * Saxon extension: set to a space-separated list of element names, in Clark notation,
         * within which no content is to be indented. This is typically because the element contains
         * mixed content in which whitespace is significant.
         */
        SAXON_SUPPRESS_INDENTATION(SaxonOutputKeys.SUPPRESS_INDENTATION),
        /**
         * Saxon extension: set to a space-separated list of element names, in Clark notation,
         * representing elements that will be preceded by an extra blank line in the output in addition
         * to normal indentation.
         */
        SAXON_DOUBLE_SPACE(SaxonOutputKeys.DOUBLE_SPACE),
        /**
         * Saxon extension for internal use: used in XSLT to tell the serializer whether the
         * stylesheet used version="1.0" or version="2.0"
         */
        SAXON_STYLESHEET_VERSION(SaxonOutputKeys.STYLESHEET_VERSION),
        /**
         * Saxon extension to indicate how characters outside the encoding should be represented,
         * for example "hex" for hexadecimal character references, "decimal" for decimal character references
         */
        SAXON_CHARACTER_REPRESENTATION(SaxonOutputKeys.CHARACTER_REPRESENTATION),
        /**
         * Saxon extension for use when writing to the text output method; this option causes the processing
         * instructions hex and b64 to be recognized containing hexBinary or base64 data respectively.
         */
        SAXON_RECOGNIZE_BINARY(SaxonOutputKeys.RECOGNIZE_BINARY),
        /**
         * Saxon extension for use when output is sent to a SAX ContentHandler: indicates that the output
         * is required to be well-formed (exactly one top-level element)
         */
        SAXON_REQUIRE_WELL_FORMED(SaxonOutputKeys.REQUIRE_WELL_FORMED),
        /**
         * Saxon extension, indicates that the output of a query is to be wrapped before serialization,
         * such that each item in the result sequence is enclosed in an element indicating its type
         */
        SAXON_WRAP(SaxonOutputKeys.WRAP),
        /**
         * Saxon extension for internal use in XSLT, indicates that this output document is the implicitly
         * created result tree as distinct from a tree created using &lt;xsl:result-document&gt;
         */
        SAXON_IMPLICIT_RESULT_DOCUMENT(SaxonOutputKeys.IMPLICIT_RESULT_DOCUMENT),
        /**
         * Saxon extension for interfacing with debuggers; indicates that the location information is
         * available for events in this output stream
         */
        SAXON_SUPPLY_SOURCE_LOCATOR(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR),

        /**
         * Set to a string used to separate adjacent items in an XQuery result sequence
         */
        ITEM_SEPARATOR(SaxonOutputKeys.ITEM_SEPARATOR);

        private String name;

        private Property(String name) {
            this.name = name;
        }

        /**
         * Get the name of the property expressed as a QName in Clark notation.
         * The namespace will be null for standard serialization properties,
         * and will be the Saxon namespace <code>http://saxon.sf.net/</code> for Saxon extensions
         *
         * @return the name of the serialization property as a QName in Clark notation, {uri}local
         */

        public String toString() {
            return name;
        }

        /**
         * Get the name of the property expressed as a QName.
         * The namespace will be null for standard serialization properties,
         * and will be the Saxon namespace <code>http://saxon.sf.net/</code> for Saxon extensions
         *
         * @return the name of the serialization property as a QName
         */

        public QName getQName() {
            return QName.fromClarkName(name);
        }
    }

    /**
     * Create a Serializer
     */

    public Serializer() {
    }

    /**
     * Create a Serializer initialized to write to a given OutputStream.
     * <p>Closing the output stream after use is the responsibility of the caller.</p>
     *
     * @param stream The OutputStream to which the Serializer will write
     */

    public Serializer(OutputStream stream) {
        setOutputStream(stream);
    }

    /**
     * Create a Serializer initialized to write to a given Writer.
     * <p>Closing the writer after use is the responsibility of the caller.</p>
     *
     * @param writer The Writer to which the Serializer will write
     */

    public Serializer(Writer writer) {
        setOutputWriter(writer);
    }

    /**
     * Create a Serializer initialized to write to a given File.
     *
     * @param file The File to which the Serializer will write
     */

    public Serializer(File file) {
        setOutputFile(file);
    }

    /**
     * Set the Processor associated with this Serializer. This will be called automatically if the
     * serializer is created using one of the <code>Processor.newSerializer()</code> methods. The Serializer
     * currently needs to know about the Processor only if the method {@link #getXMLStreamWriter} is called.
     *
     * @param processor the associated Processor
     * @since 9.3
     */

    public void setProcessor(Processor processor) {
        this.config = processor.getUnderlyingConfiguration();
    }

    /**
     * Set the value of a serialization property. Any existing value of the property is overridden.
     * If the supplied value is null, any existing value of the property is removed.
     * <p/>
     * <p>Example:</p>
     * <p><code>serializer.setOutputProperty(Serializer.Property.METHOD, "xml");</code></p>
     * <p/>
     * <p>Any serialization properties supplied via this interface take precedence over serialization
     * properties defined in the source stylesheet or query.</p>
     *
     * @param property The name of the property to be set
     * @param value    The value of the property, as a string. The format is generally as defined
     *                 in the <code>xsl:output</code> declaration in XSLT: this means that boolean properties, for
     *                 example, are represented using the strings "yes" and "no". Properties whose values are QNames,
     *                 such as <code>cdata-section-elements</code> are expressed using the Clark representation of
     *                 a QName, that is "{uri}local". Multi-valued properties (again, <code>cdata-section-elements</code>
     *                 is an example) are expressed as a space-separated list.
     * @throws IllegalArgumentException if the value of the property is invalid. The property is
     *                                  validated individually; invalid combinations of properties will be detected only when the properties
     *                                  are actually used to serialize an XML event stream.
     */

    public void setOutputProperty(Property property, /*@Nullable*/ String value) {
        try {
            SaxonOutputKeys.checkOutputProperty(property.toString(), value, null);
        } catch (XPathException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (value == null) {
            properties.remove(property);
        } else {
            properties.put(property, value);
        }
    }

    /**
     * Get the value of a serialization property
     *
     * @param property the name of the required property
     * @return the value of the required property as a string, or null if the property has
     *         not been given any value.
     */

    public String getOutputProperty(Property property) {
        return properties.get(property);
    }

    /**
     * Set the destination of the serialized output, as a Writer.
     * <p/>
     * <p>Note that when this option is used, the serializer does not perform character
     * encoding. This also means that it never replaces special characters with XML numeric
     * character references. The final encoding is the responsibility of the supplied Writer.</p>
     * <p/>
     * <p>Closing the writer after use is the responsibility of the caller.</p>
     * <p/>
     * <p>Calling this method has the side-effect of setting the OutputStream and OutputFile to null.</p>
     *
     * @param writer the Writer to which the serialized XML output will be written.
     */

    public void setOutputWriter(Writer writer) {
        result.setOutputStream(null);
        result.setSystemId((String) null);
        result.setWriter(writer);
        mustClose = false;
    }

    /**
     * Set the destination of the serialized output, as an OutputStream.
     * <p/>
     * <p>Closing the output stream after use is the responsibility of the caller.</p>
     * <p/>
     * <p>Calling this method has the side-effect of setting the OutputWriter and OutputFile to null.</p>
     *
     * @param stream the OutputStream to which the serialized XML output will be written.
     */

    public void setOutputStream(OutputStream stream) {
        result.setWriter(null);
        result.setSystemId((String) null);
        result.setOutputStream(stream);
        mustClose = false;
    }

    /**
     * Set the destination of the serialized output, as a File.
     * <p/>
     * <p>Calling this method has the side-effect of setting the current OutputWriter
     * and OutputStream to null.</p>
     *
     * @param file the File to which the serialized XML output will be written.
     */

    public void setOutputFile(File file) {
        result.setOutputStream(null);
        result.setWriter(null);
        result.setSystemId(file);
        mustClose = true;
    }

    /**
     * Serialize an XdmNode to the selected output destination using this serializer
     *
     * @param node The node to be serialized
     * @throws IllegalStateException if no outputStream, Writer, or File has been supplied as the
     *                               destination for the serialized output
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 9.3
     */

    public void serializeNode(XdmNode node) throws SaxonApiException {
        StreamResult res = result;
        if (res.getOutputStream() == null && res.getWriter() == null && res.getSystemId() == null) {
            throw new IllegalStateException("Either an outputStream, or a Writer, or a File must be supplied");
        }
        serializeNodeToResult(node, res);
    }

    /**
     * Serialize an arbitrary XdmValue to the selected output destination using this serializer. The supplied
     * sequence is first wrapped in a document node according to the rules given in section 2 (Sequence Normalization) of the
     * <a href="http://www.w3.org/TR/xslt-xquery-serialization/">XSLT/XQuery serialization specification</a>; the resulting
     * document nodes is then serialized using the serialization parameters defined in this serializer.
     *
     * @param value The value to be serialized
     * @throws IllegalStateException if no outputStream, Writer, or File has been supplied as the
     *                               destination for the serialized output, or if no Processor is associated with the serializer
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 9.3
     */


    public void serializeXdmValue(XdmValue value) throws SaxonApiException {
        if (value instanceof XdmNode) {
            serializeNode((XdmNode) value);
        } else {
            if (config == null) {
                throw new IllegalStateException("The serializer is not associated with any s9api Processor (need to call setProcessor())");
            }
            try {
                QueryResult.serializeSequence(value.getUnderlyingValue().iterate(), config, result, getOutputProperties());
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

    }

    /**
     * Serialize an XdmNode to a string using this serializer
     *
     * @param node The node to be serialized
     * @throws SaxonApiException if a serialization error occurs
     * @return the serialized representation of the node as lexical XML
     * @since 9.3
     */

    public String serializeNodeToString(XdmNode node) throws SaxonApiException {
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        serializeNodeToResult(node, sr);
        return sw.toString();
    }

    private void serializeNodeToResult(XdmNode node, Result res) throws SaxonApiException {
        try {
            QueryResult.serialize(node.getUnderlyingNode(), res, getOutputProperties());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get an XMLStreamWriter that can be used for writing application-generated XML
     * to be output via this serializer.
     *
     * @return a newly constructed XMLStreamWriter that pipes events into this Serializer
     * @throws IllegalStateException if no Processor has been set for this Serializer
     * @throws SaxonApiException if any other failure occurs
     * @since 9.3
     */

    public StreamWriterToReceiver getXMLStreamWriter() throws SaxonApiException {
        if (config == null) {
            throw new IllegalStateException("This method is available only if a Processor has been set");
        }
        Receiver r = getReceiver(config);
        r = new NamespaceReducer(r);
        return new StreamWriterToReceiver(r);
    }

    /**
     * Get the current output destination.
     *
     * @return an OutputStream, Writer, or File, depending on the previous calls to
     *         {@link #setOutputStream}, {@link #setOutputWriter}, or {@link #setOutputFile}; or
     *         null, if no output destination has been set up.
     */

    public Object getOutputDestination() {
        if (result.getOutputStream() != null) {
            return result.getOutputStream();
        }
        if (result.getWriter() != null) {
            return result.getWriter();
        }
        String systemId = result.getSystemId();
        if (systemId != null) {
            try {
                return new File(new URI(systemId));
            } catch (URISyntaxException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Return a receiver to which Saxon will send events. This method is provided
     * primarily for internal use, though it could also be called by user applications
     * wanting to make use of the Saxon serializer.
     *
     * @param config The Saxon configuration. This is an internal implementation object
     *               held within the {@link Processor}
     * @return a receiver to which XML events will be sent
     */

    public Receiver getReceiver(Configuration config) throws SaxonApiException {
        try {
            SerializerFactory sf = config.getSerializerFactory();
            PipelineConfiguration pipe = config.makePipelineConfiguration();
            Properties props = getOutputProperties();
            Receiver target = sf.getReceiver(result, pipe, props);
            if (target.getSystemId() == null) {
                target.setSystemId(result.getSystemId());
            }
            return target;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Return a receiver to which Saxon will send events. This method is provided
     * primarily for internal use, though it could also be called by user applications
     * wanting to make use of the Saxon serializer.
     *
     * @param executable The Saxon Executable for the transformation or query. The serialization
     *                   properties defined in this Serializer are supplemented by properties that have been
     *                   defined in the query or stylesheet associated with the Executable. The properties defined
     *                   in this Serializer take precedence over those in the stylesheet or query.
     * @return a receiver to which XML events will be sent
     * @throws SaxonApiException if any failure occurs
     */

    protected Receiver getReceiver(Executable executable) throws SaxonApiException {
        try {
            Configuration config = executable.getConfiguration();
            SerializerFactory sf = config.getSerializerFactory();
            PipelineConfiguration pipe = config.makePipelineConfiguration();
            pipe.setSerializing(true);
            pipe.setHostLanguage(executable.getHostLanguage());
            Properties baseProps = executable.getDefaultOutputProperties();

            for (Map.Entry<Property, String> entry : properties.entrySet()) {
                QName name = entry.getKey().getQName();
                ResultDocument.setSerializationProperty(
                        baseProps, name.getNamespaceURI(), name.getLocalName(), entry.getValue(), null, true, config);
            }
            Receiver target = sf.getReceiver(result, pipe, baseProps, executable.getCharacterMapIndex());
            if (target.getSystemId() == null) {
                target.setSystemId(result.getSystemId());
            }
            return target;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Create a Properties object holding the defined serialization properties. This
     * will be in the same format as JAXP interfaces such as
     * {@link javax.xml.transform.Transformer#getOutputProperties()}
     *
     * @return a newly-constructed Properties object holding the declared serialization properties
     */

    protected Properties getOutputProperties() {
        Properties props = new Properties();
        for (Entry<Property, String> entry : properties.entrySet()) {
            props.setProperty(entry.getKey().toString(), entry.getValue());
        }
        return props;
    }

    /**
     * Get the JAXP StreamResult object representing the output destination
     * of this serializer
     */

    protected Result getResult() {
        return result;
    }

    /**
     * Close any resources associated with this destination. Note that this does <b>not</b>
     * close any user-supplied OutputStream or Writer; those must be closed explicitly
     * by the calling application.
     */

    public void close() throws SaxonApiException {
        if (mustClose) {
            // This relies on the fact that the SerializerFactory sets the OutputStream
            OutputStream stream = result.getOutputStream();
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException err) {
                    throw new SaxonApiException("Failed while closing output file", err);
                }
            }
            Writer writer = result.getWriter();  // Path not used, but there for safety
            if (writer != null) {
                try {
                    writer.close();
                } catch (java.io.IOException err) {
                    throw new SaxonApiException("Failed while closing output file", err);
                }
            }
        }
    }
}

