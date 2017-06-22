////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.Binding;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.Current;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;

/**
 * This class represents a pattern that sets the value of current() to the
 * node being matched, and then wraps another pattern that uses the value
 * of current()
 */
public class PatternThatSetsCurrent extends Pattern {

    private LocalVariableBinding binding;
    private Pattern wrappedPattern;

    public PatternThatSetsCurrent(Pattern wrappedPattern) {
        this.wrappedPattern = wrappedPattern;
        this.binding = new LocalVariableBinding(Current.FN_CURRENT, SequenceType.SINGLE_ITEM);
        setExecutable(wrappedPattern.getExecutable());
        setOriginalText(wrappedPattern.toString());
        setLineNumber(wrappedPattern.getLineNumber());
        setSystemId(wrappedPattern.getSystemId());
    }

    /**
     * Get the binding of the item being matched by the pattern, that is, the binding that
     * represents the value of the current() function
     * @return the binding of the item being matched by the pattern
     */

    public Binding getCurrentBinding() {
        return binding;
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager the slot manager representing the stack frame for local variables
     * @param nextFree    the next slot that is free to be allocated
     * @return the next slot that is free to be allocated
     */
    @Override
    public int allocateSlots(SlotManager slotManager, int nextFree) {
        slotManager.allocateSlotNumber(Current.FN_CURRENT);
        binding.setSlotNumber(nextFree++);
        return wrappedPattern.allocateSlots(slotManager, nextFree);
    }

    /**
     * Iterate over the subexpressions within this pattern
     *
     * @return an iterator over the subexpressions. Default implementation returns an empty sequence
     */
    @Override
    public Iterator<Expression> iterateSubExpressions() {
        return wrappedPattern.iterateSubExpressions();
    }

    /**
     * Determine whether this Pattern matches the given item. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the item matches the Pattern, false otherwise
     */
    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        context.setLocalVariable(binding.getLocalSlotNumber(), item);
        return wrappedPattern.matches(item, context);
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     * @return an ItemType, as specific as possible, which all the matching items satisfy
     */
    @Override
    public ItemType getItemType() {
        return wrappedPattern.getItemType();
    }

    /**
     * Simplify the pattern by applying any context-independent optimisations.
     * Default implementation does nothing.
     *
     * @param visitor the expression visitor
     * @return the optimised Pattern
     */
    @Override
    public Pattern simplify(ExpressionVisitor visitor) throws XPathException {
        wrappedPattern = wrappedPattern.simplify(visitor);
        return this;
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */
    @Override
    public Pattern analyze(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        wrappedPattern = wrappedPattern.analyze(visitor, contextItemType);
        return this;
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE. For patterns that
     * do not match nodes, return -1.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */
    @Override
    public int getNodeKind() {
        return wrappedPattern.getNodeKind();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints,
     *         or it if matches atomic values
     */
    @Override
    public int getFingerprint() {
        return wrappedPattern.getFingerprint();
    }

    /**
     * Determine the default priority to use if this pattern appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */
    @Override
    public double getDefaultPriority() {
        return wrappedPattern.getDefaultPriority();
    }

    /**
     * Get the original pattern text
     */
    @Override
    public String toString() {
        return wrappedPattern.toString();
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
     */
    @Override
    public int getHostLanguage() {
        return wrappedPattern.getHostLanguage();
    }

    /**
     * Replace a subexpression by a replacement subexpression
     *
     * @param original    the expression to be replaced
     * @param replacement the new expression to be inserted in its place
     * @return true if the replacement was carried out
     */
    @Override
    public boolean replaceSubExpression(Expression original, Expression replacement) {
        return wrappedPattern.replaceSubExpression(original, replacement);
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @param allowExtensions if false, the result is determined strictly according to the W3C
     *                        "guaranteed streamability rules. If true, Saxon extensions are permitted: that is, constructs
     *                        may be recognized as motionless by Saxon even if they are not recognized as motionless by
     *                        the W3C rules.
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     *         node without changing the position in the streamed input file
     */
    @Override
    public boolean isMotionless(boolean allowExtensions) {
        return wrappedPattern.isMotionless(allowExtensions);
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */
    @Override
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return wrappedPattern.matchesBeneathAnchor(node, anchor, context);
    }

}

