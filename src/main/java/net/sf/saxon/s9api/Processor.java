////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TreeReceiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.Source;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;

/**
 * The <code>Processor</code> class serves three purposes: it allows global Saxon configuration options to be set;
 * it acts as a factory for generating XQuery, XPath, and XSLT compilers; and it owns certain shared
 * resources such as the Saxon NamePool and compiled schemas. This is the first object that a
 * Saxon application should create. Once established, a Processor may be used in multiple threads.
 * <p/>
 * <p>It is possible to run more than one Saxon Processor concurrently, but only when running completely
 * independent workloads. Nothing can be shared between Processor instances. Within a query or transformation,
 * all source documents and schemas must be built using the same Processor, which must also be used to
 * compile the query or stylesheet.</p>
 */

public class Processor {

    private Configuration config;
    private SchemaManager schemaManager;

    /**
     * Create a Processor
     *
     * @param licensedEdition indicates whether the Processor requires features of Saxon that need a license
     *                        file (that is, features not available in Saxon HE (Home Edition). If true, the method will create
     *                        a Configuration appropriate to the version of the software that is running: for example, if running
     *                        Saxon-EE, it will create an EnterpriseConfiguration. The method does not at this stage check that a license
     *                        is available, and in the absence of a license, it should run successfully provided no features that
     *                        require licensing are actually used. If the argument is set to false, a plain Home Edition Configuration
     *                        is created unconditionally.
     */

    public Processor(boolean licensedEdition) {
        if (licensedEdition) {
            config = Configuration.newConfiguration();
            if (config.getEditionCode().equals("EE")) {
                schemaManager = new SchemaManager(config);
            }
        } else {
            config = new Configuration();
        }
        config.setProcessor(this);
    }

    /**
     * Create a Processor based on an existing Configuration. This constructor is useful for transition,
     * when new components of an application are to use s9api interfaces but existing components use lower-level
     * interfaces.
     *
     * @param config the Configuration to be used by this processor
     * @since 9.3
     */

    public Processor(/*@NotNull*/ Configuration config) {
        this.config = config;
        if (config.getEditionCode().equals("EE")) {
            schemaManager = new SchemaManager(config);
        }
    }

    /**
     * Create a Processor configured according to the settings in a supplied configuration file.
     *
     * @param source the Source of the configuration file
     * @throws SaxonApiException if the configuration file cannot be read, or its contents are invalid
     * @since 9.2
     */

    public Processor(Source source) throws SaxonApiException {
        try {
            config = Configuration.readConfiguration(source);
            schemaManager = new SchemaManager(config);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        config.setProcessor(this);
    }

    /**
     * Create a DocumentBuilder. A DocumentBuilder is used to load source XML documents.
     *
     * @return a newly created DocumentBuilder
     */

    /*@NotNull*/
    public DocumentBuilder newDocumentBuilder() {
        return new DocumentBuilder(config);
    }

    /**
     * Create an XPathCompiler. An XPathCompiler is used to compile XPath expressions.
     *
     * @return a newly created XPathCompiler
     */

    /*@NotNull*/
    public XPathCompiler newXPathCompiler() {
        return new XPathCompiler(this);
    }

    /**
     * Create an XsltCompiler. An XsltCompiler is used to compile XSLT stylesheets.
     *
     * @return a newly created XsltCompiler
     * @throws UnsupportedOperationException if this version of the Saxon product does not support XSLT processing
     */

    /*@NotNull*/
    public XsltCompiler newXsltCompiler() {
        return new XsltCompiler(this);
    }

    /**
     * Create an XQueryCompiler. An XQueryCompiler is used to compile XQuery queries.
     *
     * @return a newly created XQueryCompiler
     * @throws UnsupportedOperationException if this version of the Saxon product does not support XQuery processing
     */

    /*@NotNull*/
    public XQueryCompiler newXQueryCompiler() {
        return new XQueryCompiler(this);
    }

    /**
     * Create a Serializer
     *
     * @return a new Serializer
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer() {
        Serializer s = new Serializer();
        s.setProcessor(this);
        return s;
    }

    /**
     * Create a Serializer initialized to write to a given OutputStream.
     * <p>Closing the output stream after use is the responsibility of the caller.</p>
     *
     * @param stream The OutputStream to which the Serializer will write
     * @return a new Serializer
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(OutputStream stream) {
        Serializer s = new Serializer();
        s.setProcessor(this);
        s.setOutputStream(stream);
        return s;
    }

    /**
     * Create a Serializer initialized to write to a given Writer.
     * <p>Closing the writer after use is the responsibility of the caller.</p>
     *
     * @param writer The Writer to which the Serializer will write
     * @return a new Serializer
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(Writer writer) {
        Serializer s = new Serializer();
        s.setProcessor(this);
        s.setOutputWriter(writer);
        return s;
    }

    /**
     * Create a Serializer initialized to write to a given File.
     *
     * @param file The File to which the Serializer will write
     * @return a new Serializer
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(File file) {
        Serializer s = new Serializer();
        s.setProcessor(this);
        s.setOutputFile(file);
        return s;
    }

    /**
     * Register a simple external/extension function that is to be made available within any stylesheet, query,
     * or XPath expression compiled under the control of this processor.
     * <p/>
     * <p>This interface provides only for simple extension functions that have no side-effects and no dependencies
     * on the static or dynamic context.
     *
     * @param function the implementation of the extension function.</p>
     * @since 9.4
     */

    public void registerExtensionFunction(ExtensionFunction function) {
        ExtensionFunctionDefinitionWrapper wrapper = new ExtensionFunctionDefinitionWrapper(function);
        registerExtensionFunction(wrapper);
    }

    /**
     * Register an extension function that is to be made available within any stylesheet, query,
     * or XPath expression compiled under the control of this processor. This method
     * registers an extension function implemented as an instance of
     * {@link net.sf.saxon.lib.ExtensionFunctionDefinition}, using an arbitrary name and namespace.
     * <p/>
     * <p>This interface allows extension functions that have dependencies on the static or dynamic
     * context. It also allows an extension function to declare that it has side-effects, in which
     * case calls to the function will be optimized less aggressively than usual, although the semantics
     * are still to some degree unpredictable.</p>
     *
     * @param function the implementation of the extension function.
     * @since 9.2
     */

    public void registerExtensionFunction(ExtensionFunctionDefinition function) {
        try {
            config.registerExtensionFunction(function);
        } catch (Exception err) {
            throw new IllegalArgumentException(err);
        }
    }

    /**
     * Get the associated SchemaManager. The SchemaManager provides capabilities to load and cache
     * XML schema definitions. There is exactly one SchemaManager in a schema-aware Processor, and none
     * in a Processor that is not schema-aware. The SchemaManager is created automatically by the system.
     *
     * @return the associated SchemaManager, or null if the Processor is not schema-aware.
     */

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    /**
     * Test whether this processor is schema-aware
     *
     * @return true if this this processor is licensed for schema processing, false otherwise
     */

    public boolean isSchemaAware() {
        return config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION);
    }

    /**
     * Get the user-visible Saxon product version, for example "9.0.0.1"
     *
     * @return the Saxon product version, as a string
     */

    public String getSaxonProductVersion() {
        return Version.getProductVersion();
    }

    /**
     * Set the version of XML used by this Processor. If the value is set to "1.0", then
     * output documents will be serialized as XML 1.0. This option also affects
     * the characters permitted to appear in queries and stylesheets, and the characters that can appear
     * in names (for example, in path expressions).
     * <p/>
     * <p>Note that source documents specifying xml version="1.0" or "1.1" are accepted
     * regardless of this setting.</p>
     *
     * @param version must be one of the strings "1.0" or "1.1"
     * @throws IllegalArgumentException if any string other than "1.0" or "1.1" is supplied
     */

    public void setXmlVersion(/*@NotNull*/ String version) {
        if (version.equals("1.0")) {
            config.setXMLVersion(Configuration.XML10);
        } else if (version.equals("1.1")) {
            config.setXMLVersion(Configuration.XML11);
        } else {
            throw new IllegalArgumentException("XmlVersion");
        }
    }

    /**
     * Get the version of XML used by this Processor. If the value is "1.0", then input documents
     * must be XML 1.0 documents, and output documents will be serialized as XML 1.0. This option also affects
     * the characters permitted to appear in queries and stylesheets, and the characters that can appear
     * in names (for example, in path expressions).
     *
     * @return one of the strings "1.0" or "1.1"
     */

    /*@NotNull*/
    public String getXmlVersion() {
        if (config.getXMLVersion() == Configuration.XML10) {
            return "1.0";
        } else {
            return "1.1";
        }
    }

    /**
     * Set a configuration property
     *
     * @param name  the name of the option to be set. The names of the options available are listed
     *              as constants in class {@link net.sf.saxon.lib.FeatureKeys}.
     * @param value the value of the option to be set.
     * @throws IllegalArgumentException if the property name is not recognized
     */

    public void setConfigurationProperty(/*@NotNull*/ String name, /*@NotNull*/ Object value) {
        config.setConfigurationProperty(name, value);
    }

    /**
     * Get the value of a configuration property
     *
     * @param name the name of the option required. The names of the properties available are listed
     *             as constants in class {@link net.sf.saxon.lib.FeatureKeys}.
     * @return the value of the property, if one is set; or null if the property is unset and there is
     *         no default.
     * @throws IllegalArgumentException if the property name is not recognized
     */


   /*@Nullable*/ public Object getConfigurationProperty(/*@NotNull*/ String name) {
        return config.getConfigurationProperty(name);
    }

    /**
     * Get the underlying {@link Configuration} object that underpins this Processor. This method
     * provides an escape hatch to internal Saxon implementation objects that offer a finer and lower-level
     * degree of control than the s9api classes and methods. Some of these classes and methods may change
     * from release to release.
     *
     * @return the underlying Configuration object
     */

    public Configuration getUnderlyingConfiguration() {
        return config;
    }

    /**
     * Write an XdmValue to a given destination. The sequence represented by the XdmValue is "normalized"
     * as defined in the serialization specification (this is equivalent to constructing a document node
     * in XSLT or XQuery with this sequence as the content expression), and the resulting document is
     * then copied to the destination. If the destination is a serializer this has the effect of serializing
     * the sequence as described in the W3C specifications.
     *
     * @param value       the value to be written
     * @param destination the destination to which the value is to be written
     * @throws SaxonApiException if any failure occurs, for example a serialization error
     */

    public void writeXdmValue(/*@NotNull*/ XdmValue value, /*@NotNull*/ Destination destination) throws SaxonApiException {
        try {
            Receiver out = destination.getReceiver(config);
            out = new NamespaceReducer(out);
            TreeReceiver tree = new TreeReceiver(out);
            tree.open();
            tree.startDocument(0);
            for (XdmItem item : value) {
                tree.append((Item) item.getUnderlyingValue(), 0, NodeInfo.ALL_NAMESPACES);
            }
            tree.endDocument();
            tree.close();
            destination.close();
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }


    private static class ExtensionFunctionDefinitionWrapper extends ExtensionFunctionDefinition {

        private ExtensionFunction function;

        public ExtensionFunctionDefinitionWrapper(ExtensionFunction function) {
            this.function = function;
        }

        /**
         * Get the name of the function, as a QName.
         * <p>This method must be implemented in all subclasses</p>
         *
         * @return the function name
         */
        @Override
        public StructuredQName getFunctionQName() {
            return function.getName().getStructuredQName();
        }

        /**
         * Get the minimum number of arguments required by the function
         * <p>The default implementation returns the number of items in the result of calling
         * {@link #getArgumentTypes}</p>
         *
         * @return the minimum number of arguments that must be supplied in a call to this function
         */
        @Override
        public int getMinimumNumberOfArguments() {
            return function.getArgumentTypes().length;
        }

        /**
         * Get the maximum number of arguments allowed by the function.
         * <p>The default implementation returns the value of {@link #getMinimumNumberOfArguments}
         *
         * @return the maximum number of arguments that may be supplied in a call to this function
         */
        @Override
        public int getMaximumNumberOfArguments() {
            return function.getArgumentTypes().length;
        }

        /**
         * Get the required types for the arguments of this function.
         * <p>This method must be implemented in all subtypes.</p>
         *
         * @return the required types of the arguments, as defined by the function signature. Normally
         *         this should be an array of size {@link #getMaximumNumberOfArguments()}; however for functions
         *         that allow a variable number of arguments, the array can be smaller than this, with the last
         *         entry in the array providing the required type for all the remaining arguments.
         */
        /*@NotNull*/
        @Override
        public net.sf.saxon.value.SequenceType[] getArgumentTypes() {
            net.sf.saxon.s9api.SequenceType[] declaredArgs = function.getArgumentTypes();
            net.sf.saxon.value.SequenceType[] types = new net.sf.saxon.value.SequenceType[declaredArgs.length];
            for (int i = 0; i < declaredArgs.length; i++) {
                types[i] = net.sf.saxon.value.SequenceType.makeSequenceType(
                        declaredArgs[i].getItemType().getUnderlyingItemType(),
                        declaredArgs[i].getOccurrenceIndicator().getCardinality());
            }
            return types;
        }

        /**
         * Get the type of the result of the function
         * <p>This method must be implemented in all subtypes.</p>
         *
         * @param suppliedArgumentTypes the static types of the supplied arguments to the function.
         *                              This is provided so that a more precise result type can be returned in the common
         *                              case where the type of the result depends on the types of the arguments.
         * @return the return type of the function, as defined by its function signature
         */
        @Override
        public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
            net.sf.saxon.s9api.SequenceType declaredResult = function.getResultType();
            return net.sf.saxon.value.SequenceType.makeSequenceType(
                    declaredResult.getItemType().getUnderlyingItemType(),
                    declaredResult.getOccurrenceIndicator().getCardinality());
        }

        /**
         * Ask whether the result actually returned by the function can be trusted,
         * or whether it should be checked against the declared type.
         *
         * @return true if the function implementation warrants that the value it returns will
         *         be an instance of the declared result type. The default value is false, in which case
         *         the result will be checked at run-time to ensure that it conforms to the declared type.
         *         If the value true is returned, but the function returns a value of the wrong type, the
         *         consequences are unpredictable.
         */
        @Override
        public boolean trustResultType() {
            return false;
        }

        /**
         * Ask whether the result of the function depends on the focus, or on other variable parts
         * of the context.
         *
         * @return true if the result of the function depends on the context item, position, or size.
         *         Despite the method name, the method should also return true if the function depends on other
         *         parts of the context that vary from one part of the query/stylesheet to another, for example
         *         the XPath default namespace.
         *         <p>The default implementation returns false.</p>
         *         <p>The method must return true if the function
         *         makes use of any of these values from the dynamic context. Returning true inhibits certain
         *         optimizations, such as moving the function call out of the body of an xsl:for-each loop,
         *         or extracting it into a global variable.</p>
         */
        @Override
        public boolean dependsOnFocus() {
            return false;
        }

        /**
         * Ask whether the function has side-effects. If the function does have side-effects, the optimizer
         * will be less aggressive in moving or removing calls to the function. However, calls on functions
         * with side-effects can never be guaranteed.
         *
         * @return true if the function has side-effects (including creation of new nodes, if the
         *         identity of those nodes is significant). The default implementation returns false.
         */
        @Override
        public boolean hasSideEffects() {
            return false;
        }

        /**
         * Create a call on this function. This method is called by the compiler when it identifies
         * a function call that calls this function.
         */
        /*@NotNull*/
        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(
                        /*@NotNull*/ XPathContext context, Sequence[] arguments) throws XPathException {
                    XdmValue[] args = new XdmValue[arguments.length];
                    for (int i = 0; i < args.length; i++) {
                        Sequence val = SequenceExtent.makeSequenceExtent(arguments[i].iterate());
                        args[i] = XdmValue.wrap(val);
                    }
                    try {
                        XdmValue result = function.call(args);
                        return result.getUnderlyingValue();
                    } catch (SaxonApiException e) {
                        throw new XPathException(e);
                    }
                }
            };
        }


    }


}

