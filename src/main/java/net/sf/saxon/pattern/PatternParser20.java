////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionParser;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.Tokenizer;
import net.sf.saxon.functions.Doc;
import net.sf.saxon.functions.Id;
import net.sf.saxon.functions.KeyFn;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;

/**
 * Parser for XSLT patterns. This is created by overriding selected parts of the standard ExpressionParser.
 */

public class PatternParser20 extends ExpressionParser implements PatternParser {

    int inPredicate = 0;

    /**
     * Parse a string representing an XSLT pattern
     *
     * @param pattern the pattern expressed as a String
     * @param env     the static context for the pattern
     * @return a Pattern object representing the result of parsing
     * @throws XPathException if the pattern contains a syntax error
     */

    /*@NotNull*/
    public Pattern parsePattern(String pattern, StaticContext env) throws XPathException {
        this.env = env;
        nameChecker = env.getConfiguration().getNameChecker();
        language = XSLT_PATTERN;
        Expression exp = parse(pattern, 0, Token.EOF, env.getLineNumber(), env);
        exp.setContainer(defaultContainer);
        ExpressionVisitor visitor = ExpressionVisitor.make(env, exp.getExecutable());
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true);
        Pattern pat = PatternMaker.fromExpression(exp.simplify(visitor).typeCheck(visitor, cit), env.getConfiguration(), false);
        if (exp instanceof FilterExpression && pat instanceof ItemTypePattern) {
            // the pattern has been simplified but needs to retain a default priority based on its syntactic form (test match89)
            ((ItemTypePattern) pat).setPriority(0.5);
        }
        return pat;
    }

    /**
     * Callback to tailor the tokenizer
     */

    protected void customizeTokenizer(Tokenizer t) {
        t.disallowUnionKeyword = true;
    }

    /**
     * Override the parsing of top-level expressions
     *
     * @return the parsed expression
     * @throws XPathException
     */

    /*@NotNull*/
    public Expression parseExpression() throws XPathException {
        if (inPredicate > 0) {
            return super.parseExpression();
        } else {
            return parseBinaryExpression(parsePathExpression(), 10);
        }
    }

    /**
     * Parse a basic step expression (without the predicates)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseBasicStep(boolean firstInPattern) throws XPathException {
        if (inPredicate > 0) {
            return super.parseBasicStep(firstInPattern);
        } else {
            switch (t.currentToken) {
                case Token.DOLLAR:
                    grumble("A variable reference is not allowed in an XSLT pattern (except in a predicate)");
                    return null;

                case Token.LPAR:
                    grumble("Parentheses are not allowed in an XSLT 2.0 pattern");
                    return null;

                case Token.STRING_LITERAL:
                case Token.NUMBER:
                case Token.KEYWORD_CURLY:
                case Token.ELEMENT_QNAME:
                case Token.ATTRIBUTE_QNAME:
                case Token.NAMESPACE_QNAME:
                case Token.PI_QNAME:
                case Token.TAG:
                case Token.INLINE_FUNCTION_LITERAL:
                case Token.DOTDOT:
                    grumble("Token " + currentTokenDisplay() + " not allowed here in an XSLT pattern");
                    return null;

                case Token.FUNCTION:
                    if (!firstInPattern) {
                        grumble("In an XSLT pattern, a function call is allowed only as the first step in a path");
                    }
                    return super.parseBasicStep(firstInPattern);
                default:
                    return super.parseBasicStep(firstInPattern);

            }
        }
    }

    @Override
    protected void testPermittedAxis(byte axis) throws XPathException {
        if (inPredicate == 0) {
            if (axis != AxisInfo.CHILD && axis != AxisInfo.ATTRIBUTE) {
                grumble("Unless XSLT 3.0 is enabled, the only axes allowed in a pattern are the child and attribute axes");
            }

        }
    }

    /**
     * Parse a type pattern of the form "~ ItemType" (introduced in XSLT 3.0)
     *
     * @return the type pattern, wrapped in a PatternSponsor to satisfy the parsing interface
     * @throws XPathException if any error is found, for example if XSLT 3.0 is not enabled
     */

    /*@NotNull*/
    protected Expression parseTypePattern() throws XPathException {
        grumble("Type patterns (~ItemType) require XSLT 3.0 to be enabled");
        return null;
    }

    /**
     * Parse an expression appearing within a predicate. This enables full XPath parsing, without
     * the normal rules that apply within an XSLT pattern
     *
     * @return the parsed expression that appears within the predicate
     * @throws XPathException
     */

    /*@NotNull*/
    protected Expression parsePredicate() throws XPathException {
        boolean disallow = t.disallowUnionKeyword;
        t.disallowUnionKeyword = false;
        ++inPredicate;
        Expression exp = parseExpression();
        --inPredicate;
        t.disallowUnionKeyword = disallow;
        return exp;
    }

    /**
     * Parse a function call appearing within a pattern. Unless within a predicate, this
     * imposes the constraints on which function calls are allowed to appear in a pattern
     *
     * @return the expression that results from the parsing (usually a FunctionCall)
     * @throws XPathException
     */

    /*@NotNull*/
    protected Expression parseFunctionCall() throws XPathException {
        Expression fn = super.parseFunctionCall();
        if (inPredicate > 0) {
            return fn;
        } else {
            if (fn instanceof Id) {

                // Only one argument allowed, which must be a string literal or variable reference
                if (((Id) fn).getNumberOfArguments() != 1) {
                    grumble("id() in an XSLT 2.0 pattern must have only one argument");
                } else {
                    Expression arg = ((Id) fn).getArguments()[0];
                    if (!(arg instanceof VariableReference || arg instanceof StringLiteral)) {
                        grumble("Argument to id() in a pattern must be a variable reference or string literal");
                    }
                }

            } else if (fn instanceof KeyFn) {

                // Only two arguments allowed
                if (((KeyFn) fn).getNumberOfArguments() != 2) {
                    grumble("key() in an XSLT 2.0 pattern must have exactly two arguments");
                } else {
                    Expression arg0 = ((KeyFn) fn).getArguments()[0];
                    if (!(arg0 instanceof StringLiteral)) {
                        grumble("First argument to key() in an XSLT 2.0 pattern must be a string literal");
                    }
                    Expression arg1 = ((KeyFn) fn).getArguments()[1];
                    if (!(arg1 instanceof VariableReference || arg1 instanceof Literal)) {
                        grumble("Second argument to id() in an XSLT 2.0 pattern must be a variable reference or literal");
                    }
                }

            } else if (fn instanceof Doc) {

                grumble("The doc() function is not allowed in an XSLT 2.0 pattern");

            } else {
                grumble("The " + fn.toString() + " function is not allowed at the head of a pattern");
            }
        }
        return fn;
    }

    public Expression parseFunctionArgument() throws XPathException {
        if (inPredicate > 0) {
            return super.parseFunctionArgument();
        } else {
            switch (t.currentToken) {
                case Token.DOLLAR:
                    return parseVariableReference();

                case Token.STRING_LITERAL:
                    return parseStringLiteral();

                case Token.NUMBER:
                    return parseNumericLiteral();

                default:
                    grumble("A function argument in an XSLT pattern must be a variable reference or literal");
                    return null;
            }
        }
    }

    public Expression makeTracer(int startOffset, Expression exp, int construct, StructuredQName qName) {
        // Suppress tracing of pattern evaluation
        return exp;
    }
}

