////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.SimplePositionalPattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.MemoClosure;
import net.sf.saxon.value.NumericValue;

import java.util.Iterator;
import java.util.List;

/**
 * A SubscriptExpression represents a FilterExpression of the form EXPR[n]
 * where n is known to be singleton numeric and to be independent of the focus; it does not need to be constant
 */
public class SubscriptExpression extends SingleItemFilter {

    Expression subscript;

    /**
     * Construct a SubscriptExpression
     * @param base        the expression to be filtered
     * @param subscript   the positional subscript filter
     */

    public SubscriptExpression(Expression base, Expression subscript) {
        this.operand = base;
        this.subscript = subscript;
        adoptChildExpression(base);
        adoptChildExpression(subscript);
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        subscript = visitor.typeCheck(subscript, contextItemType);
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.optimize(operand, contextItemType);
        subscript = visitor.optimize(subscript, contextItemType);
        if (Literal.isConstantOne(subscript)) {
            return FirstItemExpression.makeFirstItemExpression(operand);
        }
        return this;
    }

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            if (offer.action != PromotionOffer.UNORDERED) {
                operand = doPromotion(operand, offer);
                subscript = doPromotion(subscript, offer);
            }
            return this;
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new SubscriptExpression(operand.copy(), subscript.copy());
    }

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(operand, subscript);
    }

    /**
      * Replace one subexpression by a replacement subexpression
      * @param original the original subexpression
      * @param replacement the replacement subexpression
      * @return true if the original subexpression is found
      */

     public boolean replaceSubExpression(Expression original, Expression replacement) {
         boolean found = false;
         if (operand == original) {
             operand = replacement;
             found = true;
         } else if (subscript == original) {
             subscript = replacement;
             found = true;
         }
         return found;
     }

    /**
     * Get the subscript expression
     * @return the expression used to compute the one-based start offset
     */

    public Expression getSubscriptExpression() {
        return subscript;
    }

    /**
     * Compare two expressions to see if they are equal
     * @param other the other expression
     * @return true if the expressions are equivalent
     */

    public boolean equals(Object other) {
        return other instanceof SubscriptExpression &&
                operand.equals(((SubscriptExpression)other).operand) &&
                subscript == ((SubscriptExpression)other).subscript;
    }

    public int hashCode() {
        return operand.hashCode() ^ subscript.hashCode();
    }

    /**
     * Get the static cardinality: this implementation is appropriate for [1] and [last()] which will always
     * return something if the input is non-empty
    */

    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }


//#ifdefined STREAM

    /**
     * Convert this expression to a streaming pattern (a pattern used internally to match nodes during
     * push processing of an event stream)
     *
     * @param config           the Saxon configuration
     * @param reasonForFailure a list which will be populated with messages giving reasons why the
     *                         expression cannot be converted
     * @return the equivalent pattern if conversion succeeds; otherwise null
     */
    @Override
    public Pattern toStreamingPattern(Configuration config, List<String> reasonForFailure) {
        TypeHierarchy th = config.getTypeHierarchy();
        Expression base = getBaseExpression();

        if (base instanceof AxisExpression &&
                ((AxisExpression)base).getAxis() == AxisInfo.CHILD &&
                base.getItemType(th).getPrimitiveType() == Type.ELEMENT) {
            return new SimplePositionalPattern(
                    (NodeTest)base.getItemType(th),
                    this,
                    Token.FEQ);
        } else {
            return super.toStreamingPattern(config, reasonForFailure);
        }

    }


//#endif

    public Item evaluateItem(XPathContext context) throws XPathException {
        NumericValue index = (NumericValue)subscript.evaluateItem(context);
        if (index == null) {
            return null;
        }
        if (index.compareTo(Integer.MAX_VALUE) <= 0 && index.isWholeNumber()) {
            int intindex = (int)index.longValue();
            if (intindex < 1) {
                return null;
            }
            Item item;
            SequenceIterator iter = operand.iterate(context);
            if (intindex == 1) {
                item = iter.next();
            } else if (iter instanceof MemoClosure.ProgressiveIterator) {
                item = ((MemoClosure.ProgressiveIterator)iter).itemAt(intindex - 1);
            } else if ((iter.getProperties() & SequenceIterator.GROUNDED) != 0) {
                GroundedValue value = ((GroundedIterator)iter).materialize();
                item = value.itemAt(intindex-1);
            } else {
                SequenceIterator tail = TailIterator.make(iter, intindex);
                item = tail.next();
                tail.close();
            }
            return item;
        } else {
            // there is no item at the required position
            return null;
        }
    }






    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("subscript");
        operand.explain(destination);
        subscript.explain(destination);
        destination.endElement();
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p/>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(operand) + "[" + subscript.toString() + "]";
    }
}

