////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.tree.util.FastStringBuffer;

/**
 * An abstract class that efficiently handles Unicode strings including
 * non-BMP characters; it has two subclasses, one optimized for strings
 * whose characters are all in the BMP, the other handling the general case.
 */
public abstract class UnicodeString {

    public static final UnicodeString EMPTY_STRING = new GeneralUnicodeString("");

    /**
     * Make a UnicodeString for a given CharSequence
     * @param in the input CharSequence
     * @return a UnicodeString using an appropriate implementation class
     */
    public static UnicodeString makeUnicodeString(CharSequence in) {
    	return containsSurrogatePairs(in) ? new GeneralUnicodeString(in) : new BMPString(in);
    }

    /**
     * Make a UnicodeString for a given array of codepoints
     * @param in the input CharSequence
     * @return a UnicodeString using an appropriate implementation class
     */
    public static UnicodeString makeUnicodeString(int[] in) {
        for (int ch : in) {
            if (ch > 65535) {
                return new GeneralUnicodeString(in, 0, in.length);
            }
        }
        FastStringBuffer fsb = new FastStringBuffer(in.length);
        for (int ch : in) {
            fsb.append((char) ch);
        }
        return new BMPString(fsb);
    }

    /**
     * Test whether a CharSequence contains Unicode codepoints outside the BMP range
     * @param value the string to be tested
     * @return true if the string contains non-BMP codepoints
     */
	public static boolean containsSurrogatePairs(CharSequence value) {
		for (int i = 0; i < value.length(); i++) {
			int c = (int) value.charAt(i);
			if (c >= 55296 && c <= 56319) {
				return true;
			}
		}
		return false;
	}

    /**
     * Get a substring of this string
     * @param beginIndex the index of the first character to be included (counting
     * codepoints, not 16-bit characters)
     * @param endIndex the index of the first character to be NOT included (counting
     * codepoints, not 16-bit characters)
     * @return a substring
     * @throws IndexOutOfBoundsException if the selection goes off the start or end of the string
     * (this function follows the semantics of String.substring(), not the XPath semantics
     */
    public abstract UnicodeString substring(int beginIndex, int endIndex);

    /**
     * Get the first match for a given character
     * @param search the character to look for
     * @param start the first position to look
     * @return the position of the first occurrence of the sought character, or -1 if not found
     */
    public abstract int indexOf(int search, int start);

    /**
     * Get the character at a specified position
     * @param pos the index of the required character (counting
     * codepoints, not 16-bit characters)
     * @return a character (Unicode codepoint) at the specified position.
     */
    public abstract int charAt(int pos);

    /**
     * Get the length of the string, in Unicode codepoints
     * @return the number of codepoints in the string
     */
    public abstract int length();

    /**
     * Ask whether a given position is at (or beyond) the end of the string
     * @param pos the index of the required character (counting
     * codepoints, not 16-bit characters)
     * @return <tt>true</tt> iff if the specified index is after the end of the character stream
     */
    public abstract boolean isEnd(int pos);

    /**
     * Get the value of this UnicodeString as a CharSequence
     * @return a CharSequence representing the same characters as this UnicodeString
     */
    public abstract CharSequence getCharSequence();
}
