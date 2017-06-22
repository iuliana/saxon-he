////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.StringValue;


/**
* This class supports the function namespace-uri-for-prefix()
*/

public class NamespaceForPrefix extends SystemFunctionCall implements Callable {

    /**
     * Evaluate the function
     * @param context the XPath dynamic context
     * @return the URI corresponding to the prefix supplied in the first argument, or null
     * if the prefix is not in scope
     * @throws XPathException if a failure occurs evaluating the arguments
     */

    public AnyURIValue evaluateItem(XPathContext context) throws XPathException {
        NodeInfo element = (NodeInfo)argument[1].evaluateItem(context);
        StringValue p = (StringValue)argument[0].evaluateItem(context);
        return namespaceUriForPrefix(p, element);
    }


    /**
     * Evaluate the expression
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AnyURIValue result = namespaceUriForPrefix((StringValue)arguments[0].head(), (NodeInfo)arguments[1].head());
        return (result==null ? EmptySequence.getInstance() : result);
    }

    /**
     * Private supporting method
     * @param p  the prefix
     * @param element the element node
     * @return  the corresponding namespace, or null if not in scope
     */

    /*@Nullable*/ private static AnyURIValue namespaceUriForPrefix(StringValue p, NodeInfo element) {
        String prefix;
        if (p == null) {
            prefix = "";
        } else {
            prefix = p.getStringValue();
        }
        NamespaceResolver resolver = new InscopeNamespaceResolver(element);
        String uri = resolver.getURIForPrefix(prefix, true);
        if (uri == null || uri.length() == 0) {
            return null;
        }
        return new AnyURIValue(uri);
    }

}

