////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
* Assignation is an abstract superclass for the kinds of expression
* that declare range variables: for, some, and every.
*/

public abstract class Assignation extends Expression implements Binding {

    protected int slotNumber = -999;     // slot number for range variable
                                         // (initialized to ensure a crash if no real slot is allocated)
    protected Expression sequence;       // the expression over which the variable ranges
    protected Expression action;         // the action performed for each value of the variable
    protected StructuredQName variableName;
    protected SequenceType requiredType;
    int refCount = 2;

    //protected RangeVariable declaration;



    /**
     * Set the required type (declared type) of the variable
     * @param requiredType the required type
     */
    public void setRequiredType(SequenceType requiredType) {
        this.requiredType = requiredType;
    }

    /**
     * Set the name of the variable
     * @param variableName the name of the variable
     */

    public void setVariableQName(StructuredQName variableName) {
        this.variableName = variableName;
    }


    /**
     * Get the name of the variable
     * @return the variable name, as a QName
     */

    public StructuredQName getVariableQName() {
        return variableName;
    }

    public StructuredQName getObjectName() {
        return variableName;
    }


    /**
     * Get the declared type of the variable
     *
     * @return the declared type
     */

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     */
    public IntegerValue[] getIntegerBoundsForVariable() {
        return sequence.getIntegerBounds();
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     */

    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
    * Get the value of the range variable
    */

    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        Sequence actual = context.evaluateLocalVariable(slotNumber);
        if (!(actual instanceof GroundedValue || actual instanceof NodeInfo)) {
            actual = SequenceTool.toGroundedValue(actual);
            context.setLocalVariable(slotNumber, actual);
        }
        return actual;
    }

    /**
     * Add the "return" or "satisfies" expression, and fix up all references to the
     * range variable that occur within that expression
     * @param action the expression that occurs after the "return" keyword of a "for"
     * expression, the "satisfies" keyword of "some/every", or the ":=" operator of
     * a "let" expression.
     *
     * 
     */

    public void setAction(Expression action) {
        this.action = action;
        adoptChildExpression(action);
    }

    /**
     * Indicate whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local.
     */

    public final boolean isGlobal() {
        return false;
    }

    /**
    * Test whether it is permitted to assign to the variable using the saxon:assign
    * extension element. This will only be for an XSLT global variable where the extra
    * attribute saxon:assignable="yes" is present.
    */

    public final boolean isAssignable() {
        return false;
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    public void checkForUpdatingSubexpressions() throws XPathException {
        sequence.checkForUpdatingSubexpressions();
        if (sequence.isUpdatingExpression()) {
            XPathException err = new XPathException(
                        "An updating expression cannot be used to initialize a variable", "XUST0001");
            err.setLocator(sequence);
            throw err;
        }
        action.checkForUpdatingSubexpressions();
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     * @return true if this is an updating expression
     */

    public boolean isUpdatingExpression() {
        return action.isUpdatingExpression();
    }

    /**
     * Get the action expression
     * @return the action expression (introduced by "return" or "satisfies")
     */

    public Expression getAction() {
        return action;
    }

    /**
     * Set the "sequence" expression - the one to which the variable is bound
     * @param sequence the expression to which the variable is bound
     */

    public void setSequence(Expression sequence) {
        this.sequence = sequence;
        adoptChildExpression(sequence);
    }

    /**
     * Get the "sequence" expression - the one to which the variable is bound
     * @return the expression to which the variable is bound
     */

    public Expression getSequence() {
        return sequence;
    }

    /**
    * Set the slot number for the range variable
     * @param nr the slot number to be used
    */

    public void setSlotNumber(int nr) {
        slotNumber = nr;
    }

    /**
     * Get the number of slots required. Normally 1, except for a FOR expression with an AT clause, where it is 2.
     * @return the number of slots required
     */

    public int getRequiredSlots() {
        return 1;
    }

    /**
    * Simplify the expression
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        sequence = visitor.simplify(sequence);
        action = visitor.simplify(action);
        return this;
    }

    public boolean hasVariableBinding(Binding binding){

         return this == binding;
    }


    /**
    * Promote this expression if possible
    */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            sequence = doPromotion(sequence, offer);
            if (offer.action == PromotionOffer.INLINE_VARIABLE_REFERENCES ||
                    offer.action == PromotionOffer.UNORDERED ||
                    offer.action == PromotionOffer.REPLACE_CURRENT) {
                action = doPromotion(action, offer);
            } else if (offer.action == PromotionOffer.RANGE_INDEPENDENT ||
                    offer.action == PromotionOffer.FOCUS_INDEPENDENT) {
                // Pass the offer to the action expression only if the action isn't dependent on the
                // variable bound by this assignation
                Binding[] savedBindingList = offer.bindingList;
                offer.bindingList = extendBindingList(offer.bindingList);
                action = doPromotion(action, offer);
                offer.bindingList = savedBindingList;
            }
            return this;
        }
    }

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     */

    public void suppressValidation(int validationMode) {
        action.suppressValidation(validationMode);
    }

    /**
     * Extend an array of variable bindings to include the binding(s) defined in this expression
     * @param in a set of variable bindings
     * @return a set of variable bindings including all those supplied plus this one
     */

    public Binding[] extendBindingList(/*@Nullable*/ Binding[] in) {
        Binding[] newBindingList;
        if (in == null) {
            newBindingList = new Binding[1];
        } else {
            newBindingList = new Binding[in.length + 1];
            System.arraycopy(in, 0, newBindingList, 0, in.length);
        }
        newBindingList[newBindingList.length - 1] = this;
        return newBindingList;
    }

    /**
    * Get the immediate subexpressions of this expression
    */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(sequence, action);
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
        SubExpressionInfo sequenceInfo = new SubExpressionInfo(sequence, true, false, INSPECTION_CONTEXT);
        SubExpressionInfo actionInfo = new SubExpressionInfo(action, true, !(this instanceof LetExpression), INHERITED_CONTEXT);
        return new PairIterator<SubExpressionInfo>(sequenceInfo, actionInfo);
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (sequence == original) {
            sequence = replacement;
            found = true;
        }
        if (action == original) {
            action = replacement;
            found = true;
        }
        return found;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p/>
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet varPath = sequence.addToPathMap(pathMap, pathMapNodeSet);
        pathMap.registerPathForVariable(this, varPath);
        return action.addToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Get the display name of the range variable, for diagnostics only
     * @return the lexical QName of the range variable. For system allocated
     * variables, the conventional namespace prefix "zz" is used.
    */

    public String getVariableName() {
        if (variableName == null) {
            return "zz:var" + hashCode();
        } else {
            return variableName.getDisplayName();
        }
    }

    /**
     * Get the name of the range variable as a Name or EQName.
     * @return the name of the range variable. For system allocated
     * variables, the namespace "http://ns.saxonica.com/anonymous-var"
     * is used. For names in no namespace, the local name alone is used
     */

    public String getVariableEQName() {
        if (variableName == null) {
            return "Q{http://ns.saxonica.com/anonymous-var}var" + hashCode();
        } else if (variableName.isInNamespace("")) {
            return variableName.getLocalPart();
        } else {
            return variableName.getEQName();
        }
    }

    /**
     * Refine the type information associated with this variable declaration. This is useful when the
     * type of the variable has not been explicitly declared (which is common); the variable then takes
     * a static type based on the type of the expression to which it is bound. The effect of this call
     * is to update the static expression type for all references to this variable.
     * @param type the inferred item type of the expression to which the variable is bound
     * @param cardinality the inferred cardinality of the expression to which the variable is bound
     * @param constantValue the constant value to which the variable is bound (null if there is no constant value)
     * @param properties other static properties of the expression to which the variable is bound
     * @param visitor an expression visitor to provide context information
     * @param currentExpression the expression that binds the variable
     */

    public void refineTypeInformation(ItemType type, int cardinality,
                                      GroundedValue constantValue, int properties,
                                      ExpressionVisitor visitor,
                                      Assignation currentExpression) {
        List references = new ArrayList();
        ExpressionTool.gatherVariableReferences(currentExpression.getAction(), this, references);
        for (Iterator iter=references.iterator(); iter.hasNext();) {
            BindingReference ref = (BindingReference)iter.next();
            if (ref instanceof VariableReference) {
                ((VariableReference)ref).refineVariableType(type, cardinality, constantValue, properties, visitor);
                visitor.resetStaticProperties();
            }
        }
    }

    /**
     * Register a variable reference that refers to the variable bound in this expression
     * @param isLoopingReference - true if the reference occurs within a loop, such as the predicate
     * of a filter expression
     */

    public void addReference(boolean isLoopingReference) {
        if (refCount != FilterExpression.FILTERED) {
            refCount += (isLoopingReference ? 10 : 1);
        }
    }

    /**
     * Get the (nominal) count of the number of references to this variable
     * @return zero if there are no references, one if there is a single reference that is not in
     * a loop, some higher number if there are multiple references (or a single reference in a loop),
     * or the special value @link RangeVariable#FILTERED} if there are any references
     * in filter expressions that require searching.
     */

    public int getNominalReferenceCount() {
        return refCount;
    }

    /**
     * Test whether the variable bound by this let expression should be indexable
     * @return true if the variable should be indexable
     */

    public boolean isIndexedVariable() {
        return (refCount == FilterExpression.FILTERED);
    }

    /**
     * Replace all references to the variable bound by this let expression,
     * that occur within the action expression, with the given expression
     *
     * @param opt The optimizer
     * @param seq the expression
     * @throws net.sf.saxon.trans.XPathException
     */

    public void replaceVariable(Optimizer opt, Expression seq) throws XPathException {
        PromotionOffer offer2 = new PromotionOffer(opt);
        offer2.action = PromotionOffer.INLINE_VARIABLE_REFERENCES;
        offer2.bindingList = new Binding[] {this};
        offer2.containingExpression = seq;
        action = doPromotion(action, offer2);
        if (offer2.accepted) {
            // there might be further references to the variable
            offer2.accepted = false;
            replaceVariable(opt, seq);
        }
        if (isIndexedVariable() && seq instanceof VariableReference) {
            Binding newBinding = ((VariableReference) seq).getBinding();
            if (newBinding instanceof Assignation) {
                ((Assignation) newBinding).setIndexedVariable();
            }
        }
    }

    /**
     * Indicate that the variable bound by this let expression should be indexable
     * (because it is used in an appropriate filter expression)
     */

    public void setIndexedVariable() {
        refCount = FilterExpression.FILTERED;
    }
}

