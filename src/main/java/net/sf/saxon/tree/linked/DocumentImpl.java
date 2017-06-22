////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorOverSequence;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntHashMap;

import java.util.*;

/**
 * A node in the XML parse tree representing the Document itself (or equivalently, the root
 * node of the Document).
 * <p/>
 * <p>A DocumentImpl object may either represent a real document node, or it may represent an imaginary
 * container for a parentless element.</p>
 *
 * @author Michael H. Kay
 */

public final class DocumentImpl extends ParentNodeImpl implements DocumentInfo, MutableDocumentInfo {

    //private static int nextDocumentNumber = 0;

    private ElementImpl documentElement;


   /*@Nullable*/ private HashMap<String, NodeInfo> idTable;
    private long documentNumber;
    private String baseURI;
    private HashMap<String, String[]> entityTable;

    /*@Nullable*/ private IntHashMap<List<NodeImpl>> elementList;
    private HashMap<String, Object> userData;
    private Configuration config;
    private LineNumberMap lineNumberMap;
    private SystemIdMap systemIdMap = new SystemIdMap();
    private boolean imaginary;

    /**
     * Create a DocumentImpl
     */

    public DocumentImpl() {
        setRawParent(null);
    }

    /**
     * Set the Configuration that contains this document
     *
     * @param config the Saxon configuration
     */

    public void setConfiguration(/*@NotNull*/ Configuration config) {
        this.config = config;
        documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
    }

    /**
     * Get the configuration previously set using setConfiguration
     *
     * @return the Saxon configuration
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
     * Get a Builder suitable for building nodes that can be attached to this document.
     *
     * @return a new TreeBuilder
     */

    /*@NotNull*/
    public Builder newBuilder() {
        LinkedTreeBuilder builder = new LinkedTreeBuilder(config.makePipelineConfiguration());
        builder.setAllocateSequenceNumbers(false);
        return builder;
    }

    /**
     * Set whether this is an imaginary document node
     *
     * @param imaginary if true, this is an imaginary node - the tree is really rooted at the topmost element
     */

    public void setImaginary(boolean imaginary) {
        this.imaginary = imaginary;
    }

    /**
     * Ask whether this is an imaginary document node
     *
     * @return true if this is an imaginary node - the tree is really rooted at the topmost element
     */

    public boolean isImaginary() {
        return imaginary;
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return documentElement != null && documentElement.getTypeAnnotation() != StandardNames.XS_UNTYPED;
    }

    /**
     * Get the unique document number
     */

    public long getDocumentNumber() {
        return documentNumber;
    }

    /**
     * Set the top-level element of the document (variously called the root element or the
     * document element). Note that a DocumentImpl may represent the root of a result tree
     * fragment, in which case there is no document element.
     *
     * @param e the top-level element
     */

    void setDocumentElement(ElementImpl e) {
        documentElement = e;
    }

    /**
     * Copy the system ID and line number map from another document
     * (used when grafting a simplified stylesheet)
     *
     * @param original the document whose system ID and line number maps are to be grafted
     *                 onto this tree
     */

    public void graftLocationMap(/*@NotNull*/ DocumentImpl original) {
        systemIdMap = original.systemIdMap;
        lineNumberMap = original.lineNumberMap;
    }

    /**
     * Set the system id (base URI) of this node
     */

    public void setSystemId( String /*@Nullable*/uri) {
        if (uri == null) {
            uri = "";
        }
        systemIdMap.setSystemId(getRawSequenceNumber(), uri);
    }

    /**
     * Get the system id of this root node
     */

    public String getSystemId() {
        return systemIdMap.getSystemId(getRawSequenceNumber());
    }

    /**
     * Set the base URI of this document node
     *
     * @param uri the new base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
     * Get the base URI of this root node.
     *
     * @return the base URI
     */

    public String getBaseURI() {
        if (baseURI != null) {
            return baseURI;
        }
        return getSystemId();
    }


    /**
     * Set the system id of an element in the document
     *
     * @param seq the sequence number of the element
     * @param uri the system identifier (base URI) of the element
     */

    void setSystemId(int seq,  String /*@Nullable*/uri) {
        if (uri == null) {
            uri = "";
        }
        systemIdMap.setSystemId(seq, uri);
    }


    /**
     * Get the system id of an element in the document
     *
     * @param seq the sequence number of the element
     * @return the systemId (base URI) of the element
     */

    String getSystemId(int seq) {
        return systemIdMap.getSystemId(seq);
    }


    /**
     * Set line numbering on
     */

    public void setLineNumbering() {
        lineNumberMap = new LineNumberMap();
        lineNumberMap.setLineAndColumn(getRawSequenceNumber(), 0, -1);
    }

    /**
     * Set the line number for an element. Ignored if line numbering is off.
     *
     * @param sequence the sequence number of the element
     * @param line     the line number of the element
     * @param column   the column number of the element
     */

    void setLineAndColumn(int sequence, int line, int column) {
        if (lineNumberMap != null && sequence >= 0) {
            lineNumberMap.setLineAndColumn(sequence, line, column);
        }
    }

    /**
     * Get the line number for an element.
     *
     * @param sequence the sequence number of the element
     * @return the line number for an element. Return -1 if line numbering is off, or if
     *         the element was added subsequent to document creation by use of XQuery update
     */

    int getLineNumber(int sequence) {
        if (lineNumberMap != null && sequence >= 0) {
            return lineNumberMap.getLineNumber(sequence);
        }
        return -1;
    }

    /**
     * Get the column number for an element.
     *
     * @param sequence the sequence number of the element
     * @return the column number for an element. Return -1 if line numbering is off, or if
     *         the element was added subsequent to document creation by use of XQuery update
     */

    int getColumnNumber(int sequence) {
        if (lineNumberMap != null && sequence >= 0) {
            return lineNumberMap.getColumnNumber(sequence);
        }
        return -1;
    }


    /**
     * Get the line number of this root node.
     *
     * @return 0 always
     */

    public int getLineNumber() {
        return 0;
    }

    /**
     * Return the type of node.
     *
     * @return Type.DOCUMENT (always)
     */

    public final int getNodeKind() {
        return Type.DOCUMENT;
    }

    /**
     * Get next sibling - always null
     *
     * @return null
     */


    public /*@Nullable*/final NodeImpl getNextSibling() {
        return null;
    }

    /**
     * Get previous sibling - always null
     *
     * @return null
     */

    /*@Nullable*/
    public final NodeImpl getPreviousSibling() {
        return null;
    }

    /**
     * Get the root (outermost) element.
     *
     * @return the Element node for the outermost element of the document.
     */

    public ElementImpl getDocumentElement() {
        return documentElement;
    }

    /**
     * Get the root node
     *
     * @return the NodeInfo representing the root of this tree
     */

    /*@NotNull*/
    public NodeInfo getRoot() {
        return this;
    }

    /**
     * Get the root (document) node
     *
     * @return the DocumentInfo representing this document
     */

    /*@NotNull*/
    public DocumentInfo getDocumentRoot() {
        return this;
    }

    /**
     * Get the physical root of the tree. This may be an imaginary document node: this method
     * should be used only when control information held at the physical root is required
     *
     * @return the document node, which may be imaginary
     */

    /*@NotNull*/
    public DocumentImpl getPhysicalRoot() {
        return this;
    }

    /**
     * Get a character string that uniquely identifies this node
     *
     * @param buffer a buffer into which will be placed a string based on the document number
     */

    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.append('d');
        buffer.append(Long.toString(documentNumber));
    }

    /**
     * Get a list of all elements with a given name fingerprint
     *
     * @param fingerprint the fingerprint of the required element name
     * @return an iterator over all the elements with this name
     */

    /*@NotNull*/
    AxisIterator getAllElements(int fingerprint) {
        if (elementList == null) {
            elementList = new IntHashMap<List<NodeImpl>>(500);
        }
        IntHashMap<List<NodeImpl>> eList = elementList;
        List<NodeImpl> list = eList.get(fingerprint);
        if (list == null) {
            list = new ArrayList<NodeImpl>(500);
            NodeImpl next = getNextInDocument(this);
            while (next != null) {
                if (next.getNodeKind() == Type.ELEMENT &&
                        next.getFingerprint() == fingerprint) {
                    list.add(next);
                }
                next = next.getNextInDocument(this);
            }
            eList.put(fingerprint, list);
        }
        return new AxisIteratorOverSequence<NodeImpl>(
                new net.sf.saxon.tree.iter.ListIterator<NodeImpl>(list));
    }

    /**
     * Remove a node from any indexes when it is detached from the tree
     *
     * @param node the node to be removed from all indexes
     */

    public void deIndex(/*@NotNull*/ NodeImpl node) {
        if (node instanceof ElementImpl) {
            IntHashMap<List<NodeImpl>> eList = elementList;
            if (eList != null) {
                List<NodeImpl> list = eList.get(node.getFingerprint());
                if (list == null) {
                    return;
                }
                list.remove(node);
            }
            if (node.isId()) {
                deregisterID(node.getStringValue());
            }
        } else if (node instanceof AttributeImpl) {
            if (node.isId()) {
                deregisterID(node.getStringValue());
            }
        }
    }

    /**
     * Index all the ID attributes. This is done the first time the id() function
     * is used on this document, or the first time that id() is called after a sequence of updates
     */

    private void indexIDs() {
        if (idTable != null) {
            return;      // ID's are already indexed
        }
        idTable = new HashMap<String, NodeInfo>(256);
        NameChecker checker = getConfiguration().getNameChecker();

        NodeImpl curr = this;
        NodeImpl root = curr;
        while (curr != null) {
            if (curr.getNodeKind() == Type.ELEMENT) {
                //noinspection ConstantConditions
                ElementImpl e = (ElementImpl) curr;
                if (e.isId()) {
                    registerID(e, Whitespace.trim(e.getStringValueCS()));
                }
                AttributeCollectionImpl atts = (AttributeCollectionImpl) e.getAttributeList();
                for (int i = 0; i < atts.getLength(); i++) {
                    if (!atts.isDeleted(i) && atts.isId(i) && checker.isValidNCName(Whitespace.trim(atts.getValue(i)))) {
                        // don't index any invalid IDs - these can arise when using a non-validating parser
                        registerID(e, Whitespace.trim(atts.getValue(i)));
                    }
                }
            }
            curr = curr.getNextInDocument(root);
        }
    }

    /**
     * Register a unique element ID. Does nothing if there is already an element with that ID.
     *
     * @param e  The Element having a particular unique ID value
     * @param id The unique ID value
     */

    protected void registerID(NodeInfo e, String id) {
        // the XPath spec (5.2.1) says ignore the second ID if it's not unique
        if (idTable == null) {
            idTable = new HashMap<String, NodeInfo>(256);
        }
        HashMap<String, NodeInfo> table = idTable;
        Object old = table.get(id);
        if (old == null) {
            table.put(id, e);
        }
    }

    /**
     * Get the element with a given ID.
     *
     * @param id        The unique ID of the required element, previously registered using registerID()
     * @param getParent true if the requirement is for the parent of the node with the given ID,
     *                  not the node itself.
     * @return The NodeInfo for the given ID if one has been registered, otherwise null.
     */


    public NodeInfo/*@Nullable*/ selectID(String id, boolean getParent) {
        if (idTable == null) {
            indexIDs();
        }
        assert idTable != null;
        NodeInfo node = idTable.get(id);
        if (node != null && getParent && node.isId() && node.getStringValue().equals(id)) {
            node = node.getParent();
        }
        return node;
    }

    /**
     * Remove the entry for a given ID (when nodes are deleted). Does nothing if the id value is not
     * present in the index.
     *
     * @param id The id value
     */

    protected void deregisterID(String id) {
        id = Whitespace.trim(id);
        if (idTable != null) {
            idTable.remove(id);
        }
    }

    /**
     * Set an unparsed entity URI associated with this document. For system use only, while
     * building the document.
     *
     * @param name     the entity name
     * @param uri      the system identifier of the unparsed entity
     * @param publicId the public identifier of the unparsed entity
     */

    void setUnparsedEntity(String name, String uri, String publicId) {
        // System.err.println("setUnparsedEntity( " + name + "," + uri + ")");
        if (entityTable == null) {
            entityTable = new HashMap<String, String[]>(10);
        }
        String[] ids = new String[2];
        ids[0] = uri;
        ids[1] = publicId;
        entityTable.put(name, ids);
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator<String> getUnparsedEntityNames() {
        if (entityTable == null) {
            List<String> ls = Collections.emptyList();
            return ls.iterator();
        } else {
            return entityTable.keySet().iterator();
        }
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first holding the system ID
     *         of the entity, the second holding the public ID if there is one, or null if not. If the entity
     *         does not exist, return null.
     */

    /*@Nullable*/
    public String[] getUnparsedEntity(String name) {
        if (entityTable == null) {
            return null;
        }
        return entityTable.get(name);
    }

    /**
     * Get the type annotation of this node, if any. By convention for a document node this is
     * XS_ANY_TYPE if the document is validated, or XS_UNTYPED otherwise
     *
     * @return the type annotation, as the integer name code of the type name
     */

    public int getTypeAnnotation() {
        return getSchemaType().getFingerprint();
    }

    /**
     * Get the type annotation
     *
     * @return the type annotation of the base node
     */

    public SchemaType getSchemaType() {
        if (documentElement == null || documentElement.getTypeAnnotation() == StandardNames.XS_UNTYPED) {
            return Untyped.getInstance();
        } else {
            return AnyType.getInstance();
        }
    }

    /**
     * Copy this node to a given outputter
     */

    public void copy(/*@NotNull*/ Receiver out, int copyOptions, int locationId) throws XPathException {
        out.startDocument(CopyOptions.getStartDocumentProperties(copyOptions));

        // copy any unparsed entities

        for (Iterator<String> names = getUnparsedEntityNames(); names.hasNext(); ) {
            String name = names.next();
            String[] details = getUnparsedEntity(name);
            assert details != null;
            out.setUnparsedEntity(name, details[0], details[1]);
        }

        // copy the children

        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            next.copy(out, copyOptions, locationId);
            next = (NodeImpl) next.getNextSibling();
        }

        out.endDocument();
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    public void replaceStringValue(CharSequence stringValue) {
        throw new UnsupportedOperationException("Cannot replace the value of a document node");
    }

    /**
     * This method is called before performing a batch of updates; it allows all cached data that
     * might be invalidated by such updates to be cleared
     */

    public void resetIndexes() {
        idTable = null;
        elementList = null;
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     *
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     *              removes the existing value for the property.
     */

    public void setUserData(String key,  Object value) {
       /*@Nullable*/ if (userData == null) {
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
     *
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */

     /*@Nullable*/
    public Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
    }
}

