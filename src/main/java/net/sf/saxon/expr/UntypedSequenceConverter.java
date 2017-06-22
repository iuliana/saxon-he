////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * An UntypedSequenceConverter is an expression that performs a cast on each member of
 * a supplied sequence that is an untypedAtomic value, while leaving other items unchanged
 */

public final class UntypedSequenceConverter extends AtomicSequenceConverter {

    /**
     * Constructor
     *
     * @param sequence         this must be a sequence of atomic values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the item type to which all items in the sequence should be converted,
     *                         using the rules for "cast as".
     */

    public UntypedSequenceConverter(Expression sequence, PlainType requiredItemType) {
        super(sequence, requiredItemType);
    }

    /**
     * Create an AtomicSequenceConverter that converts all untypedAtomic values in the input sequence to
     * a specified target type, while leaving items other than untypedAtomic unchanged
     * @param config the Saxon configuration
     * @param operand the expression that delivers the input sequence
     * @param requiredItemType the type to which untypedAtomic values should be cast, which must either be an
     * atomic type or a "plain" union type
     * @return an AtomicSequenceConverter that performs the required conversion
     * @throws net.sf.saxon.trans.XPathException if an error occurs, for example if the target type is namespace-sensitive
     */


    public static UntypedSequenceConverter makeUntypedSequenceConverter(Configuration config, Expression operand, PlainType requiredItemType)
    throws XPathException {
        TypeHierarchy th = config.getTypeHierarchy();
        UntypedSequenceConverter atomicSeqConverter =
                new UntypedSequenceConverter(operand, requiredItemType);
        final ConversionRules rules = config.getConversionRules();
        final Converter untypedConverter;
        if (((SimpleType)requiredItemType).isNamespaceSensitive()) {
            throw new XPathException("Cannot convert untyped atomic values to a namespace-sensitive type", "XPTY0117");
        }
        if (requiredItemType.isAtomicType()) {
            untypedConverter = rules.getConverter(BuiltInAtomicType.UNTYPED_ATOMIC, (AtomicType)requiredItemType);
        } else {
            untypedConverter = new StringConverter.StringToUnionConverter(requiredItemType, rules);
        }
        // source type not known statically; create a converter that decides at run-time
        Converter converter = new UntypedConverter(rules, untypedConverter);
        atomicSeqConverter.setConverter(converter);
        return atomicSeqConverter;
    }

    /**
     * A Converter that converts untyped atomic values to the required type, while
     * leaving other values unchanged
     */

    public static class UntypedConverter extends Converter {
        Converter untypedConverter = null;

        /**
         * Create an UntypedConverter
         * @param rules the conversion rules
         * @param converter the converter to be used in the case where the supplied
         * value is untypedAtomic
         */

        public UntypedConverter(ConversionRules rules, Converter converter) {
            super(rules);
            untypedConverter = converter;
            //untypedConverter.setConversionRules(rules);

        }

        /*@NotNull*/
        @Override
        public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
            if (input instanceof UntypedAtomicValue) {
                return untypedConverter.convert(input);
            } else {
                return input;
            }
        }
    }

    public static UntypedSequenceConverter makeUntypedSequenceRejector(Configuration config, final Expression operand, final PlainType requiredItemType) {
        UntypedSequenceConverter atomicSeqConverter = new UntypedSequenceConverter(operand, requiredItemType);
        final ConversionRules rules = config.getConversionRules();
        final Converter untypedConverter = new Converter() {
            // called when an untyped atomic value is encountered
            public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
                ValidationFailure vf = new ValidationFailure(
                        "Implicit conversion of untypedAtomic value to " + requiredItemType.toString() + " is not allowed");
                vf.setErrorCode("XPTY0117");
                vf.setLocator(operand);
                return vf;
            }
        };

        // source type not known statically; create a converter that decides at run-time
        Converter converter = new UntypedConverter(rules, untypedConverter);
        atomicSeqConverter.setConverter(converter);
        return atomicSeqConverter;
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (th.relationship(operand.getItemType(th), BuiltInAtomicType.UNTYPED_ATOMIC) == TypeHierarchy.DISJOINT) {
            // operand cannot return untyped atomic values, so there's nothing to convert
            return operand;
        }
        return this;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p  | StaticProperty.NON_CREATIVE | StaticProperty.NOT_UNTYPED_ATOMIC;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        UntypedSequenceConverter atomicConverter = new UntypedSequenceConverter(getBaseExpression().copy(), getRequiredItemType());
        atomicConverter.setConverter(converter);
        return atomicConverter;
    }


    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @param th the type hierarchy cache
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    public ItemType getItemType(TypeHierarchy th) {
        if (operand.getItemType(th) == BuiltInAtomicType.UNTYPED_ATOMIC) {
            return getRequiredItemType();
        } else {
            return Type.getCommonSuperType(getRequiredItemType(), operand.getItemType(th), th);
        }
    }

    /**
     * Determine the static cardinality of the expression
     */

    public int computeCardinality() {
        return operand.getCardinality();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof UntypedSequenceConverter && super.equals(other);
    }

    /**
     * get HashCode for comparing two expressions.
     */

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "convertUntyped";
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "convertUntyped";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("convertUntyped");
        destination.emitAttribute("to", getRequiredItemType().toString());
        operand.explain(destination);
        destination.endElement();
    }

}

