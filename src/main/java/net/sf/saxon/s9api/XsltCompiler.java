////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.sort.RuleBasedSubstringMatcher;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trace.XSLTTraceCodeInjector;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import java.text.RuleBasedCollator;
import java.util.HashMap;
import java.util.Map;

/**
 * An XsltCompiler object allows XSLT 2.0 stylesheets to be compiled. The compiler holds information that
 * represents the static context for the compilation.
 * <p/>
 * <p>To construct an XsltCompiler, use the factory method {@link Processor#newXsltCompiler} on the Processor object.</p>
 * <p/>
 * <p>An XsltCompiler may be used repeatedly to compile multiple queries. Any changes made to the
 * XsltCompiler (that is, to the static context) do not affect queries that have already been compiled.
 * An XsltCompiler may be used concurrently in multiple threads, but it should not then be modified once
 * initialized.</p>
 *
 * @since 9.0
 */
public class XsltCompiler {

    private Processor processor;
    private Configuration config;
    private CompilerInfo compilerInfo;
    private Map<QName, XdmValue> variableList = new HashMap<QName, XdmValue>();

    /**
     * Protected constructor. The public way to create an <tt>XsltCompiler</tt> is by using the factory method
     * {@link Processor#newXsltCompiler} .
     *
     * @param processor the Saxon processor
     */

    protected XsltCompiler(Processor processor) {
        this.processor = processor;
        this.config = processor.getUnderlyingConfiguration();
        compilerInfo = new CompilerInfo(config.getDefaultXsltCompilerInfo());
        compilerInfo.setCollationMap(new CollationMap(config.getCollationMap()));
    }

    /**
     * Get the Processor from which this XsltCompiler was constructed
     *
     * @return the Processor to which this XsltCompiler belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the URIResolver to be used during stylesheet compilation. This URIResolver, despite its name,
     * is <b>not</b> used for resolving relative URIs against a base URI; it is used for dereferencing
     * an absolute URI (after resolution) to return a {@link javax.xml.transform.Source} representing the
     * location where a stylesheet module can be found.
     * <p/>
     * <p>This URIResolver is used to dereference the URIs appearing in <code>xsl:import</code>,
     * <code>xsl:include</code>, and <code>xsl:import-schema</code> declarations.
     * It is not used at run-time for resolving requests to the <code>document()</code> or similar functions.</p>
     *
     * @param resolver the URIResolver to be used during stylesheet compilation.
     */

    public void setURIResolver(URIResolver resolver) {
        compilerInfo.setURIResolver(resolver);
    }


    /**
     * Set the non-static paramters as well as static ones.
     *
     * @param name  the StructuredQName of the parameter
     * @param value as a XdmValue of the parameter
     */
    public void setParameter(QName name, XdmValue value) {
        variableList.put(name, value);
        compilerInfo.setParameter(name.getStructuredQName(), value.getUnderlyingValue());
    }

    /**
     * Get the URIResolver to be used during stylesheet compilation.
     *
     * @return the URIResolver used during stylesheet compilation. Returns null if no user-supplied
     *         URIResolver has been set.
     */

    public URIResolver getURIResolver() {
        return compilerInfo.getURIResolver();
    }

    /**
     * Set the ErrorListener to be used during this compilation episode
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation.
     */

    public void setErrorListener(ErrorListener listener) {
        compilerInfo.setErrorListener(listener);
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     *         compilation. Returns null if no user-supplied ErrorListener has been set.
     */

    public ErrorListener getErrorListener() {
        return compilerInfo.getErrorListener();
    }

    /**
     * Say that the stylesheet must be compiled to be schema-aware, even if it contains no
     * xsl:import-schema declarations. Normally a stylesheet is treated as schema-aware
     * only if it contains one or more xsl:import-schema declarations. If it is not schema-aware,
     * then all input documents must be untyped, and validation of temporary trees is disallowed
     * (though validation of the final result tree is permitted). Setting the argument to true
     * means that schema-aware code will be compiled regardless.
     *
     * @param schemaAware If true, the stylesheet will be compiled with schema-awareness
     *                    enabled even if it contains no xsl:import-schema declarations. If false, the stylesheet
     *                    is treated as schema-aware only if it contains one or more xsl:import-schema declarations.
     * @since 9.2
     */

    public void setSchemaAware(boolean schemaAware) {
        compilerInfo.setSchemaAware(schemaAware);
    }

    /**
     * Ask whether schema-awareness has been requested by means of a call on
     * {@link #setSchemaAware}
     *
     * @return true if schema-awareness has been requested
     * @since 9.2
     */

    public boolean isSchemaAware() {
        return compilerInfo.isSchemaAware();
    }

    /**
     * Bind a collation URI to a collation
     *
     * @param uri       the absolute collation URI
     * @param collation a {@link java.text.Collator} object that implements the required collation
     * @throws IllegalArgumentException if an attempt is made to rebind the standard URI
     *                                  for the Unicode codepoint collation
     * @since 9.5
     */

    public void declareCollation(String uri, final java.text.Collator collation) {
        if (uri.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
            throw new IllegalArgumentException("Cannot declare the Unicode codepoint collation URI");
        }
        StringCollator saxonCollation;
        if (collation instanceof RuleBasedCollator) {
            saxonCollation = new RuleBasedSubstringMatcher((RuleBasedCollator) collation);
        } else {
            saxonCollation = new SimpleCollation(collation);
        }
        compilerInfo.getCollationMap().setNamedCollation(uri, saxonCollation);
    }

    /**
     * Declare the default collation
     *
     * @param uri the absolute URI of the default collation. This URI must have been bound to a collation
     *            using the method {@link #declareCollation(String, java.text.Collator)}
     * @throws IllegalStateException if the collation URI has not been registered, unless it is the standard
     *                               Unicode codepoint collation which is registered implicitly
     * @since 9.5
     */

    public void declareDefaultCollation(String uri) {
        if (compilerInfo.getCollationMap().getNamedCollation(uri) == null) {
            throw new IllegalStateException("Unknown collation " + uri);
        }
        compilerInfo.getCollationMap().setDefaultCollationName(uri);
    }

    /**
     * Set the XSLT (and XPath) language level to be supported by the processor.
     *
     * @param version the language level to be supported. The value 2.0 requests a processor conforming to the
     *                XSLT 2.0 specification; 3.0 requests an XSLT 3.0 processor, while the value "0.0" (which is the
     *                default setting in the absence of a call on this method) gets a processor according to the
     *                value of the version attribute on the xsl:stylesheet element.
     *                <p>Although this interface can be used to run a 1.0 stylesheet, it is not possible to request
     *                a 1.0 processor; Saxon will handle it as a 2.0 processor in backwards-compatibility mode, which
     *                is not quite the same thing.</p>
     *                <p>The value 2.1 is accepted temporarily as a synonym for 3.0</p>
     * @throws IllegalArgumentException if the value is not numerically equal to 0.0, 2.0, or 3.0
     * @since 9.3
     */

    public void setXsltLanguageVersion(String version) {
        DecimalValue v;
        try {
            v = (DecimalValue) DecimalValue.makeDecimalValue(version, true).asAtomic();
        } catch (ValidationException ve) {
            throw new IllegalArgumentException(ve);
        }
        if (DecimalValue.TWO_POINT_ONE.equals(v)) {
            v = DecimalValue.THREE;
        }
        if (!DecimalValue.TWO.equals(v) && !DecimalValue.THREE.equals(v)) {
            throw new IllegalArgumentException("LanguageVersion " + v);
        }
        compilerInfo.setXsltVersion(v);
    }

    /**
     * Get the XSLT (and XPath) language level supported by the processor.
     *
     * @return the language level supported. The value 2.0 represents a processor conforming to the
     *         XSLT 2.0 specification; 3.0 represents an XSLT 3.0 processor, while the value zero (which is the
     *         default setting in the absence of a call on {@link #setXsltLanguageVersion} represents a processor
     *         selected according to the value of the version attribute on the xsl:stylesheet element.
     * @since 9.3
     */

    public String getXsltLanguageVersion() {
        return compilerInfo.getXsltVersion().toString();
    }

    /**
     * Set whether trace hooks are to be included in the compiled code. To use tracing, it is necessary
     * both to compile the code with trace hooks included, and to supply a TraceListener at run-time
     *
     * @param option true if trace code is to be compiled in, false otherwise
     * @since 9.3
     */

    public void setCompileWithTracing(boolean option) {
        if (option) {
            compilerInfo.setCodeInjector(new XSLTTraceCodeInjector());
        } else {
            compilerInfo.setCodeInjector(null);
        }
    }

    /**
     * Ask whether trace hooks are included in the compiled code.
     *
     * @return true if trace hooks are included, false if not.
     * @since 9.3
     */

    public boolean isCompileWithTracing() {
        return compilerInfo.isCompileWithTracing();
    }

//    /**
//     * Import a compiled XQuery function library.
//     * @param queryCompiler An XQueryCompiler that has been used to compile a library of XQuery functions
//     * (by using one of the overloaded methods named <code>compileLibrary</code>).
//     * @param namespace The namespace of the functions that are to be made available to the stylesheet.
//     * All the global functions with this namespace that are
//     * defined in libraries that have been compiled using this XQueryCompiler are added to the static context
//     * of the XSLT stylesheet. The stylesheet does not need to (and should not) contain a call on
//     * <code>saxon:import-query</code> to import these functions.
//     */
//
//    public void importXQueryLibrary(XQueryCompiler queryCompiler, String namespace) {
//        compilerInfo.importXQueryLibrary(queryCompiler.)
//    }

    /**
     * Compile a stylesheet.
     * <p/>
     * <p><i>Note: the term "compile" here indicates that the stylesheet is converted into an executable
     * form. There is no implication that this involves code generation.</i></p>
     * <p/>
     * <p>The source argument identifies the XML document holding the principal stylesheet module. Other
     * modules will be located relative to this module by resolving against the base URI that is defined
     * as the systemId property of the supplied Source.</p>
     * <p/>
     * <p>The following kinds of {@link javax.xml.transform.Source} are recognized:</p>
     * <p/>
     * <ul>
     * <li>{@link javax.xml.transform.stream.StreamSource}, allowing the stylesheet to be supplied as a
     * URI, as a {@link java.io.File}, as an {@link java.io.InputStream}, or as a {@link java.io.Reader}</li>
     * <li>{@link javax.xml.transform.sax.SAXSource}, allowing the stylesheet to be supplied as a stream
     * of SAX events from a SAX2-compliant XML parser (or any other source of SAX events)</li>
     * <li>{@link javax.xml.transform.dom.DOMSource}, allowing the stylesheet to be supplied as a
     * DOM tree. This option is available only if saxon9-dom.jar is on the classpath.</li>
     * <li>Document wrappers for XOM, JDOM, or DOM4J trees, provided the appropriate support libraries
     * are on the classpath</li>
     * <li>A Saxon NodeInfo, representing the root of a tree in any of the native tree formats supported
     * by Saxon</li>
     * <li>An {@link XdmNode} representing the document node of the stylesheet module</li>
     * </ul>
     *
     * @param source Source object representing the principal stylesheet module to be compiled
     * @return an XsltExecutable, which represents the compiled stylesheet.
     * @throws SaxonApiException if the stylesheet contains static errors or if it cannot be read. Note that
     *                           the exception that is thrown will <b>not</b> contain details of the actual errors found in the stylesheet. These
     *                           will instead be notified to the registered ErrorListener. The default ErrorListener displays error messages
     *                           on the standard error output.
     */

    public XsltExecutable compile(/*@NotNull*/ Source source) throws SaxonApiException {
        try {
            PreparedStylesheet pss = PreparedStylesheet.compile(source, config, compilerInfo);
            return new XsltExecutable(processor, pss);
        } catch (TransformerConfigurationException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying CompilerInfo object, which provides more detailed (but less stable) control
     * over some compilation options
     *
     * @return the underlying CompilerInfo object, which holds compilation-time options. The methods on
     *         this object are not guaranteed stable from release to release.
     */

    public CompilerInfo getUnderlyingCompilerInfo() {
        return compilerInfo;
    }

}

