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
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.style.ExpressionContext;
import net.sf.saxon.trans.KeyDefinitionSet;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;


public class KeyFn extends SystemFunctionCall {

    private  transient NamespaceResolver nsContext = null;
    private KeyDefinitionSet staticKeySet = null; // null if name resolution is done at run-time
    private transient boolean checked = false;
    private transient boolean internal = false;
        // the second time checkArguments is called, it's a global check so the static context is inaccurate

    /**
     * Get the key name, if known statically. If not known statically, return null.
     * @return the key name if known, otherwise null
     */

    public StructuredQName getStaticKeyName() {
        return (staticKeySet == null ? null : staticKeySet.getKeyName());
    }
    
    public KeyDefinitionSet getStaticKeySet(){
    	
    	return staticKeySet;
    }
    
    public boolean getInternal(){
    	return internal;
    }
    

    public NamespaceResolver getNamespaceResolver(){
    	return nsContext;
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        try {
            return super.typeCheck(visitor, contextItemType);
        } catch (XPathException err) {
            if ("XPDY0002".equals(err.getErrorCodeLocalPart()) && argument[2] instanceof RootExpression) {
                XPathException e = new XPathException("Cannot call the key() function when there is no context node");
                e.setErrorCode("XTDE1270");
                e.maybeSetLocation(this);
                throw e;
            }
            throw err;
        }
    }

    /**
     * Non-standard constructor to create an internal call on key() with a known key definition
     * @param keySet the set of KeyDefinitions (always a single KeyDefinition)
     * @param name the name allocated to the key (first argument of the function)
     * @param value the value being searched for (second argument of the function)
     * @param doc the document being searched (third argument)
     * @return a call on the key() function
     */

    public static KeyFn internalKeyCall(KeyDefinitionSet keySet, String name, Expression value, Expression doc) {
        KeyFn k = new KeyFn();
        k.argument = new Expression[] {new StringLiteral(name), value, doc};
        k.staticKeySet = keySet;
        k.checked = true;
        k.internal = true;
        k.setDetails(StandardFunction.getFunction("key", 3));
        k.setFunctionName(FN_KEY);
        k.adoptChildExpression(value);
        k.adoptChildExpression(doc);
        return k;
    }

    private final static StructuredQName FN_KEY = new StructuredQName("fn", NamespaceConstant.FN, "key");

    /**
     * Simplify: add a third implicit argument, the context document
     * @param visitor the expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        if (!internal && !(visitor.getStaticContext() instanceof ExpressionContext)) {
            throw new XPathException("The key() function is available only in XPath expressions within an XSLT stylesheet");
        }
        KeyFn f = (KeyFn)super.simplify(visitor);
        if (argument.length == 2) {
            f.addContextDocumentArgument(2, "key");
        }
        return f;
    }

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        if (checked) return;
        checked = true;
        super.checkArguments(visitor);
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        argument[1] = ExpressionTool.unsorted(opt, argument[1], false);
        if (argument[0] instanceof StringLiteral) {
            // common case, key name is supplied as a constant
            StructuredQName keyName;
            try {
                keyName = StructuredQName.fromLexicalQName(
                        ((StringLiteral)argument[0]).getStringValue(),
                        false, true,
                        visitor.getConfiguration().getNameChecker(),
                        visitor.getStaticContext().getNamespaceResolver());
            } catch (XPathException e) {
                XPathException err = new XPathException("Error in key name " +
                        ((StringLiteral)argument[0]).getStringValue() + ": " + e.getMessage());
                err.setLocator(this);
                err.setErrorCode("XTDE1260");
                throw err;
            }
            staticKeySet = visitor.getExecutable().getKeyManager().getKeyDefinitionSet(keyName);
            if (staticKeySet == null) {
                XPathException err = new XPathException("Key " +
                        ((StringLiteral)argument[0]).getStringValue() + " has not been defined");
                err.setLocator(this);
                err.setErrorCode("XTDE1260");
                throw err;
            }
        } else {
            // we need to save the namespace context
            nsContext = visitor.getStaticContext().getNamespaceResolver();
        }
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-signficant. These properties are used for optimizations. In general, if
    * a property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NON_CREATIVE;
        if ((getNumberOfArguments() == 2) ||
                (argument[2].getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor the expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Add a representation of a doc() call or similar function to a PathMap.
     * This is a convenience method called by the addToPathMap() methods for doc(), document(), collection()
     * and similar functions. These all create a new root expression in the path map.
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        argument[0].addToPathMap(pathMap, pathMapNodeSet);
        argument[1].addToPathMap(pathMap, pathMapNodeSet);
        PathMap.PathMapNodeSet target = argument[2].addToPathMap(pathMap, pathMapNodeSet);
        // indicate that the function navigates to all nodes in the containing document
        target = target.createArc(AxisInfo.ANCESTOR_OR_SELF, NodeKindTest.DOCUMENT);
        return target.createArc(AxisInfo.DESCENDANT, AnyNodeTest.getInstance());
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        KeyFn k = (KeyFn)super.copy();
        k.nsContext = nsContext;
        k.staticKeySet = staticKeySet;
        k.internal = internal;
        k.checked = checked;
        return k;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return this==o;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (nsContext != null ? nsContext.hashCode() : 0);
        result = 31 * result + (staticKeySet != null ? staticKeySet.hashCode() : 0);
        result = 31 * result + (checked ? 1 : 0);
        result = 31 * result + (internal ? 1 : 0);
        return result;
    }

    /**
    * Enumerate the results of the expression
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        Controller controller = context.getController();

        Item arg2;
        try {
            arg2 = argument[2].evaluateItem(context);
        } catch (XPathException e) {
            String code = e.getErrorCodeLocalPart();
            if ("XPDY0002".equals(code)  && argument[2] instanceof RootExpression) {
                dynamicError("Cannot call the key() function when there is no context node", "XTDE1270", context);
            } else if ("XPDY0050".equals(code)) {
                dynamicError("In the key() function," +
                            " the node supplied in the third argument (or the context node if absent)" +
                            " must be in a tree whose root is a document node", "XTDE1270", context);
            } else if ("XPTY0020".equals(code) || "XPTY0019".equals(code)) {
                dynamicError("Cannot call the key() function when the context item is an atomic value",
                        "XTDE1270", context);
            }
            throw e;
        }

        NodeInfo origin = (NodeInfo)arg2;
        NodeInfo root = origin.getRoot();
        if (root.getNodeKind() != Type.DOCUMENT) {
            dynamicError("In the key() function," +
                            " the node supplied in the third argument (or the context node if absent)" +
                            " must be in a tree whose root is a document node", "XTDE1270", context);
            return null;
        }
        DocumentInfo doc = (DocumentInfo)root;

        KeyDefinitionSet selectedKeySet = staticKeySet;
        if (selectedKeySet == null) {
            String givenkeyname = argument[0].evaluateItem(context).getStringValue();
            StructuredQName qName = null;
            try {
                boolean is30 = context.getController().getExecutable().isAllowXPath30();
                qName = StructuredQName.fromLexicalQName(
                            givenkeyname, false, is30, controller.getConfiguration().getNameChecker(),
                            nsContext);
            } catch (XPathException err) {
                dynamicError("Invalid key name: " + err.getMessage(), "XTDE1260", context);
            }
            selectedKeySet = controller.getKeyManager().getKeyDefinitionSet(qName);
            if (selectedKeySet == null) {
                dynamicError("Key '" + givenkeyname + "' has not been defined", "XTDE1260", context);
                return null;
            }
        }

//        if (internal) {
//            System.err.println("Using key " + fprint + " on doc " + doc);
//        }

        // If the second argument is a singleton, we evaluate the function
        // directly; otherwise we recurse to evaluate it once for each Item
        // in the sequence.

        Expression expression = argument[1];
        SequenceIterator allResults;
        if (Cardinality.allowsMany(expression.getCardinality())) {
            final XPathContext keyContext = context;
            final DocumentInfo document = doc;
            final KeyManager keyManager = controller.getKeyManager();
            final KeyDefinitionSet keySet = selectedKeySet;
            MappingFunction map = new MappingFunction<AtomicValue, NodeInfo>() {
                // Map a value to the sequence of nodes having that value as a key value
                public SequenceIterator<NodeInfo> map(AtomicValue item) throws XPathException {
                    return keyManager.selectByKey(
                            keySet, document, item, keyContext);
                }
            };

            SequenceIterator keys = argument[1].iterate(context);
            SequenceIterator allValues = new MappingIterator(keys, map);
            allResults = new DocumentOrderIterator(allValues, LocalOrderComparer.getInstance());
        } else {
            try {
                AtomicValue keyValue = (AtomicValue)argument[1].evaluateItem(context);
                if (keyValue == null) {
                    return EmptyIterator.getInstance();
                }
                KeyManager keyManager = controller.getKeyManager();
                allResults = keyManager.selectByKey(selectedKeySet, doc, keyValue, context);
            } catch (XPathException e) {
                e.maybeSetLocation(this);
                throw e;
            }
        }
        if (origin == doc) {
            return allResults;
        }
        return new ItemMappingIterator(allResults, new SubtreeFilter(origin));
    }

    /**
     * Mapping class to filter nodes that have the origin node as an ancestor-or-self
     */

    public static class SubtreeFilter implements ItemMappingFunction<NodeInfo, NodeInfo> {

        private NodeInfo origin;

        public SubtreeFilter(NodeInfo origin) {
            this.origin = origin;
        }

        public NodeInfo mapItem(NodeInfo item) throws XPathException {
            if (Navigator.isAncestorOrSelf(origin, item)) {
                return item;
            } else {
                return null;
            }
        }

    }



        /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        throw new XPathException("Dynamic evaluation of fn:key() is not supported");
    }

}


