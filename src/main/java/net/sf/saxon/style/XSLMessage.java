////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.Message;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NamespaceException;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
* An xsl:message element in the stylesheet. <br>
*/

public final class XSLMessage extends StyleElement {

    private Expression terminate = null;
    private Expression select = null;
    private Expression errorCode = null;

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    /**
    * Determine whether this type of element is allowed to contain a template-body
    * @return true: yes, it may contain a template-body
    */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

        String terminateAtt = null;
        String selectAtt = null;
        String errorCodeAtt = null;
		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals("terminate")) {
        		terminateAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
            } else if (f.equals("error-code")) {
                errorCodeAtt = atts.getValue(a);
            } else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }
        if (selectAtt!=null) {
            select = makeExpression(selectAtt);
        }

        if (errorCodeAtt != null) {
            errorCode = makeAttributeValueTemplate(errorCodeAtt);
        }

        if (terminateAtt==null) {
            terminateAtt = "no";
        }

        checkAttributeValue("terminate", terminateAtt, true, StyleElement.YES_NO);
        terminate = makeAttributeValueTemplate(terminateAtt);
    }

    public void validate(Declaration decl) throws XPathException {
        select = typeCheck("select", select);
        terminate = typeCheck("terminate", terminate);
        if (errorCode != null && !isXslt30Processor()) {
            // we have a 2.0 processor
            if (forwardsCompatibleModeIsEnabled()) {
                compileWarning("xsl:message/@error-code is ignored in forwards-compatibility mode", "");
            } else {
                compileError("The xsl:message/@error-code attribute is not recognized by an XSLT 2.0 processor");
            }
        }
        if (errorCode == null) {
            errorCode = new StringLiteral("Q{http://www.w3.org/2005/xqt-errors}XTMM9000");
        } else {
            errorCode = typeCheck("error-code", errorCode);
        }
    }

	public Expression compile(Executable exec, Declaration decl) throws XPathException {
		Expression b = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), true);
		if (b != null) {
			if (select == null) {
				select = b;
			} else {
				select = Block.makeBlock(select, b);
				select.setLocationId(allocateLocationId(getSystemId(), getLineNumber()));
			}
		}
		if (select == null) {
			select = new StringLiteral("xsl:message (no content)");
		}
		if (errorCode instanceof StringLiteral) {
			// resolve any QName prefix now
			String code = ((StringLiteral) errorCode).getStringValue();
			if (code.indexOf(':') >= 0 && !code.startsWith("Q{")) {
				try {
					StructuredQName name = makeQName(code);
					errorCode = new StringLiteral("Q" + name.getClarkName());
				} catch (NamespaceException err) {
					errorCode = new StringLiteral("Q{http://www.w3.org/2005/xqt-errors}XTMM9000");
				}
			}
		}
		Message inst = new Message(select, terminate, errorCode);
		if (!(errorCode instanceof StringLiteral)) {
			// evaluation of the error code may need the namespace context
			inst.setNamespaceResolver(makeNamespaceContext());
		}
		return inst;
	}

}
