////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SubExpressionInfo;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of the fn:concat() function
 */


public class Concat extends SystemFunctionCall implements Callable {




    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        for (Sequence arg : arguments) {
            Item item = arg.head();
            if (item != null) {
                fsb.append(arg.head().getStringValueCS());
            }
        }
        return new StringValue(fsb);
    }


    /**
    * Get the required type of the nth argument
    */

    protected SequenceType getRequiredType(int arg) {
        return getDetails().argumentTypes[0];
        // concat() is a special case
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
        for (Expression anArgument : argument) {
            list.add(new SubExpressionInfo(anArgument, true, false, getDetails().syntacticContext[0]));
        }
        return list.iterator();
    }

    /**
    * Evaluate the function in a string context
    */

    public CharSequence evaluateAsString(XPathContext c) throws XPathException {
        return evaluateItem(c).getStringValueCS();
    }

    /**
    * Evaluate in a general context
    */

    public StringValue evaluateItem(XPathContext c) throws XPathException {
        int numArgs = argument.length;
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        for (int i=0; i<numArgs; i++) {
            AtomicValue val = (AtomicValue)argument[i].evaluateItem(c);
            if (val!=null) {
                sb.append(val.getStringValueCS());
            }
        }
        return StringValue.makeStringValue(sb.condense());
    }

    /**
     * Process the instruction in push mode. This avoids constructing the concatenated string
     * in memory, instead each argument can be sent straight to the serializer.
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public void process(/*@NotNull*/ XPathContext context) throws XPathException {
        SequenceReceiver out = context.getReceiver();
        if (out instanceof ComplexContentOutputter) {
            // This optimization is only safe if the output forms part of document or element content
            int numArgs = argument.length;
            // Start and end with an empty string to force space separation from any previous or following outputs
            out.append(StringValue.EMPTY_STRING, 0, 0);
            boolean empty = true;
            for (int i=0; i<numArgs; i++) {
                AtomicValue val = (AtomicValue)argument[i].evaluateItem(context);
                if (val!=null) {
                    out.characters(val.getStringValueCS(), 0, 0);
                    empty = false;
                }
            }
            if (!empty) {
                out.append(StringValue.EMPTY_STRING, 0, 0);
            }
        } else {
            out.append(evaluateItem(context), 0, 0);
        }
    }



}

