////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionParser;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.functions.Concat;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
* This class represents an attribute value template. The class allows an AVT to be parsed, and
* can construct an Expression that returns the effective value of the AVT.
*
* This is an abstract class that is never instantiated, it contains static methods only.
*/

public abstract class AttributeValueTemplate {

    private AttributeValueTemplate() {}

    /**
     * Static factory method to create an AVT from an XSLT string representation.
    */
    public static Expression make(String avt, int lineNumber, ExpressionContext env) throws XPathException {

        List<Expression> components = new ArrayList<>(5);

        int i0, i1, i8, i9;
        int len = avt.length();
        int last = 0;
        ExpressionVisitor visitor = ExpressionVisitor.make(env, env.getExecutable());
        while (last < len) {

            i0 = avt.indexOf('{', last);
            i1 = avt.indexOf("{{", last);
            i8 = avt.indexOf('}', last);
            i9 = avt.indexOf("}}", last);

            if ((i0 < 0 || len < i0) && (i8 < 0 || len < i8)) {   // found end of string
                addStringComponent(components, avt, last, len);
                break;
            } else if (i8 >= 0 && (i0 < 0 || i8 < i0)) {             // found a "}"
                if (i8 != i9) {                        // a "}" that isn't a "}}"
                    XPathException err = new XPathException("Closing curly brace in attribute value template \"" + avt.substring(0, len) + "\" must be doubled");
                    err.setErrorCode("XTSE0370");
                    err.setIsStaticError(true);
                    throw err;
                }
                addStringComponent(components, avt, last, i8 + 1);
                last = i8 + 2;
            } else if (i1 >= 0 && i1 == i0) {              // found a doubled "{{"
                addStringComponent(components, avt, last, i1 + 1);
                last = i1 + 2;
            } else if (i0 >= 0) {                        // found a single "{"
                if (i0 > last) {
                    addStringComponent(components, avt, last, i0);
                }
                Expression exp;
                ExpressionParser parser = env.getConfiguration().newExpressionParser("XP", false, env.getXPathLanguageLevel());
                parser.setDefaultContainer(env.getStyleElement());
                parser.setLanguage(ExpressionParser.XPATH, env.getXPathLanguageLevel());
                exp = parser.parse(avt, i0 + 1, Token.RCURLY, lineNumber, env);
                exp = visitor.simplify(exp);
                last = parser.getTokenizer().currentTokenStartOffset + 1;

                if (env.getStyleElement() instanceof XSLAnalyzeString && isIntegerOrIntegerPair(exp)) {
                    env.issueWarning("Found {" + showIntegers(exp) + "} in regex attribute: perhaps {{" + showIntegers(exp) + "}} was intended? (The attribute is an AVT, so curly braces should be doubled)", exp);
                }

                if (env.isInBackwardsCompatibleMode()) {
                    components.add(makeFirstItem(exp, env));
                } else {
                    components.add(visitor.simplify(
                            XSLLeafNodeConstructor.makeSimpleContentConstructor(
                                    exp,
                                    new StringLiteral(StringValue.SINGLE_SPACE), env.getConfiguration())));
                }

            } else {
                throw new IllegalStateException("Internal error parsing AVT");
            }
        }

        // is it empty?

        if (components.size() == 0) {
            return new StringLiteral(StringValue.EMPTY_STRING);
        }

        // is it a single component?

        if (components.size() == 1) {
            return visitor.simplify((Expression) components.get(0));
        }

        // otherwise, return an expression that concatenates the components
        
        Expression[] args = new Expression[components.size()];
        components.toArray(args);
        Concat fn = (Concat) SystemFunctionCall.makeSystemFunction("concat", args);
        fn.setLocationId(env.getLocationMap().allocateLocationId(env.getSystemId(), lineNumber));
        return visitor.simplify(fn);

    }

    /**
     * Used to detect warning condition when braces are undoubled in the regex attribute of xsl:analyze-string
     * @param exp an expression
     * @return true if the expression is an integer literal or a pair of two integer literals
     */

    private static boolean isIntegerOrIntegerPair(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal)exp).getValue();
            if (val instanceof IntegerValue) {
                return true;
            }
            if (val.getLength() == 2) {
                if (val.itemAt(0) instanceof IntegerValue && val.itemAt(1) instanceof IntegerValue) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Used to report warning condition when braces are undoubled in the regex attribute of xsl:analyze-string
     * @param exp an expression
     * @return string representation of an integer literal or a pair of two integer literals
     */

    private static String showIntegers(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal)exp).getValue();
            if (val instanceof IntegerValue) {
                return val.toString();
            }
            if (val.getLength() == 2) {
                if (val.itemAt(0) instanceof IntegerValue && val.itemAt(1) instanceof IntegerValue) {
                    return val.itemAt(0).toString() + "," + val.itemAt(1).toString();
                }
            }
        }
        return "";
    }

    private static void addStringComponent(List<Expression> components, String avt, int start, int end) {
        if (start < end) {
            components.add(new StringLiteral(avt.substring(start, end)));
        }
    }

    /**
    * Make an expression that extracts the first item of a sequence, after atomization
    */

    /*@NotNull*/ public static Expression makeFirstItem(Expression exp, StaticContext env) {
       if(Literal.isEmptySequence(exp)){
    	   return exp;
       }
    	final TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
        if (!exp.getItemType(th).isPlainType()) {
            exp = Atomizer.makeAtomizer(exp);
        }
        if (Cardinality.allowsMany(exp.getCardinality())) {
            exp = FirstItemExpression.makeFirstItemExpression(exp);
        }
        if (!th.isSubType(exp.getItemType(th), BuiltInAtomicType.STRING)) {
            exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
            ((AtomicSequenceConverter)exp).allocateConverter(env.getConfiguration(), false);
        }
        return exp;
    }

}

