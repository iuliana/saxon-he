////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.StringValue;

/**
* A ATokenIterator is an iterator over the strings that result from tokenizing a string using a regular expression
*/

public class ATokenIterator implements SequenceIterator<StringValue> {

    private UnicodeString input;
    private REMatcher matcher;
    /*@Nullable*/ private UnicodeString current;
    private int position = 0;
    private int prevEnd = 0;


    /**
    * Construct an ATokenIterator.
    */

    public ATokenIterator(UnicodeString input, REMatcher matcher) {
        this.input = input;
        this.matcher = matcher;
        prevEnd = 0;
    }

    public StringValue next() {
        if (prevEnd < 0) {
            current = null;
            position = -1;
            return null;
        }

        if (matcher.match(input, prevEnd)) {
            int start = matcher.getParenStart(0);
            current = input.substring(prevEnd, start);
            prevEnd = matcher.getParenEnd(0);
        } else {
            current = input.substring(prevEnd, input.length());
            prevEnd = -1;
        }
        position++;
        return currentStringValue();
    }

    private StringValue currentStringValue() {
        if (current instanceof BMPString) {
            return StringValue.makeStringValue(((BMPString)current).getCharSequence());
        } else {
            return StringValue.makeStringValue(current.toString());
        }
    }

    public StringValue current() {
        return (current==null ? null : currentStringValue());
    }

    public int position() {
        return position;
    }

    public void close() {
    }

    /*@NotNull*/
    public ATokenIterator getAnother() {
        return new ATokenIterator(input, new REMatcher(matcher.getProgram()));
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
     *         and {@link #LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

}

