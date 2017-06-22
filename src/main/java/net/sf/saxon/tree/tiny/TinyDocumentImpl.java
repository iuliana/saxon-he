////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorOverSequence;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.*;


/**
  * A node in the XML parse tree representing the Document itself (or equivalently, the root
  * node of the Document).<P>
  */

public final class TinyDocumentImpl extends TinyParentNodeImpl
    implements DocumentInfo {

    private HashMap<String, NodeInfo> idTable;
    private IntHashMap<List<TinyElementImpl>> elementList;
    private HashMap<String, String[]> entityTable;
    private HashMap<String, Object> userData;
    private String baseURI;



    public TinyDocumentImpl(/*@NotNull*/ TinyTree tree) {
        this.tree = tree;
        nodeNr = tree.numberOfNodes;
    }

    /**
     * Get the tree containing this node
     */

    public TinyTree getTree() {
        return tree;
    }

    /**
     * Get the configuration previously set using setConfiguration
     */

    public Configuration getConfiguration() {
        return tree.getConfiguration();
    }

    /**
    * Set the system id of this node
    */

    public void setSystemId(String uri) {
        tree.setSystemId(nodeNr, uri);
    }

    /**
    * Get the system id of this root node
    */

    public String getSystemId() {
        return tree.getSystemId(nodeNr);
    }

    /**
     * Set the base URI of this document node
     * @param uri the base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
    * Get the base URI of this root node.
    */

    public String getBaseURI() {
        if (baseURI != null) {
            return baseURI;
        }
        return getSystemId();
    }

    /**
    * Get the line number of this root node.
    * @return 0 always
    */

    public int getLineNumber() {
        return 0;
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return tree.getTypeCodeArray() != null;
    }

    /**
    * Return the type of node.
    * @return Type.DOCUMENT (always)
    */

    public final int getNodeKind() {
        return Type.DOCUMENT;
    }

    /**
     * Find the parent node of this node.
     * @return The Node object describing the containing element or root node.
     */

    /*@Nullable*/ public NodeInfo getParent()  {
        return null;
    }

    /**
    * Get the root node
    * @return the NodeInfo that is the root of the tree - not necessarily a document node
    */

    /*@NotNull*/ public NodeInfo getRoot() {
        return this;
    }

    /**
    * Get the root (document) node
    * @return the DocumentInfo representing the document node, or null if the
    * root of the tree is not a document node
    */

    /*@NotNull*/ public DocumentInfo getDocumentRoot() {
        return this;
    }

    /**
    * Get a character string that uniquely identifies this node
     * @param buffer to contain an identifier based on the document number
     */

    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.append('d');
        buffer.append(Long.toString(getDocumentNumber()));
    }

    /**
     * Get the typed value.
     * @return the typed value. This will either be a single AtomicValue or a Value whose items are
     *         atomic values.
     */

    /*@NotNull*/ public AtomicSequence atomize() throws XPathException {
        return new UntypedAtomicValue(getStringValueCS());
    }

    /**
    * Get a list of all elements with a given name. This is implemented
    * as a memo function: the first time it is called for a particular
    * element type, it remembers the result for next time.
     * @param fingerprint the fingerprint identifying the required element name
     * @return an iterator over all elements with this name
     */

    /*@NotNull*/ AxisIterator getAllElements(int fingerprint) {
    	if (elementList==null) {
    	    elementList = new IntHashMap<List<TinyElementImpl>>(20);
    	}
        List<TinyElementImpl> list = elementList.get(fingerprint);
        if (list==null) {
            list = getElementList(fingerprint);
            elementList.put(fingerprint, list);
        }
        return new AxisIteratorOverSequence<TinyElementImpl>(
                new net.sf.saxon.tree.iter.ListIterator<TinyElementImpl>(list));
    }

    /**
     * Get a list containing all the elements with a given element name
     * @param fingerprint the fingerprint of the element name
     * @return list a List containing the TinyElementImpl objects
     */

    /*@NotNull*/ List<TinyElementImpl> getElementList(int fingerprint) {
        int size = tree.getNumberOfNodes()/20;
        if (size > 100) {
            size = 100;
        }
        if (size < 20) {
            size = 20;
        }
        ArrayList<TinyElementImpl> list = new ArrayList<TinyElementImpl>(size);
        int i = nodeNr+1;
        try {
            while (tree.depth[i] != 0) {
                if (tree.nodeKind[i]==Type.ELEMENT &&
                        (tree.nameCode[i] & 0xfffff) == fingerprint) {
                    list.add((TinyElementImpl)tree.getNode(i));
                }
                i++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // this shouldn't happen. If it does happen, it means the tree wasn't properly closed
            // during construction (there is no stopper node at the end). In this case, we'll recover
            return list;
        }
        list.trimToSize();
        return list;
    }

    /**
    * Register a unique element ID. Fails if there is already an element with that ID.
    * @param e The NodeInfo (always an element) having a particular unique ID value
    * @param id The unique ID value. The caller is responsible for checking that this
     * is a valid NCName.
    */

    void registerID(NodeInfo e, String id) {
        if (idTable==null) {
            idTable = new HashMap<String, NodeInfo>(256);
        }

        // the XPath spec (5.2.1) says ignore the second ID if it's not unique
        NodeInfo old = idTable.get(id);
        if (old==null) {
            idTable.put(id, e);
        }

    }

    /**
    * Get the element with a given ID.
    * @param id The unique ID of the required element, previously registered using registerID()
    * @param getParent true if the required element is the parent of the element of type ID
     * @return The NodeInfo (always an Element) for the given ID if one has been registered,
    * otherwise null.
    */

    /*@Nullable*/ public NodeInfo selectID(String id, boolean getParent) {
        if (idTable==null) {
            return null;			// no ID values found
        }
        NodeInfo node = idTable.get(id);
        if (node != null && getParent && node.isId() && node.getStringValue().equals(id)) {
            node = node.getParent();
        }
        return node;
    }

    /**
    * Set an unparsed entity URI associated with this document. For system use only, while
    * building the document.
     * @param name the name of the unparsed entity
     * @param uri the system identifier of the unparsed entity
     * @param publicId the public identifier of the unparsed entity
     */

    void setUnparsedEntity(String name, String uri, String publicId) {
        if (entityTable==null) {
            entityTable = new HashMap<String, String[]>(20);
        }
        String[] ids = new String[2];
        ids[0] = uri;
        ids[1] = publicId;
        entityTable.put(name, ids);
    }

    /**
     * Get the list of unparsed entities defined in this document
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator<String> getUnparsedEntityNames() {
        if (entityTable == null) {
            List<String> emptyList = Collections.emptyList();
            return emptyList.iterator();
        } else {
            return entityTable.keySet().iterator();
        }
    }

    /**
    * Get the unparsed entity with a given nameID if there is one, or null if not. If the entity
    * does not exist, return null.
    * @param name the name of the entity
    * @return if the entity exists, return an array of two Strings, the first holding the system ID
    * of the entity, the second holding the public
    */

    /*@Nullable*/ public String[] getUnparsedEntity(String name) {
        if (entityTable==null) {
            return null;
        }
        return entityTable.get(name);
    }

    /**
     * Get the type annotation of this node, if any.
     * @return XS_UNTYPED if no validation has been done, XS_ANY_TYPE if the document has been validated
     */

    public int getTypeAnnotation() {
        AxisIterator children = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo node = children.next();
        if (node == null || node.getTypeAnnotation() == StandardNames.XS_UNTYPED) {
            return StandardNames.XS_UNTYPED;
        } else {
            return StandardNames.XS_ANY_TYPE;
        }
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
        AxisIterator children = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo node = children.next();
        if (node == null || node.getSchemaType() == Untyped.getInstance()) {
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

        if (entityTable != null) {
            for (Map.Entry<String, String[]> entry : entityTable.entrySet()) {
                String name = entry.getKey();
                String[] details = entry.getValue();
                String systemId = details[0];
                String publicId = details[1];
                out.setUnparsedEntity(name, systemId, publicId);
            }
        }

        // output the children

        AxisIterator children = iterateAxis(AxisInfo.CHILD);
        while (true) {
            NodeInfo n = children.next();
            if (n == null) {
                break;
            }
            n.copy(out, copyOptions, locationId);
        }

        out.endDocument();
    }

    public void showSize() {
        tree.showSize();
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    public int hashCode() {
        // Chosen to give a hashcode that is likely (a) to be distinct from other documents, and (b) to
        // be distinct from other nodes in the same document
        return (int)tree.getDocumentNumber();
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

    public void setUserData(String key, /*@Nullable*/ Object value) {
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

    /*@Nullable*/ public Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
    }

}

