////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SimpleType;

/**
 * An implementation of the AttributeCollection interface based directly on the
 * TinyTree data structure.
 */

public class TinyAttributeCollection implements AttributeCollection {

    int element;
    TinyTree tree;
    int firstAttribute;

    public TinyAttributeCollection(/*@NotNull*/ TinyTree tree, int element) {
        this.tree = tree;
        this.element = element;
        firstAttribute = tree.alpha[element];
    }

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes in the list.
     */

    public int getLength() {
        int i = firstAttribute;
        while (i < tree.numberOfAttributes && tree.attParent[i] == element) {
            i++;
        }
        return i - firstAttribute;
    }

    /**
     * Get the namecode of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The display name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    public int getNameCode(int index) {
        return tree.attCode[firstAttribute + index];
    }

    /**
     * Get the node name of an attribute (by position)
     *
     * @param index The position of the attribute in the list
     * @return The node name of the attribute, or null if there is no attribute in that position
     */
    /*@NotNull*/ public NodeName getNodeName(int index) {
        return new CodedName(tree.attCode[firstAttribute + index], tree.getNamePool());
    }

    /**
     * Get the type annotation of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The type annotation of the attribute
     */

    public SimpleType getTypeAnnotation(int index) {
        if (tree.attTypeCode == null) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }
        return (SimpleType)tree.getConfiguration().getSchemaType(tree.getAttributeAnnotation(firstAttribute + index));
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
        return 0;
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
        return tree.getSystemId(element);
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
        return -1;
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
        return 0;
    }

    /**
     * Get the prefix of the name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The prefix of the attribute name as a string, or null if there
     *         is no attribute at that position. Returns "" for an attribute that
     *         has no prefix.
     */

    public String getPrefix(int index) {
        return tree.getNamePool().getPrefix(tree.attCode[firstAttribute + index]);
    }

    /**
     * Get the lexical QName of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The lexical QName of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    public String getQName(int index) {
        return tree.getNamePool().getDisplayName(tree.attCode[firstAttribute + index]);
    }

    /**
     * Get the local name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    public String getLocalName(int index) {
        return tree.getNamePool().getLocalName(tree.attCode[firstAttribute + index]);
    }

    /**
     * Get the namespace URI of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    public String getURI(int index) {
        return tree.getNamePool().getURI(tree.attCode[firstAttribute + index]);
    }

    /**
     * Get the index of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute, or -1 if there is no attribute with this name
     */

    public int getIndex(String uri, String localname) {
        int fingerprint = tree.getNamePool().getFingerprint(uri, localname);
        return getIndexByFingerprint(fingerprint);
    }

    /**
     * Get the index, given the fingerprint
     * @param fingerprint the NamePool fingerprint of the required attribute name
     * @return The index position of the attribute, or -1 if there is no attribute with this name
     */

    public int getIndexByFingerprint(int fingerprint) {
        int i = firstAttribute;
        while (tree.attParent[i] == element) {
            if ((tree.attCode[i] & NamePool.FP_MASK) == fingerprint) {
                 return i - firstAttribute;
            }
            i++;
        }
        return -1;
    }

    /**
     * Get the attribute value using its fingerprint
     */

    /*@Nullable*/ public String getValueByFingerprint(int fingerprint) {
        return getValue(getIndexByFingerprint(fingerprint));
    }

    /**
     * Get the value of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    /*@Nullable*/ public String getValue(String uri, String localname) {
        return getValue(getIndex(uri, localname));
    }

    /**
     * Get the value of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The attribute value as a string, or null if
     *         there is no attribute at that position.
     */

    /*@Nullable*/ public String getValue(int index) {
        CharSequence cs = tree.attValue[firstAttribute + index];
        return (cs==null ? null : cs.toString());
    }

    /**
     * Determine whether a given attribute has the is-ID property set
     */

    public boolean isId(int index) {
        return ((getTypeAnnotation(index).getFingerprint() == StandardNames.XS_ID) ||
                ((tree.attCode[firstAttribute + index] & NamePool.FP_MASK) == StandardNames.XML_ID));
    }

    /**
     * Determine whether a given attribute has the is-idref property set
     */

    public boolean isIdref(int index) {
        return ((getTypeAnnotation(index).getFingerprint()) == StandardNames.XS_IDREF) ||
                ((getTypeAnnotation(index).getFingerprint()) == StandardNames.XS_IDREFS);
    }
}

