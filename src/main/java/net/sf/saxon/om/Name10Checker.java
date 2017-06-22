////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.serialize.charcode.XMLCharacterData;

/**
 * The class checks names and characters
 * against the rules of the XML 1.0 and XML Namespaces 1.0 specification
 */

public final class Name10Checker extends NameChecker {

    public static final Name10Checker theInstance = new Name10Checker();

    /**
     * Get the singular instance of this class
     * @return the singular instance of this class
     */

    public static Name10Checker getInstance() {
        return theInstance;
    }

    /**
     * Validate whether a given string constitutes a valid NCName, as defined in XML Namespaces.
     *
     * @param name the name to be tested
     * @return true if the name is a lexically-valid NCName
     */

//    public boolean isValidNCName(CharSequence name) {
//        return XMLChar.isValidNCName(name);
//    }

    /**
     * Test whether a character is a valid XML character
     *
     * @param ch the character to be tested
     * @return true if this is a valid character in the selected version of XML
     */

    public boolean isValidChar(int ch) {
        //return XMLChar.isValid(ch);
        return XMLCharacterData.isValid10(ch);
    }


    /**
     * Test whether a character can appear in an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character in an NCName. The rules for XML 1.0 fifth
     * edition are the same as the XML 1.1 rules, and these are the rules that we use.
     */

    public boolean isNCNameChar(int ch) {
        return XMLCharacterData.isNCName11(ch);
    }

    /**
     * Test whether a character can appear at the start of an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character at the start of an NCName. The rules for XML 1.0 fifth
     * edition are the same as the XML 1.1 rules, and these are the rules that we use.
     */

    public boolean isNCNameStartChar(int ch) {
        return XMLCharacterData.isNCNameStart11(ch);
    }

    /**
     * Return the XML version supported by this NameChecker
     *
     * @return "1.0" as a string
     */

    /*@NotNull*/ public String getXMLVersion() {
        return "1.0";
    }

    public static void main(String[] args) {
        System.err.println(new Name10Checker().isValidNCName("a:b"));
    }
}

