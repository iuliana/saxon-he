////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.sxpath.AbstractStaticContext;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This class implements the static context used for evaluating use-when expressions in XSLT 2.0
 * A new instance of this class is created for each use-when expression encountered; there are
 * therefore no issues with reusability. The class provides a Container for the expression as well
 * as the static context information; the Executable contains the single XPath expression only, and
 * is created for the purpose.
 */

public class UseWhenStaticContext extends AbstractStaticContext implements XSLTStaticContext, Container {

    private NamespaceResolver namespaceContext;
    private FunctionLibrary functionLibrary;
    private Executable executable;
    private Map<StructuredQName, GroundedValue> staticVariables;

    /**
     * Create a static context for evaluating use-when expressions
     *
     * @param config           the Saxon configuration
     * @param namespaceContext the namespace context in which the use-when expression appears
     * @param staticVariables the static variables available for use within use-when expressions
     */

    public UseWhenStaticContext(Configuration config, NamespaceResolver namespaceContext, Map<StructuredQName, GroundedValue> staticVariables) {
        setConfiguration(config);
        this.namespaceContext = namespaceContext;

        FunctionLibraryList lib = new FunctionLibraryList();
        lib.addFunctionLibrary(SystemFunctionLibrary.getSystemFunctionLibrary(
                StandardFunction.CORE | StandardFunction.USE_WHEN));
        lib.addFunctionLibrary(getConfiguration().getVendorFunctionLibrary());
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(getConfiguration()));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        functionLibrary = lib;
        executable = new Executable(config);
        this.staticVariables = staticVariables;
    }

    /**
     * Get the Executable representing the containing XSLT stylesheet
     *
     * @return the Executable
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Issue a compile-time warning
     */

    public void issueWarning(String s, SourceLocator locator) {
        XPathException err = new XPathException(s);
        err.setLocator(locator);
        try {
            getConfiguration().getErrorListener().warning(err);
        } catch (TransformerException e) {
            // ignore response
        }
    }

    /**
     * Get the System ID of the container of the expression. This is the containing
     * entity (file) and is therefore useful for diagnostics. Use getBaseURI() to get
     * the base URI, which may be different.
     */

    public String getSystemId() {
        return getBaseURI();
    }

    /**
     * Get the granularity of the container.
     *
     * @return 0 for a temporary container created during parsing; 1 for a container
     *         that operates at the level of an XPath expression; 2 for a container at the level
     *         of a global function or template
     */

    public int getContainerGranularity() {
        return 1;
    }

    /**
     * Get the URI for a namespace prefix. The default namespace is NOT used
     * when the prefix is empty.
     *
     * @param prefix The prefix
     * @throws net.sf.saxon.trans.XPathException
     *          if the prefix is not declared
     */

    public String getURIForPrefix(String prefix) throws XPathException {
        String uri = namespaceContext.getURIForPrefix(prefix, false);
        if (uri == null) {
            XPathException err = new XPathException("Namespace prefix '" + prefix + "' has not been declared");
            err.setErrorCode("XTDE0290");
            throw err;
        }
        return uri;
    }

    /**
     * Bind a variable used in this element to its declaration
     *
     * @param qName the name of the variable
     * @return an expression representing the variable reference, This will often be
     * a {@link net.sf.saxon.expr.VariableReference}, suitably initialized to refer to the corresponding variable declaration,
     * but in general it can be any expression which returns the variable's value when evaluated. In this version of the method,
     * the value of the variable is known statically, so the returned expression is a literal containing the variable's value.
     */

    public Expression bindVariable(StructuredQName qName) throws XPathException {
        if (staticVariables != null) {
            GroundedValue val = staticVariables.get(qName);
            if (val != null) {
                return Literal.makeLiteral(val);
            }
        }
        XPathException err = new XPathException("Variables (other than XSLT 3.0 static variables) cannot be used in a use-when expression");
        err.setErrorCode("XPST0008");
        err.setIsStaticError(true);
        throw err;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     */

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Get a named collation.
     *
     * @param name The name of the required collation. Supply null to get the default collation.
     * @return the collation; or null if the required collation is not found.
     */

//    /*@Nullable*/ public StringCollator getCollation(String name) {
//        return null;
//    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    public String getDefaultCollationName() {
        return NamespaceConstant.CODEPOINT_COLLATION_URI;
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
        return false;
    }

    /**
     * Determine whether a Schema for a given target namespace has been imported. Note that the
     * in-scope element declarations, attribute declarations and schema types are the types registered
     * with the (schema-aware) configuration, provided that their namespace URI is registered
     * in the static context as being an imported schema namespace. (A consequence of this is that
     * within a Configuration, there can only be one schema for any given namespace, including the
     * null namespace).
     */

    public boolean isImportedSchema(String namespace) {
        return false;
    }

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    public Set<String> getImportedSchemaNamespaces() {
        return Collections.emptySet();
    }

    /**
     * Determine whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     *
     * @param type the supposedly built-in type. This will always be a type in the
     *             XS or XDT namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(BuiltInAtomicType type) {
        Configuration config = getConfiguration();
        if (type.getFingerprint() == StandardNames.XS_DATE_TIME_STAMP) {
            return config.getXsdVersion() == Configuration.XSD11;
        }
        return isSchemaAware() || type.isAllowedInBasicXSLT();
    }

    /**
     * Get a namespace resolver to resolve the namespaces declared in this static context.
     *
     * @return a namespace resolver.
     */

    public NamespaceResolver getNamespaceResolver() {
        return namespaceContext;
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context, or null if named decimal
     *         formats are not supported in this environment.
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return null;
    }

    /**
     * Determine if an extension element is available
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the name is invalid or the prefix is not declared
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
            StyleNodeFactory factory = getConfiguration().makeStyleNodeFactory();
            factory.setXsltProcessorVersion(getXPathLanguageLevel());
            return factory.isElementAvailable(uri, parts[1]);
        } catch (QNameException e) {
            XPathException err = new XPathException("Invalid element name. " + e.getMessage());
            err.setErrorCode("XTDE1440");
            throw err;
        }
    }

}

