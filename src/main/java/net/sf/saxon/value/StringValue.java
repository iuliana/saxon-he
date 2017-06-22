////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.regex.BMPString;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;


/**
 * An atomic value of type xs:string. This class is also used for types derived from xs:string.
 * Subclasses of StringValue are used for xs:untypedAtomic and xs:anyURI values.
 */

public class StringValue extends AtomicValue {

    /*@NotNull*/ public static final StringValue EMPTY_STRING = new StringValue("");
    /*@NotNull*/ public static final StringValue SINGLE_SPACE = new StringValue(" ");
    /*@NotNull*/ public static final StringValue TRUE = new StringValue("true");
    /*@NotNull*/ public static final StringValue FALSE = new StringValue("false");

    static {
        EMPTY_STRING.unicodeString = new BMPString("");
        SINGLE_SPACE.unicodeString = new BMPString(" ");
        TRUE.unicodeString = new BMPString("true");
        FALSE.unicodeString = new BMPString("false");
    }

    // We hold the value as a CharSequence (it may be a StringBuffer rather than a string)
    // But the first time this is converted to a string, we keep it as a string

    protected CharSequence value;     // may be zero-length, will never be null
    protected UnicodeString unicodeString = null;

    /**
     * Protected constructor for use by subtypes
     */

    protected StringValue() {
        value = "";
        typeLabel = BuiltInAtomicType.STRING;
    }

    /**
     * Constructor. Note that although a StringValue may wrap any kind of CharSequence
     * (usually a String, but it can also be, for example, a StringBuffer), the caller
     * is responsible for ensuring that the value is immutable.
     * @param value the String value. Null is taken as equivalent to "".
     */

    public StringValue(/*@Nullable*/ CharSequence value) {
        this.value = (value == null ? "" : value);
        typeLabel = BuiltInAtomicType.STRING;
    }

    /**
     * Constructor. Note that although a StringValue may wrap any kind of CharSequence
     * (usually a String, but it can also be, for example, a StringBuffer), the caller
     * is responsible for ensuring that the value is immutable.
     * @param value the String value.
     * @param typeLabel the type of the value to be created. The caller must ensure that this is
     *  a type derived from string and that the string is valid against this type.
     */

    public StringValue(CharSequence value, AtomicType typeLabel) {
        this.value = value;
        this.typeLabel = typeLabel;
    }


    /**
     * Assert that the string is known to contain no surrogate pairs
     */

    public void setContainsNoSurrogates() {
        unicodeString = new BMPString(value);
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        StringValue v = new StringValue(value);
        v.unicodeString = unicodeString;
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Factory method. Unlike the constructor, this avoids creating a new StringValue in the case
     * of a zero-length string (and potentially other strings, in future)
     * @param value the String value. Null is taken as equivalent to "".
     * @return the corresponding StringValue
     */

    /*@NotNull*/ public static StringValue makeStringValue(/*@Nullable*/ CharSequence value) {
        if (value == null || value.length() == 0) {
            return StringValue.EMPTY_STRING;
        } else {
            return new StringValue(value);
        }
    }

    public static StringValue makeStringValue(UnicodeString unicode) {
        if (unicode.length() == 0) {
            return EMPTY_STRING;
        }
        StringValue sv = new StringValue(unicode.getCharSequence());
        sv.unicodeString = unicode;
        return sv;
    }

    /**
     * Get the string value as a CharSequence
     */

    public final CharSequence getPrimitiveStringValue() {
        return value;
    }

    /**
     * Set the value of the item as a CharSequence.
     * <p><b>For system use only. In principle, a StringValue is immutable. However, in special circumstances,
     * if it is newly constructed, the content can be changed to reflect the effect of the whiteSpace facet.</b></p>
     * @param value the value of the string
     */
    
    public final void setStringValueCS(CharSequence value) {
        this.value = value;
    }

    /**
     * Get the length of this string, as defined in XPath. This is not the same as the Java length,
     * as a Unicode surrogate pair counts as a single character
     * @return the length of the string in Unicode code points
     */

    public int getStringLength() {
        if (unicodeString == null) {
            makeUnicodeString();
        }
        return unicodeString.length();
    }

    public void makeUnicodeString() {
        unicodeString = UnicodeString.makeUnicodeString(value);
    }

    public UnicodeString getUnicodeString() {
        if (unicodeString == null) {
            makeUnicodeString();
        }
        return unicodeString;
    }

    /**
     * Get the length of a string, as defined in XPath. This is not the same as the Java length,
     * as a Unicode surrogate pair counts as a single character.
     * @param s The string whose length is required
     * @return the length of the string in Unicode code points
     */

    public static int getStringLength(/*@NotNull*/ CharSequence s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            int c = (int) s.charAt(i);
            if (c < 55296 || c > 56319) n++;    // don't count high surrogates, i.e. D800 to DBFF
        }
        return n;
    }


    /**
     * Determine whether the string is a zero-length string. This may
     * be more efficient than testing whether the length is equal to zero
     * @return true if the string is zero length
     */

    public boolean isZeroLength() {
        return value.length() == 0;
    }

    /**
     * Determine whether the string contains surrogate pairs
     * @return true if the string contains any non-BMP characters
     */

    public boolean containsSurrogatePairs() {
        if (unicodeString != null) {
            return unicodeString.length() != value.length();
        }
        return UnicodeString.containsSurrogatePairs(value);
    }

    /**
     * Ask whether the string is known to contain no surrogate pairs.
     * @return true if it is known to contain no surrogates, false if the answer is not known
     */

    public boolean isKnownToContainNoSurrogates() {
        return unicodeString instanceof BMPString;
    }

    /**
     * Iterate over a string, returning a sequence of integers representing the Unicode code-point values
     * @return an iterator over the characters (Unicode code points) in the string
     */

    /*@NotNull*/ public UnfailingIterator iterateCharacters() {
        return new CharacterIterator();
    }
                         
    /**
     * Expand a string containing surrogate pairs into an array of 32-bit characters
     * @param s the string to be expanded
     * @return an array of integers representing the Unicode code points
     */

    /*@NotNull*/ public static int[] expand(/*@NotNull*/ CharSequence s) {
        int[] array = new int[getStringLength(s)];
        int o = 0;
        for (int i = 0; i < s.length(); i++) {
            int charval;
            int c = s.charAt(i);
            if (c >= 55296 && c <= 56319) {
                // we'll trust the data to be sound
                charval = ((c - 55296) * 1024) + ((int) s.charAt(i + 1) - 56320) + 65536;
                i++;
            } else {
                charval = c;
            }
            array[o++] = charval;
        }
        return array;
    }

    /**
     * Contract an array of integers containing Unicode codepoints into a Java string
     * @param codes an array of integers representing the Unicode code points
     * @param used the number of items in the array that are actually used
     * @return the constructed string
     */

    /*@NotNull*/ public static CharSequence contract(/*@NotNull*/ int[] codes, int used) {
        FastStringBuffer sb = new FastStringBuffer(codes.length);
        for (int i=0; i<used; i++) {
            sb.appendWideChar(codes[i]);
        }
        return sb;
    }


    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * If the ordered parameter is set to true, the result will be a Comparable and will support a compareTo()
     * method with the semantics of the XPath lt/gt operator, provided that the other operand is also obtained
     * using the getXPathComparable() method. In all cases the result will support equals() and hashCode() methods
     * that support the semantics of the XPath eq operator, again provided that the other operand is also obtained
     * using the getXPathComparable() method. A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     *
     * @param ordered true if an ordered comparison is required. In this case the result is null if the
     *                type is unordered; in other cases the returned value will be a Comparable.
     * @param collator Collation to be used for comparing strings
     * @param context the XPath dynamic evaluation context, used in cases where the comparison is context
     *                sensitive
     * @return an Object whose equals() and hashCode() methods implement the XPath comparison semantics
     *         with respect to this atomic value. If ordered is specified, the result will either be null if
     *         no ordering is defined, or will be a Comparable
     */

    public Object getXPathComparable(boolean ordered, /*@NotNull*/ StringCollator collator, XPathContext context) {
        return collator.getCollationKey(asString());
    }

    /**
     * Determine if two AtomicValues are equal, according to XPath rules. (This method
     * is not used for string comparisons, which are always under the control of a collation.
     * If we get here, it's because there's a type error in the comparison.)
     * @throws ClassCastException always
     */

    @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
    public boolean equals(Object other) {
        throw new ClassCastException("equals on StringValue is not allowed");
    }
    
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Returns the underlying string, with the side-effect of storing the string internally for future use,
     * potentially reducing the cost if it is ever needed again
     * @return the value as a String
     */

    public String asString() {
        if(value instanceof String) {
            return (String)value;
        }
        String s = value.toString();
        value = s;
        return s;
    }

    /**
     * Test whether this StringValue is equal to another under the rules of the codepoint collation
     * @param other the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean codepointEquals(/*@NotNull*/ StringValue other) {
        // avoid conversion of CharSequence to String if values are different lengths
        return value.length() == other.value.length() &&
                asString().equals(other.asString());
        // It might be better to do character-by-character comparison in all cases; or it might not.
        // We do it this way in the hope that string comparison compiles to native code.
    }

    /**
     * Get the effective boolean value of a string
     * @return true if the string has length greater than zero
     */

    public boolean effectiveBooleanValue() {
        return value.length() > 0;
    }


    /*@NotNull*/ public String toString() {
        return "\"" + value + '\"';
    }

    /**
     * Get a Comparable value that implements the XML Schema comparison semantics for this value.
     * Returns null if the value is not comparable according to XML Schema rules. This implementation
     * returns the underlying Java string, which works because strings will only be compared for
     * equality, not for ordering, and the equality rules for strings in XML schema are the same as in Java.
     */

    public Comparable getSchemaComparable() {
        return asString();
    }

    /**
     * Determine whether two atomic values are identical, as determined by XML Schema rules. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * <p>Note that even this check ignores the type annotation of the value. The integer 3 and the short 3
     * are considered identical, even though they are not fully interchangeable. "Identical" means the
     * same point in the value space, regardless of type annotation.</p>
     * <p>NaN is identical to itself.</p>
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return v instanceof StringValue &&
                (this instanceof AnyURIValue == v instanceof AnyURIValue) &&
                (this instanceof UntypedAtomicValue == v instanceof UntypedAtomicValue) &&
                asString().equals(((StringValue)v).asString());
    }

    /**
     * Produce a diagnostic representation of the contents of the string
     * @param s the string
     * @return a string in which non-Ascii-printable characters are replaced by \ uXXXX escapes
     */

    /*@NotNull*/ public static String diagnosticDisplay(/*@NotNull*/ String s) {
        FastStringBuffer fsb = new FastStringBuffer(s.length());
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7e) {
                fsb.append(c);
            } else {
                fsb.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) {
                    fsb.append("0123456789ABCDEF".charAt((c >> shift) & 0xF));
                }
            }
        }
        return fsb.toString();
    }

    /**
     * CharacterIterator is used to iterate over the characters in a string,
     * returning them as integers representing the Unicode code-point.
     */


    public final class CharacterIterator implements UnfailingIterator<Int64Value> {

        int inpos = 0;        // 0-based index of the current Java char
        int outpos = 0;       // 1-based value of position() function
        int current = -1;     // Unicode codepoint most recently returned

        /**
         * Create an iterator over a string
         */

        public CharacterIterator() {
        }

        /*@Nullable*/ public Int64Value next() {
            if (inpos < value.length()) {
                int c = value.charAt(inpos++);
                if (c >= 55296 && c <= 56319) {
                    // we'll trust the data to be sound
                    try {
                        current = ((c - 55296) * 1024) + ((int) value.charAt(inpos++) - 56320) + 65536;
                    } catch (StringIndexOutOfBoundsException e) {
                        System.err.println("Invalid surrogate at end of string");
                        System.err.println(diagnosticDisplay(value.toString()));
                        e.printStackTrace();
                        throw e;
                    }
                } else {
                    current = c;
                }
                outpos++;
                return new Int64Value(current);
            } else {
                outpos = -1;
                return null;
            }
        }

        /*@Nullable*/ public Int64Value current() {
            if (outpos < 1) {
                return null;
            }
            return new Int64Value(current);
        }

        public int position() {
            return outpos;
        }

        public void close() {
        }

        /*@NotNull*/ public UnfailingIterator<Int64Value> getAnother() {
            return new CharacterIterator();
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED} and {@link #LAST_POSITION_FINDER}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         */

        public int getProperties() {
            return 0;
        }
    }


}

