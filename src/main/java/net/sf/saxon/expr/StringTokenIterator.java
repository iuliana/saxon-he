////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.StringValue;

import java.util.StringTokenizer;

/**
* StringTokenIterator: breaks a string up into tokens,
* and returns the tokens as a sequence of strings.
*/

public class StringTokenIterator implements UnfailingIterator<StringValue> {

    private String theString;
    /*@Nullable*/ private String delimiters;  // null implies use whitespace as delimiter
    private StringTokenizer tokenizer;
    private String current;
    private int position = 0;

    /**
    * Construct a StringTokenIterator that will break the supplied
    * string into tokens at whitespace boundaries
     * @param string the string to be tokenized
     */

    public StringTokenIterator (String string) {
        theString = string;
        delimiters = null;
        tokenizer = new StringTokenizer(string, " \t\n\r", false);
    }

    /**
    * Construct a StringTokenIterator that will break the supplied
    * string into tokens at any of the delimiter characters included in the
    * delimiter string.
     * @param string the string to be tokenized
     * @param delimiters the characters that are recognized as token separators
     */

    public StringTokenIterator (String string, String delimiters) {
        theString = string;
        this.delimiters = delimiters;
        tokenizer = new StringTokenizer(string, delimiters, false);
    }

    public StringValue next() {
        if (tokenizer.hasMoreElements()) {
            current = (String)tokenizer.nextElement();
            position++;
            return new StringValue(current);
        } else {
            current = null;
            position = -1;
            return null;
        }
    }

    public StringValue current() {
        return (current == null ? null : new StringValue(current));
    }

    public int position() {
        return position;
    }

    public void close() {
    }

    /*@NotNull*/
    public StringTokenIterator getAnother() {
        if (delimiters==null) {
            return new StringTokenIterator(theString);
        } else {
            return new StringTokenIterator(theString, delimiters);
        }
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link SequenceIterator#GROUNDED}, {@link SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }
}

