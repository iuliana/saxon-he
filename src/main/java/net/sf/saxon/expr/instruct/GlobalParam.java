////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;

/**
* The compiled form of a global xsl:param element in an XSLT stylesheet or an
* external variable declared in the prolog of a Query. <br>
* The xsl:param element in XSLT has mandatory attribute name and optional attribute select. It can also
* be specified as required="yes" or required="no". In standard XQuery 1.0 external variables are always required,
* and no default value can be specified; but Saxon provides an extension pragma that allows a query
* to specify a default. XQuery 1.1 adds standard syntax for defining a default value.
*/

public final class GlobalParam extends GlobalVariable {

    public GlobalParam() {}

    /**
    * Get the name of this instruction for diagnostic and tracing purposes
    */

    public int getInstructionNameCode() {
        return StandardNames.XSL_PARAM;
    }

    /**
    * Evaluate the variable
    */

    @Override
    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Bindery b = controller.getBindery();
        boolean wasSupplied;
        try {
            wasSupplied = b.useGlobalParameter(
                    getVariableQName(), getSlotNumber(), getRequiredType(), context);
        } catch (XPathException e) {
            e.setLocator(this);
            throw e;
        }

        Sequence val = b.getGlobalVariableValue(this);
        if (wasSupplied || val!=null) {
            return val;
        } else {
            if (isRequiredParam()) {
                XPathException e = new XPathException("No value supplied for required parameter $" +
                        getVariableQName().getDisplayName());
                e.setXPathContext(context);
                e.setLocator(this);
                e.setErrorCode(getHostLanguage() == Configuration.XSLT ? "XTDE0050" : "XPDY0002");
                throw e;
            } else if (isImplicitlyRequiredParam()) {
                XPathException e = new XPathException("A value must be supplied for parameter $" +
                        getVariableQName().getDisplayName() +
                        " because there is no default value for the required type");
                e.setXPathContext(context);
                e.setLocator(this);
                e.setErrorCode("XTDE0610");
                throw e;
            }
            // evaluate and save the default value
            return actuallyEvaluate(context);
        }
    }

}

