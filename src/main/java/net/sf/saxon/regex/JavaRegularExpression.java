////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of RegularExpression that calls the JDK regular expression library directly.
 * This can be invoked by appending ";j" to the flags attribute/argument
 */
public class JavaRegularExpression implements RegularExpression {

    Pattern pattern;
    String javaRegex;
    int flagBits;

    /**
     * Create a regular expression, starting with an already-translated Java regex.
     * NOTE: this constructor is called from compiled XQuery code
     * @param javaRegex the regular expression after translation to Java notation
     * @param flags the user-specified flags (prior to any semicolon)
     */

    public JavaRegularExpression(CharSequence javaRegex, String flags) throws XPathException {
        this.flagBits = setFlags(flags);
        this.javaRegex = javaRegex.toString();
        pattern = Pattern.compile(this.javaRegex, flagBits & (~(Pattern.COMMENTS)));
    }

    /**
     * Get the Java regular expression (after translation from an XPath regex, but before compilation)
     * @return the regular expression in Java notation
     */

    public String getJavaRegularExpression() {
        return javaRegex;
    }

    /**
     * Get the flag bits as used by the Java regular expression engine
     * @return the flag bits
     */

    public int getFlagBits() {
        return flagBits;
    }

    /**
     * Use this regular expression to analyze an input string, in support of the XSLT
     * analyze-string instruction. The resulting RegexIterator provides both the matching and
     * non-matching substrings, and allows them to be distinguished. It also provides access
     * to matched subgroups.
     */

    public RegexIterator analyze(CharSequence input) {
        return new JRegexIterator(input.toString(), pattern);
    }

    /**
     * Determine whether the regular expression contains a match for a given string
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */

    public boolean containsMatch(CharSequence input) {
        return pattern.matcher(input).find();
    }

    /**
     * Determine whether the regular expression matches a given string in its entirety
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */

    public boolean matches(CharSequence input) {
        return pattern.matcher(input).matches();
    }

    /**
     * Replace all substrings of a supplied input string that match the regular expression
     * with a replacement string.
     *
     * @param input       the input string on which replacements are to be performed
     * @param replacement the replacement string in the format of the XPath replace() function
     * @return the result of performing the replacement
     * @throws net.sf.saxon.trans.XPathException
     *          if the replacement string is invalid
     */

    public CharSequence replace(CharSequence input, CharSequence replacement) throws XPathException {
        Matcher matcher = pattern.matcher(input);
        try {
            return matcher.replaceAll(replacement.toString());
        } catch (IndexOutOfBoundsException e) {
            throw new XPathException(e.getMessage(), "FORX0004");
        }

    }

    /**
     * Use this regular expression to tokenize an input string.
     *
     * @param input the string to be tokenized
     * @return a SequenceIterator containing the resulting tokens, as objects of type StringValue
     */

    public SequenceIterator tokenize(CharSequence input) {
        if (input.length() == 0) {
            return EmptyIterator.getInstance();
        }
        return new JTokenIterator(input, pattern);
    }

    /**
     * Set the Java flags from the supplied XPath flags. The flags recognized have their
     * Java-defined meanings rather than their XPath-defined meanings. The available flags are:
     * <p>d - UNIX_LINES</p>
     * <p>m - MULTILINE</p>
     * <p>i - CASE_INSENSITIVE</p>
     * <p>s - DOTALL</p>
     * <p>x - COMMENTS</p>
     * <p>u - UNICODE_CASE</p>
     * <p>q - LITERAL</p>
     * <p>c - CANON_EQ</p>
     * @param inFlags the flags as a string, e.g. "im"
     * @return the flags as a bit-significant integer
     * @throws XPathException if the supplied value contains an unrecognized flag character
     * @see java.util.regex.Pattern
     */

    public static int setFlags(/*@NotNull*/ CharSequence inFlags) throws XPathException {
        int flags = Pattern.UNIX_LINES;
        for (int i=0; i<inFlags.length(); i++) {
            char c = inFlags.charAt(i);
            switch (c) {
            case 'd':
                flags |= Pattern.UNIX_LINES;
                break;
            case 'm':
                flags |= Pattern.MULTILINE;
                break;
            case 'i':
                flags |= Pattern.CASE_INSENSITIVE;
                break;
            case 's':
                flags |= Pattern.DOTALL;
                break;
            case 'x':
                flags |= Pattern.COMMENTS;  // note, this enables comments as well as whitespace
                break;
            case 'u':
                flags |= Pattern.UNICODE_CASE;
                break;
            case 'q':
                flags |= Pattern.LITERAL;
                break;
            case 'c':
                flags |= Pattern.CANON_EQ;
                break;
            default:
                XPathException err = new XPathException("Invalid character '" + c + "' in regular expression flags");
                err.setErrorCode("FORX0001");
                throw err;
            }
        }
        return flags;
    }
}

