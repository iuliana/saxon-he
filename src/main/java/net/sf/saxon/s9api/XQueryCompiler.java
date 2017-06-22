////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.sort.RuleBasedSubstringMatcher;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.ErrorListener;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.RuleBasedCollator;

/**
 * An XQueryCompiler object allows XQuery 1.0 queries to be compiled. The compiler holds information that
 * represents the static context for the compilation.
 * <p/>
 * <p>To construct an XQueryCompiler, use the factory method {@link Processor#newXQueryCompiler}.</p>
 * <p/>
 * <p>An XQueryCompiler may be used repeatedly to compile multiple queries. Any changes made to the
 * XQueryCompiler (that is, to the static context) do not affect queries that have already been compiled.
 * An XQueryCompiler may be used concurrently in multiple threads, but it should not then be modified once
 * initialized.</p>
 *
 * @since 9.0
 */

public class XQueryCompiler {

    private Processor processor;
    private StaticQueryContext env;
    private ItemType requiredContextItemType;
    private String encoding;

    /**
     * Protected constructor
     *
     * @param processor the Saxon Processor
     */

    protected XQueryCompiler(Processor processor) {
        this.processor = processor;
        this.env = processor.getUnderlyingConfiguration().newStaticQueryContext();
    }

    /**
     * Get the Processor from which this XQueryCompiler was constructed
     *
     * @return the Processor to which this XQueryCompiler belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the static base URI for the query
     *
     * @param baseURI the static base URI; or null to indicate that no base URI is available
     */

    public void setBaseURI(URI baseURI) {
        if (baseURI == null) {
            env.setBaseURI(null);
        } else {
            if (!baseURI.isAbsolute()) {
                throw new IllegalArgumentException("Base URI must be an absolute URI: " + baseURI);
            }
            env.setBaseURI(baseURI.toString());
        }
    }

    /**
     * Get the static base URI for the query
     *
     * @return the static base URI
     */

    public URI getBaseURI() {
        try {
            return new URI(env.getBaseURI());
        } catch (URISyntaxException err) {
            throw new IllegalStateException(err);
        }
    }

    /**
     * Set the ErrorListener to be used during this query compilation episode
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation.
     */

    public void setErrorListener(ErrorListener listener) {
        env.setErrorListener(listener);
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     *         compilation. If no user-supplied ErrorListener has been set, returns the system-supplied
     *         ErrorListener.
     */

    public ErrorListener getErrorListener() {
        return env.getErrorListener();
    }

    /**
     * Set whether trace hooks are to be included in the compiled code. To use tracing, it is necessary
     * both to compile the code with trace hooks included, and to supply a TraceListener at run-time
     *
     * @param option true if trace code is to be compiled in, false otherwise
     */

    public void setCompileWithTracing(boolean option) {
        env.setCompileWithTracing(option);
    }

    /**
     * Ask whether trace hooks are included in the compiled code.
     *
     * @return true if trace hooks are included, false if not.
     */

    public boolean isCompileWithTracing() {
        return env.isCompileWithTracing();
    }

    /**
     * Set a user-defined ModuleURIResolver for resolving URIs used in <code>import module</code>
     * declarations in the XQuery prolog.
     * This will override any ModuleURIResolver that was specified as part of the configuration.
     *
     * @param resolver the ModuleURIResolver to be used
     */

    public void setModuleURIResolver(ModuleURIResolver resolver) {
        env.setModuleURIResolver(resolver);
    }

    /**
     * Get the user-defined ModuleURIResolver for resolving URIs used in <code>import module</code>
     * declarations in the XQuery prolog; returns null if none has been explicitly set either
     * here or in the Saxon Configuration.
     *
     * @return the registered ModuleURIResolver
     */

    /*@Nullable*/
    public ModuleURIResolver getModuleURIResolver() {
        return env.getModuleURIResolver();
    }

    /**
     * Set the encoding of the supplied query. This is ignored if the query is supplied
     * in character form, that is, as a <code>String</code> or as a <code>Reader</code>. If no value
     * is set, the query processor will attempt to infer the encoding, defaulting to UTF-8 if no
     * information is available.
     *
     * @param encoding the encoding of the supplied query, for example "iso-8859-1"
     * @since 9.1
     */

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Get the encoding previously set for the supplied query.
     *
     * @return the encoding previously set using {@link #setEncoding(String)}, or null
     *         if no value has been set. Note that this is not necessarily the actual encoding of the query.
     * @since 9.2
     */

    public String getEncoding() {
        return encoding;
    }

    /**
     * Say whether the query is allowed to be updating. XQuery update syntax will be rejected
     * during query compilation unless this flag is set. XQuery Update is supported only under Saxon-EE.
     *
     * @param updating true if the query is allowed to use the XQuery Update facility
     *                 (requires Saxon-EE). If set to false, the query must not be an updating query. If set
     *                 to true, it may be either an updating or a non-updating query.
     * @since 9.1
     */

    public void setUpdatingEnabled(boolean updating) {
        env.setUpdatingEnabled(updating);
    }

    /**
     * Ask whether the query is allowed to use XQuery Update syntax
     *
     * @return true if the query is allowed to use the XQuery Update facility. Note that this
     *         does not necessarily mean that the query is an updating query; but if the value is false,
     *         the it must definitely be non-updating.
     * @since 9.1
     */

    public boolean isUpdatingEnabled() {
        return env.isUpdatingEnabled();
    }

    /**
     * Say that the query must be compiled to be schema-aware, even if it contains no
     * "import schema" declarations. Normally a query is treated as schema-aware
     * only if it contains one or more "import schema" declarations. If it is not schema-aware,
     * then all input documents must be untyped (or xs:anyType), and validation of temporary nodes is disallowed
     * (though validation of the final result tree is permitted). Setting the argument to true
     * means that schema-aware code will be compiled regardless.
     *
     * @param schemaAware If true, the stylesheet will be compiled with schema-awareness
     *                    enabled even if it contains no xsl:import-schema declarations. If false, the stylesheet
     *                    is treated as schema-aware only if it contains one or more xsl:import-schema declarations.
     *                    Note that setting the value to false does not disable use of an import-schema declaration.
     * @since 9.2
     */

    public void setSchemaAware(boolean schemaAware) {
        env.setSchemaAware(schemaAware);
    }

    /**
     * Ask whether schema-awareness has been requested either by means of a call on
     * {@link #setSchemaAware}
     *
     * @return true if schema-awareness has been requested
     * @since 9.2
     */

    public boolean isSchemaAware() {
        return env.isSchemaAware();
    }

    /**
     * Say whether an XQuery 1.0 or XQuery 3.0 processor is required.
     *
     * @param version Must be "1.0" or "3.0". At present onle limited support
     *                for XQuery 3.01 is available. This functionality is available only in Saxon-EE, and it cannot
     *                be used in conjunction with XQuery Updates. To use XQuery 3.0 features,
     *                the query prolog must also specify version="3.0".
     *                <p>In Saxon 9.3, the value "1.1" is accepted as a synonym for "3.0".</p>
     * @throws IllegalArgumentException if the version is not 1.0 or 3.0.
     * @since 9.2
     */

    public void setLanguageVersion(String version) {
        DecimalValue v;
        try {
            v = (DecimalValue) DecimalValue.makeDecimalValue(version, true).asAtomic();
        } catch (ValidationException ve) {
            throw new IllegalArgumentException(ve);
        }
        if (DecimalValue.ONE_POINT_ONE.equals(v)) {
            v = DecimalValue.THREE;
        }
        if (!DecimalValue.ONE.equals(v) && !DecimalValue.THREE.equals(v)) {
            throw new IllegalArgumentException("LanguageVersion " + v);
        }
        env.setLanguageVersion(v);
    }

    /**
     * Ask whether an XQuery 1.0 or XQuery 1.1 processor is being used
     *
     * @return version: "1.0" or "3.0"
     * @since 9.2
     */

    public String getLanguageVersion() {
        return env.getLanguageVersion().toString();
    }

    /**
     * Declare a namespace binding as part of the static context for queries compiled using this
     * XQueryCompiler. This binding may be overridden by a binding that appears in the query prolog.
     * The namespace binding will form part of the static context of the query, but it will not be copied
     * into result trees unless the prefix is actually used in an element or attribute name.
     *
     * @param prefix The namespace prefix. If the value is a zero-length string, this method sets the default
     *               namespace for elements and types.
     * @param uri    The namespace URI. It is possible to specify a zero-length string to "undeclare" a namespace;
     *               in this case the prefix will not be available for use, except in the case where the prefix
     *               is also a zero length string, in which case the absence of a prefix implies that the name
     *               is in no namespace.
     * @throws NullPointerException     if either the prefix or uri is null.
     * @throws IllegalArgumentException in the event of an invalid declaration of the XML namespace
     */

    public void declareNamespace(String prefix, String uri) {
        env.declareNamespace(prefix, uri);
    }

    /**
     * Bind a collation URI to a collation
     *
     * @param uri       the absolute collation URI
     * @param collation a {@link java.text.Collator} object that implements the required collation
     * @throws IllegalArgumentException if an attempt is made to rebind the standard URI
     *                                  for the Unicode codepoint collation
     * @since 9.4
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
        env.getCollationMap().setNamedCollation(uri, saxonCollation);
    }

    /**
     * Declare the default collation
     *
     * @param uri the absolute URI of the default collation. This URI must have been bound to a collation
     *            using the method {@link #declareCollation(String, java.text.Collator)}
     * @throws IllegalStateException if the collation URI has not been registered, unless it is the standard
     *                               Unicode codepoint collation which is registered implicitly
     * @since 9.4
     */

    public void declareDefaultCollation(String uri) {
        if (env.getCollationMap().getNamedCollation(uri) == null) {
            throw new IllegalStateException("Unknown collation " + uri);
        }
        env.getCollationMap().setDefaultCollationName(uri);
    }


    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextItemType = type;
        env.setRequiredContextItemType(type.getUnderlyingItemType());
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Compile a library module supplied as a string. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(String query) throws SaxonApiException {
        try {
            env.compileLibrary(query);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a library module  supplied as a file. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The encoding of the input stream may be specified using {@link #setEncoding(String)};
     * if this has not been set, the compiler determines the encoding from the version header of the
     * query, and if that too is absent, it assumes UTF-8.</p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the file containing the query. The URI corresponding to this file will be used as the
     *              base URI of the query, overriding any URI supplied using {@link #setBaseURI(java.net.URI)} (but not
     *              overriding any base URI specified within the query prolog)
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.2
     */

    public void compileLibrary(File query) throws SaxonApiException, IOException {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(query);
            String savedBaseUri = env.getBaseURI();
            env.setBaseURI(query.toURI().toString());
            env.compileLibrary(stream, encoding);
            env.setBaseURI(savedBaseUri);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Compile a library module supplied as a Reader. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(Reader query) throws SaxonApiException {
        try {
            env.compileLibrary(query);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        } catch (IOException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a library module supplied as an InputStream. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The encoding of the input stream may be specified using {@link #setEncoding(String)};
     * if this has not been set, the compiler determines the encoding from the version header of the
     * query, and if that too is absent, it assumes UTF-8. </p>
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(InputStream query) throws SaxonApiException {
        try {
            env.compileLibrary(query, encoding);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        } catch (IOException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a string.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     *
     * @param query the text of the query
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.0
     */

    public XQueryExecutable compile(String query) throws SaxonApiException {
        try {
            return new XQueryExecutable(processor, env.compileQuery(query));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a file
     *
     * @param query the file containing the query. The URI corresponding to this file will be used as the
     *              base URI of the query, overriding any URI supplied using {@link #setBaseURI(java.net.URI)} (but not
     *              overriding any base URI specified within the query prolog)
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.1
     */

    public XQueryExecutable compile(File query) throws SaxonApiException, IOException {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(query);
            String savedBaseUri = env.getBaseURI();
            env.setBaseURI(query.toURI().toString());
            XQueryExecutable exec =
                    new XQueryExecutable(processor, env.compileQuery(stream, encoding));
            env.setBaseURI(savedBaseUri);
            return exec;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Compile a query supplied as an InputStream
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     *
     * @param query the input stream on which the query is supplied. This will be consumed by this method
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.1
     */

    public XQueryExecutable compile(InputStream query) throws SaxonApiException, IOException {
        try {
            return new XQueryExecutable(processor, env.compileQuery(query, encoding));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a Reader
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     *
     * @param query the input stream on which the query is supplied. This will be consumed by this method
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.1
     */

    public XQueryExecutable compile(Reader query) throws SaxonApiException, IOException {
        try {
            return new XQueryExecutable(processor, env.compileQuery(query));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying {@link net.sf.saxon.query.StaticQueryContext} object that maintains the static context
     * information on behalf of this XQueryCompiler. This method provides an escape hatch to internal Saxon
     * implementation objects that offer a finer and lower-level degree of control than the s9api classes and
     * methods. Some of these classes and methods may change from release to release.
     *
     * @return the underlying StaticQueryContext object
     * @since 9.2
     */

    public StaticQueryContext getUnderlyingStaticContext() {
        return env;
    }


}

