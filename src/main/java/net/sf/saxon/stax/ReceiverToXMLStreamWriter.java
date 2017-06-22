////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.stax;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
  * ReceiverToXMLStreamWriter is a Receiver writes XML by using the XMLStreamWriter
  */
public class ReceiverToXMLStreamWriter implements Receiver {

    protected PipelineConfiguration pipe;
    protected Configuration config;
    protected String systemId;
    protected String baseURI;
    private XMLStreamWriter writer;

    public ReceiverToXMLStreamWriter(XMLStreamWriter writer) {
        this.writer = writer;
    }

    public void setPipelineConfiguration(PipelineConfiguration pipe) {
       this.pipe=pipe;
       config = pipe.getConfiguration();
    }

    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    public void setSystemId(String systemId) {
        this.systemId =systemId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void open() throws XPathException {
    }

    public void startDocument(int properties) throws XPathException {
        try {
            writer.writeStartDocument();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void endDocument() throws XPathException {
        try {
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {

    }

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        String local = elemName.getLocalPart();
        String uri = elemName.getURI();
        String prefix = elemName.getPrefix();
        try {
            if(prefix.equals("") && uri.equals("")){
                writer.writeStartElement(local);
            } else if(prefix.equals("") && !uri.equals("")) {
                writer.writeStartElement(prefix, local, uri);
            } else {
                writer.writeStartElement(prefix, local, uri);
            }
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }

    }

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        try {
            writer.writeNamespace(namespaceBinding.getPrefix(), namespaceBinding.getURI());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        String local = attName.getLocalPart();
        String uri = attName.getURI();
        String prefix = attName.getPrefix();
        try {
            if(prefix.equals("") && uri.equals("")) {
                writer.writeAttribute(local, value.toString());
            } else if(prefix.equals("") && !uri.equals("")){
                writer.writeAttribute(uri, local, value.toString());
            } else {
                writer.writeAttribute(prefix, uri, local, value.toString());
            }
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void startContent() throws XPathException {
    }

    public void endElement() throws XPathException {
        try {
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        try {
            writer.writeCharacters(chars.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void processingInstruction(String name, CharSequence data, int locationId, int properties) throws XPathException {
        try {
            writer.writeProcessingInstruction(name, data.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void comment(CharSequence content, int locationId, int properties) throws XPathException {
        try {
            writer.writeComment(content.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public void close() throws XPathException {
        try {
            writer.close();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    public boolean usesTypeAnnotations() {
        return false;
    }
}
