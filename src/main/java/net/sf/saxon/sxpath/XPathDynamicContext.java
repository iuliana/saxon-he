////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;

/**
 * This object represents the dynamic XPath execution context for use in the free-standing Saxon XPath API.
 * The dynamic context holds the context item and the values of external variables used by the XPath expression.
 *
 * <p>This object is always created via the method
 * {@link net.sf.saxon.sxpath.XPathExpression#createDynamicContext(net.sf.saxon.om.Item)}</p>
 */
public class XPathDynamicContext {

    private ItemType contextItemType;
    private XPathContextMajor contextObject;
    private SlotManager stackFrameMap;

    protected XPathDynamicContext(ItemType contextItemType, XPathContextMajor contextObject, SlotManager stackFrameMap) {
        this.contextItemType = contextItemType;
        this.contextObject = contextObject;
        this.stackFrameMap = stackFrameMap;
    }

    /**
     * Set the context item to a node derived from a supplied Source object. This may be
     * any implementation of the Source interface recognized by Saxon. Note that the
     * Saxon {@link NodeInfo} interface, representing a node in a tree, is one such
     * implementation; others include {@link javax.xml.transform.stream.StreamSource},
     * {@link javax.xml.transform.sax.SAXSource}, and {@link javax.xml.transform.dom.DOMSource}
     *
     * @param source The source object representing the node that will be used as the context item
     * @throws XPathException if a failure occurs reading or parsing a Source object to build an input tree,
     * or if the source is a document that was built under the wrong configuration
     */

    public void setContextNode(Source source) throws XPathException {
        NodeInfo origin;
        if (source instanceof NodeInfo) {
            origin = (NodeInfo)source;
            if (!origin.getConfiguration().isCompatible(contextObject.getConfiguration())) {
                throw new XPathException(
                        "Supplied node must be built using the same or a compatible Configuration",
                        SaxonErrorCode.SXXP0004);
            }
        } else {
            origin = contextObject.getConfiguration().buildDocument(source);
        }
        setContextItem(origin);
    }

    /**
     * Set the context item for evaluation of the XPath Expression
     * @param item the context item
     * @throws XPathException if the node is in a document that was built under the wrong configuration
     */

    public void setContextItem(Item item) throws XPathException {
        if (item instanceof NodeInfo) {
            if (!((NodeInfo)item).getConfiguration().isCompatible(contextObject.getConfiguration())) {
                throw new XPathException(
                        "Supplied node must be built using the same or a compatible Configuration",
                        SaxonErrorCode.SXXP0004);
            }
        }
        if (!contextItemType.matchesItem(item, false, contextObject.getConfiguration())) {
            throw new XPathException("Supplied context item does not match required context item type " +
                    contextItemType.toString());
        }
        UnfailingIterator iter = SingletonIterator.makeIterator(item);
        iter.next();
        contextObject.setCurrentIterator(iter);
    }

    /**
     * Get the context item
     * @return the context item if there is one, or null otherwise
     */

    public Item getContextItem() {
        return contextObject.getContextItem();
    }

    /**
     * Set the value of an external variable used within the XPath expression
     * @param variable the object representing the variable, as returned by the
     * {@link net.sf.saxon.sxpath.XPathEvaluator#declareVariable(String, String)} method.
     * Note that setting the value of a variable does not modify the {@link XPathVariable}
     * object itself, which means that this method is thread-safe.
     * @param value The value of the variable.
     * @throws XPathException if the supplied value does not conform to the required type of the
     * variable; or if the supplied value contains a node that does not belong to this Configuration
     * (or another Configuration that shares the same namePool)
     */

    public void setVariable(XPathVariable variable, Sequence value) throws XPathException {
        SequenceType requiredType = variable.getRequiredType();
        if (requiredType != SequenceType.ANY_SEQUENCE) {
            XPathException err = TypeChecker.testConformance(value, requiredType, contextObject);
            if (err != null) {
                throw err;
            }
        }
        SequenceIterator iter = value.iterate();
        while (true) {
            Item item = iter.next();
            if (item == null) {
                break;
            }
            if (item instanceof NodeInfo) {
                if (!((NodeInfo)item).getConfiguration().isCompatible(contextObject.getConfiguration())) {
                    throw new XPathException(
                            "Supplied node must be built using the same or a compatible Configuration",
                            SaxonErrorCode.SXXP0004);
                }
            }
        }
        int slot = variable.getLocalSlotNumber();
        StructuredQName expectedName = (slot >= stackFrameMap.getNumberOfVariables() ? null :
                stackFrameMap.getVariableMap().get(slot));
        if (!variable.getVariableQName().equals(expectedName)) {
            throw new XPathException(
                    "Supplied XPathVariable is bound to the wrong slot: perhaps it was created using a different static context");
        }
        contextObject.setLocalVariable(slot, value);
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * document(), etc.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *      null.
     * @since 9.2
     */

    public void setURIResolver(URIResolver resolver) {
        contextObject.getController().setURIResolver(resolver);
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise.
     * @since 9.2
     */

    public URIResolver getURIResolver() {
        return contextObject.getController().getURIResolver();
    }

    /**
     * Set the CollectionURIResolver used for resolving collection URIs.
     * Defaults to the CollectionURIResolver registered with the Configuration
     * @param resolver the resolver for references to collections
     * @since 9.4
     */

    public void setCollectionURIResolver(CollectionURIResolver resolver) {
        contextObject.getController().setCollectionURIResolver(resolver);
    }

    /**
     * Get the CollectionURIResolver used for resolving references to collections.
     * If none has been set on the Controller, returns the
     * CollectionURIResolver registered with the Configuration
     * @return the resolver for references to collections
     * @since 9.4
     */

    public CollectionURIResolver getCollectionURIResolver() {
        return contextObject.getController().getCollectionURIResolver();
    }


    /**
     * Set the error listener.
     *
     * @param listener the ErrorListener to be used
     * @since 9.2
     */

    public void setErrorListener(ErrorListener listener) {
        contextObject.getController().setErrorListener(listener);
    }

    /**
     * Get the error listener.
     *
     * @return the ErrorListener in use
     * @since 9.2
     */

    public ErrorListener getErrorListener() {
        return contextObject.getController().getErrorListener();
    }
    

    /**
     * For system use: get the wrapped XPathContext object
     * @return the underlying XPathContext object
     */

    public XPathContext getXPathContextObject() {
        return contextObject;
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:unparsed-text() and related functions.
     *
     * @param resolver An object that implements the UnparsedTextURIResolver interface, or
     *      null.
     * @since 9.5
     */

    public void setUnparsedTextURIResolver(UnparsedTextURIResolver resolver) {
        contextObject.getController().setUnparsedTextURIResolver(resolver);
    }

    /**
     * Get the URI resolver for unparsed text.
     *
     * @return the user-supplied unparsed text URI resolver if there is one, or the
     *     system-defined one otherwise
     * @since 9.5
     */

    public UnparsedTextURIResolver getUnparsedTextURIResolver() {
        return contextObject.getController().getUnparsedTextURIResolver();
    }

    /**
     * Check that all external variables have been given a value
     * @param stackFrameMap describes the stack frame
     * @param numberOfExternals the number of variables that need to be supplied
     * @throws XPathException if required variables have not been given a value
     */

    protected void checkExternalVariables(/*@NotNull*/ SlotManager stackFrameMap, int numberOfExternals) throws XPathException {
        Sequence[] stack = contextObject.getStackFrame().getStackFrameValues();
        for (int i=0; i<numberOfExternals; i++) {
            if (stack[i] == null) {
                StructuredQName qname = stackFrameMap.getVariableMap().get(i);
                throw new XPathException("No value has been supplied for variable $" + qname.getDisplayName());
            }
        }
    }
}

