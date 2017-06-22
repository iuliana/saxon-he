////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.Iterator;


/**
 * An xsl:processing-instruction element in the stylesheet, or a processing-instruction
 * constructor in a query
 */

public class ProcessingInstruction extends SimpleNodeConstructor {

    /*@NotNull*/ private Expression name;

    /**
     * Create an xsl:processing-instruction instruction
     * @param name the expression used to compute the name of the generated
     * processing-instruction
     */

    public ProcessingInstruction(/*@NotNull*/ Expression name) {
        this.name = name;
        adoptChildExpression(name);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * @return the string "xsl:processing-instruction"
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_PROCESSING_INSTRUCTION;
    }

    /**
     * Get the expression that defines the processing instruction name
     * @return the expression that defines the processing instruction name
     */

    /*@NotNull*/
    public Expression getNameExpression() {
        return name;
    }

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return NodeKindTest.PROCESSING_INSTRUCTION;
    }

    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        name = visitor.simplify(name);
        return super.simplify(visitor);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ProcessingInstruction exp = new ProcessingInstruction(name.copy());
        exp.setSelect(select.copy(), getExecutable().getConfiguration());
        return exp;
    }

    public void localTypeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        name = visitor.typeCheck(name, contextItemType);
        adoptChildExpression(name);

        RoleLocator role = new RoleLocator(RoleLocator.INSTRUCTION, "processing-instruction/name", 0);
        //role.setSourceLocator(this);
        name = TypeChecker.staticTypeCheck(name, SequenceType.SINGLE_STRING, false, role, visitor);
        adoptChildExpression(name);

        // Do early checking of name if known statically

        if (name instanceof Literal) {
            String s = ((Literal)name).getValue().getStringValue();
            checkName(Whitespace.trim(s), env.makeEarlyEvaluationContext());
        }

        // Do early checking of content if known statically

        if (select instanceof Literal) {
            String s = ((Literal)select).getValue().getStringValue();
            String s2 = checkContent(s, env.makeEarlyEvaluationContext());
            if (!s2.equals(s)) {
                setSelect(new StringLiteral(s2), env.getConfiguration());
            }
        }
    }

    public int getDependencies() {
        return name.getDependencies() | super.getDependencies();
    }

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(name, select);
    }

    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        return new PairIterator<SubExpressionInfo>(
            new SubExpressionInfo(name, true, false, NODE_VALUE_CONTEXT),
            new SubExpressionInfo(select, true, false, NODE_VALUE_CONTEXT)
        );
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (select == original) {
            select = replacement;
            found = true;
        }
        if (name == original) {
            name = replacement;
            found = true;
        }
        return found;
    }


    /**
     * Offer promotion for subexpressions. The offer will be accepted if the subexpression
     * is not dependent on the factors (e.g. the context item) identified in the PromotionOffer.
     * By default the offer is not accepted - this is appropriate in the case of simple expressions
     * such as constant values and variable references where promotion would give no performance
     * advantage. This method is always called at compile time.
     *
     * @param offer details of the offer, for example the offer to move
     *     expressions that don't depend on the context to an outer level in
     *     the containing expression
     * @exception XPathException if any error is detected
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        name = doPromotion(name, offer);
        super.promoteInst(offer);
    }


    /**
     * Process the value of the node, to create the new node.
     * @param value the string value of the new node
     * @param context the dynamic evaluation context
     * @throws XPathException
     */

    public void processValue(CharSequence value, XPathContext context) throws XPathException {
        String expandedName = evaluateName(context);
        if (expandedName != null) {
            String data = checkContent(value.toString(), context);
            SequenceReceiver out = context.getReceiver();
            out.processingInstruction(expandedName, data, locationId, 0);
        }
    }

    /**
     * Check the content of the node, and adjust it if necessary
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    protected String checkContent(String data, XPathContext context) throws XPathException {
        if (isXSLT()) {
            return checkContentXSLT(data);
        } else {
            try {
                return checkContentXQuery(data);
            } catch (XPathException err) {
                err.setXPathContext(context);
                err.setLocator(this);
                throw err;
            }
        }
    }

    /**
     * Check the content of the node, and adjust it if necessary, using the XSLT rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     */

    public static String checkContentXSLT(String data)  {
        int hh;
        while ((hh = data.indexOf("?>")) >= 0) {
            data = data.substring(0, hh + 1) + ' ' + data.substring(hh + 1);
        }
        return Whitespace.removeLeadingWhitespace(data).toString();
    }

    /**
     * Check the content of the node, and adjust it if necessary, using the XQuery rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    public static String checkContentXQuery(String data) throws XPathException {
        if (data.contains("?>")) {
            throw new XPathException("Invalid characters (?>) in processing instruction", "XQDY0026");
        }
        return Whitespace.removeLeadingWhitespace(data).toString();
    }

    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        String expandedName = evaluateName(context);
        return new NoNamespaceName(expandedName);
    }

    /**
     * Evaluate the name of the processing instruction.
     * @param context the dynamic evaluation context
     * @return the name of the processing instruction (an NCName), or null, incicating an invalid name
     * @throws XPathException if evaluation fails, or if the recoverable error is treated as fatal
     */
    private String evaluateName(XPathContext context) throws XPathException {
        String expandedName;
        try {
            expandedName = Whitespace.trim(name.evaluateAsString(context));
        } catch (ClassCastException err) {
            XPathException e = new XPathException("Processing instruction name is not a string");
            e.setXPathContext(context);
            e.setErrorCode("XQDY0041");
            throw dynamicError(this, e, context);
        }
        checkName(expandedName, context);
        return expandedName;
    }

    private void checkName(String expandedName, XPathContext context) throws XPathException {
        if (!(context.getConfiguration().getNameChecker().isValidNCName(expandedName))) {
            XPathException e = new XPathException("Processing instruction name " + Err.wrap(expandedName) + " is not a valid NCName");
            e.setXPathContext(context);
            e.setErrorCode((isXSLT() ? "XTDE0890" : "XQDY0041"));
            throw dynamicError(this, e, context);
        }
        if (expandedName.equalsIgnoreCase("xml")) {
            XPathException e = new XPathException("Processing instructions cannot be named 'xml' in any combination of upper/lower case");
            e.setXPathContext(context);
            e.setErrorCode((isXSLT() ? "XTDE0890" : "XQDY0064"));
            throw dynamicError(this, e, context);
        }
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("processingInstruction");
        out.startSubsidiaryElement("name");
        name.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("select");
        getContentExpression().explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

}

