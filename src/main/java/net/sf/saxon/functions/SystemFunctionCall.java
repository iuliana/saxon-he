////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
* Abstract superclass for calls to functions in the standard function library
*/

public abstract class SystemFunctionCall extends FunctionCall implements Callable {

    public Callable getConvertingCallable() {
        final Callable raw = this;
        return new Callable() {
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
                Sequence[] convertedArgs = new Sequence[arguments.length];
                for (int i=0; i<arguments.length; i++) {
                    RoleLocator role = new RoleLocator(RoleLocator.FUNCTION, getFunctionName(), i);
                    SequenceType requiredType = getRequiredType(i);
                    TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
                    convertedArgs[i] = th.applyFunctionConversionRules(
                            arguments[i], requiredType, role, SystemFunctionCall.this);
                }
                return raw.call(context, convertedArgs);
            }
        };
    }

    /**
     * Make a system function call (one in the standard function namespace).
     * @param name The local name of the function.
     * @param arguments the arguments to the function call
     * @return a FunctionCall that implements this function, if it
     * exists, or null if the function is unknown.
     */

    /*@Nullable*/ public static FunctionCall makeSystemFunction(String name, /*@NotNull*/ Expression[] arguments) {
        StandardFunction.Entry entry = StandardFunction.getFunction(name, arguments.length);
        if (entry==null) {
            return null;
        }
        Class functionClass = entry.implementationClass;
        try {
            SystemFunctionCall f = (SystemFunctionCall)functionClass.newInstance();
            f.setDetails(entry);
            f.setFunctionName(new StructuredQName("", NamespaceConstant.FN, name));
            f.setArguments(arguments);
            return f;
        } catch (IllegalAccessException err) {
            return null;
        } catch (InstantiationException err) {
            return null;
        }
    }


    private StandardFunction.Entry details;
    protected int operation;

    /**
     * Set the details of this type of function
     * @param entry information giving details of the function signature
    */

    public void setDetails(StandardFunction.Entry entry) {
        details = entry;
        operation = details.opcode;
    }

    /**
     * Get the details of the function signature
     * @return information about the function signature
    */

    public StandardFunction.Entry getDetails() {
        return details;
    }
    
    public int getOperation(){
    	return operation;
    }



    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     *         {@link #PROCESS_METHOD}
     */

    public int getImplementationMethod() {
        boolean feedable = getNumberOfArguments() > 0 &&
                !Cardinality.allowsMany(getRequiredType(0).getCardinality());
        int methods = super.getImplementationMethod();
        if (feedable) {
            methods |= Expression.ITEM_FEED_METHOD;
        }
        return methods;
    }

    public NodeInfo getDefaultArgumentNode(XPathContext context, Sequence[] arguments, String funcName) throws XPathException{
        if (arguments.length == 0) {
            Item item = context.getContextItem();
            if (item == null) {
                throw new XPathException("Context item for "+funcName+" is absent", "XPDY0002");
            } else if (!(item instanceof NodeInfo)) {
                throw new XPathException("Context item for "+funcName+" must be a node", "XPTY0004");
            } else {
                return (NodeInfo)item;
            }
        } else {
            return (NodeInfo)arguments[0].head();
        }
    }

    /**
     * Bind aspects of the static context on which the particular function depends
     * @param env the static context of the function call
     * @throws XPathException if execution with this static context will inevitably fail
     */

    public void bindStaticContext(StaticContext env) throws XPathException {
        // default: no action
    }

    /**
    * Method called during static type checking
    */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        checkArgumentCount(details.minArguments, details.maxArguments);
        for (int i=0; i<argument.length; i++) {
            checkArgument(i, visitor);
        }
    }

    /**
     * Perform static type checking on an argument to a function call, and add
     * type conversion logic where necessary.
     * @param arg argument number, zero-based
     * @param visitor an expression visitor
     * @throws XPathException if the argument is statically invalid
    */

    private void checkArgument(int arg, /*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        RoleLocator role = new RoleLocator(RoleLocator.FUNCTION,
                getFunctionName(), arg);
        //role.setSourceLocator(this);
        role.setErrorCode(getErrorCodeForTypeErrors());
        argument[arg] = TypeChecker.staticTypeCheck(
                argument[arg],
                getRequiredType(arg),
                visitor.getStaticContext().isInBackwardsCompatibleMode(),
                role, visitor);
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

    /*@NotNull*/
    public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression sf = super.optimize(visitor, contextItemType);
        if (sf == this && argument.length <= details.resultIfEmpty.length) {
                    // the condition eliminates concat, which is a special case.
            for (int i=0; i<argument.length; i++) {
                if (Literal.isEmptySequence(argument[i]) && details.resultIfEmpty[i] != null) {
                    return Literal.makeLiteral(SequenceTool.toGroundedValue(details.resultIfEmpty[i]));
                }
            }
        }
        return sf;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Expression[] a2 = new Expression[argument.length];
        for (int i=0; i<argument.length; i++) {
            a2[i] = argument[i].copy();
        }
        Expression e2 = SystemFunctionCall.makeSystemFunction(details.name, a2);
        if (e2 == null) {
            throw new UnsupportedOperationException("SystemFunction.copy()");
        }
        ExpressionTool.copyLocationInfo(this, e2);
        return e2;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        List<SubExpressionInfo> list = new ArrayList<SubExpressionInfo>(argument.length);
        for (int i=0; i<argument.length; i++) {
            list.add(new SubExpressionInfo(argument[i], true, false, details.syntacticContext[i]));
        }
        return list.iterator();
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o != null) && (o instanceof SystemFunctionCall) && super.equals(o) && operation == ((SystemFunctionCall)o).operation;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + operation;
        return result;
    }

    /**
     * Return true if two objects are equal or if both are null
     * @param x the first object
     * @param y the second object
     * @return true if both x and y are null or if x.equals(y)
     */

    public static boolean equalOrNull(Object x, Object y) {
        if (x == null) {
            return (y == null);
        } else {
            return (y != null) && x.equals(y);
        }
    }


    /**
     * Return the error code to be used for type errors. This is overridden for functions
     * such as exactly-one(), one-or-more(), ...
     * @return the error code to be used for type errors in the function call. Normally XPTY0004,
     * but different codes are used for functions such as exactly-one()
     */

    public String getErrorCodeForTypeErrors() {
        return "XPTY0004";
    }

    /**
     * Get the required type of the nth argument
     * @param arg the number of the argument whose type is requested, zero-based
     * @return the required type of the argument as defined in the function signature
    */

    protected SequenceType getRequiredType(int arg) {
        if (details == null) {
            return SequenceType.ANY_SEQUENCE;
        }
        return details.argumentTypes[arg];
        // this is overridden for concat()
    }

    /**
    * Determine the item type of the value returned by the function
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (details == null) {
            // probably an unresolved function call
            return AnyItemType.getInstance();
        }
        ItemType type = details.itemType;
        if ((details.properties & StandardFunction.AS_ARG0) != 0) {
            if (argument.length > 0) {
                return argument[0].getItemType(th);
            } else {
                return AnyItemType.getInstance();
                // if there is no first argument, an error will be reported
            }
        } else if ((details.properties & StandardFunction.AS_PRIM_ARG0) != 0) {
            if (argument.length > 0) {
                return argument[0].getItemType(th).getPrimitiveItemType();
            } else {
                return AnyItemType.getInstance();
                // if there is no first argument, an error will be reported
            }
        } else {
            return type;
        }
    }

    /**
    * Determine the cardinality of the function.
    */

    public int computeCardinality() {
        if (details==null) {
            //System.err.println("**** No details for " + getClass() + " at " + this);
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        return details.cardinality;
    }

    /**
     * Determine the special properties of this expression. The general rule
     * is that a system function call is non-creative if its return type is
     * atomic, or if all its arguments are non-creative. This is overridden
     * for the generate-id() function, which is considered creative if
     * its operand is creative (because the result depends on the
     * identity of the operand)
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (details == null) {
            return p;
        }
        if (details.itemType.isPlainType() ||
                (details.properties & StandardFunction.AS_ARG0) != 0 || (details.properties & StandardFunction.AS_PRIM_ARG0) != 0) {
            return p | StaticProperty.NON_CREATIVE;
        }
        for (Expression anArgument : argument) {
            if ((anArgument.getSpecialProperties() & StaticProperty.NON_CREATIVE) == 0) {
                // the argument is creative
                return p;
            }
        }
        return p | StaticProperty.NON_CREATIVE;
    }

    /**
    * Set "." as the default value for the first and only argument. Called from subclasses.
     * @param visitor the expression visitor
     */

    protected final void useContextItemAsDefault(/*@NotNull*/ ExpressionVisitor visitor) {
        if (argument.length==0) {
            argument = new Expression[1];
            argument[0] = new ContextItemExpression();
            details = StandardFunction.getFunction(getFunctionName().getLocalPart(), 1);
            ExpressionTool.copyLocationInfo(this, argument[0]);
            visitor.resetStaticProperties();
        }
        // Note that the extra argument is added before type-checking takes place. The
        // type-checking will add any necessary checks to ensure that the context item
        // is a node, in cases where this is required.
    }

    /**
    * Add an implicit argument referring to the context document. Called by functions such as
    * id() and key() that take the context document as an implicit argument
     * @param pos the position of the argument whose default value is ".", zero-based
     * @param augmentedName the name to be used for the function call with its extra argument.
     * There are some cases where user function calls cannot supply the argument directly (notably
     * unparsed-entity-uri() and unparsed-entity-public-id()) and in these cases a synthesized
     * function name is used for the new function call.
     * @throws net.sf.saxon.trans.XPathException if a static error is found
    */

    protected final void addContextDocumentArgument(int pos, String augmentedName)
    throws XPathException {
        if (argument.length > pos) {
            return;
            // this can happen during optimization, if the extra argument is already present
        }
        if (argument.length != pos) {
            throw new XPathException("Too few arguments in call to " + augmentedName + "() function");
        }
        Expression[] newArgs = new Expression[pos+1];
        System.arraycopy(argument, 0, newArgs, 0, argument.length);
        RootExpression rootExpression = new RootExpression();
        ExpressionTool.copyLocationInfo(this, rootExpression);
        newArgs[pos] = rootExpression;
        argument = newArgs;
        setDetails(StandardFunction.getFunction(augmentedName, newArgs.length));
    }

    /**
     * Add a representation of a doc() call or similar function to a PathMap.
     * This is a convenience method called by the addToPathMap() methods for doc(), document(), collection()
     * and similar functions. These all create a new root expression in the path map.
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodes the node in the PathMap representing the focus at the point where this expression
     *                    is called. Set to null if this expression appears at the top level.
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    /*@Nullable*/ public PathMap.PathMapNodeSet addDocToPathMap(/*@NotNull*/ PathMap pathMap, PathMap.PathMapNodeSet pathMapNodes) {
        argument[0].addToPathMap(pathMap, pathMapNodes);
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }

    /**
     * Helper method for subclasses: get the context item if it is a node, throwing appropriate errors
     * if not
     * @param context the XPath dynamic context
     * @return the context item if it exists and is a node
     * @throws XPathException if there is no context item or if the context item is not a node
     */

    protected NodeInfo getContextNode(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            XPathException err = new XPathException("Context item for " + getFunctionName() + "() is absent", "XPDY0002");
            err.maybeSetContext(context);
            err.setLocator(this);
            throw err;
        } else if (!(item instanceof NodeInfo)) {
            XPathException err =  new XPathException("Context item for " + getFunctionName() +"() is not a node", "XPTY0004");
            err.maybeSetContext(context);
            err.setLocator(this);
            throw err;
        } else {
            return (NodeInfo)item;
        }
    }

}

