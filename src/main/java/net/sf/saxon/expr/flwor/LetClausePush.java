////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 *  Implements the changes to a tuple stream effected by the Let clause in a FLWOR expression
 */
public class LetClausePush extends TuplePush {

    TuplePush destination;
    LetClause letClause;

    public LetClausePush(TuplePush destination, LetClause letClause) {
        this.destination = destination;
        this.letClause = letClause;
    }

    /*
     * Notify the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        Sequence val = ExpressionTool.lazyEvaluate(letClause.getSequence(), context, 100);
        // TODO: be smarter, see LetExpression.eval()
        context.setLocalVariable(letClause.getRangeVariable().getLocalSlotNumber(), val);
        destination.processTuple(context);
    }

    /*
     * Close the tuple stream
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

