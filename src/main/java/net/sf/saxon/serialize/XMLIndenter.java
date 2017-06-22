////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.util.*;

/**
* XMLIndenter: This ProxyReceiver indents elements, by adding character data where appropriate.
* The character data is always added as "ignorable white space", that is, it is never added
* adjacent to existing character data.
*
* @author Michael Kay
*/


public class XMLIndenter extends ProxyReceiver {

    private int level = 0;

    private char[] indentChars = {'\n', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
    private boolean sameline = false;
    private boolean afterStartTag = false;
    private boolean afterEndTag = true;
    private boolean allWhite = true;
    private int line = 0;       // line and column measure the number of lines and columns
    private int column = 0;     // .. in whitespace text nodes between tags
    private int suppressedAtLevel = -1;
    /*@Nullable*/ private Set<NodeName> suppressedElements = null;
    private XMLEmitter emitter;
    private AttributeCollectionImpl bufferedAttributes;
    private List<NamespaceBinding> bufferedNamespaces = new ArrayList<NamespaceBinding>(8);


    /**
     * Create an XML Indenter
     * @param next the next receiver in the pipeline, always an XMLEmitter
     */

    public XMLIndenter(XMLEmitter next) {
        super(next);
        emitter = next;
        bufferedAttributes = new AttributeCollectionImpl(getConfiguration());
    }

    /**
     * Set the properties for this indenter
     * @param props the serialization properties
    */

    public void setOutputProperties(Properties props) {

        String omit = props.getProperty(OutputKeys.OMIT_XML_DECLARATION);
        afterEndTag = omit==null || !"yes".equals(Whitespace.trim(omit)) ||
                    props.getProperty(OutputKeys.DOCTYPE_SYSTEM)!=null ;
        String s = props.getProperty(SaxonOutputKeys.SUPPRESS_INDENTATION);
        if (s == null) {
            s = props.getProperty("{http://saxon.sf.net/}suppress-indentation");
            // for compatibility: since 9.3 also available in default namespace
        }
        if (s != null) {
            suppressedElements = new HashSet<NodeName>(8);
            StringTokenizer st = new StringTokenizer(s, " \t\r\n");
            while (st.hasMoreTokens()) {
                String clarkName = st.nextToken();
                suppressedElements.add(FingerprintedQName.fromClarkName(clarkName));
            }
        }

    }

    /**
    * Start of document
    */

    public void open() throws XPathException {
        nextReceiver.open();
    }

    /**
    * Output element start tag
    */

    public void startElement(NodeName nameCode, SchemaType type, int locationId, int properties) throws XPathException {
        if (afterStartTag || afterEndTag) {
            if (isDoubleSpaced(nameCode)) {
                nextReceiver.characters("\n", 0, 0);
                line = 0;
                column = 0;
            }
            indent();
        }
        nextReceiver.startElement(nameCode, type, locationId, properties);
        level++;
        sameline = true;
        afterStartTag = true;
        afterEndTag = false;
        allWhite = true;
        line = 0;
        if (suppressedElements != null && suppressedAtLevel == -1 && suppressedElements.contains(nameCode)) {
            suppressedAtLevel = level;
        }
        int typeCode = type.getFingerprint();
        if (typeCode >= 1024 && suppressedAtLevel < 0 &&
                ((type = getConfiguration().getSchemaType(typeCode)) != null && type.isComplexType() &&
                        ((ComplexType)type).isMixedContent())) {
            // suppress indentation for elements with mixed content. (Note this also suppresses
            // indentation for all descendants of such elements. We could be smarter than this.)
            suppressedAtLevel = level;
        }
        bufferedAttributes.clear();
        bufferedNamespaces.clear();
    }

    @Override
    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        bufferedNamespaces.add(namespaceBinding);
    }

    /**
    * Output an attribute
    */
	public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties)
		throws XPathException {
		if (suppressedAtLevel < 0 && value.equals("preserve") && attName.isInNamespace(NamespaceConstant.XML) &&
			attName.getLocalPart().equals("space")) {
			// Note, we are suppressing indentation within an xml:space="preserve" region even if a descendant
			// specifies xml:space="default
			suppressedAtLevel = level;
		}
		bufferedAttributes.addAttribute(attName, typeCode, value.toString(), locationId, properties);
		// nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
	}

    public void startContent() throws XPathException {
        int len = 0;
        int count = 0;
        int indent = -1;
        AttributeCollectionImpl ba = bufferedAttributes;
        if (suppressedAtLevel < 0) {
            for (NamespaceBinding binding : bufferedNamespaces) {
                String prefix = binding.getPrefix();
                if (prefix.length()==0) {
                    len += 9 + binding.getURI().length();
                } else {
                    len += prefix.length() + 10 + binding.getURI().length();
                }
            }
            for (int i=0; i<ba.getLength(); i++) {
                String prefix = ba.getPrefix(i);
                len += ba.getLocalName(i).length()
                            + ba.getValue(i).length()
                            + 4 + (prefix.length()==0 ? 4 : prefix.length() + 5);
            }
            if (len > getLineLength()) {
                indent = (level-1) * getIndentation() + emitter.elementStack.peek().length() + 3;
            }
        }
        for (NamespaceBinding binding : bufferedNamespaces) {
            nextReceiver.namespace(binding, 0);
            if (indent > 0 && count++ == 0) {
                emitter.setIndentForNextAttribute(indent);
            }
        }
        for (int i=0; i<ba.getLength(); i++) {
            nextReceiver.attribute(ba.getNodeName(i), ba.getTypeAnnotation(i),
                    ba.getValue(i), ba.getLocationId(i), ba.getProperties(i));
            if (indent > 0 && count++ == 0) {
                emitter.setIndentForNextAttribute(indent);
            }
        }
        nextReceiver.startContent();

    }

    /**
    * Output element end tag
    */

    public void endElement() throws XPathException {
        level--;
        if (afterEndTag && !sameline) {
            indent();
        }
        nextReceiver.endElement();
        sameline = false;
        afterEndTag = true;
        afterStartTag = false;
        allWhite = true;
        line = 0;
        if (level == (suppressedAtLevel - 1)) {
            suppressedAtLevel = -1;
            // remove the suppression of indentation
        }
    }

    /**
    * Output a processing instruction
    */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (afterEndTag) {
            indent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
        //afterStartTag = false;
        //afterEndTag = false;
    }

    /**
    * Output character data
    */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        for (int i=0; i<chars.length(); i++) {
            char c = chars.charAt(i);
            if (c=='\n') {
                sameline = false;
                line++;
                column = 0;
            }
            if (!Character.isWhitespace(c)) {
                allWhite = false;
            }
            column++;
        }
        nextReceiver.characters(chars, locationId, properties);
        if (!allWhite) {
            afterStartTag = false;
            afterEndTag = false;
        }
    }

    /**
    * Output a comment
    */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        if (afterEndTag) {
            indent();
        }
        nextReceiver.comment(chars, locationId, properties);
        //afterStartTag = false;
        //afterEndTag = false;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return true;
    }

    /**
     * Output white space to reflect the current indentation level
     * @throws XPathException if a downstream error occurs doing the output
    */

    private void indent() throws XPathException {
        if (suppressedAtLevel >= 0) {
            // indentation has been suppressed (e.g. by xmlspace="preserve")
            return;
        }
        int spaces = level * getIndentation();
        if (line>0) {
            spaces -= column;
            if (spaces <= 0) {
                return;     // there's already enough white space, don't add more
            }
        }
        if (spaces+2 >= indentChars.length) {
            int increment = 5 * getIndentation();
            if (spaces + 2 > indentChars.length + increment) {
                increment += spaces + 2;
            }
            char[] c2 = new char[indentChars.length + increment];
            System.arraycopy(indentChars, 0, c2, 0, indentChars.length);
            Arrays.fill(c2, indentChars.length, c2.length, ' ');
            indentChars = c2;
        }
        // output the initial newline character only if line==0
        int start = (line == 0 ? 0 : 1);
        //super.characters(indentChars.subSequence(start, start+spaces+1), 0, ReceiverOptions.NO_SPECIAL_CHARS);
        nextReceiver.characters(new CharSlice(indentChars, start, spaces+1), 0, ReceiverOptions.NO_SPECIAL_CHARS);
        sameline = false;
    }

    @Override
    public void endDocument() throws XPathException {
        if (afterEndTag) {
            characters("\n", 0, 0);  // if permitted, output a trailing newline, for tidier console output
        }
        super.endDocument();
    }

    /**
     * Get the number of spaces to be used for indentation
     * @return the number of spaces to be added to the indentation for each level
     */

    protected int getIndentation() {
        return 3;
    }

    /**
     * Ask whether a particular element is to be double-spaced
     * @param name the element name
     * @return true if double-spacing is in effect for this element
     */

    protected boolean isDoubleSpaced(NodeName name) {
        return false;
    }

    /**
     * Get the suggested maximum length of a line
     * @return the suggested maximum line length (used for wrapping attributes)
     */

    protected int getLineLength() {
        return 80;
    }
}

