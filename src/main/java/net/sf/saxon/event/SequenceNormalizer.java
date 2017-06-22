////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses�?, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.AtomicValue;

/**
 * Implement the "sequence normalization" logic as defined in the XSLT 3.0/XQuery 3.0
 * serialization spec.
 *
 * <p>This class is used only if an ItemSeparator is specified. In the absence of an ItemSeparator,
 * the insertion of a single space performed by the ComplexContentOutputter serves the purpose.</p>
 */

public class SequenceNormalizer extends ProxyReceiver {

	private String separator;
	private int level = 0;
	private boolean first = true;

	public SequenceNormalizer(SequenceReceiver next, String separator) {
		super(next);
		this.separator = separator;
	}

	/**
	 * Start of a document node.
	 */
	@Override
	public void startDocument(int properties) throws XPathException {
		sep();
		level++;
		getUnderlyingReceiver().startDocument(properties);
	}

	/**
	 * Notify the end of a document node
	 */
	@Override
	public void endDocument() throws XPathException {
		level--;
		getUnderlyingReceiver().endDocument();
	}

	/**
	 * Notify the start of an element
	 *
	 * @param elemName   integer code identifying the name of the element within the name pool.
	 * @param typeCode   integer code identifying the element's type within the name pool.
	 * @param properties properties of the element node
	 */
	@Override
	public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
		sep();
		level++;
		getUnderlyingReceiver().startElement(elemName, typeCode, locationId, properties);
	}

	/**
	 * End of element
	 */
	@Override
	public void endElement() throws XPathException {
		level--;
		super.endElement();
	}

	/**
	 * Character data
	 */
	@Override
	public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
		sep();
		getUnderlyingReceiver().characters(chars, locationId, properties);
	}

	/**
	 * Processing Instruction
	 */
	@Override
	public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
		sep();
		getUnderlyingReceiver().processingInstruction(target, data, locationId, properties);
	}

	/**
	 * Output a comment
	 */
	@Override
	public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
		sep();
		getUnderlyingReceiver().comment(chars, locationId, properties);
	}

	/**
	 * Append an arbitrary item (node or atomic value) to the output
	 *
	 * @param item           the item to be appended
	 * @param locationId     the location of the calling instruction, for diagnostics
	 * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
	 *                       need to be copied. Values are {@link net.sf.saxon.om.NodeInfo#ALL_NAMESPACES},
	 *                       {@link net.sf.saxon.om.NodeInfo#LOCAL_NAMESPACES}, {@link net.sf.saxon.om.NodeInfo#NO_NAMESPACES}
	 */
	@Override
	public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
		sep();
		if (level==0 && item instanceof AtomicValue) {
			CharSequence cs = item.getStringValueCS();
			getUnderlyingReceiver().characters(cs, locationId, 0);
		} else {
			((SequenceReceiver)getUnderlyingReceiver()).append(item, locationId, copyNamespaces);
		}
	}

	/**
	 * Output the separator, assuming we are at the top level and not at the start
	 */

	public void sep() throws XPathException {
		if (level == 0 && !first) {
			getUnderlyingReceiver().characters(separator, 0, 0);
		} else {
			first = false;
		}
	}
}

