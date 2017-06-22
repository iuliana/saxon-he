////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;


/**
  * <b>TransformerHandlerImpl</b> implements the javax.xml.transform.sax.TransformerHandler
  * interface. It acts as a ContentHandler and LexicalHandler which receives a stream of
  * SAX events representing an input document, and performs a transformation treating this
  * SAX stream as the source document of the transformation.
  * @author Michael H. Kay
  */

public class TransformerHandlerImpl extends ReceivingContentHandler implements TransformerHandler {

    Controller controller;
    Builder builder;
    Receiver receiver;
    /*@Nullable*/ Result result;
    String systemId;
    boolean started = false;

    /**
     * Create a TransformerHandlerImpl and initialise variables. The constructor is protected, because
     * the Filter should be created using newTransformerHandler() in the SAXTransformerFactory
     * class
     * @param controller the Controller to be used
    */

    protected TransformerHandlerImpl(Controller controller) {
        this.controller = controller;
        Configuration config = controller.getConfiguration();
        int validation = controller.getSchemaValidationMode();
        builder = controller.makeBuilder();
        PipelineConfiguration pipe = builder.getPipelineConfiguration();
        ParseOptions options = pipe.getParseOptions();
        options.setCheckEntityReferences(true);
        setPipelineConfiguration(pipe);
        receiver = controller.makeStripper(builder);
        if (controller.getExecutable().stripsInputTypeAnnotations()) {
            receiver = config.getAnnotationStripper(receiver);
        }
        if (validation != Validation.PRESERVE) {
            options.setSchemaValidationMode(validation);
            options.setStripSpace(Whitespace.NONE);
            receiver = config.getDocumentValidator(receiver, getSystemId(), options);
        }
        setReceiver(receiver);
    }

    /**
     * Start of a new document. The TransformerHandler is not serially reusable, so this method
     * must only be called once.
     * @throws SAXException only if an overriding subclass throws this exception
     * @throws UnsupportedOperationException if an attempt is made to reuse the TransformerHandler by calling
     * startDocument() more than once.
     */

    public void startDocument () throws SAXException {
        if (started) {
            throw new UnsupportedOperationException(
                    "The TransformerHandler is not serially reusable. The startDocument() method must be called once only.");
        }
        started = true;
        super.startDocument();
    }

    /**
    * Get the Transformer used for this transformation
    */

    public Transformer getTransformer() {
        return controller;
    }

    /**
     * Set the SystemId of the document. Note that in reporting location information, Saxon gives
     * priority to the system Id reported by the SAX Parser in the Locator passed to the
     * {@link #setDocumentLocator(org.xml.sax.Locator)} method. The SystemId passed to this method
     * is used as the base URI for resolving relative references.
     * @param url the systemId of the source document
    */

    public void setSystemId(String url) {
        systemId = url;
        receiver.setSystemId(url);
    }

    /**
     * Get the systemId of the document. This will be the systemId obtained from the Locator passed to the
     * {@link #setDocumentLocator(org.xml.sax.Locator)} method if available, otherwise the SystemId passed
     * to the {@link #setSystemId(String)} method.
    */

    public String getSystemId() {
        return systemId;
//        String s = super.getSystemId();
//        return (s == null ? systemId : s);
    }


    /**
    * Set the output destination of the transformation
    */

    public void setResult(/*@Nullable*/ Result result) {
        if (result==null) {
            throw new IllegalArgumentException("Result must not be null");
        }
        this.result = result;
    }

    /**
     * Get the output destination of the transformation
     * @return the output destination
    */

    /*@Nullable*/ public Result getResult() {
        return result;
    }

    /**
    * Override the behaviour of endDocument() in ReceivingContentHandler, so that it fires off
    * the transformation of the constructed document
    */

    public void endDocument() throws SAXException {
        super.endDocument();
        DocumentInfo doc = (DocumentInfo)builder.getCurrentRoot();
        builder.reset();
        if (doc==null) {
            throw new SAXException("No source document has been built");
        }

        try {
            controller.transformDocument(doc, result);
        } catch (TransformerException err) {
            if (err instanceof XPathException) {
                controller.reportFatalError((XPathException)err);
            }
            throw new SAXException(err);
        }
    }

}

