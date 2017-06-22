////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.instruct.Template;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.StylesheetSpaceStrippingRule;
import net.sf.saxon.style.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This <B>PreparedStylesheet</B> class represents a Stylesheet that has been
 * prepared for execution (or "compiled").
 * <p/>
 * Note that the PreparedStylesheet object does not contain a reference to the source stylesheet
 * tree (rooted at an XSLStyleSheet object). This allows the source tree to be garbage-collected
 * when it is no longer required.
 */

public class PreparedStylesheet extends Executable implements Templates, Serializable {

    private CompilerInfo compilerInfo;
    private transient StyleNodeFactory nodeFactory;
    private int errorCount = 0;
    private HashMap<URI, PreparedStylesheet> nextStylesheetCache;
    // cache for stylesheets named as "saxon:next-in-chain"

    // definitions of decimal formats
    private DecimalFormatManager decimalFormatManager;

    // definitions of template rules (XSLT only)
    private RuleManager ruleManager;

    // index of named templates.
    private HashMap<StructuredQName, Template> namedTemplateTable;

    // a boolean flag indicating whether the stylesheet makes any use of tunnel parameters
    private boolean usesTunnel = false;

    // manager class for accumulator rules (XSLT 3.0 only)
    private IAccumulatorManager accumulatorManager = null;

    // manager list of variables coming from xsl:param and xsl:variable relating to statis attribute
    private GlobalParameterSet variableList = new GlobalParameterSet();



    /**
     * Constructor - deliberately protected
     * @param config The Configuration set up by the TransformerFactory
     * @param info   Compilation options
     */

    protected PreparedStylesheet(Configuration config, CompilerInfo info) {
        super(config);
        nodeFactory = config.makeStyleNodeFactory();
        nodeFactory.setXsltProcessorVersion(info.getXsltVersion());
        nodeFactory.makeAccumulatorManager(this);
        RuleManager rm = new RuleManager();
        rm.setRecoveryPolicy(info.getRecoveryPolicy());
        setRuleManager(rm);
        setHostLanguage(Configuration.XSLT, info.getXsltVersion().equals(DecimalValue.THREE));
        setCollationMap(info.getCollationMap());
        setSchemaAware(info.isSchemaAware());
        compilerInfo = info;
        if (compilerInfo.getErrorListener() == null) {
            compilerInfo.setErrorListener(config.getErrorListener());
        }
    }

    /**
     * Factory method to make a PreparedStylesheet
     * @param source the source of this principal stylesheet module
     * @param config the Saxon configuration
     * @param info   compile-time options for this stylesheet compilation
     * @return the prepared stylesheet
     * @throws javax.xml.transform.TransformerConfigurationException if there is a static error in the stylesheet
     */

    public static PreparedStylesheet compile(Source source, Configuration config, CompilerInfo info)
            throws TransformerConfigurationException {
        PreparedStylesheet pss = new PreparedStylesheet(config, info);
        pss.prepare(source);
        return pss;
    }

    /**
     * Make a Transformer from this Templates object.
     * @return the new Transformer (always a Controller)
     * @see net.sf.saxon.Controller
     */

    public Transformer newTransformer() {
        Controller c = new Controller(getConfiguration(), this);
        c.setPreparedStylesheet(this);
        c.setRecoveryPolicy(compilerInfo.getRecoveryPolicy());
        if (compilerInfo.getDefaultInitialTemplate() != null) {
            try {
                c.setInitialTemplate(compilerInfo.getDefaultInitialTemplate().getClarkName());
            } catch (XPathException err) {
                // ignore error if there is no template with this name
            }

        }
        if (compilerInfo.getDefaultInitialMode() != null) {
            c.setInitialMode(compilerInfo.getDefaultInitialMode().getClarkName());
        }


        return c;
    }

    /**
     * Set the configuration in which this stylesheet is compiled.
     * Intended for internal use.
     * @param config the configuration to be used.
     */

    public void setConfiguration(Configuration config) {
        super.setConfiguration(config);
        this.compilerInfo = config.getDefaultXsltCompilerInfo();
    }

    /**
     * Get the StyleNodeFactory in use. The StyleNodeFactory determines which subclass of StyleElement
     * to use for each element node in the stylesheet tree.
     * @return the StyleNodeFactory
     */

    public StyleNodeFactory getStyleNodeFactory() {
        return nodeFactory;
    }

    /**
     * Set the DecimalFormatManager which handles decimal-format definitions
     * @param dfm the DecimalFormatManager containing the named xsl:decimal-format definitions
     */

    public void setDecimalFormatManager(DecimalFormatManager dfm) {
        decimalFormatManager = dfm;
    }

    /**
     * Get the DecimalFormatManager which handles decimal-format definitions
     * @return the DecimalFormatManager containing the named xsl:decimal-format definitions
     */

    public DecimalFormatManager getDecimalFormatManager() {
        if (decimalFormatManager == null) {
            decimalFormatManager = new DecimalFormatManager();
        }
        return decimalFormatManager;
    }

    /**
     * Get the class that manages accumulator functions
     * @return the class that manages accumulators. Always null in Saxon-HE
     */

    public IAccumulatorManager getAccumulatorManager() {
        return accumulatorManager;
    }

    /**
     * Set the class that manages accumulator functions
     * @param accumulatorManager the manager of accumulator functions
     */

    public void setAccumulatorManager(IAccumulatorManager accumulatorManager) {
        this.accumulatorManager = accumulatorManager;
    }

    /**
     * Say that the stylesheet uses tunnel parameters. (This information is used by the bytecode generator,
     * which avoids generating code to pass tunnel parameters on every apply-templates call if there are no
     * tunnel parameters anywhere in the stylesheet).
     */

    public void setUsesTunnelParameters() {
        usesTunnel = true;
    }

    /**
     * Ask whether the stylesheet uses tunnel parameters. (Called by the bytecode generator).
     * @return true if the stylesheet uses tunnel parameters.
     */

    public boolean usesTunnelParameters() {
        return usesTunnel;
    }


    /**
     * Prepare a stylesheet from a Source document
     * @param styleSource the source document containing the stylesheet
     * @throws TransformerConfigurationException
     *          if compilation of the
     *          stylesheet fails for any reason
     */

    protected void prepare(Source styleSource) throws TransformerConfigurationException {
        DocumentImpl doc;
        try {
            doc = loadStylesheetModule(styleSource);
            setStylesheetDocument(doc);
        } catch (XPathException e) {
            try {
                compilerInfo.getErrorListener().fatalError(e);
            } catch (TransformerException e2) {
                // ignore an exception thrown by the error handler
            }
            if (errorCount == 0) {
                errorCount++;
            }
        }

        if (errorCount > 0) {
            throw new TransformerConfigurationException(
                    "Failed to compile stylesheet. " +
                            errorCount +
                            (errorCount == 1 ? " error " : " errors ") +
                            "detected.");
        }
    }

    /**
     * Build the tree representation of a stylesheet module
     * @param styleSource the source of the module
     * @return the root Document node of the tree containing the stylesheet
     *         module
     * @throws XPathException if XML parsing or tree
     *                        construction fails
     */
    public DocumentImpl loadStylesheetModule(Source styleSource)
            throws XPathException {

        StyleNodeFactory nodeFactory = getStyleNodeFactory();

        PipelineConfiguration pipe = getConfiguration().makePipelineConfiguration();
        LinkedTreeBuilder styleBuilder = new LinkedTreeBuilder(pipe);
        pipe.setURIResolver(compilerInfo.getURIResolver());
        pipe.setErrorListener(compilerInfo.getErrorListener());
        styleBuilder.setSystemId(styleSource.getSystemId());
        styleBuilder.setNodeFactory(nodeFactory);
        styleBuilder.setLineNumbering(true);

        UseWhenFilter useWhenFilter = new UseWhenFilter(this, styleBuilder);
        StartTagBuffer startTagBuffer = new StartTagBuffer(useWhenFilter);
        useWhenFilter.setStartTagBuffer(startTagBuffer);

        StylesheetSpaceStrippingRule rule = new StylesheetSpaceStrippingRule(getConfiguration().getNamePool());
        Stripper styleStripper = new Stripper(rule, startTagBuffer);
        CommentStripper commentStripper = new CommentStripper(styleStripper);

        // build the stylesheet document

        DocumentImpl doc;

        ParseOptions options;
        if (styleSource instanceof AugmentedSource) {
            options = ((AugmentedSource)styleSource).getParseOptions();
            styleSource = ((AugmentedSource)styleSource).getContainedSource();
        } else {
            options = new ParseOptions();
        }
        options.setErrorListener(compilerInfo.getErrorListener());
        options.setSchemaValidationMode(Validation.STRIP);
        options.setDTDValidationMode(Validation.STRIP);
        options.setLineNumbering(true);
        options.setStripSpace(Whitespace.NONE);
        try {
            if (options.getXMLReader() == null && Configuration.getPlatform().isJava()) {
                XMLReader styleParser = getConfiguration().getStyleParser();
                options.setXMLReader(styleParser);
                Sender.send(styleSource, commentStripper, options);
                getConfiguration().reuseStyleParser(styleParser);
            } else {
                Sender.send(styleSource, commentStripper, options);
            }
            doc = (DocumentImpl)styleBuilder.getCurrentRoot();
            styleBuilder.reset();
            return doc;

        } finally {
            if (options.isPleaseCloseAfterUse()) {
                ParseOptions.close(styleSource);
            }
        }
    }


    /**
     * Create a PreparedStylesheet from a supplied DocumentInfo
     * Note: the document must have been built using the StyleNodeFactory
     * @param doc the document containing the stylesheet module
     * @throws XPathException if the document supplied
     *                        is not a stylesheet
     */

    protected void setStylesheetDocument(DocumentImpl doc)
            throws XPathException {

        DocumentImpl styleDoc = doc;

        // If top-level node is a literal result element, stitch it into a skeleton stylesheet

        StyleElement topnode = (StyleElement)styleDoc.getDocumentElement();
        if (topnode instanceof LiteralResultElement) {
            styleDoc = ((LiteralResultElement)topnode).makeStylesheet(this);
        }

        if (!(styleDoc.getDocumentElement() instanceof XSLStylesheet)) {
            throw new XPathException(
                    "Outermost element of stylesheet is not xsl:stylesheet or xsl:transform or literal result element");
        }

        XSLStylesheet top = (XSLStylesheet)styleDoc.getDocumentElement();
        if (compilerInfo.isVersionWarning() &&
                top.getEffectiveVersion().compareTo(getStyleNodeFactory().getXsltProcessorVersion()) != 0) {
            try {
                TransformerException w = new TransformerException(
                        "Running an XSLT " + top.getEffectiveVersion() + " stylesheet with an XSLT " +
                                getStyleNodeFactory().getXsltProcessorVersion() + " processor");
                w.setLocator(topnode);
                getConfiguration().getErrorListener().warning(w);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
        }

        if (getStyleNodeFactory().getXsltProcessorVersion().compareTo(DecimalValue.TWO) > 0) {
            // The condition is checked again later in non-open code, but we can give a better error message here
            getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "XSLT 3.0");
        }


        PrincipalStylesheetModule psm = new PrincipalStylesheetModule(top, 0);
        psm.setPreparedStylesheet(this);
        psm.setVersion(top.getAttributeValue("version"));
        psm.createFunctionLibrary(compilerInfo);

        // Preprocess the stylesheet, performing validation and preparing template definitions

        //executable.setLocationMap(psm.getLocationMap());
        top.setPrincipalStylesheetModule(psm);
        try {
            psm.preprocess();
        } catch (XPathException e) {
            Throwable e2 = e.getException();
            if (e2 instanceof XPathException) {
                try {
                    compilerInfo.getErrorListener().fatalError((XPathException)e2);
                } catch (TransformerException e3) {
                    // ignore an error thrown by the ErrorListener
                }
            }
            throw e;
        }

        // Compile the stylesheet, retaining the resulting executable

        psm.compileStylesheet();
    }

    /**
     * Set the RuleManager that handles template rules
     *
     * @param rm the RuleManager containing details of all the template rules
     */

    public void setRuleManager(RuleManager rm) {
        ruleManager = rm;
    }

    /**
     * Get the RuleManager which handles template rules
     *
     * @return the RuleManager registered with setRuleManager
     */

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    /**
     * Get the named template with a given name.
     *
     * @param qName The template name
     * @return The template (of highest import precedence) with this name if there is one;
     *         null if none is found.
     */

    /*@Nullable*/ public Template getNamedTemplate(StructuredQName qName) {
        if (namedTemplateTable == null) {
            return null;
        }
        return namedTemplateTable.get(qName);
    }

    /**
     * Register the named template with a given name
     * @param templateName the name of a named XSLT template
     * @param template the template
     */

    public void putNamedTemplate(StructuredQName templateName, Template template) {
        if (namedTemplateTable == null) {
            namedTemplateTable = new HashMap<StructuredQName, Template>(32);
        }
        namedTemplateTable.put(templateName, template);
    }

    /**
     * Iterate over all the named templates defined in this Executable
     * @return an iterator, the items returned being of class {@link net.sf.saxon.expr.instruct.Template}
     */

    public Iterator<Template> iterateNamedTemplates() {
        if (namedTemplateTable == null) {
            List<Template> list = Collections.emptyList();
            return list.iterator();
        } else {
            return namedTemplateTable.values().iterator();
        }
    }

    /**
     * Explain the expression tree for named templates in a stylesheet
     * @param presenter destination for the explanatory output
     */

    public void explainNamedTemplates(ExpressionPresenter presenter) {
        presenter.startElement("namedTemplates");
        if (namedTemplateTable != null) {
            for (Template t : namedTemplateTable.values()) {
                presenter.startElement("template");
                presenter.emitAttribute("name", t.getTemplateName().getDisplayName());
                presenter.emitAttribute("line", Integer.toString(t.getLineNumber()));
                presenter.emitAttribute("module", t.getSystemId());
                if (t.getBody() != null) {
                    t.getBody().explain(presenter);
                }
                presenter.endElement();
            }
        }
        presenter.endElement();
    }
    

    /**
     * Determine whether trace hooks are included in the compiled code.
     * @return true if trace hooks are included, false if not.
     * @since 8.9
     */

    public boolean isCompileWithTracing() {
        return compilerInfo.isCompileWithTracing();
    }


    /**
     * Get the properties for xsl:output.  JAXP method. The object returned will
     * be a clone of the internal values, and thus it can be mutated
     * without mutating the Templates object, and then handed in to
     * the process method.
     * <p>In Saxon, the properties object is a new, empty, Properties object that is
     * backed by the live properties to supply default values for missing properties.
     * This means that the property values must be read using the getProperty() method.
     * Calling the get() method on the underlying Hashtable will return null.</p>
     * <p>In Saxon 8.x, this method gets the output properties for the unnamed output
     * format in the stylesheet.</p>
     * @return A Properties object reflecting the output properties defined
     *         for the default (unnamed) output format in the stylesheet. It may
     *         be mutated and supplied to the setOutputProperties() method of the
     *         Transformer, without affecting other transformations that use the
     *         same stylesheet.
     * @see javax.xml.transform.Transformer#setOutputProperties
     */


    public Properties getOutputProperties() {
        Properties details = getDefaultOutputProperties();
        return new Properties(details);
    }

    /**
     * Report a compile time error. This calls the errorListener to output details
     * of the error, and increments an error count.
     * @param err the exception containing details of the error
     * @throws TransformerException if the ErrorListener decides that the
     *                              error should be reported
     */

    public void reportError(TransformerException err) throws TransformerException {
        if (err instanceof XPathException) {
            if (!((XPathException)err).hasBeenReported()) {
                errorCount++;
                try {
                    compilerInfo.getErrorListener().fatalError(err);
                    ((XPathException)err).setHasBeenReported(true);
                } catch (Exception err2) {
                    // ignore secondary error
                }
            } else if (errorCount == 0) {
                errorCount++;
            }
        } else {
            errorCount++;
            compilerInfo.getErrorListener().fatalError(err);
        }
    }

    /**
     * Get the number of errors reported so far
     * @return the number of errors reported
     */

    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Report a compile time warning. This calls the errorListener to output details
     * of the warning.
     * @param err an exception holding details of the warning condition to be
     *            reported
     */

    public void reportWarning(TransformerException err) {
        try {
            compilerInfo.getErrorListener().warning(err);
        } catch (TransformerException err2) {
            // ignore secondary error
        }
    }

    /**
     * Get a "next in chain" stylesheet. This method is intended for internal use.
     * @param href    the relative URI of the next-in-chain stylesheet
     * @param baseURI the baseURI against which this relativeURI is to be resolved
     * @return the cached stylesheet if present in the cache, or null if not
     */

    /*@Nullable*/ public PreparedStylesheet getCachedStylesheet(String href, String baseURI) {
        URI abs = null;
        try {
            abs = new URI(baseURI).resolve(href);
        } catch (URISyntaxException err) {
            //
        }
        PreparedStylesheet result = null;
        if (abs != null && nextStylesheetCache != null) {
            result = nextStylesheetCache.get(abs);
        }
        return result;
    }

    /**
     * Save a "next in chain" stylesheet in compiled form, so that it can be reused repeatedly.
     * This method is intended for internal use.
     * @param href    the relative URI of the stylesheet
     * @param baseURI the base URI against which the relative URI is resolved
     * @param pss     the prepared stylesheet object to be cached
     */

    public void putCachedStylesheet(String href, String baseURI, PreparedStylesheet pss) {
        URI abs = null;
        try {
            abs = new URI(baseURI).resolve(href);
        } catch (URISyntaxException err) {
            //
        }
        if (abs != null) {
            if (nextStylesheetCache == null) {
                nextStylesheetCache = new HashMap<URI, PreparedStylesheet>(4);
            }
            nextStylesheetCache.put(abs, pss);
        }
    }

    /**
     * Get the CompilerInfo containing details of XSLT compilation options
     * @return the CompilerInfo containing compilation options
     * @since 9.2
     */

    public CompilerInfo getCompilerInfo() {
        return compilerInfo;
    }

    /**
     * Produce an XML representation of the compiled and optimized stylesheet
     * @param presenter defines the destination and format of the output
     */

    public void explain(ExpressionPresenter presenter) {
        presenter.startElement("stylesheet");
        getKeyManager().explainKeys(presenter);
        explainGlobalVariables(presenter);
        ruleManager.explainTemplateRules(presenter);
        explainNamedTemplates(presenter);
        FunctionLibraryList libList = getFunctionLibrary();
        List<FunctionLibrary> libraryList = libList.getLibraryList();
        presenter.startElement("functions");
        for (FunctionLibrary lib : libraryList) {
            if (lib instanceof ExecutableFunctionLibrary) {
                for (Iterator f = ((ExecutableFunctionLibrary) lib).iterateFunctions(); f.hasNext(); ) {
                    UserFunction func = (UserFunction) f.next();
                    presenter.startElement("function");
                    presenter.emitAttribute("name", func.getFunctionName().getDisplayName());
                    presenter.emitAttribute("line", Integer.toString(func.getLineNumber()));
                    presenter.emitAttribute("module", func.getSystemId());
                    func.getBody().explain(presenter);
                    presenter.endElement();
                }
            }
        }
        presenter.endElement();
        presenter.endElement();
    }

    /**
     * Get the stylesheet specification(s) associated
     * via the xml-stylesheet processing instruction (see
     * http://www.w3.org/TR/xml-stylesheet/) with the document
     * document specified in the source parameter, and that match
     * the given criteria.  Note that it is possible to return several
     * stylesheets, in which case they are applied as if they were
     * a list of imports or cascades.
     * @param config  The Saxon Configuration
     * @param source  The XML source document.
     * @param media   The media attribute to be matched.  May be null, in which
     *                case the prefered templates will be used (i.e. alternate = no).
     * @param title   The value of the title attribute to match.  May be null.
     * @param charset The value of the charset attribute to match.  May be null.
     * @return A Source object suitable for passing to the TransformerFactory.
     * @throws TransformerConfigurationException
     *          if any problems occur
     */


    public static Source getAssociatedStylesheet(
            Configuration config, Source source, String media, String title, String charset)
            throws TransformerConfigurationException {
        PIGrabber grabber = new PIGrabber(new Sink(config.makePipelineConfiguration()));
        grabber.setFactory(config);
        grabber.setCriteria(media, title);
        grabber.setBaseURI(source.getSystemId());
        grabber.setURIResolver(config.getURIResolver());

        try {
            Sender.send(source, grabber, null);
            // this parse will be aborted when the first start tag is found
        } catch (XPathException err) {
            if (grabber.isTerminated()) {
                // do nothing
            } else {
                throw new TransformerConfigurationException(
                        "Failed while looking for xml-stylesheet PI", err);
            }
        }

        try {
            Source[] sources = grabber.getAssociatedStylesheets();
            if (sources == null) {
                throw new TransformerConfigurationException(
                        "No matching <?xml-stylesheet?> processing instruction found");
            }
            return compositeStylesheet(config, source.getSystemId(), sources);
        } catch (TransformerException err) {
            if (err instanceof TransformerConfigurationException) {
                throw (TransformerConfigurationException)err;
            } else {
                throw new TransformerConfigurationException(err);
            }
        }
    }

    /**
     * Process a series of stylesheet inputs, treating them in import or cascade
     * order.  This is mainly for support of the getAssociatedStylesheets
     * method, but may be useful for other purposes.
     * @param config the Saxon configuration
     * @param baseURI the base URI to be used for the synthesized composite stylesheet
     * @param sources An array of Source objects representing individual stylesheets.
     * @return A Source object representing a composite stylesheet.
     * @throws javax.xml.transform.TransformerConfigurationException if there is a static error
     * in the stylesheet
     */

    private static Source compositeStylesheet(Configuration config, String baseURI, Source[] sources)
            throws TransformerConfigurationException {

        if (sources.length == 1) {
            return sources[0];
        } else if (sources.length == 0) {
            throw new TransformerConfigurationException(
                    "No stylesheets were supplied");
        }

        // create a new top-level stylesheet that imports all the others

        StringBuilder sb = new StringBuilder(250);
        sb.append("<xsl:stylesheet version='1.0' ");
        sb.append(" xmlns:xsl='" + NamespaceConstant.XSLT + "'>");
        for (Source source : sources) {
            sb.append("<xsl:import href='").append(source.getSystemId()).append("'/>");
        }
        sb.append("</xsl:stylesheet>");
        InputSource composite = new InputSource();
        composite.setSystemId(baseURI);
        composite.setCharacterStream(new StringReader(sb.toString()));
        return new SAXSource(config.getSourceParser(), composite);
    }

}

