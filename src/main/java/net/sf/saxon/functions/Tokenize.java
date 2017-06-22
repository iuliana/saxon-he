////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.EmptySequence;


/**
* This class implements the tokenize() function for regular expression matching. This returns a
* sequence of strings representing the unmatched substrings: the separators which match the
* regular expression are not returned.
*/

public class Tokenize extends SystemFunctionCall implements Callable {

    /*@Nullable*/ private RegularExpression regexp;
    private boolean allow30features = false;

    /**
    * Simplify and validate.
    * This is a pure function so it can be simplified in advance if the arguments are known
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        allow30features = DecimalValue.THREE.equals(visitor.getStaticContext().getXPathLanguageLevel());
        Expression e = simplifyArguments(visitor);
        if (e == this) {
            maybePrecompile(visitor);
        }
        return e;
    }

    private void maybePrecompile(ExpressionVisitor visitor) throws XPathException {
        // compile the regular expression once if possible
        if (regexp == null) {
            try {
                regexp = Matches.tryToCompile(argument, 1, 2, visitor.getStaticContext());
            } catch (XPathException err) {
                err.setLocator(this);
                throw err;
            }
            // check that it's not a pattern that matches ""
            if (regexp != null && regexp.matches("")) {
                XPathException err = new XPathException("The regular expression in tokenize() must not be one that matches a zero-length string");
                err.setErrorCode("FORX0003");
                err.setLocator(this);
                throw err;
            }
        }
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        // try once again to compile the regular expression once if possible
        // (used when the regex has been identified as a constant as a result of earlier rewrites)
        if (e == this) {
            maybePrecompile(visitor);
        }
        return e;
    }

    /**
     * Get the compiled regular expression if available, otherwise return null
     * @return the compiled regular expression, or null
     */

    public RegularExpression getCompiledRegularExpression() {
        return regexp;
    }

    /**
    * Iterate over the results of the function
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        AtomicValue sv = (AtomicValue)argument[0].evaluateItem(c);
        if (sv==null) {
            return EmptyIterator.getInstance();
        }
        CharSequence input = sv.getStringValueCS();
        if (input.length() == 0) {
            return EmptyIterator.getInstance();
        }

        RegularExpression re = regexp;
        if (re == null) {

            sv = (AtomicValue)argument[1].evaluateItem(c);
            CharSequence pattern = sv.getStringValueCS();

            CharSequence flags;
            if (argument.length==2) {
                flags = "";
            } else {
                sv = (AtomicValue)argument[2].evaluateItem(c);
                flags = sv.getStringValueCS();
            }

            re = buildRegex(c, pattern, flags);

        }
        return re.tokenize(input);
    }

    private RegularExpression buildRegex(XPathContext c, CharSequence pattern, CharSequence flags) throws XPathException {
        RegularExpression re;
        try {
            re = Configuration.getPlatform().compileRegularExpression(
                                    pattern, flags.toString(), (allow30features ? "XP30" : "XP20"), null);

        } catch (XPathException err) {
            XPathException de = new XPathException(err);
            de.setErrorCode("FORX0002");
            de.setXPathContext(c);
            de.setLocator(this);
            throw de;
        }
        // check that it's not a pattern that matches ""
        if (re.matches("")) {
            XPathException err = new XPathException("The regular expression in tokenize() must not be one that matches a zero-length string");
            err.setErrorCode("FORX0003");
            err.setLocator(this);
            throw err;
        }
        return re;
    }

    /**
     * Evaluate the expression
     *
     *
     *
 * @param context   the dynamic evaluation context
 * @param arguments the values of the arguments, supplied as SequenceIterators
 * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue sv = (AtomicValue)arguments[0].head();
        if (sv==null) {
            return EmptySequence.getInstance();
        }
        CharSequence input = sv.getStringValueCS();
        if (input.length() == 0) {
            return EmptySequence.getInstance();
        }

        RegularExpression re = regexp;
        if (re == null) {
            sv = (AtomicValue)arguments[1].head();
            CharSequence pattern = sv.getStringValueCS();

            CharSequence flags;
            if (argument.length==2) {
                flags = "";
            } else {
                sv = (AtomicValue)arguments[2].head();
                flags = sv.getStringValueCS();
            }

            re = buildRegex(context, pattern, flags);

        }
        return SequenceTool.toLazySequence(re.tokenize(input));
    }
}

