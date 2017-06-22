////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;

import java.io.Serializable;

/**
 * A CollationURIResolver accepts a collation name as input, and returns
 * a collation (represented by a {@link StringCollator} as output. A CollationURIResolver
 * can be registered with the Configuration (or with a TransformerFactory)
 * to resolve all collation URIs used in a stylesheet or query.
 */
public interface CollationURIResolver extends Serializable {

    /**
     * Resolve a collation URI (expressed as a string) and return
     * the corresponding collation.
     * @param relativeURI the collation URI as written in the query or stylesheet
     * @param baseURI The base URI of the static context where the collation URI
     * appears. The base URI is available only in cases where the collation URI is resolved
     * at compile time; in cases where the collation URI is not resolved until execution
     * time (typically because it is supplied as an expression rather than as a string literal)
     * this parameter is currently set to null.
     * @param config The configuration. Provided in case the collation URI resolver
     * needs it.
     * @return a StringCollator, representing the collation to be used. Note that although
     * any StringCollator may be returned, functions such as contains() that need to break
     * a string into its collation units will work only if the returned StringCollator
     * is a {@link net.sf.saxon.lib.SubstringMatcher}.
     * <p>If the Collation URI cannot be resolved, return null.
     * Note that unlike the JAXP URIResolver, returning null does not cause the default
     * CollationURIResolver to be invoked; if this is required, the user-written CollationURIResolver
     * should explicitly instantiate and invoke the {@link StandardCollationURIResolver} before
     * returning null.</p>
     * @since 8.5/8.9 (this interface was introduced provisionally in 8.5, and modified in 8.9 to return
     * a StringCollator rather than a Comparator).
     */

    public StringCollator resolve(String relativeURI, String baseURI, Configuration config);
}

