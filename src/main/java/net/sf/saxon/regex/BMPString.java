////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

/**
 * An implementation of UnicodeString optimized for strings that contain
 * no characters outside the BMP (i.e. no characters whose codepoints exceed 65535)
 */
public final class BMPString extends UnicodeString {
    /**
     * encapsulated
     */
    private final CharSequence src;

    /**
     * @param src - encapsulated CharSequence.
     *            The client must ensure that this contains no surrogate pairs
     */
    public BMPString(CharSequence src) {
        this.src = src;
    }

    public UnicodeString substring(int beginIndex, int endIndex) {
        return new BMPString(src.subSequence(beginIndex, endIndex));
    }

    public int charAt(int pos) {
        return src.charAt(pos);
    }

    public int indexOf(int search, int pos) {
        if (search > 65535) {
            return -1;
        } else {
            for (int i=pos; i<src.length(); i++) {
                if (src.charAt(i) == (char)search) {
                    return i;
                }
            }
            return -1;
        }
    }

    public int length() {
        return src.length();
    }

    public boolean isEnd(int pos) {
        return (pos >= src.length());
    }

    public String toString() {
        return src.toString();
    }

    public CharSequence getCharSequence() {
        return src;
    }
}
