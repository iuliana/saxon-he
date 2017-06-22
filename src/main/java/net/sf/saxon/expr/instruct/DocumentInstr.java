////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.evpull.BracketedDocumentIterator;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.evpull.SingletonEventIterator;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.StringJoin;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.TextFragmentValue;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.Stack;



/**
 * An instruction to create a document node. This corresponds to the xsl:document-node
 * instruction in XSLT. It is also used to support the document node constructor
 * expression in XQuery, and is generated implicitly within an xsl:variable
 * that constructs a temporary tree.
 *
 * <p>Conceptually it represents an XSLT instruction xsl:document-node,
 * with no attributes, whose content is a complex content constructor for the
 * children of the document node.</p>
 */

public class DocumentInstr extends ParentNodeConstructor {

    private boolean textOnly;
    /*@Nullable*/ private String constantText;

    /**
     * Create a document constructor instruction
     * @param textOnly true if the content contains text nodes only
     * @param constantText if the content contains text nodes only and the text is known at compile time,
     *        supplies the textual content
     * @param baseURI the base URI of the instruction
     */

    public DocumentInstr(boolean textOnly,
                         String constantText,
                         String baseURI) {
        this.textOnly = textOnly;
        this.constantText = constantText;
        setBaseURI(baseURI);
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    public int getImplementationMethod() {
        return Expression.EVALUATE_METHOD;
    }

    /**
     * Determine whether this is a "text only" document: essentially, an XSLT xsl:variable that contains
     * a single text node or xsl:value-of instruction.
     * @return true if this is a text-only document
     */

    public boolean isTextOnly() {
        return textOnly;
    }

    /**
     * For a text-only instruction, determine if the text value is fixed and if so return it;
     * otherwise return null
     * @return the fixed text value if appropriate; otherwise null
     */

    public /*@Nullable*/ CharSequence getConstantText() {
        return constantText;
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
        setLazyConstruction(visitor.getConfiguration().getBooleanProperty(FeatureKeys.LAZY_CONSTRUCTION_MODE));
        return super.simplify(visitor);
    }



    /**
     * Check statically that the sequence of child instructions doesn't violate any obvious constraints
     * on the content of the node
     * @param env the static context
     * @throws XPathException
     */

    protected void checkContentSequence(StaticContext env) throws XPathException {
        checkContentSequence(env, content, getValidationOptions());
    }

    protected static void checkContentSequence(StaticContext env, Expression content, ParseOptions validationOptions)
    throws XPathException {
        Expression[] components;
        if (content instanceof Block) {
            components = ((Block)content).getChildren();
        } else {
            components = new Expression[] {content};
        }
        int validation = (validationOptions == null ? Validation.PRESERVE : validationOptions.getSchemaValidationMode());
        SchemaType type = (validationOptions == null ? null : validationOptions.getTopLevelType());
        int elementCount = 0;
        boolean isXSLT = content.getHostLanguage() == Configuration.XSLT;
        TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
        for (Expression component : components) {
            ItemType it = component.getItemType(th);
            if (it instanceof NodeTest) {
                int possibleNodeKinds = ((NodeTest) it).getNodeKindMask();
                if (possibleNodeKinds == 1 << Type.ATTRIBUTE) {
                    XPathException de = new XPathException("Cannot create an attribute node whose parent is a document node");
                    de.setErrorCode(isXSLT ? "XTDE0420" : "XPTY0004");
                    de.setLocator(component);
                    throw de;
                } else if (possibleNodeKinds == 1 << Type.NAMESPACE) {
                    XPathException de = new XPathException("Cannot create a namespace node whose parent is a document node");
                    de.setErrorCode(isXSLT ? "XTDE0420" : "XQTY0024");
                    de.setLocator(component);
                    throw de;
                }
                if (possibleNodeKinds == 1 << Type.ELEMENT) {
                    elementCount++;
                    if (elementCount > 1 &&
                            (validation == Validation.STRICT || validation == Validation.LAX || type != null)) {
                        XPathException de = new XPathException("A valid document must have only one child element");
                        if (isXSLT) {
                            de.setErrorCode("XTTE1550");
                        } else {
                            de.setErrorCode("XQDY0061");
                        }
                        de.setLocator(component);
                        throw de;
                    }
                    if (validation == Validation.STRICT && component instanceof FixedElement) {
                        SchemaDeclaration decl = env.getConfiguration().getElementDeclaration(
                                ((FixedElement) component).getElementName().getFingerprint());
                        if (decl != null) {
                            ((FixedElement) component).getContentExpression().
                                    checkPermittedContents(decl.getType(), env, true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        p |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        if (getValidationAction() == Validation.SKIP) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    /**
     * In the case of a text-only instruction (xsl:variable containing a text node or one or more xsl:value-of
     * instructions), return an expression that evaluates to the textual content as an instance of xs:untypedAtomic
     * @return an expression that evaluates to the textual content
     */

    public Expression getStringValueExpression() {
        if (textOnly) {
            if (constantText != null) {
                return new StringLiteral(new UntypedAtomicValue(constantText));
            } else if (content instanceof ValueOf) {
                return ((ValueOf)content).convertToCastAsString();
            } else {
                StringJoin fn = (StringJoin) SystemFunctionCall.makeSystemFunction(
                        "string-join", new Expression[]{content, new StringLiteral(StringValue.EMPTY_STRING)});
                CastExpression cast = new CastExpression(fn, BuiltInAtomicType.UNTYPED_ATOMIC, false);
                ExpressionTool.copyLocationInfo(this, cast);
                return cast;
            }
        } else {
            throw new AssertionError("getStringValueExpression() called on non-text-only document instruction");
        }
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        DocumentInstr doc = new DocumentInstr(textOnly, constantText, getBaseURI());
        doc.setContentExpression(content.copy());
        doc.setValidationAction(getValidationAction(), getSchemaType());
        doc.setLazyConstruction(isLazyConstruction());
        return doc;
    }

    /**
     * Get the item type
     * @param th The TypeHierarchy
     * @return the in
     */
    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return NodeKindTest.DOCUMENT;
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        if (preservingTypes && !textOnly) {
            SequenceReceiver out = context.getReceiver();
            out.startDocument(0);
            content.process(context);
            out.endDocument();
            return null;
        } else {
            Item item = evaluateItem(context);
            if (item != null) {
                SequenceReceiver out = context.getReceiver();
                out.append(item, locationId, NodeInfo.ALL_NAMESPACES);
            }
            return null;
        }
    }

    /**
     * Evaluate as an item.
     */

    public DocumentInfo evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (isLazyConstruction() && (
                !controller.getExecutable().isSchemaAware() ||
                        (getValidationAction() == Validation.PRESERVE && getSchemaType() == null))) {
            return context.getConfiguration().makeUnconstructedDocument(this, context);
            //return new UnconstructedDocument(this, context);
        } else {

            DocumentInfo root;
            if (textOnly) {
                CharSequence textValue;
                if (constantText != null) {
                    textValue = constantText;
                } else {
                    FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
                    SequenceIterator iter = content.iterate(context);
                    while (true) {
                        Item item = iter.next();
                        if (item==null) break;
                        sb.append(item.getStringValueCS());
                    }
                    textValue = sb.condense();
                }
                root = new TextFragmentValue(textValue, getBaseURI());
                ((TextFragmentValue)root).setConfiguration(controller.getConfiguration());
            } else {
                try {
                	SequenceReceiver saved = context.getReceiver();

                    Builder builder = controller.makeBuilder();
                    
                    builder.setBaseURI(getBaseURI());
                    builder.setTiming(false);

                    PipelineConfiguration pipe = controller.makePipelineConfiguration();
                    pipe.setHostLanguage(getHostLanguage());
                    //pipe.setBaseURI(baseURI);
                    builder.setPipelineConfiguration(pipe);

                    context.changeOutputDestination(builder, getValidationOptions());
                    Receiver out = context.getReceiver();
                    out.open();
                    out.startDocument(0);

                    content.process(context);

                    out.endDocument();
                    out.close();
                    context.setReceiver(saved);
                    root = (DocumentInfo)builder.getCurrentRoot();
                } catch (XPathException e) {
                    e.maybeSetLocation(this);
                    e.maybeSetContext(context);
                    throw e;
                }
            }
            return root;
        }
    }

    public EventIterator iterateEvents(XPathContext context) throws XPathException {
        if (getValidationAction() != Validation.PRESERVE) {
            // Schema validation can't be done in pull mode
            return new SingletonEventIterator(evaluateItem(context));
        }
        return new BracketedDocumentIterator(content.iterateEvents(context));

    }


    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * (the string "document-constructor")
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_DOCUMENT;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("documentNode");
        out.emitAttribute("validation", Validation.toString(getValidationAction()));
        final SchemaType schemaType = getSchemaType();
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getDescription());
        }        
        content.explain(out);
        out.endElement();
    }


}

