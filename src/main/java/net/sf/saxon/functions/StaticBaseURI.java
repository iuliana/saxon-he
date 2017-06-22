////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.EmptySequence;

/**
 * This class supports the static-base-uri() function in XPath 2.0. The expression
 * is always evaluated at compile time.
*/

public class StaticBaseURI extends CompileTimeFunction {

    String staticBaseURI;

    /**
     * Bind aspects of the static context on which the particular function depends
     *
     * @param env the static context of the function call
     * @throws net.sf.saxon.trans.XPathException
     *          if execution with this static context will inevitably fail
     */
    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        if (staticBaseURI == null) {
            staticBaseURI = env.getBaseURI();
        }
    }

    /**
    * Compile time evaluation
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        if (staticBaseURI != null) {
            return Literal.makeLiteral(new AnyURIValue(staticBaseURI));
        }
        String baseURI = visitor.getStaticContext().getBaseURI();
        if (baseURI == null) {
            return Literal.makeEmptySequence();
        }
        return Literal.makeLiteral(new AnyURIValue(baseURI));
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        if (staticBaseURI == null) {
            return EmptySequence.getInstance();
        }
        return new AnyURIValue(staticBaseURI);
    }
}

