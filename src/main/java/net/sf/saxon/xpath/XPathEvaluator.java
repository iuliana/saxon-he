////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DOMNodeWrapper;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.sxpath.SimpleContainer;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.tree.wrapper.SpaceStrippedDocument;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.Whitespace;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.*;
import java.io.File;
import java.util.List;

/**
  * <p>XPathEvaluator implements the JAXP API for standalone XPath processing (that is,
  * executing XPath expressions in the absence of an XSLT stylesheet). It is an implementation
  * of the JAXP 1.3 XPath interface, with additional methods provided (a) for backwards
  * compatibility (b) to give extra control over the XPath evaluation, and (c) to support
  * XPath 2.0.</p>
  *
  * <p>It is intended to evolve this so that it only supports the JAXP style of operation.
  * Some of the methods are therefore marked as deprecated in this release, and will be
  * dropped in a future release.</p>
  *
  * <p>For an alternative XPath API, offering more direct access to Saxon capabilities,
  * see {@link net.sf.saxon.sxpath.XPathEvaluator}.</p>
  *
  * <p>Note that the <code>XPathEvaluator</code> links to a Saxon {@link Configuration}
  * object. By default a new <code>Configuration</code> is created automatically. In many
  * applications, however, it is desirable to share a configuration. The default configuration
  * is not schema aware. All source documents used by XPath expressions under this evaluator
  * must themselves be built using the <code>Configuration</code> used by this evaluator.</p>
  *
  * @author Michael H. Kay
  */

public class XPathEvaluator implements XPath {

    private Configuration config;
    /*@Nullable*/ private NodeInfo contextNode = null;
    private JAXPXPathStaticContext staticContext;
    private boolean stripSpace = false;

    /**
     * Default constructor. Creates an XPathEvaluator with Configuration appropriate
     * to the version of the Saxon software being run.
     */

    public XPathEvaluator() {
        this(Configuration.newConfiguration());
    }

    /**
     * Construct an XPathEvaluator with a specified configuration.
     * @param config the configuration to be used. If schema-aware XPath expressions are to be used,
     * this must be an EnterpriseConfiguration.
     */
    public XPathEvaluator(/*@NotNull*/ Configuration config) {
        this.config = config;
        staticContext = new JAXPXPathStaticContext(config);
    }

    /**
    * Construct an XPathEvaluator to process a particular source document. This is equivalent to
    * using the default constructor and immediately calling setSource().
    * @param source The source document (or a specific node within it).
    */

    public XPathEvaluator(/*@NotNull*/ Source source) throws net.sf.saxon.trans.XPathException {
        if (source instanceof NodeInfo) {
            config = ((NodeInfo)source).getDocumentRoot().getConfiguration();
        } else {
            config = new Configuration();
        }
        staticContext = new JAXPXPathStaticContext(config);
        setSource(source);
    }

    /**
     * Set the XPath language level to be supported. The values recognized are
     * the strings "1.0", "2.0", and "3.0". The default is "2.0". The value "1.0"
     * does not disable 2.0 features, but switches on 1.0 compatibility mode. The value
     * "3.0" is necessary to enable XPath 3.0 features. Setting the value to "3.0"
     * switches backwards compatibility off.
     * @param level one of "1.0", "2.0", or "3.0".
     * @since 9.5
     */

    public void setXPathLanguageLevel(String level) {
        if ("1.0".equals(level)) {
            staticContext.setBackwardsCompatibilityMode(true);
            staticContext.setXPathLanguageLevel(DecimalValue.TWO);
        } else if ("2.0".equals(level)) {
            staticContext.setBackwardsCompatibilityMode(false);
            staticContext.setXPathLanguageLevel(DecimalValue.TWO);
        } else if ("3.0".equals(level)) {
            staticContext.setBackwardsCompatibilityMode(false);
            staticContext.setXPathLanguageLevel(DecimalValue.THREE);
        } else {
            throw new IllegalArgumentException(level);
        }
    }

    /**
     * Get the Configuration used by this XPathEvaluator
     * @return the Configuration used by this XPathEvaluator
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Indicate whether all whitespace text nodes in the source document are to be
     * removed. This option has no effect unless it is called before the call on setSource(),
     * and unless the Source supplied to setSource() is a SAXSource or StreamSource.
     * @param strip True if all whitespace text nodes are to be stripped from the source document,
     * false otherwise. The default if the method is not called is false.
     * @deprecated since 8.9. The preferred way to define options for the way in which source
     * documents are built is to use the class {@link net.sf.saxon.lib.AugmentedSource} for any
     * of the methods expecting a {@link Source} object.
    */

    public void setStripSpace(boolean strip) {
        stripSpace = strip;
    }

    /**
     * Supply a document against which XPath expressions are to be executed, converting it to a
     * Saxon NodeInfo object.
     * <p>If the supplied source is a <code>NodeInfo</code>, it is returned unchanged.</p>
     * <p>If the supplied source is a <code>DOMSource</code>, the result is a Saxon <code>NodeInfo</code>
     * wrapper around the DOM Node contained by the DOMSource.</p>
     * <p>In all other cases, the result is a document node, and is the same as the result of calling
     * {@link Configuration#buildDocument(javax.xml.transform.Source)} with the same argument;
     * except that when whitespace stripping has been requested using {@link #setStripSpace(boolean)},
     * this request is passed on.</p>
     * <p>Despite the name of this method, it does not change the state of the <code>XPathEvaluator</code>
     * in any way.</p>
     * @param source Any javax.xml.transform.Source object representing the document against
     * which XPath expressions will be executed. Note that a Saxon {@link net.sf.saxon.om.DocumentInfo DocumentInfo}
     * (indeed any {@link net.sf.saxon.om.NodeInfo NodeInfo})
     * can be used as a Source. To use a third-party DOM Document as a source, create an instance of
     * {@link javax.xml.transform.dom.DOMSource DOMSource} to wrap it.
     *  <p>The Source object supplied also determines the initial setting
     * of the context item. In most cases the context node will be the root of the supplied document;
     * however, if a NodeInfo or DOMSource is supplied it can be any node in the document. </p>
     * @return the NodeInfo of the start node in the resulting document object.
     * @throws XPathException if the supplied Source is a NodeInfo object that was built using an incompatible
     * Configuration (that is, a Configuration using a different NamePool). Also, if any error occurs parsing
     * the document supplied as the Source.
    */

    public NodeInfo setSource(/*@NotNull*/ Source source) throws net.sf.saxon.trans.XPathException {
        if (source instanceof DOMSource) {
            Node node = ((DOMSource)source).getNode();
            String baseURI = source.getSystemId();
            DocumentWrapper documentWrapper = new DocumentWrapper(node.getOwnerDocument(), baseURI, config);
            DOMNodeWrapper nodeWrapper = documentWrapper.wrap(node);
            if (stripSpace) {
                SpaceStrippedDocument sdoc = new SpaceStrippedDocument(documentWrapper, AllElementsSpaceStrippingRule.getInstance());
                return sdoc.wrap(nodeWrapper);
            } else {
                return nodeWrapper;
            }
        } else if (source instanceof NodeInfo) {
            NodeInfo origin = (NodeInfo)source;
            if (!origin.getConfiguration().isCompatible(config)) {
                throw new net.sf.saxon.trans.XPathException(
                        "Supplied node must be built using the same or a compatible Configuration",
                        SaxonErrorCode.SXXP0004);
            }
            if (stripSpace) {
                SpaceStrippedDocument sdoc = new SpaceStrippedDocument(origin.getDocumentRoot(), AllElementsSpaceStrippingRule.getInstance());
                return sdoc.wrap(origin);
            } else {
                return origin;
            }
        } else {
            ParseOptions options = new ParseOptions();
            if (stripSpace) {
                options.setStripSpace(Whitespace.ALL);
            }
            return config.buildDocument(source, options);
        }
    }

    /**
     * Set the static context for compiling XPath expressions. This provides control over the
     * environment in which the expression is compiled, for example it allows namespace prefixes to
     * be declared, variables to be bound and functions to be defined. For most purposes, the static
     * context can be defined by providing and tailoring an instance of the JAXPXPathStaticContext class.
     * Until this method is called, a default static context is used, in which no namespaces are defined
     * other than the standard ones (xml, xslt, and saxon), and no variables or functions (other than the
     * core XPath functions) are available.
     * @param context the static context
     * @throws IllegalArgumentException if the supplied static context uses a different and incompatible
     * Configuration from the one used in this XPathEvaluator
    */

    public void setStaticContext(/*@NotNull*/ JAXPXPathStaticContext context) {
        if (!config.isCompatible(context.getConfiguration())) {
            throw new IllegalArgumentException("Supplied static context uses a different and incompatible Configuration");
        }
        staticContext = context;
    }

    /**
     * Get the current static context
     * @return the static context
    */

    public JAXPXPathStaticContext getStaticContext() {
        return staticContext;
    }

    /**
     * Get the executable
     * @return the executable
     */

//    public Executable getExecutable() {
//        return staticContext.getExecutable();
//    }

    /**
     * Prepare an XPath expression for subsequent evaluation.
     * @param expression The XPath expression to be evaluated, supplied as a string.
     * @return an XPathExpression object representing the prepared expression
     * @throws net.sf.saxon.trans.XPathException if the syntax of the expression is wrong, or if it references namespaces,
     * variables, or functions that have not been declared.
     * @deprecated since Saxon 8.9 - use {@link #compile(String)}
    */

    /*@NotNull*/ public XPathExpressionImpl createExpression(String expression) throws net.sf.saxon.trans.XPathException {
        return createExpressionInternal(expression);
    }

    /*@NotNull*/ private XPathExpressionImpl createExpressionInternal(String expression) throws net.sf.saxon.trans.XPathException {
        Executable exec = new Executable(getConfiguration());
        SimpleContainer container = new SimpleContainer(exec);
        Expression exp = ExpressionTool.make(expression, staticContext, container, 0, -1, 1, null);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, exec);
        visitor.setExecutable(exec);
        final ExpressionVisitor.ContextItemType contextItemType = new ExpressionVisitor.ContextItemType(Type.ITEM_TYPE, true);
        exp = visitor.typeCheck(exp, contextItemType);
        exp = visitor.optimize(exp, contextItemType);
        SlotManager map = staticContext.getConfiguration().makeSlotManager();
        ExpressionTool.allocateSlots(exp, 0, map);
        exp.setContainer(container);
        XPathExpressionImpl xpe = new XPathExpressionImpl(exp, exec);
        xpe.setStackFrameMap(map);
        if (contextNode != null) {
            xpe.privatelySetContextNode(contextNode);
        }
        return xpe;
    }

    /**
     * Set the context node. This provides the context node for any expressions executed after this
     * method is called, including expressions that were prepared before it was called.
     * @param node The node to be used as the context node. The node must be within a tree built using
     * the same Saxon {@link Configuration} as used by this XPathEvaluator.
     * @deprecated since Saxon 8.9 - use the various method defined in the JAXP interface definition,
     * which allow a NodeInfo object to be supplied as the value of the Source argument
     * @throws IllegalArgumentException if the supplied node was built using the wrong Configuration
    */

    public void setContextNode(/*@NotNull*/ NodeInfo node) {
        if (!node.getConfiguration().isCompatible(config)) {
            throw new IllegalArgumentException(
                    "Supplied node must be built using the same or a compatible Configuration");
        }
        contextNode = node;
    }

    public void reset() {
        contextNode = null;
        stripSpace = false;
        staticContext = new JAXPXPathStaticContext(config);
    }

    /**
     * Set XPath 1.0 compatibility mode on or off (by default, it is false). This applies
     * to any XPath expression compiled while this option is in force.
     * @param compatible true if XPath 1.0 compatibility mode is to be set to true, false
     * if it is to be set to false.
     */

    public void setBackwardsCompatible(boolean compatible) {
        staticContext.setBackwardsCompatibilityMode(compatible);
    }

    /**
     * Get the value of XPath 1.0 compatibility mode
     * @return true if XPath 1.0 compatibility mode is set
     */

    public boolean isBackwardsCompatible() {
        return staticContext.isInBackwardsCompatibleMode();
    }

    /**
     * Set the resolver for XPath variables
     * @param xPathVariableResolver a resolver for variables
     */

    public void setXPathVariableResolver(XPathVariableResolver xPathVariableResolver) {
        staticContext.setXPathVariableResolver(xPathVariableResolver);
    }

    /**
     * Get the resolver for XPath variables
     * @return the resolver, if one has been set
     */
    public XPathVariableResolver getXPathVariableResolver() {
        return staticContext.getXPathVariableResolver();
    }

    /**
     * Set the resolver for XPath functions
     * @param xPathFunctionResolver a resolver for XPath function calls
     */

    public void setXPathFunctionResolver(XPathFunctionResolver xPathFunctionResolver) {
        staticContext.setXPathFunctionResolver(xPathFunctionResolver);
    }

    /**
     * Get the resolver for XPath functions
     * @return the resolver, if one has been set
     */

    /*@Nullable*/ public XPathFunctionResolver getXPathFunctionResolver() {
        return staticContext.getXPathFunctionResolver();
    }

    /**
     * Set the namespace context to be used.
     * @param namespaceContext The namespace context
     */

    public void setNamespaceContext(NamespaceContext namespaceContext) {
        staticContext.setNamespaceContext(namespaceContext);
    }

    /**
     * Get the namespace context, if one has been set using {@link #setNamespaceContext}
     * @return the namespace context if set, or null otherwise
     */

    public NamespaceContext getNamespaceContext() {
        return staticContext.getNamespaceContext();
    }

    /**
     * Import a schema. This is possible only if Saxon-EE is being used,
     * and if the Configuration is an EnterpriseConfiguration. Having imported a schema, the types
     * defined in that schema become part of the static context.
     * @param source A Source object identifying the schema document to be loaded
     * @throws net.sf.saxon.type.SchemaException if the schema contained in this document is invalid
     * @throws UnsupportedOperationException if the configuration is not schema-aware
     */

    public void importSchema(Source source) throws SchemaException {
        staticContext.importSchema(source);
        staticContext.setSchemaAware(true);
    }

    /**
     * Compile an XPath 2.0 expression
     * @param expr the XPath 2.0 expression to be compiled, as a string
     * @return the compiled form of the expression
     * @throws XPathExpressionException if there are any static errors in the expression.
     * Note that references to undeclared variables are not treated as static errors, because
     * variables are not pre-declared using this API.
     */
    /*@NotNull*/ public XPathExpression compile(/*@Nullable*/ String expr) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        try {
            return createExpressionInternal(expr);
        } catch (net.sf.saxon.trans.XPathException e) {
            throw new XPathExpressionException(e);
        }
    }

    /**
     * Single-shot method to compile and execute an XPath 2.0 expression.
     * @param expr The XPath 2.0 expression to be compiled and executed
     * @param node The context node for evaluation of the expression.
     *
     * <p>This may be a NodeInfo object, representing a node in Saxon's native
     * implementation of the data model, or it may be a node in any supported
     * external object model: DOM, JDOM, DOM4J, or XOM, or any other model for
     * which support has been configured in the Configuration. Note that the
     * supporting libraries for the chosen model must be on the class path.</p>
     *
     * <p><b>Contrary to the interface specification, Saxon does not supply an empty
     * document when the value is null. This is because Saxon supports multiple object models,
     * and it's unclear what kind of document node would be appropriate. Instead, Saxon uses
     * the node supplied to the {@link #setContextNode} method if available, and if none
     * is available, executes the XPath expression with the context item undefined.</p></p>
     *
     * @param qName The type of result required. For details, see
     *  {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @return the result of evaluating the expression, returned as described in
     *  {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @throws XPathExpressionException if any static or dynamic error occurs
     * in evaluating the expression.
     */

    public Object evaluate(String expr, Object node, QName qName) throws XPathExpressionException {
        XPathExpression exp = compile(expr);
        return exp.evaluate(node, qName);
    }

    /**
     * Single-shot method to compile an execute an XPath 2.0 expression, returning
     * the result as a string.
     * @param expr The XPath 2.0 expression to be compiled and executed
     * @param node The context node for evaluation of the expression
     *
     * <p>This may be a NodeInfo object, representing a node in Saxon's native
     * implementation of the data model, or it may be a node in any supported
     * external object model: DOM, JDOM, DOM4J, or XOM, or any other model for
     * which support has been configured in the Configuration. Note that the
     * supporting libraries for the chosen model must be on the class path.</p>
     *
     * <p><b>Contrary to the interface specification, Saxon does not supply an empty
     * document when the value is null. This is because Saxon supports multiple object models,
     * and it's unclear what kind of document node would be appropriate. Instead, Saxon uses
     * the node supplied to the {@link #setContextNode} method if available, and if none
     * is available, executes the XPath expression with the context item undefined.</p></p>
     * @return the result of evaluating the expression, converted to a string as if
     * by calling the XPath string() function
     * @throws XPathExpressionException if any static or dynamic error occurs
     * in evaluating the expression.
     */

    public String evaluate(String expr, Object node) throws XPathExpressionException {
        XPathExpression exp = compile(expr);
        return exp.evaluate(node);
    }

    /**
     * Single-shot method to parse and build a source document, and
     * compile an execute an XPath 2.0 expression, against that document
     * @param expr The XPath 2.0 expression to be compiled and executed
     * @param inputSource The source document: this will be parsed and built into a tree,
     * and the XPath expression will be executed with the root node of the tree as the
     * context node.
     * @param qName The type of result required. For details, see
     *  {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @return the result of evaluating the expression, returned as described in
     *  {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @throws XPathExpressionException if any static or dynamic error occurs
     * in evaluating the expression.
     * @throws NullPointerException if any of the three arguments is null
     */

    public Object evaluate(/*@Nullable*/ String expr, /*@Nullable*/ InputSource inputSource, /*@Nullable*/ QName qName) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        if (qName == null) {
            throw new NullPointerException("qName");
        }
        XPathExpression exp = compile(expr);
        return exp.evaluate(inputSource, qName);
    }

    /**
     * Single-shot method to parse and build a source document, and
     * compile an execute an XPath 2.0 expression, against that document,
     * returning the result as a string
     * @param expr The XPath 2.0 expression to be compiled and executed
     * @param inputSource The source document: this will be parsed and built into a tree,
     * and the XPath expression will be executed with the root node of the tree as the
     * context node
     * @return the result of evaluating the expression, converted to a string as
     * if by calling the XPath string() function
     * @throws XPathExpressionException if any static or dynamic error occurs
     * in evaluating the expression.
     * @throws NullPointerException if either of the two arguments is null
     */

    public String evaluate(/*@Nullable*/ String expr, /*@Nullable*/ InputSource inputSource) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        XPathExpression exp = compile(expr);
        return exp.evaluate(inputSource);
    }

    /**
     * A simple command-line interface for the XPathEvaluator (not documented).
     * @param args command line arguments.
     * First parameter is the filename containing the source document, second
     * parameter is the XPath expression.
     * @throws Exception if any error occurs
     */

    public static void main(/*@NotNull*/ String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("format: java XPathEvaluator source.xml \"expression\"");
            return;
        }
        XPathEvaluator xpe = new XPathEvaluator();
        List results = (List)xpe.evaluate(args[1], new StreamSource(new File(args[0])), XPathConstants.NODESET);
        for (int i = 0; i < results.size(); i++) {
            Object o = results.get(i);
            System.err.println(o);
        }

    }

}

