////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SimpleType;

/**
* CharacterMapExpander: This ProxyReceiver expands characters occurring in a character map,
 * as specified by the XSLT 2.0 xsl:character-map declaration
*
* @author Michael Kay
*/


public class CharacterMapExpander extends ProxyReceiver {

    private CharacterMap charMap;
    private boolean useNullMarkers = true;

    public CharacterMapExpander(Receiver next) {
        super(next);
    }

    /**
     * Set the character maps to be used by this CharacterMapExpander.
     * They are merged into a single character map if there is more than one.
     */

    public void setCharacterMap(CharacterMap map) {
        charMap = map;
    }

    /**
     * Indicate whether the result of character mapping should be marked using NUL
     * characters to prevent subsequent XML or HTML character escaping. The default value
     * is true (used for the XML and HTML output methods); the value false is used by the text
     * output method.
     */

    public void setUseNullMarkers(boolean use) {
        useNullMarkers = use;
    }

    /**
     * Output an attribute
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties)
            throws XPathException {
        if ((properties & ReceiverOptions.DISABLE_CHARACTER_MAPS) == 0) {
            CharSequence mapped = charMap.map(value, useNullMarkers);
            if (mapped == value) {
                // no mapping was done
                nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
            } else {
                nextReceiver.attribute(nameCode, typeCode, mapped,
                        locationId,
                        (properties | ReceiverOptions.USE_NULL_MARKERS) & ~ReceiverOptions.NO_SPECIAL_CHARS);
            }
        } else {
            nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
        }
    }

    /**
    * Output character data
    */

    public void characters(/*@NotNull*/ CharSequence chars, int locationId, int properties) throws XPathException {

        if ((properties & ReceiverOptions.DISABLE_ESCAPING) == 0) {
            CharSequence mapped = charMap.map(chars, useNullMarkers);
            if (mapped != chars) {
                properties = (properties | ReceiverOptions.USE_NULL_MARKERS) & ~ReceiverOptions.NO_SPECIAL_CHARS;
            }
            nextReceiver.characters(mapped, locationId, properties);
        } else {
            // if the user requests disable-output-escaping, this overrides the character
            // mapping
            nextReceiver.characters(chars, locationId, properties);
        }

    }


}

