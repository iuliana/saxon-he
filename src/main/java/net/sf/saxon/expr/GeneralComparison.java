////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.Minimax;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GeneralComparison: a boolean expression that compares two expressions
 * for equals, not-equals, greater-than or less-than. This implements the operators
 * =, !=, <, >, etc. This implementation is not used when in backwards-compatible mode
 */

public class GeneralComparison extends BinaryExpression implements ComparisonExpression, Callable {

    public static final int ONE_TO_ONE = 0;
    public static final int MANY_TO_ONE = 1;
    public static final int MANY_TO_MANY = 2;
    // Note, a one-to-many comparison is inverted into a many-to-one comparison
    
    protected int singletonOperator;
    protected AtomicComparer comparer;
    protected boolean needsRuntimeCheck = true;
    protected int comparisonCardinality = MANY_TO_MANY;

    /**
     * Create a relational expression identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p1 the right-hand operand
     */
    public GeneralComparison(Expression p0, int op, Expression p1) {
        super(p0, op, p1);
        singletonOperator = getCorrespondingSingletonOperator(op);
    }

    /**
     * Ask whether a runtime check of the types of the operands is needed
     */
    public boolean needsRuntimeCheck() {
        return needsRuntimeCheck;
    }

    /**
     * Say whether a runtime check of the types of the operands is needed
     */
    public void setNeedsRuntimeCheck(boolean needsCheck) {
        needsRuntimeCheck = needsCheck;
    }

    /**
     * Ask whether the comparison is known to be many-to-one, one-to-one, or many-to-many.
     * (Note, an expression that is one-to-many will be converted to one that is many-to-one).
     */
    public int getComparisonCardinality() {
        return comparisonCardinality;
    }

    /**
     * Say whether the comparison is known to be many-to-one, one-to-one, or many-to-many.
     */
    public void setComparisonCardinality(int card) {
        comparisonCardinality = card;
    }

    /**
     * Set the comparer to be used
     *
     * @param comparer the comparer to be used
     */
    public void setAtomicComparer(AtomicComparer comparer) {
        this.comparer = comparer;
    }

    @Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        Expression e = super.simplify(visitor);
        if (e != this) {
            ExpressionTool.copyLocationInfo(this, e);
            return e;
        } else if (visitor.getStaticContext().isInBackwardsCompatibleMode()) {
            Expression[] operands = getOperands();
            GeneralComparison10 gc10 = new GeneralComparison10(operands[0], getOperator(), operands[1]);
            gc10.setAtomicComparer(getAtomicComparer());
            return gc10;
        } else {
            Expression[] operands = getOperands();
            GeneralComparison20 gc20 = new GeneralComparison20(operands[0], getOperator(), operands[1]);
            gc20.setAtomicComparer(getAtomicComparer());
            return gc20;
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    public String getExpressionName() {
        return "GeneralComparison";
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
     */
    public AtomicComparer getAtomicComparer() {
        return comparer;
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */
    public int getSingletonOperator() {
        return singletonOperator;
    }

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     *         should be converted to strings.
     */
    public boolean convertsUntypedToOther() {
        return true;
    }

    /**
     * Determine the static cardinality. Returns [1..1]
     */
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Type-check the expression
     *
     * @return the checked expression
     */
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        Expression oldOp0 = operand0;
        Expression oldOp1 = operand1;

        operand0 = visitor.typeCheck(operand0, contextItemType);
        operand1 = visitor.typeCheck(operand1, contextItemType);

        // If either operand is statically empty, return false

        if (Literal.isEmptySequence(operand0) || Literal.isEmptySequence(operand1)) {
            return Literal.makeLiteral(BooleanValue.FALSE);
        }

        // Neither operand needs to be sorted

        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        operand0 = ExpressionTool.unsorted(opt, operand0, false);
        operand1 = ExpressionTool.unsorted(opt, operand1, false);

        SequenceType atomicType = SequenceType.ATOMIC_SEQUENCE;

        RoleLocator role0 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 0);
        //role0.setSourceLocator(this);
        operand0 = TypeChecker.staticTypeCheck(operand0, atomicType, false, role0, visitor);

        RoleLocator role1 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 1);
        //role1.setSourceLocator(this);
        operand1 = TypeChecker.staticTypeCheck(operand1, atomicType, false, role1, visitor);

        if (operand0 != oldOp0) {
            adoptChildExpression(operand0);
        }

        if (operand1 != oldOp1) {
            adoptChildExpression(operand1);
        }

        ItemType t0 = operand0.getItemType(th);  // this is always an atomic type or empty-sequence()
        ItemType t1 = operand1.getItemType(th);  // this is always an atomic type or empty-sequence()

        if (t0 instanceof ErrorType || t1 instanceof ErrorType) {
            return Literal.makeLiteral(BooleanValue.FALSE);
        }

        if (((AtomicType) t0).isExternalType() || ((AtomicType) t1).isExternalType()) {
            XPathException err = new XPathException("Cannot perform comparisons involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocator(this);
            throw err;
        }

        BuiltInAtomicType pt0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        BuiltInAtomicType pt1 = (BuiltInAtomicType) t1.getPrimitiveItemType();

        int c0 = operand0.getCardinality();
        int c1 = operand1.getCardinality();

        if (c0 == StaticProperty.EMPTY || c1 == StaticProperty.EMPTY) {
            return Literal.makeLiteral(BooleanValue.FALSE);
        }

        if (t0.equals(BuiltInAtomicType.ANY_ATOMIC) || t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC) ||
                t1.equals(BuiltInAtomicType.ANY_ATOMIC) || t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            // then no static type checking is possible
		} else if (!Type.isPossiblyComparable(pt0, pt1, Token.isOrderedOperator(singletonOperator))) {
			XPathException err = new XPathException("Cannot compare " + t0.toString() + " to " + t1.toString());
			err.setErrorCode("XPTY0004");
			err.setIsTypeError(true);
			err.setLocator(this);
			throw err;
		}

        needsRuntimeCheck = !Type.isGuaranteedGenerallyComparable(pt0, pt1, Token.isOrderedOperator(singletonOperator));

		if (c0 == StaticProperty.EXACTLY_ONE && c1 == StaticProperty.EXACTLY_ONE &&
			!t0.equals(BuiltInAtomicType.ANY_ATOMIC) &&
			!t1.equals(BuiltInAtomicType.ANY_ATOMIC)) {

            // Use a value comparison if both arguments are singletons, and if the comparison operator to
            // be used can be determined.

            Expression e0 = operand0;
            Expression e1 = operand1;

            if (t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                    e0 = new CastExpression(operand0, BuiltInAtomicType.STRING, false);
                    adoptChildExpression(e0);
                    e1 = new CastExpression(operand1, BuiltInAtomicType.STRING, false);
                    adoptChildExpression(e1);
                } else if (th.isSubType(t1, BuiltInAtomicType.NUMERIC)) {
                    e0 = new CastExpression(operand0, BuiltInAtomicType.DOUBLE, false);
                    adoptChildExpression(e0);
                } else {
                    e0 = new CastExpression(operand0, pt1, false);
                    adoptChildExpression(e0);
                }
            } else if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                if (th.isSubType(t0, BuiltInAtomicType.NUMERIC)) {
                    e1 = new CastExpression(operand1, BuiltInAtomicType.DOUBLE, false);
                    adoptChildExpression(e1);
                } else {
                    e1 = new CastExpression(operand1, pt0, false);
                    adoptChildExpression(e1);
                }
            }

            ValueComparison vc = new ValueComparison(e0, singletonOperator, e1);
            vc.setAtomicComparer(comparer);
            ExpressionTool.copyLocationInfo(this, vc);
            return visitor.typeCheck(visitor.simplify(vc), contextItemType);
        }

        StaticContext env = visitor.getStaticContext();
        if (comparer == null) {
            // In XSLT, only do this the first time through, otherwise default-collation may be missed
            final String defaultCollationName = env.getDefaultCollationName();
            StringCollator collation = env.getCollation(defaultCollationName);
            if (collation == null) {
                collation = CodepointCollator.getInstance();
            }
            comparer = GenericAtomicComparer.makeAtomicComparer(
                    pt0, pt1, collation, visitor.getConfiguration().getConversionContext());
        }


        // evaluate the expression now if both arguments are constant

        if ((operand0 instanceof Literal) && (operand1 instanceof Literal)) {
            return Literal.makeLiteral((AtomicValue) evaluateItem(env.makeEarlyEvaluationContext()));
        }
        return this;
    }

    private static Expression makeMinOrMax(Expression exp, String function) {
        FunctionCall fn = SystemFunctionCall.makeSystemFunction(function, new Expression[]{exp});
        ((Minimax) fn).setIgnoreNaN(true);
        return fn;
    }

    /**
     * Optimize the expression
     *
     * @return the checked expression
     */
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        final StaticContext env = visitor.getStaticContext();
        final Optimizer opt = visitor.getConfiguration().obtainOptimizer();

        operand0 = visitor.optimize(operand0, contextItemType);
        operand1 = visitor.optimize(operand1, contextItemType);

        // If either operand is statically empty, return false

        if (Literal.isEmptySequence(operand0) || Literal.isEmptySequence(operand1)) {
            return Literal.makeLiteral(BooleanValue.FALSE);
        }

        // Neither operand needs to be sorted

        operand0 = ExpressionTool.unsorted(opt, operand0, false);
        operand1 = ExpressionTool.unsorted(opt, operand1, false);

        if (operand0 instanceof Literal && operand1 instanceof Literal) {
            return Literal.makeLiteral(
                    SequenceTool.toGroundedValue(evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext())));
        }

        ItemType t0 = operand0.getItemType(th);
        ItemType t1 = operand1.getItemType(th);

        int c0 = operand0.getCardinality();
        int c1 = operand1.getCardinality();

        // Check if neither argument allows a sequence of >1

        boolean many0 = Cardinality.allowsMany(c0);
        boolean many1 = Cardinality.allowsMany(c1);

        if (many0) {
            if (many1) {
                comparisonCardinality = MANY_TO_MANY;
            } else {
                comparisonCardinality = MANY_TO_ONE;
            }
        } else {
            if (many1) {
                GeneralComparison mc = getInverseComparison();
                mc.comparisonCardinality = MANY_TO_ONE;
                ExpressionTool.copyLocationInfo(this, mc);
                mc.comparer = comparer;
                mc.needsRuntimeCheck = needsRuntimeCheck;
                return visitor.optimize(mc, contextItemType);
            } else {
                comparisonCardinality = ONE_TO_ONE;
            }
        }

        if (comparer == null) {
            final String defaultCollationName = env.getDefaultCollationName();
            StringCollator comp = env.getCollation(defaultCollationName);
            if (comp == null) {
                comp = CodepointCollator.getInstance();
            }
            BuiltInAtomicType pt0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
            BuiltInAtomicType pt1 = (BuiltInAtomicType) t1.getPrimitiveItemType();
            comparer = GenericAtomicComparer.makeAtomicComparer(pt0, pt1, comp,
                    env.getConfiguration().getConversionContext());
        }

        // If one operand is numeric, then construct code
        // to force the other operand to numeric

        // TODO: shouldn't this happen during type checking?

        Expression e0 = operand0;
        Expression e1 = operand1;

        boolean numeric0 = th.isSubType(t0, BuiltInAtomicType.NUMERIC);
        boolean numeric1 = th.isSubType(t1, BuiltInAtomicType.NUMERIC);
        if (numeric1 && !numeric0) {
            RoleLocator role = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 0);
            //role.setSourceLocator(this);
            e0 = TypeChecker.staticTypeCheck(e0, SequenceType.NUMERIC_SEQUENCE, false, role, visitor);
        }

        if (numeric0 && !numeric1) {
            RoleLocator role = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 1);
            //role.setSourceLocator(this);
            e1 = TypeChecker.staticTypeCheck(e1, SequenceType.NUMERIC_SEQUENCE, false, role, visitor);
        }


        // look for (N to M = I)
        // First a variable range...

		if (e0 instanceof RangeExpression && operator == Token.EQUALS &&
			th.isSubType(operand1.getItemType(th), BuiltInAtomicType.NUMERIC) &&
			!Cardinality.allowsMany(e1.getCardinality())) {
			
            Expression min = ((RangeExpression) e0).operand0;
            Expression max = ((RangeExpression) e0).operand1;
            IntegerRangeTest ir = new IntegerRangeTest(e1, min, max);
            ExpressionTool.copyLocationInfo(this, ir);
            return ir;
        }

        // Now a fixed range...

        if (e0 instanceof Literal) {
            GroundedValue value0 = ((Literal) e0).getValue();
			if (value0 instanceof IntegerRange && operator == Token.EQUALS &&
				th.isSubType(e1.getItemType(th), BuiltInAtomicType.NUMERIC) &&
				!Cardinality.allowsMany(e1.getCardinality())) {
				
                long min = ((IntegerRange) value0).getStart();
                long max = ((IntegerRange) value0).getEnd();
                IntegerRangeTest ir = new IntegerRangeTest(e1,
                        Literal.makeLiteral(Int64Value.makeIntegerValue(min)),
                        Literal.makeLiteral(Int64Value.makeIntegerValue(max)));
                ExpressionTool.copyLocationInfo(this, ir);
                return ir;
            }
        }

        // If the operator is gt, ge, lt, le then replace X < Y by min(X) < max(Y)

        // This optimization is done only in the case where at least one of the
        // sequences is known to be purely numeric. It isn't safe if both sequences
        // contain untyped atomic values, because in that case, the type of the
        // comparison isn't known in advance. For example [(1, U1) < ("fred", U2)]
        // involves both string and numeric comparisons.

        if (operator != Token.EQUALS && operator != Token.NE &&
                (th.isSubType(t0, BuiltInAtomicType.NUMERIC) || th.isSubType(t1, BuiltInAtomicType.NUMERIC))) {

            // System.err.println("** using minimax optimization **");
            ValueComparison vc;
            switch (operator) {
                case Token.LT:
                case Token.LE:
                    vc = new ValueComparison(makeMinOrMax(e0, "min"), singletonOperator, makeMinOrMax(e1, "max"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    vc.setAtomicComparer(comparer);
                    break;
                case Token.GT:
                case Token.GE:
                    vc = new ValueComparison(makeMinOrMax(e0, "max"), singletonOperator, makeMinOrMax(e1, "min"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    vc.setAtomicComparer(comparer);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown operator " + operator);
            }

            ExpressionTool.copyLocationInfo(this, vc);
            return visitor.typeCheck(vc, contextItemType);
        }


        // evaluate the expression now if both arguments are constant

        operand0 = e0;
        operand1 = e1;
        if ((operand0 instanceof Literal) && (operand1 instanceof Literal)) {
            return Literal.makeLiteral(evaluateItem(env.makeEarlyEvaluationContext()));
        }

        // Finally, convert to use the GeneralComparisonEE algorithm if in Saxon-EE
        return visitor.getConfiguration().obtainOptimizer().optimizeGeneralComparison(this, false);
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/ public Expression copy() {
        GeneralComparison gc = new GeneralComparison(operand0.copy(), operator, operand1.copy());
        gc.comparer = comparer;
        gc.singletonOperator = singletonOperator;
        gc.needsRuntimeCheck = needsRuntimeCheck;
        gc.comparisonCardinality = comparisonCardinality;
        return gc;
    }

    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands
     */

    /*@Nullable*/ public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        switch (comparisonCardinality) {
            case ONE_TO_ONE: {
                AtomicValue value0 = (AtomicValue) operand0.evaluateItem(context);
                AtomicValue value1 = (AtomicValue) operand1.evaluateItem(context);
                return BooleanValue.get(evaluateOneToOne(value0, value1, context));
            }
            case MANY_TO_ONE: {
                SequenceIterator iter0 = operand0.iterate(context);
                AtomicValue value1 = (AtomicValue) operand1.evaluateItem(context);
                return BooleanValue.get(evaluateManyToOne(iter0, value1, context));
            }
            case MANY_TO_MANY: {
                SequenceIterator iter1 = operand0.iterate(context);
                SequenceIterator iter2 = operand1.iterate(context);
                return BooleanValue.get(evaluateManyToMany(iter1, iter2, context));
            }
        }
        return null;
    }

    /**
     * Evaluate the expression
     *
     *
     *
 * @param context   the dynamic evaluation context
 * @param arguments the values of the arguments, supplied as SequenceIterators
 * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        switch (comparisonCardinality) {
            case ONE_TO_ONE: {
                AtomicValue value0 = (AtomicValue) arguments[0].head();
                AtomicValue value1 = (AtomicValue) arguments[1].head();
                return BooleanValue.get(evaluateOneToOne(value0, value1, context));
            }
            case MANY_TO_ONE: {
                SequenceIterator iter0 = arguments[0].iterate();
                AtomicValue value1 = (AtomicValue) arguments[1].head();
                return BooleanValue.get(evaluateManyToOne(iter0, value1, context));
            }
            case MANY_TO_MANY: {
                SequenceIterator iter1 = arguments[0].iterate();
                SequenceIterator iter2 = arguments[1].iterate();
                return BooleanValue.get(evaluateManyToMany(iter1, iter2, context));
            }
        }
        return null;
    }

    /**
     * Evaluate the expression in a boolean context
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the numeric comparison of the two operands
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        switch (comparisonCardinality) {
            case ONE_TO_ONE: {
                AtomicValue value0 = (AtomicValue) operand0.evaluateItem(context);
                AtomicValue value1 = (AtomicValue) operand1.evaluateItem(context);
                return evaluateOneToOne(value0, value1, context);
            }
            case MANY_TO_ONE: {
                SequenceIterator iter0 = operand0.iterate(context);
                AtomicValue value1 = (AtomicValue) operand1.evaluateItem(context);
                return evaluateManyToOne(iter0, value1, context);
            }
            case MANY_TO_MANY: {
                SequenceIterator iter1 = operand0.iterate(context);
                SequenceIterator iter2 = operand1.iterate(context);
                return evaluateManyToMany(iter1, iter2, context);
            }
        }
        return false;
    }

    /**
     * Evaluate a (zero-or-one)-to-(zero-or-one) comparison
     * @param value0  the first value, or null if empty
     * @param value1  the second value, or null if empty
     * @param context  dynamic evaluation context
     * @return  the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    private boolean evaluateOneToOne(AtomicValue value0, AtomicValue value1, XPathContext context) throws XPathException {
        try {
            return !(value0 == null || value1 == null) &&
                    compare(value0, singletonOperator, value1, comparer, needsRuntimeCheck, context);
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }

    }

    /**
     * Evaluate a (zero-to-many)-to-(zero-or-one) comparison
     * @param iter0  iterator over the first value
     * @param value1  the second value, or null if empty
     * @param context  dynamic evaluation context
     * @return  the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    private boolean evaluateManyToOne(SequenceIterator iter0, AtomicValue value1, XPathContext context) throws XPathException {
        try {
            if (value1 == null) {
                return false;
            }

            if (iter0 instanceof SingletonIterator) {
                AtomicValue v = (AtomicValue) iter0.next();
                if (v == null)
                    return false;
                boolean result = compare(v, singletonOperator, value1, comparer, needsRuntimeCheck, context);
                iter0.close();
                return result;
            } else {
                AtomicValue item0;
                //boolean firstCheck = false;
                while ((item0 = (AtomicValue) iter0.next()) != null) {
                   /* if (!firstCheck &&
                            BuiltInAtomicType.BOOLEAN.equals(value1.getPrimitiveType())) {
                        if (singletonOperator == Token.FEQ) {
                            return value1.effectiveBooleanValue();
                        } else if (singletonOperator == Token.FNE) {
                            return !value1.effectiveBooleanValue();
                        }
                    }
                    firstCheck = true;*/
                    if (compare(item0, singletonOperator, value1, comparer, needsRuntimeCheck, context)) {
                        iter0.close();
                        return true;
                    }
                }
               /* if (item0 == null && BuiltInAtomicType.BOOLEAN.equals(value1.getPrimitiveType())) {
                    if (singletonOperator == Token.FEQ) {
                        return !value1.effectiveBooleanValue();
                    } else if (singletonOperator == Token.FNE) {
                        return value1.effectiveBooleanValue();
                    }
                }*/
            }
            return false;
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Evaluate a (zero-or-one)-to-(zero-or-one) comparison
     * @param iter0  iterator over the first value
     * @param iter1  iterator the second value
     * @param context  dynamic evaluation context
     * @return  the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    public boolean evaluateManyToMany(SequenceIterator iter0, SequenceIterator iter1, XPathContext context) throws XPathException {
        try {
            boolean exhausted0 = false;
            boolean exhausted1 = false;

            List<AtomicValue> value0 = new ArrayList<>();
            List<AtomicValue> value1 = new ArrayList<>();

            // Read items from the two sequences alternately, in each case comparing the item to
            // all items that have previously been read from the other sequence. In the worst case
            // the number of comparisons is N*M, and the memory usage is (max(N,M)*2) where N and M
            // are the number of items in the two sequences. In practice, either M or N is often 1,
            // meaning that in this case neither list will ever hold more than one item.

            while (true) {
                if (!exhausted0) {
                    AtomicValue item0 = (AtomicValue) iter0.next();
                    if (item0 == null) {
                        if (exhausted1) {
                            return false;
                        }
                        exhausted0 = true;
                    } else {
                        for (AtomicValue item1 : value1) {
                            if (compare(item0, singletonOperator, item1, comparer, needsRuntimeCheck, context)) {
                                iter0.close();
                                iter1.close();
                                return true;
                            }
                        }
                        if (!exhausted1) {
                            value0.add(item0);
                        }
                    }
                }
                if (!exhausted1) {
                    AtomicValue item1 = (AtomicValue) iter1.next();
                    if (item1 == null) {
                        if (exhausted0) {
                            return false;
                        }
                        exhausted1 = true;
                    } else {
                        for (AtomicValue item0 : value0) {
                            if (compare(item0, singletonOperator, item1, comparer, needsRuntimeCheck, context)) {
                                iter0.close();
                                iter1.close();
                                return true;
                            }
                        }
                        if (!exhausted0) {
                            value1.add(item1);
                        }
                    }
                }
            }
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }

    }

    /**
     * Compare two atomic values
     *
     * @param a0         the first value
     * @param operator   the operator, for example {@link net.sf.saxon.expr.parser.Token#EQUALS}
     * @param a1         the second value
     * @param comparer   the comparer to be used to perform the comparison
     * @param checkTypes set to true if the operand types need to be checked for comparability at runtime
     * @param context    the XPath evaluation context @return true if the comparison succeeds
     */

    public static boolean compare(AtomicValue a0,
                                  int operator,
                                  AtomicValue a1,
                                  AtomicComparer comparer,
                                  boolean checkTypes,
                                  XPathContext context) throws XPathException {

        final ConversionRules rules = context.getConfiguration().getConversionRules();

        try {
            if ((a0 instanceof NumericValue) && (a1 instanceof BooleanValue)) {
                a0 = Converter.convert(a0, BuiltInAtomicType.BOOLEAN, rules).asAtomic();
                checkTypes = false;
            } else if ((a1 instanceof NumericValue) && (a0 instanceof BooleanValue)) {
                a1 = Converter.convert(a1, BuiltInAtomicType.BOOLEAN, rules).asAtomic();
                checkTypes = false;
            }

            boolean u0 = (a0 instanceof UntypedAtomicValue);
            boolean u1 = (a1 instanceof UntypedAtomicValue);
            if (u0 != u1) {
                // one value untyped, the other not
                if (u0) {
                    // a1 is untyped atomic
                    if (a1 instanceof NumericValue) {
                        a0 = Converter.convert(a0, BuiltInAtomicType.DOUBLE, rules).asAtomic();
                    } else {
                        a0 = Converter.convert(a0, a1.getPrimitiveType(), rules).asAtomic();
                    }
                } else {
                    // a2 is untyped atomic
                    if (a0 instanceof NumericValue) {
                        a1 = Converter.convert(a1, BuiltInAtomicType.DOUBLE, rules).asAtomic();
                    } else {
                        a1 = Converter.convert(a1, a0.getPrimitiveType(), rules).asAtomic();
                    }
                }
                checkTypes = false; // No further checking needed if conversion succeeded
            }


        } catch (ValidationException e) {
            if (operator == Token.FEQ)
                return false;
            else if (operator == Token.FNE)
                return true;
        }
        return ValueComparison.compare(a0, operator, a1, comparer.provideContext(context), checkTypes);
    }

    /**
     * Determine the data type of the expression
     *
     * @param th the type hierarchy cache
     * @return the value BuiltInAtomicType.BOOLEAN
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Return the singleton form of the comparison operator, e.g. FEQ for EQUALS, FGT for GT
     *
     * @param op the many-to-many form of the operator, for example {@link Token#LE}
     * @return the corresponding singleton operator, for example {@link Token#FLE}
     */

    public static int getCorrespondingSingletonOperator(int op) {
        switch (op) {
            case Token.EQUALS:
                return Token.FEQ;
            case Token.GE:
                return Token.FGE;
            case Token.NE:
                return Token.FNE;
            case Token.LT:
                return Token.FLT;
            case Token.GT:
                return Token.FGT;
            case Token.LE:
                return Token.FLE;
            default:
                return op;
        }
    }

    protected GeneralComparison getInverseComparison() {
        return new GeneralComparison(operand1, Token.inverse(operator), operand0);
    }



//    protected String displayOperator() {
//        return "many-to-many " + super.displayOperator();
//    }

    protected void explainExtraAttributes(ExpressionPresenter out) {
        String cc = "";
        switch (comparisonCardinality) {
            case ONE_TO_ONE:
                cc = "one-to-one";
                break;
            case MANY_TO_ONE:
                cc = "many-to-one";
                break;
            case MANY_TO_MANY:
                cc = "many-to-many";
                break;
        }
        out.emitAttribute("cardinality", cc);
    }

}

