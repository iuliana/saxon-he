package net.sf.saxon.expr;


import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.Set;

/**
 * Expression class for a cast to a union type
 */
public class CastToUnion extends UnaryExpression {

    private UnionType targetType;
    private boolean allowEmpty;
    /*@Nullable*/
    private NamespaceResolver nsResolver = null;

    public CastToUnion(Expression source, UnionType targetType, boolean allowEmpty) {
        super(source);
        this.targetType = targetType;
        this.allowEmpty = allowEmpty;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public UnionType getTargetType() {
        return targetType;
    }

    public NamespaceResolver getNamespaceResolver() {
        return nsResolver;
    }

    /**
     * Simplify the expression
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        if (targetType.isNamespaceSensitive()) {
            StaticContext env = visitor.getStaticContext();
            nsResolver = env.getNamespaceResolver();
        }
        operand = visitor.simplify(operand);
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        SequenceType atomicType = SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, getCardinality());

        RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "cast as", 0);
        //role.setSourceLocator(this);
        operand = TypeChecker.staticTypeCheck(operand, atomicType, false, role, visitor);

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        if (operand instanceof Literal) {
            GroundedValue literalOperand = ((Literal) operand).getValue();
            if (literalOperand instanceof AtomicValue) {
                GroundedValue av = SequenceTool.toGroundedValue(iterate(visitor.getStaticContext().makeEarlyEvaluationContext()));
                return Literal.makeLiteral(av);
            }
            if (literalOperand instanceof EmptySequence) {
                if (allowEmpty) {
                    return operand;
                } else {
                    XPathException err = new XPathException("Cast can never succeed: the operand must not be an empty sequence");
                    err.setErrorCode("XPTY0004");
                    err.setLocator(this);
                    err.setIsTypeError(true);
                    throw err;
                }
            }
        }

        return this;
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
        // if the operand can't be empty, then set allowEmpty to false to provide more information for analysis
        if (!Cardinality.allowsZero(operand.getCardinality())) {
            allowEmpty = false;
            resetLocalStaticProperties();
        }
        return this;
    }

    /**
     * Get the static cardinality of the expression
     */

    public int computeCardinality() {
        int c = StaticProperty.ALLOWS_ONE;
        if (allowEmpty && Cardinality.allowsZero(operand.getCardinality())) {
            c |= StaticProperty.ALLOWS_ZERO;
        }
        if (targetType.containsListType()) {
            c |= StaticProperty.ALLOWS_ZERO;
            c |= StaticProperty.ALLOWS_MANY;
        }
        return c;
    }

    /**
     * Get the static type of the expression
     *
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (targetType instanceof PlainType) {
            return (PlainType)targetType;
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NON_CREATIVE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        CastToUnion c = new CastToUnion(getBaseExpression().copy(), targetType, allowEmpty);
        c.nsResolver = nsResolver;
        return c;
    }

    /**
     * Evaluate the expression
     */

    public SequenceIterator iterate(XPathContext context) throws XPathException {
        ConversionRules rules = context.getConfiguration().getConversionRules();
        AtomicValue value = (AtomicValue) operand.evaluateItem(context);
        if (value == null) {
            if (allowEmpty) {
                return null;
            } else {
                XPathException e = new XPathException("Cast does not allow an empty sequence");
                e.setXPathContext(context);
                e.setLocator(this);
                e.setErrorCode("XPTY0004");
                throw e;
            }
        }

        try {
            AtomicSequence result = cast(value, targetType, nsResolver, rules);
            return result.iterate();
        } catch (XPathException err) {
            err.maybeSetContext(context);
            err.maybeSetLocation(this);
            err.setErrorCode("FORG0001");
            throw err;
        }
    }

    /**
     * Static method to perform the castable check of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param targetType the union type to which the value is to be converted
     * @param nsResolver the namespace context, required if the type is namespace-sensitive
     * @param context    the XPath dynamic evaluation context
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     * @throws XPathException if the conversion fails
     */
    public static boolean castable(AtomicValue value, UnionType targetType,
                                   NamespaceResolver nsResolver, XPathContext context) {

        try {
            CastToUnion.cast(value, targetType, nsResolver, context.getConfiguration().getConversionRules());
            return true;
        } catch (XPathException err) {
            return false;
        }
    }

    /**
     * Static method to perform the cast of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param targetType the union type to which the value is to be converted
     * @param nsResolver the namespace context, required if the type is namespace-sensitive
     * @param rules      the conversion rules
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     * @throws XPathException if the conversion fails
     */

    public static AtomicSequence cast(AtomicValue value, UnionType targetType,
                                      NamespaceResolver nsResolver, ConversionRules rules)
            throws XPathException {
        //ConversionRules rules = context.getConfiguration().getConversionRules();
        if (value == null) {
            throw new NullPointerException();
        }

        // 1. If the value is a string or untypedAtomic, try casting to each of the member types

        if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
            try {
                return targetType.getTypedValue(value.getStringValueCS(), nsResolver, rules);
            } catch (ValidationException e) {
                e.setErrorCode("FORG0001");
                throw e;
            }
        }

        // 2. If the value is an instance of a type in the transitive membership of the union, return it unchanged

        AtomicType label = value.getItemType();
        Set<PlainType> memberTypes = targetType.getPlainMemberTypes();
        while (label != null) {
            if (memberTypes.contains(label)) {
                return value;
            } else {
                label = (label.getBaseType() instanceof AtomicType ? (AtomicType) label.getBaseType() : null);
            }
        }

        // 3. if the value can be cast to any of the member types, return the result of that cast

        for (PlainType type : memberTypes) {
            if (type instanceof AtomicType) {
                Converter c = rules.getConverter(value.getItemType(), (AtomicType) type);
                if (c != null) {
                    ConversionResult result = c.convert(value);
                    if (result instanceof AtomicValue) {
                        return (AtomicValue) result;
                    }
                }
            }
        }

        throw new XPathException("Cannot convert the supplied value to the required union type", "FORG0001");
    }



    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                targetType == ((CastToUnion) other).targetType &&
                allowEmpty == ((CastToUnion) other).allowEmpty &&
                nsResolver == ((CastToUnion) other).nsResolver;
    }

    /**
     * get HashCode for comparing two expressions.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ targetType.hashCode();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return targetType.getEQName() + "(" + operand.toString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("castToUnion");
        out.emitAttribute("as", targetType.toString());
        operand.explain(out);
        out.endElement();
    }

}

// Copyright (c) 2011 Saxonica Limited. All rights reserved.


