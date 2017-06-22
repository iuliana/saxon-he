////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;

import java.util.Iterator;
import java.util.List;


/**
 * An AxisExpression is always obtained by simplifying a PathExpression.
 * It represents a PathExpression that starts either at the context node, and uses
 * a simple node-test with no filters. For example "*", "title", "./item",
 * "@*", or "ancestor::chapter*".
 * <p/>
 * <p>An AxisExpression delivers nodes in axis order (not in document order).
 * To get nodes in document order, in the case of a reverse axis, the expression
 * should be wrapped in a call on reverse().</p>
 */

public final class AxisExpression extends Expression {

    private byte axis;
    /*@Nullable*/
    private NodeTest test;
    /*@Nullable*/
    private ItemType itemType = null;
    private ItemType contextItemType = null;
    private int computedCardinality = -1;
    private boolean doneWarnings = false;
    private boolean contextMaybeUndefined = true;

    /**
     * Constructor for an AxisExpression whose origin is the context item
     *
     * @param axis     The axis to be used in this AxisExpression: relevant constants are defined
     *                 in class {@link net.sf.saxon.om.AxisInfo}.
     * @param nodeTest The conditions to be satisfied by selected nodes. May be null,
     *                 indicating that any node on the axis is acceptable
     * @see net.sf.saxon.om.AxisInfo
     */

    public AxisExpression(byte axis, /*@Nullable*/ NodeTest nodeTest) {
        this.axis = axis;
        this.test = nodeTest;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "axisStep";
    }

    /**
     * Simplify an expression
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        Expression e2 = super.simplify(visitor);
        if (e2 != this) {
            return e2;
        }
        if (axis == AxisInfo.PARENT && (test == null || test instanceof AnyNodeTest)) {
            ParentNodeExpression p = new ParentNodeExpression();
            ExpressionTool.copyLocationInfo(this, p);
            return p;
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (contextItemType == null) {
            XPathException err = new XPathException("Axis step " + toString(visitor.getConfiguration().getNamePool()) +
                    " cannot be used here: the context item is absent");
            err.setIsTypeError(true);
            err.setErrorCode("XPDY0002");
            err.setLocator(this);
            throw err;
        } else {
            contextMaybeUndefined = contextItemType.contextMaybeUndefined;
        }
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        int relation = th.relationship(contextItemType.itemType, AnyNodeTest.getInstance());

        if (relation == TypeHierarchy.DISJOINT) {
            XPathException err = new XPathException("Axis step " + toString(visitor.getConfiguration().getNamePool()) +
                    " cannot be used here: the context item is not a node");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0020");
            err.setLocator(this);
            throw err;
        } else if (relation == TypeHierarchy.OVERLAPS || relation == TypeHierarchy.SUBSUMES) {
            // need to insert a dynamic check of the context item type
            ContextItemExpression exp = new ContextItemExpression();
            ExpressionTool.copyLocationInfo(this, exp);
            RoleLocator role = new RoleLocator(RoleLocator.AXIS_STEP, AxisInfo.axisName[axis], 0);
            role.setErrorCode("XPTY0020");
            ItemChecker checker = new ItemChecker(exp, AnyNodeTest.getInstance(), role);
            ExpressionTool.copyLocationInfo(this, checker);
            SimpleStepExpression step = new SimpleStepExpression(checker, this);
            ExpressionTool.copyLocationInfo(this, step);
            return step;
        }

        StaticContext env = visitor.getStaticContext();

        if (this.contextItemType == contextItemType && doneWarnings) {
            return this;
        }

        this.contextItemType = contextItemType.itemType;
        doneWarnings = true;

        ItemType contextType = this.contextItemType;

        if (contextType instanceof NodeTest) {
            int origin = contextType.getPrimitiveType();
            if (origin != Type.NODE) {
                if (AxisInfo.isAlwaysEmpty(axis, origin)) {
                    env.issueWarning("The " + AxisInfo.axisName[axis] + " axis starting at " +
                            (origin == Type.ELEMENT || origin == Type.ATTRIBUTE ? "an " : "a ") +
                            NodeKindTest.nodeKindName(origin) + " node will never select anything",
                            this);
                    return Literal.makeEmptySequence();
                }
            }

            if (test != null) {
                int kind = test.getPrimitiveType();
                if (kind != Type.NODE) {
                    if (!AxisInfo.containsNodeKind(axis, kind)) {
                        env.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any " +
                                NodeKindTest.nodeKindName(kind) + " nodes",
                                this);
                        return Literal.makeEmptySequence();
                    }
                }
                if (axis == AxisInfo.SELF && kind != Type.NODE && origin != Type.NODE && kind != origin) {
                    env.issueWarning("The self axis will never select any " +
                            NodeKindTest.nodeKindName(kind) +
                            " nodes when starting at " +
                            (origin == Type.ELEMENT || origin == Type.ATTRIBUTE ? "an " : "a ") +
                            NodeKindTest.nodeKindName(origin) + " node", this);
                    return Literal.makeEmptySequence();
                }
                if (axis == AxisInfo.SELF) {
                    itemType = new CombinedNodeTest(test, Token.INTERSECT, (NodeTest) contextType);
                }

                // If the content type of the context item is known, see whether the node test can select anything

                if (contextType instanceof DocumentNodeTest && kind == Type.ELEMENT) {
                    NodeTest elementTest = ((DocumentNodeTest) contextType).getElementTest();
                    IntSet outermostElementNames = elementTest.getRequiredNodeNames();
                    if (outermostElementNames != null) {
                        IntSet selectedElementNames = test.getRequiredNodeNames();
                        if (selectedElementNames != null) {
                            if (axis == AxisInfo.CHILD) {
                                // check that the name appearing in the step is one of the names allowed by the nodetest

                                if (selectedElementNames.intersect(outermostElementNames).isEmpty()) {
                                    env.issueWarning("Starting at a document node, the step is selecting an element whose name " +
                                            "is not among the names of child elements permitted for this document node type", this);

                                    return Literal.makeEmptySequence();
                                }

                                if (env.isSchemaAware() &&
                                        elementTest instanceof SchemaNodeTest &&
                                        outermostElementNames.size() == 1) {
                                    IntIterator oeni = outermostElementNames.iterator();
                                    int outermostElementName = (oeni.hasNext() ? oeni.next() : -1);
                                    SchemaDeclaration decl = config.getElementDeclaration(outermostElementName);
                                    if (decl == null) {
                                        env.issueWarning("Element " + config.getNamePool().getDisplayName(outermostElementName) +
                                                " is not declared in the schema", this);
                                        itemType = elementTest;
                                    } else {
                                        SchemaType contentType = decl.getType();
                                        itemType = new CombinedNodeTest(
                                                elementTest, Token.INTERSECT,
                                                new ContentTypeTest(Type.ELEMENT, contentType, config, true));
                                    }
                                } else {
                                    itemType = elementTest;
                                }
                                return this;

                            } else if (axis == AxisInfo.DESCENDANT) {
                                // check that the name appearing in the step is one of the names allowed by the nodetest
                                boolean canMatchOutermost = !selectedElementNames.intersect(outermostElementNames).isEmpty();
                                if (!canMatchOutermost) {
                                    // The expression /descendant::x starting at the document node doesn't match the outermost
                                    // element, so replace it by child::*/descendant::x, and check that
                                    Expression path = ExpressionTool.makePathExpression(new AxisExpression(AxisInfo.CHILD, elementTest), new AxisExpression(AxisInfo.DESCENDANT, test), false);
                                    ExpressionTool.copyLocationInfo(this, path);
                                    return path.typeCheck(visitor, contextItemType);
                                }
                            }
                        }
                    }
                }

                SchemaType contentType = ((NodeTest) contextType).getContentType();
                if (contentType == AnyType.getInstance()) {
                    // fast exit in non-schema-aware case
                    return this;
                }

                int targetfp = test.getFingerprint();

                if (contentType.isSimpleType()) {
                    if ((axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) &&
                            (kind == Type.ELEMENT || kind == Type.ATTRIBUTE || kind == Type.DOCUMENT)) {
                        env.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any " +
                                NodeKindTest.nodeKindName(kind) +
                                " nodes when starting at " +
                                (origin == Type.ATTRIBUTE ? "an attribute node" : getStartingNodeDescription(contentType)),
                                this);
                    } else if (axis == AxisInfo.CHILD && kind == Type.TEXT &&
                            (visitor.getParentExpression() instanceof Atomizer)) {
                        env.issueWarning("Selecting the text nodes of an element with simple content may give the " +
                                "wrong answer in the presence of comments or processing instructions. It is usually " +
                                "better to omit the '/text()' step", this);
                    } else if (axis == AxisInfo.ATTRIBUTE) {
                        Iterator extensions = config.getExtensionsOfType(contentType);
                        boolean found = false;
                        if (targetfp == -1) {
                            while (extensions.hasNext()) {
                                ComplexType extension = (ComplexType) extensions.next();
                                if (extension.allowsAttributes()) {
                                    found = true;
                                    break;
                                }
                            }
                        } else {
                            while (extensions.hasNext()) {
                                ComplexType extension = (ComplexType) extensions.next();
                                try {
                                    if (extension.getAttributeUseType(targetfp) != null) {
                                        found = true;
                                        break;
                                    }
                                } catch (SchemaException e) {
                                    // ignore the error
                                }
                            }
                        }
                        if (!found) {
                            env.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select " +
                                    (targetfp == -1 ?
                                            "any attribute nodes" :
                                            "an attribute node named " + getDiagnosticName(targetfp, env)) +
                                    " when starting at " + getStartingNodeDescription(contentType), this);
                            // Despite the warning, leave the expression unchanged. This is because
                            // we don't necessarily know about all extended types at compile time:
                            // in particular, we don't seal the XML Schema namespace to block extensions
                            // of built-in types
                        }
                    }
                } else if (((ComplexType) contentType).isSimpleContent() &&
                        (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) &&
                        (kind == Type.ELEMENT || kind == Type.DOCUMENT)) {
                    // We don't need to consider extended types here, because a type with complex content
                    // can never be defined as an extension of a type with simple content
                    env.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any " +
                            NodeKindTest.nodeKindName(kind) +
                            " nodes when starting at " +
                            getStartingNodeDescription(contentType) +
                            ", as this type requires simple content", this);
                    return Literal.makeEmptySequence();
                } else if (((ComplexType) contentType).isEmptyContent() &&
                        (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF)) {
                    for (Iterator iter = config.getExtensionsOfType(contentType); iter.hasNext(); ) {
                        ComplexType extension = (ComplexType) iter.next();
                        if (!extension.isEmptyContent()) {
                            return this;
                        }
                    }
                    env.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any" +
                            " nodes when starting at " +
                            getStartingNodeDescription(contentType) +
                            ", as this type requires empty content", this);
                    return Literal.makeEmptySequence();
                } else if (axis == AxisInfo.ATTRIBUTE) {
                    if (targetfp == -1) {
                        if (!((ComplexType) contentType).allowsAttributes()) {
                            env.issueWarning("The complex type " + contentType.getDescription() +
                                    " allows no attributes other than the standard attributes in the xsi namespace", this);
                        }
                    } else {
                        try {
                            SchemaType schemaType;
                            if (targetfp == StandardNames.XSI_TYPE) {
                                schemaType = BuiltInAtomicType.QNAME;
                            } else if (targetfp == StandardNames.XSI_SCHEMA_LOCATION) {
                                schemaType = BuiltInListType.ANY_URIS;
                            } else if (targetfp == StandardNames.XSI_NO_NAMESPACE_SCHEMA_LOCATION) {
                                schemaType = BuiltInAtomicType.ANY_URI;
                            } else if (targetfp == StandardNames.XSI_NIL) {
                                schemaType = BuiltInAtomicType.BOOLEAN;
                            } else {
                                schemaType = ((ComplexType) contentType).getAttributeUseType(targetfp);
                            }
                            if (schemaType == null) {
                                env.issueWarning("The complex type " + contentType.getDescription() +
                                        " does not allow an attribute named " + getDiagnosticName(targetfp, env), this);
                                return Literal.makeEmptySequence();
                            } else {
                                itemType = new CombinedNodeTest(
                                        test,
                                        Token.INTERSECT,
                                        new ContentTypeTest(Type.ATTRIBUTE, schemaType, env.getConfiguration(), false));
                            }
                        } catch (SchemaException e) {
                            // ignore the exception
                        }
                    }
                } else if (axis == AxisInfo.CHILD && kind == Type.ELEMENT) {
                    try {
                        int childElement = targetfp;
                        if (targetfp == -1) {
                            // select="child::*"
                            if (((ComplexType) contentType).containsElementWildcard()) {
                                return this;
                            }
                            IntHashSet children = new IntHashSet();
                            ((ComplexType) contentType).gatherAllPermittedChildren(children, false);
                            if (children.isEmpty()) {
                                env.issueWarning("The complex type " + contentType.getDescription() +
                                        " does not allow children", this);
                                return Literal.makeEmptySequence();
                            }
//                            if (children.contains(-1)) {
//                                return this;
//                            }
                            if (children.size() == 1) {
                                IntIterator iter = children.iterator();
                                if (iter.hasNext()) {
                                    childElement = iter.next();
                                }
                            } else {
                                return this;
                            }
                        }
                        SchemaType schemaType = ((ComplexType) contentType).getElementParticleType(childElement, true);
                        if (schemaType == null) {
                            env.issueWarning("The complex type " + contentType.getDescription() +
                                    " does not allow a child element named " + getDiagnosticName(childElement, env), this);
                            return Literal.makeEmptySequence();
                        } else {
                            itemType = new CombinedNodeTest(
                                    test,
                                    Token.INTERSECT,
                                    new ContentTypeTest(Type.ELEMENT, schemaType, env.getConfiguration(), true));
                            computedCardinality = ((ComplexType) contentType).getElementParticleCardinality(childElement, true);
                            visitor.resetStaticProperties();
                            if (computedCardinality == StaticProperty.ALLOWS_ZERO) {
                                // this shouldn't happen, because we've already checked for this a different way.
                                // but it's worth being safe (there was a bug involving an incorrect inference here)
                                env.issueWarning("The complex type " + contentType.getDescription() +
                                        " appears not to allow a child element named " + getDiagnosticName(childElement, env), this);
                                return Literal.makeEmptySequence();
                            }
                            if (!Cardinality.allowsMany(computedCardinality)) {
                                // if there can be at most one child of this name, create a FirstItemExpression
                                // to stop the search after the first one is found
                                return FirstItemExpression.makeFirstItemExpression(this);
                            }
                        }
                    } catch (SchemaException e) {
                        // ignore the exception
                    }
                } else if (axis == AxisInfo.DESCENDANT && kind == Type.ELEMENT && targetfp != -1) {
                    // when searching for a specific element on the descendant axis, try to produce a more
                    // specific path that avoids searching branches of the tree where the element cannot occur
                    try {
                        IntHashSet descendants = new IntHashSet();
                        ((ComplexType) contentType).gatherAllPermittedDescendants(descendants);
                        if (descendants.contains(-1)) {
                            return this;
                        }
                        if (descendants.contains(targetfp)) {
                            IntHashSet children = new IntHashSet();
                            ((ComplexType) contentType).gatherAllPermittedChildren(children, false);
                            IntHashSet usefulChildren = new IntHashSet();
                            boolean considerSelf = false;
                            boolean considerDescendants = false;
                            for (IntIterator child = children.iterator(); child.hasNext(); ) {
                                int c = child.next();
                                if (c == targetfp) {
                                    usefulChildren.add(c);
                                    considerSelf = true;
                                }
                                SchemaType st = ((ComplexType) contentType).getElementParticleType(c, true);
                                if (st == null) {
                                    throw new AssertionError("Can't find type for child element " + c);
                                }
                                if (st instanceof ComplexType) {
                                    IntHashSet subDescendants = new IntHashSet();
                                    ((ComplexType) st).gatherAllPermittedDescendants(subDescendants);
                                    if (subDescendants.contains(targetfp)) {
                                        usefulChildren.add(c);
                                        considerDescendants = true;
                                    }
                                }
                            }
                            itemType = test;
                            if (considerDescendants) {
                                SchemaType st = ((ComplexType) contentType).getDescendantElementType(targetfp);
                                if (st != AnyType.getInstance()) {
                                    itemType = new CombinedNodeTest(
                                            test, Token.INTERSECT,
                                            new ContentTypeTest(Type.ELEMENT, st, config, true));
                                }
                                //return this;
                            }
                            if (usefulChildren.size() < children.size()) {
                                NodeTest childTest = makeUnionNodeTest(usefulChildren, visitor.getConfiguration().getNamePool());
                                AxisExpression first = new AxisExpression(AxisInfo.CHILD, childTest);
                                ExpressionTool.copyLocationInfo(this, first);
                                byte nextAxis;
                                if (considerSelf) {
                                    nextAxis = (considerDescendants ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.SELF);
                                } else {
                                    nextAxis = AxisInfo.DESCENDANT;
                                }
                                AxisExpression next = new AxisExpression(nextAxis, (NodeTest) itemType);
                                ExpressionTool.copyLocationInfo(this, next);
                                Expression path = ExpressionTool.makePathExpression(first, next, false);
                                ExpressionTool.copyLocationInfo(this, path);
                                return path.typeCheck(visitor, contextItemType);
                            }
                        } else {
                            env.issueWarning("The complex type " + contentType.getDescription() +
                                    " does not allow a descendant element named " + getDiagnosticName(targetfp, env), this);
                        }
                    } catch (SchemaException e) {
                        throw new AssertionError(e);
                    }


                }
            }
        }

        return this;
    }

    /*
     * Get a string representation of a name to use in diagnostics
     */

    private static String getDiagnosticName(int fp, StaticContext env) {
        NamePool pool = env.getNamePool();
        String uri = pool.getURI(fp);
        NamespaceResolver resolver = env.getNamespaceResolver();
        for (Iterator<String> it = resolver.iteratePrefixes(); it.hasNext(); ) {
            String prefix = it.next();
            if (uri.equals(resolver.getURIForPrefix(prefix, true))) {
                if (prefix.length() == 0) {
                    return "{" + uri + "}" + pool.getLocalName(fp);
                } else {
                    return prefix + ":" + pool.getLocalName(fp);
                }
            }
        }
        return "{" + uri + "}" + pool.getLocalName(fp);
    }

    private static String getStartingNodeDescription(SchemaType type) {
        String s = type.getDescription();
        if (s.startsWith("of element")) {
            return "a valid element named" + s.substring("of element".length());
        } else if (s.startsWith("of attribute")) {
            return "a valid attribute named" + s.substring("of attribute".length());
        } else {
            return "a node with " + (type.isSimpleType() ? "simple" : "complex") + " type " + s;
        }
    }

    /**
     * Make a union node test for a set of supplied element fingerprints
     *
     * @param elements the set of integer element fingerprints to be tested for. Must not
     * be empty.
     * @param pool     the name pool
     * @return a NodeTest that returns true if the node is an element whose name is one of the names
     *         in this set
     */

    private NodeTest makeUnionNodeTest(IntHashSet elements, NamePool pool) {
        NodeTest test = null;
        for (IntIterator iter = elements.iterator(); iter.hasNext(); ) {
            int fp = iter.next();
            NodeTest nextTest = new NameTest(Type.ELEMENT, fp, pool);
            if (test == null) {
                test = nextTest;
            } else {
                test = new CombinedNodeTest(test, Token.UNION, nextTest);
            }
        }
        return test;
    }

    /**
     * Get the static type of the context item for this AxisExpression. May be null if not known.
     *
     * @return the statically-inferred type, or null if not known
     */

    public ItemType getContextItemType() {
        return contextItemType;
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     */

//    public Expression optimize(ExpressionVisitor visitor, ItemType contextItemType) {
//        return this;
//    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof AxisExpression)) {
            return false;
        }
        if (axis != ((AxisExpression) other).axis) {
            return false;
        }
        if (test == null) {
            return ((AxisExpression) other).test == null;
        }
        return test.toString().equals(((AxisExpression) other).test.toString());
    }

    /**
     * get HashCode for comparing two expressions
     */

    public int hashCode() {
        // generate an arbitrary hash code that depends on the axis and the node test
        int h = 9375162 + axis << 20;
        if (test != null) {
            h ^= test.getPrimitiveType() << 16;
            h ^= test.getFingerprint();
        }
        return h;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE
     */

//    public int getIntrinsicDependencies() {
//	    return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
//    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        AxisExpression a2 = new AxisExpression(axis, test);
        a2.itemType = itemType;
        a2.contextItemType = contextItemType;
        a2.computedCardinality = computedCardinality;
        a2.doneWarnings = doneWarnings;
        return a2;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        return StaticProperty.CONTEXT_DOCUMENT_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NON_CREATIVE |
                (AxisInfo.isForwards[axis] ? StaticProperty.ORDERED_NODESET : StaticProperty.REVERSE_DOCUMENT_ORDER) |
                (AxisInfo.isPeerAxis[axis] ? StaticProperty.PEER_NODESET : 0) |
                (AxisInfo.isSubtreeAxis[axis] ? StaticProperty.SUBTREE_NODESET : 0) |
                ((axis == AxisInfo.ATTRIBUTE || axis == AxisInfo.NAMESPACE) ? StaticProperty.ATTRIBUTE_NS_NODESET : 0);
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @param th the type hierarchy cache
     * @return Type.NODE or a subtype, based on the NodeTest in the axis step, plus
     *         information about the content type if this is known from schema analysis
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        if (itemType != null) {
            return itemType;
        }
        int p = AxisInfo.principalNodeType[axis];
        switch (p) {
            case Type.ATTRIBUTE:
            case Type.NAMESPACE:
                return NodeKindTest.makeNodeKindTest(p);
            default:
                if (test == null) {
                    return AnyNodeTest.getInstance();
                } else {
                    return test;
                }
        }
    }

    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     *         dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */
    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Determine the cardinality of the result of this expression
     */

    public final int computeCardinality() {
        if (computedCardinality != -1) {
            // This takes care of the case where cardinality was computed during type checking of the child axis
            return computedCardinality;
        }
        NodeTest originNodeType;
        NodeTest nodeTest = test;
        if (contextItemType instanceof NodeTest) {
            originNodeType = (NodeTest) contextItemType;
        } else if (contextItemType instanceof AnyItemType) {
            originNodeType = AnyNodeTest.getInstance();
        } else {
            // context item not a node - we'll report a type error somewhere along the line
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        if (axis == AxisInfo.ATTRIBUTE && nodeTest instanceof NameTest) {
            SchemaType contentType = originNodeType.getContentType();
            if (contentType instanceof ComplexType) {
                try {
                    return ((ComplexType) contentType).getAttributeUseCardinality(nodeTest.getFingerprint());
                } catch (SchemaException err) {
                    // shouldn't happen; play safe
                    return StaticProperty.ALLOWS_ZERO_OR_ONE;
                }
            } else if (contentType instanceof SimpleType) {
                return StaticProperty.EMPTY;
            }
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else if (axis == AxisInfo.DESCENDANT && nodeTest instanceof NameTest && nodeTest.getPrimitiveType() == Type.ELEMENT) {
            SchemaType contentType = originNodeType.getContentType();
            if (contentType instanceof ComplexType) {
                try {
                    return ((ComplexType) contentType).getDescendantElementCardinality(nodeTest.getFingerprint());
                } catch (SchemaException err) {
                    // shouldn't happen; play safe
                    return StaticProperty.ALLOWS_ZERO_OR_MORE;
                }
            } else {
                return StaticProperty.EMPTY;
            }

        } else if (axis == AxisInfo.SELF) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        // the parent axis isn't handled by this class
    }

    /**
     * Determine whether the expression can be evaluated without reference to the part of the context
     * document outside the subtree rooted at the context node.
     *
     * @return true if the expression has no dependencies on the context node, or if the only dependencies
     *         on the context node are downward selections using the self, child, descendant, attribute, and namespace
     *         axes.
     */

    public boolean isSubtreeExpression() {
        return AxisInfo.isSubtreeAxis[axis];
    }

    /**
     * Get the axis
     *
     * @return the axis number, for example {@link net.sf.saxon.om.AxisInfo#CHILD}
     */

    public byte getAxis() {
        return axis;
    }

    /**
     * Get the NodeTest. Returns null if the AxisExpression can return any node.
     *
     * @return the node test, or null if all nodes are returned
     */

    public NodeTest getNodeTest() {
        return test;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (pathMapNodeSet == null) {
            ContextItemExpression cie = new ContextItemExpression();
            cie.setContainer(getContainer());
            pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
        }
        return pathMapNodeSet.createArc(axis, (test == null ? AnyNodeTest.getInstance() : test));
    }

    /**
     * Ask whether there is a possibility that the context item will be undefined
     *
     * @return true if this is a possibility
     */

    public boolean isContextPossiblyUndefined() {
        return contextMaybeUndefined;
    }

    

    private void addFreeRangingMessage(int axis, List<String> reasons) {
        if (reasons != null) {
            reasons.add("The " + AxisInfo.axisName[axis] + " axis in a general context is free-ranging (used at line " + getLineNumber() + ")");
        }
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @param is30   true if this is XSLT 3.0
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean is30) throws XPathException {

        NodeTest test = getNodeTest();

        if (test == null) {
            test = AnyNodeTest.getInstance();
        }
        if (test instanceof AnyNodeTest && (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT)) {
            test = AnyChildNodeTest.getInstance();
        }
        int kind = test.getPrimitiveType();
        if (axis == AxisInfo.SELF) {
            return new ItemTypePattern(test);
        } else if (axis == AxisInfo.ATTRIBUTE) {
            if (kind == Type.NODE) {
                // attribute::node() matches any attribute, and only an attribute
                return new ItemTypePattern(NodeKindTest.ATTRIBUTE);
            } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                // for example, attribute::comment()
                return new ItemTypePattern(ErrorType.getInstance());
            } else {
                return new ItemTypePattern(test);
            }
        } else if (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) {
            if (kind != Type.NODE && !AxisInfo.containsNodeKind(axis, kind)) {
                return new ItemTypePattern(ErrorType.getInstance());
            }
            return new ItemTypePattern(test);
        } else if (axis == AxisInfo.NAMESPACE) {
            if (kind == Type.NODE) {
                // namespace::node() matches any attribute, and only an attribute
                return new ItemTypePattern(NodeKindTest.NAMESPACE);
            } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                // for example, namespace::comment()
                return new ItemTypePattern(ErrorType.getInstance());
            } else {
                return new ItemTypePattern(test);
            }
        } else {
            throw new XPathException("Only downwards axes are allowed in a pattern", "XTSE0340");
        }
        // TODO: //A only matches an A element in a tree rooted at a document
    }

//#ifdefined STREAM

    /**
     * Convert this expression to a streaming pattern (a pattern used internally to match nodes during
     * push processing of an event stream)
     *
     * @param config           the Saxon configuration
     * @param reasonForFailure a list which will be populated with messages giving reasons why the
     *                         expression cannot be converted
     * @return the equivalent pattern if conversion succeeds; otherwise null
     */
    @Override
    public Pattern toStreamingPattern(Configuration config, List<String> reasonForFailure) {
        SlashExpression s = new SlashExpression(new ContextItemExpression(), this);
        //SlashExpression s = new SlashExpression(new AxisExpression(AxisInfo.SELF, AnyNodeTest.getInstance()), this);
        try {
            return s.toPattern(config, true);
        } catch (XPathException e) {
            reasonForFailure.add(e.getMessage());
            return null;
        }
    }

//#endif

    /**
     * Evaluate the path-expression in a given context to return a NodeSet
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    public AxisIterator<? extends NodeInfo> iterate(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            // Might as well do the test anyway, whether or not contextMaybeUndefined is set
            NamePool pool;
            try {
                pool = context.getConfiguration().getNamePool();
            } catch (Exception err) {
                pool = null;
            }
            XPathException err = new XPathException("The context item for axis step " +
                    (pool == null ? toString() : toString(pool)) + " is absent");
            err.setErrorCode("XPDY0002");
            err.setXPathContext(context);
            err.setLocator(this);
            err.setIsTypeError(true);
            throw err;
        }
        try {
            if (test == null) {
                return ((NodeInfo) item).iterateAxis(axis);
            } else {
                return ((NodeInfo) item).iterateAxis(axis, test);
            }
        } catch (ClassCastException cce) {
            NamePool pool;
            try {
                pool = context.getConfiguration().getNamePool();
            } catch (Exception err) {
                pool = null;
            }
            XPathException err = new XPathException("The context item for axis step " +
                    (pool == null ? toString() : toString(pool)) + " is not a node");
            err.setErrorCode("XPTY0020");
            err.setXPathContext(context);
            err.setLocator(this);
            err.setIsTypeError(true);
            throw err;
        } catch (UnsupportedOperationException err) {
            if (err.getCause() instanceof XPathException) {
                XPathException ec = (XPathException) err.getCause();
                ec.maybeSetLocation(this);
                ec.maybeSetContext(context);
                throw ec;
            } else {
                // the namespace axis is not supported for all tree implementations
                dynamicError(err.getMessage(), "XPST0010", context);
                return null;
            }
        }
    }

    /**
     * Iterate the axis from a given starting node, without regard to context
     *
     * @param origin the starting node
     * @return the iterator over the axis
     */

    public SequenceIterator iterate(Item origin) throws XPathException {
        try {
            if (test == null) {
                return ((NodeInfo) origin).iterateAxis(axis);
            } else {
                return ((NodeInfo) origin).iterateAxis(axis, test);
            }
        } catch (ClassCastException cce) {
            XPathException err = new XPathException("The context item for axis step " +
                    toString() + " is not a node");
            err.setErrorCode("XPTY0020");
            err.setLocator(this);
            err.setIsTypeError(true);
            throw err;
        }
    }

    

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("axis");
        destination.emitAttribute("name", AxisInfo.axisName[axis]);
        destination.emitAttribute("nodeTest", (test == null ? "node()" : test.toString()));
        destination.endElement();
    }

    /**
     * Represent the expression as a string. The resulting string will be a valid XPath 3.0 expression
     * with no dependencies on namespace bindings other than the binding of the prefix "xs" to the XML Schema
     * namespace.
     *
     * @return the expression as a string in XPath 3.0 syntax
     */

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.TINY);
        fsb.append(AxisInfo.axisName[axis]);
        fsb.append("::");
        fsb.append(test == null ? "node()" : test.toString());
        return fsb.toString();
    }

    /**
     * Represent the expression as a string for diagnostics
     *
     * @param pool the name pool, used for expanding names in the node test
     * @return a string representation of the expression
     */

    public String toString(NamePool pool) {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.TINY);
        fsb.append(AxisInfo.axisName[axis]);
        fsb.append("::");
        fsb.append(test == null ? "node()" : test.toString());
        return fsb.toString();
    }
}

