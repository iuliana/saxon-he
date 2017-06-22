////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.Iterator;
import java.util.List;

/**
 * An object derived from a xsl:with-param element in the stylesheet. <br>
 */

public class WithParam extends GeneralVariable {

    public static final WithParam[] EMPTY_ARRAY = new WithParam[0];

    int parameterId;
    boolean typeChecked = false;

    public WithParam() {
    }

    /**
     * Allocate a number which is essentially an alias for the parameter name,
     * unique within a stylesheet
     *
     * @param id the parameter id
     */

    public void setParameterId(int id) {
        parameterId = id;
    }

    /**
     * Say whether this parameter will have been typechecked by the caller to ensure it satisfies
     * the required type, in which case the callee need not do a dynamic type check
     *
     * @param checked true if the caller has done static type checking against the required type
     */

    public void setTypeChecked(boolean checked) {
        typeChecked = checked;
    }

    /**
     * Get the parameter id, which is essentially an alias for the parameter name,
     * unique within a stylesheet
     *
     * @return the parameter id
     */

    public int getParameterId() {
        return parameterId;
    }


    public int getInstructionNameCode() {
        return StandardNames.XSL_WITH_PARAM;
    }

    /**
     * Static method to simplify a set of with-param elements
     * @param params the set of parameters to be simplified
     * @param visitor the expression visitor
     * @throws XPathException if a static error is found
     */

    public static void simplify(WithParam[] params, ExpressionVisitor visitor) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                Expression select = param.getSelectExpression();
                if (select != null) {
                    param.setSelectExpression(visitor.simplify(select));
                }
            }
        }
    }

    /**
      * Static method to typecheck a set of with-param elements
      * @param params the set of parameters to be checked
      * @param visitor the expression visitor
      * @param contextItemType static information about the context item type and existence
      * @throws XPathException if a static error is found
      */


    public static void typeCheck(WithParam[] params, ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                Expression select = param.getSelectExpression();
                if (select != null) {
                    param.setSelectExpression(visitor.typeCheck(select, contextItemType));
                }
            }
        }
    }

    /**
      * Static method to optimize a set of with-param elements
      * @param params the set of parameters to be optimized
      * @param visitor the expression visitor
      * @param contextItemType static information about the context item type and existence
      * @throws XPathException if a static error is found
      */

    public static void optimize(ExpressionVisitor visitor, WithParam[] params, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                param.optimize(visitor, contextItemType);
            }
        }
    }

    /**
      * Static method to do expression promotion on a set of with-param elements
      * @param parent the parent instruction (for example apply-templates or call-template)
      * @param params the set of parameters to be be investigated for promotion
      * @param offer the promotion offer to be passed to subexpressions
      * @throws XPathException if a static error is found
      */

    public static void promoteParams(Expression parent, WithParam[] params, PromotionOffer offer) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                Expression select = param.getSelectExpression();
                if (select != null) {
                    param.setSelectExpression(select.promote(offer, parent));
                }
            }
        }
    }

    /**
     * Static method to copy a set of parameters
     *
     * @param params the parameters to be copied
     * @return the resulting copy
     */

    public static WithParam[] copy(WithParam[] params) {
        if (params == null) {
            return null;
        }
        WithParam[] result = new WithParam[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = new WithParam();
            result[i].parameterId = params[i].parameterId;
            result[i].slotNumber = params[i].slotNumber;
            result[i].typeChecked = params[i].typeChecked;
            result[i].select = params[i].select.copy();
            result[i].requiredType = params[i].requiredType;
            result[i].variableQName = params[i].variableQName;
        }
        return result;
    }

    /**
     * Static method to gather the XPath expressions used in an array of WithParam parameters (add them to the supplied list)
     * @param params the set of with-param elements to be searched
     * @param list the list to which the subexpressions will be added
     */

    public static void gatherXPathExpressions(WithParam[] params, List<Expression> list) {
        if (params != null) {
            for (WithParam param : params) {
                for (Iterator<Expression> it = param.iterateSubExpressions(); it.hasNext(); ) {
                    list.add(it.next());
                }
            }
        }
    }

    /**
     * Static method to explain a set of parameters
     * @param params the set of parameters to be explained
     * @param out the destination for the explanation
     */

    public static void explainParameters(WithParam[] params, ExpressionPresenter out) {
        if (params != null) {
            for (WithParam param : params) {
                out.startElement("withParam");
                out.emitAttribute("name", param.getVariableQName().getDisplayName());
                param.getSelectExpression().explain(out);
                out.endElement();
            }
        }
    }

    /**
     * Static method to replace a subexpression within any parameter within which it is found
     * @param params the set of parameters to be examined
     * @param original the subexpression to be replaced
     * @param replacement the replacement expression
     * @return true if a replacement was made
     */

    public static boolean replaceXPathExpression(WithParam[] params, Expression original, Expression replacement) {
        boolean found = false;
        if (params != null) {
            for (WithParam param : params) {
                boolean f = param.replaceSubExpression(original, replacement);
                found |= f;
            }
        }
        return found;
    }

    /**
     * Evaluate the variable (method exists only to satisfy the interface)
     */

    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        throw new UnsupportedOperationException();
    }

    /**
     * Ask whether static type checking has been done
     *
     * @return true if the caller has done static type checking against the type required by the callee
     */

    public boolean isTypeChecked() {
        return typeChecked;
    }
}

