////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.ComparisonKey;
import net.sf.saxon.functions.Component;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static net.sf.saxon.expr.CastExpression.CAST_EX_CODE;

/**
 * A value of type DateTime
 */

public final class DateTimeValue extends CalendarValue implements Comparable {

    private int year;       // the year as written, +1 for BC years
    private byte month;     // the month as written, range 1-12
    private byte day;       // the day as written, range 1-31
    private byte hour;      // the hour as written (except for midnight), range 0-23
    private byte minute;   // the minutes as written, range 0-59
    private byte second;   // the seconds as written, range 0-59 (no leap seconds)
    private int microsecond;
    private boolean xsd10rules;  // true if XSD 1.0 rules apply for negative years

    /**
     * Private default constructor
     */

    private DateTimeValue() {
    }

    /**
     * Get the dateTime value representing the nominal
     * date/time of this transformation run. Two calls within the same
     * query or transformation will always return the same answer.
     *
     * @param context the XPath dynamic context. May be null, in which case
     * the current date and time are taken directly from the system clock
     * @return the current xs:dateTime
     */

    /*@Nullable*/ public static DateTimeValue getCurrentDateTime(/*@Nullable*/ XPathContext context) {
        Controller c;
        if (context == null || (c = context.getController()) == null) {
            // non-XSLT/XQuery environment
            // We also take this path when evaluating compile-time expressions that require an implicit timezone.
            return new DateTimeValue(new GregorianCalendar(), true);
        } else {
            return c.getCurrentDateTime();
        }
    }

    /**
     * Constructor: create a dateTime value given a Java calendar object
     *
     * @param calendar    holds the date and time
     * @param tzSpecified indicates whether the timezone is specified
     */

    public DateTimeValue(/*@NotNull*/ Calendar calendar, boolean tzSpecified) {
        int era = calendar.get(GregorianCalendar.ERA);
        year = calendar.get(Calendar.YEAR);
        if (era == GregorianCalendar.BC) {
            year = 1 - year;
        }
        month = (byte)(calendar.get(Calendar.MONTH) + 1);
        day = (byte)(calendar.get(Calendar.DATE));
        hour = (byte)(calendar.get(Calendar.HOUR_OF_DAY));
        minute = (byte)(calendar.get(Calendar.MINUTE));
        second = (byte)(calendar.get(Calendar.SECOND));
        microsecond = calendar.get(Calendar.MILLISECOND) * 1000;
        if (tzSpecified) {
            int tz = (calendar.get(Calendar.ZONE_OFFSET) +
                    calendar.get(Calendar.DST_OFFSET)) / 60000;
            setTimezoneInMinutes(tz);
        }
        typeLabel = BuiltInAtomicType.DATE_TIME;
        xsd10rules = true;
    }

    /**
     * Factory method: create a dateTime value given a Java Date object. The returned dateTime
     * value will always have a timezone, which will always be UTC.
     *
     * @param suppliedDate holds the date and time
     * @return the corresponding xs:dateTime value
     */

    /*@NotNull*/ public static DateTimeValue fromJavaDate(/*@NotNull*/ Date suppliedDate) throws XPathException {
        long millis = suppliedDate.getTime();
        return (DateTimeValue)EPOCH.add(DayTimeDurationValue.fromMilliseconds(millis));
    }

    /**
     * Fixed date/time used by Java (and Unix) as the origin of the universe: 1970-01-01
     */

    /*@NotNull*/ public static final DateTimeValue EPOCH =
            new DateTimeValue(1970, (byte)1, (byte)1, (byte)0, (byte)0, (byte)0, 0, 0, true);

    /**
     * Factory method: create a dateTime value given a date and a time.
     *
     * @param date the date
     * @param time the time
     * @return the dateTime with the given components. If either component is null, returns null
     * @throws XPathException if the timezones are both present and inconsistent
     */

    /*@Nullable*/ public static DateTimeValue makeDateTimeValue(/*@Nullable*/ DateValue date, /*@Nullable*/ TimeValue time) throws XPathException {
        if (date == null || time == null) {
            return null;
        }
        int tz1 = date.getTimezoneInMinutes();
        int tz2 = time.getTimezoneInMinutes();
        if (tz1 != NO_TIMEZONE && tz2 != NO_TIMEZONE && tz1 != tz2) {
            XPathException err = new XPathException("Supplied date and time are in different timezones");
            err.setErrorCode("FORG0008");
            throw err;
        }

        DateTimeValue v = date.toDateTime();
        v.hour = time.getHour();
        v.minute = time.getMinute();
        v.second = time.getSecond();
        v.microsecond = time.getMicrosecond();
        v.setTimezoneInMinutes(Math.max(tz1, tz2));
        v.typeLabel = BuiltInAtomicType.DATE_TIME;
        v.xsd10rules = date.xsd10rules;
        return v;
    }

    /**
     * Factory method: create a dateTime value from a supplied string, in
     * ISO 8601 format
     *
     * @param s a string in the lexical space of xs:dateTime
     * @param rules the conversion rules to be used (determining whether year zero is allowed)
     * @return either a DateTimeValue representing the xs:dateTime supplied, or a ValidationFailure if
     *         the lexical value was invalid
     */

    /*@NotNull*/ public static ConversionResult makeDateTimeValue(CharSequence s, /*@NotNull*/ ConversionRules rules) {
        // input must have format [-]yyyy-mm-ddThh:mm:ss[.fff*][([+|-]hh:mm | Z)]
        DateTimeValue dt = new DateTimeValue();
        dt.xsd10rules = !rules.isAllowYearZero();
        StringTokenizer tok = new StringTokenizer(Whitespace.trimWhitespace(s).toString(), "-:.+TZ", true);

        if (!tok.hasMoreElements()) {
            return badDate("too short", s);
        }
        String part = (String)tok.nextElement();
        int era = +1;
        if ("+".equals(part)) {
            return badDate("Date must not start with '+' sign", s);
        } else if ("-".equals(part)) {
            era = -1;
            if (!tok.hasMoreElements()) {
                return badDate("No year after '-'", s);
            }
            part = (String)tok.nextElement();
        }
        int value = DurationValue.simpleInteger(part);
        if (value < 0) {
            if (value == -1) {
                return badDate("Non-numeric year component", s);
            } else {
                return badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
            }
        }
        dt.year = value * era;
        if (part.length() < 4) {
            return badDate("Year is less than four digits", s);
        }
        if (part.length() > 4 && part.charAt(0) == '0') {
            return badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
        }
        if (dt.year == 0 && !rules.isAllowYearZero()) {
            return badDate("Year zero is not allowed", s);
        }
        if (era < 0 && !rules.isAllowYearZero()) {
            dt.year++;     // if year zero not allowed, -0001 is the year before +0001, represented as 0 internally.
        }
        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        if (!"-".equals(tok.nextElement())) {
            return badDate("Wrong delimiter after year", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        part = (String)tok.nextElement();
        if (part.length() != 2) {
            return badDate("Month must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric month component", s);
        }
        dt.month = (byte)value;
        if (dt.month < 1 || dt.month > 12) {
            return badDate("Month is out of range", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        if (!"-".equals(tok.nextElement())) {
            return badDate("Wrong delimiter after month", s);
        }
        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        part = (String)tok.nextElement();
        if (part.length() != 2) {
            return badDate("Day must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric day component", s);
        }
        dt.day = (byte)value;
        if (dt.day < 1 || dt.day > 31) {
            return badDate("Day is out of range", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        if (!"T".equals(tok.nextElement())) {
            return badDate("Wrong delimiter after day", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        part = (String)tok.nextElement();
        if (part.length() != 2) {
            return badDate("Hour must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric hour component", s);
        }
        dt.hour = (byte)value;
        if (dt.hour > 24) {
            return badDate("Hour is out of range", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextElement())) {
            return badDate("Wrong delimiter after hour", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        part = (String)tok.nextElement();
        if (part.length() != 2) {
            return badDate("Minute must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric minute component", s);
        }
        dt.minute = (byte)value;
        if (dt.minute > 59) {
            return badDate("Minute is out of range", s);
        }
        if (dt.hour == 24 && dt.minute != 0) {
            return badDate("If hour is 24, minute must be 00", s);
        }
        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextElement())) {
            return badDate("Wrong delimiter after minute", s);
        }

        if (!tok.hasMoreElements()) {
            return badDate("Too short", s);
        }
        part = (String)tok.nextElement();
        if (part.length() != 2) {
            return badDate("Second must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric second component", s);
        }
        dt.second = (byte)value;

        if (dt.second > 59) {
            return badDate("Second is out of range", s);
        }
        if (dt.hour == 24 && dt.second != 0) {
            return badDate("If hour is 24, second must be 00", s);
        }

        int tz = 0;

        int state = 0;
        while (tok.hasMoreElements()) {
            if (state == 9) {
                return badDate("Characters after the end", s);
            }
            String delim = (String)tok.nextElement();
            if (".".equals(delim)) {
                if (state != 0) {
                    return badDate("Decimal separator occurs twice", s);
                }
                if (!tok.hasMoreElements()) {
                    return badDate("Decimal point must be followed by digits", s);
                }
                part = (String)tok.nextElement();
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric fractional seconds component", s);
                }
                double fractionalSeconds = Double.parseDouble('.' + part);
                dt.microsecond = (int)(Math.round(fractionalSeconds * 1000000));
                if (dt.hour == 24 && dt.microsecond != 0) {
                    return badDate("If hour is 24, fractional seconds must be 0", s);
                }
                state = 1;
            } else if ("Z".equals(delim)) {
                if (state > 1) {
                    return badDate("Z cannot occur here", s);
                }
                tz = 0;
                state = 9;  // we've finished
                dt.setTimezoneInMinutes(0);
            } else if ("+".equals(delim) || "-".equals(delim)) {
                if (state > 1) {
                    return badDate(delim + " cannot occur here", s);
                }
                state = 2;
                if (!tok.hasMoreElements()) {
                    return badDate("Missing timezone", s);
                }
                part = (String)tok.nextElement();
                if (part.length() != 2) {
                    return badDate("Timezone hour must be two digits", s);
                }
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric timezone hour component", s);
                }
                tz = value;
                if (tz > 14) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                tz *= 60;

                if ("-".equals(delim)) {
                    tz = -tz;
                }

            } else if (":".equals(delim)) {
                if (state != 2) {
                    return badDate("Misplaced ':'", s);
                }
                state = 9;
                part = (String)tok.nextElement();
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric timezone minute component", s);
                }
                int tzminute = value;
                if (part.length() != 2) {
                    return badDate("Timezone minute must be two digits", s);
                }
                if (tzminute > 59) {
                    return badDate("Timezone minute is out of range", s);
                }
                if (tz < 0) {
                    tzminute = -tzminute;
                }
                if (Math.abs(tz) == 14 * 60 && tzminute != 0) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                tz += tzminute;
                dt.setTimezoneInMinutes(tz);
            } else {
                return badDate("Timezone format is incorrect", s);
            }
        }

        if (state == 2 || state == 3) {
            return badDate("Timezone incomplete", s);
        }

        boolean midnight = false;
        if (dt.hour == 24) {
            dt.hour = 0;
            midnight = true;
        }

        // Check that this is a valid calendar date
        if (!DateValue.isValidDate(dt.year, dt.month, dt.day)) {
            return badDate("Non-existent date", s);
        }

        // Adjust midnight to 00:00:00 on the next day
        if (midnight) {
            DateValue t = DateValue.tomorrow(dt.year, dt.month, dt.day);
            dt.year = t.getYear();
            dt.month = t.getMonth();
            dt.day = t.getDay();
        }


        dt.typeLabel = BuiltInAtomicType.DATE_TIME;
        return dt;
    }

    private static ValidationFailure badDate(String msg, CharSequence value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode(CAST_EX_CODE);
        return err;
    }

    private static ValidationFailure badDate(String msg, CharSequence value, String errorCode) {
        ValidationFailure err = new ValidationFailure(
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode(errorCode);
        return err;
    }

    /**
     * Constructor: construct a DateTimeValue from its components.
     * This constructor performs no validation.
     *
     * @param year        The year as held internally (note that the year before 1AD is 0)
     * @param month       The month, 1-12
     * @param day         The day 1-31
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param microsecond the number of microseconds, 0-999999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param xsd10Check  true if the dateTime value should behave under XSD 1.0 rules, that is,
     *                    negative dates assume there is no year zero. (Not that regardless of this
     *                    setting, the year argument is set on the basis that the year before +1 is
     *                    supplied as zero; but if the xsd10Check flag is set, this value will be displayed
     *                    with a year of -1.)
     */

    public DateTimeValue(int year, byte month, byte day,
                         byte hour, byte minute, byte second, int microsecond, int tz, boolean xsd10Check) {
        
        this.xsd10rules = xsd10Check;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.microsecond = microsecond;
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.DATE_TIME;
    }

    /**
     * Convert the value to a built-in subtype of xs:dateTime
     * @param subtype the target subtype
     * @return null if the conversion succeeds; a ValidationFailure describing the failure if it fails.
     */

    /*@Nullable*/ public ValidationFailure convertToSubType(/*@NotNull*/ BuiltInAtomicType subtype) {
        if (subtype.getFingerprint() == StandardNames.XS_DATE_TIME_STAMP) {
            if (hasTimezone()) {
                setTypeLabel(subtype);
                return null;
            } else {
                ValidationFailure err = new ValidationFailure(
                        "The value " + Err.depict(this) +
                                " is not a valid xs:dateTimeStamp: it has no timezone");
                err.setErrorCode(CAST_EX_CODE);
                return err;
            }
        } else {
            throw new IllegalArgumentException("Unknown subtype of xs:dateTime");
        }
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/ public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DATE_TIME;
    }

    /**
     * Get the year component, in its internal form (which allows a year zero)
     *
     * @return the year component
     */

    public int getYear() {
        return year;
    }

    /**
     * Get the month component, 1-12
     *
     * @return the month component
     */

    public byte getMonth() {
        return month;
    }

    /**
     * Get the day component, 1-31
     *
     * @return the day component
     */

    public byte getDay() {
        return day;
    }

    /**
     * Get the hour component, 0-23
     *
     * @return the hour component (never 24, even if the input was specified as 24:00:00)
     */

    public byte getHour() {
        return hour;
    }

    /**
     * Get the minute component, 0-59
     *
     * @return the minute component
     */

    public byte getMinute() {
        return minute;
    }

    /**
     * Get the second component, 0-59
     *
     * @return the second component
     */

    public byte getSecond() {
        return second;
    }

    /**
     * Get the microsecond component, 0-999999
     *
     * @return the microsecond component
     */

    public int getMicrosecond() {
        return microsecond;
    }

    /**
     * Convert the value to a DateTime, retaining all the components that are actually present, and
     * substituting conventional values for components that are missing. (This method does nothing in
     * the case of xs:dateTime, but is there to implement a method in the {@link CalendarValue} interface).
     *
     * @return the value as an xs:dateTime
     */

    /*@NotNull*/ public DateTimeValue toDateTime() {
        return this;
    }

    /**
     * Ask whether this value uses the XSD 1.0 rules (which don't allow year zero) or the XSD 1.1 rules (which do).
     * @return true if the value uses the XSD 1.0 rules
     */

    public boolean isXsd10Rules() {
        return xsd10rules;
    }

    /**
     * Normalize the date and time to be in timezone Z.
     *
     * @param cc used to supply the implicit timezone, used when the value has
     *           no explicit timezone
     * @return in general, a new DateTimeValue in timezone Z, representing the same instant in time.
     *         Returns the original DateTimeValue if this is already in timezone Z.
     * @throws NoDynamicContextException if the implicit timezone is needed and is not available
     */

    /*@NotNull*/ public DateTimeValue normalize(/*@Nullable*/ XPathContext cc) throws NoDynamicContextException {
        if (hasTimezone()) {
            return (DateTimeValue)adjustTimezone(0);
        } else {
            if (cc == null){
                throw new NoDynamicContextException("DateTime operation needs access to implicit timezone");
            }
            DateTimeValue dt = (DateTimeValue)copyAsSubType(null);
            dt.setTimezoneInMinutes(cc.getImplicitTimezone());
            return (DateTimeValue)dt.adjustTimezone(0);
        }
    }

    /**
     * Get a comparison key for this value. Two values are equal if and only if they their comparison
     * keys are equal
     * @param context XPath dynamic context
     * @throws NoDynamicContextException if the implicit timezone is needed and is not available
     */

    /*@NotNull*/ public ComparisonKey getComparisonKey(/*@NotNull*/ XPathContext context) throws NoDynamicContextException {
        return new ComparisonKey(StandardNames.XS_DATE_TIME, normalize(context));
    }

    /**
     * Get the Julian instant: a decimal value whose integer part is the Julian day number
     * multiplied by the number of seconds per day,
     * and whose fractional part is the fraction of the second.
     * This method operates on the local time, ignoring the timezone. The caller should call normalize()
     * before calling this method to get a normalized time.
     *
     * @return the Julian instant corresponding to this xs:dateTime value
     */

    public BigDecimal toJulianInstant() {
        int julianDay = DateValue.getJulianDayNumber(year, month, day);
        long julianSecond = julianDay * (24L * 60L * 60L);
        julianSecond += (((hour * 60L + minute) * 60L) + second);
        BigDecimal j = BigDecimal.valueOf(julianSecond);
        if (microsecond == 0) {
            return j;
        } else {
            return j.add(BigDecimal.valueOf(microsecond).divide(DecimalValue.BIG_DECIMAL_ONE_MILLION, 6, BigDecimal.ROUND_HALF_EVEN));
        }
    }

    /**
     * Get the DateTimeValue corresponding to a given Julian instant
     *
     * @param instant the Julian instant: a decimal value whose integer part is the Julian day number
     *                multiplied by the number of seconds per day, and whose fractional part is the fraction of the second.
     * @return the xs:dateTime value corresponding to the Julian instant. This will always be in timezone Z.
     */

    /*@NotNull*/ public static DateTimeValue fromJulianInstant(/*@NotNull*/ BigDecimal instant) {
        BigInteger julianSecond = instant.toBigInteger();
        BigDecimal microseconds = instant.subtract(new BigDecimal(julianSecond)).multiply(DecimalValue.BIG_DECIMAL_ONE_MILLION);
        long js = julianSecond.longValue();
        long jd = js / (24L * 60L * 60L);
        DateValue date = DateValue.dateFromJulianDayNumber((int)jd);
        js = js % (24L * 60L * 60L);
        byte hour = (byte)(js / (60L * 60L));
        js = js % (60L * 60L);
        byte minute = (byte)(js / (60L));
        js = js % (60L);
        return new DateTimeValue(date.getYear(), date.getMonth(), date.getDay(),
                hour, minute, (byte)js, microseconds.intValue(),0 , true);
    }

    /**
     * Get a Java Calendar object representing the value of this DateTime. This will respect the timezone
     * if there is one, or be in GMT otherwise.
     *
     * @return a Java GregorianCalendar object representing the value of this xs:dateTime value.
     */

    /*@NotNull*/ public GregorianCalendar getCalendar() {
        int tz = (hasTimezone() ? getTimezoneInMinutes() * 60000 : 0);
        TimeZone zone = new SimpleTimeZone(tz, "LLL");
        GregorianCalendar calendar = new GregorianCalendar(zone);
        calendar.setGregorianChange(new Date(Long.MIN_VALUE));
        calendar.setLenient(false);
        int yr = year;
        if (year <= 0) {
            yr = (xsd10rules ? 1 - year : 0 - year);
            calendar.set(Calendar.ERA, GregorianCalendar.BC);
        }
        calendar.set(yr, month - 1, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, microsecond / 1000);   // loses precision unavoidably
        calendar.set(Calendar.ZONE_OFFSET, tz);
//        Note JDK 1.5 dependency. JDK 1.4 limited the range to +|- 12 hours, so the code was:
//        if (tz >= -12*60*60*1000 && tz <= +12*60*60*1000) {
//            calendar.set(Calendar.ZONE_OFFSET, tz);
//        }
        calendar.set(Calendar.DST_OFFSET, 0);
        return calendar;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation. The value returned is the localized representation,
     *         that is it uses the timezone contained within the value itself.
     */

    /*@NotNull*/ public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(30);
        int yr = year;
        if (year <= 0) {
            yr = -yr + (xsd10rules ? 1 : 0);    // no year zero in lexical space for XSD 1.0
            if(yr!=0){
                sb.append('-');
            }
        }
        appendString(sb, yr, (yr > 9999 ? (Integer.toString(yr)).length() : 4));
        sb.append('-');
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);
        sb.append('T');
        appendTwoDigits(sb, hour);
        sb.append(':');
        appendTwoDigits(sb, minute);
        sb.append(':');
        appendTwoDigits(sb, second);
        if (microsecond != 0) {
            sb.append('.');
            int ms = microsecond;
            int div = 100000;
            while (ms > 0) {
                int d = ms / div;
                sb.append((char)(d + '0'));
                ms = ms % div;
                div /= 10;
            }
        }

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb;

    }

    /**
     * Extract the Date part
     * @return a DateValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/ public DateValue toDateValue() {
        DateValue dv = new DateValue(year, month, day, getTimezoneInMinutes(),xsd10rules);
        return dv;
    }

    /**
     * Extract the Time part
     * @return a TimeValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/ public TimeValue toTimeValue() {
        return new TimeValue(hour, minute, second, microsecond, getTimezoneInMinutes());
    }


    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For an xs:dateTime it is the
     * date/time adjusted to UTC.
     *
     * @return the canonical lexical representation as defined in XML Schema
     */

    public CharSequence getCanonicalLexicalRepresentation() {
        if (hasTimezone() && getTimezoneInMinutes() != 0) {
            return adjustTimezone(0).getStringValueCS();
        } else {
            return getStringValueCS();
        }
    }

    /**
     * Make a copy of this date, time, or dateTime value, but with a new type label
     *
     * @param typeLabel the type label to be attached to the new copy. It is the caller's responsibility
     *                  to ensure that the value actually conforms to the rules for this type.
     */

    /*@NotNull*/ public AtomicValue copyAsSubType(AtomicType typeLabel) {
        DateTimeValue v = new DateTimeValue(year, month, day,
                hour, minute, second, microsecond, getTimezoneInMinutes(),xsd10rules);
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Return a new dateTime with the same normalized value, but
     * in a different timezone.
     *
     * @param timezone the new timezone offset, in minutes
     * @return the date/time in the new timezone. This will be a new DateTimeValue unless no change
     *         was required to the original value
     */

    /*@NotNull*/ public CalendarValue adjustTimezone(int timezone) {
        if (!hasTimezone()) {
            CalendarValue in = (CalendarValue)copyAsSubType(typeLabel);
            in.setTimezoneInMinutes(timezone);
            return in;
        }
        int oldtz = getTimezoneInMinutes();
        if (oldtz == timezone) {
            return this;
        }
        int tz = timezone - oldtz;
        int h = hour;
        int mi = minute;
        mi += tz;
        if (mi < 0 || mi > 59) {
            h += Math.floor(mi / 60.0);
            mi = (mi + 60 * 24) % 60;
        }

        if (h >= 0 && h < 24) {
            return new DateTimeValue(year, month, day, (byte)h, (byte)mi, second, microsecond, timezone,xsd10rules);
        }

        // Following code is designed to handle the corner case of adjusting from -14:00 to +14:00 or
        // vice versa, which can cause a change of two days in the date
        DateTimeValue dt = this;
        while (h < 0) {
            h += 24;
            DateValue t = DateValue.yesterday(dt.getYear(), dt.getMonth(), dt.getDay());
            dt = new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                    (byte)h, (byte)mi, second, microsecond, timezone,xsd10rules);
        }
        if (h > 23) {
            h -= 24;
            DateValue t = DateValue.tomorrow(year, month, day);
            return new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                    (byte)h, (byte)mi, second, microsecond, timezone,xsd10rules);
        }
        return dt;
    }

    /**
     * Add a duration to a dateTime
     *
     * @param duration the duration to be added (may be negative)
     * @return the new date
     * @throws net.sf.saxon.trans.XPathException
     *          if the duration is an xs:duration, as distinct from
     *          a subclass thereof
     */

    /*@NotNull*/ public CalendarValue add(/*@NotNull*/ DurationValue duration) throws XPathException {
        if (duration instanceof DayTimeDurationValue) {
            long microseconds = ((DayTimeDurationValue)duration).getLengthInMicroseconds();
            BigDecimal seconds = BigDecimal.valueOf(microseconds).divide(
                    DecimalValue.BIG_DECIMAL_ONE_MILLION, 6, BigDecimal.ROUND_HALF_EVEN);
            BigDecimal julian = toJulianInstant();
            julian = julian.add(seconds);
            DateTimeValue dt = fromJulianInstant(julian);
            dt.setTimezoneInMinutes(getTimezoneInMinutes());
            dt.xsd10rules = this.xsd10rules;
            return dt;
        } else if (duration instanceof YearMonthDurationValue) {
            int months = ((YearMonthDurationValue)duration).getLengthInMonths();
            int m = (month - 1) + months;
            int y = year + m / 12;
            m = m % 12;
            if (m < 0) {
                m += 12;
                y -= 1;
            }
            m++;
            int d = day;
            while (!DateValue.isValidDate(y, m, d)) {
                d -= 1;
            }
            return  new DateTimeValue(y, (byte)m, (byte)d,
                    hour, minute, second, microsecond, getTimezoneInMinutes(),xsd10rules);
        } else {
            XPathException err = new XPathException("DateTime arithmetic is not supported on xs:duration, only on its subtypes");
            err.setIsTypeError(true);
            throw err;
        }
    }

    /**
     * Determine the difference between two points in time, as a duration
     *
     * @param other   the other point in time
     * @param context the XPath dynamic context
     * @return the duration as an xs:dayTimeDuration
     * @throws net.sf.saxon.trans.XPathException
     *          for example if one value is a date and the other is a time
     */

    public DayTimeDurationValue subtract(/*@NotNull*/ CalendarValue other, XPathContext context) throws XPathException {
        if (!(other instanceof DateTimeValue)) {
            XPathException err = new XPathException("First operand of '-' is a dateTime, but the second is not");
            err.setIsTypeError(true);
            throw err;
        }
        return super.subtract(other, context);
    }

    /**
     * Convert to Java object (for passing to external functions)
     */

//    public Object convertAtomicToJava(Class target, XPathContext context) throws XPathException {
//        if (target.isAssignableFrom(Date.class)) {
//            return getCalendar().getTime();
//        } else if (target.isAssignableFrom(GregorianCalendar.class)) {
//            return getCalendar();
//        } else if (target.isAssignableFrom(DateTimeValue.class)) {
//            return this;
//        } else if (target == Object.class) {
//            return getStringValue();
//        } else {
//            Object o = super.convertSequenceToJava(target, context);
//            if (o == null) {
//                throw new XPathException("Conversion of dateTime to " + target.getName() +
//                        " is not supported");
//            }
//            return o;
//        }
//    }
//
    /**
     * Get a component of the value. Returns null if the timezone component is
     * requested and is not present.
     */

    /*@Nullable*/ public AtomicValue getComponent(int component) throws XPathException {
        switch (component) {
        case Component.YEAR_ALLOWING_ZERO:
            return Int64Value.makeIntegerValue(year);
        case Component.YEAR:
            return Int64Value.makeIntegerValue(year > 0 ? year : year - 1);
        case Component.MONTH:
            return Int64Value.makeIntegerValue(month);
        case Component.DAY:
            return Int64Value.makeIntegerValue(day);
        case Component.HOURS:
            return Int64Value.makeIntegerValue(hour);
        case Component.MINUTES:
            return Int64Value.makeIntegerValue(minute);
        case Component.SECONDS:
            BigDecimal d = BigDecimal.valueOf(microsecond);
            d = d.divide(DecimalValue.BIG_DECIMAL_ONE_MILLION, 6, BigDecimal.ROUND_HALF_UP);
            d = d.add(BigDecimal.valueOf(second));
            return new DecimalValue(d);
        case Component.WHOLE_SECONDS: //(internal use only)
            return Int64Value.makeIntegerValue(second);
        case Component.MICROSECONDS:
            // internal use only
            return new Int64Value(microsecond);
        case Component.TIMEZONE:
            if (hasTimezone()) {
                return DayTimeDurationValue.fromMilliseconds(60000L * getTimezoneInMinutes());
            } else {
                return null;
            }
        default:
            throw new IllegalArgumentException("Unknown component for dateTime: " + component);
        }
    }

    /**
     * Compare the value to another dateTime value, following the XPath comparison semantics
     *
     * @param other  The other dateTime value
     * @param context XPath dynamic evaluation context
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be values in the implicit timezone (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException if the other value is not a DateTimeValue (the parameter
     *                            is declared as CalendarValue to satisfy the interface)
     * @throws NoDynamicContextException if the implicit timezone is needed and is not available
     */

    public int compareTo(/*@NotNull*/ CalendarValue other, /*@Nullable*/ XPathContext context) throws NoDynamicContextException {
        if (!(other instanceof DateTimeValue)) {
            throw new ClassCastException("DateTime values are not comparable to " + other.getClass());
        }
        DateTimeValue v2 = (DateTimeValue)other;
        if (getTimezoneInMinutes() == v2.getTimezoneInMinutes()) {
            // both values are in the same timezone (explicitly or implicitly)
            if (year != v2.year) {
                return IntegerValue.signum(year - v2.year);
            }
            if (month != v2.month) {
                return IntegerValue.signum(month - v2.month);
            }
            if (day != v2.day) {
                return IntegerValue.signum(day - v2.day);
            }
            if (hour != v2.hour) {
                return IntegerValue.signum(hour - v2.hour);
            }
            if (minute != v2.minute) {
                return IntegerValue.signum(minute - v2.minute);
            }
            if (second != v2.second) {
                return IntegerValue.signum(second - v2.second);
            }
            if (microsecond != v2.microsecond) {
                return IntegerValue.signum(microsecond - v2.microsecond);
            }
            return 0;
        }
        return normalize(context).compareTo(v2.normalize(context), context);
    }

    /**
     * Context-free comparison of two DateTimeValue values. For this to work,
     * the two values must either both have a timezone or both have none.
     * @param v2 the other value
     * @return the result of the comparison: -1 if the first is earlier, 0 if they
     * are equal, +1 if the first is later
     * @throws ClassCastException if the values are not comparable (which might be because
     * no timezone is available)
     */

    public int compareTo(Object v2) {
        try {
            return compareTo((DateTimeValue)v2, null);
        } catch (Exception err) {
            throw new ClassCastException("DateTime comparison requires access to implicit timezone");
        }
    }

    /*@NotNull*/ public Comparable getSchemaComparable() {
        return new DateTimeComparable();
    }

    /**
     * DateTimeComparable is an object that implements the XML Schema rules for comparing date/time values
     */

    private class DateTimeComparable implements Comparable {

        /*@NotNull*/ private DateTimeValue asDateTimeValue() {
            return DateTimeValue.this;
        }

        // Rules from XML Schema Part 2
        public int compareTo(/*@NotNull*/ Object o) {
            if (o instanceof DateTimeComparable) {
                DateTimeValue dt0 = DateTimeValue.this;
                DateTimeValue dt1 = ((DateTimeComparable)o).asDateTimeValue();
                if (dt0.hasTimezone()) {
                    if (dt1.hasTimezone()) {
                        dt0 = (DateTimeValue)dt0.adjustTimezone(0);
                        dt1 = (DateTimeValue)dt1.adjustTimezone(0);
                        return dt0.compareTo(dt1);
                    } else {
                        DateTimeValue dt1max = (DateTimeValue)dt1.adjustTimezone(14*60);
                        if (dt0.compareTo(dt1max) < 0) {
                            return -1;
                        }
                        DateTimeValue dt1min = (DateTimeValue)dt1.adjustTimezone(-14*60);
                        if (dt0.compareTo(dt1min) > 0) {
                            return +1;
                        }
                        return SequenceTool.INDETERMINATE_ORDERING;
                    }
                } else {
                    if (dt1.hasTimezone()) {
                        DateTimeValue dt0min = (DateTimeValue)dt0.adjustTimezone(-14*60);
                        if (dt0min.compareTo(dt1) < 0) {
                            return -1;
                        }
                        DateTimeValue dt0max = (DateTimeValue)dt0.adjustTimezone(14*60);
                        if (dt0max.compareTo(dt1) > 0) {
                            return +1;
                        }
                        return SequenceTool.INDETERMINATE_ORDERING;
                    } else {
                        dt0 = (DateTimeValue)dt0.adjustTimezone(0);
                        dt1 = (DateTimeValue)dt1.adjustTimezone(0);
                        return dt0.compareTo(dt1);
                    }
                }

            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return o instanceof DateTimeComparable &&
                    DateTimeValue.this.hasTimezone() == ((DateTimeComparable)o).asDateTimeValue().hasTimezone() &&
                    compareTo(o) == 0;
        }
        
        public int hashCode() {
            DateTimeValue dt0 = (DateTimeValue)adjustTimezone(0);
            return (dt0.year<<20) ^ (dt0.month<<16) ^ (dt0.day<<11) ^
                    (dt0.hour<<7) ^ (dt0.minute<<2) ^ (dt0.second*1000000 + dt0.microsecond);
        }
    }

    /**
     * Context-free comparison of two dateTime values
     * @param o the other date time value
     * @return true if the two values represent the same instant in time
     * @throws ClassCastException if one of the values has a timezone and the other does not
     */

    public boolean equals(Object o) {
        //noinspection RedundantCast
        return compareTo((DateTimeValue)o) == 0;
    }

    /**
     * Hash code for context-free comparison of date time values. Note that equality testing
     * and therefore hashCode() works only for values with a timezone
     * @return  a hash code
     */

    public int hashCode() {
        return hashCode(year, month, day, hour, minute, second, microsecond, getTimezoneInMinutes());
    }

    static int hashCode(int year, byte month, byte day, byte hour, byte minute, byte second, int microsecond, int tzMinutes) {
        int tz = -tzMinutes;
        int h = hour;
        int mi = minute;
        mi += tz;
        if (mi < 0 || mi > 59) {
            h += Math.floor(mi / 60.0);
            mi = (mi + 60 * 24) % 60;
        }
        while (h < 0) {
            h += 24;
            DateValue t = DateValue.yesterday(year, month, day);
            year = t.getYear();
            month = t.getMonth();
            day = t.getDay();
        }
        while (h > 23) {
            h -= 24;
            DateValue t = DateValue.tomorrow(year, month, day);
            year = t.getYear();
            month = t.getMonth();
            day = t.getDay();
        }
        return (year<<4) ^ (month<<28) ^ (day<<23) ^ (h<<18) ^ (mi<<13) ^ second ^ microsecond;

    }

}

