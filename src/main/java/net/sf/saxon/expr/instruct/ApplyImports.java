////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.Rule;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DecimalValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;




/**
* An xsl:apply-imports element in the stylesheet. NOTE: NextMatch is a subclass
*/

public class ApplyImports extends Instruction implements ITemplateCall {

    // TODO: make ApplyImports and NextMatch subclasses of an abstract superclass

    /*@NotNull*/ WithParam[] actualParams = WithParam.EMPTY_ARRAY;
    /*@NotNull*/ WithParam[] tunnelParams = WithParam.EMPTY_ARRAY;
    boolean allowAnyItem = false;

    public ApplyImports() {
    }

    /**
     * Set the actual parameters on the call, including tunnel parameters
     * @param actualParams the non-tunnel parameters
     * @param tunnelParams the tunnel parameters
     */

    public void setActualParameters(/*@NotNull*/ WithParam[] actualParams, /*@NotNull*/ WithParam[] tunnelParams ) {
        this.actualParams = actualParams;
        this.tunnelParams = tunnelParams;
    }

    /**
    * Get the name of this instruction for diagnostic and tracing purposes
    */
    public int getInstructionNameCode() {
        return StandardNames.XSL_APPLY_IMPORTS;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    public int getImplementationMethod() {
        return super.getImplementationMethod() | Expression.WATCH_METHOD;
    }


    /**
     * Get the actual (non-tunnel) parameters passed to the called template
     * @return the non-tunnel parameters
     */

    /*@NotNull*/
    public WithParam[] getActualParams() {
        return actualParams;
    }

    /**
     * Get the tunnel parameters passed to the called template
     * @return the tunnel parameters
     */

    /*@NotNull*/
    public WithParam[] getTunnelParams() {
        return tunnelParams;
    }

    /**
     * Ask whether the instruction can process any item (XSLT 3.0), or only nodes (XSLT 1.0/2.0)
     * @return true if any item can be processed
     */

    public boolean isAllowAnyItem() {
        return allowAnyItem;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @exception net.sf.saxon.trans.XPathException if an error is discovered during expression
     *     rewriting
     * @return the simplified expression
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        WithParam.simplify(actualParams, visitor);
        WithParam.simplify(tunnelParams, visitor);
        allowAnyItem = visitor.getStaticContext().getXPathLanguageLevel().equals(DecimalValue.THREE);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextItemType);
        WithParam.typeCheck(tunnelParams, visitor, contextItemType);
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextItemType);
        WithParam.optimize(visitor, tunnelParams, contextItemType);
        return this;
    }

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        ApplyImports ai2 = new ApplyImports();
        ai2.setActualParameters(WithParam.copy(actualParams), WithParam.copy(tunnelParams));
        ai2.allowAnyItem = allowAnyItem;
        return ai2;
    }
    

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true (which is almost invariably the case, so it's not worth
     * doing any further analysis to find out more precisely).
     */

    public final boolean createsNewNodes() {
        return true;
    }

    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     * @param offer The type of rewrite being offered
     * @throws XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        WithParam.promoteParams(this, actualParams, offer);
        WithParam.promoteParams(this, tunnelParams, offer);
    }



    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(10);
        WithParam.gatherXPathExpressions(actualParams, list);
        WithParam.gatherXPathExpressions(tunnelParams, list);
        return list.iterator();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (WithParam.replaceXPathExpression(actualParams, original, replacement)) {
            found = true;
        }
        if (WithParam.replaceXPathExpression(tunnelParams, original, replacement)) {
            found = true;
        }
        return found;
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
        // This logic is assuming the mode is streamable (so called templates can't return streamed nodes)
        //PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        //result.setReturnable(false);
        if (pathMapNodeSet == null) {
            ContextItemExpression cie = new ContextItemExpression();
            cie.setContainer(getContainer());
            pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
        }
        pathMapNodeSet.addDescendants();
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }



    public TailCall processLeavingTail(XPathContext context) throws XPathException {

        Controller controller = context.getController();
        assert controller != null;
        // handle parameters if any

        ParameterSet params = assembleParams(context, actualParams);
        ParameterSet tunnels = assembleTunnelParams(context, tunnelParams);

        Rule currentTemplateRule = context.getCurrentTemplateRule();
        if (currentTemplateRule==null) {
            XPathException e = new XPathException("There is no current template rule");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0560");
            e.setLocator(this);
            throw e;
        }

        int min = currentTemplateRule.getMinImportPrecedence();
        int max = currentTemplateRule.getPrecedence()-1;
        Mode mode = context.getCurrentMode();
        if (mode == null) {
            mode = controller.getRuleManager().getUnnamedMode();
        }
        if (context.getCurrentIterator()==null) {
            XPathException e = new XPathException("Cannot call xsl:apply-imports when there is no context item");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0565");
            e.setLocator(this);
            throw e;
        }
        Item currentItem = context.getCurrentIterator().current();
        if (!allowAnyItem && !(currentItem instanceof NodeInfo)) {
            XPathException e = new XPathException("Cannot call xsl:apply-imports when context item is not a node");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0565");
            e.setLocator(this);
            throw e;
        }
        Rule rule = controller.getRuleManager().getTemplateRule(currentItem, mode, min, max, context);

		if (rule==null) {             // use the default action for the node
            mode.getBuiltInRuleSet().process(currentItem, params, tunnels, context, getLocationId());
        } else {
            XPathContextMajor c2 = context.newContext();
            Template nh = (Template)rule.getAction();
            c2.setOrigin(this);
            //c2.setOriginatingConstructType(Location.TEMPLATE);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnels);
            c2.openStackFrame(nh.getStackFrameMap());
            c2.setCurrentTemplateRule(rule);
            nh.apply(c2);
        }
        return null;
                // We never treat apply-imports as a tail call, though we could
    }

    


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("applyImports");
        if (actualParams.length > 0) {
            out.startSubsidiaryElement("withParams");
            WithParam.explainParameters(actualParams, out);
            out.endSubsidiaryElement();
        }
        if (tunnelParams.length > 0) {
            out.startSubsidiaryElement("tunnelParams");
            WithParam.explainParameters(tunnelParams, out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

}

