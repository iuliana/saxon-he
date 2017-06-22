////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;

import java.io.Serializable;
import java.util.HashMap;



/**
 * A library of integrated function calls, that is, user-written extension functions implemented
 * as instances of the class IntegratedFunction.
 */
public class IntegratedFunctionLibrary implements FunctionLibrary, Serializable {

    private HashMap<StructuredQName, ExtensionFunctionDefinition> functions =
            new HashMap<StructuredQName, ExtensionFunctionDefinition>();

    /**
     * Register an integrated function with this function library
     * @param function the implementation of the function (or set of functions)
     */

    public void registerFunction(ExtensionFunctionDefinition function) {
        functions.put(function.getFunctionQName(), function);
    }

    /**
     * Bind an extension function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     *
     * @param functionName the QName of the function being called
     * @param arity
     * @param staticArgs   The expressions supplied statically in arguments to the function call.
     *                     The length of this array represents the arity of the function. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality()) may
     *                     be used as part of the binding algorithm. In some cases it may be possible for the function
     *                     to be pre-evaluated at compile time, for example if these expressions are all constant values.
     *                     <p/>
     *                     The conventions of the XPath language demand that the results of a function depend only on the
     *                     values of the expressions supplied as arguments, and not on the form of those expressions. For
     *                     example, the result of f(4) is expected to be the same as f(2+2). The actual expression is supplied
     *                     here to enable the binding mechanism to select the most efficient possible implementation (including
     *                     compile-time pre-evaluation where appropriate).
     * @param env          The static context of the function call
     * @param container
     * @return An object representing the function to be called, if one is found;
     *         null if no function was found matching the required name and arity.
     * @throws net.sf.saxon.trans.XPathException
     *          if a function is found with the required name and arity, but
     *          the implementation of the function cannot be loaded or used; or if an error occurs
     *          while searching for the function.
     */

    public Expression bind(StructuredQName functionName, int arity, Expression[] staticArgs, StaticContext env, Container container)
            throws XPathException {
        ExtensionFunctionDefinition defn = functions.get(functionName);
        if (defn == null) {
            return null;
        }
        try {
            return makeFunctionCall(defn, staticArgs, container);
        } catch (Exception err) {
            throw new XPathException("Failed to create call to extension function " + functionName.getDisplayName(), err);
        }
    }

    public static Expression makeFunctionCall(ExtensionFunctionDefinition defn, Expression[] staticArgs, Container container) {
        ExtensionFunctionCall f = defn.makeCallExpression();
        f.setDefinition(defn, container);
        IntegratedFunctionCall fc = new IntegratedFunctionCall(f);
        fc.setFunctionName(defn.getFunctionQName());
        fc.setArguments(staticArgs);
        return fc;
    }




    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @param arity        The number of arguments.
     * @return true if a function of this name and arity is available for calling
     */
    public boolean isAvailable(StructuredQName functionName, int arity) {
        ExtensionFunctionDefinition defn = functions.get(functionName);
        return defn != null && defn.getMaximumNumberOfArguments() >= arity && defn.getMinimumNumberOfArguments() <= arity;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     * @return a copy of this function library. This must be an instance of the original class.
     */

    public FunctionLibrary copy() {
        IntegratedFunctionLibrary lib = new IntegratedFunctionLibrary();
        lib.functions = new HashMap<StructuredQName, ExtensionFunctionDefinition>(functions);
        return lib;
    }

}

