////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.LocalOrderComparer;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;


/**
* The XPath id() or element-with-id() function
* XPath 2.0 version: accepts any sequence as the first parameter; each item in the sequence
* is taken as an IDREFS value, that is, a space-separated list of ID values.
 * Also accepts an optional second argument to identify the target document, this
 * defaults to the context node.
*/


public class Id extends SystemFunctionCall implements Callable {

    public static final int ID = 0;
    public static final int ELEMENT_WITH_ID = 1;

    private boolean isSingletonId = false;

    /**
    * Simplify: add a second implicit argument, the context document
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        Id id = (Id)super.simplify(visitor);
        if (argument.length == 1) {
            id.addContextDocumentArgument(1, getFunctionName().getLocalPart());
        }
        return id;
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, /*@Nullable*/ ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (argument[1] instanceof RootExpression &&
                contextItemType != null && contextItemType.itemType != null &&
                contextItemType.itemType.isPlainType()) {
            // intercept this to get better diagnostics
            XPathException err = new XPathException(getFunctionName().getLocalPart() +
                    "() function called when the context item is not a node");
            err.setErrorCode("XPTY0004");
            err.setLocator(this);
            throw err;
        }
        return super.typeCheck(visitor, contextItemType);
    }

    /**
     * Static analysis: prevent sorting of the argument
     */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        argument[0] = ExpressionTool.unsorted(opt, argument[0], false);
        isSingletonId = !Cardinality.allowsMany(argument[0].getCardinality());
    }

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-signficant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NON_CREATIVE;
        if ((getNumberOfArguments() == 1) ||
                (argument[1].getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }


    /**
     * Add a representation of a doc() call or similar function to a PathMap.
     * This is a convenience method called by the addToPathMap() methods for doc(), document(), collection()
     * and similar functions. These all create a new root expression in the path map.
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        argument[0].addToPathMap(pathMap, pathMapNodeSet);
        PathMap.PathMapNodeSet target = argument[1].addToPathMap(pathMap, pathMapNodeSet);
        // indicate that the function navigates to all elements in the document
        target = target.createArc(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
        return target;
    }

    /**
    * Evaluate the function to return an iteration of selected nodes.
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        NodeInfo arg1;
        try {
            arg1 = (NodeInfo)argument[1].evaluateItem(context);
        } catch (XPathException e) {
            if (context.getContextItem() instanceof AtomicValue) {
                // Override the unhelpful message that trickles down...
                XPathException e2 = new XPathException("id() function called when the context item is not a node");
                e2.setErrorCode("XPTY0004");
                e2.setXPathContext(context);
                e2.setLocator(this);
                throw e2;
            } else {
                throw e;
            }
        }
        arg1 = arg1.getRoot();
        if (arg1.getNodeKind() != Type.DOCUMENT) {
            dynamicError("In the " + getFunctionName().getLocalPart() + "() function," +
                            " the tree being searched must be one whose root is a document node", "FODC0001", context);
            return null;
        }
        DocumentInfo doc = (DocumentInfo)arg1;

        if (isSingletonId) {
            AtomicValue arg = (AtomicValue)argument[0].evaluateItem(context);
            if (arg==null) {
                return EmptyIterator.getInstance();
            }
            String idrefs = arg.getStringValue();
            return getIdSingle(doc, idrefs, operation);
        } else {
            SequenceIterator idrefs = argument[0].iterate(context);
            return getIdMultiple(doc, idrefs, operation);
        }
    }

    /**
     * Get an iterator over the nodes that have an id equal to one of the values is a whitespace separated
     * string
     * @param doc The document to be searched
     * @param idrefs a string containing zero or more whitespace-separated ID values to be found in the document
     * @param operation either {@link #ID} or {@link #ELEMENT_WITH_ID}
     * @return an iterator over the nodes whose ID is one of the specified values
     * @throws XPathException
     */

    public static SequenceIterator getIdSingle(DocumentInfo doc, String idrefs, int operation) throws XPathException {
        boolean white = false;
        for (int i=idrefs.length()-1; i>=0; i--) {
            char c = idrefs.charAt(i);
            if (c <= 0x20 && (c == 0x20 || c == 0x09 || c == 0x0a || c == 0x0d)) {
                white = true;
                break;
            }
        }

        if (white) {
            StringTokenIterator tokens = new StringTokenIterator(idrefs);
            IdMappingFunction map = new IdMappingFunction();
            map.document = doc;
            map.operation = operation;
            SequenceIterator result = new MappingIterator(tokens, map);
            return new DocumentOrderIterator(result, LocalOrderComparer.getInstance());
        } else {
            return SingletonIterator.makeIterator(doc.selectID(idrefs, operation == ELEMENT_WITH_ID));
        }
    }

    /**
     * Get an iterator over the nodes that have an id equal to one of the values is a set of whitespace separated
     * strings
     * @param doc The document to be searched
     * @param idrefs an iterator over a set of strings each of which is a string containing
     * zero or more whitespace-separated ID values to be found in the document
     * @return an iterator over the nodes whose ID is one of the specified values
     * @throws XPathException
     */

    public static SequenceIterator getIdMultiple(DocumentInfo doc, SequenceIterator idrefs, int operation) throws XPathException {
        IdMappingFunction map = new IdMappingFunction();
        map.document = doc;
        map.operation = operation;
        SequenceIterator result = new MappingIterator(idrefs, map);
        return new DocumentOrderIterator(result, LocalOrderComparer.getInstance());
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo start = (arguments.length == 1 ? getContextNode(context) : (NodeInfo)arguments[1].head());
        NodeInfo arg1 = start.getRoot();
        if (arg1.getNodeKind() != Type.DOCUMENT) {
            dynamicError("In the " + getFunctionName().getLocalPart() + "() function," +
                            " the tree being searched must be one whose root is a document node", "FODC0001", context);
            return null;
        }
        DocumentInfo doc = (DocumentInfo)arg1;
        SequenceIterator idrefs = arguments[0].iterate();
        return SequenceTool.toLazySequence(getIdMultiple(doc, idrefs, operation));
    }

    private static class IdMappingFunction implements MappingFunction<StringValue, NodeInfo> {

        public DocumentInfo document;
        private int operation;

        /**
        * Evaluate the function for a single string value
        * (implements the MappingFunction interface)
        */

        public SequenceIterator<NodeInfo> map(StringValue item) throws XPathException {

            String idrefs = Whitespace.trim(item.getStringValueCS());

            // If this value contains a space, we need to break it up into its
            // separate tokens; if not, we can process it directly

            if (Whitespace.containsWhitespace(idrefs)) {
                StringTokenIterator tokens = new StringTokenIterator(idrefs);
                IdMappingFunction submap = new IdMappingFunction();
                submap.document = document;
                submap.operation = operation;
                return new MappingIterator(tokens, submap);

            } else {
                return SingletonIterator.makeIterator(document.selectID(idrefs, operation == ELEMENT_WITH_ID));
            }
        }
    }

}

