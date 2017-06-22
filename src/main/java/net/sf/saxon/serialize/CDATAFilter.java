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
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.OutputKeys;
import java.util.*;

/**
* CDATAFilter: This ProxyEmitter converts character data to CDATA sections,
* if the character data belongs to one of a set of element types to be handled this way.
*
* @author Michael Kay
*/


public class CDATAFilter extends ProxyReceiver {

    private FastStringBuffer buffer = new FastStringBuffer(FastStringBuffer.MEDIUM);
    private Stack<NodeName> stack = new Stack<NodeName>();
    private Set<NodeName> nameList;             // names of cdata elements
    private CharacterSet characterSet;

    /**
     * Create a CDATA Filter
     * @param next the next receiver in the pipeline
     */

    public CDATAFilter(Receiver next) {
        super(next);
    }

    /**
     * Set the properties for this CDATA filter
     * @param details the output properties
     * @throws net.sf.saxon.trans.XPathException if an error occurs
    */

    public void setOutputProperties (/*@NotNull*/ Properties details)
    throws XPathException {
        getCdataElements(details);
        characterSet = getConfiguration().getCharacterSetFactory().getCharacterSet(details);
    }

    /**
    * Output element start tag
    */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        flush();
        stack.push(elemName);
        nextReceiver.startElement(elemName, typeCode, locationId, properties);
    }

    /**
    * Output element end tag
    */

    public void endElement() throws XPathException {
        flush();
        stack.pop();
        nextReceiver.endElement();
    }

    /**
    * Output a processing instruction
    */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        flush();
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
    * Output character data
    */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {

        if ((properties & ReceiverOptions.DISABLE_ESCAPING) == 0) {
            buffer.append(chars.toString());
        } else {
            // if the user requests disable-output-escaping, this overrides the CDATA request. We end
            // the CDATA section and output the characters as supplied.
            flush();
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
    * Output a comment
    */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        flush();
        nextReceiver.comment(chars, locationId, properties);
    }


    /**
     * Flush the buffer containing accumulated character data,
     * generating it as CDATA where appropriate
     */

    private void flush() throws XPathException {
        boolean cdata;
        int end = buffer.length();
        if (end==0) return;

        if (stack.isEmpty()) {
            cdata = false;      // text is not part of any element
        } else {
            NodeName top = stack.peek();
            cdata = isCDATA(top);
        }

        if (cdata) {

            // Check that the buffer doesn't include a character not available in the current
            // encoding

            int start = 0;
            int k = 0;
            while ( k < end ) {
                int next = buffer.charAt(k);
                int skip = 1;
                if (UTF16CharacterSet.isHighSurrogate((char)next)) {
                    next = UTF16CharacterSet.combinePair((char)next, buffer.charAt(k+1));
                    skip = 2;
                }
                if (next != 0 && characterSet.inCharset(next)) {
                    k++;
                } else {

                    // flush out the preceding characters as CDATA

                    char[] array = new char[k-start];
                    buffer.getChars(start, k, array, 0);
                    flushCDATA(array, k-start);

                    while (k < end) {
                        // output consecutive non-encodable characters
                        // before restarting the CDATA section
                        //super.characters(CharBuffer.wrap(buffer, k, k+skip), 0, 0);
                        nextReceiver.characters(buffer.subSequence(k, k+skip), 0, ReceiverOptions.DISABLE_CHARACTER_MAPS);
                                // was: (..., ReceiverOptions.DISABLE_ESCAPING);
                        k += skip;
                        if (k >= end) {
                            break;
                        }
                        next = buffer.charAt(k);
                        skip = 1;
                        if (UTF16CharacterSet.isHighSurrogate((char)next)) {
                            next = UTF16CharacterSet.combinePair((char)next, buffer.charAt(k+1));
                            skip = 2;
                        }
                        if (characterSet.inCharset(next)) {
                            break;
                        }
                    }
                    start=k;
                }
            }
            char[] rest = new char[end-start];
            buffer.getChars(start, end, rest, 0);
            flushCDATA(rest, end-start);

        } else {
            nextReceiver.characters(buffer, 0, 0);
        }

        buffer.setLength(0);

    }

    /**
     * Output an array as a CDATA section. At this stage we have checked that all the characters
     * are OK, but we haven't checked that there is no "]]>" sequence in the data
     * @param array the data to be output
     * @param len the number of characters in the array actually used
    */

    private void flushCDATA(char[] array, int len) throws XPathException {
        if (len == 0) {
            return;
        }
        final int chprop = ReceiverOptions.DISABLE_ESCAPING | ReceiverOptions.DISABLE_CHARACTER_MAPS;
        nextReceiver.characters("<![CDATA[", 0, chprop);

        // Check that the character data doesn't include the substring "]]>"
        // Also get rid of any zero bytes inserted by character map expansion

        int i=0;
        int doneto=0;
        while (i<len-2) {
            if (array[i]==']' && array[i+1]==']' && array[i+2]=='>') {
                nextReceiver.characters(new CharSlice(array, doneto, i+2-doneto), 0, chprop);
                nextReceiver.characters("]]><![CDATA[", 0, chprop);
                doneto=i+2;
            } else if (array[i]==0) {
                nextReceiver.characters(new CharSlice(array, doneto, i-doneto), 0, chprop);
                doneto=i+1;
            }
            i++;
        }
        nextReceiver.characters(new CharSlice(array, doneto, len-doneto), 0, chprop);
        nextReceiver.characters("]]>", 0, chprop);
    }


    /**
     * See if a particular element is a CDATA element
     * @param elementName identifies the name of element we are interested
     * @return true if this element is included in cdata-section-elements
    */

    private boolean isCDATA(NodeName elementName) {
        return nameList.contains(elementName);
	}

    /**
     * Extract the list of CDATA elements from the output properties
     * @param details the output properties
    */

    private void getCdataElements(Properties details) {
        boolean isHTML = "html".equals(details.getProperty(OutputKeys.METHOD));
        boolean isHTML5 = isHTML && "5.0".equals(details.getProperty(OutputKeys.VERSION));
        boolean isHTML4 = isHTML && !isHTML5;
        String cdata = details.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdata==null) {
            // this doesn't happen, but there's no harm allowing for it
            nameList = new HashSet<NodeName>(0);
            return;
        }
        nameList = new HashSet<NodeName>(10);
        StringTokenizer st2 = new StringTokenizer(cdata, " \t\n\r", false);
        while (st2.hasMoreTokens()) {
            String expandedName = st2.nextToken();
            StructuredQName sq = StructuredQName.fromClarkName(expandedName);
            String uri = sq.getURI();
            if (!isHTML || (isHTML4 && !uri.equals("")) || (isHTML5 && !uri.equals("") && !uri.equals(NamespaceConstant.XHTML))) {
                nameList.add(new FingerprintedQName("", sq.getURI(), sq.getLocalPart()));
            }
        }
    }

}

