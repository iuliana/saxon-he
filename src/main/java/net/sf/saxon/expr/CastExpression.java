////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;



import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.functions.StringFn;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import static net.sf.saxon.expr.ValueComparison.VC_EX_CODE;
import static net.sf.saxon.util.ExceptionBuilder.newEx;

/**
 * Cast Expression: implements "cast as data-type ( expression )". It also allows an internal
 * cast, which has the same semantics as a user-requested cast, but maps an empty sequence to
 * an empty sequence.
 *
 * This expression class does not handle casting to a union type.
*/

public class CastExpression extends CastingExpression  {

    public static final String CAST_EX_CODE = "FORG0001";

    /**
     * Create a cast expression
     * @param source expression giving the value to be converted
     * @param target the type to which the value is to be converted
     * @param allowEmpty true if the expression allows an empty sequence as input, producing
     * an empty sequence as output. If false, an empty sequence is a type error.
     */

    public CastExpression(Expression source, AtomicType target, boolean allowEmpty) {
        super(source, target, allowEmpty);
    }

    /**
    * Type-check the expression
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        SequenceType atomicType = SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, getCardinality());

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "cast as", 0);
        ItemType sourceItemType = null;

//        if (getTargetType().isNamespaceSensitive()) {
//            int rel = th.relationship(operand.getItemType(th), AnyNodeTest.getInstance());
//            if (rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMED_BY) {
//                // See spec bug 11964
//                XPathException err = new XPathException("Atomization is not allowed when casting to QName or NOTATION");
//                err.setErrorCode("XPTY0004");
//                err.setLocator(this);
//                err.setIsTypeError(true);
//                throw err;
//            } else if (rel != TypeHierarchy.DISJOINT) {
//                operand = new ItemChecker(operand, BuiltInAtomicType.ANY_ATOMIC, role);
//            }
//            sourceItemType = BuiltInAtomicType.ANY_ATOMIC;
//        } else {
            operand = TypeChecker.staticTypeCheck(operand, atomicType, false, role, visitor);
            sourceItemType = operand.getItemType(th);
//        }

        if (sourceItemType instanceof ErrorType) {
            if (allowsEmpty()) {
                return Literal.makeEmptySequence();
            } else {
                throw newEx("Cast does not allow an empty sequence as input", true, VC_EX_CODE, this);
            }
        }
        AtomicType sourceType = (AtomicType) sourceItemType;
        int r = th.relationship(sourceType, getTargetType());
        if (r == TypeHierarchy.SAME_TYPE) {
            return operand;
        } else if (r == TypeHierarchy.SUBSUMED_BY) {
            // It's generally true that any expression defined to return an X is allowed to return a subtype of X.
            // However, people seem to get upset if we treat the cast as a no-op.
            converter = new Converter.UpCastingConverter(getTargetType());
        } else {

            ConversionRules rules = visitor.getConfiguration().getConversionRules();

            if (!(sourceType == BuiltInAtomicType.ANY_ATOMIC || sourceType.getFingerprint() == Type.EMPTY)) {
                converter = rules.getConverter(sourceType, getTargetType());
                if (converter == null) {
                    throw newEx("Casting from " + sourceType + " to " + getTargetType() +
                            " can never succeed", true, VC_EX_CODE, this);
                } else {
                    if (getTargetType().isNamespaceSensitive()) {
                        converter.setNamespaceResolver(nsResolver);
                    }
                }
                if (converter.isXPath30Conversion() && !visitor.getStaticContext().getXPathLanguageLevel().equals(DecimalValue.THREE)
                        && !(operand instanceof Literal && getTargetType().getPrimitiveType() == StandardNames.XS_QNAME)) {
                    throw newEx("Casting from " + sourceType + " to " + getTargetType() +
                            " requires XPath 3.0 functionality to be enabled", true, VC_EX_CODE, this);
                }
            }
        }
        
        if (operand instanceof Literal) {
            return preEvaluate();
        }

        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression e2 = super.optimize(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        // Eliminate pointless casting between untypedAtomic and string
        if (getTargetType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
            if (operand instanceof StringFn) {
                Expression e = ((StringFn)operand).getArguments()[0];
                if (e.getItemType(th) instanceof AtomicType && e.getCardinality() == StaticProperty.EXACTLY_ONE) {
                    operand = e;
                }
            } else if (operand instanceof CastExpression) {
                if (((CastExpression)operand).getTargetType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return operand;
                } else if (((CastExpression)operand).getTargetType() == BuiltInAtomicType.STRING) {
                    ((CastExpression)operand).setTargetType(BuiltInAtomicType.UNTYPED_ATOMIC);
                    return operand;
                }
            } else if (operand instanceof AtomicSequenceConverter) {
                if (operand.getItemType(th) == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return operand;
                } else if (operand.getItemType(th) == BuiltInAtomicType.STRING) {
                    AtomicSequenceConverter old = (AtomicSequenceConverter)operand;
                    AtomicSequenceConverter asc = new AtomicSequenceConverter(
                            old.getBaseExpression(),
                            BuiltInAtomicType.UNTYPED_ATOMIC
                    );
                    return asc.typeCheck(visitor, contextItemType)
                            .optimize(visitor, contextItemType);
                }
            }
        }
        // avoid converting anything to a string and then back again
        if (operand instanceof StringFn) {
            Expression e = ((StringFn)operand).getArguments()[0];
            ItemType et = e.getItemType(th);
            if (et instanceof AtomicType &&
                    e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                    th.isSubType(et, getTargetType())) {
                return e;
            }
        }
        // avoid converting anything to untypedAtomic and then back again
        if (operand instanceof CastExpression) {
            ItemType it = ((CastExpression)operand).getTargetType();
            if (th.isSubType(it, BuiltInAtomicType.STRING) || th.isSubType(it, BuiltInAtomicType.UNTYPED_ATOMIC)) {
                Expression e = ((CastExpression)operand).getBaseExpression();
                ItemType et = e.getItemType(th);
                if (et instanceof AtomicType &&
                        e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                        th.isSubType(et, getTargetType())) {
                    return e;
                }
            }
        }
        if (operand instanceof AtomicSequenceConverter) {
            ItemType it = operand.getItemType(th);
            if (th.isSubType(it, BuiltInAtomicType.STRING) || th.isSubType(it, BuiltInAtomicType.UNTYPED_ATOMIC)) {
                Expression e = ((AtomicSequenceConverter)operand).getBaseExpression();
                ItemType et = e.getItemType(th);
                if (et instanceof AtomicType &&
                        e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                        th.isSubType(et, getTargetType())) {
                    return e;
                }
            }
        }
        // if the operand can't be empty, then set allowEmpty to false to provide more information for analysis
        if (!Cardinality.allowsZero(operand.getCardinality())) {
            setAllowEmpty(false);
            resetLocalStaticProperties();
        }

        if (operand instanceof Literal) {
            return preEvaluate();
        }
        return this;
    }

    /**
     * Perform early (compile-time) evaluation
     */

    protected Expression preEvaluate() throws XPathException {
        GroundedValue literalOperand = ((Literal)operand).getValue();
        if (literalOperand instanceof AtomicValue && converter != null) {
            if (converter.isXPath30Conversion() && !getExecutable().isAllowXPath30() &&
                    !(converter instanceof StringConverter.StringToQName && isOperandIsStringLiteral())) {
                throw newEx("Casting from " + ((AtomicValue)literalOperand).getPrimitiveType() + " to "
                        + getTargetType() + " requires XPath 3.0 to be enabled", true, VC_EX_CODE, this);
            }
            ConversionResult result = converter.convert((AtomicValue)literalOperand);
            if (result instanceof ValidationFailure) {
                ValidationFailure err = (ValidationFailure)result;
                String code = err.getErrorCode();
                if (code == null) {
                    code = CAST_EX_CODE;
                }
                throw newEx(err.getMessage(), false, code, this);
            } else {
                return Literal.makeLiteral((AtomicValue)result);
            }
        }
        if (literalOperand instanceof EmptySequence) {
            if (allowsEmpty()) {
                return operand;
            } else {
                throw newEx("Cast can never succeed: the operand must not be an empty sequence", true, VC_EX_CODE, this);
            }
        }
        return this;
    }

    /**
    * Get the static cardinality of the expression
    */

    public int computeCardinality() {
        return (allowsEmpty() && Cardinality.allowsZero(operand.getCardinality())
                ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.EXACTLY_ONE);
    }

    /**
     * Get the static type of the expression
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return getTargetType();
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
        if (converter == Converter.BOOLEAN_TO_INTEGER) {
            return new IntegerValue[]{Int64Value.ZERO, Int64Value.PLUS_ONE};
        } else {
            return null;
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        CastExpression c2 = new CastExpression(getBaseExpression().copy(), getTargetType(), allowsEmpty());
        c2.converter = converter;
        c2.nsResolver = nsResolver;
        c2.setOperandIsStringLiteral(isOperandIsStringLiteral());
        return c2;
    }

    /**
    * Evaluate the expression
    */

    /*@Nullable*/ public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue value = (AtomicValue)operand.evaluateItem(context);
        if (value==null) {
            if (allowsEmpty()) {
                return null;
            } else {
                throw newEx("Cast does not allow an empty sequence", VC_EX_CODE, context, this);
            }
        }

        Converter converter = this.converter;
        if (converter == null) {
            ConversionRules rules = context.getConfiguration().getConversionRules();
            converter = rules.getConverter(value.getPrimitiveType(), getTargetType());
            if (converter == null) {
                throw newEx("Casting from " + value.getPrimitiveType() + " to " + getTargetType() + " is not permitted", true, VC_EX_CODE, this);
            }
            if (converter.isXPath30Conversion() && !getExecutable().isAllowXPath30() &&
                    !(converter instanceof StringConverter.StringToQName && isOperandIsStringLiteral())) {
                throw newEx("Casting from " + value.getPrimitiveType() + " to " + getTargetType() + " requires XPath 3.0 to be enabled", true, VC_EX_CODE, this);
            }
            if (nsResolver != null) {
                converter.setNamespaceResolver(nsResolver);
            }
        }
        ConversionResult result = converter.convert(value);
        if (result instanceof ValidationFailure) {
            ValidationFailure err = (ValidationFailure)result;
            String code = err.getErrorCode();
            if (code == null) {
                code = CAST_EX_CODE;
            }
            dynamicError(err.getMessage(), code, context);
            return null;
        }
        return (AtomicValue)result;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
		return other instanceof CastExpression && operand.equals(((CastExpression) other).operand) &&
			getTargetType() == ((CastExpression) other).getTargetType();
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ getTargetType().hashCode();
    }

    /**
     * Represent the expression as a string. The resulting string will be a valid XPath 3.0 expression
     * with no dependencies on namespace bindings.
     * @return the expression as a string in XPath 3.0 syntax
     */

    public String toString() {
        return getTargetType().getEQName() + "(" + operand.toString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("cast");
        out.emitAttribute("as", getTargetType().getEQName());
        operand.explain(out);
        out.endElement();
    }

}

