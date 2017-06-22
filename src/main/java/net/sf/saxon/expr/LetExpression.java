////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.TypeCheckerEnvironment;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A LetExpression is modelled on the XQuery syntax let $x := expr return expr. This syntax
 * is not available in the surface XPath language, but it is used internally in an optimized
 * expression tree.
 */
public class LetExpression extends Assignation implements TailCallReturner {

    // This integer holds an approximation to the number of times that the declared variable is referenced.
    // The value 1 means there is only one reference and it is not in a loop, which means that the value will
    // not be retained in memory. If there are multiple references or references within a loop, the value will
    // be a small integer > 1. The special value FILTERED indicates that there is a reference within a loop
    // in the form $x[predicate], which indicates that the value should potentially be indexable.  The initial
    // value 2 is for safety; if a LetExpression is optimized without first being typechecked (which happens
    // in the case of optimizer-created variables) then this ensures that no damaging rewrites are done.

    private int evaluationMode = ExpressionTool.UNDECIDED;

    /**
     * Create a LetExpression
     */
    public LetExpression() {
        //System.err.println("let");
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "let";
    }

    public void setRefCount(int refCount){
        this.refCount = refCount;
    }

    /**
     * Type-check the expression. This also has the side-effect of counting the number of references
     * to the variable (treating references that occur within a loop specially)
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        // The order of events is critical here. First we ensure that the type of the
        // sequence expression is established. This is used to establish the type of the variable,
        // which in turn is required when type-checking the action part.

        sequence = visitor.typeCheck(sequence, contextItemType);

        RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, getVariableQName(), 0);
        sequence = TypeChecker.strictTypeCheck(sequence, requiredType, role, visitor.getStaticContext());
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        final ItemType actualItemType = sequence.getItemType(th);

        refineTypeInformation(actualItemType, sequence.getCardinality(),
                (sequence instanceof Literal ? ((Literal) sequence).getValue() : null),
                sequence.getSpecialProperties(), visitor, this);

        boolean indexed = (refCount == FilterExpression.FILTERED);
        refCount = 0;
        action = visitor.typeCheck(action, contextItemType);
        if (indexed) {
            refCount = FilterExpression.FILTERED;
        }
        return this;
    }


    /**
     * Determine whether this expression implements its own method for static type checking
     *
     * @return true - this expression has a non-trivial implementation of the staticTypeCheck()
     *         method
     */
    public boolean implementsStaticTypeCheck() {
        return true;
    }

    /**
     * Static type checking for let expressions is delegated to the expression itself,
     * and is performed on the "action" expression, to allow further delegation to the branches
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
	public Expression staticTypeCheck(SequenceType req, boolean backwardsCompatible, RoleLocator role,
		TypeCheckerEnvironment visitor) throws XPathException {
		action = TypeChecker.staticTypeCheck(action, req, backwardsCompatible, role, visitor);
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
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        Optimizer opt = visitor.getConfiguration().obtainOptimizer();

        // if this is a construct of the form "let $j := EXP return $j" replace it with EXP
        // Remarkably, people do write this, and it can also be produced by previous rewrites
        // Note that type checks will already have been added to the sequence expression

        if (action instanceof VariableReference && ((VariableReference) action).getBinding() == this) {
            Expression e2 = visitor.optimize(sequence, contextItemType);
            opt.trace("Eliminated trivial variable " + getVariableName(), e2);
            return e2;
        }

        /**
         * Unless this has already been done, find and count the references to this variable
         */

        // if this is an XSLT construct of the form <xsl:variable>text</xsl:variable>, try to replace
        // it by <xsl:variable select=""/>. This can be done if all the references to the variable use
        // its value as a string (rather than, say, as a node or as a boolean)
		if (sequence instanceof DocumentInstr && ((DocumentInstr) sequence).isTextOnly() &&
			allReferencesAreFlattened()) {
			sequence = ((DocumentInstr) sequence).getStringValueExpression();
			requiredType = SequenceType.SINGLE_UNTYPED_ATOMIC;
			adoptChildExpression(sequence);
			refineTypeInformation(requiredType.getPrimaryType(), requiredType.getCardinality(), null, 0, visitor, this);
		}

        // refCount is initialized during the typeCheck() phase
        if (refCount == 0) {
            // variable is not used - no need to evaluate it
            Expression a = visitor.optimize(action, contextItemType);
            ExpressionTool.copyLocationInfo(this, a);
            opt.trace("Eliminated unused variable " + getVariableName(), a);
            return a;
        }

        // Don't inline context-dependent variables in a streamable template. See strmode011.
        // The reason for this is that a variable <xsl:variable><xsl:copy-of select="."/></xsl:variable>
        // can be evaluated in streaming mode, but an arbitrary expression using copy() inline can't (e.g.
        // if it appears in a path expression or as an operand of an arithmetic expression)

        if (refCount == 1 && ExpressionTool.dependsOnFocus(sequence)) {
			Container container = getContainer();
			if (container instanceof Template && ((Template) container).isDeclaredStreamable() &&
				sequence instanceof DocumentInstr) {
				refCount = 5;
			}
        }

        if (refCount == 1 || sequence instanceof Literal) {
            // Either there's only one reference, and it's not in a loop.
            // Or the variable is bound to a constant value.
            // In these two cases we can inline the reference.
            // That is, we replace "let $x := SEQ return f($x)" by "f(SEQ)".
            // Note, we rely on the fact that any context-changing expression is treated as a loop,
            // and generates a refCount greater than one.
            replaceVariable(opt, sequence);
            Expression e2 = visitor.optimize(action, contextItemType);
            opt.trace("Inlined local variable " + getVariableName(), e2);
            return e2;
        }

        int tries = 0;
        while (tries++ < 5) {
            Expression seq2 = visitor.optimize(sequence, contextItemType);
            if (seq2 == sequence) {
                break;
            }
            sequence = seq2;
            adoptChildExpression(sequence);
            visitor.resetStaticProperties();
        }

        tries = 0;
        while (tries++ < 5) {
            Expression act2 = visitor.optimize(action, contextItemType);
            if (act2 == action) {
                break;
            }
            action = act2;
            adoptChildExpression(action);
            visitor.resetStaticProperties();
        }

		evaluationMode = (isIndexedVariable()
			? ExpressionTool.MAKE_CLOSURE
			: ExpressionTool.lazyEvaluationMode(sequence));
        return this;
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */
    @Override
    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (sequence == original) {
            sequence = replacement;
            setEvaluationMode(ExpressionTool.eagerEvaluationMode(sequence));
            found = true;
        }
        if (action == original) {
            action = replacement;
            found = true;
        }
        return found;
    }

    /**
     * Determine whether all references to this variable are using the value either
     * (a) by atomizing it, or (b) by taking its string value. (This excludes usages
     * such as testing the existence of a node or taking the effective boolean value).
     * @return true if all references are known to atomize (or stringify) the value,
     * false otherwise. The value false may indicate "not known".
     */
    private boolean allReferencesAreFlattened() {
		List references = new ArrayList();
		ExpressionTool.gatherVariableReferences(action, this, references);
		for (int i = references.size() - 1; i >= 0; i--) {
			BindingReference bref = (BindingReference) references.get(i);
			if (bref instanceof VariableReference) {
				VariableReference ref = (VariableReference) bref;
				if (ref.isFlattened()) {
					// OK, it's a string context
				} else {
					return false;
				}
			} else {
				// it must be saxon:assign
				return false;
			}
		}
		return true;
	}

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     * @return true if this expression is vacuous
     */
    @Override
    public boolean isVacuousExpression() {
        return action.isVacuousExpression();
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */
    @Override
    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        action.checkPermittedContents(parentType, env, whole);
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return action.getIntegerBounds();
    }

    /**
     * Iterate over the result of the expression to return a sequence of items
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        return let.action.iterate(context);
    }

    /**
     * Iterate over the result of the expression to return a sequence of events
     */
    @Override
    public EventIterator iterateEvents(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        return let.action.iterateEvents(context);
    }

    /**
     * Evaluate the variable.
     * @param context the dynamic evaluation context
     * @return the result of evaluating the expression that is bound to the variable
     */
    public Sequence eval(XPathContext context) throws XPathException {
        if (evaluationMode == ExpressionTool.UNDECIDED) {
            evaluationMode = ExpressionTool.lazyEvaluationMode(sequence);
        }
        return ExpressionTool.evaluate(sequence, evaluationMode, context, refCount);
    }

    /**
     * Evaluate the expression as a singleton
     */
    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        return let.action.evaluateItem(context);
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */
    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        return let.action.effectiveBooleanValue(context);
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */
    @Override
    public void process(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        let.action.process(context);
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @param th the type hierarchy cache
     * @return one of the values Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */
    public ItemType getItemType(TypeHierarchy th) {
        return action.getItemType(th);
    }

    /**
     * Determine the static cardinality of the expression
     */
    public int computeCardinality() {
        return action.getCardinality();
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */
    @Override
    public int computeSpecialProperties() {
        int props = action.getSpecialProperties();
        int seqProps = sequence.getSpecialProperties();
        if ((seqProps & StaticProperty.NON_CREATIVE) == 0) {
            props &= ~StaticProperty.NON_CREATIVE;
        }
        return props;
    }

    /**
     * Mark tail function calls
     */
    @Override
    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        return ExpressionTool.markTailFunctionCalls(action, qName, arity);
    }

    /**
     * Promote this expression if possible
     */
    @Override
    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            // pass the offer on to the sequence expression
            Expression seq2 = doPromotion(sequence, offer);
            if (seq2 != sequence) {
                // if we've extracted a global variable, it may need to be marked indexable
                if (seq2 instanceof VariableReference) {
                    Binding b = ((VariableReference)seq2).getBinding();
                    if (b instanceof GlobalVariable) {
                        ((GlobalVariable)b).setReferenceCount(refCount < 10 ? 10 : refCount);
                    }
                }
                sequence = seq2;
            }
            if (offer.action == PromotionOffer.INLINE_VARIABLE_REFERENCES ||
                    offer.action == PromotionOffer.UNORDERED || offer.action == PromotionOffer.REPLACE_CURRENT ||
                    offer.action == PromotionOffer.EXTRACT_GLOBAL_VARIABLES) {
                action = doPromotion(action, offer);
            } else if (offer.action == PromotionOffer.RANGE_INDEPENDENT ||
                    offer.action == PromotionOffer.FOCUS_INDEPENDENT) {
                // Pass the offer to the action expression after adding the variable bound by this let expression,
                // so that a subexpression must depend on neither variable if it is to be promoted
                Binding[] savedBindingList = offer.bindingList;
                offer.bindingList = extendBindingList(offer.bindingList);
                action = doPromotion(action, offer);
                offer.bindingList = savedBindingList;
            }
            // if this results in the expression (let $x := $y return Z), replace all references to
            // to $x by references to $y in the Z part, and eliminate this LetExpression by
            // returning the action part.
            if (sequence instanceof VariableReference) {
                Binding b = ((VariableReference)sequence).getBinding();
                if (b != null && !b.isAssignable()) {
                    replaceVariable(offer.getOptimizer(), sequence);
                    // defensive programming. If someone in the tree fails to pass this request down,
                    // there will still be a reference to the variable on the tree, which will cause
                    // a crash later. So we'll check that the variable really has gone from the
                    // tree before deleting the variable binding.
                    if (ExpressionTool.dependsOnVariable(action, new Binding[]{this})) {
                        offer.getOptimizer().trace("Failed to eliminate redundant variable $" + getVariableName(), this);
                    } else {
                        return action;
                    }
                }
            }
                                 
            return this;
        }
    }


    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */
    public Expression copy() {
        LetExpression let = new LetExpression();
        let.refCount = refCount;
        let.setVariableQName(variableName);
        let.setRequiredType(requiredType);
        let.setSequence(sequence.copy());
        Expression newAction = action.copy();
        let.setAction(newAction);
        ExpressionTool.rebindVariableReferences(newAction, this, let);
        return let;
    }

    /**
     * ProcessLeavingTail: called to do the real work of this instruction.
     * The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     *         a function call or template call that is delegated to the caller, to be made after the stack has
     *         been unwound so as to save stack space.
     */
    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        if (let.action instanceof TailCallReturner) {
            return ((TailCallReturner) let.action).processLeavingTail(context);
        } else {
            let.action.process(context);
            return null;
        }
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */
    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.action instanceof LetExpression) {
                let = (LetExpression) let.action;
            } else {
                break;
            }
        }
        let.action.evaluatePendingUpdates(context, pul);
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     * @return a representation of the expression as a string
     */
    @Override
	public String toString() {
		return "let $" + getVariableEQName() + " := " + sequence.toString() + " return " +
			ExpressionTool.parenthesize(action);
	}

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */
    public void explain(ExpressionPresenter out) {
        out.startElement("let");
        out.emitAttribute("variable", getVariableEQName());
        out.emitAttribute("as", sequence.getItemType(out.getTypeHierarchy()).toString() +
                Cardinality.getOccurrenceIndicator(sequence.getCardinality()));
        if (isIndexedVariable()) {
            out.emitAttribute("indexable", "true");
        }
        out.emitAttribute("slot", Integer.toString(getLocalSlotNumber()));
        out.startSubsidiaryElement("be");
        sequence.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("return");
        action.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

	public void setEvaluationMode(int evaluationMode) {
		this.evaluationMode = evaluationMode;
	}

	public int getEvaluationMode() {
		return evaluationMode;
	}

}
