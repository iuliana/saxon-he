////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

import java.util.Arrays;

import static net.sf.saxon.expr.CastExpression.CAST_EX_CODE;

/**
 * A value of type xs:hexBinary
 */

public class HexBinaryValue extends AtomicValue {

    private byte[] binaryValue;


    /**
     * Constructor: create a hexBinary value from a supplied string, in which
     * each octet is represented by a pair of values from 0-9, a-f, A-F
     *
     * @param in character representation of the hexBinary value
     */

    public HexBinaryValue(CharSequence in) throws XPathException {
        CharSequence s = Whitespace.trimWhitespace(in);
        if ((s.length() & 1) != 0) {
            XPathException err = new XPathException("A hexBinary value must contain an even number of characters");
            err.setErrorCode(CAST_EX_CODE);
            throw err;
        }
        binaryValue = new byte[s.length() / 2];
        for (int i = 0; i < binaryValue.length; i++) {
            binaryValue[i] = (byte)((fromHex(s.charAt(2 * i)) << 4) +
                    (fromHex(s.charAt(2 * i + 1))));
        }
        typeLabel = BuiltInAtomicType.HEX_BINARY;
    }

    /**
     * Constructor: create a HexBinary value from a supplied string in hexBinary encoding,
     * with a specified type. This method throws no checked exceptions; the caller is expected
     * to ensure that the string is a valid Base64 lexical representation, that it conforms
     * to the specified type, and that the type is indeed a subtype of xs:base64Binary.
     * An unchecked exception such as an IllegalArgumentException may be thrown if these
     * conditions are not satisfied, but this is not guaranteed.
     *
     * @param s    the value in hexBinary encoding, with no leading or trailing whitespace
     * @param type the atomic type. This must be xs:base64binary or a subtype.
     */

    public HexBinaryValue(/*@NotNull*/ CharSequence s, AtomicType type) {
        if ((s.length() & 1) != 0) {
            throw new IllegalArgumentException(
                    "A hexBinary value must contain an even number of characters");
        }
        binaryValue = new byte[s.length() / 2];
        try {
            for (int i = 0; i < binaryValue.length; i++) {
                binaryValue[i] = (byte)((fromHex(s.charAt(2 * i)) << 4) +
                        (fromHex(s.charAt(2 * i + 1))));
            }
        } catch (XPathException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        typeLabel = type;
    }

    /**
     * Constructor: create a hexBinary value from a given array of bytes
     *
     * @param value the value as an array of bytes
     */

    public HexBinaryValue(byte[] value) {
        binaryValue = value;
        typeLabel = BuiltInAtomicType.HEX_BINARY;
    }

    /**
     * Create a primitive copy of this atomic value (usually so that the type label can be changed).
     *
     * @param typeLabel the target type (a derived type from hexBinary)
     */

    /*@NotNull*/ public AtomicValue copyAsSubType(AtomicType typeLabel) {
        HexBinaryValue v = new HexBinaryValue(binaryValue);
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/ public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.HEX_BINARY;
    }

    /**
     * Get the binary value
     *
     * @return the binary value, as a byte array
     */

    public byte[] getBinaryValue() {
        return binaryValue;
    }

    /**
     * Decode a single hex digit
     *
     * @param c the hex digit
     * @return the numeric value of the hex digit
     * @throws XPathException if it isn't a hex digit
     */

    private int fromHex(char c) throws XPathException {
        int d = "0123456789ABCDEFabcdef".indexOf(c);
        if (d > 15) {
            d = d - 6;
        }
        if (d < 0) {
            XPathException err = new XPathException("Invalid hexadecimal digit");
            err.setErrorCode(CAST_EX_CODE);
            throw err;
        }
        return d;
    }

    /**
     * Convert to string
     *
     * @return the canonical representation.
     */

    /*@NotNull*/ public CharSequence getPrimitiveStringValue() {
        String digits = "0123456789ABCDEF";
        FastStringBuffer sb = new FastStringBuffer(binaryValue.length * 2);
        for (int i = 0; i < binaryValue.length; i++) {
            sb.append(digits.charAt((binaryValue[i] >> 4) & 0xf));
            sb.append(digits.charAt(binaryValue[i] & 0xf));
        }
        return sb;
    }


    /**
     * Get the number of octets in the value
     *
     * @return the number of octets (bytes) in the value
     */

    public int getLengthInOctets() {
        return binaryValue.length;
    }

    /**
     * Convert to Java object (for passing to external functions)
     */

//    public Object convertAtomicToJava(Class target, XPathContext context) throws XPathException {
//
//        if (target.isAssignableFrom(HexBinaryValue.class)) {
//            return this;
//        } else if (target == Object.class) {
//            return getStringValue();
//        } else {
//            Object o = super.convertSequenceToJava(target, context);
//            if (o == null) {
//                throw new XPathException("Conversion of hexBinary to " + target.getName() +
//                        " is not supported");
//            }
//            return o;
//        }
//    }

    /**
     * Support XML Schema comparison semantics
     */

    /*@NotNull*/ public Comparable getSchemaComparable() {
        return new HexBinaryComparable();
    }

    private class HexBinaryComparable implements Comparable {

        /*@NotNull*/ public HexBinaryValue getHexBinaryValue() {
            return HexBinaryValue.this;
        }

        public int compareTo(/*@NotNull*/ Object o) {
            if (o instanceof HexBinaryComparable &&
                    Arrays.equals(getHexBinaryValue().binaryValue,
                            ((HexBinaryComparable)o).getHexBinaryValue().binaryValue)) {
                return 0;
            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return compareTo(o) == 0;
        }

        public int hashCode() {
            return HexBinaryValue.this.hashCode();
        }
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
     * @param collator
     * @param context the XPath dynamic evaluation context, used in cases where the comparison is context
*                sensitive @return an Object whose equals() and hashCode() methods implement the XPath comparison semantics
     */

    /*@Nullable*/ public Object getXPathComparable(boolean ordered, StringCollator collator, XPathContext context) {
        return (ordered ? null : this);
    }

    /**
     * Test if the two hexBinary or Base64Binaryvalues are equal.
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof HexBinaryValue && Arrays.equals(binaryValue, ((HexBinaryValue)other).binaryValue);
    }

    public int hashCode() {
        return Base64BinaryValue.byteArrayHashCode(binaryValue);
    }

}

