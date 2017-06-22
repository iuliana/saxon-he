////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.type.SchemaType;

import java.util.Arrays;
import java.util.HashSet;

/**
* HTMLIndenter: This ProxyEmitter indents HTML elements, by adding whitespace
* character data where appropriate.
* The character data is never added when within an inline element.
* The string used for indentation defaults to three spaces
*
* @author Michael Kay
*/


public class HTMLIndenter extends ProxyReceiver {

    private int level = 0;

    private char[] indentChars = {'\n', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};

    private boolean sameLine = false;
    private boolean inFormattedTag = false;
    private boolean afterInline = false;
    private boolean afterFormatted = true;    // to prevent a newline at the start
    /*@NotNull*/ private int[] propertyStack = new int[20];
    private NameClassifier classifier;

    /**
     * The NameClassifier classifies element names according to whether the element is (a) an inline element,
     * and/or (b) a formatted element
     */

    interface NameClassifier {

        static final int IS_INLINE = 1;
        static final int IS_FORMATTED = 2;

        /**
         * Classify an element name as inline, formatted, or both or neither.
         * This method is overridden in the XHTML indenter
         * @param name the element name
         * @return a bit-significant integer containing flags IS_INLINE and/or IS_FORMATTED
         */
        int classifyTag(NodeName name);

    }

    final private static String[] inlineTags = {
            "tt", "i", "b", "u", "s", "strike", "big", "small", "em", "strong", "dfn", "code", "samp",
             "kbd", "var", "cite", "abbr", "acronym", "a", "img", "applet", "object", "font",
             "basefont", "br", "script", "map", "q", "sub", "sup", "span", "bdo", "iframe", "input",
             "select", "textarea", "label", "button", "ins", "del" };

            // INS and DEL are not actually inline elements, but the SGML DTD for HTML
            // (apparently) permits them to be used as if they were.

    final static String[] formattedTags = {"pre", "script", "style", "textarea", "xmp"};
                                        // "xmp" is obsolete but still encountered!

    /**
     * Class to classify HTML names
     */

    static class HTMLNameClassifier implements NameClassifier {

        // the list of inline tags is from the HTML 4.0 (loose) spec. The significance is that we
        // mustn't add spaces immediately before or after one of these elements.

        final static HTMLNameClassifier THE_INSTANCE = new HTMLNameClassifier();

        private static HTMLTagHashSet inlineTable = new HTMLTagHashSet(101);

        static {
            for (String inlineTag : inlineTags) {
                inlineTable.add(inlineTag);
            }
        }

        /**
         * Classify an element name as inline, formatted, or both or neither.
         * This method is overridden in the XHTML indenter
         * @param elemName the element name
         * @return a bit-significant integer containing flags IS_INLINE and/or IS_FORMATTED
         */
         public int classifyTag(NodeName elemName) {
            int r = 0;
            String tag = elemName.getDisplayName();
            if (inlineTable.contains(tag)) {
                r |= IS_INLINE;
            }
            if (formattedTable.contains(tag)) {
                r |= IS_FORMATTED;
            }
            return r;
        }

        // Table of preformatted elements

        private static HTMLTagHashSet formattedTable = new HTMLTagHashSet(23);


        static {
            for (String formattedTag : formattedTags) {
                formattedTable.add(formattedTag);
            }
        }
    }

    /**
     * Class to classify XHTML names
     */

    static class XHTMLNameClassifier implements NameClassifier {

        final static XHTMLNameClassifier THE_INSTANCE = new XHTMLNameClassifier();

        private final static HashSet<NodeName> inlineTagSet;
        private final static HashSet<NodeName> formattedTagSet;

        static {
            inlineTagSet = new HashSet<NodeName>(50);
            formattedTagSet = new HashSet<NodeName>(10);
            for (String inlineTag : inlineTags) {
                inlineTagSet.add(new FingerprintedQName("", NamespaceConstant.XHTML, inlineTag));
            }
            for (String formattedTag : formattedTags) {
                formattedTagSet.add(new FingerprintedQName("", NamespaceConstant.XHTML, formattedTag));
            }
        }

        /**
         * Classify an element name as inline, formatted, or both or neither.
         * This method is overridden in the XHTML indenter
         * @param name the element name
         * @return a bit-significant integer containing flags IS_INLINE and/or IS_FORMATTED
         */

        public int classifyTag(NodeName name) {
            int r = 0;
            if (inlineTagSet.contains(name)) {
                r |= IS_INLINE;
            }
            if (formattedTagSet.contains(name)) {
                r |= IS_FORMATTED;
            }
            return r;
        }

    }

    public HTMLIndenter(Receiver next, String method) {
        super(next);
        if ("xhtml".equals(method)) {
            classifier = XHTMLNameClassifier.THE_INSTANCE;
        } else {
            classifier = HTMLNameClassifier.THE_INSTANCE;
        }
    }

    /**
    * Output element start tag
    */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        int tagProps = classifier.classifyTag(nameCode);
        if (level >= propertyStack.length) {
            int[] p2 = new int[level*2];
            System.arraycopy(propertyStack, 0, p2, 0, propertyStack.length);
            propertyStack = p2;
        }
        propertyStack[level] = tagProps;
        boolean inlineTag = (tagProps & NameClassifier.IS_INLINE) != 0;
        inFormattedTag = inFormattedTag || ((tagProps & NameClassifier.IS_FORMATTED) != 0);
        if (!inlineTag && !inFormattedTag &&
             !afterInline && !afterFormatted) {
            indent();
        }

        nextReceiver.startElement(nameCode, typeCode, locationId, properties);
        level++;
        sameLine = true;
        afterInline = false;
        afterFormatted = false;
    }

    /**
    * Output element end tag
    */

    public void endElement() throws XPathException {
        level--;
        boolean thisInline = (propertyStack[level] & NameClassifier.IS_INLINE) != 0;
        boolean thisFormatted = (propertyStack[level] & NameClassifier.IS_FORMATTED) != 0;
        if (!thisInline && !thisFormatted && !afterInline &&
                 !sameLine && !afterFormatted && !inFormattedTag) {
            indent();
            afterInline = false;
            afterFormatted = false;
        } else {
            afterInline = thisInline;
            afterFormatted = thisFormatted;
        }
        nextReceiver.endElement();
        inFormattedTag = inFormattedTag && !thisFormatted;
        sameLine = false;
    }

    /**
    * Output character data
    */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (inFormattedTag || (properties & ReceiverOptions.USE_NULL_MARKERS) != 0) {
            // don't split the text if in a tag such as <pre>, or if the text contains the result of
            // expanding a character map
            nextReceiver.characters(chars, locationId, properties);
        } else {
            // otherwise try to split long lines into multiple lines
            int lastNL = 0;
            for (int i=0; i<chars.length(); i++) {
                if (chars.charAt(i)=='\n' || (i-lastNL > getLineLength() && chars.charAt(i)==' ')) {
                    sameLine = false;
                    nextReceiver.characters(chars.subSequence(lastNL, i), locationId, properties);
                    indent();
                    lastNL = i+1;
                    while (lastNL<chars.length() && chars.charAt(lastNL)==' ') {
                        lastNL++;
                    }
                }
            }
            if (lastNL < chars.length()) {
                nextReceiver.characters(chars.subSequence(lastNL, chars.length()), locationId, properties);
            }
        }
        afterInline = false;
    }

    /**
    * Output a comment
    */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        indent();
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Output white space to reflect the current indentation level
     * @throws net.sf.saxon.trans.XPathException if an error occurs downstream in the pipeline
     */

    private void indent() throws XPathException {
        int spaces = level * getIndentation();
        if (spaces+1 >= indentChars.length) {
            int increment = 5 * getIndentation();
            if (spaces + 1 > indentChars.length + increment) {
                increment += spaces + 1;
            }
            char[] c2 = new char[indentChars.length + increment];
            System.arraycopy(indentChars, 0, c2, 0, indentChars.length);
            Arrays.fill(c2, indentChars.length, c2.length, ' ');
            indentChars = c2;
        }
        nextReceiver.characters(new CharSlice(indentChars, 0, spaces+1), 0, 0);
        sameLine = false;
    }

    /**
     * Get the number of spaces to be used for indentation
     * @return the number of spaces to be added to the indentation for each level
     */

    protected int getIndentation() {
        return 3;
    }

    /**
     * Get the maximum length of lines, after which long lines will be word-wrapped
     * @return the maximum line length
     */

    protected int getLineLength() {
        return 80;
    }

}

