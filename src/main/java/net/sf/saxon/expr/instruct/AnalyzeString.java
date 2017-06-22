////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An xsl:analyze-string element in the stylesheet. New at XSLT 2.0
 */

public class AnalyzeString extends Instruction {

    private Expression select;
    private Expression regex;
    private Expression flags;
    private Expression matching;
    private Expression nonMatching;
    private RegularExpression pattern;

    private boolean allow30features = false;
    private boolean useXsltErrorCodes = true;

    /**
     * Construct an AnalyzeString instruction
     *
     * @param select      the expression containing the input string
     * @param regex       the regular expression
     * @param flags       the flags parameter
     * @param matching    actions to be applied to a matching substring
     * @param nonMatching actions to be applied to a non-matching substring
     * @param pattern     the compiled regular expression, if it was known statically
     */
    public AnalyzeString(Expression select,
                         Expression regex,
                         Expression flags,
                         Expression matching,
                         Expression nonMatching,
                         RegularExpression pattern) {
        this.select = select;
        this.regex = regex;
        this.flags = flags;
        this.matching = matching;
        this.nonMatching = nonMatching;
        this.pattern = pattern;

        Iterator kids = iterateSubExpressions();
        while (kids.hasNext()) {
            Expression child = (Expression) kids.next();
            adoptChildExpression(child);
        }

    }

    public int getInstructionNameCode() {
        return StandardNames.XSL_ANALYZE_STRING;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered.
     */

    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD | Expression.ITERATE_METHOD;
    }

    /**
     * @return the expression containing the input string
     */
    public Expression getSelectExpression() {
        return select;
    }

    /**
     * @return the compiled regular expression, if it was known statically
     */
    public RegularExpression getPatternExpression() {
        return pattern;
    }

    /**
     * @return the flags parameter
     */
    public Expression getFlagsExpression() {
        return flags;
    }

    /**
     * @return if allow XSLT 3.0 features
     */
    public boolean isAllow30features() {
        return allow30features;
    }

    /**
     * Say whether the expression should return the error codes for the fn:analyze-string function
     * or the xsl:analyze-string instruction
     *
     * @param xslt if true use the error codes for  xsl:analyze-string, otherwise use the error codes for fn:analyze-string
     */
    public void setUseXsltErrorCodes(boolean xslt) {
        useXsltErrorCodes = xslt;
    }

    /**
     * Ask whether the expression should return the error codes for the fn:analyze-string function
     * or the xsl:analyze-string instruction
     *
     * @return true if using the error codes for  xsl:analyze-string, otherwise use the error codes for fn:analyze-string
     */

    public boolean isUseXsltErrorCodes() {
        return useXsltErrorCodes;
    }

    /**
     * @return the regular expression
     */
    public Expression getRegexExpression() {
        return regex;
    }

    /**
     * Get the expression used to process matching substrings
     *
     * @return the expression used to process matching substrings
     */

    public Expression getMatchingExpression() {
        return matching;
    }

    /**
     * Get the expression used to process non-matching substrings
     *
     * @return the expression used to process non-matching substrings
     */

    public Expression getNonMatchingExpression() {
        return nonMatching;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        select = visitor.simplify(select);
        regex = visitor.simplify(regex);
        flags = visitor.simplify(flags);
        matching = visitor.simplify(matching);
        nonMatching = visitor.simplify(nonMatching);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        allow30features = visitor.getStaticContext().getXPathLanguageLevel().equals(DecimalValue.THREE);
        select = visitor.typeCheck(select, contextItemType);
        adoptChildExpression(select);
        regex = visitor.typeCheck(regex, contextItemType);
        adoptChildExpression(regex);
        flags = visitor.typeCheck(flags, contextItemType);
        adoptChildExpression(flags);
        if (matching != null) {
            matching = visitor.typeCheck(matching, new ExpressionVisitor.ContextItemType(BuiltInAtomicType.STRING, false));
            adoptChildExpression(matching);
        }
        if (nonMatching != null) {
            nonMatching = visitor.typeCheck(nonMatching, new ExpressionVisitor.ContextItemType(BuiltInAtomicType.STRING, false));
            adoptChildExpression(nonMatching);
        }
        RoleLocator role = new RoleLocator(RoleLocator.INSTRUCTION, "analyze-string/select", 0);
        SequenceType required = (allow30features ? SequenceType.OPTIONAL_STRING : SequenceType.SINGLE_STRING);
        // see bug 7976
        select = TypeChecker.staticTypeCheck(select, required, false, role, visitor);

        role = new RoleLocator(RoleLocator.INSTRUCTION, "analyze-string/regex", 0);
        regex = TypeChecker.staticTypeCheck(regex, SequenceType.SINGLE_STRING, false, role, visitor);

        role = new RoleLocator(RoleLocator.INSTRUCTION, "analyze-string/flags", 0);
        flags = TypeChecker.staticTypeCheck(flags, SequenceType.SINGLE_STRING, false, role, visitor);

        return this;
    }


    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        select = visitor.optimize(select, contextItemType);
        adoptChildExpression(select);
        regex = visitor.optimize(regex, contextItemType);
        adoptChildExpression(regex);
        flags = visitor.optimize(flags, contextItemType);
        adoptChildExpression(flags);
        if (matching != null) {
            matching = matching.optimize(visitor, new ExpressionVisitor.ContextItemType(BuiltInAtomicType.STRING, false));
            adoptChildExpression(matching);
        }
        if (nonMatching != null) {
            nonMatching = nonMatching.optimize(visitor, new ExpressionVisitor.ContextItemType(BuiltInAtomicType.STRING, false));
            adoptChildExpression(nonMatching);
        }
        if (pattern == null && regex instanceof StringLiteral && flags instanceof StringLiteral) {
            try {
                final CharSequence regex = ((StringLiteral) this.regex).getStringValue();
                final CharSequence flagstr = ((StringLiteral) flags).getStringValue();

                String hostLang = (DecimalValue.THREE.equals(visitor.getStaticContext().getXPathLanguageLevel()) ? "XP30" : "XP20");
                List<String> warnings = new ArrayList<String>();
                pattern = Configuration.getPlatform().compileRegularExpression(regex, flagstr.toString(), hostLang, warnings);
                for (String w : warnings) {
                    visitor.getStaticContext().issueWarning(w, this);
                }

                if (pattern.matches("")) {
                    // prevent it being reported more than once
                    pattern = Configuration.getPlatform().compileRegularExpression("x", "", "XP20", warnings);
                    invalidRegex("The regular expression must not be one that matches a zero-length string",
                            (useXsltErrorCodes ? "XTDE1150" : "FORX0003"));
                }
            } catch (XPathException err) {
                if ("XTDE1150".equals(err.getErrorCodeLocalPart())) {
                    throw err;
                }
                if ("FORX0001".equals(err.getErrorCodeLocalPart())) {
                    invalidRegex("Error in regular expression flags: " + err, (useXsltErrorCodes ? "XTDE1145" : "FORX0001"));
                } else {
                    invalidRegex("Error in regular expression: " + err, (useXsltErrorCodes ? "XTDE1140" : err.getErrorCodeLocalPart()));
                }
            }
        }
        return this;
    }

    private void invalidRegex(String message, String errorCode) throws XPathException {
        pattern = null;
        XPathException err = new XPathException(message, errorCode);
        err.setLocator(this);
        throw err;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new AnalyzeString(copy(select), copy(regex), copy(flags), copy(matching), copy(nonMatching), pattern);
    }

    private Expression copy(Expression exp) {
        return (exp == null ? null : exp.copy());
    }


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        if (matching != null) {
            matching.checkPermittedContents(parentType, env, false);
        }
        if (nonMatching != null) {
            nonMatching.checkPermittedContents(parentType, env, false);
        }
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @param th the type hierarchy cache
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (matching != null) {
            if (nonMatching != null) {
                return Type.getCommonSuperType(matching.getItemType(th), nonMatching.getItemType(th), th);
            } else {
                return matching.getItemType(th);
            }
        } else {
            if (nonMatching != null) {
                return nonMatching.getItemType(th);
            } else {
                return ErrorType.getInstance();
            }
        }
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    public int computeDependencies() {
        // some of the dependencies in the "action" part and in the grouping and sort keys aren't relevant,
        // because they don't depend on values set outside the for-each-group expression
        int dependencies = 0;
        dependencies |= select.getDependencies();
        dependencies |= regex.getDependencies();
        dependencies |= flags.getDependencies();
        if (matching != null) {
            dependencies |= (matching.getDependencies() & ~
                    (StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_REGEX_GROUP));
        }
        if (nonMatching != null) {
            dependencies |= (nonMatching.getDependencies() & ~
                    (StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_REGEX_GROUP));
        }
        return dependencies;
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        regex = doPromotion(regex, offer);
        flags = doPromotion(flags, offer);
        if (matching != null) {
            matching = doPromotion(matching, offer);
        }
        if (nonMatching != null) {
            nonMatching = doPromotion(nonMatching, offer);
        }
    }

    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(5);
        list.add(select);
        list.add(regex);
        list.add(flags);
        if (matching != null) {
            list.add(matching);
        }
        if (nonMatching != null) {
            list.add(nonMatching);
        }
        return list.iterator();
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        ArrayList<SubExpressionInfo> list = new ArrayList<SubExpressionInfo>(5);
        list.add(new SubExpressionInfo(select, true, false, NODE_VALUE_CONTEXT));
        list.add(new SubExpressionInfo(regex, true, false, NODE_VALUE_CONTEXT));
        list.add(new SubExpressionInfo(flags, true, false, NODE_VALUE_CONTEXT));
        if (matching != null) {
            list.add(new SubExpressionInfo(matching, false, true, NAVIGATION_CONTEXT));
        }
        if (nonMatching != null) {
            list.add(new SubExpressionInfo(nonMatching, false, true, NAVIGATION_CONTEXT));
        }
        return list.iterator();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     *
     * @param original    the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (select == original) {
            select = replacement;
            found = true;
        }
        if (regex == original) {
            regex = replacement;
            found = true;
        }
        if (flags == original) {
            flags = replacement;
            found = true;
        }
        if (matching == original) {
            matching = replacement;
            found = true;
        }
        if (nonMatching == original) {
            nonMatching = replacement;
            found = true;
        }
        return found;
    }

    /**
     * ProcessLeavingTail: called to do the real work of this instruction. This method
     * must be implemented in each subclass. The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     *         a function call or template call that is delegated to the caller, to be made after the stack has
     *         been unwound so as to save stack space.
     */

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        RegexIterator iter = getRegexIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(iter);
        c2.setCurrentRegexIterator(iter);

        while (true) {
            Item it = iter.next();
            if (it == null) {
                break;
            }
            if (iter.isMatching()) {
                if (matching != null) {
                    //if (processingAction == null) {
                    matching.process(c2);
                    //} else {
                    //    iter.processMatchingSubstring(context, processingAction);
                    //}
                }
            } else {
                if (nonMatching != null) {
                    nonMatching.process(c2);
                }
            }
        }

        return null;

    }

    /**
     * Get an iterator over the substrings defined by the regular expression
     *
     * @param context the evaluation context
     * @return an iterator that returns matching and nonmatching substrings
     * @throws XPathException if evaluation fails with a dynamic error
     */

    private RegexIterator getRegexIterator(XPathContext context) throws XPathException {
        CharSequence input = select.evaluateAsString(context);

        RegularExpression re = pattern;
        if (re == null) {
            CharSequence flagstr = flags.evaluateAsString(context);
            re = Configuration.getPlatform().compileRegularExpression(
                    regex.evaluateAsString(context), flagstr.toString(), (allow30features ? "XP30" : "XP20"), null);

            if (re.matches("")) {
                dynamicError("The regular expression must not be one that matches a zero-length string",
                        "XTDE1150", context);
            }
        }

        return re.analyze(input);
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    public SequenceIterator<StringValue> iterate(XPathContext context) throws XPathException {
        RegexIterator iter = getRegexIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(iter);
        c2.setCurrentRegexIterator(iter);

        AnalyzeMappingFunction fn = new AnalyzeMappingFunction(iter, c2, nonMatching, matching);
        return new ContextMappingIterator(fn, c2);
    }

    

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("analyzeString");
        out.startSubsidiaryElement("select");
        select.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("regex");
        regex.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("flags");
        flags.explain(out);
        out.endSubsidiaryElement();
        if (matching != null) {
            out.startSubsidiaryElement("matching");
            matching.explain(out);
            out.endSubsidiaryElement();
        }
        if (nonMatching != null) {
            out.startSubsidiaryElement("nonMatching");
            nonMatching.explain(out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }


}

