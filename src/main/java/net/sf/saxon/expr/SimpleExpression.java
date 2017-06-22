////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Arrays;
import java.util.Iterator;


/**
 * An abstract implementation of Expression designed to make it easy to implement new expressions,
 * in particular, expressions to support extension instructions.
 *
 * <p>An implementation of this class must supply the {@link Callable#call(XPathContext, net.sf.saxon.om.Sequence[])}
 * method to evaluate the expression and return its result.</p>
 */

public abstract class SimpleExpression extends Expression implements Callable {

    public static final Expression[] NO_ARGUMENTS = new Expression[0];

    protected Expression[] arguments = NO_ARGUMENTS;

    /**
     * Constructor
     */

    public SimpleExpression() {
    }

    /**
     * Set the immediate sub-expressions of this expression.
     *
     * @param sub an array containing the sub-expressions of this expression
     */

    public void setArguments(Expression[] sub) {
        arguments = sub;
        for (Expression aSub : sub) {
            adoptChildExpression(aSub);
        }
    }

    /**
     * Get the immediate sub-expressions of this expression.
     *
     * @return an array containing the sub-expressions of this expression
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return Arrays.asList(arguments).iterator();
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
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == original) {
                arguments[i] = replacement;
                found = true;
            }
        }
        return found;
    }

    /**
     * Simplify the expression
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] != null) {
                arguments[i] = visitor.simplify(arguments[i]);
            }
        }
        return this;
    }


    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] != null) {
                arguments[i] = visitor.typeCheck(arguments[i], contextItemType);
            }
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] != null) {
                arguments[i] = visitor.optimize(arguments[i], contextItemType);
            }
        }
        return this;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        throw new UnsupportedOperationException("SimpleExpression.copy()");
    }

    /**
     * Offer promotion for this subexpression. The offer will be accepted if the subexpression
     * is not dependent on the factors (e.g. the context item) identified in the PromotionOffer.
     * By default the offer is not accepted - this is appropriate in the case of simple expressions
     * such as constant values and variable references where promotion would give no performance
     * advantage. This method is always called at compile time.
     *
     * @param offer  details of the offer, for example the offer to move
     *               expressions that don't depend on the context to an outer level in
     *               the containing expression
     * @param parent the parent of this expression
     * @return if the offer is not accepted, return this expression unchanged.
     *         Otherwise return the result of rewriting the expression to promote
     *         this subexpression
     * @throws XPathException if any error is detected
     */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] != null) {
                arguments[i] = doPromotion(arguments[i], offer);
            }
        }
        return this;
    }


    /**
     * Determine the data type of the items returned by this expression. This implementation
     * returns "item()", which can be overridden in a subclass.
     *
     * @param th the type hierarchy cache
     * @return the data type
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return Type.ITEM_TYPE;
    }

    /**
     * Determine the static cardinality of the expression. This implementation
     * returns "zero or more", which can be overridden in a subclass.
     */

    public int computeCardinality() {
        if ((getImplementationMethod() & Expression.EVALUATE_METHOD) == 0) {
            return StaticProperty.ALLOWS_ONE_OR_MORE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    public int computeDependencies() {
        return super.computeDependencies();
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    public final Item evaluateItem(XPathContext context) throws XPathException {
        return call(context, evaluateArguments(context)).head();
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    public final SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
        return call(context, evaluateArguments(context)).iterate();
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public final void process(XPathContext context) throws XPathException {
        SequenceIterator iter = call(context, evaluateArguments(context)).iterate();
        Item it;
        while ((it = iter.next()) != null) {
            context.getReceiver().append(it, locationId, NodeInfo.ALL_NAMESPACES);
        }
    }

    /**
     * Internal method to evaluate the arguments prior to calling the generic call() method
     * @param context the XPath dynamic context
     * @return the values of the (evaluated) arguments
     * @throws XPathException if a dynamic error occurs
     */

    private Sequence[] evaluateArguments(XPathContext context) throws XPathException {
        Sequence[] iters = new Sequence[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            iters[i] = SequenceTool.toLazySequence(arguments[i].iterate(context));
        }
        return iters;
    }

    /**
     * Get the subexpressions (arguments to this expression)
     *
     * @return the arguments, as an array
     */
    public Expression[] getArguments() {
        return arguments;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("userExpression");
        destination.emitAttribute("class", getExpressionType());
        for (Expression argument : arguments) {
            argument.explain(destination);
        }
        destination.endElement();
    }

    /**
     * Return a distinguishing name for the expression, for use in diagnostics.
     * By default the class name is used.
     *
     * @return a distinguishing name for the expression (defaults to the name of the implementation class)
     */

    public String getExpressionType() {
        return getClass().getName();
    }

}

