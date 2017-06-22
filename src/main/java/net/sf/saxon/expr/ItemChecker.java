////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.event.TypeCheckingFilter;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;

import java.util.List;


/**
* A ItemChecker implements the item type checking of "treat as": that is,
* it returns the supplied sequence, checking that all its items are of the correct type
*/

public final class ItemChecker extends UnaryExpression {

    private ItemType requiredItemType;
    private RoleLocator role;

    /**
     * Constructor
     * @param sequence the expression whose value we are checking
     * @param itemType the required type of the items in the sequence
     * @param role information used in constructing an error message
    */

    public ItemChecker(Expression sequence, ItemType itemType, RoleLocator role) {
        super(sequence);
        requiredItemType = itemType;
        this.role = role;
        adoptChildExpression(sequence);
    }

    /**
     * Get the required type
     * @return the required type of the items in the sequence
     */

    public ItemType getRequiredType() {
        return requiredItemType;
    }

    /**
     * Get the RoleLocator (used to construct error messages)
     * @return the RoleLocator
     */

    public RoleLocator getRoleLocator() {
        return role;
    }

    /**
    * Simplify an expression
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        operand = visitor.simplify(operand);
        if (requiredItemType instanceof AnyItemType) {
            return operand;
        }
        return this;
    }

    /**
    * Type-check the expression
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        // When typeCheck is called a second time, we might have more information...

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        int card = operand.getCardinality();
        if (card == StaticProperty.EMPTY) {
            //value is always empty, so no item checking needed
            return operand;
        }
        ItemType supplied = operand.getItemType(th);
        int relation = th.relationship(requiredItemType, supplied);
        if (relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMES) {
            return operand;
        } else if (relation == TypeHierarchy.DISJOINT) {
            final NamePool namePool = visitor.getConfiguration().getNamePool();
            if (Cardinality.allowsZero(card)) {

                String message = role.composeErrorMessage(
                        requiredItemType, operand.getItemType(th));
                visitor.getStaticContext().issueWarning("The only value that can pass type-checking is an empty sequence. " +
                        message, this);
            } else if (requiredItemType.equals(BuiltInAtomicType.STRING) && th.isSubType(supplied, BuiltInAtomicType.ANY_URI)) {
                // URI promotion will take care of this at run-time
                return operand;
            } else {
                String message = role.composeErrorMessage(requiredItemType, operand.getItemType(th));
                XPathException err = new XPathException(message);
                err.setErrorCode(role.getErrorCode());
                err.setLocator(this);
                err.setIsTypeError(role.isTypeError());
                throw err;
            }
        }
        return this;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        int m = ITERATE_METHOD | PROCESS_METHOD | ITEM_FEED_METHOD;
        if (!Cardinality.allowsMany(getCardinality())) {
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
    /*@Nullable*/@Override
    public IntegerValue[] getIntegerBounds() {
        return operand.getIntegerBounds();
    }

    /**
    * Iterate over the sequence of values
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        SequenceIterator base = operand.iterate(context);
        return new ItemMappingIterator(base, getMappingFunction(context), true);
    }

    /**
     * Get the mapping function used to implement this item check. This mapping function is applied
     * to each item in the input sequence.
     * @param context The dynamic context used to evaluate the mapping function
     * @return the mapping function. This will be an identity mapping: the output sequence is the same
     * as the input sequence, unless the dynamic type checking reveals an error.
     */

    public ItemMappingFunction getMappingFunction(XPathContext context) {
        return new ItemTypeCheckingFunction<Item>(requiredItemType, role, this, context.getConfiguration());
    }

    /**
    * Evaluate as an Item.
    */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Item item = operand.evaluateItem(context);
        if (item==null) return null;
        testConformance(item, context);
        return item;
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public void process(XPathContext context) throws XPathException {
        Expression next = operand;
        int card = StaticProperty.ALLOWS_ZERO_OR_MORE;
        if (next instanceof CardinalityChecker) {
            card = ((CardinalityChecker)next).getRequiredCardinality();
            next = ((CardinalityChecker)next).getBaseExpression();
        }
        if ((next.getImplementationMethod() & PROCESS_METHOD) != 0 && !(requiredItemType instanceof DocumentNodeTest)) {
            SequenceReceiver out = context.getReceiver();
            TypeCheckingFilter filter = new TypeCheckingFilter(out);
            filter.setRequiredType(requiredItemType, card, role, this);
            context.setReceiver(filter);
            next.process(context);
            filter.close();
            context.setReceiver(out);
        } else {
            super.process(context);
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new ItemChecker(getBaseExpression().copy(), requiredItemType, role);
    }


    private void testConformance(Item item, XPathContext context) throws XPathException {
        if (!requiredItemType.matchesItem(item, true, (context == null ? null : context.getConfiguration()))) {
            String message;
            if (context == null) {
                // no name pool available
                message = "Supplied value of type " + Type.displayTypeName(item) +
                        " does not match the required type of " + role.getMessage();
            } else {
                final NamePool pool = context.getNamePool();
                final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
                message = role.composeErrorMessage(requiredItemType, Type.getItemType(item, th));
            }
            String errorCode = role.getErrorCode();
            if ("XPDY0050".equals(errorCode)) {
                // error in "treat as" assertion
                dynamicError(message, errorCode, context);
            } else {
                typeError(message, errorCode, context);
            }
        }
    }

    /**
     * Determine the data type of the items returned by the expression
     * @param th the type hierarchy cache
     */

	/*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        ItemType operandType = operand.getItemType(th);
        int relationship = th.relationship(requiredItemType, operandType);
        switch (relationship) {
            case TypeHierarchy.OVERLAPS:
                if (requiredItemType instanceof NodeTest && operandType instanceof NodeTest) {
                    return new CombinedNodeTest((NodeTest)requiredItemType, Token.INTERSECT, (NodeTest)operandType);
                } else {
                    // we don't know how to intersect atomic types, it doesn't actually happen
                    return requiredItemType;
                }

            case TypeHierarchy.SUBSUMES:
            case TypeHierarchy.SAME_TYPE:
                // shouldn't happen, but it doesn't matter
                return operandType;
            case TypeHierarchy.SUBSUMED_BY:
            default:
                return requiredItemType;
        }
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                requiredItemType == ((ItemChecker)other).requiredItemType;
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ requiredItemType.hashCode();
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("treat");
        out.emitAttribute("as", requiredItemType.toString());
        operand.explain(out);
        out.endElement();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "TreatAs";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        String typeDesc = requiredItemType.toString();
        return "(" + operand.toString() + ") treat as " + typeDesc;
    }

}


