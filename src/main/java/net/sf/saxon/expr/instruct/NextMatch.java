////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.Rule;
import net.sf.saxon.trans.XPathException;

import java.util.Arrays;



/**
* An xsl:next-match element in the stylesheet
*/

public class NextMatch extends ApplyImports {

    boolean useTailRecursion;

    public NextMatch(boolean useTailRecursion) {
        this.useTailRecursion = useTailRecursion;
    }

    /**
    * Get the name of this instruction for diagnostic and tracing purposes
    */

    public int getInstructionNameCode() {
        return StandardNames.XSL_NEXT_MATCH;
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        NextMatch nm2 = new NextMatch(useTailRecursion);
        nm2.setActualParameters(WithParam.copy(actualParams), WithParam.copy(tunnelParams));
        nm2.allowAnyItem = allowAnyItem;
        return nm2;
    }


    /*@Nullable*/ public TailCall processLeavingTail(XPathContext context) throws XPathException {

        Controller controller = context.getController();
        assert controller != null;

        // handle parameters if any

        ParameterSet params = assembleParams(context, actualParams);
        ParameterSet tunnels = assembleTunnelParams(context, tunnelParams);

        Rule currentRule = context.getCurrentTemplateRule();
        if (currentRule==null) {
            XPathException e = new XPathException("There is no current template rule");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0560");
            throw e;
        }
        Mode mode = context.getCurrentMode();
        if (mode == null) {
            mode = controller.getRuleManager().getUnnamedMode();
        }
        if (context.getCurrentIterator()==null) {
            XPathException e = new XPathException("Cannot call xsl:next-match when there is no context item");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0565");
            throw e;
        }
        Item currentItem = context.getCurrentIterator().current();
        if (!allowAnyItem && !(currentItem instanceof NodeInfo)) {
            XPathException e = new XPathException("Cannot call xsl:next-match when context item is not a node");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0565");
            throw e;
        }
        Rule rule = controller.getRuleManager().getNextMatchHandler(currentItem, mode, currentRule, context);

		if (rule==null) {             // use the default action for the node
            mode.getBuiltInRuleSet().process(currentItem, params, tunnels, context, getLocationId());
        } else if (useTailRecursion) {
            //Template nh = (Template)rule.getAction();
            // clear all the local variables: they are no longer needed
            Arrays.fill(context.getStackFrame().getStackFrameValues(), null);
            return new NextMatchPackage(rule, params, tunnels, context);
        } else {
            Template nh = (Template)rule.getAction();
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            c2.setOriginatingConstructType(Location.TEMPLATE);
            c2.openStackFrame(nh.getStackFrameMap());
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnels);
            c2.setCurrentTemplateRule(rule);
            nh.apply(c2);
        }
        return null;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("nextMatch");
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

    /**
    * A NextMatchPackage is an object that encapsulates the name of a template to be called,
    * the parameters to be supplied, and the execution context. This object can be returned as a tail
    * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
    * template to execute in a finite stack size
    */

    private class NextMatchPackage implements TailCall {

        private Rule rule;
        private ParameterSet params;
        private ParameterSet tunnelParams;
        private XPathContext evaluationContext;

        /**
         * Construct a NextMatchPackage that contains information about a call.
         * @param rule the rule identifying the Template to be called
         * @param params the parameters to be supplied to the called template
         * @param tunnelParams the tunnel parameter supplied to the called template
         * @param evaluationContext saved context information from the Controller (current mode, etc)
         * which must be reset to ensure that the template is called with all the context information
         * intact
         */

        public NextMatchPackage(Rule rule,
                                   ParameterSet params,
                                   ParameterSet tunnelParams,
                                   XPathContext evaluationContext) {
            this.rule = rule;
            this.params = params;
            this.tunnelParams = tunnelParams;
            this.evaluationContext = evaluationContext;
        }

        /**
        * Process the template call encapsulated by this package.
        * @return another TailCall. This will never be the original call, but it may be the next
        * recursive call. For example, if A calls B which calls C which calls D, then B may return
        * a TailCall to A representing the call from B to C; when this is processed, the result may be
        * a TailCall representing the call from C to D.
         * @throws XPathException if a dynamic error occurs
        */

        public TailCall processLeavingTail() throws XPathException {
            Template nh = (Template)rule.getAction();
            XPathContextMajor c2 = evaluationContext.newContext();
            c2.setOrigin(NextMatch.this);
            c2.setOriginatingConstructType(Location.TEMPLATE);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnelParams);
            c2.openStackFrame(nh.getStackFrameMap());
            c2.setCurrentTemplateRule(rule);

            // System.err.println("Tail call on template");

            return nh.applyLeavingTail(c2);
        }
    }


}

