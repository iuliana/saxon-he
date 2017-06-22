////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.WithParam;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.value.SequenceType;

/**
* An xsl:with-param element in the stylesheet. <br>
* The xsl:with-param element has mandatory attribute name and optional attribute select
*/

public class XSLWithParam extends XSLGeneralVariable {

    private int allowedAttributes =
            SourceBinding.SELECT | SourceBinding.AS | SourceBinding.TUNNEL;

    @Override
    protected void prepareAttributes() throws XPathException {
        sourceBinding.prepareAttributes(allowedAttributes);
    }

    public boolean isTunnelParam() {
        return sourceBinding.hasProperty(SourceBinding.TUNNEL);
    }

    public void validate(Declaration decl) throws XPathException {
        super.validate(decl);

        // Check for duplicate parameter names

        AxisIterator iter = iterateAxis(AxisInfo.PRECEDING_SIBLING);
        while (true) {
            Item prev = iter.next();
            if (prev == null) {
                break;
            }
            if (prev instanceof XSLWithParam) {
                if (sourceBinding.getVariableQName().equals(((XSLWithParam)prev).sourceBinding.getVariableQName())) {
                    compileError("Duplicate parameter name", "XTSE0670");
                }
            }
        }

        // Register that the stylesheet uses tunnel parameters
        if (sourceBinding.hasProperty(SourceBinding.TUNNEL)) {
            getPreparedStylesheet().setUsesTunnelParameters();
        }

    }

    public void checkAgainstRequiredType(SequenceType required) throws XPathException {
        sourceBinding.checkAgainstRequiredType(required);
    }

    /*@NotNull*/ public WithParam compileWithParam(Executable exec, Declaration decl) throws XPathException {
        PrincipalStylesheetModule psm = getPrincipalStylesheetModule();

        sourceBinding.handleSequenceConstructor(exec, decl);

        WithParam inst = new WithParam();
        //inst.adoptChildExpression(select);
        inst.setSelectExpression(sourceBinding.getSelectExpression());
        inst.setParameterId(psm.allocateUniqueParameterNumber(sourceBinding.getVariableQName()));
        inst.setVariableQName(sourceBinding.getVariableQName());
        inst.setTunnel(sourceBinding.hasProperty(SourceBinding.TUNNEL));
        inst.setRequiredType(sourceBinding.getInferredType(true));
        //initializeBinding(exec, decl, inst);
        return inst;
    }

}

