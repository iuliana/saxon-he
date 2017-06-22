////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import org.apache.axiom.om.*;

import java.util.Stack;

/**
  * JDOMWriter is a Receiver that constructs a JDOM document from the stream of events
  */

public class AxiomWriter extends net.sf.saxon.event.Builder {

    private OMFactory factory;
    private OMDocument document;
    private Stack<OMContainer> ancestors = new Stack<OMContainer>();
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.MEDIUM);

    /**
     * Create a JDOMWriter using the default node factory
     * @param pipe information about the Saxon pipeline
     */

    public AxiomWriter(PipelineConfiguration pipe) {
        super(pipe);
        factory = OMAbstractFactory.getOMFactory();
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
       // no-op
    }

    /**
    * Start of the document.
    */

    public void open () {}

    /**
    * End of the document.
    */

    public void close () {}

    /**
     * Start of a document node.
    */

    public void startDocument(int properties) throws XPathException {
        document = factory.createOMDocument();
        ancestors.push(document);
        textBuffer.setLength(0);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        ancestors.pop();
    }

    /**
    * Start of an element.
    */

    public void startElement (NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        flush();
        String local = nameCode.getLocalPart();
        String uri = nameCode.getURI();
        String prefix = nameCode.getPrefix();
        if (ancestors.isEmpty()) {
            startDocument(0);
            implicitDocumentNode = true;
        }
        OMElement element;
        if (uri.length() != 0) {
            OMNamespace ns = factory.createOMNamespace(uri, prefix);
            element = factory.createOMElement(local, ns);
        } else {
            element = factory.createOMElement(local, null);
        }
        //OMElement element = factory.createOMElement(new QName(uri, local, prefix));
        if (ancestors.size() == 1) {
            document.setOMDocumentElement(element);
        } else {
            ancestors.peek().addChild(element);
        }
        ancestors.push(element);
    }

    public void namespace (NamespaceBinding namespaceBinding, int properties) throws XPathException {
        String prefix =  namespaceBinding.getPrefix();
        String uri =  namespaceBinding.getURI();
        if (prefix.equals("")) {
            ((OMElement)ancestors.peek()).declareDefaultNamespace(uri);
        } else if (uri.equals("")) {
            // ignore namespace undeclarations - Axiom can't handle them
        } else {
            OMNamespace ns = factory.createOMNamespace(uri, prefix);
            ((OMElement)ancestors.peek()).declareNamespace(ns);
        }
    }

    public void attribute (NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
    throws XPathException {
        String local = nameCode.getLocalPart();
        String uri = nameCode.getURI();
        String prefix = nameCode.getPrefix();
        OMNamespace ns = (uri.length()==0 ? null : factory.createOMNamespace(uri, prefix));
        OMAttribute att = factory.createOMAttribute(local, ns, value.toString());
        if ((properties & ReceiverOptions.IS_ID) != 0 || (local.equals("id") && uri.equals(NamespaceConstant.XML))) {
            att.setAttributeType("ID");
        } else if ((properties & ReceiverOptions.IS_IDREF) != 0) {
            att.setAttributeType("IDREF");
        }
        ((OMElement)ancestors.peek()).addAttribute(att);
    }

    public void startContent() throws XPathException {
        flush();
    }

    /**
    * End of an element.
    */

    public void endElement () throws XPathException {
        flush();
        ancestors.pop();
        Object parent = ancestors.peek();
        if (parent == document && implicitDocumentNode) {
            endDocument();
        }
    }

    /**
    * Character data.
    */

    public void characters (CharSequence chars, int locationId, int properties) throws XPathException {
        textBuffer.append(chars);
    }

    private void flush() {
        if (textBuffer.length() != 0) {
            OMText text = factory.createOMText(textBuffer.toString());
            ancestors.peek().addChild(text);
            textBuffer.setLength(0);
        }
    }


    /**
    * Handle a processing instruction.
    */

    public void processingInstruction (String target, CharSequence data, int locationId, int properties)
            throws XPathException {
        flush();
        OMContainer parent = ancestors.peek();
        OMProcessingInstruction pi = factory.createOMProcessingInstruction(parent, target, data.toString());
        //parent.addChild(pi);
    }

    /**
    * Handle a comment.
    */

    public void comment (CharSequence chars, int locationId, int properties) throws XPathException{
        flush();
        OMContainer parent = ancestors.peek();
        OMComment comment = factory.createOMComment(parent, chars.toString());
        //parent.addChild(comment);
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Get the constructed document node
     * @return the document node of the constructed XOM tree
     */

    public OMDocument getDocument() {
        return document;
    }

    /**
     * Get the current root node.
     * @return a Saxon wrapper around the constructed XOM document node
     */

    /*@Nullable*/ public NodeInfo getCurrentRoot() {
        return new AxiomDocumentWrapper(document, systemId, config);
    }
}

