////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.TypeHierarchy;


/**
 * An SimpleStepExpression is a special case of a SlashExpression in which the
 * start expression selects a single item (or nothing), and the step expression is
 * a simple AxisExpression. This is designed to avoid the costs of creating a new
 * dynamic context for expressions (common in XQuery) such as
 * for $b in EXPR return $b/title
 *
*/

public final class SimpleStepExpression extends SlashExpression {

    public SimpleStepExpression(Expression start, Expression step) {
        super(start, step);
        if (!(step instanceof AxisExpression)) {
            throw new IllegalArgumentException();
        }
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression start2 = visitor.typeCheck(start, contextItemType);
        if (start2 != start) {
            setStartExpression(start2);
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression step2 = visitor.typeCheck(step, new ExpressionVisitor.ContextItemType(start2.getItemType(th), false));
        if (step2 != step) {
            setStepExpression(step2);
        }
        if (!(step instanceof AxisExpression)) {
            SlashExpression se = new SlashExpression(start, step);
            ExpressionTool.copyLocationInfo(this, se);
            return se;
        }
        if (start instanceof ContextItemExpression && AxisInfo.isForwards[((AxisExpression)step).getAxis()]) {
            return step;
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */
    /*@NotNull*/
    @Override
    public Expression copy() {
        return new SimpleStepExpression(start.copy(), step.copy());
    }

    /**
     * Evaluate the expression, returning an iterator over the result
     * @param context the evaluation context
     */
    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Item origin = start.evaluateItem(context);
        if (origin == null) {
            return EmptyIterator.getInstance();
        }
        return ((AxisExpression)step).iterate(origin);
    }


}

