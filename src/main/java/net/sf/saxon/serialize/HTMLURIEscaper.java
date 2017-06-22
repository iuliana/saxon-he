////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
  * This class is used as a filter on the serialization pipeline; it performs the function
  * of escaping URI-valued attributes in HTML
  * @author Michael H. Kay
  */

public class HTMLURIEscaper extends ProxyReceiver {

    /**
    * Table of attributes whose value is a URL
    */

    // we use two HashMaps to avoid unnecessary string concatenations

    private static HTMLTagHashSet urlAttributes = new HTMLTagHashSet(47);
    private static HTMLTagHashSet urlCombinations = new HTMLTagHashSet(101);

    static {
        setUrlAttribute("form", "action");
        setUrlAttribute("object", "archive");
        setUrlAttribute("body", "background");
        setUrlAttribute("q", "cite");
        setUrlAttribute("blockquote", "cite");
        setUrlAttribute("del", "cite");
        setUrlAttribute("ins", "cite");
        setUrlAttribute("object", "classid");
        setUrlAttribute("object", "codebase");
        setUrlAttribute("applet", "codebase");
        setUrlAttribute("object", "data");
        setUrlAttribute("button", "datasrc");
        setUrlAttribute("div", "datasrc");
        setUrlAttribute("input", "datasrc");
        setUrlAttribute("object", "datasrc");
        setUrlAttribute("select", "datasrc");
        setUrlAttribute("span", "datasrc");
        setUrlAttribute("table", "datasrc");
        setUrlAttribute("textarea", "datasrc");
        setUrlAttribute("script", "for");
        setUrlAttribute("a", "href");
        setUrlAttribute("a", "name");       // see second note in section B.2.1 of HTML 4 specification
        setUrlAttribute("area", "href");
        setUrlAttribute("link", "href");
        setUrlAttribute("base", "href");
        setUrlAttribute("img", "longdesc");
        setUrlAttribute("frame", "longdesc");
        setUrlAttribute("iframe", "longdesc");
        setUrlAttribute("head", "profile");
        setUrlAttribute("script", "src");
        setUrlAttribute("input", "src");
        setUrlAttribute("frame", "src");
        setUrlAttribute("iframe", "src");
        setUrlAttribute("img", "src");
        setUrlAttribute("img", "usemap");
        setUrlAttribute("input", "usemap");
        setUrlAttribute("object", "usemap");
    }

    private static void setUrlAttribute(String element, String attribute) {
        urlAttributes.add(attribute);
        urlCombinations.add(element + '+' + attribute);
    }

    public boolean isUrlAttribute(NodeName element, NodeName attribute) {
        if (pool == null) {
            pool = getNamePool();
        }
        String attributeName = attribute.getDisplayName();
        if (!urlAttributes.contains(attributeName)) {
            return false;
        }
        String elementName = element.getDisplayName();
        return urlCombinations.contains(elementName + '+' + attributeName);
    }

    protected NodeName currentElement;
    protected boolean escapeURIAttributes = true;
    protected NamePool pool;

    public HTMLURIEscaper(Receiver nextReceiver) {
        super(nextReceiver);
    }

     /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        nextReceiver.startDocument(properties);
        pool = getPipelineConfiguration().getConfiguration().getNamePool();
    }

    /**
     * Notify the start of an element
     *
     * @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        currentElement = nameCode;
        nextReceiver.startElement(nameCode, typeCode, locationId, properties);
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

	public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
		throws XPathException {
		if (escapeURIAttributes && (properties & ReceiverOptions.DISABLE_ESCAPING) == 0 &&
			isUrlAttribute(currentElement, nameCode)) {
			nextReceiver.attribute(nameCode, typeCode, escapeURL(value, true, getConfiguration()), locationId,
				properties | ReceiverOptions.DISABLE_CHARACTER_MAPS);
		} else {
			nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
		}
	}

    /**
     * Escape a URI according to the HTML rules: that is, a non-ASCII character (specifically,
     * a character outside the range 32 - 126) is replaced by the %HH encoding of the octets in
     * its UTF-8 representation
     * @param url the URI to be escaped
     * @param normalize
     * @return the URI after escaping non-ASCII characters
     */

    /*@NotNull*/ public static CharSequence escapeURL(CharSequence url, boolean normalize, Configuration config) throws XPathException {
        // optimize for the common case where the string is all ASCII characters
        for (int i=url.length()-1; i>=0; i--) {
            char ch = url.charAt(i);
            if (ch<32 || ch>126) {
                if (normalize) {
                    CharSequence normalized = new Normalizer(Normalizer.C, config).normalize(url);
                    return reallyEscapeURL(normalized);
                } else {
                    return reallyEscapeURL(url);
                }
            }
        }
        return url;
    }

    private static CharSequence reallyEscapeURL(CharSequence url) {
        FastStringBuffer sb = new FastStringBuffer(url.length() + 20);
        final String hex = "0123456789ABCDEF";
        byte[] array = new byte[4];

        for (int i=0; i<url.length(); i++) {
            char ch = url.charAt(i);
            if (ch<32 || ch>126) {
                int used = UTF8CharacterSet.getUTF8Encoding(ch,
                                                 (i+1 < url.length() ? url.charAt(i+1): ' '), array);
                for (int b=0; b<used; b++) {
                    //int v = (array[b]>=0 ? array[b] : 256 + array[b]);
                    int v = ((int)array[b]) & 0xff;
                    sb.append('%');
                    sb.append(hex.charAt(v/16));
                    sb.append(hex.charAt(v%16));
                }

            } else {
                sb.append(ch);
            }
        }
        return sb;
    }
}

