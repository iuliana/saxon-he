////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

/**
 * Implements the XPath number() function. 
 */

public class NumberFn extends SystemFunctionCall implements Callable {

    /**
    * Simplify and validate.
    * This is a pure function so it can be simplified in advance if the arguments are known
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        useContextItemAsDefault(visitor);
        argument[0].setFlattened(true);
        return simplifyArguments(visitor);
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        if (argument[0] instanceof NumberFn) {
            // happens through repeated rewriting
            argument[0] = ((NumberFn)argument[0]).argument[0];
        }
        return this;
    }

    /**
     * Add a representation of a doc() call or similar function to a PathMap.
     * This is a convenience method called by the addToPathMap() methods for doc(), document(), collection()
     * and similar functions. These all create a new root expression in the path map.
     *
     * @param pathMap      the PathMap to which the expression should be added
     * @param pathMapNodes the node in the PathMap representing the focus at the point where this expression
     *                     is called. Set to null if this expression appears at the top level.
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    /*@Nullable*/ public PathMap.PathMapNodeSet addDocToPathMap(/*@NotNull*/ PathMap pathMap, PathMap.PathMapNodeSet pathMapNodes) {
        PathMap.PathMapNodeSet result = argument[0].addToPathMap(pathMap, pathMapNodes);
        if (result != null) {
            result.setAtomized();
        }
        return null;
    }

    /**
    * Evaluate in a general context
    */

    public DoubleValue evaluateItem(XPathContext context) throws XPathException {
        Item arg0 = argument[0].evaluateItem(context);
        if (arg0==null) {
            return DoubleValue.NaN;
        }
        ConversionRules rules = context.getConfiguration().getConversionRules();
        if (arg0 instanceof BooleanValue) {
            return (DoubleValue)Converter.BOOLEAN_TO_DOUBLE.convert(((AtomicValue) arg0)).asAtomic();
        } else if (arg0 instanceof NumericValue) {
            return (DoubleValue)Converter.NUMERIC_TO_DOUBLE.convert(((AtomicValue) arg0)).asAtomic();
        } else if (arg0 instanceof StringValue && !(arg0 instanceof AnyURIValue)) {
            StringConverter sc = rules.getStringConverter(BuiltInAtomicType.DOUBLE);
            ConversionResult cr = sc.convert((AtomicValue)arg0);
            if (cr instanceof ValidationFailure) {
                return DoubleValue.NaN;
            } else {
                return (DoubleValue)cr;
            }
        } else {
            return DoubleValue.NaN;
        }
    }

    /**
     * Static method to perform the same conversion as the number() function. This is different from the
     * convert(Type.DOUBLE) in that it produces NaN rather than an error for non-numeric operands.
     * @param value the value to be converted
     * @param config
     * @return the result of the conversion
     */

    public static DoubleValue convert(AtomicValue value, Configuration config) {
        try {
            if (value==null) {
                return DoubleValue.NaN;
            }
            if (value instanceof BooleanValue) {
                return new DoubleValue(((BooleanValue)value).getBooleanValue() ? 1.0e0 : 0.0e0);
            }
            if (value instanceof DoubleValue) {
                return ((DoubleValue)value);
            }
            if (value instanceof NumericValue) {
                return new DoubleValue(((NumericValue)value).getDoubleValue());
            }
            if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
                double d = config.getConversionRules().getStringToDoubleConverter().stringToNumber(value.getStringValueCS());
                return new DoubleValue(d);
            }
            return DoubleValue.NaN;
        } catch (NumberFormatException e) {
            return DoubleValue.NaN;
        }
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue in;
        if (arguments.length == 0) {
            Item c = context.getContextItem();
            if (c == null) {
                throw new XPathException("Context item for number() is absent");
            }
            if (c instanceof AtomicValue) {
                in = (AtomicValue)c;
            } else if (c instanceof NodeInfo) {
                AtomicSequence as = ((NodeInfo)c).atomize();
                if (as.getLength() == 0) {
                    return DoubleValue.NaN;
                } else if (as.getLength() == 1) {
                    in = as.head();
                } else {
                    throw new XPathException("Atomization of context item for number() is a sequence containing more than one item", "XPTY0004");
                }
            } else {
                throw new XPathException("Context item for number() cannot be atomized", "XPTY0004");
            }
        } else {
            in = (AtomicValue)arguments[0].head();
            if (in == null) {
                return EmptySequence.getInstance();
            }
        }
        return convert(in, context.getConfiguration());
    }


}

