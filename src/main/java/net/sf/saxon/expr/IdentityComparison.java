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
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.List;


/**
* IdentityComparison: a boolean expression that compares two nodes
* for equals, not-equals, greater-than or less-than based on identity and
* document ordering
*/

public final class IdentityComparison extends BinaryExpression {

    private boolean generateIdEmulation = false;
        // this flag is set if an "X is Y" or "X isnot Y" comparison is being used
        // to emulate generate-id(X) = / != generate-id(Y). The handling of an empty
        // sequence in the two cases is different.

    /**
    * Create an identity comparison identifying the two operands and the operator
    * @param p1 the left-hand operand
    * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
    * @param p2 the right-hand operand
    */

    public IdentityComparison(Expression p1, int op, Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Set flag to indicate different empty-sequence behavior when emulating
     * comparison of two generate-id's. This is relevant when operands evaluate
     * to an empty sequence; if both are empty, the "is" operator returns (), while the
     * generate-id() comparison compares two empty strings, which returns true; if one is
     * empty, the "is" operator returns (), while the generate-id() comparison returns false.
     * @param flag true if this function is being used to compare generate-id() output
     */

    public void setGenerateIdEmulation(boolean flag) {
        generateIdEmulation = flag;
    }

    /**
     * Test the flag that indicates different empty-sequence behavior when emulating
     * comparison of two generate-id's
     * @return true if this function is being used to compare generate-id() output
     */

    public boolean isGenerateIdEmulation() {
        return generateIdEmulation;
    }

    /**
    * Type-check the expression
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        operand0 = visitor.typeCheck(operand0, contextItemType);
        operand1 = visitor.typeCheck(operand1, contextItemType);

        if (!generateIdEmulation) {
            if (Literal.isEmptySequence(operand0) || Literal.isEmptySequence(operand1)) {
                return Literal.makeEmptySequence();
            }
        }

        RoleLocator role0 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 0);
        //role0.setSourceLocator(this);
        operand0 = TypeChecker.staticTypeCheck(
                operand0, SequenceType.OPTIONAL_NODE, false, role0, visitor);

        RoleLocator role1 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 1);
        //role1.setSourceLocator(this);
        operand1 = TypeChecker.staticTypeCheck(
                operand1, SequenceType.OPTIONAL_NODE, false, role1, visitor);
        
        if (!Cardinality.allowsZero(operand0.getCardinality()) && !Cardinality.allowsZero(operand1.getCardinality())) {
            generateIdEmulation = false;
            // because the flag only makes a difference if one of the operands evaluates to ()
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

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression r = super.optimize(visitor, contextItemType);
        if (r != this) {
            if (!generateIdEmulation) {
                if (Literal.isEmptySequence(operand0) || Literal.isEmptySequence(operand1)) {
                    return Literal.makeEmptySequence();
                }
            }
        }
        return r;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        IdentityComparison ic = new IdentityComparison(operand0.copy(), operator, operand1.copy());
        ic.generateIdEmulation = generateIdEmulation;
        return ic;
    }



    /**
    * Evaluate the expression
    */

    /*@Nullable*/ public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        NodeInfo node0 = getNode(operand0, context);
        if (node0==null) {
            if (generateIdEmulation) {
                return BooleanValue.get(getNode(operand1, context)==null);
            }
            return null;
        }

        NodeInfo node1 = getNode(operand1, context);
        if (node1==null) {
            if (generateIdEmulation) {
                return BooleanValue.FALSE;
            }
            return null;
        }

        return BooleanValue.get(compareIdentity(node0, node1));
    }

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        NodeInfo node0 = getNode(operand0, context);
        if (node0==null) {
            return generateIdEmulation && getNode(operand1, context) == null;
        }

        NodeInfo node1 = getNode(operand1, context);
        return node1 != null && compareIdentity(node0, node1);

    }

    private boolean compareIdentity(NodeInfo node0, NodeInfo node1) {

        switch (operator) {
        case Token.IS:
            return node0.isSameNodeInfo(node1);
        case Token.PRECEDES:
            return GlobalOrderComparer.getInstance().compare(node0, node1) < 0;
        case Token.FOLLOWS:
            return GlobalOrderComparer.getInstance().compare(node0, node1) > 0;
        default:
            throw new UnsupportedOperationException("Unknown node identity test");
        }
    }

    private static NodeInfo getNode(Expression exp, XPathContext c) throws XPathException {
        return (NodeInfo)exp.evaluateItem(c);
    }


    /**
    * Determine the data type of the expression
    * @return Type.BOOLEAN
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return BuiltInAtomicType.BOOLEAN;
    }



}

