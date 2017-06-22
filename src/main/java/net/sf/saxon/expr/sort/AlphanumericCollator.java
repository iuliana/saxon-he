////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Comparer that treats strings as an alternating sequence of alpha parts and numeric parts. The
 * alpha parts are compared using a base collation supplied as a parameter; the numeric parts are
 * compared numerically. "Numeric" here means a sequence of consecutive ASCII digits 0-9.
 * <p>
 * Note: this StringCollator produces an ordering that is not compatible with equals().
 * </p>
 */

public class AlphanumericCollator implements StringCollator, java.io.Serializable {

    private StringCollator baseCollator;
    private static Pattern pattern = Pattern.compile("\\d+");

    /**
     * Create an alphanumeric collation
     * @param base the collation used to compare the alphabetic parts of the string
     */

    public AlphanumericCollator(StringCollator base) {
        baseCollator = base;
    }

    /**
    * Compare two objects.
    * @return <0 if a<b, 0 if a=b, >0 if a>b
    */

    public int compareStrings(String s1, String s2) {
        int pos1 = 0;
        int pos2 = 0;
        Matcher m1 = pattern.matcher(s1);
        Matcher m2 = pattern.matcher(s2);
        while (true) {

            // find the next number in each string

            boolean b1 = m1.find(pos1);
            boolean b2 = m2.find(pos2);
            int m1start = (b1 ? m1.start() : s1.length());
            int m2start = (b2 ? m2.start() : s2.length());

            // compare an alphabetic pair (even if zero-length)

            int c = baseCollator.compareStrings(s1.substring(pos1, m1start), s2.substring(pos2, m2start));
            if (c != 0) {
                return c;
            }

            // if one match found a number and the other didn't, exit accordingly

            if (b1 && !b2) {
                return +1;
            } else if (b2 && !b1) {
                return -1;
            } else if (!b1 && !b2) {
                return 0;
            }

            // a number was found in each of the strings: compare the numbers

            BigInteger n1 = new BigInteger(s1.substring(m1start, m1.end()));
            BigInteger n2 = new BigInteger(s2.substring(m2start, m2.end()));
            c = n1.compareTo(n2);
            if (c != 0) {
                return c;
            }

            // the numbers are equal: move on to the next part of the string

            pos1 = m1.end();
            pos2 = m2.end();
        }
    }

    /**
     * Compare two strings for equality. This may be more efficient than using compareStrings and
     * testing whether the result is zero, but it must give the same result
     * @param s1 the first string
     * @param s2 the second string
     * @return true if and only if the strings are considered equal,
     */

    public boolean comparesEqual(String s1, String s2) {
        return compareStrings(s1, s2) == 0;
    }

    /**
     * Get a collation key for two Strings. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     */

    public Object getCollationKey(/*@NotNull*/ String s) {
        // The string is normalized by removing leading zeros in a numeric component
        FastStringBuffer sb = new FastStringBuffer(s.length()*2);
        int pos1 = 0;
        Matcher m1 = pattern.matcher(s);
        while (true) {

            // find the next number in the string

            boolean b1 = m1.find(pos1);
            int m1start = (b1 ? m1.start() : s.length());

            // handle an alphabetic part (even if zero-length)

            sb.append(s.substring(pos1, m1start));

            // reached end?

            if (!b1) {
                return sb.toString();
            }

            // handle a numeric part

            int n1 = Integer.parseInt(s.substring(m1start, m1.end()));
            sb.append(n1 + "");

            // move on to the next part of the string

            pos1 = m1.end();
        }
    }

}

