////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.LocationMap;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.IntegratedFunctionLibrary;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.SourceLocator;
import java.util.Set;

/**
* An ExpressionContext represents the context for an XPath expression written
* in the stylesheet.
*/

public class ExpressionContext implements XSLTStaticContext {

	private StyleElement element;
	private NamePool namePool;
    /*@Nullable*/ private NamespaceResolver namespaceResolver = null;

    /**
     * Create a static context for XPath expressions in an XSLT stylesheet
     * @param styleElement the element on which the XPath expression appears
     */

    public ExpressionContext(StyleElement styleElement) {
		element = styleElement;
		namePool = styleElement.getNamePool();
	}

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        return element.getConfiguration();
    }

    /**
     * Get the executable
     * @return the executable
     */

    public Executable getExecutable() {
        return element.getPreparedStylesheet();
    }

    /**
     * Ask whether expressions compiled under this static context are schema-aware.
     * They must be schema-aware if the expression is to handle typed (validated) nodes
     *
     * @return true if expressions are schema-aware
     */
    public boolean isSchemaAware() {
        return getExecutable().isSchemaAware();
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration(),
                element.getPrincipalStylesheetModule().getCollationMap());
    }


    /**
     * Get the location map
     */

    public LocationMap getLocationMap() {
        return getExecutable().getLocationMap();
    }

    /**
    * Issue a compile-time warning
    */

    public void issueWarning(String s, SourceLocator locator) {
        element.issueWarning(s, locator);
    }

    /**
    * Get the NamePool used for compiling expressions
    */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
    * Get the System ID of the entity containing the expression (used for diagnostics)
    */

    public String getSystemId() {
    	return element.getSystemId();
    }

    /**
    * Get the line number of the expression within its containing entity
    * Returns -1 if no line number is available
    */

    public int getLineNumber() {
    	return element.getLineNumber();
    }

    /**
    * Get the Base URI of the element containing the expression, for resolving any
    * relative URI's used in the expression.
    * Used by the document() function.
    */

    public String getBaseURI() {
        return element.getBaseURI();
    }

    /**
    * Get the URI for a prefix, using this Element as the context for namespace resolution.
    * The default namespace will not be used when the prefix is empty.
    * @param prefix The prefix
    * @throws XPathException if the prefix is not declared
    */

    public String getURIForPrefix(String prefix) throws XPathException {
        String uri = element.getURIForPrefix(prefix, false);
        if (uri == null) {
            XPathException err = new XPathException("Undeclared namespace prefix " + Err.wrap(prefix));
            err.setErrorCode("XPST0081");
            err.setIsStaticError(true);
            throw err;
        }
        return uri;
    }

    /**
     * Get a copy of the NamespaceResolver suitable for saving in the executable code
     * @return a NamespaceResolver
    */


    public NamespaceResolver getNamespaceResolver() {
        if (namespaceResolver == null) {
            namespaceResolver = element.makeNamespaceContext();
        }
        return namespaceResolver;
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
     * @return the decimal format manager for this static context, or null if named decimal
     *         formats are not supported in this environment.
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return element.getPreparedStylesheet().getDecimalFormatManager();
    }

    /**
    * Get a fingerprint for a name, using this as the context for namespace resolution
    * @param qname The name as written, in the form "[prefix:]localname"
    * @param useDefault Defines the action when there is no prefix. If true, use
    * the default namespace URI (as for element names). If false, use no namespace URI
    * (as for attribute names).
    * @return -1 if the name is not already present in the name pool
    */

    public int getFingerprint(String qname, boolean useDefault) throws XPathException {

        String[] parts;
        try {
            parts = getConfiguration().getNameChecker().getQNameParts(qname);
        } catch (QNameException err) {
            throw new XPathException(err.getMessage());
        }
        String prefix = parts[0];
        if (prefix.length() == 0) {
            String uri = "";

            if (useDefault) {
                uri = getURIForPrefix(prefix);
            }

			return namePool.getFingerprint(uri, qname);

        } else {

            String uri = getURIForPrefix(prefix);
			return namePool.getFingerprint(uri, parts[1]);
        }
    }

    /**
     * Bind a variable to an object that can be used to refer to it
     * @param qName the name of the variable
     * @return a VariableDeclaration object that can be used to identify it in the Bindery,
     * @throws XPathException if the variable has not been declared
    */

    public Expression bindVariable(StructuredQName qName) throws XPathException {
        if (element.getLocalPart().equals("accumulator-rule") && qName.getClarkName().equals("value")) {
            // XSLT 3.0
            return new SuppliedParameterReference(0);
        }
        SourceBinding xslVariableDeclaration = element.bindVariable(qName);
        if (xslVariableDeclaration == null) {
            // it might have been declared in an imported query
            GlobalVariable var = getExecutable().getGlobalVariable(qName);
            if (var != null) {
                return new VariableReference(var);
            }
            // it might be an implicit error variable in try/catch
            if (getXPathLanguageLevel().equals(DecimalValue.THREE) && NamespaceConstant.ERR.equals(qName.getURI())) {
                AxisIterator catchers = element.iterateAxis(AxisInfo.ANCESTOR_OR_SELF,
                        new NameTest(Type.ELEMENT, StandardNames.XSL_CATCH, getNamePool()));
                StyleElement catcher = (StyleElement)catchers.next();
                if (catcher != null) {
                    for (StructuredQName errorVariable : StandardNames.errorVariables) {
                        if (errorVariable.getLocalPart().equals(qName.getLocalPart())) {
                            IntegratedFunctionLibrary lib = getConfiguration().getVendorFunctionLibrary();
                            StructuredQName functionName =
                                    new StructuredQName("saxon", NamespaceConstant.SAXON, "dynamic-error-info");
                            Expression[] args = new Expression[]{new StringLiteral(qName.getLocalPart())};
                            return lib.bind(functionName, 1, args, this, element);
                        }
                    }
                }
            }
            XPathException err = new XPathException("Variable " + qName.getDisplayName() +
                    " has not been declared (or its declaration is not in scope)");
            err.setErrorCode("XPST0008");
            err.setIsStaticError(true);
            throw err;
        }

        VariableReference var;
        if (xslVariableDeclaration.hasProperty(SourceBinding.GLOBAL)) {
            var = new VariableReference();
        } else if (xslVariableDeclaration.hasProperty(SourceBinding.GROUP)) {
            var = new GroupVariableReference();
        } else {
            var = new LocalVariableReference();
        }
        xslVariableDeclaration.registerReference(var);
        return var;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     */

    public FunctionLibrary getFunctionLibrary() {
        return element.getPrincipalStylesheetModule().getFunctionLibrary();
    }

    /**
    * Determine if an extension element is available
    * @throws XPathException if the name is invalid or the prefix is not declared
    */

    public boolean isElementAvailable(String qname) throws XPathException {
        try {
            String[] parts = getConfiguration().getNameChecker().getQNameParts(qname);
            String uri;
            if (parts[0].length() == 0) {
                uri = getDefaultElementNamespace();
            } else {
                uri = getURIForPrefix(parts[0]);
            }
            return element.getPreparedStylesheet().getStyleNodeFactory().isElementAvailable(uri, parts[1]);
        } catch (QNameException e) {
            XPathException err = new XPathException("Invalid element name. " + e.getMessage());
            err.setErrorCode("XTDE1440");
            throw err;
        }
    }

    /**
    * Get a named collation.
    * @param name The name of the required collation. Supply null to get the default collation.
    * @return the collation; or null if the required collation is not found.
    */

    public StringCollator getCollation(String name) {
        if (name == null) {
            return getCollation(getDefaultCollationName());
        } else {
            return element.getPrincipalStylesheetModule().findCollation(name, element.getBaseURI());
        }
    }

    /**
    * Get the default collation. Return null if no default collation has been defined
    */

    public String getDefaultCollationName() {
        return element.getDefaultCollationName();
    }

    /**
     * Get the default XPath namespace for elements and types
     * Return NamespaceConstant.NULL for the non-namespace
    */

    public String getDefaultElementNamespace() {
        return element.getDefaultXPathNamespace();
    }

    /**
     * Get the default function namespace
     */

    public String getDefaultFunctionNamespace() {
        return NamespaceConstant.FN;
    }

    /**
    * Determine whether Backwards Compatible Mode is used
    */

    public boolean isInBackwardsCompatibleMode() {
        return element.xPath10ModeIsEnabled();
    }

    /**
     * Get the XPath language level supported, as a string.
     * The current levels supported are 2.0, and 3.0. The XPath language level will be the same as the
     * XSLT processor version
     * @return the XPath language level; the return value will be either
     * {@link net.sf.saxon.value.DecimalValue#TWO} or {@link net.sf.saxon.value.DecimalValue#THREE}
     * @since 9.3
     */

    public DecimalValue getXPathLanguageLevel() {
        return element.getPreparedStylesheet().getStyleNodeFactory().getXsltProcessorVersion();
    }

    /**
     * Test whether a schema has been imported for a given namespace
     * @param namespace the target namespace of the required schema
     * @return true if a schema for this namespace has been imported
     */

    public boolean isImportedSchema(String namespace) {
        return element.getPrincipalStylesheetModule().isImportedSchema(namespace);
    }

    /**
     * Get the set of imported schemas
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    public Set<String> getImportedSchemaNamespaces() {
        return element.getPrincipalStylesheetModule().getImportedSchemaTable();
    }

    /**
     * Determine whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     *
     * @param type the supposedly built-in type. This will always be a type in the
     *                    XS or XDT namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(BuiltInAtomicType type) {
        Configuration config = getConfiguration();
        if (type.getFingerprint() == StandardNames.XS_DATE_TIME_STAMP) {
            return config.getXsdVersion() == Configuration.XSD11;
        }
        return type.isAllowedInBasicXSLT() ||
                config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION);
    }

    /**
     * Get the stylesheet element containing this XPath expression
     * @return the element in the tree representation of the source stylesheet
     */

    public StyleElement getStyleElement() {
        return element;
    }
}

