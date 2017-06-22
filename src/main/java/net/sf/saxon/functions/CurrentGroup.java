////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.DecimalValue;

/**
* Implements the XSLT function current-group()
*/

public class CurrentGroup extends SystemFunctionCall implements Callable {

    boolean is30 = false;

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        is30 = visitor.getStaticContext().getXPathLanguageLevel().equals(DecimalValue.THREE);
        super.checkArguments(visitor);
    }

    /**
     * Determine the dependencies
     */

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CURRENT_GROUP;
    }

    /**
    * Return an iteration over the result sequence
    */

    /*@NotNull*/
    public SequenceIterator<? extends Item> iterate(XPathContext c) throws XPathException {
        GroupIterator gi = c.getCurrentGroupIterator();
        if (gi==null || !gi.hasCurrentGroup()) {
            if (is30) {
                XPathException err = new XPathException("There is no current group", "XTDE1061");
                err.setLocator(this);
                throw err;
            } else {
                return EmptyIterator.emptyIterator();
            }
        }
        return gi.iterateCurrentGroup();
    }

    /**
     * Evaluate the expression
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments /*@NotNull*/) throws XPathException {
        return SequenceTool.toLazySequence(iterate(context));
    }
}

