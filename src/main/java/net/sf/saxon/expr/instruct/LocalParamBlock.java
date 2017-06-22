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
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Arrays;
import java.util.Iterator;


/**
* Represents the set of xsl:param elements at the start of an xsl:iterate instruction
*/

public class LocalParamBlock extends Instruction {

    private LocalParamSetter[] children;

    /**
     * Create the block of parameters
     * @param params the parameters
     */

    public LocalParamBlock(LocalParamSetter[] params) {
        this.children = params;
        for (LocalParamSetter child : children) {
            adoptChildExpression(child);
        }
    }

    public String getExpressionName() {
        return "block";
    }

    /**
    * Get the children of this instruction
    * @return the children of this instruction, as an array of Instruction objects. May return
     * a zero-length array if there are no children
    */

    public LocalParamSetter[] getChildren() {
        return children;
    }


    public int computeSpecialProperties() {
        return 0;
    }

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return Arrays.asList((Expression[])children).iterator();
    }

    /**
     * Replace one subexpression by a replacement subexpression
     * @param original the original subexpression
     * @param replacement the replacement subexpression
     * @return true if the original subexpression is found
     */

    public boolean replaceSubExpression(Expression original, Expression replacement) {
        boolean found = false;
        if (replacement instanceof LocalParamSetter) {
            for (int c=0; c<children.length; c++) {
                if (children[c] == original) {
                    children[c] = (LocalParamSetter)replacement;
                    found = true;
                }
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
        LocalParamSetter[] c2 = new LocalParamSetter[children.length];
        for (int c=0; c<children.length; c++) {
            c2[c] = (LocalParamSetter)children[c].copy();
        }
        return new LocalParamBlock(c2);
    }

    /**
     * Determine the data type of the items returned by this expression
     * @return the data type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        return ErrorType.getInstance();
    }

    /**
     * Determine the cardinality of the expression
     */

    public final int getCardinality() {
        return StaticProperty.EMPTY;
    }

     /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @exception net.sf.saxon.trans.XPathException if an error is discovered during expression
     *     rewriting
     * @return the simplified expression
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = (LocalParamSetter)visitor.simplify(children[c]);
            adoptChildExpression(children[c]);
        }
        return this;
    }

     /*@NotNull*/
     public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = (LocalParamSetter)visitor.typeCheck(children[c], contextItemType);
            adoptChildExpression(children[c]);
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        for (int c=0; c<children.length; c++) {
            children[c] = (LocalParamSetter)visitor.optimize(children[c], contextItemType);
            adoptChildExpression(children[c]);
        }
        return this;
    }



    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     * @param offer The type of rewrite being offered
     * @throws net.sf.saxon.trans.XPathException
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        for (LocalParamSetter p : children) {
            p.getBinding().setSelectExpression(doPromotion(p.getBinding().getSelectExpression(), offer));
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter out) {
        out.startElement("params");
        for (LocalParamSetter child : children) {
            child.explain(out);
        }
        out.endElement();
    }


    /*@Nullable*/ public TailCall processLeavingTail(XPathContext context) throws XPathException {
        for (LocalParamSetter param : children) {
            try {
                context.setLocalVariable(param.getBinding().getSlotNumber(), param.getBinding().getSelectValue(context));
            } catch (XPathException e) {
                e.maybeSetLocation(param);
                e.maybeSetContext(context);
                throw e;
            }
        }
    	return null;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return PROCESS_METHOD;
    }



}

