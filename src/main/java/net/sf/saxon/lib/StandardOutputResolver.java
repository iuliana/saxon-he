////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.*;


/**
* This class defines the default OutputURIResolver. This is a counterpart to the JAXP
* URIResolver, but is used to map the URI of a secondary result document to a Result object
* which acts as the destination for the new document.
* @author Michael H. Kay
*/

public class StandardOutputResolver implements OutputURIResolver, Serializable {

    private static StandardOutputResolver theInstance = new StandardOutputResolver();

    /**
     * Get a singular instance
     * @return the singleton instance of the class
    */

    public static StandardOutputResolver getInstance() {
        return theInstance;
    }

    /**
     * Get an instance of this OutputURIResolver class.
     * <p>This method is called every time an xsl:result-document instruction
     * is evaluated (with an href attribute). The resolve() and close() methods
     * will be called on the returned instance.</p>
     * <p>This OutputURIResolver is stateless (that is, it retains no information
     * between resolve() and close()), so the same instance can safely be returned
     * each time. For a stateful OutputURIResolver, it must either take care to be
     * thread-safe (handling multiple invocations of xsl:result-document concurrently),
     * or it must return a fresh instance of itself for each call.</p>
     */

    public StandardOutputResolver newInstance() {
        return this;
    }

    /**
    * Resolve an output URI
    * @param href The relative URI of the output document. This corresponds to the
    * href attribute of the xsl:result-document instruction.
    * @param base The base URI that should be used. This is the base output URI,
    * normally the URI of the principal output file.
    * @return a Result object representing the destination for the XML document
    */

    public Result resolve(String href, /*@Nullable*/ String base) throws XPathException {

        // System.err.println("Output URI Resolver (href='" + href + "', base='" + base + "')");

        String which = "base";
        try {
            URI absoluteURI;
            if (href.length() == 0) {
                if (base==null) {
                    throw new XPathException("The system identifier of the principal output file is unknown");
                }
                absoluteURI= new URI(base);
            } else {
                which = "relative";
                absoluteURI= new URI(href);
            }
            if (!absoluteURI.isAbsolute()) {
                if (base==null) {
                    throw new XPathException("The system identifier of the principal output file is unknown");
                }
                which = "base";
                URI baseURI = new URI(base);
                which = "relative";
                absoluteURI = baseURI.resolve(href);
            }

            if ("file".equals(absoluteURI.getScheme())) {
                return makeOutputFile(absoluteURI);

            } else {

                // See if the Java VM can conjure up a writable URL connection for us.
                // This is optimistic: I have yet to discover a URL scheme that it can handle "out of the box".
                // But it can apparently be achieved using custom-written protocol handlers.

                URLConnection connection = absoluteURI.toURL().openConnection();
                connection.setDoInput(false);
                connection.setDoOutput(true);
                connection.connect();
                OutputStream stream = connection.getOutputStream();
                StreamResult result = new StreamResult(stream);
                result.setSystemId(absoluteURI.toASCIIString());
                return result;
            }
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid syntax for " + which + " URI", err);
        } catch (IllegalArgumentException err2) {
            throw new XPathException("Invalid " + which + " URI syntax", err2);
        } catch (MalformedURLException err3) {
            throw new XPathException("Resolved URL is malformed", err3);
        } catch (UnknownServiceException err5) {
            throw new XPathException("Specified protocol does not allow output", err5);
        } catch (IOException err4) {
            throw new XPathException("Cannot open connection to specified URL", err4);
        }
    }

    /**
     * Create an output file (unless it already exists) and return a reference to it as a Result object
     * @param absoluteURI the URI of the output file (which should use the "file" scheme
     * @return a Result object referencing this output file
     * @throws XPathException
     */

    public static synchronized Result makeOutputFile(URI absoluteURI) throws XPathException {
        try {
            return new StreamResult(new File(absoluteURI));
        } catch (IllegalArgumentException err) {
            throw new XPathException("Cannot write to URI " + absoluteURI + " (" + err.getMessage() + ")");
        }
    }

    /**
    * Signal completion of the result document. This method is called by the system
    * when the result document has been successfully written. It allows the resolver
    * to perform tidy-up actions such as closing output streams, or firing off
    * processes that take this result tree as input. Note that the OutputURIResolver
    * is stateless, so the original href is supplied to identify the document
    * that has been completed.
    */

    public void close(Result result) throws XPathException {
        if (result instanceof StreamResult) {
            OutputStream stream = ((StreamResult)result).getOutputStream();
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException err) {
                    throw new XPathException("Failed while closing output file", err);
                }
            }
            Writer writer = ((StreamResult)result).getWriter(); // Path not used, but there for safety
            if (writer != null) {
                try {
                    writer.close();
                } catch (java.io.IOException err) {
                    throw new XPathException("Failed while closing output file", err);
                }
            }
        }
    }

}

