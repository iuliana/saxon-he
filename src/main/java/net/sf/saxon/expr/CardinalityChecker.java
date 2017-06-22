////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import org.apache.log4j.Logger;

import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.event.TypeCheckingFilter;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceExtent;

/**
* A CardinalityChecker implements the cardinality checking of "treat as": that is,
* it returns the supplied sequence, checking that its cardinality is correct
*/

public final class CardinalityChecker extends UnaryExpression {

	private static final Logger LOGGER = Logger.getLogger(CardinalityChecker.class);
    private int requiredCardinality = -1;
    private RoleLocator role;

    /**
     * Private Constructor: use factory method
     * @param sequence the base sequence whose cardinality is to be checked
     * @param cardinality the required cardinality
     * @param role information to be used in error reporting
    */
    private CardinalityChecker(Expression sequence, int cardinality, RoleLocator role) {
        super(sequence);
        requiredCardinality = cardinality;
        this.role = role;
        //computeStaticProperties();
        adoptChildExpression(sequence);
    }

    /**
     * Factory method to construct a CardinalityChecker. The method may create an expression that combines
     * the cardinality checking with the functionality of the underlying expression class
     * @param sequence the base sequence whose cardinality is to be checked
     * @param cardinality the required cardinality
     * @param role information to be used in error reporting
     * @return a new Expression that does the CardinalityChecking (not necessarily a CardinalityChecker)
     */
    public static Expression makeCardinalityChecker(Expression sequence, int cardinality, RoleLocator role) {
        Expression result;
        if (sequence instanceof Atomizer && !Cardinality.allowsMany(cardinality)) {
            Expression base = ((Atomizer)sequence).getBaseExpression();
            result = new SingletonAtomizer(base, role, Cardinality.allowsZero(cardinality));
        } else {
            result = new CardinalityChecker(sequence, cardinality, role);
        }
        ExpressionTool.copyLocationInfo(sequence, result);
        return result;
    }

    /**
     * Get the required cardinality
     * @return the cardinality required by this checker
     */
    public int getRequiredCardinality() {
        return requiredCardinality;
    }

    /**
    * Type-check the expression
    */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        if (requiredCardinality == StaticProperty.ALLOWS_ZERO_OR_MORE ||
                    Cardinality.subsumes(requiredCardinality, operand.getCardinality())) {
            return operand;
        }
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
    @Override
	public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType)
		throws XPathException {
		operand = visitor.optimize(operand, contextItemType);
		if (requiredCardinality == StaticProperty.ALLOWS_ZERO_OR_MORE ||
			Cardinality.subsumes(requiredCardinality, operand.getCardinality())) {
			return operand;
		}
		// do cardinality checking before item checking (may avoid the need for a mapping iterator)
		if (operand instanceof ItemChecker) {
			ItemChecker checker = (ItemChecker) operand;
			Expression base = checker.getBaseExpression();
			operand = base;
			checker.replaceSubExpression(base, this);
			checker.resetLocalStaticProperties();
			this.resetLocalStaticProperties();
			return checker;
		}
		if (operand instanceof Literal) {
			return new Literal(SequenceExtent.makeSequenceExtent(iterate(visitor.makeDynamicContext())));
		}
		return this;
	}

    /**
     * Set the error code to be returned (this is used when evaluating the functions such
     * as exactly-one() which have their own error codes)
     * @param code the error code to be used
     */
    public void setErrorCode(String code) {
        role.setErrorCode(code);
    }

    /**
     * Get the RoleLocator, which contains diagnostic information for use if the cardinality check fails
     * @return the diagnostic information
     */
    public RoleLocator getRoleLocator() {
        return role;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */
    public int getImplementationMethod() {
        int m = ITERATE_METHOD | PROCESS_METHOD | ITEM_FEED_METHOD;
        if (!Cardinality.allowsMany(requiredCardinality)) {
            m |= EVALUATE_METHOD;
        }
        return m;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return operand.getIntegerBounds();
    }

    /**
    * Iterate over the sequence of values
    */
    @Override
	public SequenceIterator iterate(XPathContext context) throws XPathException {
		SequenceIterator base = operand.iterate(context);
		// If the base iterator knows how many items there are, then check it now rather than wasting time
		if ((base.getProperties() & SequenceIterator.LAST_POSITION_FINDER) != 0) {
			int count = ((LastPositionFinder) base).getLength();
			if (count == 0 && !Cardinality.allowsZero(requiredCardinality)) {
				typeError("An empty sequence is not allowed as the " +
					role.getMessage(), role.getErrorCode(), context);
			} else if (count == 1 && requiredCardinality == StaticProperty.EMPTY) {
				typeError("The only value allowed for the " +
					role.getMessage() + " is an empty sequence", role.getErrorCode(), context);
			} else if (count > 1 && !Cardinality.allowsMany(requiredCardinality)) {
				typeError("A sequence of more than one item is not allowed as the " +
					role.getMessage() + depictSequenceStart(base.getAnother(), 2),
					role.getErrorCode(), context);
			}
			return base;
		}
		// Otherwise return an iterator that does the checking on the fly
		return new CardinalityCheckingIterator(base, requiredCardinality, role, this);
	}

    /**
     * Show the first couple of items in a sequence in an error message
     * @param seq iterator over the sequence
     * @param max maximum number of items to be shown
     * @return a message display of the contents of the sequence
     */
    public static String depictSequenceStart(SequenceIterator seq, int max) {
        try {
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
            int count = 0;
            sb.append(" (");
            while (true) {
                Item next = seq.next();
                if (next == null) {
                    sb.append(") ");
                    return sb.toString();
                }
                if (count++ > 0) {
                    sb.append(", ");
                }
                if (count > max) {
                    sb.append("...) ");
                    return sb.toString();
                }

                sb.append(Err.depict(next));
            }
        } catch (XPathException e) {
        	LOGGER.error("Could not depict start sequence", e);
            return "";
        }
    }

    /**
     * Evaluate as an Item. For this class, this implies checking that the underlying
     * expression delivers a singleton.
    */
    @Override
	public Item evaluateItem(XPathContext context) throws XPathException {
		SequenceIterator iter = operand.iterate(context);
		Item first = iter.next();
		if (first == null && !Cardinality.allowsZero(requiredCardinality)) {
				typeError("An empty sequence is not allowed as the " + role.getMessage(), role.getErrorCode(), context);
				return null;
		} else if (requiredCardinality == StaticProperty.EMPTY) {
			typeError("An empty sequence is required as the " + role.getMessage(), role.getErrorCode(), context);
			return null;
		}
		Item second = iter.next();
		if (second != null) {
			typeError("A sequence of more than one item is not allowed as the " +
				role.getMessage() + depictSequenceStart(iter.getAnother(), 2), role.getErrorCode(), context);
			return null;
		}
		return first;
	}

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */
    @Override
    public void process(XPathContext context) throws XPathException {
        Expression next = operand;
        ItemType type = Type.ITEM_TYPE;
        if (next instanceof ItemChecker) {
            type = ((ItemChecker)next).getRequiredType();
            next = ((ItemChecker)next).getBaseExpression();
        }
        if ((next.getImplementationMethod() & PROCESS_METHOD) != 0 && !(type instanceof DocumentNodeTest)) {
            SequenceReceiver out = context.getReceiver();
            TypeCheckingFilter filter = new TypeCheckingFilter(out);
            filter.setRequiredType(type, requiredCardinality, role, this);
            context.setReceiver(filter);
            next.process(context);
            try {
                filter.close();
            } catch (XPathException e) {
                e.maybeSetLocation(this);
                throw e;
            }
            context.setReceiver(out);
        } else {
            super.process(context);
        }
    }

    /**
    * Determine the data type of the items returned by the expression, if possible
    * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
    * or Type.ITEM (meaning not known in advance)
     * @param th the type hierarchy cache
     */
    @Override
    public ItemType getItemType(TypeHierarchy th) {
	    return operand.getItemType(th);
	}

	/**
	* Determine the static cardinality of the expression
	*/
    @Override
	public int computeCardinality() {
        return requiredCardinality;
	}

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-signficant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */
    @Override
    public int computeSpecialProperties() {
        return operand.getSpecialProperties();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */
    public Expression copy() {
        return new CardinalityChecker(getBaseExpression().copy(), requiredCardinality, role);
    }

    /**
    * Is this expression the same as another expression?
    */
	@Override
	public boolean equals(Object other) {
		return super.equals(other) && requiredCardinality == ((CardinalityChecker) other).requiredCardinality;
	}

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */
    @Override
    public int hashCode() {
        return super.hashCode() ^ requiredCardinality;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("checkCardinality");
        out.emitAttribute("occurs", Cardinality.toString(requiredCardinality));
        operand.explain(out);
        out.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form. For subclasses of Expression that represent XPath expressions, the result should always
     * be a string that parses as an XPath 3.0 expression.
     */
    @Override
    public String toString() {
    	String expression = operand.toString();
        switch (requiredCardinality) {
            case StaticProperty.ALLOWS_ONE:
                return "exactly-one(" + expression + ')';
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return "zero-or-one(" + expression + ')';
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return "one-or-more(" + expression + ')';
            case StaticProperty.EMPTY:
                return "must-be-empty(" + expression + ")";
            default:
                return "check(" + expression + ')';
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "CheckCardinality";
    }
}
