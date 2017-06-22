////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
 * An exception thrown by the Saxon s9api API. This is always a wrapper for some other underlying exception
 */
public class SaxonApiException extends Exception {

    /**
     * Create a SaxonApiException
     * @param cause the underlying cause of the exception
     */

    public SaxonApiException(Throwable cause) {
        super(cause);
    }

    /**
     * Create a SaxonApiException
     * @param message the message
     */

    public SaxonApiException(String message) {
        super(new XPathException(message));
    }

    /**
     * Create a SaxonApiException
     * @param message the message
     * @param cause the underlying cause of the exception
     */

    public SaxonApiException(String message, Throwable cause) {
        super(new XPathException(message, cause));
    }

    /**
     * Returns the detail message string of this throwable.
     *
     * @return the detail message string of this <tt>Throwable</tt> instance
     *         (which may be <tt>null</tt>).
     */
    public String getMessage() {
        return getCause().getMessage();
    }

    /**
     * Get the error code associated with the exception, if there is one
     * @return the associated error code, or null if no error code is available
     * @since 9.3
     */

    /*@Nullable*/ public QName getErrorCode() {
        Throwable cause = getCause();
        if (cause instanceof XPathException) {
            StructuredQName code = ((XPathException)cause).getErrorCodeQName();
            return (code==null ? null : new QName(code));
        } else {
            return null;
        }
    }
}

