////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * Arithmetic Expression: an expression using one of the operators
 * plus, minus, multiply, div, idiv, mod. Note that this code does not handle backwards
 * compatibility mode: see {@link ArithmeticExpression10}
 */

public class ArithmeticExpression extends BinaryExpression {

    private Calculator calculator;
    protected boolean simplified = false;

    /**
     * Create an arithmetic expression
     * @param p0 the first operand
     * @param operator the operator, for example {@link Token#PLUS}
     * @param p1 the second operand
     */

    public ArithmeticExpression(Expression p0, int operator, Expression p1) {
        super(p0, operator, p1);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "arithmetic";
    }

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        if (simplified) {
            // Don't simplify more than once; because in XSLT the static context on subsequent calls
            // might not know whether backwards compatibility is in force or not
            return this;
        }
        simplified = true;
        Expression e = super.simplify(visitor);
        if (e == this && visitor.getStaticContext().isInBackwardsCompatibleMode()) {
            return new ArithmeticExpression10(operand0, operator, operand1);
        } else {
            if (operator == Token.NEGATE && Literal.isAtomic(operand1)) {
                // very early evaluation of expressions like "-1", so they are treated as numeric literals
                AtomicValue val = (AtomicValue)((Literal)operand1).getValue();
                if (val instanceof NumericValue) {
                    return Literal.makeLiteral(((NumericValue)val).negate());
                }
            }
            return e;
        }
    }

    /**
     * Get the calculator allocated to evaluate this expression
     * @return the calculator, a helper object that does the actual calculation
     */

    public Calculator getCalculator() {
        return calculator;
    }

    /**
     * Type-check the expression statically. We try to work out which particular
     * arithmetic function to use if the types of operands are known an compile time.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        Expression oldOp0 = operand0;
        Expression oldOp1 = operand1;

        operand0 = visitor.typeCheck(operand0, contextItemType);
        operand1 = visitor.typeCheck(operand1, contextItemType);


        SequenceType atomicType = SequenceType.OPTIONAL_ATOMIC;

        RoleLocator role0 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 0);
        //role0.setSourceLocator(this);
        operand0 = TypeChecker.staticTypeCheck(operand0, atomicType, false, role0, visitor);
        final ItemType itemType0 = operand0.getItemType(th);
        if (itemType0 instanceof ErrorType) {
            return Literal.makeEmptySequence();
        }
        AtomicType type0 = (AtomicType) itemType0.getPrimitiveItemType();
        if (type0.getFingerprint() == StandardNames.XS_UNTYPED_ATOMIC) {
        	operand0 = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), operand0, BuiltInAtomicType.DOUBLE);
            type0 = BuiltInAtomicType.DOUBLE;
        } else if (/*!(operand0 instanceof UntypedAtomicConverter)*/
                (operand0.getSpecialProperties()&StaticProperty.NOT_UNTYPED_ATOMIC) == 0 &&
                th.relationship(type0, BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT) {
        	operand0 = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), operand0, BuiltInAtomicType.DOUBLE);
            type0 = (AtomicType)operand0.getItemType(th);
        }

        // System.err.println("First operand"); operand0.display(10);

        RoleLocator role1 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 1);
        //role1.setSourceLocator(this);
        operand1 = TypeChecker.staticTypeCheck(operand1, atomicType, false, role1, visitor);
        final ItemType itemType1 = operand1.getItemType(th);
        if (itemType1 instanceof ErrorType) {
            return Literal.makeEmptySequence();
        }
        AtomicType type1 = (AtomicType)itemType1.getPrimitiveItemType();
        if (type1.getFingerprint() == StandardNames.XS_UNTYPED_ATOMIC) {
        	operand1 = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), operand1, BuiltInAtomicType.DOUBLE);
            type1 = BuiltInAtomicType.DOUBLE;
        } else if (/*!(operand1 instanceof UntypedAtomicConverter) &&*/
                (operand1.getSpecialProperties()&StaticProperty.NOT_UNTYPED_ATOMIC) == 0 &&
                th.relationship(type1, BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT) {
        	operand1 = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), operand1, BuiltInAtomicType.DOUBLE);
            type1 = (AtomicType)operand1.getItemType(th);
        }

        if (operand0 != oldOp0) {
            adoptChildExpression(operand0);
        }

        if (operand1 != oldOp1) {
            adoptChildExpression(operand1);
        }

        if (Literal.isEmptySequence(operand0) ||
                Literal.isEmptySequence(operand1)) {
            return Literal.makeEmptySequence();
        }

        if (type0.isExternalType() || type1.isExternalType()) {
            XPathException de = new XPathException("Arithmetic operators are not defined for external objects");
            de.setLocator(this);
            de.setErrorCode("XPTY0004");
            throw de;
        }

        if (operator == Token.NEGATE) {
            if (operand1 instanceof Literal && ((Literal)operand1).getValue() instanceof NumericValue) {
                NumericValue nv = (NumericValue)((Literal)operand1).getValue();
                return Literal.makeLiteral(nv.negate());
            } else {
                NegateExpression ne = new NegateExpression(operand1);
                ne.setBackwardsCompatible(false);
                return visitor.typeCheck(ne, contextItemType);
            }
        }

        // Get a calculator to implement the arithmetic operation. If the types are not yet specifically known,
        // we allow this to return an "ANY" calculator which defers the decision. However, we only allow this if
        // at least one of the operand types is AnyAtomicType or (otherwise unspecified) numeric.

        boolean mustResolve = !(type0.equals(BuiltInAtomicType.ANY_ATOMIC) || type1.equals(BuiltInAtomicType.ANY_ATOMIC)
                || type0.equals(BuiltInAtomicType.NUMERIC) || type1.equals(BuiltInAtomicType.NUMERIC));

        calculator = Calculator.getCalculator(
                type0.getFingerprint(), type1.getFingerprint(), mapOpCode(operator), mustResolve);

        if (calculator == null) {
            XPathException de = new XPathException("Arithmetic operator is not defined for arguments of types (" +
                    type0.getDescription() + ", " + type1.getDescription() + ")");
            de.setLocator(this);
            de.setErrorCode("XPTY0004");
            throw de;
        }

        try {
            if ((operand0 instanceof Literal) && (operand1 instanceof Literal)) {
                return Literal.makeLiteral(SequenceTool.toGroundedValue(evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext())));
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time, or it might be due to context such as the implicit timezone
            // not being available yet
        }
        return this;
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
    /*@Nullable*/@Override
    public IntegerValue[] getIntegerBounds() {
        IntegerValue[] bounds0 = operand0.getIntegerBounds();
        IntegerValue[] bounds1 = operand1.getIntegerBounds();
        if (bounds0 == null || bounds1 == null) {
            return null;
        } else {
            switch (operator) {
                case Token.PLUS:
                    return new IntegerValue[]{bounds0[0].plus(bounds1[0]), bounds0[1].plus(bounds1[1])};
                case Token.MINUS:
                    return new IntegerValue[]{bounds0[0].minus(bounds1[1]), bounds0[1].minus(bounds1[0])};
                case Token.MULT:
                    if (operand1 instanceof Literal) {
                        IntegerValue val1 = bounds1[0];
                        if (val1.signum() > 0) {
                            return new IntegerValue[]{bounds0[0].times(val1), bounds0[1].times(val1)};
                        } else {
                            return null;
                        }
                    } else if (operand0 instanceof Literal) {
                        IntegerValue val0 = bounds1[0];
                        if (val0.signum() > 0) {
                            return new IntegerValue[]{bounds1[0].times(val0), bounds1[1].times(val0)};
                        } else {
                            return null;
                        }
                    }
                case Token.DIV:
                case Token.IDIV:
                    if (operand1 instanceof Literal) {
                        IntegerValue val1 = bounds1[0];
                        if (val1.signum() > 0) {
                            try {
                                return new IntegerValue[]{bounds0[0].idiv(val1), bounds0[1].idiv(val1)};
                            } catch (XPathException e) {
                                return null;
                            }
                        }
                    }
                    return null;
                default:
                    return null;
            }
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ArithmeticExpression ae = new ArithmeticExpression(operand0.copy(), operator, operand1.copy());
        ae.calculator = calculator;
        ae.simplified = simplified;
        return ae;
    }

    /**
     * Static method to apply arithmetic to two values
     * @param value0 the first value
     * @param operator the operator as denoted in the Calculator class, for example {@link Calculator#PLUS}
     * @param value1 the second value
     * @param context the XPath dynamic evaluation context
     * @return the result of the arithmetic operation
     */

    public static AtomicValue compute(AtomicValue value0, int operator, AtomicValue value1, XPathContext context)
            throws XPathException {
        int p0 = value0.getPrimitiveType().getFingerprint();
        int p1 = value1.getPrimitiveType().getFingerprint();
        Calculator calculator = Calculator.getCalculator(p0, p1, operator, false);
        return calculator.compute(value0, value1, context);
    }

    /**
     * Map operator codes from those in the Token class to those in the Calculator class
     * @param op an operator denoted by a constant in the {@link Token} class, for example {@link Token#PLUS}
     * @return an operator denoted by a constant defined in the {@link Calculator} class, for example
     * {@link Calculator#PLUS}
     */

    public static int mapOpCode(int op) {
        switch (op) {
            case Token.PLUS:
                return Calculator.PLUS;
            case Token.MINUS:
            case Token.NEGATE:
                return Calculator.MINUS;
            case Token.MULT:
                return Calculator.TIMES;
            case Token.DIV:
                return Calculator.DIV;
            case Token.IDIV:
                return Calculator.IDIV;
            case Token.MOD:
                return Calculator.MOD;
            default:
                throw new IllegalArgumentException();
        }

    }

    /**
     * Determine the data type of the expression, insofar as this is known statically
     * @param th the type hierarchy cache
     * @return the atomic type of the result of this arithmetic expression
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (calculator == null) {
            return BuiltInAtomicType.ANY_ATOMIC;  // type is not known statically
        } else {
            ItemType t1 = operand0.getItemType(th);
            if (!(t1 instanceof AtomicType)) {
                t1 = t1.getAtomizedItemType();
            }
            ItemType t2 = operand1.getItemType(th);
            if (!(t2 instanceof AtomicType)) {
                t2 = t2.getAtomizedItemType();
            }
            ItemType resultType = calculator.getResultType((AtomicType) t1.getPrimitiveItemType(),
                    (AtomicType) t2.getPrimitiveItemType());

            if (resultType.equals(BuiltInAtomicType.ANY_ATOMIC)) {
                // there are a few special cases where we can do better. For example, given X+1, where the type of X
                // is unknown, we can still infer that the result is numeric. (Not so for X*2, however, where it could
                // be a duration)
                if ((operator == Token.PLUS || operator == Token.MINUS) &&
                        (th.isSubType(t2, BuiltInAtomicType.NUMERIC) || th.isSubType(t1, BuiltInAtomicType.NUMERIC))) {
                    resultType = BuiltInAtomicType.NUMERIC;
                }
            }
            return resultType;
        }
    }

    /**
     * Evaluate the expression.
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {

        AtomicValue v0 = (AtomicValue) operand0.evaluateItem(context);
        if (v0 == null) {
            return null;
        }

        AtomicValue v1 = (AtomicValue) operand1.evaluateItem(context);
        if (v1 == null) {
            return null;
        }

        try {
            return calculator.compute(v0, v1, context);
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }
    }

   


}

