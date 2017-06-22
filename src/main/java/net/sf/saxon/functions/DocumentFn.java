////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.RelativeURIResolver;
import net.sf.saxon.lib.StandardErrorHandler;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NonDelegatingURIResolver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SingletonItem;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.apache.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Implements the XSLT document() function
 */

public class DocumentFn extends SystemFunctionCall implements Callable {

	private static final Logger log = Logger.getLogger(DocumentFn.class);

    /*@Nullable*/ private String expressionBaseURI = null;

    /**
    * Method called during static type checking
    */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        if (expressionBaseURI == null) {
            // only do this once. The second call supplies an env pointing to the containing
            // xsl:template, which has a different base URI (and in a simplified stylesheet, has no base URI)
            super.checkArguments(visitor);
            expressionBaseURI = visitor.getStaticContext().getBaseURI();
            Optimizer opt = visitor.getConfiguration().obtainOptimizer();
            argument[0] = ExpressionTool.unsorted(opt, argument[0], false);
        }
    }

    /**
    * Determine the static cardinality
    */

    public int computeCardinality() {
        Expression expression = argument[0];
        if (Cardinality.allowsMany(expression.getCardinality())) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        // may have to revise this if the argument can be a list-valued element or attribute
    }

    /**
     * Get the base URI from the static context
     * @return the base URI
     */

    public String getStaticBaseURI() {
        return expressionBaseURI;
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-signficant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.PEER_NODESET |
                StaticProperty.NON_CREATIVE;
        // Declaring it as a peer node-set expression avoids sorting of expressions such as
        // document(XXX)/a/b/c
        // The document() function might appear to be creative: but it isn't, because multiple calls
        // with the same arguments will produce identical results.
    }

    /**
    * preEvaluate: the document() function can be evaluated at compile time if (a) the argument
     * is a string literal, and (b) the option {@link FeatureKeys#PRE_EVALUATE_DOC_FUNCTION} is set.
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        Configuration config = visitor.getConfiguration();
        if (getNumberOfArguments() == 1 &&
                ((Boolean)config.getConfigurationProperty(FeatureKeys.PRE_EVALUATE_DOC_FUNCTION)).booleanValue()) {
            try {
                AtomicValue hrefVal = (AtomicValue)argument[0].evaluateItem(null);
                if (hrefVal==null) {
                    return null;
                }
                String href = hrefVal.getStringValue();
                if (href.indexOf('#') >= 0) {
                    return this;
                }
                NodeInfo item = DocumentFn.preLoadDoc(href, expressionBaseURI, config, this);
                if (item!=null) {
                    return Literal.makeLiteral(new SingletonItem(item));
                }
            } catch (Exception err) {
                // ignore the exception and try again at run-time
                return this;
            }
        }
        return this;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodeSet the set of nodes in the path map that are affected
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return addDocToPathMap(pathMap, pathMapNodeSet);
    }
    

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        DocumentFn d = (DocumentFn)super.copy();
        d.expressionBaseURI = expressionBaseURI;
        return d;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof DocumentFn) && super.equals(o)
                && equalOrNull(expressionBaseURI, ((DocumentFn)o).expressionBaseURI);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (expressionBaseURI != null ? expressionBaseURI.hashCode() : 0);
        return result;
    }

    /**
    * iterate() handles evaluation of the function:
    * it returns a sequence of Document nodes
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        int numArgs = argument.length;

        SequenceIterator hrefSequence = argument[0].iterate(context);
        String baseURI = null;
        if (numArgs==2) {
            // we can trust the type checking: it must be a node
            NodeInfo base = (NodeInfo)argument[1].evaluateItem(context);
            baseURI = base.getBaseURI();
        }

        DocumentMappingFunction map = new DocumentMappingFunction(context);
        map.baseURI = baseURI;
        map.stylesheetURI = expressionBaseURI;
        map.locator = this;

        ItemMappingIterator iter = new ItemMappingIterator(hrefSequence, map);

        Expression expression = argument[0];
        if (Cardinality.allowsMany(expression.getCardinality())) {
            return new DocumentOrderIterator(iter, GlobalOrderComparer.getInstance());
            // this is to make sure we eliminate duplicates: two href's might be the same
        } else {
            return iter;
        }
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        int numArgs = argument.length;

        SequenceIterator hrefSequence = arguments[0].iterate();
        String baseURI = null;
        if (numArgs==2) {
            // we can trust the type checking: it must be a node
            NodeInfo base = (NodeInfo)arguments[1].head();
            baseURI = base.getBaseURI();
        }

        DocumentMappingFunction map = new DocumentMappingFunction(context);
        map.baseURI = baseURI;
        map.stylesheetURI = expressionBaseURI;
        map.locator = this;

        ItemMappingIterator iter = new ItemMappingIterator(hrefSequence, map);

        Expression expression = argument[0];
        if (Cardinality.allowsMany(expression.getCardinality())) {
            return SequenceTool.toLazySequence(new DocumentOrderIterator(iter, GlobalOrderComparer.getInstance()));
            // this is to make sure we eliminate duplicates: two href's might be the same
        } else {
            return SequenceTool.toLazySequence(iter);
        }
    }

    private static class DocumentMappingFunction implements ItemMappingFunction {

        public String baseURI;
        public String stylesheetURI;
        public SourceLocator locator;
        public XPathContext context;

        public DocumentMappingFunction(XPathContext context) {
            this.context = context;
        }

        public Item mapItem(Item item) throws XPathException {
            String b = baseURI;
            if (b==null) {
                if (item instanceof NodeInfo) {
                    b = ((NodeInfo)item).getBaseURI();
                } else {
                    b = stylesheetURI;
                }
            }
            try {
                return makeDoc(item.getStringValue(), b, context, locator);
            } catch (XPathException xerr) {
                if (!xerr.hasBeenReported()) {
                    xerr.maybeSetLocation(locator);
                    String code = (xerr.getException() instanceof URISyntaxException) ? "FODC0005" : "FODC0002";
                    xerr.maybeSetErrorCode(code);
                    try {
                        context.getController().recoverableError(xerr);
                    } catch (XPathException err2) {
                        throw xerr;
                    }
                }
                return null;
            }
        }
    }

    /**
     * Supporting routine to load one external document given a URI (href) and a baseURI. This is used
     * in the normal case when a document is loaded at run-time (that is, when a Controller is available)
     * @param href the relative URI
     * @param baseURI the base URI
     * @param c the dynamic XPath context
     * @param locator used to identify the location of the instruction in event of error
     * @return the root of the constructed document, or the selected element within the document
     * if a fragment identifier was supplied
     * @throws XPathException if reading or parsing the document fails
    */

    public static NodeInfo makeDoc(String href, String baseURI, XPathContext c, SourceLocator locator)
            throws XPathException {

        Configuration config = c.getConfiguration();

        // If the href contains a fragment identifier, strip it out now
        //System.err.println("Entering makeDoc " + href);
        int hash = href.indexOf('#');

        String fragmentId = null;
        if (hash>=0) {
            if (hash==href.length()-1) {
                // # sign at end - just ignore it
                href = href.substring(0, hash);
            } else {
                fragmentId = href.substring(hash+1);
                href = href.substring(0, hash);
                if (!config.getNameChecker().isValidNCName(fragmentId)) {
                    XPathException de = new XPathException("The fragment identifier " + Err.wrap(fragmentId) + " is not a valid NCName");
                    de.setErrorCode("XTRE1160");
                    de.setXPathContext(c);
                    de.setLocator(locator);
                    throw de;
                }
            }
        }

        Controller controller = c.getController();
        if (controller == null) {
            throw new XPathException("doc() function is not available in this environment");
        }

        // Resolve relative URI
        DocumentURI documentKey = computeDocumentKey(href, baseURI, c);

        // see if the document is already loaded

        DocumentInfo doc = config.getGlobalDocumentPool().find(documentKey);
        if (doc != null) {
            return doc;
        }

        DocumentPool pool = controller.getDocumentPool();
        doc = pool.find(documentKey);
        if (doc != null) {
            return getFragment(doc, fragmentId, c, locator);
        }

        // check that the document was not written by this transformation

        if (!controller.checkUniqueOutputDestination(documentKey)) {
            pool.markUnavailable(documentKey);
            XPathException err = new XPathException(
                    "Cannot read a document that was written during the same transformation: " + documentKey);
            err.setXPathContext(c);
            err.setErrorCode("XTRE1500");
            err.setLocator(locator);
            throw err;
        }

        try {

            if (pool.isMarkedUnavailable(documentKey)) {
                XPathException err = new XPathException(
                        "Document has been marked not available: " + documentKey);
                err.setXPathContext(c);
                err.setErrorCode("FODC0002");
                throw err;
            }

            // Get a Source from the URIResolver

            Source source = resolveURI(href, baseURI, documentKey.toString(), controller);


            //System.err.println("URI resolver returned " + source.getClass() + " " + source.getSystemId());
            source = config.getSourceResolver().resolveSource(source, config);
            //System.err.println("Resolved source " + source.getClass() + " " + source.getSystemId());

            DocumentInfo newdoc;
            if (source instanceof NodeInfo || source instanceof DOMSource) {
                NodeInfo startNode = controller.prepareInputTree(source);
                newdoc = startNode.getDocumentRoot();
            } else {
                Builder b = controller.makeBuilder();
                Receiver s = b;
                ParseOptions options = new ParseOptions(b.getPipelineConfiguration().getParseOptions());
                options.setStripSpace(Whitespace.XSLT);
                if (controller.getExecutable().stripsInputTypeAnnotations()) {
                    s = controller.getConfiguration().getAnnotationStripper(s);
                }
                options.setSchemaValidationMode(controller.getSchemaValidationMode());
                if (locator instanceof DocAvailable) {
                    StandardErrorHandler eh = new StandardErrorHandler(controller.getErrorListener());
                    eh.setSilent();
                    options.setErrorHandler(eh);
                }
                PathMap map = controller.getPathMapForDocumentProjection();
                if (map != null) {
                    PathMap.PathMapRoot pathRoot = map.getRootForDocument(documentKey.toString());
                    if (pathRoot != null && !pathRoot.isReturnable() && !pathRoot.hasUnknownDependencies()) {
                        options.addFilter(config.makeDocumentProjector(pathRoot));
                    }
                }
                s.setPipelineConfiguration(b.getPipelineConfiguration());
                try {
                    Sender.send(source, s, options);
                    newdoc = (DocumentInfo)b.getCurrentRoot();
                    b.reset();
                } finally {
                    if (options.isPleaseCloseAfterUse()) {
                        ParseOptions.close(source);
                    }
                }
            }
            controller.registerDocument(newdoc, documentKey);
            controller.addUnavailableOutputDestination(documentKey);
            return getFragment(newdoc, fragmentId, c, locator);

        } catch (TransformerException err) {
            pool.markUnavailable(documentKey);
            XPathException xerr = XPathException.makeXPathException(err);
            xerr.maybeSetLocation(locator);
            String code = (err.getException() instanceof URISyntaxException) ? "FODC0005" : "FODC0002";
            xerr.maybeSetErrorCode(code);
            throw xerr;
        }
    }

    /**
     * Call the URIResolver to resolve a URI
     * @param href the supplied relative URI, stripped of any fragment identifier
     * @param baseURI the base URI
     * @param documentKey the absolute URI if already available, or null otherwise
     * @param controller the Saxon controller
     * @return a Source representing the document to be read
     * @throws XPathException
     */

    public static Source resolveURI(String href, String baseURI, String documentKey, Controller controller)
            throws XPathException {
        URIResolver resolver = controller.getURIResolver();
        Source source;

        try {
            if (resolver instanceof RelativeURIResolver && documentKey != null) {
                source = ((RelativeURIResolver)resolver).dereference(documentKey);
            } else {
                source = resolver.resolve(href, baseURI);
            }
        } catch (Exception ex) {
            XPathException de = new XPathException("Exception thrown by URIResolver", ex);
            if (controller.getConfiguration().getBooleanProperty(FeatureKeys.TRACE_EXTERNAL_FUNCTIONS)) {
                log.error("Could not resolve the URI", ex);
            }
            throw de;
        }

        // if a user URI resolver returns null, try the standard one
        // (Note, the standard URI resolver never returns null)
        if (source==null && !(resolver instanceof NonDelegatingURIResolver)) {
            resolver = controller.getStandardURIResolver();
            try {
                if (resolver instanceof RelativeURIResolver && documentKey != null) {
                    source = ((RelativeURIResolver)resolver).dereference(documentKey);
                } else {
                    source = resolver.resolve(href, baseURI);
                }
            } catch (TransformerException ex) {
                throw XPathException.makeXPathException(ex);
            }
        }
        return source;

    }

    /**
     * Compute a document key
     */

    protected static DocumentURI computeDocumentKey(String href, String baseURI, XPathContext c) throws XPathException {
        // Resolve relative URI
        Controller controller = c.getController();

        URIResolver resolver = controller.getURIResolver();
        if (resolver == null) {
            resolver = controller.getStandardURIResolver();
        }
        return computeDocumentKey(href, baseURI, resolver);
    }

    /**
     * Compute a document key (an absolute URI that can be used to see if a document is already loaded)
     * @param href the relative URI
     * @param baseURI the base URI
     * @param resolver the URIResolver
     */

    public static DocumentURI computeDocumentKey(String href, String baseURI, URIResolver resolver) throws XPathException {
        String documentKey;
        if (resolver instanceof RelativeURIResolver) {
            // If this is the case, the URIResolver is responsible for absolutization as well as dereferencing
            try {
                documentKey = ((RelativeURIResolver)resolver).makeAbsolute(href, baseURI);
            } catch (TransformerException e) {
                documentKey = '/' + href;
            }
        } else {
            // Saxon takes charge of absolutization, leaving the user URIResolver to handle dereferencing only
            if (baseURI==null) {    // no base URI available
                try {
                    // the href might be an absolute URL
                    documentKey = (new URI(href)).toString();
                } catch (URISyntaxException err) {
                    // it isn't; but the URI resolver might know how to cope
                    documentKey = '/' + href;
                }
            } else if (href.length() == 0) {
                // common case in XSLT, which java.net.URI#resolve() does not handle correctly
                documentKey = baseURI;
            } else {
                try {
                    URI uri = new URI(baseURI).resolve(href);
                    documentKey = uri.toString();
                } catch (URISyntaxException err) {
                    documentKey = baseURI + "/../" + href;
                } catch (IllegalArgumentException err) {
                    documentKey = baseURI + "/../" + href;
                }
            }
        }
        return new DocumentURI(documentKey);
    }

    /**
     * Supporting routine to load one external document given a URI (href) and a baseURI. This is used
     * when the document is pre-loaded at compile time.
     * @param href the relative URI. This must not contain a fragment identifier
     * @param baseURI the base URI
     * @param config the Saxon configuration
     * @param locator used to identify the location of the instruction in event of error. May be null.
     * @return the root of the constructed document, or the selected element within the document
     * if a fragment identifier was supplied
    */

    public static NodeInfo preLoadDoc(String href, String baseURI, Configuration config, SourceLocator locator)
            throws XPathException {

        int hash = href.indexOf('#');
        if (hash>=0) {
            throw new XPathException("Fragment identifier not supported for preloaded documents");
        }

        // Resolve relative URI
        String documentKey;
        URIResolver resolver = config.getURIResolver();
        if (resolver instanceof RelativeURIResolver) {
            try {
                documentKey = ((RelativeURIResolver)resolver).makeAbsolute(href, baseURI);
            } catch (TransformerException e) {
                documentKey = '/' + href;
                baseURI = "";
            }
        } else {
            if (baseURI==null) {    // no base URI available
                try {
                    // the href might be an absolute URL
                    documentKey = (new URI(href)).toString();
                } catch (URISyntaxException err) {
                    // it isn't; but the URI resolver might know how to cope
                    documentKey = '/' + href;
                    baseURI = "";
                }
            } else if (href.length() == 0) {
                // common case in XSLT, which java.net.URI#resolve() does not handle correctly
                documentKey = baseURI;
            } else {
                try {
                    URI uri = new URI(baseURI).resolve(href);
                    documentKey = uri.toString();
                } catch (URISyntaxException err) {
                    documentKey = baseURI + "/../" + href;
                } catch (IllegalArgumentException err) {
                    documentKey = baseURI + "/../" + href;
                }
            }
        }

        // see if the document is already loaded

        DocumentInfo doc = config.getGlobalDocumentPool().find(documentKey);
        if (doc != null) {
            return doc;
        }

        try {
            // Get a Source from the URIResolver

            URIResolver r = resolver;
            Source source = null;
            if (r != null) {
                try {
                    source = r.resolve(href, baseURI);
                } catch (Exception ex) {
                    XPathException de = new XPathException("Exception thrown by URIResolver", ex);
                    if (config.getBooleanProperty(FeatureKeys.TRACE_EXTERNAL_FUNCTIONS)) {
                        log.error("Could not get the source from the URIResolver", ex);
                    }
                    de.setLocator(locator);
                    throw de;
                }
            }

            // if a user URI resolver returns null, try the standard one
            // (Note, the standard URI resolver never returns null)
            if (source==null && !(r instanceof NonDelegatingURIResolver)) {
                r = config.getSystemURIResolver();
                source = r.resolve(href, baseURI);
            }
            //System.err.println("URI resolver returned " + source.getClass() + " " + source.getSystemId());
            source = config.getSourceResolver().resolveSource(source, config);
            //System.err.println("Resolved source " + source.getClass() + " " + source.getSystemId());

            DocumentInfo newdoc = config.buildDocument(source);
            config.getGlobalDocumentPool().add(newdoc, documentKey);
            return newdoc;

        } catch (TransformerException err) {
            XPathException xerr = XPathException.makeXPathException(err);
            xerr.setLocator(locator);
            xerr.setErrorCode("FODC0002");
            throw new XPathException(err);
        }
    }


    /**
     * Copy the documents identified by this expression to a given Receiver. This method is used only when it is
     * known that the documents are being copied, because there is then no problem about node identity.
     * @param context the XPath dynamic context
     * @param out the destination to which the documents will be sent
     */

    public void sendDocuments(XPathContext context, Receiver out) throws XPathException {
        SequenceIterator hrefSequence = argument[0].iterate(context);
        String explicitBaseURI = null;
        if (argument.length==2) {
            // we can trust the type checking: it must be a node
            NodeInfo base = (NodeInfo)argument[1].evaluateItem(context);
            explicitBaseURI = base.getBaseURI();
        }
        while (true) {
            Item href = hrefSequence.next();
            if (href == null) {
                break;
            }
            String base;
            if (explicitBaseURI == null) {
                if (href instanceof NodeInfo) {
                    base = ((NodeInfo)href).getBaseURI();
                } else {
                    base = expressionBaseURI;
                }
            } else {
                base = explicitBaseURI;
            }
            sendDoc(href.getStringValue(), base, context, this, out);
        }
    }

    /**
     * Supporting routine to push one external document given a URI (href) and a baseURI to a given Receiver.
     * This method cannot handle fragment identifiers
     * @param href the relative URI
     * @param baseURL the base URI
     * @param c the XPath dynamic context
     * @param locator used to identify the lcoation of the instruction in case of error
     * @param out the destination where the document is to be sent
    */

    public static void sendDoc(String href, String baseURL, XPathContext c,
                               SourceLocator locator, Receiver out) throws XPathException {

        PipelineConfiguration pipe = out.getPipelineConfiguration();
        if (pipe == null) {
            pipe = c.getController().makePipelineConfiguration();
            out.setPipelineConfiguration(pipe);
        }

        // Resolve relative URI

        String documentKey;
        if (baseURL==null) {    // no base URI available
            try {
                // the href might be an absolute URL
                documentKey = (new URI(href)).toString();
            } catch (URISyntaxException err) {
                // it isn't; but the URI resolver might know how to cope
                documentKey = '/' + href;
                baseURL = "";
            }
        } else if (href.length() == 0) {
            // common case in XSLT, which java.net.URI#resolve() does not handle correctly
            documentKey = baseURL;
        } else {
            try {
                URI url = new URI(baseURL).resolve(href);
                documentKey = url.toString();
            } catch (URISyntaxException err) {
                documentKey = baseURL + "/../" + href;
            } catch (IllegalArgumentException err) {
                documentKey = baseURL + "/../" + href;
            }
        }

        Controller controller = c.getController();

        // see if the document is already loaded

        DocumentInfo doc = controller.getDocumentPool().find(documentKey);
        Source source = null;
        if (doc != null) {
            source = doc;
        } else {

            try {
                // Get a Source from the URIResolver

                URIResolver r = controller.getURIResolver();
                if (r != null) {
                    source = r.resolve(href, baseURL);
                }

                // if a user URI resolver returns null, try the standard one
                // (Note, the standard URI resolver never returns null)
                if (source==null) {
                    r = controller.getStandardURIResolver();
                    source = r.resolve(href, baseURL);
                }
                if (source instanceof NodeInfo || source instanceof DOMSource) {
                    NodeInfo startNode = controller.prepareInputTree(source);
                    source = startNode.getDocumentRoot();
                }
            } catch (TransformerException err) {
                XPathException xerr = XPathException.makeXPathException(err);
                xerr.setLocator(locator);
                xerr.maybeSetErrorCode("FODC0005");
                throw xerr;
            }
        }
        ParseOptions options = new ParseOptions();
        options.setStripSpace(Whitespace.XSLT);

        if (controller.getExecutable().stripsInputTypeAnnotations()) {
            out = controller.getConfiguration().getAnnotationStripper(out);
        }
        out.setPipelineConfiguration(pipe);
        try {
            Sender.send(source, out, options);
        } catch (XPathException e) {
            e.maybeSetLocation(locator);
            e.maybeSetErrorCode("FODC0002");
            throw e;
        }
    }


    /**
     * Resolve the fragment identifier within a URI Reference.
     * Only "bare names" XPointers are recognized, that is, a fragment identifier
     * that matches an ID attribute value within the target document.
     * @param doc the document node
     * @param fragmentId the fragment identifier (an ID value within the document)
     * @param context the XPath dynamic context
     * @return the element within the supplied document that matches the
     * given id value; or null if no such element is found.
    */

    private static NodeInfo getFragment(DocumentInfo doc, String fragmentId, XPathContext context, SourceLocator locator)
    throws XPathException {
        // TODO: we only support one kind of fragment identifier. The rules say
        // that the interpretation of the fragment identifier depends on media type,
        // but we aren't getting the media type from the URIResolver.
        if (fragmentId==null) {
            return doc;
        }
        if (!context.getConfiguration().getNameChecker().isValidNCName(fragmentId)) {
            XPathException err = new XPathException("Invalid fragment identifier in URI");
            err.setXPathContext(context);
            err.setErrorCode("XTRE1160");
            err.setLocator(locator);
            try {
                context.getController().recoverableError(err);
            } catch (XPathException dynamicError) {
                throw err;
            }
            return doc;
        }
        return doc.selectID(fragmentId, false);
    }

}

