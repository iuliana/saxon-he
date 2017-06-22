////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.TypeCheckerEnvironment;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import javax.xml.transform.SourceLocator;
import java.util.Iterator;
import java.util.Stack;

/**
 * The ExpressionVisitor supports the various phases of processing of an expression tree which require
 * a recursive walk of the tree structure visiting each node in turn. In maintains a stack holding the
 * ancestor nodes of the node currently being visited.
 */

public class ExpressionVisitor implements TypeCheckerEnvironment {

    private Stack<Expression> expressionStack;
    private Executable executable;
    private StaticContext staticContext;
    private Configuration configuration;
    private boolean optimizeForStreaming = false;

    /**
     * Create an ExpressionVisitor
     */

    public ExpressionVisitor() {
        expressionStack = new Stack<Expression>();
    }

    /**
     * Get the Saxon configuration
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Set the Saxon configuration
     *
     * @param configuration the Saxon configuration
     */


    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Get the Executable containing the expressions being visited
     *
     * @return the Executable
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the Executable containing the expressions being visited
     *
     * @param executable the Executable
     */

    public void setExecutable(Executable executable) {
        this.executable = executable;
    }

    public CollationMap getCollationMap() {
        return executable.getCollationTable();
    }

    /**
     * Get the stack containing all the expressions currently being visited
     *
     * @return the expression stack holding all the containing expressions of the current expression;
     *         the objects on this Stack are instances of {@link Expression}
     */

    public Stack<Expression> getExpressionStack() {
        return expressionStack;
    }

    /**
     * Set the stack used to hold the expressions being visited
     *
     * @param expressionStack the expression stack
     */

    public void setExpressionStack(Stack<Expression> expressionStack) {
        this.expressionStack = expressionStack;
    }

    /**
     * Get the static context for the expressions being visited. Note: this may not reflect all changes
     * in static context (e.g. namespace context, base URI) applying to nested expressions
     *
     * @return the static context
     */

    public StaticContext getStaticContext() {
        return staticContext;
    }

    /**
     * Set the static context for the expressions being visited. Note: this may not reflect all changes
     * in static context (e.g. namespace context, base URI) applying to nested expressions
     *
     * @param staticContext the static context
     */

    public void setStaticContext(StaticContext staticContext) {
        this.staticContext = staticContext;
    }

    /**
     * Get the current expression, the one being visited
     *
     * @return the current expression
     */

    public Expression getCurrentExpression() {
        return expressionStack.peek();
    }

    /**
     * Factory method: make an expression visitor
     *
     * @param env the static context
     * @param exec the executable
     * @return the new expression visitor
     */

    public static ExpressionVisitor make(StaticContext env, Executable exec) {
        ExpressionVisitor visitor = new ExpressionVisitor();
        visitor.setStaticContext(env);
        visitor.setExecutable(exec);
        visitor.setConfiguration(env.getConfiguration());
        return visitor;
    }

    /**
     * Issue a warning message
     *
     * @param message the message
     */

    public void issueWarning(String message, SourceLocator locator) {
        staticContext.issueWarning(message, locator);
    }

    /**
     * Create a dynamic context suitable for early evaluation of constant subexpressions
     */

    public XPathContext makeDynamicContext() {
        return staticContext.makeEarlyEvaluationContext();
    }

    /**
     * Simplify an expression, via the ExpressionVisitor
     *
     * @param exp the expression to be simplified. Possibly null.
     * @return the simplified expression. Returns null if and only if the supplied expression is null.
     * @throws XPathException if any error occurs
     */

    public Expression simplify(/*@Nullable*/ Expression exp) throws XPathException {
        if (exp != null) {
            expressionStack.push(exp);
            Expression exp2 = exp.simplify(this);
            if (exp2 != exp) {
                ExpressionTool.copyLocationInfo(exp, exp2);
            }
            expressionStack.pop();
            return exp2;
        } else {
            return null;
        }
    }

    /**
     * Type check an expression, via the ExpressionVisitor
     *
     * @param exp             the expression to be typechecked
     * @param contextItemType the static type of the context item for this expression. The argument can be
     *        set to null to indicate that it is known that the context item will be absent.
     * @return the expression that results from type checking (this may be wrapped in expressions that
     *         perform dynamic checking of the item type or cardinality, or that perform atomization or numeric
     *         promotion)
     * @throws XPathException if static type checking fails, that is, if the expression cannot possibly
     *                        deliver a value of the required type
     */

    public Expression typeCheck(Expression exp, /*@Nullable*/ ContextItemType contextItemType) throws XPathException {
        if (exp != null) {
            expressionStack.push(exp);
            Expression exp2;

            try {
                exp2 = exp.typeCheck(this, contextItemType);
            } catch (XPathException e) {
                if (!e.isReportableStatically()) {
                    getStaticContext().issueWarning("Evaluation will always throw a dynamic error: " + e.getMessage(), exp);
                    exp2 = new ErrorExpression(e);
                } else {
                    throw e;
                }
            }
            if (exp2 != exp) {
                ExpressionTool.copyLocationInfo(exp, exp2);
            }
            expressionStack.pop();
            return exp2;
        } else {
            return null;
        }
    }

    /**
     * Tell the visitor to optimize expressions for evaluation in a streaming environment
     *
     * @param option true if optimizing for streaming
     */

    public void setOptimizeForStreaming(boolean option) {
        optimizeForStreaming = option;
    }

    /**
     * Ask whether the visitor is to optimize expressions for evaluation in a streaming environment
     *
     * @return true if optimizing for streaming
     */

    public boolean isOptimizeForStreaming() {
        return optimizeForStreaming;
    }

    /**
     * Optimize an expression, via the ExpressionVisitor
     *
     * @param exp             the expression to be typechecked
     * @param contextItemType the static type of the context item for this expression. Passing null indicates
     * that the context item will always be absent
     * @return the rewritten expression
     * @throws XPathException if a static error is found
     */

    public Expression optimize(Expression exp,
                               /*@Nullable*/ ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (exp != null) {
            expressionStack.push(exp);
            Expression exp2 = null;

            try {
                exp2 = exp.optimize(this, contextItemType);
            } catch (XPathException e) {
                if (!e.isReportableStatically()) {
                    getStaticContext().issueWarning("Evaluation will always throw a dynamic error: " + e.getMessage(), exp);
                    exp2 = new ErrorExpression(e);
                } else {
                    throw e;
                }
            }
            if (exp2 != exp) {
                ExpressionTool.copyLocationInfo(exp, exp2);
            }
            expressionStack.pop();
            return exp2;
        } else {
            return null;
        }
    }

    /**
     * Get the parent expression of the current expression in the expression tree
     *
     * @return the parent of the current expression (or null if this is the root)
     */

    public Expression getParentExpression() {
        int pos = expressionStack.size() - 2;
        if (pos > 0) {
            return expressionStack.get(pos);
        } else {
            return null;
        }
    }

    /**
     * Return true if the current expression at the top of the visitor's stack is evaluated repeatedly
     * when a given ancestor expression is evaluated once
     *
     * @param ancestor the ancestor expression. May be null, in which case the search goes all the way
     *                 to the base of the stack.
     * @return true if the current expression is evaluated repeatedly
     */

    public boolean isLoopingSubexpression(/*@Nullable*/ Expression ancestor) {
        int top = expressionStack.size() - 1;
        while (true) {
            if (top <= 0) {
                return false;
            }
            Expression parent = expressionStack.get(top - 1);
            if (hasLoopingSubexpression(parent, (expressionStack.get(top)))) {
                return true;
            }
            if (parent == ancestor) {
                return false;
            }
            top--;
        }
    }

    public boolean isLoopingReference(Binding binding, VariableReference ref) {
        int top = expressionStack.size() - 1;
        while (true) {
            if (top <= 0) {
                // haven't found the binding on the stack, so the safe thing is to assume we're in a loop
                return true;
            }
            Expression parent = expressionStack.get(top - 1);
            if (parent instanceof FLWORExpression) {
                if (parent.hasVariableBinding(binding)) {
                    // The variable is declared in one of the clauses of the FLWOR expression
                    return ((FLWORExpression) parent).hasLoopingVariableReference(binding);
                } else {
                    // The variable is declared outside the FLWOR expression
                    if (hasLoopingSubexpression(parent, (expressionStack.get(top)))) {
                        return true;
                    }
                }
            } else {
                if (hasLoopingSubexpression(parent, (expressionStack.get(top)))) {
                    return true;
                }
                if (parent.hasVariableBinding(binding)) {
                    return false;
                }
            }
            top--;
        }
    }

    private static boolean hasLoopingSubexpression(Expression parent, Expression child) {
        for (Iterator<SubExpressionInfo> iter = parent.iterateSubExpressionInfo(); iter.hasNext();) {
            SubExpressionInfo info = iter.next();
            if (info.expression == child) {
                return info.isEvaluatedRepeatedly;
            }
        }
        return false;
    }

    /**
     * Reset the static properties for the current expression and for all its containing expressions.
     * This should be done whenever the expression is changed in a way that might
     * affect the properties. It causes the properties to be recomputed next time they are needed.
     */

    public final void resetStaticProperties() {
        for (Expression exp : expressionStack) {
            exp.resetLocalStaticProperties();
        }
    }

    /**
     * A data structure that represents the required type of the context item, together
     * with information about whether it is known to be present or absent or whether it
     * is not known statically whether it is present or absent.
     */

    public static class ContextItemType {
        public ItemType itemType;
        public boolean contextMaybeUndefined;

        public ContextItemType(ItemType itemType, boolean maybeUndefined) {
            this.itemType = itemType;
            this.contextMaybeUndefined = maybeUndefined;
        }
    }


}

