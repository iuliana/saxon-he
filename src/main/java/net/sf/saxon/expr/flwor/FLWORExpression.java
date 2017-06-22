////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Controller;
import net.sf.saxon.TypeCheckerEnvironment;
import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * This class represents a FLWOR expression, evaluated using tuple streams
 */
public class FLWORExpression extends Expression {

	private static final Logger LOGGER = Logger.getLogger(FLWORExpression.class);
	
    public List<Clause> clauses;
    public Expression returnClause;

    public FLWORExpression(List<Clause> clauses, Expression returnClause) {
        this.clauses = clauses;
        this.returnClause = returnClause;
    }

    /**
     * Get the list of clauses of the FLWOR expression, in the order they are written.
     * This excludes the return clause
     *
     * @return the list of clauses
     */
    public List<Clause> getClauseList() {
        return clauses;
    }

	private static boolean isLoopingClause(Clause c) {
		return c.getClauseKey() == Clause.FOR || c.getClauseKey() == Clause.GROUPBYCLAUSE ||
			c.getClauseKey() == Clause.WINDOW;
    }

    /**
     * Get the return clause of the FLWOR expression
     *
     * @return the expression contained in the return clause
     */
    public Expression getReturnClause() {
        return returnClause;
    }

    /**
     * Determine whether a given variable binding belongs to this FLWOR expression
     *
     * @param binding the binding being sought
     * @return true if this binding belongs to one of the clauses of this FLWOR expression
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        for (Clause c : clauses) {
            if (clauseHasBinding(c, binding)) {
                return true;
            }
        }
        return false;
    }

    private boolean clauseHasBinding(Clause c, Binding binding) {
        for (Binding b : c.getRangeVariables()) {
            if (b == binding) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression
     *          rewriting
     */
    @Override
    public Expression simplify(final ExpressionVisitor visitor) throws XPathException {
        ExpressionProcessor simplifier = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) throws XPathException {
                return visitor.simplify(expr);
            }
        };
        for (Clause c : clauses) {
            c.processSubExpressions(simplifier);
        }
        returnClause = visitor.simplify(returnClause);
        return this;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     * <p/>
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     * <p/>
     * <p>If the implementation returns a value other than "this", then it is required to ensure that
     * the location information in the returned expression have been set up correctly.
     * It should not rely on the caller to do this, although for historical reasons many callers do so.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten to perform necessary run-time type checks,
     *         and to perform other type-related optimizations
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    @Override
    public Expression typeCheck(final ExpressionVisitor visitor, final ExpressionVisitor.ContextItemType contextItemType)
            throws XPathException {

        ExpressionProcessor typeChecker = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) throws XPathException {
                return visitor.typeCheck(expr, contextItemType);
            }
        };
        for (int i = 0; i < clauses.size(); i++) {
            clauses.get(i).processSubExpressions(typeChecker);
            clauses.get(i).typeCheck(visitor);
            LocalVariableBinding[] bindings = clauses.get(i).getRangeVariables();

            for (Binding b : bindings) {
                List references = new ArrayList();
                for (int j = i; j < clauses.size(); j++) {
                    clauses.get(j).gatherVariableReferences(visitor, b, references);
                }
                ExpressionTool.gatherVariableReferences(returnClause, b, references);
                clauses.get(i).refineVariableType(visitor, references, returnClause);
            }
        }
        returnClause = visitor.typeCheck(returnClause, contextItemType);
        return this;
    }

    /**
     * Determine whether this expression implements its own method for static type checking
     *
     * @return true - this expression has a non-trivial implementation of the staticTypeCheck()
     *         method
     */
    @Override
    public boolean implementsStaticTypeCheck() {
        for (Clause c : clauses) {
            switch (c.getClauseKey()) {
                case Clause.LET:
                case Clause.WHERE:
                    continue;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * Static type checking for let expressions is delegated to the expression itself,
     * and is performed on the "return" expression, to allow further delegation to the branches
     * of a conditional
     * @param req the required type
     * @param backwardsCompatible true if backwards compatibility mode applies
     * @param role the role of the expression in relation to the required type
     * @param visitor an expression visitor
     * @return the expression after type checking (perhaps augmented with dynamic type checking code)
     * @throws XPathException if failures occur, for example if the static type of one branch of the conditional
     * is incompatible with the required type
     */
    @Override
	public Expression staticTypeCheck(SequenceType req, boolean backwardsCompatible,
		RoleLocator role, TypeCheckerEnvironment visitor) throws XPathException {
		// only called if implementsStaticTypeCheck() returns true
		returnClause = TypeChecker.staticTypeCheck(returnClause, req, backwardsCompatible, role, visitor);
		return this;
	}

    /**
     * This method is required to refine the variabletype of the returnClause
     * Repeated code in the Clause sub-classes. Room here for commoning-up code
     * */


    /**
     * Determine the data type of the items returned by the expression.
     *
     * @param th the type hierarchy cache
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */
    @Override
    public ItemType getItemType(TypeHierarchy th) {
        return returnClause.getItemType(th);
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     *         {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     *         {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */
    @Override
    protected int computeCardinality() {
        // Assume that simple cases, like a FLWOR whose clauses are all "let" clauses, will have been converted into something else.
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the immediate sub-expressions of this expression.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterator<Expression> iterateSubExpressions() {
        final List<Expression> list = new ArrayList<>(5);
        ExpressionProcessor processor = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) {
                list.add(expr);
                return expr;
            }
        };
        try {
            for (Clause c : clauses) {
                c.processSubExpressions(processor);
            }
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
        list.add(returnClause);
        return list.iterator();
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        final List<SubExpressionInfo> list = new ArrayList<>(5);
        ExpressionProcessor processor = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) {
                list.add(new SubExpressionInfo(expr, true, false, NAVIGATION_CONTEXT));
                return expr;
            }
        };
        boolean foundLoopClause = false;
        try {
            for (Clause c : clauses) {
                if (isLoopingClause(c)) {
                    foundLoopClause = true;
                }
                c.processSubExpressions(processor);
            }
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
        list.add(new SubExpressionInfo(returnClause, true, foundLoopClause, INHERITED_CONTEXT));
        return list.iterator();

    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */
    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        ExpressionProcessor processor = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) throws XPathException {
                expr.checkForUpdatingSubexpressions();
                if (expr.isUpdatingExpression()) {
					throw new XPathException("An updating expression cannot be used in a clause of a FLWOR expression",
						"XUST0001");
                }
                return expr;
            }
        };
        for (Clause c : clauses) {
            c.processSubExpressions(processor);
        }
        returnClause.checkForUpdatingSubexpressions();
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */
    @Override
    public boolean isUpdatingExpression() {
        return returnClause.isUpdatingExpression();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     *
     * @param original    the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */
    @Override
    public boolean replaceSubExpression(final Expression original, final Expression replacement) {
        final List<Boolean> changed = new ArrayList<>();
        ExpressionProcessor processor = new ExpressionProcessor() {
            public Expression processExpression(Expression expr) {
                if (expr == original) {
                    changed.add(Boolean.TRUE);
                    return replacement;
                }
                return expr;
            }
        };
        try {
            for (Clause c : clauses) {
                c.processSubExpressions(processor);
            }
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
        if (returnClause == original) {
            returnClause = replacement;
            return true;
        }
        return !changed.isEmpty();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("FLWOR");
        for (Clause c : clauses) {
            c.explain(out);
        }
        out.startSubsidiaryElement("return");
        returnClause.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */
    @Override
    public Expression copy() {
        List<Clause> newClauses = new ArrayList<>();
        List<LocalVariableBinding> oldBindings = new ArrayList<>();
        List<LocalVariableBinding> newBindings = new ArrayList<>();
        for (Clause c : clauses) {
            Clause c2 = c.copy();
            oldBindings.addAll(Arrays.asList(c.getRangeVariables()));
            newBindings.addAll(Arrays.asList(c2.getRangeVariables()));
            newClauses.add(c2);
        }
        FLWORExpression f2 = new FLWORExpression(newClauses, returnClause.copy());
        ExpressionTool.copyLocationInfo(this, f2);
        for (int i = 0; i < oldBindings.size(); i++) {
            ExpressionTool.rebindVariableReferences(f2, oldBindings.get(i), newBindings.get(i));
        }
        return f2;
    }

    /**
     * Promote this expression if possible
     */
    @Override
    public Expression promote(final PromotionOffer offer, final Expression parent) throws XPathException {
		ExpressionProcessor processor = new ExpressionProcessor() {

			public Expression processExpression(Expression expr) throws XPathException {
				return doPromotion(expr, offer);
			}
		};
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
		} else if (offer.action == PromotionOffer.RANGE_INDEPENDENT ||
			offer.action == PromotionOffer.FOCUS_INDEPENDENT) {
            // Pass the offer to the action expression only if the action isn't dependent on a
            // variable bound within the FLWOR expression
            Binding[] savedBindingList = offer.bindingList;
            for (Clause c : clauses) {
                offer.bindingList = extendBindingList(offer.bindingList, c.getRangeVariables());
                c.processSubExpressions(processor);
            }
            offer.bindingList = savedBindingList;
            return this;
        } else {

            try {
                for (Clause c : clauses) {
                    c.processSubExpressions(processor);
                }
            } catch (XPathException e) {
                throw new IllegalStateException(e);
            }
            returnClause.promote(offer, this);
            return this;
        }
    }

	private Binding[] extendBindingList(Binding[] bindings, LocalVariableBinding[] moreBindings) {
		if (bindings == null) {
			bindings = new Binding[0];
		}
		if (moreBindings == null || moreBindings.length == 0) {
			return bindings;
		} else {
			Binding[] b2 = new Binding[bindings.length + moreBindings.length];
			System.arraycopy(bindings, 0, b2, 0, bindings.length);
			System.arraycopy(moreBindings, 0, b2, bindings.length, moreBindings.length);
			return b2;
		}
	}

    @Override
    public int getEvaluationMethod() {
        return Expression.PROCESS_METHOD;
    }

    @Override
	public Expression optimize(final ExpressionVisitor visitor, final ExpressionVisitor.ContextItemType contextItemType)
		throws XPathException {

        // Optimize all the subexpressions
		for (Clause c : clauses) {
			c.processSubExpressions(new ExpressionProcessor() {

				public Expression processExpression(Expression expr) throws XPathException {
					return visitor.optimize(expr, contextItemType);
				}
			});
			c.optimize(visitor, contextItemType);
		}

        // Optimize the return expression
        returnClause = returnClause.optimize(visitor, contextItemType);

        // If any 'let' clause declares a variable that is used only once, then inline it. If the variable
        // is not used at all, then eliminate it

        boolean tryAgain;
        boolean changed = false;
        do {
            tryAgain = false;
            for (Clause c : clauses) {
                if (c.getClauseKey() == Clause.LET) {
					LetClause lc = (LetClause) c;
                    if (!ExpressionTool.dependsOnVariable(this, new Binding[]{lc.getRangeVariable()})) {
                        clauses.remove(c);
                        tryAgain = true;
                        break;
                    }
                    boolean suppressInlining = false;
                    for (Clause c2 : clauses) {
                        if (c2.containsNonInlineableVariableReference(lc.getRangeVariable())) {
                            suppressInlining = true;
                            break;
                        }
                    }
					if (!suppressInlining) {
						if (lc.getRangeVariable().getNominalReferenceCount() == 1 ||
							lc.getSequence() instanceof VariableReference || lc.getSequence() instanceof Literal) {
							ExpressionTool.replaceVariableReferences(this, lc.getRangeVariable(),
								lc.getSequence().copy());
							clauses.remove(c);
							if (clauses.isEmpty()) {
								return returnClause;
							}
							tryAgain = true;
							break;
						}
					}
                }
            }
            changed |= tryAgain;
        } while (tryAgain);
        
        // If changed, remove any redundant trace clauses
		for (int i = clauses.size() - 1; i >= 1; i--) {
			if (clauses.get(i).getClauseKey() == Clause.TRACE && clauses.get(i - 1).getClauseKey() == Clause.TRACE) {
				clauses.remove(i);
			}
		}

        // If any 'where' clause depends on the context item, remove this dependency, because it makes
        // it easier to rearrange where clauses as predicates
        boolean depends = false;
        for (Clause w : clauses) {
            if (w instanceof WhereClause && ExpressionTool.dependsOnFocus(((WhereClause) w).getPredicate())) {
                depends = true;
                break;
            }
        }
        if (depends) {
            Expression expr1 = ExpressionTool.tryToFactorOutDot(this, contextItemType.itemType);
            if (expr1 == null || expr1 == this) {
                //no optimisation possible
                return this;
            }
            resetLocalStaticProperties();
            return expr1.optimize(visitor, contextItemType);
        }

        // Now convert any terms within WHERE clauses where possible into predicates on the appropriate
        // expression bound to a variable on a for clause. This enables the resulting filter expression
        // to be handled using indexing (in Saxon-EE), and it also reduces the number of items that need
        // to be tested against the predicate

        Expression expr2 = rewriteWhereClause(visitor, contextItemType);
        if (expr2 != null && expr2 != this) {
            return expr2.optimize(visitor, contextItemType);
        }

        // If the FLWOR expression consists entirely of FOR and LET clauses, convert it to a ForExpression
        // or LetExpression. This is largely to take advantage of existing optimizations implemented for those
        // expressions.

        boolean allForOrLetExpr = true;
        for (Clause c : clauses) {
            if (!((c instanceof ForClause) || (c instanceof LetClause))) {
                allForOrLetExpr = false;
                break;
            }
        }

        if (allForOrLetExpr) {
            return rewriteForOrLet(visitor, contextItemType);
        }

        return this;
    }

    /**
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item
     * @return We return this expression, with WhereClauses moved up as far as possible in the list of clauses.
     *         A Where clause cannot move above a Count clause because it changes the number of tuples in the tuple stream.
     *         Alternatively, return null if no rewriting is possible.
     * @throws XPathException if the rewrite fails for any reason
     */
    private Expression rewriteWhereClause(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType)
            throws XPathException {
        WhereClause whereClause;
        int whereIndex = 0;
        class WhereClauseStruct {
            int whereIndex = 0;
            WhereClause whereClause;
        }
        List<WhereClauseStruct> whereList = new ArrayList<>();

        for (Clause c : clauses) {
            if (c instanceof WhereClause) {
                WhereClauseStruct wStruct = new WhereClauseStruct();
                wStruct.whereClause = (WhereClause) c;

                //keep track of whereclause from the end of the list of clauses.
                //We are always attempting to rewrite whereclauses from left to right,
                // therefore index will always be in snyc
                wStruct.whereIndex = clauses.size() - whereIndex;
                whereList.add(wStruct);
            }
            whereIndex++;
        }

        if (whereList.isEmpty()) {
            return null;
        }

        while (!whereList.isEmpty()) {
            whereClause = whereList.get(0).whereClause;
            whereIndex = whereList.get(0).whereIndex;
            Expression condition = whereClause.getPredicate();
            List<Expression> list = new ArrayList<>(5);
            BooleanExpression.listAndComponents(condition, list);
            for (int i = list.size() - 1; i >= 0; i--) {
                Expression term = list.get(i);
                for (int c = clauses.size() - whereIndex - 1; c >= 0; c--) {
                    Clause clause = clauses.get(c);
                    Binding[] bindingList = clause.getRangeVariables();

                    // Find the first clause prior to the where clause that declares variables on which the
                    // term of the where clause depends

                    if (ExpressionTool.dependsOnVariable(term, bindingList) || clause.getClauseKey() == Clause.COUNT) {
                        // remove this term from the where clause
                        Expression removedExpr = list.remove(i);
                        if (list.isEmpty()) {
                            // the where clause has no terms left, so remove the clause
                            clauses.remove(clauses.size() - whereIndex);
                        } else {
                            // change the predicate of the where clause to use only those terms that remain
                            whereClause.setPredicate(makeAndCondition(list));
                        }
                        if ((clause instanceof ForClause) && !(((ForClause) clause).isAllowingEmpty())) {
                            // if the clause is a "for" clause, try to add the term as a predicate
                            boolean added = ((ForClause) clause).addPredicate(this, visitor, contextItemType, term);
                            //If we cannot add the WhereClause term as a predicate then put it back into the list of clauses
                            if (!added) {
                                clauses.add(c + 1, new WhereClause(removedExpr));
                            }
                        } else {
                            // the clause is not a "for" clause, so just move the "where" to this place in the list of clauses
                            WhereClause newWhere = new WhereClause(term);
                            clauses.add(c + 1, newWhere);
                        }
                        // we found a variable on which the term depends so we can't move it any further
                        break;
                    }
                }
                if (list.size() - 1 == i) {
                    list.remove(i);
                    if (list.isEmpty()) {
                        clauses.remove(clauses.size() - whereIndex);
                    } else {
                        whereClause.setPredicate(makeAndCondition(list));
                    }
                    WhereClause newWhere = new WhereClause(term);
                    clauses.add(0, newWhere);
                }
            }

            whereList.remove(0);
        }
        return this;
    }

    /**
     * Recursive method to make a list of expressions into a AndExpression
     *
     * @param list of Expression
     * @return And Expression of list of expressions
     */
    private Expression makeAndCondition(List<Expression> list) {
        if (list.size() == 1) {
            return list.get(0);
        } else {
            return new AndExpression(list.get(0), makeAndCondition(list.subList(1, list.size())));
        }
    }

    /**
     * Rewrite a FLWOR expression that consists entirely of "for" and "let" clauses as
     * a LetExpression or ForExpression
     *
     * @param visitor         - ExpressionVisitor
     * @param contextItemType -  ExpressionVisitor.ContextItemTyp
     * @return the rewritten expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */
	private Expression rewriteForOrLet(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType)
		throws XPathException {
        Expression action = returnClause;
		CodeInjector injector = null;
		if (visitor.getStaticContext() instanceof QueryModule) {
			injector = ((QueryModule) visitor.getStaticContext()).getCodeInjector();
		}

        for (int i = clauses.size() - 1; i >= 0; i--) {

            if (clauses.get(i) instanceof ForClause) {
                ForClause forClause = (ForClause) clauses.get(i);
                ForExpression forExpr;
                if (forClause.isAllowingEmpty()) {
                    forExpr = (ForExpression) visitor.getConfiguration().makeOuterForExpression();
                } else {
                    forExpr = new ForExpression();
                }

                forExpr.setLocationId(forClause.getLocationId());
                forExpr.setAction(action);

                forExpr.setSequence(forClause.getSequence());
                forExpr.setVariableQName(forClause.getRangeVariable().getVariableQName());
                forExpr.setRequiredType(forClause.getRangeVariable().getRequiredType());
                ExpressionTool.rebindVariableReferences(action, forClause.getRangeVariable(), forExpr);
                if (forClause.getPositionVariable() != null) {
                    PositionVariable posVar = new PositionVariable();
                    posVar.setVariableQName(forClause.getPositionVariable().getVariableQName());
                    ExpressionTool.rebindVariableReferences(action, forClause.getPositionVariable(), posVar);
                    forExpr.setPositionVariable(posVar);
                }
                action = forExpr;

				if (injector != null) {
					action = injector.inject(action, visitor.getStaticContext(), Location.FOR_EXPRESSION,
						forExpr.getVariableQName());
				}
            } else {
                LetClause letClause = (LetClause) clauses.get(i);
                LetExpression letExpr = new LetExpression();
                letExpr.setLocationId(letClause.getLocationId());
                letExpr.setAction(action);
                letExpr.setSequence(letClause.getSequence());
                letExpr.setVariableQName(letClause.getRangeVariable().getVariableQName());
                letExpr.setRequiredType(letClause.getRangeVariable().getRequiredType());
                letExpr.setRefCount(letClause.getRangeVariable().getNominalReferenceCount());
                ExpressionTool.rebindVariableReferences(action, letClause.getRangeVariable(), letExpr);
                action = letExpr;

				if (injector != null) {
					action = injector.inject(action, visitor.getStaticContext(), Location.LET_EXPRESSION,
						letExpr.getVariableQName());
				}
            }

        }
        action = action.optimize(visitor, contextItemType);
        return action;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */
    @Override
    public SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
        //force push mode if there is a window clause that is not the first clause
        // TODO: this is a temporary solution (pre 9.4 release) because WindowPull fails to call pullTuple
        // on the previous clause in the FLWOR expression
        for (int i = 1; i < clauses.size(); i++) {
            if (clauses.get(i).getClauseKey() == Clause.WINDOW) {
                Controller controller = context.getController();
                SequenceReceiver saved = context.getReceiver();
                SequenceOutputter seq = controller.allocateSequenceOutputter(20);
                seq.getPipelineConfiguration().setHostLanguage(getHostLanguage());
                context.setReceiver(seq);
                process(context);
                context.setReceiver(saved);
                seq.close();
                return seq.iterate();
            }
        }

        TuplePull stream = new SingularityPull();
        for (Clause c : clauses) {
            stream = c.getPullStream(stream, context);
        }
        return new ReturnClauseIterator(stream, this, context);
    }


    /**
     * Process the instruction, without returning any tail calls
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void process(XPathContext context) throws XPathException {
        TuplePush destination = new ReturnClausePush(returnClause);
        for (int i = clauses.size() - 1; i >= 0; i--) {
            Clause c = clauses.get(i);
            destination = c.getPushStream(destination, context);
        }
        destination.processTuple(context);
        destination.close();
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     * @throws net.sf.saxon.trans.XPathException
     *                                       if evaluation fails
     * @throws UnsupportedOperationException if the expression is not an updating expression
     */

    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        TuplePull stream = new SingularityPull();
        for (Clause c : clauses) {
            stream = c.getPullStream(stream, context);
        }
        while (stream.nextTuple(context)) {
            returnClause.evaluatePendingUpdates(context, pul);
        }
    }

    /**
     * Display the expression as a string
     */
    @Override
    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        for (Clause c : clauses) {
            sb.append(c.toString());
            sb.append(' ');
        }
        sb.append(" return ");
        sb.append(returnClause.toString());
        return sb.toString();
    }

    /**
     * Determine whether a variable reference found within a clause of a FLWOR expression is a looping
     * reference, that is, whether the variable is used more than once
     * @param binding the variable binding, which may be bound in a clause of the same FLWOR expression,
     * or in some containing expression
     * @return true if a reference to the variable occurs within a loop relative to the binding, that is, if the
     * variable's value is used more than once. Note that this method only detects a loop that is due to the clauses
     * of this FLWOR expression itself. A loop in an inner expression or outer expression of the FLWOR expression must
     * be detected by the caller.
     */
    public boolean hasLoopingVariableReference(final Binding binding) {

        // Determine the clause that binds the variable (if any)

        int bindingClause = -1;
		for (int i = 0; i < clauses.size(); i++) {
			if (clauseHasBinding(clauses.get(i), binding)) {
				bindingClause = i;
				break;
			}
		}

        boolean boundOutside = bindingClause < 0;
        if (boundOutside) {
            bindingClause = 0;
        }

        // Determine the last clause that contains a reference to the variable.
        // (If any reference to the variable is a looping reference, then the last one will be)

        int lastReferencingClause = clauses.size(); // indicates the return clause
        if (!ExpressionTool.dependsOnVariable(returnClause, new Binding[]{binding})) {
            // artifice to get a response value from the generic processExpression() method
            final List<Boolean> response = new ArrayList<>();
			ExpressionProcessor checker = new ExpressionProcessor() {

				public Expression processExpression(Expression expr) throws XPathException {
					if (response.isEmpty() && ExpressionTool.dependsOnVariable(expr, new Binding[]{binding})) {
						response.add(Boolean.TRUE);
					}
					return expr;
				}
			};
			for (int i = clauses.size() - 1; i >= 0; i--) {
				try {
					clauses.get(i).processSubExpressions(checker);
					if (!response.isEmpty()) {
						lastReferencingClause = i;
						break;
					}
				} catch (XPathException e) {
					LOGGER.error("Could not determine whether a variable reference found within a clause of a FLWOR " +
						"expression is a looping reference", e);
					assert false;
				}
			}
		}

        // If any clause between the binding clause and the last referencing clause is a looping clause,
        // then the variable is used within a loop

		for (int i = lastReferencingClause - 1; i >= bindingClause; i--) {
			if (isLoopingClause(clauses.get(i))) {
				return true;
			}
		}

        // otherwise there is no loop caused by the clauses of the FLWOR expression itself.

        return false;
    }
}
