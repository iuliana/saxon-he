////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses�?, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;

/**
 * This class generates HTML 4.0 output
 */
public class HTML40Emitter extends HTMLEmitter{

	static {
		setEmptyTag("area");
		setEmptyTag("base");
		setEmptyTag("basefont");
		setEmptyTag("br");
		setEmptyTag("col");
		setEmptyTag("embed");
		setEmptyTag("frame");
		setEmptyTag("hr");
		setEmptyTag("img");
		setEmptyTag("input");
		setEmptyTag("isindex");
		setEmptyTag("link");
		setEmptyTag("meta");
		setEmptyTag("param");
	}


	/**
	 * Constructor
	 */

	public HTML40Emitter() {

	}

	/**
	 * Decide whether an element is "serialized as an HTML element" in the language of the 3.0 specification
	 *
	 * @return true if the element is to be serialized as an HTML element
	 */
	@Override
	protected boolean isHTMLElement(NodeName name) {
		return name.getURI().equals("");
	}

	@Override
	protected void openDocument() throws XPathException {

		String versionProperty = outputProperties.getProperty(SaxonOutputKeys.HTML_VERSION);
		// Note, we recognize html-version even when running XSLT 2.0.
		if (versionProperty == null) {
			versionProperty = outputProperties.getProperty(OutputKeys.VERSION);
		}

		if (versionProperty != null) {
			if (versionProperty.equals("4.0") || versionProperty.equals("4.01")) {
				version = 4;
			} else {
				XPathException err = new XPathException("Unsupported HTML version: " + versionProperty);
				err.setErrorCode("SESU0013");
				throw err;
			}
		}
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
		if (systemId != null || publicId != null) {
			writeDocType(null, "html", systemId, publicId);
		}
	}


}
