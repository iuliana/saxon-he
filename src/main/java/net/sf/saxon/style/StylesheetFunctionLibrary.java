////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.UserFunctionCall;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;



/**
 * A StylesheetFunctionLibrary contains functions defined by the user in a stylesheet. This library is used at
 * compile time only, as it contains references to the actual XSLFunction objects. Binding to a function in this
 * library registers the function call on a fix-up list to be notified when the actual compiled function becomes
 * available.
 */

public class StylesheetFunctionLibrary implements FunctionLibrary {

    private PrincipalStylesheetModule stylesheet;
    private boolean overriding;

    /**
     * Create a FunctionLibrary that provides access to stylesheet functions
     * @param sheet The XSLStylesheet element of the principal stylesheet module
     * @param overriding set to true if this library is to contain functions specifying override="yes",
     * or to false if it is to contain functions specifying override="no". (XSLT uses two instances
     * of this class, one for overriding functions and one for non-overriding functions.)
     */
    public StylesheetFunctionLibrary(PrincipalStylesheetModule sheet, boolean overriding) {
        this.stylesheet = sheet;
        this.overriding = overriding;
    }

    /**
     * Ask whether the functions in this library are "overriding" functions, that is, defined with
     * xsl:function override="yes".
     * @return true if these are overriding functions, false otherwise
     */

    public boolean isOverriding() {
        return overriding;
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     *
     * @param functionName
     * @param arity
     * @param staticArgs  The expressions supplied statically in the function call. The intention is
     * that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     * be used as part of the binding algorithm.
     * @param env
     * @param container
     * @return An object representing the extension function to be called, if one is found;
     * null if no extension function was found matching the required name and arity.
     * @throws net.sf.saxon.trans.XPathException if a function is found with the required name and arity, but
     * the implementation of the function cannot be loaded or used; or if an error occurs
     * while searching for the function; or if this function library "owns" the namespace containing
     * the function call, but no function was found.
     */

    public Expression bind(StructuredQName functionName, int arity, Expression[] staticArgs, StaticContext env, Container container)
            throws XPathException {
        XSLFunction fn = stylesheet.getFunction(functionName, staticArgs.length);
        if (fn==null) {
            return null;
        }
        if (fn.isOverriding() != overriding) {
            return null;
        }
        UserFunctionCall fc = new UserFunctionCall();
        fn.registerReference(fc);
        fc.setFunctionName(functionName);
        fc.setArguments(staticArgs);
        fc.setContainer(container);
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
        return stylesheet.getFunction(functionName, arity) != null;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    public FunctionLibrary copy() {
        return this;
    }

}

