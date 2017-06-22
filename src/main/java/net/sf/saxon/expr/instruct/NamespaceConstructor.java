////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Whitespace;

import java.util.Iterator;

/**
* A namespace constructor instruction. (xsl:namespace in XSLT 2.0, or namespace{}{} in XQuery 1.1)
*/

public class NamespaceConstructor extends SimpleNodeConstructor {

    private Expression name;

    /**
     * Create an xsl:namespace instruction for dynamic construction of namespace nodes
     * @param name the expression to evaluate the name of the node (that is, the prefix)
     */

    public NamespaceConstructor(Expression name) {
        this.name = name;
        adoptChildExpression(name);
    }

    /**
     * Get the expression that defines the namespace node's name
     * @return the expression that defines the namespace node's name (that is, the namespace prefix)
     */

    public Expression getNameExpression() {
        return name;
    }

    /**
    * Set the name of this instruction for diagnostic and tracing purposes
    */

    public int getInstructionNameCode() {
        return StandardNames.XSL_NAMESPACE;
    }

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        name = visitor.simplify(name);
        return super.simplify(visitor);
    }

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return NodeKindTest.NAMESPACE;
    }

    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        name = doPromotion(name, offer);
        super.promoteInst(offer);
    }

    public void localTypeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        name = visitor.typeCheck(name, contextItemType);
        adoptChildExpression(name);

        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (!th.isSubType(name.getItemType(th), BuiltInAtomicType.STRING)) {
            name = SystemFunctionCall.makeSystemFunction("string", new Expression[]{name});
        }
    }

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(select, name);
    }

    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        return new PairIterator<SubExpressionInfo>(
            new SubExpressionInfo(name, true, false, NODE_VALUE_CONTEXT),
            new SubExpressionInfo(select, true, false, NODE_VALUE_CONTEXT)
        );
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        NamespaceConstructor exp = new NamespaceConstructor(name.copy());
        exp.setSelect(select.copy(), getExecutable().getConfiguration());
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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


    private String evaluatePrefix(XPathContext context) throws XPathException {
        String prefix = Whitespace.trim(name.evaluateAsString(context));
        assert prefix != null;
        if (!(prefix.length() == 0 || context.getConfiguration().getNameChecker().isValidNCName(prefix))) {
            XPathException err = new XPathException("Namespace prefix is invalid: " + prefix, this);
            err.setErrorCode(isXSLT() ? "XTDE0920" : "FORG0001");
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        if (prefix.equals("xmlns")) {
            XPathException err = new XPathException("Namespace prefix 'xmlns' is not allowed", this);
            err.setErrorCode(isXSLT() ? "XTDE0920" : "XQDY0101");
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }
        return prefix;
    }

    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        String prefix = evaluatePrefix(context);
        return new NoNamespaceName(prefix);
    }

    public void processValue(CharSequence value, XPathContext context) throws XPathException {
        String prefix = evaluatePrefix(context);
        String uri = value.toString();
        checkPrefixAndUri(prefix, uri, context);

        NamespaceBinding nscode = new NamespaceBinding(prefix, uri);
        SequenceReceiver out = context.getReceiver();
        out.namespace(nscode, ReceiverOptions.REJECT_DUPLICATES);
    }


    /**
     * Evaluate as an expression. We rely on the fact that when these instructions
     * are generated by XQuery, there will always be a valueExpression to evaluate
     * the content
     */

    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        NodeInfo node = (NodeInfo)super.evaluateItem(context);
        assert node != null;
        String prefix = node.getLocalPart();
        String uri = node.getStringValue();
        checkPrefixAndUri(prefix, uri, context);
        return node;
    }

    private void checkPrefixAndUri(String prefix, String uri, XPathContext context) throws XPathException {
        if (prefix.equals("xml") != uri.equals(NamespaceConstant.XML)) {
            XPathException err = new XPathException("Namespace prefix 'xml' and namespace uri " + NamespaceConstant.XML +
                    " must only be used together", this);
            err.setErrorCode(isXSLT() ? "XTDE0925" : "XQDY0101");
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        if (uri.length() == 0) {
            XPathException err = new XPathException("Namespace URI is an empty string", this);
            err.setErrorCode(isXSLT() ? "XTDE0930" : "XQDY0101");
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        if (uri.equals(NamespaceConstant.XMLNS)) {
            XPathException err = new XPathException("A namespace node cannot have the reserved namespace " +
                    NamespaceConstant.XMLNS, this);
            err.setErrorCode("XTDE0935");
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        if (!StandardURIChecker.getInstance().isValidURI(uri)) {
            XPathException de = new XPathException("The string value of the constructed namespace node must be a valid URI");
            de.setErrorCode("XTDE0905");
            de.setXPathContext(context);
            de.setLocator(this);
            throw de;
        }
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("namespace");
        out.startSubsidiaryElement("name");
        name.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("select");
        getContentExpression().explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

}