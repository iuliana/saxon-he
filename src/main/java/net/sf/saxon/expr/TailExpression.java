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
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.SimplePositionalPattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceExtent;

import java.util.Iterator;
import java.util.List;

/**
 * A TailExpression represents a FilterExpression of the form EXPR[position() > n]
 * Here n is usually 2, but we allow other values
 */
public class TailExpression extends Expression {

    Expression base;
    int start;      // 1-based offset of first item from base expression
                    // to be included

    /**
     * Construct a TailExpression, representing a filter expression of the form
     * $base[position() >= $start]
     * @param base    the expression to be filtered
     * @param start   the position (1-based) of the first item to be included
     */
    public TailExpression(Expression base, int start) {
        this.base = base;
        this.start = start;
        adoptChildExpression(base);
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        base = visitor.typeCheck(base, contextItemType);
        return this;
    }

	@Override
	public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType)
		throws XPathException {
		base = visitor.optimize(base, contextItemType);
		if (base instanceof Literal) {
			GroundedValue value = SequenceExtent
				.makeSequenceExtent(iterate(visitor.getStaticContext().makeEarlyEvaluationContext()));
			return Literal.makeLiteral(value);
		}
		return this;
	}

    @Override
    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            if (offer.action != PromotionOffer.UNORDERED) {
                base = doPromotion(base, offer);
            }
            return this;
        }
    }
    
    @Override
    public int computeSpecialProperties() {
        return base.getSpecialProperties();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */
    public Expression copy() {
        return new TailExpression(base.copy(), start);
    }

    public ItemType getItemType(TypeHierarchy th) {
        return base.getItemType(th);
    }

    public int computeCardinality() {
        return base.getCardinality() | StaticProperty.ALLOWS_ZERO;
    }

    @Override
    public Iterator<Expression> iterateSubExpressions() {
        return new MonoIterator(base);
    }

    /**
      * Replace one subexpression by a replacement subexpression
      * @param original the original subexpression
      * @param replacement the replacement subexpression
      * @return true if the original subexpression is found
      */
	@Override
	public boolean replaceSubExpression(Expression original, Expression replacement) {
		boolean found = false;
		if (base == original) {
			base = replacement;
			found = true;
		}
		return found;
	}

    /**
     * Get the base expression (of which this expression returns the tail part of the value)
     * @return the base expression
     */
    public Expression getBaseExpression() {
        return base;
    }

    /**
     * Get the start offset
     * @return the one-based start offset (returns 2 if all but the first item is being selected)
     */
    public int getStart() {
        return start;
    }

    /**
     * Compare two expressions to see if they are equal
     * @param other the other expression
     * @return true if the expressions are equivalent
     */
	public boolean equals(Object other) {
		return other instanceof TailExpression && start == ((TailExpression) other).start &&
			base.equals(((TailExpression) other).base);
	}

    public int hashCode() {
        return base.hashCode();
    }

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
		if (base instanceof AxisExpression && ((AxisExpression) base).getAxis() == AxisInfo.CHILD &&
			base.getItemType(th).getPrimitiveType() == Type.ELEMENT) {
			return new SimplePositionalPattern((NodeTest) base.getItemType(th),
				Literal.makeLiteral(Int64Value.makeIntegerValue(getStart())), Token.FGE);
		} else {
			return super.toStreamingPattern(config, reasonForFailure);
		}
	}

    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        SequenceIterator baseIter = base.iterate(context);
        return TailIterator.make(baseIter, start);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */
    public void explain(ExpressionPresenter destination) {
        destination.startElement("tail");
        destination.emitAttribute("start", Integer.toString(start));
        base.explain(destination);
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
        if (start == 2) {
            return "tail(" + base.toString() + ")";
        } else {
            return ExpressionTool.parenthesize(base) + "[position() ge " + start + "]";
        }
    }
}
