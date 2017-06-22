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
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

import javax.xml.transform.OutputKeys;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * An xsl:message element in the stylesheet.
 */

public class Message extends Instruction {

    /*@NotNull*/ private Expression terminate;
    /*@NotNull*/ private Expression select;
    private Expression errorCode;
    private NamespaceResolver nsResolver;
    private boolean isAssert;

    /**
     * Create an xsl:message instruction
     *
     * @param select    the expression that constructs the message (composite of the select attribute
     *                  and the contained sequence constructor)
     * @param terminate expression that calculates terminate = yes or no.
     */

    public Message(/*@NotNull*/ Expression select, /*@NotNull*/ Expression terminate, Expression errorCode) {
        this.terminate = terminate;
        this.select = select;
        this.errorCode = errorCode;
        adoptChildExpression(terminate);
        adoptChildExpression(select);
    }

    /**
     * Say whether this instruction is implementing xsl:message or xsl:assert
     * @param isAssert true if this is xsl:assert; false if it is xsl:message
     */

    public void setIsAssert(boolean isAssert) {
        this.isAssert = isAssert;
    }

    /**
     * Set a namespace resolver for resolving any namespace prefix appearing in the value of the error-code
     * attribute
     * @param resolver
     */

    public void setNamespaceResolver(NamespaceResolver resolver) {
        this.nsResolver = resolver;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        select = visitor.simplify(select);
        terminate = visitor.simplify(terminate);
        errorCode = visitor.simplify(errorCode);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        select = visitor.typeCheck(select, contextItemType);
        adoptChildExpression(select);
        terminate = visitor.typeCheck(terminate, contextItemType);
        adoptChildExpression(terminate);
        errorCode = visitor.typeCheck(errorCode, contextItemType);
        adoptChildExpression(errorCode);
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        select = visitor.optimize(select, contextItemType);
        adoptChildExpression(select);
        terminate = visitor.optimize(terminate, contextItemType);
        adoptChildExpression(terminate);
        errorCode = visitor.optimize(errorCode, contextItemType);
        adoptChildExpression(errorCode);
        return this;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new Message(select.copy(), terminate.copy(), errorCode.copy());
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    public int getInstructionNameCode() {
        return (isAssert ? StandardNames.XSL_ASSERT : StandardNames.XSL_MESSAGE);
    }

    /**
     * Get the item type. To avoid spurious compile-time type errors, we falsely declare that the
     * instruction can return anything
     *
     * @param th the type hierarchy cache
     * @return AnyItemType
     */
    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return AnyItemType.getInstance();
    }

    /**
     * Get the static cardinality. To avoid spurious compile-time type errors, we falsely declare that the
     * instruction returns zero or one items - this is always acceptable
     *
     * @return zero or one
     */

    public int getCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true.
     */

    public final boolean createsNewNodes() {
        return true;
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        terminate = doPromotion(terminate, offer);
        errorCode = doPromotion(errorCode, offer);
    }


    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        Expression[] children = new Expression[]{select, terminate, errorCode};
        return Arrays.asList(children).iterator();
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
        if (terminate == original) {
            terminate = replacement;
            found = true;
        }
        if (errorCode == original) {
            errorCode = replacement;
            found = true;
        }
        return found;
    }


    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Receiver emitter = controller.getMessageEmitter();
        if (emitter != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (emitter) {
                // In Saxon-EE, multithreading can cause different messages to be entangled unless we synchronize.

                SequenceReceiver rec = new TreeReceiver(emitter);
                rec = new AttributeMasker(rec);

                SequenceReceiver saved = context.getReceiver();

                Properties props = new Properties();
                props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                SerializerFactory sf = context.getConfiguration().getSerializerFactory();
                PipelineConfiguration pipe = controller.makePipelineConfiguration();
                pipe.setHostLanguage(Configuration.XSLT);
                Receiver receiver = sf.getReceiver(rec, pipe, props);
                context.changeOutputDestination(receiver, null);

                boolean abort = false;
                String term = terminate.evaluateAsString(context).toString();
                if (term.equals("no")) {
                    // no action
                } else if (term.equals("yes")) {
                    abort = true;
                } else {
                    XPathException e = new XPathException("The terminate attribute of xsl:message must be 'yes' or 'no'");
                    e.setXPathContext(context);
                    e.setErrorCode("XTDE0030");
                    throw e;
                }


                rec.startDocument(abort ? ReceiverOptions.TERMINATE : 0);

                SequenceIterator iter = select.iterate(context);
                while (true) {
                    Item item = iter.next();
                    if (item == null) {
                        break;
                    }
                    rec.append(item, locationId, NodeInfo.ALL_NAMESPACES);
                }

                rec.endDocument();
                context.setReceiver(saved);
                if (abort) {
                    TerminationException te = new TerminationException(
                            "Processing terminated by " + StandardErrorListener.getInstructionName(this) +
                                    " at line " + getLineNumber() +
                                    " in " + StandardErrorListener.abbreviatePath(getSystemId()));
                    try {
                        String code = errorCode.evaluateAsString(context).toString();
                        StructuredQName errorCode = StructuredQName.fromLexicalQName(
                                    code, false, true, context.getConfiguration().getNameChecker(), nsResolver);
                        te.setErrorCodeQName(errorCode);
                        te.setLocator(this);
                    } catch (XPathException err) {
                        // no action, ignore the error
                    }
                    throw te;
                }
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("xslMessage");
        out.endElement();
    }

    private static class AttributeMasker extends ProxyReceiver {
        private boolean contentStarted = true;

        public AttributeMasker(SequenceReceiver next) {
            super(next);
        }

        public void startElement(NodeName nameCode, SchemaType typeCode, int locationId, int properties) throws XPathException {
            contentStarted = false;
            super.startElement(nameCode, typeCode, locationId, properties);
        }

        public void startContent() throws XPathException {
            contentStarted = true;
            super.startContent();
        }


        public void attribute(NodeName attributeName, SimpleType typeCode, CharSequence value, int locationId, int properties)
                throws XPathException {
            if (contentStarted) {
                String attName = attributeName.getDisplayName();
                processingInstruction("attribute", "name=\"" + attName + "\" value=\"" + value + "\"", 0, 0);
            } else {
                super.attribute(attributeName, typeCode, value, locationId, properties);
            }
        }

        public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
            if (contentStarted) {
                String prefix = namespaceBinding.getPrefix();
                String uri = namespaceBinding.getURI();
                processingInstruction("namespace", "prefix=\"" + prefix + "\" uri=\"" + uri + "\"", 0, 0);
            } else {
                super.namespace(namespaceBinding, properties);
            }
        }

        public void append(Item item, int locationId, int copyNamespaces) throws XPathException {
            if (item instanceof NodeInfo) {
                int kind = ((NodeInfo) item).getNodeKind();
                if (kind == Type.ATTRIBUTE || kind == Type.NAMESPACE) {
                    ((NodeInfo) item).copy(this, 0, 0);
                    return;
                }
            }
            ((SequenceReceiver) nextReceiver).append(item, locationId, copyNamespaces);
        }
    }


}