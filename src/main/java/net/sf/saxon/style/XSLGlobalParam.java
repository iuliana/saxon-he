////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SuppliedParameterReference;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
* An xsl:param element representing a global parameter (stylesheet parameter) in the stylesheet. <br>
* The xsl:param element has mandatory attribute name and optional attributes
 *  select, required, as, ...
*/

public class XSLGlobalParam extends XSLGlobalVariable {

    protected int getPermittedAttributes() {
        return
            SourceBinding.REQUIRED |
            SourceBinding.SELECT |
            SourceBinding.AS |
            SourceBinding.STATIC;
    }

    /*@Nullable*/ Expression conversion = null;

    public XSLGlobalParam() {
        sourceBinding.setProperty(SourceBinding.PARAM, true);
    }

    public void validate(Declaration decl) throws XPathException {

        if (sourceBinding.hasProperty(SourceBinding.REQUIRED)) {
            if (sourceBinding.getSelectExpression() != null) {
                // NB, we do this test before setting the default select attribute
                compileError("The select attribute should be omitted when required='yes'", "XTSE0010");
            }
            if (hasChildNodes()) {
                compileError("A parameter specifying required='yes' must have empty content", "XTSE0010");
            }
        }

        super.validate(decl);
    }


    /**
    * Compile a global xsl:param element: this ensures space is available for local variables declared within
    * this global variable
    */

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        if(sourceBinding.isStatic()) {
            super.compileDeclaration(exec, decl);
        }
        if (!redundant) {
            StructuredQName name = sourceBinding.getVariableQName();
            int slot = exec.getGlobalVariableMap().allocateSlotNumber(name);
            if (sourceBinding.getDeclaredType() != null) {
                SuppliedParameterReference pref = new SuppliedParameterReference(slot);
                pref.setLocationId(staticContext.getLocationMap().allocateLocationId(getSystemId(), getLineNumber()));
                RoleLocator role = new RoleLocator(RoleLocator.PARAM, name.getDisplayName(), 0);
                role.setErrorCode("XTTE0590");
                conversion = TypeChecker.staticTypeCheck(
                        pref,
                        sourceBinding.getDeclaredType(),
                        false,
                        role, makeExpressionVisitor());
            }

            sourceBinding.handleSequenceConstructor(exec, decl);

            GlobalParam binding = new GlobalParam();
            binding.setExecutable(getPreparedStylesheet());
            binding.setContainer(binding);
            if (sourceBinding.hasProperty(SourceBinding.REQUIRED) /*|| sourceBinding.hasProperty(SourceBinding.IMPLICITLY_REQUIRED)*/ ) {
                getPreparedStylesheet().addRequiredParam(sourceBinding.getVariableQName());
            }
            Expression select = sourceBinding.getSelectExpression();
            if (select != null) {
                select.setContainer(binding);
            }
            binding.setSelectExpression(select);
            binding.setVariableQName(sourceBinding.getVariableQName());
            initializeBinding(exec, decl, binding);

            binding.setSlotNumber(slot);
            binding.setRequiredType(getRequiredType());
            binding.setRequiredParam(sourceBinding.hasProperty(SourceBinding.REQUIRED));
            binding.setImplicitlyRequiredParam(sourceBinding.hasProperty(SourceBinding.IMPLICITLY_REQUIRED));
            sourceBinding.fixupBinding(binding);
            compiledVariable = binding;
        }
    }




    /**
    * Get the static type of the parameter. This is the declared type, because we cannot know
    * the actual value in advance.
    */

    public SequenceType getRequiredType() {
        SequenceType declaredType = sourceBinding.getDeclaredType();
        if (declaredType!=null) {
            return declaredType;
        } else {
            return SequenceType.ANY_SEQUENCE;
        }
    }

}

