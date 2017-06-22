////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
* Implement XPath function fn:data()
*/

public class Data extends CompileTimeFunction implements Callable {

    /**
     * Simplify and validate.
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
    	  if (argument.length==0) {
              argument = new Expression[1];
              argument[0] = new ContextItemExpression();
          }
        Expression a = Atomizer.makeAtomizer(argument[0]);
        ExpressionTool.copyLocationInfo(this, a);
        return visitor.simplify(a);
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
     * Evaluate the expression. (Used for run-time evaluation via function-lookup().)
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence arg;
        if (arguments.length == 0) {
            arg = context.getContextItem();
        } else {
            arg = arguments[0];
        }
        if (arg instanceof Item) {
            if (arg instanceof NodeInfo) {
                return ((NodeInfo)arg).atomize();
            } else if (arg instanceof AtomicValue) {
                return arg;
            } else {
                throw new XPathException("Cannot atomize a function item or external value", "FOTY0017");
            }
        } else {
            SequenceIterator a = Atomizer.getAtomizingIterator(arg.iterate(), false);
            return SequenceTool.toLazySequence(a);
        }
    }
}

