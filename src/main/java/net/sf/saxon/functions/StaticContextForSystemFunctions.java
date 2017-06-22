////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.LocationMap;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;

/**
 * An implementation of the StaticContext interface, holding those parts of the
 * static context that can be used by system functions. This is especially relevant
 * when system functions are executed dynamically via function-lookup().
*/

public class StaticContextForSystemFunctions  {

    private String baseURI = null;
    private Executable executable;
    private String defaultElementNamespace = NamespaceConstant.NULL;
    private DecimalFormatManager decimalFormatManager = null;

    public StaticContextForSystemFunctions(Executable exec) {
        this.executable = exec;
    }

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        return executable.getConfiguration();
    }

    /**
     * Get the collation map in use
     * @return the collation map
     */

    public CollationMap getCollationMap() {
        return executable.getCollationTable();
    }

    /**
     * Ask whether this static context is schema-aware
     * @return true if this context is schema-aware
     */

    public boolean isSchemaAware() {
        return executable.isSchemaAware();
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration(), getCollationMap());
    }


    public LocationMap getLocationMap() {
        return null;
    }

    /**
     * Set the base URI in the static context
     * @param baseURI the base URI of the expression
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
        return null;
    }

    /**
    * Get a named collation.
    * @return the collation identified by the given name, as set previously using declareCollation.
    * Return null if no collation with this name is found.
    */

    public StringCollator getCollation(String name) {
        return getCollationMap().getNamedCollation(name);
    }

    /**
    * Get the name of the default collation.
    * @return the name of the default collation; or the name of the codepoint collation
    * if no default collation has been defined
    */

    public String getDefaultCollationName() {
        return getCollationMap().getDefaultCollationName();
    }

    /**
    * Get the NamePool used for compiling expressions
    */

    public NamePool getNamePool() {
        return getConfiguration().getNamePool();
    }

    /**
     * Issue a compile-time warning. This method is used during XPath expression compilation to
     * output warning conditions. The default implementation writes the message to the
     * error listener registered with the Configuration.
    */

    public void issueWarning(String s, SourceLocator locator) {
        try {
            getConfiguration().getErrorListener().warning(new XPathException(s));
        } catch (TransformerException e) {
            getConfiguration().getStandardErrorOutput().println("Warning: " + s);
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
     * Get the default function namespace.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     * @return the default namesapce for functions
     */

    public String getDefaultFunctionNamespace() {
        return null;
    }



    /**
     * Get the XPath language level supported, as a string.
     * The current levels supported are "2.0", and "3.0". The default is "2.0".
     * If running XQuery 1.0, the value is "2.0"; if running XQuery 3.0, it is "3.0".
     * @return the XPath language level
     * @since 9.3
     */

    public DecimalValue getXPathLanguageLevel() {
        return DecimalValue.TWO;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     * @return true if XPath 1.0 compatibility mode is to be set to true;
     * otherwise false
     */

    public boolean isInBackwardsCompatibleMode() {
        return false;
    }

    /**
     * Determine whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     *
     * @param type the supposedly built-in type. This will always be a type in the XS namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(BuiltInAtomicType type) {
        return true;
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

}

