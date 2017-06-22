////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * This class is used for generating complex content, that is, the content of an
 * element or document node. It enforces the rules on the order of events within
 * complex content (attributes and namespaces must come first), and it implements
 * part of the namespace fixup rules, in particular, it ensures that there is a
 * namespace node for the namespace used in the element name and in each attribute
 * name.
 *
 * <p>The same ComplexContentOutputter may be used for generating an entire XML
 * document; it is not necessary to create a new outputter for each element node.</p>
 *
 * @author Michael H. Kay
 */

public final class ComplexContentOutputter extends SequenceReceiver {

    private Receiver nextReceiver;
            // the next receiver in the output pipeline

    private int pendingStartTagDepth = -2;
            // -2 means we are at the top level, or immediately within a document node
            // -1 means we are in the content of an element node whose start tag is complete
    private NodeName pendingStartTag = null;
    private int level = -1; // records the number of startDocument or startElement events
                            // that have not yet been closed. Note that startDocument and startElement
                            // events may be arbitrarily nested; startDocument and endDocument
                            // are ignored unless they occur at the outermost level, except that they
                            // still change the level number
    private boolean[] currentLevelIsDocument = new boolean[20];
    private Boolean elementIsInNullNamespace;
    private NodeName[] pendingAttCode = new NodeName[20];
    private SimpleType[] pendingAttType = new SimpleType[20];
    private String[] pendingAttValue = new String[20];
    private int[] pendingAttLocation = new int[20];
    private int[] pendingAttProp = new int[20];
    private int pendingAttListSize = 0;

    private NamespaceBinding[] pendingNSList = new NamespaceBinding[20];
    private int pendingNSListSize = 0;

    private SchemaType currentSimpleType = null;  // any other value means we are currently writing an
                                         // element of a particular simple type

    private int startElementProperties;
    private int startElementLocationId = -1;
    private boolean declaresDefaultNamespace;
    private int hostLanguage = Configuration.XSLT;
    private boolean started = false;

    /**
     * Create a ComplexContentOutputter
     * @param pipe the pipeline configuration
     */

    public ComplexContentOutputter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        //System.err.println("ComplexContentOutputter init");
    }

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver != null) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    /**
     * Set the host language
     * @param language the host language, for example {@link Configuration#XQUERY}
     */

    public void setHostLanguage(int language) {
        hostLanguage = language;
    }

    /**
     * Set the receiver (to handle the next stage in the pipeline) directly
     * @param receiver the receiver to handle the next stage in the pipeline
     */

    public void setReceiver(Receiver receiver) {
        this.nextReceiver = receiver;
    }

    /**
     * Start the output process
     */

    public void open() throws XPathException {
        nextReceiver.open();
        previousAtomic = false;
    }

    /**
     * Start of a document node.
    */

    public void startDocument(int properties) throws XPathException {
        level++;
        if (level == 0) {
            nextReceiver.startDocument(properties);
        } else if (pendingStartTagDepth >= 0) {
            startContent();
            pendingStartTagDepth = -2;
        }
        previousAtomic = false;
        if (currentLevelIsDocument.length < level+1) {
            boolean[] b2 = new boolean[level*2];
            System.arraycopy(currentLevelIsDocument, 0, b2, 0, level);
            currentLevelIsDocument = b2;
        }
        currentLevelIsDocument[level] = true;
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (level == 0) {
            nextReceiver.endDocument();
        }
        previousAtomic = false;
        level--;
    }



    /**
    * Produce text content output. <BR>
    * Special characters are escaped using XML/HTML conventions if the output format
    * requires it.
    * @param s The String to be output
    * @exception XPathException for any failure
    */

    public void characters(CharSequence s, int locationId, int properties) throws XPathException {
        previousAtomic = false;
        if (s==null) return;
        int len = s.length();
        if (len==0) return;
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.characters(s, locationId, properties);
    }

    /**
    * Output an element start tag. <br>
    * The actual output of the tag is deferred until all attributes have been output
    * using attribute().
     * @param elemName The element name
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        //System.err.println("CCO " + this + "StartElement " + nameCode);
        level++;
        started = true;
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        startElementProperties = properties;
        startElementLocationId = locationId;
        pendingAttListSize = 0;
        pendingNSListSize = 0;
        pendingStartTag = elemName;
        pendingStartTagDepth = 1;
        elementIsInNullNamespace = null; // meaning not yet computed
        declaresDefaultNamespace = false;
        currentSimpleType = typeCode;
        previousAtomic = false;
        if (currentLevelIsDocument.length < level+1) {
            boolean[] b2 = new boolean[level*2];
            System.arraycopy(currentLevelIsDocument, 0, b2, 0, level);
            currentLevelIsDocument = b2;
        }
        currentLevelIsDocument[level] = false;
    }


    /**
    * Output a namespace declaration. <br>
    * This is added to a list of pending namespaces for the current start tag.
    * If there is already another declaration of the same prefix, this one is
    * ignored, unless the REJECT_DUPLICATES flag is set, in which case this is an error.
    * Note that unlike SAX2 startPrefixMapping(), this call is made AFTER writing the start tag.
    * @param nsBinding The namespace binding
    * @throws XPathException if there is no start tag to write to (created using writeStartTag),
    * or if character content has been written since the start tag was written.
    */

    public void namespace(NamespaceBinding nsBinding, int properties)
    throws XPathException {

        // System.err.println("Write namespace prefix=" + (nscode>>16) + " uri=" + (nscode&0xffff));
        if (pendingStartTagDepth < 0) {
            LocationProvider lp = getPipelineConfiguration().getLocationProvider();
            throw NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.NAMESPACE,
                    nsBinding.getPrefix(),
                    hostLanguage,
                    pendingStartTagDepth == -2,
                    getPipelineConfiguration().isSerializing(),
                    lp,
                    startElementLocationId);
        }

        // elimination of namespaces already present on an outer element of the
        // result tree is done by the NamespaceReducer.

        // Handle declarations whose prefix is duplicated for this element.

        boolean rejectDuplicates = (properties & ReceiverOptions.REJECT_DUPLICATES) != 0;

        for (int i=0; i<pendingNSListSize; i++) {
            if (nsBinding.equals(pendingNSList[i])) {
                // same prefix and URI: ignore this duplicate
                return;
            }
            if (nsBinding.getPrefix().equals(pendingNSList[i].getPrefix())) {
                if (pendingNSList[i].isDefaultUndeclaration() || nsBinding.isDefaultUndeclaration()) {
                    // xmlns="" overridden by xmlns="abc"
                    pendingNSList[i] = nsBinding;
                } else if (rejectDuplicates) {
                    String prefix = nsBinding.getPrefix();
                    String uri1 = nsBinding.getURI();
                    String uri2 = pendingNSList[i].getURI();
                    XPathException err = new XPathException("Cannot create two namespace nodes with the same prefix mapped to different URIs (prefix=" +
                            (prefix.length() == 0 ? "\"\"" : prefix) + ", URI=" +
                            (uri1.length() == 0 ? "\"\"" : uri1) + ", URI=" +
                            (uri2.length() == 0 ? "\"\"" : uri2) + ")");
                    err.setErrorCode(hostLanguage == Configuration.XSLT ? "XTDE0430" : "XQDY0102");
                    throw err;
                } else {
        		    // same prefix, do a quick exit
        		    return;
        		}
        	}
        }

        // It is an error to output a namespace node for the default namespace if the element
        // itself is in the null namespace, as the resulting element could not be serialized

        if ((nsBinding.getPrefix().length()==0) && (nsBinding.getURI().length()!=0)) {
            declaresDefaultNamespace = true;
            if (elementIsInNullNamespace == null) {
                elementIsInNullNamespace = Boolean.valueOf(pendingStartTag.isInNamespace(""));
            }
            if (elementIsInNullNamespace) {
                XPathException err = new XPathException("Cannot output a namespace node for the default namespace when the element is in no namespace");
                err.setErrorCode("XTDE0440");
                throw err;
            }
        }

        // if it's not a duplicate namespace, add it to the list for this start tag

        if (pendingNSListSize+1 > pendingNSList.length) {
            NamespaceBinding[] newlist = new NamespaceBinding[pendingNSListSize * 2];
            System.arraycopy(pendingNSList, 0, newlist, 0, pendingNSListSize);
            pendingNSList = newlist;
        }
        pendingNSList[pendingNSListSize++] = nsBinding;
        previousAtomic = false;
    }


    /**
    * Output an attribute value. <br>
    * This is added to a list of pending attributes for the current start tag, overwriting
    * any previous attribute with the same name. <br>
    * This method should NOT be used to output namespace declarations.<br>
    *
     *
     * @param attName The name of the attribute
     * @param value The value of the attribute
     * @param properties Bit fields containing properties of the attribute to be written
     * @throws XPathException if there is no start tag to write to (created using writeStartTag),
    * or if character content has been written since the start tag was written.
    */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        //System.err.println("Write attribute " + nameCode + "=" + value + " to Outputter " + this);
        if (pendingStartTagDepth < 0) {
            // The complexity here is in identifying the right error message and error code

            LocationProvider lp = getPipelineConfiguration().getLocationProvider();
            XPathException err = NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.ATTRIBUTE,
                    attName.getDisplayName(),
                    hostLanguage,
                    level < 0 || currentLevelIsDocument[level],
                    getPipelineConfiguration().isSerializing(),
                    lp,
                    startElementLocationId);
            if (lp != null && locationId > 0) {
                err.setLocator(new ExpressionLocation(lp, locationId));
            }
            throw err;
        }

        // if this is a duplicate attribute, overwrite the original, unless
        // the REJECT_DUPLICATES option is set.

        for (int a=0; a<pendingAttListSize; a++) {
            if ((pendingAttCode[a].equals(attName))) {
                if (hostLanguage == Configuration.XSLT) {
                    pendingAttType[a] = typeCode;
                    pendingAttValue[a] = value.toString();
                        // we have to copy the CharSequence, because some kinds of CharSequence are mutable.
                    pendingAttLocation[a] = locationId;
                    pendingAttProp[a] = properties;
                    return;
                } else {
                    XPathException err = new XPathException("Cannot create an element having two attributes with the same name: " +
                            Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                    err.setErrorCode("XQDY0025");
                    throw err;
                }
            }
        }

        // for top-level attributes (attributes whose parent element is not being copied),
        // check that the type annotation is not namespace-sensitive (because the namespace context might
        // be different, and we don't do namespace fixup for prefixes in content: see bug 4151

        if (level==0 && !typeCode.equals(BuiltInAtomicType.UNTYPED_ATOMIC) /**/ && currentLevelIsDocument[0] /**/) {
            // commenting-out in line above done MHK 22 Jul 2011 to pass test Constr-cont-nsmode-8
            // reverted 2011-07-27 to pass tests in qischema family
            if (typeCode.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                            Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                err.setErrorCode((hostLanguage == Configuration.XSLT ? "XTTE0950" : "XQTY0086"));
                throw err;
            }
        }

        // otherwise, add this one to the list

        if (pendingAttListSize >= pendingAttCode.length) {
            NodeName[] attCode2 = new NodeName[pendingAttListSize*2];
            SimpleType[] attType2 = new SimpleType[pendingAttListSize*2];
            String[] attValue2 = new String[pendingAttListSize*2];
            int[] attLoc2 = new int[pendingAttListSize*2];
            int[] attProp2 = new int[pendingAttListSize*2];
            System.arraycopy(pendingAttCode, 0, attCode2, 0, pendingAttListSize);
            System.arraycopy(pendingAttType, 0, attType2, 0, pendingAttListSize);
            System.arraycopy(pendingAttValue, 0, attValue2, 0, pendingAttListSize);
            System.arraycopy(pendingAttLocation, 0, attLoc2, 0, pendingAttListSize);
            System.arraycopy(pendingAttProp, 0, attProp2, 0, pendingAttListSize);
            pendingAttCode = attCode2;
            pendingAttType = attType2;
            pendingAttValue = attValue2;
            pendingAttLocation = attLoc2;
            pendingAttProp = attProp2;
        }

        pendingAttCode[pendingAttListSize] = attName;
        pendingAttType[pendingAttListSize] = typeCode;
        pendingAttValue[pendingAttListSize] = value.toString();
        pendingAttLocation[pendingAttListSize] = locationId;
        pendingAttProp[pendingAttListSize] = properties;
        pendingAttListSize++;
        previousAtomic = false;
    }

	/**
	 * Check that the prefix for an element or attribute is acceptable, allocating a substitute
	 * prefix if not. The prefix is acceptable unless a namespace declaration has been
	 * written that assignes this prefix to a different namespace URI. This method
	 * also checks that the element or attribute namespace has been declared, and declares it
	 * if not.
     * @param nodeName the proposed name, including proposed prefix
     * @param seq sequence number, used for generating a substitute prefix when necessary
     * @return a nameCode to use in place of the proposed nameCode (or the original nameCode
     * if no change is needed)
     * @throws net.sf.saxon.trans.XPathException if an error occurs writing the new
     * namespace node
	*/

	private NodeName checkProposedPrefix(NodeName nodeName, int seq) throws XPathException {
        NamespaceBinding binding = nodeName.getNamespaceBinding();
		String nsprefix = binding.getPrefix();

        for (int i=0; i<pendingNSListSize; i++) {
        	if (nsprefix.equals(pendingNSList[i].getPrefix())) {
        		// same prefix
        		if (binding.getURI().equals((pendingNSList[i].getURI()))) {
        			// same URI
        			return nodeName;	// all is well
        		} else {
        			String prefix = getSubstitutePrefix(binding, seq);
                    NodeName newName = new FingerprintedQName(prefix, nodeName.getURI(), nodeName.getLocalPart());
        			namespace(newName.getNamespaceBinding(), 0);
        			return newName;
        		}
        	}
        }
        // no declaration of this prefix: declare it now
        namespace(binding, 0);
        return nodeName;
    }

    /**
     * It is possible for a single output element to use the same prefix to refer to different
     * namespaces. In this case we have to generate an alternative prefix for uniqueness. The
     * one we generate is based on the sequential position of the element/attribute: this is
     * designed to ensure both uniqueness (with a high probability) and repeatability
     * @param nscode the proposed namespace code
     * @param seq sequence number for use in the substitute prefix
     * @return a prefix to use in place of the one originally proposed
    */

    private String getSubstitutePrefix(NamespaceBinding nscode, int seq) {
        return nscode.getPrefix() + '_' + seq;
    }

    /**
    * Output an element end tag.
    */

    public void endElement() throws XPathException {
        //System.err.println("Write end tag " + this + " : " + name);
        if (pendingStartTagDepth >= 0) {
            startContent();
        } else {
            pendingStartTagDepth = -2;
            pendingStartTag = null;
        }

        // write the end tag

        nextReceiver.endElement();
        level--;
        previousAtomic = false;
    }

    /**
    * Write a comment
    */

    public void comment(CharSequence comment, int locationId, int properties) throws XPathException {
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.comment(comment, locationId, properties);
        previousAtomic = false;
    }

    /**
    * Write a processing instruction
    */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) throws XPathException {
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
        previousAtomic = false;
    }

    /**
    * Append an arbitrary item (node or atomic value) to the output
     * @param item the item to be appended
     * @param locationId the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
     * need to be copied. Values are {@link net.sf.saxon.om.NodeInfo#ALL_NAMESPACES},
     * {@link net.sf.saxon.om.NodeInfo#LOCAL_NAMESPACES}, {@link net.sf.saxon.om.NodeInfo#NO_NAMESPACES}
     */

    public void append(/*@Nullable*/ Item item, int locationId, int copyNamespaces) throws XPathException {
        if (item == null) {
            //return;
        } else if (item instanceof AtomicValue || item instanceof ObjectValue) {
            if (previousAtomic) {
                characters(" ", locationId, 0);
            }
            characters(item.getStringValueCS(), locationId, 0);
            previousAtomic = true;
        } else if (item instanceof FunctionItem) {
            throw new XPathException("Cannot add a function item to a node tree");
        } else if (((NodeInfo)item).getNodeKind() == Type.DOCUMENT) {
            startDocument(0);
            SequenceIterator iter = ((NodeInfo)item).iterateAxis(AxisInfo.CHILD);
            while (true) {
                Item it = iter.next();
                if (it == null) break;
                append(it, locationId, copyNamespaces);
            }
            endDocument();
            previousAtomic = false;
        } else {
            int copyOptions = CopyOptions.TYPE_ANNOTATIONS;
            if (copyNamespaces == NodeInfo.LOCAL_NAMESPACES) {
                copyOptions |= CopyOptions.LOCAL_NAMESPACES;
            } else if (copyNamespaces == NodeInfo.ALL_NAMESPACES) {
                copyOptions |= CopyOptions.ALL_NAMESPACES;
            }
            ((NodeInfo)item).copy(this, copyOptions, locationId);
            previousAtomic = false;
        }
    }


    /**
    * Close the output
    */

    public void close() throws XPathException {
        // System.err.println("Close " + this + " using emitter " + emitter.getClass());
        nextReceiver.close();
        previousAtomic = false;
    }

    /**
    * Flush out a pending start tag
    */

    public void startContent() throws XPathException {

        if (pendingStartTagDepth < 0) {
            // this can happen if the method is called from outside,
            // e.g. from a SequenceOutputter earlier in the pipeline
            return;
        }

        started = true;
        int props = startElementProperties;
        NodeName elcode = pendingStartTag;
        if (declaresDefaultNamespace || pendingStartTag.getPrefix().length()!=0) {
            // skip this check if the element is unprefixed and no xmlns="abc" declaration has been encountered
            elcode = checkProposedPrefix(pendingStartTag, 0);
            props = startElementProperties | ReceiverOptions.NAMESPACE_OK;
        }
        nextReceiver.startElement(elcode, currentSimpleType, startElementLocationId, props);

        for (int a=0; a<pendingAttListSize; a++) {
            NodeName attcode = pendingAttCode[a];
            if (!attcode.isInNamespace("")) {	// non-null prefix
                attcode = checkProposedPrefix(attcode, a+1);
                pendingAttCode[a] = attcode;
            }
        }

        for (int n=0; n<pendingNSListSize; n++) {
            nextReceiver.namespace(pendingNSList[n], 0);
        }

        for (int a=0; a<pendingAttListSize; a++) {
            nextReceiver.attribute( pendingAttCode[a],
                                pendingAttType[a],
                                pendingAttValue[a],
                                pendingAttLocation[a],
                                pendingAttProp[a]);
        }

        nextReceiver.startContent();

        pendingAttListSize = 0;
        pendingNSListSize = 0;
        pendingStartTagDepth = -1;
        previousAtomic = false;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }
}

