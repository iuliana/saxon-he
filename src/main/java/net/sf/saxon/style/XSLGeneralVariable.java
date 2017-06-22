////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.GeneralVariable;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
* This class defines common behaviour across xsl:variable, xsl:param, and xsl:with-param
*/

public abstract class XSLGeneralVariable extends StyleElement {

    protected SourceBinding sourceBinding = new SourceBinding(this);
    protected GeneralVariable compiledVariable = null;

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     * @return the item type returned. This is null for a variable: we are not
     * interested in the type of the variable, but in what the xsl:variable constributes
     * to the result of the sequence constructor it is part of.
     */

    protected ItemType getReturnedItemType() {
        return null;
    }

    /**
     * Get the source binding object that holds information about the declared variable.
     */

    public SourceBinding getSourceBinding() {
        return sourceBinding;
    }

    public StructuredQName getVariableQName() {
        return sourceBinding.getVariableQName();
    }

    /**
    * Determine whether this type of element is allowed to contain a template-body
    * @return true: yes, it may contain a template-body
    */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Test whether this is a global variable or parameter
     * @return true if this is global
     */

    public boolean isGlobal() {
        return isTopLevel();
            // might be called before the "global" field is initialized
    }

    /**
     * Check that the variable is not already declared, and allocate a slot number
     * @param decl the declaration being validated. A single XSLVariableDeclaration object may represent
     * multiple declarations if it appears in a stylesheet module that is included/imported more than once
     */

    public void validate(Declaration decl) throws XPathException {
        sourceBinding.validate();
        if (sourceBinding.hasProperty(SourceBinding.STATIC) && !isXslt30Processor()) {
            compileError("Static variables and parameters require XSLT 3.0 to be enabled");
        }
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     */

    public void postValidate() throws XPathException {
        sourceBinding.postValidate();
    }


    protected void setReferenceCount(GeneralVariable var) {
        // overridden in subclass
    }

    /**
     * Get the type of construct. This will be a constant in
     * class {@link net.sf.saxon.trace.Location}. This method is part of the
     * {@link net.sf.saxon.trace.InstructionInfo} interface
     */

    public int getConstructType() {
        return StandardNames.XSL_VARIABLE;
    }

    public GeneralVariable getCompiledVariable() {
        return compiledVariable;
    }


}

