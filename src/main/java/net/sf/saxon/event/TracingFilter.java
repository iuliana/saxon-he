////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.Whitespace;

import java.io.PrintStream;

/**
 * A filter that can be inserted into a Receiver pipeline to trace the events that pass through.
 * This class is not normally used in Saxon, but is available for diagnostics when needed.
 */
public class TracingFilter extends ProxyReceiver {

    private static int nextid = 0;
    private int id;
    private String indent = "";
    private PrintStream out = System.err;

    /**
     * Create a TracingFilter and allocate a unique Id.
     * @param nextReceiver the underlying receiver to which the events will be sent
     */

    public TracingFilter(Receiver nextReceiver) {
        super(nextReceiver);
        id = nextid++;
    }

    /**
     * Create a TracingFilter, allocate a unique Id, and supply the destination for diagnostic
     * trace messages
     * @param nextReceiver the underlying receiver to which the events will be sent
     * @param diagnosticOutput the destination for diagnostic trace messages
     */

    public TracingFilter(Receiver nextReceiver, PrintStream diagnosticOutput) {
        super(nextReceiver);
        id = nextid++;
        out = diagnosticOutput;
    }

    /**
     * Get the unique id that was allocated to this TracingFilter
     * @return the unique id (which is included in all diagnostic messages)
     */

    public int getId() {
        return id;
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

    public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
        out.println("RCVR " + id + indent + " APPEND " + item.getClass().getName());
        if (nextReceiver instanceof SequenceReceiver) {
            ((SequenceReceiver)nextReceiver).append(item, locationId, copyNamespaces);
        } else {
            super.append(item, locationId, copyNamespaces);
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " ATTRIBUTE " + nameCode.getDisplayName());
        nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " CHARACTERS " + (Whitespace.isWhite(chars) ? "(whitespace)" : ""));
        FastStringBuffer sb = new FastStringBuffer(chars.length() * 3);
        for (int i=0; i<chars.length(); i++) {
            sb.append((int)chars.charAt(i) + " ");
        }
        out.println("    \"" + sb + '\"');
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * End of document
     */

    public void close() throws XPathException {
        out.println("RCVR " + id + indent + " CLOSE");
        nextReceiver.close();
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " COMMENT");
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        out.println("RCVR " + id + indent + " END DOCUMENT");
        nextReceiver.endDocument();
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        indent = indent.substring(2);
        out.println("RCVR " + id + indent + " END ELEMENT");
        nextReceiver.endElement();
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding the namespace (prefix, uri) pair to be notified
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " NAMESPACE " +
                namespaceBinding.getPrefix() + "=" + namespaceBinding.getURI());
        nextReceiver.namespace(namespaceBinding, properties);
    }

    /**
     * Start of event stream
     */

    public void open() throws XPathException {
        out.println("RCVR " + id + indent + " OPEN");
        nextReceiver.open();
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " PROCESSING INSTRUCTION");
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */


    public void startContent() throws XPathException {
        out.println("RCVR " + id + indent + " START CONTENT");
        nextReceiver.startContent();
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        out.println("RCVR " + id + indent + " START DOCUMENT");
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the start of an element
     *
     * @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties properties of the element node
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " START ELEMENT " + nameCode.getDisplayName());
//        out.println("RCVR " + id + indent +
//                "   (Loc: sysId=" +
//                getPipelineConfiguration().getLocationProvider().getSystemId(locationId) +
//                " line=" +
//                getPipelineConfiguration().getLocationProvider().getLineNumber(locationId) + ")");
        indent = indent + "  ";
        nextReceiver.startElement(nameCode, typeCode, locationId, properties);
    }
}

