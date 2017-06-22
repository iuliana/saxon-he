////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.HomogeneityCheckerIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * This class is an expression that does a run-time check of the result of a "/" expression
 * to ensure that (a) the results consists entirely of atomic values and function items, or entirely of nodes,
 * and (b) if the results are nodes, then they are deduplicated and sorted into document order.
 */
public class HomogeneityChecker extends UnaryExpression {

    public HomogeneityChecker(Expression base) {
        super(base);
    }

    /**
     * Type-check the expression. Default implementation for unary operators that accept
     * any kind of operand
     */
    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (getBaseExpression() instanceof HomogeneityChecker) {
            return visitor.typeCheck(getBaseExpression(), contextItemType);
        }
        Expression e = super.typeCheck(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType type = getBaseExpression().getItemType(th);
        int rel = th.relationship(type, AnyNodeTest.getInstance());
        if (rel == TypeHierarchy.DISJOINT) {
            // expression cannot return nodes, so this checker is redundant
            if(getBaseExpression() instanceof SlashExpression && ((SlashExpression)getBaseExpression()).getLeadingSteps() instanceof SlashExpression &&
                    (((SlashExpression)getBaseExpression()).getLeadingSteps().getSpecialProperties() & StaticProperty.ORDERED_NODESET) == 0){
                DocumentSorter ds = new DocumentSorter(((SlashExpression)getBaseExpression()).getLeadingSteps());
                SlashExpression se = new SlashExpression(ds, ((SlashExpression)getBaseExpression()).getLastStep());
                ExpressionTool.copyLocationInfo(this, se);
                return se;
            } else {
                return getBaseExpression();
            }
        } else if (rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMED_BY) {
            // expression always returns nodes, so replace this expression with a DocumentSorter
            DocumentSorter ds = new DocumentSorter(getBaseExpression());
            ExpressionTool.copyLocationInfo(this, ds);
            return ds;
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (getBaseExpression() instanceof HomogeneityChecker) {
            return visitor.optimize(getBaseExpression(), contextItemType);
        }
        return super.optimize(visitor, contextItemType);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */
    /*@NotNull*/
    @Override
    public Expression copy() {
        return new HomogeneityChecker(getBaseExpression().copy());
    }

    /**
     * Iterate the path-expression in a given context
     * @param context the evaluation context
     */

    /*@NotNull*/
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        // This class delivers the result of the path expression in unsorted order,
        // without removal of duplicates. If sorting and deduplication are needed,
        // this is achieved by wrapping the path expression in a DocumentSorter

        SequenceIterator base = getBaseExpression().iterate(context);
        return new HomogeneityCheckerIterator(base, this);   
    }



    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "homogeneityChecker";
    }
}

