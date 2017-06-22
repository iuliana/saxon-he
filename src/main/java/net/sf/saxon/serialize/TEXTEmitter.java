////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import javax.xml.transform.OutputKeys;

/**
  * This class generates TEXT output
  * @author Michael H. Kay
  */

public class TEXTEmitter extends XMLEmitter {

    /**
    * Start of the document.
    */

    public void open () throws XPathException  {}

	protected void openDocument() throws XPathException {
		if (writer == null) {
			makeWriter();
		}
		if (characterSet == null) {
			characterSet = UTF8CharacterSet.getInstance();
		}
		// Write a BOM if requested
		String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
		if (encoding == null || encoding.equalsIgnoreCase("utf8")) {
			encoding = "UTF-8";
		}
		String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);
		if ("yes".equals(byteOrderMark) &&
			("UTF-8".equalsIgnoreCase(encoding) || "UTF-16LE".equalsIgnoreCase(encoding) ||
				"UTF-16BE".equalsIgnoreCase(encoding))) {
			try {
				if (writer != null) {
					writer.write('\uFEFF');
				}
			} catch (java.io.IOException err) {
				// Might be an encoding exception; just ignore it
			}
		}
		started = true;
	}

    /**
    * Output the XML declaration. This implementation does nothing.
    */

    public void writeDeclaration() throws XPathException {}

    /**
    * Produce output using the current Writer. <BR>
    * Special characters are not escaped.
    * @param chars Character sequence to be output
    * @param properties bit fields holding special properties of the characters
    * @exception XPathException for any failure
    */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }
        if ((properties & ReceiverOptions.NO_SPECIAL_CHARS) == 0) {
            int badchar = testCharacters(chars);
            if (badchar != 0) {
                throw new XPathException(
                        "Output character not available in this encoding (decimal " + badchar + ")");
            }
        }
        try {
            writer.write(chars.toString());
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
    }

    /**
    * Output an element start tag. <br>
    * Does nothing with this output method.
     * @param elemName The element name (tag)
      * @param typeCode The type annotation
     * @param properties Bit fields holding any special properties of the element
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) {
        // no-op
    }

    public void namespace(NamespaceBinding namespaceBinding, int properties) {}

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) {}


    /**
    * Output an element end tag. <br>
    * Does nothing  with this output method.
    */

    public void endElement() {
        // no-op
    }

    /**
     * Output a processing instruction. <br>
     * Does nothing with this output method.
    */

    public void processingInstruction(String name, /*@NotNull*/ CharSequence value, int locationId, int properties)
    throws XPathException {}

    /**
    * Output a comment. <br>
    * Does nothing with this output method.
    */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {}

}

