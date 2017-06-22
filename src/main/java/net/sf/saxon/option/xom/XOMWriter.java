////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.xom;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import nu.xom.*;

import java.util.Stack;

/**
  * XOMWriter is a Receiver that constructs a XOM document from the stream of events
  */

public class XOMWriter extends net.sf.saxon.event.Builder {

    //private PipelineConfiguration pipe;
    //private NamePool namePool;
    private Document document;
    private Stack<ParentNode> ancestors = new Stack<ParentNode>();
    private NodeFactory nodeFactory;
//    private String systemId;
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.SMALL);

    /**
     * Create a XOMWriter using the default node factory
     * @param pipe the pipeline configuration
     */

    public XOMWriter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        this.nodeFactory = new NodeFactory();
    }

    /**
     * Create a XOMWriter
     * @param pipe the pipeline configuration
     * @param factory the XOM NodeFactory to be used
     */

    public XOMWriter(/*@NotNull*/ PipelineConfiguration pipe, /*@NotNull*/ NodeFactory factory) {
        super(pipe);
        this.nodeFactory = factory;
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
        document = nodeFactory.startMakingDocument();
        try {
            document.setBaseURI(systemId);
        } catch (MalformedURIException e) {
            // XOM objects if the URI is invalid
            throw new XPathException(e);
        }
        ancestors.push(document);
        textBuffer.setLength(0);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        nodeFactory.finishMakingDocument(document);
        ancestors.pop();
    }

    /**
    * Start of an element.
    */

    public void startElement (NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        flush();
        String qname = nameCode.getDisplayName();
        String uri = nameCode.getURI();
        Element element;
        if (ancestors.isEmpty()) {
            startDocument(0);
            implicitDocumentNode = true;
        }
        if (ancestors.size() == 1) {
            element = nodeFactory.makeRootElement(qname, uri);
            document.setRootElement(element);
        } else {
            element = nodeFactory.startMakingElement(qname, uri);
        }
        if (element == null) {
            throw new XPathException("XOM node factory returned null");
        }
        ancestors.push(element);
    }

    public void namespace (NamespaceBinding namespaceBinding, int properties) throws XPathException {
        String prefix = namespaceBinding.getPrefix();
        String uri = namespaceBinding.getURI();
        try {
            ((Element)ancestors.peek()).addNamespaceDeclaration(prefix, uri);
        } catch (MalformedURIException e) {
            throw new XPathException("XOM requires namespace names to be legal URIs: " + uri);
        }
    }

    public void attribute (NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
    throws XPathException {
        String qname = nameCode.getDisplayName();
        String uri = nameCode.getURI();
        Nodes nodes = nodeFactory.makeAttribute(qname, uri, value.toString(), Attribute.Type.CDATA);
        for (int n=0; n<nodes.size(); n++) {
            Node node = nodes.get(n);
            if (node instanceof Attribute) {
                ((Element)ancestors.peek()).addAttribute((Attribute) node);
            } else {
                ((Element)ancestors.peek()).appendChild(node);
            }
        }
    }

    public void startContent() throws XPathException {
        flush();
    }

    /**
    * End of an element.
    */

    public void endElement () throws XPathException {
        flush();
        Element element = (Element)ancestors.pop();
        Node parent = ancestors.peek();
        Nodes nodes = nodeFactory.finishMakingElement(element);
        if (parent == document) {
            if (implicitDocumentNode) {
                endDocument();
            }
        } else {
            for (int n=0; n<nodes.size(); n++) {
                Node node = nodes.get(n);
                if (node instanceof Attribute) {
                    ((Element)parent).addAttribute((Attribute) node);
                } else {
                    ((Element)parent).appendChild(node);
                }
            }
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
            Nodes nodes = nodeFactory.makeText(textBuffer.toString());
            for (int n=0; n<nodes.size(); n++) {
                Node node = nodes.get(n);
                if (node instanceof Attribute) {
                    ((Element)ancestors.peek()).addAttribute((Attribute) node);
                } else {
                    ancestors.peek().appendChild(node);
                }
            }
            textBuffer.setLength(0);
        }
    }


    /**
    * Handle a processing instruction.
    */

    public void processingInstruction (String target, CharSequence data, int locationId, int properties)
            throws XPathException {
        flush();
        Nodes nodes = nodeFactory.makeProcessingInstruction(target, data.toString());
        for (int n=0; n<nodes.size(); n++) {
            Node node = nodes.get(n);
            if (node instanceof Attribute) {
                ((Element)ancestors.peek()).addAttribute((Attribute) node);
            } else {
                 ancestors.peek().appendChild(node);
            }
        }
    }

    /**
    * Handle a comment.
    */

    public void comment (CharSequence chars, int locationId, int properties) throws XPathException{
        flush();
        Nodes nodes = nodeFactory.makeComment(chars.toString());
        for (int n=0; n<nodes.size(); n++) {
            Node node = nodes.get(n);
            if (node instanceof Attribute) {
                ((Element)ancestors.peek()).addAttribute((Attribute) node);
            } else {
                ancestors.peek().appendChild(node);
            }
        }
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

    public Document getDocument() {
        return document;
    }

    /**
     * Get the current root node.
     * @return a Saxon wrapper around the constructed XOM document node
     */

    public NodeInfo getCurrentRoot() {
        return new XOMDocumentWrapper(document, systemId, config);
    }
}

