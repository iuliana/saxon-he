////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

import javax.xml.transform.TransformerException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * StandardCollationURIResolver allows a Collation to be created given
 * a URI starting with "http://saxon.sf.net/collation" followed by a set of query parameters.
*/

public class StandardCollationURIResolver implements CollationURIResolver {

    private static final StandardCollationURIResolver theInstance = new StandardCollationURIResolver();

    /**
     * The class is normally used as a singleton, but the constructor is public to allow the class to be named
     * as a value of the configuration property COLLATION_URI_RESOLVER
     */
    public StandardCollationURIResolver() {
    }

    /**
     * Return the singleton instance of this class
     * @return the singleton instance
     */

    public static StandardCollationURIResolver getInstance() {
        return theInstance;
    }


    /**
     * Create a collator from a parameterized URI
     * @return null if the collation URI is not recognized. If the collation URI is recognized but contains
     * errors, the method returns null after sending a warning to the ErrorListener.
     */

    /*@Nullable*/ public StringCollator resolve(String uri, String base, Configuration config) {
        try {
            if (uri.equals("http://saxon.sf.net/collation")) {
                return Configuration.getPlatform().makeCollation(config, new Properties(), uri);
            } else if (uri.startsWith("http://saxon.sf.net/collation?")) {
                URI uuri;
                try {
                    uuri = new URI(uri);
                } catch (URISyntaxException err) {
                    throw new XPathException(err);
                }
                Properties props = new Properties();
                String query = uuri.getRawQuery();
                StringTokenizer queryTokenizer = new StringTokenizer(query, ";&");
                while (queryTokenizer.hasMoreElements()) {
                    String param = queryTokenizer.nextToken();
                    int eq = param.indexOf('=');
                    if (eq > 0 && eq < param.length()-1) {
                        String kw = param.substring(0, eq);
                        String val = AnyURIValue.decode(param.substring(eq + 1));
                        props.setProperty(kw, val);
                    }
                }
                return Configuration.getPlatform().makeCollation(config, props, uri);
            } else {
                return null;
            }
        } catch (XPathException e) {
            try {
                config.getErrorListener().warning(e);
            } catch (TransformerException e1) {
                //
            }
            return null;
        }
    }


}

