////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
* Handler for xsl:variable elements acting as local variable declarations in a stylesheet. <br>
* The xsl:variable element has mandatory attribute name and optional attribute select
*/

public class XSLLocalVariable extends XSLGeneralVariable {

    private static int permittedAttributes =
            SourceBinding.SELECT |
            SourceBinding.AS;

    /**
     * Ask whether this element contains a binding for a variable with a given name; and if it does,
     * return the source binding information
     * @param name the variable name
     * @return the binding information if this element binds a variable of this name; otherwise null
     */

    public SourceBinding getBindingInformation(StructuredQName name) {
        if (name.equals(sourceBinding.getVariableQName())) {
            return sourceBinding;
        } else {
            return null;
        }
    }

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction (well, it can be, anyway)
    */

    public boolean isInstruction() {
        return true;
    }

    @Override
    public void prepareAttributes() throws XPathException {
        sourceBinding.prepareAttributes(permittedAttributes);
    }

    /**
    * Get the static type of the variable. This is the declared type, unless the value
    * is statically known and constant, in which case it is the actual type of the value.
    */

    public SequenceType getRequiredType() {
        return sourceBinding.getInferredType(true);
    }

    @Override
    public void fixupReferences() throws XPathException {
        sourceBinding.fixupReferences();
        super.fixupReferences();
    }

    /**
     * Process this local variable declaration by expanding any sequence constructor and setting
     * the select expression to the result
     * @param exec the executable
     * @param decl the declaration being compiled
     * @throws XPathException if an error occurs
     */

    public void compileLocalVariable(Executable exec, Declaration decl) throws XPathException {

        //if (!sourceBinding.getReferences().isEmpty()) {
            sourceBinding.handleSequenceConstructor(exec, decl);
        //}

    }


}

