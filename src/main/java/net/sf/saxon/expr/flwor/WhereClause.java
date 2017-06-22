////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.List;

/**
 * A "where" clause in a FLWOR expression
 */
public class WhereClause extends Clause {

    private Expression predicate;

    public WhereClause(Expression predicate) {
        this.predicate = predicate;
    }

    @Override
    public int getClauseKey() {
        return WHERE;
    }

    public Expression getPredicate(){
        return predicate;
    }

    public void setPredicate(Expression predicate){
        this.predicate = predicate;
    }

    public WhereClause copy() {
        WhereClause w2 = new WhereClause(predicate.copy());
        w2.setLocationId(getLocationId());
        return w2;
    }

      /**
     * Type-check the expression
     */
    @Override
    public void typeCheck(ExpressionVisitor visitor) throws XPathException {
        super.typeCheck(visitor);
    }


    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     * @param base the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new WhereClausePull(base, predicate);
    }

    @Override
    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> references){
        ExpressionTool.gatherVariableReferences(predicate, binding, references);
    }

     @Override
    public void refineVariableType(ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr) {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        final ItemType actualItemType = predicate.getItemType(th);
         for (VariableReference ref : references) {
             ref.refineVariableType(actualItemType, predicate.getCardinality(),
                     (predicate instanceof Literal ? ((Literal) predicate).getValue() : null),
                     predicate.getSpecialProperties(), visitor);
             visitor.resetStaticProperties();
         }
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context  the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, XPathContext context) {
        return new WhereClausePush(destination, predicate);
    }

    /**
     * Process the subexpressions of this clause
    * @param processor the expression processor used to process the subexpressions
    *
    */
    @Override
    public void processSubExpressions(ExpressionProcessor processor) throws XPathException {
        predicate = processor.processExpression(predicate);
    }

    public  Expression getBaseExpression(){
        return predicate;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("where");
        predicate.explain(out);
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        fsb.append("where ");
        fsb.append(predicate.toString());
        return fsb.toString();
    }
}

