////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.evpull.BlockEventIterator;
import net.sf.saxon.evpull.EmptyEventIterator;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerRange;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;



/**
* An expression that delivers the concatenation of the results of its subexpressions. This may
 * represent an XSLT sequence constructor, or an XPath/XQuery expression of the form (a,b,c,d).
*/

public class Block extends Instruction {

    // TODO: allow the last expression in a Block to be a tail-call of a function, at least in push mode

    private Expression[] children;
    private boolean allNodesUntyped;

    /**
     * Create an empty block
     */

    public Block() {
    }

    /**
     * Static factory method to create a block. If one of the arguments is already a block,
     * the contents will be merged into a new composite block
     * @param e1 the first subexpression (child) of the block
     * @param e2 the second subexpression (child) of the block
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     * have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(/*@Nullable*/ Expression e1, Expression e2) {
        if (e1==null || Literal.isEmptySequence(e1)) {
            return e2;
        }
        if (e2==null || Literal.isEmptySequence(e2)) {
            return e1;
        }
        if (e1 instanceof Block || e2 instanceof Block) {
            Iterator<Expression> it1 = (e1 instanceof Block ? e1.iterateSubExpressions() : new MonoIterator<Expression>(e1));
            Iterator<Expression> it2 = (e2 instanceof Block ? e2.iterateSubExpressions() : new MonoIterator<Expression>(e2));
            List<Expression> list = new ArrayList<Expression>(10);
            while (it1.hasNext()) {
                list.add(it1.next());
            }
            while (it2.hasNext()) {
                list.add(it2.next());
            }
            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            Block b = new Block();
            b.setChildren(exps);
            return b;
        } else {
            Expression[] exps = {e1, e2};
            Block b = new Block();
            b.setChildren(exps);
            return b;
        }
    }

    /**
     * Static factory method to create a block from a list of expressions
     * @param list the list of expressions making up this block. The members of the List must
     * be instances of Expression
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     * have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(List<Expression> list) {
        if (list.size() == 0) {
            return Literal.makeEmptySequence();
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            Block b = new Block();
            b.setChildren(exps);
            return b;
        }
    }


    /**
    * Set the children of this instruction
    * @param children The instructions that are children of this instruction
    */

    public void setChildren(Expression[] children) {
        this.children = children;
        for (Expression aChildren : children) {
            adoptChildExpression(aChildren);
        }
    }

    public String getExpressionName() {
        return "sequence";
    }

    /**
    * Get the children of this instruction
    * @return the children of this instruction, as an array of Instruction objects. May return
     * a zero-length array if there are no children
    */

    public Expression[] getChildren() {
        return children;
    }


    public int computeSpecialProperties() {
        if (children.length == 0) {
            // An empty sequence has all special properties except "has side effects".
            return StaticProperty.SPECIAL_PROPERTY_MASK &~ StaticProperty.HAS_SIDE_EFFECTS;
        }
        int p = super.computeSpecialProperties();
        if (allNodesUntyped) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        // if all the expressions are axis expressions, we have a same-document node-set
        boolean allAxisExpressions = true;
        boolean allChildAxis = true;
        boolean allSubtreeAxis = true;
        for (Expression child : children) {
            if (!(child instanceof AxisExpression)) {
                allAxisExpressions = false;
                allChildAxis = false;
                allSubtreeAxis = false;
                break;
            }
            byte axis = ((AxisExpression) child).getAxis();
            if (axis != AxisInfo.CHILD) {
                allChildAxis = false;
            }
            if (!AxisInfo.isSubtreeAxis[axis]) {
                allSubtreeAxis = false;
            }
        }
        if (allAxisExpressions) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET |
               StaticProperty.SINGLE_DOCUMENT_NODESET |
               StaticProperty.NON_CREATIVE;
            // if they all use the child axis, then we have a peer node-set
            if (allChildAxis) {
                p |= StaticProperty.PEER_NODESET;
            }
            if (allSubtreeAxis) {
                p |= StaticProperty.SUBTREE_NODESET;
            }
            // special case: selecting attributes then children, node-set is sorted
            if (children.length == 2 &&
                    ((AxisExpression)children[0]).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression)children[1]).getAxis() == AxisInfo.CHILD) {
                p |= StaticProperty.ORDERED_NODESET;
            }
        }
        return p;
    }

    /**
     * Determine whether the block includes any instructions that might return nodes with a type annotation
     * @param th the type hierarchy cache
     * @return true if any expression in the block can return type-annotated nodes
     */

    private boolean mayReturnTypedNodes(TypeHierarchy th) {
        for (Expression exp : children) {
            if ((exp.getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED) == 0) {
                ItemType it = exp.getItemType(th);
                if (th.relationship(it, NodeKindTest.ELEMENT) != TypeHierarchy.DISJOINT ||
                        th.relationship(it, NodeKindTest.ATTRIBUTE) != TypeHierarchy.DISJOINT) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Merge any adjacent instructions that create literal text nodes
     * @return the expression after merging literal text instructions
     */

    public Expression mergeAdjacentTextInstructions() {
        boolean[] isLiteralText = new boolean[children.length];
        boolean hasAdjacentTextNodes = false;
        for (int i=0; i<children.length; i++) {
            isLiteralText[i] = children[i] instanceof ValueOf &&
                    ((ValueOf)children[i]).getContentExpression() instanceof StringLiteral &&
                    !((ValueOf)children[i]).isDisableOutputEscaping();

            if (i > 0 && isLiteralText[i] && isLiteralText[i-1]) {
                hasAdjacentTextNodes = true;
            }
        }
        if (hasAdjacentTextNodes) {
            List<Expression> content = new ArrayList<Expression>(children.length);
            String pendingText = null;
            for (int i=0; i<children.length; i++) {
                if (isLiteralText[i]) {
                    pendingText = (pendingText == null ? "" : pendingText) +
                                    ((StringLiteral)((ValueOf)children[i]).getContentExpression()).getStringValue();
                } else {
                    if (pendingText != null) {
                        ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                        content.add(inst);
                        pendingText = null;
                    }
                    content.add(children[i]);
                }
            }
            if (pendingText != null) {
                ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                content.add(inst);
            }
            return makeBlock(content);
        } else {
            return this;
        }
    }


    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return Arrays.asList(children).iterator();
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
        List<SubExpressionInfo> info = new ArrayList<SubExpressionInfo>(children.length);
        for (Expression child : children) {
            info.add(new SubExpressionInfo(child, true, false, INHERITED_CONTEXT));
        }
        return info.iterator();
    }

    /**
     * Test whether the Block includes a LocalParam instruction (which will be true only if it is the
     * body of an XSLT template)
     * @return true if the Block contains a LocalParam instruction
     */

//    public boolean containsLocalParam() {
//        return children.length > 0 && children[0] instanceof LocalParamSetter;
//    }    

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        for (int c=0; c<children.length; c++) {
            if (children[c] == original) {
                children[c] = replacement;
                found = true;
            }
        }
        return found;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Block b2 = new Block();
        Expression[] c2 = new Expression[children.length];
        b2.children = c2;
        for (int c=0; c<children.length; c++) {
            c2[c] = children[c].copy();
        }
        b2.children = c2;
        for (int c=0; c<children.length; c++) {
            b2.adoptChildExpression(c2[c]);
        }
        b2.allNodesUntyped = allNodesUntyped;
        ExpressionTool.copyLocationInfo(this, b2);
        return b2;
    }

    /**
     * Determine the data type of the items returned by this expression
     * @return the data type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        if (children.length==0) {
            return ErrorType.getInstance();
        }
        ItemType t1 = children[0].getItemType(th);
        for (int i=1; i<children.length; i++) {
            t1 = Type.getCommonSuperType(t1, children[i].getItemType(th), th);
            if (t1 instanceof AnyItemType) {
                return t1;  // no point going any further
            }
        }
        return t1;
    }

    /**
     * Determine the cardinality of the expression
     */

    public final int getCardinality() {
        if (children.length==0) {
            return StaticProperty.EMPTY;
        }
        int c1 = children[0].getCardinality();
        for (int i=1; i<children.length; i++) {
            c1 = Cardinality.sum(c1, children[i].getCardinality());
            if (c1 == StaticProperty.ALLOWS_ZERO_OR_MORE) {
                break;
            }
        }
        return c1;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if any child instruction
     * returns true.
     */

    public final boolean createsNewNodes() {
        for (Expression aChildren : children) {
            int props = aChildren.getSpecialProperties();
            if ((props & StaticProperty.NON_CREATIVE) == 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Check to ensure that this expression does not contain any updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    public void checkForUpdatingSubexpressions() throws XPathException {
        if (children.length < 2) {
            return;
        }
        boolean updating = false;
        boolean nonUpdating = false;
        for (Expression child : children) {
            if (!ExpressionTool.isAllowedInUpdatingContext(child)) {
                if (updating) {
                    XPathException err = new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001");
                    err.setLocator(child);
                    throw err;
                }
                nonUpdating = true;
            }
            if (child.isUpdatingExpression()) {
                if (nonUpdating) {
                    XPathException err = new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001");
                    err.setLocator(child);
                    throw err;
                }
                updating = true;
            }
        }
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     * @return true if this expression is vacuous
     */

    public boolean isVacuousExpression() {
        // true if all subexpressions are vacuous
        for (Expression child : children) {
            if (!child.isVacuousExpression()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @exception XPathException if an error is discovered during expression
     *     rewriting
     * @return the simplified expression
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        boolean allAtomic = true;
        boolean nested = false;

        for (int c=0; c<children.length; c++) {
            children[c] = visitor.simplify(children[c]);
            if (!Literal.isAtomic(children[c])) {
                allAtomic = false;
            }
            if (children[c] instanceof Block) {
                nested = true;
            } else if (Literal.isEmptySequence(children[c])) {
                nested = true;
            }
        }
        if (children.length == 1) {
            return getChildren()[0];
        }
        if (children.length == 0) {
            Expression result = Literal.makeEmptySequence();
            ExpressionTool.copyLocationInfo(this, result);
            return result;
        }
        if (nested) {
            List<Expression> list = new ArrayList<Expression>(children.length*2);
            flatten(list);
            children = new Expression[list.size()];
            for (int i=0; i<children.length; i++) {
                children[i] = list.get(i);
                adoptChildExpression(children[i]);
            }
        }
        if (allAtomic) {
            AtomicValue[] values = new AtomicValue[children.length];
            for (int c=0; c<children.length; c++) {
                values[c] = (AtomicValue)((Literal)children[c]).getValue();
            }
            Expression result = Literal.makeLiteral(new SequenceExtent<AtomicValue>(values));
            ExpressionTool.copyLocationInfo(this, result);
            return result;
        }

        return this;
    }

    /**
     * Simplify the contents of a Block by merging any nested blocks, merging adjacent
     * literals, and eliminating any empty sequences.
     * @param targetList the new list of expressions comprising the contents of the block
     * after simplification
     * @throws XPathException should not happen
     */

    private void flatten(List<Expression> targetList) throws XPathException {
        List<Item> currentLiteralList = null;
        for (Expression child : children) {
            if (Literal.isEmptySequence(child)) {
                // do nothing, omit it from the output
            } else if (child instanceof Block) {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                ((Block) child).flatten(targetList);
            } else if (child instanceof Literal && !(((Literal) child).getValue() instanceof IntegerRange)) {
                SequenceIterator iterator = ((Literal) child).getValue().iterate();
                if (currentLiteralList == null) {
                    currentLiteralList = new ArrayList<Item>(10);
                }
                while (true) {
                    Item item = iterator.next();
                    if (item == null) {
                        break;
                    }
                    currentLiteralList.add(item);
                }
                // no-op
            } else {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                targetList.add(child);
            }
        }
        flushCurrentLiteralList(currentLiteralList, targetList);
    }

    /**
     * Determine whether the block is a candidate for evaluation using a "shared append expression"
     * where the result of the evaluation is a sequence implemented as a list of subsequences
     */

    public boolean isCandidateForSharedAppend() {
        for (Expression exp : children) {
            if (exp instanceof VariableReference || exp instanceof Literal) {
                return true;
            }
        }
        return false;
    }

    private void flushCurrentLiteralList(List<Item> currentLiteralList, List<Expression> list) throws XPathException {
        if (currentLiteralList != null) {
            SequenceIterator<Item> iter = new net.sf.saxon.tree.iter.ListIterator<Item>(currentLiteralList);
            list.add(Literal.makeLiteral(SequenceExtent.makeSequenceExtent(iter)));
        }
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = visitor.typeCheck(children[c], contextItemType);
            adoptChildExpression(children[c]);
        }
        if (!mayReturnTypedNodes(visitor.getConfiguration().getTypeHierarchy())) {
            resetLocalStaticProperties();
            allNodesUntyped = true;
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = visitor.optimize(children[c], contextItemType);
            adoptChildExpression(children[c]);
        }
        boolean canSimplify = false;
        boolean prevLiteral = false;
        // Simplify the expression by collapsing nested blocks and merging adjacent literals
        for (Expression child : children) {
            if (child instanceof Block) {
                canSimplify = true;
                break;
            }
            if (child instanceof Literal) {
                if (prevLiteral || Literal.isEmptySequence(child)) {
                    canSimplify = true;
                    break;
                }
                prevLiteral = true;
            } else {
                prevLiteral = false;
            }
        }
        if (canSimplify) {
            List<Expression> list = new ArrayList<Expression>(children.length*2);
            flatten(list);
            children = new Expression[list.size()];
            for (int i=0; i<children.length; i++) {
                children[i] = list.get(i);
                adoptChildExpression(children[i]);
            }
        }
        if (children.length == 0) {
            return Literal.makeEmptySequence();
        } else if (children.length == 1) {
            return children[0];
        } else {
            return this;
        }
    }



    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = doPromotion(children[c], offer);
        }
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        for (Expression child : children) {
            child.checkPermittedContents(parentType, env, false);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("sequence");
        for (Expression child : children) {
            child.explain(out);
        }
        out.endElement();
    }


    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        TailCall tc = null;
        for (Expression child : children) {
            try {
                if (child instanceof TailCallReturner) {
                    tc = ((TailCallReturner) child).processLeavingTail(context);
                } else {
                    child.process(context);
                    tc = null;
                }
            } catch (XPathException e) {
                e.maybeSetLocation(child);
                e.maybeSetContext(context);
                throw e;
            }
        }
    	return tc;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Iterate over the results of all the child expressions
     */

    /*@NotNull*/
    public SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
        if (children.length == 0) {
            return EmptyIterator.emptyIterator();
        } else if (children.length == 1) {
            return children[0].iterate(context);
        } else {
            return new BlockIterator(children, context);
        }
    }

    /**
     * Get an EventIterator over the results of all the child expressions
     * @param context the XPath dynamic context
     * @return an EventIterator
     */

    public EventIterator iterateEvents(XPathContext context) throws XPathException {
        if (children.length == 0) {
            return EmptyEventIterator.getInstance();
        } else if (children.length == 1) {
            return children[0].iterateEvents(context);
        } else {
            return new BlockEventIterator(children, context);
        }
    }


    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        for (Expression child : children) {
            child.evaluatePendingUpdates(context, pul);
        }
    }


}