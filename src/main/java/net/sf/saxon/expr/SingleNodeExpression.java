////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;


/**
* A node set expression that will always return zero or one nodes
*/

public abstract class SingleNodeExpression extends Expression {

	private boolean contextMaybeUndefined = true;
	
    /**
    * Type-check the expression.
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, /*@Nullable*/ ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (contextItemType == null || contextItemType.itemType == null) {
            XPathException err = new XPathException(noContextMessage() + ": the context item is absent");
            err.setErrorCode("XPDY0002");
            err.setIsTypeError(true);
            err.setLocator(this);
            throw err;
        }else {
            contextMaybeUndefined = contextItemType.contextMaybeUndefined;
        }
        if (contextItemType.itemType.isPlainType()) {
            XPathException err = new XPathException(noContextMessage() + ": the context item is an atomic value");
            err.setErrorCode("XPTY0020");
            err.setIsTypeError(true);
            err.setLocator(this);
            throw err;
        }
        return this;
    }

    /**
     * Customize the error message on type checking
     */

    protected abstract String noContextMessage();

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
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        // repeat the check: in XSLT insufficient information is available the first time
        return typeCheck(visitor, contextItemType);
    }


    /**
    * Specify that the expression returns a singleton
    */

    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
    * Determine the data type of the items returned by this expression
    * @return Type.NODE
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return AnyNodeTest.getInstance();
    }

    /**
    * Determine which aspects of the context the expression depends on. The result is
    * a bitwise-or'ed value composed from constants such as StaticProperty.VARIABLES and
    * StaticProperty.CURRENT_NODE
    */

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    public int computeSpecialProperties() {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.CONTEXT_DOCUMENT_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NON_CREATIVE;
    }

    /**
    * Get the single node to which this expression refers. Returns null if the node-set is empty
    */

    public abstract NodeInfo getNode(XPathContext context) throws XPathException;

    /**
     * Ask whether there is a possibility that the context item will be undefined
     * @return true if this is a possibility
     */

    public boolean isContextPossiblyUndefined() {
        return contextMaybeUndefined;
    }
    
    /**
    * Evaluate the expression in a given context to return an iterator
    * @param context the evaluation context
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return SingletonIterator.makeIterator(getNode(context));
    }

    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        return getNode(context);
    }

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return getNode(context) != null;
    }

}

