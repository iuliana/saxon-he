////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Binding;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.List;

/**
 *  A "Clause" refers specifically to one of the clauses of a FLWOR expression, for example the "for"
 *  clause, the "let" clause, the "where" or "order by" clause. (The "return" clause, however, is not
 *  modelled as a Clause).
 */
public abstract class Clause {

    public static final int FOR = 0;
    public static final int LET = 1;
    public static final int WINDOW = 2;
    public static final int GROUPBYCLAUSE = 3;
    public static final int COUNT = 4;
    public static final int ORDERBYCLAUSE = 5;
    public static final int WHERE = 6;
    public static final int TRACE = 7;

    private int locationId;

    /**
     * Get the location ID, which can be used in conjunction with a LocationProvider to determine
     * the system ID and line number of the clause
     * @return the location ID
     */
    public int getLocationId() {
        return locationId;
    }

    /**
     * Set the location ID, which can be used in conjunction with a LocationProvider to determine
     * the system ID and line number of the clause
     * @param locationId the location ID
     */

    public void setLocationId(int locationId) {
        this.locationId = locationId;
    }

    /**
     * Create a copy of this clause
     * @return the copied clause
     */

    public abstract Clause copy();

    /**
     * Optimize any expressions contained within this clause
     * @param visitor the ExpressionVisitor, providing access to static context information
     * @param contextItemType the type of the context item
     * @throws XPathException if any error is detected
     */
    public void optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {}

    /**
     * Type-check any expression contained within this clause
     * @param visitor the ExpressionVisitor, providing access to static context information
     * @throws XPathException if any error is detected
    */

     public void typeCheck(ExpressionVisitor visitor) throws XPathException {
     }

    /**
     * Get a pull-mode tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */

    public abstract TuplePull getPullStream(TuplePull base, XPathContext context);

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     * expression
     */

    public abstract TuplePush getPushStream(TuplePush destination, XPathContext context);

    /**
     * Process the subexpressions of this clause
     * @param processor the expression processor used to process the subexpressions
     * @throws XPathException if any error is detected
     */

    public abstract void processSubExpressions(ExpressionProcessor processor) throws XPathException;

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */

    public abstract void explain(ExpressionPresenter out);

    /**
     * Get the variables bound by this clause
     * @return the variable bindings
     */

    public LocalVariableBinding[] getRangeVariables() {
        return new LocalVariableBinding[0];
    }

    /**
     * Build a list of all references to a variables declared in this clause
     * @param visitor the expression visitor
     * @param binding a variable declared in this clause
     * @param refs the list of variable references, initially empty, to which the method will append
     */

    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> refs){}

    /**
     * Determine whether the clause contains a reference to a local variable binding that cannot be inlined
     * @param binding the binding for the local variable in question
     * @return true if this clause uses the variable in a way that does not permit inlining
     */

    public boolean containsNonInlineableVariableReference(Binding binding) {
        return false;
    }

    /**
     * Supply improved type information to the expressions that contain references to the variables declared in this clause
     * @param visitor the expression visitor
     * @param references the list of variable references
     * @param returnExpr the expression in the return clause
     */

    public void refineVariableType(final ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr){}

     /**
     * Get a keyword identifying what kind of clause this is
     * @return the kind of clause
     */

    public abstract int getClauseKey();
}


