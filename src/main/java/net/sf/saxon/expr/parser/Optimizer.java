////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.Template;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.style.XSLTemplate;
import net.sf.saxon.trans.RuleTarget;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UnionType;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

/**
 * This class performs optimizations that vary between different versions of the Saxon product.
 * The optimizer is obtained from the Saxon Configuration. This class is the version used in Saxon-B,
 * which in most cases does no optimization at all: the methods are provided so that they can be
 * overridden in Saxon-EE.
 */
public class Optimizer implements Serializable {

    public static final int NO_OPTIMIZATION = 0;
    public static final int FULL_OPTIMIZATION = 10;

    /*@NotNull*/ protected Configuration config;
    private int optimizationLevel = FULL_OPTIMIZATION;
    private boolean tracing;

    /**
     * Create an Optimizer.
     * @param config the Saxon configuration
     */

    public Optimizer(Configuration config) {
        this.config = config;
        this.tracing = config.getBooleanProperty(FeatureKeys.TRACE_OPTIMIZER_DECISIONS);
    }

    /**
     * Get the Saxon configuration object
     * @return the configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the optimization level
     * @param level the optimization level, between 0 (no optimization) and 10 (full optimization).
     * Currently all values greater than zero have the same effect as full optimization
     */

    public void setOptimizationLevel(int level) {
        if (level<NO_OPTIMIZATION || level>FULL_OPTIMIZATION) {
            throw new IllegalArgumentException("Optimization level");
        }
        optimizationLevel = level;
    }

    /**
     * Get the optimization level
     * @return the optimization level, between 0 (no optimization) and 10 (full optimization).
     * Currently all values greater than zero have the same effect as full optimization
     */

    public int getOptimizationLevel() {
        return optimizationLevel;
    }

    /**
     * Simplify a GeneralComparison expression
     * @param gc the GeneralComparison to be simplified
     * @param backwardsCompatible true if in 1.0 compatibility mode
     * @return the simplified expression
     */

    public BinaryExpression optimizeGeneralComparison(GeneralComparison gc, boolean backwardsCompatible) {
        return gc;
    }

    /**
     * Attempt to optimize a copy operation. Return null if no optimization is possible.
     * @param select the expression that selects the items to be copied
     * @return null if no optimization is possible, or an expression that does an optimized
     * copy of these items otherwise
     */

    /*@Nullable*/
    public Expression optimizeCopy(Expression select) throws XPathException {
        final TypeHierarchy th = config.getTypeHierarchy();
        if (select.getItemType(th).isPlainType()) {
            return select;
        }
        return null;
    }


    /**
     * Examine a path expression to see whether it can be replaced by a call on the key() function;
     * if so, generate an appropriate key definition and return the call on key(). If not, return null.
     * @param pathExp The path expression to be converted.
     * @param visitor The expression visitor
     * @return the optimized expression, or null if no optimization is possible
     */

    public Expression convertPathExpressionToKey(SlashExpression pathExp, ExpressionVisitor visitor)
    throws XPathException {
        return null;
    }

    /**
     * Try converting a filter expression to a call on the key function. Return the supplied
     * expression unchanged if not possible
     *
     * @param f the filter expression to be converted
     * @param visitor the expression visitor, which must be currently visiting the filter expression f
     * @param indexFirstOperand true if the first operand of the filter comparison is to be indexed;
     * false if it is the second operand
     * @param contextIsDoc true if the context item is known to be a document node
     * @return the optimized expression, or the unchanged expression f if no optimization is possible
     */

    public Expression tryIndexedFilter(FilterExpression f, ExpressionVisitor visitor, boolean indexFirstOperand, boolean contextIsDoc) {
        return f;
    }

    /**
     * Convert a path expression such as a/b/c[predicate] into a filter expression
     * of the form (a/b/c)[predicate]. This is possible whenever the predicate is non-positional.
     * The conversion is useful in the case where the path expression appears inside a loop,
     * where the predicate depends on the loop variable but a/b/c does not.
     * @param pathExp the path expression to be converted
     * @param th the type hierarchy cache
     * @return the resulting filterexpression if conversion is possible, or null if not
     */

    public FilterExpression convertToFilterExpression(SlashExpression pathExp, TypeHierarchy th)
    throws XPathException {
        return null;
    }

    /**
     * Test whether a filter predicate is indexable.
     * @param filter the predicate expression
     * @return 0 if not indexable; +1 if the predicate is in the form expression=value; -1 if it is in
     * the form value=expression
     */

    public int isIndexableFilter(Expression filter) {
        return 0;
    }

    /**
     * Create an indexed value
     * @param iter the iterator that delivers the sequence of values to be indexed
     * @return the indexed value
     * @throws UnsupportedOperationException: this method should not be called in Saxon-B
     */

    public Sequence makeIndexedValue(SequenceIterator iter) throws XPathException {
        throw new UnsupportedOperationException("Indexing requires Saxon-EE");
    }

    /**
     * Determine whether it is possible to rearrange an expression so that all references to a given
     * variable are replaced by a reference to ".". This is true of there are no references to the variable
     * within a filter predicate or on the rhs of a "/" operator.
     * @param exp the expression in question
     * @param binding an array of bindings defining range variables; the method tests that there are no
     * references to any of these variables within a predicate or on the rhs of "/"
     * @return true if the variable reference can be replaced
     */

    public boolean isVariableReplaceableByDot(Expression exp, Binding[] binding) {
        // TODO: the fact that a variable reference appears inside a predicate (etc) shouldn't stop us
        // rewriting a where clause as a predicate. We just have to bind a new variable:
        // for $x in P where abc[n = $x/m] ==> for $x in P[let $z := . return abc[n = $z/m]
        // We could probably do this in all cases and then let $z be optimized away where appropriate
        if (exp instanceof ContextSwitchingExpression) {
            Expression start = ((ContextSwitchingExpression)exp).getControllingExpression();
            Expression step = ((ContextSwitchingExpression)exp).getControlledExpression();
            return isVariableReplaceableByDot(start, binding) &&
                    !ExpressionTool.dependsOnVariable(step, binding);
        } else {
            Iterator iter = exp.iterateSubExpressions();
            while (iter.hasNext()) {
                Expression sub = (Expression)iter.next();
                if (!isVariableReplaceableByDot(sub, binding)) {
                    return false;
                }
            }
            return true;
        }
    }


    /**
     * Make a conditional document sorter. This optimization is attempted
     * when a DocumentSorter is wrapped around a path expression
     * @param sorter the document sorter
     * @param path the path expression
     * @return the original sorter unchanged when no optimization is possible, which is always the
     * case in Saxon-B
     */

    public Expression makeConditionalDocumentSorter(DocumentSorter sorter, SlashExpression path) {
        return sorter;
    }

    /**
     * Replace a function call by the body of the function, assuming all conditions for inlining
     * the function are satisfied
     * @param functionCall the functionCall expression
     * @param visitor the expression visitor
     * @param contextItemType the context item type
     * @return either the original expression unchanged, or an expression that consists of the inlined
     * function body, with all function parameters bound as required. In Saxon-B, function inlining is
     * not supported, so the original functionCall is always returned unchanged
     */

    public Expression tryInlineFunctionCall(
            UserFunctionCall functionCall, ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) {
        return functionCall;
    }

    /**
     * Identify expressions within a function or template body that can be promoted to be
     * evaluated as global variables.
     * @param body the body of the template or function
     * @param visitor the expression visitor
     * @return the expression after subexpressions have been promoted to global variables; or null if
     * nothing has changed
     */
    
    public Expression promoteExpressionsToGlobal(Expression body, ExpressionVisitor visitor)
    throws XPathException {
        return null;
    }

    /**
     * Try to convert a Choose expression into a switch
     * @param choose the Choose expression
     * @param env the static context
     * @return the result of optimizing this (the original expression if no optimization was possible)
     */

    public Expression trySwitch(Choose choose, StaticContext env) {
        return choose;
    }

    /**
     * Extract subexpressions from the body of a function that can be evaluated
     * as global variables
     * @param body the body of the function
     * @param offer The PromotionOffer. Will be marked to indicate whether any action was taken
     * @return a reference to the new global variable if a variable has been created, or null if not
     */

    public Expression extractGlobalVariables(Expression body, ExpressionVisitor visitor, PromotionOffer offer)
    throws XPathException {
        return null;
    }

    /**
     * Make a streaming applyTemplates instruction. Supported in Saxon-EE only
     * @param inst the unoptimized applyTemplates instruction
     * @param reasonsForFailure a list to which diagnostic messages will be added
     * @return the expression converted for streaming
     * @throws XPathException if an error is detected
     */

    public Expression makeStreamingApplyTemplates(ApplyTemplates inst, List<String> reasonsForFailure) throws XPathException {
        return inst;
    }

    /**
     * Generate the inversion of the expression comprising the body of a template rules.
     * Supported in Saxon-EE only
     * @param pattern the match pattern of this template rule
     * @param template the template to be inverted
     * @param nodeTest the static item type of the context node of the template
     */

    public RuleTarget makeInversion(Pattern pattern, Template template, ItemType nodeTest) throws XPathException {
        return null;
    }

    /**
     * In streaming mode, make the copy operation applied to subexpressions of a complex-content
     * sequence constructor into explicit copy-of operations.
     */

    public void makeCopyOperationsExplicit(Expression parent, Expression child) throws XPathException {
        // no action unless streaming
    }

    /**
     * Check the streamability of a template
     * @param sourceTemplate the source of the template in the stylesheet tree
     * @param compiledTemplate the compiled template
     * @throws XPathException if the template is declared streamable but does not satisfy the straming rules
     */

    public void checkStreamability(XSLTemplate sourceTemplate, Template compiledTemplate) throws XPathException {
        // no action unless streaming
    }

    /**
     * Make an expression that casts to a union type. Not available in Saxon-HE
     */

    public Expression makeCastToUnion(Expression operand, SchemaType targetType, boolean allowsEmpty) {
        throw new UnsupportedOperationException("Cast to union is not supported in Saxon-HE");
    }

    /**
     * Make an expression that casts to a list type. Not available in Saxon-HE
     */

    public Expression makeCastToList(Expression operand, SchemaType targetType, boolean allowsEmpty) {
        throw new UnsupportedOperationException("Cast to List is not supported in Saxon-HE");
    }

    /**
     * Make an expression castable check to a union type. Not available in Saxon-HE
     */

    public Expression makeCastableToUnion(Expression operand, SchemaType targetType, boolean allowsEmpty) {
        throw new UnsupportedOperationException("Cast to List is not supported in Saxon-HE");
    }

    /**
     * Make an expression castable check to a union type. Not available in Saxon-HE
     */

    public Expression makeCastableToList(Expression operand, SchemaType targetType, boolean allowsEmpty) {
        throw new UnsupportedOperationException("Cast to List is not supported in Saxon-HE");
    }

    /**
     * In streaming mode, optimizer a ForExpression for streaming
     * @param expr the expression to be optimized
     * @return the optimized expression
     */

    public Expression optimizeForExpressionForStreaming(ForExpression expr) throws XPathException {
        return expr;
    }

    /**
     * In streaming mode, optimizer a QuantifiedExpression for streaming
     * @param expr the expression to be optimized
     * @return the optimized expression
     */

    public Expression optimizeQuantifiedExpressionForStreaming(QuantifiedExpression expr) throws XPathException {
        return expr;
    }

    public FunctionItem makeCastToUnion(final UnionType type, final NamespaceResolver resolver) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generate a multi-threaded version of an instruction.
     * Supported in Saxon-EE only; ignored with no action in Saxon-HE and Saxon-PE
     * @param instruction the instruction to be multi-threaded
     * @return the multi-threaded version of the instruction
     */

    public Expression generateMultithreadedInstruction(Expression instruction) {
        return instruction;
    }

    /**
     * Generate Java byte code for an expression
     *
     * @param expr the expression to be compiled
     * @param objectName the name of the object (e.g. function) being compiled
     * @param evaluationMethods The evaluation modes for which code is generated. Currently a subset of
* {@link net.sf.saxon.expr.Expression#PROCESS_METHOD}, {@link net.sf.saxon.expr.Expression#ITERATE_METHOD}. If no code is generated for
     * */

    public Expression compileToByteCode(Expression expr, String objectName, int evaluationMethods) {
        return null;
    }

    /**
     * Trace optimization actions
     * @param message the message to be displayed
     * @param exp the expression after being rewritten
     */

    public void trace(String message, Expression exp) {
        if (tracing) {
            PrintStream err = getConfiguration().getStandardErrorOutput();
            err.println("OPT ======================================");
            err.println("OPT : At line " + exp.getLineNumber() + " of " + exp.getSystemId());
            err.println("OPT : " + message);
            err.println("OPT ====== Expression after rewrite ======");
            exp.explain(err);
            err.println("\nOPT ======================================");
        }
    }

    /**
     * Trace optimization actions
     * @param message the message to be displayed
     */

    public void trace(String message) {
        if (tracing) {
            PrintStream err = getConfiguration().getStandardErrorOutput();
            err.println("OPT ======================================");
            err.println("OPT : " + message);
            err.println("OPT ======================================");
        }
    }
}

