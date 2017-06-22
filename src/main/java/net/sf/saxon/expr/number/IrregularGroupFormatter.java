////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.z.IntSet;

import java.util.List;

/**
 * Handles grouping separators when formatting a number in cases where the grouping separators are
 * not at regular intervals
 */

public class IrregularGroupFormatter extends NumericGroupFormatter {

    /*@Nullable*/ private IntSet groupingPositions = null;
    private List<Integer> separators = null;

    /**
     * Create a formatter for numbers where the grouping separators occur at irregular positions
     * @param groupingPositions the positions where the separators are to be inserted
     * @param sep array holding the separators to be inserted, as Unicode codepoints, in order starting
     * with the right-most
     * @param adjustedPicture
     */

    public IrregularGroupFormatter(IntSet groupingPositions, List<Integer> sep, UnicodeString adjustedPicture) {
        this.groupingPositions = groupingPositions;
        separators = sep;
        this.adjustedPicture = adjustedPicture;
    }

    @Override
    public String format(FastStringBuffer value) {
        UnicodeString in = UnicodeString.makeUnicodeString(value);
        int[] out = new int[in.length() + separators.size()];
        int j = 0;
        int k = out.length - 1;
        for (int i=in.length()-1; i>=0; i--) {
            out[k--] = in.charAt(i);
            if (groupingPositions.contains(in.length()-i)) {
                out[k--] = separators.get(j++);
            }
        }
        return UnicodeString.makeUnicodeString(out).toString();
    }

    /**
     * Get the grouping separator to be used. If more than one is used, return the last.
     * If no grouping separators are used, return null
     *
     * @return the grouping separator
     */
    @Override
    public String getSeparator() {
        if (separators.size() == 0) {
            return null;
        } else {
            int sep = separators.get(separators.size() - 1);
            FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.TINY);
            fsb.appendWideChar(sep);
            return fsb.toString();
        }
    }
}

