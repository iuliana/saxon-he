////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.DocumentPool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TimingCodeInjector;
import net.sf.saxon.trace.TimingTraceListener;
import net.sf.saxon.trace.XQueryTraceListener;
import net.sf.saxon.trans.CommandLineOptions;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.DecimalValue;

import org.apache.log4j.Logger;
import org.xml.sax.InputSource;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * This <B>Query</B> class provides a command-line interface to the Saxon XQuery processor.<p>
 * <p/>
 * The XQuery syntax supported conforms to the W3C XQuery 1.0 drafts.
 *
 * @author Michael H. Kay
 */
public class Query {

	private static final Logger LOGGER = Logger.getLogger(Query.class);
	
    protected Configuration config;
    protected Properties outputProperties = new Properties();
    protected boolean showTime = false;
    protected int repeat = 1;
    /*@Nullable*/ protected String sourceFileName = null;
    /*@Nullable*/ protected String queryFileName = null;
    protected boolean useURLs = false;
    /*@Nullable*/ protected String outputFileName = null;
    /*@Nullable*/ protected String moduleURIResolverClass = null;
    /*@Nullable*/ protected String uriResolverClass = null;
    protected boolean explain = false;
    protected boolean wrap = false;
    protected boolean pullMode = false;
    protected boolean projection = false;
    /*@Nullable*/ protected DecimalValue languageVersion = null;
    protected boolean updating = false;
    protected boolean writeback = false;
    protected boolean backup = true;
    /*@Nullable*/ protected String explainOutputFileName = null;
    //private PrintStream traceDestination = System.err;
    private boolean closeTraceDestination = false;
    private boolean allowExit = true;

    /**
     * Get the configuration in use
     *
     * @return the configuration
     */

    protected Configuration getConfiguration() {
        return config;
    }

    /**
     * Main program, can be used directly from the command line.
     * <p>The format is:</P>
     * <p>java net.sf.saxon.Query [options] <I>query-file</I> &gt;<I>output-file</I></P>
     * <p>followed by any number of parameters in the form {keyword=value}... which can be
     * referenced from within the query.</p>
     * <p>This program executes the query in query-file.</p>
     *
     * @param args List of arguments supplied on operating system command line
     * @throws Exception Indicates that a compile-time or
     *                   run-time error occurred
     */

    public static void main(String args[])
            throws Exception {
        // the real work is delegated to another routine so that it can be used in a subclass
        (new Query()).doQuery(args, "java net.sf.saxon.Query");
    }

    /**
     * Set the options that are recognized on the command line. This method can be overridden in a subclass
     * to define additional command line options.
     * @param options the CommandLineOptions in which the recognized options are to be registered.
     */

    public void setPermittedOptions(CommandLineOptions options) {
        options.addRecognizedOption("backup", CommandLineOptions.TYPE_BOOLEAN,
                "Save updated documents before overwriting");
        options.addRecognizedOption("catalog", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "Use specified catalog file to resolve URIs");
        options.addRecognizedOption("config", (CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED),
                "Use specified configuration file");
        options.addRecognizedOption("cr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                "Use specified collection URI resolver class");
        options.addRecognizedOption("dtd", CommandLineOptions.TYPE_ENUMERATION,
                "Validate using DTD");
        options.setPermittedValues("dtd", new String[]{"on","off","recover"}, "on");
        options.addRecognizedOption("expand", CommandLineOptions.TYPE_BOOLEAN,
                "Expand attribute defaults from DTD or Schema");
        options.addRecognizedOption("explain", CommandLineOptions.TYPE_FILENAME,
                "Display compiled expression tree and optimization decisions");
        options.addRecognizedOption("ext", CommandLineOptions.TYPE_BOOLEAN,
                "Allow calls to Java extension functions and xsl:result-document");
        options.addRecognizedOption("init", CommandLineOptions.TYPE_CLASSNAME,
                "User-supplied net.sf.saxon.lib.Initializer class to initialize the Saxon Configuration");
        options.addRecognizedOption("l", CommandLineOptions.TYPE_BOOLEAN,
                "Maintain line numbers for source documents");
        options.addRecognizedOption("mr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                "Use named ModuleURIResolver class");
        options.addRecognizedOption("now", CommandLineOptions.TYPE_DATETIME | CommandLineOptions.VALUE_REQUIRED,
                "Run with specified current date/time");
        options.addRecognizedOption("o", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "Use specified file for primary output");
        options.addRecognizedOption("opt", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                "Use optimization level 0..10");
        options.addRecognizedOption("outval", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Action when validation of output file fails");
        options.setPermittedValues("outval", new String[]{"recover","fatal"}, null);
        options.addRecognizedOption("p", CommandLineOptions.TYPE_BOOLEAN,
                "Recognize query parameters in URI passed to doc()");
        options.addRecognizedOption("pipe", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Execute internally in push or pull mode");
        options.setPermittedValues("pipe", new String[]{"push","pull"}, null);
        options.addRecognizedOption("projection", CommandLineOptions.TYPE_BOOLEAN,
                "Use source document projection");
        options.addRecognizedOption("q", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "Query filename");
        options.addRecognizedOption("qs", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                "Query string (usually in quotes)");
        options.addRecognizedOption("quit", CommandLineOptions.TYPE_BOOLEAN | CommandLineOptions.VALUE_REQUIRED,
                "Quit JVM if query fails");
        options.addRecognizedOption("qversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Indicate whether XQuery version 3.0 is supported");
        options.setPermittedValues("qversion", new String[]{"1.0","1.1","3.0"}, null);
        options.addRecognizedOption("r", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                "Use named URIResolver class");
        options.addRecognizedOption("repeat", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                "Run N times for performance measurement");
        options.addRecognizedOption("s", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "Source file for primary input");
        options.addRecognizedOption("sa", CommandLineOptions.TYPE_BOOLEAN,
                "Run in schema-aware mode");
        options.addRecognizedOption("strip", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Handling of whitespace text nodes in source documents");
        options.setPermittedValues("strip", new String[]{"none","all","ignorable"}, null);
        options.addRecognizedOption("t", CommandLineOptions.TYPE_BOOLEAN,
                "Display version and timing information");
        options.addRecognizedOption("T", CommandLineOptions.TYPE_CLASSNAME,
                "Use named TraceListener class, or standard TraceListener");
        options.addRecognizedOption("TJ", CommandLineOptions.TYPE_BOOLEAN,
                "Debug binding and execution of extension functions");
        options.setPermittedValues("TJ", new String[]{"on","off"}, "on");
        options.addRecognizedOption("tree", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Use specified tree model for source documents");
        options.addRecognizedOption("TP", CommandLineOptions.TYPE_FILENAME,
                "Use profiling trace listener, with specified output file");
        options.addRecognizedOption("traceout", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "File for output of trace() and -T output");
        options.setPermittedValues("tree", new String[]{"linked","tiny","tinyc"}, null);
        options.addRecognizedOption("u", CommandLineOptions.TYPE_BOOLEAN,
                "Interpret filename arguments as URIs");
        options.setPermittedValues("u", new String[]{"on","off"}, "on");
        options.addRecognizedOption("update", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Enable or disable XQuery updates, or enable the syntax but discard the updates");
        options.setPermittedValues("update", new String[]{"on","off","discard"}, null);
        options.addRecognizedOption("val", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Apply validation to source documents");
        options.setPermittedValues("val", new String[]{"strict","lax"}, "strict");
        options.addRecognizedOption("wrap", CommandLineOptions.TYPE_BOOLEAN,
                "Wrap result sequence in XML elements");
        options.addRecognizedOption("x", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                "Use named XMLReader class for parsing source documents");
        options.addRecognizedOption("xi", CommandLineOptions.TYPE_BOOLEAN,
                "Expand XInclude directives in source documents");
        options.addRecognizedOption("xmlversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Indicate whether XML 1.1 is supported");
        options.setPermittedValues("xmlversion", new String[]{"1.0","1.1"}, null);
        options.addRecognizedOption("xsd", CommandLineOptions.TYPE_FILENAME_LIST | CommandLineOptions.VALUE_REQUIRED,
                "List of schema documents to be preloaded");
        options.addRecognizedOption("xsdversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                "Indicate whether XSD 1.1 is supported");
        options.setPermittedValues("xsdversion", new String[]{"1.0","1.1"}, null);
        options.addRecognizedOption("xsiloc", CommandLineOptions.TYPE_BOOLEAN,
                "Load schemas named in xsi:schemaLocation (default on)");
        options.addRecognizedOption("?", CommandLineOptions.VALUE_PROHIBITED,
                "Display command line help text");

    }


    /**
     * Support method for main program. This support method can also be invoked from subclasses
     * that support the same command line interface
     *
     * @param args    the command-line arguments
     * @param command name of the class, to be used in error messages
     */

    protected void doQuery(String args[], String command) {

        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        try {
            options.setActualOptions(args);
        } catch (XPathException err) {
            quit(err.getMessage(), 2);
        }


        boolean schemaAware = false;
        String configFile = options.getOptionValue("config");
        if (configFile != null) {
            try {
                config = Configuration.readConfiguration((new StreamSource(configFile)));
                schemaAware = config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
            } catch (XPathException e) {
                quit(e.getMessage(), 2);
            }
        }

        if (config == null && !schemaAware) {
            schemaAware = options.testIfSchemaAware();
        }

        if (config == null) {
            config = Configuration.newConfiguration();
        }

        config.setHostLanguage(Configuration.XQUERY);

        StaticQueryContext staticEnv;
        DynamicQueryContext dynamicEnv = new DynamicQueryContext(config);

        // Check the command-line arguments.

        try {
            parseOptions(options, command, dynamicEnv);
            staticEnv = config.newStaticQueryContext();
            staticEnv.setSchemaAware(schemaAware);

            if (languageVersion != null) {
                staticEnv.setLanguageVersion(languageVersion);
            }
            if (updating) {
                staticEnv.setUpdatingEnabled(true);
            }
            if (config.getTraceListener() != null) {
                staticEnv.setCompileWithTracing(true);
            }

            if (moduleURIResolverClass != null) {
                Object mr = config.getInstance(moduleURIResolverClass, null);
                if (!(mr instanceof ModuleURIResolver)) {
                    badUsage(moduleURIResolverClass + " is not a ModuleURIResolver");
                }
                staticEnv.setModuleURIResolver((ModuleURIResolver)mr);
            }

            if (uriResolverClass != null) {
                config.setURIResolver(config.makeURIResolver(uriResolverClass));
                dynamicEnv.setURIResolver(config.makeURIResolver(uriResolverClass));
            }

            config.displayLicenseMessage();
            if (schemaAware && !config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
                if ("EE".equals(config.getEditionCode())) {
                    quit("Installed license does not allow schema-aware query", 2);
                } else {
                    quit("Schema-aware query requires Saxon Enterprise Edition", 2);
                }
            }
            if(languageVersion.equals(DecimalValue.THREE) && !config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                if ("HE".equals(config.getEditionCode())) {
                    quit("XQuery version 3.0 requires Saxon-PE or Saxon-EE", 2);
                } else {
                    quit("XQuery version 3.0 requires a license", 2);
                }
            }
            if (pullMode) {
                //config.setLazyConstructionMode(true);
            }

            if (explain) {
                config.setBooleanProperty(FeatureKeys.TRACE_OPTIMIZER_DECISIONS, true);
            }

            Source sourceInput = null;

            if (sourceFileName != null) {
                sourceInput = processSourceFile(sourceFileName, useURLs);
            }

            long startTime = System.nanoTime();
            if (showTime) {
                System.err.println("Analyzing query from " + queryFileName);
            }

            // Compile the query

            XQueryExpression exp;
            try {
                exp = compileQuery(staticEnv, queryFileName, useURLs);

                if (showTime) {
                    long endTime = System.nanoTime();
                    System.err.println("Analysis time: " + ((endTime - startTime)/1e6) + " milliseconds");
                    startTime = endTime;
                }

            } catch (XPathException err) {
                int line = -1;
                String module = null;
                if (err.getLocator() != null) {
                    line = err.getLocator().getLineNumber();
                    module = err.getLocator().getSystemId();
                }
                if (err.hasBeenReported()) {
                    quit("Static error(s) in query", 2);
                } else {
                    if (line == -1) {
                        System.err.println("Static error in query: " + err.getMessage());
                    } else {
                        System.err.println("Static error at line " + line + " of " + module + ':');
                        System.err.println(err.getMessage());
                    }
                }
                exp = null;
                if (allowExit) {
                    System.exit(2);
                } else {
                    throw new RuntimeException(err);
                }
            }

            if (explain) {
                explain(exp);
            }

            // Load the source file (applying document projection if requested)

            exp.setAllowDocumentProjection(projection);
            processSource(sourceInput, exp, dynamicEnv);

            // Run the query (repeatedly, if the -repeat option was set)

            startTime = System.nanoTime();
            long totalTime = 0;
            int r;
            for (r = 0; r < repeat; r++) {      // repeat is for internal testing/timing
                try {
                    OutputStream destination;
                    if (outputFileName != null) {
                        File outputFile = new File(outputFileName);
                        if (outputFile.isDirectory()) {
                            quit("Output is a directory", 2);
                        }
                        if (!outputFile.exists()) {
                            File directory = outputFile.getParentFile();
                            if (directory != null && !directory.exists()) {
                                directory.mkdirs();
                            }
                            outputFile.createNewFile();
                        }
                        destination = new FileOutputStream(outputFile);
                    } else {
                        destination = System.out;
                    }

                    runQuery(exp, dynamicEnv, destination, outputProperties);
                } catch (TerminationException err) {
                    throw err;
                } catch (XPathException err) {
                    if (err.hasBeenReported()) {
                        //err.printStackTrace();
                        throw new XPathException("Run-time errors were reported");
                    } else {
                        throw err;
                    }
                }

                if (showTime) {
                    long endTime = System.nanoTime();
                    if (r >= 3) {
                        totalTime += (endTime - startTime);
                    }
                    if (repeat < 100) {
                        System.err.println("Execution time: " + CommandLineOptions.showExecutionTimeNano(endTime - startTime));
                        System.err.println("Memory used: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
                    } else if (totalTime > 1000000000000L) {
                        // quit after 1000 seconds
                        break;
                    }
                    startTime = endTime;
                }
            }

            if (repeat > 3) {
                System.err.println("Average execution time: " +
                        CommandLineOptions.showExecutionTimeNano((totalTime / (r - 3))));
            }

        } catch (TerminationException err) {
        	LOGGER.error("Could not evaluate query", err);
            quit(err.getMessage(), 1);
        } catch (XPathException | LicenseException err) {
        	LOGGER.error("Query processing failed", err);
            quit("Query processing failed: " + err.getMessage(), 2);
        } catch (TransformerFactoryConfigurationError err) {
        	LOGGER.error("Query processing failed", err);
            quit("Query processing failed", 2);
        } catch (SchemaException err) {
        	LOGGER.error("Schema processing failed", err);
            quit("Schema processing failed: " + err.getMessage(), 2);
        } catch (Exception err) {
            LOGGER.error("Could not evaluate query", err);
			quit("Fatal error during query: " + err.getClass().getName() + ": " +
				err.getMessage() == null ? " (no message)" : err.getMessage(), 2);
        }
    }

    /**
     * Parse the options supplied on the command line
     * @param options the command line arguments
     * @param command the name of the command that was used (for diagnostics only)
     * @param dynamicEnv the XQuery dynamic context
     * @throws TransformerException if failures occur. Note, the method may also invoke System.exit().
     */
    protected void parseOptions(CommandLineOptions options, String command, DynamicQueryContext dynamicEnv)
            throws TransformerException {

        // Apply those options which simply update the Configuration

        options.applyToConfiguration(config);

        // Apply options that are processed locally

        allowExit = !"off".equals(options.getOptionValue("quit"));

        dynamicEnv.setURIResolver(config.getURIResolver());

        backup = "on".equals(options.getOptionValue("backup"));
        explainOutputFileName = options.getOptionValue("explain");
        explain = explainOutputFileName != null;
        moduleURIResolverClass = options.getOptionValue("mr");
        outputFileName = options.getOptionValue("o");

        String value = options.getOptionValue("p");
        if ("on".equals(value)) {
            config.setParameterizedURIResolver();
            useURLs = true;
        }

        pullMode = "pull".equals(options.getOptionValue("pipe"));
        projection = "on".equals(options.getOptionValue("projection"));


        value = options.getOptionValue("q");
        if (value != null) {
            queryFileName = value;
        }

        value = options.getOptionValue("qs");
        if (value != null) {
            queryFileName = "{" + value + "}";
        }

        try {
            String qv = options.getOptionValue("qversion");
            if (qv == null) {
                qv = "1.0";
            }
            if ("1.1".equals(qv)) {
                qv = "3.0";
            }
            languageVersion = (DecimalValue)DecimalValue.makeDecimalValue(qv, true).asAtomic();
        } catch (ValidationException err) {
        	LOGGER.error("XQuery version " + options.getOptionValue("qversion") + " must be numeric", err);
            badUsage("XQuery version " + options.getOptionValue("qversion") + " must be numeric");
        }

        value = options.getOptionValue("repeat");
        if (value != null) {
            repeat = Integer.parseInt(value);
        }

        sourceFileName = options.getOptionValue("s");

        value = options.getOptionValue("t");
        if ("on".equals(value)) {
            config.setTiming(true);
            showTime = true;
        }

        value = options.getOptionValue("traceout");
        if (value != null) {
            if (value.equals("#err")) {
                // no action, this is the default
            } else if (value.equals("#out")) {
                dynamicEnv.setTraceFunctionDestination(System.out);
            } else if (value.equals("#null")) {
                dynamicEnv.setTraceFunctionDestination(null);
            } else {
                try {
                    dynamicEnv.setTraceFunctionDestination(
                            new PrintStream(new FileOutputStream(new File(value))));
                    closeTraceDestination = true;
                } catch (FileNotFoundException e) {
                	LOGGER.error("Trace output file " + value + " cannot be created", e);
                    badUsage("Trace output file " + value + " cannot be created");
                }
            }
        }

        value = options.getOptionValue("T");
        if (value != null) {
            if ("".equals(value)) {
                config.setTraceListener(new XQueryTraceListener());
            } else {
                config.setTraceListenerClass(value);
            }
            config.setLineNumbering(true);
        }
        
        value = options.getOptionValue("TP");
        if (value != null) {
            TimingTraceListener listener = new TimingTraceListener();
            config.setTraceListener(listener);
            config.setLineNumbering(true);
            config.getDefaultStaticQueryContext().setCodeInjector(new TimingCodeInjector());
            if (value.length() > 0) {
                try {
					listener.setOutputDestination(new PrintStream(new FileOutputStream(new File(value))));
				} catch (FileNotFoundException e) {
					LOGGER.error("Trace output file " + value + " cannot be created", e);
					badUsage("Trace output file " + value + " cannot be created");
				}
            }
        }

        value = options.getOptionValue("u");
        if (value != null) {
            useURLs = "on".equals(value);
        }

        value = options.getOptionValue("update");
        if (value != null) {
            if (!"off".equals(value)) {
                updating = true;
            }
            writeback = !"discard".equals(value);
        }

        wrap = "on".equals(options.getOptionValue("wrap"));

        value = options.getOptionValue("x");
        if (value != null) {
            config.setSourceParserClass(value);
        }

        String additionalSchemas = options.getOptionValue("xsd");

        value = options.getOptionValue("?");
        if (value != null) {
            badUsage("");
        }

        // Apply options defined locally in a subclass

        applyLocalOptions(options, config);

        // Apply positional options

        List<String> positional = options.getPositionalOptions();
        int currentPositionalOption = 0;

        if (queryFileName == null) {
            if (positional.size() == currentPositionalOption) {
                badUsage("No query file name");
            }
            queryFileName = positional.get(currentPositionalOption++);
        }

        if (currentPositionalOption < positional.size()) {
            badUsage("Unrecognized option: " + positional.get(currentPositionalOption));
        }

        GlobalParameterSet params = new GlobalParameterSet();
        options.setParams(config, params, outputProperties);
        dynamicEnv.setParameters(params);

        if (additionalSchemas != null) {
            CommandLineOptions.loadAdditionalSchemas(config, additionalSchemas);
        }
    }

    /**
     * Customisation hook: apply options defined locally in a subclass
     * @param options the CommandLineOptions
     * @param config the Saxon Configuration
     */
	protected void applyLocalOptions(CommandLineOptions options, Configuration config) {}

    protected Source processSourceFile(String sourceFileName, boolean useURLs) throws TransformerException {
        Source sourceInput;
        if (useURLs || sourceFileName.startsWith("http:") || sourceFileName.startsWith("file:")) {
            sourceInput = config.getURIResolver().resolve(sourceFileName, null);
            if (sourceInput == null) {
                sourceInput = config.getSystemURIResolver().resolve(sourceFileName, null);
            }
        } else if (sourceFileName.equals("-")) {
            // take input from stdin
            sourceInput = new StreamSource(System.in);
        } else {
            File sourceFile = new File(sourceFileName);
            if (!sourceFile.exists()) {
                quit("Source file " + sourceFile + " does not exist", 2);
            }

            if (Configuration.getPlatform().isJava()) {
                InputSource eis = new InputSource(sourceFile.toURI().toString());
                sourceInput = new SAXSource(eis);
            } else {
                sourceInput = new StreamSource(sourceFile.toURI().toString());
            }
        }
        return sourceInput;
    }

    /** Compile the query
	 *
	 * @param staticEnv the static query context
	 * @param queryFileName the filename holding the query (or "-" for the standard input)
	 * @param useURLs true if the filename is in the form of a URI
	 * @return the compiled query
	 * @throws XPathException if query compilation fails
	 * @throws IOException if the query cannot be read */
	protected XQueryExpression compileQuery(StaticQueryContext staticEnv, String queryFileName, boolean useURLs)
		throws XPathException, IOException {
		XQueryExpression exp;
		if (queryFileName.equals("-")) {
			Reader queryReader = new InputStreamReader(System.in);
			exp = staticEnv.compileQuery(queryReader);
		} else if (queryFileName.startsWith("{") && queryFileName.endsWith("}")) {
			// query is inline on the command line
			String q = queryFileName.substring(1, queryFileName.length() - 1);
			exp = staticEnv.compileQuery(q);
		} else if (useURLs || queryFileName.startsWith("http:") || queryFileName.startsWith("file:")) {
			ModuleURIResolver resolver = staticEnv.getModuleURIResolver();
			boolean isStandardResolver = false;
			if (resolver == null) {
				resolver = staticEnv.getConfiguration().getStandardModuleURIResolver();
				isStandardResolver = true;
			}
			while (true) {
				String[] locations = {queryFileName};
				Source[] sources;
				try {
					sources = resolver.resolve(null, null, locations);
				} catch (Exception e) {
					if (e instanceof XPathException) {
						throw (XPathException) e;
					} else {
						XPathException err = new XPathException("Exception in ModuleURIResolver: ", e);
						err.setErrorCode("XQST0059");
						throw err;
					}
				}
				if (sources == null) {
					if (isStandardResolver) {
						// this should not happen
						quit("System problem: standard ModuleURIResolver returned null", 4);
					} else {
						resolver = staticEnv.getConfiguration().getStandardModuleURIResolver();
						isStandardResolver = true;
					}
				} else {
					if (sources.length != 1 || !(sources[0] instanceof StreamSource)) {
						quit("Module URI Resolver must return a single StreamSource", 2);
					}
					String queryText = QueryReader.readSourceQuery((StreamSource) sources[0], config.getNameChecker());
					exp = staticEnv.compileQuery(queryText);
					break;
				}
			}
		} else {
			InputStream queryStream = null;
			try {
				queryStream = new FileInputStream(queryFileName);
				staticEnv.setBaseURI(new File(queryFileName).toURI().toString());
				exp = staticEnv.compileQuery(queryStream, null);
			} finally {
				if (queryStream != null) {
					queryStream.close();
				}
			}
		}
		return exp;
	}

    /**
     * Explain the results of query compilation
     *
     * @param exp the compiled expression
     * @throws FileNotFoundException if the destination for the explanation doesn't exist
     * @throws XPathException        if other failures occur
     */
    protected void explain(XQueryExpression exp) throws FileNotFoundException, XPathException {
        OutputStream explainOutput;
        if (explainOutputFileName == null || "".equals(explainOutputFileName)) {
            explainOutput = System.err;
        } else {
            explainOutput = new FileOutputStream(new File(explainOutputFileName));
        }
        Properties props = ExpressionPresenter.makeDefaultProperties();
        Receiver diag = config.getSerializerFactory().getReceiver(new StreamResult(explainOutput),
                config.makePipelineConfiguration(), props);
        ExpressionPresenter expressionPresenter = new ExpressionPresenter(config, diag);
        exp.explain(expressionPresenter);
    }

    /**
     * Process the supplied source file
     *
     * @param sourceInput the supplied source
     * @param exp         the compiled XQuery expression
     * @param dynamicEnv  the dynamic query context
     * @throws XPathException if processing fails
     */
	protected void processSource(Source sourceInput, XQueryExpression exp, DynamicQueryContext dynamicEnv)
		throws XPathException {
        if (sourceInput != null) {
            ParseOptions options = new ParseOptions(config.getParseOptions());
            if (showTime) {
                System.err.println("Processing " + sourceInput.getSystemId());
            }
            if (!exp.usesContextItem()) {
                System.err.println("Source document ignored - query does not access the context item");
                sourceInput = null;

            } else if (projection) {
                PathMap map = exp.getPathMap();
                PathMap.PathMapRoot contextRoot = map.getContextDocumentRoot();
                if (explain) {
                    System.err.println("DOCUMENT PROJECTION: PATH MAP");
                    map.diagnosticDump(System.err);
                }
                if (contextRoot != null) {
                    if (contextRoot.hasUnknownDependencies()) {
                        System.err.println("Document projection for the context document is not possible, " +
                                "because the query uses paths that defy analysis");
                    } else {
                        options.addFilter(config.makeDocumentProjector(contextRoot));
                    }
                } else {
                    System.err.println("Source document supplied, but query does not access the context item");
                }
            }
            if (sourceInput != null) {
                DocumentInfo doc = config.buildDocument(sourceInput, options);
                dynamicEnv.setContextItem(doc);
            }
        }
    }

    /**
     * Run the query
     *
     * @param exp         the compiled query expression
     * @param dynamicEnv  the dynamic query context
     * @param destination the destination for serialized results
     * @param outputProps serialization properties defining the output format
     * @throws XPathException if the query fails
     * @throws IOException    if input or output fails
     */
	protected void runQuery(XQueryExpression exp, DynamicQueryContext dynamicEnv, OutputStream destination,
		final Properties outputProps) throws XPathException, IOException {
        if (exp.getExpression().isUpdatingExpression() && updating) {
            if (outputProps.getProperty(OutputKeys.METHOD) == null) {
                outputProps.setProperty(OutputKeys.METHOD, "xml");
            }
            if (writeback) {
                final List<XPathException> errors = new ArrayList<>(3);
                UpdateAgent agent = new UpdateAgent() {
                    public void update(NodeInfo node, Controller controller) throws XPathException {
                        try {
                            DocumentPool pool = controller.getDocumentPool();
                            String documentURI = pool.getDocumentURI(node);
                            if (documentURI != null) {
                                QueryResult.rewriteToDisk(node, outputProps, backup, (showTime ? System.err : null));
                            } else if (showTime) {
                                System.err.println("Updated document discarded because it was not read using doc()");
                            }
                        } catch (XPathException err) {
                            System.err.println(err.getMessage());
                            errors.add(err);
                        }
                    }
                };
                exp.runUpdate(dynamicEnv, agent);

                if (!errors.isEmpty()) {
                    throw errors.get(0);
                }
            } else {
                Set affectedDocuments = exp.runUpdate(dynamicEnv);
                if (affectedDocuments.contains(dynamicEnv.getContextItem())) {
                    QueryResult.serialize((NodeInfo)dynamicEnv.getContextItem(),
                            new StreamResult(destination),
                            outputProps);
                }
            }
        } else if (wrap && !pullMode) {
            SequenceIterator results = exp.iterator(dynamicEnv);
            DocumentInfo resultDoc = QueryResult.wrap(results, config);
            QueryResult.serialize(resultDoc, new StreamResult(destination), outputProps);
            destination.close();
        } else if (pullMode) {
            if (wrap) {
                outputProps.setProperty(SaxonOutputKeys.WRAP, "yes");
            }
            exp.pull(dynamicEnv, new StreamResult(destination), outputProps);
        } else {
            exp.run(dynamicEnv, new StreamResult(destination), outputProps);
        }
        if (closeTraceDestination) {
            dynamicEnv.getTraceFunctionDestination().close();
        }
    }

    /**
     * Exit with a message
     *
     * @param message The message to be output
     * @param code    The result code to be returned to the operating
     *                system shell
     */
    protected void quit(String message, int code) {
        System.err.println(message);
        if (allowExit) {
            System.exit(code);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * Report incorrect usage of the command line, with a list of the options and arguments that are available
     *
     * @param message The error message
     */
     protected void badUsage(String message) {
        if (!"".equals(message)) {
            System.err.println(message);
        }
        if (!showTime) {
            System.err.println(config.getProductTitle());
        }
        System.err.println("Usage: see http://www.saxonica.com/documentation9.5/using-xquery/commandline.html");
        System.err.println("Format: " + getClass().getName() + " options params");
        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        System.err.println("Options available:" + options.displayPermittedOptions());
        System.err.println("Use -XYZ:? for details of option XYZ");
        System.err.println("Params: ");
        System.err.println("  param=value           Set query string parameter");
        System.err.println("  +param=filename       Set query document parameter");
        System.err.println("  ?param=expression     Set query parameter using XPath");
        System.err.println("  !param=value          Set serialization parameter");
        if (allowExit) {
            if ("".equals(message)) {
                System.exit(0);
            } else {
                System.exit(2);
            }
        } else {
            throw new RuntimeException(message);
        }
    }
}
