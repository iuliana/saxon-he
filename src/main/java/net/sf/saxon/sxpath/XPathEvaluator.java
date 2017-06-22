////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.*;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.PatternSponsor;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.DecimalValue;

/**
 * This class provides a native Saxon API for free-standing evaluation of XPath expressions. Unlike the
 * JAXP API offered by {@link net.sf.saxon.xpath.XPathEvaluator} it exposes Saxon classes and interfaces
 * and thus provides a more strongly-typed interface with greater control over the detailed behaviour.
 *
 * <p>However, the preferred API for user applications calling XPath is the s9api {@link net.sf.saxon.s9api.XPathCompiler}
 * interface.</p>
 *
 * @author Michael H. Kay
 * @since 8.4
 */

public class XPathEvaluator {

    private XPathStaticContext staticContext;

    /**
     * Default constructor. Creates an XPathEvaluator with a default configuration and name pool.
     * Note that any source documents used by an XPath expression under this XPathEvaluator must
     * be built using the {@link Configuration} that is implicitly created by this constructor,
     * which is accessible using the {@link #getConfiguration} method.
    */

    public XPathEvaluator() {
        this(new Configuration());
    }

    /**
     * Construct an XPathEvaluator with a specified configuration.
     * @param config the configuration to be used. If the XPathEvaluator is
     * to be used to run schema-aware XPath expressions this must be an instance
     * of {@link com.saxonica.config.EnterpriseConfiguration}
     */
    public XPathEvaluator(Configuration config) {
        staticContext = new IndependentContext(config);
    }

    /**
     * Get the Configuration in use.
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return staticContext.getConfiguration();
    }

    /**
     * Declare a variable, making it available for use within a subsequently-compiled XPath expression.
     * The variable may subsequently be assigned a value using the method
     * {@link XPathDynamicContext#setVariable(XPathVariable, net.sf.saxon.om.Sequence)}.
     * Any external variable used in an XPath expression must be declared before the XPath expression
     * is compiled.
     * @param uri The namespace URI of the variable name. Use "" for the null namespace.
     * @param localName The local part of the variable name.
     * @return an object representing the variable
     */

    public XPathVariable declareVariable(String uri, String localName) {
        return staticContext.declareVariable(uri, localName);
    }

    /**
    * Set the static context for compiling XPath expressions. This provides more detailed control over the
    * environment in which the expression is compiled, for example it allows namespace prefixes to
    * be declared, variables to be bound and functions to be defined. For most purposes, the static
    * context can be defined by providing and tailoring an instance of the {@link IndependentContext} class.
    * Until this method is called, a default static context is used, in which no namespaces are defined
    * other than the standard ones (xml, xslt, and saxon), and no variables or functions (other than the
    * core XPath functions) are available.
     * @param context the XPath static context
     * <p>Setting a new static context clears any variables and namespaces that have previously been declared.</p>
    */

    public void setStaticContext(XPathStaticContext context) {
        staticContext = context;
    }

    /**
     * Get the current static context. This will always return a value; if no static context has been
     * supplied by the user, the system will have created its own. A system-created static context
     * will always be an instance of {@link IndependentContext}
     * @return the static context object
    */

    public XPathStaticContext getStaticContext() {
        return staticContext;
    }

    /**
    * Prepare (compile) an XPath expression for subsequent evaluation.
    * @param expression The XPath expression to be compiled, supplied as a string.
    * @return an XPathExpression object representing the prepared expression
    * @throws XPathException if the syntax of the expression is wrong, or if it references namespaces,
    * variables, or functions that have not been declared.
    */

    public XPathExpression createExpression(String expression) throws XPathException {
        Configuration config = getConfiguration();
        Executable exec = new Executable(config);
        SimpleContainer container = new SimpleContainer(exec);
        boolean allowXPath30 = staticContext.getXPathLanguageLevel().equals(DecimalValue.THREE);
        exec.setSchemaAware(staticContext.isSchemaAware());
        exec.setHostLanguage(Configuration.XPATH, allowXPath30);

        // Make the function library that's available at run-time (e.g. for saxon:evaluate() and function-lookup()).
        // This includes all user-defined functions regardless of which module they are in

        FunctionLibrary userlib = exec.getFunctionLibrary();
        FunctionLibraryList lib = new FunctionLibraryList();
        int permittedFunctions = StandardFunction.CORE;
        if (allowXPath30) {
            permittedFunctions |= StandardFunction.XPATH30;
        }
        lib.addFunctionLibrary(
                SystemFunctionLibrary.getSystemFunctionLibrary(permittedFunctions));
        lib.addFunctionLibrary(config.getVendorFunctionLibrary());
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(config));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        if(userlib != null) {
            lib.addFunctionLibrary(userlib);
        }
        exec.setFunctionLibrary(lib);


        Expression exp = ExpressionTool.make(expression, staticContext, container, 0, -1, 1, null);
        exp.setContainer(container);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, exec);
        visitor.setExecutable(exec);
        ItemType contextItemType = staticContext.getRequiredContextItemType();
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(contextItemType, true);
        exp = visitor.typeCheck(exp, cit);
        exp = visitor.optimize(exp, cit);
        exp.setContainer(container);    // might have lost its container during optimization
        SlotManager map = staticContext.getStackFrameMap();
        int numberOfExternalVariables = map.getNumberOfVariables();
        ExpressionTool.allocateSlots(exp, numberOfExternalVariables, map);
        XPathExpression xpe = new XPathExpression(this, exp);
        xpe.setStackFrameMap(map, numberOfExternalVariables);
        return xpe;
    }

    /**
     * Prepare (compile) an XSLT pattern for subsequent evaluation. The result is an XPathExpression
     * object representing a (pseudo-) expression that when evaluated returns a boolean result: true
     * if the context node matches the pattern, false if it does not.
     * @param pattern the XSLT pattern to be compiled, supplied as a string
     * @return an XPathExpression object representing the pattern, wrapped as an expression
     * @throws XPathException if the syntax of the expression is wrong, or if it references namespaces,
     * variables, or functions that have not been declared.
     * @since 9.1
     */

    /*@NotNull*/ public XPathExpression createPattern(String pattern) throws XPathException {
        Executable exec = new Executable(getConfiguration());
        Pattern pat = Pattern.make(pattern, staticContext, exec);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, exec);
        pat.analyze(visitor, new ExpressionVisitor.ContextItemType(Type.NODE_TYPE, true));
        SlotManager map = staticContext.getStackFrameMap();
        int slots = map.getNumberOfVariables();
        slots = pat.allocateSlots(map, slots);
        PatternSponsor sponsor = new PatternSponsor(pat);
        XPathExpression xpe = new XPathExpression(this, sponsor);
        xpe.setStackFrameMap(map, slots);
        return xpe;
    }

    /**
     * Set the external namespace resolver to be used. The NamespaceResolver is stored
     * as part of the static context. It overrides any namespaces declared directly
     * using declareNamespace on the staticContext object
     * @param namespaceResolver The namespace resolver, which maintains a mapping of prefixes to URIs.
     * Any namespace prefix used in the XPath expression is resolved using this namespaceResolver.
     */

    public void setNamespaceResolver(NamespaceResolver namespaceResolver) {
        staticContext.setNamespaceResolver(namespaceResolver);
    }

    /**
     * Get the external namespace resolver, if one has been set using {@link #setNamespaceResolver}
     * @return the namespace resolver supplied by the user if set, or a system-defined namespace resolver
     * otherwise. By default, the {@link IndependentContext} object used as the {@link XPathStaticContext}
     * also acts as the {@link NamespaceResolver}.
     */

    public NamespaceResolver getNamespaceResolver() {
        return staticContext.getNamespaceResolver();
    }

    /**
     * Set the default namespace for elements and types
     * @param uri The namespace to be used to qualify unprefixed element names and type names appearing
     * in the XPath expression.
     */

    public void setDefaultElementNamespace(String uri) {
        staticContext.setDefaultElementNamespace(uri);
    }

 
}

