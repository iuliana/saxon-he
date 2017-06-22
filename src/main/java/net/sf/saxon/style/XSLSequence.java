////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;


/**
 * An xsl:sequence element in the stylesheet. <br>
 * The xsl:sequence element takes attributes:<ul>
 * <li>a mandatory attribute select="expression".</li>
 * </ul>
 */

public final class XSLSequence extends StyleElement {

    private Expression select;

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
        if (select == null) {
            return AnyItemType.getInstance();
        }
        final TypeHierarchy th = getConfiguration().getTypeHierarchy();
        return select.getItemType(th);
    }

    /**
    * Determine whether this type of element is allowed to contain a sequence constructor
    * @return in XSLT 2.0, false. In XSLT 3.0 true: yes, it may contain a sequence constructor
    */

    public boolean mayContainSequenceConstructor() {
        return isXslt30Processor();
    }

    /**
    * Determine whether this type of element is allowed to contain an xsl:fallback
    * instruction
    */

    public boolean mayContainFallback() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

		String selectAtt = null;

		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.SELECT)) {
        		selectAtt = atts.getValue(a);
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (selectAtt!=null) {
            select = makeExpression(selectAtt);
        } else if (!isXslt30Processor()) {
            reportAbsence(StandardNames.SELECT);
            select = Literal.makeEmptySequence();
        }
    }

    public void validate(Declaration decl) throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while (true) {
            NodeInfo child = kids.next();
            if (child == null) break;
            if (!(child instanceof XSLFallback)) {
                if (isXslt30Processor()) {
                    if (select != null) {
                        compileError("An xsl:sequence element with a select attribute must be empty", "XTSE3185");
                    }
                } else {
                    compileError("The only child node allowed for xsl:sequence is an xsl:fallback instruction", "XTSE0010");
                }
                break;
            }
        }
        select = typeCheck("select", select);
    }

    /*@Nullable*/ public Expression compile(Executable exec, Declaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), false);
        }
        return select;
    }

}

