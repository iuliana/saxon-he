////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.LocalOrderComparer;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.KeyDefinitionSet;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.StringValue;


public class Idref extends SystemFunctionCall implements Callable {

    private KeyDefinitionSet idRefKey;

    /**
    * Simplify: add a second implicit argument, the context document
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        Idref f = (Idref)super.simplify(visitor);
        f.addContextDocumentArgument(1, "idref");
        return f;
    }


    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.typeCheck(visitor, contextItemType);
        idRefKey = visitor.getExecutable().getKeyManager().getKeyDefinitionSet(
                    StandardNames.getStructuredQName(StandardNames.XS_IDREFS));
        return e;
    }

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        argument[0] = ExpressionTool.unsorted(opt, argument[0], false);
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
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Idref i2 = (Idref)super.copy();
        i2.idRefKey = idRefKey;
        return i2;
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
        // indicate that the function navigates to all nodes in the document
        target = target.createArc(AxisInfo.DESCENDANT, AnyNodeTest.getInstance());
        return target;
    }


    /**
    * Enumerate the results of the expression
    */

    /*@NotNull*/
    public SequenceIterator<NodeInfo> iterate(XPathContext context) throws XPathException {

        Controller controller = context.getController();
        assert controller != null;

        NodeInfo arg2 = (NodeInfo)argument[1].evaluateItem(context);
        assert arg2 != null;
        arg2 = arg2.getRoot();
        if (arg2.getNodeKind() != Type.DOCUMENT) {
            dynamicError("In the idref() function," +
                            " the tree being searched must be one whose root is a document node", "FODC0001", context);
            return EmptyIterator.emptyIterator();
        }
        DocumentInfo doc = (DocumentInfo)arg2;

        // If the argument is a singleton, we evaluate the function
        // directly; otherwise we recurse to evaluate it once for each Item
        // in the sequence.

        Expression expression = argument[0];
        if (Cardinality.allowsMany(expression.getCardinality())) {
            SequenceIterator keys = argument[0].iterate(context);
            return getIdrefMultiple(doc, keys, context);

        } else {
            AtomicValue keyValue = (AtomicValue)argument[0].evaluateItem(context);
            if (keyValue == null) {
                return EmptyIterator.emptyIterator();
            }
            KeyManager keyManager = controller.getKeyManager();
            return keyManager.selectByKey(idRefKey, doc, keyValue, context);

        }
    }

    /**
     * Get the result when multiple idref values are supplied. Note this is also called from
     * compiled XQuery code.
     * @param doc the document to be searched
     * @param keys the idref values supplied
     * @param context the dynamic execution context
     * @return iterator over the result of the function
     * @throws XPathException if a dynamic error occurs
     */

    public static SequenceIterator<NodeInfo> getIdrefMultiple(DocumentInfo doc, SequenceIterator keys, XPathContext context)
    throws XPathException {
        IdrefMappingFunction map = new IdrefMappingFunction();
        map.document = doc;
        map.keyContext = context;
        map.keyManager =  context.getController().getKeyManager();
        map.keySet = map.keyManager.getKeyDefinitionSet(StandardNames.getStructuredQName(StandardNames.XS_IDREFS));
        SequenceIterator allValues = new MappingIterator(keys, map);
        return new DocumentOrderIterator(allValues, LocalOrderComparer.getInstance());
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
        NodeInfo arg2 = start.getRoot();
        if (arg2.getNodeKind() != Type.DOCUMENT) {
            dynamicError("In the idref() function," +
                            " the tree being searched must be one whose root is a document node", "FODC0001", context);
            return null;
        }
        DocumentInfo doc = (DocumentInfo)arg2;
        return SequenceTool.toLazySequence(getIdrefMultiple(doc, arguments[0].iterate(), context));
    }

    private static class IdrefMappingFunction implements MappingFunction<StringValue, NodeInfo> {
        public DocumentInfo document;
        public XPathContext keyContext;
        public KeyManager keyManager;
        public KeyDefinitionSet keySet;

        /**
        * Implement the MappingFunction interface
        */

        public SequenceIterator<NodeInfo> map(StringValue item) throws XPathException {
            KeyManager keyManager = keyContext.getController().getKeyManager();
            return keyManager.selectByKey(keySet, document, item, keyContext);
        }
    }

}

