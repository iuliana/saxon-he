////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.evpull.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;

import java.util.Iterator;
import java.util.Stack;





/**
 * An instruction that creates an element node. There are two subtypes, FixedElement
 * for use where the name is known statically, and Element where it is computed
 * dynamically. To allow use in both XSLT and XQuery, the class acts both as an
 * Instruction and as an Expression.
 */

public abstract class ElementCreator extends ParentNodeConstructor {

    /**
     * The inheritNamespaces flag indicates that the namespace nodes on the element created by this instruction
     * are to be inherited (copied) on the children of this element. That is, if this flag is false, the child
     * elements must carry a namespace undeclaration for all the namespaces on the parent, unless they are
     * redeclared in some way.
     */

    protected boolean inheritNamespaces = true;

    protected boolean inheritFromParent = true;

    /**
     * The onEmpty expression is used in XSLT 3.0 to supply an alternative result to be returned
     * when the content of the element (including attributes) is empty. The most common case is
     * to return an empty sequence instead of constructing the element.
     */

    protected Expression onEmpty = null;

    /**
     * Construct an ElementCreator. Exists for the benefit of subclasses.
     */

    public ElementCreator() {
    }

    @Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        if (onEmpty != null) {
            onEmpty = visitor.simplify(onEmpty);
        }
        return super.simplify(visitor);
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (onEmpty != null) {
            onEmpty = visitor.typeCheck(onEmpty, contextItemType);
        }
        return super.typeCheck(visitor, contextItemType);
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (onEmpty != null) {
            onEmpty = visitor.optimize(onEmpty, contextItemType);
        }
        return super.optimize(visitor, contextItemType);
    }

    /**
     * Get the item type of the value returned by this instruction
     *
     * @param th the type hierarchy cache
     * @return the item type
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (onEmpty == null) {
            return NodeKindTest.ELEMENT;
        } else {
            return Type.getCommonSuperType(NodeKindTest.ELEMENT, onEmpty.getItemType(th), th);
        }
    }

    @Override
    public int getCardinality() {
        if (onEmpty == null) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return Cardinality.union(StaticProperty.EXACTLY_ONE, onEmpty.getCardinality());
        }
    }

    @Override
    public Iterator<Expression> iterateSubExpressions() {
        if (onEmpty == null) {
            return super.iterateSubExpressions();
        } else {
            return new PairIterator<Expression>(content, onEmpty);
        }
    }

    @Override
    public Iterator<SubExpressionInfo> iterateSubExpressionInfo() {
        if (onEmpty == null) {
            return super.iterateSubExpressionInfo();
        } else {
            return new PairIterator<SubExpressionInfo>(
                    new SubExpressionInfo(content, true, false, NODE_VALUE_CONTEXT),
                    new SubExpressionInfo(onEmpty, true, false, NAVIGATION_CONTEXT));
        }
    }

    /**
     * Determine whether the inherit namespaces flag is set
     *
     * @return true if namespaces constructed on a parent element are to be inherited by its children
     */

    public boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

    public boolean isInheritFromParent() {
        return inheritFromParent;
    }

    /**
     * Set the on-empty expression, which defines the value to be returned if the element would otherwise
     * be empty
     *
     * @param onEmpty the expression to be evaluated if the element would otherwise be empty
     */

    public void setOnEmpty(Expression onEmpty) {
        this.onEmpty = onEmpty;
    }

    /**
     * Get the on-empty expression, which defines the value to be returned if the element would otherwise
     * be empty
     *
     * @return the on-empty expression if there is one, or null otherwise
     */

    public Expression getOnEmpty() {
        return onEmpty;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties() |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
        if (getValidationAction() == Validation.STRIP) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        if (onEmpty != null) {
            return p & onEmpty.getSpecialProperties();
        } else {
            return p;
        }
    }

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     */

    public void suppressValidation(int parentValidationMode) {
        if (getValidationAction() == parentValidationMode && getSchemaType() == null) {
            // TODO: is this safe? e.g. if the child has validation=strict but matches a skip wildcard in the parent
            setValidationAction(Validation.PRESERVE, null);
        }
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @param env the static context
     * @throws XPathException
     */

    protected void checkContentSequence(StaticContext env) throws XPathException {
        if (content instanceof Block) {
            TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
            Expression[] components = ((Block) content).getChildren();
            boolean foundChild = false;
            boolean foundPossibleChild = false;
            int childNodeKinds = (1 << Type.TEXT | 1 << Type.ELEMENT | 1 << Type.COMMENT | 1 << Type.PROCESSING_INSTRUCTION);
            for (Expression component : components) {

                ItemType it = component.getItemType(th);
                if (it instanceof NodeTest) {
                    boolean maybeEmpty = Cardinality.allowsZero(component.getCardinality());
                    int possibleNodeKinds = ((NodeTest) it).getNodeKindMask();
                    if ((possibleNodeKinds & 1 << Type.TEXT) != 0) {
                        // the text node might turn out to be zero-length. If that's a possibility,
                        // then we only issue a warning. Also, we need to completely ignore a known
                        // zero-length text node, which is included to prevent space-separation
                        // in an XQuery construct like <a>{@x}{@y}</b>
                        if (component instanceof ValueOf &&
                                ((ValueOf) component).select instanceof StringLiteral) {
                            String value = (((StringLiteral) ((ValueOf) component).select).getStringValue());
                            if (value.length() == 0) {
                                // continue;  // not an error
                            } else {
                                foundChild = true;
                            }
                        } else {
                            foundPossibleChild = true;
                        }
                    } else if ((possibleNodeKinds & ~childNodeKinds) == 0) {
                        if (maybeEmpty) {
                            foundPossibleChild = true;
                        } else {
                            foundChild = true;
                        }
                    } else if (foundChild && possibleNodeKinds == 1 << Type.ATTRIBUTE && !maybeEmpty) {
                        XPathException de = new XPathException(
                                "Cannot create an attribute node after creating a child of the containing element");
                        de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                        de.setLocator(component);
                        throw de;
                    } else if (foundChild && possibleNodeKinds == 1 << Type.NAMESPACE && !maybeEmpty) {
                        XPathException de = new XPathException(
                                "Cannot create a namespace node after creating a child of the containing element");
                        de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                        de.setLocator(component);
                        throw de;
                    } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == 1 << Type.ATTRIBUTE) {
                        env.issueWarning(
                                "Creating an attribute here will fail if previous instructions create any children",
                                component);
                    } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == 1 << Type.NAMESPACE) {
                        env.issueWarning(
                                "Creating a namespace node here will fail if previous instructions create any children",
                                component);
                    }
                }
            }

        }
    }

    /**
     * Determine (at run-time) the name code of the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode for the benefit of xsl:copy, the node being copied; otherwise null
     * @return the integer name code representing the element name
     * @throws XPathException if a failure occurs
     */

    public abstract NodeName getElementName(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException;

    /**
     * Get the base URI for the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI of the constructed element
     */

    public abstract String getNewBaseURI(XPathContext context, NodeInfo copiedNode);

    /**
     * Callback to output namespace nodes for the new element. This method is responsible
     * for ensuring that a namespace node is always generated for the namespace of the element
     * name itself.
     *
     * @param context    The execution context
     * @param receiver   the Receiver where the namespace nodes are to be written
     * @param nameCode   the name code of the element being created
     * @param copiedNode the node being copied (for xsl:copy) or null otherwise
     * @throws XPathException if a dynamic error occurs
     */

    public abstract void outputNamespaceNodes(
            XPathContext context, Receiver receiver, NodeName nameCode, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException;

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     *
     * @return the set of namespace bindings.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    public NamespaceBinding[] getActiveNamespaces() throws XPathException {
        return null;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD | Expression.EVALUATE_METHOD;
    }

    public EventIterator iterateEvents(XPathContext context) throws XPathException {
        return iterateEvents(context, null);
    }

    protected EventIterator iterateEvents(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException {
        if (!preservingTypes && getValidationAction() != Validation.STRIP) {
            // Schema validation can't be done in pull mode
            return new SingletonEventIterator(evaluateItem(context));
        }
        if (onEmpty != null) {
            // The on-empty attribute can't be handled in pull mode
            return new SingletonEventIterator(evaluateItem(context));
        }
        final Controller controller = context.getController();
        assert controller != null;
        StartElementEvent start = new StartElementEvent(controller.makePipelineConfiguration());
        start.setElementName(getElementName(context, copiedNode));
        start.setTypeCode(getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance());
        start.setLocalNamespaces(getActiveNamespaces());
        start.setLocationId(locationId);
        EventIterator result = new BracketedElementIterator(
                start, content.iterateEvents(context), EndElementEvent.getInstance());
        if (getValidationAction() == Validation.STRIP && controller.getExecutable().isSchemaAware()) {
            return new EventAnnotationStripper(result);
        } else {
            return result;
        }
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     * @param context XPath dynamic evaluation context
     * @return null (this instruction never returns a tail call)
     * @throws XPathException
     */
    public TailCall processLeavingTail(XPathContext context)
            throws XPathException {
        return processLeavingTail(context, null);
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode null except in the case of xsl:copy, when it is the node being copied; otherwise null
     * @return null (this instruction never returns a tail call)
     * @throws XPathException if a dynamic error occurs
     */
    public final TailCall processLeavingTail(XPathContext context, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException {

        try {

            NodeName elemName = getElementName(context, copiedNode);
            SchemaType typeCode = (getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance());

            SequenceReceiver out = context.getReceiver();
            SequenceReceiver saved = out;
            boolean pop = false;
            Receiver elemOut = out;
            if (!preservingTypes) {
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setTopLevelElement(elemName);
                Receiver validator = context.getConfiguration().getElementValidator(out, options, locationId);

                if (validator != out) {
                    out = new TreeReceiver(validator);
                    context.setReceiver(out);
                    pop = true;
                }
                elemOut = out;
            }

            if (onEmpty != null) {
                OnEmptyHandler monitor = new OnEmptyHandler(out, onEmpty, context);
                context.setReceiver(monitor);
                pop = true;
                elemOut = monitor;
            }

            if (elemOut.getSystemId() == null) {
                elemOut.setSystemId(getNewBaseURI(context, copiedNode));
            }
            int properties = (inheritNamespaces ? 0 : ReceiverOptions.DISINHERIT_NAMESPACES);
            if (!inheritFromParent) {
                properties |= ReceiverOptions.REFUSE_NAMESPACE_INHERITANCE;
            }
            elemOut.startElement(elemName, typeCode, locationId, properties);

            // output the required namespace nodes via a callback

            outputNamespaceNodes(context, elemOut, elemName, copiedNode);

            // process subordinate instructions to generate attributes and content
            content.process(context);

            // output the element end tag (which will fail if validation fails)
            elemOut.endElement();

            if (pop) {
                context.setReceiver(saved);
            }
            return null;

        } catch (XPathException e) {
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Evaluate the constructor, returning the constructed element node. If lazy construction
     * mode is in effect, then an UnconstructedParent object is returned instead.
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        if (isLazyConstruction() && preservingTypes && onEmpty == null) {
            return context.getConfiguration().makeUnconstructedElement(this, context);
        } else {
            NodeInfo node = constructElement(context, null);
            // TODO: recover from validation errors that might have occurred
            if (onEmpty != null && !node.hasChildNodes() && node.iterateAxis(AxisInfo.ATTRIBUTE).next() == null) {
                return onEmpty.evaluateItem(context);
            } else {
                return node;
            }
        }
    }

    /**
     * Construct the element node as a free-standing (parentless) node in a tiny tree
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode for the benefit of xsl:copy, the node being copied
     * @return the constructed element node
     * @throws XPathException if a dynamic error occurs
     */
    private NodeInfo constructElement(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException {
        try {
            Controller controller = context.getController();
            assert controller != null;
            SequenceReceiver saved = context.getReceiver();
            SequenceOutputter seq = controller.allocateSequenceOutputter(1);
            seq.getPipelineConfiguration().setHostLanguage(getHostLanguage());

            NodeName elemName = getElementName(context, copiedNode);
            SchemaType typeCode = (getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance());

            SequenceReceiver ini = seq;
            if (!preservingTypes) {
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setTopLevelElement(elemName);
                Receiver validator = controller.getConfiguration().getElementValidator(ini, options, locationId);

                if (ini.getSystemId() == null) {
                    ini.setSystemId(getNewBaseURI(context, copiedNode));
                }
                if (validator == ini) {
                    context.setReceiver(ini);
                } else {
                    TreeReceiver tr = new TreeReceiver(validator);
                    tr.setPipelineConfiguration(seq.getPipelineConfiguration());
                    context.setReceiver(tr);
                    ini = tr;
                }
            } else {
                context.setReceiver(ini);
                if (ini.getSystemId() == null) {
                    ini.setSystemId(getNewBaseURI(context, copiedNode));
                }
            }

            ini.open();
            int properties = (inheritNamespaces ? 0 : ReceiverOptions.DISINHERIT_NAMESPACES);
            ini.startElement(elemName, typeCode, locationId, properties);

            // output the namespace nodes for the new element
            outputNamespaceNodes(context, ini, elemName, null);

            content.process(context);

            ini.endElement();
            ini.close();
            context.setReceiver(saved);

            // the constructed element is the first and only item in the sequence
            NodeInfo result = (NodeInfo) seq.popLastItem();
            seq.reset();
            return result;

        } catch (XPathException err) {
            if (err instanceof ValidationException) {
                ((ValidationException) err).setSourceLocator(this);
                ((ValidationException) err).setSystemId(getSystemId());
            }
            err.maybeSetLocation(this);
            err.maybeSetContext(context);
            throw err;
        }
    }



}

