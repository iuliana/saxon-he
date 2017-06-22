////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

//import com.saxonica.functions.hof.CallableFunctionItem;
//import com.saxonica.functions.hof.SpecificFunctionType;
import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.value.SequenceType;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionResolver;
import java.util.Arrays;

/**
 * The XPathFunctionLibrary is a FunctionLibrary that supports binding of XPath function
 * calls to instances of the JAXP XPathFunction interface returned by an XPathFunctionResolver.
 */

public class XPathFunctionLibrary implements FunctionLibrary {

    private XPathFunctionResolver resolver;

    /**
     * Construct a XPathFunctionLibrary
     */

    public XPathFunctionLibrary() {
    }

    /**
      * Set the resolver
      * @param resolver The XPathFunctionResolver wrapped by this FunctionLibrary
      */

    public void setXPathFunctionResolver(XPathFunctionResolver resolver) {
        this.resolver = resolver;
    }

    /**
      * Get the resolver
      * @return the XPathFunctionResolver wrapped by this FunctionLibrary
      */
    
    public XPathFunctionResolver getXPathFunctionResolver() {
        return resolver;
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
     * null if no extension function was found matching the required name, arity, or signature.
     */

    /*@Nullable*/ public Expression bind(/*@NotNull*/ StructuredQName functionName, /*@NotNull*/ int arity, Expression[] staticArgs, StaticContext env, Container container)
            throws XPathException {
        if (resolver == null) {
            return null;
        }
        QName name = new QName(functionName.getURI(), functionName.getLocalPart());
        XPathFunction function = resolver.resolveFunction(name, staticArgs.length);
        if (function == null) {
            return null;
        }
        XPathFunctionCall fc = new XPathFunctionCall(function);
        fc.setArguments(staticArgs);
        return fc;
    }

//#ifdefined HOF
    /**
     * Test whether a function with a given name and arity is available; if so, return a function
     * item that can be dynamically called.
     * <p/>
     * <p>This supports the function-lookup() function in XPath 3.0.</p>
     *
     *
     *
     * @param functionName  the qualified name of the function being called
     * @param arity         The number of arguments.
     * @param staticContext the static context to be used by the function, in the event that
     *                      it is a system function with dependencies on the static context
     * @return if a function of this name and arity is available for calling, then a corresponding
     *         function item; or null if the function does not exist
     * @throws net.sf.saxon.trans.XPathException
     *          in the event of certain errors, for example attempting to get a function
     *          that is private
     */
    public FunctionItem getFunctionItem(StructuredQName functionName, int arity, StaticContext staticContext) throws XPathException {
        if (resolver == null) {
            return null;
        }
        QName name = new QName(functionName.getURI(), functionName.getLocalPart());
        XPathFunction function = resolver.resolveFunction(name, arity);
        if (function == null) {
            return null;
        }
        XPathFunctionCall functionCall = new XPathFunctionCall(function);
        SequenceType[] argTypes = new SequenceType[arity];
        Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);
        return null;
    }
//#endif


    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @param arity        The number of arguments.
     * @return true if a function of this name and arity is available for calling
     */
    public boolean isAvailable(StructuredQName functionName, int arity) {
        return resolver != null &&
                resolver.resolveFunction(
                        new QName(functionName.getURI(), functionName.getLocalPart()), arity) != null;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    /*@NotNull*/ public FunctionLibrary copy() {
        XPathFunctionLibrary xfl = new XPathFunctionLibrary();
        xfl.resolver = resolver;
        return xfl;
    }


}

