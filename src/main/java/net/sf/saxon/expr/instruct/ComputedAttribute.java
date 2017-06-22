////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * An instruction derived from an xsl:attribute element in stylesheet, or from
 * an attribute constructor in XQuery, in cases where the attribute name is not known
 * statically
 */

public final class ComputedAttribute extends AttributeCreator {

    private Expression attributeName;
    private Expression namespace = null;
    private Expression onEmpty = null;
    private NamespaceResolver nsContext;
    private boolean allowNameAsQName;

    /**
     * Construct an Attribute instruction
     *
     * @param attributeName    An expression to calculate the attribute name
     * @param namespace        An expression to calculate the attribute namespace
     * @param nsContext        a NamespaceContext object containing the static namespace context of the
     *                         stylesheet instruction
     * @param validationAction e.g. validation=strict, lax, strip, preserve
     * @param schemaType       Type against which the attribute must be validated. This must not be a namespace-sensitive
     *                         type; it is the caller's responsibility to check this.
     * @param allowNameAsQName true if the attributeName expression is allowed to evaluate to a value
     *                         of type xs:QName (true in XQuery, false in XSLT)
     */

    public ComputedAttribute(Expression attributeName,
                             Expression namespace,
                             NamespaceResolver nsContext,
                             int validationAction,
                             SimpleType schemaType,
                             boolean allowNameAsQName) {
        this.attributeName = attributeName;
        this.namespace = namespace;
        this.nsContext = nsContext;
        setSchemaType(schemaType);
        setValidationAction(validationAction);
        setOptions(0);
        this.allowNameAsQName = allowNameAsQName;
        adoptChildExpression(attributeName);
        adoptChildExpression(namespace);
    }

    /**
     * Set the on-empty expression, which defines the value to be returned if the attribute would otherwise
     * be empty
     *
     * @param onEmpty the expression to be evaluated if the attribute would otherwise be empty
     */

    public void setOnEmpty(Expression onEmpty) {
        this.onEmpty = onEmpty;
    }

    /**
     * Get the on-empty expression, which defines the value to be returned if the attribute would otherwise
     * be empty
     * @return the on-empty expression if there is one, or null otherwise
     */

    public Expression getOnEmpty() {
        return onEmpty;
    }

    /**
     * Indicate that two attributes with the same name are not acceptable.
     * (This option is set in XQuery, but not in XSLT)
     */

    public void setRejectDuplicates() {
        setOptions(getOptions() | ReceiverOptions.REJECT_DUPLICATES);
    }

    /**
     * Get the name of this instruction
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_ATTRIBUTE;
    }

    /**
     * Get the expression used to compute the name of the attribute
     *
     * @return the expression used to compute the name of the attribute
     */

    public Expression getNameExpression() {
        return attributeName;
    }

    /**
     * Get the expression used to compute the namespace part of the name of the attribute
     *
     * @return the expression used to compute the namespace part of the name of the attribute
     */

    public Expression getNamespaceExpression() {
        return namespace;
    }

    /**
     * Get the namespace resolver used to resolve any prefix in the name of the attribute
     *
     * @return the namespace resolver if one has been saved; or null otherwise
     */

    public NamespaceResolver getNamespaceResolver() {
        return nsContext;
    }

    /**
     * Get the static type of this expression
     *
     * @param th the type hierarchy cache
     * @return the static type of the item returned by this expression
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (onEmpty == null || Literal.isEmptySequence(onEmpty)) {
            return NodeKindTest.ATTRIBUTE;
        } else {
            return Type.getCommonSuperType(NodeKindTest.ATTRIBUTE, onEmpty.getItemType(th), th);
        }
    }

    /**
     * Get the static cardinality of this expression
     *
     * @return the static cardinality (exactly one)
     */

    public int getCardinality() {
        if (onEmpty == null) {
            return StaticProperty.EXACTLY_ONE;
        } else if (Literal.isEmptySequence(onEmpty)) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return Cardinality.union(StaticProperty.EXACTLY_ONE, onEmpty.getCardinality());
        }
    }

    /**
     * Ask whether it is allowed for the name to be evaluted as an xs:QName instance (true in XQuery)
     *
     * @return the boolean if name is allowed as a QName
     */
    public boolean isAllowNameAsQName() {
        return allowNameAsQName;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    public int computeSpecialProperties() {
        return super.computeSpecialProperties() |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
    }


    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        attributeName = visitor.simplify(attributeName);
        namespace = visitor.simplify(namespace);
        onEmpty = visitor.simplify(onEmpty);
        return super.simplify(visitor);
    }

    public void localTypeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        attributeName = visitor.typeCheck(attributeName, contextItemType);
        onEmpty = visitor.typeCheck(onEmpty, contextItemType);
        adoptChildExpression(attributeName);

        RoleLocator role = new RoleLocator(RoleLocator.INSTRUCTION, "attribute/name", 0);
        //role.setSourceLocator(this);

        if (allowNameAsQName) {
            // Can only happen in XQuery
            attributeName = TypeChecker.staticTypeCheck(attributeName,
                    SequenceType.SINGLE_ATOMIC, false, role, visitor);
            TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            ItemType nameItemType = attributeName.getItemType(th);
            boolean maybeString = th.relationship(nameItemType, BuiltInAtomicType.STRING) != TypeHierarchy.DISJOINT ||
                    th.relationship(nameItemType, BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT;
            boolean maybeQName = th.relationship(nameItemType, BuiltInAtomicType.QNAME) != TypeHierarchy.DISJOINT;
            if (!(maybeString || maybeQName)) {
                XPathException err = new XPathException(
                        "The attribute name must be either an xs:string, an xs:QName, or untyped atomic");
                err.setErrorCode("XPTY0004");
                err.setIsTypeError(true);
                err.setLocator(this);
                throw err;
            }
        } else {
            TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            if (!th.isSubType(attributeName.getItemType(th), BuiltInAtomicType.STRING)) {
                attributeName = SystemFunctionCall.makeSystemFunction("string", new Expression[]{attributeName});
            }
        }

        if (namespace != null) {
            visitor.typeCheck(namespace, contextItemType);
            adoptChildExpression(namespace);

            role = new RoleLocator(RoleLocator.INSTRUCTION, "attribute/namespace", 0);
            //role.setSourceLocator(this);
            namespace = TypeChecker.staticTypeCheck(
                    namespace, SequenceType.SINGLE_STRING, false, role, visitor);
        }

        if (Literal.isAtomic(attributeName)) {
            // Check we have a valid lexical QName, whose prefix is in scope where necessary
            try {
                AtomicValue val = (AtomicValue) ((Literal) attributeName).getValue();
                if (val instanceof StringValue) {
                    String[] parts = env.getConfiguration().getNameChecker().checkQNameParts(val.getStringValueCS());
                    if (namespace == null) {
                        String uri = getNamespaceResolver().getURIForPrefix(parts[0], true);
                        if (uri == null) {
                            XPathException se = new XPathException("Prefix " + parts[0] + " has not been declared");
                            if (isXSLT()) {
                                se.setErrorCode("XTDE0860");
                                se.setIsStaticError(true);
                                throw se;
                            } else {
                                se.setErrorCode("XQDY0074");
                                se.setIsStaticError(false);
                                throw se;
                            }
                        }
                        namespace = new StringLiteral(uri);
                    }
                }
            } catch (XPathException e) {
                if (e.getErrorCodeQName() == null || e.getErrorCodeLocalPart().equals("FORG0001")) {
                    e.setErrorCode(isXSLT() ? "XTDE0850" : "XQDY0074");
                }
                e.maybeSetLocation(this);
                e.setIsStaticError(true);
                throw e;
            }
        }
    }


    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        attributeName = visitor.optimize(attributeName, contextItemType);
        namespace = visitor.optimize(namespace, contextItemType);
        onEmpty = visitor.optimize(onEmpty, contextItemType);
        Expression exp = super.optimize(visitor, contextItemType);
        if (exp != this) {
            return exp;
        }
        // If the name is known statically, use a FixedAttribute instead
        if (attributeName instanceof Literal && (namespace == null || namespace instanceof Literal) && onEmpty == null) {
            XPathContext context = visitor.getStaticContext().makeEarlyEvaluationContext();
            NodeName nc = evaluateNodeName(context);
            FixedAttribute fa = new FixedAttribute(nc, getValidationAction(), getSchemaType());
            fa.setSelect(getContentExpression(), visitor.getConfiguration());
            return fa;
        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ComputedAttribute exp = new ComputedAttribute(
                attributeName == null ? null : attributeName.copy(),
                namespace == null ? null : namespace.copy(),
                nsContext, getValidationAction(), getSchemaType(), allowNameAsQName);
        exp.setOnEmpty(onEmpty);
        exp.setSelect(select.copy(), getExecutable().getConfiguration());
        return exp;
    }

    /**
     * Get the subexpressions of this expression
     *
     * @return an iterator over the subexpressions
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(4);
        list.add(select);
        list.add(attributeName);
        if (namespace != null) {
            list.add(namespace);
        }
        if (onEmpty != null) {
            list.add(onEmpty);
        }
        return list.iterator();
    }

    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        ArrayList<SubExpressionInfo> list = new ArrayList<SubExpressionInfo>(4);
        list.add(new SubExpressionInfo(select, true, false, NODE_VALUE_CONTEXT));
        list.add(new SubExpressionInfo(attributeName, true, false, NODE_VALUE_CONTEXT));
        if (namespace != null) {
            list.add(new SubExpressionInfo(namespace, true, false, NODE_VALUE_CONTEXT));
        }
        if (onEmpty != null) {
            list.add(new SubExpressionInfo(onEmpty, true, false, NODE_VALUE_CONTEXT));
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
        if (attributeName == original) {
            attributeName = replacement;
            found = true;
        }
        if (namespace == original) {
            namespace = replacement;
            found = true;
        }
        if (onEmpty == original) {
            onEmpty = replacement;
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
     *              expressions that don't depend on the context to an outer level in
     *              the containing expression
     * @throws XPathException if any error is detected
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        attributeName = doPromotion(attributeName, offer);
        if (namespace != null) {
            namespace = doPromotion(namespace, offer);
        }
        if (onEmpty != null) {
            onEmpty = doPromotion(onEmpty, offer);
        }
        super.promoteInst(offer);
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        if (parentType instanceof SimpleType) {
            String msg = "Attributes are not permitted here: ";
            if (parentType.isAnonymousType()) {
                msg += "the containing element is defined to have a simple type";
            } else {
                msg += "the containing element is of simple type " + parentType.getDescription();
            }
            XPathException err = new XPathException(msg);
            err.setIsTypeError(true);
            err.setLocator(this);
            throw err;
        }
    }


    /**
     * Determine the name to be used for the attribute, as an integer name code
     *
     * @param context Dynamic evaluation context
     * @return the integer name code for the attribute name
     * @throws XPathException
     */

    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        NamePool pool = context.getNamePool();

        Item nameValue = attributeName.evaluateItem(context);

        String prefix;
        String localName;
        String uri = null;

        if (nameValue instanceof StringValue) {
            // this will always be the case in XSLT
            CharSequence rawName = nameValue.getStringValueCS();
            rawName = Whitespace.trimWhitespace(rawName); // required in XSLT; possibly wrong in XQuery
            try {
                String[] parts = context.getConfiguration().getNameChecker().getQNameParts(rawName);
                prefix = parts[0];
                localName = parts[1];
            } catch (QNameException err) {
                XPathException err1 = new XPathException("Invalid attribute name: " + rawName, this);
                err1.setErrorCode((isXSLT() ? "XTDE0850" : "XQDY0074"));
                err1.setXPathContext(context);
                throw dynamicError(this, err1, context);
            }
            if (rawName.toString().equals("xmlns")) {
                if (namespace == null) {
                    XPathException err = new XPathException("Invalid attribute name: " + rawName, this);
                    err.setErrorCode((isXSLT() ? "XTDE0855" : "XQDY0044"));
                    err.setXPathContext(context);
                    throw dynamicError(this, err, context);
                }
            }
            if (prefix.equals("xmlns")) {
                if (namespace == null) {
                    XPathException err = new XPathException("Invalid attribute name: " + rawName, this);
                    err.setErrorCode((isXSLT() ? "XTDE0860" : "XQDY0044"));
                    err.setXPathContext(context);
                    throw dynamicError(this, err, context);
                } else {
                    // ignore the prefix "xmlns"
                    prefix = "";
                }
            }

        } else if (nameValue instanceof QNameValue && allowNameAsQName) {
            // this is allowed in XQuery
            localName = ((QNameValue) nameValue).getLocalName();
            uri = ((QNameValue) nameValue).getNamespaceURI();
            if (localName.equals("xmlns") && uri.length() == 0) {
                XPathException err = new XPathException("Invalid attribute name: xmlns", this);
                err.setErrorCode("XQDY0044");
                err.setXPathContext(context);
                throw dynamicError(this, err, context);
            }

            if (uri.length() == 0) {
                prefix = "";
            } else {
                prefix = ((QNameValue) nameValue).getPrefix();
                if (prefix.length() == 0) {
                    prefix = pool.suggestPrefixForURI(uri);
                    if (prefix == null) {
                        prefix = "ns0";
                        // If the prefix is a duplicate, a different one will be substituted
                    }
                }
                if (uri.equals(NamespaceConstant.XML) != "xml".equals(prefix)) {
                    String message;
                    if ("xml".equals(prefix)) {
                        message = "When the prefix is 'xml', the namespace URI must be " + NamespaceConstant.XML;
                    } else {
                        message = "When the namespace URI is " + NamespaceConstant.XML + ", the prefix must be 'xml'";
                    }
                    XPathException err = new XPathException(message, this);
                    err.setErrorCode((isXSLT() ? "XTDE0835" : "XQDY0044"));
                    err.setXPathContext(context);
                    throw dynamicError(this, err, context);
                }
            }

            if ("xmlns".equals(prefix)) {
                XPathException err = new XPathException("Invalid attribute namespace: http://www.w3.org/2000/xmlns/", this);
                err.setErrorCode("XQDY0044");
                err.setXPathContext(context);
                throw dynamicError(this, err, context);
            }

        } else {
            XPathException err = new XPathException("Attribute name must be either a string or a QName", this);
            err.setErrorCode("XPTY0004");
            err.setIsTypeError(true);
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        if (namespace == null && uri == null) {
            if (prefix.length() == 0) {
                uri = "";
            } else {
                uri = nsContext.getURIForPrefix(prefix, false);
                if (uri == null) {
                    XPathException err = new XPathException("Undeclared prefix in attribute name: " + prefix, this);
                    err.setErrorCode((isXSLT() ? "XTDE0860" : "XQDY0074"));
                    err.setXPathContext(context);
                    throw dynamicError(this, err, context);
                }
            }

        } else {
            if (uri == null) {
                // generate a name using the supplied namespace URI
                if (namespace instanceof StringLiteral) {
                    uri = ((StringLiteral) namespace).getStringValue();
                } else {
                    uri = namespace.evaluateAsString(context).toString();
                    if (!StandardURIChecker.getInstance().isValidURI(uri)) {
                        XPathException de = new XPathException("The value of the namespace attribute must be a valid URI");
                        de.setErrorCode("XTDE0865");
                        de.setXPathContext(context);
                        de.setLocator(this);
                        throw de;
                    }
                }
            }
            if (uri.length() == 0) {
                // there is a special rule for this case in the XSLT specification;
                // we force the attribute to go in the null namespace
                prefix = "";

            } else {
                // if a suggested prefix is given, use it; otherwise try to find a prefix
                // associated with this URI; if all else fails, invent one.
                if (prefix.length() == 0) {
                    prefix = pool.suggestPrefixForURI(uri);
                    if (prefix == null) {
                        prefix = "ns0";
                        // this will be replaced later if it is already in use
                    }
                }
            }
        }

        if (uri.equals(NamespaceConstant.XMLNS)) {
            XPathException err = new XPathException("Cannot create attribute in namespace " + uri, this);
            err.setErrorCode((isXSLT() ? "XTDE0835" : "XQDY0044"));
            err.setXPathContext(context);
            throw dynamicError(this, err, context);
        }

        return new FingerprintedQName(prefix, uri, localName);
    }

    /**
     * Process the value of the node, to create the new node.
     *
     * @param value   the string value of the new node
     * @param context the dynamic evaluation context
     * @throws net.sf.saxon.trans.XPathException
     *
     */
    @Override
    public void processValue(CharSequence value, XPathContext context) throws XPathException {
        if (value.length() == 0 && onEmpty != null) {
            onEmpty.process(context);
        } else {
            super.processValue(value, context);
        }
    }

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Item node = super.evaluateItem(context);
        if (onEmpty != null && node.getStringValue().length() == 0) {
            return onEmpty.evaluateItem(context);
        } else {
            validateOrphanAttribute((Orphan)node, context);
            return node;
        }
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("computedAttribute");
        out.emitAttribute("validation", Validation.toString(getValidationAction()));
        SimpleType type = getSchemaType();
        if (type != null) {
            out.emitAttribute("type", type.getDescription());
        }
        out.startSubsidiaryElement("name");
        attributeName.explain(out);
        out.endSubsidiaryElement();
        if (namespace != null) {
            out.startSubsidiaryElement("namespace");
            namespace.explain(out);
            out.endSubsidiaryElement();
        }
        if (onEmpty != null) {
            out.startSubsidiaryElement("on-empty");
            onEmpty.explain(out);
            out.endSubsidiaryElement();
        }
        out.startSubsidiaryElement("select");
        getContentExpression().explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }
}

