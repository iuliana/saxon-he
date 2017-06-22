////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.log4j.Logger;

/**
 * A value of type xs:dayTimeDuration
 */
public final class DayTimeDurationValue extends DurationValue implements Comparable {

	private static final Logger LOGGER = Logger.getLogger(DayTimeDurationValue.class);
	
    /**
     * Private constructor for internal use
     */
    private DayTimeDurationValue() {
        typeLabel = BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Factory method: create a duration value from a supplied string, in
     * ISO 8601 format [-]PnDTnHnMnS
     *
     * @param s the lexical representation of the xs:dayTimeDuration value
     * @return a DayTimeDurationValue if the format is correct, or a ValidationErrorValue if not
     */
    public static ConversionResult makeDayTimeDurationValue(CharSequence s) {
        ConversionResult d = DurationValue.makeDuration(s, false, true);
        if (d instanceof ValidationFailure) {
            return d;
        }
        DurationValue dv = (DurationValue)d;
        return Converter.DURATION_TO_DAY_TIME_DURATION.convert(dv);
    }

    /**
     * Create a dayTimeDuration given the number of days, hours, minutes, and seconds. This
     * constructor performs no validation. The components (apart from sign) must all be non-negative
     * integers; they need not be normalized (for example, 36 hours is acceptable)
     *
     * @param sign         positive number for positive durations, negative for negative duratoins
     * @param days         number of days
     * @param hours        number of hours
     * @param minutes      number of minutes
     * @param seconds      number of seconds
     * @param microseconds number of microseconds
     * @throws IllegalArgumentException if the value is out of range; specifically, if the total
     * number of seconds exceeds 2^63; or if any of the values is negative
     */
	public DayTimeDurationValue(int sign, int days, int hours, int minutes, long seconds, int microseconds)
		throws IllegalArgumentException {
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0 || microseconds < 0) {
            throw new IllegalArgumentException("Negative component value");
        }
        if (((double)days)*(24*60*60) + ((double)hours)*(60*60) +
                ((double)minutes)*60 + (double)seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration seconds limit exceeded");
        }
        negative = (sign < 0);
        months = 0;
        long h = (long)days * 24L + (long)hours;
        long m = h * 60L + (long)minutes;
        long s = m * 60L + seconds;
        if (microseconds > 1000000) {
            s += microseconds / 1000000;
            microseconds %= 1000000;
        }
        this.seconds = s;
        this.microseconds = microseconds;
        if (s == 0 && microseconds == 0) {
            negative = false;
        }
        typeLabel = BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        DayTimeDurationValue v = DayTimeDurationValue.fromMicroseconds(getLengthInMicroseconds());
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(32);
        if (negative) {
            sb.append('-');
        }

        int days = getDays();
        int hours = getHours();
        int minutes = getMinutes();
        int seconds = getSeconds();

        sb.append('P');
        if (days != 0) {
            sb.append(days + "D");
        }
        if (days == 0 || hours != 0 || minutes != 0 || seconds != 0 || microseconds != 0) {
            sb.append('T');
        }
        if (hours != 0) {
            sb.append(hours + "H");
        }
        if (minutes != 0) {
            sb.append(minutes + "M");
        }
        if (seconds != 0 || microseconds != 0 || (days == 0 && minutes == 0 && hours == 0)) {
            if (microseconds == 0) {
                sb.append(seconds + "S");
            } else {
                long ms = (seconds * 1000000l) + microseconds;
                String mss = Long.toString(ms);
                if (seconds == 0) {
                    mss = "0000000" + mss;
                    mss = mss.substring(mss.length() - 7);
                }
                sb.append(mss.substring(0, mss.length() - 6));
                sb.append('.');
                int lastSigDigit = mss.length() - 1;
                while (mss.charAt(lastSigDigit) == '0') {
                    lastSigDigit--;
                }
                sb.append(mss.substring(mss.length() - 6, lastSigDigit + 1));
                sb.append('S');
            }
        }
        return sb;
    }

    /**
     * Get length of duration in seconds
     */
    @Override
    public double getLengthInSeconds() {
		double a = seconds + ((double) microseconds / 1000000);
        return negative ? -a : a;
    }

    /**
     * Get length of duration in microseconds, as a long
     *
     * @return the length in microseconds
     */
    public long getLengthInMicroseconds() {
        long a = seconds * 1000000 + microseconds;
        return negative ? -a : a;
    }

    /**
     * Construct a duration value as a number of seconds.
     *
     * @param seconds the number of seconds in the duration. May be negative
     * @return the xs:dayTimeDuration value with the specified length
     */
    public static DayTimeDurationValue fromSeconds(BigDecimal seconds) {
        DayTimeDurationValue sdv = new DayTimeDurationValue();
        sdv.negative = (seconds.signum() < 0);
        if (sdv.negative) {
            seconds = seconds.negate();
        }
        BigDecimal microseconds = seconds.multiply(DecimalValue.BIG_DECIMAL_ONE_MILLION);
        BigInteger intMicros = microseconds.toBigInteger();
        BigInteger[] parts = intMicros.divideAndRemainder(BigInteger.valueOf(1000000));
        sdv.seconds = parts[0].longValue();
        sdv.microseconds = parts[1].intValue();
        return sdv;
    }

    /**
     * Construct a duration value as a number of milliseconds.
     *
     * @param milliseconds the number of milliseconds in the duration (may be negative)
     * @return the corresponding xs:dayTimeDuration value
     * @throws ValidationException if implementation-defined limits are exceeded, specifically
     * if the total number of seconds exceeds 2^63.
     */
    public static DayTimeDurationValue fromMilliseconds(long milliseconds) throws ValidationException {
        int sign = Long.signum(milliseconds);
        // Note JDK 1.5 dependency on Long.signum
        if (sign < 0) {
            milliseconds = -milliseconds;
        }
        try {
			return new DayTimeDurationValue(sign, 0, 0, 0, milliseconds / 1000, (int) (milliseconds % 1000) * 1000);
        } catch (IllegalArgumentException err) {
            // limits exceeded
        	LOGGER.error("Duration exceeds limits", err);
            throw new ValidationException("Duration exceeds limits");
        }
    }

    /**
     * Construct a duration value as a number of microseconds.
     *
     * @param microseconds the number of microseconds in the duration. The maximum and minimum
     *                     limits are such that the number of days in the duration must fit in a 32-bit signed integer.
     * @return the xs:dayTimeDuration represented by the given number of microseconds
     * @throws IllegalArgumentException if the value is out of range.
     */
    public static DayTimeDurationValue fromMicroseconds(long microseconds) throws IllegalArgumentException {
        int sign = Long.signum(microseconds);
        // Note JDK 1.5 dependency on Long.signum()
        if (sign < 0) {
            microseconds = -microseconds;
        }
		return new DayTimeDurationValue(sign, 0, 0, 0, microseconds / 1000000, (int) (microseconds % 1000000));
    }

    /**
     * Multiply duration by a number. This is also used when dividing a duration by a number.
     */
    @Override
    public DurationValue multiply(double n) throws XPathException {
        if (Double.isNaN(n)) {
            XPathException err = new XPathException("Cannot multiply/divide a duration by NaN");
            err.setErrorCode("FOCA0005");
            throw err;
        }
        double m = (double)getLengthInMicroseconds();
        double product = n * m;
		if (product > Long.MAX_VALUE || product < Long.MIN_VALUE || Double.isInfinite(product) ||
			Double.isNaN(product)) {
			XPathException err = new XPathException("Overflow when multiplying/dividing a duration by a number");
			err.setErrorCode("FODT0002");
			throw err;
		}
        try {
            return fromMicroseconds((long)product);
        } catch (IllegalArgumentException err) {
            if (err.getCause() instanceof XPathException) {
                throw (XPathException)err.getCause();
            } else {
                XPathException err2 = new XPathException("Overflow when multiplying/dividing a duration by a number", err);
                err2.setErrorCode("FODT0002");
                throw err2;
            }
        }
    }

    /**
     * Find the ratio between two durations
     *
     * @param other the dividend
     * @return the ratio, as a decimal
     * @throws XPathException
     */
    @Override
    public DecimalValue divide(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            BigDecimal v1 = BigDecimal.valueOf(getLengthInMicroseconds());
            BigDecimal v2 = BigDecimal.valueOf(((DayTimeDurationValue)other).getLengthInMicroseconds());
            if (v2.signum() == 0) {
                XPathException err = new XPathException("Divide by zero (durations)");
                err.setErrorCode("FOAR0001");
                throw err;
            }
            return new DecimalValue(v1.divide(v2, 20, BigDecimal.ROUND_HALF_EVEN));
        } else {
            XPathException err = new XPathException("Cannot divide two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Add two dayTimeDurations
     */
    @Override
    public DurationValue add(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            try {
                return fromMicroseconds(getLengthInMicroseconds() +
                        ((DayTimeDurationValue)other).getLengthInMicroseconds());
            } catch (IllegalArgumentException e) {
                XPathException err = new XPathException("Overflow when adding two durations");
                err.setErrorCode("FODT0002");
                LOGGER.error("Overflow when adding two durations", e);
                throw err;
            }
        } else {
            XPathException err = new XPathException("Cannot add two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Subtract two dayTime-durations
     */
    @Override
    public DurationValue subtract(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            try {
                return fromMicroseconds(getLengthInMicroseconds() -
                        ((DayTimeDurationValue)other).getLengthInMicroseconds());
            } catch (IllegalArgumentException e) {
                XPathException err = new XPathException("Overflow when subtracting two durations");
                err.setErrorCode("FODT0002");
                LOGGER.error("Overflow when subtracting two durations", e);
                throw err;
            }
        } else {
            XPathException err = new XPathException("Cannot subtract two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Negate a duration (same as subtracting from zero, but it preserves the type of the original duration)
     *
     * @throws IllegalArgumentException in the extremely unlikely event that the duration is one that cannot
     *          be negated (because the limit for positive durations is one second
     *          off from the limit for negative durations)
     */
    @Override
    public DurationValue negate() throws IllegalArgumentException {
		return fromMicroseconds(-getLengthInMicroseconds());
    }

    /**
     * Compare the value to another duration value
     *
     * @param other The other dateTime value
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be UTC values (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException if the other value is not a DateTimeValue (the parameter
     *                            is declared as Object to satisfy the Comparable interface)
     */
    @Override
    public int compareTo(Object other) {
        if (other instanceof DayTimeDurationValue) {
			long diff = getLengthInMicroseconds() - ((DayTimeDurationValue) other).getLengthInMicroseconds();
            if (diff < 0) {
                return -1;
            } else if (diff > 0) {
                return +1;
            } else {
                return 0;
            }
        } else {
			throw new ClassCastException("Cannot compare a dayTimeDuration to an object of class " + other.getClass());
        }
    }

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns the value itself. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     * @param ordered true if an ordered comparable is needed
     * @param collator Collation used for string comparison
     * @param context XPath dynamic context
     */
    @Override
    public Object getXPathComparable(boolean ordered, StringCollator collator, XPathContext context) {
        return this;
    }

}
