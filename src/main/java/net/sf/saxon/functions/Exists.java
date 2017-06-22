////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.BooleanValue;

/**
 * Implementation of the fn:exists function
 */
public class Exists extends Aggregate implements Negatable, Callable {

	public int getImplementationMethod() {
		return super.getImplementationMethod() | WATCH_METHOD;
	}

	/**
	 * Check whether this specific instance of the expression is negatable
	 *
	 * @return true if it is
	 */
	public boolean isNegatable(ExpressionVisitor visitor) {
		return true;
	}

	/**
	 * Return the negation of the expression
	 *
	 * @return the negation of the expression
	 */
	public Expression negate() {
		FunctionCall fc = SystemFunctionCall.makeSystemFunction("empty", getArguments());
		fc.setLocationId(getLocationId());
		return fc;
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
	 * @throws net.sf.saxon.trans.XPathException if an error is discovered during this phase
	 *                                           (typically a type error)
	 */

    /*@NotNull*/
	public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor,
			ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
		Expression e2 = super.optimize(visitor, contextItemType);
		if (e2 != this) {
			return e2;
		}
		// See if we can deduce the answer from the cardinality
		int c = argument[0].getCardinality();
		if (c == StaticProperty.ALLOWS_ONE_OR_MORE) {
			return Literal.makeLiteral(BooleanValue.TRUE);
		} else if (c == StaticProperty.ALLOWS_ZERO) {
			return Literal.makeLiteral(BooleanValue.FALSE);
		}
		argument[0] = ExpressionTool.unsorted(visitor.getConfiguration().obtainOptimizer(), argument[0], false);
		// Rewrite
		//    exists(A|B) => exists(A) or exists(B)
		if (argument[0] instanceof VennExpression && !visitor.isOptimizeForStreaming()) {
			VennExpression v = (VennExpression) argument[0];
			if (v.getOperator() == Token.UNION) {
				FunctionCall e0 = SystemFunctionCall.makeSystemFunction("exists", new Expression[]{v.getOperands()[0]});
				FunctionCall e1 = SystemFunctionCall.makeSystemFunction("exists", new Expression[]{v.getOperands()[1]});
				return new OrExpression(e0, e1).optimize(visitor, contextItemType);
			}
		}
		return this;
	}

	/**
	 * Evaluate the function
	 */
	public BooleanValue evaluateItem(XPathContext context) throws XPathException {
		return BooleanValue.get(effectiveBooleanValue(context));
	}

	/**
	 * Evaluate the function in a boolean context
	 */
	public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
		try {
			return exists(argument[0].iterate(c));
		} catch (XPathException err) {
			err.maybeSetLocation(this);
			err.maybeSetContext(c);
			throw err;
		}
	}

	private boolean exists(SequenceIterator iter) throws XPathException {
		boolean result;
		if ((iter.getProperties() & SequenceIterator.LOOKAHEAD) != 0) {
			result = false;
			if (((LookaheadIterator) iter).hasNext()) {
				while (((LookaheadIterator) iter).hasNext()) {
					Item currentItem = iter.next();
					if (currentItem != null && !"()".equals(currentItem.getStringValue())) {
						iter.close();
						return true;
					}
				}
			}
		} else {
			result = iter.next() != null;
		}
		iter.close();
		return result;
	}

	/**
	 * Evaluate the expression
	 *
	 * @param context   the dynamic evaluation context
	 * @param arguments the values of the arguments, supplied as SequenceIterators
	 * @return the result of the evaluation, in the form of a SequenceIterator
	 * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
	 */
	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		try {
			return BooleanValue.get(exists(arguments[0].iterate()));
		} catch (XPathException err) {
			err.maybeSetLocation(this);
			err.maybeSetContext(context);
			throw err;
		}
	}
}

