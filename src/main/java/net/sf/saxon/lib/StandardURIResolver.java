////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.Platform;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.IDFilter;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.functions.EscapeURI;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NonDelegatingURIResolver;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;


/**
* This class provides the service of converting a URI into an InputSource.
* It is used to get stylesheet modules referenced by xsl:import and xsl:include,
* and source documents referenced by the document() function. The standard version
* handles anything that the java URL class will handle.
* You can write a subclass to handle other kinds of URI, e.g. references to things in
* a database.
* @author Michael H. Kay
*/

public class StandardURIResolver implements NonDelegatingURIResolver, Serializable {

    // TODO: IDEA: support the data: URI scheme. (Requires unescaping of the URI, then parsing the content as XML)

    /*@Nullable*/ private Configuration config = null;
    protected boolean recognizeQueryParameters = false;

    /**
     * Create a StandardURIResolver, with no reference to a Configuration.
     * Note: it is preferable but not essential to supply a Configuration, either in the constructor
     * or in a subsequent call of <code>setConfiguration()</code>
     */

    public StandardURIResolver() {
        this(null);
    }

    /**
     * Create a StandardURIResolver, with a reference to a Configuration
     * @param config The Configuration object. May be null.
     * This is used (if available) to get a reusable SAX Parser for a source XML document
     */

    public StandardURIResolver(/*@Nullable*/ Configuration config) {
        this.config = config;
    }

    /**
     * Indicate that query parameters (such as validation=strict) are to be recognized
     * @param recognize Set to true if query parameters in the URI are to be recognized and acted upon.
     * The default (for compatibility and interoperability reasons) is false.
     */

    public void setRecognizeQueryParameters(boolean recognize) {
        recognizeQueryParameters = recognize;
    }

    /**
     * Determine whether query parameters (such as validation=strict) are to be recognized
     * @return true if query parameters are recognized and interpreted by Saxon.
     */

    public boolean queryParametersAreRecognized() {
        return recognizeQueryParameters;
    }

    /**
     * Get the relevant platform
     * @return the platform
     */

    protected Platform getPlatform() {
        return Configuration.getPlatform();
    }

    /**
     * Set the configuration
     * @param config the configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the configuration if available
     * @return the configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
    * Resolve a URI
    * @param href The relative or absolute URI. May be an empty string. May contain
    * a fragment identifier starting with "#", which must be the value of an ID attribute
    * in the referenced XML document.
    * @param base The base URI that should be used. May be null if uri is absolute.
    * @return a Source object representing an XML document
    */

    public Source resolve(String href, String base)
    throws XPathException {

        if (config != null && config.isTiming()) {
            assert config != null;
            config.getStandardErrorOutput().println("URIResolver.resolve href=\"" + href + "\" base=\"" + base + "\"");
        }
        // System.err.println("StandardURIResolver, href=" + href + ", base=" + base);

        String relativeURI = href;
        String id = null;

        // Extract any fragment identifier. Note, this code is no longer used to
        // resolve fragment identifiers in URI references passed to the document()
        // function: the code of the document() function handles these itself.

        int hash = href.indexOf('#');
        if (hash>=0) {
            relativeURI = href.substring(0, hash);
            id = href.substring(hash+1);
            // System.err.println("StandardURIResolver, href=" + href + ", id=" + id);
        }

        URIQueryParameters params = null;
        URI uri;
        URI relative;
        try {
            relativeURI = ResolveURI.escapeSpaces(relativeURI);
            relative = new URI(relativeURI);
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid relative URI " + Err.wrap(relativeURI), err);
        }

        String query = relative.getQuery();
        if (query != null && recognizeQueryParameters) {
            params = new URIQueryParameters(query, config);
            int q = relativeURI.indexOf('?');
            relativeURI = relativeURI.substring(0, q);
        }

        Source source = null;
        if (recognizeQueryParameters && relativeURI.endsWith(".ptree")) {
            source = getPTreeSource(relativeURI, base);
        }

        if (source == null) {
            try {
                uri = ResolveURI.makeAbsolute(relativeURI, base);
            } catch (URISyntaxException err) {
                // System.err.println("Recovering from " + err);
                // last resort: if the base URI is null, or is itself a relative URI, we
                // try to expand it relative to the current working directory
                String expandedBase = ResolveURI.tryToExpand(base);
                if (!expandedBase.equals(base)) { // prevent infinite recursion
                    return resolve(href, expandedBase);
                }
                //err.printStackTrace();
                throw new XPathException("Invalid URI " + Err.wrap(relativeURI) + " - base " + Err.wrap(base), err);
            }

            // Check that any "%" sign in the URI is part of a well-formed percent-encoded UTF-8 character.
            // Without this check, dereferencing the resulting URL can fail with arbitrary unchecked exceptions

            final String uriString = uri.toString();
            EscapeURI.checkPercentEncoding(uriString);

            source = new SAXSource();
            setSAXInputSource((SAXSource)source, uriString);


            if (params != null) {
                XMLReader parser = params.getXMLReader();
                if (parser != null) {
                    ((SAXSource)source).setXMLReader(parser);
                }
            }

            if (((SAXSource)source).getXMLReader() == null) {
                if (config==null) {
                    try {
                        ((SAXSource)source).setXMLReader(Configuration.getPlatform().loadParser());
                    } catch (Exception err) {
                        throw new XPathException(err);
                    }
                } else {
                    //((SAXSource)source).setXMLReader(config.getSourceParser());
                    // Leave the Sender to allocate an XMLReader, so that it can be returned to the pool after use
                }
            }
        }

        if (params != null) {
            int stripSpace = params.getStripSpace();
            source = AugmentedSource.makeAugmentedSource(source);
            ((AugmentedSource)source).setStripSpace(stripSpace);
        }

        if (id != null) {
            final String idFinal = id;
            FilterFactory factory = new FilterFactory() {
                public ProxyReceiver makeFilter(Receiver next) {
                    return new IDFilter(next, idFinal);
                }
            };
            source = AugmentedSource.makeAugmentedSource(source);
            ((AugmentedSource)source).addFilter(factory);
        }

        if (params != null) {
            Integer validation = params.getValidationMode();
            if (validation != null) {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource)source).setSchemaValidationMode(validation);
            }
        }

        if (params != null) {
            Boolean xinclude = params.getXInclude();
            if (xinclude != null) {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource)source).setXIncludeAware(xinclude.booleanValue());
            }
        }

        return source;
    }

    /**
     * Handle a PTree source file (Saxon-EE only)
     * @param href the relative URI
     * @param base the base URI
     * @return the new Source object
     */

    protected Source getPTreeSource(String href, String base) throws XPathException {
        throw new XPathException("PTree files can only be read using a Saxon-EE configuration");
    }

    /**
     * Set the InputSource part of the returned SAXSource. This is done in a separate
     * method to allow subclassing. The default implementation simply places the URI in the
     * InputSource, allowing the XML parser to take responsibility for the dereferencing.
     * A subclass may choose to dereference the URI at this point an place an InputStream
     * in the SAXSource.
     * @param source the SAXSource being initialized
     * @param uriString the absolute (resolved) URI to be used
     */

    protected void setSAXInputSource(SAXSource source, String uriString) {
        source.setInputSource(new InputSource(uriString));
        source.setSystemId(uriString);
    }

}

