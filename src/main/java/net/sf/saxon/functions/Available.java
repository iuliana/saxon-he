////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.trans.XPathException;

/**
* This class supports the XSLT element-available, function-available, and type-available functions.
*/

public abstract class Available extends SystemFunctionCall {

    protected NamespaceResolver nsContext;
    private transient boolean checked = false;



    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        // the second time checkArguments is called, it's a global check so the static context is inaccurate
        if (checked) {
            return;
        }
        checked = true;
        super.checkArguments(visitor);
        if (!(argument[0] instanceof Literal &&
                (argument.length==1 || argument[1] instanceof Literal))) {
            // we need to save the namespace context
            nsContext = visitor.getStaticContext().getNamespaceResolver();
        }
    }



}

