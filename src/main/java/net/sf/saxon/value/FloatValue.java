////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.DoubleSortComparer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Converter;

import java.math.BigDecimal;

/**
* A numeric (single precision floating point) value
*/

public final class FloatValue extends NumericValue {

    public static final FloatValue ZERO = new FloatValue((float)0.0);
    public static final FloatValue NEGATIVE_ZERO = new FloatValue((float)-0.0);
    public static final FloatValue ONE = new FloatValue((float)1.0);
    public static final FloatValue NaN = new FloatValue(Float.NaN);

    private float value;

    /**
    * Constructor supplying a float
    * @param value the value of the float
    */

    public FloatValue(float value) {
        this.value = value;
        typeLabel = BuiltInAtomicType.FLOAT;
    }

    /**
     * Static factory method (for convenience in compiled bytecode)
     * @param value the value of the float
     * @return the FloatValue
     */

    public static FloatValue makeFloatValue(float value) {
        return new FloatValue(value);
    }

    /**
     * Constructor supplying a float and an AtomicType, for creating
     * a value that belongs to a user-defined subtype of xs:float. It is
     * the caller's responsibility to ensure that the supplied value conforms
     * to the supplied type.
     * @param value the value of the NumericValue
     * @param type the type of the value. This must be a subtype of xs:float, and the
     * value must conform to this type. The method does not check these conditions.
     */

    public FloatValue(float value, AtomicType type) {
        this.value = value;
        typeLabel = type;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        FloatValue v = new FloatValue(value);
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
        return BuiltInAtomicType.FLOAT;
    }

    /**
    * Get the value
    */

    public float getFloatValue() {
        return value;
    }

    public double getDoubleValue() {
        return (double)value;
    }

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted, for example if it is NaN or infinite
     */
    @Override
    public BigDecimal getDecimalValue() throws XPathException {
		return BigDecimal.valueOf((double) value);
    }

    /**
     * Return the numeric value as a Java long.
     *
     * @return the numeric value as a Java long. This performs truncation
     *         towards zero.
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted
     */
    @Override
    public long longValue() throws XPathException {
		return (long) value;
    }

    /**
     * Get the hashCode. This must conform to the rules for other NumericValue hashcodes
     * @see NumericValue#hashCode
     */
    public int hashCode() {
        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
			return (int) value;
        } else {
            return Double.valueOf(getDoubleValue()).hashCode();
        }
    }

    /**
     * Test whether the value is the double/float value NaN
     */
    public boolean isNaN() {
        return Float.isNaN(value);
    }

    /**
     * Get the effective boolean value
     * @return true unless the value is zero or NaN
     */
	public boolean effectiveBooleanValue() {
		return (value != 0.0 && !Float.isNaN(value));
    }


    /**
    * Get the value as a String
    * @return a String representation of the value
    */

//    public String getStringValue() {
//       return getStringValueCS().toString();
//    }

    /**
    * Get the value as a String
    * @return a String representation of the value
    */

    /*@NotNull*/ public CharSequence getPrimitiveStringValue() {
        return floatToString(value);
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:float, the canonical
     * representation always uses exponential notation.
     */

    public CharSequence getCanonicalLexicalRepresentation() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.TINY);
        return FloatingPointConverter.appendFloat(fsb, value, true);
    }

    /**
     * Internal method used for conversion of a float to a string
     * @param value the actual value
     * @return the value converted to a string, according to the XPath casting rules.
     */
    public static CharSequence floatToString(float value) {
        return FloatingPointConverter.appendFloat(new FastStringBuffer(FastStringBuffer.TINY), value, false);
    }

    /**
    * Negate the value
    */
    public NumericValue negate() {
        return new FloatValue(-value);
    }

    /**
    * Implement the XPath floor() function
    */
    public NumericValue floor() {
		return new FloatValue((float) Math.floor(value));
	 }

    /**
    * Implement the XPath ceiling() function
    */
    public NumericValue ceiling() {
		return new FloatValue((float) Math.ceil(value));
    }

    /**
    * Implement the XPath round() function
    */
    public NumericValue round(int scale) {
        if (Float.isNaN(value)) {
            return this;
        }
        if (Float.isInfinite(value)) {
            return this;
        }
        if (value == 0.0) {
            return this;    // handles the negative zero case
        }

        if (scale == 0 && value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            if (value >= -0.5 && value < 0.0) {
                return new FloatValue(-0.0f);
            }
            return new FloatValue((float) Math.round(value));
        }
        DoubleValue d = new DoubleValue(getDoubleValue());
        d = (DoubleValue) d.round(scale);
        return new FloatValue(d.getFloatValue());
    }

    /**
    * Implement the XPath round-to-half-even() function
    */

    public NumericValue roundHalfToEven(int scale) {
        DoubleValue d = new DoubleValue(getDoubleValue());
        d = (DoubleValue)d.roundHalfToEven(scale);
        return new FloatValue(d.getFloatValue());
    }

    /**
     * Determine whether the value is negative, zero, or positive
     * @return -1 if negative, 0 if zero (including negative zero), +1 if positive, NaN if NaN
     */

    public int signum() {
        if (Float.isNaN(value)) {
            return 0;
        }
        if (value > 0) return 1;
        if (value == 0) return 0;
        return -1;
    }

    /**
     * Determine with the value is (double or float) negative zero
     */
    @Override
    public boolean isNegativeZero() {
        return value == 0 && (Float.floatToIntBits(value) & FloatingPointConverter.floatSignMask) != 0;
    }

    /**
    * Determine whether the value is a whole number, that is, whether it compares
    * equal to some integer
    */

    public boolean isWholeNumber() {
        return value == Math.floor(value) && !Float.isInfinite(value);
    }

    /**
     * Get the absolute value as defined by the XPath abs() function
     * @return the absolute value
     * @since 9.2
     */

    public NumericValue abs() {
        if (value > 0.0) {
            return this;
        } else {
            return new FloatValue(Math.abs(value));
        }
    }

    public int compareTo(Object other) {
        if (!(other instanceof NumericValue)) {
            throw new ClassCastException("Numeric values are not comparable to " + other.getClass());
        }
        if (other instanceof FloatValue) {
            float otherFloat = ((FloatValue)other).value;
            if (value == otherFloat) return 0;
            if (value < otherFloat) return -1;
            return +1;
        }
        if (other instanceof DoubleValue) {
            return super.compareTo(other);
        }
        return compareTo(Converter.NUMERIC_TO_FLOAT.convert(
            (((NumericValue)other)).asAtomic()));
    }

    /**
     * Compare the value to a long
     * @param other the value to be compared with
     * @return -1 if this is less, 0 if this is equal, +1 if this is greater or if this is NaN
     */

    public int compareTo(long other) {
        float otherFloat = (float)other;
        if (value == otherFloat) return 0;
        if (value < otherFloat) return -1;
        return +1;
    }

    /**
     * Get an object that implements XML Schema comparison semantics
     */

    public Comparable getSchemaComparable() {
        // convert negative to positive zero because Float.compareTo() does the wrong thing
        // TODO: what shall we do with NaN?
        return (value == 0.0f ? 0.0f : value);
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
        return v instanceof FloatValue && DoubleSortComparer.getInstance().comparesEqual(this, (FloatValue)v);
    }
}

