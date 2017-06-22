////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;

/**
 * This expression is equivalent to (A intersect B) in the case where A has cardinality
 * zero-or-one. This is handled as a special case because the standard sort-merge algorithm
 * involves an unnecessary sort on B.
 */
public class SingletonIntersectExpression extends VennExpression {

    /**
     * Special case of an intersect expression where the first argument is a singleton
     * @param p1 the first argument, always a singleton
     * @param op the operator, always Token.INTERSECT
     * @param p2 the second argument
     */

    public SingletonIntersectExpression(final Expression p1, final int op, final Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Simplify the expression
     *
     * @param visitor an expression visitor
     */
    @Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    /**
     * Type-check the expression
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand0 = visitor.typeCheck(operand0, contextItemType);
        operand1 = visitor.typeCheck(operand1, contextItemType);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy() {
        return new SingletonIntersectExpression(operand0.copy(), operator, operand1.copy());
    }

    /**
     * Iterate over the value of the expression. The result will always be sorted in document order,
     * with duplicates eliminated
     * @param c The context for evaluation
     * @return a SequenceIterator representing the union of the two operands
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        NodeInfo m = (NodeInfo)operand0.evaluateItem(c);
        if (m == null) {
            return EmptyIterator.getInstance();
        }
        SequenceIterator iter = operand1.iterate(c);
        while (true) {
            NodeInfo n = (NodeInfo)iter.next();
            if (n == null) {
                return EmptyIterator.getInstance();
            }
            if (n.isSameNodeInfo(m)) {
                return SingletonIterator.makeIterator(m);
            }
        }
    }

    /**
     * Get the effective boolean value. In the case of a union expression, this
     * is reduced to an OR expression, for efficiency
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
        NodeInfo m = (NodeInfo)operand0.evaluateItem(c);
        if (m == null) {
            return false;
        }
        SequenceIterator iter = operand1.iterate(c);
        while (true) {
            NodeInfo n = (NodeInfo)iter.next();
            if (n == null) {
                return false;
            }
            if (n.isSameNodeInfo(m)) {
                return true;
            }
        }
    }

    public String getExpressionName() {
        return "singleton-intersect";
    }

/**
     * Display the operator used by this binary expression
     * @return String representation of the operator (for diagnostic display only)
     */

    protected String displayOperator() {
        return "singleton-intersect";
    }

}

