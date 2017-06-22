////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.net.URI;

/**
 * A document builder holds properties controlling how a Saxon document tree should be built, and
 * provides methods to invoke the tree construction.
 * <p/>
 * <p>This class has no public constructor.  Users should construct a DocumentBuilder
 * by calling the factory method {@link net.sf.saxon.s9api.Processor#newDocumentBuilder()}.</p>
 * <p/>
 * <p>All documents used in a single Saxon query, transformation, or validation episode must
 * be built with the same {@link Configuration}. However, there is no requirement that they
 * should use the same <code>DocumentBuilder</code>.</p>
 * <p/>
 * <p>Sharing of a <code>DocumentBuilder</code> across multiple threads is not recommended. However,
 * in the current implementation sharing a <code>DocumentBuilder</code> (once initialized) will only
 * cause problems if a <code>SchemaValidator</code> is used.</p>
 *
 * @since 9.0
 */

public class DocumentBuilder {

    private Configuration config;
    private SchemaValidator schemaValidator;
    private boolean dtdValidation;
    private boolean lineNumbering;
    private TreeModel treeModel = TreeModel.TINY_TREE;
    private WhitespaceStrippingPolicy whitespacePolicy;
    private URI baseURI;
    private XQueryExecutable projectionQuery;

    // TODO: combine the functionality of this class with that of XdmDestination

    /**
     * Create a DocumentBuilder. This is a protected constructor. Users should construct a DocumentBuilder
     * by calling the factory method {@link net.sf.saxon.s9api.Processor#newDocumentBuilder()}.
     *
     * @param config the Saxon configuration
     */

    protected DocumentBuilder(Configuration config) {
        this.config = config;
    }

    /**
     * Set the tree model to be used for documents constructed using this DocumentBuilder.
     * By default, the TinyTree is used (irrespective of the TreeModel set in the underlying
     * Configuration).
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     * {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. It can also be
     * an external object model such as {@link net.sf.saxon.option.xom.XOMObjectModel}
     * @since 9.2
     */

    public void setTreeModel(TreeModel model) {
        this.treeModel = model;
    }

    /**
     * Get the tree model to be used for documents constructed using this DocumentBuilder.
     * By default, the TinyTree is used (irrespective of the TreeModel set in the underlying
     * Configuration).
     *
     * @return the tree model in use: typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     * {@link net.sf.saxon.om.TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     * a user-defined tree model can be used.
     * @since 9.2
     */

    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Say whether line numbering is to be enabled for documents constructed using this DocumentBuilder.
     * This has the effect that the line number in the original source document is maintained in the constructed
     * tree, for each element node (and only for elements). The line number in question is generally the line number
     * on which the closing ">" of the element start tag appears.
     * <p/>
     * <p>By default, line numbers are not maintained.</p>
     * <p/>
     * <p>Errors relating to document parsing and validation will generally contain line numbers whether or not
     * this option is set, because such errors are detected during document construction.</p>
     * <p/>
     * <p>Line numbering is not available for all kinds of source: for example,
     * it is not available when loading from an existing DOM Document.</p>
     * <p/>
     * <p>The resulting line numbers are accessible to applications using the
     * XPath extension function saxon:line-number() applied to a node, or using the
     * Java method {@link net.sf.saxon.om.NodeInfo#getLineNumber()} </p>
     * <p/>
     * <p>Line numbers are maintained only for element nodes; the line number
     * returned for any other node will be that of the most recent element. For an element node, the
     * line number is generally that of the closing angle bracket at the end of the start tag
     * (this is what a SAX parser notifies)</p>
     *
     * @param option true if line numbers are to be maintained, false otherwise.
     */

    public void setLineNumbering(boolean option) {
        lineNumbering = option;
    }

    /**
     * Ask whether line numbering is enabled for documents loaded using this
     * <code>DocumentBuilder</code>.
     * <p/>
     * <p>By default, line numbering is disabled.</p>
     * <p/>
     * <p>Line numbering is not available for all kinds of source: in particular,
     * it is not available when loading from an existing XmlDocument.</p>
     * <p/>
     * <p>The resulting line numbers are accessible to applications using the
     * extension function saxon:line-number() applied to a node, or using the
     * Java method {@link net.sf.saxon.om.NodeInfo#getLineNumber()}</p>
     * <p/>
     * <p>Line numbers are maintained only for element nodes; the line number
     * returned for any other node will be that of the most recent element. For an element node, the
     * line number is generally that of the closing angle bracket at the end of the start tag
     * (this is what a SAX parser notifies)</p>
     *
     * @return true if line numbering is enabled
     */

    public boolean isLineNumbering() {
        return lineNumbering;
    }

    /**
     * Set the schemaValidator to be used. This determines whether schema validation is applied to an input
     * document and whether type annotations in a supplied document are retained. If no schemaValidator
     * is supplied, then schema validation does not take place.
     * <p/>
     * <p>This option requires the schema-aware version of the Saxon product (Saxon-EE).</p>
     * <p/>
     * <p>Since a <code>SchemaValidator</code> is serially reusable but not thread-safe, using this
     * method is not appropriate when the <code>DocumentBuilder</code> is shared between threads.</p>
     *
     * @param validator the SchemaValidator to be used
     */

    public void setSchemaValidator(SchemaValidator validator) {
        schemaValidator = validator;
    }

    /**
     * Get the SchemaValidator used to validate documents loaded using this
     * <code>DocumentBuilder</code>.
     *
     * @return the SchemaValidator if one has been set; otherwise null.
     */
    public SchemaValidator getSchemaValidator() {
        return schemaValidator;
    }

    /**
     * Set whether DTD validation should be applied to documents loaded using this
     * <code>DocumentBuilder</code>.
     * <p/>
     * <p>By default, no DTD validation takes place.</p>
     *
     * @param option true if DTD validation is to be applied to the document
     */

    public void setDTDValidation(boolean option) {
        dtdValidation = option;
    }

    /**
     * Ask whether DTD validation is to be applied to documents loaded using this <code>DocumentBuilder</code>
     *
     * @return true if DTD validation is to be applied
     */

    public boolean isDTDValidation() {
        return dtdValidation;
    }

    /**
     * Set the whitespace stripping policy applied when loading a document
     * using this <code>DocumentBuilder</code>.
     * <p/>
     * <p>By default, whitespace text nodes appearing in element-only content
     * are stripped, and all other whitespace text nodes are retained.</p>
     *
     * @param policy the policy for stripping whitespace-only text nodes from
     *               source documents
     */

    public void setWhitespaceStrippingPolicy(WhitespaceStrippingPolicy policy) {
        whitespacePolicy = policy;
    }

    /**
     * Get the white whitespace stripping policy applied when loading a document
     * using this <code>DocumentBuilder</code>.
     *
     * @return the policy for stripping whitespace-only text nodes
     */

    public WhitespaceStrippingPolicy getWhitespaceStrippingPolicy() {
        return whitespacePolicy;
    }

    /**
     * Set the base URI of a document loaded using this <code>DocumentBuilder</code>.
     * <p/>
     * <p>This is used for resolving any relative URIs appearing
     * within the document, for example in references to DTDs and external entities.</p>
     * <p/>
     * <p>This information is required when the document is loaded from a source that does not
     * provide an intrinsic URI, notably when loading from a Stream or a DOMSource. The value is
     * ignored when loading from a source that does have an intrinsic base URI.</p>
     *
     * @param uri the base URI of documents loaded using this <code>DocumentBuilder</code>. This
     *            must be an absolute URI.
     * @throws IllegalArgumentException if the baseURI supplied is not an absolute URI
     */

    public void setBaseURI(URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Supplied base URI must be absolute");
        }
        baseURI = uri;
    }

    /**
     * Get the base URI of documents loaded using this DocumentBuilder when no other URI is available.
     *
     * @return the base URI to be used, or null if no value has been set.
     */

    public URI getBaseURI() {
        return baseURI;
    }

    /**
     * Set a compiled query to be used for implementing document projection. The effect of using
     * this option is that the tree constructed by the DocumentBuilder contains only those parts
     * of the source document that are needed to answer this query. Running this query against
     * the projected document should give the same results as against the raw document, but the
     * projected document typically occupies significantly less memory. It is permissible to run
     * other queries against the projected document, but unless they are carefully chosen, they
     * will give the wrong answer, because the document being used is different from the original.
     * <p>The query should be written to use the projected document as its initial context item.
     * For example, if the query is <code>//ITEM[COLOR='blue')</code>, then only <code>ITEM</code>
     * elements and their <code>COLOR</code> children will be retained in the projected document.</p>
     * <p>This facility is only available in Saxon-EE; if the facility is not available,
     * calling this method has no effect.</p>
     * @param query the compiled query used to control document projection
     * @since 9.3
     */

    public void setDocumentProjectionQuery(XQueryExecutable query) {
        this.projectionQuery = query;
    }

    /**
     * Get the compiled query to be used for implementing document projection.
     * @return the query set using {@link #setDocumentProjectionQuery} if this
     * has been called, or null otherwise
     * @since 9.3. In 9.4 the unused and undocumented first argument is removed.
     */

    public XQueryExecutable getDocumentProjectionQuery() {
        return this.projectionQuery;
    }

    /**
     * Load an XML document, to create a tree representation of the document in memory.
     *
     * @param source A JAXP Source object identifying the source of the document. This can always be
     *   a {@link javax.xml.transform.stream.StreamSource} or a {@link javax.xml.transform.sax.SAXSource}.
     *   Some kinds of Source are consumed by this method, and should only be used once.
     *   <p/>
     *   <p>If a SAXSource is supplied, the XMLReader held within the SAXSource may be modified (by setting
     *   features and properties) to reflect the options selected on this DocumentBuilder.</p>
     *   <p>An instance of {@link javax.xml.transform.dom.DOMSource} is accepted provided that the Saxon support
     *   code for DOM (in saxon9-dom.jar) is on the classpath.</p>
     *   <p/>
     *   <p>If the source is an instance of {@link net.sf.saxon.om.NodeInfo} then the subtree rooted at this node
     *   will be copied (applying schema validation if requested) to create a new tree.</p>
     *   <p/>
     *   <p>Saxon also accepts an instance of {@link javax.xml.transform.stax.StAXSource} or
     *  {@link net.sf.saxon.pull.PullSource}, which can be used to supply a document that is to be parsed
     *  using a StAX parser.</p>
     *   <p>(9.2) This method no longer accepts an instance of {@link net.sf.saxon.lib.AugmentedSource}, because of
     *  confusion over interactions between the properties of the AugmentedSource and the properties
     *  of this DocumentBuilder.</p>
     * @return An <code>XdmNode</code>. This will be
     *         the document node at the root of the tree of the resulting in-memory document.
     * @throws NullPointerException if the source argument is null
     * @throws IllegalArgumentException if the kind of source is not recognized
     * @throws SaxonApiException if any other failure occurs building the document, for example
     * a parsing error
     */

    public XdmNode build(/*@Nullable*/ Source source) throws SaxonApiException {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (source instanceof AugmentedSource) {
            throw new IllegalArgumentException("AugmentedSource not accepted");
        }
        ParseOptions options = new ParseOptions(config.getParseOptions());
        options.setDTDValidationMode(dtdValidation ? Validation.STRICT : Validation.STRIP);
        if (schemaValidator != null) {
            options.setSchemaValidationMode(schemaValidator.isLax() ? Validation.LAX : Validation.STRICT);
            if (schemaValidator.getDocumentElementName() != null) {
                QName qn = schemaValidator.getDocumentElementName();
                options.setTopLevelElement(new FingerprintedQName(
                        qn.getPrefix(), qn.getNamespaceURI(), qn.getLocalName()));
            }
            if (schemaValidator.getDocumentElementType() != null) {
                options.setTopLevelType(schemaValidator.getDocumentElementType());
            }
        }
        if (treeModel != null) {
            options.setModel(treeModel);
        }
        if (whitespacePolicy != null) {
            int option = whitespacePolicy.ordinal();
            if (option == Whitespace.XSLT) {
                options.setStripSpace(Whitespace.NONE);
                options.addFilter(whitespacePolicy.makeStripper());
            } else {
                options.setStripSpace(option);
            }
        }
        options.setLineNumbering(lineNumbering);
        if (source.getSystemId() == null && baseURI != null) {
            source.setSystemId(baseURI.toString());
        }
        if (projectionQuery != null) {
            XQueryExpression exp = projectionQuery.getUnderlyingCompiledQuery();
            PathMap map = exp.getPathMap();
                PathMap.PathMapRoot contextRoot = map.getContextDocumentRoot();
                if (contextRoot != null) {
                    if (contextRoot.hasUnknownDependencies()) {
                        // No action (no document projection takes place)
                    } else {
                        options.addFilter(config.makeDocumentProjector(contextRoot));
                    }
                } else {
                    // No action, query does not access the context item
                }
        }
        try {
            NodeInfo doc = config.buildDocument(source, options);
            return new XdmNode(doc);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Build a document from a supplied XML file
     * @param file the supplied file
     * @return the XdmNode representing the root of the document tree
     * @throws SaxonApiException if any failure occurs retrieving or parsing the document
     */

    public XdmNode build(File file) throws SaxonApiException {
        return build(new StreamSource(file));
    }

    /**
     * Get an {@link org.xml.sax.ContentHandler} that may be used to build the document programmatically.
     * @return a newly constructed {@link BuildingContentHandler}, which implements the <code>ContentHandler</code>
     * interface. If schema validation has been requested for this <code>DocumentBuilder</code>, then the document constructed
     * using the <code>ContentHandler</code> will be validated as it is written.
     * <p>Note that the returned <code>ContentHandler</code> expects namespace scopes to be indicated
     * explicitly by calls to {@link org.xml.sax.ContentHandler#startPrefixMapping} and
     * {@link org.xml.sax.ContentHandler#endPrefixMapping}.</p>
     * <p>If the stream of events supplied to the <code>ContentHandler</code> does not constitute
     * a well formed (and namespace-well-formed) document, the effect is undefined; Saxon may fail
     * to detect the error, and construct an unusable tree. </p>
     * @throws SaxonApiException if any failure occurs
     * @since 9.3
     */

    public BuildingContentHandler newBuildingContentHandler() throws SaxonApiException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = treeModel.makeBuilder(pipe);
        builder.setLineNumbering(lineNumbering);
        Receiver r = builder;
        r = new NamespaceReducer(r);
        if (schemaValidator != null) {
            r = schemaValidator.getReceiver(config);
            r.setPipelineConfiguration(pipe);
            if (r instanceof ProxyReceiver) {
                ((ProxyReceiver)r).setUnderlyingReceiver(builder);
            }
        }
        return new BuildingContentHandlerImpl(r, builder);
    }

    /**
     * Private implementation of BuildingContentHandler
     */

    private static class BuildingContentHandlerImpl extends ReceivingContentHandler
        implements BuildingContentHandler  {

        private Builder builder;

        public BuildingContentHandlerImpl(Receiver r, Builder b) {
            setReceiver(r);
            setPipelineConfiguration(r.getPipelineConfiguration());
            this.builder = b;
        }

        public XdmNode getDocumentNode() throws SaxonApiException {
            return new XdmNode(builder.getCurrentRoot());
        }
    }

    /**
     * Get an {@link javax.xml.stream.XMLStreamWriter} that may be used to build the document programmatically.
     * @return a newly constructed {@link BuildingStreamWriter}, which implements the <code>XMLStreamWriter</code>
     * interface. If schema validation has been requested for this <code>DocumentBuilder</code>, then the document constructed
     * using the <code>XMLStreamWriter</code> will be validated as it is written.
     * <p>If the stream of events supplied to the <code>XMLStreamWriter</code> does not constitute
     * a well formed (and namespace-well-formed) document, the effect is undefined; Saxon may fail
     * to detect the error, and construct an unusable tree. </p>
     * @throws SaxonApiException if any failure occurs
     * @since 9.3
     */

    public BuildingStreamWriterImpl newBuildingStreamWriter() throws SaxonApiException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = treeModel.makeBuilder(pipe);
        builder.setLineNumbering(lineNumbering);
        Receiver r = builder;
        r = new NamespaceReducer(r);
        if (schemaValidator != null) {
            r = schemaValidator.getReceiver(config);
            r.setPipelineConfiguration(pipe);
            if (r instanceof ProxyReceiver) {
                ((ProxyReceiver)r).setUnderlyingReceiver(builder);
            }
        }
        return new BuildingStreamWriterImpl(r, builder);
    }

     /**
     * Create a node by wrapping a recognized external node from a supported object model.
     *
     * <p>If the supplied object implements the {@link net.sf.saxon.om.NodeInfo} interface then it
     * will be wrapped as an <code>XdmNode</code> without copying and without change. The <code>NodeInfo</code>
     * must have been created using a {@link net.sf.saxon.Configuration} compatible
     * with the one used by this <code>Processor</code> (specifically, one that uses the same
     * {@link net.sf.saxon.om.NamePool})</p>
     *
     * <p>To wrap nodes from other object models, such as DOM, the support module for the external object
     * model must be on the class path and registered with the Saxon configuration. The support modules
     * for DOM, JDOM, DOM4J and XOM are registered automatically if they can be found on the classpath.</p>
     *
     * <p>It is best to avoid calling this method repeatedly to wrap different nodes in the same document.
     * Each such wrapper conceptually creates a new XDM tree instance with its own identity. Although the
     * memory is shared, operations that rely on node identity might not have the expected result. It is
     * best to create a single wrapper for the document node, and then to navigate to the other nodes in the
     * tree using S9API interfaces.</p>
     *
     * @param node the node in the external tree representation. Either an instance of
     * {@link net.sf.saxon.om.NodeInfo}, or an instances of a node in an external object model.
     * Nodes in other object models (such as DOM, JDOM, etc) are recognized only if
     * the support module for the external object model is known to the Configuration.
     * @return the supplied node wrapped as an XdmNode
     * @throws IllegalArgumentException if the type of object supplied is not recognized. This may be because
     * node was created using a different Saxon Processor, or because the required code for the external
     * object model is not on the class path
     */

    public XdmNode wrap(Object node) throws IllegalArgumentException {
         if (node instanceof NodeInfo) {
             NodeInfo nodeInfo = (NodeInfo)node;
             if (nodeInfo.getConfiguration().isCompatible(config)) {
                return new XdmNode((NodeInfo)node);
             } else {
                 throw new IllegalArgumentException("Supplied NodeInfo was created using a different Configuration");
             }
         } else {
             try {
                 JPConverter converter = JPConverter.allocate(node.getClass(), config);
                 NodeInfo nodeInfo = (NodeInfo)converter.convert(node, new EarlyEvaluationContext(config, null));
                 return (XdmNode)XdmItem.wrapItem(nodeInfo);
             } catch (XPathException e) {
                 throw new IllegalArgumentException(e.getMessage());
             }
         }
    }


}

