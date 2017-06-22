////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.SaxonLocator;
import net.sf.saxon.event.SourceLocationProvider;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;
import org.w3c.dom.*;
import org.xml.sax.helpers.NamespaceSupport;

import java.util.HashMap;
import java.util.Map;

/**
* DOMSender.java: pseudo-SAX driver for a DOM source document.
* This class takes an existing
* DOM Document and walks around it in a depth-first traversal,
* calling a Receiver to process the nodes as it does so
*/

public class DOMSender implements SaxonLocator, SourceLocationProvider {
    /*@NotNull*/ private Receiver receiver;
    /*@NotNull*/ protected Node root;
    private NamespaceSupport nsSupport = new NamespaceSupport();
    private String[] parts = new String[3];
    private String[] elparts = new String[3];
    private HashMap<String, String> nsDeclarations = new HashMap<String, String>(10);
    protected String systemId;

    /**
     * Create a DOMSender that will send events representing the nodes in a tree
     * to a nominated receiver
     * @param startNode the root node of the tree to be send. Usually a document or element node.
     * @param receiver the object to be notified of the resulting events. The supplied Receiver must
     * be initialized with a PipelineConfiguration.The PipelineConfiguration
     * of the Receiver will be modified to set this DOMSender as its LocationProvider.
     */

    public DOMSender(Node startNode, Receiver receiver) {
        if (startNode == null) {
            throw new NullPointerException("startNode");
        }
        if (receiver == null) {
            throw new NullPointerException("receiver");
        }
        this.root = startNode;
        this.receiver = receiver;
    }

   /**
     * Set the systemId of the source document (which will also be
     * used for the destination)
     * @param systemId the systemId of the source document
    */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Walk a tree (traversing the nodes depth first).
     * @throws IllegalStateException if the
     * start node is of a node kind other than document, document fragment, element, text,
     * comment, or processing instruction (for example, if it is an attribute node).
     * @throws net.sf.saxon.trans.XPathException On any error in the document
    */

    public void send() throws XPathException {
        receiver.setSystemId(systemId);
        receiver.getPipelineConfiguration().setLocationProvider(this);

        receiver.open();
        switch (root.getNodeType()) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                receiver.startDocument(0);
                walkNode(root);
                receiver.endDocument();
                break;
            case Node.ELEMENT_NODE:
                sendElement((Element)root);
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                receiver.characters(((CharacterData)root).getData(), 0, 0);
                break;
            case Node.COMMENT_NODE:
                receiver.comment(((Comment)root).getData(), 0, 0);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                receiver.processingInstruction(
                        ((ProcessingInstruction)root).getTarget(),
                        ((ProcessingInstruction)root).getData(), 0, 0);
                break;
            default:
                throw new IllegalStateException("DOMSender: unsupported kind of start node (" + root.getNodeType() + ")");
        }
        receiver.close();
    }

    /**
     * Walk a tree starting from a particular element node. This has to make
     * sure that all the namespace declarations in scope for the element are
     * treated as if they were namespace declarations on the element itself.
     * @param startNode the start element node from which the walk will start
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */

    private void sendElement(Element startNode) throws XPathException {
        Element node = startNode;
        boolean hasNamespaceDeclarations = gatherNamespaces(node, false);
        while (true) {
            gatherNamespaces(node, true);
            Node parent = node.getParentNode();
            if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                node = (Element)parent;
            } else {
                break;
            }
        }
        outputElement(startNode, hasNamespaceDeclarations);
    }

  /**
    * Walk an element of a document (traversing the children depth first)
    * @param node The DOM Element object to walk
    * @exception net.sf.saxon.trans.XPathException On any error in the document
    *
    */

    private void walkNode (Node node) throws XPathException {
        // See https://saxonica.plan.io/issues/1955
        if (node.hasChildNodes()) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                switch (child.getNodeType()) {
                    case Node.DOCUMENT_NODE:
                    case Node.DOCUMENT_FRAGMENT_NODE:
                        break;                  // should not happen
                    case Node.ELEMENT_NODE:
                        Element element = (Element)child;
                        boolean hasNamespaces = gatherNamespaces(element, false);
                        outputElement(element, hasNamespaces);
                        nsSupport.popContext();
                        break;
                    case Node.ATTRIBUTE_NODE:        // have already dealt with attributes
                        break;
                    case Node.PROCESSING_INSTRUCTION_NODE:
                        receiver.processingInstruction(
                            ((ProcessingInstruction)child).getTarget(),
                            ((ProcessingInstruction)child).getData(),
                                0, 0);
                        break;
                    case Node.COMMENT_NODE: {
                        String text = ((Comment)child).getData();
                        if (text!=null) {
                            receiver.comment(text, 0, 0);
                        }
                        break;
                    }
                    case Node.TEXT_NODE:
                    case Node.CDATA_SECTION_NODE: {
                        String text = ((CharacterData)child).getData();
                        if (text!=null) {
                            receiver.characters(text, 0, 0);
                        }
                        break;
                    }
                    case Node.ENTITY_REFERENCE_NODE:
                        walkNode(child);
                        break;
                    default:
                        break;                  // should not happen
                }
            }
        }

    }

    private void outputElement(Element element, boolean hasNamespaceDeclarations) throws XPathException {
        String[] elparts2 = nsSupport.processName(element.getTagName(), elparts, false);
        if (elparts2==null) {
              throw new XPathException("Undeclared namespace in " + element.getTagName());
        }
        String uri = elparts2[0];
        String local = elparts2[1];
        String prefix = NameChecker.getPrefix(elparts2[2]);

        receiver.startElement(new FingerprintedQName(prefix, uri, local), Untyped.getInstance(), 0, 0);
        for (Map.Entry<String, String> decl : nsDeclarations.entrySet()) {
            receiver.namespace(new NamespaceBinding(decl.getKey(), decl.getValue()), 0);
        }

        NamedNodeMap atts = element.getAttributes();
        if (atts != null) {
            final int len = atts.getLength();
            for (int a2=0; a2<len; a2++) {
                Attr att = (Attr)atts.item(a2);
                String attname = att.getName();
                if (hasNamespaceDeclarations && (attname.equals("xmlns") || attname.startsWith("xmlns:"))) {
                    // do nothing: namespace declarations have already been processed
                } else {
                    //System.err.println("Processing attribute " + attname);
                    String[] parts2 = nsSupport.processName(attname, parts, true);
                    if (parts2==null) {
                          throw new XPathException("Undeclared namespace in " + attname);
                    }
                    String atturi = parts2[0];
                    String attlocal = parts2[1];
                    String attprefix = NameChecker.getPrefix(parts2[2]);

                    // Note, DOM gives no guarantee that the prefix and URI are actually consistent. For example,
                    // it's possible programmatically to construct attribute nodes that have a namespace URI but
                    // no prefix. We don't attempt to deal with such situations: garbage in, garbage out.

                    NodeName attCode = new FingerprintedQName(attprefix, atturi, attlocal);

                    receiver.attribute(attCode, BuiltInAtomicType.UNTYPED_ATOMIC, att.getValue(), 0, 0);
                }
            }
        }
        receiver.startContent();

        walkNode(element);

        receiver.endElement();
    }

    /**
     * Collect all the namespace attributes in scope for a given element. The namespace
     * declaration attributes are added to the nsDeclarations map (which records namespaces
     * declared for this element only), and are stacked on the stack maintated by the nsSupport
     * object.
     * @param element The element whose namespace declarations are required
     * @param cumulative If true, the namespace declarations on this element are added to the
     * current context, without creating a new context. If false, a new namespace context is
     * created.
     * @return true if any of the attributes on this element are namespace declarations (this information
     * is used to avoid repeated checking while processing the attributes as attributes).
     */

    private boolean gatherNamespaces(Element element, boolean cumulative) {

        boolean hasNamespaceDeclarations = false;
        if (!cumulative) {
            nsSupport.pushContext();
            nsDeclarations.clear();
        }

        // we can't rely on namespace declaration attributes being present -
        // there may be undeclared namespace prefixes. So we
        // declare all namespaces encountered, to be on the safe side.

        try {
            String prefix = element.getPrefix();
            String uri = element.getNamespaceURI();
            if (prefix==null) prefix="";
            if (uri==null) uri="";
            //System.err.println("Implicit Namespace: " + prefix + "=" + uri);
            if (nsDeclarations.get(prefix)==null) {
                nsSupport.declarePrefix(prefix, uri);
                nsDeclarations.put(prefix, uri);
            }
        } catch (Throwable err) {
            // it must be a level 1 DOM
        }

        NamedNodeMap atts = element.getAttributes();

        // Apparently the Oracle DOM returns null if there are no attributes:
        if (atts == null) {
            return false;
        }
        int alen = atts.getLength();
        for (int a1=0; a1<alen; a1++) {
            Attr att = (Attr)atts.item(a1);
            String attname = att.getName();
            boolean possibleNamespace = attname.startsWith("xmlns");
            if (possibleNamespace && attname.equals("xmlns")) {
                //System.err.println("Default namespace: " + att.getValue());
                hasNamespaceDeclarations = true;
                String uri = att.getValue();
                if (nsDeclarations.get("")==null || (!cumulative && !nsDeclarations.get("").equals(uri))) {
                    nsSupport.declarePrefix("", uri);
                    nsDeclarations.put("", uri);
                }
            } else if (possibleNamespace && attname.startsWith("xmlns:")) {
                //System.err.println("Namespace: " + attname.substring(6) + "=" + att.getValue());
                hasNamespaceDeclarations = true;
                String prefix = attname.substring(6);
                if (nsDeclarations.get(prefix)==null) {
                    String uri = att.getValue();
                    nsSupport.declarePrefix(prefix, uri);
                    nsDeclarations.put(prefix, uri);
                }
            } else if (attname.indexOf(':')>=0) {
                try {
                    String prefix = att.getPrefix();
                    String uri = att.getNamespaceURI();
                    //System.err.println("Implicit Namespace: " + prefix + "=" + uri);
                    if (nsDeclarations.get(prefix)==null) {
                        nsSupport.declarePrefix(prefix, uri);
                        nsDeclarations.put(prefix, uri);
                    }
                } catch (Throwable err) {
                    // it must be a level 1 DOM
                }
            }
        }
        return hasNamespaceDeclarations;
    }

    // Implement the SAX Locator interface. This is needed to pass the base URI of nodes
    // to the receiver. We don't attempt to preserve the original base URI of each individual
    // node as it is copied, only the base URI of the document as a whole.

	public int getColumnNumber() {
		return -1;
	}

	public int getLineNumber() {
		return -1;
	}

	public String getPublicId() {
		return null;
	}

	public String getSystemId() {
		return systemId;
	}

    public String getSystemId(long locationId) {
        return getSystemId();
    }

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    public int getColumnNumber(long locationId) {
        return getColumnNumber();
    }

}

