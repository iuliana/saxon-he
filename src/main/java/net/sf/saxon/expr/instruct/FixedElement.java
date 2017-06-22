////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import org.xml.sax.Locator;

import java.util.Iterator;


/**
* An instruction that creates an element node whose name is known statically.
 * Used for literal results elements in XSLT, for direct element constructors
 * in XQuery, and for xsl:element in cases where the name and namespace are
 * known statically.
*/

public class FixedElement extends ElementCreator {

    private NodeName elementName;
    /*@NotNull*/ protected NamespaceBinding[] namespaceBindings;
    private ItemType itemType;

    /**
     * Create an instruction that creates a new element node
     * @param elementName Represents the name of the element node
     * @param namespaceBindings List of namespaces to be added to the element node.
     *                       Supply an empty array if none are required.
     * @param inheritNamespaces true if the children of this element are to inherit its namespaces
     * @param inheritFromParent true if this element is to inherit namespaces from its parent
     * @param schemaType Type annotation for the new element node
     * @param validation Validation mode to be applied, for example STRICT, LAX, SKIP
     */
    public FixedElement(NodeName elementName,
                        NamespaceBinding[] namespaceBindings,
                        boolean inheritNamespaces,
                        boolean inheritFromParent,
                        SchemaType schemaType,
                        int validation) {
        this.elementName = elementName;
        this.namespaceBindings = namespaceBindings;
        this.inheritNamespaces = inheritNamespaces;
        this.inheritFromParent = inheritFromParent;
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
    }

    /**
     * Simplify an expression. This performs any context-independent rewriting
     * @param visitor the expression visitor
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        final Configuration config = visitor.getConfiguration();
        setLazyConstruction(config.getBooleanProperty(FeatureKeys.LAZY_CONSTRUCTION_MODE));
        preservingTypes |= !visitor.getExecutable().isSchemaAware();
        return super.simplify(visitor);
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     * @param env the static context
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    protected void checkContentSequence(StaticContext env) throws XPathException {
        super.checkContentSequence(env);
        itemType = computeFixedElementItemType(this, env,
                getValidationAction(), getSchemaType(), elementName, content);
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        // Remove any unnecessary creation of namespace nodes by child literal result elements.
        // Specifically, if this instruction creates a namespace node, then a child literal result element
        // doesn't need to create the same namespace if all the following conditions are true:
        // (a) the child element is in the same namespace as its parent, and
        // (b) this element doesn't specify xsl:inherit-namespaces="no"
        // (c) the child element is incapable of creating attributes in a non-null namespace

        if (!inheritNamespaces) {
            return this;
        }
        if (namespaceBindings.length == 0) {
            return this;
        }
        if (content instanceof FixedElement) {
            FixedElement fixedContent = ((FixedElement)content);
            if (elementName.isInSameNamespace(fixedContent.getElementName())) {
                fixedContent.removeRedundantNamespaces(visitor, namespaceBindings);
            }
            return this;
        }
        if (content instanceof Block) {
            Iterator iter = content.iterateSubExpressions();
            while (iter.hasNext()) {
                Expression exp = (Expression)iter.next();
                if (exp instanceof FixedElement && elementName.isInSameNamespace(((FixedElement)exp).getElementName())) {
                    ((FixedElement)exp).removeRedundantNamespaces(visitor, namespaceBindings);
                }
            }
        }
        return this;
    }

    /**
     * Remove namespaces that are not required for this element because they are output on
     * the parent element
     * @param visitor the expression visitor
     * @param parentNamespaces the namespaces that are output by the parent element
     */

    private void removeRedundantNamespaces(ExpressionVisitor visitor, NamespaceBinding[] parentNamespaces) {
        // It's only safe to remove any namespaces if the element is incapable of creating any attribute nodes
        // in a non-null namespace
        // This is because namespaces created on this element take precedence over namespaces created by namespace
        // fixup based on the prefix used in the attribute name (see atrs24)
        if (namespaceBindings.length == 0) {
            return;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType contentType = content.getItemType(th);
        boolean ok = th.relationship(contentType, NodeKindTest.ATTRIBUTE) == TypeHierarchy.DISJOINT;
        if (!ok) {
            // if the content might include attributes, discount any that are known to be in the null namespace
            if (content instanceof Block) {
                ok = true;
                Iterator iter = content.iterateSubExpressions();
                while (iter.hasNext()) {
                    Expression exp = (Expression)iter.next();
                    if (exp instanceof FixedAttribute) {
                        int attNameCode = ((FixedAttribute)exp).getAttributeNameCode();
                        if (NamePool.isPrefixed(attNameCode)) {
                            ok = false;
                            break;
                        }
                    } else {
                        ItemType childType = exp.getItemType(th);
                        if (th.relationship(childType, NodeKindTest.ATTRIBUTE) != TypeHierarchy.DISJOINT) {
                            ok = false;
                            break;
                        }
                    }
                }
            }
        }
        if (ok) {
            int removed = 0;
            for (int i=0; i< namespaceBindings.length; i++) {
                for (NamespaceBinding parentNamespace : parentNamespaces) {
                    if (namespaceBindings[i] == parentNamespace) {
                        namespaceBindings[i] = null;
                        removed++;
                        break;
                    }
                }
            }
            if (removed > 0) {
                if (removed == namespaceBindings.length) {
                    namespaceBindings = NamespaceBinding.EMPTY_ARRAY;
                } else {
                    NamespaceBinding[] ns2 = new NamespaceBinding[namespaceBindings.length - removed];
                    int j=0;
                    for (NamespaceBinding namespaceBinding : namespaceBindings) {
                        if (namespaceBinding != null) {
                            ns2[j++] = namespaceBinding;
                        }
                    }
                    namespaceBindings = ns2;
                }
            }
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        NamespaceBinding[] ns2 = namespaceBindings;
        if (namespaceBindings.length != 0) {
            ns2 = new NamespaceBinding[namespaceBindings.length];
            System.arraycopy(namespaceBindings, 0, ns2, 0, ns2.length);
        }
        FixedElement fe = new FixedElement(elementName, ns2, inheritNamespaces, inheritFromParent, getSchemaType(), getValidationAction());
        fe.setContentExpression(content.copy());
        fe.setBaseURI(getBaseURI());
        if (getOnEmpty() != null) {
            fe.setOnEmpty(getOnEmpty().copy());
        }
        return fe;
    }

    /**
     * Determine the item type of an element being constructed
     * @param instr the FixedElement instruction
     * @param env the static context
     * @param validation the schema validation mode
     * @param schemaType the schema type for validation
     * @param elementName the name of the element
     * @param content the expression that computes the content of the element
     * @return the item type
     * @throws XPathException if a static error is detected
     */

    private ItemType computeFixedElementItemType(FixedElement instr, StaticContext env,
                                            int validation, SchemaType schemaType,
                                            NodeName elementName, Expression content) throws XPathException {
        final Configuration config = env.getConfiguration();
        ItemType itemType;
        int fp = elementName.getFingerprint();
        if (schemaType == null) {
            if (validation == Validation.STRICT) {
                SchemaDeclaration decl = config.getElementDeclaration(fp);
                if (decl == null) {
                    XPathException err = new XPathException("There is no global element declaration for " +
                            elementName.getStructuredQName().getClarkName() +
                            ", so strict validation will fail");
                    err.setErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0027");
                    err.setIsTypeError(true);
                    err.setLocator(instr);
                    throw err;
                }
                if (decl.isAbstract()) {
                    XPathException err = new XPathException("The element declaration for " +
                            elementName.getStructuredQName().getClarkName() +
                            " is abstract, so strict validation will fail");
                    err.setErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0027");
                    err.setIsTypeError(true);
                    err.setLocator(instr);
                    throw err;
                }
                schemaType = decl.getType();
                instr.getValidationOptions().setTopLevelType(schemaType);
                itemType = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, fp, env.getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, schemaType, config, false));
                try {
                    schemaType.analyzeContentExpression(content, Type.ELEMENT, env);
                } catch (XPathException e) {
                    e.setErrorCode(instr.isXSLT() ? "XTTE1510" : "XQDY0027");
                    e.setLocator(instr);
                    throw e;
                }
                SchemaType xsiType = instr.getXSIType(env);
                if (xsiType != null) {
                    xsiType.analyzeContentExpression(content, Type.ELEMENT, env);
                    try {
                        config.checkTypeDerivationIsOK(xsiType, schemaType, 0);
                    } catch (SchemaException e) {
                        ValidationException ve = new ValidationException("The specified xsi:type " + xsiType.getDescription() +
                                " is not validly derived from the required type " + schemaType.getDescription());
                        ve.setConstraintReference(1, "cvc-elt", "4.3");
                        ve.setErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027");
                        ve.setLocator((Locator)instr);
                        throw ve;
                    }
                }
            } else if (validation == Validation.LAX) {
                SchemaDeclaration decl = config.getElementDeclaration(fp);
                if (decl == null) {
                    env.issueWarning("There is no global element declaration for " +
                            elementName.getDisplayName(), instr);
                    itemType = new NameTest(Type.ELEMENT, fp, env.getNamePool());
                } else {
                    schemaType = decl.getType();
                    instr.getValidationOptions().setTopLevelType(schemaType);
                    itemType = new CombinedNodeTest(
                            new NameTest(Type.ELEMENT, fp, env.getNamePool()),
                            Token.INTERSECT,
                            new ContentTypeTest(Type.ELEMENT, instr.getSchemaType(), config, false));
                    try {
                        schemaType.analyzeContentExpression(content, Type.ELEMENT, env);
                    } catch (XPathException e) {
                        e.setErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027");
                        e.setLocator(instr);
                        throw e;
                    }
                }
            } else if (validation == Validation.PRESERVE) {
                // we know the result will be an element of type xs:anyType
                itemType = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, fp, env.getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false));
            } else {
                // we know the result will be an untyped element
                itemType = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, fp, env.getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false));
            }
        } else {
            itemType = new CombinedNodeTest(
                    new NameTest(Type.ELEMENT, fp, env.getNamePool()),
                    Token.INTERSECT,
                    new ContentTypeTest(Type.ELEMENT, schemaType, config, false)
            );
            try {
                schemaType.analyzeContentExpression(content, Type.ELEMENT, env);
            } catch (XPathException e) {
                e.setErrorCode(instr.isXSLT() ? "XTTE1540" : "XQDY0027");
                e.setLocator(instr);
                throw e;
            }
        }
        return itemType;
    }

    /**
     * Get the type of the item returned by this instruction
     * @return the item type
     * @param th The type hierarchy cache
     */
    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (itemType == null) {
            return super.getItemType(th);
        }
        return itemType;
    }

    /**
     * Callback from the superclass ElementCreator to get the nameCode
     * for the element name
     *
     *
     * @param context The evaluation context (not used)
     * @param copiedNode For the benefit of the xsl:copy instruction, the node to be copied
     * @return the name code for the element name
     */

    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) {
        return elementName;
    }

    public NodeName getElementName() {
        return elementName;
    }

    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return getBaseURI();
    }

    /**
     * Determine whether the element constructor creates a fixed xsi:type attribute, and if so, return the
     * relevant type.
     * @param env the static context
     * @return the type denoted by the constructor's xsi:type attribute if there is one.
     * Return null if there is no xsi:type attribute, or if the value of the xsi:type
     * attribute is a type that is not statically known (this is allowed)
     * @throws XPathException if there is an xsi:type attribute and its value is not a QName.
     */

    private SchemaType getXSIType(StaticContext env) throws XPathException {
        if (content instanceof FixedAttribute) {
            return testForXSIType((FixedAttribute)content, env);
        } else if (content instanceof Block) {
            Iterator iter = content.iterateSubExpressions();
            while (iter.hasNext()) {
                Expression exp = (Expression)iter.next();
                if (exp instanceof FixedAttribute) {
                    SchemaType type = testForXSIType((FixedAttribute)exp, env);
                    if (type != null) {
                        return type;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * Test whether a FixedAttribute child instruction of this FixedElement is creating an xsi:type
     * attribute whose value is known statically; if this is the case, then return the schema type
     * named in this attribute
     * @param fat The FixedAttribute instruction
     * @param env The XPath static context
     * @return the schema type if this is an xsi:type attribute instruction whose value is known at compile time;
     * otherwise null
     * @throws XPathException if an error occurs
     */

    private SchemaType testForXSIType(FixedAttribute fat, StaticContext env) throws XPathException {
        int att = fat.getAttributeNameCode() & NamePool.FP_MASK;
        if (att == StandardNames.XSI_TYPE) {
            Expression attValue = fat.getContentExpression();
            if (attValue instanceof StringLiteral) {
                try {
                    NamePool pool = env.getNamePool();
                    String[] parts = env.getConfiguration().getNameChecker().getQNameParts(
                            ((StringLiteral)attValue).getStringValue());
                    // The only namespace bindings we can trust are those declared on this element
                    // We could also trust those on enclosing LREs in the same function/template,
                    // but it's not a big win to go looking for them.
                    String uri = null;
                    for (NamespaceBinding namespaceBinding : namespaceBindings) {
                        String prefix = namespaceBinding.getPrefix();
                        if (prefix.equals(parts[0])) {
                            uri = namespaceBinding.getURI();
                            break;
                        }
                    }
                    if (uri == null) {
                        return null;
                    }
                    int typefp = pool.allocate(parts[0], uri, parts[1]) & NamePool.FP_MASK;
                    return env.getConfiguration().getSchemaType(typefp);
                } catch (QNameException e) {
                    throw new XPathException(e.getMessage());
                }
            }
        }
        return null;
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
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element is of simple type " + parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocator(this);
            throw err;
        } else if (((ComplexType)parentType).isSimpleContent()) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element has a complex type with simple content");
            err.setIsTypeError(true);
            err.setLocator(this);
            throw err;
        }

        // Check that a sequence consisting of this element alone is valid against the content model
        if (whole) {
            Block block = new Block();
            block.setChildren(new Expression[]{this});
            parentType.analyzeContentExpression(block, Type.ELEMENT, env);
        }
               
        SchemaType type;
        try {
            type = ((ComplexType)parentType).getElementParticleType(elementName.getFingerprint(), true);
        } catch (SchemaException e) {
            throw new XPathException(e);
        }
        if (type == null) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted in the content model of the complex type " +
                    parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocator(this);
            err.setErrorCode(isXSLT() ? "XTTE1510" : "XQDY0027");
            throw err;
        }
        if (type instanceof AnyType) {
            return;
        }

        try {
            content.checkPermittedContents(type, env, true);
        } catch (XPathException e) {
            if (e.getLocator() == null || e.getLocator() == e) {
                e.setLocator(this);
            }
            throw e;
        }
    }

    /**
     * Callback from the superclass ElementCreator to output the namespace nodes
     * @param context The evaluation context (not used)
     * @param out The receiver to handle the output
     * @param nameCode the name of this element
     * @param copiedNode in the case of xsl:copy, the node being copied
     */

    public void outputNamespaceNodes(XPathContext context, Receiver out, NodeName nameCode, NodeInfo copiedNode)
    throws XPathException {
        for (NamespaceBinding namespaceBinding : namespaceBindings) {
            out.namespace(namespaceBinding, 0);
        }
    }

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     * The result is an array of namespace codes, the codes either occupy the whole array or are
     * terminated by a -1 entry. A result of null is equivalent to a zero-length array.
     */

    public NamespaceBinding[] getActiveNamespaces() {
        return namespaceBindings;
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("directElement");
        out.emitAttribute("name", elementName.getStructuredQName().getClarkName());
        out.emitAttribute("validation", Validation.toString(getValidationAction()));
        SchemaType type = getSchemaType();
        if (type != null) {
            out.emitAttribute("type", type.getDescription());
        }
        content.explain(out);
        out.endElement();
    }
}

