////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.BooleanValue;

/**
* An xsl:choose elements in the stylesheet. <br>
*/

public class XSLChoose extends StyleElement {

    private StyleElement otherwise;
    private int numberOfWhens = 0;

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return getCommonChildItemType();
    }

    public void prepareAttributes() throws XPathException {
		AttributeCollection atts = getAttributeList();
		for (int a=0; a<atts.getLength(); a++) {
        	checkUnknownAttribute(atts.getNodeName(a));
        }
    }

    public void validate(Declaration decl) throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while(true) {
            NodeInfo curr = kids.next();
            if (curr == null) {
                break;
            }
            if (curr instanceof XSLWhen) {
                if (otherwise!=null) {
                    otherwise.compileError("xsl:otherwise must come last", "XTSE0010");
                }
                numberOfWhens++;
            } else if (curr instanceof XSLOtherwise) {
                if (otherwise!=null) {
                    ((XSLOtherwise)curr).compileError("Only one xsl:otherwise is allowed in an xsl:choose", "XTSE0010");
                } else {
                    otherwise = (StyleElement)curr;
                }
            } else if (curr instanceof StyleElement) {
                ((StyleElement)curr).compileError("Only xsl:when and xsl:otherwise are allowed here", "XTSE0010");
            } else {
                compileError("Only xsl:when and xsl:otherwise are allowed within xsl:choose", "XTSE0010");
            }
        }

        if (numberOfWhens==0) {
            compileError("xsl:choose must contain at least one xsl:when", "XTSE0010");
        }
    }

    /**
    * Mark tail-recursive calls on templates and functions.
    */

    public boolean markTailCalls() {
        boolean found = false;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while(true) {
            NodeInfo curr = kids.next();
            if (curr == null) {
                return found;
            }
            if (curr instanceof StyleElement) {
                found |= ((StyleElement)curr).markTailCalls();
            }
        }
    }


    /*@Nullable*/ public Expression compile(Executable exec, Declaration decl) throws XPathException {

        int entries = numberOfWhens + (otherwise==null ? 0 : 1);
        Expression[] conditions = new Expression[entries];
        Expression[] actions = new Expression[entries];

        int w = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while(true) {
            NodeInfo curr = kids.next();
            if (curr == null) {
                break;
            }
            if (curr instanceof XSLWhen) {
                conditions[w] = ((XSLWhen)curr).getCondition();
                Expression b = ((XSLWhen)curr).compileSequenceConstructor(
                        exec, decl, curr.iterateAxis(AxisInfo.CHILD), true);
                if (b == null) {
                    b = Literal.makeEmptySequence();
                }
                try {
                    b = makeExpressionVisitor().simplify(b);
                    actions[w] = b;
                } catch (XPathException e) {
                    compileError(e);
                }

                if (getPreparedStylesheet().isCompileWithTracing()) {
                    actions[w] = makeTraceInstruction((XSLWhen)curr, actions[w]);
                }

                // Optimize for constant conditions (true or false)
                if (conditions[w] instanceof Literal && ((Literal)conditions[w]).getValue() instanceof BooleanValue) {
                    if (((BooleanValue)((Literal)conditions[w]).getValue()).getBooleanValue()) {
                        // constant true: truncate the tests here
                        entries = w+1;
                        break;
                    } else {
                        // constant false: omit this test
                        w--;
                        entries--;
                    }
                }
                w++;
            } else if (curr instanceof XSLOtherwise) {
                conditions[w] = Literal.makeLiteral(BooleanValue.TRUE);
                Expression b = ((XSLOtherwise)curr).compileSequenceConstructor(
                        exec, decl, curr.iterateAxis(AxisInfo.CHILD), true);
                if (b == null) {
                    b = Literal.makeEmptySequence();
                }
                try {
                    b = makeExpressionVisitor().simplify(b);
                    actions[w] = b;
                } catch (XPathException e) {
                    compileError(e);
                }
                if (getPreparedStylesheet().isCompileWithTracing()) {
                    actions[w] = makeTraceInstruction((XSLOtherwise)curr, actions[w]);
                }
                w++;
            } else {
                // Ignore: problem has already been reported.
            }
        }

        if (conditions.length != entries) {
            // we've optimized some entries away
            if (entries==0) {
                return null; // return a no-op
            }
            if (entries==1 && (conditions[0] instanceof Literal) &&
                    ((Literal)conditions[0]).getValue() instanceof BooleanValue) {
                if (((BooleanValue)((Literal)conditions[0]).getValue()).getBooleanValue()) {
                    // only one condition left, and it's known to be true: return the corresponding action
                    return actions[0];
                } else {
                    // but if it's false, return a no-op
                    return null;
                }
            }
            Expression[] conditions2 = new Expression[entries];
            System.arraycopy(conditions, 0, conditions2, 0, entries);
            Expression[] actions2 = new Expression[entries];
            System.arraycopy(actions, 0, actions2, 0, entries);
            conditions = conditions2;
            actions = actions2;
        }

        return new Choose(conditions, actions);
    }

}

