////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.evpull.EventMappingFunction;
import net.sf.saxon.evpull.EventMappingIterator;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
* A ForExpression maps an expression over a sequence.
* This version works with range variables, it doesn't change the context information
*/

public class ForExpression extends Assignation {

    /*@Nullable*/ protected PositionVariable positionVariable = null;
    int actionCardinality = StaticProperty.ALLOWS_MANY;

    /**
     * Create a "for" expression (for $x at $p in SEQUENCE return ACTION)
     */

    public ForExpression() {
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "for";
    }

    /**
     * Set the reference to the position variable (XQuery only)
     * @param decl the range variable declaration for the position variable
     */

    public void setPositionVariable (PositionVariable decl) {
        positionVariable = decl;
    }
    
    /*@Nullable*/ public PositionVariable getPositionVariable () {
        return positionVariable;
    }

    public boolean hasVariableBinding(Binding binding){
         return this == binding || positionVariable == binding;
    }

    /**
     * Get the name of the position variable
     * @return the name of the position variable ("at $p") if there is one, or null if not
     */

    /*@Nullable*/ public StructuredQName getPositionVariableName() {
        if (positionVariable == null) {
            return null;
        } else {
            return positionVariable.getVariableQName();
        }
    }

    /**
     * Set the slot number for the range variable
     * @param nr the slot number allocated to the range variable on the local stack frame.
     * This implicitly allocates the next slot number to the position variable if there is one.
    */

    public void setSlotNumber(int nr) {
        super.setSlotNumber(nr);
        if (positionVariable != null) {
            positionVariable.setSlotNumber(nr+1);
        }
    }

    /**
     * Get the number of slots required.
     * @return normally 1, except for a FOR expression with an AT clause, where it is 2.
     */

    public int getRequiredSlots() {
        return (positionVariable == null ? 1 : 2);
    }

    /**
    * Type-check the expression
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        // The order of events is critical here. First we ensure that the type of the
        // sequence expression is established. This is used to establish the type of the variable,
        // which in turn is required when type-checking the action part.

        sequence = visitor.typeCheck(sequence, contextItemType);
        if (Literal.isEmptySequence(sequence)) {
            return sequence;
        }

        if (requiredType != null) {
            // if declaration is null, we've already done the type checking in a previous pass
            final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            SequenceType decl = requiredType;
            SequenceType sequenceType = SequenceType.makeSequenceType(
                    decl.getPrimaryType(), StaticProperty.ALLOWS_ZERO_OR_MORE);
            RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, variableName, 0
            );
            //role.setSourceLocator(this);
            sequence = TypeChecker.strictTypeCheck(
                    sequence, sequenceType, role, visitor.getStaticContext());
            ItemType actualItemType = sequence.getItemType(th);
            refineTypeInformation(actualItemType,
                    getRangeVariableCardinality(),
                    null,
                    sequence.getSpecialProperties(), visitor, this);
        }

        action = visitor.typeCheck(action, contextItemType);
        if (Literal.isEmptySequence(action)) {
            return action;
        }
        actionCardinality = action.getCardinality();
        return this;
    }

    /**
     * Get the cardinality of the range variable
     * @return the cardinality of the range variable (StaticProperty.EXACTLY_ONE). Can be overridden
     * in a subclass
     */

    protected int getRangeVariableCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
    * Optimize the expression
    */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        boolean debug = opt.getConfiguration().getBooleanProperty(FeatureKeys.TRACE_OPTIMIZER_DECISIONS);

        // Try to promote any WHERE clause appearing immediately within the FOR expression

        if (Choose.isSingleBranchChoice(action)) {
            Expression act2 = visitor.optimize(action, contextItemType);
            if (act2 != action) {
                action = act2;
                adoptChildExpression(action);
                visitor.resetStaticProperties();
            }
        }

        Expression p = promoteWhereClause(positionVariable);
        if (p != null) {
            if (debug) {
                opt.trace("Promoted where clause in for $" + getVariableName(), p);
            }
            return visitor.optimize(p, contextItemType);
        }

        // See if there is a simple "where" condition that can be turned into a predicate

//        Expression pred = convertWhereToPredicate(visitor, contextItemType);
//        if (pred != null) {
//            if (debug) {
//                opt.trace("Converted where clause in for $" + getVariableName() + " to predicate", pred);
//            }
//            if (pred != this) {
//                return visitor.optimize(pred, contextItemType);
//            }
//        }

        Expression seq2 = visitor.optimize(sequence, contextItemType);
        if (seq2 != sequence) {
            sequence = seq2;
            adoptChildExpression(sequence);
            visitor.resetStaticProperties();
            return optimize(visitor, contextItemType);
        }

        if (Literal.isEmptySequence(sequence)) {
            return sequence;
        }

        Expression act2 = visitor.optimize(action, contextItemType);
        if (act2 != action) {
            action = act2;
            adoptChildExpression(action);
            visitor.resetStaticProperties();
            // it's now worth re-attempting the "where" clause optimizations
            return optimize(visitor, contextItemType);
        }

        if (Literal.isEmptySequence(action)) {
            return action;
        }

        Expression e2 = extractLoopInvariants(visitor, contextItemType);
        if (e2 != null && e2 != this) {
            if (debug) {
                opt.trace("Extracted invariant in 'for $" + getVariableName() + "' loop", e2);
            }
            return visitor.optimize(e2, contextItemType);
        }

        // Simplify an expression of the form "for $b in a/b/c return $b/d".
        // (XQuery users seem to write these a lot!)

        if (positionVariable==null &&
                sequence instanceof SlashExpression && action instanceof SlashExpression) {
            SlashExpression path2 = (SlashExpression)action;
            Expression start2 = path2.getControllingExpression();
            Expression step2 = path2.getControlledExpression();
            if (start2 instanceof VariableReference && ((VariableReference)start2).getBinding() == this &&
                    ExpressionTool.getReferenceCount(action, this, false) == 1 &&
                    ((step2.getDependencies() & (StaticProperty.DEPENDS_ON_POSITION | StaticProperty.DEPENDS_ON_LAST)) == 0)) {
                Expression newPath = new SlashExpression(sequence, path2.getControlledExpression());
                ExpressionTool.copyLocationInfo(this, newPath);
                newPath = visitor.typeCheck(visitor.simplify(newPath), contextItemType);
                if (newPath instanceof SlashExpression) {
                    // if not, it has been wrapped in a DocumentSorter or Reverser, which makes it ineligible.
                    // see test qxmp299, where this condition isn't satisfied
                    if (debug) {
                        opt.trace("Collapsed return clause of for $" + getVariableName() +
                                " into path expression", newPath);
                    }
                    return visitor.optimize(newPath, contextItemType);
                }
            }
        }

        // Simplify an expression of the form "for $x in EXPR return $x". These sometimes
        // arise as a result of previous optimization steps.

        if (action instanceof VariableReference && ((VariableReference)action).getBinding() == this) {
            if (debug) {
                opt.trace("Collapsed redundant for expression $" + getVariableName(), sequence);
            }
            return sequence;
        }

        // Rewrite an expression of the form "for $x at $p in EXPR return $p" as "1 to count(EXPR)"

        if (action instanceof VariableReference && ((VariableReference)action).getBinding() == positionVariable) {
            FunctionCall count = SystemFunctionCall.makeSystemFunction("count", new Expression[]{sequence});
            RangeExpression range = new RangeExpression(Literal.makeLiteral(Int64Value.PLUS_ONE), Token.TO, count);
            if (debug) {
                opt.trace("Replaced 'for $x at $p in EXP return $p' by '1 to count(EXP)'", range);
            }
            return range.optimize(visitor, contextItemType);
        }

        // If the cardinality of the sequence is exactly one, rewrite as a LET expression

        if (sequence.getCardinality() == StaticProperty.EXACTLY_ONE) {
            LetExpression let = new LetExpression();
            let.setVariableQName(variableName);
            let.setRequiredType(SequenceType.makeSequenceType(
                    sequence.getItemType(visitor.getConfiguration().getTypeHierarchy()),
                    StaticProperty.EXACTLY_ONE));
            let.setSequence(sequence);
            let.setAction(action);
            let.setSlotNumber(slotNumber);
            ExpressionTool.rebindVariableReferences(action, this, let);
            if (positionVariable != null) {
                LetExpression let2 =new LetExpression();
                let2.setVariableQName(positionVariable.getVariableQName());
                let2.setSequence(Literal.makeLiteral(IntegerValue.PLUS_ONE));
                let2.setAction(let);
                let2.setSlotNumber(positionVariable.getLocalSlotNumber());
                let2.setRequiredType(SequenceType.SINGLE_INTEGER);
                ExpressionTool.rebindVariableReferences(let, positionVariable, let2);
                let = let2;
            }
            return let.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
        }

        if (visitor.isOptimizeForStreaming() && positionVariable == null) {
            Expression e3 = visitor.getConfiguration().obtainOptimizer().optimizeForExpressionForStreaming(this);
            if (e3 != this) {
                return visitor.optimize(e3, contextItemType);
            }
        }

        //declaration = null;     // let the garbage collector take it
        return this;
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
    /*@Nullable*/@Override
    public IntegerValue[] getIntegerBounds() {
        return action.getIntegerBounds();
    }

    /**
     * Extract subexpressions in the action part that don't depend on the range variable
     * @param visitor the expression visitor
     * @param contextItemType the item type of the context item
     * @return the optimized expression if it has changed, or null if no optimization was possible
     */

    private Expression extractLoopInvariants(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        // Extract subexpressions that don't depend on the range variable or the position variable
        // If a subexpression is (or might be) creative, this is, if it creates new nodes, we don't
        // extract it from the loop, but we do extract its non-creative subexpressions

        //if (positionVariable == null) {
            PromotionOffer offer = new PromotionOffer(visitor.getConfiguration().obtainOptimizer());
            offer.containingExpression = this;
            offer.action = PromotionOffer.RANGE_INDEPENDENT;
            if (positionVariable == null) {
                offer.bindingList = new Binding[] {this};
            } else {
                offer.bindingList = new Binding[] {this, positionVariable};
            }
            action = doPromotion(action, offer);
            if (offer.containingExpression instanceof LetExpression) {
                // a subexpression has been promoted
                //offer.containingExpression.setParentExpression(container);
                // try again: there may be further subexpressions to promote
                offer.containingExpression = visitor.optimize(offer.containingExpression, contextItemType);
            }
            return offer.containingExpression;
        //}
        //return null;

    }

    /**
     * Promote a WHERE clause whose condition doesn't depend on the variable being bound.
     * This rewrites an expression of the form
     *
     * <p>let $i := SEQ return if (C) then R else ()</p>
     * <p>to the form:</p>
     * <p>if (C) then (let $i := SEQ return R) else ()</p>
     *
     * @param positionBinding the binding of the position variable if any
     * @return an expression in which terms from the WHERE clause that can be extracted have been extracted
     */

    /*@Nullable*/ protected Expression promoteWhereClause(/*@Nullable*/ Binding positionBinding) {
        if (Choose.isSingleBranchChoice(action)) {
            Expression condition = ((Choose)action).getConditions()[0];
            Binding[] bindingList;
            if (positionBinding == null) {
                bindingList = new Binding[] {this};
            } else {
                bindingList = new Binding[] {this, positionBinding};
            }
            List list = new ArrayList(5);
            Expression promotedCondition = null;
            BooleanExpression.listAndComponents(condition, list);
            for (int i = list.size() - 1; i >= 0; i--) {
                Expression term = (Expression) list.get(i);
                if (!ExpressionTool.dependsOnVariable(term, bindingList)) {
                    if (promotedCondition == null) {
                        promotedCondition = term;
                    } else {
                        promotedCondition = new AndExpression(term, promotedCondition);
                    }
                    list.remove(i);
                }
            }
            if (promotedCondition != null) {
                if (list.isEmpty()) {
                    // the whole if() condition has been promoted
                    Expression oldThen = ((Choose)action).getActions()[0];
                    setAction(oldThen);
                    return Choose.makeConditional(condition, this);
                } else {
                    // one or more terms of the if() condition have been promoted
                    Expression retainedCondition = (Expression) list.get(0);
                    for (int i = 1; i < list.size(); i++) {
                        retainedCondition = new AndExpression(retainedCondition, (Expression) list.get(i));
                    }
                    ((Choose)action).getConditions()[0] = retainedCondition;
                    Expression newIf = Choose.makeConditional(
                            promotedCondition, this, Literal.makeEmptySequence());
                    ExpressionTool.copyLocationInfo(this, newIf);
                    return newIf;
                }
            }
        }
        return null;
    }


    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ForExpression forExp = new ForExpression();
        forExp.setRequiredType(requiredType);
        forExp.setVariableQName(variableName);
        forExp.setSequence(sequence.copy());
        Expression newAction = action.copy();
        forExp.setAction(newAction);
        forExp.variableName = variableName;
        forExp.slotNumber = slotNumber;
        ExpressionTool.rebindVariableReferences(newAction, this, forExp);
        if (positionVariable != null) {
            PositionVariable pv2 = new PositionVariable();
            pv2.setVariableQName(positionVariable.getVariableQName());
            forExp.setPositionVariable(pv2);
            ExpressionTool.rebindVariableReferences(newAction, positionVariable, pv2);
        }
        return forExp;
    }

    /**
     * Mark tail function calls: only possible if the for expression iterates zero or one times.
     * (This arises in XSLT/XPath, which does not have a LET expression, so FOR gets used instead)
     */

    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        if (!Cardinality.allowsMany(sequence.getCardinality())) {
            return ExpressionTool.markTailFunctionCalls(action, qName, arity);
        } else {
            return UserFunctionCall.NOT_TAIL_CALL;
        }
    }

    /**
     * Extend an array of variable bindings to include the binding(s) defined in this expression
     */

    public Binding[] extendBindingList(Binding[] in) {
        if (positionVariable == null) {
            return super.extendBindingList(in);
        }
        Binding[] newBindingList = new Binding[in.length+2];
        System.arraycopy(in, 0, newBindingList, 0, in.length);
        newBindingList[in.length] = this;
        newBindingList[in.length+1] = positionVariable;
        return newBindingList;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     * @return true if this expression is vacuous
     */

    public boolean isVacuousExpression() {
        return action.isVacuousExpression();
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        action.checkPermittedContents(parentType, env, false);
    }



    /**
    * Iterate over the sequence of values
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        // First create an iteration of the base sequence.

        // Then create a MappingIterator which applies a mapping function to each
        // item in the base sequence. The mapping function is essentially the "return"
        // expression, wrapped in a MappingAction object that is responsible also for
        // setting the range variable at each step.

        SequenceIterator base = sequence.iterate(context);
        int pslot = (positionVariable == null ? -1 : positionVariable.getLocalSlotNumber());
        MappingAction map = new MappingAction(context, getLocalSlotNumber(), pslot, action);
        switch (actionCardinality) {
            case StaticProperty.EXACTLY_ONE:
                return new ItemMappingIterator(base, map, true);
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return new ItemMappingIterator(base, map, false);
            default:
                return new MappingIterator(base, map);
        }
    }

    /**
     * Deliver the result of the expression as a sequence of events.
     * @param context The dynamic evaluation context
     * @return the result of the expression as an iterator over a sequence of PullEvent objects
     * @throws XPathException if a dynamic error occurs during expression evaluation
     */
    
    /*@Nullable*/ public EventIterator iterateEvents(XPathContext context) throws XPathException {

        // First create an iteration of the base sequence.

        // Then create an EventMappingIterator which applies a mapping function to each
        // item in the base sequence. The mapping function is essentially the "return"
        // expression, wrapped in an EventMappingAction object that is responsible also for
        // setting the range variable at each step.

        SequenceIterator base = sequence.iterate(context);
        EventMappingFunction map = new EventMappingAction(context, getLocalSlotNumber(), positionVariable, action);
        return new EventMappingIterator(base, map);
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */

    public void process(XPathContext context) throws XPathException {
        SequenceIterator iter = sequence.iterate(context);
        int position = 1;
        int slot = getLocalSlotNumber();
        int pslot = -1;
        if (positionVariable != null) {
            pslot = positionVariable.getLocalSlotNumber();
        }
        while (true) {
            Item item = iter.next();
            if (item == null) break;
            context.setLocalVariable(slot, item);
            if (pslot >= 0) {
                context.setLocalVariable(pslot, Int64Value.makeIntegerValue(position++));
            }
            action.process(context);
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

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        SequenceIterator iter = sequence.iterate(context);
        int position = 1;
        int slot = getLocalSlotNumber();
        int pslot = -1;
        if (positionVariable != null) {
            pslot = positionVariable.getLocalSlotNumber();
        }
        while (true) {
            Item item = iter.next();
            if (item == null) break;
            context.setLocalVariable(slot, item);
            if (pslot >= 0) {
                context.setLocalVariable(pslot, Int64Value.makeIntegerValue(position++));
            }
            action.evaluatePendingUpdates(context, pul);
        }
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     * @return one of the values Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     * or Type.ITEM (meaning not known in advance)
     * @param th the type hierarchy cache
     */

	/*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
	    return action.getItemType(th);
	}

	/**
	* Determine the static cardinality of the expression
	*/

	public int computeCardinality() {
        int c1 = sequence.getCardinality();
        int c2 = action.getCardinality();
        return Cardinality.multiply(c1, c2);
	}

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     * @return a representation of the expression as a string
     */

    public String toString() {
        return "for $" + getVariableEQName() +
                " in " + (sequence==null ? "(...)" : sequence.toString()) +
                " return " + (action==null ? "(...)" : ExpressionTool.parenthesize(action));
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("for");
        explainSpecializedAttributes(out);
        out.emitAttribute("variable", getVariableEQName());
        out.emitAttribute("as", sequence.getItemType(out.getTypeHierarchy()).toString());
        if (positionVariable != null) {
            out.emitAttribute("at", positionVariable.getVariableQName().getEQName());
        }
        out.startSubsidiaryElement("in");
        sequence.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("return");
        action.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

    protected void explainSpecializedAttributes(ExpressionPresenter out) {
        // no action
    }

    /**
     * The MappingAction represents the action to be taken for each item in the
     * source sequence. It acts as the MappingFunction for the mapping iterator.
     */

    public static class MappingAction implements MappingFunction, ItemMappingFunction, StatefulMappingFunction {

        protected XPathContext context;
        private int slotNumber;
        private Expression action;
        private int pslot = -1;
        private int position = 1;
        
        public MappingAction() {}

        public MappingAction(XPathContext context,
                                int slotNumber,
                                int pslot,
                                Expression action) {
            this.context = context;
            this.slotNumber = slotNumber;
            this.pslot = pslot;
            this.action = action;
        }

        /*@Nullable*/ public SequenceIterator map(Item item) throws XPathException {
            context.setLocalVariable(slotNumber, item);
            if (pslot >= 0) {
                context.setLocalVariable(pslot, Int64Value.makeIntegerValue(position++));
            }
            return action.iterate(context);
        }

        /*@Nullable*/ public Item mapItem(Item item) throws XPathException {
            context.setLocalVariable(slotNumber, item);
            if (pslot >= 0) {
                context.setLocalVariable(pslot, Int64Value.makeIntegerValue(position++));
            }
            return action.evaluateItem(context);
        }

        public StatefulMappingFunction getAnother() {
            // Create a copy of the stack frame, so that changes made to local variables by the cloned
            // iterator are not seen by the original iterator
            XPathContextMajor c2 = context.newContext();
            StackFrame oldstack = context.getStackFrame();
            Sequence[] vars = oldstack.getStackFrameValues();
            Sequence[] newvars = new Sequence[vars.length];
            System.arraycopy(vars, 0, newvars, 0, vars.length);
            c2.setStackFrame(oldstack.getStackFrameMap(), newvars);
            return new MappingAction(c2, slotNumber, pslot, action);
        }
    }

    /**
     * The EventMappingAction represents the action to be taken for each item in the
     * source sequence. It acts as the EventMappingFunction for the mapping iterator, and
     * also provides the Binding of the position variable (at $n) in XQuery, if used.
     */

    protected static class EventMappingAction implements EventMappingFunction {

        private XPathContext context;
        private int slotNumber;
        private Expression action;
        private int position = 1;
        private int pslot = -1;

        public EventMappingAction(XPathContext context,
                                int slotNumber,
                                /*@Nullable*/ PositionVariable positionBinding,
                                Expression action) {
            this.context = context;
            this.slotNumber = slotNumber;
            if (positionBinding != null) {
                pslot = positionBinding.getLocalSlotNumber();
            }
            this.action = action;
        }

        /*@Nullable*/ public EventIterator map(Item item) throws XPathException {
            context.setLocalVariable(slotNumber, item);
            if (pslot >= 0) {
                context.setLocalVariable(pslot, Int64Value.makeIntegerValue(position++));
            }
            return action.iterateEvents(context);
        }

    }




    /**
     * Get the type of this expression for use in tracing and diagnostics
     * @return the type of expression, as enumerated in class {@link net.sf.saxon.trace.Location}
     */

    public int getConstructType() {
        return Location.FOR_EXPRESSION;
    }

}

