////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.MonoIterator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

import java.util.Iterator;

/**
  * A BooleanExpressionPattern is a pattern of the form ?{ Expr } introduced in XSLT 3.0. It matches
  * an item if the expression has an effective boolean value of true() when evaluated with that item
  * as the singleton focus.
  * @author Michael H. Kay
  */

public class BooleanExpressionPattern extends Pattern {

    private Expression expression;
    /**
     * Create a BooleanExpressionPattern
     * @param expression the expression to be evaluated
     */

    public BooleanExpressionPattern(Expression expression) {
        this.expression = expression;
    }

    /**
     * Allocate slots to any variables used within the pattern
     * @param slotManager holds details of the allocated slots
     * @param nextFree the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(expression, nextFree, slotManager);
    }

    /**
     * Iterate over the subexpressions within this pattern
     *
     * @return an iterator over the subexpressions. Default implementation returns an empty sequence
     */
    /*@NotNull*/
    @Override
    public Iterator<Expression> iterateSubExpressions() {
        return new MonoIterator<Expression>(expression);
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */
    @Override
    public Pattern analyze(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(getItemType(), false);
        expression = visitor.typeCheck(expression, cit);
        return this;
    }


    /**
     * Determine whether this Pattern matches the given item. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     * @param item The item to be tested against the Pattern
     * @param context The context in which the match is to take place. Only relevant if the pattern
        * uses variables, or contains calls on functions such as document() or key().
        * @return true if the item matches the Pattern, false otherwise
    */

    public boolean matches(Item item, XPathContext context) {
        XPathContext c2 = context.newMinorContext();
        UnfailingIterator<Item> iter = SingletonIterator.makeIterator(item);
        iter.next();
        c2.setCurrentIterator(iter);
        try {
            return expression.effectiveBooleanValue(c2);
        } catch (XPathException e) {
            return false;
        }
    }

    /**
    * Get an Itemtype that all the items matching this pattern must satisfy
    */

    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
    * Determine the default priority of this item type test when used on its own as a Pattern
    */

    public final double getDefaultPriority() {
    	return 1;
    }


    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE. For patterns that
     * do not match nodes, return -1.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    public int getNodeKind() {
        return -1;
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Display the pattern for diagnostics
     */

    public String toString() {
        return "?{" + expression.toString() + "}";
    }

    /**
     * Determine whether this pattern is the same as another pattern
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return (other instanceof BooleanExpressionPattern) &&
                ((BooleanExpressionPattern)other).expression.equals(expression);
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        return 0x7aeffea9 ^ expression.hashCode();
    }



}

