////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The MetaTagAdjuster adds a meta element to the content of the head element, indicating
 * the required content type and encoding; it also removes any existing meta element
 * containing this information
 */

public class MetaTagAdjuster extends ProxyReceiver {

    boolean seekingHead = true;
    int droppingMetaTags = -1;
    boolean inMetaTag = false;
    boolean foundHead = false;
    /*@Nullable*/ String headPrefix = null;
    NodeName metaCode;
    String requiredURI = "";
    AttributeCollectionImpl attributes;
    List<NamespaceBinding> namespaces = new ArrayList<NamespaceBinding>();
    String encoding;
    String mediaType;
    int level = 0;
    boolean isXHTML = false;

    /**
     * Create a new MetaTagAdjuster
     * @param next the next receiver in the pipeline
     */

    public MetaTagAdjuster(Receiver next) {
        super(next);
    }

    /**
    * Set output properties
     * @param details the serialization properties
     */

    public void setOutputProperties(Properties details) {
        encoding = details.getProperty(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = "UTF-8";
        }
        mediaType = details.getProperty(OutputKeys.MEDIA_TYPE);
        if (mediaType == null) {
            mediaType = "text/html";
        }
    }

    /**
     * Indicate whether we're handling HTML or XHTML
     */

    public void setIsXHTML(boolean xhtml) {
        isXHTML = xhtml;
        if (xhtml) {
            requiredURI = NamespaceConstant.XHTML;
        } else {
            requiredURI = "";
        }
    }

    /**
     * Compare a name: case-blindly in the case of HTML, case-sensitive for XHTML
     */

    private boolean comparesEqual(String name1, String name2) {
        if (isXHTML) {
            return name1.equals(name2);
        } else {
            return name1.equalsIgnoreCase(name2);
        }

    }

    /**
     * Notify the start of an element
     *
     * @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (droppingMetaTags == level) {
            metaCode = nameCode;
            String localName = nameCode.getLocalPart();
            if (nameCode.isInNamespace(requiredURI) && comparesEqual(localName, "meta")) {
                inMetaTag = true;
                attributes.clear();
                namespaces.clear();
                return;
            }
        }
        level++;
        nextReceiver.startElement(nameCode, typeCode, locationId, properties);
        if (seekingHead) {
            NamePool namePool = getNamePool();
            String localName = nameCode.getLocalPart();
            if (nameCode.isInNamespace(requiredURI) && comparesEqual(localName, "head")) {
                foundHead = true;
                headPrefix = nameCode.getPrefix();
            }
        }

    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        if (inMetaTag) {
            attributes.addAttribute(nameCode, typeCode, value.toString(), locationId, properties);
        } else {
            nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
        }
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding the prefix/uri pair representing the namespace binding
     * @param properties       any special properties to be passed on this call
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */
    @Override
    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        if (inMetaTag) {
            // fix bug 1788
            namespaces.add(namespaceBinding);
        } else {
            nextReceiver.namespace(namespaceBinding, properties);
        }
    }


    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */


    public void startContent() throws XPathException {
        if (foundHead) {
            foundHead = false;
            NamePool namePool = getNamePool();
            nextReceiver.startContent();
            FingerprintedQName metaCode = new FingerprintedQName(headPrefix, requiredURI, "meta");
            nextReceiver.startElement(metaCode, Untyped.getInstance(), 0, 0);
            nextReceiver.attribute(new NoNamespaceName("http-equiv"), BuiltInAtomicType.UNTYPED_ATOMIC, "Content-Type", 0, 0);
            nextReceiver.attribute(new NoNamespaceName("content"), BuiltInAtomicType.UNTYPED_ATOMIC, mediaType + "; charset=" + encoding, 0, 0);
            nextReceiver.startContent();
            droppingMetaTags = level;
            seekingHead = false;
            attributes = new AttributeCollectionImpl(getConfiguration());
            nextReceiver.endElement();
        }
        if (!inMetaTag) {
            nextReceiver.startContent();
        }
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        if (inMetaTag) {
            inMetaTag = false;
            // if there was an http-equiv="ContentType" attribute, discard the meta element entirely
            boolean found = false;
            for (int i=0; i<attributes.getLength(); i++) {
                String name = attributes.getLocalName(i);
                if (comparesEqual(name, "http-equiv")) {
                    String value = Whitespace.trim(attributes.getValue(i));
                    if (value.equalsIgnoreCase("Content-Type")) {
                        // case-blind comparison even for XHTML
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                // this was a meta element, but not one of the kind that we discard
                nextReceiver.startElement(metaCode, Untyped.getInstance(), 0, 0);
                for (int i=0; i<attributes.getLength(); i++) {
                    NodeName name = attributes.getNodeName(i);
                    SimpleType typeCode = attributes.getTypeAnnotation(i);
                    String value = attributes.getValue(i);
                    int locationId = attributes.getLocationId(i);
                    int properties = attributes.getProperties(i);
                    nextReceiver.attribute(name, typeCode, value, locationId, properties);
                }
                nextReceiver.startContent();
                nextReceiver.endElement();
            }
        } else {
            level--;
            if (droppingMetaTags == level+1) {
                droppingMetaTags = -1;
            }
            nextReceiver.endElement();
        }
    }

}
