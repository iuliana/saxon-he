////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Binding;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LocalVariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

/**
 * This class represents an "order by" clause in a FLWOR expression
 */
public class OrderByClause extends Clause {

    SortKeyDefinition[] sortKeys;
    AtomicComparer[] comparators;
    TupleExpression tupleExpression;

    public OrderByClause(SortKeyDefinition[] sortKeys, TupleExpression tupleExpression) {
        this.sortKeys = sortKeys;
        this.tupleExpression = tupleExpression;
    }

    @Override
    public int getClauseKey() {
        return ORDERBYCLAUSE;
    }

    @Override
    public boolean containsNonInlineableVariableReference(Binding binding) {
        for (LocalVariableReference ref : tupleExpression.getSlots()) {
            if (ref.getBinding() == binding) {
                return true;
            }
        }
        return false;
    }

    public OrderByClause copy() {
        SortKeyDefinition[] sk2 = new SortKeyDefinition[sortKeys.length];
        for (int i=0; i<sortKeys.length; i++) {
            sk2[i] = sortKeys[i].copy();
        }
        OrderByClause obc = new OrderByClause(sk2, (TupleExpression)tupleExpression.copy());
        obc.setLocationId(getLocationId());
        obc.comparators = comparators;
        return obc;
    }

    public SortKeyDefinition[] getSortKeyDefinitions() {
        return sortKeys;
    }

    public AtomicComparer[] getAtomicComparers() {
        return comparators;
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     *
     * @param base the input tuple stream
     * @param context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new OrderByClausePull(base, tupleExpression, this, context);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, XPathContext context) {
        return new OrderByClausePush(destination, tupleExpression, this, context);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     *
     */
    @Override
    public void processSubExpressions(ExpressionProcessor processor) throws XPathException {
        tupleExpression = (TupleExpression)processor.processExpression(tupleExpression);
        for (int i=0; i<sortKeys.length; i++) {
            sortKeys[i].processSubExpressions(processor);
        }
    }

    /**
      * Type-check the expression
      */

     public void typeCheck(ExpressionVisitor visitor) throws XPathException {
         boolean allKeysFixed = true;
         for (int i = 0; i < sortKeys.length; i++) {
             if (!(sortKeys[i].isFixed())) {
                 allKeysFixed = false;
                 break;
             }
         }

         if (allKeysFixed) {
             comparators = new AtomicComparer[sortKeys.length];
         }

         for (int i = 0; i < sortKeys.length; i++) {
             Expression sortKey = sortKeys[i].getSortKey();
             RoleLocator role = new RoleLocator(RoleLocator.ORDER_BY, "", i);
             role.setErrorCode("XPTY0004");
             sortKey = TypeChecker.staticTypeCheck(sortKey, SequenceType.OPTIONAL_ATOMIC, false, role, visitor);
             sortKeys[i].setSortKey(sortKey, false);
             //sortKeys[i].typeCheck(visitor, contextItemType);
             if (sortKeys[i].isFixed()) {
                 AtomicComparer comp = sortKeys[i].makeComparator(
                         visitor.getStaticContext().makeEarlyEvaluationContext());
                 sortKeys[i].setFinalComparator(comp);
                 if (allKeysFixed) {
                     comparators[i] = comp;
                 }
             }

         }
     }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("order-by");
        for (SortKeyDefinition k : sortKeys) {
            out.startSubsidiaryElement("key");
            k.getSortKey().explain(out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        fsb.append("order by ... ");
        return fsb.toString();
    }

    /**
     * Callback for evaluating the sort keys
     */

    /*@Nullable*/ public AtomicValue evaluateSortKey(int n, XPathContext c) throws XPathException {
        return (AtomicValue)sortKeys[n].getSortKey().evaluateItem(c);
    }

}

