////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.LocationMap;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.TraceCodeInjector;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;

/**
 * StaticQueryContext contains information used to build a StaticContext for use when processing XQuery
 * expressions.
 *
 * <p>Despite its name, <code>StaticQueryContext</code> no longer implements the <code>StaticContext</code>
 * interface, which means it cannot be used directly by Saxon when parsing a query. Instead it is first copied
 * to create a <code>QueryModule</code> object, which does implement the <code>StaticContext</code> interface.
 *
 * <p>The application constructs a StaticQueryContext
 * and initializes it with information about the context, for example, default namespaces, base URI, and so on.
 * When a query is compiled using this StaticQueryContext, the query parser makes a copy of the StaticQueryContext
 * and uses this internally, modifying it with information obtained from the query prolog, as well as information
 * such as namespace and variable declarations that can occur at any point in the query. The query parser does
 * not modify the original StaticQueryContext supplied by the calling application, which may therefore be used
 * for compiling multiple queries, serially or even in multiple threads.</p>
 *
 * <p>This class forms part of Saxon's published XQuery API. Methods that
 * are considered stable are labelled with the JavaDoc "since" tag.
 * The value 8.4 indicates a method introduced at or before Saxon 8.4; other
 * values indicate the version at which the method was introduced.</p>
 *
 * <p>In the longer term, this entire API may at some stage be superseded by a proposed
 * standard Java API for XQuery.</p>
 *
 * @since 8.4
 */

public class StaticQueryContext {

    private Configuration config;
    private NamePool namePool;
    private String baseURI;
    private HashMap<String, String> userDeclaredNamespaces;
    /*@Nullable*/ private Set<GlobalVariable> userDeclaredVariables;
    private boolean inheritNamespaces = true;
    private boolean preserveNamespaces = true;
    private int constructionMode = Validation.PRESERVE;
    /*@Nullable*/ private NamespaceResolver externalNamespaceResolver = null;
    private String defaultFunctionNamespace;
    /*@Nullable*/ private String defaultElementNamespace = NamespaceConstant.NULL;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private boolean preserveSpace = false;
    private boolean defaultEmptyLeast = true;
    /*@Nullable*/ private ModuleURIResolver moduleURIResolver;
    private ErrorListener errorListener;
    /*@Nullable*/ private CodeInjector codeInjector;
    private boolean isUpdating = false;
    private DecimalValue languageVersion = DecimalValue.ONE;
    private CollationMap collationMap;
    //private LocationMap locationMap;
    private boolean schemaAware;


    /**
     * Private constructor used when copying a context
     */

    protected StaticQueryContext() {
    }

    /**
     * Create a StaticQueryContext using a given Configuration. This creates a StaticQueryContext for a main module
     * (that is, a module that is not a library module).
     * @param config the Saxon Configuration
     *
     * @since 8.4
     * @deprecated since 9.2. Use config.newStaticQueryContext(). This will create a StaticQueryContext with
     * capabilities appropriate to the configuration (for example, offering XQuery 1.1 support).
     */

    public StaticQueryContext(/*@NotNull*/ Configuration config) {
        this(config.getDefaultStaticQueryContext());
        this.config = config;
        namePool = config.getNamePool();
        errorListener = config.getErrorListener();
        moduleURIResolver = config.getModuleURIResolver();
        if (errorListener instanceof StandardErrorListener) {
            errorListener = ((StandardErrorListener)errorListener).makeAnother(Configuration.XQUERY);
            ((StandardErrorListener)errorListener).setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
        }
        collationMap = new CollationMap(config.getCollationMap());
        //locationMap = new LocationMap();
        schemaAware = (Boolean)config.getConfigurationProperty(FeatureKeys.XQUERY_SCHEMA_AWARE);
//        executable = new Executable(config);
//        executable.setCollationTable(new CollationMap(config.getCollationMap()));
//        executable.setHostLanguage(Configuration.XQUERY);
//        executable.setLocationMap(new LocationMap());
//        executable.setSchemaAware((Boolean)config.getConfigurationProperty(FeatureKeys.XQUERY_SCHEMA_AWARE));
        reset();
    }

    /**
      * Create a StaticQueryContext using a given Configuration. This creates a StaticQueryContext for a main module
      * (that is, a module that is not a library module).
      *
      * <p>This method is primarily for internal use. The recommended way to create a StaticQueryContext is by
      * using the factory method Configuration.newStaticQueryContext().</p>
      * @param config the Saxon Configuration
      * @param initial if set, this is the StaticQueryContext owned by the Configuration
      */

     public StaticQueryContext(/*@NotNull*/ Configuration config, boolean initial) {
         if (initial) {
             this.config = config;
             namePool = config.getNamePool();
             errorListener = config.getErrorListener();
             moduleURIResolver = config.getModuleURIResolver();
             if (errorListener instanceof StandardErrorListener) {
                 errorListener = ((StandardErrorListener)errorListener).makeAnother(Configuration.XQUERY);
                 ((StandardErrorListener)errorListener).setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
             }
             collationMap = new CollationMap(config.getCollationMap());
             //locationMap = new LocationMap();
             //schemaAware = (Boolean)config.getConfigurationProperty(FeatureKeys.XQUERY_SCHEMA_AWARE);
//             executable = new Executable(config);
//             executable.setCollationTable(new CollationMap(config.getCollationMap()));
//             executable.setHostLanguage(Configuration.XQUERY);
//             executable.setLocationMap(new LocationMap());
             reset();
         } else {
             copyFrom(config.getDefaultStaticQueryContext());
         }
     }


    /**
     * Create a copy of a supplied StaticQueryContext
     * @param c the StaticQueryContext to be copied
     */

    public StaticQueryContext(/*@NotNull*/ StaticQueryContext c) {
        copyFrom(c);
    }

    private void copyFrom(/*@NotNull*/ StaticQueryContext c) {
        config = c.config;
        namePool = c.namePool;
        baseURI = c.baseURI;
        moduleURIResolver = c.moduleURIResolver;
        if (c.userDeclaredNamespaces != null) {
            userDeclaredNamespaces = new HashMap<String, String>(c.userDeclaredNamespaces);
        }
        if (c.userDeclaredVariables != null) {
            userDeclaredVariables = new HashSet<GlobalVariable>(c.userDeclaredVariables);
        }
        inheritNamespaces = c.inheritNamespaces;
        preserveNamespaces = c.preserveNamespaces;
        constructionMode = c.constructionMode;
        externalNamespaceResolver = c.externalNamespaceResolver;
        defaultElementNamespace = c.defaultElementNamespace;
        defaultFunctionNamespace = c.defaultFunctionNamespace;
        requiredContextItemType = c.requiredContextItemType;
        preserveSpace = c.preserveSpace;
        defaultEmptyLeast = c.defaultEmptyLeast;
        moduleURIResolver = c.moduleURIResolver;
        errorListener = c.errorListener;
        codeInjector = c.codeInjector;
        isUpdating = c.isUpdating;
        languageVersion = c.languageVersion;
        collationMap = new CollationMap(c.collationMap);
        //locationMap = new LocationMap();
        schemaAware = c.schemaAware;
//        executable = new Executable(config);
//        executable.setCollationTable(new CollationMap(c.executable.getCollationTable()));
//        executable.setHostLanguage(Configuration.XQUERY);
//        executable.setLocationMap(new LocationMap());
//        executable.setSchemaAware(c.executable.isSchemaAware());
    }


    /**
     * Reset the state of this StaticQueryContext to an uninitialized state
     *
     * @since 8.4
     */

    public void reset() {
        userDeclaredNamespaces = new HashMap<String, String>(10);
        externalNamespaceResolver = null;
        errorListener = config.getErrorListener();
        if (errorListener instanceof StandardErrorListener) {
            errorListener = ((StandardErrorListener)errorListener).makeAnother(Configuration.XQUERY);
            ((StandardErrorListener)errorListener).setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
        }
        constructionMode = getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY) ?
                Validation.PRESERVE : Validation.STRIP;
        preserveSpace = false;
        defaultEmptyLeast = true;
        requiredContextItemType = AnyItemType.getInstance();
        defaultFunctionNamespace = NamespaceConstant.FN;
        defaultElementNamespace = NamespaceConstant.NULL;
        moduleURIResolver = config.getModuleURIResolver();
        clearNamespaces();
        isUpdating = false;
        languageVersion = DecimalValue.ONE;

    }

    /**
     * Set the Configuration options
     * @param config the Saxon Configuration
     * @throws IllegalArgumentException if the configuration supplied is different from the existing
     *                                  configuration
     * @since 8.4
     */

    public void setConfiguration(/*@NotNull*/ Configuration config) {
        if (this.config != null && this.config != config) {
            throw new IllegalArgumentException("Configuration cannot be changed dynamically");
        }
        this.config = config;
        namePool = config.getNamePool();
    }

    /**
     * Get the Configuration options
     * @return the Saxon configuration
     * @since 8.4
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the executable containing this query
     * @return the executable (which is newly created by this method)
     */

    /*@NotNull*/ public Executable getExecutable() {
        Executable executable = new Executable(config);
        executable.setCollationMap(collationMap);
        executable.setLocationMap(new LocationMap());
        executable.setSchemaAware(schemaAware);
        executable.setHostLanguage(Configuration.XQUERY, getLanguageVersion().equals(DecimalValue.THREE));
        return executable;
    }

    /**
     * Say whether this query is schema-aware
     * @param aware true if this query is schema-aware
     * @since 9.2.1.2
     */

    public void setSchemaAware(boolean aware) {
        this.schemaAware = aware;
    }

    /**
     * Ask whether this query is schema-aware
     * @return true if this query is schema-aware
     * @since 9.2.1.2
     */

    public boolean isSchemaAware() {
        return schemaAware;
    }

    /**
     * Set the Base URI of the query
     * @param baseURI the base URI of the query, or null to indicate that no base URI is available
     * @since 8.4
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Convenience method for building Saxon's internal representation of a source XML
     * document. The document will be built using Configuration (and NamePool) associated
     * with this StaticQueryContext.
     *
     * <p>This method is retained for backwards compatibility; however, it is merely a wrapper
     * around the method {@link Configuration#buildDocument}, which should be called in preference.</p>
     *
     * @param source Any javax.xml.transform.Source object representing the document against
     *               which queries will be executed. Note that a Saxon {@link net.sf.saxon.om.DocumentInfo DocumentInfo}
     *               (indeed any {@link net.sf.saxon.om.NodeInfo NodeInfo})
     *               can be used as a Source. To use a third-party DOM Document as a source, create an instance of
     *               {@link javax.xml.transform.dom.DOMSource DOMSource} to wrap it.
     *               <p>For additional control over the way in which the source document is processed,
     *               supply an {@link net.sf.saxon.lib.AugmentedSource AugmentedSource} object and set appropriate
     *               options on the object.</p>
     * @return the DocumentInfo representing the root node of the resulting document object.
     * @since 8.4
     * @deprecated since 9.2: use {@link Configuration#buildDocument(javax.xml.transform.Source)}
     * @throws XPathException if building the document fails
     */

    /*@NotNull*/ public DocumentInfo buildDocument(Source source) throws XPathException {
        return config.buildDocument(source);
    }

    /**
     * Set the language version.
     * @param version The XQuery language version. Must be "1.0" or "3.0" (the value 1.1 is also
     * accepted temporarily).
     * Note that XQuery 3.0 language features cannot be used with XQuery Updates. The Query
     * Prolog must also specify version="3.0".
     * @since 9.2; changed in 9.3 to expect a DecimalValue rather than a string
     */

    public void setLanguageVersion(DecimalValue version) {
        if (DecimalValue.ONE.equals(version) || DecimalValue.THREE.equals(version)) {
            languageVersion = version;
        } else if (DecimalValue.ONE_POINT_ONE.equals(version)) {
            languageVersion = DecimalValue.THREE;
        } else {
            throw new IllegalArgumentException("languageVersion = " + version);
        }
    }

    /**
     * Get the language version
     * @return the language version. Either "1.0" or "1.1". Default is "1.0".
     * @since 9.2; changed in 9.3 to return a DecimalValue rather than a string.
     */

    public DecimalValue getLanguageVersion() {
        return languageVersion;
    }

    /**
     * Get any extension function library that was previously set.
     * @return the extension function library, or null if none has been set. The result will always be null if called
     * in Saxon-HE; setting an extension function library requires the Saxon-PE or Saxon-EE versions of this class.
     *
     * @since 9.4
     */


   /*@Nullable*/ public FunctionLibrary getExtensionFunctionLibrary() {
        return null;
    }


    /**
     * Ask whether compile-time generation of trace code was requested
     * @since 9.0
     * @return true if compile-time generation of code was requested
     */

    public boolean isCompileWithTracing() {
        return codeInjector instanceof TraceCodeInjector;
    }

    /**
     * Request compile-time generation of trace code (or not)
     * @param trace true if compile-time generation of trace code is required
     * @since 9.0
     */

    public void setCompileWithTracing(boolean trace) {
        if (trace) {
            codeInjector = new TraceCodeInjector();
        } else {
            codeInjector = null;
        }
    }

    /**
     * Request that the parser should insert custom code into the expression tree
     * by calling a supplied CodeInjector to process each expression as it is parsed,
     * for example for tracing or performance measurement
     * @param injector the CodeInjector to be used. May be null, in which case
     * no code is injected
     * @since 9.4
     */

    public void setCodeInjector( /*@Nullable*/ CodeInjector injector) {
        this.codeInjector = injector;
    }

    /**
     * Get any CodeInjector that has been registered
     * @return the registered CodeInjector, or null
     * @since 9.4
     */

     /*@Nullable*/ public CodeInjector getCodeInjector() {
        return codeInjector;
    }

    /**
     * Ask whether XQuery Update is allowed
     * @return true if XQuery update is supported by queries using this context
     */

    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * Set the namespace inheritance mode
     *
     * @param inherit true if namespaces are inherited, false if not
     * @since 8.4
     */

    public void setInheritNamespaces(boolean inherit) {
        inheritNamespaces = inherit;
    }

    /**
     * Get the namespace inheritance mode
     *
     * @return true if namespaces are inherited, false if not
     * @since 8.4
     */

    public boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

    /**
     * Set the namespace copy mode
     *
     * @param inherit true if namespaces are preserved, false if not
     * @since 8.4
     */

    public void setPreserveNamespaces(boolean inherit) {
        preserveNamespaces = inherit;
    }

    /**
     * Get the namespace copy mode
     *
     * @return true if namespaces are preserved, false if not
     * @since 8.4
     */

    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    /**
     * Set the construction mode for this module
     *
     * @param mode one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     * @since 8.4
     */

    public void setConstructionMode(int mode) {
        constructionMode = mode;
    }

    /**
     * Get the current construction mode
     *
     * @return one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     * @since 8.4
     */

    public int getConstructionMode() {
        return constructionMode;
    }

    /**
     * Prepare an XQuery query for subsequent evaluation. The source text of the query
     * is supplied as a String. The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     *
     * <p>Note that this interface makes the caller responsible for decoding the query and
     * presenting it as a string of characters. This means it is likely that any encoding
     * specified in the query prolog will be ignored.</p>
     *
     * @param query The XQuery query to be evaluated, supplied as a string.
     * @return an XQueryExpression object representing the prepared expression
     * @throws net.sf.saxon.trans.XPathException
     *          if the syntax of the expression is wrong,
     *          or if it references namespaces, variables, or functions that have not been declared,
     *          or contains other static errors.
     * @since 8.4
     */

    /*@NotNull*/ public XQueryExpression compileQuery(/*@NotNull*/ String query) throws XPathException {
        QueryParser qp = (QueryParser)config.newExpressionParser("XQ", isUpdating, languageVersion);
        if (codeInjector != null) {
            qp.setCodeInjector(codeInjector);
        } else if (config.isCompileWithTracing()) {
            qp.setCodeInjector(new TraceCodeInjector());
        }
        QueryModule mainModule = new QueryModule(this);
        Executable executable = getExecutable();
        mainModule.setExecutable(executable);
        if (languageVersion.equals(DecimalValue.THREE)) {
            qp.setDisableCycleChecks(true);
        }
        return qp.makeXQueryExpression(query, mainModule, config);
    }

    /**
     * Prepare an XQuery query for subsequent evaluation. The Query is supplied
     * in the form of a Reader. The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     *
     * <p>Note that this interface makes the Reader responsible for decoding the query and
     * presenting it as a stream of characters. This means it is likely that any encoding
     * specified in the query prolog will be ignored. Also, some implementations of Reader
     * cannot handle a byte order mark.</p>
     *
     * @param source A Reader giving access to the text of the XQuery query to be compiled.
     * @return an XPathExpression object representing the prepared expression.
     * @throws net.sf.saxon.trans.XPathException
     *                             if the syntax of the expression is wrong, or if it references namespaces,
     *                             variables, or functions that have not been declared, or any other static error is reported.
     * @throws java.io.IOException if a failure occurs reading the supplied input.
     * @since 8.4
     */

     /*@Nullable*/ public XQueryExpression compileQuery(/*@NotNull*/ Reader source)
            throws XPathException, IOException {
        char[] buffer = new char[4096];
        StringBuilder sb = new StringBuilder(4096);
        while (true) {
            int n = source.read(buffer);
            if (n > 0) {
                sb.append(buffer, 0, n);
            } else {
                break;
            }
        }
        return compileQuery(sb.toString());
    }

    /**
     * Prepare an XQuery query for subsequent evaluation. The Query is supplied
     * in the form of a InputStream, with an optional encoding. If the encoding is not specified,
     * the query parser attempts to obtain the encoding by inspecting the input stream: it looks specifically
     * for a byte order mark, and for the encoding option in the version declaration of an XQuery prolog.
     * The encoding defaults to UTF-8.
     * The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     *
     * @param source   An InputStream giving access to the text of the XQuery query to be compiled, as a stream
     *                 of octets
     * @param encoding The encoding used to translate characters to octets in the query source. The parameter
     *                 may be null: in this case the query parser attempts to infer the encoding by inspecting the source,
     *                 and if that fails, it assumes UTF-8 encoding
     * @return an XPathExpression object representing the prepared expression.
     * @throws net.sf.saxon.trans.XPathException
     *                             if the syntax of the expression is wrong, or if it references namespaces,
     *                             variables, or functions that have not been declared, or any other static error is reported.
     * @throws java.io.IOException if a failure occurs reading the supplied input.
     * @since 8.5
     */
     /*@Nullable*/
     public XQueryExpression compileQuery(/*@NotNull*/ InputStream source, /*@Nullable*/ String encoding)
            throws XPathException, IOException {
        String query = QueryReader.readInputStream(source, encoding, config.getNameChecker());
        return compileQuery(query);
    }

    /**
      * Compile an XQuery library module for subsequent evaluation. This method is supported
      * only in Saxon-EE
      * @param query the content of the module, as a string
      * @throws XPathException if a static error exists in the query
      * @throws UnsupportedOperationException if not using Saxon-EE
      * @since 9.2 (changed in 9.3 to return void)
      */

     public void compileLibrary(String query) throws XPathException {
         throw new UnsupportedOperationException("Separate compilation of query libraries requires Saxon-EE");
     }

     /**
      * Prepare an XQuery library module for subsequent evaluation. This method is supported
      * only in Saxon-EE. The effect of the method is that subsequent query compilations using this static
      * context can import the module URI without specifying a location hint; the import then takes effect
      * without requiring the module to be compiled each time it is imported.
      * @param source the content of the module, as a Reader which supplies the source code
      * @throws XPathException if a static error exists in the query
      * @throws IOException if the input cannot be read
      * @throws UnsupportedOperationException if not using Saxon-EE
      * @since 9.2 (changed in 9.3 to return void)
      */

     public void compileLibrary(Reader source)
             throws XPathException, IOException {
         throw new UnsupportedOperationException("Separate compilation of query libraries requires Saxon-EE");
     }

     /**
      * Prepare an XQuery library module for subsequent evaluation. This method is supported
      * only in Saxon-EE. The effect of the method is that subsequent query compilations using this static
      * context can import the module URI without specifying a location hint; the import then takes effect
      * without requiring the module to be compiled each time it is imported.
      * @param source the content of the module, as an InputStream which supplies the source code
      * @param encoding the character encoding of the input stream. May be null, in which case the encoding
      * is inferred, for example by looking at the query declaration
      * @throws XPathException if a static error exists in the query
      * @throws IOException if the input cannot be read
      * @throws UnsupportedOperationException if not using Saxon-EE
      * @since 9.2 (changed in 9.3 to return void)
      */

     public void compileLibrary(InputStream source, /*@Nullable*/ String encoding)
             throws XPathException, IOException {
         throw new UnsupportedOperationException("Separate compilation of query libraries requires Saxon-EE");
     }

    /**
     * Get a previously compiled library module
     * @param namespace the module namespace
     * @return the QueryLibrary if a module with this namespace has been compiled as a library module;
     * otherwise null. Always null when not using Saxon-EE.
     * @since 9.3
     */

     /*@Nullable*/ public QueryLibrary getCompiledLibrary(String namespace) {
        return null;
    }


    /**
     * Declare a namespace whose prefix can be used in expressions. This is
     * equivalent to declaring a prefix in the Query prolog.
     * Any namespace declared in the Query prolog overrides a namespace declared using
     * this API.
     *
     * @param prefix   The namespace prefix. Must not be null. Setting this to "" means that the
     *                 namespace will be used as the default namespace for elements and types.
     * @param uri      The namespace URI. Must not be null. The value "" (zero-length string) is used
     *                 to undeclare a namespace; it is not an error if there is no existing binding for
     *                 the namespace prefix.
     * @throws NullPointerException if either the prefix or URI is null
     * @throws IllegalArgumentException if the prefix is "xml" and the namespace is not the XML namespace, or vice
     * versa; or if the prefix is "xmlns", or the URI is "http://www.w3.org/2000/xmlns/"
     * @since 9.0
     */

    public void declareNamespace( /*@Nullable*/ String prefix, /*@Nullable*/ String uri) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declareNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declareNamespace()");
        }
        if ((prefix.equals("xml") != uri.equals(NamespaceConstant.XML))) {
            throw new IllegalArgumentException("Misdeclaration of XML namespace");
        }
        if (prefix.equals("xmlns") || uri.equals(NamespaceConstant.XMLNS)) {
            throw new IllegalArgumentException("Misdeclaration of xmlns namespace");
        }
        if (prefix.length() == 0) {
            defaultElementNamespace = uri;
        }
        if (uri.length() == 0) {
            userDeclaredNamespaces.remove(prefix);
        } else {
            userDeclaredNamespaces.put(prefix, uri);
        }

    }

    /**
     * Clear all the user-declared namespaces
     *
     * @since 9.0
     */

    public void clearNamespaces() {
        userDeclaredNamespaces.clear();
        declareNamespace("xml", NamespaceConstant.XML);
        declareNamespace("xs", NamespaceConstant.SCHEMA);
        declareNamespace("xsi", NamespaceConstant.SCHEMA_INSTANCE);
        declareNamespace("fn", NamespaceConstant.FN);
        declareNamespace("local", NamespaceConstant.LOCAL);
        declareNamespace("err", NamespaceConstant.ERR);
        declareNamespace("saxon", NamespaceConstant.SAXON);
        declareNamespace("", "");

    }

    /**
     * Get the map of user-declared namespaces
     * @return the user-declared namespaces
     */

    protected HashMap<String, String> getUserDeclaredNamespaces() {
        return userDeclaredNamespaces;
    }

    /**
     * Get the namespace prefixes that have been declared using the method {@link #declareNamespace}
     * @return an iterator that returns the namespace prefixes that have been explicitly declared, as
     * strings. The default namespace for elements and types will be included, using the prefix "".
     * @since 9.0
     */

    public Iterator<String> iterateDeclaredPrefixes() {
        return userDeclaredNamespaces.keySet().iterator();
    }

    /**
     * Get the namespace URI for a given prefix, which must have been declared using the method
     * {@link #declareNamespace}. Note that this method will not call the external namespace resolver
     * to resolve the prefix.
     * @param prefix the namespace prefix, or "" to represent the null prefix
     * @return the namespace URI. Returns "" to represent the non-namespace,
     * null to indicate that the prefix has not been declared
     */

    public String getNamespaceForPrefix(String prefix) {
        return userDeclaredNamespaces.get(prefix);
    }

    /**
     * Set an external namespace resolver. If a namespace prefix cannot be resolved using any
     * other mechanism, then as a last resort the external namespace resolver is called to
     * obtain a URI for the given prefix.
     *
     * <p><i>Changed in Saxon 9.0 so that the namespaces resolved by the external namespace resolver
     * are available at run-time, just like namespaces declared in the query prolog. In consequence,
     * the supplied NamespaceResolver must now implement the
     * {@link net.sf.saxon.om.NamespaceResolver#iteratePrefixes()} method.</i></p>
     *
     * @param resolver the external namespace resolver
     */

    public void setExternalNamespaceResolver(NamespaceResolver resolver) {
        externalNamespaceResolver = resolver;
    }

    /**
     * Get the external namespace resolver that has been registered using
     * setExternalNamespaceResolver(), if any.
     * @return the external namespace resolver
     */

     /*@Nullable*/ public NamespaceResolver getExternalNamespaceResolver() {
        return externalNamespaceResolver;
    }

    /**
     * Get the default function namespace
     *
     * @return the default function namespace (defaults to the fn: namespace)
     * @since 8.4
     */

    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the default function namespace
     *
     * @param defaultFunctionNamespace The namespace to be used for unprefixed function calls
     * @since 8.4
     */

    public void setDefaultFunctionNamespace(String defaultFunctionNamespace) {
        this.defaultFunctionNamespace = defaultFunctionNamespace;
    }

    /**
     * Set the default element namespace
     * @param uri the namespace URI to be used as the default namespace for elements and types
     * @since 8.4
     */

    public void setDefaultElementNamespace(String uri) {
        defaultElementNamespace = uri;
        declareNamespace("", uri);
    }

    /**
     * Get the default namespace for elements and types
     * @return the namespace URI to be used as the default namespace for elements and types
     * @since 8.9 Modified in 8.9 to return the namespace URI as a string rather than an integer code
     */

     /*@Nullable*/ public String getDefaultElementNamespace() {
        return defaultElementNamespace;
    }

    /**
     * Declare a global variable. This has the same effect as including a global variable declaration
     * in the Query Prolog of the main query module. A static error occurs when compiling the query if the
     * query prolog contains a declaration of the same variable.
     * @param qName the qualified name of the variable
     * @param type the declared type of the variable
     * @param value the initial value of the variable. May be null if the variable is external.
     * @param external true if the variable is external, that is, if its value may be set at run-time.
     * @throws NullPointerException if the value is null, unless the variable is external
     * @throws XPathException if the value of the variable is not consistent with its type.
     * @since 9.1
     */

    public void declareGlobalVariable(
            StructuredQName qName, /*@NotNull*/ SequenceType type, /*@Nullable*/ Sequence value, boolean external)
            throws XPathException {
        if (value == null && !external) {
            throw new NullPointerException("No initial value for declared variable");
        }
        if (value != null && !type.matches(value, getConfiguration())) {    // bug 2248
            throw new XPathException("Value of declared variable does not match its type");
        }
        GlobalVariable var = (external ? new GlobalParam() : new GlobalVariable());
        var.setExecutable(getExecutable());
        var.setVariableQName(qName);
        var.setRequiredType(type);
        if (value != null) {    // bug 2248
            var.setSelectExpression(Literal.makeLiteral(SequenceTool.toGroundedValue(value)));
        }
        if (userDeclaredVariables == null) {
            userDeclaredVariables = new HashSet<GlobalVariable>();
        }
        userDeclaredVariables.add(var);
    }

    /**
     * Iterate over all the declared global variables
     * @return an iterator over all the global variables that have been declared. They are returned
     * as instances of class {@link GlobalVariable}
     * @since 9.1
     */

    public Iterator<GlobalVariable> iterateDeclaredGlobalVariables() {
        if (userDeclaredVariables == null) {
            List<GlobalVariable> empty = Collections.emptyList();
            return empty.iterator();
        } else {
            return userDeclaredVariables.iterator();
        }
    }

    /**
     * Clear all declared global variables
     * @since 9.1
     */

    public void clearDeclaredGlobalVariables() {
        userDeclaredVariables = null;
    }

    /**
     * Set a user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog.
     * This will be used for resolving URIs in XQuery "import module" declarations, overriding
     * any ModuleURIResolver that was specified as part of the configuration.
     * @param resolver the ModuleURIResolver to be used
     */

    public void setModuleURIResolver(ModuleURIResolver resolver) {
        moduleURIResolver = resolver;
    }

    /**
     * Get the user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog; returns null if none has been explicitly set either
     * on the StaticQueryContext or on the Configuration.
     * @return the registered ModuleURIResolver
     */

     /*@Nullable*/ public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }


    /**
     * Declare a named collation. Collations are only available in a query if this method
     * has been called externally to declare the collation and associate it with an
     * implementation, in the form of a Java Comparator. The default collation is the
     * Unicode codepoint collation, unless otherwise specified.
     *
     * @param name       The name of the collation (technically, a URI)
     * @param comparator The Java Comparator used to implement the collating sequence
     * @since 8.4.
     */

    public void declareCollation(String name, Comparator comparator) {
        declareCollation(name, new SimpleCollation(comparator));
    }


    /**
     * Declare a named collation. Collations are only available in a query if this method
     * has been called externally to declare the collation and associate it with an
     * implementation, in the form of a Java StringCollator. The default collation is the
     * Unicode codepoint collation, unless otherwise specified.
     *
     * @param name       The name of the collation (technically, a URI)
     * @param comparator The Java Comparator used to implement the collating sequence
     * @since 8.9.
     */

    public void declareCollation(String name, StringCollator comparator) {
        collationMap.setNamedCollation(name, comparator);
    }

    /**
     * Set the default collation.
     * @param name The collation name, as specified in the query prolog. The name
     * is not validated until it is used.
     * @since 8.4. Changed in 8.6 so it no longer validates the collation name: this is
     * because the base URI is not necessarily known at the point where the default
     * collation is declared.
     * @throws NullPointerException if the supplied value is null
     */

    public void declareDefaultCollation(String name)  {
        collationMap.setDefaultCollationName(name);
    }

    /**
     * Get a named collation.
     * @param name the name of the collation, as an absolute URI
     * @return the collation identified by the given name, as set previously using declareCollation.
     *         If no collation with this name has been declared, the method calls the CollationURIResolver
     *         to locate a collation with this name.
     *         Return null if no collation with this name is found.
     * @throws NullPointerException if the collation name argument is null.
     * @since 8.4
     */

    public StringCollator getCollation(/*@Nullable*/ String name) {
        if (name == null) {
            throw new NullPointerException("collationName");
        }
        return collationMap.getNamedCollation(name);
    }

    /**
     * Get the collation map
     * @return the collation map, which identifies all the known collations
     */

    public CollationMap getCollationMap() {
        return collationMap;
    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined. The name is returned in the form
     *         it was specified; that is, it is not yet resolved against the base URI. (This
     *         is because the base URI declaration can follow the default collation declaration
     *         in the query prolog.) If no default collation has been specified, the "default default"
     *         (that is, the Unicode codepoint collation) is returned.
     * @since 8.4
     */

     /*@Nullable*/ public String getDefaultCollationName() {
        return collationMap.getDefaultCollationName();
    }

    /**
     * Get a CollationMap that maps all registered collations to Comparators.
     * Note that this returns a snapshot copy of the data held by the static context.
     * This method is provided for internal use by the query processor.
     * <p/>
     * This method is intended for internal use.
     * @return the CollationMap containing all the collations defined in this static context
     */

    /*@NotNull*/ public CollationMap getAllCollations() {
        return new CollationMap(collationMap);
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     * @param type the required type of the context item
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextItemType = type;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     * @return the required type of the context item
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Get the NamePool used for compiling expressions
     * @return the name pool
     * @since 8.4
     */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
     * Get the system ID of the container of the expression. Used to construct error messages.
     * Note that the systemID and the Base URI are currently identical, but they might be distinguished
     * in the future.
     *
     * @return the Base URI
     * @since 8.4
     */

    public String getSystemId() {
        return baseURI;
    }

    /**
     * Get the Base URI of the query, for resolving any relative URI's used
     * in the expression.
     * Note that the systemID and the Base URI are currently identical, but they might be distinguished
     * in the future.
     * Used by the document() function.
     *
     * @return the base URI of the query
     * @since 8.4
     */

    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Set the policy for preserving boundary space
     * @param preserve true if boundary space is to be preserved, false if it is to be stripped
     * @since 9.0
     */

    public void setPreserveBoundarySpace(boolean preserve) {
        preserveSpace = preserve;
    }

    /**
     * Ask whether the policy for boundary space is "preserve" or "strip"
     * @return true if the policy is to preserve boundary space, false if it is to strip it
     * @since 9.0
     */

    public boolean isPreserveBoundarySpace() {
        return preserveSpace;
    }

    /**
     * Set the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     * @param least true if the empty sequence is considered less than any other value (the default),
     * false if it is considered greater than any other value
     * @since 9.0
     */

    public void setEmptyLeast(boolean least) {
        defaultEmptyLeast = least;
    }

    /**
     * Ask where an empty sequence should appear in the collation order, if not otherwise
     * specified in the "order by" clause
     * @return true if the empty sequence is considered less than any other value (the default),
     * false if it is considered greater than any other value
     * @since 9.0
     */

    public boolean isEmptyLeast() {
        return defaultEmptyLeast;
    }

    /**
     * Set the ErrorListener to be used to report compile-time errors in a query. This will also
     * be the default for the run-time error listener used to report dynamic errors
     * @param listener the ErrorListener to be used
     */

    public void setErrorListener(ErrorListener listener) {
        errorListener = listener;
        if (errorListener instanceof StandardErrorListener) {
            errorListener = ((StandardErrorListener)errorListener).makeAnother(Configuration.XQUERY);
            ((StandardErrorListener)errorListener).setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
        }
    }

    /**
     * Get the ErrorListener in use for this static context
     * @return the registered ErrorListener
     */

    public ErrorListener getErrorListener() {
        if (errorListener == null) {
            errorListener = config.getErrorListener();
        }
        return errorListener;
    }

    /**
     * Say whether the query is allowed to be updating. XQuery update syntax will be rejected
     * during query compilation unless this flag is set.
     * @param updating true if the query is allowed to use the XQuery Update facility
     * (requires Saxon-EE). If set to false, the query must not be an updating query. If set
     * to true, it may be either an updating or a non-updating query.
     * @since 9.1
     */

    public void setUpdatingEnabled(boolean updating) {
        isUpdating = updating;
    }

    /**
     * Ask whether the query is allowed to be updating
     * @return true if the query is allowed to use the XQuery Update facility. Note that this
     * does not necessarily mean that the query is an updating query; but if the value is false,
     * the it must definitely be non-updating.
     * @since 9.1
     */

    public boolean isUpdatingEnabled() {
        return isUpdating;
    }



//    public static void main(String[] args) throws Exception {
//        StaticQueryContext c = new StaticQueryContext(new Configuration());
//        c.declareGlobalVariable(
//                new StructuredQName("", "", "ping"),
//                SequenceType.SINGLE_STRING,
//                new StringValue("pong"),
//                true
//        );
//        XQueryExpression exp = c.compileQuery("$ping");
//        DynamicQueryContext env = new DynamicQueryContext(c.getConfiguration());
//        //env.setParameterValue("ping", new StringValue("pang"));
//        exp.run(env, new StreamResult(System.out), null);
//
//    }

}

