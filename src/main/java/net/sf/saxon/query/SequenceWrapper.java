////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * This class can be used in a push pipeline: it accepts any sequence as input, and generates
 * a document in which the items of the sequence are wrapped by elements containing information about
 * the types of the items in the input sequence.
 */

public class SequenceWrapper extends SequenceReceiver {

    public static final String RESULT_NS = QueryResult.RESULT_NS;

    private Receiver out;
    private int depth = 0;

    //@SuppressWarnings({"FieldCanBeLocal"})
    private FingerprintedQName resultSequence;
    private FingerprintedQName resultDocument;
    private FingerprintedQName resultElement;
    private FingerprintedQName resultAttribute;
    private FingerprintedQName resultText;
    private FingerprintedQName resultComment;
    private FingerprintedQName resultPI;
    private FingerprintedQName resultNamespace;
    private FingerprintedQName resultAtomicValue;
    private FingerprintedQName xsiType;

    /**
     * Create a sequence wrapper. This creates an XML representation of the items sent to destination
     * in which the types of all items are made explicit
     * @param destination the sequence being wrapped
     */

    public SequenceWrapper(Receiver destination) {
        super(destination.getPipelineConfiguration());
        out = destination;
        // out = new TracingFilter(out);
    }

    public Receiver getDestination() {
        return out;
    }

    public void open() throws XPathException {

        resultSequence = new FingerprintedQName("result", RESULT_NS, "sequence");
        resultDocument = new FingerprintedQName("result", RESULT_NS, "document");
        resultElement = new FingerprintedQName("result", RESULT_NS, "element");
        resultAttribute = new FingerprintedQName("result", RESULT_NS, "attribute");
        resultText = new FingerprintedQName("result", RESULT_NS, "text");
        resultComment = new FingerprintedQName("result", RESULT_NS, "comment");
        resultPI = new FingerprintedQName("result", RESULT_NS, "processing-instruction");
        resultNamespace = new FingerprintedQName("result", RESULT_NS, "namespace");
        resultAtomicValue = new FingerprintedQName("result", RESULT_NS, "atomic-value");
        xsiType = new FingerprintedQName("xsi", NamespaceConstant.SCHEMA_INSTANCE, "type");

        out.open();
        out.startDocument(0);

        out.startElement(resultSequence, Untyped.getInstance(), 0, 0);
        out.namespace(new NamespaceBinding("result", RESULT_NS), 0);
        out.namespace(new NamespaceBinding("xs", NamespaceConstant.SCHEMA), 0);
        out.namespace(new NamespaceBinding("xsi", NamespaceConstant.SCHEMA_INSTANCE), 0);
        out.startContent();

    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        out.startElement(resultDocument, Untyped.getInstance(), 0, 0);
        out.startContent();
        depth++;
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        out.endElement();
        depth--;
    }

    /**
     * Notify the start of an element
     *
     * @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        if (depth++ == 0) {
            out.startElement(resultElement, Untyped.getInstance(), 0, 0);
            out.startContent();
        }
        out.startElement(nameCode, typeCode, locationId, properties);
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        out.endElement();
        if (--depth == 0) {
            out.endElement();   // close the wrapping element
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param attName   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        if (depth==0) {
            out.startElement(resultAttribute, Untyped.getInstance(), 0, 0);
            if (!attName.isInNamespace("")) {
                out.namespace(attName.getNamespaceBinding(), 0);
            }
            out.attribute(attName, typeCode, value, locationId, properties);
            out.startContent();
            out.endElement();
        } else {
            out.attribute(attName, typeCode, value, locationId, properties);
        }
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        if (depth == 0) {
            out.startElement(resultNamespace, Untyped.getInstance(), 0, 0);
            out.namespace(namespaceBinding, properties);
            out.startContent();
            out.endElement();
        } else {
            out.namespace(namespaceBinding, properties);
        }
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (depth==0) {
            out.startElement(resultText, Untyped.getInstance(), 0, 0);
            out.startContent();
            out.characters(chars, locationId, properties);
            out.endElement();
        } else {
            out.characters(chars, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        if (depth==0) {
            out.startElement(resultComment, Untyped.getInstance(), 0, 0);
            out.startContent();
            out.comment(chars, locationId, properties);
            out.endElement();
        } else {
            out.comment(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (depth==0) {
            out.startElement(resultPI, Untyped.getInstance(), 0, 0);
            out.startContent();
            out.processingInstruction(target, data, locationId, properties);
            out.endElement();
        } else {
            out.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    public void append(/*@NotNull*/ Item item, int locationId, int copyNamespaces) throws XPathException {
        if (item instanceof FunctionItem) {
            throw new XPathException("Wrapped output: cannot display a function item");
        } else if (item instanceof ObjectValue) {
            throw new XPathException("Wrapped output: cannot display an external Java object");
        } else if (item instanceof AtomicValue) {
            final NamePool pool = getNamePool();
            out.startElement(resultAtomicValue, Untyped.getInstance(), 0, 0);
            AtomicType type = (AtomicType)((AtomicValue)item).getItemType();
            int nameCode = type.getNameCode();
            String prefix = pool.getPrefix(nameCode);
            String localName = pool.getLocalName(nameCode);
            String uri = pool.getURI(nameCode);
            if (prefix.length() == 0) {
                prefix = pool.suggestPrefixForURI(uri);
                if (prefix == null) {
                    prefix = "p" + uri.hashCode();
                }
            }
            String displayName = prefix + ':' + localName;
            out.namespace(new NamespaceBinding(prefix, uri), 0);
            out.attribute(xsiType, BuiltInAtomicType.UNTYPED_ATOMIC, displayName, 0, 0);
            out.startContent();
            out.characters(item.getStringValue(), 0, 0);
            out.endElement();
        } else {
            ((NodeInfo)item).copy(this, (CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS), locationId);
        }
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */

    public void startContent() throws XPathException {
        out.startContent();
    }

    /**
     * Notify the end of the event stream
     */

    public void close() throws XPathException {
        out.endElement();   // close the result:sequence element
        out.endDocument();
        out.close();
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
}

