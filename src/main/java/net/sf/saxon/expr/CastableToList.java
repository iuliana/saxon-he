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
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.ListType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.*;

/**
 * Expression class for a cast to a List type
 */
public class CastableToList extends UnaryExpression {

    private ListType targetType;
    private boolean allowEmpty;
    /*@Nullable*/
    private NamespaceResolver nsResolver = null;

    public CastableToList(Expression source, ListType targetType, boolean allowEmpty) {
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

        RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "castable as", 0);
        //role.setSourceLocator(this);
        operand = TypeChecker.staticTypeCheck(operand, atomicType, false, role, visitor);

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        boolean maybeString = th.relationship(operand.getItemType(th), BuiltInAtomicType.STRING) != TypeHierarchy.DISJOINT;
        boolean maybeUntyped = th.relationship(operand.getItemType(th), BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT;

        if (!maybeString && !maybeUntyped){
            // Casting from other types can never succeed
           return Literal.makeLiteral(BooleanValue.FALSE);
        }

        if (operand instanceof Literal) {
            GroundedValue literalOperand = ((Literal) operand).getValue();
            if (literalOperand instanceof AtomicValue) {
                return Literal.makeLiteral(BooleanValue.get(effectiveBooleanValue(visitor.getStaticContext().makeEarlyEvaluationContext())));
            }
            if (literalOperand instanceof EmptySequence) {
                return Literal.makeLiteral(BooleanValue.get((allowEmpty ? true : false)));
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
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Get the static type of the expression
     *
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return BuiltInAtomicType.BOOLEAN;
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
        CastableToList c = new CastableToList(getBaseExpression().copy(), targetType, allowEmpty);
        c.nsResolver = nsResolver;
        return c;
    }


    /**
     * Evaluate the expression
     */

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        AtomicValue value = (AtomicValue) operand.evaluateItem(context);
        if (value == null) {
            return allowEmpty;
        }
        try {
            CastToList.cast(value.getStringValue(), targetType, nsResolver, context.getConfiguration().getConversionRules());
            return true;
        } catch (XPathException err) {
            return false;
        }
    }




    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                targetType == ((CastableToList) other).targetType &&
                allowEmpty == ((CastableToList) other).allowEmpty &&
                nsResolver == ((CastableToList) other).nsResolver;
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
        return operand.toString() + " castable as " + targetType.getEQName();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("castableToList");
        out.emitAttribute("as", targetType.toString());
        operand.explain(out);
        out.endElement();
    }

}

// Copyright (c) 2011 Saxonica Limited. All rights reserved.


