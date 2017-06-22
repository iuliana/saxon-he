////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * This outputter is used when writing a sequence of atomic values and nodes, for
 * example, when xsl:variable is used with content and an "as" attribute. The outputter
 * builds the sequence; the concrete subclass is responsible for deciding what to do with the
 * resulting items.
 *
 * <p>This class is not used to build temporary trees. For that, the ComplexContentOutputter
 * is used.</p>
 *
 *
 * @author Michael H. Kay
 */

public abstract class SequenceWriter extends SequenceReceiver {
    private Receiver outputter = null;
    private Builder builder = null;
    private TreeModel treeModel;
    private int level = 0;
    private boolean inStartTag = false;

    public SequenceWriter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        //System.err.println("SequenceWriter init");
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     * @param item the item to be written to the sequence
     * @throws net.sf.saxon.trans.XPathException if any failure occurs while writing the item
     */

    public abstract void write(Item item) throws XPathException;

    public TreeModel getTreeModel() {
        return treeModel;
    }

    public void setTreeModel(TreeModel treeModel) {
        this.treeModel = treeModel;
    }

    /**
     * Start of a document node.
    */

    public void startDocument(int properties) throws XPathException {
        if (outputter==null) {
            createTree(((properties & ReceiverOptions.MUTABLE_TREE) != 0));
        }
        if (level++ == 0) {
            outputter.startDocument(properties);
        }
    }

    /**
     * Create a (skeletal) tree to hold a document or element node.
     * @param mutable set to true if the tree is required to support in-situ updates (other that the initial
     * sequential writing of nodes to construct the tree)
     * @throws net.sf.saxon.trans.XPathException if any error occurs while creating the tree
     */

    private void createTree(boolean mutable) throws XPathException {
        PipelineConfiguration pipe = getPipelineConfiguration();
        if (treeModel != null) {
            builder = treeModel.makeBuilder(pipe);
        } else {
            if (mutable) {
                TreeModel model = pipe.getController().getModel();
                if (model.isMutable()) {
                    builder = pipe.getController().makeBuilder();
                } else {
                    builder = new LinkedTreeBuilder(pipe);
                }
            } else {
                builder = pipe.getController().makeBuilder();
            }
        }
        builder.setPipelineConfiguration(pipe);
        builder.setSystemId(getSystemId());
        builder.setTiming(false);

        NamespaceReducer reducer = new NamespaceReducer(builder);

        ComplexContentOutputter cco = new ComplexContentOutputter(pipe);
        cco.setHostLanguage(pipe.getHostLanguage());
        cco.setReceiver(reducer);
        outputter = cco;

        outputter.setSystemId(systemId);
        outputter.setPipelineConfiguration(getPipelineConfiguration());
        outputter.open();
    }

    /**
     * Decide whether reuse of the SequenceWriter is advisable
     * @return true if reuse is considered advisable
     */

    protected boolean adviseReuse() {
        if (builder instanceof TinyBuilder) {
            TinyTree tree = ((TinyBuilder)builder).getTree();
            return tree != null && tree.getNumberOfNodes() < 20000;
        } else {
            return false;
        }
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (--level == 0) {
            outputter.endDocument();
            DocumentInfo doc = (DocumentInfo)builder.getCurrentRoot();
            // add the constructed document to the result sequence
            append(doc, 0, NodeInfo.ALL_NAMESPACES);
        }
        previousAtomic = false;
    }

    /**
    * Output an element start tag.
     * @param elemName The element name code - a code held in the Name Pool
      * @param typeCode Integer code identifying the type of this element. Zero identifies the default
 * type, that is xs:anyType
     * @param properties bit-significant flags indicating any special information
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {

        if (inStartTag) {
            startContent();
        }

        if (outputter==null) {
            createTree(((properties & ReceiverOptions.MUTABLE_TREE) != 0));
        }
        //System.err.println("SEQUENCE_WRITER startElement " + this);
        outputter.startElement(elemName, typeCode, locationId, properties);
        level++;
        inStartTag = true;
        previousAtomic = false;
    }

    /**
    * Output an element end tag.
    */

    public void endElement() throws XPathException {
        if (inStartTag) {
            startContent();
        }
        //System.err.println("SEQUENCE_WRITER endElement " + this);
        outputter.endElement();
        if (--level == 0) {
            outputter.close();
            NodeInfo element = builder.getCurrentRoot();
            append(element, 0, NodeInfo.ALL_NAMESPACES);
        }
        previousAtomic = false;
    }

    /**
    * Output a namespace declaration. <br>
    * This is added to a list of pending namespaces for the current start tag.
    * If there is already another declaration of the same prefix, this one is
    * ignored.
    * Note that unlike SAX2 startPrefixMapping(), this call is made AFTER writing the start tag.
    * @param namespaceBinding The namespace binding
    * @param properties Allows special properties to be passed if required
    * @throws net.sf.saxon.trans.XPathException if there is no start tag to write to (created using writeStartTag),
    * or if character content has been written since the start tag was written.
    */

    public void namespace(NamespaceBinding namespaceBinding, int properties)
    throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.NAMESPACE);
            o.setNodeName(new NoNamespaceName(namespaceBinding.getPrefix()));
            o.setStringValue(namespaceBinding.getURI());
            append(o, 0, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.namespace(namespaceBinding, properties);
        }
        previousAtomic = false;
    }

    /**
    * Output an attribute value. <br>
    *
     *
     * @param attName An integer code representing the name of the attribute, as held in the Name Pool
     * @param typeCode Integer code identifying the type annotation of the attribute; zero represents
     * the default type (xs:untypedAtomic)
     * @param value The value of the attribute
     * @param properties Bit significant flags for passing extra information to the serializer, e.g.
     * to disable escaping
     * @throws net.sf.saxon.trans.XPathException if there is no start tag to write to (created using writeStartTag),
    * or if character content has been written since the start tag was written.
    */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties)
    throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.ATTRIBUTE);
            o.setNodeName(attName);
            o.setStringValue(value);
            o.setTypeAnnotation(typeCode);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.attribute(attName, typeCode, value, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
    * The startContent() event is notified after all namespaces and attributes of an element
    * have been notified, and before any child nodes are notified.
    * @throws net.sf.saxon.trans.XPathException for any failure
    */

    public void startContent() throws XPathException {
        inStartTag = false;
        outputter.startContent();
        previousAtomic = false;
    }

    /**
    * Produce text content output. <BR>
    * @param s The String to be output
    * @param properties bit-significant flags for extra information, e.g. disable-output-escaping
    * @throws net.sf.saxon.trans.XPathException for any failure
    */

    public void characters(CharSequence s, int locationId, int properties) throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.TEXT);
            o.setStringValue(s.toString());
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            if (s.length() > 0) {
                if (inStartTag) {
                    startContent();
                }
                outputter.characters(s, locationId, properties);
            }
        }
        previousAtomic = false;
    }

    /**
    * Write a comment.
    */

    public void comment(CharSequence comment, int locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.COMMENT);
            o.setStringValue(comment);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.comment(comment, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
    * Write a processing instruction
    * No-op in this implementation
    */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeName(new NoNamespaceName(target));
            o.setNodeKind(Type.PROCESSING_INSTRUCTION);
            o.setStringValue(data);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.processingInstruction(target, data, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
    * Close the output
    */

    public void close() throws XPathException {
        previousAtomic = false;
        if (outputter != null) {
            outputter.close();
        }
    }

    /**
    * Append an item to the sequence, performing any necessary type-checking and conversion
    */

    public void append(/*@Nullable*/ Item item, int locationId, int copyNamespaces) throws XPathException {

        if (item==null) {
            return;
        }

        if (level==0) {
            write(item);
            previousAtomic = false;
        } else {
            if (item instanceof AtomicValue || item instanceof ObjectValue) {
                // If an atomic value is written to a tree, and the previous item was also
                // an atomic value, then add a single space to separate them
                if (previousAtomic) {
                    outputter.characters(" ", 0, 0);
                }
                outputter.characters(item.getStringValueCS(), 0, 0);
                previousAtomic = true;
            } else if (item instanceof FunctionItem) {
                XPathException err = new XPathException("Cannot write a function item to an XML tree", "XPTY0004");
                err.setLocator(getPipelineConfiguration().getSourceLocation(locationId));
                throw err;
            } else {
                NodeInfo node = (NodeInfo)item;
                if (node.getNodeKind() == Type.ATTRIBUTE && ((SimpleType)node.getSchemaType()).isNamespaceSensitive()) {
                    XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                                Err.wrap(node.getDisplayName(), Err.ATTRIBUTE));
                    err.setErrorCode((getPipelineConfiguration().getHostLanguage() == Configuration.XSLT ? "XTTE0950" : "XQTY0086"));
                    throw err;
                }
                ((NodeInfo)item).copy(outputter, (CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS), locationId);
                previousAtomic = false;
            }
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return outputter == null || outputter.usesTypeAnnotations();
    }
}

