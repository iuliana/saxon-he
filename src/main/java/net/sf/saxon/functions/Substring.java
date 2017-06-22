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
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the XPath substring() function
 */

public class Substring extends SystemFunctionCall implements Callable {

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (argument[1] instanceof NumberFn) {
            Expression a1 = ((NumberFn)argument[1]).getArguments()[0];
            if (th.isSubType(a1.getItemType(th), BuiltInAtomicType.INTEGER)) {
                argument[1] = a1;
            }
        }
        if (argument.length > 2 && argument[2] instanceof NumberFn) {
            Expression a2 = ((NumberFn)argument[2]).getArguments()[0];
            if (th.isSubType(a2.getItemType(th), BuiltInAtomicType.INTEGER)) {
                argument[2] = a2;
            }
        }
        return this;
    }

    /**
    * Evaluate the function
    */

    public StringValue evaluateItem(XPathContext context) throws XPathException {

        AtomicValue av = (AtomicValue)argument[0].evaluateItem(context);
        if (av==null) {
            return StringValue.EMPTY_STRING;
        }
        StringValue sv = (StringValue)av;
        if (sv.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        AtomicValue a1 = (AtomicValue)argument[1].evaluateItem(context);
        NumericValue a = (NumericValue)a1;

        if (argument.length==2) {
            StringValue result = StringValue.makeStringValue(substring(sv, a));
            if (sv.isKnownToContainNoSurrogates()) {
                result.setContainsNoSurrogates();
            }
            return result;
        } else {
            AtomicValue b2 = (AtomicValue)argument[2].evaluateItem(context);
            NumericValue b = (NumericValue)b2;
            StringValue result = StringValue.makeStringValue(substring(sv, a, b, context));
            if (sv.isKnownToContainNoSurrogates()) {
                result.setContainsNoSurrogates();
            }
            return result;
        }
    }

    /**
     * Implement the substring function with two arguments.
     * @param sv the string value
     * @param start the numeric offset (1-based) of the first character to be included in the result
     * (if not an integer, the XPath rules apply)
     * @return the substring starting at this position.
    */

    public static UnicodeString substring(StringValue sv, NumericValue start) {
        UnicodeString s = sv.getUnicodeString();
        int slength = s.length();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value)start).longValue();
            if (lstart > slength) {
                return UnicodeString.EMPTY_STRING;
            } else if (lstart <= 0) {
                lstart = 1;
            }
        } else {
            //NumericValue rstart = start.round();
            // We need to be careful to handle cases such as plus/minus infinity
            if (start.isNaN()) {
                return UnicodeString.EMPTY_STRING;
            } else if (start.signum() <= 0) {
                return s;
            } else if (start.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                return UnicodeString.EMPTY_STRING;
            } else {
                lstart = Math.round(start.getDoubleValue());
            }
        }

        if (lstart > s.length()) {
            return UnicodeString.EMPTY_STRING;
        }
        return s.substring((int)lstart-1, s.length());
    }

    /**
     * Implement the substring function with three arguments.
     * @param sv the string value
     * @param start the numeric offset (1-based) of the first character to be included in the result
     * (if not an integer, the XPath rules apply)
     * @param len the length of the required substring (again, XPath rules apply)
     * @param context the XPath dynamic context. Provided because some arithmetic computations require it
     * @return the substring starting at this position.
    */

    public static UnicodeString substring(StringValue sv, NumericValue start, /*@NotNull*/ NumericValue len, XPathContext context) {

        CharSequence s = sv.getStringValueCS();
        int slength = s.length();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value)start).longValue();
            if (lstart > slength) {
                return UnicodeString.EMPTY_STRING;
            }
        } else {
            start = start.round(0);
            // TODO: convert directly to a long using Math.round, as in the 2-argument case
            // We need to be careful to handle cases such as plus/minus infinity and NaN
            if (start.isNaN()) {
                return UnicodeString.EMPTY_STRING;
            } else if (start.signum() <= 0) {
                lstart = 0;
            } else if (start.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                return UnicodeString.EMPTY_STRING;
            } else {
                try {
                    lstart = start.longValue();
                } catch (XPathException err) {
                    // this shouldn't happen unless the string length exceeds the bounds
                    // of a long
                    throw new AssertionError("string length out of permissible range");
                }
            }
        }

        NumericValue end;
        try {
            //end = start.arithmetic(Token.PLUS, len.round(), context);
            end = (NumericValue)ArithmeticExpression.compute(start, Calculator.PLUS, len.round(0), context);
        } catch (XPathException e) {
            throw new AssertionError("Unexpected arithmetic failure in substring");
        }
        long lend;
        if (end instanceof Int64Value) {
            //noinspection RedundantCast
            lend = ((Int64Value)end).longValue();
        } else {
            // We need to be careful to handle cases such as plus/minus infinity and NaN
            if (end.isNaN()) {
                return UnicodeString.EMPTY_STRING;
            } else if (end.signum() <= 0) {
                return UnicodeString.EMPTY_STRING;
            } else if (end.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                lend = slength + 1L;
            } else {
                try {
                    lend = end.ceiling().longValue();
                } catch (XPathException err) {
                    // this shouldn't happen unless the string length exceeds the bounds
                    // of a long
                    throw new AssertionError("string length out of permissible range");
                }
            }
        }

        if (lend < lstart) {
            return UnicodeString.EMPTY_STRING;
        }

        UnicodeString us = sv.getUnicodeString();
        int clength = us.length();
        int a1 = (int)lstart - 1;
        if (a1 >= clength) {
            return UnicodeString.EMPTY_STRING;
        }
        int a2 = Math.min(clength, (int)lend - 1);
        if (a1 < 0) {
            if (a2 < 0) {
                return UnicodeString.EMPTY_STRING;
            } else {
                a1 = 0;
            }
        }
        return us.substring(a1, a2);
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
        StringValue arg0 = (StringValue)arguments[0].head();
        NumericValue arg1 = (NumericValue)arguments[1].head();
        if (arguments.length == 2) {
            return StringValue.makeStringValue(substring(arg0, arg1));
        } else {
            NumericValue arg2 = (NumericValue)arguments[2].head();
            return StringValue.makeStringValue(substring(arg0, arg1, arg2, context));
        }
    }


}

