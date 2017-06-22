////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

class IdentityTransformer extends Controller {

    protected IdentityTransformer(Configuration config) {
        super(config);
    }

    /**
    * Perform identify transformation from Source to Result
    */

    public void transform(Source source, Result result)
    throws TransformerException {
        try {
            if (getConfiguration().isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
                getExecutable().setSchemaAware(true);
            }
            PipelineConfiguration pipe = makePipelineConfiguration();
            SerializerFactory sf = getConfiguration().getSerializerFactory();
            Receiver receiver = sf.getReceiver(
                    result, pipe, getOutputProperties());
            NamespaceReducer reducer = new NamespaceReducer(receiver);
            ParseOptions options = pipe.getParseOptions();
            options.setContinueAfterValidationErrors(true);
            Sender.send(source, reducer, options);
        } catch (XPathException err) {
            Throwable cause = err.getException();
            if (cause != null && cause instanceof SAXParseException) {
                // This generally means the error was already reported.
                // But if a RuntimeException occurs in Saxon during a callback from
                // the Crimson parser, Crimson wraps this in a SAXParseException without
                // reporting it further.
                SAXParseException spe = (SAXParseException)cause;
                cause = spe.getException();
                if (cause instanceof RuntimeException) {
                    reportFatalError(err);
                }
            } else {
                reportFatalError(err);
            }
            throw err;
        }
    }

}

