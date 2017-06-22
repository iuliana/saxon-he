////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.evpull.EmptyEventIterator;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;


/**
 * This class represents a call to a user-defined function in the stylesheet or query.
 */

public class UserFunctionCall extends FunctionCall implements UserFunctionReference {

    private SequenceType staticType;
    private UserFunction function;
    private int tailCall = NOT_TAIL_CALL;

    /*@Nullable*/ private int[] argumentEvaluationModes = null;

    public static final int NOT_TAIL_CALL = 0;
    public static final int FOREIGN_TAIL_CALL = 1;
    public static final int SELF_TAIL_CALL = 2;

    /**
     * Create a function call to a user-written function in a query or stylesheet
     */

    public UserFunctionCall() {
    }

    /**
     * Set the static type
     *
     * @param type the static type of the result of the function call
     */

    public void setStaticType(SequenceType type) {
        staticType = type;
    }

    /**
     * Create the reference to the function to be called
     *
     * @param compiledFunction the function being called
     */

    public void setFunction(UserFunction compiledFunction) {
        function = compiledFunction;
    }

    /**
     * Check the function call against the declared function signature
     *
     * @param compiledFunction the function being called
     * @param visitor          an expression visitor
     */

    public void checkFunctionCall(UserFunction compiledFunction,
                                  ExpressionVisitor visitor) throws XPathException {
        Executable executable = visitor.getExecutable();
        boolean isXSLT = executable != null && executable.getHostLanguage() == Configuration.XSLT;
        int n = compiledFunction.getNumberOfArguments();
        for (int i = 0; i < n; i++) {
            Object name = compiledFunction.getFunctionName();
            if (name == null) {
                name = "";
            }
            RoleLocator role = new RoleLocator(
                    RoleLocator.FUNCTION, name, i);
            if (isXSLT) {
                role.setErrorCode("XTTE0790");
            }
            //role.setSourceLocator(this);
            argument[i] = TypeChecker.staticTypeCheck(
                    argument[i],
                    compiledFunction.getArgumentType(i),
                    false,
                    role, visitor);
        }
    }


    /**
     * Get the function that is being called by this function call
     *
     * @return the function being called
     */

    public UserFunction getFunction() {
        return function;
    }

    /**
     * Determine whether this is a tail call (not necessarily a recursive tail call)
     *
     * @return true if this function call is a tail call
     */

    public boolean isTailCall() {
        return tailCall != NOT_TAIL_CALL;
    }

    public boolean isRecursiveTailCall() {
        return tailCall == SELF_TAIL_CALL;
    }

    /**
     * Method called during the type checking phase
     */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        // these checks are now done in setFunction(), at the time when the function
        // call is bound to an actual function
    }

    /**
     * Get the qualified of the function being called
     *
     * @return the qualified name
     */

    public final StructuredQName getFunctionName() {
        StructuredQName n = super.getFunctionName();
        if (n == null) {
            return function.getFunctionName();
        } else {
            return n;
        }
    }

    /**
     * Get the evaluation modes that have been determined for each of the arguments
     *
     * @return an array of integers representing the evaluation modes, one for each argument
     */

    public int[] getArgumentEvaluationModes() {
        return argumentEvaluationModes;
    }

    /**
     * Pre-evaluate a function at compile time. This version of the method suppresses
     * early evaluation by doing nothing.
     *
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Determine the data type of the expression, if possible
     *
     * @param th the type hierarchy cache
     * @return Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (staticType == null) {
            // the actual type is not known yet, so we return an approximation
            return AnyItemType.getInstance();
        } else {
            return staticType.getPrimaryType();
        }
    }

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_USER_FUNCTIONS;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    public boolean isUpdatingExpression() {
        return function.isUpdating();
    }

    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link net.sf.saxon.expr.StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */

    protected int computeSpecialProperties() {
        // Inherit the properties of the function being called if possible. But we have to prevent
        // looping when the function is recursive. For safety, we only consider the properties of the
        // function body if it contains no further function calls.
        if (function == null) {
            return super.computeSpecialProperties();
        } else {
            List calledFunctions = new ArrayList();
            ExpressionTool.gatherCalledFunctions(function.getBody(), calledFunctions);
            if (calledFunctions.isEmpty()) {
                return function.getBody().computeSpecialProperties();
            } else {
                return super.computeSpecialProperties();
            }
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        if (function == null) {
            // not bound yet, we have no way to register the new copy with the XSLFunction
            throw new UnsupportedOperationException("UserFunctionCall.copy()");
        }
        UserFunctionCall ufc = new UserFunctionCall();
        ufc.setFunction(function);
        ufc.setStaticType(staticType);
        Expression[] a2 = new Expression[argument.length];
        for (int i = 0; i < argument.length; i++) {
            a2[i] = argument[i].copy();
        }
        ufc.argument = a2;
        if (argumentEvaluationModes != null) {
            int[] am2 = new int[argumentEvaluationModes.length];
            System.arraycopy(argumentEvaluationModes, 0, am2, 0, am2.length);
            ufc.argumentEvaluationModes = am2;
        }
        return ufc;
    }

    /**
     * Determine the cardinality of the result
     */

    public int computeCardinality() {
        if (staticType == null) {
            // the actual type is not known yet, so we return an approximation
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return staticType.getCardinality();
        }
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.typeCheck(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        if (function != null) {
            if (function.getFunctionName() == null) {
                // This is an inline function item; add type-checking for the argument types and the result type
                checkFunctionCall(function, visitor);
                Expression body = function.getBody();
                body = body.typeCheck(visitor, null);
                TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
                RoleLocator role = new RoleLocator(RoleLocator.FUNCTION_RESULT, "", 0);
                body = TypeChecker.staticTypeCheck(body, function.getResultType(th), visitor.getStaticContext().isInBackwardsCompatibleMode(), role, visitor);
                function.setBody(body);
            }
            computeArgumentEvaluationModes();
            if (staticType == SequenceType.ANY_SEQUENCE) {
                // try to get a better type
                staticType = function.getResultType(visitor.getConfiguration().getTypeHierarchy());
            }
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e == this && function != null) {
            computeArgumentEvaluationModes();
            Expression e2 = visitor.getConfiguration().obtainOptimizer().tryInlineFunctionCall(
                    this, visitor, contextItemType);
            if (e2 != this) {
                return visitor.optimize(e2, contextItemType);
            }
            return e2;
        }
        return e;
    }

    /**
     * Promote this expression if possible
     */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            boolean changed = false;
            if (offer.action != PromotionOffer.UNORDERED) {
                for (int i = 0; i < argument.length; i++) {
                    Expression a2 = doPromotion(argument[i], offer);
                    changed |= (a2 != argument[i]);
                    argument[i] = a2;
                }
            }
            if (changed && function != null) {
                computeArgumentEvaluationModes();
            }
            return this;
        }
    }

    /**
     * Compute the evaluation mode of each argument
     */

    public void computeArgumentEvaluationModes() {
        argumentEvaluationModes = new int[argument.length];
        for (int i = 0; i < argument.length; i++) {
            int refs = function.getParameterDefinitions()[i].getReferenceCount();
            if (refs == 0) {
                // the argument is never referenced, so don't evaluate it
                argumentEvaluationModes[i] = ExpressionTool.RETURN_EMPTY_SEQUENCE;
            } else if (function.getParameterDefinitions()[i].isIndexedVariable()) {
                argumentEvaluationModes[i] = ExpressionTool.MAKE_INDEXED_VARIABLE;
            } else if ((argument[i].getDependencies() & StaticProperty.DEPENDS_ON_USER_FUNCTIONS) != 0) {
                // if the argument contains a call to a user-defined function, then it might be a recursive call.
                // It's better to evaluate it now, rather than waiting until we are on a new stack frame, as
                // that can blow the stack if done repeatedly. (See test func42)
                argumentEvaluationModes[i] = ExpressionTool.eagerEvaluationMode(argument[i]);
            } else {
                int m = ExpressionTool.lazyEvaluationMode(argument[i]);
                if (m == ExpressionTool.MAKE_CLOSURE && refs > 1) {
                    m = ExpressionTool.MAKE_MEMO_CLOSURE;
                }
                argumentEvaluationModes[i] = m;
            }
        }
    }

    @Override
    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = super.replaceSubExpression(original, replacement);
        if (found) {
            computeArgumentEvaluationModes();
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
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return addExternalFunctionCallToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. This marks the function call as tailRecursive if
     * if is a call to the containing function, and in this case it also returns "true" to the caller to indicate
     * that a tail call was found.
     */

    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        tailCall = (getFunctionName().equals(qName) &&
                arity == getNumberOfArguments() ? SELF_TAIL_CALL : FOREIGN_TAIL_CALL);
        return tailCall;
    }

    public int getImplementationMethod() {
        if (Cardinality.allowsMany(getCardinality())) {
            return ITERATE_METHOD | PROCESS_METHOD;
        } else {
            return EVALUATE_METHOD;
        }
    }

    /**
     * Call the function, returning the value as an item. This method will be used
     * only when the cardinality is zero or one. If the function is tail recursive,
     * it returns an Object representing the arguments to the next (recursive) call
     */

    public Item evaluateItem(XPathContext c) throws XPathException {
        return callFunction(c).head();
    }

    /**
     * Call the function, returning an iterator over the results. (But if the function is
     * tail recursive, it returns an iterator over the arguments of the recursive call)
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        return callFunction(c).iterate();
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
        Sequence[] actualArgs = evaluateArguments(context);
        XPathContextMajor c2 = context.newCleanContext();
        c2.setOrigin(this);
        function.callUpdating(actualArgs, c2, pul);
    }

    /**
     * This is the method that actually does the function call
     *
     * @param c the dynamic context
     * @return the result of the function
     * @throws XPathException if dynamic errors occur
     */
    private Sequence callFunction(XPathContext c) throws XPathException {
        Sequence[] actualArgs = evaluateArguments(c);

        if (isTailCall()) {
            ((XPathContextMajor) c).requestTailCall(function, actualArgs);
            return EmptySequence.getInstance();
        }

        XPathContextMajor c2 = c.newCleanContext();
        c2.setOrigin(this);
        c2.setTemporaryOutputState(true);
        try {
            return function.call(c2, actualArgs);
        } catch (StackOverflowError err) {
            throw new XPathException("Too many nested function calls. May be due to infinite recursion.", this);
        }
    }

    /**
     * Process the function call in push mode
     *
     * @param context the XPath dynamic context
     * @throws XPathException
     */

    public void process(XPathContext context) throws XPathException {
        Sequence[] actualArgs = evaluateArguments(context);
        if (isTailCall()) {
            ((XPathContextMajor) context).requestTailCall(function, actualArgs);
        } else {
            SequenceReceiver out = context.getReceiver();
            XPathContextMajor c2 = context.newCleanContext();
            c2.setReceiver(out);
            c2.setTemporaryOutputState(true);
            c2.setOrigin(this);
            function.process(actualArgs, c2);
        }
    }

    /**
     * Process the function call in pull mode
     *
     * @param context the XPath dynamic context
     * @throws XPathException
     */

    public EventIterator iterateEvents(XPathContext context) throws XPathException {
        Sequence[] actualArgs = evaluateArguments(context);
        if (isTailCall()) {
            ((XPathContextMajor) context).requestTailCall(function, actualArgs);
            return EmptyEventIterator.getInstance();
        } else {
            SequenceReceiver out = context.getReceiver();
            XPathContextMajor c2 = context.newCleanContext();
            c2.setReceiver(out);
            c2.setOrigin(this);
            return function.iterateEvents(actualArgs, c2);
        }
    }


    public Sequence[] evaluateArguments(XPathContext c) throws XPathException {
        int numArgs = argument.length;
        Sequence[] actualArgs = new Sequence[numArgs];
        if (argumentEvaluationModes == null) {
            // should have been done at compile time
            computeArgumentEvaluationModes();
        }
        for (int i = 0; i < numArgs; i++) {

            int refs = function.getParameterDefinitions()[i].getReferenceCount();
            actualArgs[i] = ExpressionTool.evaluate(argument[i], argumentEvaluationModes[i], c, refs);

            if (actualArgs[i] == null) {
                actualArgs[i] = EmptySequence.getInstance();
            }
            // If the argument has come in as a (non-memo) closure but there are multiple references to it,
            // then we materialize it in memory now. This shouldn't really happen but it does (tour.xq)
            if (refs > 1 && actualArgs[i] instanceof Closure) {
                actualArgs[i] = ((Closure) actualArgs[i]).reduce();
            }
        }
        return actualArgs;
    }

    /**
     * Call the function dynamically. For this to be possible, the static arguments of the function call
     * must have been set up as {@link SuppliedParameterReference} objects. The actual arguments are placed on the
     * callee's stack, and the type conversion takes place "in situ".
     *
     * @param suppliedArguments the values to be used for the arguments of the function
     * @param context           the dynamic evaluation context
     * @return the result of evaluating the function
     */

    public Sequence dynamicCall(Sequence[] suppliedArguments, XPathContext context) throws XPathException {
        Sequence[] convertedArgs = new Sequence[suppliedArguments.length];
        XPathContextMajor c2 = context.newCleanContext();
        c2.setOrigin(this);
        c2.setCaller(context);
        c2.openStackFrame(suppliedArguments.length);
        for (int i = 0; i < suppliedArguments.length; i++) {
            c2.setLocalVariable(i, suppliedArguments[i]);
            convertedArgs[i] = ExpressionTool.lazyEvaluate(argument[i], c2, 10);
        }
        XPathContextMajor c3 = c2.newCleanContext();
        c3.setOrigin(this);
        return function.call(c3, convertedArgs);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("functionCall");
        if (getFunctionName() != null) {
            out.emitAttribute("name", getDisplayName());
            out.emitAttribute("tailCall",
                    ((tailCall == NOT_TAIL_CALL ? "false" : (tailCall == SELF_TAIL_CALL ? "self" : "foreign"))));
        }
        for (int a = 0; a < argument.length; a++) {
            argument[a].explain(out);
        }
        if (getFunctionName() == null) {
            out.startElement("body");
            function.getBody().explain(out);
            out.endElement();
        }
        out.endElement();
    }

    public int getConstructType() {
        return Location.FUNCTION_CALL;
    }

    public Object getProperty(String name) {
        if (name.equals("target")) {
            return function;
        }
        return super.getProperty(name);
    }

    public StructuredQName getObjectName() {
        return getFunctionName();
    }


    /**
     * Get the line number within the document or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the line number within the document or module.
     */

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    /**
     * Get the URI of the document or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the URI of the document or module.
     */

    public String getSystemId(long locationId) {
        return getSystemId();
    }

}