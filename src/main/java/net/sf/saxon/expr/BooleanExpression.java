////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.BooleanFn;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

import java.util.Iterator;
import java.util.List;


/**
 * Boolean expression: two truth values combined using AND or OR.
 */

public abstract class BooleanExpression extends BinaryExpression implements Negatable {

	/**
	 * Construct a boolean expression
	 * @param p1 the first operand
	 * @param operator one of {@link Token#AND} or {@link Token#OR}
	 * @param p2 the second operand
	 */

	public BooleanExpression(Expression p1, int operator, Expression p2) {
		super(p1, operator, p2);
	}


	/**
	 * Get a name identifying the kind of expression, in terms meaningful to a user.
	 *
	 * @return a name identifying the kind of expression, in terms meaningful to a user.
	 *         The name will always be in the form of a lexical XML QName, and should match the name used
	 *         in explain() output displaying the expression.
	 */
	@Override
	public String getExpressionName() {
		return Token.tokens[getOperator()] + "-expression";
	}

	/*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
		Expression e = super.typeCheck(visitor, contextItemType);
		if (e == this) {
			XPathException err0 = TypeChecker.ebvError(operand0, visitor.getConfiguration().getTypeHierarchy());
			if (err0 != null) {
				err0.setLocator(this);
				throw err0;
			}
			XPathException err1 = TypeChecker.ebvError(operand1, visitor.getConfiguration().getTypeHierarchy());
			if (err1 != null) {
				err1.setLocator(this);
				throw err1;
			}
			// Precompute the EBV of any constant operand
			if (operand0 instanceof Literal && !(((Literal)operand0).getValue() instanceof BooleanValue)) {
				operand0 = Literal.makeLiteral(BooleanValue.get(operand0.effectiveBooleanValue(null)));
			}
			if (operand1 instanceof Literal && !(((Literal)operand1).getValue() instanceof BooleanValue)) {
				operand1 = Literal.makeLiteral(BooleanValue.get(operand1.effectiveBooleanValue(null)));
			}
		}
		return e;
	}

	/**
	 * Determine the static cardinality. Returns [1..1]
	 */

	public int computeCardinality() {
		return StaticProperty.EXACTLY_ONE;
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

        final Expression e = super.optimize(visitor, contextItemType);
		if (e != this) {
			return e;
		}

		Optimizer opt = visitor.getConfiguration().obtainOptimizer();
		operand0 = ExpressionTool.unsortedIfHomogeneous(opt, operand0);
		operand1 = ExpressionTool.unsortedIfHomogeneous(opt, operand1);

		Expression op0 = BooleanFn.rewriteEffectiveBooleanValue(operand0, visitor, contextItemType);
		if (op0 != null) {
			operand0 = op0;
		}
		Expression op1 = BooleanFn.rewriteEffectiveBooleanValue(operand1, visitor, contextItemType);
		if (op1 != null) {
			operand1 = op1;
		}
		return this;
	}

	protected Expression forceToBoolean(Expression in, TypeHierarchy th) {
		if (in.getItemType(th) == BuiltInAtomicType.BOOLEAN && in.getCardinality() == StaticProperty.ALLOWS_ONE) {
			return in;
		} else {
			return SystemFunctionCall.makeSystemFunction("boolean", new Expression[]{in});
		}
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
	 * Return the negation of this boolean expression, that is, an expression that returns true
	 * when this expression returns false, and vice versa
	 *
	 * @return the negation of this expression
	 */

	public abstract Expression negate();

	/**
	 * Evaluate the expression
	 */

	public BooleanValue evaluateItem(XPathContext context) throws XPathException {
		return BooleanValue.get(effectiveBooleanValue(context));
	}

	/**
	 * Evaluate as a boolean.
	 */

	public abstract boolean effectiveBooleanValue(XPathContext c) throws XPathException;

	/**
	 * Determine the data type of the expression
	 * @return BuiltInAtomicType.BOOLEAN
	 * @param th the type hierarchy cache
	 */

	/*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
		return BuiltInAtomicType.BOOLEAN;
	}

	/**
	 * Construct a list containing the "anded" subexpressions of an expression:
	 * if the expression is (A and B and C), this returns (A, B, C).
	 * @param exp the expression to be decomposed
	 * @param list the list to which the subexpressions are to be added.
	 */

	public static void listAndComponents(Expression exp, List list) {
		if (exp instanceof BooleanExpression && ((BooleanExpression)exp).getOperator() == Token.AND) {
			for (Iterator iter = exp.iterateSubExpressions(); iter.hasNext();) {
				listAndComponents((Expression)iter.next(), list);
			}
		} else {
			list.add(exp);
		}
	}
}

