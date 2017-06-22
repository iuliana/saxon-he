////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.*;
import net.sf.saxon.evpull.PullEventSource;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.number.Numberer_en;
import net.sf.saxon.expr.parser.ExpressionParser;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.IntegratedFunctionLibrary;
import net.sf.saxon.functions.VendorFunctionLibrary;
import net.sf.saxon.java.JavaPlatform;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.PatternParser20;
import net.sf.saxon.pull.PullSource;
import net.sf.saxon.query.QueryParser;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.serialize.charcode.CharacterSetFactory;
import net.sf.saxon.style.StyleNodeFactory;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trace.TraceCodeInjector;
import net.sf.saxon.trace.XSLTTraceCodeInjector;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.DocumentNumberAllocator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import org.apache.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class holds details of user-selected configuration options for a set of transformations
 * and/or queries. When running XSLT, the preferred way of setting configuration options is via
 * the JAXP TransformerFactory interface, but the Configuration object provides a finer
 * level of control. As yet there is no standard API for XQuery, so the only way of setting
 * Configuration information is to use the methods on this class directly.
 * <p/>
 * <p>As well as holding configuration settings, this class acts as a factory for classes
 * providing service in particular areas: error handling, URI resolution, and the like. Some
 * of these services are chosen on the basis of the current platform (Java or .NET), some vary
 * depending whether the environment is schema-aware or not.</p>
 * <p/>
 * <p>The <code>Configuration</code> provides access to a {@link NamePool} which is used to manage
 * all the names used in stylesheets, queries, schemas, and source and documents: the NamePool
 * allocates integer codes to these names allowing efficient storage and comparison. Normally
 * there will be a one-to-one relationship between a <code>NamePool</code> and a <code>Configuration</code>.
 * It is possible, however, for several <code>Configuration</code> objects to share the same
 * <code>NamePool</code>. Until Saxon 8.9, by default all <code>Configuration</code> objects
 * shared a single <code>NamePool</code> unless configured otherwise; this changed in 8.9 so that
 * the default is to allocate a new <code>NamePool</code> for each <code>Configuration</code>.</p>
 * <p/>
 * <p>The <code>Configuration</code> establishes the scope within which node identity is managed.
 * Every document belongs to a <code>Configuration</code>, and every node has a distinct identity
 * within that <code>Configuration</code>. In consequence, it is not possible for any query or
 * transformation to manipulate multiple documents unless they all belong to the same
 * <code>Configuration</code>.</p>
 * <p/>
 * <p>Saxon-EE has a subclass of the <code>Configuration</code> class which provides the additional
 * services needed for schema-aware processing. The {@link com.saxonica.config.EnterpriseConfiguration}
 * also holds a cache of loaded schema components used for compiling schema-aware transformations
 * and queries, and for validating instance documents.</p>
 * <p/>
 * <p>Since Saxon 8.4, the JavaDoc documentation for Saxon attempts to identify interfaces
 * that are considered stable, and will only be changed in a backwards-incompatible way
 * if there is an overriding reason to do so. These interfaces and methods are labelled
 * with the JavaDoc "since" tag. The value 8.n indicates a method in this category that
 * was introduced in Saxon version 8.n: or in the case of 8.4, that was present in Saxon 8.4
 * and possibly in earlier releases. (In some cases, these methods have been unchanged for
 * a long time.) Methods without a "since" tag, although public, are provided for internal
 * use or for use by advanced users, and are subject to change from one release to the next.
 * The presence of a "since" tag on a class or interface indicates that there are one or more
 * methods in the class that are considered stable; it does not mean that all methods are
 * stable.
 *
 * @since 8.4
 */
public class Configuration implements Serializable, SourceResolver, NotationSet {

	private static final Logger LOGGER = Logger.getLogger(Configuration.class);
    private static final Platform platform = makePlatform();
    public static final Class<? extends Configuration> configurationClass = makeConfigurationClass();
    public static final String softwareEdition = setSoftwareEdition();
    private transient Object apiProcessor = null;
    private transient CharacterSetFactory characterSetFactory;
    private CollationMap collationMap = new CollationMap(this);
    private CollationURIResolver collationResolver = new StandardCollationURIResolver();
    protected CollectionURIResolver collectionResolver = new StandardCollectionURIResolver();
    private EnvironmentVariableResolver environmentVariableResolver = new StandardEnvironmentVariableResolver();
    private String defaultCollection = null;
    private ParseOptions defaultParseOptions = new ParseOptions();
    private transient StaticQueryContext defaultStaticQueryContext;

    private CompilerInfo defaultXsltCompilerInfo = new CompilerInfo();


    private DocumentNumberAllocator documentNumberAllocator = new DocumentNumberAllocator();
    private transient Debugger debugger = null;
    private String defaultLanguage = Locale.getDefault().getLanguage();


    private String defaultCountry = Locale.getDefault().getCountry();
    private Properties defaultSerializationProperties = new Properties();
    private int domLevel = 3;
    private transient DynamicLoader dynamicLoader = new DynamicLoader();

    private Set<String> enabledProperties = new HashSet<>();

    private List<ExternalObjectModel> externalObjectModels = new ArrayList<>(4);
    private DocumentPool globalDocumentPool = new DocumentPool();
    private int hostLanguage = XSLT;
    private IntegratedFunctionLibrary integratedFunctionLibrary = new IntegratedFunctionLibrary();
    private transient LocalizerFactory localizerFactory;
    private ModuleURIResolver moduleURIResolver = null;
    private StaticQueryContextFactory staticQueryContextFactory = new StaticQueryContextFactory();
    private NamePool namePool = new NamePool();

    protected int optimizationLevel = Optimizer.FULL_OPTIMIZATION;
    protected Optimizer optimizer = null;

    private SchemaURIResolver schemaURIResolver = null;
    private SerializerFactory serializerFactory = new SerializerFactory(this);
    private volatile ConcurrentLinkedQueue<XMLReader> sourceParserPool = new ConcurrentLinkedQueue<>();
    private volatile ConcurrentLinkedQueue<XMLReader> styleParserPool = new ConcurrentLinkedQueue<>();
    private String sourceParserClass;
    private transient SourceResolver sourceResolver = this;
    private transient PrintStream standardErrorOutput = System.err;
    private ModuleURIResolver standardModuleURIResolver = StandardModuleURIResolver.getInstance();
    private String styleParserClass;
    private StandardURIResolver systemURIResolver = new StandardURIResolver(this);

    private transient XPathContext theConversionContext = null;
    private ConversionRules theConversionRules = null;
    private transient TraceListener traceListener = null;
    private String traceListenerClass = null;
    protected transient TypeHierarchy typeHierarchy;

    private transient URIResolver uriResolver;
    protected VendorFunctionLibrary vendorFunctionLibrary;
    protected int xsdVersion = XSD10;
    private int xmlVersion = XML10;

    /**
     * Constant indicating that the processor should take the recovery action
     * when a recoverable error occurs, with no warning message.
     */
    public static final int RECOVER_SILENTLY = 0;
    /**
     * Constant indicating that the processor should produce a warning
     * when a recoverable error occurs, and should then take the recovery
     * action and continue.
     */
    public static final int RECOVER_WITH_WARNINGS = 1;
    /**
     * Constant indicating that when a recoverable error occurs, the
     * processor should not attempt to take the defined recovery action,
     * but should terminate with an error.
     */
    public static final int DO_NOT_RECOVER = 2;

    /**
     * Constant indicating the XML Version 1.0
     */

    public static final int XML10 = 10;

    /**
     * Constant indicating the XML Version 1.1
     */

    public static final int XML11 = 11;

    /**
     * Constant indicating that the host language is XSLT
     */
    public static final int XSLT = 50;

    /**
     * Constant indicating that the host language is XQuery
     */
    public static final int XQUERY = 51;

    /**
     * Constant indicating that the "host language" is XML Schema
     */
    public static final int XML_SCHEMA = 52;

    /**
     * Constant indicating that the host language is Java: that is, this is a free-standing
     * Java application with no XSLT or XQuery content
     */
    public static final int JAVA_APPLICATION = 53;

    /**
     * Constant indicating that the host language is XPATH itself - that is, a free-standing XPath environment
     */
    public static final int XPATH = 54;

    /**
     * Language versions for XML Schema
     */
    public static final int XSD10 = 10;
    public static final int XSD11 = 11;

    /**
     * Read a resource file issued with the Saxon product
     *
     * @param filename the filename of the file to be read
     * @param messages List to be populated with messages in the event of failure
     * @param loaders  List to be populated with the ClassLoader that succeeded in loading the resource
     * @return an InputStream for reading the file/resource
     */
    public static InputStream locateResource(String filename, List<String> messages, List<ClassLoader> loaders) {
        ClassLoader loader = null;
        try {
            loader = Thread.currentThread().getContextClassLoader();
        } catch (Exception err) {
        	LOGGER.error("Failed to getContextClassLoader() - continuing\n");
            messages.add("Failed to getContextClassLoader() - continuing\n");
        }

        InputStream in = null;

        if (loader != null) {
            in = loader.getResourceAsStream(filename);
            if (in == null) {
                messages.add("Cannot read " + filename + " file located using ClassLoader " +
                        loader + " - continuing\n");
            }
        }

        if (in == null) {
            loader = Configuration.class.getClassLoader();
            if (loader != null) {
                in = loader.getResourceAsStream(filename);
                if (in == null) {
                    messages.add("Cannot read " + filename + " file located using ClassLoader " +
                            loader + " - continuing\n");
                }
            }
        }

        if (in == null) {
            // Means we're in a very strange class-loading environment, things are getting desparate
            URL url = ClassLoader.getSystemResource(filename);
            if (url != null) {
                try {
                    in = url.openStream();
                } catch (IOException ioe) {
                	LOGGER.error("Could not open stream of URL", ioe);
					messages.add("IO error " + ioe.getMessage() + " reading " + filename +
						" located using getSystemResource(): using defaults");
                    in = null;
                }
            }
        }
        loaders.add(loader);
        return in;

    }

    /**
     * Factory method to construct a Configuration object by reading a configuration file.
     *
     * @param source Source object containing the configuration file
     * @return the resulting Configuration
     * @throws net.sf.saxon.trans.XPathException if the configuration file cannot be read
     * or is invalid
     */
    public static Configuration readConfiguration(Source source) throws XPathException {
        Configuration tempConfig = newConfiguration();
        return tempConfig.readConfigurationFile(source);
    }

    /**
     * Read the configuration file an construct a new Configuration (the real one)
     *
     * @param source the source of the configuration file
     * @return the Configuration that will be used for real work
     * @throws XPathException if the configuration file cannot be read or is invalid
     */
    protected Configuration readConfigurationFile(Source source) throws XPathException {
        return new ConfigurationReader().makeConfiguration(source);
    }

    /**
     * Create a non-schema-aware configuration object with default settings for all options.
     *
     * @since 8.4
     */
    public Configuration() {
        init();
    }

    /**
     * Factory method to create a Configuration, of the class defined using conditional
     * compilation tags when compiling this version of Saxon: that is,
     * the type of Configuration appropriate to the edition of the software
     * being used. This method does not check that the Configuration is licensed.
     *
     * @return a Configuration object of the class appropriate to the Saxon edition in use.
     * @since 9.2
     */
    public static Configuration newConfiguration() {
        //System.err.println("New configuration: " + configurationClass.getName());
        try {
            return configurationClass.newInstance();
        } catch (Exception e) {
            LOGGER.error("Cannot instantiate a Configuration", e);
            throw new RuntimeException("Cannot instantiate a Configuration", e);
        }
    }

    protected void init() {
        platform.initialize(this);
        defaultXsltCompilerInfo.setURIResolver(getSystemURIResolver());
        StandardEntityResolver resolver = new StandardEntityResolver();
        resolver.setConfiguration(this);
        defaultParseOptions.setEntityResolver(resolver);
        internalSetBooleanProperty(FeatureKeys.PREFER_JAXP_PARSER, Boolean.TRUE);
        internalSetBooleanProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS, Boolean.TRUE);
    }

    /**
     * Static method to instantiate a professional or enterprise configuration.
     * <p>This method fails if the specified configuration class cannot be loaded,
     * but it does not check whether there is a license available.
     *
     * @param classLoader - the class loader to be used. If null, the context class loader for the current
     *                    thread is used.
     * @param className   - the name of the configuration class. Defaults to
     *                    "com.saxonica.config.ProfessionalConfiguration" if null is supplied. This allows an assembly
     *                    qualified name to be supplied under .NET. The class, once instantiated, must be an instance
     *                    of Configuration.
     * @return the new ProfessionalConfiguration or EnterpriseConfiguration
     * @throws RuntimeException if the required Saxon edition cannot be loaded
     * @since 9.2 (renamed from makeSchemaAwareConfiguration)
     */
    public static Configuration makeLicensedConfiguration(ClassLoader classLoader, /*@Nullable*/ String className)
            throws RuntimeException {
        if (className == null) {
            className = "com.saxonica.config.ProfessionalConfiguration";
        }
        try {
            Class theClass;
            ClassLoader loader = classLoader;
            if (loader == null) {
                try {
                    loader = Thread.currentThread().getContextClassLoader();
                } catch (Exception err) {
                	LOGGER.error("Failed to getContextClassLoader() - continuing", err);
                }
            }
            if (loader != null) {
                try {
                    theClass = loader.loadClass(className);
                } catch (Exception ex) {
                	LOGGER.error("Could not load class", ex);
                    theClass = Class.forName(className);
                }
            } else {
                theClass = Class.forName(className);
            }
            return (Configuration) theClass.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Make an enterprise configuration
     *
     * @param loader    the class loader (or null)
     * @param className the name of the configuration class (or null)
     * @return the same result as {@link #makeLicensedConfiguration}
     * @deprecated since 9.2. Use {@link #makeLicensedConfiguration} instead.
     */
    public static Configuration makeSchemaAwareConfiguration(ClassLoader loader, String className) {
        return makeLicensedConfiguration(loader, className);
    }

    /**
     * Get the edition code identifying this configuration: "HE", "PE" or "EE"
     * @return the code identifying the Saxon edition associated with this configuration
     */
    public String getEditionCode() {
        return "HE";
    }

    /**
     * Save the Processor object that owns this Configuration in the relevant API.
     *
     * @param processor This can be any object, but it is actually used to hold one of the
     *                  following:
     *                  <ul>
     *                  <li>When using the Java s9api interface, the <code>net.sf.saxon.s9api.Processor</code></li>
     *                  <li>When using the .NET interface, the <code>Saxon.Api.Processor</code></li>
     *                  <li>When using the JAXP transformation interface, the JAXP <code>TransformerFactory</code></li>
     *                  <li>When using the JAXP XPath interface, the JAXP <code>XPathFactory</code></li>
     *                  <li>When using the JAXP Schema interface, the JAXP <code>SchemaFactory</code></li>
     *                  <li>When using XQJ, the <code>XQDataSource</code></li>
     *                  </ul>
     * @since 9.2
     */
    public void setProcessor(Object processor) {
        this.apiProcessor = processor;
    }

    /**
     * Get the Processor object that created this Configuration in the relevant API.
     * <p>The main purpose of this interface is to allow extension functions called from
     * a stylesheet or query to get access to the originating processor. This is particularly
     * useful when methods are static as there is then limited scope for passing data from the
     * calling application to the code of the extension function.</p>
     *
     * @return the processor that was supplied to the {@link #setProcessor(Object)} method, or null
     *         if this method has not been called. In practice this property is used to hold one of the
     *         following:
     *         <ul>
     *         <li>When using the Java s9api interface, the <code>net.sf.saxon.s9api.Processor</code></li>
     *         <li>When using the .NET interface, the <code>Saxon.Api.Processor</code></li>
     *         <li>When using the JAXP transformation interface, the JAXP <code>TransformerFactory</code></li>
     *         <li>When using the JAXP XPath interface, the JAXP <code>XPathFactory</code></li>
     *         <li>When using the JAXP Schema interface, the JAXP <code>SchemaFactory</code></li>
     *         <li>When using XQJ, the <code>XQDataSource</code></li>
     *         </ul>
     * @since 9.2
     */
    public Object getProcessor() {
        return apiProcessor;
    }

    /**
     * Get a message used to identify this product when a transformation is run using the -t option
     *
     * @return A string containing both the product name and the product
     *         version
     * @since 8.4
     */
	public String getProductTitle() {
		return "Saxon-" + getEditionCode() + " " + Version.getProductVersion() + platform.getPlatformSuffix() +
			" from Saxonica";
	}

    /**
     * Check whether a particular feature is licensed, with a fatal error if it is not
     *
     * @param feature the feature in question, identified by a constant in class {@link net.sf.saxon.Configuration.LicenseFeature}
     * @param name the name of the feature for use in diagnostics
     * @throws LicenseException if the feature is not licensed. This is a RunTimeException, so it will normally be fatal.
     */
    public void checkLicensedFeature(int feature, String name) throws LicenseException {
        String require = (feature == LicenseFeature.PROFESSIONAL_EDITION ? "PE" : "EE");
        String message = "Requested feature (" + name + ") requires Saxon-" + require;
        if (!softwareEdition.equals("HE")) {
            message += ". You are using Saxon-" + softwareEdition + " software, but the Configuration is an instance of " +
                getClass().getName() + "; to use this feature you need to create an instance of " +
                (feature == LicenseFeature.PROFESSIONAL_EDITION ?
                        "com.saxonica.config.ProfessionalConfiguration" :
                        "com.saxonica.config.EnterpriseConfiguration");
        }
        throw new LicenseException(message, LicenseException.WRONG_CONFIGURATION);
    }

    /**
     * Determine if a particular feature is licensed.
     *
     * @param feature the feature in question, identified by a constant in class {@link net.sf.saxon.Configuration.LicenseFeature}
     * @return true if the feature is licensed, false if it is not.
     */
    public boolean isLicensedFeature(int feature) {
        // changing this to true will do no good; it will cause Saxon to attempt to use the unavailable feature, rather than
        // recovering from its absence.
        return false;
    }

    /**
     * Determine if the configuration is schema-aware, for the given host language
     *
     * @param language the required host language: XSLT, XQUERY, or XML_SCHEMA
     * @return true if the configuration is schema-aware
     * @since 8.4
     * @deprecated since 9.2: use isLicensedFeature() instead
     */
    public boolean isSchemaAware(int language) {
        return false;
        // changing this to true will do no good!
    }

    /**
     * Display a message about the license status
     */
    public void displayLicenseMessage() {
    }

    /**
     * Get the host language used in this configuration. The typical values
     * are XSLT and XQUERY. The values XML_SCHEMA and JAVA_APPLICATION may also
     * be encountered.
     * <p/>
     * This method is problematic because it is possible to run multiple transformations
     * or queries within the same configuration. The method is therefore best avoided.
     * Instead, use {@link net.sf.saxon.expr.Container#getHostLanguage}.
     * Internally its only use is in deciding (in Saxon-EE only) which error listener to
     * use by default at compile time, and since the standard XSLT and XQuery listeners have
     * no differences when used for static errors, the choice is immaterial.
     *
     * @return Configuration.XSLT or Configuration.XQUERY
     */
    public int getHostLanguage() {
        return hostLanguage;
    }

    /**
     * Set the host language used in this configuration. The possible values
     * are XSLT and XQUERY.
     *
     * @param hostLanguage Configuration.XSLT or Configuration.XQUERY
     */
    public void setHostLanguage(int hostLanguage) {
        this.hostLanguage = hostLanguage;
    }

    /**
     * Get the Platform to be used for platform-dependent methods
     *
     * @return the platform to be used
     */
    public static Platform getPlatform() {
        return platform;
    }

    /**
     * Set the DynamicLoader to be used. By default an instance of {@link DynamicLoader} is used
     * for all dynamic loading of Java classes. This method allows the actions of the standard
     * DynamicLoader to be overridden
     *
     * @param dynamicLoader the DynamicLoader to be used by this Configuration
     */
    public void setDynamicLoader(DynamicLoader dynamicLoader) {
        this.dynamicLoader = dynamicLoader;
    }

    /**
     * Get the DynamicLoader used by this Configuration. By default the standard system-supplied
     * dynamic loader is returned.
     *
     * @return the DynamicLoader in use - either a user-supplied DynamicLoader, or the standard one
     *         supplied by the system.
     */
    public DynamicLoader getDynamicLoader() {
        return dynamicLoader;
    }

    /**
     * Load a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p/>
     * This method is intended for internal use only. The call is delegated to the
     * <code>DynamicLoader</code>, which may be overridden by a user-defined <code>DynamicLoader</code>.
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param tracing     true if diagnostic tracing is required
     * @param classLoader The ClassLoader to be used to load the class, or null to
     *                    use the ClassLoader selected by the DynamicLoader.
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */
    public Class getClass(String className, boolean tracing, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
        return dynamicLoader.getClass(className, (tracing ? standardErrorOutput : null), classLoader);
    }

    /**
     * Instantiate a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p/>
     * This method is intended for internal use only. The call is delegated to the
     * <code>DynamicLoader</code>, which may be overridden by a user-defined <code>DynamicLoader</code>.
     * <p/>
     * Diagnostic output is produced if the option "isTiming" is set (corresponding to the -t option on
     * the command line).
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param classLoader The ClassLoader to be used to load the class, or null to
     *                    use the ClassLoader selected by the DynamicLoader.
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */
    public Object getInstance(String className, ClassLoader classLoader) throws XPathException {
        return dynamicLoader.getInstance(className, (isTiming() ? standardErrorOutput : null), classLoader);
    }

    /**
     * Get the URIResolver used in this configuration
     *
     * @return the URIResolver. If no URIResolver has been set explicitly, the
     *         default URIResolver is used.
     * @since 8.4
     */
    public URIResolver getURIResolver() {
    	return uriResolver == null ? systemURIResolver : uriResolver;
    }

    /**
     * Set the URIResolver to be used in this configuration. This will be used to
     * resolve the URIs used statically (e.g. by xsl:include) and also the URIs used
     * dynamically by functions such as document() and doc(). Note that the URIResolver
     * does not resolve the URI in the sense of RFC 2396 (which is also the sense in which
     * the resolve-uri() function uses the term): rather it dereferences an absolute URI
     * to obtain an actual resource, which is returned as a Source object.
     *
     * @param resolver The URIResolver to be used.
     * @since 8.4
     */
    public void setURIResolver(URIResolver resolver) {
        uriResolver = resolver;
		if (resolver instanceof StandardURIResolver) {
			((StandardURIResolver) resolver).setConfiguration(this);
		}
		defaultXsltCompilerInfo.setURIResolver(resolver);
	}

    /**
     * Set the URIResolver to a URI resolver that allows query parameters after the URI,
     * and in the case of Saxon-EE, that inteprets the file extension .ptree
     */
    public void setParameterizedURIResolver() {
        getSystemURIResolver().setRecognizeQueryParameters(true);
    }

    /**
     * Get the system-defined URI Resolver. This is used when the user-defined URI resolver
     * returns null as the result of the resolve() method
     *
     * @return the system-defined URI resolver
     */
    public StandardURIResolver getSystemURIResolver() {
        return systemURIResolver;
    }

    /**
     * Create an instance of a URIResolver with a specified class name.
     * Note that this method does not register the URIResolver with this Configuration.
     *
     * @param className The fully-qualified name of the URIResolver class
     * @return The newly created URIResolver
     * @throws TransformerException if the requested class does not
     *                              implement the javax.xml.transform.URIResolver interface
     */
    public URIResolver makeURIResolver(String className) throws TransformerException {
        Object obj = dynamicLoader.getInstance(className, null);
        if (obj instanceof StandardURIResolver) {
            ((StandardURIResolver) obj).setConfiguration(this);
        }
        if (obj instanceof URIResolver) {
            return (URIResolver) obj;
        }
        throw new XPathException("Class " + className + " is not a URIResolver");
    }

    /**
     * Get the ErrorListener used in this configuration. If no ErrorListener
     * has been supplied explicitly, the default ErrorListener is used.
     *
     * @return the ErrorListener.
     * @since 8.4
     */
    public ErrorListener getErrorListener() {
        ErrorListener listener = defaultParseOptions.getErrorListener();
        if (listener == null) {
            listener = new StandardErrorListener();
            ((StandardErrorListener) listener).setErrorOutput(standardErrorOutput);
            ((StandardErrorListener) listener).setRecoveryPolicy(defaultXsltCompilerInfo.getRecoveryPolicy());
            defaultParseOptions.setErrorListener(listener);
        }
        return listener;
    }

    /**
     * Set the ErrorListener to be used in this configuration. The ErrorListener
     * is informed of all static and dynamic errors detected, and can decide whether
     * run-time warnings are to be treated as fatal.
     *
     * @param listener the ErrorListener to be used
     * @since 8.4
     */
    public void setErrorListener(ErrorListener listener) {
        defaultParseOptions.setErrorListener(listener);
    }


    /**
     * Report a fatal error
     *
     * @param err the exception to be reported
     */
    public void reportFatalError(XPathException err) {
        if (!err.hasBeenReported()) {
            try {
                getErrorListener().fatalError(err);
            } catch (TransformerException e) {
                LOGGER.error("Could not receive notification of the non-recoverable error", e);
            }
            err.setHasBeenReported(true);
        }
    }

    /**
     * Set the standard error output to be used in all cases where no more specific destination
     * is defined. This defaults to System.err.
     *
     * @param out the stream to be used for error output where no more specific destination
     *            has been supplied
     * @since 9.3
     */
    public void setStandardErrorOutput(PrintStream out) {
        standardErrorOutput = out;
    }

    /**
     * Get the standard error output to be used in all cases where no more specific destination
     * is defined. This defaults to System.err.
     *
     * @return the stream to be used for error output where no more specific destination
     *         has been supplied
     * @since 9.3
     */
    public PrintStream getStandardErrorOutput() {
        if (standardErrorOutput == null) {
            standardErrorOutput = System.err;
        }
        return standardErrorOutput;
    }

    /**
     * Set the XML version to be used by default for validating characters and names.
     * Note that source documents specifying xml version="1.0" or "1.1" are accepted
     * regardless of this setting. The effect of this switch is to change the validation
     * rules for types such as Name and NCName, to change the meaning of \i and \c in
     * regular expressions, and to determine whether the serializer allows XML 1.1 documents
     * to be constructed.
     *
     * @param version one of the constants XML10 or XML11
     * @since 8.6
     */
    public void setXMLVersion(int version) {
        xmlVersion = version;
        theConversionRules = null;
    }

    /**
     * Get the XML version to be used by default for validating characters and names
     *
     * @return one of the constants {@link #XML10} or {@link #XML11}
     * @since 8.6
     */
    public int getXMLVersion() {
        return xmlVersion;
    }

    /**
     * Get the parsing and document building options defined in this configuration
     *
     * @return the parsing and document building options. Note that any changes to this
     *         ParseOptions object will be reflected back in the Configuration; if changes are to be made
     *         locally, the caller should create a copy.
     * @since 9.2
     */
    public ParseOptions getParseOptions() {
        return defaultParseOptions;
    }

    /**
     * Get a class that can be used to check names against the selected XML version
     *
     * @return a class that can be used for name checking
     * @since 8.6
     */
    public NameChecker getNameChecker() {
        return getConversionRules().getNameChecker();
    }

    /**
     * Set the conversion rules to be used to convert between atomic types. By default,
     * The rules depend on the versions of XML and XSD in use by the configuration.
     *
     * @param rules the conversion rules to be used
     * @since 9.3
     */
    public void setConversionRules(ConversionRules rules) {
        this.theConversionRules = rules;
    }

    /**
     * Get the conversion rules used to convert between atomic types. By default, the rules depend on the versions
     * of XML and XSD in use by the configuration
     *
     * @return the appropriate conversion rules
     * @since 9.3
     */
    public ConversionRules getConversionRules() {
        if (theConversionRules == null) {
            synchronized (this) {
                ConversionRules cv = new ConversionRules();
                cv.setNameChecker(xmlVersion == XML10 ? Name10Checker.getInstance() : Name11Checker.getInstance());
				cv.setStringToDoubleConverter(
					xsdVersion == XSD10 ? StringToDouble.getInstance() : StringToDouble11.getInstance());
                cv.setNotationSet(this);
                if (xsdVersion == XSD10) {
                    cv.setURIChecker(StandardURIChecker.getInstance());
                    // In XSD 1.1, there is no checking
                }
                cv.setAllowYearZero(xsdVersion != XSD10);
                return theConversionRules = cv;
            }
        } else {
            return theConversionRules;
        }
    }

    /**
     * Get the version of XML Schema to be used
     *
     * @return {@link #XSD10} or {@link #XSD11}
     * @since 9.2
     */
    public int getXsdVersion() {
        return xsdVersion;
    }

    /**
     * Get an XPathContext object with sufficient capability to perform comparisons and conversions
     *
     * @return a dynamic context for performing conversions
     */
    public XPathContext getConversionContext() {
        if (theConversionContext == null) {
            theConversionContext = new EarlyEvaluationContext(this, new CollationMap(this));
        }
        return theConversionContext;
    }

    /**
     * Get the Tree Model used by this Configuration. This is either
     * {@link Builder#LINKED_TREE}, {@link Builder#TINY_TREE}, or {@link Builder#TINY_TREE_CONDENSED}.
     * The default is <code>Builder.TINY_TREE</code>.
     *
     * @return the selected Tree Model
     * @since 8.4 (Condensed tinytree added in 9.2)
     */
    public int getTreeModel() {
        return defaultParseOptions.getModel().getSymbolicValue();
    }

    /**
     * Set the Tree Model used by this Configuration. This is either
     * {@link Builder#LINKED_TREE} or {@link Builder#TINY_TREE}, or {@link Builder#TINY_TREE_CONDENSED}.
     * The default is <code>Builder.TINY_TREE</code>.
     *
     * @param treeModel the integer constant representing the selected Tree Model
     * @since 8.4 (Condensed tinytree added in 9.2)
     */
    public void setTreeModel(int treeModel) {
        defaultParseOptions.setModel(TreeModel.getTreeModel(treeModel));
    }

    /**
     * Determine whether source documents will maintain line numbers, for the
     * benefit of the saxon:line-number() extension function as well as run-time
     * tracing.
     *
     * @return true if line numbers are maintained in source documents
     * @since 8.4
     */
    public boolean isLineNumbering() {
        return defaultParseOptions.isLineNumbering();
    }

    /**
     * Determine whether source documents will maintain line numbers, for the
     * benefit of the saxon:line-number() extension function as well as run-time
     * tracing.
     *
     * @param lineNumbering true if line numbers are maintained in source documents
     * @since 8.4
     */
    public void setLineNumbering(boolean lineNumbering) {
        defaultParseOptions.setLineNumbering(lineNumbering);
    }

    /**
     * Set whether or not source documents (including stylesheets and schemas) are have
     * XInclude processing applied to them, or not. Default is false.
     *
     * @param state true if XInclude elements are to be expanded, false if not
     * @since 8.9
     */
    public void setXIncludeAware(boolean state) {
        defaultParseOptions.setXIncludeAware(state);
    }

    /**
     * Test whether or not source documents (including stylesheets and schemas) are to have
     * XInclude processing applied to them, or not
     *
     * @return true if XInclude elements are to be expanded, false if not
     * @since 8.9
     */
    public boolean isXIncludeAware() {
        return defaultParseOptions.isXIncludeAware();
    }

    /**
     * Get the TraceListener used for run-time tracing of instruction execution.
     *
     * @return the TraceListener that was set using {@link #setTraceListener} if set.
     *         Otherwise, returns null.
     * @since 8.4. Modified in 9.1.
     */

    public TraceListener getTraceListener() {
        return traceListener;
    }

    /**
     * Get or create the TraceListener used for run-time tracing of instruction execution.
     *
     * @return If a TraceListener has been set using {@link #setTraceListener(net.sf.saxon.lib.TraceListener)},
     *         returns that TraceListener. Otherwise, if a TraceListener class has been set using
     *         {@link #setTraceListenerClass(String)}, returns a newly created instance of that class.
     *         Otherwise, returns null.
     * @throws XPathException if the supplied TraceListenerClass cannot be instantiated as an instance
     *                        of TraceListener
     * @since 9.1.
     */
    public TraceListener makeTraceListener() throws XPathException {
        if (traceListener != null) {
            return traceListener;
        } else if (traceListenerClass != null) {
            try {
                return makeTraceListener(traceListenerClass);
            } catch (ClassCastException e) {
                throw new XPathException(e);
            }
        } else {
            return null;
        }
    }

    /**
     * Set the TraceListener to be used for run-time tracing of instruction execution.
     * <p/>
     * <p>Note: this method should not be used if the Configuration is multithreading. In that situation,
     * use {@link #setCompileWithTracing(boolean)} to force stylesheets and queries to be compiled
     * with trace code enabled, and use {@link Controller#addTraceListener(net.sf.saxon.lib.TraceListener)} to
     * supply a TraceListener at run time.</p>
     *
     * @param traceListener The TraceListener to be used. If null is supplied, any existing TraceListener is removed
     * @since 8.4
     */
    public void setTraceListener(TraceListener traceListener) {
        this.traceListener = traceListener;
        setCompileWithTracing(traceListener != null);
        internalSetBooleanProperty(FeatureKeys.ALLOW_MULTITHREADING, Boolean.FALSE);
    }

    /**
     * Set the name of the trace listener class to be used for run-time tracing of instruction
     * execution. A new instance of this class will be created for each query or transformation
     * that requires tracing. The class must be an instance of {@link TraceListener}.
     *
     * @param className the name of the trace listener class. If null, any existing trace listener is
     * removed from the configuration.
     * @throws IllegalArgumentException if the class cannot be instantiated or does not implement
     *                                  TraceListener
     * @since 9.1. Changed in 9.4 to allow null to be supplied.
     */
    public void setTraceListenerClass(String className) {
        if (className == null) {
            traceListenerClass = null;
            setCompileWithTracing(false);
        } else {
            try {
                makeTraceListener(className);
            } catch (XPathException err) {
            	LOGGER.error(className + ": " + err.getMessage(), err);
                throw new IllegalArgumentException(className + ": " + err.getMessage());
            }
            this.traceListenerClass = className;
            setCompileWithTracing(true);
        }
    }

    /**
     * Get the name of the trace listener class to be used for run-time tracing of instruction
     * execution. A new instance of this class will be created for each query or transformation
     * that requires tracing. The class must be an instance of {@link net.sf.saxon.lib.TraceListener}.
     *
     * @return the name of the trace listener class, or null if no trace listener class
     *         has been nominated.
     * @since 9.1
     */
    public String getTraceListenerClass() {
        return traceListenerClass;
    }

    /**
     * Determine whether compile-time generation of trace code was requested
     *
     * @return true if compile-time generation of code was requested
     * @since 8.8
     */
    public boolean isCompileWithTracing() {
        return getBooleanProperty(FeatureKeys.COMPILE_WITH_TRACING);
    }

    /**
     * Request compile-time generation of trace code (or not)
     *
     * @param trace true if compile-time generation of trace code is required
     * @since 8.8
     */
    public void setCompileWithTracing(boolean trace) {
        internalSetBooleanProperty(FeatureKeys.COMPILE_WITH_TRACING, trace);
        if (defaultXsltCompilerInfo != null) {
            if (trace) {
                defaultXsltCompilerInfo.setCodeInjector(new XSLTTraceCodeInjector());
            } else {
                defaultXsltCompilerInfo.setCodeInjector(null);
            }
        }
        if (defaultStaticQueryContext != null) {
            if (trace) {
                defaultStaticQueryContext.setCodeInjector(new TraceCodeInjector());
            } else {
                defaultStaticQueryContext.setCodeInjector(null);
            }
        }
    }

    /**
     * Create an instance of a TraceListener with a specified class name
     *
     * @param className The fully qualified class name of the TraceListener to
     *                  be constructed
     * @return the newly constructed TraceListener
     * @throws net.sf.saxon.trans.XPathException
     *          if the requested class does not
     *          implement the net.sf.saxon.trace.TraceListener interface
     */
    public TraceListener makeTraceListener(String className) throws XPathException {
        Object obj = dynamicLoader.getInstance(className, null);
        if (obj instanceof TraceListener) {
            return (TraceListener) obj;
        }
        throw new XPathException("Class " + className + " is not a TraceListener");
    }

    /**
     * Register an extension function that is to be made available within any stylesheet, query,
     * or XPath expression compiled under the control of this processor. This method
     * registers an extension function implemented as an instance of
     * {@link net.sf.saxon.lib.ExtensionFunctionDefinition}, using an arbitrary name and namespace.
     * This supplements the ability to call arbitrary Java methods using a namespace and local name
     * that are related to the Java class and method name.
     *
     * @param function the object that implements the extension function.
     * @since 9.2
     */
    public void registerExtensionFunction(ExtensionFunctionDefinition function) {
        integratedFunctionLibrary.registerFunction(function);
    }

    /**
     * Get the IntegratedFunction library containing integrated extension functions
     *
     * @return the IntegratedFunctionLibrary
     * @since 9.2
     */
    public IntegratedFunctionLibrary getIntegratedFunctionLibrary() {
        return integratedFunctionLibrary;
    }


    /**
     * Get the FunctionLibrary used to bind calls on Saxon-defined extension functions.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the FunctionLibrary used for extension functions in the Saxon library.
     */
    public VendorFunctionLibrary getVendorFunctionLibrary() {
        if (vendorFunctionLibrary == null) {
            vendorFunctionLibrary = new VendorFunctionLibrary();
        }
        return vendorFunctionLibrary;
    }

    /**
     * Add the registered extension binders to a function library.
     * This method is intended primarily for internal use
     *
     * @param list the function library list
     */
    public void addExtensionBinders(FunctionLibraryList list) {
        // no action in this class
    }

    /**
     * Make a UserFunction object.
     * This method is for internal use.
     *
     * @param memoFunction true if the function is to be a memo function, This option is ignored
     *                     in Saxon-HE.
     * @return a new UserFunction object
     */
    public UserFunction newUserFunction(boolean memoFunction) {
        return new UserFunction();
    }

    /**
     * Set a CollationURIResolver to be used to resolve collation URIs (that is,
     * to take a URI identifying a collation, and return the corresponding collation).
     * Note that Saxon attempts first to resolve a collation URI using the resolver
     * registered with the Controller; if that returns null, it tries again using the
     * resolver registered with the Configuration.
     * <p/>
     * Note that it is undefined whether collation URIs are resolved at compile time
     * or at run-time. It is therefore inadvisable to change the CollationURIResolver after
     * compiling a query or stylesheet and before running it.
     *
     * @param resolver the collation URI resolver to be used. This replaces any collation
     *                 URI resolver previously registered.
     * @since 8.5
     */
    public void setCollationURIResolver(CollationURIResolver resolver) {
        collationResolver = resolver;
    }

    /**
     * Get the collation URI resolver associated with this configuration. This will
     * return the CollationURIResolver previously set using the {@link #setCollationURIResolver}
     * method; if this has not been called, it returns the system-defined collation URI resolver
     *
     * @return the registered CollationURIResolver
     * @since 8.5
     */
    public CollationURIResolver getCollationURIResolver() {
        return collationResolver;
    }

    /**
     * Get the collation map, which can be used to register named collations (and a default collation)
     * at the Configuration level. Any collations registered in this collation map apply to all
     * queries, stylesheets, and Xpath expressions compiled under this collation. The effective
     * contents of the collation map are the contents at the time the query or stylesheet is compiled.
     *
     * @return the collation map
     */
    public CollationMap getCollationMap() {
        return collationMap;
    }

    /**
     * Set the default collection.
     * <p/>
     * <p>If no default collection URI is specified, then a request for the default collection
     * is handled by calling the registered collection URI resolver with an argument of null.</p>
     *
     * @param uri the URI of the default collection. Calling the collection() function
     *            with no arguments is equivalent to calling collection() with this URI as an argument.
     *            The URI will be dereferenced by passing it to the registered CollectionURIResolver.
     *            If null is supplied, any existing default collection is removed.
     * @since 9.2
     */
    public void setDefaultCollection(String uri) {
        defaultCollection = uri;
    }

    /**
     * Get the URI of the default collection. Returns null if no default collection URI has
     * been registered.
     *
     * @return the default collection URI. This is dereferenced in the same way as a normal
     *         collection URI (via the CollectionURIResolver) to return a sequence of nodes
     * @since 9.2
     */
    public String getDefaultCollection() {
        return defaultCollection;
    }

    /**
     * Set a CollectionURIResolver to be used to resolve collection URIs (that is,
     * the URI supplied in a call to the collection() function).
     * <p/>
     * Collection URIs are always resolved at run-time, using the CollectionURIResolver
     * in force at the time the collection() function is called.
     *
     * @param resolver the collection URI resolver to be used. This replaces any collection
     *                 URI resolver previously registered.  The value must not be null.
     * @since 8.5
     */
    public void setCollectionURIResolver(CollectionURIResolver resolver) {
        collectionResolver = resolver;
    }

    /**
     * Get the collection URI resolver associated with this configuration. This will
     * return the CollectionURIResolver previously set using the {@link #setCollectionURIResolver}
     * method; if this has not been called, it returns the system-defined collection URI resolver
     *
     * @return the registered CollectionURIResolver
     * @since 8.5
     */
    public CollectionURIResolver getCollectionURIResolver() {
        return collectionResolver;
    }

    /**
     * Set the localizer factory to be used
     *
     * @param factory the LocalizerFactory
     * @since 9.2
     */
    public void setLocalizerFactory(LocalizerFactory factory) {
        this.localizerFactory = factory;
    }

    /**
     * Get the localizer factory in use
     *
     * @return the LocalizerFactory, if any. If none has been set, returns null.
     * @since 9.2
     */
    public LocalizerFactory getLocalizerFactory() {
        return localizerFactory;
    }

    /**
     * Set the default language to be used for number and date formatting when no language is specified.
     * If none is set explicitly, the default Locale for the Java Virtual Machine is used.
     *
     * @param language the default language to be used, as an ISO code for example "en" or "fr-CA"
     * @since 9.2
     */
    public void setDefaultLanguage(String language) {
        defaultLanguage = language;
    }

    /**
     * Get the default language. Unless an explicit default is set, this will be the language
     * of the default Locale for the Java Virtual Machine
     *
     * @return the default language
     * @since 9.2
     */
    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * Set the default country to be used for number and date formatting when no country is specified.
     * If none is set explicitly, the default Locale for the Java Virtual Machine is used.
     *
     * @param country the default country to be used, as an ISO code for example "US" or "GB"
     * @since 9.2
     */
    public void setDefaultCountry(String country) {
        defaultCountry = country;
    }

    /**
     * Get the default country to be used for number and date formatting when no country is specified.
     * If none is set explicitly, the default Locale for the Java Virtual Machine is used.
     *
     * @return the default country to be used, as an ISO code for example "US" or "GB"
     * @since 9.2
     */
    public String getDefaultCountry() {
        return defaultCountry;
    }


    /**
     * Load a Numberer class for a given language and check it is OK.
     * This method is provided primarily for internal use.
     *
     * @param language the language for which a Numberer is required. May be null,
     *                 indicating default language
     * @param country  the country for which a Numberer is required. May be null,
     *                 indicating default country
     * @return a suitable numberer. If no specific numberer is available
     *         for the language, the default numberer (normally English) is used.
     */
    public Numberer makeNumberer(String language, String country) {
        if (localizerFactory == null) {
            return new Numberer_en();
        } else {
            Numberer numberer = localizerFactory.getNumberer(language, country);
            if (numberer == null) {
                numberer = new Numberer_en();
            }
            return numberer;
        }
    }

    /**
     * Set a user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in an XQuery prolog.
     * This acts as the default value for the ModuleURIResolver in the StaticQueryContext, and may be
     * overridden by a more specific ModuleURIResolver nominated as part of the StaticQueryContext.
     *
     * @param resolver the URI resolver for XQuery modules. May be null, in which case any existing
     * Module URI Resolver is removed from the configuration
     */
    public void setModuleURIResolver(ModuleURIResolver resolver) {
        moduleURIResolver = resolver;
    }

    /**
     * Create and register an instance of a ModuleURIResolver with a specified class name.
     * This will be used for resolving URIs in XQuery "import module" declarations, unless
     * a more specific ModuleURIResolver has been nominated as part of the StaticQueryContext.
     *
     * @param className The fully-qualified name of the LocationHintResolver class
     * @throws TransformerException if the requested class does not
     *                              implement the net.sf.saxon.LocationHintResolver interface
     */
    public void setModuleURIResolver(String className) throws TransformerException {
        Object obj = dynamicLoader.getInstance(className, null);
        if (obj instanceof ModuleURIResolver) {
            setModuleURIResolver((ModuleURIResolver) obj);
        } else {
            throw new XPathException("Class " + className + " is not a ModuleURIResolver");
        }
    }

    /**
     * Get the user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog; returns null if none has been explicitly set.
     *
     * @return the resolver for Module URIs
     */
    public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }

    /**
     * Get the standard system-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog.
     *
     * @return the standard system-defined ModuleURIResolver for resolving URIs
     */
    public ModuleURIResolver getStandardModuleURIResolver() {
        return standardModuleURIResolver;
    }

    /**
     * Set a user-defined SchemaURIResolver for resolving URIs used in "import schema"
     * declarations.
     *
     * @param resolver the URI resolver used for import schema declarations. May be null,
     * in which case any existing URI resolver is removed from the Configuration.
     */
    public void setSchemaURIResolver(SchemaURIResolver resolver) {
        schemaURIResolver = resolver;
    }

    /**
     * Get the user-defined SchemaURIResolver for resolving URIs used in "import schema"
     * declarations; if none has been explicitly set, returns null.
     *
     * @return the user-defined SchemaURIResolver for resolving URIs
     */
    public SchemaURIResolver getSchemaURIResolver() {
        return schemaURIResolver;
    }

    /**
     * Get the default options for XSLT compilation
     *
     * @return the default options for XSLT compilation. The CompilerInfo object will reflect any options
     *         set using other methods available for this Configuration object
     */
    public CompilerInfo getDefaultXsltCompilerInfo() {
        return defaultXsltCompilerInfo;
    }

    /**
     * Get the default options for XQuery compilation
     * @return the default XQuery static context for this configuration
     */
    public StaticQueryContext getDefaultStaticQueryContext() {
        if (defaultStaticQueryContext == null) {
            defaultStaticQueryContext = new StaticQueryContext(this, true);
        }
        return defaultStaticQueryContext;
    }

    /**
     * Determine how recoverable run-time errors are to be handled. This applies
     * only if the standard ErrorListener is used.
     *
     * @return the current recovery policy. The options are {@link #RECOVER_SILENTLY},
     *         {@link #RECOVER_WITH_WARNINGS}, or {@link #DO_NOT_RECOVER}.
     * @since 8.4
     */
    public int getRecoveryPolicy() {
        return defaultXsltCompilerInfo.getRecoveryPolicy();
    }

    /**
     * Determine how recoverable run-time errors are to be handled. This applies
     * only if the standard ErrorListener is used. The recovery policy applies to
     * errors classified in the XSLT 2.0 specification as recoverable dynamic errors,
     * but only in those cases where Saxon provides a choice over how the error is handled:
     * in some cases, Saxon makes the decision itself.
     *
     * @param recoveryPolicy the recovery policy to be used. The options are {@link #RECOVER_SILENTLY},
     *                       {@link #RECOVER_WITH_WARNINGS}, or {@link #DO_NOT_RECOVER}.
     * @since 8.4
     */
    public void setRecoveryPolicy(int recoveryPolicy) {
        defaultXsltCompilerInfo.setRecoveryPolicy(recoveryPolicy);
    }

    /**
     * Get the name of the class that will be instantiated to create a MessageEmitter,
     * to process the output of xsl:message instructions in XSLT.
     *
     * @return the full class name of the message emitter class.
     * @since 8.4
     */
    public String getMessageEmitterClass() {
        return defaultXsltCompilerInfo.getMessageReceiverClassName();
    }

    /**
     * Set the name of the class that will be instantiated to
     * to process the output of xsl:message instructions in XSLT.
     *
     * @param messageReceiverClassName the full class name of the message receiver. This
     *                                 must implement net.sf.saxon.event.Receiver.
     * @since 8.4
     */
    public void setMessageEmitterClass(String messageReceiverClassName) {
        defaultXsltCompilerInfo.setMessageReceiverClassName(messageReceiverClassName);
    }

    /**
     * Get the name of the class that will be instantiated to create an XML parser
     * for parsing source documents (for example, documents loaded using the document()
     * or doc() functions).
     * <p/>
     * This method is retained in Saxon for backwards compatibility, but the preferred way
     * of choosing an XML parser is to use JAXP interfaces, for example by supplying a
     * JAXP Source object initialized with an appropriate implementation of org.xml.sax.XMLReader.
     *
     * @return the fully qualified name of the XML parser class
     */
    public String getSourceParserClass() {
        return sourceParserClass;
    }

    /**
     * Set the name of the class that will be instantiated to create an XML parser
     * for parsing source documents (for example, documents loaded using the document()
     * or doc() functions).
     * <p/>
     * This method is retained in Saxon for backwards compatibility, but the preferred way
     * of choosing an XML parser is to use JAXP interfaces, for example by supplying a
     * JAXP Source object initialized with an appropriate implementation of org.xml.sax.XMLReader.
     *
     * @param sourceParserClass the fully qualified name of the XML parser class. This must implement
     *                          the SAX2 XMLReader interface.
     */
    public void setSourceParserClass(String sourceParserClass) {
        this.sourceParserClass = sourceParserClass;
    }

    /**
     * Get the name of the class that will be instantiated to create an XML parser
     * for parsing stylesheet modules.
     * <p/>
     * This method is retained in Saxon for backwards compatibility, but the preferred way
     * of choosing an XML parser is to use JAXP interfaces, for example by supplying a
     * JAXP Source object initialized with an appropriate implementation of org.xml.sax.XMLReader.
     *
     * @return the fully qualified name of the XML parser class
     */
    public String getStyleParserClass() {
        return styleParserClass;
    }

    /**
     * Set the name of the class that will be instantiated to create an XML parser
     * for parsing stylesheet modules.
     * <p/>
     * This method is retained in Saxon for backwards compatibility, but the preferred way
     * of choosing an XML parser is to use JAXP interfaces, for example by supplying a
     * JAXP Source object initialized with an appropriate implementation of org.xml.sax.XMLReader.
     *
     * @param parser the fully qualified name of the XML parser class
     */
    public void setStyleParserClass(String parser) {
        this.styleParserClass = parser;
    }
    
    /**
     * Get the OutputURIResolver that will be used to resolve URIs used in the
     * href attribute of the xsl:result-document instruction.
     *
     * @return the OutputURIResolver. If none has been supplied explicitly, the
     *         default OutputURIResolver is returned.
     * @since 8.4
     */
    public OutputURIResolver getOutputURIResolver() {
        return defaultXsltCompilerInfo.getOutputURIResolver();
    }

    /**
     * Set the OutputURIResolver that will be used to resolve URIs used in the
     * href attribute of the xsl:result-document instruction.
     *
     * @param outputURIResolver the OutputURIResolver to be used.
     * @since 8.4
     */
    public void setOutputURIResolver(OutputURIResolver outputURIResolver) {
        defaultXsltCompilerInfo.setOutputURIResolver(outputURIResolver);
    }

    /**
     * Set a custom SerializerFactory. This will be used to create a serializer for a given
     * set of output properties and result destination.
     *
     * @param factory a custom SerializerFactory
     * @since 8.8
     */
    public void setSerializerFactory(SerializerFactory factory) {
        serializerFactory = factory;
    }

    /**
     * Get the SerializerFactory. This returns the standard built-in SerializerFactory, unless
     * a custom SerializerFactory has been registered.
     *
     * @return the SerializerFactory in use
     * @since 8.8
     */
    public SerializerFactory getSerializerFactory() {
        return serializerFactory;
    }

    /**
     * Get the CharacterSetFactory. Note: at present this cannot be changed.
     *
     * @return the CharacterSetFactory in use.
     * @since 9.2
     */
    public CharacterSetFactory getCharacterSetFactory() {
        if (characterSetFactory == null) {
            characterSetFactory = new CharacterSetFactory();
        }
        return characterSetFactory;
    }

    /**
     * Set the default serialization properties
     *
     * @param props the default properties
     */
    public void setDefaultSerializationProperties(Properties props) {
        defaultSerializationProperties = props;
    }

    /**
     * Get the default serialization properties
     *
     * @return the default properties
     */
    public Properties getDefaultSerializationProperties() {
        return defaultSerializationProperties;
    }

    /**
     * Process an xsl:result-document instruction. The Saxon-HE version of this method simply executes the instruction.
     * The Saxon-EE version starts a new thread, and executes the instruction in that thread.
     * @param instruction the instruction to be executed
     * @param content the expression that defines the content of the new result document
     * @param context the evaluation context
     * @throws XPathException if any dynamic error occurs
     */
	public void processResultDocument(ResultDocument instruction, Expression content, XPathContext context)
		throws XPathException {
		instruction.processInstruction(content, context);
	}

    /**
     * Determine whether brief progress messages and timing information will be output
     * to System.err.
     * <p/>
     * This method is provided largely for internal use. Progress messages are normally
     * controlled directly from the command line interfaces, and are not normally used when
     * driving Saxon from the Java API.
     *
     * @return true if these messages are to be output.
     */
    public boolean isTiming() {
        return enabledProperties.contains(FeatureKeys.TIMING);
    }

    /**
     * Determine whether brief progress messages and timing information will be output
     * to System.err.
     * <p/>
     * This method is provided largely for internal use. Progress messages are normally
     * controlled directly from the command line interfaces, and are not normally used when
     *
     * @param timing true if these messages are to be output.
     */
    public void setTiming(boolean timing) {
        if (timing) {
            enabledProperties.add(FeatureKeys.TIMING);
        } else {
            enabledProperties.remove(FeatureKeys.TIMING);
        }
    }

    /**
     * Determine whether a warning is to be output when running against a stylesheet labelled
     * as version="1.0". The XSLT specification requires such a warning unless the user disables it.
     *
     * @return true if these messages are to be output.
     * @since 8.4
     */
    public boolean isVersionWarning() {
        return defaultXsltCompilerInfo.isVersionWarning();
    }

    /**
     * Determine whether a warning is to be output when the version attribute of the stylesheet does
     * not match the XSLT processor version. (In the case where the stylesheet version is "1.0",
     * the XSLT specification requires such a warning unless the user disables it.)
     *
     * @param warn true if these warning messages are to be output.
     * @since 8.4
     */
    public void setVersionWarning(boolean warn) {
        defaultXsltCompilerInfo.setVersionWarning(warn);
    }

    /**
     * Ask whether streamability extensions are allowed. Saxon allows some constructs
     * to be streamed that are not guaranteed-streamable under the W3C streamability
     * rules, provided that this option is enabled.
     * @return true if streamability extensions are enabled
     * @see {@link FeatureKeys#ALLOW_STREAMABILITY_EXTENSIONS}
     */
    public boolean isAllowStreamabilityExtensions() {
        return enabledProperties.contains(FeatureKeys.ALLOW_STREAMABILITY_EXTENSIONS);
    }

    /**
     * Determine whether the XML parser for source documents will be asked to perform
     * validation of source documents
     *
     * @return true if DTD validation is requested.
     * @since 8.4
     */
    public boolean isValidation() {
        return defaultParseOptions.getDTDValidationMode() == Validation.STRICT ||
                defaultParseOptions.getDTDValidationMode() == Validation.LAX;
    }

    /**
     * Determine whether the XML parser for source documents will be asked to perform
     * DTD validation of source documents
     *
     * @param validation true if DTD validation is to be requested.
     * @since 8.4
     */
    public void setValidation(boolean validation) {
        defaultParseOptions.setDTDValidationMode(validation ? Validation.STRICT : Validation.STRIP);
    }

    /**
     * Create a document projector for a given path map. Document projection is available only
     * in Saxon-EE, so the Saxon-B version of this method throws an exception
     *
     * @param map the path map used to control document projection
     * @return a push filter that implements document projection
     * @throws UnsupportedOperationException if this is not a schema-aware configuration, or
     *                                       if no Saxon-EE license is available
     */
    public FilterFactory makeDocumentProjector(PathMap.PathMapRoot map) {
        throw new UnsupportedOperationException("Document projection requires Saxon-EE");
    }

    /**
     * Ask whether source documents (supplied as a StreamSource or SAXSource)
     * should be subjected to schema validation, and if so, in what validation mode
     *
     * @return the schema validation mode previously set using setSchemaValidationMode(),
     *         or the default mode {@link Validation#STRIP} otherwise.
     */
    public int getSchemaValidationMode() {
        return defaultParseOptions.getSchemaValidationMode();
    }

    /**
     * Say whether source documents (supplied as a StreamSource or SAXSource)
     * should be subjected to schema validation, and if so, in what validation mode.
     * This value may be overridden at the level of a Controller for an individual transformation or query.
     *
     * @param validationMode the validation (or construction) mode to be used for source documents.
     *                       One of {@link Validation#STRIP}, {@link Validation#PRESERVE}, {@link Validation#STRICT},
     *                       {@link Validation#LAX}
     * @since 8.4
     */
    public void setSchemaValidationMode(int validationMode) {
        switch (validationMode) {
            case Validation.STRIP:
            case Validation.PRESERVE:
                break;
            case Validation.LAX:
                if (!isLicensedFeature(LicenseFeature.SCHEMA_VALIDATION)) {
                    // if schema processing isn't supported, then there's never a schema, so lax validation is a no-op.
                    validationMode = Validation.STRIP;
                }
                break;
            case Validation.STRICT:
                checkLicensedFeature(LicenseFeature.SCHEMA_VALIDATION, "strict validation");
                break;
            default:
                throw new IllegalArgumentException("Unsupported validation mode " + validationMode);
        }
        defaultParseOptions.setSchemaValidationMode(validationMode);
    }

    /**
     * Indicate whether schema validation failures on result documents are to be treated
     * as fatal errors or as warnings. Note that all validation
     * errors are reported to the error() method of the ErrorListener, and processing always
     * continues except when that method chooses to throw an exception. At the end of the document,
     * a fatal error is thrown if (a) there have been any validation errors, and (b) this option
     * is not set.
     *
     * @param warn true if schema validation failures are to be treated as warnings; false if they
     *             are to be treated as fatal errors.
     * @since 8.4
     */

    public void setValidationWarnings(boolean warn) {
        defaultParseOptions.setContinueAfterValidationErrors(warn);
    }

    /**
     * Determine whether schema validation failures on result documents are to be treated
     * as fatal errors or as warnings. Note that all validation
     * errors are reported to the error() method of the ErrorListener, and processing always
     * continues except when that method chooses to throw an exception. At the end of the document,
     * a fatal error is thrown if (a) there have been any validation errors, and (b) this option
     * is not set.
     *
     * @return true if validation errors are to be treated as warnings (that is, the
     *         validation failure is reported but processing continues as normal); false
     *         if validation errors are fatal.
     * @since 8.4
     */

    public boolean isValidationWarnings() {
        return defaultParseOptions.isContinueAfterValidationErrors();
    }

    /**
     * Indicate whether attributes that have a fixed or default value are to be expanded when
     * generating a final result tree. By default (and for conformance with the W3C specifications)
     * it is required that fixed and default values should be expanded. However, there are use cases
     * for example when generating XHTML when this serves no useful purpose and merely bloats the output.
     * <p/>
     * <p>This option can be overridden at the level of a PipelineConfiguration</p>
     *
     * @param expand true if fixed and default values are to be expanded as required by the W3C
     *               specifications; false if this action is to be disabled. Note that this only affects the validation
     *               of final result trees; it is not possible to suppress expansion of fixed or default values on input
     *               documents, as this would make the type annotations on input nodes unsound.
     * @since 9.0
     */

    public void setExpandAttributeDefaults(boolean expand) {
        defaultParseOptions.setExpandAttributeDefaults(expand);
    }

    /**
     * Determine whether elements and attributes that have a fixed or default value are to be expanded.
     * This option applies both to DTD-defined attribute defaults and to schema-defined defaults for
     * elements and attributes. If an XML parser is used that does not report whether defaults have
     * been used, this option is ignored.
     * <p/>
     * * <p>This option can be overridden at the level of a PipelineConfiguration</p>
     *
     * @return true if elements and attributes that have a fixed or default value are to be expanded,
     *         false if defaults are not to be expanded. The default value is true. Note that the setting "false"
     *         is potentially non-conformant with the W3C specifications.
     * @since 9.0
     */

    public boolean isExpandAttributeDefaults() {
        return defaultParseOptions.isExpandAttributeDefaults();
    }


    /**
     * Get the target namepool to be used for stylesheets/queries and for source documents.
     *
     * @return the target name pool. If no NamePoo has been specified explicitly, the
     *         default NamePool is returned.
     * @since 8.4
     */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
     * Set the NamePool to be used for stylesheets/queries and for source documents.
     * <p/>
     * <p> Using this method allows several Configurations to share the same NamePool. This
     * was the normal default arrangement until Saxon 8.9, which changed the default so
     * that each Configuration uses its own NamePool.</p>
     * <p/>
     * <p>Sharing a NamePool creates a potential bottleneck, since changes to the namepool are
     * synchronized.</p>
     *
     * @param targetNamePool The NamePool to be used.
     * @since 8.4
     */

    public void setNamePool(NamePool targetNamePool) {
        namePool = targetNamePool;
    }

    /**
     * Get the TypeHierarchy: a cache holding type information
     *
     * @return the type hierarchy cache
     */

    public TypeHierarchy getTypeHierarchy() {
        if (typeHierarchy == null) {
            typeHierarchy = new TypeHierarchy(this);
        }
        return typeHierarchy;
    }

    /**
     * Get the document number allocator.
     * <p/>
     * The document number allocator is used to allocate a unique number to each document built under this
     * configuration. The document number forms the basis of all tests for node identity; it is therefore essential
     * that when two documents are accessed in the same XPath expression, they have distinct document numbers.
     * Normally this is ensured by building them under the same Configuration. Using this method together with
     * {@link #setDocumentNumberAllocator}, however, it is possible to have two different Configurations that share
     * a single DocumentNumberAllocator
     *
     * @return the current DocumentNumberAllocator
     * @since 9.0
     */

    public DocumentNumberAllocator getDocumentNumberAllocator() {
        return documentNumberAllocator;
    }

    /**
     * Set the document number allocator.
     * <p/>
     * The document number allocator is used to allocate a unique number to each document built under this
     * configuration. The document number forms the basis of all tests for node identity; it is therefore essential
     * that when two documents are accessed in the same XPath expression, they have distinct document numbers.
     * Normally this is ensured by building them under the same Configuration. Using this method together with
     * {@link #getDocumentNumberAllocator}, however, it is possible to have two different Configurations that share
     * a single DocumentNumberAllocator</p>
     * <p>This method is for advanced applications only. Misuse of the method can cause problems with node identity.
     * The method should not be used except while initializing a Configuration, and it should be used only to
     * arrange for two different configurations to share the same DocumentNumberAllocators. In this case they
     * should also share the same NamePool.
     *
     * @param allocator the DocumentNumberAllocator to be used
     * @since 9.0
     */

    public void setDocumentNumberAllocator(DocumentNumberAllocator allocator) {
        documentNumberAllocator = allocator;
    }

    /**
     * Determine whether two Configurations are compatible. When queries, transformations, and path expressions
     * are run, all the Configurations used to build the documents and to compile the queries and stylesheets
     * must be compatible. Two Configurations are compatible if they share the same NamePool and the same
     * DocumentNumberAllocator.
     *
     * @param other the other Configuration to be compared with this one
     * @return true if the two configurations are compatible
     */

    public boolean isCompatible(Configuration other) {
        return namePool == other.namePool && documentNumberAllocator == other.documentNumberAllocator;
    }

    /**
     * Get the global document pool. This is used for documents preloaded during query or stylesheet
     * compilation. The user application can preload documents into the global pool, where they will be found
     * if any query or stylesheet requests the specified document using the doc() or document() function.
     *
     * @return the global document pool
     * @since 9.1
     */

    public DocumentPool getGlobalDocumentPool() {
        return globalDocumentPool;
    }

    /**
     * Determine whether whitespace-only text nodes are to be stripped unconditionally
     * from source documents.
     *
     * @return true if all whitespace-only text nodes are stripped.
     * @since 8.4
     */

    public boolean isStripsAllWhiteSpace() {
        return defaultParseOptions.getStripSpace() == Whitespace.ALL;
    }

    /**
     * Determine whether whitespace-only text nodes are to be stripped unconditionally
     * from source documents.
     *
     * @param stripsAllWhiteSpace if all whitespace-only text nodes are to be stripped.
     * @since 8.4
     */

    public void setStripsAllWhiteSpace(boolean stripsAllWhiteSpace) {
        if (stripsAllWhiteSpace) {
            defaultParseOptions.setStripSpace(Whitespace.ALL);
        }
    }

    /**
     * Set which kinds of whitespace-only text node should be stripped.
     *
     * @param kind the kind of whitespace-only text node that should be stripped when building
     *             a source tree. One of {@link Whitespace#NONE} (none), {@link Whitespace#ALL} (all),
     *             or {@link Whitespace#IGNORABLE} (element-content whitespace as defined in a DTD or schema)
     */

    public void setStripsWhiteSpace(int kind) {
        defaultParseOptions.setStripSpace(kind);
    }

    /**
     * Set which kinds of whitespace-only text node should be stripped.
     *
     * @return kind the kind of whitespace-only text node that should be stripped when building
     *         a source tree. One of {@link net.sf.saxon.value.Whitespace#NONE} (none), {@link Whitespace#ALL} (all),
     *         or {@link Whitespace#IGNORABLE} (element-content whitespace as defined in a DTD or schema)
     */

    public int getStripsWhiteSpace() {
        return defaultParseOptions.getStripSpace();
    }


    /**
     * Get a parser for source documents. The parser is allocated from a pool if any are available
     * from the pool: the client should ideally return the parser to the pool after use, so that it
     * can be reused.
     * <p/>
     * This method is intended primarily for internal use.
     *
     * @return a parser, in which the namespace properties must be set as follows:
     *         namespaces=true; namespace-prefixes=false. The DTD validation feature of the parser will be set
     *         on or off depending on the {@link #setValidation(boolean)} setting.
     * @throws javax.xml.transform.TransformerFactoryConfigurationError if a failure occurs
     * configuring the parser for use.
     */

    public XMLReader getSourceParser() throws TransformerFactoryConfigurationError {
        if (sourceParserPool == null) {
            sourceParserPool = new ConcurrentLinkedQueue<XMLReader>();
        }
        XMLReader parser = sourceParserPool.poll();
        if (parser != null) {
            return parser;
        }

        if (getSourceParserClass() != null) {
            parser = makeParser(getSourceParserClass());
        } else {
            parser = loadParser();
        }
        if (isTiming()) {
            reportParserDetails(parser);
        }
        try {
            Sender.configureParser(parser);
        } catch (XPathException err) {
            throw new TransformerFactoryConfigurationError(err);
        }
        if (isValidation()) {
            try {
                parser.setFeature("http://xml.org/sax/features/validation", true);
            } catch (SAXException err) {
                throw new TransformerFactoryConfigurationError("The XML parser does not support validation");
            }
        }

        return parser;
    }

    /**
     * Report the parser details to the standard error output
     *
     * @param reader the parser
     */

    private void reportParserDetails(XMLReader reader) {
        String name = reader.getClass().getName();
//        if (name.equals("com.sun.org.apache.xerces.internal.parsers.SAXParser")) {
//            name += " version " + com.sun.org.apache.xerces.internal.impl.Version.getVersion();
//        }
        standardErrorOutput.println("Using parser " + name);
    }

    /**
     * Return a source parser to the pool, for reuse
     *
     * @param parser The parser: the caller must not supply a parser that was obtained by any
     *               mechanism other than calling the getSourceParser() method.
     *               Must not be null.
     */

    public synchronized void reuseSourceParser(/*@NotNull*/ XMLReader parser) {
        if (sourceParserPool == null) {
            sourceParserPool = new ConcurrentLinkedQueue<XMLReader>();
        }
        try {
            try {
                // give things back to the garbage collecter
                parser.setContentHandler(null);
                if(parser.getEntityResolver() == defaultParseOptions.getEntityResolver()) {
                    parser.setEntityResolver(null);
                }
                parser.setDTDHandler(null);
                parser.setErrorHandler(null);
                // Unfortunately setting the lexical handler to null doesn't work on Xerces, because
                // it tests "value instanceof LexicalHandler". So we set it to a lexical handler that
                // holds no references
                parser.setProperty("http://xml.org/sax/properties/lexical-handler", dummyLexicalHandler);
            } catch (SAXNotRecognizedException err) {
                //
            } catch (SAXNotSupportedException err) {
                //
            }
            sourceParserPool.offer(parser);
        } catch (Exception e) {
            // setting the callbacks on an XMLReader to null doesn't always work; some parsers throw a
            // NullPointerException. If anything goes wrong, the simplest approach is to ignore the error
            // and not attempt to reuse the parser.
        }
    }

    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    private static XMLReader loadParser() {
        return platform.loadParser();
    }

    /**
     * Get the parser for stylesheet documents. This parser is also used for schema documents.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return an XML parser (a SAX2 parser) that can be used for stylesheets and schema documents
     * @throws javax.xml.transform.TransformerFactoryConfigurationError if an error occurs
     * configuring the parser
     */

    public synchronized XMLReader getStyleParser() throws TransformerFactoryConfigurationError {
        if (styleParserPool == null) {
            styleParserPool = new ConcurrentLinkedQueue<XMLReader>();
        }
        XMLReader parser = styleParserPool.poll();
        if (parser != null) {
            return parser;
        }

        if (getStyleParserClass() != null) {
            parser = makeParser(getStyleParserClass());
        } else {
            parser = loadParser();
            StandardEntityResolver resolver = new StandardEntityResolver();
            resolver.setConfiguration(this);
            parser.setEntityResolver(resolver);
        }
        try {
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        } catch (SAXNotRecognizedException e) {
            throw new TransformerFactoryConfigurationError(e);
        } catch (SAXNotSupportedException e) {
            throw new TransformerFactoryConfigurationError(e);
        }
        return parser;
    }

    private static LexicalHandler dummyLexicalHandler = new DefaultHandler2();

    /**
     * Return a stylesheet (or schema) parser to the pool, for reuse
     *
     * @param parser The parser: the caller must not supply a parser that was obtained by any
     *               mechanism other than calling the getStyleParser() method.
     */

    public synchronized void reuseStyleParser(XMLReader parser) {
        if (styleParserPool == null) {
            styleParserPool = new ConcurrentLinkedQueue<XMLReader>();
        }
        try {
            try {
                // give things back to the garbage collecter
                parser.setContentHandler(null);
                //parser.setEntityResolver(null);
                parser.setDTDHandler(null);
                parser.setErrorHandler(null);
                // Unfortunately setting the lexical handler to null doesn't work on Xerces, because
                // it tests "value instanceof LexicalHandler". Instead we set the lexical handler to one
                // that holds no references
                parser.setProperty("http://xml.org/sax/properties/lexical-handler", dummyLexicalHandler);
            } catch (SAXNotRecognizedException err) {
                //
            } catch (SAXNotSupportedException err) {
                //
            }
            styleParserPool.offer(parser);
        } catch (Exception e) {
            // setting the callbacks on an XMLReader to null doesn't always work; some parsers throw a
            // NullPointerException. If anything goes wrong, the simplest approach is to ignore the error
            // and not attempt to reuse the parser.
        }
    }

    /**
     * Simple interface to load a schema document
     *
     * @param absoluteURI the absolute URI of the location of the schema document
     * @throws net.sf.saxon.type.SchemaException if the schema document at the given location cannot be read or is invalid
     */

    public void loadSchema(String absoluteURI) throws SchemaException {
        readSchema(makePipelineConfiguration(), "", absoluteURI, null);
    }

    /**
     * Read a schema from a given schema location
     * <p/>
     * This method is intended for internal use.
     *
     * @param pipe           the PipelineConfiguration
     * @param baseURI        the base URI of the instruction requesting the reading of the schema
     * @param schemaLocation the location of the schema to be read
     * @param expected       The expected targetNamespace of the schema being read, or null if there is no expectation
     * @return the target namespace of the schema; null if there is no expectation
     * @throws UnsupportedOperationException when called in the non-schema-aware version of the product
     * @throws net.sf.saxon.type.SchemaException if the schema cannot be read
     */

    /*@Nullable*/
    public String readSchema(PipelineConfiguration pipe, String baseURI, String schemaLocation, /*@Nullable*/ String expected)
            throws SchemaException {
        needEnterpriseEdition();
        return null;
    }

    /**
     * Read schemas from a list of schema locations.
     * <p/>
     * This method is intended for internal use.
     *
     * @param pipe            the pipeline configuration
     * @param baseURI         the base URI against which the schema locations are to be resolved
     * @param schemaLocations the relative URIs specified as schema locations
     * @param expected        the namespace URI which is expected as the target namespace of the loaded schema
     * @throws net.sf.saxon.type.SchemaException
     *          if an error occurs
     */

    public void readMultipleSchemas(PipelineConfiguration pipe, String baseURI, Collection<String> schemaLocations, String expected)
            throws SchemaException {
        needEnterpriseEdition();
    }


    /**
     * Read an inline schema from a stylesheet.
     * <p/>
     * This method is intended for internal use.
     *
     * @param root          the xs:schema element in the stylesheet
     * @param expected      the target namespace expected; null if there is no
     *                      expectation.
     * @param errorListener The destination for error messages. May be null, in which case
     *                      the errorListener registered with this Configuration is used.
     * @return the actual target namespace of the schema
     * @throws net.sf.saxon.type.SchemaException if the schema cannot be processed
     */

    /*@Nullable*/
    public String readInlineSchema(NodeInfo root, String expected, ErrorListener errorListener)
            throws SchemaException {
        needEnterpriseEdition();
        return null;
    }

    /**
     * Throw an error indicating that a request cannot be satisfied because it requires
     * the schema-aware edition of Saxon
     */

    protected void needEnterpriseEdition() {
        throw new UnsupportedOperationException(
                "You need the Enterprise Edition of Saxon (with an EnterpriseConfiguration) for this operation");
    }

    /**
     * Load a schema, which will be available for use by all subsequent operations using
     * this Configuration. Any errors will be notified to the ErrorListener associated with
     * this Configuration.
     *
     * @param schemaSource the JAXP Source object identifying the schema document to be loaded
     * @throws SchemaException               if the schema cannot be read or parsed or if it is invalid
     * @throws UnsupportedOperationException if the configuration is not schema-aware
     * @since 8.4
     */

    public void addSchemaSource(Source schemaSource) throws SchemaException {
        addSchemaSource(schemaSource, getErrorListener());
    }

    /**
     * Load a schema, which will be available for use by all subsequent operations using
     * this EnterpriseConfiguration.
     *
     * @param schemaSource  the JAXP Source object identifying the schema document to be loaded
     * @param errorListener the ErrorListener to be notified of any errors in the schema.
     * @throws SchemaException if the schema cannot be read or parsed or if it is invalid
     */

    public void addSchemaSource(Source schemaSource, ErrorListener errorListener) throws SchemaException {
        needEnterpriseEdition();
    }

    /**
     * Add a built-in schema for a given namespace. This is a no-op if the configuration is not schema-aware
     *
     * @param namespace the namespace. Currently built-in schemas are available for the XML and FN namespaces
     */

    public void addSchemaForBuiltInNamespace(String namespace) {
        // no action
    }

    /**
     * Determine whether the Configuration contains a cached schema for a given target namespace
     *
     * @param targetNamespace the target namespace of the schema being sought (supply "" for the
     *                        unnamed namespace)
     * @return true if the schema for this namespace is available, false if not.
     */

    public boolean isSchemaAvailable(String targetNamespace) {
        return false;
    }

    /**
     * Remove all schema components that have been loaded into this Configuration.
     * This method must not be used if any processes (such as stylesheet or query compilations
     * or executions) are currently active. In a multi-threaded environment, it is the user's
     * responsibility to ensure that this method is not called unless it is safe to do so.
     */

    public void clearSchemaCache() {
        // no-op except in Saxon-EE
    }

    /**
     * Get the set of namespaces of imported schemas
     *
     * @return a Set whose members are the namespaces of all schemas in the schema cache, as
     *         String objects
     */

    public Set getImportedNamespaces() {
        return Collections.EMPTY_SET;
    }

    /**
     * Mark a schema namespace as being sealed. This is done when components from this namespace
     * are first used for validating a source document or compiling a source document or query. Once
     * a namespace has been sealed, it is not permitted to change the schema components in that namespace
     * by redefining them, deriving new types by extension, or adding to their substitution groups.
     *
     * @param namespace the namespace URI of the components to be sealed
     */

    public void sealNamespace(String namespace) {
        //
    }

    /**
     * Get the set of saxon:param schema parameters declared in the schema held by this Configuration.
     * @return the set of parameters. May return null if none have been declared.
     */

    public Collection<GlobalParam> getDeclaredSchemaParameters() {
        return null;
    }

    /**
     * Get the set of complex types that have been defined as extensions of a given type.
     * Note that we do not seal the schema namespace, so this list is not necessarily final; we must
     * assume that new extensions of built-in simple types can be added at any time
     *
     * @param type the type whose extensions are required
     * @return an iterator over the types that are derived from the given type by extension
     */

    public Iterator<? extends SchemaType> getExtensionsOfType(SchemaType type) {
        Set<SchemaType> e = Collections.emptySet();
        return e.iterator();
    }

    /**
     * Import a precompiled Schema Component Model from a given Source. The schema components derived from this schema
     * document are added to the cache of schema components maintained by this SchemaManager
     *
     * @param source the XML file containing the schema component model, as generated by a previous call on
     *               {@link #exportComponents}
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public void importComponents(Source source) throws XPathException {
        needEnterpriseEdition();
    }

    /**
     * Export a precompiled Schema Component Model containing all the components (except built-in components)
     * that have been loaded into this Processor.
     *
     * @param out the destination to recieve the precompiled Schema Component Model in the form of an
     *            XML document
     * @throws net.sf.saxon.trans.XPathException if a failure occurs
     */

    public void exportComponents(Receiver out) throws XPathException {
        needEnterpriseEdition();
    }

    /**
     * Get information about the schema in the form of a function item. Supports the extension function
     * saxon:schema
     * @return null for a non-schema-aware configuration
     */

    public FunctionItem getSchemaAsFunctionItem() {
        return null;
    }

    /**
     * Get information about the schema in the form of a function item. Supports the extension function
     * saxon:schema
     * @param kind the component kind, e.g. "element declaration"
     * @param name the component name
     * @return null for a non-schema-aware configuration
     */

    public FunctionItem getSchemaComponentAsFunctionItem(String kind, QNameValue name) throws XPathException {
        return null;
    }



    /**
     * Get a global element declaration.
     * <p/>
     * This method is intended for internal use.
     *
     * @param fingerprint the NamePool fingerprint of the name of the required
     *                    element declaration
     * @return the element declaration whose name matches the given
     *         fingerprint, or null if no element declaration with this name has
     *         been registered.
     */

    /*@Nullable*/
    public SchemaDeclaration getElementDeclaration(int fingerprint) {
        return null;
    }

    /**
     * Get a global element declaration.
     *
     * @param qName the name of the required
     *              element declaration
     * @return the element declaration whose name matches the given
     *         fingerprint, or null if no element declaration with this name has
     *         been registered.
     */


    /*@Nullable*/
    public SchemaDeclaration getElementDeclaration(StructuredQName qName) {
        return null;
    }

    /**
     * Get a global attribute declaration.
     * <p/>
     * This method is intended for internal use
     *
     * @param fingerprint the namepool fingerprint of the required attribute
     *                    declaration
     * @return the attribute declaration whose name matches the given
     *         fingerprint, or null if no element declaration with this name has
     *         been registered.
     */

    /*@Nullable*/
    public SchemaDeclaration getAttributeDeclaration(int fingerprint) {
        return null;
    }

    /**
     * Get the top-level schema type definition with a given fingerprint.
     * <p/>
     * This method is intended for internal use and for use by advanced
     * applications. (The SchemaType object returned cannot yet be considered
     * a stable API, and may be superseded when a JAXP API for schema information
     * is defined.)
     *
     * @param fingerprint the fingerprint of the schema type
     * @return the schema type , or null if there is none
     *         with this name.
     */

    /*@Nullable*/
    public SchemaType getSchemaType(int fingerprint) {
        if (fingerprint < 1023) {
            return BuiltInType.getSchemaType(fingerprint);
        }
        return null;
    }

    /**
     * Ask whether a given notation has been declared in the schema
     *
     * @param uri   the targetNamespace of the notation
     * @param local the local part of the notation name
     * @return true if the notation has been declared, false if not
     * @since 9.3
     */

    public boolean isDeclaredNotation(String uri, String local) {
        return false;
    }

    /**
     * Get the external object type corresponding to a fingerprint if it is indeed an external object
     * type, otherwise return null
     *
     * @param fingerprint the name of the type
     * @return the external object type it the name is in the JAVA_TYPE namespace, otherwise null.
     */

    /*@Nullable*/
    protected ExternalObjectType getExternalObjectType(int fingerprint) {
        if (getNamePool().getURI(fingerprint).equals(NamespaceConstant.JAVA_TYPE)) {
            try {
                Class namedClass = dynamicLoader.getClass(getNamePool().getLocalName(fingerprint), null, null);
                if (namedClass == null) {
                    return null;
                }
                return new ExternalObjectType(namedClass, this);
            } catch (XPathException err) {
                return null;
            }
        }
        return null;
    }

    /**
     * Check that a type is validly derived from another type, following the rules for the Schema Component
     * Constraint "Is Type Derivation OK (Simple)" (3.14.6) or "Is Type Derivation OK (Complex)" (3.4.6) as
     * appropriate.
     *
     * @param derived the derived type
     * @param base    the base type; the algorithm tests whether derivation from this type is permitted
     * @param block   the derivations that are blocked by the relevant element declaration
     * @throws SchemaException if the derivation is not allowed
     */

    public void checkTypeDerivationIsOK(SchemaType derived, SchemaType base, int block)
            throws SchemaException {
        // no action. Although the method can be used to check built-in types, it is never
        // needed in the non-schema-aware product
    }

    /**
     * Get a document-level validator to add to a Receiver pipeline.
     * <p/>
     * This method is intended for internal use.
     *
     *
     * @param receiver            The receiver to which events should be sent after validation
     * @param systemId            the base URI of the document being validated
     * @param validationOptions   Supplies options relevant to XSD validation
     * @return A Receiver to which events can be sent for validation
     */

    public Receiver getDocumentValidator(Receiver receiver,
                                         String systemId,
                                         ParseOptions validationOptions) {
        // non-schema-aware version
        return receiver;
    }

    /**
     * Get a Receiver that can be used to validate an element, and that passes the validated
     * element on to a target receiver. If validation is not supported, the returned receiver
     * will be the target receiver.
     * <p/>
     * This method is intended for internal use.
     *
     * @param receiver   the target receiver tp receive the validated element
     * @param validationOptions options affecting the way XSD validation is done
     * @param locationId current location in the stylesheet or query
     * @return The target receiver, indicating that with this configuration, no validation
     *         is performed.
     * @throws net.sf.saxon.trans.XPathException if a validator for the element cannot be created
     */

    public SequenceReceiver getElementValidator(SequenceReceiver receiver,
                                                ParseOptions validationOptions,
                                                int locationId)
            throws XPathException {
        return receiver;
    }

    /**
     * Validate an attribute value.
     * <p/>
     * This method is intended for internal use.
     *
     *
     * @param nameCode   the name of the attribute
     * @param value      the value of the attribute as a string
     * @param validation STRICT or LAX
     * @return the type annotation to apply to the attribute node
     * @throws ValidationException if the value is invalid
     */

    public SimpleType validateAttribute(int nameCode, CharSequence value, int validation)
            throws ValidationException {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Add to a pipeline a receiver that strips all type annotations. This
     * has a null implementation in the Saxon-B product, because type annotations
     * can never arise.
     * <p/>
     * This method is intended for internal use.
     *
     * @param destination the Receiver that events will be written to after whitespace stripping
     * @return the Receiver to which events should be sent for stripping
     */

    public Receiver getAnnotationStripper(Receiver destination) {
        return destination;
    }

    /**
     * Create a new SAX XMLReader object using the class name provided. <br>
     * <p/>
     * The named class must exist and must implement the
     * org.xml.sax.XMLReader or Parser interface. <br>
     * <p/>
     * This method returns an instance of the parser named.
     * <p/>
     * This method is intended for internal use.
     *
     * @param className A string containing the name of the
     *                  SAX parser class, for example "com.microstar.sax.LarkDriver"
     * @return an instance of the Parser class named, or null if it is not
     *         loadable or is not a Parser.
     * @throws javax.xml.transform.TransformerFactoryConfigurationError if a failure
     * occurs configuring the parser of this class
     */
    public XMLReader makeParser(String className)
            throws TransformerFactoryConfigurationError {
        Object obj;
        try {
            obj = dynamicLoader.getInstance(className, null);
        } catch (XPathException err) {
            throw new TransformerFactoryConfigurationError(err);
        }
        if (obj instanceof XMLReader) {
            return (XMLReader) obj;
        }
        throw new TransformerFactoryConfigurationError("Class " + className +
                " is not a SAX2 XMLReader");
    }

    /**
     * Make an expression Parser for a specified version of XPath or XQuery
     *
     * @param language        set to "XP" (XPath) or "XQ" (XQuery) or "PATTERN" (XSLT Patterns)
     * @param updating        indicates whether or not XQuery update syntax may be used. Note that XQuery Update
     *                        is supported only in Saxon-EE
     * @param languageVersion the required version (e.g "1.0", "3.0")
     * @return the QueryParser
     * @throws UnsupportedOperationException if a parser that supports update syntax is requested on Saxon-B
     */

    public ExpressionParser newExpressionParser(String language, boolean updating, DecimalValue languageVersion) {
        if ("XQ".equals(language)) {
            if (updating) {
                throw new UnsupportedOperationException("XQuery Update is supported only in Saxon-EE");
            } else if (DecimalValue.THREE.equals(languageVersion) || DecimalValue.ONE_POINT_ONE.equals(languageVersion)) {
                throw new UnsupportedOperationException("XQuery 3.0 extensions are supported only in Saxon-PE");
            } else if (DecimalValue.ONE.equals(languageVersion)) {
                return new QueryParser();
            } else {
                throw new IllegalArgumentException("Unknown XQuery version " + languageVersion);
            }
        } else if ("XP".equals(language)) {
            if (DecimalValue.THREE.equals(languageVersion)) {
                throw new UnsupportedOperationException("XPath 3.0 extensions are supported only in Saxon-PE");
            } else if (DecimalValue.TWO.equals(languageVersion)) {
                return new ExpressionParser();
            } else {
                throw new IllegalArgumentException("Unknown XPath version " + languageVersion);
            }
        } else if ("PATTERN".equals(language)) {
            if (DecimalValue.THREE.equals(languageVersion)) {
                throw new UnsupportedOperationException("XSLT 3.0 extensions are supported only in Saxon-PE");
            } else {
                return new PatternParser20();
            }
        } else {
            throw new IllegalArgumentException("Unknown expression language " + language);
        }
    }

    /**
     * Make an OuterForExpression (Saxon-PE and Saxon-EE only)
     * @return the new expression
     */

    public Expression makeOuterForExpression() {
        throw new UnsupportedOperationException("'allowing empty' requires Saxon-PE or -EE");
    }



    /**
     * Get a locale given a language code in XML format.
     * <p/>
     * This method is intended for internal use.
     *
     * @param lang the language code
     * @return the Java locale
     */

    public static Locale getLocale(String lang) {
        int hyphen = lang.indexOf('-');
        String language, country;
        if (hyphen < 1) {
            language = lang;
            country = "";
        } else {
            language = lang.substring(1, hyphen);
            country = lang.substring(hyphen + 1);
        }
        return new Locale(language, country);
    }

    /**
     * Set the debugger to be used.
     * <p/>
     * This method is provided for advanced users only, and is subject to change.
     *
     * @param debugger the debugger to be used, or null if no debugger is to be used
     */

    public void setDebugger(/*@Nullable*/ Debugger debugger) {
        this.debugger = debugger;
    }

    /**
     * Get the debugger in use. This will be null if no debugger has been registered.
     * <p/>
     * This method is provided for advanced users only, and is subject to change.
     *
     * @return the debugger in use, or null if none is in use
     */

    /*@Nullable*/
    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * Factory method to create a SlotManager.
     * <p/>
     * This method is provided for advanced users only, and is subject to change.
     *
     * @return a SlotManager (which is a skeletal stack frame representing the mapping of variable
     *         names to slots on the stack frame)
     */

    public SlotManager makeSlotManager() {
        if (debugger == null) {
            return new SlotManager();
        } else {
            return debugger.makeSlotManager();
        }
    }

    /**
     * Create a streaming transformer
     *
     * @param context the initial XPath context
     * @param mode    the initial mode, which must be a streaming mode
     * @return a Receiver to which the streamed input document will be pushed
     * @throws XPathException if a streaming transformer cannot be created (which
     * is always the case in Saxon-HE and Saxon-PE)
     */

    /*@NotNull*/
    public Receiver makeStreamingTransformer(XPathContext context, Mode mode) throws XPathException {
        throw new XPathException("Streaming is only available in Saxon-EE");
    }

    /**
     * Factory method to get an Optimizer.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the optimizer used in this configuration, which is created if necessary
     */

    /*@NotNull*/
    public Optimizer obtainOptimizer() {
        if (optimizer == null) {
            optimizer = new Optimizer(this);
            optimizer.setOptimizationLevel(optimizationLevel);
            assert optimizer != null;
            return optimizer;
        } else {
            return optimizer;
        }
    }

    /**
     * Make a Closure, given the expected reference count
     *
     *
     * @param expression the expression to be evaluated
     * @param ref        the (nominal) number of times the value of the expression is required
     * @param context    the XPath dynamic evaluation context
     * @return the constructed Closure
     * @throws XPathException if a failure occurs constructing the Closure
     */

    public Sequence makeClosure(Expression expression, int ref, XPathContext context) throws XPathException {
        Sequence seq = new Closure();
        if (ref > 1) {
            seq = new MemoClosure();
        }
        return seq;
    }

    /**
     * Make a SequenceExtent, given the expected reference count
     *
     * @param expression the expression to be evaluated
     * @param ref        the (nominal) number of times the value of the expression is required
     * @param context    the XPath dynamic evaluation context
     * @return the constructed SequenceExtent
     * @throws XPathException if evaluation of the expression fails
     */

    public Sequence makeSequenceExtent(Expression expression, int ref, XPathContext context) throws XPathException {
        return SequenceExtent.makeSequenceExtent(expression.iterate(context));
    }


    /**
     * Factory method to get the StyleNodeFactory, used for constructing elements
     * in a stylesheet document
     *
     * @return the StyleNodeFactory used in this Configuration
     */

    public StyleNodeFactory makeStyleNodeFactory() {
        return new StyleNodeFactory(this);
    }

    /**
     * Register an external object model with this Configuration.
     *
     * @param model The external object model.
     *              This can either be one of the system-supplied external
     *              object models for JDOM, XOM, or DOM, or a user-supplied external object model.
     *              <p/>
     *              This method is intended for advanced users only, and is subject to change.
     */

    public void registerExternalObjectModel(ExternalObjectModel model) {
        if (externalObjectModels == null) {
            externalObjectModels = new ArrayList<ExternalObjectModel>(4);
        }
        if (!externalObjectModels.contains(model)) {
            externalObjectModels.add(model);
        }
    }

    /**
     * Get the external object model with a given URI, if registered
     *
     * @param uri the identifying URI of the required external object model
     * @return the requested external object model if available, or null otherwise
     */

    /*@Nullable*/
    public ExternalObjectModel getExternalObjectModel(String uri) {
        for (ExternalObjectModel model : externalObjectModels) {
            if (model.getIdentifyingURI().equals(uri)) {
                return model;
            }
        }
        return null;
    }

    /**
     * Get the external object model that recognizes a particular class of node, if available
     *
     * @param nodeClass the class of the Node object in the external object model
     * @return the requested external object model if available, or null otherwise
     */

    /*@Nullable*/
    public ExternalObjectModel getExternalObjectModel(Class nodeClass) {
        for (ExternalObjectModel model : externalObjectModels) {
            PJConverter converter = model.getPJConverter(nodeClass);
            if (converter != null) {
                return model;
            }
        }
        return null;
    }

    /**
     * Get all the registered external object models.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return a list of external object models supported. The members of the list are of
     *         type {@link ExternalObjectModel}
     */

    public List<ExternalObjectModel> getExternalObjectModels() {
        return externalObjectModels;
    }

    /**
     * Get a NodeInfo corresponding to a DOM or other external Node,
     * either by wrapping or unwrapping the external Node.
     * <p/>
     * This method is intended for internal use.
     *
     * @param source A Source representing the wrapped or unwrapped external Node. This will typically
     *               be a DOMSource, but it may be a similar Source recognized by some other registered external
     *               object model.
     * @return If the Source is a DOMSource and the underlying node is a wrapper around a Saxon NodeInfo,
     *         returns the wrapped Saxon NodeInfo. If the Source is a DOMSource and the undelying node is not such a wrapper,
     *         returns a new Saxon NodeInfo that wraps the DOM Node. If the Source is any other kind of source, it
     *         is offered to each registered external object model for similar treatment. The result is the
     *         NodeInfo object obtained by wrapping or unwrapping the supplied external node.
     * @throws IllegalArgumentException if the source object is not of a recognized class. This method does
     *                                  <emph>not</emph> call the registered {@link SourceResolver to resolve the Source}.
     */

    public NodeInfo unravel(Source source) {
        List<ExternalObjectModel> externalObjectModels = getExternalObjectModels();
        for (ExternalObjectModel model : externalObjectModels) {
            NodeInfo node = model.unravel(source, this);
            if (node != null) {
                if (!node.getConfiguration().isCompatible(this)) {
                    throw new IllegalArgumentException("Externally supplied Node belongs to the wrong Configuration");
                }
                return node;
            }
        }
        if (source instanceof NodeInfo) {
            if (!((NodeInfo) source).getConfiguration().isCompatible(this)) {
                throw new IllegalArgumentException("Externally supplied NodeInfo belongs to the wrong Configuration");
            }
            return (NodeInfo) source;
        }

        throw new IllegalArgumentException("A source of class " +
                    source.getClass() + " is not recognized by any registered object model");

    }

    /**
     * Set the level of DOM interface to be used
     *
     * @param level the DOM level. Must be 2 or 3. By default Saxon assumes that DOM level 3 is available;
     *              this parameter can be set to the value 2 to indicate that Saxon should not use methods unless they
     *              are available in DOM level 2. From Saxon 9.2, this switch remains available, but the use of
     *              DOM level 2 is untested and unsupported.
     */

    public void setDOMLevel(int level) {
        if (!(level == 2 || level == 3)) {
            throw new IllegalArgumentException("DOM Level must be 2 or 3");
        }
        domLevel = level;
    }

    /**
     * Get the level of DOM interface to be used
     *
     * @return the DOM level. Always 2 or 3.
     */

    public int getDOMLevel() {
        return domLevel;
    }

    /**
     * Set the StaticQueryContextFactory used for creating instances of StaticQueryContext
     * @param factory the factory class to be used when a new StaticQueryContext is required.
     * Note that this is not used for the default StaticQueryContext held in the Configuration itself.
     */

    public void setStaticQueryContextFactory(StaticQueryContextFactory factory) {
        staticQueryContextFactory = factory;
    }

    /**
     * Get a new StaticQueryContext (which is also the factory class for creating a query parser).
     * Note that this method is used to underpin the s9api and XQJ APIs for XQuery compilation, and
     * that modifying the behaviour of the StaticQueryContext can affect the behaviour of those APIs
     *
     * @return a new StaticQueryContext
     */

    public StaticQueryContext newStaticQueryContext() {
        return staticQueryContextFactory.newStaticQueryContext(this);
    }

    /**
     * Get a new Pending Update List
     *
     * @return the new Pending Update List
     * @throws UnsupportedOperationException if called when using Saxon-B
     */

    public PendingUpdateList newPendingUpdateList() {
        throw new UnsupportedOperationException("XQuery update is supported only in Saxon-EE");
    }

    /**
     * Make a PipelineConfiguration from the properties of this Configuration
     *
     * @return a new PipelineConfiguration
     * @since 8.4
     */

    public PipelineConfiguration makePipelineConfiguration() {
        PipelineConfiguration pipe = new PipelineConfiguration(this);
        pipe.setURIResolver(getURIResolver());
        pipe.setSchemaURIResolver(getSchemaURIResolver());
        pipe.setHostLanguage(getHostLanguage());
        pipe.setParseOptions(new ParseOptions(defaultParseOptions));
        return pipe;
    }

    /**
     * Get the configuration, given the context. This is provided as a static method to make it accessible
     * as an extension function.
     *
     * @param context the XPath dynamic context
     * @return the Saxon Configuration for a given XPath dynamic context
     */

    public static Configuration getConfiguration(XPathContext context) {
        return context.getConfiguration();
    }

    /**
     * Get the type of function items (values representing a function that can be
     * used as an argument to higher-order functions)
     */

//    public AtomicType getFunctionItemType() {
//        throw new UnsupportedOperationException("Higher-order functions require Saxon-EE");
//    }

    /**
     * Supply a SourceResolver. This is used for handling unknown implementations of the
     * {@link javax.xml.transform.Source} interface: a user-supplied SourceResolver can handle
     * such Source objects and translate them to a kind of Source that Saxon understands.
     *
     * @param resolver the source resolver.
     */

    public void setSourceResolver(SourceResolver resolver) {
        sourceResolver = resolver;
    }

    /**
     * Get the current SourceResolver. If none has been supplied, a system-defined SourceResolver
     * is returned.
     *
     * @return the current SourceResolver
     */

    public SourceResolver getSourceResolver() {
        return sourceResolver;
    }

    /**
     * Resolve a Source.
     *
     * @param source A source object, typically the source supplied as the first
     *               argument to {@link javax.xml.transform.Transformer#transform(javax.xml.transform.Source, javax.xml.transform.Result)}
     *               or similar methods.
     * @param config The Configuration. This provides the SourceResolver with access to
     *               configuration information; it also allows the SourceResolver to invoke the
     *               resolveSource() method on the Configuration object as a fallback implementation.
     * @return a source object that Saxon knows how to process. This must be an instance of one
     *         of the classes  StreamSource, SAXSource, DOMSource, {@link net.sf.saxon.lib.AugmentedSource},
     *         {@link net.sf.saxon.om.NodeInfo},
     *         or {@link net.sf.saxon.pull.PullSource}. Return null if the Source object is not
     *         recognized
     * @throws XPathException if the Source object is recognized but cannot be processed
     */

    /*@Nullable*/
    public Source resolveSource(Source source, Configuration config) throws XPathException {
        if (source instanceof AugmentedSource) {
            return source;
        }
        if (source instanceof StreamSource) {
            return source;
        }
        if (source instanceof SAXSource) {
            return source;
        }
        if (source instanceof DOMSource) {
            return source;
        }
        if (source instanceof NodeInfo) {
            return source;
        }
        if (source instanceof PullSource) {
            return source;
        }
        if (source instanceof PullEventSource) {
            return source;
        }
        return null;
    }

    /**
     * Build a document tree, using options set on this Configuration and on the supplied source
     * object. Options set on the source object override options set in the Configuration. The Source
     * object must be one of the kinds of source recognized by Saxon, or a source that can be resolved
     * using the registered {@link SourceResolver}.
     *
     * @param source the Source to be used. This may be an {@link AugmentedSource}, allowing options
     *               to be specified for the way in which this document will be built. If an AugmentedSource
     *               is supplied then options set in the AugmentedSource take precendence over options
     *               set in the Configuration.
     *               <p>From Saxon 9.2, this method always creates a new tree, it never wraps or returns
     *               an existing tree.</p>
     * @return the document node of the constructed document
     * @throws XPathException if any errors occur during document parsing or validation. Detailed
     *                        errors occurring during schema validation will be written to the ErrorListener associated
     *                        with the AugmentedSource, if supplied, or with the Configuration otherwise.
     * @since 8.9. Modified in 9.0 to avoid copying a supplied document where this is not
     *        necessary. Modified in 9.2 so that this interface always constructs a new tree; it never
     *        wraps an existing document, even if an AugmentedSource that requests wrapping is supplied.
     */

    public DocumentInfo buildDocument(/*@Nullable*/ Source source) throws XPathException {

        if (source == null) {
            throw new NullPointerException("source");
        }

        // Resolve user-defined implementations of Source
        Source src2 = resolveSource(source, this);   // TODO the called buildDocument() does this again redundantly
        if (src2 == null) {
            throw new XPathException("Unknown source class " + source.getClass().getName());
        }
        source = src2;

        ParseOptions options;
        Source underlyingSource = source;
        if (source instanceof AugmentedSource) {
            options = ((AugmentedSource) source).getParseOptions();
            underlyingSource = ((AugmentedSource) source).getContainedSource();
        } else {
            options = new ParseOptions();
        }

        source = underlyingSource;
        return buildDocument(source, options);
    }

    /**
     * Build a document, using specified options for parsing and building. This method always
     * constructs a new tree, it never wraps an existing document (regardless of anything in
     * the parseOptions)
     *
     * @param source       the source of the document to be constructed. If this is an
     *                     AugmentedSource, then any parser options contained in the AugmentedSource take precedence
     *                     over options specified in the parseOptions argument.
     * @param parseOptions options for parsing and constructing the document. Any options that
     *                     are not explicitly set in parseOptions default first to the values supplied in the source
     *                     argument if it is an AugmentedSource, and then to the values set in this Configuration.
     *                     The supplied parseOptions object is not modified.
     * @return the document node of the constructed document
     * @throws XPathException if parsing fails, or if the Source represents a node other than
     *                        a document node
     * @since 9.2
     */

    public DocumentInfo buildDocument(/*@Nullable*/ Source source, ParseOptions parseOptions) throws XPathException {

        if (source == null) {
            throw new NullPointerException("source");
        }

        boolean finallyClose = false;
        try {
            ParseOptions options = new ParseOptions(parseOptions);

            // Resolve user-defined implementations of Source
            Source src2 = resolveSource(source, this);
            if (src2 == null) {
                throw new XPathException("Unknown source class " + source.getClass().getName());
            }
            source = src2;

            if (source instanceof AugmentedSource) {
                options.merge(((AugmentedSource) source).getParseOptions());
            }

            options.applyDefaults(this);
            finallyClose = options.isPleaseCloseAfterUse();

            // Create an appropriate Builder

            TreeModel treeModel = options.getModel();

            // Decide whether line numbering is in use

            boolean lineNumbering = options.isLineNumbering();

            PipelineConfiguration pipe = makePipelineConfiguration();
            Builder builder = treeModel.makeBuilder(pipe);
            builder.setTiming(isTiming());
            builder.setLineNumbering(lineNumbering);
            builder.setPipelineConfiguration(pipe);
            Sender.send(source, new NamespaceReducer(builder), options);

            // Get the constructed document

            NodeInfo newdoc = builder.getCurrentRoot();
            if (!(newdoc instanceof DocumentInfo)) {
                throw new XPathException("Source object represents a node other than a document node");
            }

            // Reset the builder, detaching it from the constructed document

            builder.reset();

            // Return the constructed document

            return (DocumentInfo) newdoc;

        } finally {
            // If requested, close the input stream
            if (finallyClose) {
                ParseOptions.close(source);
            }
        }

    }


    /**
     * Load a named output emitter or SAX2 ContentHandler and check it is OK.
     *
     * @param clarkName the QName of the user-supplied ContentHandler (requested as a prefixed
     *                  value of the method attribute in xsl:output, or anywhere that serialization parameters
     *                  are allowed), encoded in Clark format as {uri}local
     * @param props     the properties to be used in the case of a dynamically-loaded ContentHandler.
     * @return a Receiver (despite the name, it is not required to be an Emitter)
     * @throws net.sf.saxon.trans.XPathException if a failure occurs creating the Emitter
     */

    public Receiver makeEmitter(String clarkName, Properties props) throws XPathException {
        int brace = clarkName.indexOf('}');
        String localName = clarkName.substring(brace + 1);
        int colon = localName.indexOf(':');
        String className = localName.substring(colon + 1);
        Object handler;
        try {
            handler = dynamicLoader.getInstance(className, null);
        } catch (XPathException e) {
            throw new XPathException("Cannot create user-supplied output method. " + e.getMessage(),
                    SaxonErrorCode.SXCH0004);
        }

        if (handler instanceof Receiver) {
            return (Receiver) handler;
        } else if (handler instanceof ContentHandler) {
            ContentHandlerProxy emitter = new ContentHandlerProxy();
            emitter.setUnderlyingContentHandler((ContentHandler) handler);
            emitter.setOutputProperties(props);
            return emitter;
        } else {
            throw new XPathException("Output method " + className +
                    " is neither a Receiver nor a SAX2 ContentHandler");
        }

    }

    /**
     * Make an "unconstructed" (that is, lazily-constructed) element node
     *
     * @param instr   the instruction that creates the element
     * @param context the dynamic evaluation context
     * @return the lazily constructed element node
     * @throws net.sf.saxon.trans.XPathException if an error occurs, for example
     * if called in Saxon-HE
     */

    public NodeInfo makeUnconstructedElement(ElementCreator instr, XPathContext context)
            throws XPathException {
        throw new XPathException("Lazy element construction requires Saxon-PE");
    }

    /**
     * Make an "unconstructed" (that is, lazily-constructed) document node
     *
     * @param instr   the instruction that creates the document node
     * @param context the dynamic evaluation context
     * @return the lazily constructed document node
     * @throws net.sf.saxon.trans.XPathException
     *          in Saxon-HE
     */

    public DocumentInfo makeUnconstructedDocument(DocumentInstr instr, XPathContext context)
            throws XPathException {
        throw new XPathException("Lazy document construction requires Saxon-PE");
    }

    /**
     * Get the sibling position of a node: specifically, count how many preceding siblings
     * of a node satisfy the nodetest. This method is included here so it can be subclassed
     * to handle streamed nodes.
     * @param node the starting node, which is assumed to satisfy the node test
     * @param nodeTest the node test
     * @return the number of preceding siblings that satisfy the node test, plus one, unless the
     * number exceeds max, in which case return some number greater than or equal to max.
     */

    public int getSiblingPosition(NodeInfo node, NodeTest nodeTest, int max) throws XPathException {
        AxisIterator prev = node.iterateAxis(AxisInfo.PRECEDING_SIBLING, nodeTest);
        int count = 1;
        while (true) {
            NodeInfo n = prev.next();
            if (n == null) {
                return count;
            }
            if (++count > max) {
                return count;
            }
        }
    }


    /**
     * Set a property of the configuration. This method underpins the setAttribute() method of the
     * TransformerFactory implementation, and is provided
     * to enable setting of Configuration properties using URIs without instantiating a TransformerFactory:
     * specifically, this may be useful when running XQuery, and it is also used by the Validator API
     *
     * @param name  the URI identifying the property to be set. See the class {@link FeatureKeys} for
     *              constants representing the property names that can be set.
     * @param value the value of the property. Note that boolean values may be supplied either as a Boolean,
     *              or as one of the strings "0", "1", "true", "false", "yes", "no", "on", or "off".
     * @throws IllegalArgumentException if the property name is not recognized or if the value is not
     *                                  a valid value for the named property
     */

    public void setConfigurationProperty(String name, Object value) {

        if (name.equals(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.ALLOW_MULTITHREADING)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.COLLATION_URI_RESOLVER)) {
            if (!(value instanceof CollationURIResolver)) {
                throw new IllegalArgumentException(
                        "COLLATION_URI_RESOLVER value must be an instance of net.sf.saxon.lib.CollationURIResolver");
            }
            setCollationURIResolver((CollationURIResolver) value);

        } else if (name.equals(FeatureKeys.COLLATION_URI_RESOLVER_CLASS)) {
            setCollationURIResolver(
                    (CollationURIResolver) instantiateClassName(name, value, CollationURIResolver.class));

        } else if (name.equals(FeatureKeys.COLLECTION_URI_RESOLVER)) {
            if (!(value instanceof CollectionURIResolver)) {
                throw new IllegalArgumentException(
                        "COLLECTION_URI_RESOLVER value must be an instance of net.sf.saxon.lib.CollectionURIResolver");
            }
            setCollectionURIResolver((CollectionURIResolver) value);

        } else if (name.equals(FeatureKeys.COLLECTION_URI_RESOLVER_CLASS)) {
            setCollectionURIResolver(
                    (CollectionURIResolver) instantiateClassName(name, value, CollectionURIResolver.class));

        } else if (name.equals(FeatureKeys.COMPILE_WITH_TRACING)) {
            boolean b = requireBoolean("COMPILE_WITH_TRACING", value);
            setCompileWithTracing(b);

        } else if (name.equals(FeatureKeys.DEFAULT_COLLATION)) {
            getCollationMap().setDefaultCollationName(value.toString());

        } else if (name.equals(FeatureKeys.DEFAULT_COLLECTION)) {
            setDefaultCollection(value.toString());

        } else if (name.equals(FeatureKeys.DEFAULT_COUNTRY)) {
            setDefaultCountry(value.toString());

        } else if (name.equals(FeatureKeys.DEFAULT_LANGUAGE)) {
            setDefaultLanguage(value.toString());

        } else if (name.equals(FeatureKeys.DTD_VALIDATION)) {
            boolean b = requireBoolean(name, value);
            setValidation(b);

        } else if (name.equals(FeatureKeys.DTD_VALIDATION_RECOVERABLE)) {
            boolean b = requireBoolean(name, value);
            if (b) {
                defaultParseOptions.setDTDValidationMode(Validation.LAX);
            } else {
                defaultParseOptions.setDTDValidationMode(isValidation() ? Validation.STRICT : Validation.SKIP);
            }

        } else if (name.equals(FeatureKeys.ENTITY_RESOLVER_CLASS)) {
            if ("".equals(value)) {
                defaultParseOptions.setEntityResolver(null);
            } else {
                defaultParseOptions.setEntityResolver(
                        (EntityResolver) instantiateClassName(name, value, EntityResolver.class));
            }

        } else if (name.equals(FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER)) {
            if (!(value instanceof EnvironmentVariableResolver)) {
                throw new IllegalArgumentException(
                        "ENVIRONMENT_VARIABLE_RESOLVER value must be an instance of net.sf.saxon.lib.EnvironmentVariableResolver");
            }
            environmentVariableResolver = (EnvironmentVariableResolver)value;

        } else if (name.equals(FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER_CLASS)) {
            environmentVariableResolver =
                    (EnvironmentVariableResolver)instantiateClassName(name, value, EnvironmentVariableResolver.class);

        } else if (name.equals(FeatureKeys.ERROR_LISTENER_CLASS)) {
            setErrorListener((ErrorListener) instantiateClassName(name, value, ErrorListener.class));

        } else if (name.equals(FeatureKeys.EXPAND_ATTRIBUTE_DEFAULTS)) {
            boolean b = requireBoolean(name, value);
            setExpandAttributeDefaults(b);

        } else if (name.equals(FeatureKeys.IGNORE_SAX_SOURCE_PARSER)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.LAZY_CONSTRUCTION_MODE)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.LINE_NUMBERING)) {
            boolean b = requireBoolean(name, value);
            setLineNumbering(b);

        } else if (name.equals(FeatureKeys.MESSAGE_EMITTER_CLASS)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("MESSAGE_EMITTER_CLASS class must be a String");
            }
            setMessageEmitterClass((String) value);

        } else if (name.equals(FeatureKeys.MODULE_URI_RESOLVER)) {
            if (!(value instanceof ModuleURIResolver)) {
                throw new IllegalArgumentException(
                        "MODULE_URI_RESOLVER value must be an instance of net.sf.saxon.lib.ModuleURIResolver");
            }
            setModuleURIResolver((ModuleURIResolver) value);

        } else if (name.equals(FeatureKeys.MODULE_URI_RESOLVER_CLASS)) {
            setModuleURIResolver(
                    (ModuleURIResolver) instantiateClassName(name, value, ModuleURIResolver.class));

        } else if (name.equals(FeatureKeys.NAME_POOL)) {
            if (!(value instanceof NamePool)) {
                throw new IllegalArgumentException("NAME_POOL value must be an instance of net.sf.saxon.om.NamePool");
            }
            setNamePool((NamePool) value);

        } else if (name.equals(FeatureKeys.OPTIMIZATION_LEVEL)) {
            String s = requireString(name, value);
            try {
                optimizationLevel = Integer.parseInt(s);
                if (optimizationLevel < Optimizer.NO_OPTIMIZATION || optimizationLevel > Optimizer.FULL_OPTIMIZATION) {
                    throw new IllegalArgumentException("OPTIMIZATION_LEVEL must be in the range " +
                            Optimizer.NO_OPTIMIZATION + " to " + Optimizer.FULL_OPTIMIZATION);
                }
                if (optimizer != null) {
                    optimizer.setOptimizationLevel(optimizationLevel);
                }
                if (optimizationLevel < 10) {
                    internalSetBooleanProperty(FeatureKeys.GENERATE_BYTE_CODE, Boolean.FALSE);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("OPTIMIZATION_LEVEL value must be a number represented as a string");
            }

        } else if (name.equals(FeatureKeys.OUTPUT_URI_RESOLVER)) {
            if (!(value instanceof OutputURIResolver)) {
                throw new IllegalArgumentException(
                        "OUTPUT_URI_RESOLVER value must be an instance of net.sf.saxon.lib.OutputURIResolver");
            }
            setOutputURIResolver((OutputURIResolver) value);

        } else if (name.equals(FeatureKeys.OUTPUT_URI_RESOLVER_CLASS)) {
            setOutputURIResolver(
                    (OutputURIResolver) instantiateClassName(name, value, OutputURIResolver.class));

        } else if (name.equals(FeatureKeys.PRE_EVALUATE_DOC_FUNCTION)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.PREFER_JAXP_PARSER)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.RECOGNIZE_URI_QUERY_PARAMETERS)) {
            boolean b = requireBoolean(name, value);
            getSystemURIResolver().setRecognizeQueryParameters(b);

        } else if (name.equals(FeatureKeys.RECOVERY_POLICY)) {
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException("RECOVERY_POLICY value must be an Integer");
            }
            setRecoveryPolicy((Integer) value);

        } else if (name.equals(FeatureKeys.RECOVERY_POLICY_NAME)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("RECOVERY_POLICY_NAME value must be a String");
            }
            int rval;
            if (value.equals("recoverSilently")) {
                rval = RECOVER_SILENTLY;
            } else if (value.equals("recoverWithWarnings")) {
                rval = RECOVER_WITH_WARNINGS;
            } else if (value.equals("doNotRecover")) {
                rval = DO_NOT_RECOVER;
            } else {
                throw new IllegalArgumentException(
                        "Unrecognized value of RECOVERY_POLICY_NAME = '" + value +
                                "': must be 'recoverSilently', 'recoverWithWarnings', or 'doNotRecover'");
            }
            setRecoveryPolicy(rval);

        } else if (name.equals(FeatureKeys.SERIALIZER_FACTORY_CLASS)) {
            setSerializerFactory(
                    (SerializerFactory) instantiateClassName(name, value, OutputURIResolver.class));

        } else if (name.equals(FeatureKeys.SCHEMA_URI_RESOLVER)) {
            if (!(value instanceof SchemaURIResolver)) {
                throw new IllegalArgumentException(
                        "SCHEMA_URI_RESOLVER value must be an instance of net.sf.saxon.lib.SchemaURIResolver");
            }
            setSchemaURIResolver((SchemaURIResolver) value);

        } else if (name.equals(FeatureKeys.SCHEMA_URI_RESOLVER_CLASS)) {
            setSchemaURIResolver(
                    (SchemaURIResolver) instantiateClassName(name, value, SchemaURIResolver.class));


        } else if (name.equals(FeatureKeys.SCHEMA_VALIDATION)) {
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException("SCHEMA_VALIDATION must be an integer");
            }
            setSchemaValidationMode((Integer) value);

        } else if (name.equals(FeatureKeys.SCHEMA_VALIDATION_MODE)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("SCHEMA_VALIDATION_MODE must be a string");
            }
            setSchemaValidationMode(Validation.getCode((String) value));

        } else if (name.equals(FeatureKeys.SOURCE_PARSER_CLASS)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("SOURCE_PARSER_CLASS class must be a String");
            }
            setSourceParserClass((String) value);

        } else if (name.equals(FeatureKeys.SOURCE_RESOLVER_CLASS)) {
            setSourceResolver(
                    (SourceResolver) instantiateClassName(name, value, SourceResolver.class));

        } else if (name.equals(FeatureKeys.STANDARD_ERROR_OUTPUT_FILE)) {
            // Note, this property is write-only
            try (FileOutputStream fos = new FileOutputStream(((String) value), true);
            	PrintStream ps = new PrintStream(fos, true);) {
                setStandardErrorOutput(ps);
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe);
            }

        } else if (name.equals(FeatureKeys.STRIP_WHITESPACE)) {
            String s = requireString(name, value);
            int ival;
            if (s.equals("all")) {
                ival = Whitespace.ALL;
            } else if (s.equals("none")) {
                ival = Whitespace.NONE;
            } else if (s.equals("ignorable")) {
                ival = Whitespace.IGNORABLE;
            } else {
                throw new IllegalArgumentException(
                        "Unrecognized value STRIP_WHITESPACE = '" + value +
                                "': must be 'all', 'none', or 'ignorable'");
            }
            setStripsWhiteSpace(ival);


        } else if (name.equals(FeatureKeys.STYLE_PARSER_CLASS)) {
            setStyleParserClass(requireString(name, value));

        } else if (name.equals(FeatureKeys.TIMING)) {
            setTiming(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.TRACE_EXTERNAL_FUNCTIONS)) {
            internalSetBooleanProperty(FeatureKeys.TRACE_EXTERNAL_FUNCTIONS, value);

        } else if (name.equals(FeatureKeys.TRACE_OPTIMIZER_DECISIONS)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.TRACE_LISTENER)) {
            if (!(value instanceof TraceListener)) {
                throw new IllegalArgumentException("TRACE_LISTENER is of wrong class");
            }
            setTraceListener((TraceListener) value);

        } else if (name.equals(FeatureKeys.TRACE_LISTENER_CLASS)) {
            setTraceListenerClass(requireString(name, value));

        } else if (name.equals(FeatureKeys.TREE_MODEL)) {
            if (!(value instanceof Integer)) {
                throw new IllegalArgumentException("Tree model must be an Integer");
            }
            setTreeModel((Integer) value);

        } else if (name.equals(FeatureKeys.TREE_MODEL_NAME)) {
            String s = requireString(name, value);
            if (s.equals("tinyTree")) {
                setTreeModel(Builder.TINY_TREE);
            } else if (s.equals("tinyTreeCondensed")) {
                setTreeModel(Builder.TINY_TREE_CONDENSED);
            } else if (s.equals("linkedTree")) {
                setTreeModel(Builder.LINKED_TREE);
            } else  if (s.equals("jdom")) {
                setTreeModel(Builder.JDOM_TREE);
            } else  if (s.equals("jdom2")) {
                setTreeModel(Builder.JDOM2_TREE);
            } else {
                throw new IllegalArgumentException(
                        "Unrecognized value TREE_MODEL_NAME = '" + value +
                                "': must be linkedTree|tinyTree|tinyTreeCondensed");
            }
        } else if (name.equals(FeatureKeys.URI_RESOLVER_CLASS)) {
            setURIResolver(
                    (URIResolver) instantiateClassName(name, value, URIResolver.class));

        } else if (name.equals(FeatureKeys.USE_PI_DISABLE_OUTPUT_ESCAPING)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.USE_TYPED_VALUE_CACHE)) {
            internalSetBooleanProperty(name, value);

        } else if (name.equals(FeatureKeys.USE_XSI_SCHEMA_LOCATION)) {
            defaultParseOptions.setUseXsiSchemaLocation(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.VALIDATION_COMMENTS)) {
            defaultParseOptions.setAddCommentsAfterValidationErrors(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.VALIDATION_WARNINGS)) {
            setValidationWarnings(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.VERSION_WARNING)) {
            setVersionWarning(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XINCLUDE)) {
            setXIncludeAware(requireBoolean(name, value));

        } else if(name.startsWith(FeatureKeys.XML_PARSER_FEATURE)){
             String uri = name.substring(FeatureKeys.XML_PARSER_FEATURE.length());
             defaultParseOptions.addParserFeature(uri, requireBoolean(name, value));

        } else if (name.startsWith(FeatureKeys.XML_PARSER_PROPERTY)) {
            String uri = name.substring(FeatureKeys.XML_PARSER_PROPERTY.length());
            defaultParseOptions.addParserProperties(uri, requireString(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_ALLOW_UPDATE)) {
            getDefaultStaticQueryContext().setUpdatingEnabled(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_CONSTRUCTION_MODE)) {
            getDefaultStaticQueryContext().setConstructionMode(Validation.getCode(value.toString()));

        } else if (name.equals(FeatureKeys.XQUERY_DEFAULT_ELEMENT_NAMESPACE)) {
            getDefaultStaticQueryContext().setDefaultElementNamespace(value.toString());

        } else if (name.equals(FeatureKeys.XQUERY_DEFAULT_FUNCTION_NAMESPACE)) {
            getDefaultStaticQueryContext().setDefaultFunctionNamespace(value.toString());

        } else if (name.equals(FeatureKeys.XQUERY_EMPTY_LEAST)) {
            getDefaultStaticQueryContext().setEmptyLeast(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_INHERIT_NAMESPACES)) {
            getDefaultStaticQueryContext().setInheritNamespaces(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_PRESERVE_BOUNDARY_SPACE)) {
            getDefaultStaticQueryContext().setPreserveBoundarySpace(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_PRESERVE_NAMESPACES)) {
            getDefaultStaticQueryContext().setPreserveNamespaces(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE)) {
            ExpressionParser parser = new ExpressionParser();
            parser.setLanguage(ExpressionParser.SEQUENCE_TYPE, DecimalValue.THREE);
            try {
                SequenceType type = parser.parseSequenceType(value.toString(), new IndependentContext(this));
                // TODO: disallow occurrence indicator
                getDefaultStaticQueryContext().setRequiredContextItemType(type.getPrimaryType());
            } catch (XPathException err) {
                throw new IllegalArgumentException(err);
            }

        } else if (name.equals(FeatureKeys.XQUERY_SCHEMA_AWARE)) {
            getDefaultStaticQueryContext().setSchemaAware(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XQUERY_STATIC_ERROR_LISTENER_CLASS)) {
            getDefaultStaticQueryContext().setErrorListener(
                    (ErrorListener) instantiateClassName(name, value, ErrorListener.class));

        } else if (name.equals(FeatureKeys.XQUERY_VERSION)) {
            if ("1.1".equals(value)) {
                value = "3.0";
            }
            DecimalValue version;
            try {
                version = (DecimalValue) DecimalValue.makeDecimalValue(value.toString(), true);
            } catch (Exception e) {
                throw new IllegalArgumentException("XQuery version");
            }
            getDefaultStaticQueryContext().setLanguageVersion(version);

        } else if (name.equals(FeatureKeys.XML_VERSION) || name.equals("http://saxon.sf.bet/feature/xml-version")) {
            // spelling mistake retained for backwards compatibility with 8.9 and earlier
            String s = requireString(name, value);
            if (!(s.equals("1.0") || s.equals("1.1"))) {
                throw new IllegalArgumentException(
                        "XML_VERSION value must be \"1.0\" or \"1.1\" as a String");

            }
            setXMLVersion((s.equals("1.0") ? XML10 : XML11));

        } else if (name.equals(FeatureKeys.XSD_VERSION)) {
            String s = requireString(name, value);
            if (!(s.equals("1.0") || s.equals("1.1"))) {
                throw new IllegalArgumentException(
                        "XSD_VERSION value must be \"1.0\" or \"1.1\" as a String");

            }
            xsdVersion = ((value.equals("1.0") ? XSD10 : XSD11));
            theConversionRules = null;
        } else if (name.equals(FeatureKeys.XSLT_INITIAL_MODE)) {
            String s = requireString(name, value);
            getDefaultXsltCompilerInfo().setDefaultInitialMode(StructuredQName.fromClarkName(s));

        } else if (name.equals(FeatureKeys.XSLT_INITIAL_TEMPLATE)) {
            String s = requireString(name, value);
            getDefaultXsltCompilerInfo().setDefaultInitialTemplate(StructuredQName.fromClarkName(s));

        } else if (name.equals(FeatureKeys.XSLT_SCHEMA_AWARE)) {
            getDefaultXsltCompilerInfo().setSchemaAware(requireBoolean(name, value));

        } else if (name.equals(FeatureKeys.XSLT_STATIC_ERROR_LISTENER_CLASS)) {
            getDefaultXsltCompilerInfo().setErrorListener(
                    (ErrorListener) instantiateClassName(name, value, ErrorListener.class));

        } else if (name.equals(FeatureKeys.XSLT_STATIC_URI_RESOLVER_CLASS)) {
            getDefaultXsltCompilerInfo().setURIResolver(
                    (URIResolver) instantiateClassName(name, value, URIResolver.class));

        } else if (name.equals(FeatureKeys.XSLT_VERSION)) {
            try {
                if ("2.1".equals(value)) {
                    value = "3.0";
                }
                ConversionResult vn = DecimalValue.makeDecimalValue(requireString(name, value), true);
                DecimalValue dv = (DecimalValue) vn.asAtomic();
                getDefaultXsltCompilerInfo().setXsltVersion(dv);
            } catch (ValidationException e) {
                throw new IllegalArgumentException("XSLT version must be a decimal number");
            }

        } else {
            throw new IllegalArgumentException("Unknown configuration option " + name);
        }
    }

    /**
     * Validate a property value where the required type is boolean
     *
     * @param propertyName the name of the property
     * @param value        the supplied value of the property. This may be either a java.lang.Boolean, or a string
     *                     taking one of the values on|off, true|false, yes|no, or 1|0 (suited to the conventions of different
     *                     configuration APIs that end up calling this method)
     * @return the value as a boolean
     * @throws IllegalArgumentException if the supplied value cannot be validated as a recognized boolean value
     */

    protected boolean requireBoolean(String propertyName, Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            if ("true".equals(value) || "on".equals(value) || "yes".equals(value) || "1".equals(value)) {
                return true;
            } else if ("false".equals(value) || "off".equals(value) || "no".equals(value) || "0".equals(value)) {
                return false;
            } else {
                throw new IllegalArgumentException(propertyName + " must be 'true' or 'false' (or on|off, yes|no, 1|0)");
            }
        } else {
            throw new IllegalArgumentException(propertyName + " must be a boolean (or a string representing a boolean)");
        }
    }

    /**
     * Set a boolean property value, without checking that it is a recognized property name
     * @param propertyName the name of the property to be set
     * @param value a representation of the boolean value.
     * This may be either a java.lang.Boolean, or a string
     * taking one of the values on|off, true|false, yes|no, or 1|0 (suited to the conventions of different
     * configuration APIs that end up calling this method)
     */

    protected void internalSetBooleanProperty(String propertyName, Object value) {
        boolean b = requireBoolean(propertyName, value);
        if (b) {
            enabledProperties.add(propertyName);
        } else {
            enabledProperties.remove(propertyName);
        }
    }

    /**
     * Get a boolean property of the configuration
     *
     * @param propertyName the name of the required property. See the class {@link FeatureKeys} for
     *             constants representing the property names that can be requested. This class only recognizes
     *             properties whose type is boolean.
     * @return the value of the property. In the case of an unrecognized property name, the value returned is
     * false: no error is thrown.
     */

    public boolean getBooleanProperty(String propertyName) {
        return enabledProperties.contains(propertyName);
    }

    /**
     * Set a boolean property of the configuration
     *
     * @param propertyName the name of the required property. See the class {@link FeatureKeys} for
     *             constants representing the property names that can be requested. This class only recognizes
     *             properties whose type is boolean.
     * @param value the value of the property.
     * @throws IllegalArgumentException if the property name is not recognized (as a property whose expected
     * value is boolean)
     */

    public void setBooleanProperty(String propertyName, boolean value) {
        setConfigurationProperty(propertyName, value);
    }

    protected String requireString(String propertyName, Object value) {
        if (value instanceof String) {
            return ((String) value);
        } else {
            throw new IllegalArgumentException("The value of " + propertyName + " must be a string");
        }
    }


    protected Object instantiateClassName(String propertyName, Object value, Class requiredClass) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    propertyName + " must be a String");
        }
        try {
            Object obj = getInstance((String) value, null);
            if (!requiredClass.isAssignableFrom(obj.getClass())) {
                throw new IllegalArgumentException("Error in " + propertyName +
                        ": Class " + value + " does not implement " + requiredClass.getName());
            }
            return obj;
        } catch (XPathException err) {
            throw new IllegalArgumentException(
                    "Cannot use " + value + " as the value of " + propertyName + ". " + err.getMessage());
        }
    }

    /**
     * Get a property of the configuration
     *
     * @param name the name of the required property. See the class {@link FeatureKeys} for
     *             constants representing the property names that can be requested.
     * @return the value of the property. Note that boolean values are returned as a Boolean,
     *         even if the value was supplied as a string (for example "true" or "on").
     * @throws IllegalArgumentException thrown if the property is not one that Saxon recognizes.
     */

    /*@NotNull*/
    public Object getConfigurationProperty(String name) {
        if (name.equals(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.ALLOW_MULTITHREADING)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.COLLATION_URI_RESOLVER)) {
            return getCollationURIResolver();

        } else if (name.equals(FeatureKeys.COLLATION_URI_RESOLVER_CLASS)) {
            return getCollationURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.COLLECTION_URI_RESOLVER)) {
            return getCollectionURIResolver();

        } else if (name.equals(FeatureKeys.COLLECTION_URI_RESOLVER_CLASS)) {
            return getCollectionURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.GENERATE_BYTE_CODE)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.COMPILE_WITH_TRACING)) {
            return isCompileWithTracing();

        } else if (name.equals(FeatureKeys.DEFAULT_COLLATION)) {
            return getCollationMap().getDefaultCollationName();

        } else if (name.equals(FeatureKeys.DEFAULT_COLLECTION)) {
            return getDefaultCollection();

        } else if (name.equals(FeatureKeys.DEFAULT_COUNTRY)) {
            return getDefaultCountry();

        } else if (name.equals(FeatureKeys.DEFAULT_LANGUAGE)) {
            return getDefaultLanguage();

        } else if (name.equals(FeatureKeys.DTD_VALIDATION)) {
            return isValidation();

        } else if (name.equals(FeatureKeys.DTD_VALIDATION_RECOVERABLE)) {
            return defaultParseOptions.getDTDValidationMode() == Validation.LAX;

        } else if (name.equals(FeatureKeys.ERROR_LISTENER_CLASS)) {
            return getErrorListener().getClass().getName();

        } else if (name.equals(FeatureKeys.ENTITY_RESOLVER_CLASS)) {
            EntityResolver er = defaultParseOptions.getEntityResolver();
            if (er == null) {
                return "";
            } else {
                return er.getClass().getName();
            }

        } else if (name.equals(FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER)) {
            return environmentVariableResolver;

        } else if (name.equals(FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER_CLASS)) {
            return environmentVariableResolver.getClass().getName();

        } else if (name.equals(FeatureKeys.EXPAND_ATTRIBUTE_DEFAULTS)) {
            return isExpandAttributeDefaults();

        } else if (name.equals(FeatureKeys.IGNORE_SAX_SOURCE_PARSER)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.LAZY_CONSTRUCTION_MODE)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.LINE_NUMBERING)) {
            return isLineNumbering();

        } else if (name.equals(FeatureKeys.MESSAGE_EMITTER_CLASS)) {
            return getMessageEmitterClass();

        } else if (name.equals(FeatureKeys.MODULE_URI_RESOLVER)) {
            return getModuleURIResolver();

        } else if (name.equals(FeatureKeys.MODULE_URI_RESOLVER_CLASS)) {
            return getModuleURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.NAME_POOL)) {
            return getNamePool();

        } else if (name.equals(FeatureKeys.OPTIMIZATION_LEVEL)) {
            return Integer.toString(optimizationLevel);

        } else if (name.equals(FeatureKeys.OUTPUT_URI_RESOLVER)) {
            return getOutputURIResolver();

        } else if (name.equals(FeatureKeys.OUTPUT_URI_RESOLVER_CLASS)) {
            return getOutputURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.PRE_EVALUATE_DOC_FUNCTION)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.PREFER_JAXP_PARSER)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.RECOGNIZE_URI_QUERY_PARAMETERS)) {
            return getSystemURIResolver().queryParametersAreRecognized();

        } else if (name.equals(FeatureKeys.RECOVERY_POLICY)) {
            return getRecoveryPolicy();

        } else if (name.equals(FeatureKeys.RECOVERY_POLICY_NAME)) {
            switch (getRecoveryPolicy()) {
                case RECOVER_SILENTLY:
                    return "recoverSilently";
                case RECOVER_WITH_WARNINGS:
                    return "recoverWithWarnings";
                case DO_NOT_RECOVER:
                    return "doNotRecover";
                default:
                    throw new IllegalStateException();
            }

        } else if (name.equals(FeatureKeys.SCHEMA_URI_RESOLVER)) {
            return getSchemaURIResolver();

        } else if (name.equals(FeatureKeys.SCHEMA_URI_RESOLVER_CLASS)) {
            return getSchemaURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.SCHEMA_VALIDATION)) {
            return getSchemaValidationMode();

        } else if (name.equals(FeatureKeys.SCHEMA_VALIDATION_MODE)) {
            return Validation.toString(getSchemaValidationMode());

        } else if (name.equals(FeatureKeys.SERIALIZER_FACTORY_CLASS)) {
            return getSerializerFactory().getClass().getName();

        } else if (name.equals(FeatureKeys.SOURCE_PARSER_CLASS)) {
            return getSourceParserClass();

        } else if (name.equals(FeatureKeys.SOURCE_RESOLVER_CLASS)) {
            return getSourceResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.STRIP_WHITESPACE)) {
            int s = getStripsWhiteSpace();
            if (s == Whitespace.ALL) {
                return "all";
            } else if (s == Whitespace.IGNORABLE) {
                return "ignorable";
            } else {
                return "none";
            }

        } else if (name.equals(FeatureKeys.STYLE_PARSER_CLASS)) {
            return getStyleParserClass();

        } else if (name.equals(FeatureKeys.TIMING)) {
            return isTiming();

        } else if (name.equals(FeatureKeys.TRACE_LISTENER)) {
            return traceListener;

        } else if (name.equals(FeatureKeys.TRACE_LISTENER_CLASS)) {
            return traceListenerClass;

        } else if (name.equals(FeatureKeys.TRACE_EXTERNAL_FUNCTIONS)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.TRACE_OPTIMIZER_DECISIONS)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.TREE_MODEL)) {
            return getTreeModel();

        } else if (name.equals(FeatureKeys.TREE_MODEL_NAME)) {
            switch (getTreeModel()) {
                case Builder.TINY_TREE:
                default:
                    return "tinyTree";
                case Builder.TINY_TREE_CONDENSED:
                    return "tinyTreeCondensed";
                case Builder.LINKED_TREE:
                    return "linkedTree";
            }

        } else if (name.equals(FeatureKeys.URI_RESOLVER_CLASS)) {
            return getURIResolver().getClass().getName();

        } else if (name.equals(FeatureKeys.USE_PI_DISABLE_OUTPUT_ESCAPING)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.USE_TYPED_VALUE_CACHE)) {
            return getBooleanProperty(name);

        } else if (name.equals(FeatureKeys.USE_XSI_SCHEMA_LOCATION)) {
            return defaultParseOptions.isUseXsiSchemaLocation();

        } else if (name.equals(FeatureKeys.VALIDATION_COMMENTS)) {
            return defaultParseOptions.isAddCommentsAfterValidationErrors();

        } else if (name.equals(FeatureKeys.VALIDATION_WARNINGS)) {
            return isValidationWarnings();

        } else if (name.equals(FeatureKeys.VERSION_WARNING)) {
            return isVersionWarning();

        } else if (name.equals(FeatureKeys.XINCLUDE)) {
            return isXIncludeAware();

        } else if (name.equals(FeatureKeys.XML_VERSION)) {
            // Spelling mistake retained for backwards compatibility with 8.9 and earlier
            return (getXMLVersion() == XML10 ? "1.0" : "1.1");

        } else if (name.equals(FeatureKeys.XQUERY_ALLOW_UPDATE)) {
            return getDefaultStaticQueryContext().isUpdatingEnabled();

        } else if (name.equals(FeatureKeys.XQUERY_CONSTRUCTION_MODE)) {
            return getDefaultStaticQueryContext().getConstructionMode();

        } else if (name.equals(FeatureKeys.XQUERY_DEFAULT_ELEMENT_NAMESPACE)) {
            return getDefaultStaticQueryContext().getDefaultElementNamespace();

        } else if (name.equals(FeatureKeys.XQUERY_DEFAULT_FUNCTION_NAMESPACE)) {
            return getDefaultStaticQueryContext().getDefaultFunctionNamespace();

        } else if (name.equals(FeatureKeys.XQUERY_EMPTY_LEAST)) {
            return getDefaultStaticQueryContext().isEmptyLeast();

        } else if (name.equals(FeatureKeys.XQUERY_INHERIT_NAMESPACES)) {
            return getDefaultStaticQueryContext().isInheritNamespaces();

        } else if (name.equals(FeatureKeys.XQUERY_PRESERVE_BOUNDARY_SPACE)) {
            return getDefaultStaticQueryContext().isPreserveBoundarySpace();

        } else if (name.equals(FeatureKeys.XQUERY_PRESERVE_NAMESPACES)) {
            return getDefaultStaticQueryContext().isPreserveNamespaces();

        } else if (name.equals(FeatureKeys.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE)) {
            return getDefaultStaticQueryContext().getRequiredContextItemType();

        } else if (name.equals(FeatureKeys.XQUERY_SCHEMA_AWARE)) {
            return getDefaultStaticQueryContext().isSchemaAware();

        } else if (name.equals(FeatureKeys.XQUERY_STATIC_ERROR_LISTENER_CLASS)) {
            return getDefaultStaticQueryContext().getErrorListener().getClass().getName();

        } else if (name.equals(FeatureKeys.XQUERY_VERSION)) {
            return getDefaultStaticQueryContext().getLanguageVersion();

        } else if (name.equals(FeatureKeys.XSD_VERSION)) {
            return (xsdVersion == XSD10 ? "1.0" : "1.1");

        } else if (name.equals(FeatureKeys.XSLT_INITIAL_MODE)) {
            return getDefaultXsltCompilerInfo().getDefaultInitialMode().getClarkName();

        } else if (name.equals(FeatureKeys.XSLT_INITIAL_TEMPLATE)) {
            return getDefaultXsltCompilerInfo().getDefaultInitialTemplate().getClarkName();

        } else if (name.equals(FeatureKeys.XSLT_SCHEMA_AWARE)) {
            return defaultXsltCompilerInfo.isSchemaAware();

        } else if (name.equals(FeatureKeys.XSLT_STATIC_ERROR_LISTENER_CLASS)) {
            return getDefaultXsltCompilerInfo().getErrorListener().getClass().getName();

        } else if (name.equals(FeatureKeys.XSLT_STATIC_URI_RESOLVER_CLASS)) {
            return getDefaultXsltCompilerInfo().getURIResolver().getClass().getName();

        } else if (name.startsWith(FeatureKeys.XML_PARSER_FEATURE)) {
             String uri = name.substring(FeatureKeys.XML_PARSER_FEATURE.length());
             return defaultParseOptions.getParserFeature(uri);
        } else if (name.startsWith(FeatureKeys.XML_PARSER_PROPERTY)) {
             String uri = name.substring(FeatureKeys.XML_PARSER_PROPERTY.length());
             return defaultParseOptions.getParserProperty(uri);
        } else if (name.equals(FeatureKeys.XSLT_VERSION)) {
            return getDefaultXsltCompilerInfo().getXsltVersion().toString();

        } else {
            throw new IllegalArgumentException("Unknown attribute " + name);
        }
    }



    /**
     * Ask whether bytecode should be generated. The default setting
     * is true in Saxon Enterprise Edition and false in all other cases. Setting the option to
     * true has no effect if Saxon-EE is not available (but if it is set to true, this method will
     * return true). Setting the option to false in Saxon-EE
     * is permitted if for some reason bytecode generation is to be suppressed (one possible reason
     * is to improve compilation performance at the expense of evaluation performance).
     * @param hostLanguage one of XSLT or XQUERY
     * @return true if the option is switched on
     */

    public boolean isGenerateByteCode(int hostLanguage) {
        return getBooleanProperty(FeatureKeys.GENERATE_BYTE_CODE) &&
                isLicensedFeature(hostLanguage == XSLT ?
                        LicenseFeature.ENTERPRISE_XSLT :
                        LicenseFeature.ENTERPRISE_XQUERY);
    }

    /**
     * Called by the garbage collector on an object when garbage collection
     * determines that there are no more references to the object. This implementation
     * closes the error output file if one has been allocated.
     */

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (standardErrorOutput != System.err) {
            standardErrorOutput.close();
        }
    }

    /**
     * This class contains constants representing features of the software that may or may
     * not be licensed. (Note, this list is at a finer-grained level than the actual
     * purchasing options.)
     */

    public static class LicenseFeature {
        public static final int SCHEMA_VALIDATION = 1;
        public static final int ENTERPRISE_XSLT = 2;
        public static final int ENTERPRISE_XQUERY = 4;
        public static final int PROFESSIONAL_EDITION = 8;
    }

    /**
     * Make use of Preprocessor directives to create
     * a Platform class depending on the platform choice
     *
     * @return an Platform class
     */
    public static Platform makePlatform(){
          Platform platformChoice;
            platformChoice = new JavaPlatform();

       return platformChoice;
    }

    public static Class<? extends Configuration> makeConfigurationClass() {
            Class<? extends Configuration> configClass;
            configClass = Configuration.class;
        return configClass;
    }


    public static String setSoftwareEdition() {
            String softwareEditionTemp = "HE";

        return softwareEditionTemp;
    }
}

