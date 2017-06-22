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
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;

import java.util.List;


/**
 * An AtomicSequenceConverter is an expression that performs a cast on each member of
 * a supplied sequence
 */

public class AtomicSequenceConverter extends UnaryExpression {

    public static ToStringMappingFunction TO_STRING_MAPPER = new ToStringMappingFunction();

    private PlainType requiredItemType;
    /*@Nullable*/ protected Converter converter;

    /**
     * Constructor
     *
     * @param sequence         this must be a sequence of atomic values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the item type to which all items in the sequence should be converted,
     */

    public AtomicSequenceConverter(Expression sequence, PlainType requiredItemType) {
        super(sequence);
        this.requiredItemType = requiredItemType;
    }


    public void allocateConverter(Configuration config, boolean allowNull) {

        ItemType sourceType = operand.getItemType(config.getTypeHierarchy());
        final ConversionRules rules = config.getConversionRules();
        if (sourceType instanceof ErrorType) {
            converter = Converter.IDENTITY_CONVERTER;
        } else if (!(sourceType instanceof AtomicType)) {
            converter = null;
        } else if (requiredItemType instanceof AtomicType) {
            converter = rules.getConverter((AtomicType) sourceType, (AtomicType)requiredItemType);
        } else if (((SimpleType)requiredItemType).isUnionType()) {
            converter = new StringConverter.StringToUnionConverter(requiredItemType, rules);
        }

        if (converter == null && !allowNull) {
            // source type not known statically; create a converter that decides at run-time
            converter = new Converter(rules) {
                /*@NotNull*/
                public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
                    Converter converter = rules.getConverter(input.getPrimitiveType(), (AtomicType)requiredItemType);
                    if (converter == null) {
                        return new ValidationFailure("Cannot convert value from " + input.getPrimitiveType() + " to " + requiredItemType);
                    } else {
                        return converter.convert(input);
                    }
                }
            };
        }

    }

    /**
     * Get the required item type (the target type of the conversion
     * @return the required item type
     */

    public PlainType getRequiredItemType() {
        return requiredItemType;
    }

    /**
     * Get the converter used to convert the items in the sequence
     *
     * @return the converter. Note that a converter is always allocated during the typeCheck() phase,
     *         even if the source type is not known.
     */

    public Converter getConverter() {
        return converter;
    }

    public void setConverter(Converter converter) {
        this.converter = converter;
    }

    /**
     * Simplify an expression
     *
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        operand = visitor.simplify(operand);
        if (operand instanceof Literal && requiredItemType instanceof AtomicType) {
            if (Literal.isEmptySequence(operand)) {
                return operand;
            }
            Configuration config = visitor.getConfiguration();
            allocateConverter(config, true);
            if (converter != null) {
                GroundedValue val = SequenceExtent.makeSequenceExtent(
                        iterate(visitor.getStaticContext().makeEarlyEvaluationContext()));
                return Literal.makeLiteral(val);
            }
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        operand = visitor.typeCheck(operand, contextItemType);
        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        if (th.isSubType(operand.getItemType(th), requiredItemType)) {
            return operand;
        } else {
            if (converter == null) {
                allocateConverter(config, true);
            }
            return this;
        }
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     */
    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (operand instanceof UntypedSequenceConverter) {
            UntypedSequenceConverter asc = (UntypedSequenceConverter)operand;
            ItemType ascType = asc.getItemType(th);
            if (ascType == requiredItemType) {
                return operand;
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                        (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                UntypedSequenceConverter old = (UntypedSequenceConverter)operand;
                UntypedSequenceConverter asc2 = new UntypedSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType);
                return asc2.typeCheck(visitor, contextItemType)
                        .optimize(visitor, contextItemType);
            }
        } else if (operand instanceof AtomicSequenceConverter) {
            AtomicSequenceConverter asc = (AtomicSequenceConverter)operand;
            ItemType ascType = asc.getItemType(th);
            if (ascType == requiredItemType) {
                return operand;
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                        (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                AtomicSequenceConverter old = (AtomicSequenceConverter)operand;
                AtomicSequenceConverter asc2 = new AtomicSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType
                );
                return asc2.typeCheck(visitor, contextItemType)
                        .optimize(visitor, contextItemType);
            }
        }
        return this;

    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        return super.computeSpecialProperties() | StaticProperty.NON_CREATIVE;
    }



    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        AtomicSequenceConverter atomicConverter = new AtomicSequenceConverter(getBaseExpression().copy(), requiredItemType);
        atomicConverter.setConverter(converter);
        return atomicConverter;
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    public SequenceIterator<? extends AtomicValue> iterate(final XPathContext context) throws XPathException {
        SequenceIterator base = operand.iterate(context);
        if (converter == null) {
            allocateConverter(context.getConfiguration(), false);
        }
        if (converter == Converter.TO_STRING) {
            return new ItemMappingIterator(base, TO_STRING_MAPPER, true);
        } else {
            AtomicSequenceMappingFunction mapper = new AtomicSequenceMappingFunction();
            mapper.setConverter(converter);
            return new ItemMappingIterator(base, mapper, true);
        }
    }

    /**
     * Mapping function wrapped around a converter
     */

    public static class AtomicSequenceMappingFunction
            implements ItemMappingFunction<Item, AtomicValue> {
        private Converter converter;

        public void setConverter(Converter converter) {
            this.converter = converter;
        }

        public AtomicValue mapItem(Item item) throws XPathException {
            return converter.convert((AtomicValue)item).asAtomic();
        }
    }

    /**
     * Mapping function that converts every item in a sequence to a string
     */

    public static class ToStringMappingFunction
            implements ItemMappingFunction<Item, StringValue> {
        public StringValue mapItem(Item item) throws XPathException {
            return StringValue.makeStringValue(item.getStringValueCS());
        }
    }


    /**
     * Evaluate as an Item. This should only be called if the AtomicSequenceConverter has cardinality zero-or-one
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue item = (AtomicValue)operand.evaluateItem(context);
        if (item == null) {
            return null;
        }
        return converter.convert(item).asAtomic();
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
        return requiredItemType;
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
        return super.equals(other) &&
                requiredItemType.equals(((AtomicSequenceConverter) other).requiredItemType);
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ requiredItemType.hashCode();
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
        return "convertItems";
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "convertItems";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void explain(ExpressionPresenter destination) {
        destination.startElement("convertItems");
        destination.emitAttribute("to", requiredItemType.toString());
        operand.explain(destination);
        destination.endElement();
    }

}

