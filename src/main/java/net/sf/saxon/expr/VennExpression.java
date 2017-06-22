////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.*;


/**
* An expression representing a nodeset that is a union, difference, or
* intersection of two other NodeSets
*/

public class VennExpression extends BinaryExpression {

    /**
    * Constructor
    * @param p1 the left-hand operand
    * @param op the operator (union, intersection, or difference)
    * @param p2 the right-hand operand
    */

    public VennExpression(final Expression p1, final int op, final Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        switch (operator) {
            case Token.UNION:       return "union";
            case Token.INTERSECT:   return "intersect";
            case Token.EXCEPT:      return "except";
            default:                return "unknown";
        }
    }

    /**
    * Determine the data type of the items returned by this expression
    * @return the data type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public final ItemType getItemType(TypeHierarchy th) {
        final ItemType t1 = operand0.getItemType(th);
        if (operator == Token.UNION) {
            final ItemType t2 = operand1.getItemType(th);
            return Type.getCommonSuperType(t1, t2, th);
        } else {
            return t1;
        }
    }

    /**
    * Determine the static cardinality of the expression
    */

    public final int computeCardinality() {
        final int c1 = operand0.getCardinality();
        final int c2 = operand1.getCardinality();
        switch (operator) {
            case Token.UNION:
                if (Literal.isEmptySequence(operand0)) return c2;
                if (Literal.isEmptySequence(operand1)) return c1;
                return c1 | c2 | StaticProperty.ALLOWS_ONE | StaticProperty.ALLOWS_MANY;
                    // allows ZERO only if one operand allows ZERO
            case Token.INTERSECT:
                if (Literal.isEmptySequence(operand0)) return StaticProperty.EMPTY;
                if (Literal.isEmptySequence(operand1)) return StaticProperty.EMPTY;
                return (c1 & c2) | StaticProperty.ALLOWS_ZERO | StaticProperty.ALLOWS_ONE;
                    // allows MANY only if both operands allow MANY
            case Token.EXCEPT:
                if (Literal.isEmptySequence(operand0)) return StaticProperty.EMPTY;
                if (Literal.isEmptySequence(operand1)) return c1;
                return c1 | StaticProperty.ALLOWS_ZERO | StaticProperty.ALLOWS_ONE;
                    // allows MANY only if first operand allows MANY
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-signficant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        final int prop0 = operand0.getSpecialProperties();
        final int prop1 = operand1.getSpecialProperties();
        int props = StaticProperty.ORDERED_NODESET;
        if (testContextDocumentNodeSet(prop0, prop1)) {
            props |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        if (testSubTree(prop0, prop1)) {
            props |= StaticProperty.SUBTREE_NODESET;
        }
        if (!testCreative(prop0, prop1)) {
            props |= StaticProperty.NON_CREATIVE;
        }
        return props;
    }

    /**
     * Determine whether all the nodes in the node-set are guaranteed to
     * come from the same document as the context node. Used for optimization.
     * @param prop0 contains the Context Document Nodeset property of the first operand
     * @param prop1 contains the Context Document Nodeset property of the second operand
     * @return true if all the nodes come from the context document
     */

    private boolean testContextDocumentNodeSet(final int prop0, final int prop1) {
        switch (operator) {
            case Token.UNION:
                return (prop0 & prop1 & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
            case Token.INTERSECT:
                return ((prop0 | prop1) & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
            case Token.EXCEPT:
                return (prop0 & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
        }
        return false;
    }

    /**
     * Gather the component operands of a union or intersect expression
     * @param operator union or intersect
     * @param set the set into which the components are to be gathered. If the operator
     * is union, this follows the tree gathering all operands of union expressions. Ditto,
     * mutatis mutandis, for intersect expressions.
     */

    public void gatherComponents(int operator, Set set) {
        if (operand0 instanceof VennExpression && ((VennExpression)operand0).operator == operator) {
            ((VennExpression)operand0).gatherComponents(operator, set);
        } else {
            set.add(operand0);
        }
        if (operand1 instanceof VennExpression && ((VennExpression)operand1).operator == operator) {
            ((VennExpression)operand1).gatherComponents(operator, set);
        } else {
            set.add(operand1);
        }
    }

    /**
     * Determine whether all the nodes in the node-set are guaranteed to
     * come from a subtree rooted at the context node. Used for optimization.
     * @param prop0 contains the SubTree property of the first operand
     * @param prop1 contains the SubTree property of the second operand
     * @return true if all the nodes come from the tree rooted at the context node
     */

    private boolean testSubTree(final int prop0, final int prop1) {
        switch (operator) {
            case Token.UNION:
                return (prop0 & prop1 & StaticProperty.SUBTREE_NODESET) != 0;
            case Token.INTERSECT:
                return ((prop0 | prop1) & StaticProperty.SUBTREE_NODESET) != 0;
            case Token.EXCEPT:
                return (prop0 & StaticProperty.SUBTREE_NODESET) != 0;
        }
        return false;
    }

    /**
     * Determine whether the expression can create new nodes
     * @param prop0 contains the noncreative property of the first operand
     * @param prop1 contains the noncreative property of the second operand
     * @return true if the expression can create new nodes
     */

    private boolean testCreative(final int prop0, final int prop1) {
        return !(((prop0 & StaticProperty.NON_CREATIVE) != 0) &&
                ((prop1 & StaticProperty.NON_CREATIVE) != 0));
    }


    /**
    * Simplify the expression
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        operand0 = visitor.simplify(operand0);
        operand1 = visitor.simplify(operand1);
        return this;
    }

    /**
    * Type-check the expression
    */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, final ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
       
        operand0 = visitor.typeCheck(operand0, contextItemType);
        operand1 = visitor.typeCheck(operand1, contextItemType);

        if (!(operand0 instanceof PatternSponsor)) {
            final RoleLocator role0 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 0);
            operand0 = TypeChecker.staticTypeCheck(operand0, SequenceType.NODE_SEQUENCE, false, role0, visitor);
        }

        if (!(operand1 instanceof PatternSponsor)) {
            final RoleLocator role1 = new RoleLocator(RoleLocator.BINARY_EXPR, Token.tokens[operator], 1);
            operand1 = TypeChecker.staticTypeCheck(operand1, SequenceType.NODE_SEQUENCE, false, role1, visitor);
        }

        // For the intersect and except operators, if the types are disjoint then we can simplify
        if (operator != Token.UNION) {
            TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            ItemType t0 = operand0.getItemType(th);
            ItemType t1 = operand1.getItemType(th);
            if (th.relationship(t0, t1) == TypeHierarchy.DISJOINT) {
                if (operator == Token.INTERSECT) {
                    return Literal.makeEmptySequence();
                } else {
                    if ((operand0.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                        return operand0;
                    } else {
                        return new DocumentSorter(operand0);
                    }
                }
            }
        }

        return this;
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
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
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }

        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();

        // If either operand is an empty sequence, simplify the expression. This can happen
        // after reduction with constructs of the form //a[condition] | //b[not(condition)],
        // common in XPath 1.0 because there were no conditional expressions.

        switch (operator) {
            case Token.UNION:
                if (Literal.isEmptySequence(operand0) &&
                        (operand1.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) return operand1;
                if (Literal.isEmptySequence(operand1) &&
                        (operand0.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) return operand0;
                break;
            case Token.INTERSECT:
                if (Literal.isEmptySequence(operand0)) return operand0;
                if (Literal.isEmptySequence(operand1)) return operand1;
                break;
            case Token.EXCEPT:
                if (Literal.isEmptySequence(operand0)) return operand0;
                if (Literal.isEmptySequence(operand1) &&
                        (operand0.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) return operand0;
                break;
        }

        // If both are axis expressions on the same axis, merge them
        // ie. rewrite (axis::test1 | axis::test2) as axis::(test1 | test2)

        if (operand0 instanceof AxisExpression && operand1 instanceof AxisExpression) {
            final AxisExpression a1 = (AxisExpression)operand0;
            final AxisExpression a2 = (AxisExpression)operand1;
            if (a1.getAxis() == a2.getAxis()) {
                AxisExpression ax = new AxisExpression(a1.getAxis(),
                             new CombinedNodeTest(a1.getNodeTest(),
                                                  operator,
                                                  a2.getNodeTest()));
                ExpressionTool.copyLocationInfo(this, ax);
                return ax;
            }
        }

        // If both are path expressions starting the same way, merge them
        // i.e. rewrite (/X | /Y) as /(X|Y). This applies recursively, so that
        // /A/B/C | /A/B/D becomes /A/B/child::(C|D)

        // This optimization was previously done for all three operators. However, it's not safe for "except":
        // A//B except A//C//B cannot be rewritten as A/descendant-or-self::node()/(B except C//B). As a quick
        // fix, the optimization has been retained for "union" but dropped for "intersect" and "except". Need to
        // do a more rigorous analysis of the conditions under which it is safe.

        // TODO: generalize this code to handle all distributive operators

        if (operand0 instanceof SlashExpression && operand1 instanceof SlashExpression && operator==Token.UNION) {
            final SlashExpression path1 = (SlashExpression)operand0;
            final SlashExpression path2 = (SlashExpression)operand1;

            if (path1.getFirstStep().equals(path2.getFirstStep())) {
                final VennExpression venn = new VennExpression(
                                            path1.getRemainingSteps(),
                                            operator,
                                            path2.getRemainingSteps());
                ExpressionTool.copyLocationInfo(this, venn);
                final Expression path = ExpressionTool.makePathExpression(path1.getFirstStep(), venn, false);
                ExpressionTool.copyLocationInfo(this, path);
                return visitor.optimize(path, contextItemType);
            }
        }

        // Try merging two non-positional filter expressions:
        // A[exp0] | A[exp1] becomes A[exp0 or exp1]

        if (operand0 instanceof FilterExpression && operand1 instanceof FilterExpression) {
            final FilterExpression exp0 = (FilterExpression)operand0;
            final FilterExpression exp1 = (FilterExpression)operand1;

            if (!exp0.isPositional(th) &&
                    !exp1.isPositional(th) &&
                    exp0.getControllingExpression().equals(exp1.getControllingExpression())) {
                final Expression filter;
                switch (operator) {
                    case Token.UNION:
                        filter = new OrExpression(exp0.getFilter(),
                                exp1.getFilter());
                        break;
                    case Token.INTERSECT:
                        filter = new AndExpression(exp0.getFilter(),
                                exp1.getFilter());
                        break;
                    case Token.EXCEPT:
                        final FunctionCall negate2 = SystemFunctionCall.makeSystemFunction(
                                "not", new Expression[]{exp1.getFilter()});
                        filter = new AndExpression(exp0.getFilter(),
                                negate2);
                        break;
                    default:
                        throw new AssertionError("Unknown operator " + operator);
                }
                ExpressionTool.copyLocationInfo(this, filter);
                FilterExpression f = new FilterExpression(exp0.getControllingExpression(), filter);
                ExpressionTool.copyLocationInfo(this, f);
                return f.simplify(visitor).typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
            }
        }

        // Convert @*|node() into @*,node() to eliminate the sorted merge operation
        // Avoid doing this when streaming because xsl:value-of select="@*,node()" is not currently streamable
        if (!visitor.isOptimizeForStreaming() && operator == Token.UNION &&
                operand0 instanceof AxisExpression && operand1 instanceof AxisExpression) {
            AxisExpression a0 = (AxisExpression)operand0;
            AxisExpression a1 = (AxisExpression)operand1;
            if (a0.getAxis() == AxisInfo.ATTRIBUTE && a1.getAxis() == AxisInfo.CHILD) {
                Block b = new Block();
                b.setChildren(new Expression[]{operand0, operand1});
                return b;
            } else if (a1.getAxis() == AxisInfo.ATTRIBUTE && a0.getAxis() == AxisInfo.CHILD) {
                Block b = new Block();
                b.setChildren(new Expression[]{operand1, operand0});
                return b;
            }
        }

        // Convert (A intersect B) to use a serial search where one operand is a singleton
        if (operator == Token.INTERSECT && !Cardinality.allowsMany(operand0.getCardinality())) {
            return new SingletonIntersectExpression(operand0, operator,
                    ExpressionTool.unsorted(config.obtainOptimizer(), operand1, false));
        }
        if (operator == Token.INTERSECT && !Cardinality.allowsMany(operand1.getCardinality())) {
            return new SingletonIntersectExpression(operand1, operator,
                    ExpressionTool.unsorted(config.obtainOptimizer(), operand0, false));
        }

        // If the types of the operands are disjoint, simplify "intersect" and "except"
        if (operandsAreDisjoint(th)) {
            if (operator == Token.INTERSECT) {
                return Literal.makeEmptySequence();
            } else if (operator == Token.EXCEPT) {
                if ((operand0.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                    return operand0;
                } else {
                    return new DocumentSorter(operand0);
                }
            }
        }
        return this;
    }

    private boolean operandsAreDisjoint(TypeHierarchy th) {
        return th.relationship(operand0.getItemType(th), operand1.getItemType(th)) == TypeHierarchy.DISJOINT;
    }

    /**
    * Promote this expression if possible
    */

    public Expression promote(PromotionOffer offer, Expression parent) throws XPathException {
        if (offer.action == PromotionOffer.UNORDERED) {
            if (operator == Token.UNION && operandsAreDisjoint(offer.getOptimizer().getConfiguration().getTypeHierarchy())) {
                // replace union operator by comma operator to avoid cost of sorting into document order. See XMark q7
                Block block = new Block();
                block.setChildren(new Expression[]{operand0, operand1});
                ExpressionTool.copyLocationInfo(this, block);
                return block;
            }
            Expression exp = offer.accept(parent, this);
            if (exp != null) {
                return exp;
            } else {
                if (offer.action == PromotionOffer.UNORDERED) {
                    operand0 = doPromotion(operand0, offer);
                    operand1 = doPromotion(operand1, offer);
                }
                return this;
            }
        } else {
            return super.promote(offer, parent);
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        return new VennExpression(operand0.copy(), operator, operand1.copy());
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
        SubExpressionInfo i0 = new SubExpressionInfo(operand0, true, false, INHERITED_CONTEXT);
        SubExpressionInfo i1 = new SubExpressionInfo(operand1, true, false, INHERITED_CONTEXT);
        return new PairIterator<SubExpressionInfo>(i0, i1);
    }



    /**
    * Is this expression the same as another expression?
    */

    public boolean equals(Object other) {
        // NOTE: it's possible that the method in the superclass is already adequate for this
        if (other instanceof VennExpression) {
            VennExpression b = (VennExpression)other;
            if (operator != b.operator) {
                return false;
            }
            if (operand0.equals(b.operand0) && operand1.equals(b.operand1)) {
               return true;
            }
            if (operator == Token.UNION || operator == Token.INTERSECT) {
                // These are commutative and associative, so for example (A|B)|C equals B|(A|C)
                Set s0 = new HashSet(10);
                gatherComponents(operator, s0);
                Set s1 = new HashSet(10);
                ((VennExpression)other).gatherComponents(operator, s1);
                return s0.equals(s1);
            }
        }
        return false;
    }

    public int hashCode() {
        return operand0.hashCode() ^ operand1.hashCode();
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @param is30   true if this is XSLT 3.0
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean is30) throws XPathException {
            if (operator == Token.UNION) {
                return new UnionPattern(
                        operand0.toPattern(config, is30),
                        operand1.toPattern(config, is30));
            } else if (is30) {
                if (operator == Token.EXCEPT) {
                    return new ExceptPattern(
                            operand0.toPattern(config, is30),
                            operand1.toPattern(config, is30));
                } else {
                    return new IntersectPattern(
                            operand0.toPattern(config, is30),
                            operand1.toPattern(config, is30));
                }
            } else {
                throw new XPathException("Cannot use intersect or except in an XSLT 2.0 pattern");
            }
    }

//#ifdefined STREAM

    /**
     * Convert this expression to a streaming pattern (a pattern used internally to match nodes during
     * push processing of an event stream)
     *
     * @param config           the Saxon configuration
     * @param reasonForFailure a list which will be populated with messages giving reasons why the
     *                         expression cannot be converted
     * @return the equivalent pattern if conversion succeeds; otherwise null
     */
    @Override
    public Pattern toStreamingPattern(Configuration config, List<String> reasonForFailure) {
        Pattern result;
        if (operator == Token.UNION) {
            result = new UnionPattern(
                    operand0.toStreamingPattern(config, reasonForFailure),
                    operand1.toStreamingPattern(config, reasonForFailure));
        } else if (operator == Token.EXCEPT) {
            result = new ExceptPattern(
                    operand0.toStreamingPattern(config, reasonForFailure),
                    operand1.toStreamingPattern(config, reasonForFailure));
        } else {
            result = new IntersectPattern(
                    operand0.toStreamingPattern(config, reasonForFailure),
                    operand1.toStreamingPattern(config, reasonForFailure));
        }
        if (!reasonForFailure.isEmpty()) {
            return null;
        } else {
            return result;
        }
    }

//#endif

    /**
    * Iterate over the value of the expression. The result will always be sorted in document order,
    * with duplicates eliminated
    * @param c The context for evaluation
    * @return a SequenceIterator representing the union of the two operands
    */

    /*@NotNull*/
    public SequenceIterator iterate(final XPathContext c) throws XPathException {
        SequenceIterator i1 = operand0.iterate(c);
        //return Type.isNodeType(getItemType()) && isSingleton();
        // this is a sufficient condition, but other expressions override this method
        if ((operand0.getSpecialProperties() & StaticProperty.ORDERED_NODESET) == 0) {
            i1 = new DocumentOrderIterator(i1, GlobalOrderComparer.getInstance());
        }
        SequenceIterator i2 = operand1.iterate(c);
        //return Type.isNodeType(getItemType()) && isSingleton();
        // this is a sufficient condition, but other expressions override this method
        if ((operand1.getSpecialProperties() & StaticProperty.ORDERED_NODESET) == 0) {
            i2 = new DocumentOrderIterator(i2, GlobalOrderComparer.getInstance());
        }
        switch (operator) {
            case Token.UNION:
                return new UnionEnumeration(i1, i2,
                                            GlobalOrderComparer.getInstance());
            case Token.INTERSECT:
                return new IntersectionEnumeration(i1, i2,
                                            GlobalOrderComparer.getInstance());
            case Token.EXCEPT:
                return new DifferenceEnumeration(i1, i2,
                                            GlobalOrderComparer.getInstance());
        }
        throw new UnsupportedOperationException("Unknown operator in Venn Expression");
    }

    /**
    * Get the effective boolean value. In the case of a union expression, this
    * is reduced to an OR expression, for efficiency
    */

    public boolean effectiveBooleanValue(final XPathContext context) throws XPathException {
        if (operator == Token.UNION) {
            // NOTE: this optimization was probably already done statically
            return operand0.effectiveBooleanValue(context) || operand1.effectiveBooleanValue(context);
        } else {
            return super.effectiveBooleanValue(context);
        }
    }



}

