////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Controller;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;


/**
  * <b>TransformerReceiver</b> is similar in concept to the JAXP TransformerHandler,
 * except that it implements Saxon's Receiver interface rather than the standard
 * SAX2 interface. This means that it allows nodes with type annotations to be
 * passed down a pipeline from one transformation to another.
  */

public class TransformerReceiver extends ProxyReceiver {

    Controller controller;
    Builder builder;
    Result result;

    /**
     * Create a TransformerReceiver and initialise variables.
     * @param controller the Controller (Saxon's implementation of the JAXP {@link javax.xml.transform.Transformer})
     */

    public TransformerReceiver(Controller controller) {
        super(controller.makeBuilder());
        this.controller = controller;
        this.builder = (Builder)getUnderlyingReceiver();
    }

    /**
     * Start of event stream
     */

    public void open() throws XPathException {
        builder.setSystemId(systemId);
        Receiver stripper = controller.makeStripper(builder);
        if (controller.getExecutable().stripsInputTypeAnnotations()) {
            stripper = controller.getConfiguration().getAnnotationStripper(stripper);
        }
        setUnderlyingReceiver(stripper);
        nextReceiver.open();
    }

    /**
     * Get the Transformer used for this transformation
     * @return the transformer (which will always be an instance of {@link net.sf.saxon.Controller})
    */

    public Transformer getTransformer() {
        return controller;
    }

    /**
    * Set the SystemId of the document
    */

    public void setSystemId(String systemId) {
        super.setSystemId(systemId);
        controller.setBaseOutputURI(systemId);
    }

    /**
     * Set the output destination of the transformation. This method must be called before
     * the transformation can proceed.
     * @param result the destination to which the transformation output will be written
     */

    public void setResult(Result result) {
        this.result = result;
    }

    /**
    * Get the output destination of the transformation
     * @return the output destination. May be null if no destination has been set.
    */

    /*@Nullable*/ public Result getResult() {
        return result;
    }

    /**
    * Override the behaviour of close() in ProxyReceiver, so that it fires off
    * the transformation of the constructed document
    */

    public void close() throws XPathException {
        nextReceiver.close();
        DocumentInfo doc = (DocumentInfo)builder.getCurrentRoot();
        builder.reset();
        builder = null;
        if (doc==null) {
            throw new XPathException("No source document has been built");
        }
        if (result==null) {
            throw new XPathException("No output destination has been supplied");
        }
        try {
            controller.transformDocument(doc, result);
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }




}

