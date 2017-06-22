////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.AnyURIValue;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;

/**
 * Implement the fn:collection() function. This is responsible for calling the
 * registered {@link CollectionURIResolver}. For the effect of the default
 * system-supplied CollectionURIResolver, see {@link net.sf.saxon.lib.StandardCollectionURIResolver}
 */

public class Collection extends SystemFunctionCall implements Callable {

    /**
     * URI representing a collection that is always empty, regardless of any collection URI resolver
     */
    public static final String EMPTY_COLLECTION = "http://saxon.sf.net/collection/empty";

    /*@Nullable*/
    private String expressionBaseURI = null;

    public String getStaticBaseURI() {
        return expressionBaseURI;
    }

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        if (expressionBaseURI == null) {
            super.checkArguments(visitor);
            expressionBaseURI = visitor.getStaticContext().getBaseURI();
        }
    }

    public int computeSpecialProperties() {
        // See redmine bug 1652. We cannot assume that the nodes will be in document order because we can't assume
        // they will all be "new" documents. We can't even assume that they will be distinct.
        return (super.computeSpecialProperties() & ~StaticProperty.NON_CREATIVE) | StaticProperty.PEER_NODESET;
    }

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     *
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return addDocToPathMap(pathMap, pathMapNodeSet);
    }


    /**
     * Iterate over the contents of the collection
     *
     * @param context the dynamic context
     * @return an iterator, whose items will always be nodes (typically but not necessarily document nodes)
     * @throws XPathException
     */

    /*@NotNull*/
    public SequenceIterator<Item> iterate(final XPathContext context) throws XPathException {

        String href;

        if (getNumberOfArguments() == 0) {
            // No arguments supplied: this gets the default collection
            href = context.getController().getDefaultCollection();
        } else {
            Item arg = argument[0].evaluateItem(context);
            if (arg == null) {
                href = context.getController().getDefaultCollection();
            } else {
                href = arg.getStringValue();
            }
        }

        if (EMPTY_COLLECTION.equals(href)) {
            return EmptyIterator.emptyIterator();
        }
        CollectionURIResolver resolver = context.getConfiguration().getCollectionURIResolver();
        SequenceIterator iter;
        try {
            iter = resolver.resolve(href, expressionBaseURI, context);
        } catch (XPathException e) {
            e.setLocator(this);
            if (e.getErrorCodeQName() == null) {
                e.setErrorCode("FODC0002");
            }
            throw e;
        }

        return getResolverResults(iter, expressionBaseURI, context, this);
    }

    /**
     * Return the results of iterating over the results returned by the CollectionURIResolver.
     * <p>Note, this method is called by generated code</p>
     *
     * @param iter    iterator over the results of the CollectionURIResolver
     * @param baseURI the base URI
     * @param context the dynamic context
     * @param locator location of the instruction
     * @return an iterator over the documents in the collection.
     */

    public static SequenceIterator getResolverResults(
            SequenceIterator iter, final String baseURI, final XPathContext context, final SourceLocator locator) {
        if (iter == null) {
            return EmptyIterator.getInstance();
        } else {
            ItemMappingFunction imf = new ItemMappingFunction() {
                public Item mapItem(Item item) throws XPathException {
                    if (item instanceof NodeInfo) {
                        return item;
                    } else if (item instanceof AnyURIValue) {
                        return DocumentFn.makeDoc(
                                item.getStringValue(),
                                baseURI,
                                context,
                                locator);
                    } else if (item instanceof Source) {
                        return context.getConfiguration().buildDocument(((Source) item));
                    } else {
                        throw new XPathException("Value returned by CollectionURIResolver must be a Source or an anyURI");
                    }
                }
            };
            return new ItemMappingIterator(iter, imf);
        }
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        String href;

        if (getNumberOfArguments() == 0) {
            // No arguments supplied: this gets the default collection
            href = context.getConfiguration().getDefaultCollection();
        } else {
            Item arg = arguments[0].head();
            if (arg == null) {
                href = context.getConfiguration().getDefaultCollection();
            } else {
                href = arg.getStringValue();
            }
        }

        CollectionURIResolver resolver = context.getConfiguration().getCollectionURIResolver();
        SequenceIterator iter;
        try {
            iter = resolver.resolve(href, expressionBaseURI, context);
        } catch (XPathException e) {
            e.setLocator(this);
            throw e;
        }
        return SequenceTool.toLazySequence(getResolverResults(iter, expressionBaseURI, context, this));
    }


    // TODO: provide control over error recovery (etc) through options in the catalog file.

}

