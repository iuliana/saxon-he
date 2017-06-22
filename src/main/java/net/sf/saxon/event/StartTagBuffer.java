////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * StartTagBuffer is a ProxyReceiver that buffers attributes and namespace events within a start tag.
 * It maintains details of the namespace context, and a full set of attribute information, on behalf
 * of other filters that need access to namespace information or need to process attributes in arbitrary
 * order.
 *
 * <p>StartTagBuffer also implements namespace fixup (the process of creating namespace nodes|bindings on behalf
 * of constructed element and attribute nodes). Although this would be done anyway, further down the pipeline,
 * it has to be done early in the case of a validating pipeline, because the namespace bindings must be created
 * before any namespace-sensitive attribute content is validated.</p>
 *
 * <p>The StartTagBuffer also allows error conditions to be buffered. This is because the XSIAttributeHandler
 * validates attributes such as xsi:type and xsi:nil before attempting to match its parent element against
 * a particle of its containing type. It is possible that the parent element will match a wildcard particle
 * with processContents="skip", in which case an invalid xsi:type or xsi:nil attribute is not an error.</p>
 */

public class StartTagBuffer extends ProxyReceiver implements NamespaceResolver {

    public StartTagBuffer(Receiver next) {
        super(next);
    }

    // Details of the pending element event

    protected NodeName elementNameCode;
    protected SchemaType elementTypeCode;
    protected int elementLocationId;
    protected int elementProperties;

    // Details of pending attribute events

    protected AttributeCollectionImpl bufferedAttributes;
    private boolean acceptAttributes;
    private boolean inDocument;

    // We keep track of namespaces. The namespaces
    // array holds a list of all namespaces currently declared (organised as pairs of entries,
    // prefix followed by URI). The stack contains an entry for each element currently open; the
    // value on the stack is an Integer giving the number of namespaces added to the main
    // namespace stack by that element.

    protected NamespaceBinding[] namespaces = new NamespaceBinding[50];          // all namespaces currently declared
    protected int namespacesSize = 0;                  // all namespaces currently declared
    private int[] countStack = new int[50];
    private int depth = 0;
    private int attCount = 0;

    /**
     * Set the pipeline configuration
     * @param pipe the pipeline configuration
     */

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        super.setPipelineConfiguration(pipe);
        bufferedAttributes = new AttributeCollectionImpl(pipe.getConfiguration());
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        // a document node in the content sequence of an element is ignored. However, we need
        // to stop attributes being created within the document node.
        if (depth == 0) {
            depth++;
            super.startDocument(properties);
        }
        acceptAttributes = false;
        inDocument = true;      // we ought to clear this on endDocument, but it only affects diagnostics
    }


    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (depth == 1) {
            depth--;
            super.endDocument();
        }
    }

    /**
    * startElement
    */

    public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {

        elementNameCode = nameCode;
        elementTypeCode = typeCode;
        elementLocationId = locationId;
        elementProperties = properties;

        bufferedAttributes.clear();

        // Record the current height of the namespace list so it can be reset at endElement time

        countStack[depth] = 0;
        if (++depth >= countStack.length) {
            int[] newstack = new int[depth*2];
            System.arraycopy(countStack, 0, newstack, 0, depth);
            countStack = newstack;
        }

        // Ensure that the element namespace is output, unless this is done
        // automatically by the caller (which is true, for example, for a literal
        // result element).

        acceptAttributes = true;
        inDocument = false;
        if ((properties & ReceiverOptions.NAMESPACE_OK) == 0) {
            namespace(nameCode.getNamespaceBinding(), 0);
        }
        attCount = 0;
    }

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {

        if (!acceptAttributes) {
            throw NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.NAMESPACE, namespaceBinding.getPrefix(),
                    getPipelineConfiguration().getHostLanguage(),
                    inDocument, false, null, -1);
        }

        // avoid duplicates
        for (int n=0; n<countStack[depth - 1]; n++) {
            if (namespaces[namespacesSize - 1 - n].equals(namespaceBinding)) {
                return;
            }
        }
        addToStack(namespaceBinding);
        countStack[depth - 1]++;
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     *
     *
     * @param attName   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties)
            throws XPathException {

        if (!acceptAttributes) {
            throw NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.ATTRIBUTE, attName.getDisplayName(),
                    getPipelineConfiguration().getHostLanguage(),
                    inDocument, false, null, -1);
        }

        // Perform namespace fixup for the attribute

        if (((properties & ReceiverOptions.NAMESPACE_OK) == 0) &&
                !attName.isInNamespace("")) {	// non-null prefix
            attName = checkProposedPrefix(attName, attCount++);
        }
        bufferedAttributes.addAttribute(attName, typeCode, value.toString(), locationId, properties);

        // Note: we're relying on the fact that AttributeCollection can hold two attributes of the same name
        // and maintain their order, because the check for duplicate attributes is not done until later in the
        // pipeline. We validate both the attributes (see Bugzilla #4600 which legitimizes this.)

    }

    /**
     * Add a namespace declaration (or undeclaration) to the stack
     * @param binding the namespace binding for the declaration
    */

    private void addToStack(NamespaceBinding binding) {
		// expand the stack if necessary
        if (namespacesSize+1 >= namespaces.length) {
            NamespaceBinding[] newlist = new NamespaceBinding[namespacesSize*2];
            System.arraycopy(namespaces, 0, newlist, 0, namespacesSize);
            namespaces = newlist;
        }
        namespaces[namespacesSize++] = binding;
    }

    /**
     * startContent: Add any namespace undeclarations needed to stop
     * namespaces being inherited from parent elements
     */

    public void startContent() throws XPathException {
        nextReceiver.startElement(elementNameCode, elementTypeCode, elementLocationId,
                elementProperties | ReceiverOptions.NAMESPACE_OK);
        declareNamespacesForStartElement();

        final int length = bufferedAttributes.getLength();
        for (int i=0; i<length; i++) {
            nextReceiver.attribute(bufferedAttributes.getNodeName(i),
                    bufferedAttributes.getTypeAnnotation(i),
                    bufferedAttributes.getValue(i),
                    bufferedAttributes.getLocationId(i),
                    bufferedAttributes.getProperties(i) | ReceiverOptions.NAMESPACE_OK);
        }
        acceptAttributes = false;
        nextReceiver.startContent();
    }

    protected void declareNamespacesForStartElement() throws XPathException {
        for (int i=namespacesSize - countStack[depth-1]; i<namespacesSize; i++) {
            nextReceiver.namespace(namespaces[i], 0);
        }
    }

    /**
     * Get the namespaces declared (or undeclared) at the current level
     * @return an array of namespace bindings
     */

    public NamespaceBinding[] getLocalNamespaces() {
        int size = countStack[depth-1];
        if (size == 0) {
            return NamespaceBinding.EMPTY_ARRAY;
        } else {
            NamespaceBinding[] localBindings = new NamespaceBinding[countStack[depth-1]];
            System.arraycopy(namespaces, namespacesSize - size, localBindings, 0, size);
            return localBindings;
        }
    }

    /**
     * Signal namespace events for all in-scope namespaces to the current receiver in the pipeline
     * @throws XPathException if any downstream error occurs
     */

    protected void declareAllNamespaces() throws XPathException {
        for (int i=0; i<namespacesSize; i++) {
            nextReceiver.namespace(namespaces[i], 0);
        }
    }

    /**
    * endElement: Discard the namespaces declared locally on this element.
    */

    public void endElement () throws XPathException {
        nextReceiver.endElement();
        undeclareNamespacesForElement();
    }

    protected void undeclareNamespacesForElement() {
        namespacesSize -= countStack[--depth];
    }

    /**
     * Determine if the current element has any attributes
     * @return true if the element has one or more attributes
     */

    public boolean hasAttributes() {
        return bufferedAttributes.getLength() > 0;
    }

    /**
     * Get the value of the current attribute with a given nameCode
     * @param nameCode the name of the required attribute
     * @return the attribute value, or null if the attribute is not present
     */

    public String getAttribute(int nameCode) {
        return bufferedAttributes.getValueByFingerprint(nameCode & 0xfffff);
    }

    /**
     * Get the value of the current attribute with a given name
     * @param uri the uri of the name of the required attribute
     * @param local the local part of the name of the required attribute
     * @return the attribute value, or null if the attribute is not present
     */

    public String getAttribute(String uri, String local) {
        return bufferedAttributes.getValue(uri, local);
    }

    /**
     * Get all the attributes on the current element start tag
     * @return an AttributeCollection containing all the attributes
     */

    public AttributeCollection getAllAttributes() {
        return bufferedAttributes;
    }

    /**
     * Ask whether the attribute collection contains any attributes
     * in a specified namespace
     * @param uri the specified namespace
     * @return true if there are one or more attributes in this namespace
     */

    public boolean hasAttributeInNamespace(String uri) {
        return bufferedAttributes.hasAttributeInNamespace(uri);
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.                                                         f
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope
     */

    /*@Nullable*/ public String getURIForPrefix(String prefix, boolean useDefault) {
        if (prefix.length()==0 && !useDefault) {
            return NamespaceConstant.NULL;
        } else if ("xml".equals(prefix)) {
            return NamespaceConstant.XML;
        } else {
             for (int i=namespacesSize-1; i>=0; i--) {
                 if (namespaces[i].getPrefix().equals(prefix)) {
                     String uri = namespaces[i].getURI();
                     if (uri.length()==0) {
//                    // we've found a namespace undeclaration, so it's as if the prefix weren't there at all
                     } else {
                        return uri;
                     }
                 }
             }
        }
        return (prefix.length()==0 ? NamespaceConstant.NULL : null);
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<String>(namespacesSize);
        for (int i=namespacesSize-1; i>=0; i--) {
            String prefix = namespaces[i].getPrefix();
            if (!prefixes.contains(prefix)) {
                prefixes.add(prefix);
            }
        }
        prefixes.add("xml");
        return prefixes.iterator();
    }

    /**
     * Check that the prefix for an element or attribute is acceptable, allocating a substitute
     * prefix if not. The prefix is acceptable unless a namespace declaration has been
     * written that assignes this prefix to a different namespace URI. This method
     * also checks that the element or attribute namespace has been declared, and declares it
     * if not.
     * @param nameCode the proposed element or attribute name
     * @param seq sequence number of attribute, used for generating distinctive prefixes
     * @return the actual allocated name, which may be different.
     * @throws net.sf.saxon.trans.XPathException if any error occurs writing the new namespace binding
    */

    private NodeName checkProposedPrefix(NodeName nameCode, int seq) throws XPathException {
        NamespaceBinding binding = nameCode.getNamespaceBinding();
        String prefix = binding.getPrefix();

        String existingURI = getURIForPrefix(prefix, true);
        if (existingURI == null) {
            // prefix has not been declared: declare it now (namespace fixup)
            namespace(binding, 0);
            return nameCode;
        } else {
            if (binding.getURI().equals(existingURI)) {
                // prefix is already bound to this URI
                return nameCode;	// all is well
            } else {
                // conflict: prefix is currently bound to a different URI
                prefix = getSubstitutePrefix(binding, seq);

                NodeName newCode = new FingerprintedQName(
                                    prefix,
                                    nameCode.getURI(),
                                    nameCode.getLocalPart());
                namespace(newCode.getNamespaceBinding(), 0);
                return newCode;
            }
        }
    }

    /**
     * It is possible for a single output element to use the same prefix to refer to different
     * namespaces. In this case we have to generate an alternative prefix for uniqueness. The
     * one we generate is based on the sequential position of the element/attribute: this is
     * designed to ensure both uniqueness (with a high probability) and repeatability
     * @param binding the namespace binding of the proposed element or attribute name
     * @param seq sequence number of attribute, used for generating distinctive prefixes
     * @return the actual allocated name, which may be different.
     */

    private String getSubstitutePrefix(NamespaceBinding binding, int seq) {
        String prefix = binding.getPrefix();
        return prefix + '_' + seq;
    }

}

