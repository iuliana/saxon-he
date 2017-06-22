////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A wrapper expression used to trace expressions in XPath and XQuery.
 */

public class TraceExpression extends Instruction implements InstructionInfo {

    private StructuredQName objectName;
    private int constructType;
    /*@Nullable*/ private NamespaceResolver namespaceResolver = null;
    private HashMap<String, Object> properties = new HashMap<String, Object>(10);
    Expression child;   // the instruction or other expression to be traced

    /**
     * Create a trace expression that traces execution of a given child expression
     * @param child the expression to be traced. This will be available to the TraceListener
     * as the value of the "expression" property of the InstructionInfo.
     */
    public TraceExpression(Expression child) {
        this.child = child;
        adoptChildExpression(child);
        setProperty("expression", child);
    }

    /**
     * Set the type of construct. This will generally be a constant
     * in class {@link net.sf.saxon.trace.Location}
     * @param type an integer code for the type of construct being traced
     */

    public void setConstructType(int type) {
        constructType = type;
    }

    /**
     * Get the construct type. This will generally be a constant
     * in class {@link net.sf.saxon.trace.Location}
     */
    public int getConstructType() {
        return constructType;
    }

    /**
     * Set the namespace context for the instruction being traced. This is needed if the
     * tracelistener wants to evaluate XPath expressions in the context of the current instruction
     * @param resolver The namespace resolver, or null if none is needed
     */

    public void setNamespaceResolver(/*@Nullable*/ NamespaceResolver resolver) {
        namespaceResolver = resolver;
    }

    /**
     * Get the namespace resolver to supply the namespace context of the instruction
     * that is being traced
     * @return the namespace resolver, or null if none is in use
     */

    /*@Nullable*/
    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    /**
     * Set a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * @param qName the name of the object, or null if not applicable
     */

    public void setObjectName(StructuredQName qName) {
        objectName = qName;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * @return the name of the object, or null if not applicable
     */

    public StructuredQName getObjectName() {
        return objectName;
    }

    /**
     * Set a named property of the instruction/expression
     * @param name the name of the property
     * @param value the value of the property
     */

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Get a named property of the instruction/expression
     * @param name the name of the property
     * @return the value of the property
     */

    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     */

    public Iterator<String> getProperties() {
        return properties.keySet().iterator();
    }


    /**
     * Get the InstructionInfo details about the construct. This is to satisfy the InstructionInfoProvider
     * interface.
     * @return the instruction details
     */

    public InstructionInfo getInstructionInfo() {
        return this;
    }

    /**
     * Get the system identifier (that is the base URI) of the static context of the expression being
     * traced. This returns the same result as getSystemId(), it is provided to satisfy the
     * {@link net.sf.saxon.event.LocationProvider} interface.
     * @param locationId not used
     * @return the URI of the module containing the expression
     */
    public String getSystemId(long locationId) {
        return getSystemId();
    }
     /**
     * Get the line number of the expression being
     * traced. This returns the same result as getLineNumber(), it is provided to satisfy the
     * {@link net.sf.saxon.event.LocationProvider} interface.
     * @param locationId not used
     * @return the line number of the expression within its module
     */

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    public int getColumnNumber(long locationId) {
        return getColumnNumber();
    }

    /*@NotNull*/
    public Expression copy() {
        TraceExpression t = new TraceExpression(child.copy());
        t.objectName = objectName;
        t.namespaceResolver = namespaceResolver;
        t.constructType = constructType;
        return t;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return child.isUpdatingExpression();
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return child.isVacuousExpression();
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updating subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        child.checkForUpdatingSubexpressions();
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @exception net.sf.saxon.trans.XPathException if an error is discovered during expression
     *     rewriting
     * @return the simplified expression
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        child = visitor.simplify(child);
        if (child instanceof TraceExpression) {
            return child;
        }
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        child = visitor.typeCheck(child, contextItemType);
        adoptChildExpression(child);
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        child = visitor.optimize(child, contextItemType);
        adoptChildExpression(child);
        return this;
    }

    public int getImplementationMethod() {
        return child.getImplementationMethod();
    }

    /**
     * Offer promotion for this subexpression. The offer will be accepted if the subexpression
     * is not dependent on the factors (e.g. the context item) identified in the PromotionOffer.
     * This method is always called at compile time.
     *
     * @param offer details of the offer, for example the offer to move
     *              expressions that don't depend on the context to an outer level in
     *              the containing expression
     * @param parent the parent of the subexpression
     * @return if the offer is not accepted, return this expression unchanged.
     *         Otherwise return the result of rewriting the expression to promote
     *         this subexpression
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is detected
     */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        // Many rewrites are not attempted if tracing is activated. But those that are, for example
        // rewriting of calls to current(), must be carried out.
        Expression newChild = child.promote(offer, parent);
        if (newChild != child) {
            child = newChild;
            adoptChildExpression(child);
            return this;
        }
        return this;
    }

    /**
     * Execute this instruction, with the possibility of returning tail calls if there are any.
     * This outputs the trace information via the registered TraceListener,
     * and invokes the instruction being traced.
     * @param context the dynamic execution context
     * @return either null, or a tail call that the caller must invoke on return
     * @throws net.sf.saxon.trans.XPathException
     */
    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        TraceListener listener = controller.getTraceListener();
    	if (controller.isTracing()) {
            assert listener != null;
   	        listener.enter(getInstructionInfo(), context);
        }
        // Don't attempt tail call optimization when tracing, the results are too confusing
        child.process(context);
   	    if (controller.isTracing()) {
            assert listener != null;
   	        listener.leave(getInstructionInfo());
        }
        return null;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     * @return the static item type of the instruction
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return child.getItemType(th);
    }

    /**
     * Determine the static cardinality of the expression. This establishes how many items
     * there will be in the result of the expression, at compile time (i.e., without
     * actually evaluating the result.
     *
     * @return one of the values Cardinality.ONE_OR_MORE,
     *         Cardinality.ZERO_OR_MORE, Cardinality.EXACTLY_ONE,
     *         Cardinality.ZERO_OR_ONE, Cardinality.EMPTY. This default
     *         implementation returns ZERO_OR_MORE (which effectively gives no
     *         information).
     */

    public int getCardinality() {
        return child.getCardinality();
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CONTEXT_ITEM} and
     * {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CURRENT_ITEM}. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     *     the expression
     */

    public int getDependencies() {
        return child.getDependencies();
    }

    /**
     * Determine whether this instruction creates new nodes.
     *
     *
     */

    public final boolean createsNewNodes() {
        return (child.getSpecialProperties() & StaticProperty.NON_CREATIVE) == 0;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    public int computeDependencies() {
        return child.computeDependencies();
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @exception net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *     expression
     * @return the node or atomic value that results from evaluating the
     *     expression; or null to indicate that the result is an empty
     *     sequence
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        Item result = child.evaluateItem(context);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
        return result;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @exception net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *     expression
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *     of the expression
     */

    /*@NotNull*/
    public SequenceIterator<Item> iterate(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        SequenceIterator result = child.iterate(context);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
        return result;
    }

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new MonoIterator<Expression>(child);
    }

    /**
      * Replace one subexpression by a replacement subexpression
      * @param original the original subexpression
      * @param replacement the replacement subexpression
      * @return true if the original subexpression is found
      */

     public boolean replaceSubExpression(Expression original, Expression replacement) {
         boolean found = false;
         if (child == original) {
             child = replacement;
             found = true;
         }
         return found;
     }

    public Expression getChildExpression() {
        return child;
    }

    public int getInstructionNameCode() {
        if (child instanceof Instruction) {
            return ((Instruction)child).getInstructionNameCode();
        } else {
            return -1;
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        child.explain(out);
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        child.evaluatePendingUpdates(context, pul);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
    }



}

