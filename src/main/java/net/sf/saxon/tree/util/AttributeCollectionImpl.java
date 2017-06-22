////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.om.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SimpleType;
import org.xml.sax.Attributes;

import java.util.Arrays;


/**
 * AttributeCollectionImpl is an implementation of both the SAX2 interface Attributes
 * and the Saxon equivalent AttributeCollection.
 * <p/>
 * <p>As well as providing the information required by the SAX2 interface, an
 * AttributeCollection can hold type information (as needed to support the JAXP 1.3
 * {@link javax.xml.validation.ValidatorHandler} interface), and location information
 * for debugging. The location information is used in the case of attributes on a result
 * tree to identify the location in the query or stylesheet from which they were
 * generated.
 */

public class AttributeCollectionImpl implements Attributes, AttributeCollection {

    private Configuration config;
    private LocationProvider locationProvider;
    // Following fields can be null ONLY if used==0. We avoid allocating the arrays for the common
    // case of an empty attribute collection.
    /*@Nullable*/ private NodeName[] names = null;
    /*@Nullable*/ private String[] values = null;
    /*@Nullable*/ private long[] codes = null;
    // locationId in top half, properties in bottom half
    private int used = 0;
    // the types array can be null even if used>0; this indicates that all attributes are untyped
    /*@Nullable*/ private SimpleType[] types = null;

    // Empty attribute collection. The caller is trusted not to try and modify it.

    /*@NotNull*/ public static AttributeCollectionImpl EMPTY_ATTRIBUTE_COLLECTION =
            new AttributeCollectionImpl((Configuration) null);

    /**
     * Create an empty attribute list.
     *
     * @param config the Saxon Configuration
     */

    public AttributeCollectionImpl(Configuration config) {
        this.config = config;
        used = 0;
    }

    /**
     * Create an attribute list as a copy of an existing attribute list
     *
     * @param atts the existing attribute list to be copied
     * @return the copied attribute list. Note that if the original attribute list
     *         is empty, the method returns the singleton object {@link #EMPTY_ATTRIBUTE_COLLECTION};
     *         this case must therefore be handled specially if the returned attribute list is to
     *         be modified.
     */

    /*@Nullable*/ public static AttributeCollectionImpl copy(/*@NotNull*/ AttributeCollectionImpl atts) {
        if (atts.getLength() == 0) {
            return EMPTY_ATTRIBUTE_COLLECTION;
        }
        AttributeCollectionImpl t = new AttributeCollectionImpl(atts.config);
        t.used = atts.used;
        t.locationProvider = atts.locationProvider;
        t.names = new NodeName[atts.used];
        t.values = new String[atts.used];
        t.codes = new long[atts.used];
        System.arraycopy(atts.names, 0, t.names, 0, atts.used);
        System.arraycopy(atts.values, 0, t.values, 0, atts.used);
        System.arraycopy(atts.codes, 0, t.codes, 0, atts.used);
        if (atts.types != null) {
            t.types = new SimpleType[atts.used];
            System.arraycopy(atts.types, 0, t.types, 0, atts.used);
        }
        return t;
    }

    /**
     * Set the location provider. This must be set if the methods getSystemId() and getLineNumber()
     * are to be used to get location information for an attribute.
     *
     * @param provider the location provider
     */

    public void setLocationProvider(LocationProvider provider) {
        locationProvider = provider;
    }

    /**
     * Add an attribute to an attribute list. The parameters correspond
     * to the parameters of the {@link net.sf.saxon.event.Receiver#attribute(net.sf.saxon.om.NodeName, net.sf.saxon.type.SimpleType, CharSequence, int, int)}
     * method. There is no check that the name of the attribute is distinct from other attributes
     * already in the collection: this check must be made by the caller.
     *
     * @param nodeName   Object representing the attribute name.
     * @param type       The attribute type
     * @param value      The attribute value (must not be null)
     * @param locationId Identifies the attribute location.
     * @param properties Attribute properties
     */

    public void addAttribute(NodeName nodeName, SimpleType type, String value, int locationId, int properties) {
        if (values == null) {
            names = new NodeName[5];
            values = new String[5];
            codes = new long[5];
            if (!type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                types = new SimpleType[5];
            }
            used = 0;
        }
        if (values.length == used) {
            int newsize = (used == 0 ? 5 : used * 2);
            NodeName[] n2 = new NodeName[newsize];
            String[] v2 = new String[newsize];
            long[] c2 = new long[newsize];
            System.arraycopy(names, 0, n2, 0, used);
            System.arraycopy(values, 0, v2, 0, used);
            System.arraycopy(codes, 0, c2, 0, used);
            names = n2;
            values = v2;
            codes = c2;
            if (types != null) {
                SimpleType[] t2 = new SimpleType[newsize];
                System.arraycopy(types, 0, t2, 0, used);
                types = t2;
            }
        }
        int n = used;
        names[n] = nodeName;
        codes[n] = ((long)locationId << 32) | (long)properties;
        setTypeAnnotation(n, type);
        values[used++] = value;
    }

    /**
     * Set (overwrite) an attribute in the attribute list. The parameters correspond
     * to the parameters of the {@link net.sf.saxon.event.Receiver#attribute(net.sf.saxon.om.NodeName, net.sf.saxon.type.SimpleType, CharSequence, int, int)}
     * method.
     *
     * @param index      Identifies the entry to be replaced. Must be in range (nasty things happen if not)
     * @param nodeName   representing the attribute name.
     * @param type       The attribute type code
     * @param value      The attribute value (must not be null)
     * @param locationId Identifies the attribtue location.
     * @param properties Attribute properties
     */

    public void setAttribute(int index, NodeName nodeName, SimpleType type, String value, int locationId, int properties) {
        names[index] = nodeName;
        codes[index] = ((long)locationId << 32) | (long)properties;
        setTypeAnnotation(index, type);
        values[index] = value;
    }


    /**
     * Clear the attribute list. This removes the values but doesn't free the memory used.
     * free the memory, use clear() then compact().
     */

    public void clear() {
        used = 0;
    }

    /**
     * Compact the attribute list to avoid wasting memory
     */

    public void compact() {
        if (used == 0) {
            codes = null;
            values = null;
        } else if (values != null && values.length > used) {
            NodeName[] n2 = new NodeName[used];
            String[] v2 = new String[used];
            long[] c2 = new long[used];
            System.arraycopy(names, 0, n2, 0, used);
            System.arraycopy(values, 0, v2, 0, used);
            System.arraycopy(codes, 0, c2, 0, used);
            values = v2;
            codes = c2;
            names = n2;
            if (types != null) {
                SimpleType[] t2 = new SimpleType[used];
                System.arraycopy(types, 0, t2, 0, used);
                types = t2;
            }
        }
    }

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes that have been created in this attribute collection. This is the number
     *         of slots used in the list, including any slots allocated to attributes that have since been deleted.
     *         Such slots are not reused, to preserve attribute identity.
     */

    public int getLength() {
        return (values == null ? 0 : used);
    }

    /**
     * Get the namecode of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The display name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    public int getNameCode(int index) {
        if (names == null) {
            return -1;
        }
        if (index < 0 || index >= used) {
            return -1;
        }

        return names[index].allocateNameCode(config.getNamePool());
    }

    /**
     * Get the node name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The node name, as a NodeName object, or null if there is no name at this index
     */

    /*@Nullable*/ public NodeName getNodeName(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }

        return names[index];
    }


    /**
     * Get the type of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The type annotation
     */

    public SimpleType getTypeAnnotation(int index) {
        if (types == null) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }
        if (index < 0 || index >= used) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }

        return types[index];
    }

    /**
     * Get the locationID of an attribute (by position)
     *
     * @param index The position of the attribute in the list.
     * @return The location identifier of the attribute. This can be supplied
     *         to a {@link net.sf.saxon.event.LocationProvider} in order to obtain the
     *         actual system identifier and line number of the relevant location
     */

    public int getLocationId(int index) {
        if (codes == null) {
            return -1;
        }
        if (index < 0 || index >= used) {
            return -1;
        }

        return (int)(codes[index]>>32);
    }

    /**
     * Get the systemId part of the location of an attribute, at a given index.
     * <p/>
     * <p>Attribute location information is not available from a SAX parser, so this method
     * is not useful for getting the location of an attribute in a source document. However,
     * in a Saxon result document, the location information represents the location in the
     * stylesheet of the instruction used to generate this attribute, which is useful for
     * debugging.</p>
     *
     * @param index the required attribute
     * @return the systemId of the location of the attribute
     */

    public String getSystemId(int index) {
        return locationProvider.getSystemId(getLocationId(index));
    }

    /**
     * Get the line number part of the location of an attribute, at a given index.
     * <p/>
     * <p>Attribute location information is not available from a SAX parser, so this method
     * is not useful for getting the location of an attribute in a source document. However,
     * in a Saxon result document, the location information represents the location in the
     * stylesheet of the instruction used to generate this attribute, which is useful for
     * debugging.</p>
     *
     * @param index the required attribute
     * @return the line number of the location of the attribute
     */

    public int getLineNumber(int index) {
        return locationProvider.getLineNumber(getLocationId(index));
    }

    /**
     * Get the properties of an attribute (by position)
     *
     * @param index The position of the attribute in the list.
     * @return The properties of the attribute. This is a set
     *         of bit-settings defined in class {@link net.sf.saxon.event.ReceiverOptions}. The
     *         most interesting of these is {{@link net.sf.saxon.event.ReceiverOptions#DEFAULTED_ATTRIBUTE},
     *         which indicates an attribute that was added to an element as a result of schema validation.
     */

    public int getProperties(int index) {
        if (codes == null) {
            return -1;
        }
        if (index < 0 || index >= used) {
            return -1;
        }

        return (int)codes[index];
    }

    /**
     * Get the prefix of the name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The prefix of the attribute name as a string, or null if there
     *         is no attribute at that position. Returns "" for an attribute that
     *         has no prefix.
     */

    /*@Nullable*/ public String getPrefix(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getPrefix();
    }

    /**
     * Get the lexical QName of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The lexical QName of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/ public String getQName(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getDisplayName();
    }

    /**
     * Get the local name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/ public String getLocalName(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getLocalPart();
    }

    /**
     * Get the namespace URI of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/ public String getURI(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getURI();
    }


    /**
     * Get the type of an attribute (by position). This is a SAX2 method,
     * so it gets the type name as a DTD attribute type, mapped from the
     * schema type code.
     *
     * @param index The position of the attribute in the list.
     * @return The attribute type as a string ("NMTOKEN" for an
     *         enumeration, and "CDATA" if no declaration was
     *         read), or null if there is no attribute at
     *         that position.
     */

    /*@NotNull*/ public String getType(int index) {
        int typeCode = getTypeAnnotation(index).getFingerprint();
        switch (typeCode) {
            case StandardNames.XS_ID:
                return "ID";
            case StandardNames.XS_IDREF:
                return "IDREF";
            case StandardNames.XS_NMTOKEN:
                return "NMTOKEN";
            case StandardNames.XS_ENTITY:
                return "ENTITY";
            case StandardNames.XS_IDREFS:
                return "IDREFS";
            case StandardNames.XS_NMTOKENS:
                return "NMTOKENS";
            case StandardNames.XS_ENTITIES:
                return "ENTITIES";
            default:
                return "CDATA";
        }
    }

    /**
     * Get the type of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    /*@Nullable*/ public String getType(String uri, String localname) {
        int index = findByName(uri, localname);
        return (index < 0 ? null : getType(index));
    }

    /**
     * Get the value of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The attribute value as a string, or null if
     *         there is no attribute at that position.
     */

    /*@Nullable*/ public String getValue(int index) {
        if (values == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return values[index];
    }

    /**
     * Get the value of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    /*@Nullable*/ public String getValue(String uri, String localname) {
        int index = findByName(uri, localname);
        return (index < 0 ? null : getValue(index));
    }

    /**
     * Get the attribute value using its fingerprint
     */

    /*@Nullable*/ public String getValueByFingerprint(int fingerprint) {
        int index = findByFingerprint(fingerprint);
        return (index < 0 ? null : getValue(index));
    }

    /**
     * Get the index of an attribute, from its lexical QName
     *
     * @param qname The lexical QName of the attribute. The prefix must match.
     * @return The index position of the attribute
     */

    public int getIndex(/*@NotNull*/ String qname) {
        if (names == null) {
            return -1;
        }
        if (qname.indexOf(':') < 0) {
            return findByName("", qname);
        }
        // Searching using prefix+localname is not recommended, but SAX allows it...
        String[] parts;
        try {
            parts = Name11Checker.getInstance().getQNameParts(qname);
        } catch (QNameException err) {
            return -1;
        }
        String prefix = parts[0];
        if (prefix.length() == 0) {
            return findByName("", qname);
        } else {
            String localName = parts[1];
            for (int i = 0; i < used; i++) {
                if (names[i] != null) {
                    String lname = names[i].getLocalPart();
                    String ppref = names[i].getPrefix();
                    if (localName.equals(lname) && prefix.equals(ppref)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    /**
     * Get the index of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    public int getIndex(String uri, String localname) {
        return findByName(uri, localname);
    }

    /**
     * Get the index, given the fingerprint.
     * Return -1 if not found.
     */

    public int getIndexByFingerprint(int fingerprint) {
        return findByFingerprint(fingerprint);
    }

    /**
     * Get the type of an attribute (by lexical QName).
     *
     * @param name The lexical QName of the attribute.
     * @return The attribute type as a string (e.g. "NMTOKEN", or
     *         "CDATA" if no declaration was read).
     */

    /*@NotNull*/ public String getType(/*@NotNull*/ String name) {
        int index = getIndex(name);
        return getType(index);
    }


    /**
     * Get the value of an attribute (by lexnical QName).
     *
     * @param name The attribute name (a lexical QName).
     *             The prefix must match the prefix originally used. This method is defined in SAX, but is
     *             not recommended except where the prefix is null.
     */

    /*@Nullable*/ public String getValue(/*@NotNull*/ String name) {
        int index = getIndex(name);
        return getValue(index);
    }

    /**
     * Ask whether the attribute collection contains any attributes
     * in a specified namespace
     * @param uri the specified namespace
     * @return true if there are one or more attributes in this namespace
     */

    public boolean hasAttributeInNamespace(String uri) {
        if (names == null || config == null) {
            return false;        // indicates an empty attribute set
        }
        for (int i = 0; i < used; i++) {
            if (names[i] != null && names[i].isInNamespace(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an attribute by expanded name
     *
     * @param uri       the namespace uri
     * @param localName the local name
     * @return the index of the attribute, or -1 if absent
     */

    private int findByName(String uri, String localName) {
        if (names == null || config == null) {
            return -1;        // indicates an empty attribute set
        }
        for (int i = 0; i < used; i++) {
            if (names[i] != null && names[i].isInNamespace(uri) && localName.equals(names[i].getLocalPart())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find an attribute by fingerprint
     *
     * @param fingerprint the fingerprint representing the name of the required attribute
     * @return the index of the attribute, or -1 if absent
     */

    private int findByFingerprint(int fingerprint) {
        if (names == null || config == null) {
            return -1;
        }
        NamePool pool = config.getNamePool();
        for (int i = 0; i < used; i++) {
            NodeName nn = names[i];
            if (nn != null) {
                if (nn.hasFingerprint()) {
                    if (nn.getFingerprint() == fingerprint) {
                        return i;
                    }
                } else {
                    if (nn.isInNamespace(pool.getURI(fingerprint)) && nn.getLocalPart().equals(pool.getLocalName(fingerprint))) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Find an attribute by node name
     *
     * @param nodeName the name of the required attribute
     * @return the index of the attribute, or -1 if absent
     */

    public int findByNodeName(/*@NotNull*/ NodeName nodeName) {
        if (codes == null || config == null) {
            return -1;
        }
        if (nodeName.hasFingerprint()) {
            return findByFingerprint(nodeName.getFingerprint());
        } else {
            return findByName(nodeName.getURI(), nodeName.getLocalPart());
        }
    }


    /**
     * Determine whether a given attribute has the is-ID property set
     */

    public boolean isId(int index) {
        return
                (!isDeleted(index)) &&
                (StandardNames.XML_ID_NAME.equals(names[index]) ||
                   (getProperties(index)& ReceiverOptions.IS_ID) != 0 ||
                   config.getTypeHierarchy().isIdCode(getTypeAnnotation(index).getFingerprint()));
    }

    /**
     * Determine whether a given attribute has the is-idref property set
     */

    public boolean isIdref(int index) {
        return config.getTypeHierarchy().isIdrefsCode(getTypeAnnotation(index).getFingerprint());
    }

    /**
     * Delete the attribute at a given index position. Note that the index position will not be reused, to ensure
     * that any new attributes added to the element have a distinct identity. Instead, the slot occupied
     * by the attribute is nilled out.
     *
     * @param index The index position of the attribute to be removed
     */

    public void removeAttribute(int index) {
        names[index] = null;
        codes[index] = -1L;
        values[index] = null;
        if (types != null) {
            types[index] = null;
        }
    }

    /**
     * Test whether the attribute at a given index has been deleted
     *
     * @param index the index position of the (ex-) attribute
     * @return true if the attribute has been deleted
     */

    public boolean isDeleted(int index) {
        return names[index] == null;
    }

    /**
     * Rename an attribute
     *
     * @param index   the index position of the attribute
     * @param newName the namecode of the new name
     */

    public void renameAttribute(int index, NodeName newName) {
        names[index] = newName;
        if (types != null) {
            types[index] = BuiltInAtomicType.UNTYPED_ATOMIC;
        }
    }

    /**
     * Replace the value of an attribute
     *
     * @param index    position of the attribute
     * @param newValue the new string value of the attribute
     */

    public void replaceAttribute(int index, /*@NotNull*/ CharSequence newValue) {
        values[index] = newValue.toString();
    }

    /**
     * Set the type annotation of an attribute
     *
     * @param index    the index position of the attribute node
     * @param type     the new type for the attribute
     */

    public void setTypeAnnotation(int index, SimpleType type) {
        if (type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            if (types != null) {
                types[index] = type;
            }
        } else {
            if (types == null) {
                types = new SimpleType[names.length];
                Arrays.fill(types, BuiltInAtomicType.UNTYPED_ATOMIC);
                types[index] = type;
            } else {
                types[index] = type;
            }
        }
    }

    /**
     * Swap two attributes (used for sorting)
     * @param i the position of one value
     * @param j the position of the other value
     */

    public void swap(int i, int j) {
        NodeName n = names[i];
        names[i] = names[j];
        names[j] = n;
        if (types != null) {
            SimpleType s = types[i];
            types[i] = types[j];
            types[j] = s;
        }
        String c = values[i];
        values[i] = values[j];
        values[j] = c;
        long p = codes[i];
        codes[i] = codes[j];
        codes[j] = p;
    }


}

