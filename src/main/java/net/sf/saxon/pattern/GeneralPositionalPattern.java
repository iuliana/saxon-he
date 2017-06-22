////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.functions.Current;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.NumericValue;

import java.util.Iterator;

/**
 * A GeneralPositionalPattern is a pattern of the form A[P] where A is an axis expression using the child axis
 * and P is an expression that depends on the position. When this kind of pattern is used for matching streamed nodes,
 * it relies on the histogram data of preceding siblings maintained as part of a
 * link com.saxonica.stream.om.FleetingParentNode
 *
 * This class handles cases where the predicate P is arbitrarily complex. Simple comparisons of position() against
 * an integer value are handled by the class SimplePositionalPattern.
 */
public class GeneralPositionalPattern extends Pattern {

    private NodeTest nodeTest;
    private Expression positionExpr;
    private boolean usesPosition = true;

    /**
     * Create a PositionalChildPattern
     */

    public GeneralPositionalPattern(NodeTest base, Expression positionExpr) {
        this.nodeTest = base;
        this.positionExpr = positionExpr;
    }


    /**
     * Get the filter assocated with the pattern
     * @return the filter predicate
     */

    public Expression getPositionExpr() {
        return positionExpr;
    }

    /**
     * Get the base pattern
     * @return the base pattern before filtering
     */

    public NodeTest getNodeTest() {
        return nodeTest;
    }


    /**
     * Simplify the pattern: perform any context-independent optimisations
     * @param visitor an expression visitor
     */

    public Pattern simplify(ExpressionVisitor visitor) throws XPathException {
        positionExpr = visitor.simplify(positionExpr);
        return this;
    }

    /**
     * Type-check the pattern, performing any type-dependent optimizations.
     * @param visitor an expression visitor
     * @param contextItemType the type of the context item at the point where the pattern appears
     * @return the optimised Pattern
     */

    public Pattern analyze(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        // analyze each component of the pattern
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(getItemType(), false);

        positionExpr = visitor.typeCheck(positionExpr, cit);
        positionExpr = ExpressionTool.unsortedIfHomogeneous(opt, positionExpr);
        positionExpr = visitor.optimize(positionExpr, cit);

        if (Literal.isConstantBoolean(positionExpr, true)) {
            return new ItemTypePattern(nodeTest);
        } else if (Literal.isConstantBoolean(positionExpr, false)) {
            // if a filter is constant false, the pattern doesn't match anything
            return new ItemTypePattern(ErrorType.getInstance());
        }

        if ((positionExpr.getDependencies() & StaticProperty.DEPENDS_ON_POSITION) == 0) {
            usesPosition = false;
        }

        // See if the expression is now known to be non-positional (see bugs 1908, 1992)
        if (!FilterExpression.isPositionalFilter(positionExpr, visitor.getConfiguration().getTypeHierarchy())) {
            byte axis = AxisInfo.CHILD;
            if (nodeTest.getPrimitiveType() == Type.ATTRIBUTE) {
                axis = AxisInfo.ATTRIBUTE;
            } else if (nodeTest.getPrimitiveType() == Type.NAMESPACE) {
                axis = AxisInfo.NAMESPACE;
            }
            AxisExpression ae = new AxisExpression(axis, nodeTest);
            FilterExpression fe = new FilterExpression(ae, positionExpr);
            return PatternMaker.fromExpression(fe, visitor.getConfiguration(), true)
                    .analyze(visitor, contextItemType);
        }

        return this;

    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */
    @Override
    public void bindCurrent(Binding binding) {
        if (ExpressionTool.callsFunction(positionExpr, Current.FN_CURRENT)) {
            replaceCurrent(positionExpr, binding);
        }
    }

    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     */

    public int getDependencies() {
        // the only dependency that's interesting is a dependency on local variables
        return positionExpr.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
    }

    /**
     * Iterate over the subexpressions within this pattern
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new MonoIterator<Expression>(positionExpr);
    }

    /**
     * Replace one subexpression by a replacement subexpression
     *
     * @param original    the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;

        if (positionExpr == original) {
            positionExpr = replacement;
            found = true;
        }
        return found;
    }

    /**
     * Allocate slots to any variables used within the pattern
     * @param slotManager
     * @param nextFree the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(positionExpr, nextFree, slotManager);
    }

    /**
     * Offer promotion for subexpressions within this pattern. The offer will be accepted if the subexpression
     * is not dependent on the factors (e.g. the context item) identified in the PromotionOffer.
     * By default the offer is not accepted - this is appropriate in the case of simple expressions
     * such as constant values and variable references where promotion would give no performance
     * advantage. This method is always called at compile time.
     * <p/>
     * <p>Unlike the corresponding method on {@link net.sf.saxon.expr.Expression}, this method does not return anything:
     * it can make internal changes to the pattern, but cannot return a different pattern. Only certain
     * kinds of promotion are applicable within a pattern: specifically, promotions affecting local
     * variable references within the pattern.
     *
     * @param offer details of the offer, for example the offer to move
     *              expressions that don't depend on the context to an outer level in
     *              the containing expression
     * @param parent
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is detected
     */

    public void promote(PromotionOffer offer, Expression parent) throws XPathException {

        Binding[] savedBindingList = offer.bindingList;
        positionExpr = positionExpr.promote(offer, parent);
        offer.bindingList = savedBindingList;
    }

    /**
     * Determine whether the pattern matches a given item.
     *
     *
     *
     *
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    public boolean matches(Item item, XPathContext context) throws XPathException {
        return item instanceof NodeInfo && matchesBeneathAnchor((NodeInfo) item, null, context);
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return internalMatches(node, anchor, context);
    }

    /**
     * Test whether the pattern matches, but without changing the current() node
     */

    private boolean internalMatches(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        // System.err.println("Matching node type and fingerprint");
        if (!nodeTest.matches(node, context)) {
            return false;
        }

        XPathContext c2 = context.newMinorContext();
        UnfailingIterator iter = SingletonIterator.makeIterator(node);
        iter.next();
        c2.setCurrentIterator(iter);

        try {
            XPathContext c = c2;
            if (usesPosition) {
                ManualIterator man = new ManualIterator(node, getActualPosition(node, context, Integer.MAX_VALUE));
                XPathContext c3 = c2.newMinorContext();
                c3.setCurrentIterator(man);
                c = c3;
            }
            Item predicate = positionExpr.evaluateItem(c);
            if (predicate instanceof NumericValue) {
                NumericValue position = (NumericValue)positionExpr.evaluateItem(context);
                if (position.isWholeNumber() && position.compareTo(0) > 0) {
                    int requiredPos = (int)position.longValue();
                    return getActualPosition(node, context, requiredPos) == requiredPos;
                } else {
                    return false;
                }
            } else {
                return ExpressionTool.effectiveBooleanValue(predicate);
            }

        } catch (XPathException e) {
            if ("XTDE0640".equals(e.getErrorCodeLocalPart())) {
                // Treat circularity error as fatal (test error213)
                throw e;
            }
            XPathException err = new XPathException("An error occurred matching pattern {" + toString() + "}: ", e);
            err.setXPathContext(c2);
            err.setErrorCodeQName(e.getErrorCodeQName());
            err.setLocator(this);
            c2.getController().recoverableError(err);
            return false;
        }
    }

    private int getActualPosition(NodeInfo node, XPathContext context, int max) throws XPathException {
        return context.getConfiguration().getSiblingPosition(node, nodeTest, max);
    }


    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Node.NODE
     *
     * @return the type of node matched by this pattern. e.g. Node.ELEMENT or Node.TEXT
     */

    public int getNodeKind() {
        return nodeTest.getPrimitiveType();
    }

    /**
     * Determine the fingerprint of nodes to which this pattern applies.
     * Used for optimisation.
     *
     * @return the fingerprint of nodes matched by this pattern.
     */

    public int getFingerprint() {
        return nodeTest.getFingerprint();
    }

    /**
     * Get an ItemType that all the nodes matching this pattern must satisfy
     */

    public ItemType getItemType() {
        return nodeTest;
    }

    /**
     * Determine whether this pattern is the same as another pattern
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof GeneralPositionalPattern) {
            GeneralPositionalPattern fp = (GeneralPositionalPattern)other;
            return nodeTest.equals(fp.nodeTest) && positionExpr.equals(fp.positionExpr);
        } else {
            return false;
        }
    }

    /**
     * hashcode supporting equals()
     */

    public int hashCode() {
        return nodeTest.hashCode() ^ positionExpr.hashCode();
    }


}
// Copyright (c) 2012 Saxonica Limited
