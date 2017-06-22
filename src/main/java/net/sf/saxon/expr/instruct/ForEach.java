////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;



/**
 * Handler for xsl:for-each elements in a stylesheet. The same class handles the "!" operator in XPath 3.0,
 * which has identical semantics to xsl:for-each, and it is used to support the "/" operator in cases where it
 * is known that the rhs delivers atomic values.
*/

public class ForEach extends Instruction implements ContextMappingFunction<Item>, ContextSwitchingExpression {

    protected Expression select;
    protected Expression action;
    /*@Nullable*/ protected Expression threads;
    protected boolean containsTailCall;

    /**
     * Create an xsl:for-each instruction
     * @param select the select expression
     * @param action the body of the xsl:for-each loop
     */

    public ForEach(Expression select, Expression action) {
        this.select = select;
        this.action = action;
        this.containsTailCall = false;
        this.threads = null;
        adoptChildExpression(select);
        adoptChildExpression(action);
    }

    /**
     * Create an xsl:for-each instruction
     * @param select the select expression
     * @param action the body of the xsl:for-each loop
     * @param containsTailCall true if the body of the loop contains a tail call on the containing function
     * @param threads if >1 causes multithreaded execution (Saxon-EE only)
     */

    public ForEach(Expression select, Expression action, boolean containsTailCall, Expression threads) {
        this.select = select;
        this.action = action;
        this.containsTailCall = containsTailCall && action instanceof TailCallReturner;
        this.threads = threads;
        adoptChildExpression(select);
        adoptChildExpression(action);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * @return the code for name xsl:for-each
    */

    public int getInstructionNameCode() {
        return StandardNames.XSL_FOR_EACH;
    }

    /**
     * Get the select expression
     * @return the select expression. Note this will have been wrapped in a sort expression
     * if sorting was requested.
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Set the select expression
     * @param select the select expression
     */

    public void setSelectExpression(Expression select) {
        this.select = select;
    }

    /**
     * Get the subexpression that sets the context item
     *
     * @return the subexpression that sets the context item, position, and size to each of its
     *         items in turn
     */
    public Expression getControllingExpression() {
        return select;
    }

    /**
     * Get the action expression (the content of the for-each)
     * @return the body of the for-each loop
     */

    public Expression getActionExpression() {
        return action;
    }

    /**
     * Set the action expression
     * @param action the select expression
     */

    public void setActionExpression(Expression action) {
        this.action = action;
    }

    /**
     * Get the subexpression that is evaluated in the new context
     *
     * @return the subexpression evaluated in the context set by the controlling expression
     */
    public Expression getControlledExpression() {
        return action;
    }

    /**
     * Get the number of threads requested
     * @return the value of the saxon:threads attribute
     */

    public Expression getNumberOfThreadsExpression() {
        return threads;
    }

    /**
     * Determine the data type of the items returned by this expression
     * @return the data type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        return action.getItemType(th);
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    public final boolean createsNewNodes() {
        int props = action.getSpecialProperties();
        return ((props & StaticProperty.NON_CREATIVE) == 0);
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @exception XPathException if an error is discovered during expression
     *     rewriting
     * @return the simplified expression
     * @param visitor the expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        select = visitor.simplify(select);
        action = visitor.simplify(action);
        threads = visitor.simplify(threads);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        select = visitor.typeCheck(select, contextItemType);
        adoptChildExpression(select);
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(select.getItemType(th), false);
        action = visitor.typeCheck(action, cit);
        adoptChildExpression(action);
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        if (Literal.isEmptySequence(action)) {
            return action;
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        select = visitor.optimize(select, contextItemType);
        adoptChildExpression(select);
        action = action.optimize(visitor, new ExpressionVisitor.ContextItemType(select.getItemType(th), false));
        adoptChildExpression(action);
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        if (Literal.isEmptySequence(action)) {
            return action;
        }

        // If any subexpressions within the body of the for-each are not dependent on the focus,
        // promote them: this causes them to be evaluated once, outside the for-each loop

        PromotionOffer offer = new PromotionOffer(visitor.getConfiguration().obtainOptimizer());
        offer.action = PromotionOffer.FOCUS_INDEPENDENT;
        offer.promoteDocumentDependent = (select.getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
        offer.promoteXSLTFunctions = false;
        offer.containingExpression = this;
        offer.bindingList = new Binding[0];
        action = doPromotion(action, offer);

        if (offer.containingExpression instanceof LetExpression) {
            offer.containingExpression =
                    visitor.optimize(offer.containingExpression, contextItemType);
        }
        Expression e2 = offer.containingExpression;
        if (e2 != this) {
            return e2;
        }
        if (threads != null) {
            return visitor.getConfiguration().obtainOptimizer().generateMultithreadedInstruction(this);
        }
        return this;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p/>
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodeSet the set of nodes in the path map that are affected
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = select.addToPathMap(pathMap, pathMapNodeSet);
        return action.addToPathMap(pathMap, target);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new ForEach(select.copy(), action.copy(), containsTailCall, threads);
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    public int computeDependencies() {
        // Some of the dependencies aren't relevant. Note that the sort keys are absorbed into the select
        // expression.
        int dependencies = 0;
        dependencies |= select.getDependencies();
        dependencies |= (action.getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS);
        return dependencies;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties(); 
        p |= (action.getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED);
        return p;
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        if (offer.action == PromotionOffer.INLINE_VARIABLE_REFERENCES ||
                offer.action == PromotionOffer.EXTRACT_GLOBAL_VARIABLES ||
                offer.action == PromotionOffer.REPLACE_CURRENT) {
            // Don't pass on other requests
            action = doPromotion(action, offer);
        }
    }



    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        if (threads == null) {
            return new PairIterator<Expression>(select, action);
        } else {
            return Arrays.asList(select, action, threads).iterator();
        }
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        SubExpressionInfo selectInfo = new SubExpressionInfo(select, true, false, INSPECTION_CONTEXT);
        SubExpressionInfo actionInfo = new SubExpressionInfo(action, false, true, INHERITED_CONTEXT);
        if (threads == null) {
            return new PairIterator<SubExpressionInfo>(selectInfo, actionInfo);
        } else {
            SubExpressionInfo threadsInfo = new SubExpressionInfo(threads, true, false, NODE_VALUE_CONTEXT);
            return Arrays.asList(selectInfo, actionInfo, threadsInfo).iterator();
        }
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (select == original) {
            select = replacement;
            found = true;
        }
        if (action == original) {
            action = replacement;
            found = true;
        }
        return found;
    }



    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | Expression.WATCH_METHOD | Expression.ITEM_FEED_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        action.checkPermittedContents(parentType, env, false);
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        SequenceIterator iter = select.iterate(context);

        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(iter);
        c2.setCurrentTemplateRule(null);

        if (containsTailCall) {
            if (controller.isTracing()) {
                TraceListener listener = controller.getTraceListener();
                assert listener != null;
                Item item = iter.next();
                if (item == null) {
                    return null;
                }
                listener.startCurrentItem(item);
                TailCall tc = ((TailCallReturner)action).processLeavingTail(c2);
                listener.endCurrentItem(item);
                return tc;
            } else {
                Item item = iter.next();
                if (item == null) {
                    return null;
                }
                return ((TailCallReturner)action).processLeavingTail(c2);
            }
        } else {
            if (controller.isTracing()) {
                TraceListener listener = controller.getTraceListener();
                assert listener != null;
                while(true) {
                    Item item = iter.next();
                    if (item == null) {
                        break;
                    }
                    listener.startCurrentItem(item);
                    action.process(c2);
                    listener.endCurrentItem(item);
                }
            } else {
                while(true) {
                    Item item = iter.next();
                    if (item == null) {
                        break;
                    }
                    action.process(c2);
                }
            }
        }
        return null;
    }

    /**
     * Return an Iterator to iterate over the values of the sequence. 
     *
     * @exception XPathException if any dynamic error occurs evaluating the
     *     expression
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *     of the expression
     */

    /*@NotNull*/
    public SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
        SequenceIterator<? extends Item> master = select.iterate(context);
        XPathContextMinor c2 = context.newMinorContext();
        c2.setCurrentIterator(master);
        master = new ContextMappingIterator<Item>(this, c2);
        return master;
    }

    /**
     * Map one item to a sequence.
     * @param context The processing context. The item to be mapped is the context item identified
     * from this context: the values of position() and last() also relate to the set of items being mapped
     * @return a SequenceIterator over the sequence of items that the supplied input
     * item maps to
     */

    public SequenceIterator map(XPathContext context) throws XPathException {
        return action.iterate(context);
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("forEach");
        explainThreads(out);        
        select.explain(out);
        out.startSubsidiaryElement("return");
        action.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

    protected void explainThreads(ExpressionPresenter out) {
        // no action in this class: implemented in subclass
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p/>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(select) + " ! " + ExpressionTool.parenthesize(action);
    }
}

