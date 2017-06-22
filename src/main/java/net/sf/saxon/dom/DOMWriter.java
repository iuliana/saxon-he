////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.Whitespace;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


/**
  * DOMWriter is a Receiver that attaches the result tree to a specified Node in the DOM Document
  */

public class DOMWriter extends Builder {

    private PipelineConfiguration pipe;
    private Node currentNode;
    /*@Nullable*/ private Document document;
    private Node nextSibling;
    private int level = 0;
    private boolean canNormalize = true;
    private String systemId;

    /**
    * Set the pipelineConfiguration
    */

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipe = pipe;
        config = pipe.getConfiguration();
    }

    /**
    * Get the pipeline configuration used for this document
    */

    /*@NotNull*/
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Set the System ID of the destination tree
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
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
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId,
     *         or null if setSystemId was not called.
     */
    public String getSystemId() {
        return systemId;
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
        if (document == null) {
            try {
                document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                currentNode = document;
            } catch (ParserConfigurationException err) {
                throw new XPathException(err);
            }
        }
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {}

    /**
    * Start of an element.
    */

    public void startElement (NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        String qname = nameCode.getDisplayName();
        String uri = nameCode.getURI();
        try {
            Element element = document.createElementNS(("".equals(uri) ? null : uri), qname);
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(element, nextSibling);
            } else {
                currentNode.appendChild(element);
            }
            currentNode = element;
        } catch (DOMException err) {
            throw new XPathException(err);
        }
        level++;
    }

    public void namespace (NamespaceBinding namespaceBinding, int properties) throws XPathException {
        try {
        	String prefix = namespaceBinding.getPrefix();
    		String uri = namespaceBinding.getURI();
    		Element element = (Element)currentNode;
            if (!(uri.equals(NamespaceConstant.XML))) {
                if (prefix.length() == 0) {
                    element.setAttributeNS(NamespaceConstant.XMLNS, "xmlns", uri);
                } else {
                    element.setAttributeNS(NamespaceConstant.XMLNS, "xmlns:" + prefix, uri);

                }
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }

    public void attribute (NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
    throws XPathException {
        String qname = nameCode.getDisplayName();
        String uri = nameCode.getURI();
        try {
    		Element element = (Element)currentNode;
            element.setAttributeNS(("".equals(uri) ? null : uri), qname, value.toString());
            // The following code assumes JDK 1.5 or JAXP 1.3
            if (nameCode.equals(StandardNames.XML_ID_NAME) ||
                    (properties & ReceiverOptions.IS_ID) != 0 ||
                    nameCode.isInNamespace(NamespaceConstant.XML) && nameCode.getLocalPart().equals("id")) {
                String localName = nameCode.getLocalPart();
                element.setIdAttributeNS(("".equals(uri) ? null : uri), localName, true);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }

    public void startContent() throws XPathException {}

    /**
    * End of an element.
    */

    public void endElement () throws XPathException {
		if (canNormalize) {
	        try {
	            currentNode.normalize();
	        } catch (Throwable err) {
	        	canNormalize = false;
	        }      // in case it's a Level 1 DOM
	    }

        currentNode = currentNode.getParentNode();
        level--;
    }


    /**
    * Character data.
    */

    public void characters (CharSequence chars, int locationId, int properties) throws XPathException
    {
        if (level == 0 && nextSibling == null && Whitespace.isWhite(chars)) {
            return; // no action for top-level whitespace
        }
        try {
            Text text = document.createTextNode(chars.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(text, nextSibling);
            } else {
                currentNode.appendChild(text);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }


    /**
    * Handle a processing instruction.
    */

    public void processingInstruction (String target, CharSequence data, int locationId, int properties)
        throws XPathException
    {
        try {
            ProcessingInstruction pi =
                document.createProcessingInstruction(target, data.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(pi, nextSibling);
            } else {
                currentNode.appendChild(pi);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }

    /**
    * Handle a comment.
    */

    public void comment (CharSequence chars, int locationId, int properties) throws XPathException
    {
        try {
            Comment comment = document.createComment(chars.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(comment, nextSibling);
            } else {
                currentNode.appendChild(comment);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
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
     * Set the attachment point for the new subtree
     * @param node the node to which the new subtree will be attached
    */

    public void setNode (Node node) {
        if (node == null) {
            return;
        }
        currentNode = node;
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            document = (Document)node;
        } else {
            document = currentNode.getOwnerDocument();
            if (document == null) {
                // which might be because currentNode() is a parentless ElementOverNodeInfo.
                // we create a DocumentOverNodeInfo, which is immutable, and will cause the DOMWriter to fail
                document = new DocumentOverNodeInfo();
            }
        }
    }

    /**
     * Set next sibling
     * @param nextSibling the node, which must be a child of the attachment point, before which the new subtree
     * will be created. If this is null the new subtree will be added after any existing children of the
     * attachment point.
     */

    public void setNextSibling(Node nextSibling) {
        this.nextSibling = nextSibling;
    }

    /**
     * Get the current root node. This will normally be a document node, but if the root of the tree
     * is an element node, it can be an element.
     * @return the root of the tree that is currently being built, or that has been most recently built
     * using this builder
     */

    @Override
    public NodeInfo getCurrentRoot() {
        return new DocumentWrapper(document, systemId, config);
    }
}

