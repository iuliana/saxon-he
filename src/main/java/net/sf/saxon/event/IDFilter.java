////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import java.util.HashSet;


/**
* IDFilter is a ProxyReceiver that extracts the subtree of a document rooted at the
* element with a given ID value. Namespace declarations outside this subtree are
* treated as if they were present on the identified element.
*/

public class IDFilter extends StartTagBuffer {

    private String requiredId;
    private int activeDepth = 0;
    private boolean matched = false;
    private HashSet<SimpleType> nonIDs;

    public IDFilter (Receiver next, String id) {
        // System.err.println("IDFilter, looking for " + id);
        super(next);
        this.requiredId = id;
    }

    /**
     * startElement
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        matched = false;
        if (activeDepth>0) {
            activeDepth++;
        }
        super.startElement(nameCode, typeCode, locationId, properties);  // this remembers the details
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param attName   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined, inter alia:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     *                   <dd>IS_ID</dd>         <dt>Attribute is an ID</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        super.attribute(attName, typeCode, value, locationId, properties);
        if ((attName.equals(StandardNames.XML_ID_NAME)) || ((properties &ReceiverOptions.IS_ID) != 0)) {
            if (value.toString().equals(requiredId)) {
                matched = true;
            }
        }
    }

    /**
     * startContent: Test if a matching ID attribute was found; if so, start outputting.
     */

    public void startContent() throws XPathException {
        if (activeDepth>0) {
            super.startContent();
        } else if (matched) {
            activeDepth = 1;
            super.startContent();
        }
    }

    protected void declareNamespacesForStartElement() throws XPathException {
        if (activeDepth == 1) {
            declareAllNamespaces();
        } else {
            super.declareNamespacesForStartElement();
        }
    }

    /**
     * endElement:
     */

    public void endElement() throws XPathException {
        if (activeDepth > 0) {
            nextReceiver.endElement();
            activeDepth--;
        } else {
            undeclareNamespacesForElement();
        }
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.comment(chars, locationId, properties);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return true;
    }

    /**
     * Test whether a type annotation code represents the type xs:ID or one of its subtypes
     * @param typeCode the fingerprint of the type name
     * @return true if the type is an ID type; false if it is not (or if the type code does not
     * resolve to a known type)
     */

    private boolean isIDCode(SimpleType typeCode) {
        if (typeCode == BuiltInAtomicType.ID) {
            return true;
        }
        if (typeCode instanceof BuiltInAtomicType) {
            return false; // No other built-in type is an ID
        }

        if (nonIDs == null) {
            nonIDs = new HashSet<SimpleType>(20);
        }
        if (nonIDs.contains(typeCode)) {
            return false;
        }
        if (typeCode.isAtomicType()) {
            if (getConfiguration().getTypeHierarchy().isSubType((AtomicType)typeCode, BuiltInAtomicType.ID)) {
                return true;
            } else {
                nonIDs.add(typeCode);
                return false;
            }
        } else {
            return false;
        }
    }

}

