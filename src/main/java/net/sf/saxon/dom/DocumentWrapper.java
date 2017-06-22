////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import org.w3c.dom.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * The document node of a tree implemented as a wrapper around a DOM Document.
 *
 * <p>Because the DOM is not thread-safe even when reading, and because Saxon-EE can spawn multiple
 * threads that access the same input tree, all methods that invoke DOM methods are synchronized
 * on the DocumentWrapper object. (This still relies on the user not allocating two DocumentWrappers
 * around the same DOM).</p>
 */

public class DocumentWrapper extends DOMNodeWrapper implements DocumentInfo {

    protected Configuration config;
    protected String baseURI;
    protected long documentNumber;
    protected boolean domLevel3;
    private HashMap<String, Object> userData;

    /**
     * Wrap a DOM Document or DocumentFragment node
     * @param doc a DOM Document or DocumentFragment node
     * @param baseURI the base URI of the document
     * @param config the Saxon configuration
     */

    public DocumentWrapper(Node doc, String baseURI, Configuration config) {
        super(doc, null, null, 0);
        if (doc.getNodeType() != Node.DOCUMENT_NODE && doc.getNodeType() != Node.DOCUMENT_FRAGMENT_NODE) {
            throw new IllegalArgumentException("Node must be a DOM Document or DocumentFragment");
        }
        node = doc;
        nodeKind = Type.DOCUMENT;
        this.baseURI = baseURI;
        docWrapper = this;
        domLevel3 = config.getDOMLevel() == 3;
        if (config.getExternalObjectModel(doc.getClass()) == null) {
            throw new IllegalArgumentException(
                    "Node class " + doc.getClass().getName() + " is not recognized in this Saxon configuration");
        }
        setConfiguration(config);
    }

    /**
     * Create a wrapper for a node in this document
     *
     * @param node the DOM node to be wrapped. This must be a node within the document wrapped by this
     *             DocumentWrapper
     * @throws IllegalArgumentException if the node is not a descendant of the Document node wrapped by
     *                                  this DocumentWrapper
     * @return the wrapped node
     */

    public DOMNodeWrapper wrap(Node node) {
        if (node == this.node) {
            return this;
        }
        Document doc = node.getOwnerDocument();
        if (doc == this.node || (domLevel3 && doc != null && doc.isSameNode(this.node))) {
            return makeWrapper(node, this);
        } else {
            throw new IllegalArgumentException(
                "DocumentWrapper#wrap: supplied node does not belong to the wrapped DOM document");
        }
    }


    @Override
    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document containing the node,
     *         or null if not known. Note this is not the same as the base URI: the base URI can be
     *         modified by xml:base, but the system ID cannot.
     */
    @Override
    public String getSystemId() {
        return baseURI;
    }

    /**
     * Set the Configuration that contains this document
     * @param config the Saxon configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
        documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
    }

    /**
     * Get the configuration previously set using setConfiguration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the name pool used for the names in this document
     */

    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return false;
    }

    /**
     * Get the unique document number
     */

    public long getDocumentNumber() {
        return documentNumber;
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id the required ID value
     * @param getParent true if the parent of the element having the given ID value is required
     * @return a NodeInfo representing the element with the given ID, or null if there
     *         is no such element. This implementation does not necessarily conform to the
     *         rule that if an invalid document contains two elements with the same ID, the one
     *         that comes last should be returned.
     */

    public synchronized NodeInfo selectID(String id, boolean getParent) {
        if (node instanceof Document) {
            Node el = ((Document)node).getElementById(id);
            if (el == null) {
                return null;
            }
            return wrap(el);
        } else {
            return null;
        }
    }

    /**
     * Determine whether this is the same node as another node. <br />
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public synchronized boolean isSameNodeInfo(NodeInfo other) {
        return other instanceof DocumentWrapper && node == ((DocumentWrapper)other).node;
    }

    /**
     * Get the list of unparsed entities defined in this document
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     * @since 9.1 (implemented for this subclass since 9.2)
     */

    public synchronized Iterator<String> getUnparsedEntityNames() {
        DocumentType docType = ((Document)node).getDoctype();
        if (docType == null) {
            List<String> ls = Collections.emptyList();
            return ls.iterator();
        }
        NamedNodeMap map = docType.getEntities();
        if (map == null) {
            List<String> ls = Collections.emptyList();
            return ls.iterator();
        }
        List<String> names = new ArrayList<String>(map.getLength());
        for (int i=0; i<map.getLength(); i++) {
            Entity e = (Entity)map.item(i);
            if (e.getNotationName() != null) {
                // it is an unparsed entity
                names.add(e.getLocalName());
            }
        }
        return names.iterator();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first
     *      holding the system ID of the entity (as an absolute URI if possible),
     *      the second holding the public ID if there is one, or null if not.
     *      If the entity does not exist, the method returns null.
     *      Applications should be written on the assumption that this array may
     *      be extended in the future to provide additional information.
     * @since 8.4 (implemented for this subclass since 9.2)
     */

    public synchronized String[] getUnparsedEntity(String name) {
        DocumentType docType = ((Document)node).getDoctype();
        if (docType == null) {
            return null;
        }
        NamedNodeMap map = docType.getEntities();
        if (map == null) {
            return null;
        }
        Entity entity = (Entity)map.getNamedItem(name);
        if (entity == null || entity.getNotationName() == null) {
            // In the first case, no entity found. In the second case, it's a parsed entity.
            return null;
        }
        String systemId = entity.getSystemId();
        try {
            URI systemIdURI = new URI(systemId);
            if (!systemIdURI.isAbsolute()) {
                String base = getBaseURI();
                if (base != null) {
                    systemIdURI = new URI(base).resolve(systemIdURI);
                    systemId = systemIdURI.toString();
                } else {
                    // base URI unknown: return the relative URI as written
                }
            }
        } catch (URISyntaxException err) {
            // invalid URI: no action - return the "URI" as written
        }
        return new String[]{systemId, entity.getPublicId()};
    }

    /**
     * Get the type annotation. Always XS_UNTYPED.
     */

    public int getTypeAnnotation() {
        return StandardNames.XS_UNTYPED;
    }

    /**
     * Get the type annotation of this node, if any. The type annotation is represented as
     * SchemaType object.
     * <p/>
     * <p>Types derived from a DTD are not reflected in the result of this method.</p>
     *
     * @return For element and attribute nodes: the type annotation derived from schema
     *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
     *         validation). For comments, text nodes, processing instructions, and namespaces: null.
     *         For document nodes, either xs:untyped if the document has not been validated, or
     *         xs:anyType if it has.
     * @since 9.4
     */
    public SchemaType getSchemaType() {
        return Untyped.getInstance();
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     *              removes the existing value for the property.
     */

    public synchronized void setUserData(String key, Object value) {
        if (userData == null) {
            userData = new HashMap<String, Object>(4);
        }
        if (value == null) {
            userData.remove(key);
        } else {
            userData.put(key, value);
        }
    }

    /**
     * Get user data held in the document node. This retrieves properties previously set using
     * {@link #setUserData}
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */

    /*@Nullable*/ public synchronized Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
    }
}

