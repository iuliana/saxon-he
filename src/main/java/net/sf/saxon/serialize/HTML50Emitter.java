////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses�?, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;

/**
 * This class generates HTML 5.0 output
 */
public class HTML50Emitter extends HTMLEmitter {


	static {
		setEmptyTag("area");
		setEmptyTag("base");
		setEmptyTag("base");
		setEmptyTag("basefont");
		setEmptyTag("br");
		setEmptyTag("col");
		setEmptyTag("command");
		setEmptyTag("embed");
		setEmptyTag("frame");
		setEmptyTag("hr");
		setEmptyTag("img");
		setEmptyTag("input");
		setEmptyTag("isindex");
		setEmptyTag("keygen");
		setEmptyTag("link");
		setEmptyTag("meta");
		setEmptyTag("param");
		setEmptyTag("source");
		setEmptyTag("track");
		setEmptyTag("wbr");
	}

	/**
	 * Constructor
	 */

	public HTML50Emitter() {
		version = 5;
	}

	/**
	 * Decide whether an element is "serialized as an HTML element" in the language of the 3.0 specification
	 *
	 * @return true if the element is to be serialized as an HTML element
	 */
	@Override
	protected boolean isHTMLElement(NodeName name) {
		String uri = name.getURI();
		return uri.equals("") || uri.equals(NamespaceConstant.XHTML);
	}

	protected void openDocument() throws XPathException {
		version = 5;
		String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
		String publicId = outputProperties.getProperty(OutputKeys.DOCTYPE_PUBLIC);

		// Treat "" as equivalent to absent. This goes beyond what the spec strictly allows.
		if ("".equals(systemId)) {
			systemId = null;
		}
		if ("".equals(publicId)) {
			publicId = null;
		}
		super.openDocument();
		writeDocType(null, "html", systemId, publicId);
	}

	/**
	 * Output the document type declaration
	 *
	 * @param displayName The element name
	 * @param systemId    The DOCTYP system identifier
	 * @param publicId    The DOCTYPE public identifier
	 */

	protected void writeDocType(NodeName name, String displayName, String systemId, String publicId) throws XPathException {
		try {
			if (systemId == null && publicId == null) {
				writer.write("<!DOCTYPE HTML>\n");
			} else {
				super.writeDocType(name, displayName, systemId, publicId);
			}
		} catch (java.io.IOException err) {
			throw new XPathException(err);
		}

	}

	protected boolean writeDocTypeWithNullSystemId() {
		return true;
	}
}
