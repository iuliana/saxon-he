////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.RuleManager;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;

import java.util.*;


/**
 * An instruction representing an xsl:apply-templates element in the stylesheet
 */

public class ApplyTemplates extends Instruction implements ITemplateCall {

    /*@NotNull*/ protected Expression select;
    /*@NotNull*/ protected WithParam[] actualParams = WithParam.EMPTY_ARRAY;
    /*@NotNull*/ protected WithParam[] tunnelParams = WithParam.EMPTY_ARRAY;
    protected boolean useCurrentMode = false;
    protected boolean useTailRecursion = false;
    protected Mode mode;
    protected boolean implicitSelect;
    protected Expression threads = null;

    protected ApplyTemplates() {
    }

    /**
     * Construct an apply-templates instructino
     *
     * @param select           the select expression
     * @param useCurrentMode   true if mode="#current" was specified
     * @param useTailRecursion true if this instruction is the last in its template
     * @param implicitSelect   true if the select expression is implicit, that is, if there was no explicit
     *                         select expression in the call. This information is used only to make error messages more meaningful.
     * @param mode             the mode specified on apply-templates
     * @param threads          expression whose value indicates how many threads to use when multithreading
     */

    public ApplyTemplates(  /*@NotNull*/ Expression select,
                            boolean useCurrentMode,
                            boolean useTailRecursion,
                            boolean implicitSelect,
                            Mode mode,
                            Expression threads) {
        init(select, useCurrentMode, useTailRecursion, mode);
        this.implicitSelect = implicitSelect;
        this.threads = threads;
    }

    protected void init(Expression select,
                        boolean useCurrentMode,
                        boolean useTailRecursion,
                        Mode mode) {
        this.select = select;
        this.useCurrentMode = useCurrentMode;
        this.useTailRecursion = useTailRecursion;
        this.mode = mode;
        adoptChildExpression(select);
    }

    /**
     * Set the actual parameters on the call
     *
     * @param actualParams represents the contained xsl:with-param elements having tunnel="no" (the default)
     * @param tunnelParams represents the contained xsl:with-param elements having tunnel="yes"
     */

    public void setActualParameters(
            WithParam[] actualParams,
            WithParam[] tunnelParams) {
        this.actualParams = actualParams;
        this.tunnelParams = tunnelParams;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_APPLY_TEMPLATES;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    public int getImplementationMethod() {
        return super.getImplementationMethod() | Expression.WATCH_METHOD;
    }

    /**
     * Get the number of threads requested
     *
     * @return the value of the saxon:threads attribute
     */

    public Expression getNumberOfThreadsExpression() {
        return threads;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @param visitor the expression visitor
     * @return the simplified expression
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        WithParam.simplify(actualParams, visitor);
        WithParam.simplify(tunnelParams, visitor);
        select = visitor.simplify(select);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextItemType);
        WithParam.typeCheck(tunnelParams, visitor, contextItemType);
        try {
            select = visitor.typeCheck(select, contextItemType);
        } catch (XPathException e) {
            if (implicitSelect) {
                String code = e.getErrorCodeLocalPart();
                if ("XPTY0020".equals(code) || "XPTY0019".equals(code)) {
                    XPathException err = new XPathException("Cannot apply-templates to child nodes when the context item is an atomic value");
                    err.setErrorCode("XTTE0510");
                    err.setIsTypeError(true);
                    throw err;
                } else if ("XPDY0002".equals(code)) {
                    XPathException err = new XPathException("Cannot apply-templates to child nodes when the context item is absent");
                    err.setErrorCode("XTTE0510");
                    err.setIsTypeError(true);
                    throw err;
                }
            }
            throw e;
        }
        adoptChildExpression(select);
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextItemType);
        WithParam.optimize(visitor, tunnelParams, contextItemType);
        select = visitor.typeCheck(select, contextItemType);  // More info available second time around
        select = visitor.optimize(select, contextItemType);
        adoptChildExpression(select);
        if (Literal.isEmptySequence(select)) {
            return select;
        }
        if (mode != null && mode.isStreamable()) {
            Optimizer opt = visitor.getConfiguration().obtainOptimizer();
            List<String> reasonsForFailure = new ArrayList<String>(2);
            Expression e2 = opt.makeStreamingApplyTemplates(this, reasonsForFailure);
            if (e2 != null) {
                return e2;
            } else {
//                String msg = "xsl:apply-templates instruction is not streamable";
//                for (String reason : reasonsForFailure) {
//                    msg += "\n  * " + reason;
//                }
//                throw new XPathException(msg);
            }
        }
        if (threads != null) {
            return visitor.getConfiguration().obtainOptimizer().generateMultithreadedInstruction(this);
        }
        return this;
    }

    public int getIntrinsicDependencies() {
        // If the instruction uses mode="#current", this represents a dependency on the context
        // which means the instruction cannot be loop-lifted or moved to a global variable.
        // We overload the dependency DEPENDS_ON_CURRENT_ITEM to achieve this effect.
        return super.getIntrinsicDependencies() | (useCurrentMode ? StaticProperty.DEPENDS_ON_CURRENT_ITEM : 0);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ApplyTemplates a2 = new ApplyTemplates(select.copy(), useCurrentMode, useTailRecursion, implicitSelect, mode, threads);
        a2.actualParams = WithParam.copy(actualParams);
        a2.tunnelParams = WithParam.copy(tunnelParams);
        a2.threads = threads;
        return a2;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true (which is almost invariably the case, so it's not worth
     * doing any further analysis to find out more precisely).
     */

    public final boolean createsNewNodes() {
        return true;
    }

    public void process(XPathContext context) throws XPathException {
        apply(context, false);
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        return apply(context, useTailRecursion);
    }

    protected TailCall apply(XPathContext context, boolean returnTailCall) throws XPathException {
        Mode thisMode = mode;
        if (useCurrentMode) {
            thisMode = context.getCurrentMode();
        }

        // handle parameters if any

        ParameterSet params = assembleParams(context, actualParams);
        ParameterSet tunnels = assembleTunnelParams(context, tunnelParams);

        if (returnTailCall) {
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            return new ApplyTemplatesPackage(
                    ExpressionTool.lazyEvaluate(select, context, 1),
                    thisMode, params, tunnels, c2, getLocationId());
        }

        // Get an iterator to iterate through the selected nodes in original order

        SequenceIterator iter = select.iterate(context);

        // Quick exit if the iterator is empty

        if (iter instanceof EmptyIterator) {
            return null;
        }

        // process the selected nodes now

        XPathContextMajor c2 = context.newContext();
        c2.setCurrentIterator(iter);
        c2.setCurrentMode(thisMode);
        c2.setOrigin(this);

        try {
            TailCall tc = thisMode.applyTemplates(params, tunnels, c2, getLocationId());
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } catch (StackOverflowError e) {
            XPathException err = new XPathException("Too many nested apply-templates calls. The stylesheet may be looping.");
            err.setErrorCode(SaxonErrorCode.SXLM0001);
            err.setLocator(this);
            err.setXPathContext(context);
            throw err;
        }

        return null;

    }

    
    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(10);
        list.add(select);
        WithParam.gatherXPathExpressions(actualParams, list);
        WithParam.gatherXPathExpressions(tunnelParams, list);
        return list.iterator();
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
        if (WithParam.replaceXPathExpression(actualParams, original, replacement)) {
            found = true;
        }
        if (WithParam.replaceXPathExpression(tunnelParams, original, replacement)) {
            found = true;
        }
        return found;
    }


    /**
     * Get the select expression
     *
     * @return the select expression
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Ask if the select expression was implicit
     *
     * @return true if no select attribute was explicitly specified
     */

    public boolean isImplicitSelect() {
        return implicitSelect;
    }

    /**
     * Ask if tail recursion is to be used
     *
     * @return true if tail recursion is used
     */

    public boolean useTailRecursion() {
        return useTailRecursion;
    }

    /**
     * Ask if mode="#current" was specified
     *
     * @return true if mode="#current" was specified
     */

    public boolean usesCurrentMode() {
        return useCurrentMode;
    }

    /**
     * Get the Mode
     *
     * @return the mode, or null if mode="#current" was specified
     */

    public Mode getMode() {
        return mode;
    }

    /**
     * Get the actual parameters passed to the called template
     *
     * @return the non-tunnel parameters
     */

    /*@NotNull*/
    public WithParam[] getActualParams() {
        return actualParams;
    }

    /**
     * Get the tunnel parameters passed to the called template
     *
     * @return the tunnel parameters
     */

    /*@NotNull*/
    public WithParam[] getTunnelParams() {
        return tunnelParams;
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        select = doPromotion(select, offer);
        WithParam.promoteParams(this, actualParams, offer);
        WithParam.promoteParams(this, tunnelParams, offer);
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
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // This logic is assuming the mode is streamable (so that called templates can't return streamed nodes)
        PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        result.setReturnable(false);
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out output destination
     */

    public void explain(ExpressionPresenter out) {

        out.startElement("applyTemplates");
        if (mode != null && !mode.isDefaultMode()) {
            out.emitAttribute("mode", mode.getModeName().getDisplayName());
        }
        explainStreaming(out);
        out.startSubsidiaryElement("select");
        select.explain(out);
        out.endSubsidiaryElement();
        if (actualParams != null && actualParams.length > 0) {
            out.startSubsidiaryElement("withParams");
            WithParam.explainParameters(actualParams, out);
            out.endSubsidiaryElement();
        }
        if (tunnelParams != null && tunnelParams.length > 0) {
            out.startSubsidiaryElement("tunnelParams");
            WithParam.explainParameters(tunnelParams, out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

    protected void explainStreaming(ExpressionPresenter out) {
        // do nothing (implemented in subclass)
    }

    /**
     * An ApplyTemplatesPackage is an object that encapsulates the sequence of nodes to be processed,
     * the mode, the parameters to be supplied, and the execution context. This object can be returned as a tail
     * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
     * template to execute in a finite stack size
     */

    protected static class ApplyTemplatesPackage implements TailCall {

        private Sequence selectedItems;
        private Mode mode;
        private ParameterSet params;
        private ParameterSet tunnelParams;
        private XPathContextMajor evaluationContext;
        private int locationId;

        ApplyTemplatesPackage(Sequence selectedItems,
                              Mode mode,
                              ParameterSet params,
                              ParameterSet tunnelParams,
                              XPathContextMajor context,
                              int locationId
        ) {
            this.selectedItems = selectedItems;
            this.mode = mode;
            this.params = params;
            this.tunnelParams = tunnelParams;
            evaluationContext = context;
            this.locationId = locationId;
        }

        public TailCall processLeavingTail() throws XPathException {
            evaluationContext.setCurrentIterator(selectedItems.iterate());
            evaluationContext.setCurrentMode(mode);
            return mode.applyTemplates(params, tunnelParams, evaluationContext, locationId);
        }
    }

    
}

