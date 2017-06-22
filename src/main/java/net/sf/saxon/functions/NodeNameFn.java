////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.QNameValue;

/**
* This class supports the node-name() function
*/

public class NodeNameFn extends SystemFunctionCall {

    /**
    * Simplify and validate.
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

    /*@Nullable*/ public QNameValue evaluateItem(XPathContext c) throws XPathException {
        NodeInfo node = (NodeInfo)argument[0].evaluateItem(c);
        return nodeName(node);
    }

    public static QNameValue nodeName(NodeInfo node) {
        if (node==null) {
            return null;
        }
        int nc = node.getNameCode();
        if (nc == -1) {
            return null;
        }
        return new QNameValue(node.getNamePool(), nc);
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo node = (arguments.length == 0 ? getContextNode(context) : (NodeInfo)arguments[0].head());
        QNameValue name = nodeName(node);
        return (name == null ? EmptySequence.getInstance() : name);
    }




}

