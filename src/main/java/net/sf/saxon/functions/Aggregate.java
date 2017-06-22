////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.trans.XPathException;

/**
 * This abstract class provides functionality common to the sum(), avg(), count(),
 * exists(), and empty() functions. These all take a sequence as input and produce a singleton
 * as output; and they are all independent of the ordering of the items in the input.
*/

public abstract class Aggregate extends SystemFunctionCall {

    /**
     * Static analysis: prevent sorting of the argument
     */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        argument[0] = ExpressionTool.unsorted(opt, argument[0], true);
        // we don't care about the order of the results, but we do care about how many nodes there are
    }

//#ifdefined STREAM
    /**
     * Get the "sweep" of this expression as defined in the W3C streamability specifications.
     * This provides an assessment of stylesheet code against the W3C criteria for guaranteed
     * streamability, and is implemented to allow these criteria to be tested. It is not the
     * case that all expression that emerge as streamable from this analysis are currently
     * capable of being streamed by Saxon
     *
     * @param syntacticContext one of the values {@link #NAVIGATION_CONTEXT},
     *                         {@link #NODE_VALUE_CONTEXT}, {@link #INHERITED_CONTEXT}, {@link #INSPECTION_CONTEXT}
     * @param allowExtensions  if false, the definition of "guaranteed streamability" in the
     *                         W3C specification is used. If true, Saxon extensions are permitted, which make some
     * @param reasons          the caller may supply a list, in which case the implementation may add to this
     *                         list a message explaining why the construct is not streamable, suitable for inclusion in an
     *                         error message.
     * @return one of the values {@link #W3C_MOTIONLESS}, {@link #W3C_CONSUMING},
     *         {@link #W3C_GROUP_CONSUMING}, {@link #W3C_FREE_RANGING}
     */
//    @Override
//    public int getStreamability(int syntacticContext, boolean allowExtensions, List<String> reasons) {
//        //return argument[0].getStreamability(ATOMIZING_CONTEXT, allowExtensions, reasons);
//        return W3C_CONSUMING;
//    }
//#endif

}

