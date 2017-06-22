////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;


/**
* This class implements the matches() function for regular expression matching
*/

public class Matches extends SystemFunctionCall {

    private RegularExpression regexp;
    private boolean allow30features = false;
    

    /**
     * Simplify and validate.
     * This is a pure function so it can be simplified in advance if the arguments are known
     * @return the simplified expression
     * @throws XPathException if any error is found (e.g. invalid regular expression)
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        allow30features = DecimalValue.THREE.equals(visitor.getStaticContext().getXPathLanguageLevel());
        Expression e = simplifyArguments(visitor);
        // compile the regular expression once if possible
        if (e == this) {
            maybePrecompile(visitor);
        }
        return e;
    }

    /**
     * Precompile the regular expression if possible
     * @param visitor an expression visitor
     */

    private void maybePrecompile(ExpressionVisitor visitor) throws XPathException {
        if (regexp == null) {
            try {
                regexp = tryToCompile(argument, 1, 2, visitor.getStaticContext());
            } catch (XPathException err) {
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
     * Get the compiled regular expression, returning null if the regex has not been compiled
     * @return the compiled regular expression, or null
     */

    public RegularExpression getCompiledRegularExpression() {
        return regexp;
    }

    /**
     * Evaluate the matches() function to give a Boolean value.
     * @param c  The dynamic evaluation context
     * @return the result as a BooleanValue, or null to indicate the empty sequence
     * @throws XPathException on an error
     */

    /*@Nullable*/ public BooleanValue evaluateItem(XPathContext c) throws XPathException {
        AtomicValue sv0 = (AtomicValue)argument[0].evaluateItem(c);
        if (sv0==null) {
            sv0 = StringValue.EMPTY_STRING;
        }

        RegularExpression re = regexp;

        if (re == null) {
            AtomicValue pat = (AtomicValue)argument[1].evaluateItem(c);
            CharSequence flags;
            if (argument.length==2) {
                flags = "";
            } else {
                AtomicValue sv2 = (AtomicValue)argument[2].evaluateItem(c);
                if (sv2==null) return null;
                flags = sv2.getStringValueCS();
            }
            return BooleanValue.get(evalMatches(sv0, pat, flags, c));
        } else {
            return BooleanValue.get(re.containsMatch(sv0.getStringValueCS()));
        }
    }

    /**
     * Interface used by compiled bytecode
     * @param input the value to be tested
     * @param regex the regular expression
     * @param flags the flags
     * @param context the dynamic context
     * @return true if the string matches the regex
     * @throws XPathException
     */

    public boolean evalMatches(AtomicValue input, AtomicValue regex, CharSequence flags, XPathContext context) throws XPathException {
        RegularExpression re;

        if (regex==null) {
            return false;
        }

        try {
            String lang = (allow30features ? "XP30" : "XP20");
            if (context.getConfiguration().getXsdVersion() == Configuration.XSD11) {
                lang += "/XSD11";
            }
            re = new ARegularExpression(regex.getStringValue(), flags.toString(), lang, null);

        } catch (XPathException err) {
            XPathException de = new XPathException(err);
            de.maybeSetErrorCode("FORX0002");
            de.setXPathContext(context);
            throw de;
        }
        return re.containsMatch(input.getStringValueCS());
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
        AtomicValue sv0 = (AtomicValue)arguments[0].head();
        if (sv0==null) {
            sv0 = StringValue.EMPTY_STRING;
        }
        AtomicValue pat = (AtomicValue)arguments[1].head();
        CharSequence flags;
        if (arguments.length==2) {
            flags = "";
        } else {
            AtomicValue sv2 = (AtomicValue)arguments[2].head();
            if (sv2==null) {
                return EmptySequence.getInstance();
            }
            flags = sv2.getStringValueCS();
        }
        return BooleanValue.get(evalMatches(sv0, pat, flags, context));
    }

    /**
     * Temporary test rig, used to submit bug report to Sun
     */
//     public static void main(String[] args) throws Exception {
//
//        matches("\u212a", "K");
//        matches("\u212a", "[A-Z]");
//        matches("\u212a", "I|J|K|L");
//        matches("\u212a", "[IJKL]");
//        matches("\u212a", "k");
//        matches("\u212a", "[a-z]");
//        matches("\u212a", "i|j|k|l");
//        matches("\u212a", "[ijkl]");
//    }
//
//    private static void matches(String in, String pattern) {
//        System.err.println("Java version " + System.getProperty("java.version"));
//        int flags = Pattern.UNIX_LINES;
//        flags |= Pattern.CASE_INSENSITIVE;
//        flags |= Pattern.UNICODE_CASE;
//        Pattern p = Pattern.compile(pattern, flags);
//        boolean b = p.matcher(in).find();
//        System.err.println("Pattern " + pattern + ": " + (b ? " match" : "no match"));
//    }

//    Results of this test with JDK 1.5.0_05:
//
//    Pattern K:  match
//    Java version 1.5.0_05
//    Pattern [A-Z]: no match
//    Java version 1.5.0_05
//    Pattern I|J|K|L:  match
//    Java version 1.5.0_05
//    Pattern [IJKL]: no match
//    Java version 1.5.0_05
//    Pattern k:  match
//    Java version 1.5.0_05
//    Pattern [a-z]:  match
//    Java version 1.5.0_05
//    Pattern i|j|k|l:  match
//    Java version 1.5.0_05
//    Pattern [ijkl]: no match

    /**
     * Try to precompile the arguments to the function. This method is shared by
     * the implementations of the three XPath functions matches(), replace(), and
     * tokenize().
     * @param args the supplied arguments to the function, as an array
     * @param patternArg the position of the argument containing the regular expression
     * @param flagsArg the position of the argument containing the flags
     * @param env the static context
     * @return the compiled regular expression, or null indicating that the information
     * is not available statically so it cannot be precompiled
     * @throws XPathException if any failure occurs, in particular, if the regular
     * expression is invalid
     */

    public static RegularExpression tryToCompile(Expression[] args, int patternArg, int flagsArg, StaticContext env)
    throws XPathException {
        if (patternArg > args.length - 1) {
            // too few arguments were supplied; the error will be reported in due course
            return null;
        }
        String flagstr = null;
        if (args.length-1 < flagsArg) {
            flagstr = "";
        } else if (args[flagsArg] instanceof StringLiteral) {
            flagstr = ((StringLiteral)args[flagsArg]).getStringValue();
        }

        if (args[patternArg] instanceof StringLiteral && flagstr != null) {
            try {
                String in = ((StringLiteral)args[patternArg]).getStringValue();
                String hostLang = (DecimalValue.THREE.equals(env.getXPathLanguageLevel()) ? "XP30": "XP20");
                if (env.getConfiguration().getXsdVersion() == Configuration.XSD11) {
                    hostLang += "/XSD11";
                }
                List<String> warnings = new ArrayList<String>(1);
                RegularExpression re = Configuration.getPlatform().compileRegularExpression(in, flagstr, hostLang, warnings);
                for (String e : warnings) {
                    env.issueWarning(e, args[patternArg]);
                }
                return re;
            } catch (XPathException err) {
                err.maybeSetErrorCode("FORX0002");
                throw err;
            }
        } else {
            return null;
        }
    }




}

