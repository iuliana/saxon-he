////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * A default implementation of the SAX ErrorHandler interface. Used by Saxon to catch XML parsing errors
 * if no error handler is supplied by the application.
 */
public class StandardErrorHandler implements org.xml.sax.ErrorHandler {

	private static final Logger LOGGER = Logger.getLogger(StandardErrorHandler.class);
	
    private ErrorListener errorListener;
    private Writer errorOutput;
    private int warningCount = 0;
    private int errorCount = 0;
    private int fatalErrorCount = 0;
    private boolean silent = false;

    public StandardErrorHandler(ErrorListener listener) {
        errorListener = listener;
    }

    /**
    * Set output for error messages produced by the default error handler.
    * The default error handler does not throw an exception
    * for parse errors or input I/O errors, rather it returns a result code and
    * writes diagnostics to a user-specified output writer, which defaults to
    * System.err<BR>
    * This call has no effect if setErrorHandler() has been called to supply a
    * user-defined error handler
    * @param writer The Writer to use for error messages
    */
    public void setErrorOutput(Writer writer) {
        errorOutput = writer;
    }

    public void setSilent() {
        silent = true;
    }

    /**
    * Callback interface for SAX: not for application use
    */
    public void warning (SAXParseException e) {
        if (errorListener != null) {
            try {
                warningCount++;
                if (!silent) {
                    errorListener.warning(new TransformerException(e));
                }
            } catch (Exception err) {
            	LOGGER.error("Could not receive notification of warning", err);
            }
        }
    }

    /**
    * Callback interface for SAX: not for application use
    */
    public void error (SAXParseException e) throws SAXException {
        errorCount++;
        if (!silent) {
            reportError(e, false);
        }
    }

    /**
    * Callback interface for SAX: not for application use
    */
    public void fatalError (SAXParseException e) throws SAXException {
        fatalErrorCount++;
        if (!silent) {
            reportError(e, true);
        }
        throw e;
    }

    /**
    * Common routine for SAX errors and fatal errors
     * @param e the exception being handled
     * @param isFatal true if the error is classified as fatal
     */
    protected void reportError (SAXParseException e, boolean isFatal) {
        if (errorListener != null) {
            try {
				ExpressionLocation loc = new ExpressionLocation(e.getSystemId(), e.getLineNumber(),
					e.getColumnNumber());
                XPathException err = new XPathException("Error reported by XML parser", loc, e);
                err.setErrorCode(SaxonErrorCode.SXXP0003);
                if (isFatal) {
                    errorListener.fatalError(err);
                } else {
                    errorListener.error(err);
                }
            } catch (Exception exception) {
            	LOGGER.error("Could not receive notification of error", exception);
            }
        } else {

            try {
                if (errorOutput == null) {
                    errorOutput = new PrintWriter(System.err);
                }
                String errcat = isFatal ? "Fatal error" : "Error";
                errorOutput.write(errcat + " reported by XML parser: " + e.getMessage() + '\n');
                errorOutput.write("  URL:    " + e.getSystemId() + '\n');
                errorOutput.write("  Line:   " + e.getLineNumber() + '\n');
                errorOutput.write("  Column: " + e.getColumnNumber() + '\n');
                errorOutput.flush();
            } catch (Exception e2) {
                LOGGER.error("Could not output errors reported by XML parser", e2);
            }
        }
    }

    /**
     * Return the number of warnings (including warnings) reported
     * @return the number of warnings
     */
    public int getWarningCount() {
        return warningCount;
    }

    /**
     * Return the number of errors reported
     * @return the number of non-fatal errors
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Return the number of fatal errors reported
     * @return the number of fatal errors
     */
    public int getFatalErrorCount() {
        return fatalErrorCount;
    }
}
