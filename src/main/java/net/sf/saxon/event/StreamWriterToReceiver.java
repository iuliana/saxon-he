////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements the XmlStreamWriter interface, translating the events into Saxon
 * Receiver events. The Receiver can be anything: a serializer, a schema validator, a tree builder.
 *
 * <p>The class will attempt to generate namespace prefixes where none have been supplied, unless the
 * <code>inventPrefixes</code> option is set to false. The preferred mode of use is to call the versions
 * of <code>writeStartElement</code> and <code>writeAttribute</code> that supply the prefix, URI, and
 * local name in full. If the prefix is omitted, the class attempts to invent a prefix. If the URI is
 * omitted, the name is assumed to be in no namespace. The <code>writeNamespace</p> method should be
 * called only if there is a need to declare a namespace prefix that is not used on any element or
 * attribute name.</p>
 *
 * <p>The class will check all names, URIs, and character content for conformance against XML well-formedness
 * rules unless the <code>checkValues</code> option is set to false.</p>
 *
 * @since 9.3
 */
public class StreamWriterToReceiver implements XMLStreamWriter {

    /**
     * The receiver to which events will be passed
     */

    private Receiver receiver;

    /**
     * The Saxon NamePool
     */

    private NamePool namePool;

    /**
     * The Name Checker
     */

    private NameChecker nameChecker;

    /**
     * Flag to indicate whether names etc are to be checked for well-formedness
     */

    private boolean isChecking = false;

    /**
     * The current depth of element nesting. -1 indicates outside startDocument/endDocument; non-negative
     * values indicate the number of open start element tags
     */

    private int depth = -1;

    /**
     * The namespace context used to determine the namespace-prefix mappings
     * in scope.
     */
    //protected NamespaceContext namespaceContext;

     /**
     * Flag set to true during processing of a start tag.
     */
    private boolean inStartTag;

    /**
     * Flag indicating that an empty element has been requested.
     */
    private boolean isEmptyElement;

    /**
     * Flag indicating that default prefixes should be allocated if the user does not declare them explicitly
     */

    private boolean inventPrefixes = true;

    /**
     * inScopeNamespaces represents namespaces that have been declared in the XML stream
     */
    private NamespaceReducer inScopeNamespaces;

    /**
     * declaredNamespaces represents prefix-to-uri bindings that have been set using setPrefix. These
     * do not necessarily correspond to namespace declarations appearing in the XML stream. Note that
     * this is a map from URIs to prefixes, not the other way around!
     */

    private Map<String, String> declaredNamespaces = new HashMap<String, String>(10);

    /**
     * rootNamespaceContext is the namespace context supplied at the start, is the final fallback
     * for allocating a prefix to a URI
     */

    private javax.xml.namespace.NamespaceContext rootNamespaceContext = null;

    /**
     * Constructor. Creates a StreamWriter as a front-end to a given Receiver.
     * @param receiver the Receiver that is to receive the events generated
     * by this StreamWriter.
     */
    public StreamWriterToReceiver(Receiver receiver) {
        // Events are passed through a NamespaceReducer which maintains the namespace context
        // It also eliminates duplicate namespace declarations, and creates extra namespace declarations
        // where needed to support prefix-uri mappings used on elements and attributes
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        this.inScopeNamespaces = new NamespaceReducer(receiver);
        this.receiver = inScopeNamespaces;
        this.nameChecker = pipe.getConfiguration().getNameChecker();
        this.namePool = pipe.getConfiguration().getNamePool();
    }

    /**
     * Say whether prefixes are to be invented when none is specified by the user
     * @param invent true if prefixes are to be invented. Default is true;
     */

    public void setInventPrefixes(boolean invent) {
        this.inventPrefixes = invent;
    }

    /**
     * Ask whether prefixes are to be invented when none is specified by the user
     * @return true if prefixes are to be invented. Default is true;
     */

    public boolean isInventPrefixes() {
        return this.inventPrefixes;
    }

    /**
     * Say whether names and values are to be checked for conformance with XML rules
     * @param check true if names and values are to be checked. Default is true;
     */

    public void setCheckValues(boolean check) {
        this.isChecking = check;
    }

    /**
     * Ask whether names and values are to be checked for conformance with XML rules
     * @return true if names and values are to be checked. Default is true;
     */

    public boolean isCheckValues() {
        return this.isChecking;
    }



    public void writeStartElement(/*@NotNull*/ String localName) throws XMLStreamException {
        String uri = inScopeNamespaces.getURIForPrefix("", true);
        assert uri != null;
        writeStartElement("", localName, uri);
    }

    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        String prefix = getPrefix(namespaceURI);
        boolean isDeclared = (prefix != null);
        if (!isDeclared) {
            if (inventPrefixes) {
                prefix = inventPrefix(namespaceURI);
            } else {
                throw new XMLStreamException("namespace " + namespaceURI + " has not been declared");
            }
        }
        writeStartElement(prefix, localName, namespaceURI);

    }

    public void writeStartElement(/*@NotNull*/ String prefix, /*@NotNull*/ String localName, /*@NotNull*/ String namespaceURI) throws XMLStreamException {
        if (depth == -1) {
            writeStartDocument();
        }
        try {
            if (!isValidURI(namespaceURI)) {
                throw new IllegalArgumentException("Invalid namespace URI: " + namespaceURI);
            }
            if (!isValidNCName(prefix)) {
                throw new IllegalArgumentException("Invalid prefix: " + prefix);
            }
            if (!isValidNCName(localName)) {
                throw new IllegalArgumentException("Invalid local name: " + localName);
            }

            startContent();
            inStartTag = true;
            depth++;
            NodeName nc;
            if (namespaceURI.length()==0) {
                nc = new NoNamespaceName(localName);
            } else {
                nc = new FingerprintedQName(prefix, namespaceURI, localName);
            }
            receiver.startElement(nc, Untyped.getInstance(), 0, 0);
            inStartTag = true;
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    /**
     * Creates a prefix that is not currently in use.
     * @param uri the URI for which a prefix is required
     * @return the chosen prefix
     */
    private String inventPrefix(String uri)  {
        String prefix = getPrefix(uri);
        if (prefix != null) {
            return prefix;
        }
        prefix = namePool.suggestPrefixForURI(uri);
        if (prefix != null) {
            return prefix;
        }
        int count = 0;
        while (true) {
            prefix = "ns" + count;
            if (inScopeNamespaces.getURIForPrefix(prefix, false) == null) {
                setPrefix(prefix, uri);
                return prefix;
            }
            count++;
        }
     }

    public void writeEmptyElement(String namespaceURI, String localName)
            throws XMLStreamException {
        writeStartElement(namespaceURI, localName);
        isEmptyElement = true;
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        writeStartElement(prefix, localName, namespaceURI);
        isEmptyElement = true;
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        writeStartElement(localName);
        isEmptyElement = true;
    }

    /**
     * Indicate the end of a start tag if one is open (no action otherwise).
     * This will also write an end tag if the element was opened as an empty element.
     * @throws javax.xml.stream.XMLStreamException if an error occurs writing to the output stream
     */
    private void startContent() throws XMLStreamException {
        if (inStartTag) {
            try {
                receiver.startContent();
            } catch (XPathException err) {
                throw new XMLStreamException(err);
            }
            inStartTag = false;
            if (isEmptyElement) {
                isEmptyElement = false;
                writeEndElement();
            }
        }
    }

    public void writeEndElement() throws XMLStreamException {
        if (depth <= 0) {
            throw new IllegalStateException("writeEndElement with no matching writeStartElement");
        }
//        if (isEmptyElement) {
//            throw new IllegalStateException("writeEndElement called for an empty element");
//        }
        try {
            startContent();
            receiver.endElement();
            depth--;
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void writeEndDocument() throws XMLStreamException {
        if (depth == -1) {
            throw new IllegalStateException("writeEndDocument with no matching writeStartDocument");
        }
        try {
            if (isEmptyElement) {
                startContent(); // which also ends the element and decrements depth
            }
            while (depth > 0) {
                writeEndElement();
            }
            receiver.endDocument();
            depth = -1;
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void close() throws XMLStreamException {
        if (depth >= 0) {
            writeEndDocument();
        }
        try {
            receiver.close();
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void flush() throws XMLStreamException {
        // no action
    }

    public void writeAttribute(String localName, String value) throws XMLStreamException {
        writeAttribute("", "", localName, value);
    }

    public void writeAttribute(/*@Nullable*/ String prefix, /*@Nullable*/ String namespaceURI, String localName, String value)
            throws XMLStreamException {
        if (!inStartTag) {
            throw new IllegalStateException("Cannot write attribute when not in a start tag");
        }
        if (prefix == null) {
            prefix = "";
        }
        if (namespaceURI == null) {
            namespaceURI = "";
        }
        if (namespaceURI.length() != 0 && !isValidURI(namespaceURI)) {
            throw new IllegalArgumentException("Invalid attribute namespace URI: " + namespaceURI);
        }
        if (prefix.length() != 0 && !isValidNCName(prefix)) {
            throw new IllegalArgumentException("Invalid attribute prefix: " + prefix);
        }
        if (!isValidNCName(localName)) {
            throw new IllegalArgumentException("Invalid attribute local name: " + localName);
        }
        if (!isValidChars(value)) {
            throw new IllegalArgumentException("Invalid characters in attribute content: " + value);
        }
        try {
            NodeName nn = new FingerprintedQName(prefix, namespaceURI, localName);
            receiver.attribute(nn, BuiltInAtomicType.UNTYPED_ATOMIC, value, -1, 0);
        }
        catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        String prefix = getPrefix(namespaceURI);
        if (prefix == null) {
            if (inventPrefixes) {
                prefix = inventPrefix(namespaceURI);
            } else {
                throw new XMLStreamException("Namespace " + namespaceURI + " has not been declared");
            }
        }
        writeAttribute(prefix, namespaceURI, localName, value);
    }

    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        if (!inStartTag) {
            throw new IllegalStateException("Cannot write namespace when not in a start tag");
        }
        if (prefix == null || "".equals(prefix) || "xmlns".equals(prefix)) {
            writeDefaultNamespace(namespaceURI);
            return;
        }
        if (!isValidURI(namespaceURI)) {
            throw new IllegalArgumentException("Invalid namespace URI: " + namespaceURI);
        }
        if (!isValidNCName(prefix)) {
            throw new IllegalArgumentException("Invalid namespace prefix: " + prefix);
        }
        outputNamespaceDeclaration(prefix, namespaceURI);
    }

    public void writeDefaultNamespace(String namespaceURI)
            throws XMLStreamException {
        if (!inStartTag) {
            throw new IllegalStateException();
        }
        if (!isValidURI(namespaceURI)) {
            throw new IllegalArgumentException("Invalid namespace URI: " + namespaceURI);
        }
        outputNamespaceDeclaration("", namespaceURI);
    }

    private void outputNamespaceDeclaration(String prefix, String namespaceURI) throws XMLStreamException {
        try {
            if (prefix == null) {
                prefix = "";
            }
            receiver.namespace(new NamespaceBinding(prefix, namespaceURI), 0);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void writeComment(/*@Nullable*/ String data) throws XMLStreamException {
        if (data == null) {
            data = "";
        }
        try {
            if (!isValidChars(data)) {
                throw new IllegalArgumentException("Invalid XML character in comment: " + data);
            }
            if (isChecking && data.contains("--")) {
                throw new IllegalArgumentException("Comment contains '--'");
            }
            startContent();
            receiver.comment(data, 0, 0);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        writeProcessingInstruction(target, "");
    }

    public void writeProcessingInstruction(/*@NotNull*/ String target, /*@NotNull*/ String data) throws XMLStreamException {
        try {
            if (isChecking) {
                if (!isValidNCName(target) || "xml".equalsIgnoreCase(target)) {
                    throw new IllegalArgumentException("Invalid PITarget: " + target);
                }
                if (!isValidChars(data)) {
                    throw new IllegalArgumentException("Invalid character in PI data: " + data);
                }
            }
            startContent();
            receiver.processingInstruction(target, data, 0, 0);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    public void writeCData(/*@NotNull*/ String data) throws XMLStreamException {
        writeCharacters(data);
    }

    public void writeDTD(String dtd) throws XMLStreamException {
        // no-op
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        throw new UnsupportedOperationException("writeEntityRef");
    }

    public void writeStartDocument() throws XMLStreamException {
        writeStartDocument(null, null);
    }

    public void writeStartDocument(/*@Nullable*/ String version) throws XMLStreamException {
        writeStartDocument(null, version);
    }

    public void writeStartDocument(/*@Nullable*/ String encoding, /*@Nullable*/ String version) throws XMLStreamException {
        if ("1.1".equals(version)) {
            nameChecker = Name11Checker.getInstance();
        }
        if (depth != -1) {
            throw new IllegalStateException("writeStartDocument must be the first call");
        }
        try {
            receiver.startDocument(0);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
        depth = 0;
    }

    public void writeCharacters(/*@Nullable*/ String text)
            throws XMLStreamException {
        if (text != null) {
            if (!isValidChars(text)) {
                throw new IllegalArgumentException("illegal XML character: " + text);
            }
            startContent();
            try {
                receiver.characters(text, 0, 0);
            } catch (XPathException err) {
                throw new XMLStreamException(err);
            }
        }
    }

    public void writeCharacters(char[] text, int start, int len)
            throws XMLStreamException {
        writeCharacters(new String(text, start, len));
    }

    public String getPrefix(String uri) {
        String prefix = declaredNamespaces.get(uri);
        if (prefix == null && rootNamespaceContext != null) {
            prefix = rootNamespaceContext.getPrefix(uri);
        }
        return prefix;
    }

    public void setPrefix(String prefix, String uri) {
        if (!isValidURI(uri)) {
            throw new IllegalArgumentException("Invalid namespace URI: " + uri);
        }
        if (!"".equals(prefix) && !isValidNCName(prefix)) {
            throw new IllegalArgumentException("Invalid namespace prefix: " + prefix);
        }
        declaredNamespaces.put(uri, prefix); //sic
    }

    public void setDefaultNamespace(String uri)
            throws XMLStreamException {
        setPrefix("", uri);
    }

    public void setNamespaceContext(javax.xml.namespace.NamespaceContext context)
            throws XMLStreamException {
        if (depth > 0) {
            throw new IllegalStateException("setNamespaceContext may only be called at the start of the document");
        }
        // Unfortunately the JAXP NamespaceContext class does not allow us to discover all the namespaces
        // that were declared, nor to declare new ones. So we have to retain it separately
        rootNamespaceContext = context;
    }

    /*@Nullable*/ public javax.xml.namespace.NamespaceContext getNamespaceContext() {
        // Note: the spec is unclear. We return the namespace context that was supplied to setNamespaceContext,
        // regardless of any other subsequent additions
        return rootNamespaceContext;
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        throw new IllegalArgumentException(name);
    }

    /**
     * Test whether a supplied name is a valid NCName
     * @param name the name to be tested
     * @return true if the name is valid or if checking is disabled
     */

    private boolean isValidNCName(String name) {
        return !isChecking || nameChecker.isValidNCName(name);
    }

    /**
     * Test whether a supplied character string is valid in XML
     * @param text the string to be tested
     * @return true if the string is valid or if checking is disabled
     */

    private boolean isValidChars(String text) {
        return !isChecking || (nameChecker.firstInvalidChar(text) == -1);
    }

    /**
     * Test whether a supplied namespace URI is a valid URI
     * @param uri the namespace URI to be tested
     * @return true if the name is valid or if checking is disabled
     */

    private boolean isValidURI(String uri) {
        return !isChecking || StandardURIChecker.getInstance().isValidURI(uri);
    }

}

