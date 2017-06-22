////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;
import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;


/**
* This interface defines an OutputURIResolver. This is a counterpart to the JAXP
* URIResolver, but is used to map the URI of a secondary result document to a Result object
* which acts as the destination for the new document.
* @author Michael H. Kay
*/

public interface OutputURIResolver {

    /**
     * Get an instance of this OutputURIResolver class.
     * <p>This method is called every time an xsl:result-document instruction
     * is evaluated (with an href attribute). The resolve() and close() methods
     * will be called on the returned instance.</p>
     * <p>If the OutputURIResolver is stateless (that is, it retains no information
     * between resolve() and close()), then the same instance can safely be returned
     * each time. For a stateful OutputURIResolver, it must either take care to be
     * thread-safe (handling multiple invocations of xsl:result-document concurrently),
     * or it must return a fresh instance of itself for each call.</p>
     */

    public OutputURIResolver newInstance();

    /**
     * Resolve an output URI.
     * @param href The relative URI of the output document. This corresponds to the
     * href attribute of the xsl:result-document instruction.
     * @param base The base URI that should be used. This is the Base Output URI, typically
     * the URI of the principal output document
     * @return a Result object representing the destination for the XML document. The
     * method can also return null, in which case the standard output URI resolver
     * will be used to create a Result object.
     * <p>The systemId property of the returned Result object should normally be the
     * result of resolving href (as a relative URI) against the value of base.
     * This systemId is used to enforce the error
     * conditions in the XSLT specification that disallow writing two result trees to the
     * same destination, or reading from a destination that is written to in the same
     * transformation. Setting the systemId to null, or to a deceptive value, will defeat
     * these error checks (which can sometimes be useful). Equally, setting the systemId
     * to the same value on repeated calls when different href/base arguments are supplied
     * will cause a spurious error.</p>
     * @throws javax.xml.transform.TransformerException if any error occurs
    */

    public Result resolve(String href, String base) throws TransformerException;

    /**
     * Signal completion of the result document. This method is called by the system
     * when the result document has been successfully written. It allows the resolver
     * to perform tidy-up actions such as closing output streams, or firing off
     * processes that take this result tree as input. Note that the OutputURIResolver
     * is stateless, so the the original Result object is supplied to identify the document
     * that has been completed.
     * @param result The result object returned by the previous call of resolve()
     * @throws javax.xml.transform.TransformerException if any error occurs
    */

    public void close(Result result) throws TransformerException;

}

