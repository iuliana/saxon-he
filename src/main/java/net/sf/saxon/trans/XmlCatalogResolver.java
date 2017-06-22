////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.helpers.Debug;

import javax.xml.transform.TransformerException;

/**
 * Provides the interface to the Apache catalog resolver. This is in a separate class to ensure that no failure
 * occurs if the resolver code is not on the classpath, unless catalog resolution is explicitly requested.
 */

public class XmlCatalogResolver {

    public static void setCatalog(String catalog, final Configuration config, boolean isTracing) throws XPathException {
        System.setProperty("xml.catalog.files", catalog);
        Configuration.getPlatform().setDefaultSAXParserFactory();
        if (isTracing) {
            // Customize the resolver to write messages to the Saxon logging destination
            CatalogManager.getStaticManager().debug = new Debug() {
                @Override
                public void message(int level, String message) {
                    if (level <= getDebug()) {
                        config.getStandardErrorOutput().println(message);
                    }
                }

                @Override
                public void message(int level, String message, String spec) {
                    if (level <= getDebug()) {
                        config.getStandardErrorOutput().println(message + ": " + spec);
                    }
                }

                @Override
                public void message(int level, String message, String spec1, String spec2) {
                    if (level <= getDebug()) {
                        config.getStandardErrorOutput().println(message + ": " + spec1);
                        config.getStandardErrorOutput().println("\t" + spec2);
                    }
                }
            };
            CatalogManager.getStaticManager().setVerbosity(2);
        }
        config.setSourceParserClass("org.apache.xml.resolver.tools.ResolvingXMLReader");
        config.setStyleParserClass("org.apache.xml.resolver.tools.ResolvingXMLReader");
        try {
            config.setURIResolver(config.makeURIResolver("org.apache.xml.resolver.tools.CatalogResolver"));
        } catch (TransformerException err) {
            throw XPathException.makeXPathException(err);
        }
    }
}

