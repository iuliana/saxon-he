////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.NumericValue;

import java.util.Arrays;
import java.util.Iterator;

/**
* An IntegerRangeTest is an expression of the form
 * E = N to M
 * where E is numeric, and N and M are both expressions of type integer.
*/

public class IntegerRangeTest extends Expression {

    /*@Nullable*/ Expression value;
    Expression min;
    Expression max;

    /**
     * Construct a IntegerRangeTest
     * @param value the integer value to be tested to see if it is in the range min to max inclusive
     * @param min the lowest permitted value
     * @param max the highest permitted value
     */

    public IntegerRangeTest(Expression value, Expression min, Expression max) {
        this.value = value;
        this.min = min;
        this.max = max;
    }

    /**
     * Get the value to be tested
     * @return the expression that evaluates to the value being tested
     */

    public Expression getValueExpression() {
        return value;
    }

    /**
     * Get the expression denoting the start of the range
     * @return the expression denoting the minumum value
     */

    public Expression getMinValueExpression() {
        return min;
    }

    /**
     * Get the expression denoting the end of the range
     * @return the expression denoting the maximum value
     */

    public Expression getMaxValueExpression() {
        return max;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        // Already done, we only get one of these expressions after the operands have been analyzed
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                                        (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        return this;
    }

    /**
    * Get the data type of the items returned
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
    * Determine the static cardinality
    */

    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new IntegerRangeTest(value.copy(), min.copy(), max.copy());
    }

    /**
     * Get the immediate sub-expressions of this expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        Expression[] e = {value, min, max};
        return Arrays.asList(e).iterator();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (value == original) {
            value = replacement;
            found = true;
        }
        if (min == original) {
            min = replacement;
            found = true;
        }
        if (max == original) {
            max = replacement;
            found = true;
        }
                return found;
    }

    /**
    * Promote this expression if possible
    */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            if (offer.action != PromotionOffer.UNORDERED) {
                value = doPromotion(value, offer);
                min = doPromotion(min, offer);
                max = doPromotion(max, offer);
            }
            return this;
        }
    }

    /**
     * Evaluate the expression
     */

    public BooleanValue evaluateItem(XPathContext c) throws XPathException {
        NumericValue v = (NumericValue)value.evaluateItem(c);
        if (v==null) {
            return BooleanValue.FALSE;
        }

        if (!v.isWholeNumber()) {
            return BooleanValue.FALSE;
        }

        NumericValue v2 = (NumericValue)min.evaluateItem(c);

        if (v.compareTo(v2) < 0) {
            return BooleanValue.FALSE;
        }

        NumericValue v3 = (NumericValue)max.evaluateItem(c);

        return BooleanValue.get(v.compareTo(v3) <= 0);
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("integerRangeTest");
        value.explain(destination);
        min.explain(destination);
        max.explain(destination);
        destination.endElement();
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p/>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. </p>
     * <p/>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(value) + " = (" +
                ExpressionTool.parenthesize(min) + " to " +
                ExpressionTool.parenthesize(max) + ")";
    }
}

