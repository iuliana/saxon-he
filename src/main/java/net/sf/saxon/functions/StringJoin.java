////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.StringValue;

/**
 * xf:string-join(string* $sequence, string $separator)
 */

public class StringJoin extends SystemFunctionCall implements Callable {

    private boolean returnEmptyIfEmpty;

    /**
     * Indicate that when the input sequence (first argument) is empty, the function should return
     * an empty sequence rather than an empty string
     *
     * @param option true if an empty sequence should be returned when the input is an empty sequence.
     */

    public void setReturnEmptyIfEmpty(boolean option) {
        returnEmptyIfEmpty = option;
    }

    public boolean isReturnEmptyIfEmpty() {
        return returnEmptyIfEmpty;
    }

    /**
     * Determine the cardinality of the function.
     */
    @Override
    public int computeCardinality() {
        if (returnEmptyIfEmpty) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return StaticProperty.EXACTLY_ONE;
        }
    }

    /*@NotNull*/
    @Override
    public Expression copy() {
        StringJoin sj = (StringJoin) super.copy();
        sj.returnEmptyIfEmpty = returnEmptyIfEmpty;
        return sj;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof StringJoin) &&
                super.equals(o) &&
                returnEmptyIfEmpty == ((StringJoin)o).returnEmptyIfEmpty;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (returnEmptyIfEmpty ? 1 : 0);
        return result;
    }

    public int getImplementationMethod() {
        return super.getImplementationMethod() | ITEM_FEED_METHOD;
    }

    /*@NotNull*/
    public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType)
    throws XPathException {
        Expression exp = super.optimize(visitor, contextItemType);
        if (exp instanceof StringJoin) {
            Expression exp2 = ((StringJoin) exp).simplifySingleton();
            if (exp != exp2) {
                return visitor.optimize(exp2, contextItemType);
            }
        }
        return exp;
    }

    private Expression simplifySingleton() {
        int card = argument[0].getCardinality();
        if (!Cardinality.allowsMany(card)) {
            if (Cardinality.allowsZero(card)) {
                return SystemFunctionCall.makeSystemFunction("string", new Expression[]{argument[0]});
            } else {
                return argument[0];
            }
        }
        return this;
    }

    public StringValue evaluateItem(XPathContext c) throws XPathException {

        // This rather tortuous code is designed to ensure that we don't evaluate the
        // separator argument unless there are at least two items in the sequence.

        SequenceIterator iter = argument[0].iterate(c);
        Item it = iter.next();
        if (it == null) {
            return (returnEmptyIfEmpty ? null : StringValue.EMPTY_STRING);
        }

        CharSequence first = it.getStringValueCS();

        it = iter.next();
        if (it == null) {
            return StringValue.makeStringValue(first);
        }

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        sb.append(first);

        // Type checking ensures that the separator is not an empty sequence
        if (argument.length == 1) {
            sb.append(it.getStringValueCS());
            while (true) {
                it = iter.next();
                if (it == null) {
                    return StringValue.makeStringValue(sb.condense());
                }
                sb.append(it.getStringValueCS());
            }

        } else {
            Item sepItem = argument[1].evaluateItem(c);
            assert sepItem != null;
            CharSequence sep = sepItem.getStringValueCS();
            sb.append(sep);
            sb.append(it.getStringValueCS());

            while (true) {
                it = iter.next();
                if (it == null) {
                    return StringValue.makeStringValue(sb.condense());
                }
                sb.append(sep);
                sb.append(it.getStringValueCS());
            }
        }
    }

    /**
     * Process the instruction in push mode. This avoids constructing the concatenated string
     * in memory, instead its parts can be sent straight to the serializer.
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public void process(XPathContext context) throws XPathException {
        // This rather tortuous code is designed to ensure that we don't evaluate the
        // separator argument unless there are at least two items in the sequence.

        SequenceReceiver out = context.getReceiver();
        if (out instanceof ComplexContentOutputter) {
            // Optimization is only safe if evaluated as part of a complex content constructor
            // Start and end with an empty string to force space separation from any previous or following outputs
            out.append(StringValue.EMPTY_STRING, 0, 0);

            SequenceIterator iter = argument[0].iterate(context);
            Item it = iter.next();
            if (it == null) {
                return;
            }

            CharSequence first = it.getStringValueCS();
            out.characters(first, 0, 0);

            it = iter.next();
            if (it == null) {
                out.append(StringValue.EMPTY_STRING, 0, 0);
                return;
            }

            // Type checking ensures that the separator is not an empty sequence
            if (argument.length == 1) {
                out.characters(it.getStringValueCS(), 0, 0);

                while (true) {
                    it = iter.next();
                    if (it == null) {
                        break;
                    }
                    out.characters(it.getStringValueCS(), 0, 0);
                }
            } else {
                Item sepItem = argument[1].evaluateItem(context);
                assert sepItem != null;
                CharSequence sep = sepItem.getStringValueCS();
                out.characters(sep, 0, 0);
                out.characters(it.getStringValueCS(), 0, 0);

                while (true) {
                    it = iter.next();
                    if (it == null) {
                        break;
                    }
                    out.characters(sep, 0, 0);
                    out.characters(it.getStringValueCS(), 0, 0);
                }

            }
            out.append(StringValue.EMPTY_STRING, 0, 0);
        } else {
            out.append(evaluateItem(context), 0, 0);
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
        SequenceIterator iter = arguments[0].iterate();
        Item it = iter.next();
        if (it == null) {
            return (returnEmptyIfEmpty ? EmptySequence.getInstance() : StringValue.EMPTY_STRING);
        }

        CharSequence first = it.getStringValueCS();

        it = iter.next();
        if (it == null) {
            return StringValue.makeStringValue(first);
        }

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        sb.append(first);

        // Type checking ensures that the separator is not an empty sequence
        if (arguments.length == 1) {
            sb.append(it.getStringValueCS());
            while (true) {
                it = iter.next();
                if (it == null) {
                    return StringValue.makeStringValue(sb.condense());
                }
                sb.append(it.getStringValueCS());
            }

        } else {
            Item sepItem = arguments[1].head();
            assert sepItem != null;
            CharSequence sep = sepItem.getStringValueCS();
            sb.append(sep);
            sb.append(it.getStringValueCS());

            while (true) {
                it = iter.next();
                if (it == null) {
                    return StringValue.makeStringValue(sb.condense());
                }
                sb.append(sep);
                sb.append(it.getStringValueCS());
            }
        }
    }



}

