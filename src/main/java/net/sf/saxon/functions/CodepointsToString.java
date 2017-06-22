////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the function fn:codepoints-to-string()
 */

public class CodepointsToString extends SystemFunctionCall implements Callable {

    /**
     * Pre-evaluate a function at compile time. Functions that do not allow
     * pre-evaluation, or that need access to context information, can override this method.
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        final XPathContext context = visitor.getStaticContext().makeEarlyEvaluationContext();
        return new StringLiteral(
            unicodeToString(((Literal)argument[0]).iterate(),
                    context.getConfiguration().getNameChecker()));
    }

    /**
    * Evaluate
    */

    public StringValue evaluateItem(/*@NotNull*/ XPathContext c) throws XPathException {
        return StringValue.makeStringValue(
                unicodeToString(argument[0].iterate(c), c.getConfiguration().getNameChecker()));
    }

    /**
    * Return the Unicode string corresponding to a given sequence of Unicode code values
     * @param chars iterator delivering the characters as integer values
     * @param checker used to test whether a character is valid in the appropriate XML version
     * @return the sequence of characters as a CharSequence
     * @throws net.sf.saxon.trans.XPathException if any of the integers is not the codepoint of a valid XML character
    */

    public static CharSequence unicodeToString(SequenceIterator chars, NameChecker checker) throws XPathException {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        while (true) {
            NumericValue nextInt = (NumericValue)chars.next();
            if (nextInt == null) {
                return sb.condense();
            }
            long next = nextInt.longValue();
            if (next < 0 || next > Integer.MAX_VALUE || !checker.isValidChar((int)next)) {
                throw new XPathException("Invalid XML character [x " + Integer.toHexString((int)next) + ']', "FOCH0001");
            }
            sb.appendWideChar((int)next);
        }
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
        SequenceIterator chars = arguments[0].iterate();
        return new StringValue(unicodeToString(chars, context.getConfiguration().getNameChecker()));
    }


}

