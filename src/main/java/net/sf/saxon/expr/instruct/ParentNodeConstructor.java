////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.PromotionOffer;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.Untyped;

import java.util.Iterator;

/**
 * An abstract class to act as a common parent for instructions that create element nodes
 * and document nodes.
 */

public abstract class ParentNodeConstructor extends Instruction implements ValidatingInstruction

{

    /*@NotNull*/ protected Expression content;
    private boolean lazyConstruction = false;
    private ParseOptions validationOptions = null;
    private String baseURI;

    /**
     * Flag set to true if validation=preserve and no schema type supplied for validation; also true
     * when validation="strip" if there is no need to physically strip type annotations
     */

    protected boolean preservingTypes = true;

    /**
     * Create a document or element node constructor instruction
     */

    public ParentNodeConstructor() {
    }

    /**
     * Set the static base URI of the instruction
     *
     * @param uri the static base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
     * Get the static base URI of the instruction
     *
     * @return the static base URI
     */

    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Indicate that lazy construction should (or should not) be used. Note that
     * this request will be ignored if validation is required
     *
     * @param lazy set to true if lazy construction should be used
     */

    public void setLazyConstruction(boolean lazy) {
        lazyConstruction = lazy;
    }

    /**
     * Establish whether lazy construction is to be used
     *
     * @return true if lazy construction is to be used
     */

    public final boolean isLazyConstruction() {
        return lazyConstruction;
    }

    /**
     * Get the schema type chosen for validation; null if not defined
     *
     * @return the type to be used for validation. (For a document constructor, this is the required
     *         type of the document element)
     */

    public SchemaType getSchemaType() {
        return validationOptions==null ? null : validationOptions.getTopLevelType();
    }

    /**
     * Get the validation options
     * @return the validation options for the content of the constructed node. May be null if no
     * validation was requested.
     */

    public ParseOptions getValidationOptions() {
        return validationOptions;
    }

    /**
     * Set the validation mode for the new document or element node
     *
     * @param mode       the validation mode, for example {@link Validation#STRICT}
     * @param schemaType the required type (for validation by type). Null if not
     * validating by type
     */


    public void setValidationAction(int mode, /*@Nullable*/ SchemaType schemaType) {
        preservingTypes = (mode == Validation.PRESERVE && schemaType == null);
        if (!preservingTypes) {
            if (validationOptions == null) {
                validationOptions = new ParseOptions();
            }
            if (schemaType == Untyped.getInstance()) {
                validationOptions.setSchemaValidationMode(Validation.SKIP);
            } else {
                validationOptions.setSchemaValidationMode(mode);
                validationOptions.setTopLevelType(schemaType);
            }
        }
    }


    /**
     * Get the validation mode for this instruction
     *
     * @return the validation mode, for example {@link Validation#STRICT} or {@link Validation#PRESERVE}
     */
    public int getValidationAction() {
        return validationOptions == null ? Validation.PRESERVE : validationOptions.getSchemaValidationMode();
    }

    /**
     * Set that the newly constructed node and everything underneath it will automatically be untyped,
     * without any need to physically remove type annotations, even though validation=STRIP is set.
     */

    public void setNoNeedToStrip() {
        preservingTypes = true;
    }

    /**
     * Set the expression that constructs the content of the element
     *
     * @param content the content expression
     */

    public void setContentExpression(Expression content) {
        this.content = content;
        adoptChildExpression(content);
    }

    /**
     * Get the expression that constructs the content of the element
     *
     * @return the content expression
     */

    public Expression getContentExpression() {
        return content;
    }

    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }


    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     * @param visitor an expression visitor
     * @return the simplified expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        content = visitor.simplify(content);
        return this;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        content = visitor.typeCheck(content, contextItemType);
        adoptChildExpression(content);
        verifyLazyConstruction();
        checkContentSequence(visitor.getStaticContext());
        return this;
    }

    /**
     * Check that the child instructions don't violate any obvious constraints for this kind of node
     *
     * @param env the static context
     * @throws XPathException if the check fails
     */

    protected abstract void checkContentSequence(StaticContext env) throws XPathException;

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        content = visitor.optimize(content, contextItemType);
        if (content instanceof Block) {
            content = ((Block) content).mergeAdjacentTextInstructions();
        }
        adoptChildExpression(content);
        if (visitor.isOptimizeForStreaming()) {
            visitor.getConfiguration().obtainOptimizer().makeCopyOperationsExplicit(this, content);
        }
        Executable exec = getExecutable();
        if (exec == null || exec.isSchemaAware()) {
            TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            if (getValidationAction() == Validation.STRIP) {
                if ((content.getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED) != 0 ||
                        (th.relationship(content.getItemType(th), NodeKindTest.ELEMENT) == TypeHierarchy.DISJOINT &&
                                th.relationship(content.getItemType(th), NodeKindTest.ATTRIBUTE) == TypeHierarchy.DISJOINT)) {
                    // No need to strip type annotations if there are none needing to be stripped
                    setNoNeedToStrip();
                }
            }
        } else {
            setNoNeedToStrip();
        }
        return this;
    }


    /**
     * Handle promotion offers, that is, non-local tree rewrites.
     *
     * @param offer The type of rewrite being offered
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    protected void promoteInst(PromotionOffer offer) throws XPathException {
        if (offer.action != PromotionOffer.UNORDERED) {
            content = doPromotion(content, offer);
        }
    }

    /**
     * Get the immediate sub-expressions of this expression.
     *
     * @return an iterator containing the sub-expressions of this expression
     */

    /*@NotNull*/
    public Iterator<Expression> iterateSubExpressions() {
        return new MonoIterator<Expression>(content);
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
        return new MonoIterator<SubExpressionInfo>(
                new SubExpressionInfo(content, true, false, NODE_VALUE_CONTEXT));
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
        if (content == original) {
            content = replacement;
            found = true;
        }
        return found;
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true.
     */

    public final boolean createsNewNodes() {
        return true;
    }

    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Check that lazy construction is possible for this element
     */

    void verifyLazyConstruction() {
        if (!isLazyConstruction()) {
            return;
        }
        // Lazy construction is not possible if the expression depends on the values of position() or last(),
        // as we can't save these.
        if ((getDependencies() & (StaticProperty.DEPENDS_ON_POSITION | StaticProperty.DEPENDS_ON_LAST)) != 0) {
            setLazyConstruction(false);
        }
        // Lazy construction is not possible if validation is required
        if (getValidationAction() == Validation.STRICT || getValidationAction() == Validation.LAX
                || getSchemaType() != null) {
            setLazyConstruction(false);
        }
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
        PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        result.setReturnable(false);
        TypeHierarchy th = getExecutable().getConfiguration().getTypeHierarchy();
        ItemType type = getItemType(th);
        if (th.relationship(type, NodeKindTest.ELEMENT) != TypeHierarchy.DISJOINT ||
                th.relationship(type, NodeKindTest.DOCUMENT) != TypeHierarchy.DISJOINT) {
            // ?? surely this condition is always true?
            result.addDescendants();
        }
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }

    /**
     * Determine whether this elementCreator performs validation or strips type annotations
     *
     * @return false if the instruction performs validation of the constructed output or if it strips
     *         type annotations, otherwise true
     */

    public boolean isPreservingTypes() {
        return preservingTypes;
    }
}


