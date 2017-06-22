////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.CopyOf;
import net.sf.saxon.expr.instruct.ForEach;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;



/**
 * A slash expression is any expression using the binary slash operator "/". The parser initially generates a slash
 * expression for all occurrences of the binary "/" operator. Subsequently, as a result of type inferencing, the
 * majority of slash expressions will be rewritten as instances of PathExpression (returning nodes) or
 * ForEach instructions (when they return atomic values). However, in the rare case where it is not possible to determine
 * statically whether the rh operand returns nodes or atomic values, instances of this class may need to be interpreted
 * directly at run time.
 */

public class SlashExpression extends Expression
        implements ContextSwitchingExpression, ContextMappingFunction<Item> {

    Expression start;
    Expression step;

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of nodes).
     * @param step  The step to be followed from each node in the start expression to yield a new
     *              sequence; this may return either nodes or atomic values (but not a mixture of the two)
     */

    public SlashExpression(Expression start, Expression step) {
        this.start = start;
        this.step = step;
        adoptChildExpression(start);
        adoptChildExpression(step);
    }

    public void setStartExpression(Expression start2) {
        if (start != start2) {
            start = start2;
            adoptChildExpression(start);
        }
    }

    public void setStepExpression(Expression step2) {
        if (step != step2) {
            step = step2;
            adoptChildExpression(step);
        }
    }

    /**
     * Get the start expression (the left-hand operand)
     *
     * @return the first operand
     */

    public Expression getControllingExpression() {
        return start;
    }

    /**
     * Get the step expression (the right-hand operand)
     *
     * @return the second operand
     */

    public Expression getControlledExpression() {
        return step;
    }

    /**
     * Simplify an expression
     *
     * @param visitor the expression visitor
     * @return the simplified expression
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        setStartExpression(visitor.simplify(start));
        setStepExpression(visitor.simplify(step));
        return this;
    }

    /**
     * Determine the data type of the items returned by this exprssion
     *
     * @param th the type hierarchy cache
     * @return the type of the step
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        return step.getItemType(th);
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return step.getIntegerBounds();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        Expression start2 = visitor.typeCheck(start, contextItemType);

        // The first operand must be of type node()*

        RoleLocator role0 = new RoleLocator(RoleLocator.BINARY_EXPR, "/", 0);
        role0.setErrorCode("XPTY0019");
        setStartExpression(
                TypeChecker.staticTypeCheck(start2, SequenceType.NODE_SEQUENCE, false, role0, visitor));

        // Now check the second operand

        setStepExpression(visitor.typeCheck(step, new ExpressionVisitor.ContextItemType(start.getItemType(th), false)));

        // If the expression has the form (a//descendant-or-self::node())/b, try to simplify it to
        // use the descendant axis

        Expression e2 = simplifyDescendantPath(visitor.getStaticContext());
        if (e2 != null) {
            return e2.typeCheck(visitor, contextItemType);
        }

        if (start instanceof ContextItemExpression &&
                ((step.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0)) {
            return step;
        }

        if (step instanceof ContextItemExpression &&
                ((start.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0)) {
            return start;
        }

        return this;
    }

    // Simplify an expression of the form a//b, where b has no positional filters.
    // This comes out of the constructor above as (a/descendent-or-self::node())/child::b,
    // but it is equivalent to a/descendant::b; and the latter is better as it
    // doesn't require sorting. Note that we can't do this until type information is available,
    // as we need to know whether any filters are positional or not.

    private SlashExpression simplifyDescendantPath(StaticContext env) {

        Expression st = start;

        // detect .//x as a special case; this will appear as descendant-or-self::node()/x

        if (start instanceof AxisExpression) {
            AxisExpression stax = (AxisExpression) start;
            if (stax.getAxis() != AxisInfo.DESCENDANT_OR_SELF) {
                return null;
            }
            ContextItemExpression cie = new ContextItemExpression();
            ExpressionTool.copyLocationInfo(this, cie);
            st = ExpressionTool.makePathExpression(cie, stax, false);
            ExpressionTool.copyLocationInfo(this, st);
        }

        if (!(st instanceof SlashExpression)) {
            return null;
        }

        SlashExpression startPath = (SlashExpression) st;
        if (!(startPath.step instanceof AxisExpression)) {
            return null;
        }

        AxisExpression mid = (AxisExpression) startPath.step;
        if (mid.getAxis() != AxisInfo.DESCENDANT_OR_SELF) {
            return null;
        }


        NodeTest test = mid.getNodeTest();
        if (!(test == null || test instanceof AnyNodeTest)) {
            return null;
        }

        Expression underlyingStep = step;
        while (underlyingStep instanceof FilterExpression) {
            if (((FilterExpression) underlyingStep).isPositional(env.getConfiguration().getTypeHierarchy())) {
                return null;
            }
            underlyingStep = ((FilterExpression) underlyingStep).getControllingExpression();
        }

        if (!(underlyingStep instanceof AxisExpression)) {
            return null;
        }

        byte underlyingAxis = ((AxisExpression) underlyingStep).getAxis();
        if (underlyingAxis == AxisInfo.CHILD ||
                underlyingAxis == AxisInfo.DESCENDANT ||
                underlyingAxis == AxisInfo.DESCENDANT_OR_SELF) {
            byte newAxis = (underlyingAxis == AxisInfo.DESCENDANT_OR_SELF ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.DESCENDANT);
            Expression newStep =
                    new AxisExpression(newAxis,
                            ((AxisExpression) underlyingStep).getNodeTest());
            ExpressionTool.copyLocationInfo(this, newStep);

            underlyingStep = step;
            // Add any filters to the new expression. We know they aren't
            // positional, so the order of the filters doesn't technically matter
            // (XPath section 2.3.4 explicitly allows us to change it.)
            // However, in the interests of predictable execution, hand-optimization, and
            // diagnosable error behaviour, we retain the original order.
            Stack<Expression> filters = new Stack<Expression>();
            while (underlyingStep instanceof FilterExpression) {
                filters.add(((FilterExpression) underlyingStep).getFilter());
                underlyingStep = ((FilterExpression) underlyingStep).getControllingExpression();
            }
            while (!filters.isEmpty()) {
                newStep = new FilterExpression(newStep, filters.pop());
                ExpressionTool.copyLocationInfo(step, newStep);
            }

            //System.err.println("Simplified this:");
            //    display(10);
            //System.err.println("as this:");
            //    new PathExpression(startPath.start, newStep).display(10);

            Expression newPath = ExpressionTool.makePathExpression(startPath.start, newStep, false);
            if (!(newPath instanceof SlashExpression)) {
                return null;
            }
            ExpressionTool.copyLocationInfo(this, newPath);
            return (SlashExpression) newPath;
        }

        if (underlyingAxis == AxisInfo.ATTRIBUTE) {

            // turn the expression a//@b into a/descendant-or-self::*/@b

            Expression newStep =
                    new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, NodeKindTest.ELEMENT);
            ExpressionTool.copyLocationInfo(this, newStep);
            Expression e2 = ExpressionTool.makePathExpression(startPath.start, newStep, false);
            Expression e3 = ExpressionTool.makePathExpression(e2, step, false);
            if (!(e3 instanceof SlashExpression)) {
                return null;
            }
            ExpressionTool.copyLocationInfo(this, e3);
            return (SlashExpression) e3;
        }

        return null;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();

        setStartExpression(visitor.optimize(start, contextItemType));
        setStepExpression(step.optimize(visitor, new ExpressionVisitor.ContextItemType(start.getItemType(th), false)));

        if (Literal.isEmptySequence(start) || Literal.isEmptySequence(step)) {
            return Literal.makeEmptySequence();
        }

        if (start instanceof RootExpression && th.isSubType(contextItemType.itemType, NodeKindTest.DOCUMENT)) {
            // remove unnecessary leading "/" - helps streaming
            return step;
        }

        // Rewrite a/b[filter] as (a/b)[filter] to improve the chance of indexing

        Expression lastStep = getLastStep();
        if (lastStep instanceof FilterExpression && !((FilterExpression) lastStep).isPositional(th)) {
            Expression leading = getLeadingSteps();
            Expression p2 = ExpressionTool.makePathExpression(leading, ((FilterExpression) lastStep).getControllingExpression(), false);
            Expression f2 = new FilterExpression(p2, ((FilterExpression) lastStep).getFilter());
            return f2.optimize(visitor, contextItemType);
        }

        if (!visitor.isOptimizeForStreaming()) {
            Expression k = opt.convertPathExpressionToKey(this, visitor);
            if (k != null) {
                return k.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
            }
        }

        // Replace //x/y by descendant::y[parent::x] to eliminate the need for sorting
        // into document order, and to make the expression streamable

        if (start instanceof AxisExpression && ((AxisExpression)start).getAxis() == AxisInfo.DESCENDANT &&
                step instanceof AxisExpression && ((AxisExpression)step).getAxis() == AxisInfo.CHILD) {
            // TODO: we could be more ambitious and attempt this in the presence of non-positional filters;
            Expression k = new FilterExpression(
                    new AxisExpression(AxisInfo.DESCENDANT, ((AxisExpression)step).getNodeTest()),
                    new AxisExpression(AxisInfo.PARENT, ((AxisExpression)start).getNodeTest()));
            // If we're not starting at the root, ensure we go down at least one level
            if (!th.isSubType(contextItemType.itemType, NodeKindTest.DOCUMENT)) {
                k = new SlashExpression(new AxisExpression(AxisInfo.CHILD, NodeKindTest.ELEMENT), k);
            }
            opt.trace("Rewrote descendant::X/child::Y as descendant::Y[parent::X]", k);
            return k;
        }

        // Replace $x/child::abcd by a SimpleStepExpression, to avoid the need for creating
        // a new dynamic context at run-time.

        if (step instanceof AxisExpression && !Cardinality.allowsMany(start.getCardinality())) {
            SimpleStepExpression sse = new SimpleStepExpression(start, step);
            ExpressionTool.copyLocationInfo(this, sse);
            return sse;
        }

        Expression k = promoteFocusIndependentSubexpressions(visitor, contextItemType);
        if (k != this) {
            return k;
        }

        if (visitor.isOptimizeForStreaming()) {
            // rewrite a/copy-of(.) as copy-of(a)
            Expression rawStep = ExpressionTool.unfilteredExpression(step);
            if (rawStep instanceof CopyOf && ((CopyOf)rawStep).getSelectExpression() instanceof ContextItemExpression) {
                ((CopyOf)rawStep).setSelectExpression(start);
                rawStep.resetLocalStaticProperties();
                step.resetLocalStaticProperties();
                return step;
            }
        }

        return this;
    }

    /**
     * Test whether a path expression is an absolute path - that is, a path whose first step selects a
     * document node; if not, see if it can be converted to an absolute path. This is possible in cases where
     * the path expression has the form a/b/c and it is known that the context item is a document node; in this
     * case it is safe to change the path expression to /a/b/c
     *
     * @param th the type hierarchy cache
     * @return the path expression if it is absolute; the converted path expression if it can be made absolute;
     *         or null if neither condition applies.
     */

    public SlashExpression tryToMakeAbsolute(TypeHierarchy th) {
        Expression first = getFirstStep();
        if (first.getItemType(th).getPrimitiveType() == Type.DOCUMENT) {
            return this;
        }
        if (first instanceof AxisExpression) {
            // This second test allows keys to be built. See XMark q9.
            ItemType contextItemType = ((AxisExpression) first).getContextItemType();
            if (contextItemType != null && contextItemType.getPrimitiveType() == Type.DOCUMENT) {
                RootExpression root = new RootExpression();
                ExpressionTool.copyLocationInfo(this, root);
                Expression path = ExpressionTool.makePathExpression(root, this, false);
                ExpressionTool.copyLocationInfo(this, path);
                return (SlashExpression) path;
            }
        }
        if (first instanceof DocumentSorter && ((DocumentSorter) first).getBaseExpression() instanceof SlashExpression) {
            // see test case filter-001 in xqts-extra
            SlashExpression se = (SlashExpression) ((DocumentSorter) first).getBaseExpression();
            SlashExpression se2 = se.tryToMakeAbsolute(th);
            if (se2 != null) {
                if (se2 == se) {
                    return this;
                } else {
                    Expression rest = getRemainingSteps();
                    DocumentSorter ds = new DocumentSorter(se2);
                    return new SlashExpression(ds, rest);
                }
            }
        }
        return null;
    }


    /**
     * If any subexpressions within the step are not dependent on the focus,
     * and if they are not "creative" expressions (expressions that can create new nodes), then
     * promote them: this causes them to be evaluated once, outside the path expression
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item for evaluating the start expression
     * @return the rewritten expression, or the original expression if no rewrite was possible
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is detected
     */

    protected Expression promoteFocusIndependentSubexpressions(
            ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {

        Optimizer opt = visitor.getConfiguration().obtainOptimizer();

        PromotionOffer offer = new PromotionOffer(opt);
        offer.action = PromotionOffer.FOCUS_INDEPENDENT;
        offer.promoteDocumentDependent = (start.getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
        offer.containingExpression = this;

        setStepExpression(doPromotion(step, offer));
        visitor.resetStaticProperties();
        if (offer.containingExpression != this) {
            offer.containingExpression =
                    visitor.optimize(visitor.typeCheck(offer.containingExpression, contextItemType), contextItemType);
            return offer.containingExpression;
        }
        return this;
    }

    /**
     * Promote this expression if possible
     */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        Expression exp = offer.accept(parent, this);
        if (exp != null) {
            return exp;
        } else {
            setStartExpression(doPromotion(start, offer));
            if (offer.action == PromotionOffer.INLINE_VARIABLE_REFERENCES ||
                    offer.action == PromotionOffer.REPLACE_CURRENT) {
                // Don't pass on other requests. We could pass them on, but only after augmenting
                // them to say we are interested in subexpressions that don't depend on either the
                // outer context or the inner context.
                setStepExpression(doPromotion(step, offer));
            }
            return this;
        }
    }

    /**
     * Get the immediate subexpressions of this expression
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new PairIterator<Expression>(start, step);
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
        SubExpressionInfo selectInfo = new SubExpressionInfo(start, true, false, INSPECTION_CONTEXT);
        SubExpressionInfo actionInfo = new SubExpressionInfo(step, false, true, INHERITED_CONTEXT);
        return new PairIterator<SubExpressionInfo>(selectInfo, actionInfo);

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
        if (start == original) {
            setStartExpression(replacement);
            found = true;
        }
        if (step == original) {
            setStepExpression(replacement);
            found = true;
        }
        return found;
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
        PathMap.PathMapNodeSet target = start.addToPathMap(pathMap, pathMapNodeSet);
        return step.addToPathMap(pathMap, target);
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE
     */

    public int computeDependencies() {
        return start.getDependencies() |
                // not all dependencies in the step matter, because the context node, etc,
                // are not those of the outer expression
                (step.getDependencies() &
                        (StaticProperty.DEPENDS_ON_XSLT_CONTEXT |
                                StaticProperty.DEPENDS_ON_LOCAL_VARIABLES |
                                StaticProperty.DEPENDS_ON_USER_FUNCTIONS));
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return ExpressionTool.makePathExpression(start.copy(), step.copy(), false);
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        int startProperties = start.getSpecialProperties();
        int stepProperties = step.getSpecialProperties();

        int p = 0;
        if (!Cardinality.allowsMany(start.getCardinality())) {
            startProperties |= StaticProperty.ORDERED_NODESET |
                    StaticProperty.PEER_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        if (!Cardinality.allowsMany(step.getCardinality())) {
            stepProperties |= StaticProperty.ORDERED_NODESET |
                    StaticProperty.PEER_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET;
        }

        if ((startProperties & stepProperties & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        if (((startProperties & StaticProperty.SINGLE_DOCUMENT_NODESET) != 0) &&
                ((stepProperties & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0)) {
            p |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        if ((startProperties & stepProperties & StaticProperty.PEER_NODESET) != 0) {
            p |= StaticProperty.PEER_NODESET;
        }
        if ((startProperties & stepProperties & StaticProperty.SUBTREE_NODESET) != 0) {
            p |= StaticProperty.SUBTREE_NODESET;
        }

        if (testNaturallySorted(startProperties, stepProperties)) {
            p |= StaticProperty.ORDERED_NODESET;
        }

        if (testNaturallyReverseSorted()) {
            p |= StaticProperty.REVERSE_DOCUMENT_ORDER;
        }

        if ((startProperties & stepProperties & StaticProperty.NON_CREATIVE) != 0) {
            p |= StaticProperty.NON_CREATIVE;
        }

        return p;
    }

    /**
     * Determine if we can guarantee that the nodes are delivered in document order.
     * This is true if the start nodes are sorted peer nodes
     * and the step is based on an Axis within the subtree rooted at each node.
     * It is also true if the start is a singleton node and the axis is sorted.
     *
     * @param startProperties the properties of the left-hand expression
     * @param stepProperties  the properties of the right-hand expression
     * @return true if the natural nested-loop evaluation strategy for the expression
     *         is known to deliver results with no duplicates and in document order, that is,
     *         if no additional sort is required
     */

    private boolean testNaturallySorted(int startProperties, int stepProperties) {

        // System.err.println("**** Testing pathExpression.isNaturallySorted()");
        // display(20);
        // System.err.println("Start is ordered node-set? " + start.isOrderedNodeSet());
        // System.err.println("Start is naturally sorted? " + start.isNaturallySorted());
        // System.err.println("Start is singleton? " + start.isSingleton());

        if ((stepProperties & StaticProperty.ORDERED_NODESET) == 0) {
            return false;
        }
        if (Cardinality.allowsMany(start.getCardinality())) {
            if ((startProperties & StaticProperty.ORDERED_NODESET) == 0) {
                return false;
            }
        } else {
            //if ((stepProperties & StaticProperty.ORDERED_NODESET) != 0) {
            return true;
            //}
        }

        // We know now that both the start and the step are sorted. But this does
        // not necessarily mean that the combination is sorted.

        // The result is sorted if the start is sorted and the step selects attributes
        // or namespaces

        if ((stepProperties & StaticProperty.ATTRIBUTE_NS_NODESET) != 0) {
            return true;
        }

        // The result is sorted if the step is creative (e.g. a call to copy-of())

        if ((stepProperties & StaticProperty.NON_CREATIVE) == 0) {
            return true;
        }

        // The result is sorted if the start selects "peer nodes" (that is, a node-set in which
        // no node is an ancestor of another) and the step selects within the subtree rooted
        // at the context node

        return ((startProperties & StaticProperty.PEER_NODESET) != 0) &&
                ((stepProperties & StaticProperty.SUBTREE_NODESET) != 0);

    }

    /**
     * Determine if the path expression naturally returns nodes in reverse document order
     *
     * @return true if the natural nested-loop evaluation strategy returns nodes in reverse
     *         document order
     */

    private boolean testNaturallyReverseSorted() {

        // Some examples of path expressions that are naturally reverse sorted:
        //     ancestor::*/@x
        //     ../preceding-sibling::x
        //     $x[1]/preceding-sibling::node()

        // This information is used to do a simple reversal of the nodes
        // instead of a full sort, which is significantly cheaper, especially
        // when using tree models (such as DOM and JDOM) in which comparing
        // nodes in document order is an expensive operation.


        if (!Cardinality.allowsMany(start.getCardinality()) &&
                (step instanceof AxisExpression)) {
            return !AxisInfo.isForwards[((AxisExpression) step).getAxis()];
        }

        return !Cardinality.allowsMany(step.getCardinality()) &&
                (start instanceof AxisExpression) &&
                !AxisInfo.isForwards[((AxisExpression) start).getAxis()];

    }


    /**
     * Determine the static cardinality of the expression
     */

    public int computeCardinality() {
        int c1 = start.getCardinality();
        int c2 = step.getCardinality();
        return Cardinality.multiply(c1, c2);
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
        Expression head = getLeadingSteps();
        Expression tail = getLastStep();
        if (head instanceof ItemChecker) {
            // No need to typecheck the context item
            ItemChecker checker = (ItemChecker) head;
            if (checker.getBaseExpression() instanceof ContextItemExpression) {
                return tail.toPattern(config, is30);
            }
        }
        Pattern tailPattern = tail.toPattern(config, is30);
        if (tailPattern instanceof ItemTypePattern) {
            if (tailPattern.getItemType() instanceof ErrorType) {
                return tailPattern;
            }
        }

        byte axis = AxisInfo.PARENT;
        Pattern headPattern = null;
        if (head instanceof SlashExpression) {
            SlashExpression start = (SlashExpression) head;
            if (start.getControlledExpression() instanceof AxisExpression) {
                AxisExpression mid = (AxisExpression) start.getControlledExpression();
                if (mid.getAxis() == AxisInfo.DESCENDANT_OR_SELF &&
                        (mid.getNodeTest() == null || mid.getNodeTest() instanceof AnyNodeTest)) {
                    axis = AxisInfo.ANCESTOR;
                    headPattern = start.getControllingExpression().toPattern(config, is30);
                }
            }
        }
        if (headPattern == null) {
            axis = PatternMaker.getAxisForPathStep(tail);
            headPattern = head.toPattern(config, is30);
        }
        return new AncestorQualifiedPattern(tailPattern, headPattern, axis);
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof SlashExpression)) {
            return false;
        }
        SlashExpression p = (SlashExpression) other;
        return (start.equals(p.start) && step.equals(p.step));
    }

    /**
     * get HashCode for comparing two expressions
     */

    public int hashCode() {
        return "SlashExpression".hashCode() + start.hashCode() + step.hashCode();
    }

    /**
     * Iterate the path-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    public SequenceIterator<? extends Item> iterate(final XPathContext context) throws XPathException {

        // This class delivers the result of the path expression in unsorted order,
        // without removal of duplicates. If sorting and deduplication are needed,
        // this is achieved by wrapping the path expression in a DocumentSorter

        SequenceIterator result = start.iterate(context);
        XPathContext context2 = context.newMinorContext();
        context2.setCurrentIterator(result);
        return new ContextMappingIterator<Item>(this, context2);
    }

    /**
     * Mapping function, from a node returned by the start iteration, to a sequence
     * returned by the child.
     */

    public SequenceIterator map(XPathContext context) throws XPathException {
        return step.iterate(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("slash");
        if (this instanceof SimpleStepExpression) {
            destination.emitAttribute("simple-step", "true");
        }
        start.explain(destination);
        step.explain(destination);
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return ExpressionTool.parenthesize(start) + "/" + ExpressionTool.parenthesize(step);
    }

    /**
     * Get the first step in this expression. A path expression A/B/C is represented as (A/B)/C, but
     * the first step is A
     *
     * @return the first step in the expression, after expanding any nested path expressions
     */

    public Expression getFirstStep() {
        if (start instanceof SlashExpression) {
            return ((SlashExpression) start).getFirstStep();
        } else {
            return start;
        }
    }

    /**
     * Get step of the path expression
     *
     * @return the step expression
     */

    public Expression getStep() {
        return step;
    }

    /**
     * Get all steps after the first.
     * This is complicated by the fact that A/B/C is represented as ((A/B)/C; we are required
     * to return B/C
     *
     * @return a path expression containing all steps in this path expression other than the first,
     *         after expanding any nested path expressions
     */

    public Expression getRemainingSteps() {
        if (start instanceof SlashExpression) {
            SlashExpression rem =
                    new SlashExpression(((SlashExpression) start).getRemainingSteps(), step);
            ExpressionTool.copyLocationInfo(start, rem);
            return rem;
        } else {
            return step;
        }
    }

    /**
     * Get the last step of the path expression
     *
     * @return the last step in the expression, after expanding any nested path expressions
     */

    public Expression getLastStep() {
        if (step instanceof SlashExpression) {
            return ((SlashExpression) step).getLastStep();
        } else {
            return step;
        }
    }

    /**
     * Get a path expression consisting of all steps except the last
     *
     * @return a path expression containing all steps in this path expression other than the last,
     *         after expanding any nested path expressions
     */

    public Expression getLeadingSteps() {
        if (step instanceof SlashExpression) {
            SlashExpression rem =
                    new SlashExpression(start, ((SlashExpression) step).getLeadingSteps());
            ExpressionTool.copyLocationInfo(start, rem);
            return rem;
        } else {
            return start;
        }
    }

    /**
     * Test whether a path expression is an absolute path - that is, a path whose first step selects a
     * document node
     *
     * @param th the type hierarchy cache
     * @return true if the first step in this path expression selects a document node
     */

    public boolean isAbsolute(TypeHierarchy th) {
        Expression first = getFirstStep();
        if (first.getItemType(th).getPrimitiveType() == Type.DOCUMENT) {
            return true;
        }
        // This second test allows keys to be built. See XMark q9.
//        if (first instanceof AxisExpression && ((AxisExpression)first).getContextItemType().getPrimitiveType() == Type.DOCUMENT) {
//            return true;
//        };
        return false;
    }




}

