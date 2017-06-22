////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.LocationMap;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.util.List;

/**
 * An abstract and configurable implementation of the StaticContext interface,
 * which defines the static context of an XPath expression.
 *
 * <p>This class implements those parts of the functionality of a static context
 * that tend to be common to most implementations: simple-valued properties such
 * as base URI and default element namespace; availability of the standard
 * function library; and support for collations.</p>
*/

public abstract class AbstractStaticContext implements StaticContext {

    private String baseURI = null;
    private Configuration config;
    private LocationMap locationMap = new LocationMap();
    private CollationMap collationMap;
    private FunctionLibraryList libraryList = new FunctionLibraryList();
    private String defaultFunctionNamespace = NamespaceConstant.FN;
    private String defaultElementNamespace = NamespaceConstant.NULL;
    private DecimalFormatManager decimalFormatManager = null;
    private boolean backwardsCompatible = false;
    private DecimalValue xpathLanguageLevel = DecimalValue.TWO;
    private boolean schemaAware = false;
    protected boolean usingDefaultFunctionLibrary;

    /**
     * Set the Configuration. This is protected so it can be used only by subclasses;
     * the configuration will normally be set at construction time
     * @param config the configuration
     */

    protected void setConfiguration(Configuration config) {
        this.config = config;
        collationMap = new CollationMap(config.getCollationMap());
    }

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set a collation map to override use of the default collation map from the configuration
     * @param collationMap the collation map to be used
     */

    public void setCollationMap(CollationMap collationMap) {
        this.collationMap = collationMap;
    }

    /**
     * Get the collation map in use
     * @return the collation map
     */

    public CollationMap getCollationMap() {
        return collationMap;
    }

    /**
     * Say whether this static context is schema-aware
     * @param aware true if this static context is schema-aware
     */

    public void setSchemaAware(boolean aware) {
        schemaAware = aware;
    }

    /**
     * Ask whether this static context is schema-aware
     * @return true if this context is schema-aware
     */

    public boolean isSchemaAware() {
        return schemaAware;
    }

    /**
     * Initialize the default function library for XPath.
     * This can be overridden using setFunctionLibrary().
     */

    protected final void setDefaultFunctionLibrary() {
        FunctionLibraryList lib = new FunctionLibraryList();
        int features = (getXPathLanguageLevel().equals(DecimalValue.THREE) ?
                (StandardFunction.CORE | StandardFunction.XPATH30) :
                (StandardFunction.CORE));
        lib.addFunctionLibrary(
                SystemFunctionLibrary.getSystemFunctionLibrary(features));
        lib.addFunctionLibrary(getConfiguration().getVendorFunctionLibrary());
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(getConfiguration()));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        setFunctionLibrary(lib);
    }

    /**
     * Add a function library to the list of function libraries
     * @param library the function library to be added
     */

    protected final void addFunctionLibrary(FunctionLibrary library) {
        //FunctionLibrary libraryList = executable.getFunctionLibrary();
        //if (libraryList instanceof FunctionLibraryList) {
            ((FunctionLibraryList)libraryList).addFunctionLibrary(library);
        //} else {
        //    throw new IllegalStateException("Registered function library cannot be extended");
        //}
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return the value {@link net.sf.saxon.Configuration#XPATH}
     */

    public int getHostLanguage() {
        return Configuration.XPATH;
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration(), collationMap);
    }


    public LocationMap getLocationMap() {
        return locationMap;
    }

    /**
     * Set the location map, which is used for translating location identifiers into URIs and line
     * numbers
     * @param locationMap the location map to be used
     */

    public void setLocationMap(LocationMap locationMap) {
        this.locationMap = locationMap;
    }

    /**
     * Set the base URI in the static context
     * @param baseURI the base URI of the expression; the value null is allowed to indicate that the base URI is not available.
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    /**
    * Get the Base URI, for resolving any relative URI's used
    * in the expression. Used by the document() function, resolve-uri(), etc.
    * @return "" if no base URI has been set
    */

    public String getBaseURI() {
        return baseURI==null ? "" : baseURI;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context. This method is called by the XPath parser when binding a function call in the
     * XPath expression to an implementation of the function.
     */

    public FunctionLibrary getFunctionLibrary() {
        return libraryList;
    }

    /**
     * Set the function library to be used
     * @param lib the function library
     */

    public void setFunctionLibrary(FunctionLibraryList lib) {
        libraryList = lib;
        usingDefaultFunctionLibrary = false;
    }

    /**
    * Declare a named collation
    * @param name The name of the collation (technically, a URI)
    * @param comparator The StringCollator used to implement the collating sequence
    * @param isDefault True if this is to be used as the default collation
    */

    public void declareCollation(String name, StringCollator comparator, boolean isDefault) {
        CollationMap collations = collationMap;
        collations.setNamedCollation(name, comparator);
        if (isDefault) {
            collations.setDefaultCollationName(name);
        }
    }


    /**
    * Get a named collation.
    * @return the collation identified by the given name, as set previously using declareCollation.
    * Return null if no collation with this name is found.
    */

    public StringCollator getCollation(String name) {
        return collationMap.getNamedCollation(name);
    }

    /**
    * Get the name of the default collation.
    * @return the name of the default collation; or the name of the codepoint collation
    * if no default collation has been defined
    */

    public String getDefaultCollationName() {
        return collationMap.getDefaultCollationName();
    }

    /**
    * Get the NamePool used for compiling expressions
    */

    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Issue a compile-time warning. This method is used during XPath expression compilation to
     * output warning conditions. The default implementation writes the message to the
     * error listener registered with the Configuration.
    */

    public void issueWarning(String s, SourceLocator locator) {
        try {
            config.getErrorListener().warning(new XPathException(s));
        } catch (TransformerException e) {
            config.getStandardErrorOutput().println("Warning: " + s);
        }
    }

    /**
    * Get the system ID of the container of the expression. Used to construct error messages.
    * @return "" always
    */

    public String getSystemId() {
        return "";
    }


    /**
    * Get the line number of the expression within that container.
    * Used to construct error messages.
    * @return -1 always
    */

    public int getLineNumber() {
        return -1;
    }


    /**
     * Get the default namespace URI for elements and types
     * Return NamespaceConstant.NULL (that is, the zero-length string) for the non-namespace
     * @return the default namespace for elements and type
    */

    public String getDefaultElementNamespace() {
        return defaultElementNamespace;
    }

    /**
     * Set the default namespace for elements and types
     * @param uri the namespace to be used for unprefixed element and type names.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultElementNamespace(String uri) {
        defaultElementNamespace = uri;
    }

    /**
     * Set the default function namespace
     * @param uri the namespace to be used for unprefixed function names.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultFunctionNamespace(String uri) {
        defaultFunctionNamespace = uri;
    }

    /**
     * Get the default function namespace.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     * @return the default namesapce for functions
     */

    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the XPath language level supported, as a string.
     * The current levels supported are 2.0, and 3.0. The default is 2.0.
     * If running XQuery 1.0, the value is "2.0"; if running XQuery 3.0, it is 3.0.
     * @param level the XPath language level
     * @since 9.3
     */

    public void setXPathLanguageLevel(DecimalValue level) {
        xpathLanguageLevel = level;
        if (usingDefaultFunctionLibrary) {
            FunctionLibraryList liblist = (FunctionLibraryList)getFunctionLibrary();
            List<FunctionLibrary> list = liblist.getLibraryList();
            for (int i=0; i<list.size(); i++) {
                if (list.get(i) instanceof SystemFunctionLibrary) {
                    int features = (getXPathLanguageLevel().equals(DecimalValue.THREE) ?
                        (StandardFunction.CORE | StandardFunction.XPATH30) :
                        (StandardFunction.CORE));
                    list.set(i, SystemFunctionLibrary.getSystemFunctionLibrary(features));
                    break;
                }
            }
        }
    }

    /**
     * Get the XPath language level supported, as a string.
     * The current levels supported are "2.0", and "3.0". The default is "2.0".
     * If running XQuery 1.0, the value is "2.0"; if running XQuery 3.0, it is "3.0".
     * @return the XPath language level
     * @since 9.3
     */

    public DecimalValue getXPathLanguageLevel() {
        return xpathLanguageLevel;
    }

    /**
     * Set XPath 1.0 backwards compatibility mode on or off
     * @param option true if XPath 1.0 compatibility mode is to be set to true;
     * otherwise false
     */

    public void setBackwardsCompatibilityMode(boolean option) {
        backwardsCompatible = option;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     * @return true if XPath 1.0 compatibility mode is to be set to true;
     * otherwise false
     */

    public boolean isInBackwardsCompatibleMode() {
        return backwardsCompatible;
    }

    /**
     * Determine whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     *
     * @param type the supposedly built-in type. This will always be a type in the XS namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(BuiltInAtomicType type) {
        return type.getFingerprint() != StandardNames.XS_DATE_TIME_STAMP ||
                config.getXsdVersion() == Configuration.XSD11;
    }

    /**
     * Set the DecimalFormatManager used to resolve the names of decimal formats used in calls
     * to the format-number() function.
     * @param manager the decimal format manager for this static context, or null if no named decimal
     *         formats are available in this environment.
     */

    public void setDecimalFormatManager(DecimalFormatManager manager) {
        this.decimalFormatManager = manager;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     * @return the required type of the context item
     * @since 9.3
     */

    public ItemType getRequiredContextItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     * @return the decimal format manager for this static context; a newly created empty
     * DecimalFormatManager if none has been supplied
     * @since 9.2
     */

    public DecimalFormatManager getDecimalFormatManager() {
        if (decimalFormatManager == null) {
            decimalFormatManager = new DecimalFormatManager();
        }
        return decimalFormatManager;
    }

    /**
     * Get the LocationProvider allowing location identifiers to be resolved.
     * @return the LocationProvider that translates location identifiers into URIs and line numbers
     */

    public LocationProvider getLocationProvider() {
        return locationMap;
    }

    /**
     * Return the public identifier.
     * <p/>
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     *
     * @return null (always).
     * @see #getSystemId
     */
    /*@Nullable*/ public String getPublicId() {
        return null;
    }

    /**
     * Return the character position where the current document event ends.
     * @return -1 (no column number is available).
     * @see #getLineNumber
     */
    public int getColumnNumber() {
        return -1;
    }

}

