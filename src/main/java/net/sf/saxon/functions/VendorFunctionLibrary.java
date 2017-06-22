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
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
 * The VendorFunctionLibrary represents specially-recognized functions in the Saxon namespace. 
 */

public class VendorFunctionLibrary extends IntegratedFunctionLibrary {

    /**
     * Create the Vendor Function Library for Saxon
     */

    public VendorFunctionLibrary() {
        init();
    }

    protected void init() {
        registerFunction(new IsWholeNumber());
    }

    @Override
    public FunctionLibrary copy() {
        return new VendorFunctionLibrary();
    }

    /**
     * Make a Saxon function with a given name
     * @param localName the local name of the function
     * @param arguments the arguments of the function
     * @param env the static context
     * @param container the container for the new expression
     * @return an exprssion representing a call on the given function
     */

    /*@Nullable*/ public Expression makeSaxonFunction(String localName, Expression[] arguments, StaticContext env, Container container)
    throws XPathException {
        String uri = NamespaceConstant.SAXON;
        StructuredQName functionName = new StructuredQName("saxon", uri, localName);
        return bind(functionName, arguments.length, arguments, env, container);
    }


}

