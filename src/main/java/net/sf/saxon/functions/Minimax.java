////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.DescendingComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import static net.sf.saxon.expr.CastExpression.CAST_EX_CODE;
import static net.sf.saxon.functions.Sum.SUM_EX_CODE;
import static net.sf.saxon.util.ExceptionBuilder.newEx;

/**
 * This class implements the min() and max() functions
 */

public abstract class Minimax extends CollatingFunction {

    public static final int MIN = 2;
    public static final int MAX = 3;

    private BuiltInAtomicType argumentType = BuiltInAtomicType.ANY_ATOMIC;
    private boolean ignoreNaN = false;

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 1;
    }

    /**
     * Indicate whether NaN values should be ignored. For the external min() and max() function, a
     * NaN value in the input causes the result to be NaN. Internally, however, min() and max() are also
     * used in such a way that NaN values should be ignored.
     *
     * @param ignore true if NaN values are to be ignored when computing the min or max.
     */

    public void setIgnoreNaN(boolean ignore) {
        ignoreNaN = ignore;
    }

    /**
     * Test whether NaN values are to be ignored
     *
     * @return true if NaN values are to be ignored. This is the case for internally-generated min() and max()
     *         functions used to support general comparisons
     */

    public boolean isIgnoreNaN() {
        return ignoreNaN;
    }



    public AtomicComparer getComparer() {
        return getPreAllocatedAtomicComparer();
    }

    public BuiltInAtomicType getArgumentType() {
        return argumentType;
    }

    /**
     * Get implementation method
     *
     * @return a value that indicates this function is capable of being streamed
     */

    public int getImplementationMethod() {
        return super.getImplementationMethod() | ITEM_FEED_METHOD;
    }

    /**
     * Static analysis: prevent sorting of the argument and preallocate a comparer if possible
     */

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        Optimizer opt = visitor.getConfiguration().obtainOptimizer();
        argument[0] = ExpressionTool.unsorted(opt, argument[0], false);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType type = argument[0].getItemType(th);
        if (type instanceof AtomicType) {
            if (type == BuiltInAtomicType.UNTYPED_ATOMIC) {
                type = BuiltInAtomicType.DOUBLE;
            }
            preAllocateComparer((AtomicType)type, (AtomicType)type, visitor.getStaticContext(), false);
        }
    }

    /**
     * Determine the cardinality of the function.
     */

    public int computeCardinality() {
        int c = super.computeCardinality();
        if (!Cardinality.allowsZero(argument[0].getCardinality())) {
            c = StaticProperty.EXACTLY_ONE;
        }
        return c;
    }


    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        StaticContext env = visitor.getStaticContext();
        if(Literal.isEmptySequence(argument[0])){
            return argument[0];
        }
        argumentType = (BuiltInAtomicType) argument[0].getItemType(th).getPrimitiveItemType();

        PlainType t0 = (PlainType) argument[0].getItemType(th);

        if (t0.isExternalType()) {
            XPathException err = new XPathException("Cannot perform computation involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocator(this);
            throw err;
        }

        BuiltInAtomicType p0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        if (p0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p0 = BuiltInAtomicType.DOUBLE;
        }

        //needsRuntimeCheck = p0.equals(BuiltInAtomicType.ANY_ATOMIC);

//        if (comparer == null) {
//            StringCollator comp = getDefaultCollation();
//            if (comp != null) {
//                comparer = GenericAtomicComparer.makeAtomicComparer(
//                        p0, p0, comp, env.getConfiguration().getConversionContext());
//            }
//        }

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
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        if (getNumberOfArguments() == 1) {
            // test for a singleton: this often happens after (A<B) is rewritten as (min(A) lt max(B))
            int card = argument[0].getCardinality();
            if (!Cardinality.allowsMany(card) && th.isSubType(argument[0].getItemType(th), BuiltInAtomicType.NUMERIC)) {
                return argument[0];
            }
        }
        return this;
    }

    /**
     * Determine the item type of the value returned by the function
     *
     * @param th the type hierarchy cache
     * @return the statically inferred type of the expression
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        ItemType t = Atomizer.getAtomizedItemType(argument[0], false, th);
        if (t.getPrimitiveType() == StandardNames.XS_UNTYPED_ATOMIC) {
            return BuiltInAtomicType.DOUBLE;
        } else {
            return t;
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        Minimax m = (Minimax) super.copy();
        m.argumentType = argumentType;
        m.ignoreNaN = ignoreNaN;
        return m;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return this==o;
    }

    /**
     * Evaluate the function
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer == null) {
            comparer = getAtomicComparer(getCollator(context), context);
        }
        SequenceIterator iter = argument[0].iterate(context);
        try {
            return minimax(iter, operation, comparer, ignoreNaN, context);
        } catch (XPathException err) {
            err.setLocator(this);
            throw err;
        }
    }

    public AtomicComparer getAtomicComparer(XPathContext context) throws XPathException {
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer != null) {
            return comparer;
        }
        StringCollator collator = getCollator(context);
        BuiltInAtomicType type = argumentType;
        if (type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            type = BuiltInAtomicType.DOUBLE;
        }
        return GenericAtomicComparer.makeAtomicComparer(type, type, collator, context);
    }

    /**
     * Static method to evaluate the minimum or maximum of a sequence
     *
     * @param iter           Iterator over the input sequence
     * @param operation      either {@link #MIN} or {@link #MAX}
     * @param atomicComparer an AtomicComparer used to compare values
     * @param ignoreNaN      true if NaN values are to be ignored
     * @param context        dynamic evaluation context
     * @return the min or max value in the sequence, according to the rules of the fn:min() or fn:max() functions
     * @throws XPathException typically if non-comparable values are found in the sequence
     */
    /*@Nullable*/ public static AtomicValue minimax(SequenceIterator iter, int operation,
                                      AtomicComparer atomicComparer, boolean ignoreNaN, XPathContext context)
            throws XPathException {

        TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        ConversionRules rules = context.getConfiguration().getConversionRules();
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        StringConverter converter2 = StringConverter.STRING_TO_DAY_TIME_DURATION;
        boolean foundDouble = false;
        boolean foundFloat = false;
        boolean foundNaN = false;
        boolean useString = false;

        // For the max function, reverse the collator
        if (operation == MAX) {
            atomicComparer = new DescendingComparer(atomicComparer);
        }
        atomicComparer = atomicComparer.provideContext(context);

        // Process the sequence, retaining the min (or max) so far. This will be an actual value found
        // in the sequence. At the same time, remember if a double and/or float has been encountered
        // anywhere in the sequence, and if so, convert the min/max to double/float at the end. This is
        // done to avoid problems if a decimal is converted first to a float and then to a double.

        // Get the first value in the sequence, ignoring any NaN values if we are ignoring NaN values
        AtomicValue min;
        AtomicValue prim;

        while (true) {
            min = (AtomicValue) iter.next();
            if (min == null) {
                return null;
            }
            prim = min;
            if (min instanceof UntypedAtomicValue) {
                try {
                    min = new DoubleValue(converter.stringToNumber(min.getStringValueCS()));
                    prim = min;
                    foundDouble = true;
                    
                } catch (NumberFormatException e) {
                    /*XPathException de = new XPathException("Failure converting " + Err.wrap(min.getStringValueCS()) + " to a number");
                    de.setErrorCode("FORG0001");
                    de.setXPathContext(context);
                    throw de;*/
                	min = new StringValue(min.getStringValue());
                	useString = true;
                }
            } else {
                if (prim instanceof DoubleValue) {
                    foundDouble = true;
                } else if (prim instanceof FloatValue) {
                    foundFloat = true;
                }
            }
            if (prim.isNaN()) {
                // if there's a NaN in the sequence, return NaN, unless ignoreNaN is set
                if (ignoreNaN) {
                    //continue;   // ignore the NaN and treat the next item as the first real one
                } else if (prim instanceof DoubleValue) {
                    return min; // return double NaN
                } else {
                    // we can't ignore a float NaN, because we might need to promote it to a double NaN
                    foundNaN = true;
                    min = FloatValue.NaN;
                    break;
                }
            } else {
                if (!prim.getPrimitiveType().isOrdered(false)) {
                    throw newEx("Type " + prim.getPrimitiveType() + " is not an ordered type", true, SUM_EX_CODE, context);
                }
                break;          // process the rest of the sequence
            }
        }

        AtomicType lowestCommonSuperType = min.getItemType();

        while (true) {
            AtomicValue test = (AtomicValue) iter.next();
            if (test == null) {
                break;
            }
            AtomicValue test2 = test;
            prim = test2;
            if (test instanceof UntypedAtomicValue) {
                try {
                	if (!useString) {
                		test2 = new DoubleValue(converter.stringToNumber(test.getStringValueCS()));
                		if (foundNaN) {
                			return DoubleValue.NaN;
                		}
                		prim = test2;
                		foundDouble = true;
                	} else {
                		test2 = new StringValue(min.getStringValue());
                		prim = test2;
                	}
                } catch (NumberFormatException e) {
                    throw newEx("Failure converting " + Err.wrap(test.getStringValueCS()) + " to a number", false,
                            CAST_EX_CODE, context);
                }
            } else {
                if (prim instanceof DoubleValue) {
                    if (foundNaN) {
                        return DoubleValue.NaN;
                    }
                    foundDouble = true;
                } else if (prim instanceof FloatValue) {
                    foundFloat = true;
                }
            }
            lowestCommonSuperType = (AtomicType) Type.getCommonSuperType(
                    lowestCommonSuperType, prim.getItemType(), th);
            if (prim.isNaN()) {
                // if there's a double NaN in the sequence, return NaN, unless ignoreNaN is set
                if (ignoreNaN) {
                    //continue;
                } else if (foundDouble) {
                    return DoubleValue.NaN;
                } else {
                    // can't return float NaN until we know whether to promote it
                    foundNaN = true;
                }
            } else {
                try {
                    if (atomicComparer.compareAtomicValues(prim, min) < 0) {
                        min = test2;
                    }
                } catch (ClassCastException err) {
                    if (min.getItemType() == test2.getItemType()) {
                        // internal error
                        throw err;
                    } else {;
                        throw newEx("Cannot compare " + min.getItemType() + " with " + test2.getItemType(), true, CAST_EX_CODE, context);
                    }
                }
            }
        }
        if (foundNaN) {
            return FloatValue.NaN;
        }
        if (foundDouble) {
            if (!(min instanceof DoubleValue)) {
                min = Converter.convert(min, BuiltInAtomicType.DOUBLE, rules);
            }
        } else if (foundFloat) {
            if (!(min instanceof FloatValue)) {
                min = Converter.convert(min, BuiltInAtomicType.FLOAT, rules);
            }
        }
        if (lowestCommonSuperType == BuiltInAtomicType.NUMERIC || min.getPrimitiveType() == lowestCommonSuperType) {
            return min;
        } else {
            return Converter.convert(min, lowestCommonSuperType, rules);
        }
    }



}

