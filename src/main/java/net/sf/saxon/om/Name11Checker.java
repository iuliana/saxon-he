////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.serialize.charcode.XMLCharacterData;

/**
 * The class checks names against the rules of the XML 1.1 and XML Namespaces 1.1 specification
 */

public final class Name11Checker extends NameChecker {

    public static final Name11Checker theInstance = new Name11Checker();

    /**
     * Get the singular instance of this class
     * @return the singular instance of this class
     */

    public static Name11Checker getInstance() {
        return theInstance;
    }

    /**
     * Test whether a character is a valid XML character
     *
     * @param ch the character to be tested
     * @return true if this is a valid character in the selected version of XML
     */

    public boolean isValidChar(int ch) {
        //return XMLChar.isValid(ch);
        return XMLCharacterData.isValid11(ch);
    }


    /**
     * Test whether a character can appear in an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character in an NCName the selected version of XML
     */

    public boolean isNCNameChar(int ch) {
        return XMLCharacterData.isNCName11(ch);
    }

    /**
     * Test whether a character can appear at the start of an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character at the start of an NCName the selected version of XML
     */

    public boolean isNCNameStartChar(int ch) {
        return XMLCharacterData.isNCNameStart11(ch);
    }


    /**
     * Return the XML version supported by this NameChecker
     *
     * @return "1.1" as a string
     */

    /*@NotNull*/ public String getXMLVersion() {
        return "1.1";
    }
}

