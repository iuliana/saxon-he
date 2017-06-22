////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.LocationMap;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.SourceLocator;
import java.util.Set;

/**
* A StaticContext contains the information needed while an expression or pattern
* is being parsed. The information is also sometimes needed at run-time.
*/

public interface StaticContext {

    /**
     * Get the system configuration
     * @return the Saxon configuration
     */

    public Configuration getConfiguration();

    /**
     * Ask whether expressions compiled under this static context are schema-aware.
     * They must be schema-aware if the expression is to handle typed (validated) nodes
     * @return true if expressions are schema-aware
     */

    public boolean isSchemaAware();


    /**
     * Construct a dynamic context for early evaluation of constant subexpressions.
     * @return a newly constructed dynamic context
     */

    public XPathContext makeEarlyEvaluationContext();

    /**
     * Get the location map. This is a mapping from short location ids held with each expression or
     * subexpression, to a fully-resolved location in a source stylesheet or query.
     * @return the location map
     */

    public LocationMap getLocationMap();

    /**
     * Issue a compile-time warning.
     * @param message The warning message. This should not contain any prefix such as "Warning".
     * @param locator the location of the construct in question. May be null.
    */

    public void issueWarning(String message, /*@Nullable*/ SourceLocator locator);

    /**
     * Get the System ID of the container of the expression. This is the containing
     * entity (file) and is therefore useful for diagnostics. Use getBaseURI() to get
     * the base URI, which may be different.
     * @return the system ID
     */

    public String getSystemId();

    /**
     * Get the line number of the expression within its containing entity
     * Returns -1 if no line number is available
     * @return the line number, or -1 if not available
    */

    public int getLineNumber();

    /**
     * Get the Base URI of the stylesheet element, for resolving any relative URI's used
     * in the expression.
     * Used by the document(), doc(), resolve-uri(), and base-uri() functions.
     * May return null if the base URI is not known.
     * @return the static base URI, or null if not known
    */

    public String getBaseURI();

    /**
     * Get the URI for a namespace prefix. The default namespace is NOT used
     * when the prefix is empty.
     * @param prefix The namespace prefix.
     * @return the corresponding namespace URI
     * @throws net.sf.saxon.trans.XPathException if the prefix is not declared; the
     * associated error code should be XPST0081
    */

    public String getURIForPrefix(String prefix) throws XPathException;

    /**
     * Get the NamePool used for compiling expressions
     * @return the name pool
     */

    public NamePool getNamePool();

    /**
     * Bind a variable used in this element to the XSLVariable element in which it is declared
     * @param qName The name of the variable
     * @return an expression representing the variable reference, This will often be
     * a {@link VariableReference}, suitably initialized to refer to the corresponding variable declaration,
     * but in general it can be any expression which returns the variable's value when evaluated.
    */

    public Expression bindVariable(StructuredQName qName) throws XPathException;

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     * @return the function library
     */

    public FunctionLibrary getFunctionLibrary();

    /**
    * Get a named collation.
    * @param name The name of the required collation. Supply null to get the default collation.
    * @return the collation; or null if the required collation is not found.
    */

    public StringCollator getCollation(String name);

    /**
    * Get the name of the default collation.
    * @return the name of the default collation; or the name of the codepoint collation
    * if no default collation has been defined
    */

    public String getDefaultCollationName();

    /**
     * Get the default XPath namespace for elements and types
     * @return the default namespace, or NamespaceConstant.NULL for the non-namespace
     */

    public String getDefaultElementNamespace();

    /**
     * Get the default function namespace
     * @return the default namespace for function names
     */

    public String getDefaultFunctionNamespace();

    /**
     * Determine whether backwards compatibility mode is used
     * @return true if 1.0 compaibility mode is in force.
    */

    public boolean isInBackwardsCompatibleMode();

    /**
     * Ask whether a Schema for a given target namespace has been imported. Note that the
     * in-scope element declarations, attribute declarations and schema types are the types registered
     * with the (schema-aware) configuration, provided that their namespace URI is registered
     * in the static context as being an imported schema namespace. (A consequence of this is that
     * within a Configuration, there can only be one schema for any given namespace, including the
     * null namespace).
     * @param namespace the target namespace in question
     * @return true if the given namespace has been imported
     */

    public boolean isImportedSchema(String namespace);

    /**
     * Get the set of imported schemas
     * @return a Set, the set of URIs representing the target namespaces of imported schemas,
     * using the zero-length string to denote the "null" namespace.
     */

    public Set<String> getImportedSchemaNamespaces();

    /**
     * Ask whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     * @param type the supposedly built-in type. This will always be a type in the
     * XS namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(BuiltInAtomicType type);

    /**
     * Get a namespace resolver to resolve the namespaces declared in this static context.
     * @return a namespace resolver.
     */

    public NamespaceResolver getNamespaceResolver();

        /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     * @return the required type of the context item
     * @since 9.3
     */

    public ItemType getRequiredContextItemType();

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     * @return the decimal format manager for this static context, or null if no named decimal
     *         formats are available in this environment.
     * @since 9.2
     */

    public DecimalFormatManager getDecimalFormatManager();

    /**
     * Get the XPath language level supported, as a decimal value.
     * The current levels supported are 2.0, and 3.0. The default is 2.0.
     * If running XQuery 1.0, the value is 2.0; if running XQuery 3.0, it is 3.0.
     * @return the XPath language level; the return value will be either
     * {@link net.sf.saxon.value.DecimalValue#TWO} or {@link net.sf.saxon.value.DecimalValue#THREE}
     * @since 9.3
     */

    public DecimalValue getXPathLanguageLevel();

}

