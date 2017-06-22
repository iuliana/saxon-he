////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaException;
import org.xml.sax.*;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class used to read a config.xml file and transfer all settings from the file to the Configuration
 */

public class ConfigurationReader implements ContentHandler, NamespaceResolver {

    private int level = 0;
    private String section = null;
    private FastStringBuffer buffer = new FastStringBuffer(100);
    protected Configuration config;
    private ClassLoader classLoader = null;
    private List<XPathException> errors = new ArrayList<XPathException>();
    private Locator locator;
    private Stack<List<String[]>> namespaceStack = new Stack<List<String[]>>();

    public ConfigurationReader() {
    }

    /**
     * Set the ClassLoader to be used for dynamic loading of the configuration, and for dynamic loading
     * of other classes used within the configuration. By default the class loader of this class is used.
     * @param classLoader the ClassLoader to be used
     */

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Create a Configuration based on the contents of this configuration file
     * @param source the Source of the configuration file
     * @return the constructed Configuration
     * @throws XPathException
     */

    public Configuration makeConfiguration(Source source) throws XPathException {
        InputSource is;
        XMLReader parser = null;
        if (source instanceof SAXSource) {
            parser = ((SAXSource)source).getXMLReader();
            is = ((SAXSource)source).getInputSource();
        } else if (source instanceof StreamSource) {
            is = new InputSource(source.getSystemId());
            is.setCharacterStream(((StreamSource)source).getReader());
            is.setByteStream(((StreamSource)source).getInputStream());
        } else {
            throw new XPathException("Source for configuration file must be a StreamSource or SAXSource");
        }
        if (parser == null) {
            // Don't use the parser from the pool, it might be validating
            parser = Configuration.getPlatform().loadParser();
            try {
                parser.setFeature("http://xml.org/sax/features/namespaces", true);
                parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            } catch (SAXNotRecognizedException e) {
                throw new TransformerFactoryConfigurationError(e);
            } catch (SAXNotSupportedException e) {
                throw new TransformerFactoryConfigurationError(e);
            }
        }
        try {
            parser.setContentHandler(this);
            parser.parse(is);
        } catch (IOException e) {
            throw new XPathException("Failed to read config file", e);
        } catch (SAXException e) {
            throw new XPathException("Failed to parse config file", e);
        }

        if (!errors.isEmpty()) {
            ErrorListener listener;
            if (config == null) {
                listener = new StandardErrorListener();
            } else {
                listener = config.getErrorListener();
            }
            try {
                for (XPathException err: errors) {
                    listener.warning(err);
                }
            } catch (TransformerException e) {
                //
            }
            throw errors.get(0);
        }
        return config;
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    public void startDocument() throws SAXException {
        namespaceStack.push(new ArrayList<String[]>());
    }

    public void endDocument() throws SAXException {
        namespaceStack.pop();
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceStack.peek().add(new String[]{prefix, uri});
    }

    public void endPrefixMapping(String prefix) throws SAXException {

    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        buffer.setLength(0);
        if (NamespaceConstant.SAXON_CONFIGURATION.equals(uri)) {
            if (level == 0) {
                if (!"configuration".equals(localName)) {
                    error(localName, null, null, "configuration");
                }
                String edition = atts.getValue("edition");
                if (edition == null) {
                    edition = "HE";
                }
                if (edition.equals("HE")) {
                    config = new Configuration();
                } else if (edition.equals("PE")) {
                    config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.ProfessionalConfiguration");
                } else if (edition.equals("EE")) {
                    config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.EnterpriseConfiguration");
                } else {
                    error("configuration", "edition", edition, "HE|PE|EE");
                    config = new Configuration();
                }
                config.getDynamicLoader().setClassLoader(classLoader);
            }
            if (level == 1) {
                section = localName;
                if ("global".equals(localName)) {
                    readGlobalElement(atts);
                } else if ("serialization".equals(localName)) {
                    readSerializationElement(atts, config.getNameChecker());
                } else if ("xquery".equals(localName)) {
                    readXQueryElement(atts);
                } else if ("xslt".equals(localName)) {
                    readXsltElement(atts);
                } else if ("xsd".equals(localName)) {
                    readXsdElement(atts);
                } else if ("resources".equals(localName)) {
                    // no action until later
                } else if ("collations".equals(localName)) {
                    // no action until later
                } else if ("localizations".equals(localName)) {
                    readLocalizationsElement(atts);
                } else {
                    error(localName, null, null, null);
            }
            } else if (level==2) {
                if ("resources".equals(section)) {
                    // no action until endElement()
                } else if ("collations".equals(section)) {
                    if (!"collation".equals(localName)) {
                        error(localName, null, null, "collation");
                    } else {
                        readCollation(atts);
                    }
                } else if ("localizations".equals(section)) {
                    if (!"localization".equals(localName)) {
                        error(localName, null, null, "localization");
                    } else {
                        readLocalization(atts);
                    }
                } else if ("xslt".equals(section)) {
                    if (!"extensionElement".equals(localName)) {
                        error(localName, null, null, "extensionElement");
                    } else {
                        readExtensionElement(atts);
                    }
                }
            }
        } else {
            errors.add(new XPathException("Configuration elements must be in namespace " + NamespaceConstant.SAXON_CONFIGURATION));
        }
        level++;
        namespaceStack.push(new ArrayList<String[]>());
    }

    private void readGlobalElement(Attributes atts) {
        Properties props = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            String name=atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).length() == 0) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "allowExternalFunctions", FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS);
        applyProperty(props, "allowMultiThreading", FeatureKeys.ALLOW_MULTITHREADING);
        applyProperty(props, "allowOldJavaUriFormat", FeatureKeys.ALLOW_OLD_JAVA_URI_FORMAT);
        applyProperty(props, "allowStreamabilityExtensions", FeatureKeys.ALLOW_STREAMABILITY_EXTENSIONS);
        applyProperty(props, "collationUriResolver", FeatureKeys.COLLATION_URI_RESOLVER_CLASS);
        applyProperty(props, "collectionUriResolver", FeatureKeys.COLLECTION_URI_RESOLVER_CLASS);
        applyProperty(props, "compileWithTracing", FeatureKeys.COMPILE_WITH_TRACING);
        applyProperty(props, "debugByteCode", FeatureKeys.DEBUG_BYTE_CODE);
        applyProperty(props, "debugByteCodeDirectory", FeatureKeys.DEBUG_BYTE_CODE_DIR);
        applyProperty(props, "defaultCollation", FeatureKeys.DEFAULT_COLLATION);
        applyProperty(props, "defaultCollection", FeatureKeys.DEFAULT_COLLECTION);
        applyProperty(props, "displayByteCode", FeatureKeys.DISPLAY_BYTE_CODE);
        applyProperty(props, "dtdValidation", FeatureKeys.DTD_VALIDATION);
        applyProperty(props, "dtdValidationRecoverable", FeatureKeys.DTD_VALIDATION_RECOVERABLE);
        applyProperty(props, "entityResolver", FeatureKeys.ENTITY_RESOLVER_CLASS);
        applyProperty(props, "errorListener", FeatureKeys.ERROR_LISTENER_CLASS);
        applyProperty(props, "environmentVariableResolver", FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER_CLASS);
        applyProperty(props, "expandAttributeDefaults", FeatureKeys.EXPAND_ATTRIBUTE_DEFAULTS);
        applyProperty(props, "generateByteCode", FeatureKeys.GENERATE_BYTE_CODE);
        applyProperty(props, "ignoreSAXSourceParser", FeatureKeys.IGNORE_SAX_SOURCE_PARSER);
        applyProperty(props, "lazyConstructionMode", FeatureKeys.LAZY_CONSTRUCTION_MODE);
        applyProperty(props, "lineNumbering", FeatureKeys.LINE_NUMBERING);
        applyProperty(props, "optimizationLevel", FeatureKeys.OPTIMIZATION_LEVEL);
        applyProperty(props, "parser", FeatureKeys.SOURCE_PARSER_CLASS);
        applyProperty(props, "preEvaluateDoc", FeatureKeys.PRE_EVALUATE_DOC_FUNCTION);
        applyProperty(props, "preferJaxpParser", FeatureKeys.PREFER_JAXP_PARSER);
        applyProperty(props, "recognizeUriQueryParameters", FeatureKeys.RECOGNIZE_URI_QUERY_PARAMETERS);
        applyProperty(props, "schemaValidation", FeatureKeys.SCHEMA_VALIDATION_MODE);
        applyProperty(props, "serializerFactory", FeatureKeys.SERIALIZER_FACTORY_CLASS);
        applyProperty(props, "sourceResolver", FeatureKeys.SOURCE_RESOLVER_CLASS);
        applyProperty(props, "standardErrorOutputFile", FeatureKeys.STANDARD_ERROR_OUTPUT_FILE);
        applyProperty(props, "stripSpace", FeatureKeys.STRIP_WHITESPACE);
        applyProperty(props, "styleParser", FeatureKeys.STYLE_PARSER_CLASS);
        applyProperty(props, "suppressEvaluationExpiryWarning", FeatureKeys.SUPPRESS_EVALUATION_EXPIRY_WARNING);
        applyProperty(props, "timing", FeatureKeys.TIMING);
        applyProperty(props, "traceExternalFunctions", FeatureKeys.TRACE_EXTERNAL_FUNCTIONS);
        applyProperty(props, "traceListener", FeatureKeys.TRACE_LISTENER_CLASS);
        applyProperty(props, "traceOptimizerDecisions", FeatureKeys.TRACE_OPTIMIZER_DECISIONS);
        applyProperty(props, "treeModel", FeatureKeys.TREE_MODEL_NAME);
        applyProperty(props, "uriResolver", FeatureKeys.URI_RESOLVER_CLASS);
        applyProperty(props, "usePiDisableOutputEscaping", FeatureKeys.USE_PI_DISABLE_OUTPUT_ESCAPING);
        applyProperty(props, "useTypedValueCache", FeatureKeys.USE_TYPED_VALUE_CACHE);
        applyProperty(props, "validationComments", FeatureKeys.VALIDATION_COMMENTS);
        applyProperty(props, "validationWarnings", FeatureKeys.VALIDATION_WARNINGS);
        applyProperty(props, "versionOfXml", FeatureKeys.XML_VERSION);
        applyProperty(props, "xInclude", FeatureKeys.XINCLUDE);
    }

    private void applyProperty(Properties props, String propertyName, String featureKey) {
        String value = props.getProperty(propertyName);
        if (value != null) {
            try {
                config.setConfigurationProperty(featureKey, value);
            } catch (IllegalArgumentException e) {
                error(props.getProperty("#element"), propertyName, value, e.getMessage());
            }
        }
    }

    private void readSerializationElement(Attributes atts, NameChecker checker) {
        Properties props = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            String uri=atts.getURI(i);
            String name=atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() == 0) {
                continue;
            }
            try {
                ResultDocument.setSerializationProperty(props,
                    uri, name, value, this, false, config);
            } catch (XPathException e) {
                errors.add(e);
            }
        }
        config.setDefaultSerializationProperties(props);
    }

    private void readCollation(Attributes atts) {
        Properties props = new Properties();
        String collationUri = null;
        for (int i=0; i<atts.getLength(); i++) {
            if (atts.getURI(i).length() == 0) {
                String name=atts.getLocalName(i);
                String value = atts.getValue(i);
                if (value.length() == 0) {
                    continue;
                }
                if ("uri".equals(name)) {
                    collationUri = value;
                } else {
                    props.put(name, value);
                }
            }
        }
        if (collationUri == null) {
            errors.add(new XPathException("collation specified with no uri"));
        }
        StringCollator collator = null;
        try {
            collator = Configuration.getPlatform().makeCollation(config, props, collationUri);
        } catch (XPathException e) {
            errors.add(e);
        }
        config.getCollationMap().setNamedCollation(collationUri, collator);

    }

    private void readLocalizationsElement(Attributes atts) {
        for (int i=0; i<atts.getLength(); i++) {
            if (atts.getURI(i).length() == 0) {
                String name=atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("defaultLanguage".equals(name) && value.length() > 0) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, value);
                }
                if ("defaultCountry".equals(name) && value.length() > 0) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_COUNTRY, value);
                }
            }
        }
    }

    private void readLocalization(Attributes atts) {
        String lang = null;
        Properties properties = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            if (atts.getURI(i).length() == 0) {
                String name=atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("lang".equals(name) && value.length() > 0) {
                    lang = value;
                } else if (value.length() > 0) {
                    properties.setProperty(name, value);
                }
            }
        }
        if (lang != null) {
            LocalizerFactory factory = config.getLocalizerFactory();
            if (factory != null) {
                factory.setLanguageProperties(lang, properties);
            }
        }
    }

    /**
     * Process details of XSLT extension elements. Overridden in Saxon-PE configuration reader
     * @param atts the attributes of the extensionElement element in the configuration file
     */

    protected void readExtensionElement(Attributes atts) {
        XPathException err = new XPathException("Extension elements are not available in Saxon-HE");
        err.setLocator(ExpressionLocation.makeFromSax(locator));
        errors.add(err);
    }

    private void readXQueryElement(Attributes atts) {
        Properties props = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            String name=atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).length() == 0) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "allowUpdate", FeatureKeys.XQUERY_ALLOW_UPDATE);
        applyProperty(props, "constructionMode", FeatureKeys.XQUERY_CONSTRUCTION_MODE);
        applyProperty(props, "defaultElementNamespace", FeatureKeys.XQUERY_DEFAULT_ELEMENT_NAMESPACE);
        applyProperty(props, "defaultFunctionNamespace", FeatureKeys.XQUERY_DEFAULT_FUNCTION_NAMESPACE);
        applyProperty(props, "emptyLeast", FeatureKeys.XQUERY_EMPTY_LEAST);
        applyProperty(props, "inheritNamespaces", FeatureKeys.XQUERY_INHERIT_NAMESPACES);
        applyProperty(props, "moduleUriResolver", FeatureKeys.MODULE_URI_RESOLVER_CLASS);
        applyProperty(props, "preserveBoundarySpace", FeatureKeys.XQUERY_PRESERVE_BOUNDARY_SPACE);
        applyProperty(props, "preserveNamespaces", FeatureKeys.XQUERY_PRESERVE_NAMESPACES);
        applyProperty(props, "requiredContextItemType", FeatureKeys.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE);
        applyProperty(props, "schemaAware", FeatureKeys.XQUERY_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", FeatureKeys.XQUERY_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "version", FeatureKeys.XQUERY_VERSION);
    }

    private void readXsltElement(Attributes atts) {
        Properties props = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            String name=atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).length() == 0) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "initialMode", FeatureKeys.XSLT_INITIAL_MODE);
        applyProperty(props, "initialTemplate", FeatureKeys.XSLT_INITIAL_TEMPLATE);
        applyProperty(props, "messageEmitter", FeatureKeys.MESSAGE_EMITTER_CLASS);
        applyProperty(props, "outputUriResolver", FeatureKeys.OUTPUT_URI_RESOLVER_CLASS);
        applyProperty(props, "recoveryPolicy", FeatureKeys.RECOVERY_POLICY_NAME);
        applyProperty(props, "schemaAware", FeatureKeys.XSLT_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", FeatureKeys.XSLT_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "staticUriResolver", FeatureKeys.XSLT_STATIC_URI_RESOLVER_CLASS);
        applyProperty(props, "styleParser", FeatureKeys.STYLE_PARSER_CLASS);
        applyProperty(props, "version", FeatureKeys.XSLT_VERSION);
        applyProperty(props, "versionWarning", FeatureKeys.VERSION_WARNING);
    }

    private void readXsdElement(Attributes atts) {
        Properties props = new Properties();
        for (int i=0; i<atts.getLength(); i++) {
            String name=atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).length() == 0) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "assertionsCanSeeComments", FeatureKeys.ASSERTIONS_CAN_SEE_COMMENTS);
        applyProperty(props, "multipleSchemaImports", FeatureKeys.MULTIPLE_SCHEMA_IMPORTS);
        applyProperty(props, "occurrenceLimits", FeatureKeys.OCCURRENCE_LIMITS);
        applyProperty(props, "schemaUriResolver", FeatureKeys.SCHEMA_URI_RESOLVER_CLASS);
        applyProperty(props, "useXsiSchemaLocation", FeatureKeys.USE_XSI_SCHEMA_LOCATION);
        applyProperty(props, "version", FeatureKeys.XSD_VERSION);
    }

    private void error(String element, String attribute, String actual, String required) {
        XPathException err;
        if (attribute == null) {
            err = new XPathException("Invalid configuration element " + element);
        } else {
            err = new XPathException("Invalid configuration property " +
                element + "/@" + attribute + ". Supplied value '" + actual + "'. " + required);
        }
        err.setLocator(ExpressionLocation.makeFromSax(locator));
        errors.add(err);
    }

    protected void errorClass(String element, String attribute, String actual, Class required, Exception cause) {
        XPathException err = new XPathException("Invalid configuration property " +
                element + (attribute == null ? "" : "/@" + attribute) +
                ". Supplied value '" + actual +
                "', required value is the name of a class that implements '" + required.getName() + "'", cause);
        err.setLocator(ExpressionLocation.makeFromSax(locator));
        errors.add(err);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (level == 3 && "resources".equals(section)) {
            String content = buffer.toString();
            if (content.length() != 0) {
                if ("externalObjectModel".equals(localName)) {
                    try {
                        ExternalObjectModel model = (ExternalObjectModel)config.getInstance(content, null);
                        config.registerExternalObjectModel(model);
                    } catch (XPathException e) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, e);
                    } catch (ClassCastException cce) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, cce);
                    }
                } else if ("extensionFunction".equals(localName)) {
                    try {
                        ExtensionFunctionDefinition model = (ExtensionFunctionDefinition)config.getInstance(content, null);
                        config.registerExtensionFunction(model);
                    } catch (XPathException e) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, e);
                    } catch (ClassCastException cce) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, cce);
                    } catch (IllegalArgumentException iae) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, iae);
                    }
                } else if ("schemaDocument".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.addSchemaSource(source);
                    } catch (SchemaException e) {
                        errors.add(XPathException.makeXPathException(e));
                    } catch (XPathException e) {
                        errors.add(e);
                    }
                } else if ("schemaComponentModel".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.importComponents(source);
                    } catch (XPathException e) {
                        errors.add(e);
                    }
                } else {
                    error(localName, null, null, null);
                }
            }
        }
        level--;
        buffer.setLength(0);
        namespaceStack.pop();
    }

    private Source getInputSource(String href) throws XPathException {
        try {
            String base = locator.getSystemId();
            URI abs = ResolveURI.makeAbsolute(href, base);
            return new StreamSource(abs.toString());
        } catch (URISyntaxException e) {
            throw new XPathException(e);
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        buffer.append(ch, start, length);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {

    }

    public void processingInstruction(String target, String data) throws SAXException {

    }

    public void skippedEntity(String name) throws SAXException {

    }

    /////////////////////////////////////////////////////
    // Implement NamespaceResolver
    /////////////////////////////////////////////////////

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         The "null namespace" is represented by the pseudo-URI "".
     */

    public String getURIForPrefix(String prefix, boolean useDefault) {
        for (int i=namespaceStack.size()-1; i>=0; i--) {
            List<String[]> list = namespaceStack.get(i);
            for (String[] pair : list) {
                if (pair[0].equals(prefix)) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    /*@Nullable*/ public Iterator<String> iteratePrefixes() {
        // Not used
        return null;
    }
}

