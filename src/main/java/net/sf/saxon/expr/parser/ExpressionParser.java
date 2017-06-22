////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.CastableToUnion;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntArraySet;
import net.sf.saxon.z.IntSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

import static net.sf.saxon.type.BuiltInAtomicType.*;

/**
 * Parser for XPath expressions and XSLT patterns.
 * <p/>
 * This code was originally inspired by James Clark's xt but has been totally rewritten (several times)
 * <p/>
 * The base class handles parsing of XPath 2.0 and XPath 3.0 syntax (switched by a languageVersion variable).
 * Subclasses refine this to handle XQuery syntax (1.0 and 3.0) and XQuery Update syntax.
 *
 * @author Michael Kay
 */


public class ExpressionParser {

    protected Tokenizer t;
    protected StaticContext env;
    protected Stack<Binding> rangeVariables = new Stack<Binding>();
    // The stack holds a list of range variables that are in scope.
    // Each entry on the stack is a Binding object containing details
    // of the variable.

    protected Container defaultContainer;
    protected NameChecker nameChecker;

    protected boolean allowXPath30Syntax = false;

    protected boolean scanOnly = false;
    // scanOnly is set to true while attributes in direct element constructors
    // are being processed. We need to parse enclosed expressions in the attribute
    // in order to find the end of the attribute value, but we don't yet know the
    // full namespace context at this stage.

    /*@Nullable*/
    protected CodeInjector codeInjector = null;

    protected int language = XPATH;     // know which language we are parsing, for diagnostics
    public static final int XPATH = 0;
    public static final int XSLT_PATTERN = 1;
    public static final int SEQUENCE_TYPE = 2;
    public static final int XQUERY = 3;

    protected DecimalValue languageVersion = DecimalValue.TWO;
    protected int catchDepth = 0;

    /**
     * Create an expression parser
     */

    public ExpressionParser() {
    }

    /**
     * Set a CodeInjector which can be used to modify or wrap expressions on the tree
     * as the expression is parsed and the tree is constructed. This is typically used
     * to add tracing code.
     *
     * @param injector the code injector to be used
     */

    public void setCodeInjector(/*@Nullable*/ CodeInjector injector) {
        this.codeInjector = injector;
    }

    /**
     * Set a CodeInjector which can be used to modify or wrap expressions on the tree
     * as the expression is parsed and the tree is constructed. This is typically used
     * to add tracing code.
     *
     * @return the code injector in use, if any; or null otherwise
     */

    /*@Nullable*/
    public CodeInjector getCodeInjector() {
        return codeInjector;
    }

    /**
     * Get the tokenizer (the lexical analyzer)
     *
     * @return the tokenizer (the lexical analyzer)
     */

    public Tokenizer getTokenizer() {
        return t;
    }

    /**
     * Get the static context used by this expression parser
     *
     * @return the static context
     */

    public StaticContext getStaticContext() {
        return env;
    }

    /**
     * Set the default container for newly constructed expressions
     *
     * @param container the default container
     */

    public void setDefaultContainer(Container container) {
        this.defaultContainer = container;
    }

    /**
     * Get the default container for newly constructed expressions
     *
     * @return the default container
     */

    public Container getDefaultContainer() {
        return defaultContainer;
    }

    /**
     * Get the name checker used by this parser
     *
     * @return the name checker (specific to XML 1.0o
     */

    public NameChecker getNameChecker() {
        return nameChecker;
    }

    public void setCatchDepth(int depth) {
        catchDepth = depth;
    }

    /**
     * Read the next token, catching any exception thrown by the tokenizer
     *
     * @throws XPathException if an invalid token is found
     */

    public void nextToken() throws XPathException {
        try {
            t.next();
            if ((t.currentToken == Token.NAME || t.currentToken == Token.FUNCTION) &&
                    t.currentTokenValue.startsWith("{")) {
                if (allowXPath30Syntax) {
                    t.currentTokenValue = normalizeEQName(t.currentTokenValue);
                } else {
                    throw new XPathException(
                            "The expanded QName syntax \"uri\":local is not allowed in this version of XPath/XQuery");
                }
            }
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
    }

    /**
     * Expect a given token; fail if the current token is different. Note that this method
     * does not read any tokens.
     *
     * @param token the expected token
     * @throws XPathException if the current token is not the expected
     *                        token
     */

    public void expect(int token) throws XPathException {
        if (t.currentToken != token) {
            grumble("expected \"" + Token.tokens[token] +
                    "\", found " + currentTokenDisplay());
        }
    }

    /**
     * Report a syntax error (a static error with error code XPST0003)
     *
     * @param message the error message
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message) throws XPathException {
        grumble(message, (language == XSLT_PATTERN ? "XTSE0340" : "XPST0003"));
    }

    /**
     * Report a static error
     *
     * @param message   the error message
     * @param errorCode the error code
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message, String errorCode) throws XPathException {
        grumble(message, new StructuredQName("", NamespaceConstant.ERR, errorCode), -1);
    }

    /**
     * Report a static error, with location information
     *
     * @param message   the error message
     * @param errorCode the error code
     * @param offset    the coded location of the error, or -1 if the location of the current token should be used
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message, String errorCode, int offset) throws XPathException {
        grumble(message, new StructuredQName("", NamespaceConstant.ERR, errorCode), offset);
    }

    /**
     * Report a static error
     *
     * @param message   the error message
     * @param errorCode the error code
     * @param offset    the coded location of the error, or -1 if the location of the current token should be used
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    protected void grumble(String message, /*@Nullable*/ StructuredQName errorCode, int offset) throws XPathException {
        if (errorCode == null) {
            errorCode = new StructuredQName("err", NamespaceConstant.ERR, "XPST0003");
        }
        String s = t.recentText(-1);
        int line;
        int column;
        if (offset == -1) {
            line = t.getLineNumber();
            column = t.getColumnNumber();
        } else {
            line = t.getLineNumber(offset);
            column = t.getColumnNumber(offset);
        }
        String lineInfo = (line == 1 ? "" : ("on line " + line + ' '));
        String columnInfo = "at char " + column + ' ';
        String prefix = getLanguage() + " syntax error " + columnInfo + lineInfo +
                (s.startsWith("...") ? "near" : "in") +
                ' ' + Err.wrap(s) + ":\n    ";
        XPathException err = new XPathException(message);
        err.setAdditionalLocationText(prefix);
        err.setIsStaticError(true);
        err.setErrorCodeQName(errorCode);
        throw err;
    }

    /**
     * Output a warning message
     *
     * @param message the text of the message
     * @throws XPathException if the message cannot be output
     */

    protected void warning(/*@NotNull*/ String message) throws XPathException {
        String s = t.recentText(-1);
        int line = t.getLineNumber();
        String lineInfo = (line == 1 ? "" : ("on line " + line + ' '));
        String prefix = lineInfo +
                (message.startsWith("...") ? "near" : "in") +
                ' ' + Err.wrap(s) + ":\n    ";
        env.issueWarning(prefix + message, null);
    }

    /**
     * Set the current language (XPath or XQuery, XSLT Pattern, or SequenceType)
     *
     * @param language one of the constants {@link #XPATH}, {@link #XQUERY}, {@link #XSLT_PATTERN}, {@link #SEQUENCE_TYPE}
     * @param version  The XPath or XQuery language version. For XQuery the value must be
     *                 "1.0" or "3.0; for XPath it must be "2.0" or "3.0". Currently
     *                 support for XQuery 3.0 and XPath 3.0 is incomplete: check the release notes.
     */

    public void setLanguage(int language, DecimalValue version) {
        switch (language) {
            case XPATH:
            case XSLT_PATTERN:
            case SEQUENCE_TYPE:
                if (DecimalValue.TWO_POINT_ONE.equals(version)) {
                    // temporarily until things stabilise
                    version = DecimalValue.THREE;
                }
                if (!(DecimalValue.TWO.equals(version) || DecimalValue.THREE.equals(version))) {
                    throw new IllegalArgumentException("Unsupported language version " + version);
                }
                break;
            case XQUERY:
                if (DecimalValue.ONE_POINT_ONE.equals(version)) {
                    // temporarily until things stabilise
                    version = DecimalValue.THREE;
                }
                if (!(DecimalValue.ONE.equals(version) || DecimalValue.THREE.equals(version))) {
                    throw new IllegalArgumentException("Unsupported language version " + version);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown language " + language);
        }
        this.language = language;
        this.languageVersion = version;
        this.allowXPath30Syntax = DecimalValue.THREE.equals(languageVersion);
    }

    /**
     * Get the current language (XPath or XQuery)
     *
     * @return a string representation of the language being parsed, for use in error messages
     */

    protected String getLanguage() {
        switch (language) {
            case XPATH:
                return "XPath";
            case XSLT_PATTERN:
                return "XSLT Pattern";
            case SEQUENCE_TYPE:
                return "SequenceType";
            case XQUERY:
                return "XQuery";
            default:
                return "XPath";
        }
    }

    /**
     * Display the current token in an error message
     *
     * @return the display representation of the token
     */
    /*@NotNull*/
    protected String currentTokenDisplay() {
        if (t.currentToken == Token.NAME) {
            return "name \"" + t.currentTokenValue + '\"';
        } else if (t.currentToken == Token.UNKNOWN) {
            return "(unknown token)";
        } else {
            return '\"' + Token.tokens[t.currentToken] + '\"';
        }
    }

    /**
     * Parse a string representing an expression. This will accept an XPath expression if called on an
     * ExpressionParser, or an XQuery expression if called on a QueryParser.
     *
     * @param expression the expression expressed as a String
     * @param start      offset within the string where parsing is to start
     * @param terminator character to treat as terminating the expression
     * @param lineNumber location of the start of the expression, for diagnostics
     * @param env        the static context for the expression
     * @return an Expression object representing the result of parsing
     * @throws XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    public Expression parse(String expression, int start, int terminator, int lineNumber, /*@NotNull*/ StaticContext env)
            throws XPathException {
        // System.err.println("Parse expression: " + expression);
        this.env = env;

        //defaultContainer = new TemporaryContainer(env.getLocationMap(), 1);
        nameChecker = env.getConfiguration().getNameChecker();
        t = new Tokenizer();
        customizeTokenizer(t);
        try {
            t.tokenize(expression, start, -1, lineNumber);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        Expression exp = parseExpression();
        if (t.currentToken != terminator) {
            if (t.currentToken == Token.EOF && terminator == Token.RCURLY) {
                grumble("Missing curly brace after expression in attribute value template", "XTSE0350");
            } else {
                grumble("Unexpected token " + currentTokenDisplay() + " beyond end of expression");
            }
        }
        return exp;
    }

    /**
     * Callback to tailor the tokenizer
     *
     * @param t the Tokenizer to be customized
     */

    protected void customizeTokenizer(Tokenizer t) {
        // do nothing
    }


    /**
     * Parse a string representing a sequence type
     *
     * @param input the string, which should conform to the XPath SequenceType
     *              production
     * @param env   the static context
     * @return a SequenceType object representing the type
     * @throws XPathException if any error is encountered
     */

    public SequenceType parseSequenceType(String input, /*@NotNull*/ StaticContext env) throws XPathException {
        this.env = env;
        nameChecker = env.getConfiguration().getNameChecker();
        language = SEQUENCE_TYPE;
        t = new Tokenizer();
        try {
            t.tokenize(input, 0, -1, 1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        SequenceType req = parseSequenceType();
        if (t.currentToken != Token.EOF) {
            grumble("Unexpected token " + currentTokenDisplay() + " beyond end of SequenceType");
        }
        return req;
    }


    //////////////////////////////////////////////////////////////////////////////////
    //                     EXPRESSIONS                                              //
    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Parse a top-level Expression:
     * ExprSingle ( ',' ExprSingle )*
     *
     * @return the Expression object that results from parsing
     * @throws XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    public Expression parseExpression() throws XPathException {
        Expression exp = parseExprSingle();
        ArrayList<Expression> list = null;
        while (t.currentToken == Token.COMMA) {
            // An expression containing a comma often contains many, so we accumulate all the
            // subexpressions into a list before creating the Block expression which reduces it to an array
            if (list == null) {
                list = new ArrayList<Expression>(10);
                list.add(exp);
            }
            nextToken();
            Expression next = parseExprSingle();
            setLocation(next);
            list.add(next);
        }
        if (list != null) {
            exp = Block.makeBlock(list);
            setLocation(exp);
        }
        return exp;
    }

    /**
     * Parse an ExprSingle
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    public Expression parseExprSingle() throws XPathException {
        switch (t.currentToken) {
            case Token.FOR:
            case Token.LET:
            case Token.FOR_SLIDING:
            case Token.FOR_TUMBLING:
                return parseFLWORExpression();
            case Token.SOME:
            case Token.EVERY:
                return parseQuantifiedExpression();
            case Token.IF:
                return parseIfExpression();
            case Token.SWITCH:
                return parseSwitchExpression();
            case Token.TYPESWITCH:
                return parseTypeswitchExpression();
            case Token.VALIDATE:
            case Token.VALIDATE_STRICT:
            case Token.VALIDATE_LAX:
            case Token.VALIDATE_TYPE:
                return parseValidateExpression();
            case Token.PRAGMA:
                return parseExtensionExpression();  // XQuery only
            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("try")) {
                    return parseTryCatchExpression();
                } else if (t.currentTokenValue.equals("map")) {
                    return parseMapExpression();
                }
                // else drop through

            default:
                return parseBinaryExpression(parseUnaryExpression(), 4);
        }
    }

    /**
     * Parse a binary expression, using operator precedence parsing. This is used
     * to parse the part of the grammary consisting largely of binary operators
     * distinguished by precedence: from "or expressions" down to "unary expressions".
     * Algorithm for the mainstream binary operators is from Wikipedia article
     * on precedence parsing;  operator precedences are from the XQuery specification
     * appendix B.
     *
     * @param lhs           Left-hand side "basic expression"
     * @param minPrecedence the minimum precedence of an operator that is to be treated as not terminating the
     *                      current expression
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    public Expression parseBinaryExpression(Expression lhs, int minPrecedence) throws XPathException {
        while (getCurrentOperatorPrecedence() >= minPrecedence) {
            int operator = t.currentToken;
            int prec = getCurrentOperatorPrecedence();
            switch (operator) {
                case Token.INSTANCE_OF:
                case Token.TREAT_AS:
                    nextToken();
                    SequenceType seq = parseSequenceType();
                    lhs = makeSequenceTypeExpression(lhs, operator, seq);
                    setLocation(lhs);
                    if (getCurrentOperatorPrecedence() >= prec) {
                        grumble("Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses");
                    }
                    break;
                case Token.CAST_AS:
                case Token.CASTABLE_AS:
                    nextToken();
                    expect(Token.NAME);
                    SimpleType at = getSimpleType(t.currentTokenValue);
                    if (at == ANY_ATOMIC) {
                        grumble("No value is castable to xs:anyAtomicType", "XPST0080");
                    }
                    if (at == NOTATION) {
                        grumble("No value is castable to xs:NOTATION", "XPST0080");
                    }
                    nextToken();
                    boolean allowEmpty = (t.currentToken == Token.QMARK);
                    if (allowEmpty) {
                        nextToken();
                    }
                    lhs = makeSingleTypeExpression(lhs, operator, at, allowEmpty);
                    setLocation(lhs);
                    if (getCurrentOperatorPrecedence() >= prec) {
                        grumble("Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses");
                    }
                    break;
                default:
                    nextToken();
                    Expression rhs = parseUnaryExpression();
                    while (getCurrentOperatorPrecedence() > prec) {
                        rhs = parseBinaryExpression(rhs, getCurrentOperatorPrecedence());
                    }
                    lhs = makeBinaryExpression(lhs, operator, rhs);
                    setLocation(lhs);
            }
        }
        return lhs;
    }

    private int getCurrentOperatorPrecedence() {
        switch (t.currentToken) {
            case Token.OR:
                return 4;
            case Token.AND:
                return 5;
            case Token.FEQ:
            case Token.FNE:
            case Token.FLE:
            case Token.FLT:
            case Token.FGE:
            case Token.FGT:
            case Token.EQUALS:
            case Token.NE:
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
            case Token.IS:
            case Token.PRECEDES:
            case Token.FOLLOWS:
                return 6;
            case Token.CONCAT:
                return 7;
            case Token.TO:
                return 8;
            case Token.PLUS:
            case Token.MINUS:
                return 9;
            case Token.MULT:
            case Token.DIV:
            case Token.IDIV:
            case Token.MOD:
                return 10;
            case Token.UNION:
                return 11;
            case Token.INTERSECT:
            case Token.EXCEPT:
                return 12;
            case Token.INSTANCE_OF:
                return 13;
            case Token.TREAT_AS:
                return 14;
            case Token.CASTABLE_AS:
                return 15;
            case Token.CAST_AS:
                return 16;
            default:
                return -1;
        }
    }

    /*@NotNull*/
    private Expression makeBinaryExpression(Expression lhs, int operator, Expression rhs) throws XPathException {
        switch (operator) {
            case Token.OR:
                return new OrExpression(lhs, rhs);
            case Token.AND:
                return new AndExpression(lhs, rhs);
            case Token.FEQ:
            case Token.FNE:
            case Token.FLE:
            case Token.FLT:
            case Token.FGE:
            case Token.FGT:
                return new ValueComparison(lhs, operator, rhs);
            case Token.EQUALS:
            case Token.NE:
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
                return new GeneralComparison(lhs, operator, rhs);
            case Token.IS:
            case Token.PRECEDES:
            case Token.FOLLOWS:
                return new IdentityComparison(lhs, operator, rhs);
            case Token.TO:
                return new RangeExpression(lhs, operator, rhs);
            case Token.CONCAT:
                if (!env.getXPathLanguageLevel().equals(DecimalValue.THREE)) {
                    grumble("Concatenation operator ('||') requires XPath 3.0 to be enabled");
                }
                Expression cc = SystemFunctionCall.makeSystemFunction("concat", new Expression[]{lhs, rhs});
                assert cc != null;
                return cc;
            case Token.PLUS:
            case Token.MINUS:
            case Token.MULT:
            case Token.DIV:
            case Token.IDIV:
            case Token.MOD:
                return new ArithmeticExpression(lhs, operator, rhs);
            case Token.UNION:
            case Token.INTERSECT:
            case Token.EXCEPT:
                return new VennExpression(lhs, operator, rhs);
            default:
                throw new IllegalArgumentException();
        }
    }

    private Expression makeSequenceTypeExpression(Expression lhs, int operator, /*@NotNull*/ SequenceType type) {
        switch (operator) {
            case Token.INSTANCE_OF:
                return new InstanceOfExpression(lhs, type);
            case Token.TREAT_AS:
                return TreatExpression.make(lhs, type);
            default:
                throw new IllegalArgumentException();
        }

    }

    private Expression makeSingleTypeExpression(Expression lhs, int operator, /*@NotNull*/ SimpleType type, boolean allowEmpty)
            throws XPathException {
        if (type instanceof AtomicType) {
            switch (operator) {
                case Token.CASTABLE_AS:
                    CastableExpression castable = new CastableExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        castable.setOperandIsStringLiteral(true);
                    }
                    if (type.isNamespaceSensitive()) {
                        castable.setNamespaceResolver(new SavedNamespaceContext(env.getNamespaceResolver()));
                    }
                    return castable;

                case Token.CAST_AS:
                    CastExpression cast = new CastExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        cast.setOperandIsStringLiteral(true);
                    }
                    if (type.isNamespaceSensitive()) {
                        cast.setNamespaceResolver(new SavedNamespaceContext(env.getNamespaceResolver()));
                    }
                    return cast;

                default:
                    throw new IllegalArgumentException();
            }
        } else {
            switch (operator) {
                case Token.CASTABLE_AS:
                    if (type.isUnionType()) {
                        return new CastableToUnion(lhs, (UnionType)type, allowEmpty);
                    } else if (type.isListType()) {
                        return env.getConfiguration().obtainOptimizer().makeCastableToList(lhs, type, allowEmpty);
                    }
                    break;
                case Token.CAST_AS:
                    if (type.isUnionType()) {
                        return new CastToUnion(lhs, (UnionType)type, allowEmpty);
                    } else if (type.isListType()) {
                        return new CastToList(lhs, (ListType)type, allowEmpty);
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            if (type == AnySimpleType.getInstance()) {
                throw new XPathException("Cannot cast to xs:anySimpleType", "XPST0051");
            } else {
                throw new XPathException("Cannot cast to " + type.getDescription(), "XPST0051");
            }

        }

    }

    /**
     * Parse a Typeswitch Expression.
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the expression that results from the parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseTypeswitchExpression() throws XPathException {
        grumble("typeswitch is not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a Switch Expression.
     * This construct is XQuery-1.1 only, so the XPath and XQuery 1.0 versions of this
     * method throw an error unconditionally
     *
     * @return the expression that results from the parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseSwitchExpression() throws XPathException {
        try {
            return parseFunctionCall();
        } catch(XPathException ex){
            grumble("switch expression requires XQuery 3.0");
            return new ErrorExpression();
        }
    }

    /**
     * Parse a Validate Expression.
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method always
     *         throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseValidateExpression() throws XPathException {
        grumble("validate{} expressions are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse an Extension Expression
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method
     *         always throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseExtensionExpression() throws XPathException {
        grumble("extension expressions (#...#) are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a Type Pattern
     * This construct is used only in XSLT patterns, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method
     *         always throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseTypePattern() throws XPathException {
        grumble("Type patterns (~ItemType) are not allowed in XPath (only in XSLT 3.0 patterns)");
        return new ErrorExpression();
    }


    /**
     * Parse a try/catch Expression
     * This construct is XQuery-3.0 only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method
     *         always throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseTryCatchExpression() throws XPathException {
        grumble("try/catch expressions are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a FOR or LET expression:
     * for $x in expr (',' $y in expr)* 'return' expr
     * let $x := expr (', $y := expr)* 'return' expr
     * This version of the method handles the subset of the FLWOR syntax allowed in XPath
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    protected Expression parseFLWORExpression() throws XPathException {
        if (t.currentToken == Token.LET && !allowXPath30Syntax) {
            grumble("'let' is not permitted in XPath 2.0");
        }
        if (t.currentToken == Token.FOR_SLIDING || t.currentToken == Token.FOR_TUMBLING) {
            grumble("sliding/tumbling windows can only be used in XQuery");
        }
        int clauses = 0;
        int offset;
        int operator = t.currentToken;
        Assignation first = null;
        Assignation previous = null;
        do {
            offset = t.currentTokenStartOffset;
            nextToken();
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;

            // declare the range variable
            Assignation v;
            if (operator == Token.FOR) {
                v = new ForExpression();
                v.setRequiredType(SequenceType.SINGLE_ITEM);
            } else /*if (operator == Token.LET)*/ {
                v = new LetExpression();
                v.setRequiredType(SequenceType.ANY_SEQUENCE);
            }

            clauses++;
            setLocation(v, offset);
            v.setVariableQName(makeStructuredQName(var, ""));
            nextToken();

            // process the "in" or ":=" clause
            expect(operator == Token.LET ? Token.ASSIGN : Token.IN);
            nextToken();
            v.setSequence(parseExprSingle());
            declareRangeVariable(v);
            if (previous == null) {
                first = v;
            } else {
                previous.setAction(v);
            }
            previous = v;

        } while (t.currentToken == Token.COMMA);

        // process the "return" expression (called the "action")
        expect(Token.RETURN);
        nextToken();
        previous.setAction(parseExprSingle());

        // undeclare all the range variables

        for (int i = 0; i < clauses; i++) {
            undeclareRangeVariable();
        }
        return makeTracer(offset, first, Location.FOR_EXPRESSION, first.getVariableQName());
    }

    /**
     * Parse a quantified expression:
     * (some|every) $x in expr 'satisfies' expr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    private Expression parseQuantifiedExpression() throws XPathException {
        int clauses = 0;
        int operator = t.currentToken;
        QuantifiedExpression first = null;
        QuantifiedExpression previous = null;
        int initialOffset = t.currentTokenStartOffset;
        do {
            int offset = t.currentTokenStartOffset;
            nextToken();
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;
            clauses++;

            // declare the range variable
            QuantifiedExpression v = new QuantifiedExpression();
            v.setRequiredType(SequenceType.SINGLE_ITEM);
            v.setOperator(operator);
            setLocation(v, offset);

            v.setVariableQName(makeStructuredQName(var, ""));
            nextToken();

            if (t.currentToken == Token.AS && language == XQUERY) {
                // We use this path for quantified expressions in XQuery, which permit an "as" clause
                nextToken();
                SequenceType type = parseSequenceType();
                if (type.getCardinality() != StaticProperty.EXACTLY_ONE) {
                    warning("Occurrence indicator on singleton range variable has no effect");
                    type = SequenceType.makeSequenceType(type.getPrimaryType(), StaticProperty.EXACTLY_ONE);
                }
                v.setRequiredType(type);
            }

            // process the "in" clause
            expect(Token.IN);
            nextToken();
            v.setSequence(parseExprSingle());
            declareRangeVariable(v);
            if (previous != null) {
                previous.setAction(v);
            } else {
                first = v;
            }
            previous = v;

        } while (t.currentToken == Token.COMMA);

        // process the "return/satisfies" expression (called the "action")
        expect(Token.SATISFIES);
        nextToken();
        previous.setAction(parseExprSingle());


        // undeclare all the range variables

        for (int i = 0; i < clauses; i++) {
            undeclareRangeVariable();
        }
        return makeTracer(initialOffset, first, Location.FOR_EXPRESSION, first.getVariableQName());

    }


    /**
     * Parse an IF expression:
     * if '(' expr ')' 'then' expr 'else' expr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    private Expression parseIfExpression() throws XPathException {
        // left paren already read
        int ifoffset = t.currentTokenStartOffset;
        nextToken();
        Expression condition = parseExpression();
        expect(Token.RPAR);
        nextToken();
        int thenoffset = t.currentTokenStartOffset;
        expect(Token.THEN);
        nextToken();
        Expression thenExp = makeTracer(thenoffset, parseExprSingle(), Location.THEN_EXPRESSION, null);
        int elseoffset = t.currentTokenStartOffset;
        expect(Token.ELSE);
        nextToken();
        Expression elseExp = makeTracer(elseoffset, parseExprSingle(), Location.ELSE_EXPRESSION, null);
        Expression ifExp = Choose.makeConditional(condition, thenExp, elseExp);
        setLocation(ifExp, ifoffset);
        return makeTracer(ifoffset, ifExp, Location.IF_EXPRESSION, null);
    }

    /**
     * Analyze a token whose expected value is the name of an atomic type,
     * or in XPath 3.0 a "plain" union type and return the object representing the atomic or union type.
     *
     * @param qname The lexical QName of the atomic type; alternatively, a Clark name
     * @return The atomic type
     * @throws XPathException if the QName is invalid or if no atomic type of that
     *                        name exists as a built-in type or a type in an imported schema
     */
    /*@NotNull*/
    private ItemType getPlainType(/*@NotNull*/ String qname) throws XPathException {
        if (scanOnly) {
            return STRING;
        }
        String uri;
        String local;
        if (qname.startsWith("{")) {
            StructuredQName sq = StructuredQName.fromClarkName(qname);
            uri = sq.getURI();
            local = sq.getLocalPart();
        } else {
            try {
                String[] parts = nameChecker.getQNameParts(qname);
                if (parts[0].length() == 0) {
                    uri = env.getDefaultElementNamespace();
                } else {
                    try {
                        uri = env.getURIForPrefix(parts[0]);
                    } catch (XPathException err) {
                        grumble(err.getMessage(), err.getErrorCodeQName(), -1);
                        uri = "";
                    }
                }
                local = parts[1];
            } catch (QNameException err) {
                grumble(err.getMessage());
                return ANY_ATOMIC;
            }
        }

        boolean builtInNamespace = uri.equals(NamespaceConstant.SCHEMA);

        if (builtInNamespace) {
            ItemType t = Type.getBuiltInItemType(uri, local);
            if (t == null) {
                grumble("Unknown atomic type " + qname, "XPST0051");
            }
            if (t instanceof BuiltInAtomicType) {
                if (!env.isAllowedBuiltInType((BuiltInAtomicType) t)) {
                    grumble("The type " + qname + " is not recognized by a Basic XSLT Processor. ", "XPST0080");
                }
                return t;
            } else if (t != null && t.isPlainType()) {
                return t;
            } else {
                grumble("The type " + qname + " is not atomic", "XPST0051");
            }
        } else if (uri.equals(NamespaceConstant.JAVA_TYPE)) {
            Class theClass;
            try {
                String className = local.replace('-', '$');
                theClass = env.getConfiguration().getClass(className, false, null);
            } catch (XPathException err) {
                grumble("Unknown Java class " + local, "XPST0051");
                return ExternalObjectType.EXTERNAL_OBJECT_TYPE;
            }
            return new ExternalObjectType(theClass, env.getConfiguration());
        } else if (uri.equals(NamespaceConstant.DOT_NET_TYPE)) {
            return (AtomicType) Configuration.getPlatform().getExternalObjectType(env.getConfiguration(), uri, local);
        } else {

            int fp = env.getNamePool().getFingerprint(uri, local);
            if (fp == -1) {
                grumble("Unknown type " + qname, "XPST0051");
            }
            SchemaType st = env.getConfiguration().getSchemaType(fp);
            if (st == null) {
                grumble("Unknown atomic type " + qname, "XPST0051");
            } else if (st.isAtomicType()) {
                if (!env.isImportedSchema(uri)) {
                    grumble("Atomic type " + qname + " exists, but its schema definition has not been imported", "XPST0051");
                }
                return (AtomicType) st;
            } else if (st instanceof ItemType && ((ItemType) st).isPlainType() && DecimalValue.THREE.equals(env.getXPathLanguageLevel())) {
                if (!env.isImportedSchema(uri)) {
                    grumble("Type " + qname + " exists, but its schema definition has not been imported", "XPST0051");
                }
                return (ItemType) st;
            } else if (st.isComplexType()) {
                grumble("Type (" + qname + ") is a complex type", "XPST0051");
                return ANY_ATOMIC;
            } else if (((SimpleType) st).isListType()) {
                grumble("Type (" + qname + ") is a list type", "XPST0051");
                return ANY_ATOMIC;
            } else if (DecimalValue.THREE.equals(env.getXPathLanguageLevel())) {
                grumble("Type (" + qname + ") is a union type that cannot be used as an item type", "XPST0051");
                return ANY_ATOMIC;
            } else {
                grumble("The union type (" + qname + ") cannot be used as an item type unless XPath 3.0 is enabled", "XPST0051");
                return ANY_ATOMIC;
            }
        }
        grumble("Unknown atomic type " + qname, "XPST0051");
        return ANY_ATOMIC;
    }


    /**
     * Analyze a token whose expected value is the name of a simple type: any type name
     * allowed as the operand of "cast" or "castable".
     *
     * @param qname The lexical QName of the atomic type; alternatively, a Clark name
     * @return The atomic type
     * @throws XPathException if the QName is invalid or if no atomic type of that
     *                        name exists as a built-in type or a type in an imported schema
     */
    /*@NotNull*/
    private SimpleType getSimpleType(/*@NotNull*/ String qname) throws XPathException {
        if (scanOnly) {
            return STRING;
        }
        String uri;
        String local;
        if (qname.startsWith("{")) {
            StructuredQName sq = StructuredQName.fromClarkName(qname);
            uri = sq.getURI();
            local = sq.getLocalPart();
        } else {
            try {
                String[] parts = nameChecker.getQNameParts(qname);
                if (parts[0].length() == 0) {
                    uri = env.getDefaultElementNamespace();
                } else {
                    try {
                        uri = env.getURIForPrefix(parts[0]);
                    } catch (XPathException err) {
                        grumble(err.getMessage(), err.getErrorCodeQName(), -1);
                        uri = "";
                    }
                }
                local = parts[1];
            } catch (QNameException err) {
                grumble(err.getMessage());
                return ANY_ATOMIC;
            }
        }

        boolean builtInNamespace = uri.equals(NamespaceConstant.SCHEMA);
        if (builtInNamespace) {
            SimpleType t = Type.getBuiltInSimpleType(uri, local);
            if (t == null) {
                grumble("Unknown simple type " + qname, "XPST0051");
            }
            if (t instanceof BuiltInAtomicType) {
                if (!env.isAllowedBuiltInType((BuiltInAtomicType) t)) {
                    grumble("The type " + qname + " is not recognized by a Basic XSLT Processor. ", "XPST0080");
                }
            }
            return t;
        } else if (uri.equals(NamespaceConstant.DOT_NET_TYPE)) {
            return (AtomicType) Configuration.getPlatform().getExternalObjectType(env.getConfiguration(), uri, local);

        } else {

            int fp = env.getNamePool().getFingerprint(uri, local);
            if (fp == -1) {
                grumble("Unknown type " + qname, "XPST0051");
            }
            SchemaType st = env.getConfiguration().getSchemaType(fp);
            if (st == null) {
                grumble("Unknown simple type " + qname, "XPST0051");
                return ANY_ATOMIC;
            }
            if (DecimalValue.THREE.equals(env.getXPathLanguageLevel())) {
                // XPath 3.0
                if (!env.isImportedSchema(uri)) {
                    grumble("Simple type " + qname + " exists, but its target namespace has not been imported in the static context");
                }
                return (SimpleType) st;

            } else {
                // XPath 2.0
                if (st.isAtomicType()) {
                    if (!env.isImportedSchema(uri)) {
                        grumble("Atomic type " + qname + " exists, but its target namespace has not been imported in the static context");
                    }
                    return (AtomicType) st;
                } else if (st.isComplexType()) {
                    grumble("Cannot cast to a complex type (" + qname + ")", "XPST0051");
                    return ANY_ATOMIC;
                } else if (((SimpleType) st).isListType()) {
                    grumble("Casting to a list type (" + qname + ") requires XPath 3.0", "XPST0051");
                    return ANY_ATOMIC;
                } else {
                    grumble("casting to a union type (" + qname + ") requires XPath 3.0", "XPST0051");
                    return ANY_ATOMIC;
                }
            }
        }
    }

    /**
     * Parse the sequence type production.
     * The QName must be the name of a built-in schema-defined data type.
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    public SequenceType parseSequenceType() throws XPathException {
        boolean disallowIndicator = t.currentTokenValue.equals("empty-sequence");
        ItemType primaryType = parseItemType();
        if (disallowIndicator) {
            // No occurrence indicator allowed
            return SequenceType.makeSequenceType(primaryType, StaticProperty.EMPTY);
        }
        int occurrenceFlag;
        switch (t.currentToken) {
            case Token.STAR:
            case Token.MULT:
                // "*" will be tokenized different ways depending on what precedes it
                occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_MORE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            case Token.PLUS:
                occurrenceFlag = StaticProperty.ALLOWS_ONE_OR_MORE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            case Token.QMARK:
                occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_ONE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            default:
                occurrenceFlag = StaticProperty.EXACTLY_ONE;
        }
        return SequenceType.makeSequenceType(primaryType, occurrenceFlag);
    }

    /**
     * Parse an ItemType within a SequenceType
     *
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    public ItemType parseItemType() throws XPathException {
        ItemType primaryType;
        if (t.currentToken == Token.LPAR) {
            primaryType = parseParenthesizedItemType();
            //nextToken();
        } else if (t.currentToken == Token.NAME) {
            primaryType = getPlainType(t.currentTokenValue);
            nextToken();
        } else if (t.currentToken == Token.NODEKIND) {
            if (t.currentTokenValue.equals("item")) {
                nextToken();
                expect(Token.RPAR);
                nextToken();
                primaryType = AnyItemType.getInstance();
            } else if (t.currentTokenValue.equals("function")) {
                primaryType = parseFunctionItemType();
            } else if (t.currentTokenValue.equals("map")) {
                primaryType = parseMapItemType();
            } else if (t.currentTokenValue.equals("empty-sequence")) {
                nextToken();
                expect(Token.RPAR);
                nextToken();
                primaryType = ErrorType.getInstance();
            } else {
                primaryType = parseKindTest();
            }
        } else if (t.currentToken == Token.PERCENT) {
            Map<StructuredQName, Annotation> annotationAssertions = parseAnnotations();
            // TODO retain the annotation assertions
            if (t.currentTokenValue.equals("function")) {
                primaryType = parseFunctionItemType();
            } else {
                grumble("Expected 'function' to follow annotation assertions, found " + Token.tokens[t.currentToken]);
                return null;
            }
        } else {
            grumble("Expected type name in SequenceType, found " + Token.tokens[t.currentToken]);
            return ANY_ATOMIC;
        }
        return primaryType;
    }

    /**
     * Get the item type used for function items (XPath 3.0 higher order functions)
     *
     * @return the item type representing a function item
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error occurs (including the case
     *          where XPath 3.0 syntax is not enabled)
     */

    /*@NotNull*/
    protected ItemType parseFunctionItemType() throws XPathException {
        grumble("The item type function() is available only when XPath 3.0 is enabled");
        return ANY_ATOMIC;
    }

    /**
     * Get the item type used for map items (XPath 3.0)
     *
     * @return the item type of the map
     * @throws XPathException if a parsing error occurs or if the map syntax
     *                        is not available
     */

    /*@NotNull*/
    protected ItemType parseMapItemType() throws XPathException {
        grumble("The item type map() is available only when XPath 3.0 is enabled");
        return ANY_ATOMIC;
    }

    /**
     * Parse a parenthesized item type (XPath 3.0)
     *
     * @return the item type
     * @throws XPathException if a syntax error is found
     */

    /*@NotNull*/
    protected ItemType parseParenthesizedItemType() throws XPathException {
        grumble("A parenthesized item type is allowed only when XPath 3.0 is enabled");
        return ANY_ATOMIC;
    }

    /**
     * Parse a UnaryExpr:<br>
     * ('+'|'-')* ValueExpr
     * parsed as ('+'|'-')? UnaryExpr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    private Expression parseUnaryExpression() throws XPathException {
        Expression exp;
        switch (t.currentToken) {
            case Token.MINUS:
                nextToken();
                exp = new ArithmeticExpression(Literal.makeLiteral(Int64Value.ZERO),
                        Token.NEGATE,
                        parseUnaryExpression());
                break;
            case Token.PLUS:
                nextToken();
                // Unary plus: can't ignore it completely, it might be a type error, or it might
                // force conversion to a number which would affect operations such as "=".
                exp = new ArithmeticExpression(Literal.makeLiteral(Int64Value.ZERO),
                        Token.PLUS,
                        parseUnaryExpression());
                break;
            case Token.VALIDATE:
            case Token.VALIDATE_STRICT:
            case Token.VALIDATE_LAX:
            case Token.VALIDATE_TYPE:
                exp = parseValidateExpression();
                break;
            case Token.PRAGMA:
                exp = parseExtensionExpression();
                break;
            case Token.TILDE:
                exp = parseTypePattern();
                break;
            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("validate")) {
                    exp = parseValidateExpression();
                    break;
                }
                // else fall through
            default:
                exp = parseSimpleMappingExpression();
        }
        setLocation(exp);
        return exp;
    }

    /**
     * Test whether the current token is one that can start a RelativePathExpression
     *
     * @return the resulting subexpression
     */

    protected boolean atStartOfRelativePath() {
        switch (t.currentToken) {
            case Token.AXIS:
            case Token.AT:
            case Token.NAME:
            case Token.PREFIX:
            case Token.SUFFIX:
            case Token.STAR:
            case Token.NODEKIND:
            case Token.DOT:
            case Token.DOTDOT:
            case Token.FUNCTION:
            case Token.STRING_LITERAL:
            case Token.NUMBER:
            case Token.LPAR:
            case Token.DOLLAR:
            case Token.PRAGMA:
            case Token.ELEMENT_QNAME:
            case Token.ATTRIBUTE_QNAME:
            case Token.PI_QNAME:
            case Token.NAMESPACE_QNAME:
            case Token.INLINE_FUNCTION_LITERAL:
                return true;
            case Token.KEYWORD_CURLY:
                return (t.currentTokenValue.equals("ordered") || t.currentTokenValue.equals("unordered"));
            default:
                return false;
        }
    }

    /**
     * Test whether the current token is one that is disallowed after a "leading lone slash".
     * These composite tokens have been parsed as operators, but are not allowed after "/" under the
     * rules of erratum E24
     *
     * @return the resulting subexpression
     */

    protected boolean disallowedAtStartOfRelativePath() {
        switch (t.currentToken) {
            // Although these "double keyword" operators can readily be recognized as operators,
            // they are not permitted after leading "/" under the rules of erratum XQ.E24
            case Token.CAST_AS:
            case Token.CASTABLE_AS:
            case Token.INSTANCE_OF:
            case Token.TREAT_AS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse a PathExpresssion. This includes "true" path expressions such as A/B/C, and also
     * constructs that may start a path expression such as a variable reference $name or a
     * parenthesed expression (A|B). Numeric and string literals also come under this heading.
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parsePathExpression() throws XPathException {
        switch (t.currentToken) {
            case Token.SLASH:
                nextToken();
                final RootExpression start = new RootExpression();
                setLocation(start);
                if (disallowedAtStartOfRelativePath()) {
                    grumble("Operator '" + Token.tokens[t.currentToken] + "' is not allowed after '/'");
                }
                if (atStartOfRelativePath()) {
                    final Expression path = parseRemainingPath(start);
                    setLocation(path);
                    return path;
                } else {
                    return start;
                }

            case Token.SLASH_SLASH:
                nextToken();
                final RootExpression start2 = new RootExpression();
                setLocation(start2);
                final AxisExpression axisExp = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
                setLocation(axisExp);
                final Expression slashExp = ExpressionTool.makePathExpression(start2, axisExp, false);
                setLocation(slashExp);
                final Expression exp = parseRemainingPath(slashExp);
                setLocation(exp);
                return exp;

            default:
                if (t.currentToken == Token.NAME &&
                        (t.currentTokenValue.equals("true") || t.currentTokenValue.equals("false"))) {
                    warning("The expression is looking for a child element named '" + t.currentTokenValue +
                            "' - perhaps " + t.currentTokenValue + "() was intended? To avoid this warning, use child::" +
                            t.currentTokenValue + " or ./" + t.currentTokenValue);
                }
                return parseRelativePath();
        }

    }

    /**
     * Parse an XPath 3.0 simple mapping expression ("!" operator)
     */

    protected Expression parseSimpleMappingExpression() throws XPathException {
        Expression exp = parsePathExpression();
        while (t.currentToken == Token.BANG) {
            if (!env.getXPathLanguageLevel().equals(DecimalValue.THREE)) {
                grumble("XPath '!' operator requires XPath 3.0 to be enabled");
            }
            nextToken();
            Expression next = parsePathExpression();
            exp = new ForEach(exp, next);
        }
        return exp;
    }


    /**
     * Parse a relative path (a sequence of steps). Called when the current token immediately
     * follows a separator (/ or //), or an implicit separator (XYZ is equivalent to ./XYZ)
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseRelativePath() throws XPathException {
        Expression exp = parseStepExpression(language == XSLT_PATTERN);
        while (t.currentToken == Token.SLASH ||
                t.currentToken == Token.SLASH_SLASH) {
            int op = t.currentToken;
            nextToken();
            Expression next = parseStepExpression(false);
            if (op == Token.SLASH) {
                exp = ExpressionTool.makePathExpression(exp, next, true);
            } else if (op == Token.SLASH_SLASH) {
                // add implicit descendant-or-self::node() step
                AxisExpression ae = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
                setLocation(ae);
                Expression one = ExpressionTool.makePathExpression(exp, ae, false);
                setLocation(one);
                exp = ExpressionTool.makePathExpression(one, next, true);
            }
            setLocation(exp);
        }
        return exp;
    }

    /**
     * Parse the remaining steps of an absolute path expression (one starting in "/" or "//"). Note that the
     * token immediately after the "/" or "//" has already been read, and in the case of "/", it has been confirmed
     * that we have a path expression starting with "/" rather than a standalone "/" expression.
     *
     * @param start the initial implicit expression: root() in the case of "/", root()/descendant-or-self::node in
     *              the case of "//"
     * @return the completed path expression
     * @throws XPathException if a static error is found
     */
    /*@NotNull*/
    protected Expression parseRemainingPath(Expression start) throws XPathException {
        Expression exp = start;
        int op = Token.SLASH;
        while (true) {
            Expression next = parseStepExpression(false);
            if (op == Token.SLASH) {
                exp = ExpressionTool.makePathExpression(exp, next, true);
            } else if (op == Token.SLASH_SLASH) {
                // add implicit descendant-or-self::node() step
                AxisExpression descOrSelf = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
                setLocation(descOrSelf);
                Expression step = ExpressionTool.makePathExpression(descOrSelf, next, false);
                setLocation(step);
                exp = ExpressionTool.makePathExpression(exp, step, true);
            } else /*if (op == Token.BANG)*/ {
                if (!env.getXPathLanguageLevel().equals(DecimalValue.THREE)) {
                    grumble("The '!' operator requires XPath 3.0 to be enabled");
                }
                exp = new ForEach(exp, next);
            }
            setLocation(exp);
            op = t.currentToken;
            if (op != Token.SLASH && op != Token.SLASH_SLASH && op != Token.BANG) {
                break;
            }
            nextToken();
        }
        return exp;
    }


    /**
     * Parse a step (including an optional sequence of predicates)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseStepExpression(boolean firstInPattern) throws XPathException {
        Expression step = parseBasicStep(firstInPattern);

        // When the filter is applied to an Axis step, the nodes are considered in
        // axis order. In all other cases they are considered in document order
        boolean reverse = (step instanceof AxisExpression) &&
                !AxisInfo.isForwards[((AxisExpression) step).getAxis()];

        while (true) {
            if (t.currentToken == Token.LSQB) {
                nextToken();
                Expression predicate = parsePredicate();
                expect(Token.RSQB);
                nextToken();
                step = new FilterExpression(step, predicate);
                setLocation(step);
            } else if (t.currentToken == Token.LPAR) {
                // dynamic function call (XQuery 3.0/XPath 3.0 syntax)
                step = parseDynamicFunctionCall(step);
                setLocation(step);
            } else {
                break;
            }
        }
        if (reverse) {
            step = SystemFunctionCall.makeSystemFunction("reverse", new Expression[]{step});
            assert step != null;
            return step;
        } else {
            return step;
        }
    }

    /**
     * Parse the expression within a predicate. A separate method so it can be overridden
     *
     * @return the expression within the predicate
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parsePredicate() throws XPathException {
        return parseExpression();
    }

    protected Map<StructuredQName, Annotation> parseAnnotations() throws XPathException {
        grumble("Function and variable annotations are allowed only in XQuery 3.0");
        return null;
    }

    protected boolean isReservedInQuery(String uri) {
        return NamespaceConstant.isReservedInQuery(uri);
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
        switch (t.currentToken) {
            case Token.DOLLAR:
                return parseVariableReference();

            case Token.LPAR:
                nextToken();
                if (t.currentToken == Token.RPAR) {
                    nextToken();
                    return Literal.makeEmptySequence();
                }
                Expression seq = parseExpression();
                expect(Token.RPAR);
                nextToken();
                return seq;

            case Token.STRING_LITERAL:
                return parseStringLiteral();

            case Token.NUMBER:
                return parseNumericLiteral();

            case Token.FUNCTION:
                return parseFunctionCall();

            case Token.DOT:
                nextToken();
                Expression cie = new ContextItemExpression();
                setLocation(cie);
                return cie;

            case Token.DOTDOT:
                nextToken();
                Expression pne = new ParentNodeExpression();
                setLocation(pne);
                return pne;

            case Token.PERCENT:
                Map<StructuredQName, Annotation> annotations = parseAnnotations();
                if (!t.currentTokenValue.equals("function")) {
                    grumble("Expected 'function' to follow the annotation assertion");
                }
                if (annotations.containsKey(Annotation.PRIVATE) ||
                        annotations.containsKey(Annotation.PUBLIC)) {
                grumble("Inline functions must not be annotated %private or %public", "XQST0125");
            }
                return parseInlineFunction(annotations);

            case Token.NODEKIND:

                    if (t.currentTokenValue.equals("function")) {
                        if (languageVersion.equals(DecimalValue.THREE)) {
                            return parseInlineFunction(null);
                        } else {
                           return parseFunctionCall();
                        }
                    } else if (t.currentTokenValue.equals("namespace-node") || t.currentTokenValue.equals("switch")) {
                        if(!languageVersion.equals(DecimalValue.THREE)) {
                            return parseFunctionCall();
                        }
                    } else if(t.currentTokenValue.equals("map")) {
                        return parseFunctionCall();
                    }

                // fall through!
            case Token.NAME:
            case Token.PREFIX:
            case Token.SUFFIX:
            case Token.STAR:
                //case Token.NODEKIND:
                byte defaultAxis = AxisInfo.CHILD;
                if (t.currentToken == Token.NODEKIND &&
                        (t.currentTokenValue.equals("attribute") || t.currentTokenValue.equals("schema-attribute"))) {
                    defaultAxis = AxisInfo.ATTRIBUTE;
                } else if (t.currentToken == Token.NODEKIND && t.currentTokenValue.equals("namespace-node")) {
                    defaultAxis = AxisInfo.NAMESPACE;
                    testPermittedAxis(AxisInfo.NAMESPACE);
                } else if (firstInPattern && t.currentToken == Token.NODEKIND && t.currentTokenValue.equals("document-node")) {
                    defaultAxis = AxisInfo.SELF;
                }
                NodeTest test = parseNodeTest(Type.ELEMENT);
                if (test instanceof AnyNodeTest) {
                    // handles patterns of the form match="node()"
                    test = (defaultAxis == AxisInfo.CHILD ? AnyChildNodeTest.getInstance() : NodeKindTest.ATTRIBUTE);
                }
                AxisExpression ae = new AxisExpression(defaultAxis, test);
                setLocation(ae);
                return ae;

            case Token.AT:
                nextToken();
                switch (t.currentToken) {

                    case Token.NAME:
                    case Token.PREFIX:
                    case Token.SUFFIX:
                    case Token.STAR:
                    case Token.NODEKIND:
                        AxisExpression ae2 = new AxisExpression(AxisInfo.ATTRIBUTE, parseNodeTest(Type.ATTRIBUTE));
                        setLocation(ae2);
                        return ae2;

                    default:
                        grumble("@ must be followed by a NodeTest");
                }
                break;

            case Token.AXIS:
                byte axis;
                try {
                    axis = AxisInfo.getAxisNumber(t.currentTokenValue);
                } catch (XPathException err) {
                    grumble(err.getMessage());
                    axis = AxisInfo.CHILD; // error recovery
                }
                testPermittedAxis(axis);
                short principalNodeType = AxisInfo.principalNodeType[axis];
                nextToken();
                switch (t.currentToken) {

                    case Token.NAME:
                    case Token.PREFIX:
                    case Token.SUFFIX:
                    case Token.STAR:
                    case Token.NODEKIND:
                        Expression ax = new AxisExpression(axis, parseNodeTest(principalNodeType));
                        setLocation(ax);
                        return ax;

                    default:
                        grumble("Unexpected token " + currentTokenDisplay() + " after axis name");
                }
                break;

            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("map")) {
                    return parseMapExpression();
                }
                // else fall through
            case Token.ELEMENT_QNAME:
            case Token.ATTRIBUTE_QNAME:
            case Token.NAMESPACE_QNAME:
            case Token.PI_QNAME:
            case Token.TAG:
                return parseConstructor();

            case Token.INLINE_FUNCTION_LITERAL:
                return parseLiteralFunctionItem();

            case Token.TILDE:
                return parseTypePattern();


            default:
                grumble("Unexpected token " + currentTokenDisplay() + " in path expression");
                //break;
        }
        return new ErrorExpression();
    }

    protected void testPermittedAxis(byte axis) throws XPathException {
        // no action by default - all axes are permitted
    }


    protected Expression parseNumericLiteral() throws XPathException {
        int offset = t.currentTokenStartOffset;
        NumericValue number = NumericValue.parseNumber(t.currentTokenValue);
        if (number.isNaN()) {
            grumble("Invalid numeric literal " + Err.wrap(t.currentTokenValue, Err.VALUE));
        }
        nextToken();
        Literal lit = Literal.makeLiteral(number);
        setLocation(lit);
        return makeTracer(offset, lit, 0, null);
    }

    protected Expression parseStringLiteral() throws XPathException {
        int offset = t.currentTokenStartOffset;
        Literal literal = makeStringLiteral(t.currentTokenValue);
        nextToken();
        return makeTracer(offset, literal, 0, null);
    }

    /*@NotNull*/
    protected Expression parseVariableReference() throws XPathException {
        nextToken();
        expect(Token.NAME);
        String var = t.currentTokenValue;
        nextToken();

        if (scanOnly) {
            return new ContextItemExpression();
            // don't do any semantic checks during a prescan
        }

        //int vtest = makeNameCode(var, false) & 0xfffff;
        StructuredQName vtest = makeStructuredQName(var, "");

        // See if it's a range variable or a variable in the context
        Binding b = findRangeVariable(vtest);
        Expression ref;
        if (b != null) {
            ref = new LocalVariableReference(b);
        } else {
            if (catchDepth > 0) {
                for (StructuredQName errorVariable : StandardNames.errorVariables) {
                	String localPart = vtest.getLocalPart();
                    if (errorVariable.getLocalPart().equals(localPart)) {
                        IntegratedFunctionLibrary lib = env.getConfiguration().getVendorFunctionLibrary();
                        StructuredQName functionName =
                                new StructuredQName("saxon", NamespaceConstant.SAXON, "dynamic-error-info");
                        Expression[] args = new Expression[]{new StringLiteral(localPart)};
                        return lib.bind(functionName, 1, args, env, null);
                    }
                }
            }
            try {
                ref = env.bindVariable(vtest);
            } catch (XPathException err) {
                Expression dummy = new ContextItemExpression();
                setLocation(dummy);
                err.maybeSetLocation(dummy);
                throw err;
            }
        }
        setLocation(ref);
        return ref;
    }

    /**
     * Method to make a string literal from a token identified as a string
     * literal. This is trivial in XPath, but in XQuery the method is overridden
     * to identify pseudo-XML character and entity references. Note that the job of handling
     * doubled string delimiters is done by the tokenizer.
     *
     * @param currentTokenValue the token as read (excluding quotation marks)
     * @return The string value of the string literal
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is found
     */

    /*@NotNull*/
    protected Literal makeStringLiteral(String currentTokenValue) throws XPathException {
        StringLiteral literal = new StringLiteral(currentTokenValue);
        setLocation(literal);
        return literal;
    }

    /**
     * Parse a node constructor. This is allowed only in XQuery, so the method throws
     * an error for XPath.
     *
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error occurs
     */

    /*@NotNull*/
    protected Expression parseConstructor() throws XPathException {
        grumble("Node constructor expressions are allowed only in XQuery, not in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a dynamic function call
     *
     * @param functionItem the expression that determines the function to be called
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is found
     */

    /*@NotNull*/
    protected Expression parseDynamicFunctionCall(Expression functionItem) throws XPathException {
        grumble("Unexpected '(' after primary expression. (Dynamic function calls require XPath 3.0)");
        return new ErrorExpression();
    }

    /**
     * Parse a NodeTest.
     * One of QName, prefix:*, *:suffix, *, text(), node(), comment(), or
     * processing-instruction(literal?), or element(~,~), attribute(~,~), etc.
     *
     * @param nodeType the node type being sought if one is specified
     * @return the resulting NodeTest object
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected NodeTest parseNodeTest(short nodeType) throws XPathException {
        int tok = t.currentToken;
        String tokv = t.currentTokenValue;
        switch (tok) {
            case Token.NAME:
                nextToken();
                return makeNameTest(nodeType, tokv, nodeType == Type.ELEMENT);

            case Token.PREFIX:
                nextToken();
                return makeNamespaceTest(nodeType, tokv);

            case Token.SUFFIX:
                nextToken();
                tokv = t.currentTokenValue;
                expect(Token.NAME);
                nextToken();
                return makeLocalNameTest(nodeType, tokv);

            case Token.STAR:
                nextToken();
                return NodeKindTest.makeNodeKindTest(nodeType);

            case Token.NODEKIND:
                return parseKindTest();

            default:
                grumble("Unrecognized node test");
                throw new XPathException(""); // unreachable instruction
        }
    }

    /**
     * Parse a KindTest
     *
     * @return the KindTest, expressed as a NodeTest object
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is found
     */

    /*@NotNull*/
    private NodeTest parseKindTest() throws XPathException {
        String typeName = t.currentTokenValue;
        boolean schemaDeclaration = (typeName.startsWith("schema-"));
        int primaryType = getSystemType(typeName);
        int nameCode = -1;
        int contentType;
        boolean empty = false;
        nextToken();
        if (t.currentToken == Token.RPAR) {
            if (schemaDeclaration) {
                grumble("schema-element() and schema-attribute() require a name to be supplied");
                return null;
            }
            empty = true;
            nextToken();
        }
        switch (primaryType) {
            case Type.ITEM:
                grumble("item() is not allowed in a path expression");
                return null;
            case Type.NODE:
                if (empty) {
                    return AnyNodeTest.getInstance();
                } else {
                    grumble("No arguments are allowed in node()");
                    return null;
                }
            case Type.TEXT:
                if (empty) {
                    return NodeKindTest.TEXT;
                } else {
                    grumble("No arguments are allowed in text()");
                    return null;
                }
            case Type.COMMENT:
                if (empty) {
                    return NodeKindTest.COMMENT;
                } else {
                    grumble("No arguments are allowed in comment()");
                    return null;
                }
            case Type.NAMESPACE:
                if (empty) {
                    if (!isNamespaceTestAllowed()) {
                        grumble("namespace-node() test is not allowed in XPath 2.0/XQuery 1.0");
                    }
                    return NodeKindTest.NAMESPACE;
                } else {
                    grumble("No arguments are allowed in namespace-node()");
                    return null;
                }
            case Type.DOCUMENT:
                if (empty) {
                    return NodeKindTest.DOCUMENT;
                } else {
                    int innerType;
                    try {
                        innerType = getSystemType(t.currentTokenValue);
                    } catch (XPathException err) {
                        innerType = Type.ITEM;
                    }
                    if (innerType != Type.ELEMENT) {
                        grumble("Argument to document-node() must be an element type descriptor");
                        return null;
                    }
                    NodeTest inner = parseKindTest();
                    expect(Token.RPAR);
                    nextToken();
                    return new DocumentNodeTest(inner);
                }
            case Type.PROCESSING_INSTRUCTION:
                if (empty) {
                    return NodeKindTest.PROCESSING_INSTRUCTION;
                } else if (t.currentToken == Token.STRING_LITERAL) {
                    String piName = Whitespace.trim(t.currentTokenValue);
                    if (!nameChecker.isValidNCName(piName)) {
                        // Became an error as a result of XPath erratum XP.E7
                        grumble("Processing instruction name must be a valid NCName", "XPTY0004");
                    } else {
                        nameCode = env.getNamePool().allocate("", "", piName);
                    }
                } else if (t.currentToken == Token.NAME) {
                    try {
                        String[] parts = nameChecker.getQNameParts(t.currentTokenValue);
                        if (parts[0].length() == 0) {
                            nameCode = makeNameCode(parts[1], false);
                        } else {
                            grumble("Processing instruction name must not contain a colon");
                        }
                    } catch (QNameException e) {
                        grumble("Invalid processing instruction name. " + e.getMessage());
                    }
                } else {
                    grumble("Processing instruction name must be a QName or a string literal");
                }
                nextToken();
                expect(Token.RPAR);
                nextToken();
                return new NameTest(Type.PROCESSING_INSTRUCTION, nameCode, env.getNamePool());

            case Type.ATTRIBUTE:
                // drop through

            case Type.ELEMENT:
                String nodeName = "";
                if (empty) {
                    return NodeKindTest.makeNodeKindTest(primaryType);
                } else if (t.currentToken == Token.STAR || t.currentToken == Token.MULT) {
                    // allow for both representations of "*" to be safe
                    if (schemaDeclaration) {
                        grumble("schema-element() and schema-attribute() must specify an actual name, not '*'");
                        return null;
                    }
                    nameCode = -1;
                } else if (t.currentToken == Token.NAME) {
                    nodeName = t.currentTokenValue;
                    nameCode = makeNameCode(t.currentTokenValue, primaryType == Type.ELEMENT);
                } else {
                    grumble("Unexpected " + Token.tokens[t.currentToken] + " after '(' in SequenceType");
                }
                String suri = null;
                if (nameCode != -1) {
                    suri = env.getNamePool().getURI(nameCode);
                }
                nextToken();
                if (t.currentToken == Token.RPAR) {
                    nextToken();
                    if (nameCode == -1) {
                        // element(*) or attribute(*)
                        return NodeKindTest.makeNodeKindTest(primaryType);
                    } else {
                        NodeTest nameTest;
                        if (primaryType == Type.ATTRIBUTE) {
                            // attribute(N) or schema-attribute(N)
                            if (schemaDeclaration) {
                                // schema-attribute(N)
                                SchemaDeclaration attributeDecl =
                                        env.getConfiguration().getAttributeDeclaration(nameCode & 0xfffff);
                                if (!env.isImportedSchema(suri)) {
                                    grumble("No schema has been imported for namespace '" + suri + '\'', "XPST0008");
                                }
                                if (attributeDecl == null) {
                                    grumble("There is no declaration for attribute @" + nodeName + " in an imported schema", "XPST0008");
                                    return null;
                                } else {
                                    return attributeDecl.makeSchemaNodeTest();
                                }
                            } else {
                                nameTest = new NameTest(Type.ATTRIBUTE, nameCode, env.getNamePool());
                                return nameTest;
                            }
                        } else {
                            // element(N) or schema-element(N)
                            if (schemaDeclaration) {
                                // schema-element(N)
                                if (!env.isImportedSchema(suri)) {
                                    grumble("No schema has been imported for namespace '" + suri + '\'', "XPST0008");
                                }
                                SchemaDeclaration elementDecl =
                                        env.getConfiguration().getElementDeclaration(nameCode & 0xfffff);
                                if (elementDecl == null) {
                                    grumble("There is no declaration for element <" + nodeName + "> in an imported schema", "XPST0008");
                                    return null;
                                } else {
                                    return elementDecl.makeSchemaNodeTest();
                                }
                            } else {
                                nameTest = new NameTest(Type.ELEMENT, nameCode, env.getNamePool());
                                return nameTest;
                            }
                        }
                    }
                } else if (t.currentToken == Token.COMMA) {
                    if (schemaDeclaration) {
                        grumble("schema-element() and schema-attribute() must have one argument only");
                        return null;
                    }
                    nextToken();
                    NodeTest result;
                    if (t.currentToken == Token.STAR) {
                        grumble("'*' is no longer permitted as the second argument of element() and attribute()");
                        return null;
                    } else if (t.currentToken == Token.NAME) {
                        SchemaType schemaType;
                        contentType = makeNameCode(t.currentTokenValue, true) & NamePool.FP_MASK;
                        String uri = env.getNamePool().getURI(contentType);
                        String lname = env.getNamePool().getLocalName(contentType);

                        if (uri.equals(NamespaceConstant.SCHEMA)) {
                            schemaType = env.getConfiguration().getSchemaType(contentType);
                        } else {
                            if (!env.isImportedSchema(uri)) {
                                grumble("No schema has been imported for namespace '" + uri + '\'', "XPST0008");
                            }
                            schemaType = env.getConfiguration().getSchemaType(contentType);
                        }
                        if (schemaType == null) {
                            grumble("Unknown type name " + lname, "XPST0008");
                            return null;
                        }
                        if (primaryType == Type.ATTRIBUTE && schemaType.isComplexType()) {
                            warning("An attribute cannot have a complex type");
                        }
                        ContentTypeTest typeTest = new ContentTypeTest(primaryType, schemaType, env.getConfiguration(), false);
                        if (nameCode == -1) {
                            // this represents element(*,T) or attribute(*,T)
                            result = typeTest;
                            if (primaryType == Type.ATTRIBUTE) {
                                nextToken();
                            } else {
                                // assert (primaryType == Type.ELEMENT);
                                nextToken();
                                if (t.currentToken == Token.QMARK) {
                                    typeTest.setNillable(true);
                                    nextToken();
                                }
                            }
                        } else {
                            if (primaryType == Type.ATTRIBUTE) {
                                NodeTest nameTest = new NameTest(Type.ATTRIBUTE, nameCode, env.getNamePool());
                                result = new CombinedNodeTest(nameTest, Token.INTERSECT, typeTest);
                                nextToken();
                            } else {
                                // assert (primaryType == Type.ELEMENT);
                                NodeTest nameTest = new NameTest(Type.ELEMENT, nameCode, env.getNamePool());
                                result = new CombinedNodeTest(nameTest, Token.INTERSECT, typeTest);
                                nextToken();
                                if (t.currentToken == Token.QMARK) {
                                    typeTest.setNillable(true);
                                    nextToken();
                                }
                            }
                        }
                    } else {
                        grumble("Unexpected " + Token.tokens[t.currentToken] + " after ',' in SequenceType");
                        return null;
                    }

                    expect(Token.RPAR);
                    nextToken();
                    return result;
                } else {
                    grumble("Expected ')' or ',' in SequenceType");
                }
                return null;
            default:
                // can't happen!
                grumble("Unknown node kind");
                return null;
        }
    }

    /**
     * Ask whether the syntax namespace-node() is allowed in a node kind test.
     *
     * @return false (currently allowed only in XQuery 3.0)
     */

    protected boolean isNamespaceTestAllowed() {
        return allowXPath30Syntax;
    }

    /**
     * Get a system type - that is, one whose name is a keyword rather than a QName. This includes the node
     * kinds such as element and attribute, and the generic types node() and item()
     *
     * @param name the name of the system type, for example "element" or "comment"
     * @return the integer constant denoting the type, for example {@link Type#ITEM} or {@link Type#ELEMENT}
     * @throws XPathException if the name is not recognized
     */
    private int getSystemType(String name) throws XPathException {
        if ("item".equals(name)) {
            return Type.ITEM;
        } else if ("document-node".equals(name)) {
            return Type.DOCUMENT;
        } else if ("element".equals(name)) {
            return Type.ELEMENT;
        } else if ("schema-element".equals(name)) {
            return Type.ELEMENT;
        } else if ("attribute".equals(name)) {
            return Type.ATTRIBUTE;
        } else if ("schema-attribute".equals(name)) {
            return Type.ATTRIBUTE;
        } else if ("text".equals(name)) {
            return Type.TEXT;
        } else if ("comment".equals(name)) {
            return Type.COMMENT;
        } else if ("processing-instruction".equals(name)) {
            return Type.PROCESSING_INSTRUCTION;
        } else if ("namespace-node".equals(name)) {
            return Type.NAMESPACE;
        } else if ("node".equals(name)) {
            return Type.NODE;
        } else {
            grumble("Unknown type " + name);
            return -1;
        }
    }

    /**
     * Parse a map expression. Requires XPath/XQuery 3.0
     * Provisional syntax
     * map { expr := expr (, expr := expr )*} }
     *
     * @return the map expression
     * @throws XPathException if a static error occurs
     */

    /*@NotNull*/
    protected Expression parseMapExpression() throws XPathException {
        grumble("map expressions require XPath 3.0/XQuery 3.0 to be enabled");
        return new ErrorExpression();
    }

    /**
     * Parse a function call.
     * function-name '(' ( Expression (',' Expression )* )? ')'
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseFunctionCall() throws XPathException {

        String fname = t.currentTokenValue;
        int offset = t.currentTokenStartOffset;
        ArrayList<Expression> args = new ArrayList<Expression>(10);

        StructuredQName functionName = resolveFunctionName(fname);
        IntSet placeMarkers = null;

        // the "(" has already been read by the Tokenizer: now parse the arguments

        nextToken();
        if (t.currentToken != Token.RPAR) {
            while (true) {
                Expression arg = parseFunctionArgument();
                if (arg == null) {
                    // this is a "?" placemarker
                    if (placeMarkers == null) {
                        placeMarkers = new IntArraySet();
                    }
                    placeMarkers.add(args.size());
                    arg = Literal.makeEmptySequence(); // a convenient fiction
                }
                args.add(arg);
                if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    break;
                }
            }
            expect(Token.RPAR);
        }
        nextToken();

        if (scanOnly) {
            return new StringLiteral(StringValue.EMPTY_STRING);
        }

        Expression[] arguments = new Expression[args.size()];
        args.toArray(arguments);

        if (placeMarkers != null) {
            return makeCurriedFunction(offset, functionName, arguments, placeMarkers);
        }

        Expression fcall;
        try {
            fcall = env.getFunctionLibrary().bind(functionName, args.size(), arguments, env, defaultContainer);
        } catch (XPathException err) {
            if (err.getErrorCodeQName() == null) {
                err.setErrorCode("XPST0017");
                err.setIsStaticError(true);
            }
            if (functionName.isInNamespace(NamespaceConstant.MAP_FUNCTIONS_2011)) {
                grumble("Saxon currently implements the XSLT 3.0 map functions in namespace " + NamespaceConstant.MAP_FUNCTIONS);
            } else {
                grumble(err.getMessage(), err.getErrorCodeQName(), offset);
            }
            return new ErrorExpression();
        }
        if (fcall == null) {
            return reportMissingFunction(offset, functionName, arguments);
        }
        //  A QName or NOTATION constructor function must be given the namespace context now
        if (fcall instanceof CastExpression &&
                ((AtomicType) fcall.getItemType(env.getConfiguration().getTypeHierarchy())).isNamespaceSensitive()) {
            ((CastExpression) fcall).setNamespaceResolver(new SavedNamespaceContext(env.getNamespaceResolver()));
        }
        // There are special rules for certain functions appearing in a pattern
        if (language == XSLT_PATTERN) {
            if (fcall instanceof RegexGroup) {
                return Literal.makeEmptySequence();
            } else if (fcall instanceof CurrentGroup) {
                grumble("The current-group() function cannot be used in a pattern",
                        "XTSE1060", offset);
                return new ErrorExpression();
            } else if (fcall instanceof CurrentGroupingKey) {
                grumble("The current-grouping-key() function cannot be used in a pattern",
                        "XTSE1070", offset);
                return new ErrorExpression();
            }
        }
        setLocation(fcall, offset);
        for (Expression argument : arguments) {
            fcall.adoptChildExpression(argument);
        }

        return makeTracer(offset, fcall, Location.FUNCTION_CALL, functionName);

    }

    /*@NotNull*/
    public Expression reportMissingFunction(int offset, /*@NotNull*/ StructuredQName functionName, /*@NotNull*/ Expression[] arguments) throws XPathException {
        String msg = "Cannot find a matching " + arguments.length +
                "-argument function named " + functionName.getClarkName() + "()";
        if (env.getConfiguration().getBooleanProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
            boolean existsWithDifferentArity = false;
            for (int i=0; i<arguments.length+5; i++) {
                if (i != arguments.length) {
                    if (env.getFunctionLibrary().isAvailable(functionName, i)) {
                        existsWithDifferentArity = true;
                        break;
                    }
                }
            }
            if (existsWithDifferentArity) {
                msg += ". The namespace URI and local name are recognized, but the number of arguments is wrong";
            } else {
                String actualURI = functionName.getURI();
                String similarNamespace = NamespaceConstant.findSimilarNamespace(actualURI);
                if (similarNamespace != null) {
                    if (similarNamespace.equals(actualURI)) {
                        if (similarNamespace.equals(NamespaceConstant.SAXON) && env.getConfiguration().getEditionCode().equals("HE")) {
                            msg += ". Saxon extension functions are not available under Saxon-HE";
                        } else if (similarNamespace.equals(NamespaceConstant.SAXON) &&
                                !env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                            msg += ". Saxon extension functions require a Saxon-PE or Saxon-EE license";
                        } else {
                            msg += ". There is no Saxon extension function with the local name " + functionName.getLocalPart();
                        }
                    } else {
                        msg += ". Perhaps the intended namespace was '" + similarNamespace + "'";
                    }
                } else if (actualURI.contains("java")) {
                    if (env.getConfiguration().getEditionCode().equals("HE")) {
                        msg += ". Note that direct calls to Java methods are not available under Saxon-HE";
                    } else {
                        msg += ". For diagnostics on calls to Java methods, use the -TJ command line option " +
                                "or set the Configuration property FeatureKeys.TRACE_EXTERNAL_FUNCTIONS";
                    }
                } else if (actualURI.startsWith("clitype:")) {
                    if (env.getConfiguration().getEditionCode().equals("HE")) {
                        msg += ". Note that direct calls to external .NET methods are not available under Saxon-HE";
                    } else {
                        msg += ". For diagnostics on calls to .NET methods, use the -TJ command line option " +
                                "or call processor.SetProperty(\"http://saxon.sf.net/feature/trace-external-functions\", \"true\")";
                    }
                }
            }
        } else {
            msg += ". External function calls have been disabled";
        }
        if (env.isInBackwardsCompatibleMode()) {
            // treat this as a dynamic error to be reported only if the function call is executed
            XPathException err = new XPathException(msg, "XTDE1425");
            ErrorExpression exp = new ErrorExpression(err);
            setLocation(exp);
            return exp;
        }
        grumble(msg, "XPST0017", offset);
        return new ErrorExpression();
    }

    /**
     * Interpret a function name, returning it as a resolved QName
     *
     * @param fname the lexical QName used as the function name; or an EQName presented
     *              by the tokenizer as a name in Clark notation
     * @return the Structured QName obtained by resolving any prefix in the function name
     * @throws XPathException if the supplied name is not a valid QName or if its prefix
     *                        is not in scope
     */

    /*@NotNull*/
    protected StructuredQName resolveFunctionName(/*@NotNull*/ String fname) throws XPathException {
        StructuredQName functionName;
        String uri;
        String local;
        int offset = t.currentTokenStartOffset;
        if (fname.startsWith("{")) {
            if (!allowXPath30Syntax) {
                grumble("Expanded QName syntax requires XPath 3.0/XQuery 3.0");
            }
            functionName = StructuredQName.fromClarkName(fname);
            uri = functionName.getURI();
            local = functionName.getLocalPart();
        } else {
            String[] parts;
            try {
                parts = nameChecker.getQNameParts(fname);
            } catch (QNameException e) {
                grumble("Function name is not a valid QName: " + fname + "()", "XPST0003", offset);
                throw new XPathException(""); // unreachable instruction
            }
            local = parts[1];
            if (parts[0].length() == 0) {
                uri = env.getDefaultFunctionNamespace();
            } else {
                try {
                    uri = env.getURIForPrefix(parts[0]);
                } catch (XPathException err) {
                    grumble(err.getMessage(), "XPST0081", offset);
                    throw err; // unreachable instruction
                }
            }
            functionName = new StructuredQName(parts[0], uri, local);
        }

        if (uri.equals(NamespaceConstant.SCHEMA)) {
            ItemType t = Type.getBuiltInItemType(uri, local);
            if (t instanceof BuiltInAtomicType && !env.isAllowedBuiltInType((BuiltInAtomicType) t)) {
                grumble("The type " + fname + " is not recognized by a Basic XSLT Processor. ", "XPST0080", offset);
                throw new XPathException(""); // unreachable instruction
            }
        }
        if (local.equals("serialize") && uri.equals(NamespaceConstant.FN) && env instanceof QueryModule) {
            String defaultUri = (allowXPath30Syntax ? NamespaceConstant.XQUERY : "");
            StructuredQName serializationQName = makeStructuredQName("serialization", defaultUri);
            if (((QueryModule) env).getFeaturesProhibited().contains(serializationQName)) {
                grumble("The fn:serialize function is not allowed when the serialization feature is disabled (see line number: " + getTokenizer().getLineNumber() + ")", "XQST0128");
            }
        }
        return functionName;
    }

    /**
     * Parse an argument to a function call. Separate method so it can
     * be overridden. With higher-order-function syntax in XPath 3.0/XQuery 3.0,
     * this returns null if the pseudo-argument "?" is found.
     *
     * @return the Expression used as the argument, or null if the argument is the place-holder "?"
     * @throws XPathException if the argument expression does not parse correctly
     */

    /*@Nullable*/
    public Expression parseFunctionArgument() throws XPathException {
        return parseExprSingle();
    }

    /**
     * Parse a literal function item (introduced in XQuery 1.1)
     * Syntax: QName # integer
     * The QName and # have already been read
     *
     * @return an ExternalObject representing the function item
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is encountered
     */

    /*@NotNull*/
    protected Expression parseLiteralFunctionItem() throws XPathException {
        grumble("Literal function items are allowed only in XPath 3.0 / XQuery 3.0");
        return new ErrorExpression();
    }

    /**
     * Parse an inline function
     * "function" "(" ParamList? ")" ("as" SequenceType)? EnclosedExpr
     * On entry, "function (" has already been read
     * @param annotations the function annotations, which have already been read, or null if there are none
     * @return the parsed inline function
     * @throws XPathException if a syntax error is found
     */

    /*@NotNull*/
    protected Expression parseInlineFunction(Map<StructuredQName, Annotation> annotations) throws XPathException {
        grumble("Inline functions are allowed only in XQuery 3.0");
        return new ErrorExpression();
    }

    /**
     * Process a function call in which one or more of the argument positions are
     * represented as "?" placemarkers (indicating partial application or currying)
     *
     * @param offset       the position of the expression in the source text
     * @param name         the function name (as if there were no currying)
     * @param args         the arguments (with EmptySequence in the placemarker positions)
     * @param placeMarkers the positions of the placemarkers    @return the curried function
     * @return the curried function
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression makeCurriedFunction(
            int offset, StructuredQName name, Expression[] args, IntSet placeMarkers)
            throws XPathException {
        grumble("Partial function application is allowed only in XPath/XQuery 3.0");
        return new ErrorExpression();
    }


    //////////////////////////////////////////////////////////////////////////////////
    // Routines for handling range variables
    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the stack of in-scope range variables
     *
     * @return the stack of variables
     */

    public Stack<Binding> getRangeVariables() {
        return rangeVariables;
    }

    /**
     * Set a new stack of in-scope range variables
     *
     * @param variables the stack of variables
     */

    public void setRangeVariables(Stack<Binding> variables) {
        this.rangeVariables = variables;
    }

    /**
     * Declare a range variable (record its existence within the parser).
     * A range variable is a variable declared within an expression, as distinct
     * from a variable declared in the context.
     *
     * @param declaration the variable declaration to be added to the stack
     * @throws XPathException if any error is encountered
     */

    public void declareRangeVariable(Binding declaration) throws XPathException {
        rangeVariables.push(declaration);
    }

    /**
     * Note when the most recently declared range variable has gone out of scope
     */

    public void undeclareRangeVariable() {
        rangeVariables.pop();
    }

    /**
     * Locate a range variable with a given name. (By "range variable", we mean a
     * variable declared within the expression where it is used.)
     *
     * @param qName identifies the name of the range variable
     * @return null if not found (this means the variable is probably a
     *         context variable); otherwise the relevant RangeVariable
     */

    /*@Nullable*/
    protected Binding findRangeVariable(StructuredQName qName) {
        for (int v = rangeVariables.size() - 1; v >= 0; v--) {
            Binding b = rangeVariables.elementAt(v);
            if (b.getVariableQName().equals(qName)) {
                return b;
            }
        }
        return null;  // not an in-scope range variable
    }

    /**
     * Set the range variable stack. Used when parsing a nested subexpression
     * inside an attribute constructor.
     *
     * @param stack the stack to be used for local variables declared within the expression
     */

    public void setRangeVariableStack(Stack<Binding> stack) {
        rangeVariables = stack;
    }

    /**
     * Make a NameCode, using the static context for namespace resolution
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively,
     *                   a QName in Clark notation ({uri}local)
     * @param useDefault Defines the action when there is no prefix. If
     *                   true, use the default namespace URI for element names. If false,
     *                   use no namespace URI (as for attribute names).
     * @return the namecode, which can be used to identify this name in the
     *         name pool
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     */

    public final int makeNameCode(/*@NotNull*/ String qname, boolean useDefault) throws XPathException {
        if (scanOnly) {
            return StandardNames.XML_SPACE;
        }
        if (qname.startsWith("{")) {
            return env.getNamePool().allocateClarkName(qname);
        }
        try {
            String[] parts = nameChecker.getQNameParts(qname);
            String prefix = parts[0];
            if (prefix.length() == 0) {
                if (useDefault) {
                    String uri = env.getDefaultElementNamespace();
                    return env.getNamePool().allocate("", uri, qname);
                } else {
                    return env.getNamePool().allocate("", "", qname);
                }
            } else {
                try {
                    String uri = env.getURIForPrefix(prefix);
                    return env.getNamePool().allocate(prefix, uri, parts[1]);
                } catch (XPathException err) {
                    grumble(err.getMessage(), err.getErrorCodeQName(), -1);
                    return -1;
                }
            }
        } catch (QNameException e) {
            grumble(e.getMessage());
            return -1;
        }
    }

    /**
     * Make a NameCode, using the static context for namespace resolution.
     * This variant of the method does not call "grumble" to report any errors
     * to the ErrorListener, it only reports errors by throwing exceptions. This
     * allows the caller to control the message output.
     *
     * @param qname      The name as written, in the form "[prefix:]localname"
     * @param defaultUri Defines the action when there is no prefix. If
     *                   true, use the default namespace URI for element names. If false,
     *                   use no namespace URI (as for attribute names).
     * @return the namecode, which can be used to identify this name in the
     *         name pool
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     * @throws QNameException if the name is not a lexically valid QName
     */

    public final StructuredQName makeStructuredQNameSilently(/*@NotNull*/ String qname, String defaultUri)
            throws XPathException, QNameException {
        if (scanOnly) {
             return new StructuredQName("", NamespaceConstant.SAXON, "dummy");
        }
        String[] parts = nameChecker.getQNameParts(qname);
        String prefix = parts[0];
           if (prefix.length() == 0) {
                return new StructuredQName("", defaultUri, qname);
        } else {
            String uri = env.getURIForPrefix(prefix);
            return new StructuredQName(prefix, uri, parts[1]);
        }
    }

    /**
     * Make a Structured QName, using the static context for namespace resolution
     *
     *
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively, a QName in
     *                   Clark format ({uri}local)
     * @param defaultUri The URI to be used if the name is written as a localname with no prefix
     * @return the QName as an instance of StructuredQName
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     */

    /*@NotNull*/
    public final StructuredQName makeStructuredQName(/*@NotNull*/ String qname, String defaultUri) throws XPathException {
        if (scanOnly) {
            return new StructuredQName("", NamespaceConstant.SAXON, "dummy");
        }
        if (qname.startsWith("{")) {
            return StructuredQName.fromClarkName(qname);
        }
        try {
            String[] parts = nameChecker.getQNameParts(qname);
            String prefix = parts[0];
            if (prefix.length() == 0) {
                return new StructuredQName("", defaultUri, qname);
            } else {
                try {
                    String uri = env.getURIForPrefix(prefix);
                    return new StructuredQName(prefix, uri, parts[1]);
                } catch (XPathException err) {
                    grumble(err.getMessage(), err.getErrorCodeQName(), -1);
                    throw err; // dummy instruction to mollify the compiler
                }
            }
        } catch (QNameException e) {
            grumble(e.getMessage());
            throw new XPathException(e); // dummy instruction to mollify the compiler
        }
    }

    /**
     * Make a FingerprintedQName, using the static context for namespace resolution
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively, a QName in
     *                   Clark format ({uri}local)
     * @param useDefault Defines the action when there is no prefix. If
     *                   true, use the default namespace URI for element names. If false,
     *                   use no namespace URI (as for attribute names).
     * @return the fingerprinted QName
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     * @throws QNameException if the supplied qname is not a lexically valid QName
     */

    /*@NotNull*/
    public final NodeName makeNodeName(/*@NotNull*/ String qname, boolean useDefault) throws XPathException, QNameException {
        if (scanOnly) {
            return new NoNamespaceName("dummy");
        }
        if (qname.startsWith("{")) {
            return FingerprintedQName.fromClarkName(qname);
        }
        String[] parts = nameChecker.getQNameParts(qname);
        String prefix = parts[0];
        if (prefix.length() == 0) {
            if (useDefault) {
                String uri = env.getDefaultElementNamespace();
                int nc = env.getNamePool().allocate("", uri, qname);
                return new FingerprintedQName("", uri, qname, nc);
            } else {
                int nc = env.getNamePool().allocate("", "", qname);
                return new NoNamespaceName(qname, nc);
            }
        } else {
            String uri = env.getURIForPrefix(prefix);
            int nc = env.getNamePool().allocate(prefix, uri, parts[1]);
            return new FingerprintedQName(prefix, uri, parts[1], nc);
        }
    }


    /**
     * Make a NameTest, using the static context for namespace resolution
     *
     * @param nodeType   the type of node required (identified by a constant in
     *                   class Type)
     * @param qname      the lexical QName of the required node; alternatively,
     *                   a QName in Clark notation ({uri}local)
     * @param useDefault true if the default namespace should be used when
     *                   the QName is unprefixed
     * @return a NameTest, representing a pattern that tests for a node of a
     *         given node kind and a given name
     * @throws XPathException if the QName is invalid
     */

    /*@NotNull*/
    public NameTest makeNameTest(short nodeType, /*@NotNull*/ String qname, boolean useDefault)
            throws XPathException {
        int nameCode = makeNameCode(qname, useDefault);
        return new NameTest(nodeType, nameCode, env.getNamePool());
    }

    /**
     * Make a NamespaceTest (name:*)
     *
     * @param nodeType integer code identifying the type of node required
     * @param prefix   the namespace prefix
     * @return the NamespaceTest, a pattern that matches all nodes in this
     *         namespace
     * @throws XPathException if the namespace prefix is not declared
     */

    /*@NotNull*/
    public NamespaceTest makeNamespaceTest(short nodeType, /*@NotNull*/ String prefix)
            throws XPathException {
        if (scanOnly) {
            // return an arbitrary namespace if we're only doing a syntax check
            return new NamespaceTest(env.getNamePool(), nodeType, NamespaceConstant.SAXON);
        }

        String uri = "";
        if (prefix.charAt(0) == '{') {
            // EQName wildcard syntax "uri":* delivered by the tokenizer as {uri}*
            int closeBrace = prefix.indexOf('}');
            uri = prefix.substring(1, closeBrace);
        } else {
            try {
                uri = env.getURIForPrefix(prefix);
            } catch (XPathException e) {
                // env.getURIForPrefix can return a dynamic error
                grumble(e.getMessage(), "XPST0081");
            }
        }

        return new NamespaceTest(env.getNamePool(), nodeType, uri);

    }

    /**
     * Make a LocalNameTest (*:name)
     *
     * @param nodeType  the kind of node to be matched
     * @param localName the requred local name
     * @return a LocalNameTest, a pattern which matches all nodes of a given
     *         local name, regardless of namespace
     * @throws XPathException if the local name is invalid
     */

    /*@NotNull*/
    public LocalNameTest makeLocalNameTest(short nodeType, String localName)
            throws XPathException {
        if (!nameChecker.isValidNCName(localName)) {
            grumble("Local name [" + localName + "] contains invalid characters");
        }
        return new LocalNameTest(env.getNamePool(), nodeType, localName);
    }

    /**
     * Set location information on an expression. At present this consists of a simple
     * line number. Needed mainly for XQuery.
     *
     * @param exp the expression whose location information is to be set
     */

    protected void setLocation(/*@NotNull*/ Expression exp) {
        setLocation(exp, t.currentTokenStartOffset);
    }

    /**
     * Set location information on an expression. At present only the line number
     * is retained. Needed mainly for XQuery. This version of the method supplies an
     * explicit offset (character position within the expression or query), which the tokenizer
     * can convert to a line number and column number.
     *
     * @param exp    the expression whose location information is to be set
     * @param offset the character position within the expression (ignoring newlines)
     */

    public void setLocation(/*@NotNull*/ Expression exp, int offset) {
        // Although we could get the column position from the offset, we choose not to retain this,
        // and only use the line number
        if (exp != null) {
            int line = t.getLineNumber(offset);
            if (exp.getLocationId() == -1) {
                int loc = env.getLocationMap().allocateLocationId(env.getSystemId(), line);
                exp.setLocationId(loc);
                // add a temporary container to provide location information
            }
            if (exp.getContainer() == null) {
                exp.setContainer(defaultContainer);
            }
        }
    }

    /**
     * Set location information on a clause of a FLWOR expression. At present only the line number
     * is retained. Needed mainly for XQuery. This version of the method supplies an
     * explicit offset (character position within the expression or query), which the tokenizer
     * can convert to a line number and column number.
     *
     * @param clause the clause whose location information is to be set
     * @param offset the character position within the expression (ignoring newlines)
     */

    public void setLocation(/*@NotNull*/ Clause clause, int offset) {
        // Although we could get the column position from the offset, we choose not to retain this,
        // and only use the line number
        int line = t.getLineNumber(offset);
        int loc = env.getLocationMap().allocateLocationId(env.getSystemId(), line);
        clause.setLocationId(loc);
    }


    /**
     * If tracing, wrap an expression in a trace instruction
     *
     * @param startOffset the position of the expression in the soruce
     * @param exp         the expression to be wrapped
     * @param construct   integer constant identifying the kind of construct
     * @param qName       the name of the construct (if applicable)
     * @return the expression that does the tracing
     */

    public Expression makeTracer(int startOffset, Expression exp, int construct, /*@Nullable*/ StructuredQName qName) {
        if (codeInjector != null) {
            return codeInjector.inject(exp, env, construct, qName);
        } else {
            return exp;
        }
    }

    /**
     * Test whether the current token is a given keyword.
     *
     * @param s The string to be compared with the current token
     * @return true if they are the same
     */

    protected boolean isKeyword(String s) {
        return (t.currentToken == Token.NAME && t.currentTokenValue.equals(s));
    }

    /**
     * Normalize an EQName. This is written in the source code in the form "uri":local, but by the
     * time it gets here it has been converted to Clark format {uri}local. This method collapses
     * whitespace within the URI
     *
     * @param s the EQName in the form of a Clark name
     * @return the normalized EQName
     * @throws XPathException so that the XQuery implementation in a subclass can do so.
     */

    protected String normalizeEQName(String s) throws XPathException {
        // overridden for XQuery
        if (!Whitespace.containsWhitespace(s)) {
            return s;
        }
        StructuredQName sq = StructuredQName.fromClarkName(s);
        CharSequence uri = Whitespace.collapseWhitespace(sq.getURI());
        return "{" + uri + "}" + sq.getLocalPart();
    }

    /**
     * Set that we are parsing in "scan only"
     *
     * @param scanOnly true if parsing is to proceed in scan-only mode. In this mode
     *                 namespace bindings are not yet known, so no attempt is made to look up namespace
     *                 prefixes.
     */

    public void setScanOnly(boolean scanOnly) {
        this.scanOnly = scanOnly;
    }

    /**
     * A Container used on a temporary basis to hold an expression while it is being parsed
     */

    protected static class TemporaryContainer implements Container, LocationProvider, Serializable {
        private LocationMap map;
        private int locationId;
        private Executable executable;

        public TemporaryContainer(LocationMap map, int locationId) {
            this.map = map;
            this.locationId = locationId;
        }

        /**
         * Get the granularity of the container.
         *
         * @return 0 for a temporary container created during parsing; 1 for a container
         *         that operates at the level of an XPath expression; 2 for a container at the level
         *         of a global function or template
         */

        public int getContainerGranularity() {
            return 0;
        }

        public void setExecutable(Executable exec) {
            executable = exec;
        }

        public Executable getExecutable() {
            return executable;
        }

        public LocationProvider getLocationProvider() {
            return map;
        }

        /*@Nullable*/
        public String getPublicId() {
            return null;
        }

        /*@Nullable*/
        public String getSystemId() {
            return map.getSystemId(locationId);
        }

        public int getLineNumber() {
            return map.getLineNumber(locationId);
        }

        public int getColumnNumber() {
            return -1;
        }

        /*@Nullable*/
        public String getSystemId(long locationId) {
            return getSystemId();
        }

        public int getLineNumber(long locationId) {
            return getLineNumber();
        }

        public int getColumnNumber(long locationId) {
            return getColumnNumber();
        }

        /**
         * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
         *
         * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
         */

        public int getHostLanguage() {
            return Configuration.XPATH;
        }

    }

}

/*

The following copyright notice is copied from the licence for xt, from which the
original version of this module was derived:
--------------------------------------------------------------------------------
Copyright (c) 1998, 1999 James Clark

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED ``AS IS'', WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL JAMES CLARK BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of James Clark shall
not be used in advertising or otherwise to promote the sale, use or
other dealings in this Software without prior written authorization
from James Clark.
---------------------------------------------------------------------------
*/