////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.Binding;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.MultiIterator;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Abstract pattern formed as the union, intersection, or difference of two other patterns;
 * concrete subclasses are used for the different operators
 */

public abstract class VennPattern extends Pattern {

    protected Pattern p1, p2;
    private int nodeKind = Type.NODE;

    /**
     * Constructor
     *
     * @param p1 the left-hand operand
     * @param p2 the right-hand operand
     */

    public VennPattern(Pattern p1, Pattern p2) {
        this.p1 = p1;
        this.p2 = p2;
        if (p1.getNodeKind() == p2.getNodeKind()) {
            nodeKind = p1.getNodeKind();
        }
    }


    /**
     * Set the executable containing this pattern
     *
     * @param executable the executable
     */

    public void setExecutable(Executable executable) {
        p1.setExecutable(executable);
        p2.setExecutable(executable);
        super.setExecutable(executable);
    }

    /**
     * Simplify the pattern: perform any context-independent optimisations
     *
     * @param visitor an expression visitor
     */

    public Pattern simplify(ExpressionVisitor visitor) throws XPathException {
        p1 = p1.simplify(visitor);
        p2 = p2.simplify(visitor);
        return this;
    }

    /**
     * Type-check the pattern.
     * This is only needed for patterns that contain variable references or function calls.
     *
     * @return the optimised Pattern
     */

    public Pattern analyze(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        mustBeNodePattern(p1);
        p1 = p1.analyze(visitor, contextItemType);
        mustBeNodePattern(p2);
        p2 = p2.analyze(visitor, contextItemType);
        return this;
    }

    private void mustBeNodePattern(Pattern p) throws XPathException {
        if (p instanceof ItemTypePattern) {
            ItemType it = p.getItemType();
            if (!(it instanceof NodeTest)) {
                XPathException err = new XPathException("The operands of a union, intersect, or except pattern " +
                        "must be patterns that match nodes", "XPTY0004");
                err.setIsTypeError(true);
                throw err;
            }
        }
    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */
    @Override
    public void bindCurrent(Binding binding) {
        p1.bindCurrent(binding);
        p2.bindCurrent(binding);
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
     * @param offer details of the offer, for example the offer to move
     *              expressions that don't depend on the context to an outer level in
     *              the containing expression
     * @param parent the parent expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is detected
     */

    public void promote(PromotionOffer offer, Expression parent) throws XPathException {
        p1.promote(offer, parent);
        p2.promote(offer, parent);
    }

    /**
     * Replace a subexpression by a replacement subexpression
     * @param original    the expression to be replaced
     * @param replacement the new expression to be inserted in its place
     * @return true if the replacement was carried out
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
            return p1.replaceSubExpression(original, replacement) ||
                    p2.replaceSubExpression(original, replacement);
    }

    /**
     * Set the original text
     */

    public void setOriginalText(String pattern) {
        super.setOriginalText(pattern);
        p1.setOriginalText(pattern);
        p2.setOriginalText(pattern);
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     *         node without changing the position in the streamed input file
     * @param allowExtensions true if Saxon extensions are allowed
     */
    @Override
    public boolean isMotionless(boolean allowExtensions) {
        return p1.isMotionless(allowExtensions) && p2.isMotionless(allowExtensions);
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager represents the stack frame on which slots are allocated
     * @param nextFree the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        nextFree = p1.allocateSlots(slotManager, nextFree);
        nextFree = p2.allocateSlots(slotManager, nextFree);
        return nextFree;
    }

    /**
     * Gather the component (non-Venn) patterns of this Venn pattern
     * @param set the set into which the components will be added
     */

    public void gatherComponentPatterns(Set<Pattern> set) {
        if (p1 instanceof VennPattern) {
            ((VennPattern)p1).gatherComponentPatterns(set);
        } else {
            set.add(p1);
        }
        if (p2 instanceof VennPattern) {
            ((VennPattern)p2).gatherComponentPatterns(set);
        } else {
            set.add(p2);
        }
    }

     /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Node.NODE
     *
     * @return the type of node matched by this pattern. e.g. Node.ELEMENT or Node.TEXT
     */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     */

    public ItemType getItemType() {
        if (nodeKind == Type.NODE) {
            return AnyNodeTest.getInstance();
        } else if (nodeKind == -1) {
            // pattern does not match nodes
            return AnyItemType.getInstance();
        } else {
            return NodeKindTest.makeNodeKindTest(nodeKind);
        }
    }


    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     *
     * @return the dependencies, as a bit-significant mask
     */

    public int getDependencies() {
        return p1.getDependencies() | p2.getDependencies();
    }

    /**
     * Iterate over the subexpressions within this pattern
     * @return an iterator over the subexpressions.
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new MultiIterator<Expression>(
                new Iterator[]{p1.iterateSubExpressions(), p2.iterateSubExpressions()});
    }

    /**
     * Get the LHS of the union
     *
     * @return the first operand of the union
     */

    public Pattern getLHS() {
        return p1;
    }

    /**
     * Get the RHS of the union
     *
     * @return the second operand of the union
     */

    public Pattern getRHS() {
        return p2;
    }

    /**
     * Override method to set the system ID, so it's set on both halves
     */

    public void setSystemId(String systemId) {
        super.setSystemId(systemId);
        p1.setSystemId(systemId);
        p2.setSystemId(systemId);
    }

    /**
     * Override method to set the system ID, so it's set on both halves
     */

    public void setLineNumber(int lineNumber) {
        super.setLineNumber(lineNumber);
        p1.setLineNumber(lineNumber);
        p2.setLineNumber(lineNumber);
    }

    /**
     * Determine whether this pattern is the same as another pattern
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof VennPattern) {
            Set<Pattern> s0 = new HashSet<Pattern>(10);
            gatherComponentPatterns(s0);
            Set<Pattern> s1 = new HashSet<Pattern>(10);
            ((VennPattern)other).gatherComponentPatterns(s1);
            return s0.equals(s1);
        } else {
            return false;
        }
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        return 0x9bd723a6 ^ p1.hashCode() ^ p2.hashCode();
    }


}
