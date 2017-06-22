////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.AnyURIValue;

import javax.xml.transform.Source;

/**
 * Implement the fn:uri-collection() function (new in XQuery 3.0/XSLT 3.0). This is responsible for calling the
 * registered {@link net.sf.saxon.lib.CollectionURIResolver}. For the effect of the default
 * system-supplied CollectionURIResolver, see {@link net.sf.saxon.lib.StandardCollectionURIResolver}
 */

public class UriCollection extends SystemFunctionCall implements Callable {

    // TODO: change the standard collection URI resolver so that query parameters such as onerror=warning become
    // part of the returned document URI, to be actioned only when the document URI is dereferenced

    /*@Nullable*/ private String expressionBaseURI = null;

    /**
     * Bind aspects of the static context on which the particular function depends
     *
     * @param env the static context of the function call
     * @throws net.sf.saxon.trans.XPathException
     *          if execution with this static context will inevitably fail
     */
    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        expressionBaseURI = env.getBaseURI();
    }

    public String getStaticBaseURI() {
        return expressionBaseURI;
    }

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Iterate over the contents of the collection
     * @param context the dynamic context
     * @return an iterator, whose items will always be nodes (typically but not necessarily document nodes)
     * @throws net.sf.saxon.trans.XPathException
     */

    /*@NotNull*/
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        String href;

        if (getNumberOfArguments() == 0) {
            // No arguments supplied: this gets the default collection
            href = context.getConfiguration().getDefaultCollection();
        } else {
            Item item = argument[0].evaluateItem(context);
            href = (item == null ? context.getConfiguration().getDefaultCollection() : item.getStringValue());
        }

        CollectionURIResolver resolver = context.getConfiguration().getCollectionURIResolver();
        SequenceIterator iter;
        try {
            iter = resolver.resolve(href, expressionBaseURI, context);
        } catch (XPathException e) {
            e.setLocator(this);
            throw e;
        }

        return getResolverResults(iter, context);
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        String href;

        if (arguments.length == 0) {
            // No arguments supplied: this gets the default collection
            href = context.getConfiguration().getDefaultCollection();
        } else {
            Item arg = arguments[0].head();
            href = (arg == null ? context.getConfiguration().getDefaultCollection() : arg.getStringValue());
        }

        CollectionURIResolver resolver = context.getConfiguration().getCollectionURIResolver();
        SequenceIterator iter;
        try {
            iter = resolver.resolve(href, expressionBaseURI, context);
        } catch (XPathException e) {
            e.setLocator(this);
            throw e;
        }

        return SequenceTool.toLazySequence(getResolverResults(iter, context));
    }

    private static SequenceIterator getResolverResults(
            SequenceIterator iter, final XPathContext context) {
        if (iter == null) {
            return EmptyIterator.getInstance();
        } else {
            ItemMappingFunction imf = new ItemMappingFunction() {
                public Item mapItem(Item item) throws XPathException {
                    if (item instanceof NodeInfo) {
                        return DocumentUriFn.getDocumentURI(((NodeInfo)item), context);
                    } else if (item instanceof Source) {
                        return new AnyURIValue(((Source)item).getSystemId());
                    } else if (item instanceof AnyURIValue) {
                        return item;
                    } else {
                        throw new XPathException("Value returned by CollectionURIResolver must be a Source or an anyURI");
                    }
                }
            };
            return new ItemMappingIterator(iter, imf);
        }
    }




}

