////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.evpull.EventIteratorToReceiver;
import net.sf.saxon.evpull.PullEventSource;
import net.sf.saxon.expr.number.Numberer_en;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.pull.PullPushCopier;
import net.sf.saxon.pull.PullSource;
import net.sf.saxon.pull.StaxBridge;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.*;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
* Sender is a helper class that sends events to a Receiver from any kind of Source object
*/

public abstract class Sender {

    // Converted to an abstract static class in Saxon 9.3

    private static Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static Class staxSourceClass;

    static {
        try {
            staxSourceClass = Class.forName("javax.xml.transform.stax.StAXSource");
        } catch (Exception err) {
            // no action; if StAXSource isn't available then we don't use it.
        }
    }

    private Sender() {}

    /**
     * Send the contents of a Source to a Receiver.
     * @param source the source to be copied. Note that if the Source contains an InputStream
     * or Reader then it will be left open, unless it is an AugmentedSource with the pleaseCloseAfterUse
     * flag set. On the other hand, if it contains a URI that needs to be dereferenced to obtain
     * an InputStream, then the InputStream will be closed after use.
     * @param receiver the destination to which it is to be copied. The pipelineConfiguration
     * of this receiver must have been initialized.
     * @param options Parse options. If source is an AugmentedSource, any options set in the
     * AugmentedSource are used in preference to those set in options. If neither specifies
     * a particular option, the defaults from the Configuration are used. If null is supplied,
     * the parse options from the PipelineConfiguration of the receiver are used.
     * @throws net.sf.saxon.trans.XPathException if any error occurs
     */

    public static void send(Source source, Receiver receiver, /*@Nullable*/ ParseOptions options)
    throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        if (options == null) {
            options = new ParseOptions(pipe.getParseOptions());
        } else {
            options = new ParseOptions(options);
        }
        if (source instanceof AugmentedSource) {
            options.merge(((AugmentedSource)source).getParseOptions());
            source = ((AugmentedSource)source).getContainedSource();
        }
        Configuration config = pipe.getConfiguration();
        options.applyDefaults(config);

        receiver.setSystemId(source.getSystemId());
        Receiver next = receiver;

        int schemaValidation = options.getSchemaValidationMode();

        List<FilterFactory> filters = options.getFilters();
        if (filters != null) {
            for (int i=filters.size()-1; i>=0; i--) {
                ProxyReceiver filter = filters.get(i).makeFilter(next);
                filter.setSystemId(source.getSystemId());
                next = filter;
            }
        }

        if (options.getStripSpace() == Whitespace.ALL) {
            next = new Stripper(AllElementsSpaceStrippingRule.getInstance(), receiver);
        } else if (options.getStripSpace() == Whitespace.XSLT) {
            Controller controller = pipe.getController();
            if (controller != null) {
                next = controller.makeStripper(next);
            }
        }

        if (source instanceof NodeInfo) {
            NodeInfo ns = (NodeInfo)source;
            String baseURI = ns.getBaseURI();
            if (schemaValidation != Validation.PRESERVE) {
                next = config.getDocumentValidator(next, baseURI, options);
            }

            int kind = ns.getNodeKind();
            if (kind != Type.DOCUMENT && kind != Type.ELEMENT) {
                throw new IllegalArgumentException("Sender can only handle document or element nodes");
            }
            next.setSystemId(baseURI);
            sendDocumentInfo(ns, next);
            return;

        } else if (source instanceof PullSource) {
            sendPullSource((PullSource)source, next, options);
            return;

        } else if (source instanceof PullEventSource) {
            sendPullEventSource((PullEventSource)source, next, options);
            return;

        } else if (source instanceof EventSource) {
            ((EventSource)source).send(next);
            return;

        } else if (source instanceof Transmitter) {
            ((Transmitter)source).transmit(next);
            return;

        } else if (source instanceof SAXSource) {
            sendSAXSource((SAXSource)source, next, options);
            return;

        } else if (source instanceof StreamSource) {
            StreamSource ss = (StreamSource)source;
            // Following code allows the .NET platform to use a Pull parser
            boolean dtdValidation = options.getDTDValidationMode() == Validation.STRICT;
            Source ps = Configuration.getPlatform().getParserSource(
                    pipe, ss, schemaValidation, dtdValidation, options.getStripSpace());
            if (ps == ss) {
                String url = source.getSystemId();
                InputSource is = new InputSource(url);
                is.setCharacterStream(ss.getReader());
                is.setByteStream(ss.getInputStream());
                boolean reuseParser = false;
                XMLReader parser = options.getXMLReader();
                if (parser == null) {
                    parser = config.getSourceParser();
                    if (options.getEntityResolver() != null && parser.getEntityResolver() == null) {
                        parser.setEntityResolver(options.getEntityResolver());
                    }
                    reuseParser = true;
                }
                //System.err.println("Using parser: " + parser.getClass().getName());
                SAXSource sax = new SAXSource(parser, is);
                sax.setSystemId(source.getSystemId());
                sendSAXSource(sax, next, options);
                if (reuseParser) {
                    config.reuseSourceParser(parser);
                }

            } else {
                // the Platform substituted a different kind of source
                // On .NET with a default URIResolver we can expect an AugnmentedSource wrapping a PullSource
                send(ps, next, options);
            }
            return;
        } else if (staxSourceClass != null && staxSourceClass.isAssignableFrom(source.getClass())) {
            // Test for a StAXSource
            // Use reflection to avoid problems if JAXP 1.4 not installed
            XMLStreamReader reader = null;
            try {
                Method getReaderMethod = staxSourceClass.getMethod("getXMLStreamReader", EMPTY_CLASS_ARRAY);
                reader = (XMLStreamReader)getReaderMethod.invoke(source);
            } catch (Exception e) {
                // no action
            }
            //XMLStreamReader reader = ((StAXSource)source).getXMLStreamReader();
            if (reader == null) {
                throw new XPathException("Saxon can only handle a StAXSource that wraps an XMLStreamReader");
            }
            StaxBridge bridge = new StaxBridge();
            bridge.setXMLStreamReader(reader);
            sendPullSource(new PullSource(bridge), next, options);
            return;
        } else {
            next = makeValidator(next, source.getSystemId(), options);

            // See if there is a registered SourceResolver than can handle it
            Source newSource = config.getSourceResolver().resolveSource(source, config);
            if (newSource instanceof StreamSource ||
                    newSource instanceof SAXSource ||
                    newSource instanceof Transmitter ||
                    newSource instanceof NodeInfo ||
                    newSource instanceof PullSource ||
                    newSource instanceof AugmentedSource ||
                    newSource instanceof EventSource) {
                send(newSource, next, options);
            }

            // See if there is a registered external object model that knows about this kind of source
            // (Note, this should pick up the platform-specific DOM model)

            List externalObjectModels = config.getExternalObjectModels();
            for (Object externalObjectModel : externalObjectModels) {
                ExternalObjectModel model = (ExternalObjectModel) externalObjectModel;
                boolean done = model.sendSource(source, next);
                if (done) {
                    return;
                }
            }

        }

        throw new XPathException("A source of type " + source.getClass().getName() +
                " is not supported in this environment");
    }

    /**
     * Send a copy of a Saxon NodeInfo representing a document or element node to a receiver
     * @param top the root of the subtree to be send. Despite the method name, this can be a document
     * node or an element node
     * @param receiver the destination to receive the events
     * @throws XPathException if any error occurs
     */


    private static void sendDocumentInfo(NodeInfo top, Receiver receiver)
    throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        NamePool targetNamePool = pipe.getConfiguration().getNamePool();
        if (top.getNamePool() != targetNamePool) {
            // This code allows a document in one Configuration to be copied to another, changing
            // namecodes as necessary
            receiver = new NamePoolConverter(receiver, top.getNamePool(), targetNamePool);
        }
        LocationCopier copier = new LocationCopier(top instanceof DocumentInfo);
        pipe.setComponent(CopyInformee.class.getName(), copier);
        pipe.setLocationProvider(copier);

        // start event stream
        receiver.open();

        // copy the contents of the document
        receiver.startDocument(0);
        top.copy(receiver, (CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS), 0);
        receiver.endDocument();

        // end event stream
        receiver.close();
    }

    /**
     * Send the contents of a SAXSource to a given Receiver
     * @param source the SAXSource
     * @param receiver the destination Receiver
     * @param options options for parsing the SAXSource
     * @throws XPathException if any failure occurs processing the Source object
     */

    private static void sendSAXSource(SAXSource source, Receiver receiver, ParseOptions options)
    throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        XMLReader parser = source.getXMLReader();
        boolean reuseParser = false;
        final Configuration config = pipe.getConfiguration();
        ErrorListener listener = options.getErrorListener();
        if (listener == null) {
            listener = pipe.getErrorListener();
        }
        ErrorHandler errorHandler = options.getErrorHandler();
        if (errorHandler == null) {
            errorHandler = new StandardErrorHandler(listener);
        }
        if (parser==null) {
            parser = options.getXMLReader();
        }
        if (parser==null) {
            SAXSource ss = new SAXSource();
            ss.setInputSource(source.getInputSource());
            ss.setSystemId(source.getSystemId());
            parser = config.getSourceParser();
            parser.setErrorHandler(errorHandler);
            if (options.getEntityResolver() != null && parser.getEntityResolver() == null) {
                parser.setEntityResolver(options.getEntityResolver());
            }
            ss.setXMLReader(parser);
            source = ss;
            reuseParser = true;
        } else {
            // user-supplied parser: ensure that it meets the namespace requirements
            configureParser(parser);
            if (parser.getErrorHandler() == null) {
                parser.setErrorHandler(errorHandler);
            }
        }

        if (!pipe.isExpandAttributeDefaults()) { //TODO: put this in ParseOptions
            try {
                parser.setFeature("http://xml.org/sax/features/use-attributes2", true);
            } catch (SAXNotRecognizedException err) {
                // ignore the failure, we did our best (Xerces gives us an Attribute2 even though it
                // doesn't recognize this request!)
            } catch (SAXNotSupportedException err) {
                // ignore the failure, we did our best
            }
        }

        boolean dtdValidation = (options.getDTDValidationMode() == Validation.STRICT ||
                options.getDTDValidationMode() == Validation.LAX);
        boolean dtdRecover = options.getDTDValidationMode() == Validation.LAX;
        try {
            parser.setFeature("http://xml.org/sax/features/validation", dtdValidation);
        } catch (SAXNotRecognizedException err) {
            if (dtdValidation) {
                throw new XPathException("XML Parser does not recognize request for DTD validation", err);
            }
        } catch (SAXNotSupportedException err) {
            if (dtdValidation) {
                throw new XPathException("XML Parser does not support DTD validation", err);
            }
        }

        Map<String, Boolean> parserFeatures = options.getParserFeatures();
        Map<String, String> parserProperties = options.getParserProperties();

        if(parserFeatures != null) {
            for(Map.Entry<String, Boolean> entry : parserFeatures.entrySet()){
                try {
                    parser.setFeature(entry.getKey(), entry.getValue());
                } catch (SAXNotRecognizedException err) {
                    config.getStandardErrorOutput().println("XML Parser does not recognize the feature "+entry.getKey());
                } catch (SAXNotSupportedException err) {
                    config.getStandardErrorOutput().println("XML Parser does not support the feature "+entry.getKey());
                }
            }
        }

        if(parserProperties != null) {
           for(Map.Entry<String, String> entry : parserProperties.entrySet()) {
               try {
                   parser.setProperty(entry.getKey(), entry.getValue());
               } catch (SAXNotRecognizedException err) {
                   config.getStandardErrorOutput().println("XML Parser does not recognize the property "+entry.getKey());
               } catch (SAXNotSupportedException err) {
                   config.getStandardErrorOutput().println("XML Parser does not support the property "+entry.getKey());
               }

           }



        }

        boolean xInclude = options.isXIncludeAware();
        if (xInclude) {
            boolean tryAgain = false;
            try {
                // This feature name is supported in the version of Xerces bundled with JDK 1.5
                parser.setFeature("http://apache.org/xml/features/xinclude-aware", true);
            } catch (SAXNotRecognizedException err) {
                tryAgain = true;
            } catch (SAXNotSupportedException err) {
                tryAgain = true;
            }
            if (tryAgain) {
                try {
                    // This feature name is supported in Xerces 2.9.0
                    parser.setFeature("http://apache.org/xml/features/xinclude", true);
                } catch (SAXNotRecognizedException err) {
                    throw new XPathException("Selected XML parser " + parser.getClass().getName() +
                            " does not recognize request for XInclude processing", err);
                } catch (SAXNotSupportedException err) {
                    throw new XPathException("Selected XML parser " + parser.getClass().getName() +
                            " does not support XInclude processing", err);
                }
            }
        }
//        if (config.isTiming()) {
//            System.err.println("Using SAX parser " + parser);
//        }



        receiver = makeValidator(receiver, source.getSystemId(), options);

        // Reuse the previous ReceivingContentHandler if possible (it contains a useful cache of names)

        ReceivingContentHandler ce;
        final ContentHandler ch = parser.getContentHandler();
        if (ch instanceof ReceivingContentHandler && config.isCompatible(((ReceivingContentHandler)ch).getConfiguration())) {
            ce = (ReceivingContentHandler)ch;
            ce.reset();
        } else {
            ce = new ReceivingContentHandler();
            parser.setContentHandler(ce);
            parser.setDTDHandler(ce);
            try {
                parser.setProperty("http://xml.org/sax/properties/lexical-handler", ce);
            } catch (SAXNotSupportedException err) {    // this just means we won't see the comments
                // ignore the error
            } catch (SAXNotRecognizedException err) {
                // ignore the error
            }
        }
//        TracingFilter tf = new TracingFilter();
//        tf.setUnderlyingReceiver(receiver);
//        tf.setPipelineConfiguration(pipe);
//        receiver = tf;

        ce.setReceiver(receiver);
        ce.setPipelineConfiguration(pipe);
    
        try {
            parser.parse(source.getInputSource());
        } catch (SAXException err) {
            Exception nested = err.getException();
            if (nested instanceof XPathException) {
                throw (XPathException)nested;
            } else if (nested instanceof RuntimeException) {
                throw (RuntimeException)nested;
            } else {
                // Check for a couple of conditions where the error reporting needs to be improved.
                // (a) The built-in parser for JDK 1.6 has a nasty habit of not notifying errors to the ErrorHandler
                // (b) Sometimes (e.g. when given an empty file), the SAXException has no location information
                if ((errorHandler instanceof StandardErrorHandler && ((StandardErrorHandler)errorHandler).getFatalErrorCount() == 0) ||
                        (err instanceof SAXParseException && ((SAXParseException)err).getSystemId()==null && source.getSystemId() != null)) {
                    //
                    XPathException de = new XPathException("Error reported by XML parser processing " +
                            source.getSystemId() + ": " + err.getMessage(), err);
                    try {
                        listener.fatalError(de);
                        de.setHasBeenReported(true);
                    } catch (TransformerException e) {
                        //
                    }
                    throw de;
                } else {
                    XPathException de = new XPathException(err);
                    de.setErrorCode(SaxonErrorCode.SXXP0003);
                    de.setHasBeenReported(true);
                    throw de;
                }
            }
        } catch (java.io.IOException err) {
            throw new XPathException("I/O error reported by XML parser processing " +
                            source.getSystemId() + ": " + err.getMessage(), err);
        }
        if (errorHandler instanceof StandardErrorHandler) {
            int errs = ((StandardErrorHandler)errorHandler).getFatalErrorCount();
            if (errs > 0) {
                throw new XPathException("The XML parser reported " + errs + (errs == 1 ? " error" : " errors"));
            }
            errs = ((StandardErrorHandler)errorHandler).getErrorCount();
            if (errs > 0) {
                String message = ("The XML parser reported " + new Numberer_en().toWords(errs).toLowerCase() +
                        " validation error" + (errs == 1 ? "" : "s"));
                if (dtdRecover) {
                    message += ". Processing continues, because recovery from validation errors was requested";
                    try {
                        listener.warning(new XPathException(message));
                    } catch (TransformerException e) {
                        //
                    }
                } else {
                    throw new XPathException(message);
                }
            }
        }
        if (reuseParser) {
            config.reuseSourceParser(parser);
        }
    }

    private static Receiver makeValidator(Receiver receiver, String systemId, ParseOptions options) throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        Configuration config = pipe.getConfiguration();
        int sv = options.getSchemaValidationMode();
        if (sv != Validation.PRESERVE && sv != Validation.DEFAULT) {
            Controller controller = pipe.getController();
            if (controller != null && !controller.getExecutable().isSchemaAware() && sv != Validation.STRIP) {
                throw new XPathException("Cannot use schema-validated input documents when the query/stylesheet is not schema-aware");
            }
            // Add a document validator to the pipeline

//            ValidationContext vc = new ValidationContext(config.getConversionRules());
//            vc.setController(controller);
//            vc.setErrorLimit(options.getValidationErrorLimit());
//            vc.setValidationParams(options.getValidationParams());
            receiver = config.getDocumentValidator(receiver, systemId, options);
        }
        return receiver;
    }

    /**
     * Copy data from a pull source to a push destination
     * @param source the pull source
     * @param receiver the push destination
     * @param options provides options for schema validation
     * @throws XPathException if any error occurs
     */

    private static void sendPullSource(PullSource source, Receiver receiver, ParseOptions options)
            throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        boolean xInclude = options.isXIncludeAware();
        if (xInclude) {
            throw new XPathException("XInclude processing is not supported with a pull parser");
        }
        receiver = makeValidator(receiver, source.getSystemId(), options);

        PullProvider provider = source.getPullProvider();
        if (provider instanceof LocationProvider) {
            pipe.setLocationProvider((LocationProvider)provider);
        }
        provider.setPipelineConfiguration(pipe);
        receiver.setPipelineConfiguration(pipe);
        PullPushCopier copier = new PullPushCopier(provider, receiver);
        try {
            copier.copy();
        } finally {
            if (options.isPleaseCloseAfterUse()) {
                provider.close();
            }
        }
    }

    private static void sendPullEventSource(PullEventSource source, Receiver receiver, ParseOptions options)
            throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        boolean xInclude = options.isXIncludeAware();
        if (xInclude) {
            throw new XPathException("XInclude processing is not supported with a pull parser");
        }
        receiver = makeValidator(receiver, source.getSystemId(), options);

        receiver.open();
        EventIterator provider = source.getEventIterator();
        if (provider instanceof LocationProvider) {
            pipe.setLocationProvider((LocationProvider)provider);
        }
        receiver.setPipelineConfiguration(pipe);
        SequenceReceiver out = nextRealReceiver(receiver) instanceof SequenceReceiver
                ? ((SequenceReceiver)receiver)
                : new TreeReceiver(receiver);
        EventIteratorToReceiver.copy(provider, out);
        receiver.close();
    }

    private static Receiver nextRealReceiver(Receiver receiver) {
        if(receiver instanceof ProxyReceiver) {
            return nextRealReceiver(((ProxyReceiver) receiver).getUnderlyingReceiver());
        }
        return receiver;
    }


    /**
     * Configure a SAX parser to ensure it has the correct namesapce properties set
     * @param parser the parser to be configured
     * @throws net.sf.saxon.trans.XPathException if the parser cannot be configured to the
     * required settings (namespaces=true, namespace-prefixes=false). Note that the SAX
     * specification requires every XMLReader to support these settings, so this error implies
     * that the XMLReader is non-conformant; this is not uncommon in cases where the XMLReader
     * is user-written.
     */

    public static void configureParser(XMLReader parser) throws XPathException {
        try {
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
        } catch (SAXNotSupportedException err) {    // SAX2 parsers MUST support this feature!
            throw new XPathException(
                "The SAX2 parser " + parser.getClass().getName() +
                        " does not recognize the 'namespaces' feature", err);
        } catch (SAXNotRecognizedException err) {
            throw new XPathException(
                "The SAX2 parser " + parser.getClass().getName() +
                        " does not support setting the 'namespaces' feature to true", err);
        }

        try {
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        } catch (SAXNotSupportedException err) {    // SAX2 parsers MUST support this feature!
            throw new XPathException(
                "The SAX2 parser "+ parser.getClass().getName() +
                        " does not recognize the 'namespace-prefixes' feature", err);
        } catch (SAXNotRecognizedException err) {
            throw new XPathException(
                "The SAX2 parser " + parser.getClass().getName() +
                        " does not support setting the 'namespace-prefixes' feature to false", err);
        }

    }

}

