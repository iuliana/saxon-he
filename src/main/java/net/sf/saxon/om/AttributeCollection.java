////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.type.SimpleType;

/**
 * AttributeCollection represents the collection of attributes available on a particular element
 * node. It is modelled on the SAX2 Attributes interface, but is extended firstly to work with
 * Saxon NamePools, and secondly to provide type information as required by the XPath 2.0 data model.
 */

public interface AttributeCollection {

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes in the list.
     */

    int getLength();

    /**
     * Get the node name of an attribute (by position)
     * @param index The position of the attribute in the list
     * @return The node name of the attribute, or null if there is no attribute in that position
     */

    NodeName getNodeName(int index);

    /**
     * Get the namecode of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The name code of the attribute, or -1 if there is no attribute at that position.
     */

    int getNameCode(int index);


    /**
     * Get the type annotation of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The type annotation
     */

    SimpleType getTypeAnnotation(int index);

    /**
     * Get the locationID of an attribute (by position)
     * @param index The position of the attribute in the list.
     * @return The location identifier of the attribute. This can be supplied
     * to a {@link net.sf.saxon.event.LocationProvider} in order to obtain the
     * actual system identifier and line number of the relevant location
     */

    int getLocationId(int index);

    /**
     * Get the systemId part of the location of an attribute, at a given index.
     *
     * <p>Attribute location information is not available from a SAX parser, so this method
     * is not useful for getting the location of an attribute in a source document. However,
     * in a Saxon result document, the location information represents the location in the
     * stylesheet of the instruction used to generate this attribute, which is useful for
     * debugging.</p>
     * @param index the required attribute
     * @return the systemId of the location of the attribute
     */

    String getSystemId(int index);

    /**
     * Get the line number part of the location of an attribute, at a given index.
     *
     * <p>Attribute location information is not available from a SAX parser, so this method
     * is not useful for getting the location of an attribute in a source document. However,
     * in a Saxon result document, the location information represents the location in the
     * stylesheet of the instruction used to generate this attribute, which is useful for
     * debugging.</p>
     * @param index the required attribute
     * @return the line number of the location of the attribute
     */

    int getLineNumber(int index);

    /**
     * Get the properties of an attribute (by position)
     * @param index The position of the attribute in the list.
     * @return The properties of the attribute. This is a set
     * of bit-settings defined in class {@link net.sf.saxon.event.ReceiverOptions}. The
     * most interesting of these is {{@link net.sf.saxon.event.ReceiverOptions#DEFAULTED_ATTRIBUTE},
     * which indicates an attribute that was added to an element as a result of schema validation.
     */

    int getProperties(int index);

    /**
     * Get the prefix of the name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The prefix of the attribute name as a string, or null if there
     *         is no attribute at that position. Returns "" for an attribute that
     *         has no prefix.
     */

    String getPrefix(int index);

    /**
     * Get the lexical QName of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The lexical QName of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    String getQName(int index);

    /**
     * Get the local name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    String getLocalName(int index);

    /**
     * Get the namespace URI of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The namespace URI of the attribute as a string
     * (empty string for an attribute in no namespace), or null if there
     * is no attribute at that position.
     */

    String getURI(int index);

    /**
     * Get the index of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    int getIndex(String uri, String localname);

    /**
     * Get the index, given the fingerprint
     */

    int getIndexByFingerprint(int fingerprint);

    /**
     * Get the attribute value using its fingerprint
     */

    String getValueByFingerprint(int fingerprint);

    /**
     * Get the value of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The value of the attribute
     */

    public String getValue(String uri, String localname);

    /**
     * Get the value of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The attribute value as a string, or null if
     *         there is no attribute at that position.
     */

    String getValue(int index);

    /**
     * Determine whether a given attribute has the is-ID property set
     */

    boolean isId(int index);

    /**
     * Determine whether a given attribute has the is-idref property set
     */

    boolean isIdref(int index);
}

