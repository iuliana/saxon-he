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
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * Expression class for a cast to a List type
 */
public class CastToList extends UnaryExpression {

    private ListType targetType;
    private boolean allowEmpty;
    /*@Nullable*/
    private NamespaceResolver nsResolver = null;

    public CastToList(Expression source, ListType targetType, boolean allowEmpty) {
        super(source);
        this.targetType = targetType;
        this.allowEmpty = allowEmpty;
    }

    public boolean isAllowEmpty(){
        return allowEmpty;
    }

    public ListType getTargetType(){
        return  targetType;
    }

    public NamespaceResolver getNamespaceResolver(){
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
        SequenceType atomicType = SequenceType.makeSequenceType(BuiltInAtomicType.STRING, (allowEmpty ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.EXACTLY_ONE));

        RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "cast as", 0);
        //role.setSourceLocator(this);
        operand = TypeChecker.staticTypeCheck(operand, atomicType, false, role, visitor);

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        boolean maybeString = th.relationship(operand.getItemType(th), BuiltInAtomicType.STRING) != TypeHierarchy.DISJOINT;
        boolean maybeUntyped = th.relationship(operand.getItemType(th), BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT;

        if(!maybeString && !maybeUntyped){
           XPathException err = new XPathException("Casting to list requires an xs:string or xs:untypedAtomic operand");
           err.setErrorCode("XPTY0004");
           err.setLocator(this);
           err.setIsTypeError(true);
           throw err;
        }

        if (operand instanceof Literal) {
            GroundedValue literalOperand = ((Literal) operand).getValue();
            if (literalOperand instanceof AtomicValue) {
                try {
                    SequenceIterator seq = iterate(visitor.getStaticContext().makeEarlyEvaluationContext());
                    return Literal.makeLiteral(SequenceTool.toGroundedValue(seq));
                } catch (XPathException err) {
                    err.maybeSetErrorCode("FORG0001");
                    err.setLocator(this);
                    err.setIsTypeError(true);
                    throw err;
                }
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
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the static type of the expression
     *
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (targetType.getItemType() instanceof ItemType) {
            return (ItemType) targetType.getItemType();
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NON_CREATIVE}.
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
        CastToList c = new CastToList(getBaseExpression().copy(), targetType, allowEmpty);
        c.nsResolver = nsResolver;
        return c;
    }

    /**
     * Evaluate the expression
     */

    public SequenceIterator iterate(XPathContext context) throws XPathException {
        AtomicValue value = (AtomicValue) operand.evaluateItem(context);
        if (value == null) {
            if (allowEmpty) {
                return EmptyIterator.emptyIterator();
            } else {
                XPathException e = new XPathException("Cast does not allow an empty sequence");
                e.setXPathContext(context);
                e.setLocator(this);
                e.setErrorCode("XPTY0004");
                throw e;
            }
        }
        return cast(value.getStringValueCS(), targetType, nsResolver, context.getConfiguration().getConversionRules()).iterate();
    }

    /**
     * Cast a string value to a list type
     * @param value the input string value
     * @param targetType the target list type
     * @param nsResolver the namespace context, needed if the type is namespace-sensitive
     * @param rules the conversion rules
     * @return the sequence of atomic values that results from the conversion
     * @throws XPathException if the conversion fails
     */

    public static AtomicSequence cast(CharSequence value, ListType targetType, final NamespaceResolver nsResolver, final ConversionRules rules)
            throws XPathException {
        ValidationFailure failure = targetType.validateContent(value, nsResolver, rules);
        if(failure != null) {
            throw failure.makeException();
        }
        return targetType.getTypedValue(value, nsResolver, rules);
    }


    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                targetType == ((CastToList) other).targetType &&
                allowEmpty == ((CastToList) other).allowEmpty &&
                nsResolver == ((CastToList) other).nsResolver;
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
        out.startElement("castToList");
        out.emitAttribute("as", targetType.toString());
        operand.explain(out);
        out.endElement();
    }

}

// Copyright (c) 2011-2012 Saxonica Limited. All rights reserved.


