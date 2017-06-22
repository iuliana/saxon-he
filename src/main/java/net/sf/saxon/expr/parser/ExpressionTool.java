////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.sort.ConditionalSorter;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.Current;
import net.sf.saxon.functions.EscapeURI;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.PatternSponsor;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.SourceLocator;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.sf.saxon.functions.Sum.SUM_EX_CODE;
import static net.sf.saxon.util.ExceptionBuilder.newEx;

/**
 * This class, ExpressionTool, contains a number of useful static methods
 * for manipulating expressions. Most importantly, it provides the factory
 * method make() for constructing a new expression
 */

public class ExpressionTool {

    public static final int UNDECIDED = -1;
    public static final int NO_EVALUATION_NEEDED = 0;
    public static final int EVALUATE_VARIABLE = 1;
    public static final int MAKE_CLOSURE = 3;
    public static final int MAKE_MEMO_CLOSURE = 4;
    public static final int RETURN_EMPTY_SEQUENCE = 5;
    public static final int EVALUATE_AND_MATERIALIZE_VARIABLE = 6;
    public static final int CALL_EVALUATE_ITEM = 7;
    public static final int ITERATE_AND_MATERIALIZE = 8;
    public static final int PROCESS = 9;
    public static final int LAZY_TAIL_EXPRESSION = 10;
    public static final int SHARED_APPEND_EXPRESSION = 11;
    public static final int MAKE_INDEXED_VARIABLE = 12;
    public static final int MAKE_SINGLETON_CLOSURE = 13;
    public static final int EVALUATE_SUPPLIED_PARAMETER = 14;

    private ExpressionTool() {
    }

    /**
     * Parse an XPath expression. This performs the basic analysis of the expression against the
     * grammar, it binds variable references and function calls to variable definitions and
     * function definitions, and it performs context-independent expression rewriting for
     * optimization purposes.
     *
     * @param expression   The expression (as a character string)
     * @param env          An object giving information about the compile-time
     *                     context of the expression
     * @param container    The expression's container
     * @param start        position of the first significant character in the expression
     * @param terminator   The token that marks the end of this expression; typically
     *                     Token.EOF, but may for example be a right curly brace
     * @param lineNumber   the line number of the start of the expression
     * @param codeInjector true  allows injection of tracing, debugging, or performance monitoring code; null if
     *                     not required
     * @return an object of type Expression
     * @throws XPathException if the expression contains a static error
     */

    /*@NotNull*/
    public static Expression make(/*@NotNull*/ String expression, StaticContext env,
                                  Container container, int start, int terminator, int lineNumber,
                                  /*@Nullable*/ CodeInjector codeInjector) throws XPathException {
        ExpressionParser parser = env.getConfiguration().newExpressionParser("XP", false, env.getXPathLanguageLevel());
        parser.setDefaultContainer(container);
        if (codeInjector != null) {
            parser.setCodeInjector(codeInjector);
        }
        if (terminator == -1) {
            terminator = Token.EOF;
        }
        Expression exp = parser.parse(expression, start, terminator, lineNumber, env);
        exp = ExpressionVisitor.make(env, exp.getExecutable()).simplify(exp);
        return exp;
    }

    /**
     * Copy location information (the line number and reference to the container) from one expression
     * to another
     *
     * @param from the expression containing the location information
     * @param to   the expression to which the information is to be copied
     */

    public static void copyLocationInfo(Expression from, Expression to) {
        if (from != null && to != null) {
            if(to.getLocationId() == -1) {
                to.setLocationId(from.getLocationId());
            }
            to.setContainer(from.getContainer());
        }
    }

    /**
     * Remove unwanted sorting from an expression, at compile time
     *
     * @param opt            the expression optimizer
     * @param exp            the expression to be optimized
     * @param retainAllNodes true if there is a need to retain exactly those nodes returned by exp
     *                       even if there are duplicates; false if the caller doesn't mind whether duplicate nodes
     *                       are retained or eliminated
     * @return the expression after rewriting
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is found while doing the rewrite
     */

    public static Expression unsorted(Optimizer opt, Expression exp, boolean retainAllNodes)
            throws XPathException {
        if (exp instanceof Literal) {
            return exp;   // fast exit
        }
        PromotionOffer offer = new PromotionOffer(opt);
        offer.action = PromotionOffer.UNORDERED;
        offer.retainAllNodes = retainAllNodes;
        return exp.promote(offer, null);
    }

    /**
     * Remove unwanted sorting from an expression, at compile time, if and only if it is known
     * that the result of the expression will be homogeneous (all nodes, or all atomic values).
     * This is done when we need the effective boolean value of a sequence: the EBV of a
     * homogenous sequence does not depend on its order, but this is not true when atomic
     * values and nodes are mixed: (N, AV) is true, but (AV, N) is an error.
     *
     * @param opt the expression optimizer
     * @param exp the expression to be optimized
     * @return the expression after rewriting
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is found while doing the rewrite
     */

    public static Expression unsortedIfHomogeneous(Optimizer opt, Expression exp)
            throws XPathException {
        if (exp instanceof Literal) {
            return exp;   // fast exit
        }
        if (exp.getItemType(opt.getConfiguration().getTypeHierarchy()) instanceof AnyItemType) {
            return exp;
        } else {
            PromotionOffer offer = new PromotionOffer(opt);
            offer.action = PromotionOffer.UNORDERED;
            offer.retainAllNodes = false;
            return exp.promote(offer, null);
        }
    }

    /**
     * Determine the method of evaluation to be used when lazy evaluation of an expression is
     * preferred. This method is called at compile time, after all optimizations have been done,
     * to determine the preferred strategy for lazy evaluation, depending on the type of expression.
     *
     * @param exp the expression to be evaluated
     * @return an integer constant identifying the evaluation mode
     */

    public static int lazyEvaluationMode(Expression exp) {
        if (exp instanceof Literal) {
            return NO_EVALUATION_NEEDED;

        } else if (exp instanceof VariableReference) {
            return EVALUATE_VARIABLE;

        } else if (exp instanceof SuppliedParameterReference) {
            return EVALUATE_SUPPLIED_PARAMETER;

        } else if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_POSITION |
                        StaticProperty.DEPENDS_ON_LAST |
                        StaticProperty.DEPENDS_ON_CURRENT_ITEM |
                        StaticProperty.DEPENDS_ON_CURRENT_GROUP |
                        StaticProperty.DEPENDS_ON_REGEX_GROUP)) != 0) {
            // we can't save these values in the closure, so we evaluate
            // the expression now if they are needed
            return eagerEvaluationMode(exp);

        } else if (exp instanceof ErrorExpression) {
            return CALL_EVALUATE_ITEM;
            // evaluateItem() on an error expression throws the latent exception

        } else if (!Cardinality.allowsMany(exp.getCardinality())) {
            // singleton expressions are always evaluated eagerly
            return eagerEvaluationMode(exp);

        } else if (exp instanceof TailExpression) {
            // Treat tail recursion as a special case, to avoid creating a deeply-nested
            // tree of Closures. If this expression is a TailExpression, and its first
            // argument is also a TailExpression, we combine the two TailExpressions into
            // one and return a closure over that.
            TailExpression tail = (TailExpression) exp;
            Expression base = tail.getBaseExpression();
            if (base instanceof VariableReference) {
                return LAZY_TAIL_EXPRESSION;
            } else {
                return MAKE_CLOSURE;
            }

        } else if (exp instanceof Block && ((Block) exp).isCandidateForSharedAppend()) {
            // If the expression is a Block, that is, it is appending a value to a sequence,
            // then we have the opportunity to use a shared list underpinning the old value and
            // the new. This takes precedence over lazy evaluation (it would be possible to do this
            // lazily, but more difficult). We currently do this for any Block that has a variable
            // reference as one of its subexpressions. The most common case is that the first argument is a reference
            // to an argument of recursive function, where the recursive function returns the result of
            // appending to the sequence.
            return SHARED_APPEND_EXPRESSION;

        } else {
            // create a Closure, a wrapper for the expression and its context
            return MAKE_CLOSURE;
        }
    }

    /**
     * Determine the method of evaluation to be used when lazy evaluation of an expression is
     * preferred. This method is called at compile time, after all optimizations have been done,
     * to determine the preferred strategy for lazy evaluation, depending on the type of expression.
     *
     * @param exp the expression to be evaluated
     * @return an integer constant identifying the evaluation mode
     */

    public static int eagerEvaluationMode(Expression exp) {
        if (exp instanceof Literal && !(((Literal) exp).getValue() instanceof Closure)) {
            return NO_EVALUATION_NEEDED;
        }
        if (exp instanceof VariableReference) {
            return EVALUATE_AND_MATERIALIZE_VARIABLE;
        }
        int m = exp.getImplementationMethod();
        if ((m & Expression.EVALUATE_METHOD) != 0) {
            return CALL_EVALUATE_ITEM;
        } else if ((m & Expression.ITERATE_METHOD) != 0) {
            return ITERATE_AND_MATERIALIZE;
        } else {
            return PROCESS;
        }
    }


    /**
     * Do lazy evaluation of an expression. This will return a value, which may optionally
     * be a SequenceIntent, which is a wrapper around an iterator over the value of the expression.
     *
     * @param exp            the expression to be evaluated
     * @param evaluationMode the evaluation mode for this expression
     * @param context        the run-time evaluation context for the expression. If
     *                       the expression is not evaluated immediately, then parts of the
     *                       context on which the expression depends need to be saved as part of
     *                       the Closure
     * @param ref            an indication of how the value will be used. The value 1 indicates that the value
     *                       is only expected to be used once, so that there is no need to keep it in memory. A small value >1
     *                       indicates multiple references, so the value will be saved when first evaluated. The special value
     *                       FILTERED indicates a reference within a loop of the form $x[predicate], indicating that the value
     *                       should be saved in a way that permits indexing.
     * @return a value: either the actual value obtained by evaluating the
     *         expression, or a Closure containing all the information needed to
     *         evaluate it later
     * @throws XPathException if any error occurs in evaluating the
     *                        expression
     */

    public static Sequence evaluate(Expression exp, int evaluationMode, XPathContext context, int ref)
            throws XPathException {
        switch (evaluationMode) {

            case NO_EVALUATION_NEEDED:
                return ((Literal) exp).getValue();

            case EVALUATE_VARIABLE:
                return ((VariableReference) exp).evaluateVariable(context);

            case EVALUATE_SUPPLIED_PARAMETER:
                return ((SuppliedParameterReference) exp).evaluateVariable(context);

            case MAKE_CLOSURE:
                return Closure.make(exp, context, ref);
            //return new SequenceExtent(exp.iterate(context));

            case MAKE_MEMO_CLOSURE:
                return Closure.make(exp, context, (ref == 1 ? 10 : ref));

            case MAKE_SINGLETON_CLOSURE:
                return new SingletonClosure(exp, context);

            case RETURN_EMPTY_SEQUENCE:
                return EmptySequence.getInstance();

            case EVALUATE_AND_MATERIALIZE_VARIABLE:
                Sequence v = ((VariableReference) exp).evaluateVariable(context);
                if (v instanceof Closure) {
                    return SequenceExtent.makeSequenceExtent(v.iterate());
                } else {
                    return v;
                }

            case CALL_EVALUATE_ITEM:
                Item item = exp.evaluateItem(context);
                if (item == null) {
                    return EmptySequence.getInstance();
                } else {
                    return item;
                }

            case UNDECIDED:
            case ITERATE_AND_MATERIALIZE:
                if (ref == FilterExpression.FILTERED) {
                    return context.getConfiguration().makeSequenceExtent(exp, ref, context);
                } else {
                    return SequenceExtent.makeSequenceExtent(exp.iterate(context));
                }

            case PROCESS:
                Controller controller = context.getController();
                SequenceReceiver saved = context.getReceiver();
                SequenceOutputter seq = controller.allocateSequenceOutputter(20);
                seq.getPipelineConfiguration().setHostLanguage(exp.getHostLanguage());
                context.setReceiver(seq);
                seq.open();
                exp.process(context);
                seq.close();
                context.setReceiver(saved);
                Sequence val = seq.getSequence();
                seq.reset();
                return val;

            case LAZY_TAIL_EXPRESSION: {
                TailExpression tail = (TailExpression) exp;
                VariableReference vr = (VariableReference) tail.getBaseExpression();
                Sequence base = evaluate(vr, EVALUATE_VARIABLE, context, ref);
                if (base instanceof MemoClosure) {
                    SequenceIterator it = base.iterate();
                    base = SequenceTool.toGroundedValue(it);
                }
                if (base instanceof IntegerRange) {
                    long start = ((IntegerRange) base).getStart() + tail.getStart() - 1;
                    long end = ((IntegerRange) base).getEnd();
                    if (start == end) {
                        return Int64Value.makeIntegerValue(end);
                    } else if (start > end) {
                        return EmptySequence.getInstance();
                    } else {
                        return new IntegerRange(start, end);
                    }
                }
                if (base instanceof SequenceExtent) {
                    return new SequenceExtent(
                            (SequenceExtent) base,
                            tail.getStart() - 1,
                            ((SequenceExtent) base).getLength() - tail.getStart() + 1);
                }

                return Closure.make(tail, context, ref);
            }

            case SHARED_APPEND_EXPRESSION: {
                if (exp instanceof Block) {
                    Block block = (Block) exp;
                    Expression[] children = block.getChildren();
                    List<GroundedValue> subsequences = new ArrayList<GroundedValue>(children.length);
                    for (Expression child : children) {
                        if (Cardinality.allowsMany(child.getCardinality())) {
                            subsequences.add(SequenceTool.toGroundedValue(child.iterate(context)));
                        } else {
                            Item j = child.evaluateItem(context);
                            if (j != null) {
                                subsequences.add(j instanceof GroundedValue ? ((GroundedValue)j) : new SingletonItem(j));
                            }
                        }
                    }
                    return new Chain(subsequences);
                } else {
                    // it's not a Block: it must have been rewritten after deciding to use this evaluation mode
                    return SequenceExtent.makeSequenceExtent(exp.iterate(context));
                }
            }

            case MAKE_INDEXED_VARIABLE:
                return context.getConfiguration().obtainOptimizer().makeIndexedValue(exp.iterate(context));

            default:
                throw new IllegalArgumentException("Unknown evaluation mode " + evaluationMode);

        }
    }

    /**
     * Do lazy evaluation of an expression. This will return a value, which may optionally
     * be a SequenceIntent, which is a wrapper around an iterator over the value of the expression.
     *
     * @param exp     the expression to be evaluated
     * @param context the run-time evaluation context for the expression. If
     *                the expression is not evaluated immediately, then parts of the
     *                context on which the expression depends need to be saved as part of
     *                the Closure
     * @param ref     an indication of how the value will be used. The value 1 indicates that the value
     *                is only expected to be used once, so that there is no need to keep it in memory. A small value >1
     *                indicates multiple references, so the value will be saved when first evaluated. The special value
     *                FILTERED indicates a reference within a loop of the form $x[predicate], indicating that the value
     *                should be saved in a way that permits indexing.
     * @return a value: either the actual value obtained by evaluating the
     *         expression, or a Closure containing all the information needed to
     *         evaluate it later
     * @throws XPathException if any error occurs in evaluating the
     *                        expression
     */

    public static Sequence lazyEvaluate(Expression exp, XPathContext context, int ref) throws XPathException {
        final int evaluationMode = lazyEvaluationMode(exp);
        return evaluate(exp, evaluationMode, context, ref);
    }

    /**
     * Evaluate an expression now; lazy evaluation is not permitted in this case
     *
     * @param exp     the expression to be evaluated
     * @param context the run-time evaluation context
     * @return the result of evaluating the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public static Sequence eagerEvaluate(Expression exp, XPathContext context) throws XPathException {
        final int evaluationMode = eagerEvaluationMode(exp);
        return evaluate(exp, evaluationMode, context, 10);
    }

    /**
     * Scan an expression to find and mark any recursive tail function calls
     *
     * @param exp   the expression to be analyzed
     * @param qName the name of the containing function
     * @param arity the arity of the containing function
     * @return 0 if no tail call was found; 1 if a tail call to a different function was found;
     *         2 if a tail call to the specified function was found. In this case the
     *         UserFunctionCall object representing the tail function call will also have been marked as
     *         a tail call.
     */

    public static int markTailFunctionCalls(Expression exp, StructuredQName qName, int arity) {
        return exp.markTailFunctionCalls(qName, arity);
    }

    /**
     * Construct indent string, for diagnostic output
     *
     * @param level the indentation level (the number of spaces to return)
     * @return a string of "level*2" spaces
     */

    public static String indent(int level) {
        FastStringBuffer fsb = new FastStringBuffer(level);
        for (int i = 0; i < level; i++) {
            fsb.append("  ");
        }
        return fsb.toString();
    }

    /**
     * Determine whether an expression contains a LocalParamSetter subexpression
     *
     * @param exp the expression to be tested
     */

    public static boolean containsLocalParam(Expression exp) {
        return contains(exp, true, e -> e instanceof LocalParamSetter);

    }

    public static boolean containsGroupVariableReference(Expression exp) {
        return contains(exp, false, e-> e instanceof GroupVariableReference);
    }

    /**
     * Test whether a given expression is, or contains, at any depth, an expression that satisfies a supplied
     * condition
     *
     * @param exp the given expression
     * @param sameFocusOnly if true, only subexpressions evaluated in the same focus are searched. If false,
     * all subexpressions are searched
     * @param predicate the condition to be satisfied
     * @return true if the given expression is or contains an expression that satisfies the condition.
     */

    public static boolean contains(Expression exp, boolean sameFocusOnly, ExpressionPredicate predicate) {
        if (predicate.matches(exp)) {
            return true;
        }
        for (Iterator<SubExpressionInfo> iter = exp.iterateSubExpressionInfo(); iter.hasNext(); ) {
            SubExpressionInfo info = iter.next();
            if ((info.hasSameFocus || !sameFocusOnly) && contains(info.expression, sameFocusOnly, predicate)) {
                return true;
            }
        }
        return false;
    }

    public static interface ExpressionPredicate {
        public boolean matches(Expression e);
    }

    /**
     * Allocate slot numbers to range variables
     *
     * @param exp      the expression whose range variables need to have slot numbers assigned
     * @param nextFree the next slot number that is available for allocation
     * @param frame    a SlotManager object that is used to track the mapping of slot numbers
     *                 to variable names for debugging purposes. May be null.
     * @return the next unallocated slot number.
     */

    public static int allocateSlots(Expression exp, int nextFree, SlotManager frame) {
        if (exp instanceof Assignation) {
            ((Assignation) exp).setSlotNumber(nextFree);
            int count = ((Assignation) exp).getRequiredSlots();
            nextFree += count;
            if (frame != null) {
                frame.allocateSlotNumber(((Assignation) exp).getVariableQName());
                if (count == 2) {
                    frame.allocateSlotNumber(((ForExpression) exp).getPositionVariableName());
                }
            }
        }
        if (exp instanceof ForEachGroup) {
            ForEachGroup feg = (ForEachGroup) exp;
            if (feg.getGroupBinding() != null) {
                feg.getGroupBinding().setSlotNumber(nextFree++);
            }
            if (feg.getKeyBinding() != null) {
                feg.getKeyBinding().setSlotNumber(nextFree++);
            }
        }
        if (exp instanceof FLWORExpression) {
            for (Clause c : ((FLWORExpression) exp).getClauseList()) {
                for (LocalVariableBinding b : c.getRangeVariables()) {
                    b.setSlotNumber(nextFree++);
                    frame.allocateSlotNumber(b.getVariableQName());
                }
            }
        }
        if (exp instanceof VariableReference) {
            VariableReference var = (VariableReference) exp;
            Binding binding = var.getBinding();
            if (exp instanceof LocalVariableReference) {
                ((LocalVariableReference) var).setSlotNumber(binding.getLocalSlotNumber());
            }
            if (binding instanceof Assignation && binding.getLocalSlotNumber() < 0) {
                // This indicates something badly wrong: we've found a variable reference on the tree, that's
                // bound to a variable declaration that is no longer on the tree. All we can do is print diagnostics.
                // The most common reason for this failure is that the declaration of the variable was removed
                // from the tree in the mistaken belief that there were no references to the variable. Variable
                // references are counted during the typeCheck phase, so this can happen if typeCheck() fails to
                // visit some branch of the expression tree.
                Assignation decl = (Assignation) binding;
                String msg = "*** Internal Saxon error: local variable encountered whose binding has been deleted";
                System.err.println(msg);
                System.err.println("Variable name: " + decl.getVariableName());
                System.err.println("Line number of reference: " + var.getLineNumber() + " in " + var.getSystemId());
                System.err.println("Line number of declaration: " + decl.getLineNumber() + " in " + decl.getSystemId());
                System.err.println("DECLARATION:");
                try {
                    decl.explain(System.err);
                } catch (Exception err) {
                    // ignore the secondary error
                }
                throw new IllegalStateException(msg);
            }

        }

        if (exp instanceof PatternSponsor) {
            nextFree = ((PatternSponsor) exp).getPattern().allocateSlots(frame, nextFree);
        } else {
            for (Iterator children = exp.iterateSubExpressions(); children.hasNext(); ) {
                Expression child = (Expression) children.next();
                nextFree = allocateSlots(child, nextFree, frame);
            }
        }

        return nextFree;

        // Note, we allocate a distinct slot to each range variable, even if the
        // scopes don't overlap. This isn't strictly necessary, but might help
        // debugging.
    }

    /**
     * Determine the effective boolean value of a sequence, given an iterator over the sequence
     *
     * @param iterator An iterator over the sequence whose effective boolean value is required
     * @return the effective boolean value
     * @throws XPathException if a dynamic error occurs
     */
    public static boolean effectiveBooleanValue(SequenceIterator<? extends Item> iterator) throws XPathException {
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof NodeInfo) {
            iterator.close();
            return true;
        } else if (first instanceof AtomicValue) {
            if (first instanceof BooleanValue) {
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a boolean");
                }
                return ((BooleanValue) first).getBooleanValue();
            } else if (first instanceof StringValue) {   // includes anyURI value
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a string");
                }
                return (!((StringValue) first).isZeroLength());
            } else if (first instanceof NumericValue) {
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a numeric value");
                }
                final NumericValue n = (NumericValue) first;
                return (n.compareTo(0) != 0) && !n.isNaN();
            } else {
                ebvError("a sequence starting with an atomic value other than a boolean, number, string, or URI");
                return false;
            }
        } else if (first instanceof FunctionItem) {
            ebvError("a sequence starting with a function item");
            return false;
        } else if (first instanceof ObjectValue) {
            if (iterator.next() != null) {
                ebvError("a sequence of two or more items starting with an external object value");
            }
            return true;
        }
        ebvError("a sequence starting with an item of unknown kind");
        return false;
    }

    /**
     * Determine the effective boolean value of a single item
     *
     * @param item the item whose effective boolean value is required
     * @return the effective boolean value
     * @throws XPathException if a dynamic error occurs
     */
    public static boolean effectiveBooleanValue(Item item) throws XPathException {
        if (item == null) {
            return false;
        }
        if (item instanceof NodeInfo) {
            return true;
        } else {
            if (item instanceof BooleanValue) {
                return ((BooleanValue) item).getBooleanValue();
            } else if (item instanceof StringValue) {   // includes anyURI value
                return !((StringValue) item).isZeroLength();
            } else if (item instanceof NumericValue) {
                final NumericValue n = (NumericValue) item;
                return (n.compareTo(0) != 0) && !n.isNaN();
            } else if (item instanceof ObjectValue) {
                // return true if external object reference is not null
                return (((ObjectValue) item).getObject() != null);
            } else {
                ebvError("an item other than a boolean, number, string, or URI");
                return false;
            }
        }
    }

    /**
     * Report an error in computing the effective boolean value of an expression
     *
     * @param reason the nature of the error
     * @throws XPathException
     */

    public static void ebvError(String reason) throws XPathException {
        throw newEx("Effective boolean value is not defined for " + reason, true, SUM_EX_CODE);
    }

    /**
     * Ask whether an expression has a dependency on the focus
     *
     * @param exp the expression
     * @return true if the value of the expression depends on the context item, position, or size
     */

    public static boolean dependsOnFocus(Expression exp) {
        return ((exp.getDependencies() & StaticProperty.DEPENDS_ON_FOCUS) != 0);
    }

    /**
     * Determine whether an expression depends on any one of a set of variables
     *
     * @param exp the expression being tested
     * @param bindingList the set of variables being tested
     * @return true if the expression depends on one of the given variables
     */

    public static boolean dependsOnVariable(Expression exp, final Binding[] bindingList) {
        return !(bindingList == null || bindingList.length == 0) &&
                contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                if (e instanceof VariableReference) {
                    for (Binding binding : bindingList) {
                        if (((VariableReference) e).getBinding() == binding) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        //        if (e instanceof VariableReference) {
//            for (Binding binding : bindingList) {
//                if (((VariableReference) e).getBinding() == binding) {
//                    return true;
//                }
//            }
//            return false;
////        } else if ((e.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) == 0) {
////            return false;
//        } else {
//            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
//                Expression child = (Expression) children.next();
//                if (dependsOnVariable(child, bindingList)) {
//                    return true;
//                }
//            }
//            return false;
//        }
    }

    /**
     * Gather a list of all the variable bindings on which a given expression depends
     *
     * @param e    the expression being tested
     * @param list a list to which the bindings are to be added. The items in this list must
     *             implement {@link Binding}
     */

    public static void gatherReferencedVariables(Expression e, List<Binding> list) {
        if (e instanceof VariableReference) {
            Binding binding = ((VariableReference) e).getBinding();
            if (!list.contains(binding)) {
                list.add(binding);
            }
        } else {
            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
                Expression child = (Expression) children.next();
                gatherReferencedVariables(child, list);
            }
        }
    }

    /**
     * Determine whether the expression contains any variable references or calls to user-written functions
     *
     * @param exp the expression being tested
     * @return true if the expression includes a variable reference or function call, or an XSLT construct
     *         equivalent to a function call (e.g call-template). Also returns true if the expression includes
     *         a variable binding element, as (a) this is likely to mean it also contains a reference, and (b)
     *         it also needs to be caught on the same paths.
     */

    public static boolean refersToVariableOrFunction(Expression exp) {
        return contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return (e instanceof VariableReference || e instanceof UserFunctionCall || e instanceof Binding
                        || e instanceof CallTemplate || e instanceof ApplyTemplates
                        || e instanceof ApplyImports);
            }
        });
//        if (e instanceof VariableReference || e instanceof UserFunctionCall || e instanceof Binding
//                || e instanceof CallTemplate || e instanceof ApplyTemplates || e instanceof ApplyImports || e instanceof NextMatch) {
//            return true;
//        } else {
//            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
//                Expression child = (Expression) children.next();
//                if (refersToVariableOrFunction(child)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    /**
     * Determine whether an expression contains a call on the function with a given fingerprint
     *
     * @param exp   The expression being tested
     * @param qName The name of the function
     * @return true if the expression contains a call on the function
     */

    public static boolean callsFunction(Expression exp, final StructuredQName qName) {
        return contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return e instanceof FunctionCall && (((FunctionCall) e).getFunctionName().equals(qName));
            }
        });
//        if (exp instanceof FunctionCall && (((FunctionCall) exp).getFunctionName().equals(qName))) {
//            return true;
//        }
//        Iterator iter = exp.iterateSubExpressions();
//        while (iter.hasNext()) {
//            Expression e = (Expression) iter.next();
//            if (callsFunction(e, qName)) {
//                return true;
//            }
//        }
//        return false;
    }

    /**
     * Gather a list of all the user-defined functions which a given expression calls directly
     *
     * @param e    the expression being tested
     * @param list a list of the functions that are called. The items in this list must
     *             be objects of class {@link UserFunction}
     */

    public static void gatherCalledFunctions(Expression e, List<UserFunction> list) {
        if (e instanceof UserFunctionCall) {
            UserFunction function = ((UserFunctionCall) e).getFunction();
            if (!list.contains(function)) {
                list.add(function);
            }
        } else {
            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
                Expression child = (Expression) children.next();
                gatherCalledFunctions(child, list);
            }
        }
    }

    /**
     * Gather a list of the names of the user-defined functions which a given expression calls directly
     *
     * @param e    the expression being tested
     * @param list a list of the functions that are called. The items in this list are strings in the format
     *             "{uri}local/arity"
     */

    public static void gatherCalledFunctionNames(Expression e, List list) {
        if (e instanceof UserFunctionCall) {
            StructuredQName name = ((UserFunctionCall) e).getFunctionName();
            int arity = ((UserFunctionCall) e).getNumberOfArguments();
            String key = name.getClarkName() + "/" + arity;
            if (!list.contains(key)) {
                list.add(key);
            }
        } else {
            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
                Expression child = (Expression) children.next();
                gatherCalledFunctionNames(child, list);
            }
        }
    }


    /**
     * Reset cached static properties within a subtree, meaning that they have to be
     * recalulated next time they are required
     *
     * @param exp the root of the subtree within which static properties should be reset
     */

    public static void resetPropertiesWithinSubtree(Expression exp) {
        exp.resetLocalStaticProperties();
        for (Iterator children = exp.iterateSubExpressions(); children.hasNext(); ) {
            Expression child = (Expression) children.next();
            resetPropertiesWithinSubtree(child);
        }
    }

    /**
     * Resolve calls to the XSLT current() function within an expression
     *
     * @param exp    the expression within which calls to current() should be resolved
     * @param config the Saxon configuration
     * @return the expression after resolving calls to current()
     * @throws XPathException if a static error is detected
     */

    public static Expression resolveCallsToCurrentFunction(Expression exp, Configuration config)
            throws XPathException {
        if (callsFunction(exp, Current.FN_CURRENT)) {
            LetExpression let = new LetExpression();
            let.setVariableQName(
                    new StructuredQName("saxon", NamespaceConstant.SAXON, "current" + exp.hashCode()));
            let.setRequiredType(SequenceType.SINGLE_ITEM);
            let.setSequence(new CurrentItemExpression());
            PromotionOffer offer = new PromotionOffer(config.obtainOptimizer());
            offer.action = PromotionOffer.REPLACE_CURRENT;
            offer.containingExpression = let;
            exp = exp.promote(offer, null);
            let.setAction(exp);
            return let;
        } else {
            return exp;
        }
    }

    /**
     * Get a list of all references to a particular variable within a subtree
     *
     * @param exp     the expression at the root of the subtree
     * @param binding the variable binding whose references are sought
     * @param list    a list to be populated with the references to this variable
     */

    public static void gatherVariableReferences(Expression exp, Binding binding, List<VariableReference> list) {
        if (exp instanceof VariableReference &&
                ((VariableReference) exp).getBinding() == binding) {
            list.add((VariableReference) exp);
        } else {
            for (Iterator iter = exp.iterateSubExpressions(); iter.hasNext(); ) {
                gatherVariableReferences((Expression) iter.next(), binding, list);
            }
        }
    }

    /**
     * Callback for selecting expressions in the tree
     */

    public interface ExpressionSelector {
        boolean matches(Expression exp);
    }

    /**
     * Replace all selected subexpressions within a subtree
     *
     * @param exp         the expression at the root of the subtree
     * @param selector    callback to determine whether a subexpression is selected
     * @param replacement the expression to be used in place of the variable reference
     * @return true if the expression has been changed, at any level
     */

    public static boolean replaceSelectedSubexpressions(Expression exp, ExpressionSelector selector, Expression replacement) {
        boolean found = false;
        for (Iterator<Expression> iter = exp.iterateSubExpressions(); iter.hasNext(); ) {
            Expression child = iter.next();
            if (selector.matches(child)) {
                found |= exp.replaceSubExpression(child, replacement);
            } else {
                found |= replaceSelectedSubexpressions(child, selector, replacement);
            }
        }
        if (found) {
            exp.resetLocalStaticProperties();
        }
        return found;
    }


    /**
     * Replace all references to a particular variable within a subtree
     *
     * @param exp         the expression at the root of the subtree
     * @param binding     the variable binding whose references are sought
     * @param replacement the expression to be used in place of the variable reference
     * @return true if the expression has been changed, at any level
     */

    public static boolean replaceVariableReferences(Expression exp, final Binding binding, Expression replacement) {
        ExpressionSelector selector = new ExpressionSelector() {
            public boolean matches(Expression child) {
                return child instanceof VariableReference && ((VariableReference) child).getBinding() == binding;
            }
        };
        return replaceSelectedSubexpressions(exp, selector, replacement);
//        boolean found = false;
//        for (Iterator<Expression> iter = exp.iterateSubExpressions(); iter.hasNext(); ) {
//            Expression child = iter.next();
//            if (child instanceof VariableReference && ((VariableReference) child).getBinding() == binding) {
//                found |= exp.replaceSubExpression(child, replacement);
//            } else {
//                found |= replaceVariableReferences(child, binding, replacement);
//            }
//        }
//        if (found) {
//            exp.resetLocalStaticProperties();
//        }
//        return found;
    }

    /**
     * Determine how often a variable is referenced. This is the number of times
     * it is referenced at run-time: so a reference in a loop counts as "many". This code
     * currently handles local variables (Let expressions) and function parameters. It is
     * not currently used for XSLT template parameters. It's not the end of the world if
     * the answer is wrong (unless it's wrongly given as zero), but if wrongly returned as
     * 1 then the variable will be repeatedly evaluated.
     *
     * @param exp     the expression within which variable references are to be counted
     * @param binding identifies the variable of interest
     * @param inLoop  true if the expression is within a loop, in which case a reference counts as many.
     *                This should be set to false on the initial call, it may be set to true on an internal recursive
     *                call
     * @return the number of references. The interesting values are 0, 1,  "many" (represented
     *         by any value >1), and the special value FILTERED, which indicates that there are
     *         multiple references and one or more of them is of the form $x[....] indicating that an
     *         index might be useful.
     */

    public static int getReferenceCount(Expression exp, Binding binding, boolean inLoop) {
        int rcount = 0;
        if (exp instanceof VariableReference && ((VariableReference) exp).getBinding() == binding) {
            if (((VariableReference) exp).isFiltered()) {
                return FilterExpression.FILTERED;
            } else {
                rcount += (inLoop ? 10 : 1);
            }
        } else if ((exp.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) == 0) {
            return 0;
        } else {
            for (Iterator<SubExpressionInfo> iter = exp.iterateSubExpressionInfo(); iter.hasNext(); ) {
                SubExpressionInfo info = iter.next();
                Expression child = info.expression;
                boolean childLoop = inLoop || info.isEvaluatedRepeatedly;
                rcount += getReferenceCount(child, binding, childLoop);
                if (rcount >= FilterExpression.FILTERED) {
                    break;
                }
            }
        }
        return rcount;
    }

    /**
     * Get the size of an expression tree (the number of subexpressions it contains)
     *
     * @param exp the expression whose size is required
     * @return the size of the expression tree, as the number of nodes
     */

    public static int expressionSize(Expression exp) {
        int total = 1;
        for (Iterator iter = exp.iterateSubExpressions(); iter.hasNext(); ) {
            total += expressionSize((Expression) iter.next());
        }
        return total;
    }


    /**
     * Rebind all variable references to a binding
     *
     * @param exp        the expression whose contained variable references are to be rebound
     * @param oldBinding the old binding for the variable references
     * @param newBinding the new binding to which the variables should be rebound
     */

    public static void rebindVariableReferences(
            Expression exp, Binding oldBinding, Binding newBinding) {
        if (exp instanceof VariableReference) {
            if (((VariableReference) exp).getBinding() == oldBinding) {
                ((VariableReference) exp).fixup(newBinding);
            }
        } else {
            Iterator iter = exp.iterateSubExpressions();
            while (iter.hasNext()) {
                Expression e = (Expression) iter.next();
                rebindVariableReferences(e, oldBinding, newBinding);
            }
        }
    }

    /**
     * Make a mapping expression. The resulting expression will include logic to check that the first operand
     * returns nodes, and that the expression as a whole is homogeneous, unless the caller requests otherwise.
     *
     * @param start              the start expression (the first operand of "/")
     * @param step               the step expression (the second operand of "/")
     * @param sortAndDeduplicate set to true if this is a path expression ("/") where the result needs to be
     *                           homogenous, and needs to be sorted and deduplicated if it consists of nodes. Set to false if the caller
     *                           knows that this is not necessary (typically because it has already been checked).
     * @return the resulting expression.
     */

    /*@NotNull*/
    public static Expression makePathExpression(
            /*@NotNull*/ Expression start, /*@NotNull*/ Expression step, boolean sortAndDeduplicate) {

//        if (sortAndDeduplicate) {
//            // if the final expression is to be sorted and deduplicated, then there is no need to apply this process
//            // to the results of subexpressions
        // code removed 2011-11-01 MHK - causes test expr36 to fail
//            start = removeSorting(start);
//            step = removeSorting(step);
//        }

        // the expression /.. is sometimes used to represent the empty node-set. Applying this simplification
        // now avoids generating warnings for this case.
        if (start instanceof RootExpression && step instanceof ParentNodeExpression) {
            return Literal.makeEmptySequence();
        }

        SlashExpression expr = new SlashExpression(start, step);

        // If start is a path expression such as a, and step is b/c, then
        // instead of a/(b/c) we construct (a/b)/c. This is because it often avoids
        // a sort.

        // The "/" operator in XPath 2.0 is not always left-associative. Problems
        // can occur if position() and last() are used on the rhs, or if node-constructors
        // appear, e.g. //b/../<d/>. So we only do this rewrite if the step is a path
        // expression in which both operands are axis expressions optionally with predicates

        if (step instanceof SlashExpression) {
            SlashExpression stepPath = (SlashExpression) step;
            if (isFilteredAxisPath(stepPath.getControllingExpression()) && isFilteredAxisPath(stepPath.getControlledExpression())) {
                expr.setStartExpression(ExpressionTool.makePathExpression(start, stepPath.getControllingExpression(), false));
                expr.setStepExpression(stepPath.getControlledExpression());
            }
        }

        if (sortAndDeduplicate) {
            // The HomogeneityChecker not only checks homogeneity, but also sorts nodes into document order
            return new HomogeneityChecker(expr);
        } else {
            return expr;
        }
    }

    /**
     * Determine whether an expression is an axis step with optional filter predicates.
     *
     * @param exp the expression to be examined
     * @return true if the supplied expression is an AxisExpression, or an AxisExpression wrapped by one
     *         or more filter expressions
     */

    private static boolean isFilteredAxisPath(Expression exp) {
        return unfilteredExpression(exp) instanceof AxisExpression;
    }

    /**
     * Get the expression that remains after removing any filter predicates
     * @param exp the expression to be examined
     * @return the expression underlying exp after removing any predicates
     */

    public static Expression unfilteredExpression(Expression exp) {
        if (exp instanceof FilterExpression) {
            return unfilteredExpression(((FilterExpression) exp).getControllingExpression());
        } else if (exp instanceof SingleItemFilter) {
            return unfilteredExpression(((SingleItemFilter) exp).getBaseExpression());
        } else {
            return exp;
        }
    }

    /**
     * Remove any wrapping expression that sorts or deduplicates a sequence, or checks its homogeneity
     *
     * @param exp the expression to be de-sorted
     * @return the expression without any sorting
     */

    private static Expression removeSorting(Expression exp) {
        if (exp instanceof DocumentSorter) {
            return ((DocumentSorter) exp).getBaseExpression();
        } else if (exp instanceof ConditionalSorter) {
            return ((ConditionalSorter) exp).getDocumentSorter().getBaseExpression();
//        } else if (exp instanceof Reverse) {
//            return ((Reverse)exp).getArguments()[0];
        } else if (exp instanceof HomogeneityChecker) {
            return ((HomogeneityChecker) exp).getBaseExpression();
        } else {
            return exp;
        }
    }


    /**
     * Try to factor out dependencies on the context item, by rewriting an expression f(.) as
     * let $dot := . return f($dot). This is not always possible, for example where f() is an extension
     * function call that uses XPathContext as an implicit argument. However, doing this increases the
     * chances of distributing a "where" condition in a FLWOR expression to the individual input sequences
     * selected by the "for" clauses.
     *
     * @param exp             the expression from which references to "." should be factored out if possible
     * @param contextItemType the static type of the context item
     * @return either the expression, after binding "." to a local variable and replacing all references to it;
     *         or null, if no changes were made.
     */

    public static Expression tryToFactorOutDot(Expression exp, ItemType contextItemType) {
        if (exp instanceof ContextItemExpression) {
            return null;
        } else if (exp instanceof LetExpression && ((LetExpression) exp).getSequence() instanceof ContextItemExpression) {
            Expression action = ((LetExpression) exp).getAction();
            boolean changed = factorOutDot(action, (LetExpression) exp);
            if (changed) {
                exp.resetLocalStaticProperties();
            }
            return exp;
        } else if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_CONTEXT_ITEM | StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT)) != 0) {
            LetExpression let = new LetExpression();
            let.setVariableQName(
                    new StructuredQName("saxon", NamespaceConstant.SAXON, "dot" + exp.hashCode()));
            let.setRequiredType(SequenceType.makeSequenceType(contextItemType, StaticProperty.EXACTLY_ONE));
            let.setSequence(new ContextItemExpression());
            let.setAction(exp);
            boolean changed = factorOutDot(exp, let);
            if (changed) {
                return let;
            } else {
                return exp;
            }
        } else {
            return null;
        }
    }

    /**
     * Replace references to the context item with references to a variable
     *
     * @param exp      the expression in which the replacement is to take place
     * @param variable the declaration of the variable
     * @return true if replacement has taken place (at any level)
     */

    public static boolean factorOutDot(Expression exp, Binding variable) {
        boolean changed = false;
        if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_CONTEXT_ITEM | StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT)) != 0) {
            Iterator<SubExpressionInfo> iter = exp.iterateSubExpressionInfo();
            while (iter.hasNext()) {
                SubExpressionInfo info = iter.next();
                if (info.hasSameFocus) {
                    Expression child = info.expression;
                    if (child instanceof ContextItemExpression) {
                        VariableReference ref = new VariableReference(variable);
                        copyLocationInfo(child, ref);
                        exp.replaceSubExpression(child, ref);
                        changed = true;
                    } else if (child instanceof ParentNodeExpression ||
                            child instanceof AxisExpression ||
                            child instanceof RootExpression) {
                        VariableReference ref = new VariableReference(variable);
                        copyLocationInfo(child, ref);
                        Expression path = ExpressionTool.makePathExpression(ref, child, false);
                        exp.replaceSubExpression(child, path);
                        changed = true;
                    } else {
                        changed |= factorOutDot(child, variable);
                    }
                }
            }
        }
        if (changed) {
            exp.resetLocalStaticProperties();
        }
        return changed;
    }

    /**
     * Determine whether the expression is either an updating expression, or an expression that is permitted
     * in a context where updating expressions are allowed
     *
     * @param exp the expression under test
     * @return true if the expression is an updating expression, or an empty sequence, or a call on error()
     */

    public static boolean isAllowedInUpdatingContext(Expression exp) {
        return exp.isUpdatingExpression() || exp.isVacuousExpression();
    }

    /**
     * Replace the Nth subexpression of an expression
     *
     * @param target      the parent expression whose subexpression is to be replaced
     * @param n           the index of the subexpression to be replaced (starting at zero)
     * @param replacement the replacement subexpression
     */

    public static void replaceNthSubexpression(Expression target, int n, Expression replacement) {
        int i = 0;
        boolean found;
        for (Iterator<Expression> iter = target.iterateSubExpressions(); iter.hasNext(); ) {
            Expression sub = iter.next();
            if (i++ == n) {
                found = target.replaceSubExpression(sub, replacement);
                if (found) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Failed to replace subexpression of " + target.getClass() +
                " with " + replacement.toString());

    }


    public static String getCurrentDirectory() {
        String dir;
        try {
            dir = System.getProperty("user.dir");
        } catch (Exception geterr) {
            // this doesn't work when running an applet
            return null;
        }
        if (!(dir.endsWith("/"))) {
            dir = dir + '/';
        }

        URI currentDirectoryURL = new File(dir).toURI();
        return currentDirectoryURL.toString();
    }

    /**
     * Determine the base URI of an expression, so that it can be saved on the expression tree for use
     * when the expression is evaluated
     *
     * @param env     the static context
     * @param locator location of the expression for error messages
     * @param fail    if true, the method throws an exception when there is no absolute base URI; otherwise, the
     *                method returns null
     * @return the absolute base URI of the expression
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    public static URI getBaseURI(StaticContext env, SourceLocator locator, boolean fail) throws XPathException {
        URI expressionBaseURI = null;
        String base = null;
        try {
            base = env.getBaseURI();
            if (base == null) {
                base = getCurrentDirectory();
            }
            if (base != null) {
                expressionBaseURI = new URI(base);
            }
        } catch (URISyntaxException e) {
            // perhaps escaping special characters will fix the problem

            String esc = EscapeURI.iriToUri(base).toString();
            try {
                expressionBaseURI = new URI(esc);
            } catch (URISyntaxException e2) {
                // don't fail unless the base URI is actually needed (it usually isn't)
                expressionBaseURI = null;
            }

            if (expressionBaseURI == null && fail) {
                XPathException err = new XPathException("The base URI " + Err.wrap(env.getBaseURI(), Err.URI) +
                        " is not a valid URI");
                err.setLocator(locator);
                throw err;
            }
        }
        return expressionBaseURI;
    }

    /**
     * Display an expression adding parentheses if it is possible they are necessary
     * because the expression has sub-expressions
     */

    public static String parenthesize(Expression exp) {
        if (exp.iterateSubExpressions().hasNext()) {
            return "(" + exp.toString() + ")";
        } else {
            return exp.toString();
        }
    }
}

