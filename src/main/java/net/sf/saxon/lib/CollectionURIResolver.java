////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

import java.io.Serializable;


/**
 * This interface defines a CollectionURIResolver. This is a counterpart to the JAXP
 * URIResolver, but is used to map the URI of collection into a sequence of documents.
 * It is used to support the fn:collection() and fn:uri-collection() functions.
 */

public interface CollectionURIResolver extends Serializable {

    /**
     * Resolve a URI.
     *
     * @param href    The relative URI of the collection. This corresponds to the
     *                argument supplied to the collection() function. If the collection() function
     *                was called with no arguments (to get the "default collection") this argument
     *                will be null.
     * @param base    The base URI that should be used. This is the base URI of the
     *                static context in which the call to collection() was made, typically the URI
     *                of the stylesheet or query module
     * @param context The dynamic execution context
     * @return an Iterator over the documents in the collection. The items returned
     *         by this iterator must be instances either of xs:anyURI, or of node() (specifically,
     *         {@link net.sf.saxon.om.NodeInfo}).
     *         <p>When the fn:uri-collection() function is called: the result will consist of
     *         (a) any items that are xs:anyURI values, and (b) the document URIs of any items that are
     *         document nodes with a document URI.</p>
     *         <p>When the fn:collection() function is called: if xs:anyURI values are returned, the corresponding
     *         document will be retrieved as if by a call to the fn:doc() function: this means that
     *         the system first checks to see if the document is already loaded, and if not, calls
     *         the registered URIResolver to dereference the URI. This is the recommended approach
     *         to ensure that the resulting collection is stable: however, it has the consequence
     *         that the documents will by default remain in memory for the duration of the query
     *         or transformation.</p>
     *         <p>
     *         If the collection URI is not recognized, the method may either return an empty iterator,
     *         in which case no error is reported, or it may throw an exception, in which case
     *         the query or transformation fails. Returning null has the same effect as returning
     *         an empty iterator.</p>
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     */

    public SequenceIterator resolve(String href, String base, XPathContext context) throws XPathException;

}

