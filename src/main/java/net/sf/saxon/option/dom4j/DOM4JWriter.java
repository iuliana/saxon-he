////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.dom4j;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import org.dom4j.*;
import org.dom4j.tree.*;

import java.util.Stack;

/**
  * JDOMWriter is a Receiver that constructs a JDOM document from the stream of events
  */

public class DOM4JWriter extends net.sf.saxon.event.Builder {

    private Document document;
    private Stack<Branch> ancestors = new Stack<Branch>();
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.MEDIUM);

    /**
     * Create a JDOMWriter using the default node factory
     */

    public DOM4JWriter(PipelineConfiguration pipe) {
        super(pipe);
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
        document = new DefaultDocument();
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
        Element element;
        if (ancestors.isEmpty()) {
            startDocument(0);
            implicitDocumentNode = true;
        }
        QName name = new QName(local, new Namespace(prefix, uri));
        if (ancestors.size() == 1) {
            element = new DefaultElement(name);
            document.setRootElement(element);
        } else {
            element = (ancestors.peek()).addElement(name);
        }
        ancestors.push(element);
    }

    public void namespace (NamespaceBinding namespaceBinding, int properties) throws XPathException {
        String prefix = namespaceBinding.getPrefix();
        String uri = namespaceBinding.getURI();
        ((Element)ancestors.peek()).addNamespace(prefix, uri);
    }

    public void attribute (NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
    throws XPathException {
        String local = nameCode.getLocalPart();
        String uri = nameCode.getURI();
        String prefix = nameCode.getPrefix();
        Namespace ns = new Namespace(prefix, uri);
        Attribute att = new DefaultAttribute(local, value.toString(), ns);
        ((Element)ancestors.peek()).add(att);
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
            Text text = new DefaultText(textBuffer.toString());
            ancestors.peek().add(text);
            textBuffer.setLength(0);
        }
    }


    /**
    * Handle a processing instruction.
    */

    public void processingInstruction (String target, CharSequence data, int locationId, int properties)
            throws XPathException {
        flush();
        ProcessingInstruction pi = new DefaultProcessingInstruction(target, data.toString());
        ancestors.peek().add(pi);
    }

    /**
    * Handle a comment.
    */

    public void comment (CharSequence chars, int locationId, int properties) throws XPathException{
        flush();
        Comment comment = new DefaultComment(chars.toString());
        ancestors.peek().add(comment);
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

    /*@Nullable*/ public NodeInfo getCurrentRoot() {
        return new DOM4JDocumentWrapper(document, systemId, config);
    }
}

