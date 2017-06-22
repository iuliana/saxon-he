////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

/**
 * Expression representing a call to a user-written extension
 * function implemented as a subtype of {@link ExtensionFunctionCall}
 */
public class IntegratedFunctionCall extends FunctionCall implements Callable {

    private ExtensionFunctionCall function;
    private SequenceType resultType = SequenceType.ANY_SEQUENCE;
    private int state = 0;

    public IntegratedFunctionCall(ExtensionFunctionCall function) {
        this.function = function;
    }

    /**
     * Get the ExtensionFunctionCall object supplied by the application
     *
     * @return the ExtensionFunctionCall object
     */

    public ExtensionFunctionCall getFunction() {
        return function;
    }


    /**
     * Get the container in which this expression is located. This will usually be a top-level construct
     * such as a function or global variable, and XSLT template, or an XQueryExpression. In the case of
     * free-standing XPath expressions it will be the StaticContext object
     *
     * @return the expression's container
     */

    @Override
    public Container getContainer() {
        return function.getContainer();
    }

    /**
     * Mark an expression as being in a given Container. This link is used primarily for diagnostics:
     * the container links to the location map held in the executable.
     * <p/>
     * <p>This affects the expression and all its subexpressions. Any subexpressions that are not in the
     * same container are marked with the new container, and this proceeds recursively. However, any
     * subexpression that is already in the correct container is not modified.</p>
     *
     * @param container The container of this expression.
     */
    @Override
    public void setContainer(Container container) {
        super.setContainer(function.getContainer());
        function.setDefinition(function.getDefinition(), container);
    }

    /**
     * Method supplied by each class of function to check arguments during parsing, when all
     * the argument expressions have been read. This implementation of the method checks the arguments
     * of the supplied function call against the argument types declared as part of the extension function
     * definition, and generates code to do the conversion if necessary.
     *
     * @param visitor the expression visitor
     * @throws net.sf.saxon.trans.XPathException
     *          if the arguments are statically determined to be incompatible
     *          with the declared argument types of the function.
     */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        ExtensionFunctionDefinition definition = function.getDefinition();
        checkArgumentCount(definition.getMinimumNumberOfArguments(), definition.getMaximumNumberOfArguments());
        final int args = getNumberOfArguments();
        SequenceType[] declaredArgumentTypes = definition.getArgumentTypes();
        if (declaredArgumentTypes == null || (args != 0 && declaredArgumentTypes.length == 0)) {
            throw new XPathException("Integrated function " + getDisplayName() +
                    " failed to declare its argument types");
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        SequenceType[] actualArgumentTypes = new SequenceType[args];
        for (int i = 0; i < args; i++) {
            argument[i] = TypeChecker.staticTypeCheck(
                    argument[i],
                    i < declaredArgumentTypes.length ?
                            declaredArgumentTypes[i] :
                            declaredArgumentTypes[declaredArgumentTypes.length - 1],
                    false,
                    new RoleLocator(RoleLocator.FUNCTION, getFunctionName(), i),
                    visitor);

            actualArgumentTypes[i] = SequenceType.makeSequenceType(
                    argument[i].getItemType(th),
                    argument[i].getCardinality());
        }
        resultType = definition.getResultType(actualArgumentTypes);
        if (state == 0) {
            function.supplyStaticContext(visitor.getStaticContext(), 0, getArguments());
        }
        state++;
    }

    /**
     * Type-check the expression.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression exp = super.typeCheck(visitor, contextItemType);
        if (exp instanceof IntegratedFunctionCall) {
            Expression exp2 = ((IntegratedFunctionCall) exp).function.rewrite(visitor.getStaticContext(), argument);
            if (exp2 == null) {
                return exp;
            } else {
                ExpressionTool.copyLocationInfo(this, exp2);
                return exp2.simplify(visitor).typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
            }
        }
        return exp;
    }

    /**
     * Pre-evaluate a function at compile time. Functions that do not allow
     * pre-evaluation, or that need access to context information, can override this method.
     *
     * @param visitor an expression visitor
     * @return the result of the early evaluation, or the original expression, or potentially
     *         a simplified expression
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    /**
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p/>
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @param th the type hierarchy cache
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return resultType.getPrimaryType();
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     *         {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     *         {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */

    protected int computeCardinality() {
        return resultType.getCardinality();
    }

    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     *         dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */

    public int getIntrinsicDependencies() {
        ExtensionFunctionDefinition definition = function.getDefinition();
        return (definition.dependsOnFocus() ? StaticProperty.DEPENDS_ON_FOCUS : 0);
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
        ExtensionFunctionDefinition definition = function.getDefinition();
        return (definition.hasSideEffects() ? StaticProperty.HAS_SIDE_EFFECTS : StaticProperty.NON_CREATIVE);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ExtensionFunctionCall newCall = function.getDefinition().makeCallExpression();
        newCall.setDefinition(function.getDefinition(), function.getContainer());
        function.copyLocalData(newCall);
        IntegratedFunctionCall copy = new IntegratedFunctionCall(newCall);
        Expression[] args = new Expression[getNumberOfArguments()];
        for (int i = 0; i < args.length; i++) {
            args[i] = argument[i].copy();
        }
        copy.setFunctionName(getFunctionName());
        copy.setArguments(args);
        copy.resultType = resultType;
        copy.state = state;
        return copy;
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

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        ExtensionFunctionDefinition definition = function.getDefinition();
        Sequence[] argValues = new Sequence[getNumberOfArguments()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = SequenceTool.toLazySequence(argument[i].iterate(context));
        }
        final RoleLocator role = new RoleLocator(RoleLocator.FUNCTION_RESULT, getFunctionName().getDisplayName(), 0);
        final Configuration config = context.getConfiguration();
        SequenceIterator result;
        try {
            result = function.call(context, argValues).iterate();
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            throw e;
        }
        if (!definition.trustResultType()) {
            int card = resultType.getCardinality();
            if (card != StaticProperty.ALLOWS_ZERO_OR_MORE) {
                result = new CardinalityCheckingIterator(result,
                        card,
                        role,
                        this);
            }
            final ItemType type = resultType.getPrimaryType();
            if (type != AnyItemType.getInstance()) {
                result = new ItemMappingIterator(result,
                        new ItemMappingFunction() {
                            public Item mapItem(Item item) throws XPathException {
                                if (!type.matchesItem(item, false, config)) {
                                    String msg = "Item returned by integrated function " +
                                            getFunctionName().getDisplayName() +
                                            "() is not of declared item type. Actual type is " +
                                            Type.getItemType(item, config.getTypeHierarchy()).toString() +
                                            "; expected type is " + type.toString();
                                    XPathException err = new XPathException(
                                            msg);
                                    err.setErrorCode("XPTY0004");
                                    err.setLocator(IntegratedFunctionCall.this);
                                    throw err;
                                }
                                return item;
                            }
                        }, true);
            }
        }
        return result;
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        Sequence[] argValues = new Sequence[getNumberOfArguments()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = SequenceTool.toLazySequence(argument[i].iterate(context));
        }
        try {
            return function.effectiveBooleanValue(context, argValues);
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            throw e;
        }
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return function.call(context, arguments);
    }

}

