////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.query.SequenceWrapper;
import net.sf.saxon.serialize.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.Serializable;
import java.util.List;
import java.util.Properties;

/**
 * Helper class to construct a serialization pipeline for a given result destination
 * and a given set of output properties. The pipeline is represented by a Receiver object
 * to which result tree events are sent.
 * <p/>
 * Since Saxon 8.8 is is possible to write a subclass of SerializerFactory and register it
 * with the Configuration, allowing customisation of the Serializer pipeline.
 * <p/>
 * The class includes methods for instantiating each of the components used on the Serialization
 * pipeline. This allows a customized SerializerFactory to replace any or all of these components
 * by subclasses that refine the behaviour.
 */

public class SerializerFactory implements Serializable {

    Configuration config;

    private static Class staxResultClass;

    static {
        try {
            staxResultClass = Class.forName("javax.xml.transform.stax.StAXResult");
        } catch (Exception err) {
            // no action; if StAXSource isn't available then we don't use it.
        }
    }

    /**
     * Create a SerializerFactory
     *
     * @param config the Saxon Configuration
     */

    public SerializerFactory(Configuration config) {
        this.config = config;
    }

    /**
     * Create a serializer with given output properties, and return
     * an XMLStreamWriter that can be used to feed events to the serializer.
     *
     * @param result     the destination of the serialized output (wraps a Writer, an OutputStream, or a File)
     * @param properties the serialization properties to be used
     * @return a serializer in the form of an XMLStreamWriter
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs
     */

    public StreamWriterToReceiver getXMLStreamWriter(
            StreamResult result,
            Properties properties) throws XPathException {
        Receiver r = getReceiver(result, config.makePipelineConfiguration(), properties);
        return new StreamWriterToReceiver(r);
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p/>
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     *
     * @param result The final destination of the serialized output. Usually a StreamResult,
     *               but other kinds of Result are possible.
     * @param pipe   The PipelineConfiguration.
     * @param props  The serialization properties. If this includes the property {@link SaxonOutputKeys#USE_CHARACTER_MAPS}
     *               then the PipelineConfiguration must contain a non-null Controller, and the Executable associated with this Controller
     *               must have a CharacterMapIndex which is used to resolve the names of the character maps appearing in this property.
     * @return the newly constructed Receiver that performs the required serialization
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     */

    public Receiver getReceiver(Result result,
                                PipelineConfiguration pipe,
                                Properties props)
            throws XPathException {
        if (pipe.getController() != null) {
            return getReceiver(result, pipe, props, pipe.getController().getExecutable().getCharacterMapIndex());
        } else {
            return getReceiver(result, pipe, props, null);
        }
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p/>
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     *
     * @param result       The final destination of the serialized output. Usually a StreamResult,
     *                     but other kinds of Result are possible.
     * @param pipe         The PipelineConfiguration.
     * @param props        The serialization properties
     * @param charMapIndex The index of character maps. Required if any of the serialization properties
     *                     is {@link SaxonOutputKeys#USE_CHARACTER_MAPS}, in which case the named character maps listed in that
     *                     property must be present in the index of character maps.
     * @return the newly constructed Receiver that performs the required serialization
     */

    public Receiver getReceiver(Result result,
                                PipelineConfiguration pipe,
                                Properties props,
                                /*@Nullable*/ CharacterMapIndex charMapIndex)
            throws XPathException {
        if (result instanceof Emitter) {
            if (((Emitter) result).getOutputProperties() == null) {
                ((Emitter) result).setOutputProperties(props);
            }
            return (Emitter) result;
        } else if (result instanceof Receiver) {
            Receiver receiver = (Receiver) result;
            receiver.setSystemId(result.getSystemId());
            receiver.setPipelineConfiguration(pipe);
            return receiver;
        } else if (result instanceof SAXResult) {
            ContentHandlerProxy proxy = newContentHandlerProxy();
            proxy.setUnderlyingContentHandler(((SAXResult) result).getHandler());
            proxy.setPipelineConfiguration(pipe);
            proxy.setOutputProperties(props);
            if ("yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
                if (pipe.getConfiguration().isCompileWithTracing()) {
                    pipe.getController().addTraceListener(proxy.getTraceListener());
                } else {
                    XPathException de = new XPathException("Cannot use saxon:supply-source-locator unless tracing was enabled at compile time");
                    de.setErrorCode(SaxonErrorCode.SXSE0002);
                    throw de;
                }
            }
            //proxy.open();
            return proxy;
        } else if (result instanceof StreamResult) {

            // The "target" is the start of the output pipeline, the Receiver that
            // instructions will actually write to (except that other things like a
            // NamespaceReducer may get added in front of it). The "emitter" is the
            // last thing in the output pipeline, the Receiver that actually generates
            // characters or bytes that are written to the StreamResult.

            Receiver target;
            String method = props.getProperty(OutputKeys.METHOD);
            if (method == null) {
                return newUncommittedSerializer(result, new Sink(pipe), props);
            }

            Emitter emitter = null;

            CharacterMapExpander characterMapExpander = null;
            String useMaps = props.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
            if (useMaps != null) {
                if (charMapIndex == null) {
                    XPathException de = new XPathException("Cannot use character maps in an environment with no Controller");
                    de.setErrorCode(SaxonErrorCode.SXSE0001);
                    throw de;
                }
                characterMapExpander = charMapIndex.makeCharacterMapExpander(useMaps, new Sink(pipe), this);
            }

            ProxyReceiver normalizer = null;
            String normForm = props.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
            if (normForm != null && !normForm.equals("none")) {
                normalizer = newUnicodeNormalizer(new Sink(pipe), props);
            }

            if ("html".equals(method)) {
                emitter = newHTMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createHTMLSerializer(emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("xml".equals(method)) {
                emitter = newXMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createXMLSerializer((XMLEmitter) emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("xhtml".equals(method)) {
                emitter = newXHTMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createXHTMLSerializer(emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("text".equals(method)) {
                emitter = newTEXTEmitter();
                emitter.setPipelineConfiguration(pipe);
                target = createTextSerializer(emitter, props, characterMapExpander, normalizer);

            } else if (method.startsWith("{" + NamespaceConstant.SAXON + "}")) {
                target = createSaxonSerializationMethod(method, props, pipe, characterMapExpander, normalizer);
                if (target instanceof Emitter) {
                    emitter = (Emitter) target;
                }

            } else {
                Receiver userReceiver;
                if (pipe == null) {
                    throw new XPathException("Unsupported serialization method " + method);
                } else {
                    userReceiver = createUserDefinedOutputMethod(method, props, pipe);
                    target = userReceiver;
                    if (userReceiver instanceof Emitter) {
                        emitter = (Emitter) userReceiver;
                    } else {
                        return userReceiver;
                    }
                }
            }
            if (emitter != null) {
                emitter.setOutputProperties(props);
                StreamResult sr = (StreamResult) result;
                emitter.setStreamResult(sr);
            }
            return target;

        } else if (staxResultClass != null && staxResultClass.isAssignableFrom(result.getClass())) {
            StAXResultHandler handler = (StAXResultHandler) config.getDynamicLoader().getInstance("net.sf.saxon.stax.StAXResultHandlerImpl", getClass().getClassLoader());
            Receiver r = handler.getReceiver(result, props);
            r.setPipelineConfiguration(pipe);
            return r;
        } else {
            if (pipe != null) {
                // try to find an external object model that knows this kind of Result
                List externalObjectModels = pipe.getConfiguration().getExternalObjectModels();
                for (Object externalObjectModel : externalObjectModels) {
                    ExternalObjectModel model = (ExternalObjectModel) externalObjectModel;
                    Receiver builder = model.getDocumentBuilder(result);
                    if (builder != null) {
                        builder.setSystemId(result.getSystemId());
                        builder.setPipelineConfiguration(pipe);
                        return builder;
                    }
                }
            }
        }

        throw new IllegalArgumentException("Unknown type of result: " + result.getClass());
    }

    /**
     * Create a serialization pipeline to implement the HTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected Receiver createHTMLSerializer(
            Emitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;
        target = emitter;
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newHTMLIndenter(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        return target;
    }

    /**
     * Create a serialization pipeline to implement the text output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newTEXTEmitter}
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected Receiver createTextSerializer(
            Emitter emitter, Properties props,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;
        target = emitter;
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            characterMapExpander.setUseNullMarkers(false);
            target = characterMapExpander;
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        target = addTextOutputFilter(target, props);
        return target;
    }

    /**
     * Create a serialization pipeline to implement the XHTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected Receiver createXHTMLSerializer(
            Emitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        // Ensure that the XHTML namespace is registered in the NamePool. Without this, the meta-tag insertion can fail
        pipe.getConfiguration().getNamePool().allocateCodeForURI(NamespaceConstant.XHTML);
        Receiver target;
        target = emitter;
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newXHTMLIndenter(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            characterMapExpander.setPipelineConfiguration(pipe);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }

        if (SaxonOutputKeys.isHtmlVersion5(props)) {
            target = addHtml5Component(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newXHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newXHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        return target;
    }

    public Receiver addHtml5Component(Receiver next, Properties outputProperties) {
        return next;
    }

    /**
     * Create a serialization pipeline to implement the XML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected Receiver createXMLSerializer(
            XMLEmitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;

        if ("yes".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newXMLIndenter(emitter, props);
        } else {
            target = emitter;
        }
        if ("1.0".equals(props.getProperty(OutputKeys.VERSION)) &&
                pipe.getConfiguration().getXMLVersion() == Configuration.XML11) {
            // Check result meets XML 1.0 constraints if configuration allows XML 1.1 input but
            // this result document must conform to 1.0
            target = newXML10ContentChecker(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        // Adding a NamespaceReducer in 9.4 because otherwise analyze-string-001 outputs a redundant xmlns="" undeclaration,
        // as a consequence of the change to TreeReceiver introduced in response to bug 5857
        //target = new NamespaceReducer(target);
        return target;
    }

    protected Receiver createSaxonSerializationMethod(
            String method, Properties props,
            PipelineConfiguration pipe, CharacterMapExpander characterMapExpander,
            ProxyReceiver normalizer) throws XPathException {
        throw new XPathException("Saxon serialization methods require Saxon-PE to be enabled");
    }

    /**
     * Create a serialization pipeline to implement a user-defined output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param method the name of the user-defined output method, as a QName in Clark format
     *               (that is "{uri}local").
     * @param props  the serialization properties
     * @param pipe   the pipeline configuration information
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected Receiver createUserDefinedOutputMethod(String method, Properties props, PipelineConfiguration pipe) throws XPathException {
        Receiver userReceiver;// See if this output method is recognized by the Configuration
        userReceiver = pipe.getConfiguration().makeEmitter(method, props);
        userReceiver.setPipelineConfiguration(pipe);
        if (userReceiver instanceof ContentHandlerProxy &&
                "yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
            if (pipe.getConfiguration().isCompileWithTracing()) {
                pipe.getController().addTraceListener(
                        ((ContentHandlerProxy) userReceiver).getTraceListener());
            } else {
                XPathException de = new XPathException(
                        "Cannot use saxon:supply-source-locator unless tracing was enabled at compile time");
                de.setErrorCode(SaxonErrorCode.SXSE0002);
                throw de;
            }
        }
        return userReceiver;
    }


    /**
     * Create a ContentHandlerProxy. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created ContentHandlerProxy.
     */

    protected ContentHandlerProxy newContentHandlerProxy() {
        return new ContentHandlerProxy();
    }

    /**
     * Create an UncommittedSerializer. This method exists so that it can be overridden in a subclass.
     *
     * @param result     the result destination
     * @param next       the next receiver in the pipeline
     * @param properties the serialization properties
     * @return the newly created UncommittedSerializer.
     */

    protected UncommittedSerializer newUncommittedSerializer(Result result, Receiver next, Properties properties) {
        return new UncommittedSerializer(result, next, properties);
    }

    /**
     * Create a new XML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XML emitter.
     */

    protected Emitter newXMLEmitter(Properties properties) {
        return new XMLEmitter();
    }

    /**
     * Create a new HTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created HTML emitter.
     */

    protected Emitter newHTMLEmitter(Properties properties) {
        HTMLEmitter emitter;
        // Note, we recognize html-version even when running XSLT 2.0.
        if (SaxonOutputKeys.isHtmlVersion5orVersion5(properties)) {
            emitter = new HTML50Emitter();
        } else {
            emitter = new HTML40Emitter();
        }
        return emitter;
    }

    /**
     * Create a new XHTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XHTML emitter.
     */

    protected Emitter newXHTMLEmitter(Properties properties) {
        return new XHTML1Emitter();
    }

    /**
     * Add a filter to the text output method pipeline. This does nothing unless overridden
     * in a superclass
     *
     * @param next       the next receiver (typically the TextEmitter)
     * @param properties the output properties
     * @return the receiver to be used in place of the "next" receiver
     */

    public Receiver addTextOutputFilter(Receiver next, Properties properties) throws XPathException {
        return next;
    }

    /**
     * Create a new Text Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created text emitter.
     */

    protected Emitter newTEXTEmitter() {
        return new TEXTEmitter();
    }


    /**
     * Create a new XML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML indenter.
     */

    protected ProxyReceiver newXMLIndenter(XMLEmitter next, Properties outputProperties) {
        XMLIndenter r = new XMLIndenter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new HTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML indenter.
     */

    protected ProxyReceiver newHTMLIndenter(Receiver next, Properties outputProperties) {
        return new HTMLIndenter(next, "html");
    }

    /**
     * Create a new XHTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML indenter.
     */

    protected ProxyReceiver newXHTMLIndenter(Receiver next, Properties outputProperties) {
        return new HTMLIndenter(next, "xhtml");
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newXHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setOutputProperties(outputProperties);
        r.setIsXHTML(true);
        return r;
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setOutputProperties(outputProperties);
        r.setIsXHTML(false);
        return r;
    }

    /**
     * Create a new HTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new HTMLURIEscaper(next);
    }

    /**
     * Create a new XHTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newXHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new XHTMLURIEscaper(next);
    }


    /**
     * Create a new CDATA Filter, responsible for insertion of CDATA sections where required.
     * This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created CDATA filter.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newCDATAFilter(Receiver next, Properties outputProperties) throws XPathException {
        CDATAFilter r = new CDATAFilter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new AttributeSorter, responsible for sorting of attributes into a specified order.
     * This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created CDATA filter.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newAttributeSorter(Receiver next, Properties outputProperties) throws XPathException {
        AttributeSorter r = new AttributeSorter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }


    /**
     * Create a new XML 1.0 content checker, responsible for checking that the output conforms to
     * XML 1.0 rules (this is used only if the Configuration supports XML 1.1 but the specific output
     * file requires XML 1.0). This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML 1.0 content checker.
     */

    protected ProxyReceiver newXML10ContentChecker(Receiver next, Properties outputProperties) {
        return new XML10ContentChecker(next);
    }

    /**
     * Create a Unicode Normalizer. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created Unicode normalizer.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newUnicodeNormalizer(Receiver next, Properties outputProperties) throws XPathException {
        String normForm = outputProperties.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
        return new UnicodeNormalizer(normForm, next);
    }

    /**
     * Create a new CharacterMapExpander. This method exists so that it can be overridden in a subclass.
     *
     * @param next the next receiver in the pipeline
     * @return the newly created CharacterMapExpander.
     */

    public CharacterMapExpander newCharacterMapExpander(Receiver next) {
        return new CharacterMapExpander(next);
    }

    /**
     * Prepare another stylesheet to handle the output of this one.
     * <p/>
     * This method is intended for internal use, to support the
     * <code>saxon:next-in-chain</code> extension.
     *
     * @param controller the current transformation
     * @param href       URI of the next stylesheet to be applied
     * @param baseURI    base URI for resolving href if it's a relative
     *                   URI
     * @param result     the output destination of the current stylesheet
     * @return a replacement destination for the current stylesheet
     * @throws XPathException if any dynamic error occurs
     */

    public Result prepareNextStylesheet(Controller controller, String href, String baseURI, Result result)
            throws TransformerException {
        controller.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "saxon:next-in-chain");
        return null;
    }

    /**
     * Get a SequenceWrapper, a class that serializes an XDM sequence with full annotation of item types, node kinds,
     * etc. There are variants for Saxon-HE and Saxon-PE
     */

    public SequenceWrapper newSequenceWrapper(Receiver destination) {
        return new SequenceWrapper(destination);
    }

}

