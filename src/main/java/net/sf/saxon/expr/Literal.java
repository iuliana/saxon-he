////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.ItemTypePattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import org.apache.log4j.Logger;

/**
 * A Literal is an expression whose value is constant: it is a class that implements the {@link Expression}
 * interface as a wrapper around a {@link GroundedValue}. This may derive from an actual literal in an XPath expression
 * or query, or it may be the result of evaluating a constant subexpression such as true() or xs:date('2007-01-16')
 */

public class Literal extends Expression {

	private static final Logger LOGGER = Logger.getLogger(Literal.class);
	
	private GroundedValue value;

	/**
	 * Create a literal as a wrapper around a Value
	 * @param value the value of this literal
	 */
	protected Literal(GroundedValue value) {
		this.value = value.reduce();
	}

	/**
	 * Get the value represented by this Literal
	 * @return the constant value
	 */
	public GroundedValue getValue() {
		return value;
	}

	/**
	 * Simplify an expression
	 * @return for a Value, this always returns the value unchanged
	 * @param visitor an expression visitor
	 */
	@Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
		return this;
	}

	/**
	 * TypeCheck an expression
	 * @return for a Value, this always returns the value unchanged
	 */
	@Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        return this;
	}

	/**
	 * Optimize an expression
	 * @return for a Value, this always returns the value unchanged
	 */
	@Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException  {
		return this;
	}

	/**
	 * Determine the data type of the items in the expression, if possible
	 * @return for the default implementation: AnyItemType (not known)
	 * @param th The TypeHierarchy. Can be null if the target is an AtomicValue.
	 */
    public ItemType getItemType(TypeHierarchy th) {
		return SequenceTool.getItemType(value, th);
	}

	/**
	 * Determine the cardinality
	 */
	public int computeCardinality() {
		if (value instanceof EmptySequence) {
			return StaticProperty.EMPTY;
		} else if (value instanceof AtomicValue) {
			return StaticProperty.EXACTLY_ONE;
		}
		try {
			SequenceIterator iter = value.iterate();
			Item next = iter.next();
			if (next == null) {
				return StaticProperty.EMPTY;
			} else {
				return iter.next() != null ? StaticProperty.ALLOWS_ONE_OR_MORE : StaticProperty.EXACTLY_ONE;
			}
		} catch (XPathException err) {
			LOGGER.error("Could not compute cardinality", err);
			return StaticProperty.ALLOWS_ZERO_OR_MORE;
		}
	}

	/**
	 * Compute the static properties of this expression (other than its type). For a
	 * Value, the only special property is {@link StaticProperty#NON_CREATIVE}.
	 * @return the value {@link StaticProperty#NON_CREATIVE}
	 */
	@Override
	public int computeSpecialProperties() {
		if (getValue() instanceof EmptySequence) {
			// An empty sequence has all special properties except "has side effects".
			return StaticProperty.SPECIAL_PROPERTY_MASK &~ StaticProperty.HAS_SIDE_EFFECTS;
		}
        return StaticProperty.NON_CREATIVE;
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
        if (value instanceof IntegerValue) {
			return new IntegerValue[]{(IntegerValue) value, (IntegerValue) value};
        } else if (value instanceof IntegerRange) {
            return new IntegerValue[]{
                    Int64Value.makeIntegerValue(((IntegerRange)value).getStart()),
                    Int64Value.makeIntegerValue(((IntegerRange)value).getEnd())};
        } else {
            return null;
        }
    }

    /**
	 * Determine whether this is a vacuous expression as defined in the XQuery update specification
	 * @return true if this expression is vacuous
	 */
    @Override
	public boolean isVacuousExpression() {
        return value.getLength() == 0;
	}

	/**
	 * Copy an expression. This makes a deep copy.
	 *
	 * @return the copy of the original expression
	 */
    public Expression copy() {
		return new Literal(value);
	}

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @param is30   true if this is XSLT 3.0
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean is30) throws XPathException {
    	return isEmptySequence(this) ? new ItemTypePattern(ErrorType.getInstance()) : super.toPattern(config, is30);
    }

    /**
	 * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
	 * by an expression in a source tree.
	 *
	 * @param pathMap     the PathMap to which the expression should be added
	 * @param pathMapNodeSet the set of nodes within the path map
	 * @return the pathMapNode representing the focus established by this expression, in the case where this
	 *         expression is the first operand of a path expression or filter expression
	 */
    @Override
	public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
		return pathMapNodeSet;
	}

	/**
	 * Determine which aspects of the context the expression depends on. The result is
	 * a bitwise-or'ed value composed from constants such as StaticProperty.VARIABLES and
	 * StaticProperty.CURRENT_NODE
	 * @return for a Value, this always returns zero.
	 */
    @Override
	public final int getDependencies() {
		return 0;
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
	 * @throws net.sf.saxon.trans.XPathException
	 *          if any dynamic error occurs evaluating the
	 *          expression
	 */
    @Override
    public SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
		return value.iterate();
	}

	/**
	 * Return an Iterator to iterate over the values of a sequence. The value of every
	 * expression can be regarded as a sequence, so this method is supported for all
	 * expressions. This default implementation handles iteration for expressions that
	 * return singleton values: for non-singleton expressions, the subclass must
	 * provide its own implementation.
	 *
	 * @return a SequenceIterator that can be used to iterate over the result
	 *         of the expression
	 * @throws net.sf.saxon.trans.XPathException
	 *          if any dynamic error occurs evaluating the
	 *          expression
	 */
    public SequenceIterator<? extends Item> iterate() throws XPathException {
		return value.iterate();
	}

	/**
	 * Evaluate as a singleton item (or empty sequence). Note: this implementation returns
	 * the first item in the sequence. The method should not be used unless appropriate type-checking
	 * has been done to ensure that the value will be a singleton.
	 */
    @Override
	public Item evaluateItem(XPathContext context) throws XPathException {
		if (value instanceof Item) {
			return (Item)value;
		}
		return value.iterate().next();
	}


	/**
	 * Process the value as an instruction, without returning any tail calls
	 * @param context The dynamic context, giving access to the current node,
	 * the current variables, etc.
	 */
    @Override
	public void process(XPathContext context) throws XPathException {
		SequenceIterator iter = value.iterate();
		SequenceReceiver out = context.getReceiver();
		while (true) {
			Item it = iter.next();
			if (it == null) {
				break;
			}
			out.append(it, 0, NodeInfo.ALL_NAMESPACES);
		}
	}

	/*
	 * Evaluate an expression as a String. This function must only be called in contexts
	 * where it is known that the expression will return a single string (or where an empty sequence
	 * is to be treated as a zero-length string). Implementations should not attempt to convert
	 * the result to a string, other than converting () to "". This method is used mainly to
	 * evaluate expressions produced by compiling an attribute value template.
	 *
	 * @exception net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
	 *     expression
	 * @exception ClassCastException if the result type of the
	 *     expression is not xs:string?
	 * @param context The context in which the expression is to be evaluated
	 * @return the value of the expression, evaluated in the current context.
	 *     The expression must return a string or (); if the value of the
	 *     expression is (), this method returns "".
	 */
	@Override
	public CharSequence evaluateAsString(XPathContext context) throws XPathException {
		AtomicValue value = (AtomicValue) evaluateItem(context);
		if (value == null) {
			return "";
		}
		return value.getStringValueCS();
	}

	/**
	 * Get the effective boolean value of the expression. This returns false if the value
	 * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
	 * false. Otherwise it returns true.
	 *
	 * @param context The context in which the expression is to be evaluated
	 * @exception net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
	 *     expression
	 * @return the effective boolean value
	 */
	@Override
	public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
		return ExpressionTool.effectiveBooleanValue(value.iterate());
	}

	/**
	 * Evaluate an updating expression, adding the results to a Pending Update List.
	 * The default implementation of this method, which is used for non-updating expressions,
	 * throws an UnsupportedOperationException. The implementation for a literal representing
	 * an empty sequence, however, is a no-op.
	 *
	 * @param context the XPath dynamic evaluation context
	 * @param pul     the pending update list to which the results should be written
	 */
	@Override
	public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
		if (value instanceof EmptySequence) {
			// do nothing
		} else {
			super.evaluatePendingUpdates(context, pul);
		}
	}

	/**
	 * Determine whether two literals are equal, when considered as expressions.
	 * @param obj the other expression
	 * @return true if the two literals are equal. The test here requires (a) identity in the
	 * sense defined by XML Schema (same value in the same value space), and (b) identical type
	 * annotations. For example the literal xs:int(3) is not equal (as an expression) to xs:short(3), 
	 * because the two expressions are not interchangeable.
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Literal)) {
			return false;
		}
		GroundedValue v0 = value;
		GroundedValue v1 = ((Literal) obj).value;
		try {
			SequenceIterator i0 = v0.iterate();
			SequenceIterator i1 = v1.iterate();
			while (true) {
				Item m0 = i0.next();
				Item m1 = i1.next();
				if (m0 == null && m1 == null) {
					return true;
				}
				if (m0 == null || m1 == null) {
					return false;
				}
				boolean n0 = (m0 instanceof NodeInfo);
				boolean n1 = (m1 instanceof NodeInfo);
				if (n0 != n1) {
					return false;
				}
				if (n0 && n1 && !((NodeInfo) m0).isSameNodeInfo((NodeInfo) m1)) {
					return false;
				}
				if (m0 instanceof AtomicValue && m1 instanceof AtomicValue) {
					if ((!((AtomicValue) m0).isIdentical((AtomicValue) m1)) ||
						((AtomicValue) m0).getItemType() != ((AtomicValue) m1).getItemType()) {
						return false;
					}
				}
			}
		} catch (XPathException err) {
			LOGGER.error("Could not determine if two literals are equal", err);
			return false;
		}
	}

	/**
	 * Return a hash code to support the equals() function
	 */
	public int hashCode() {
        if (value instanceof AtomicSequence) {
			return ((AtomicSequence) value).getSchemaComparable().hashCode();
        } else {
            return super.hashCode();
        }
	}

	/**
	 * The toString() method for an expression attempts to give a representation of the expression
	 * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
	 * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
	 */
	@Override
	public String toString() {
		return value.toString();
	}

	/**
	 * Diagnostic print of expression structure. The abstract expression tree is written to the supplied output
	 * destination.
	 */
	public void explain(ExpressionPresenter out) {
		out.startElement("literal");
		if (value instanceof EmptySequence) {
			out.emitAttribute("value", "()");
		} else if (value instanceof AtomicValue) {
			// noinspection RedundantCast
			out.emitAttribute("value", ((AtomicValue) value).getStringValue());
			// cast is needed to tell the compiler there's no exception possible
			out.emitAttribute("type", ((AtomicValue) value).getItemType().getDisplayName());
		} else {
			try {
				out.emitAttribute("count", Integer.toString(value.getLength()));
				if (value.getLength() < 20) {
					SequenceIterator iter = iterate();
					while (true) {
						Item it = iter.next();
						if (it == null) {
							break;
						}
						if (it instanceof NodeInfo) {
							out.startElement("node");
							out.emitAttribute("path", Navigator.getPath(((NodeInfo) it)));
							out.emitAttribute("uri", ((NodeInfo) it).getSystemId());
							out.endElement();
						} else if (it instanceof AtomicValue) {
							out.startElement("atomicValue");
							out.emitAttribute("value", it.getStringValue());
							out.emitAttribute("type", ((AtomicValue) it).getItemType().getDisplayName());
							out.endElement();
						} else if (it instanceof FunctionItem) {
							out.startElement("functionItem");
							if (((FunctionItem) it).getFunctionName() != null) {
								out.emitAttribute("name", ((FunctionItem) it).getFunctionName().getDisplayName());
							}
							out.emitAttribute("arity", Integer.toString(((FunctionItem) it).getArity()));
							out.endElement();
						} else if (it instanceof ObjectValue) {
							out.startElement("externalObject");
							out.emitAttribute("class", ((ObjectValue) it).getObject().getClass().getName());
							out.endElement();
						}
					}
				}
			} catch (XPathException err) {
				LOGGER.error("Could not print expression structure", err);
			}
		}
		out.endElement();
	}

	/**
	 * Test whether the literal wraps an atomic value. (Note, if this method returns false,
	 * this still leaves the possibility that the literal wraps a sequence that happens to contain
	 * a single atomic value).
	 * @param exp an expression
	 * @return true if the expression is a literal and the literal wraps an AtomicValue
	 */
	public static boolean isAtomic(Expression exp) {
		return exp instanceof Literal && ((Literal) exp).getValue() instanceof AtomicValue;
	}

	/**
	 * Test whether the literal explicitly wraps an empty sequence. (Note, if this method returns false,
	 * this still leaves the possibility that the literal wraps a sequence that happens to be empty).
	 * @param exp an expression
	 * @return true if the expression is a literal and the value of the literal is an empty sequence
	 */
	public static boolean isEmptySequence(Expression exp) {
		return exp instanceof Literal && ((Literal) exp).getValue() instanceof EmptySequence;
	}

	/**
	 * Test if a literal represents the boolean value true
	 * @param exp an expression
	 * @param value true or false
	 * @return true if the expression is a literal and the literal represents the boolean value given in the
	 * second argument
	 */
	public static boolean isConstantBoolean(Expression exp, boolean value) {
		if (exp instanceof Literal) {
			GroundedValue b = ((Literal) exp).getValue();
			return (b instanceof BooleanValue && ((BooleanValue) b).getBooleanValue() == value);
		}
		return false;
	}

	/**
	 * Test if a literal represents the integer value 1
	 * @param exp an expression
	 * @return true if the expression is a literal and the literal represents the integer value 1
	 */
	public static boolean isConstantOne(Expression exp) {
		if (exp instanceof Literal) {
			GroundedValue v = ((Literal) exp).getValue();
			return (v instanceof Int64Value && ((Int64Value) v).longValue() == 1);
		}
		return false;
	}

	/**
	 * Determine whether the expression can be evaluated without reference to the part of the context
	 * document outside the subtree rooted at the context node.
	 * @return true if the expression has no dependencies on the context node, or if the only dependencies
	 *         on the context node are downward selections using the self, child, descendant, attribute, and namespace
	 *         axes.
	 */
	@Override
	public boolean isSubtreeExpression() {
		return true;
	}

    /**
     * Factory method to make an empty-sequence literal
     * @return a literal whose value is the empty sequence
     */
    public static Literal makeEmptySequence() {
        return new Literal(EmptySequence.getInstance());
    }

    /**
     * Factory method to create a literal as a wrapper around a Value (factory method)
     * @param value the value of this literal
     * @return the Literal
     */
    public static Literal makeLiteral(GroundedValue value) {
        value = value.reduce();
        if (value instanceof StringValue) {
            return new StringLiteral((StringValue)value);

        } else {
            return new Literal(value);
        }
    }

}