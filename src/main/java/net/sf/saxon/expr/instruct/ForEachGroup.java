////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.CollatingFunction;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.PatternSponsor;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Handler for xsl:for-each-group elements in stylesheet. This is a new instruction
 * defined in XSLT 2.0
 */

public class ForEachGroup extends Instruction
        implements SortKeyEvaluator, ContextMappingFunction {

    public static final int GROUP_BY = 0;
    public static final int GROUP_ADJACENT = 1;
    public static final int GROUP_STARTING = 2;
    public static final int GROUP_ENDING = 3;

    private Expression select;
    private Expression action;
    private byte algorithm;
    private Expression key;     // for group-starting and group-ending, this is a PatternSponsor
    private Expression collationNameExpression;
    private int keyItemType;
    private URI baseURI;
    private StringCollator collator = null;             // collation used for the grouping comparisons
    private SortKeyDefinition[] sortKeyDefinitions = null;
    private transient AtomicComparer[] sortComparators = null;    // comparators used for sorting the groups
    private LocalVariableBinding groupBinding = null;
    private LocalVariableBinding keyBinding = null;
    private boolean composite = false;

    /**
     * Create a for-each-group instruction
     * @param select the select expression (selects the population to be grouped)
     * @param action the body of the for-each-group (applied to each group in turn)
     * @param algorithm one of group-by, group-adjacent, group-starting-with, group-ending-with
     * @param key expression to evaluate the grouping key
     * @param collator user for comparing strings
     * @param collationNameExpression expression that yields the name of the collation to be used
     * @param baseURI static base URI of the expression
     * @param sortKeys list of xsl:sort keys for sorting the groups
     */

    public ForEachGroup(Expression select,
                        Expression action,
                        byte algorithm,
                        Expression key,
                        StringCollator collator,
                        Expression collationNameExpression,
                        URI baseURI,
                        SortKeyDefinition[] sortKeys) {
        this.select = select;
        this.action = action;
        this.algorithm = algorithm;
        this.key = key;
        this.collator = collator;
        this.collationNameExpression = collationNameExpression;
        this.baseURI = baseURI;
        this.sortKeyDefinitions = sortKeys;
        Iterator kids = iterateSubExpressions();
        while (kids.hasNext()) {
            Expression child = (Expression)kids.next();
            adoptChildExpression(child);
        }
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * @return the name of the instruction
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_FOR_EACH_GROUP;
    }

    /**
     * Get the select expression
     * @return the select expression
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Get the action expression (the content of the for-each-group)
     * @return the body of the xsl:for-each-group instruction
     */

    public Expression getActionExpression() {
        return action;
    }

    /**
     * Get the grouping algorithm (one of group-by, group-adjacent, group-starting-with, group-ending-with)
     * @return one of group-by, group-adjacent, group-starting-with, group-ending-with
     */

    public byte getAlgorithm() {
        return algorithm;
    }

    /**
     * Get the grouping key expression expression (the group-by or group-adjacent expression, or a
     * PatternSponsor containing the group-starting-with or group-ending-with expression)
     * @return the expression used to calculate grouping keys
     */

    public Expression getGroupingKey() {
        return key;
    }

    /**
     * Get the primitive item type of the key
     * @return the primitive item type of the grouping key
     */

    public int getKeyItemType() {
        return keyItemType;
    }

    /**
     * Get the sort keys defined at the for-each-group level, that is, the keys for sorting the groups
     * @return the definitions of the sort keys defined as children of the xsl:for-each-group element
     */

    public SortKeyDefinition[] getSortKeyDefinitions() {
        return sortKeyDefinitions;
    }

    /**
     * Get the statically-allocated sort key comparators for sorting at the group level, if known
     * @return the comparators used for comparing sort key values, one entry in the array for each
     * nested xsl:sort element
     */

    public AtomicComparer[] getSortKeyComparators() {
        return sortComparators;
    }

    /**
     * Get the statically-determined collator, or null if the collation was not determined statically
     * @return the collation, if known statically, or null if not
     */

    /*@Nullable*/ public StringCollator getCollation() {
        return collator;
    }

    /**
     * Get the static base URI of the instruction
     * @return the static base URI if known, or null otherwise
     */

    /*@Nullable*/ public URI getBaseURI() {
        return baseURI;
    }


    public LocalVariableBinding getGroupBinding() {
        return groupBinding;
    }

    public void setGroupBinding(LocalVariableBinding binding) {
        groupBinding = binding;
    }

    public LocalVariableBinding getKeyBinding() {
        return keyBinding;
    }

    public void setKeyBinding(LocalVariableBinding binding) {
        keyBinding = binding;
    }

    public boolean isComposite() {
        return composite;
    }

    public void setComposite(boolean composite) {
        this.composite = composite;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @return the simplified expression
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        select = visitor.simplify(select);
        action = visitor.simplify(action);
        key = visitor.simplify(key);
        if (collationNameExpression != null) {
            collationNameExpression = visitor.simplify(collationNameExpression);
        }
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        select = visitor.typeCheck(select, contextItemType);
        ItemType selectedItemType = select.getItemType(th);
        if (groupBinding != null) {
            groupBinding.setRequiredType(SequenceType.makeSequenceType(selectedItemType, StaticProperty.ALLOWS_ONE_OR_MORE));
        }
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(selectedItemType, false);
        action = visitor.typeCheck(action, cit);
        key = visitor.typeCheck(key, cit);
        if (collationNameExpression != null) {
            collationNameExpression = visitor.typeCheck(collationNameExpression, contextItemType);
        }
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        if (Literal.isEmptySequence(action)) {
            return action;
        }
        if (sortKeyDefinitions != null) {

            boolean allFixed = true;
            for (SortKeyDefinition sk : sortKeyDefinitions) {
                Expression sortKey = sk.getSortKey();
                sortKey = visitor.typeCheck(sortKey, cit);
                if (visitor.getStaticContext().isInBackwardsCompatibleMode()) {
                    sortKey = FirstItemExpression.makeFirstItemExpression(sortKey);
                } else {
                    RoleLocator role =
                            new RoleLocator(RoleLocator.INSTRUCTION, "xsl:sort/select", 0);
                    role.setErrorCode("XTTE1020");
                    sortKey = CardinalityChecker.makeCardinalityChecker(sortKey, StaticProperty.ALLOWS_ZERO_OR_ONE, role);
                }
                sk.setSortKey(sortKey, true);

                if (sk.isFixed()) {
                    AtomicComparer comp = sk.makeComparator(
                            visitor.getStaticContext().makeEarlyEvaluationContext());
                    sk.setFinalComparator(comp);
                } else {
                    allFixed = false;
                }
            }
            if (allFixed) {
                sortComparators = new AtomicComparer[sortKeyDefinitions.length];
                for (int i=0; i< sortKeyDefinitions.length; i++) {
                    sortComparators[i] = sortKeyDefinitions[i].getFinalComparator();
                }
            }
        }
        keyItemType = key.getItemType(th).getPrimitiveType();
        if (groupBinding != null) {
            fixupGroupReferences(this, this, groupBinding);
        }
        return this;
    }

    private static void fixupGroupReferences(Expression exp, ForEachGroup feg, LocalVariableBinding groupBinding) {
        if (exp instanceof GroupVariableReference && ((GroupVariableReference)exp).getBinding() == groupBinding) {
            ((GroupVariableReference)exp).setControllingExpression(feg);
        } else {
            for (Iterator<Expression> iter = exp.iterateSubExpressions(); iter.hasNext();) {
                Expression child = iter.next();
                fixupGroupReferences(child, feg, groupBinding);
            }
        }
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        select = visitor.optimize(select, contextItemType);
        ItemType selectedItemType = select.getItemType(th);
        ExpressionVisitor.ContextItemType sit = new ExpressionVisitor.ContextItemType(selectedItemType, false);
        action = action.optimize(visitor, sit);
        key = key.optimize(visitor, sit);
        adoptChildExpression(select);
        adoptChildExpression(action);
        adoptChildExpression(key);
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        if (Literal.isEmptySequence(action)) {
            return action;
        }
        // Optimize the sort key definitions
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                Expression sortKey = skd.getSortKey();
                sortKey = visitor.optimize(sortKey, sit);
                skd.setSortKey(sortKey, true);
            }
        }
        if (collationNameExpression != null) {
            collationNameExpression = visitor.optimize(collationNameExpression, contextItemType);
        }
        if (collator == null && (collationNameExpression instanceof StringLiteral)) {
            String collation = ((StringLiteral)collationNameExpression).getStringValue();
            URI collationURI;
            try {
                collationURI = new URI(collation);
                if (!collationURI.isAbsolute()) {
                    collationURI = baseURI.resolve(collationURI);
                    final String collationNameString = collationURI.toString();
                    collationNameExpression = new StringLiteral(collationNameString);
                    collator = visitor.getStaticContext().getCollation(collationNameString);
                    if (collator == null) {
                        XPathException err = new XPathException("Unknown collation " + Err.wrap(collationURI.toString(), Err.URI));
                        err.setErrorCode("XTDE1110");
                        err.setLocator(this);
                        throw err;
                    }
                }
            } catch (URISyntaxException err) {
                XPathException e = new XPathException("Collation name '" + collationNameExpression + "' is not a valid URI");
                e.setErrorCode("XTDE1110");
                e.setLocator(this);
                throw e;
            }
        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        SortKeyDefinition[] newKeyDef = null;
        if (sortKeyDefinitions != null) {
            newKeyDef = new SortKeyDefinition[sortKeyDefinitions.length];
            for (int i = 0; i < sortKeyDefinitions.length; i++) {
                newKeyDef[i] = sortKeyDefinitions[i].copy();
            }
        }
        ForEachGroup feg = new ForEachGroup(
                select.copy(),
                action.copy(),
                algorithm,
                key.copy(),
                collator,
                collationNameExpression.copy(),
                baseURI,
                newKeyDef);
        if (groupBinding != null) {
            LocalVariableBinding lvb = groupBinding.copy();
            feg.setGroupBinding(lvb);
            ExpressionTool.rebindVariableReferences(feg, groupBinding, lvb);
        }
        if (keyBinding != null) {
            LocalVariableBinding lvb = keyBinding.copy();
            feg.setGroupBinding(lvb);
            ExpressionTool.rebindVariableReferences(feg, keyBinding, lvb);
        }
        feg.setComposite(isComposite());
        return feg;
    }


    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return action.getItemType(th);
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    public int computeDependencies() {
        // some of the dependencies in the "action" part and in the grouping and sort keys aren't relevant,
        // because they don't depend on values set outside the for-each-group expression
        int dependencies = 0;
        dependencies |= select.getDependencies();
        dependencies |= key.getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
        dependencies |= (action.getDependencies()
                    &~ (StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_CURRENT_GROUP));
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                dependencies |= (skd.getSortKey().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS);
                Expression e = skd.getCaseOrder();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= (e.getDependencies());
                }
                e = skd.getDataTypeExpression();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= (e.getDependencies());
                }
                e = skd.getLanguage();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= (e.getDependencies());
                }
            }
        }
        if (collationNameExpression != null) {
            dependencies |= collationNameExpression.getDependencies();
        }
        return dependencies;
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
        p |= (action.getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED);
        return p;
    }    

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    public final boolean createsNewNodes() {
        int props = action.getSpecialProperties();
        return ((props & StaticProperty.NON_CREATIVE) == 0);
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        if (offer.action == PromotionOffer.INLINE_VARIABLE_REFERENCES ||
                offer.action == PromotionOffer.EXTRACT_GLOBAL_VARIABLES) {
            // Don't pass on other requests
            action = doPromotion(action, offer);
            key = doPromotion(key, offer);
        }
        // TODO: promote expressions in the sort key definitions
    }

    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(8);
        list.add(select);
        list.add(action);
        list.add(key);
        if (collationNameExpression != null) {
            list.add(collationNameExpression);
        }
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                list.add(skd.getSortKey());
                Expression e = skd.getOrder();
                if (e != null) {
                    list.add(e);
                }
                e = skd.getCaseOrder();
                if (e != null) {
                    list.add(e);
                }
                e = skd.getDataTypeExpression();
                if (e != null) {
                    list.add(e);
                }
                e = skd.getLanguage();
                if (e != null) {
                    list.add(e);
                }
                e = skd.getCollationNameExpression();
                if (e != null) {
                    list.add(e);
                }
            }
        }
        return list.iterator();
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
        ArrayList<SubExpressionInfo> list = new ArrayList<SubExpressionInfo>(8);
        list.add(new SubExpressionInfo(select, true, false, INSPECTION_CONTEXT));
        list.add(new SubExpressionInfo(action, false, true, INHERITED_CONTEXT));
        list.add(new SubExpressionInfo(key, false, true, NODE_VALUE_CONTEXT));
        if (collationNameExpression != null) {
            list.add(new SubExpressionInfo(collationNameExpression, true, false, NODE_VALUE_CONTEXT));
        }
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                list.add(new SubExpressionInfo(skd.getSortKey(), false, true, NODE_VALUE_CONTEXT));
                Expression e = skd.getOrder();
                if (e != null) {
                    list.add(new SubExpressionInfo(e, true, false, NODE_VALUE_CONTEXT));
                }
                e = skd.getCaseOrder();
                if (e != null) {
                    list.add(new SubExpressionInfo(e, true, false, NODE_VALUE_CONTEXT));
                }
                e = skd.getDataTypeExpression();
                if (e != null) {
                    list.add(new SubExpressionInfo(e, true, false, NODE_VALUE_CONTEXT));
                }
                e = skd.getLanguage();
                if (e != null) {
                    list.add(new SubExpressionInfo(e, true, false, NODE_VALUE_CONTEXT));
                }
                e = skd.getCollationNameExpression();
                if (e != null) {
                    list.add(new SubExpressionInfo(e, true, false, NODE_VALUE_CONTEXT));
                }
            }
        }
        return list.iterator();

    }



    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p/>
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap     the PathMap to which the expression should be added
     * @param pathMapNodeSet  the set of nodes within the path map
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = select.addToPathMap(pathMap, pathMapNodeSet);
        if (groupBinding != null) {
            pathMap.registerPathForVariable(groupBinding, target);
        }
        if (collationNameExpression != null) {
            collationNameExpression.addToPathMap(pathMap, pathMapNodeSet);
        }
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                skd.getSortKey().addToPathMap(pathMap, target);
                Expression e = skd.getOrder();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getCaseOrder();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getDataTypeExpression();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getLanguage();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getCollationNameExpression();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
            }
        }
        return action.addToPathMap(pathMap, target);
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
        }
        if (action == original) {
            action = replacement;
            found = true;
        }
        if (collationNameExpression == original) {
            collationNameExpression = replacement;
            found = true;
        }
        if (key == original) {
            key = replacement;
            found = true;
        }
        if (sortKeyDefinitions != null) {
            for (SortKeyDefinition skd : sortKeyDefinitions) {
                if (skd.getSortKey() == original) {
                    skd.setSortKey(replacement, true);
                    found = true;
                }
                if (skd.getOrder() == original) {
                    skd.setOrder(replacement);
                    found = true;
                }
                if (skd.getCaseOrder() == original) {
                    skd.setCaseOrder(replacement);
                    found = true;
                }
                if (skd.getDataTypeExpression() == original) {
                    skd.setDataTypeExpression(replacement);
                    found = true;
                }
                if (skd.getLanguage() == original) {
                    skd.setLanguage(replacement);
                    found = true;
                }
            }
        }
        return found;
    }



    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, StaticContext env, boolean whole) throws XPathException {
        action.checkPermittedContents(parentType, env, false);
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;

        GroupIterator groupIterator = getGroupIterator(context);

        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(groupIterator);
        c2.setCurrentGroupIterator(groupIterator);
        c2.setCurrentTemplateRule(null);

        if (controller.isTracing()) {
            TraceListener listener = controller.getTraceListener();
            assert listener != null;
            while (true) {
                Item item = groupIterator.next();
                if (item == null) {
                    break;
                }
                listener.startCurrentItem(item);
                action.process(c2);
                listener.endCurrentItem(item);
            }
        } else {
            while (true) {
                Item item = groupIterator.next();
                if (item == null) {
                    break;
                }
                action.process(c2);
            }
        }

        return null;
    }

    /**
     * Get the expression which, on evaluation, yields the name of the collation to be used
     * @return the expression that returns the collation name
     */

    public Expression getCollationNameExpression() {
        return collationNameExpression;
    }

    /**
     * Get (and if necessary, create) the comparator used for comparing grouping key values
     * @param context XPath dynamic context
     * @return a StringCollator suitable for comparing the values of grouping keys
     * @throws XPathException if a failure occurs evaluating the expression that determines the collation name
     */

    private StringCollator getCollator(XPathContext context) throws XPathException {
        if (collationNameExpression != null) {
            StringValue collationValue = (StringValue)collationNameExpression.evaluateItem(context);
            assert collationValue != null;
            String cname = collationValue.getStringValue();
            cname = CollatingFunction.expandCollationURI(cname, baseURI);
            return context.getCollation(cname);
        } else {
            // Fallback - this shouldn't happen
            return CodepointCollator.getInstance();
        }
    }

    public AtomicComparer getAtomicComparer(XPathContext context) throws XPathException {
        StringCollator coll = collator;
        if (coll==null) {
            // The collation is determined at run-time
            coll = getCollator(context);
        }
        return AtomicSortComparer.makeSortComparer(coll, keyItemType, context);
    }

    private GroupIterator getGroupIterator(XPathContext context) throws XPathException {
        SequenceIterator population = select.iterate(context);

        // get an iterator over the groups in "order of first appearance"

        GroupIterator groupIterator;
        switch (algorithm) {
            case GROUP_BY: {
                AtomicComparer comparer = getAtomicComparer(context);
                XPathContext c2 = context.newMinorContext();
                c2.setCurrentIterator(population);
                // TODO: how come this needs a new context and group-adjacent doesn't?
                groupIterator = new GroupByIterator(population, key, c2, comparer, composite);
                break;
            }
            case GROUP_ADJACENT: {
                AtomicComparer comparer = getAtomicComparer(context);
                groupIterator = new GroupAdjacentIterator(population, key, context, comparer, composite);
                break;
            }
            case GROUP_STARTING:
                groupIterator = new GroupStartingIterator(population,
                        ((PatternSponsor)key).getPattern(),
                        context);
                break;
            case GROUP_ENDING:
                groupIterator = new GroupEndingIterator(population,
                        ((PatternSponsor)key).getPattern(),
                        context);
                break;
            default:
                throw new AssertionError("Unknown grouping algorithm");
        }

        if (groupBinding != null) {
            groupIterator.setGroupSlot(groupBinding.getLocalSlotNumber());
        }
        if (keyBinding != null) {
            groupIterator.setKeySlot(keyBinding.getLocalSlotNumber());
        }


        // now iterate over the leading nodes of the groups

        if (sortKeyDefinitions != null) {
            AtomicComparer[] comps = sortComparators;
            XPathContext xpc = context.newMinorContext();
            if (comps == null) {
                comps = new AtomicComparer[sortKeyDefinitions.length];
                for (int s = 0; s < sortKeyDefinitions.length; s++) {
                    comps[s] = sortKeyDefinitions[s].makeComparator(xpc);
                }
            }
            groupIterator = new SortedGroupIterator(xpc, groupIterator, this, comps);

            if (groupBinding != null) {
                groupIterator.setGroupSlot(groupBinding.getLocalSlotNumber());
            }
            if (keyBinding != null) {
                groupIterator.setKeySlot(keyBinding.getLocalSlotNumber());
            }
        }

        return groupIterator;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation relies on the process() method: it
     * "pushes" the results of the instruction to a sequence in memory, and then
     * iterates over this in-memory sequence.
     * <p/>
     * In principle instructions should implement a pipelined iterate() method that
     * avoids the overhead of intermediate storage.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        GroupIterator master = getGroupIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(master);
        c2.setCurrentGroupIterator(master);
        c2.setCurrentTemplateRule(null);
        return new ContextMappingIterator(this, c2);
    }

    /**
     * Map one item to a sequence.
     *
     * @param context The processing context. This is supplied only for mapping constructs that
     *                set the context node, position, and size. Otherwise it is null.
     * @return either (a) a SequenceIterator over the sequence of items that the supplied input
     *         item maps to, or (b) an Item if it maps to a single item, or (c) null if it maps to an empty
     *         sequence.
     */

    public SequenceIterator map(XPathContext context) throws XPathException {
        return action.iterate(context);
    }

    /**
     * Callback for evaluating the sort keys
     */

    public AtomicValue evaluateSortKey(int n, XPathContext c) throws XPathException {
        return (AtomicValue) sortKeyDefinitions[n].getSortKey().evaluateItem(c);
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("forEachGroup");
        out.emitAttribute("algorithm", getAlgorithmName(algorithm));
        out.startSubsidiaryElement("select");
        select.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("key");
        key.explain(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("return");
        action.explain(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

    private static String getAlgorithmName(byte algorithm) {
        switch (algorithm) {
            case GROUP_BY:
                return "group-by";
            case GROUP_ADJACENT:
                return "group-adjacent";
            case GROUP_STARTING:
                return "group-starting-with";
            case GROUP_ENDING:
                return "group-ending-with";
            default:
                return "** unknown algorithm **";
        }
    }
}

