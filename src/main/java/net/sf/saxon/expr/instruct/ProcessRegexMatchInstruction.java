////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

/**
 *  An internal instruction used by the fn:analyze-string function to process a matching
 *  substring. The instruction generates the content of the fn:match output element, by
 *  generating startElement and endElement events at the start and end of a group.
 */
public class ProcessRegexMatchInstruction extends Instruction {

    NodeName groupNameCode = new FingerprintedQName("fn", NamespaceConstant.FN, "group");
    NodeName nrNameCode = new NoNamespaceName("nr");

    public ProcessRegexMatchInstruction(NamePool namePool) {
        groupNameCode.allocateNameCode(namePool);
        nrNameCode.allocateNameCode(namePool);
    }

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_FOCUS;
    }

    /*@Nullable*/ public TailCall processLeavingTail(XPathContext context) throws XPathException {
        SequenceIterator iter = context.getCurrentIterator();
        if (!(iter instanceof RegexIterator)) {
            throw new IllegalStateException("Current iterator should be a RegexIterator");
        }
        ((RegexIterator)iter).processMatchingSubstring(context, new RegexIterator.OnGroup() {
            
            public void onGroupStart(XPathContext c, int groupNumber) throws XPathException {
                Receiver out = c.getReceiver();
                out.startElement(groupNameCode, Untyped.getInstance(), 0, 0);
                out.attribute(nrNameCode, BuiltInAtomicType.UNTYPED_ATOMIC, ""+groupNumber, 0, 0);
            }

            public void onGroupEnd(XPathContext c, int groupNumber) throws XPathException{
                Receiver out = c.getReceiver();
                out.endElement();
            }
        });
        return null;
    }

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    /*@NotNull*/
    public Expression copy() {
        // Safe to return "this", because the only fields are constant for a Configuration
        return this;
    }



    public void explain(ExpressionPresenter out) {
        out.startElement("processRegexMatchingSubstring");
        out.endElement();
    }
}

