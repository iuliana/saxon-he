////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.*;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

/**
  * ReceivingContentHandler is a glue class that provides a standard SAX ContentHandler
  * interface to a Saxon Receiver. To achieve this it needs to map names supplied
  * as strings to numeric name codes, for which purpose it needs access to a name
  * pool. The class also performs the function of assembling adjacent text nodes.
  * <p>The class was previously named ContentEmitter.</p>
  * <p>If the input stream contains the processing instructions assigned by JAXP to switch
  * disable-output-escaping on or off, these will be reflected in properties set in the corresponding
  * characters events. In this case adjacent text nodes will not be combined.
  * @author Michael H. Kay
  */

public class ReceivingContentHandler
        implements ContentHandler, LexicalHandler, DTDHandler //, SaxonLocator, SourceLocationProvider
{
    private PipelineConfiguration pipe;
    private Receiver receiver;
    private boolean inDTD = false;	// true while processing the DTD
    private Locator locator;        // a SAX Locator
    private LocalLocator localLocator = new LocalLocator();

    // buffer for accumulating character data, until the next markup event is received

    private char[] buffer = new char[512];
    private int charsUsed = 0;
    private CharSlice slice = new CharSlice(buffer, 0, 0);

    // array for accumulating namespace information

    private NamespaceBinding[] namespaces = new NamespaceBinding[20];
    private int namespacesUsed = 0;

    // determine whether ignorable whitespace is ignored

    private boolean ignoreIgnorable = false;

    // determine whether DTD attribute types are retained

    private boolean retainDTDAttributeTypes = false;

    // determine whether DTD attribute value defaults should be suppressed

    private boolean suppressDTDAttributeDefaults = false;

    // indicate that escaping is allowed to be disabled using the JAXP-defined processing instructions

    private boolean allowDisableOutputEscaping = false;

    // indicate that escaping is disabled

    private boolean escapingDisabled = false;

    // flag to indicate whether the last tag was a start tag or an end tag

    private boolean afterStartTag = true;

    /**
     * A local cache is used to avoid allocating namecodes for the same name more than once.
     * This reduces contention on the NamePool. This is a two-level hashmap: the first level
     * has the namespace URI as its key, and returns a HashMap which maps lexical QNames to integer
     * namecodes.
     */

    private HashMap<String, HashMap<Object, NodeName>> nameCache = new HashMap<String, HashMap<Object, NodeName>>(10);
    private HashMap<Object, NodeName> noNamespaceNameCache = new HashMap<Object, NodeName>(10);

//    private static Class attributes2class;
//    private static Method isSpecifiedMethod;


    /**
    * Create a ReceivingContentHandler and initialise variables
    */

    public ReceivingContentHandler() {
    }

    /**
     * Set the ReceivingContentHandler to its initial state, except for the local name cache,
     * which is retained
     */

    public void reset() {
        pipe = null;
        receiver = null;
        ignoreIgnorable = false;
        retainDTDAttributeTypes = false;
        charsUsed = 0;
        slice.setLength(0);
        namespacesUsed = 0;
        locator = null;
        allowDisableOutputEscaping = false;
        escapingDisabled = false;
    }

    /**
     * Set the receiver to which events are passed. ReceivingContentHandler is essentially a translator
     * that takes SAX events as input and produces Saxon Receiver events as output; these Receiver events
     * are passed to the supplied Receiver
     * @param receiver the Receiver of events
     */

    public void setReceiver(Receiver receiver) {
		this.receiver = receiver;
        //receiver = new TracingFilter(receiver);
	}

    /**
     * Get the receiver to which events are passed.
     * @return the underlying Receiver
     */

    public Receiver getReceiver() {
        return receiver;
    }

    /**
     * Set the pipeline configuration
     * @param pipe the pipeline configuration. This holds a reference to the Saxon configuration, as well as
     * information that can vary from one pipeline to another, for example the LocationProvider which resolves
     * the location of events in a source document
     */

    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
        pipe.setLocationProvider(localLocator);
        Configuration config = pipe.getConfiguration();
        ignoreIgnorable = pipe.getParseOptions().getStripSpace() != Whitespace.NONE;
        retainDTDAttributeTypes = config.getBooleanProperty(FeatureKeys.RETAIN_DTD_ATTRIBUTE_TYPES);
        suppressDTDAttributeDefaults = !pipe.isExpandAttributeDefaults();
        allowDisableOutputEscaping = (Boolean)config.getConfigurationProperty(FeatureKeys.USE_PI_DISABLE_OUTPUT_ESCAPING);
    }

    /**
     * Get the pipeline configuration
     * @return the pipeline configuration as supplied to
    {@link #setPipelineConfiguration(PipelineConfiguration)}
     */

    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the Configuration object
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return pipe.getConfiguration();
    }

    /**
     * Set whether "ignorable whitespace" should be ignored. This method is effective only
     * if called after setPipelineConfiguration, since the default value is taken from the
     * configuration.
     * @param ignore true if ignorable whitespace (whitespace in element content that is notified
     * via the {@link #ignorableWhitespace(char[], int, int)} method) should be ignored, false if
     * it should be treated as ordinary text.
     */

    public void setIgnoreIgnorableWhitespace(boolean ignore) {
        ignoreIgnorable = ignore;
    }

    /**
     * Determine whether "ignorable whitespace" is ignored. This returns the value that was set
     * using {@link #setIgnoreIgnorableWhitespace} if that has been called; otherwise the value
     * from the configuration.
     * @return true if ignorable whitespace is being ignored
     */

    public boolean isIgnoringIgnorableWhitespace() {
        return ignoreIgnorable;
    }

    /**
     * Receive notification of the beginning of a document.
     */

    public void startDocument () throws SAXException {
//        System.err.println("ReceivingContentHandler#startDocument");
        try {
            charsUsed = 0;
            namespacesUsed = 0;
            pipe.setLocationProvider(localLocator);
            receiver.setPipelineConfiguration(pipe);
            receiver.open();
            receiver.startDocument(0);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
    * Receive notification of the end of a document
    */

    public void endDocument () throws SAXException {
        // System.err.println("RCH: end document");
        try {
            flush(true);
            receiver.endDocument();
            receiver.close();
        } catch (ValidationException err) {
            err.setLocator(locator);
            throw new SAXException(err);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Supply a locator that can be called to give information about location in the source document
     * being parsed.
    */

    public void setDocumentLocator (Locator locator) {
    	this.locator = locator;
    }

    /**
    * Notify a namespace prefix to URI binding
    */

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        //System.err.println("StartPrefixMapping " + prefix + "=" + uri);
        if (prefix.equals("xmlns")) {
            // the binding xmlns:xmlns="http://www.w3.org/2000/xmlns/"
            // should never be reported, but it's been known to happen
            return;
        }
    	if (namespacesUsed >= namespaces.length) {
    		NamespaceBinding[] n2 = new NamespaceBinding[namespacesUsed * 2];
    		System.arraycopy(namespaces, 0, n2, 0, namespacesUsed);
    		namespaces = n2;
    	}
    	namespaces[namespacesUsed++] = new NamespaceBinding(prefix, uri);
    }

    /**
    * Notify that a namespace binding is going out of scope
    */

    public void endPrefixMapping(String prefix) throws SAXException {}

    /**
    * Notify an element start event, including all the associated attributes
    */

    public void startElement (String uri, String localname, String rawname, Attributes atts)
    throws SAXException
    {
//        System.err.println("ReceivingContentHandler#startElement " +
//                uri + "," + localname + "," + rawname +
//                " at line " + locator.getLineNumber() + " of " + locator.getSystemId());
        //for (int a=0; a<atts.getLength(); a++) {
        //     System.err.println("  Attribute " + atts.getURI(a) + "/" + atts.getLocalName(a) + "/" + atts.getQName(a));
        //}
        try {
            flush(true);

            NodeName elementName = getNodeName(uri, localname, rawname);
    		receiver.startElement(elementName, Untyped.getInstance(), 0, ReceiverOptions.NAMESPACE_OK);

    		for (int n=0; n<namespacesUsed; n++) {
    		    receiver.namespace(namespaces[n], 0);
    		}

    		for (int a=0; a<atts.getLength(); a++) {
                int properties = ReceiverOptions.NAMESPACE_OK;
                String qname = atts.getQName(a);
                if (qname.startsWith("xmlns") && (qname.equals("xmlns") || qname.startsWith("xmlns:"))) {
                    // We normally configure the parser so that it doesn't notify namespaces as attributes.
                    // But when running as a TransformerHandler, we have no control over the feature settings
                    // of the sender of the events. So we filter them out, just in case. There might be cases
                    // where we ought not just to ignore them, but to handle them as namespace events, but
                    // we'll cross that bridge when we come to it.
                    continue;
                }
                // Note JDK15 dependency on Attributes2.isSpecified()
                if (suppressDTDAttributeDefaults
                        && atts instanceof Attributes2
                        && !((Attributes2)atts).isSpecified(qname)) {
                    continue;
                }

                NodeName attCode = getNodeName(atts.getURI(a), atts.getLocalName(a), atts.getQName(a));
    		    String type = atts.getType(a);
    		    SimpleType typeCode = BuiltInAtomicType.UNTYPED_ATOMIC;
                if (retainDTDAttributeTypes) {
                    if (type.equals("CDATA")) {
                        // common case, no action
                    } else if (type.equals("ID")) {
                        typeCode = BuiltInAtomicType.ID;
                    } else if (type.equals("IDREF")) {
                        typeCode = BuiltInAtomicType.IDREF;
                    } else if (type.equals("IDREFS")) {
                        typeCode = BuiltInListType.IDREFS;
                    } else if (type.equals("NMTOKEN")) {
                        typeCode = BuiltInAtomicType.NMTOKEN;
                    } else if (type.equals("NMTOKENS")) {
                        typeCode = BuiltInListType.NMTOKENS;
                    } else if (type.equals("ENTITY")) {
                        typeCode = BuiltInAtomicType.ENTITY;
                    } else if (type.equals("ENTITIES")) {
                        typeCode = BuiltInListType.ENTITIES;
                    }
                } else {
                    if (type.equals("ID")) {
                        properties |= ReceiverOptions.IS_ID;
                    } else if (type.equals("IDREF")) {
                        properties |= ReceiverOptions.IS_IDREF;
                    } else if (type.equals("IDREFS")) {
                        properties |= ReceiverOptions.IS_IDREF;
                    }
                }

    		    receiver.attribute(attCode, typeCode, atts.getValue(a), 0, properties);
    		}

            afterStartTag = true;
            receiver.startContent();

            namespacesUsed = 0;
        } catch (ValidationException err) {
            if (err.getLineNumber() == -1) {
                err.setLocator(locator);
            }
            throw new SAXException(err);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Get the NamePool name code associated with a name appearing in the document
     * @param uri the namespace URI
     * @param localname the local part of the name
     * @param rawname the lexical QName
     * @return the NamePool name code, newly allocated if necessary
     * @throws SAXException if the information supplied by the SAX parser is insufficient
     */

    private NodeName getNodeName(String uri, String localname, String rawname) throws SAXException {
        // System.err.println("URI=" + uri + " local=" + " raw=" + rawname);
        // The XML parser isn't required to report the rawname (qname), though all known parsers do.
        // If none is provided, we give up
        if (rawname.length() == 0) {
            throw new SAXException("Saxon requires an XML parser that reports the QName of each element");
        }
        // It's also possible (especially when using a TransformerHandler) that the parser
        // has been configured to report the QName rather than the localname+URI
        if (localname.length() == 0) {
            throw new SAXException("Parser configuration problem: namespace reporting is not enabled");
        }

        // Following code maintains a local cache to remember all the element names that have been
        // allocated, which reduces contention on the NamePool. It also avoids parsing the lexical QName
        // when the same name is used repeatedly. We also get a tiny improvement by avoiding the first hash
        // table lookup for names in the null namespace.

        HashMap<Object, NodeName> map2 = (uri.length() == 0 ? noNamespaceNameCache : nameCache.get(uri));
        if (map2 == null) {
            map2 = new HashMap<Object, NodeName>(50);
            nameCache.put(uri, map2);
            if (uri.length() == 0) {
                noNamespaceNameCache = map2;
            }
        }

        NodeName n = map2.get(rawname);
        // we use the rawname (qname) rather than the local name because we want a namecode rather than
        // a fingerprint - that is, the prefix matters.
        if (n == null) {
            if (uri.length()==0) {
                NoNamespaceName qn = new NoNamespaceName(localname);
                map2.put(rawname, qn);
                return qn;
            } else {
                String prefix = NameChecker.getPrefix(rawname);
                FingerprintedQName qn = new FingerprintedQName(prefix, uri, localname);
                map2.put(rawname, qn);
                return qn;
            }
        } else {
            return n;
        }

    }




    /**
    * Report the end of an element (the close tag)
    */

    public void endElement (String uri, String localname, String rawname) throws SAXException {
        //System.err.println("ReceivingContentHandler#End element " + rawname);
        try {
            // don't attempt whitespace compression if this end tag follows a start tag
            flush(!afterStartTag);
            receiver.endElement();
        } catch (ValidationException err) {
            err.maybeSetLocation(ExpressionLocation.makeFromSax(locator));
            if (!err.hasBeenReported()) {
                try {
                    pipe.getErrorListener().fatalError(err);
                } catch (TransformerException e) {
                    //
                }
            }
            err.setHasBeenReported(true);
            throw new SAXException(err);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
        afterStartTag = false;
    }

    /**
     * Report character data. Note that contiguous character data may be reported as a sequence of
     * calls on this method, with arbitrary boundaries
    */

    public void characters (char ch[], int start, int length) throws SAXException {
        // System.err.println("characters (" + length + ")");
        // need to concatenate chunks of text before we can decide whether a node is all-white

        while (charsUsed + length > buffer.length) {
            char[] newbuffer = new char[buffer.length*2];
            System.arraycopy(buffer, 0, newbuffer, 0, charsUsed);
            buffer = newbuffer;
            slice = new CharSlice(buffer, 0, 0);
        }
        System.arraycopy(ch, start, buffer, charsUsed, length);
        charsUsed += length;
    }

    /**
     * Report character data classified as "Ignorable whitespace", that is, whitespace text nodes
     * appearing as children of elements with an element-only content model
    */

    public void ignorableWhitespace (char ch[], int start, int length) throws SAXException {
        if (!ignoreIgnorable) {
            characters(ch, start, length);
        }
    }

    /**
    * Notify the existence of a processing instruction
    */

    public void processingInstruction (String name, String remainder) throws SAXException {
        try {
            flush(true);
            if (!inDTD) {
                if (name==null) {
                	// trick used by the old James Clark xp parser to notify a comment
                	comment(remainder.toCharArray(), 0, remainder.length());
                } else {
                    // some parsers allow through PI names containing colons
                    if (!getConfiguration().getNameChecker().isValidNCName(name)) {
                        throw new SAXException("Invalid processing instruction name (" + name + ')');
                    }
                    if (allowDisableOutputEscaping) {
                        if (name.equals(Result.PI_DISABLE_OUTPUT_ESCAPING)) {
                            //flush();
                            escapingDisabled = true;
                            return;
                        } else if (name.equals(Result.PI_ENABLE_OUTPUT_ESCAPING)) {
                            //flush();
                            escapingDisabled = false;
                            return;
                        }
                    }
                    receiver.processingInstruction(name, Whitespace.removeLeadingWhitespace(remainder), 0, 0);
                }
            }
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Notify the existence of a comment. Note that in SAX this is part of LexicalHandler interface
     * rather than the ContentHandler interface.
    */

    public void comment (char ch[], int start, int length) throws SAXException {
        try {
            flush(true);
            if (!inDTD) {
            	receiver.comment(new CharSlice(ch, start, length), 0, 0);
            }
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
    * Flush buffer for accumulated character data
     * @param compress true if compression of whitespace should be attempted. This is an expensive
     * operation, so we avoid doing it when we hit an end tag that follows after a start tag, as
     * it's not likely to succeed in that situation.
     * @throws XPathException if flushing the character data fails
     */

    private void flush(boolean compress) throws XPathException {
        if (charsUsed > 0) {
            slice.setLength(charsUsed);
            CharSequence cs = (compress ? CompressedWhitespace.compress(slice) : slice);
            receiver.characters(cs, 0,
                    escapingDisabled ? ReceiverOptions.DISABLE_ESCAPING : ReceiverOptions.WHOLE_TEXT_NODE);
            charsUsed = 0;
            escapingDisabled = false;
        }
    }

    /**
     * Notify a skipped entity. Saxon ignores this event
     */

    public void skippedEntity(String name) throws SAXException {}

    // No-op methods to satisfy lexical handler interface

	/**
	 * Register the start of the DTD. Saxon ignores the DTD; however, it needs to know when the DTD starts and
     * ends so that it can ignore comments in the DTD, which are reported like any other comment, but which
     * are skipped because they are not part of the XPath data model
	*/

    public void startDTD (String name, String publicId, String systemId) throws SAXException {
		inDTD = true;
    }

	/**
	* Register the end of the DTD. Comments in the DTD are skipped because they
	* are not part of the XPath data model
	*/

    public void endDTD () throws SAXException {
		inDTD = false;
    }

    public void startEntity (String name) throws SAXException {}

    public void endEntity (String name)	throws SAXException {}

    public void startCDATA () throws SAXException {}

    public void endCDATA ()	throws SAXException {}

    //////////////////////////////////////////////////////////////////////////////
    // Implement DTDHandler interface
    //////////////////////////////////////////////////////////////////////////////


    public void notationDecl(       String name,
                                    String publicId,
                                    String systemId) throws SAXException
    {}


    public void unparsedEntityDecl( String name,
                                    String publicId,
                                    String systemId,
                                    String notationName) throws SAXException {
        // Some (non-conformant) SAX parsers report the systemId as written.
        // We need to turn it into an absolute URL.

        String uri = systemId;
        if (locator != null) {
            try {
                URI suppliedURI = new URI(systemId);
                if (!suppliedURI.isAbsolute()) {
                    String baseURI = locator.getSystemId();
                    if (baseURI != null) {   // See bug 2167979
                        URI absoluteURI = new URI(baseURI).resolve(systemId);
                        uri = absoluteURI.toString();
                    }
                }
            } catch (URISyntaxException err) {
                uri = systemId; // fallback
            }
        }
        try {
            receiver.setUnparsedEntity(name, uri, publicId);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }




    private class LocalLocator implements SaxonLocator, SourceLocationProvider {

        // This class is needed to bridge a SAX Locator to a JAXP SourceLocator

        /**
         * Return the system identifier for the current document event.
         * @return A string containing the system identifier, or
         *         null if none is available.
         */

        public String getSystemId() {
            return (locator == null ? null : locator.getSystemId());
        }

        /**
         * Return the public identifier for the current document event.
         * @return A string containing the public identifier, or
         *         null if none is available.
         */

        public String getPublicId() {
            return (locator==null ? null : locator.getPublicId());
        }

        /**
         * Return the line number where the current document event ends.
         * @return The line number, or -1 if none is available.
         */

        public int getLineNumber() {
            return (locator==null ? -1 : locator.getLineNumber());
        }

        /**
         * Return the character position where the current document event ends.
         * @return The column number, or -1 if none is available.
         */

        public int getColumnNumber() {
            return (locator==null ? -1 : locator.getColumnNumber());
        }

        /**
         * Get the line number within the document or module containing a particular location
         *
         * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
         * @return the line number within the document or module.
         */

        public int getLineNumber(long locationId) {
            return (locator==null ? -1 : locator.getLineNumber());
        }

        public int getColumnNumber(long locationId) {
            return (locator==null ? -1 : locator.getColumnNumber());
        }

        /**
         * Get the URI of the document or module containing a particular location
         *
         * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
         * @return the URI of the document or module.
         */

        /*@Nullable*/ public String getSystemId(long locationId) {
            return (locator == null ? null : locator.getSystemId());
        }
    }

}   // end of class ReceivingContentHandler

