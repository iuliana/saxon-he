////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.serialize.HTMLURIEscaper;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;

import java.util.Arrays;

/**
 * This class supports the functions encode-for-uri() and iri-to-uri()
 */
public class EscapeURI extends SystemFunctionCall implements Callable {

    public static final int ENCODE_FOR_URI = 1;
    public static final int IRI_TO_URI = 2;
    public static final int HTML_URI = 3;

    protected static boolean[] allowedASCII = new boolean[128];
    
    private static final String hex = "0123456789ABCDEF";
    
    private static final String hexDigits = "0123456789abcdefABCDEF";

    /**
     * Length of a UTF8 byte sequence, as a function of the first nibble
     */
    private static int[] UTF8_REPRESENTATION_LENGTH = {1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, 2, 2, 3, 4};

    static {
        Arrays.fill(allowedASCII, 0, 32, false);
        Arrays.fill(allowedASCII, 33, 127, true);
        allowedASCII[(int)'"'] = false;
        allowedASCII[(int)'<'] = false;
        allowedASCII[(int)'>'] = false;
        allowedASCII[(int)'\\'] = false;
        allowedASCII[(int)'^'] = false;
        allowedASCII[(int)'`'] = false;
        allowedASCII[(int)'{'] = false;
        allowedASCII[(int)'|'] = false;
        allowedASCII[(int)'}'] = false;
    }

    /**
    * Evaluate the function
    */
    @Override
    public StringValue evaluateItem(XPathContext c) throws XPathException {
        Item item = argument[0].evaluateItem(c);
        return evalEscapeURI(item, c);
    }

	/** Evaluate the expression
	 *
	 * @param context the dynamic evaluation context
	 * @param arguments the values of the arguments, supplied as SequenceIterators
	 * @return the result of the evaluation, in the form of a SequenceIterator
	 * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the
	 *         expression 
	 */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evalEscapeURI(arguments[0].head(), context);
    }

    private StringValue evalEscapeURI(Item item, XPathContext c) throws XPathException {
        if (item == null) {
            return StringValue.EMPTY_STRING;
        }
        final CharSequence s = item.getStringValueCS();
        switch (operation) {
            case ENCODE_FOR_URI:
                return StringValue.makeStringValue(escape(s, "-_.~"));
            case IRI_TO_URI:
                return StringValue.makeStringValue(iriToUri(s));
            case HTML_URI:
                return StringValue.makeStringValue(HTMLURIEscaper.escapeURL(s, false, c.getConfiguration()));
            default:
                throw new UnsupportedOperationException("Unknown escape operation");
        }
    }

    /**
     * Escape special characters in a URI. The characters that are %HH-encoded are
     * all non-ASCII characters
     * @param s the URI to be escaped
     * @return the %HH-encoded string
     */
	public static CharSequence iriToUri(CharSequence s) {
		// NOTE: implements a late spec change which says that characters that are illegal in an IRI,
		// for example "\", must be %-encoded.
		if (allAllowedAscii(s)) {
			// it's worth doing a prescan to avoid the cost of copying in the common all-ASCII case
			return s;
		}
		FastStringBuffer sb = new FastStringBuffer(s.length() + 20);
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c >= 0x7f || !allowedASCII[(int) c]) {
				escapeChar(c, ((i + 1) < s.length() ? s.charAt(i + 1) : ' '), sb);
			} else {
				sb.append(c);
			}
		}
		return sb;
	}

	private static boolean allAllowedAscii(CharSequence s) {
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c >= 0x7f || !allowedASCII[(int) c]) {
				return false;
			}
		}
		return true;
	}

    /**
     * Escape special characters in a URI. The characters that are %HH-encoded are
     * all non-ASCII characters, plus all ASCII characters except (a) letter A-Z
     * and a-z, (b) digits 0-9, and (c) characters listed in the allowedPunctuation
     * argument
     * @param s the URI to be escaped
     * @param allowedPunctuation ASCII characters other than letters and digits that
     * should NOT be %HH-encoded
     * @return the %HH-encoded string
     */
	public static CharSequence escape(CharSequence s, String allowedPunctuation) {
		FastStringBuffer sb = new FastStringBuffer(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
				allowedPunctuation.indexOf(c) >= 0) {
				sb.append(c);
			} else if (c <= 0x20 || c >= 0x7f) {
				escapeChar(c, ((i + 1) < s.length() ? s.charAt(i + 1) : ' '), sb);
			} else {
				escapeChar(c, ' ', sb);
			}
		}
		return sb;
	}
    /**
     * Escape a single character in %HH representation, or a pair of two chars representing
     * a surrogate pair
     * @param c the character to be escaped, or the first character of a surrogate pair
     * @param c2 the second character of a surrogate pair
     * @param sb the buffer to contain the escaped result
     */
	private static void escapeChar(char c, char c2, FastStringBuffer sb) {
		byte[] array = new byte[4];
		int used = UTF8CharacterSet.getUTF8Encoding(c, c2, array);
		for (int b = 0; b < used; b++) {
			int v = (int) array[b] & 0xff;
			sb.append('%');
			sb.append(hex.charAt(v / 16));
			sb.append(hex.charAt(v % 16));
		}
	}

    /**
     * Check that any percent-encoding within a URI is well-formed. The method assumes that a percent
     * sign followed by two hex digits represents an octet of the UTF-8 representation of a character;
     * any other percent sign is assumed to represent itself.
     * @param uri the string to be checked for validity
     * @throws XPathException if the string is not validly percent-encoded
     */
	public static void checkPercentEncoding(String uri) throws XPathException {
		for (int i = 0; i < uri.length();) {
			char c = uri.charAt(i);
			byte[] bytes;
			// Note: we're translating the UTF-8 byte sequence but then not using the value
			int expectedOctets;
			if (c == '%') {
				if (i + 2 >= uri.length()) {
					throw new XPathException("% sign in URI must be followed by two hex digits" + Err.wrap(uri));
				}
				int h1 = hexDigits.indexOf(uri.charAt(i + 1));
				if (h1 > 15) {
					h1 -= 6;
				}
				int h2 = hexDigits.indexOf(uri.charAt(i + 2));
				if (h2 > 15) {
					h2 -= 6;
				}
				if (h1 >= 0 && h2 >= 0) {
					int b = h1 << 4 | h2;
					expectedOctets = UTF8_REPRESENTATION_LENGTH[h1];
					if (expectedOctets == -1) {
						throw new XPathException("First %-encoded octet in URI is not valid as the start of a UTF-8 " +
							"character: first two bits must not be '10'" + Err.wrap(uri));
					}
					bytes = new byte[expectedOctets];
					bytes[0] = (byte) b;
					i += 3;
					for (int q = 1; q < expectedOctets; q++) {
						if (i + 2 > uri.length() || uri.charAt(i) != '%') {
							throw new XPathException("Incomplete %-encoded UTF-8 octet sequence in URI " +
								Err.wrap(uri));
						}
						h1 = hexDigits.indexOf(uri.charAt(i + 1));
						if (h1 > 15) {
							h1 -= 6;
						}
						h2 = hexDigits.indexOf(uri.charAt(i + 2));
						if (h2 > 15) {
							h2 -= 6;
						}
						if (h1 < 0 || h2 < 0) {
							throw new XPathException("Invalid %-encoded UTF-8 octet sequence in URI" + Err.wrap(uri));
						}
						if (UTF8_REPRESENTATION_LENGTH[h1] != -1) {
							throw new XPathException("In a URI, a %-encoded UTF-8 octet after the first " +
								"must have '10' as the first two bits" + Err.wrap(uri));
						}
						b = h1 << 4 | h2;
						bytes[q] = (byte) b;
						i += 3;
					}
				} else {
					throw new XPathException("% sign in URI must be followed by two hex digits" + Err.wrap(uri));
				}
			} else {
				i++;
			}
		}
	}


}

