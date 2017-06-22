////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Whitespace;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;



/**
* Handler for xsl:copy elements in stylesheet.
*/

public class Copy extends ElementCreator {

    private boolean copyNamespaces;
    private boolean selectSpecified;
    private ItemType resultItemType;
    /*@Nullable*/ private Expression select;

    /**
     * Create a shallow copy instruction
     * @param select selects the node (or other item) to be copied. Never null.
     * @param selectSpecified true if the select attribute of xsl:copy was specified explicitly (in which
     * case the context for evaluating the body will change)
     * @param copyNamespaces true if namespace nodes are to be copied when copying an element
     * @param inheritNamespaces true if child elements are to inherit the namespace nodes of their parent
     * @param schemaType the Schema type against which the content is to be validated
     * @param validation the schema validation mode
     */

    public Copy(Expression select,
                boolean selectSpecified,
                boolean copyNamespaces,
                boolean inheritNamespaces,
                SchemaType schemaType,
                int validation) {
        this.copyNamespaces = copyNamespaces;
        this.inheritNamespaces = inheritNamespaces;
        this.selectSpecified = selectSpecified;
        this.select = select;
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
        if (copyNamespaces) {
            setLazyConstruction(false);
            // can't do lazy construction at present in cases where namespaces need to be copied from the
            // source document.
        }
    }

    /**
     * Get the expression that selects the node or other item to be copied
     * @return the select expression. This will be a context item expression if no select attribute was supplied.
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Ask whether the select attribute was explicitly specified
     * @return true if it was
     */

    public boolean isSelectSpecified() {
        return selectSpecified;
    }

    /**
     * Ask whether namespace nodes are to be copied (in the case of an element)
     * @return true if all in-scope namespaces are to be copied
     */

    public boolean isCopyNamespaces() {
        return copyNamespaces;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        select = visitor.simplify(select);
        preservingTypes |= !visitor.getExecutable().isSchemaAware();
        return super.simplify(visitor);
    }


    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        try {
            select = visitor.typeCheck(select, contextItemType);
            adoptChildExpression(select);
        } catch (XPathException err) {
            if (err.getErrorCodeLocalPart().equals("XPDY0002")) {
                // See spec bug 7624, test case copy903err
                err.setErrorCode("XTTE0945");
                err.maybeSetLocation(this);
            }
            select = Literal.makeEmptySequence(); // to prevent duplicate error reporting
            throw err;
        }

        ItemType selectItemType = select.getItemType(visitor.getConfiguration().getTypeHierarchy());
        content = visitor.typeCheck(content, getInnerContextItemType(contextItemType, selectItemType));

        if (selectItemType instanceof NodeTest) {
            switch (selectItemType.getPrimitiveType()) {
                // For elements and attributes, assume the type annotation will change
                case Type.ELEMENT:
                    this.resultItemType = NodeKindTest.ELEMENT;
                    break;
                case Type.ATTRIBUTE:
                    this.resultItemType = NodeKindTest.ATTRIBUTE;
                    break;
                case Type.DOCUMENT:
                    this.resultItemType = NodeKindTest.DOCUMENT;
                    break;
                default:
                    this.resultItemType = selectItemType;
            }
        } else {
            this.resultItemType = selectItemType;
        }

        adoptChildExpression(content);
        verifyLazyConstruction();
        checkContentSequence(visitor.getStaticContext());
        return this;
    }

    /**
     * Get the context information for evaluating the content of the xsl;copy instruction, which
     * varies depending on whether a select attribute was specified or not.
     * @param contextItemType
     * @param selectItemType
     * @return
     */

    private ExpressionVisitor.ContextItemType getInnerContextItemType(ExpressionVisitor.ContextItemType contextItemType, ItemType selectItemType) {
        ExpressionVisitor.ContextItemType innerContext = contextItemType;
        if (selectSpecified) {
            innerContext = new ExpressionVisitor.ContextItemType(selectItemType, false);
        }
        return innerContext;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Copy copy = new Copy(select, selectSpecified,
                copyNamespaces, inheritNamespaces, getSchemaType(), getValidationAction());
        copy.setContentExpression(content.copy());
        copy.resultItemType = resultItemType;
        return copy;
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
        } else if (content == original) {
            content = replacement;
            found = true;
        }
        return found;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     * @return a set of bit-significant flags identifying the dependencies of
     *         the expression
     */

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
    * Get the name of this instruction for diagnostic and tracing purposes
    */

    public int getInstructionNameCode() {
        return StandardNames.XSL_COPY;
    }

    /**
      * Get the immediate sub-expressions of this expression.
      * @return an iterator containing the sub-expressions of this expression
      */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(select, content);
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
        return new PairIterator<SubExpressionInfo>(
                new SubExpressionInfo(select, true, false, INSPECTION_CONTEXT),
                new SubExpressionInfo(content, true, false, NODE_VALUE_CONTEXT));
    }


    /**
     * Get the item type of the result of this instruction.
     * @return The context item type.
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (resultItemType != null) {
            return resultItemType;
        } else {
            resultItemType = computeItemType(th);
            return resultItemType;
        }
    }

    private ItemType computeItemType(TypeHierarchy th) {
        ItemType selectItemType = select.getItemType(th);
        Executable exec = getExecutable();
        if (!exec.isSchemaAware()) {
            return selectItemType;
        }
        // The rest of the code handles the complications of schema-awareness
        Configuration config = exec.getConfiguration();
        if (getSchemaType() != null) {
            int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ELEMENT, getSchemaType(), config, false);
            }
            int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ATTRIBUTE, getSchemaType(), config, false);
            }
            return AnyNodeTest.getInstance();
        } else switch (getValidationAction()) {
            case Validation.PRESERVE:
                return selectItemType;
            case Validation.STRIP: {
                int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                    return new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false);
                }
                int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                    return new ContentTypeTest(Type.ATTRIBUTE, BuiltInAtomicType.UNTYPED_ATOMIC, config, false);
                }
                if (e != TypeHierarchy.DISJOINT || a != TypeHierarchy.DISJOINT) {
                    // it might be an element or attribute
                    return AnyNodeTest.getInstance();
                } else {
                    // it can't be an element or attribute, so stripping type annotations can't affect it
                    return selectItemType;
                }
            }
            case Validation.STRICT:
            case Validation.LAX:
                if (selectItemType instanceof NodeTest) {
                    int fp = ((NodeTest)selectItemType).getFingerprint();
                    if (fp != -1) {
                        int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                        if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                            SchemaDeclaration elem = config.getElementDeclaration(fp);
                            if (elem != null) {
                                return new ContentTypeTest(Type.ELEMENT, elem.getType(), config, false);
                            } else {
                                // No element declaration now, but there might be one at run-time
                                return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                            }
                        }
                        int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                        if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                            SchemaDeclaration attr = config.getElementDeclaration(fp);
                            if (attr != null) {
                                return new ContentTypeTest(Type.ATTRIBUTE, attr.getType(), config, false);
                            } else {
                                 // No attribute declaration now, but there might be one at run-time
                                return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                            }
                        }
                    } else {
                        int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                        if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                            return NodeKindTest.ELEMENT;
                        }
                        int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                        if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                            return NodeKindTest.ATTRIBUTE;
                        }
                    }
                    return AnyNodeTest.getInstance();
                } else if (selectItemType instanceof AtomicType) {
                    return selectItemType;
                } else {
                    return AnyItemType.getInstance();
                }
            default:
                throw new IllegalStateException();
        }
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        select = visitor.optimize(select, contextItemType);
        Expression exp = super.optimize(visitor,
                getInnerContextItemType(contextItemType, computeItemType(visitor.getConfiguration().getTypeHierarchy())));
        if (exp == this) {
            if (resultItemType == null) {
                resultItemType = computeItemType(visitor.getConfiguration().getTypeHierarchy());
            }
            if (resultItemType.isPlainType() && select != null) {
                return select;
            }
        }
        return exp;
    }

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        content = doPromotion(content, offer);
    }

    /**
     * Callback from ElementCreator when constructing an element
     *
     *
     * @param context XPath dynamic evaluation context
     * @param copiedNode the node being copied
     * @return the namecode of the element to be constructed
     * @throws XPathException
     */

    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) throws XPathException {
        return new NameOfNode(copiedNode);
    }

    /**
     * Get the base URI of a copied element node (the base URI is retained in the new copy)
     * @param context XPath dynamic evaluation context
     * @param copiedNode
     * @return the base URI
     */

    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return copiedNode.getBaseURI();
    }

    /**
     * Callback to output namespace nodes for the new element.
     *
     *
     * @param context The execution context
     * @param receiver the Receiver where the namespace nodes are to be written
     * @param nameCode
     * @param copiedNode
     * @throws XPathException
     */

    public void outputNamespaceNodes(XPathContext context, Receiver receiver, NodeName nameCode, NodeInfo copiedNode)
    throws XPathException {
        if (copyNamespaces) {
            NamespaceIterator.sendNamespaces(copiedNode, receiver);
        } else {
            // Always output the namespace of the element name itself
            receiver.namespace(nameCode.getNamespaceBinding(), 0);
        }
    }

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     * The result is an array of namespace codes, the codes either occupy the whole array or are
     * terminated by a -1 entry. A result of null is equivalent to a zero-length array.
     */

    public NamespaceBinding[] getActiveNamespaces() throws XPathException {
        if (copyNamespaces) {
            // we should have disabled lazy construction, so this shouldn't be called.
            throw new UnsupportedOperationException();
        } else {
            return null;
        }
    }


    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        SequenceReceiver out = context.getReceiver();
        XPathContext c2 = context;
        Item item;


        item = select.evaluateItem(context);
        if (item == null) {
            // See spec bug 7624, test case copy904err
            return null;
        }

        if (!(item instanceof NodeInfo)) {
            out.append(item, locationId, NodeInfo.ALL_NAMESPACES);
            return null;
        }
        NodeInfo source = (NodeInfo)item;
        //out.getPipelineConfiguration().setBaseURI(source.getBaseURI());

        // Processing depends on the node kind.

        switch(source.getNodeKind()) {

        case Type.ELEMENT:
            // use the generic code for creating new elements
            if (selectSpecified) {
                c2 = context.newMinorContext();
                SequenceIterator si = SingletonIterator.makeIterator(item);
                si.next();
                c2.setCurrentIterator(si);
            }
            return super.processLeavingTail(c2, (NodeInfo)item);

        case Type.ATTRIBUTE:
            if (getSchemaType() instanceof ComplexType) {
                dynamicError("Cannot copy an attribute when the type requested for validation is a complex type", "XTTE1535", context);
            }
            try {
                CopyOf.copyAttribute(source, (SimpleType)getSchemaType(), getValidationAction(), this, context, false);
            } catch (NoOpenStartTagException err) {
                err.setXPathContext(context);
                throw dynamicError(this, err, context);
            }
            break;

        case Type.TEXT:
            out.characters(source.getStringValueCS(), locationId, 0);
            break;

        case Type.PROCESSING_INSTRUCTION:
            out.processingInstruction(source.getDisplayName(), source.getStringValueCS(), locationId, 0);
            break;

        case Type.COMMENT:
            out.comment(source.getStringValueCS(), locationId, 0);
            break;

        case Type.NAMESPACE:
            try {
                source.copy(out, 0, locationId);
            } catch (NoOpenStartTagException err) {
                XPathException e = new XPathException(err.getMessage());
                e.setXPathContext(context);
                e.setErrorCodeQName(err.getErrorCodeQName());
                throw dynamicError(this, e, context);
            }
            break;

        case Type.DOCUMENT:
            if (selectSpecified) {
                c2 = context.newMinorContext();
                SequenceIterator si = SingletonIterator.makeIterator(item);
                si.next();
                c2.setCurrentIterator(si);
            }
            if (preservingTypes) {
                out.startDocument(0);
                content.process(c2);
                out.endDocument();
            } else {
                boolean pop = false;
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setStripSpace(Whitespace.NONE);
                Receiver val = controller.getConfiguration().
                        getDocumentValidator(out, source.getBaseURI(), options);
                if (val != out) {
                    SequenceReceiver sr = new TreeReceiver(val);
                    sr.setPipelineConfiguration(out.getPipelineConfiguration());
                    c2.setReceiver(sr);
                    pop = true;
                    out = sr;
                }
                out.startDocument(0);
                content.process(c2);
                out.endDocument();
                if (pop) {
                    context.setReceiver(out);
                }
            }
            break;

        default:
            throw new IllegalArgumentException("Unknown node kind " + source.getNodeKind());

        }
        return null;
    }

    /**
     * Evaluate as an expression. We rely on the fact that when these instructions
     * are generated by XQuery, there will always be a valueExpression to evaluate
     * the content
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        SequenceReceiver saved = context.getReceiver();
        SequenceOutputter seq = controller.allocateSequenceOutputter(1);
        seq.getPipelineConfiguration().setHostLanguage(getHostLanguage());
        context.setReceiver(seq);
        process(context);
        seq.close();
        context.setReceiver(saved);
        Item item = seq.getFirstItem();
        seq.reset();
        return item;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("copy");
        out.startSubsidiaryElement("select");
        select.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("action");
        content.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }


}

