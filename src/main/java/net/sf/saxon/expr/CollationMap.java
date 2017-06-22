////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;

import java.io.Serializable;
import java.util.HashMap;

/**
 * This object maps collation URIs to collations. Logically this function is part of the static
 * context, but it is often needed dynamically, so it is defined as a separate component that can
 * safely be retained at run-time.
 */
public class CollationMap implements Serializable {

    private Configuration config;
    private String defaultCollationName;
    private HashMap<String, StringCollator> map;

    /**
     * Create a collation map
     * @param config the Saxon configuration
     */

    public CollationMap(Configuration config) {
        CollationMap old = config.getCollationMap();
        this.config = config;
        this.defaultCollationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
    }

    /**
     * Create a copy of a collation map
     * @param in the collation map to be copied
     */

    public CollationMap(CollationMap in) {
        if (in.map != null) {
            map = new HashMap<String, StringCollator>(in.map);
        }
        config = in.config;
        defaultCollationName = in.defaultCollationName;
    }

    /**
     * Set the name of the default collation
     * @param name the default collation name (should be a URI, but this is not enforced)
     * @throws NullPointerException if the supplied name is null
     */

    public void setDefaultCollationName(/*@Nullable*/ String name) {
        if (name == null) {
            throw new NullPointerException("defaultCollationName");
        }
        defaultCollationName = name;
    }

    /**
     * Get the name of the default collation
     * @return the default collation name (should be a URI, but this is not enforced)
     */

    public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Get the default collation
     * @return the default collation, as a StringCollator
     */

    public StringCollator getDefaultCollation() {
        return getNamedCollation(defaultCollationName);
    }

    /**
     * Register a named collation
     * @param absoluteURI the name of the collation. This should be an absolute URI, but
     * this is not enforced
     * @param collator the StringCollator that implements the collating rules
     */

    public void setNamedCollation(String absoluteURI, StringCollator collator) {
        if (map == null) {
            map = new HashMap<String, StringCollator>();
        }
        map.put(absoluteURI, collator);
    }

    /**
     * Get the collation with a given collation name. If the collation name has
     * not been registered in this CollationMap, the CollationURIResolver registered
     * with the Configuration is called. If this cannot resolve the collation name,
     * it should return null.
     * @param name the collation name (should be an absolute URI)
     * @return the StringCollator with this name if known, or null if not known
     */

    public StringCollator getNamedCollation(String name) {
        if (name.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
            return CodepointCollator.getInstance();
        }
        if (map != null) {
            StringCollator c = map.get(name);
            if (c != null) {
                return c;
            }
        }
        return config.getCollationURIResolver().resolve(name, null, config);
    }
}

