////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import java.util.ArrayList;
import java.util.List;

/**
 * This receiver is inserted into the output pipeline whenever on-empty is used (XSLT 3.0)
 * on a literal result element, on xsl:element, or on xsl:copy when processing elements.
 * It delays writing the start tag until significant content is encountered (anything except
 * namespaces and empty text nodes), and if no significant content has been encountered when the
 * endElement even occurs, it evaluates the onEmpty expression in place of the original element
 * constructor.
 */
public class OnEmptyHandler extends ProxyReceiver {

    boolean processingParent = true;
    Expression onEmpty;
    XPathContext context;

    NodeName savedNodeName;
    SchemaType savedSchemaType;
    int savedLocationId;
    int savedProperties;
    List<NamespaceBinding> savedNamespaces = new ArrayList<NamespaceBinding>(4);

    public OnEmptyHandler(Receiver next, Expression onEmpty, XPathContext context) {
        super(next);
        this.onEmpty = onEmpty;
        this.context = context;
    }

    private void flush() throws XPathException {
        if (savedNodeName != null) {
            nextReceiver.startElement(savedNodeName, savedSchemaType, savedLocationId, savedProperties);
            for (NamespaceBinding binding : savedNamespaces) {
                nextReceiver.namespace(binding, 0);
            }
            savedNodeName = null;
        }
    }

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (processingParent) {
            savedNodeName = elemName;
            savedSchemaType = typeCode;
            savedLocationId = locationId;
            savedProperties = properties;
        } else {
            flush();
            super.startElement(elemName, typeCode, locationId, properties);
        }
    }

    @Override
    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        if (savedNodeName != null) {
            savedNamespaces.add(namespaceBinding);
        } else {
            super.namespace(namespaceBinding, properties);
        }
    }

    @Override
    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        flush();
        super.attribute(nameCode, typeCode, value, locationId, properties);
    }

    @Override
    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (chars.length() > 0) {
            flush();
            super.characters(chars, locationId, properties);
        }
    }

    @Override
    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        flush();
        super.processingInstruction(target, data, locationId, properties);
    }

    @Override
    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        flush();
        super.comment(chars, locationId, properties);
    }

    @Override
    public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
        flush();
        super.append(item, locationId, copyNamespaces);
    }

    @Override
    public void endElement() throws XPathException {
        if (savedNodeName != null) {
            // implies we have reached the end of the element without encountering any content
            onEmpty.process(context);
        } else {
            super.endElement();
        }
    }
}

