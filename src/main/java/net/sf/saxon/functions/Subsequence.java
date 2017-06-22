////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.SimplePositionalPattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;

import java.util.List;

/**
* Implements the XPath 2.0 subsequence()  function
*/


public class Subsequence extends SystemFunctionCall implements Callable {

    /**
    * Determine the data type of the items in the sequence
    * @return the type of the argument
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        return argument[0].getItemType(th);
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-significant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        return argument[0].getSpecialProperties();
    }


    /**
     * Determine the cardinality of the function.
     */

    public int computeCardinality() {
        if (getNumberOfArguments() == 3 && Literal.isConstantOne(argument[2])) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        return argument[0].getCardinality() | StaticProperty.ALLOWS_ZERO;
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
    public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        if (getNumberOfArguments() == 2 && Literal.isAtomic(argument[1]) && !(argument[0] instanceof ErrorExpression)) {
            NumericValue start = (NumericValue)((Literal)argument[1]).getValue();
            start = start.round(0);   // TODO: shouldn't we always round up?
            long intstart = start.longValue();
            if (intstart > Integer.MAX_VALUE) {
                return Literal.makeEmptySequence();
            }
            if (intstart <= 0) {
                return argument[0];
            }
            return new TailExpression(argument[0], (int)intstart);
        }
        return this;
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
        if (getNumberOfArguments() == 3 && Literal.isConstantOne(getArguments()[1])) {
                    // child::x[position() <= N]
            TypeHierarchy th = config.getTypeHierarchy();
            Expression base = getArguments()[0];
            if (base instanceof AxisExpression &&
                    ((AxisExpression)base).getAxis() == AxisInfo.CHILD &&
                    base.getItemType(th).getPrimitiveType() == Type.ELEMENT) {
                return new SimplePositionalPattern(
                        (NodeTest)base.getItemType(th),
                        getArguments()[2],
                        Token.FLE);
            }
        }
        return super.toStreamingPattern(config, reasonForFailure);
    }

//#endif

    /**
    * Evaluate the function to return an iteration of selected nodes.
    */

    /*@NotNull*/
    public SequenceIterator<? extends Item> iterate(XPathContext context) throws XPathException {
        SequenceIterator seq = argument[0].iterate(context);
        AtomicValue startVal0 = (AtomicValue)argument[1].evaluateItem(context);
        NumericValue startVal = (NumericValue)startVal0;

        if (argument.length == 2) {

        	return subSequence(seq, startVal, null, context);

        } else {

            // There are three arguments

            AtomicValue lengthVal0 = (AtomicValue)argument[2].evaluateItem(context);
            NumericValue lengthVal = (NumericValue)lengthVal0;

            return subSequence(seq, startVal, lengthVal, context);
        }
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException{

    	return SequenceTool.toLazySequence(subSequence(
                arguments[0].iterate(),
                (NumericValue) arguments[1].head(),
                (arguments.length == 2 ? null : (NumericValue) arguments[2].head()),
                context));

    }

    
    public static SequenceIterator subSequence(SequenceIterator seq, NumericValue startVal, NumericValue lengthVal, XPathContext context)
    throws XPathException{
    	

         if (lengthVal == (null)) {
             long lstart;
             if (startVal instanceof Int64Value) {
                 lstart = startVal.longValue();
                 if (lstart <= 1) {
                     return seq;
                 }
             } else if (startVal.isNaN()) {
                 return EmptyIterator.getInstance();
             } else {
                 startVal = startVal.round(0);
                 if (startVal.compareTo(Int64Value.PLUS_ONE) <= 0) {
                     return seq;
                 } else if (startVal.compareTo(Int64Value.MAX_LONG) > 0) {
                     return EmptyIterator.getInstance();
                 } else {
                     lstart = startVal.longValue();
                 }
             }

             if (lstart > Integer.MAX_VALUE) {
                 // we don't allow sequences longer than an this
                 return EmptyIterator.getInstance();
             }

             return TailIterator.make(seq, (int)lstart);

         } else {

             // There are three arguments


             if (startVal instanceof Int64Value && lengthVal instanceof Int64Value) {
                 long lstart = startVal.longValue();
                 if (lstart > Integer.MAX_VALUE) {
                     return EmptyIterator.getInstance();
                 }
                 long llength = lengthVal.longValue();
                 if (llength > Integer.MAX_VALUE) {
                     llength = Integer.MAX_VALUE;
                 }
                 if (llength < 1) {
                     return EmptyIterator.getInstance();
                 }
                 long lend = lstart + llength - 1;
                 if (lend < 1) {
                     return EmptyIterator.getInstance();
                 }
                 int start = (lstart < 1 ? 1 : (int)lstart);
                 return SubsequenceIterator.make(seq, start, (int)lend);
             } else {
                 if (startVal.isNaN()) {
                     return EmptyIterator.getInstance();
                 }
                 if (startVal.compareTo(Int64Value.MAX_LONG) > 0) {
                     return EmptyIterator.getInstance();
                 }
                 startVal = startVal.round(0);

                 if (lengthVal.isNaN()) {
                     return EmptyIterator.getInstance();
                 }
                 lengthVal = lengthVal.round(0);

                 if (lengthVal.compareTo(Int64Value.ZERO) <= 0) {
                     return EmptyIterator.getInstance();
                 }
                 NumericValue rend = (NumericValue)ArithmeticExpression.compute(
                         startVal, Calculator.PLUS, lengthVal, context);
                 if (rend.isNaN()) {
                     // Can happen when start = -INF, length = +INF
                     return EmptyIterator.getInstance();
                 }
                 rend = (NumericValue)ArithmeticExpression.compute(
                         rend, Calculator.MINUS, Int64Value.PLUS_ONE, context);
                 if (rend.compareTo(Int64Value.ZERO) <= 0) {
                     return EmptyIterator.getInstance();
                 }

                 long lstart;
                 if (startVal.compareTo(Int64Value.PLUS_ONE) <= 0) {
                     lstart = 1;
                 } else {
                     lstart = startVal.longValue();
                 }
                 if (lstart > Integer.MAX_VALUE) {
                     return EmptyIterator.getInstance();
                 }

                 long lend;
                 if (rend.compareTo(Int64Value.MAX_LONG) >= 0) {
                     lend = Integer.MAX_VALUE;
                 } else {
                     lend = rend.longValue();
                 }
                 return SubsequenceIterator.make(seq, (int)lstart, (int)lend);

             }
         }
    	
    }


}

