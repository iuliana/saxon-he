////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.sxpath.XPathDynamicContext;
import net.sf.saxon.sxpath.XPathExpression;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceExtent;

import javax.xml.transform.URIResolver;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An XPathSelector represents a compiled and loaded XPath expression ready for execution.
 * The XPathSelector holds details of the dynamic evaluation context for the XPath expression.
 */
@SuppressWarnings({"ForeachStatement"})
public class XPathSelector implements Iterable<XdmItem> {

    private XPathExpression exp;
    private XPathDynamicContext dynamicContext;
    private List<XPathVariable> declaredVariables;

    // protected constructor

    protected XPathSelector(XPathExpression exp,
                            ArrayList<XPathVariable> declaredVariables) {
        this.exp = exp;
        this.declaredVariables = declaredVariables;
        dynamicContext = exp.createDynamicContext();
    }

    /**
     * Set the context item for evaluating the XPath expression.
     * This may be either a node or an atomic value. Most commonly it will be a document node,
     * which might be constructed using the {@link DocumentBuilder#build} method.
     *
     * @param item The context item for evaluating the expression. Must not be null.
     * @throws SaxonApiException if an error occurs, for example because the type
     * of item supplied does not match the required item type
     *
     */

    public void setContextItem(XdmItem item) throws SaxonApiException {
        if (item == null) {
            throw new NullPointerException("contextItem");
        }
        try {
            dynamicContext.setContextItem((Item)item.getUnderlyingValue());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the context item used for evaluating the XPath expression.
     * This may be either a node or an atomic value. Most commonly it will be a document node,
     * which might be constructed using the Build method of the DocumentBuilder object.
     *
     * @return The context item for evaluating the expression, or null if no context item
     *         has been set.
     */

    public XdmItem getContextItem() {
        return XdmItem.wrapItem(dynamicContext.getContextItem());
    }

    /**
     * Set the value of a variable
     *
     * @param name  The name of the variable. This must match the name of a variable
     *              that was declared to the XPathCompiler. No error occurs if the expression does not
     *              actually reference a variable with this name.
     * @param value The value to be given to the variable.
     * @throws SaxonApiException if the variable has not been declared or if the type of the value
     * supplied does not conform to the required type that was specified when the variable was declared
     */

    public void setVariable(QName name, XdmValue value) throws SaxonApiException {
        XPathVariable var = null;
        StructuredQName qn = name.getStructuredQName();
        for (XPathVariable v : declaredVariables) {
            if (v.getVariableQName().equals(qn)) {
                var = v;
                break;
            }
        }
        if (var == null) {
            throw new SaxonApiException(
                    new XPathException("Variable has not been declared: " + name));
        }
        try {
            dynamicContext.setVariable(var, value.getUnderlyingValue());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:doc() and related functions.
     *
     * @param resolver An object that implements the URIResolver interface, or null.
     * @since 9.4
     */

    public void setURIResolver(URIResolver resolver) {
        dynamicContext.setURIResolver(resolver);
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or the
     *         system-defined one otherwise
     * @since 9.4
     */

    public URIResolver getURIResolver() {
        return dynamicContext.getURIResolver();
    }

    /**
     * Evaluate the expression, returning the result as an <code>XdmValue</code> (that is,
     * a sequence of nodes and/or atomic values).
     *
     * <p>Note: Although a singleton result <i>may</i> be represented as an <code>XdmItem</code>, there is
     * no guarantee that this will always be the case. If you know that the expression will return at
     * most one node or atomic value, it is best to use the <code>evaluateSingle</code> method, which
     * does guarantee that an <code>XdmItem</code> (or null) will be returned.</p>
     *
     * @return An <code>XdmValue</code> representing the results of the expression.
     * @throws SaxonApiException if a dynamic error occurs during the expression evaluation.
     */

    public XdmValue evaluate() throws SaxonApiException {
        Sequence value;
        try {
            value = SequenceExtent.makeSequenceExtent(exp.iterate(dynamicContext));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        return XdmValue.wrap(value);
    }

    /**
     * Evaluate the XPath expression, returning the result as an <code>XdmItem</code> (that is,
     * a single node or atomic value).
     *
     * @return an <code>XdmItem</code> representing the result of the expression, or null if the expression
     * returns an empty sequence. If the expression returns a sequence of more than one item,
     * any items after the first are ignored.
     * @throws SaxonApiException if a dynamic error occurs during the expression evaluation.
     */


    public XdmItem evaluateSingle() throws SaxonApiException {
        try {
            Item i = exp.evaluateSingle(dynamicContext);
            if (i == null) {
                return null;
            }
            return (XdmItem) XdmValue.wrap(i);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Evaluate the expression, returning the result as an <code>Iterator</code> (that is,
     * an iterator over a sequence of nodes and/or atomic values).
     *
     * <p>Because an <code>XPathSelector</code> is an {@link Iterable}, it is possible to
     * iterate over the result using a Java 5 "for each" expression, for example:</p>
     *
     * <p><pre>
     * XPathCompiler compiler = processor.newXPathCompiler();
     * XPathSelector seq = compiler.compile("1 to 20").load();
     * for (XdmItem item : seq) {
     *   System.err.println(item);
     * }
     * </pre></p>
     *
     * @return An iterator over the sequence that represents the results of the expression.
     *         Each object in this sequence will be an instance of <code>XdmItem</code>. Note
     *         that the expression may be evaluated lazily, which means that a successful response
     *         from this method does not imply that the expression has executed successfully: failures
     *         may be reported later while retrieving items from the iterator.
     * @throws SaxonApiUncheckedException
     *         if a dynamic error occurs during XPath evaluation that
     *         can be detected at this point. It is also possible that an SaxonApiUncheckedException will
     *         be thrown by the <code>hasNext()</code> method of the returned iterator.
     */

    public Iterator<XdmItem> iterator() throws SaxonApiUncheckedException {
        try {
            return new XdmSequenceIterator(exp.iterate(dynamicContext));
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Evaluate the XPath expression, returning the effective boolean value of the result.
     *
     * @return a <code>boolean</code> representing the effective boolean value of the result of evaluating
     * the expression, as defined by the rules for the fn:boolean() function.
     * @throws SaxonApiException if a dynamic error occurs during the expression evaluation, or if the result
     * of the expression is a value whose effective boolean value is not defined (for example, a date or a
     * sequence of three integers)
     * @since 9.1
     */


    public boolean effectiveBooleanValue() throws SaxonApiException {
        try {
            return exp.effectiveBooleanValue(dynamicContext);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying dynamic context object. This provides an escape hatch to the underlying
     * implementation classes, which contain methods that may change from one release to another.
     *
     * @return the underlying object representing the dynamic context for query execution
     */

    public XPathDynamicContext getUnderlyingXPathContext() {
        return dynamicContext;
    }


}

