////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.EmptySequence;

/**
* This class supports the nilled() function
*/

public class Nilled extends SystemFunctionCall implements Callable {


     /**
     * Simplify and validate.
     *
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        useContextItemAsDefault(visitor);
        return simplifyArguments(visitor);
    }

    @Override
    public int getIntrinsicDependencies() {
        if (getNumberOfArguments() == 0) {
            return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
        } else {
            return super.getIntrinsicDependencies();
        }
    }

    /**
    * Evaluate the function
    */

    public BooleanValue evaluateItem(XPathContext c) throws XPathException {
        NodeInfo node;
        node = (NodeInfo)argument[0].evaluateItem(c);
        return getNilledProperty(node);
    }

    /**
     * Determine whether a node has the nilled property
     * @param node the node in question (if null, the function returns null)
     * @return the value of the nilled accessor. Returns null for any node other than an
     * element node. For an element node, returns true if the element has been validated and
     * has an xsi:nil attribute whose value is true.
     */

    /*@Nullable*/ public static BooleanValue getNilledProperty(NodeInfo node) {
        // TODO: if the type annotation is ANYTYPE, we need to keep an extra bit to represent the nilled
        // property: it will be set only if validation has been performed. A newly-constructed element using
        // validation="preserve" has nilled=false even if xsi:nil = true 
        if (node==null || node.getNodeKind() != Type.ELEMENT) {
            return null;
        }
        return BooleanValue.get(node.isNilled());
    }

    /**
     * Determine whether a node is nilled. Returns true if the value
     * of the nilled property is true; false if the value is false or absent
     */

    public static boolean isNilled(NodeInfo node) {
        BooleanValue b = getNilledProperty(node);
        return b != null && b.getBooleanValue();
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

        NodeInfo node = getDefaultArgumentNode(context, arguments, "fn:nilled()");
        if (node==null || node.getNodeKind() != Type.ELEMENT) {
            return EmptySequence.getInstance();
        }
        return BooleanValue.get(isNilled(node));
    }
}
