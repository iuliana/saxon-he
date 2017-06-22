////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.StringValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JTokenIterator is an iterator over the strings that result from tokenizing a string using
 * a regular expression, in this case a regular expression evaluated using the JDK regex engine
*/

public class JTokenIterator implements SequenceIterator {

    private CharSequence input;
    private Pattern pattern;
    private Matcher matcher;
    /*@Nullable*/ private CharSequence current;
    private int position = 0;
    private int prevEnd = 0;


    /**
    * Construct a JTokenIterator.
    */

    public JTokenIterator (CharSequence input, Pattern pattern) {
        this.input = input;
        this.pattern = pattern;
        matcher = pattern.matcher(input);
        prevEnd = 0;
    }

    public Item next() {
        if (prevEnd < 0) {
            current = null;
            position = -1;
            return null;
        }

        if (matcher.find()) {
            current = input.subSequence(prevEnd, matcher.start());
            prevEnd = matcher.end();
        } else {
            current = input.subSequence(prevEnd, input.length());
            prevEnd = -1;
        }
        position++;
        return StringValue.makeStringValue(current);
    }

    public Item current() {
        return (current==null ? null : StringValue.makeStringValue(current));
    }

    public int position() {
        return position;
    }

    public void close() {
    }

    /*@NotNull*/
    public SequenceIterator getAnother() {
        return new JTokenIterator(input, pattern);
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

