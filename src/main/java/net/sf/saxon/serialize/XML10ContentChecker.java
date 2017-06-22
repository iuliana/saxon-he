////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.om.Name10Checker;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import java.util.HashSet;

/**
 * This class is used on the serialization pipeline to check that the document conforms
 * to XML 1.0 rules. It is placed on the pipeline only when the configuration permits
 * XML 1.1 constructs, but the particular output document is being serialized as XML 1.0
 */

public class XML10ContentChecker extends ProxyReceiver {

    private NameChecker checker = Name10Checker.getInstance();
    // TODO: is the cache thread-safe?
    private IntHashSet fingerprintCache = new IntHashSet(100);
    private HashSet<NodeName> nameCache = new HashSet<NodeName>(100);

    public XML10ContentChecker(Receiver next) {
        super(next);
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (elemName.hasFingerprint()) {
            if (!fingerprintCache.contains(elemName.getNameCode())) {
                checkLocalName(elemName, locationId);
                fingerprintCache.add(elemName.getNameCode());
            }
        } else {
            if (!nameCache.contains(elemName)) {
                checkLocalName(elemName, locationId);
                nameCache.add(elemName);
            }
        }
        nextReceiver.startElement(elemName, typeCode, locationId, properties);
    }

    private void checkLocalName(NodeName elemName, int locationId) throws XPathException {
        if (!checker.isValidNCName(elemName.getLocalPart())) {
            XPathException err = new XPathException("Invalid XML 1.0 element name " +
                    Err.wrap(elemName.getLocalPart(), Err.ELEMENT));
            err.setErrorCode("SERE0005");
            err.setLocator(new ExpressionLocation(getPipelineConfiguration().getLocationProvider(), locationId));
            throw err;
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param attName   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        if (attName.hasFingerprint()) {
            if (!fingerprintCache.contains(attName.getFingerprint())) {
                if (!checker.isValidNCName(attName.getLocalPart())) {
                    XPathException err = new XPathException("Invalid XML 1.0 attribute name " +
                            Err.wrap(attName.getLocalPart(), Err.ATTRIBUTE));
                    err.setErrorCode("SERE0005");
                    err.setLocator(new ExpressionLocation(getPipelineConfiguration().getLocationProvider(), locationId));
                    throw err;
                }
                fingerprintCache.add(attName.getFingerprint());
            }
        }
        if (!nameCache.contains(attName)) {
            if (!checker.isValidNCName(attName.getLocalPart())) {
                XPathException err = new XPathException("Invalid XML 1.0 attribute name " +
                        Err.wrap(attName.getLocalPart(), Err.ATTRIBUTE));
                err.setErrorCode("SERE0005");
                err.setLocator(new ExpressionLocation(getPipelineConfiguration().getLocationProvider(), locationId));
                throw err;
            }
            nameCache.add(attName);
        }
        checkString(value, locationId);
        nextReceiver.attribute(attName, typeCode, value, locationId, properties);
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        checkString(chars, locationId);
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        checkString(chars, locationId);
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, /*@NotNull*/ CharSequence data, int locationId, int properties) throws XPathException {
        if (!checker.isValidNCName(target)) {
            XPathException err = new XPathException("Invalid XML 1.0 processing instruction name " +
                    Err.wrap(target));
            err.setErrorCode("SERE0005");
            err.setLocator(new ExpressionLocation(getPipelineConfiguration().getLocationProvider(), locationId));
            throw err;
        }
        checkString(data, locationId);
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

     /**
      * Check that a string consists of valid XML 1.0 characters (UTF-16 encoded)
      * @param in the string to be checked
      * @param locationId the location of the string
     */

    private void checkString(CharSequence in, long locationId) throws XPathException {
         final int len = in.length();
         for (int c=0; c<len; c++) {
            int ch32 = in.charAt(c);
            if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                char low = in.charAt(++c);
                ch32 = UTF16CharacterSet.combinePair((char)ch32, low);
            }
            if (!XMLCharacterData.isValid10(ch32)) {
                XPathException err = new XPathException("The result tree contains a character not allowed by XML 1.0 (hex " +
                        Integer.toHexString(ch32) + ')');
                err.setErrorCode("SERE0006");
                err.setLocator(new ExpressionLocation(getPipelineConfiguration().getLocationProvider(), locationId));
                throw err;
            }
        }
    }

}

