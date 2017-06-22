////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.RuleTarget;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;
import java.util.List;

/**
* The runtime object corresponding to an xsl:template element in the stylesheet.
 *
 * Note that the Template object no longer has precedence information associated with it; this is now
 * only in the Rule object that references this Template. This allows two rules to share the same template,
 * with different precedences. This occurs when a stylesheet module is imported more than once, from different
 * places, with different import precedences.
*/

public class Template extends Procedure implements RuleTarget {

    // TODO: change the calling mechanism for named templates to use positional parameters
    // in the same way as functions. For templates that have both a match and a name attribute,
    // create a match template as a wrapper around the named template, resulting in separate
    // NamedTemplate and MatchTemplate classes. For named templates, perhaps compile into function
    // calls directly, the only difference being that context is retained.

    // The body of the template is represented by an expression,
    // which is responsible for any type checking that's needed.

    private Pattern matchPattern;
    private StructuredQName templateName;
    private boolean hasRequiredParams;
    private boolean bodyIsTailCallReturner;
    private SequenceType requiredType;
    private boolean declaredStreamable;
    private StructuredQName[] modeNames;

    /**
     * Create a template
     */

    public Template () {
        setHostLanguage(Configuration.XSLT);
    }

    /**
     * Initialize the template
     * @param templateName the name of the template (if any)
     * performed by apply-imports
     */

    public void setTemplateName(StructuredQName templateName) {
        this.templateName = templateName;
    }

    /**
     * Set the match pattern used with this template
     * @param pattern the match pattern (may be null for a named template)
     */

    public void setMatchPattern(Pattern pattern) {
        matchPattern = pattern;
    }

    /**
     * Get the match pattern used with this template
     * @return the match pattern, or null if this is a named template with no match pattern
     */

    public Pattern getMatchPattern() {
        return matchPattern;
    }

    /**
     * Set the expression that forms the body of the template
     * @param body the body of the template
     */

    public void setBody(Expression body) {
        super.setBody(body);
        bodyIsTailCallReturner = (body instanceof TailCallReturner);
    }

    /**
     * Get the name of the template (if it is named)
     * @return the template name, or null if unnamed
     */

    public StructuredQName getTemplateName() {
        return templateName;
    }


    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     */

    public StructuredQName getObjectName() {
        return templateName;
    }

    /**
     * Set whether this template has one or more required parameters
     * @param has true if the template has at least one required parameter
     */

    public void setHasRequiredParams(boolean has) {
        hasRequiredParams = has;
    }

    /**
     * Ask whether this template has one or more required parameters
     * @return true if this template has at least one required parameter
     */

    public boolean hasRequiredParams() {
        return hasRequiredParams;
    }

    /**
     * Set the required type to be returned by this template
     * @param type the required type as defined in the "as" attribute on the xsl:template element
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type to be returned by this template
     * @return the required type as defined in the "as" attribute on the xsl:template element
     */

    public SequenceType getRequiredType() {
        if (requiredType == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return requiredType;
        }
    }

    /**
     * Set the names of the modes associated with this template rule
     * @param modeNames the names of the modes for this template rule
     */

    public void setModeNames(StructuredQName[] modeNames) {
        this.modeNames = modeNames;
    }

    /**
     * Get the names of the modes associated with this template rule
     * @return the names of the modes for this template rule
     */

    public StructuredQName[] getModeNames() {
        return modeNames;
    }

    /**
     * Say whether or not this template is declared as streamable
     * @param streamable true if the template belongs to a streamable mode
     */

    public void setDeclaredStreamable(boolean streamable) {
        this.declaredStreamable = streamable;
    }

    /**
     * Ask whether or not this template is declared as streamable
     * @return true if the template belongs to a streamable mode
     */

    public boolean isDeclaredStreamable() {
        return declaredStreamable;
    }

    /**
     * Get the local parameter with a given parameter id
     * @param id the parameter id
     * @return the local parameter with this id if found, otherwise null
     */

    /*@Nullable*/ public LocalParam getLocalParam(int id) {
        Iterator<Expression> iter = body.iterateSubExpressions();
        while (iter.hasNext()) {
            Expression child = iter.next();
            if (child instanceof LocalParamSetter && ((LocalParamSetter)child).getBinding().getParameterId() == id) {
                return ((LocalParamSetter)child).getBinding();
            }
        }
        return null;
    }

    /**
     * Process the template, without returning any tail calls. This path is used by
     * xsl:apply-imports and xsl:next-match
     * @param context The dynamic context, giving access to the current node,
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating
     * the template
     */

    public void apply(XPathContextMajor context) throws XPathException {
        TailCall tc = applyLeavingTail(context);
        while (tc != null) {
            tc = tc.processLeavingTail();
        }
    }

    /**
     * Process this template, with the possibility of returning a tail call package if the template
     * contains any tail calls that are to be performed by the caller.
     * @param context the XPath dynamic context
     * @return null if the template exited normally; but if it was a tail call, details of the call
     * that hasn't been made yet and needs to be made by the caller
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating
     * the template
    */

    public TailCall applyLeavingTail(XPathContextMajor context) throws XPathException {
    	if (bodyIsTailCallReturner) {
            return ((TailCallReturner)body).processLeavingTail(context);
        } else {
            body.process(context);
            return null;
        }
    }

    /**
     * Expand the template. Called when the template is invoked using xsl:call-template.
     * Invoking a template by this method does not change the current template.
     * @param context the XPath dynamic context
     * @return null if the template exited normally; but if it was a tail call, details of the call
     * that hasn't been made yet and needs to be made by the caller
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating
     * the template
    */

    public TailCall expand(XPathContext context) throws XPathException {
        if (bodyIsTailCallReturner) {
            return ((TailCallReturner)body).processLeavingTail(context);
        } else if (body != null) {
            body.process(context);
        }
        return null;
    }


    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link net.sf.saxon.trace.Location}.
     */

    public int getConstructType() {
        return Location.TEMPLATE;
    }

    /**
     * Output diagnostic explanation to an ExpressionPresenter
     */

    public void explain(ExpressionPresenter presenter) {
        presenter.emitAttribute("line", getLineNumber()+"");
        presenter.emitAttribute("module", getSystemId());
        if (isDeclaredStreamable()) {
            presenter.emitAttribute("streamable", "true");
        }
        if (getBody() != null) {
            getBody().explain(presenter);
        }
    }



}

