////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Iterator;

/**
 * The compiled form of an xsl:param element within a template in an XSLT stylesheet. An xsl:param
 * element used as a template parameter is compiled into two objects: a LocalParam which represents the
 * parameter definition and the Binding for the value, and a LocalParamSetter which is an Instruction
 * to set a default value for the parameter if none has been supplied.
 *
 * <p>The xsl:param element in XSLT has mandatory attribute name and optional attribute select. It can also
 * be specified as required="yes" or required="no".</p>
 *
 * <p>This is used only for parameters to XSLT templates. For function calls, the caller of the function
 * places supplied arguments onto the callee's stackframe and the callee does not need to do anything.
 * Global parameters (XQuery external variables) are handled using {@link net.sf.saxon.expr.instruct.GlobalParam}.</p>
 *
 * <p>The LocalParam class is also used to represent parameters with the saxon:iterate instruction</p>
 */

public final class LocalParamSetter extends Instruction {

    /*@NotNull*/
    private LocalParam binding;

    public LocalParamSetter(/*@NotNull*/ LocalParam binding) {
        this.binding = binding;
    }

    /**
     * Get the LocalParam element representing the binding for this parameter
     * @return the binding element
     */

    /*@NotNull*/
    public LocalParam getBinding() {
        return binding;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @param th the type hierarchy cache
     * @return the static item type of the instruction
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType(TypeHierarchy th) {
        return ErrorType.getInstance();
    }

    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */
    @Override
    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
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
        return 0;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns a default value of false
     *
     * @return true if the instruction creates new nodes (or if it can't be proved that it doesn't)
     */
    @Override
    public boolean createsNewNodes() {
        return false;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression
     *          rewriting
     */
    /*@NotNull*/
    @Override
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        Expression select = binding.getSelectExpression();
        if (select != null) {
            binding.setSelectExpression(visitor.simplify(select));
            adoptChildExpression(select);
        }
        return this;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     * <p/>
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     * <p/>
     * <p>If the implementation returns a value other than "this", then it is required to ensure that
     * the location information in the returned expression have been set up correctly.
     * It should not rely on the caller to do this, although for historical reasons many callers do so.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten to perform necessary run-time type checks,
     *         and to perform other type-related optimizations
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression select = binding.getSelectExpression();
        if (select != null) {
            binding.setSelectExpression(visitor.typeCheck(select, contextItemType));
            adoptChildExpression(select);
        }
        binding.checkAgainstRequiredType(visitor);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression select = binding.getSelectExpression();
        if (select != null) {
            binding.setSelectExpression(visitor.optimize(select, contextItemType));
            adoptChildExpression(select);
            binding.computeEvaluationMode();
        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */
    /*@NotNull*/
    @Override
    public Expression copy() {
        return new LocalParamSetter(binding);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_PARAM;
    }

    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return binding.iterateSubExpressions();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        return binding.replaceSubExpression(original, replacement);
    }


    /**
    * Process the local parameter declaration
    */

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        int slotNumber = binding.getSlotNumber();
        int wasSupplied = context.useLocalParameter(binding.getParameterId(), slotNumber, binding.isTunnelParam());
        switch (wasSupplied) {
        case ParameterSet.SUPPLIED_AND_CHECKED:
            // No action needed
            break;

        case ParameterSet.SUPPLIED:
            // if a parameter was supplied by the caller, with no type-checking by the caller,
            // then we may need to convert it to the type required
            Expression conversion = binding.getConversion();
            int conversionEvaluationMode = binding.getConversionEvaluationMode();
            if (conversion != null) {
                context.setLocalVariable(slotNumber,
                        ExpressionTool.evaluate(conversion, conversionEvaluationMode, context, 10));
                // We do an eager evaluation here for safety, because the result of the
                // type conversion overwrites the slot where the actual supplied parameter
                // is contained.
            }
            break;

            // don't evaluate the default if a value has been supplied or if it has already been
            // evaluated by virtue of a forwards reference

        case ParameterSet.NOT_SUPPLIED:
            if (binding.isImplicitlyRequiredParam()) {
                String name = "$" + binding.getVariableQName().getDisplayName();
                XPathException e = new XPathException("A value must be supplied for parameter "
                        + name + " because " +
                        "the default value is not a valid instance of the required type");
                e.setXPathContext(context);
                e.setErrorCode("XTDE0610");
                throw e;
            } else if (binding.isRequiredParam()) {
                String name = "$" + binding.getVariableQName().getDisplayName();
                XPathException e = new XPathException("No value supplied for required parameter " + name);
                e.setXPathContext(context);
                e.setErrorCode("XTDE0700");
                throw e;
            }
            context.setLocalVariable(slotNumber, binding.getSelectValue(context));
        }
        return null;
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("param");
        out.emitAttribute("name", binding.getVariableQName().getDisplayName());
        if (binding.getSelectExpression() != null) {
            binding.getSelectExpression().explain(out);
        }
        Expression conversion = binding.getConversion();
        if (conversion != null) {
            out.startElement("conversion");
            conversion.explain(out);
            out.endElement();
        }
        out.endElement();
    }

}

