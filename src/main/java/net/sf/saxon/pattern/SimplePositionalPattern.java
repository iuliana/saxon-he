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
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.NumericValue;

import java.util.Iterator;

/**
 * A SimplePositionalPattern is a pattern of the form A[P] where A is an axis expression using the child axis
 * and P is an expression that depends on the position. When this kind of pattern is used for matching streamed nodes,
 * it relies on the histogram data of preceding siblings maintained as part of a
 * {@link com.saxonica.stream.om.FleetingParentNode}
 *
 * This class handles cases where the predicate P is a simple comparison of the value of position() against an
 * integer (which may be constant or computed. More general predicates are handled by the class
 * GeneralPositionalPattern.
 */


public final class SimplePositionalPattern extends Pattern {

    private NodeTest nodeTest;
    private Expression positionExpr;
    private int operator;


    /**
     * Create a SimplePositionalPattern
     * @param nodeTest the test that the node must satisfy
     * @param positionTest expression to evaluate the required position of the node
     * @param operator comparison between the required position and the actual position
     */

    public SimplePositionalPattern(NodeTest nodeTest, Expression positionTest, int operator) {
        this.nodeTest = nodeTest;
        this.positionExpr = positionTest;
        this.operator = operator;
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

    public int getOperator() {
        return operator;
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
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(getItemType(), false);

        positionExpr = visitor.typeCheck(positionExpr, cit);
        positionExpr = visitor.optimize(positionExpr, cit);

        return this;

    }

    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     */

    public int getDependencies() {
        int dependencies = positionExpr.getDependencies();
        // the only dependency that's interesting is a dependency on local variables
        dependencies &= StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
        return dependencies;
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
        nextFree = ExpressionTool.allocateSlots(positionExpr, nextFree, slotManager);
        return nextFree;
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
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    public boolean matches(Item item, XPathContext context) throws XPathException {
        return item instanceof NodeInfo && matchesBeneathAnchor((NodeInfo) item, null, context);
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
        return nodeTest.getPrimitiveItemType();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof SimplePositionalPattern) {
            SimplePositionalPattern fp = (SimplePositionalPattern)other;
            return nodeTest.equals(fp.nodeTest) && positionExpr.equals(fp.positionExpr) && operator == fp.operator;
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

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     * @param allowExtensions true if Saxon streamability extensions are allowed in the analysis
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     * node without changing the position in the streamed input file
     */

    public boolean isMotionless(boolean allowExtensions) {
        return true;
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
        if (!nodeTest.matches(node, context)) {
            return false;
        }

        NumericValue reqPos = (NumericValue)positionExpr.evaluateItem(context);
        if (reqPos.isNaN()) {
            return (operator == Token.FNE);
        }

        int actualPos = context.getConfiguration().getSiblingPosition(node, nodeTest, Integer.MAX_VALUE);

        int c = Double.compare((double) actualPos, reqPos.getDoubleValue());
        switch (operator) {
            case Token.FEQ:
                return c == 0;
            case Token.FNE:
                return c != 0;
            case Token.FGT:
                return c > 0;
            case Token.FLT:
                return c < 0;
            case Token.FGE:
                return c >= 0;
            case Token.FLE:
                return c <= 0;
            default:
                throw new UnsupportedOperationException("Unknown operator " + operator);
        }
    }

}

