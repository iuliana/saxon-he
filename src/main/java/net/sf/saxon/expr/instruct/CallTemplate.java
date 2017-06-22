////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Instruction representing an xsl:call-template element in the stylesheet.
 */

public class CallTemplate extends Instruction implements ITemplateCall {

    /*@Nullable*/ private Template template = null; // Null only for saxon:call-template
    /*@NotNull*/ private WithParam[] actualParams = WithParam.EMPTY_ARRAY;
    /*@NotNull*/ private WithParam[] tunnelParams = WithParam.EMPTY_ARRAY;
    private boolean useTailRecursion = false;
    private Expression calledTemplateExpression;    // allows name to be an AVT
    private NamespaceResolver nsContext;             // needed only for a dynamic call

    /**
     * Construct a CallTemplate instruction.
     *
     * @param template                 the Template object identifying the template to be called, in the normal
     *                                 case where this is known statically
     * @param useTailRecursion         true if the call is potentially tail recursive
     * @param calledTemplateExpression expression to calculate the name of the template to be called
     *                                 at run-time, this supports the saxon:allow-avt option
     * @param nsContext                the static namespace context of the instruction, needed only in the case
     *                                 where the name of the called template is to be calculated dynamically
     */

    public CallTemplate(Template template,
                        boolean useTailRecursion,
                        Expression calledTemplateExpression,
                        NamespaceResolver nsContext) {

        this.template = template;
        this.useTailRecursion = useTailRecursion;
        this.calledTemplateExpression = calledTemplateExpression;
        this.nsContext = nsContext;
        adoptChildExpression(calledTemplateExpression);
    }

    public Expression getCalledTemplateExpression(){
        return calledTemplateExpression;
    }

    public NamespaceResolver getNsContext(){
        return nsContext;
    }

    /**
     * Set the actual parameters on the call
     *
     * @param actualParams the parameters that are not tunnel parameters
     * @param tunnelParams the tunnel parameters
     */

    public void setActualParameters(
            /*@NotNull*/ WithParam[] actualParams,
            /*@NotNull*/ WithParam[] tunnelParams) {
        this.actualParams = actualParams;
        this.tunnelParams = tunnelParams;
        for (WithParam actualParam : actualParams) {
            adoptChildExpression(actualParam.getSelectExpression());
        }
        for (WithParam tunnelParam : tunnelParams) {
            adoptChildExpression(tunnelParam.getSelectExpression());
        }
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
     * Get the target template, if known statically
     *
     * @return the target template
     */

    public Template getTargetTemplate() {
        return template;
    }

    /**
     * Ask whether this is a tail call
     * @return true if this is a tail call
     */

    public boolean usesTailRecursion() {
        return useTailRecursion;
    }

    /**
     * Return the name of this instruction.
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_CALL_TEMPLATE;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        WithParam.simplify(actualParams, visitor);
        WithParam.simplify(tunnelParams, visitor);
        if (calledTemplateExpression != null) {
            calledTemplateExpression = visitor.simplify(calledTemplateExpression);
        }
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextItemType);
        WithParam.typeCheck(tunnelParams, visitor, contextItemType);
        if (calledTemplateExpression != null) {
            calledTemplateExpression = visitor.typeCheck(calledTemplateExpression, contextItemType);
            adoptChildExpression(calledTemplateExpression);
        } else if (template.getBody() != null) {
            // For non-tunnel parameters, see if the supplied value is type-safe against the declared
            // type of the value, and if so, avoid the dynamic type check
            // Can't do this check unless the target template has been compiled.
            boolean backwards = visitor.getStaticContext().isInBackwardsCompatibleMode();
            for (int p = 0; p < actualParams.length; p++) {
                WithParam wp = actualParams[p];
                int id = wp.getParameterId();
                LocalParam lp = template.getLocalParam(id);
                if (lp != null) {
                    SequenceType req = lp.getRequiredType();
                    RoleLocator role = new RoleLocator(RoleLocator.PARAM, wp.getVariableQName().getDisplayName(), p);
                    Expression select = TypeChecker.staticTypeCheck(
                            wp.getSelectExpression(), req, backwards, role, visitor);
                    wp.setSelectExpression(select);
                    wp.setTypeChecked(true);
                }
            }

        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextItemType);
        WithParam.optimize(visitor, tunnelParams, contextItemType);
        if (calledTemplateExpression != null) {
            calledTemplateExpression = visitor.optimize(calledTemplateExpression, contextItemType);
            adoptChildExpression(calledTemplateExpression);
        }
        return this;
    }


    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    public int computeCardinality() {
        if (template == null) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return template.getRequiredType().getCardinality();
        }
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @param th the type hierarchy cache
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (template == null) {
            return AnyItemType.getInstance();
        } else {
            return template.getRequiredType().getPrimaryType();
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        CallTemplate ct = new CallTemplate(template, useTailRecursion,
                (calledTemplateExpression==null ? null : calledTemplateExpression.copy()), nsContext);
        ct.actualParams = WithParam.copy(actualParams);
        ct.tunnelParams = WithParam.copy(tunnelParams);
        return ct;
    }

    public int getIntrinsicDependencies() {
        // we could go to the called template and find which parts of the context it depends on, but this
        // would create the risk of infinite recursion. So we just assume that the dependencies exist
        return StaticProperty.DEPENDS_ON_XSLT_CONTEXT |
                StaticProperty.DEPENDS_ON_FOCUS;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation currently returns true unconditionally.
     */

    public final boolean createsNewNodes() {
        return true;
    }

    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        ArrayList<Expression> list = new ArrayList<Expression>(10);
        if (calledTemplateExpression != null) {
            list.add(calledTemplateExpression);
        }
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
        if (WithParam.replaceXPathExpression(actualParams, original, replacement)) {
            found = true;
        }
        if (WithParam.replaceXPathExpression(tunnelParams, original, replacement)) {
            found = true;
        }
        if (calledTemplateExpression == original) {
            calledTemplateExpression = replacement;
        }
        return found;
    }


    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        if (calledTemplateExpression != null) {
            calledTemplateExpression = doPromotion(calledTemplateExpression, offer);
        }
        WithParam.promoteParams(this, actualParams, offer);
        WithParam.promoteParams(this, tunnelParams, offer);
    }

    /**
     * Process this instruction, without leaving any tail calls.
     *
     * @param context the dynamic context for this transformation
     * @throws XPathException if a dynamic error occurs
     */

    public void process(XPathContext context) throws XPathException {

        Template t = getTargetTemplate(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.openStackFrame(t.getStackFrameMap());
        c2.setLocalParameters(assembleParams(context, actualParams));
        c2.setTunnelParameters(assembleTunnelParams(context, tunnelParams));

        try {
            TailCall tc = t.expand(c2);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } catch (StackOverflowError e) {
            XPathException err = new XPathException("Too many nested template or function calls. The stylesheet may be looping.");
            err.setLocator(this);
            err.setXPathContext(context);
            throw err;
        }
    }

    /**
     * Process this instruction. If the called template contains a tail call (which may be
     * an xsl:call-template of xsl:apply-templates instruction) then the tail call will not
     * actually be evaluated, but will be returned in a TailCall object for the caller to execute.
     *
     * @param context the dynamic context for this transformation
     * @return an object containing information about the tail call to be executed by the
     *         caller. Returns null if there is no tail call.
     */

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        if (useTailRecursion) {

            // if name is determined dynamically, determine it now

            Template target = getTargetTemplate(context);

            // handle parameters if any
    
            ParameterSet params = assembleParams(context, actualParams);
            ParameterSet tunnels = assembleTunnelParams(context, tunnelParams);

            // Call the named template. Actually, don't call it; rather construct a call package
            // and return it to the caller, who will then process this package.

            //System.err.println("Call template using tail recursion");
            if (params == null) {                  // bug 490967
                params = ParameterSet.EMPTY_PARAMETER_SET;
            }

            // clear all the local variables: they are no longer needed
            Arrays.fill(context.getStackFrame().getStackFrameValues(), null);

            return new CallTemplatePackage(target, params, tunnels, this, context);

        } else {
            process(context);
            return null;
        }
    }

    /**
     * Get the template, in the case where it is specified dynamically.
     *
     * @param context The dynamic context of the transformation
     * @return The template to be called
     * @throws XPathException if a dynamic error occurs: specifically, if the
     *                        template name is computed at run-time (Saxon extension) and the name is invalid
     *                        or does not reference a known template
     */

    public Template getTargetTemplate(XPathContext context) throws XPathException {
        if (calledTemplateExpression != null) {
            Controller controller = context.getController();
            assert controller != null;
            CharSequence qname = calledTemplateExpression.evaluateAsString(context);

            String prefix;
            String localName;
            try {
                String[] parts = controller.getConfiguration().getNameChecker().getQNameParts(qname);
                prefix = parts[0];
                localName = parts[1];
            } catch (QNameException err) {
                dynamicError("Invalid template name. " + err.getMessage(), "XTSE0650", context);
                return null;
            }
            String uri = nsContext.getURIForPrefix(prefix, false);
            if (uri == null) {
                dynamicError("Namespace prefix " + prefix + " has not been declared", "XTSE0650", context);
            }
            StructuredQName qName = new StructuredQName("", uri, localName);
            Template target = ((PreparedStylesheet) controller.getExecutable()).getNamedTemplate(qName);
            if (target == null) {
                dynamicError("Template " + qname + " has not been defined", "XTSE0650", context);
            }
            return target;
        } else {
            return template;
        }
    }


    public StructuredQName getObjectName() {
        return (template == null ? null : template.getTemplateName());
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("callTemplate");
        if (template != null && template.getTemplateName() != null) {
            out.emitAttribute("name", template.getTemplateName().getDisplayName());
        } else {
            out.startSubsidiaryElement("name");
            calledTemplateExpression.explain(out);
            out.endSubsidiaryElement();
        }
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
     * A CallTemplatePackage is an object that encapsulates the name of a template to be called,
     * the parameters to be supplied, and the execution context. This object can be returned as a tail
     * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
     * template to execute in a finite stack size
     */

    public static class CallTemplatePackage implements TailCall {

        private Template target;
        private ParameterSet params;
        private ParameterSet tunnelParams;
        private Instruction instruction;
        private XPathContext evaluationContext;

        /**
         * Construct a CallTemplatePackage that contains information about a call.
         *
         * @param template          the Template to be called
         * @param params            the parameters to be supplied to the called template
         * @param tunnelParams      the tunnel parameter supplied to the called template
         * @param evaluationContext saved context information from the Controller (current mode, etc)
         *                          which must be reset to ensure that the template is called with all the context information
         *                          intact
         */

        public CallTemplatePackage(Template template,
                                   ParameterSet params,
                                   ParameterSet tunnelParams,
                                   Instruction instruction,
                                   XPathContext evaluationContext) {
            target = template;
            this.params = params;
            this.tunnelParams = tunnelParams;
            this.instruction = instruction;
            this.evaluationContext = evaluationContext;
        }

        /**
         * Process the template call encapsulated by this package.
         *
         * @return another TailCall. This will never be the original call, but it may be the next
         *         recursive call. For example, if A calls B which calls C which calls D, then B may return
         *         a TailCall to A representing the call from B to C; when this is processed, the result may be
         *         a TailCall representing the call from C to D.
         * @throws XPathException if a dynamic error occurs
         */

        public TailCall processLeavingTail() throws XPathException {
            // TODO: the idea of tail call optimization is to reuse the caller's stack frame rather than
            // creating a new one. We're doing this for the Java stack, but not for the context stack where
            // local variables are held. It should be possible to avoid creating a new context, and instead
            // to update the existing one in situ.
            XPathContextMajor c2 = evaluationContext.newContext();
            c2.setOrigin(instruction);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnelParams);
            c2.openStackFrame(target.getStackFrameMap());

            // System.err.println("Tail call on template");

            return target.expand(c2);
        }
    }

}

