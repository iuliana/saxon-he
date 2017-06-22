//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.sf.saxon.type;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Converter.DownCastingConverter;
import net.sf.saxon.type.Converter.StringToBase64BinaryConverter;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BigIntegerValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.DateValue;
import net.sf.saxon.value.DayTimeDurationValue;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.DurationValue;
import net.sf.saxon.value.FloatValue;
import net.sf.saxon.value.GDayValue;
import net.sf.saxon.value.GMonthDayValue;
import net.sf.saxon.value.GMonthValue;
import net.sf.saxon.value.GYearMonthValue;
import net.sf.saxon.value.GYearValue;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.NotationValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.TimeValue;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.value.YearMonthDurationValue;

public abstract class StringConverter extends Converter {
    public static final StringConverter.StringToString STRING_TO_STRING = new StringConverter.StringToString();
    public static final StringConverter.StringToLanguage STRING_TO_LANGUAGE = new StringConverter.StringToLanguage();
    public static final StringConverter.StringToNormalizedString STRING_TO_NORMALIZED_STRING = new StringConverter.StringToNormalizedString();
    public static final StringConverter.StringToToken STRING_TO_TOKEN = new StringConverter.StringToToken();
    public static final StringConverter.StringToDecimal STRING_TO_DECIMAL = new StringConverter.StringToDecimal();
    public static final StringConverter.StringToInteger STRING_TO_INTEGER = new StringConverter.StringToInteger();
    public static final StringConverter.StringToDuration STRING_TO_DURATION = new StringConverter.StringToDuration();
    public static final StringConverter.StringToDayTimeDuration STRING_TO_DAY_TIME_DURATION = new StringConverter.StringToDayTimeDuration();
    public static final StringConverter.StringToYearMonthDuration STRING_TO_YEAR_MONTH_DURATION = new StringConverter.StringToYearMonthDuration();
    public static final StringConverter.StringToTime STRING_TO_TIME = new StringConverter.StringToTime();
    public static final StringConverter.StringToBoolean STRING_TO_BOOLEAN = new StringConverter.StringToBoolean();
    public static final StringConverter.StringToHexBinary STRING_TO_HEX_BINARY = new StringConverter.StringToHexBinary();
    public static final StringToBase64BinaryConverter STRING_TO_BASE64_BINARY = new StringToBase64BinaryConverter();
    public static final StringConverter.StringToUntypedAtomic STRING_TO_UNTYPED_ATOMIC = new StringConverter.StringToUntypedAtomic();

    protected StringConverter() {
    }

    protected StringConverter(ConversionRules rules) {
        super(rules);
    }

    public abstract ConversionResult convertString(CharSequence var1);

    public ValidationFailure validate(CharSequence input) {
        ConversionResult result = this.convertString(input);
        return result instanceof ValidationFailure?(ValidationFailure)result:null;
    }

    public ConversionResult convert(AtomicValue input) {
        return this.convertString(input.getStringValueCS());
    }

    public static class StringToUnionConverter extends StringConverter {
        SimpleType targetType;
        ConversionRules rules;

        public StringToUnionConverter(PlainType targetType, ConversionRules rules) {
            if(!targetType.isPlainType()) {
                throw new IllegalArgumentException();
            } else if(((SimpleType)targetType).isNamespaceSensitive()) {
                throw new IllegalArgumentException();
            } else {
                this.targetType = (SimpleType)targetType;
                this.rules = rules;
            }
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                return this.targetType.getTypedValue(input, (NamespaceResolver)null, this.rules).head();
            } catch (XPathException var3) {
                return new ValidationFailure(ValidationException.makeXPathException(var3));
            }
        }
    }

    public static class IdentityConverter extends StringConverter {
        public static StringConverter.IdentityConverter THE_INSTANCE = new StringConverter.IdentityConverter();

        public IdentityConverter() {
        }

        public ConversionResult convert(AtomicValue input) {
            return input;
        }

        public ConversionResult convertString(CharSequence input) {
            return StringValue.makeStringValue(input);
        }

        public boolean isAlwaysSuccessful() {
            return true;
        }

        public ValidationFailure validate(CharSequence input) {
            return null;
        }
    }

    public static class StringToAnyURI extends StringConverter {
        public StringToAnyURI(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return (ConversionResult)(this.getConversionRules().isValidURI(input)?new AnyURIValue(input):new ValidationFailure("Invalid URI: " + input.toString()));
        }

        public ValidationFailure validate(CharSequence input) {
            return this.getConversionRules().isValidURI(input)?null:new ValidationFailure("Invalid URI: " + input.toString());
        }
    }

    public static class StringToNotation extends StringConverter {
        NamespaceResolver nsResolver;

        public StringToNotation(ConversionRules rules) {
            super(rules);
        }

        public void setNamespaceResolver(NamespaceResolver resolver) {
            this.nsResolver = resolver;
        }

        public NamespaceResolver getNamespaceResolver() {
            return this.nsResolver;
        }

        public ConversionResult convertString(CharSequence input) {
            if(this.getNamespaceResolver() == null) {
                throw new UnsupportedOperationException("Cannot validate a NOTATION without a namespace resolver");
            } else {
                try {
                    NameChecker nameChecker = this.getConversionRules().getNameChecker();
                    String[] parts = nameChecker.getQNameParts(Whitespace.trimWhitespace(input));
                    String uri = this.getNamespaceResolver().getURIForPrefix(parts[0], true);
                    return (ConversionResult)(uri == null?new ValidationFailure("Namespace prefix " + Err.wrap(parts[0]) + " has not been declared"):(!this.getConversionRules().isDeclaredNotation(uri, parts[1])?new ValidationFailure("Notation {" + uri + "}" + parts[1] + " is not declared in the schema"):new NotationValue(parts[0], uri, parts[1], nameChecker)));
                } catch (QNameException var5) {
                    return new ValidationFailure("Invalid lexical QName " + Err.wrap(input));
                } catch (XPathException var6) {
                    return new ValidationFailure(var6.getMessage());
                }
            }
        }
    }

    public static class StringToQName extends StringConverter {
        NamespaceResolver nsResolver;

        public StringToQName(ConversionRules rules) {
            super(rules);
        }

        public boolean isXPath30Conversion() {
            return true;
        }

        public void setNamespaceResolver(NamespaceResolver resolver) {
            this.nsResolver = resolver;
        }

        public NamespaceResolver getNamespaceResolver() {
            return this.nsResolver;
        }

        public ConversionResult convertString(CharSequence input) {
            if(this.nsResolver == null) {
                throw new UnsupportedOperationException("Cannot validate a QName without a namespace resolver");
            } else {
                try {
                    NameChecker nameChecker = this.getConversionRules().getNameChecker();
                    String[] parts = nameChecker.getQNameParts(Whitespace.trimWhitespace(input));
                    String uri = this.nsResolver.getURIForPrefix(parts[0], true);
                    return (ConversionResult)(uri == null?new ValidationFailure("Namespace prefix " + Err.wrap(parts[0]) + " has not been declared"):new QNameValue(parts[0], uri, parts[1], BuiltInAtomicType.QNAME, nameChecker));
                } catch (QNameException var5) {
                    return new ValidationFailure("Invalid lexical QName " + Err.wrap(input));
                } catch (XPathException var6) {
                    return new ValidationFailure(var6.getMessage());
                }
            }
        }
    }

    public static class StringToHexBinary extends StringConverter {
        public StringToHexBinary() {
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                return new HexBinaryValue(input);
            } catch (XPathException var3) {
                return new ValidationFailure(var3);
            }
        }
    }

    public static class StringToBoolean extends StringConverter {
        public StringToBoolean() {
        }

        public ConversionResult convertString(CharSequence input) {
            return BooleanValue.fromString(input);
        }
    }

    public static class StringToTime extends StringConverter {
        public StringToTime() {
        }

        public ConversionResult convertString(CharSequence input) {
            return TimeValue.makeTimeValue(input);
        }
    }

    public static class StringToGDayConverter extends StringConverter {
        public StringToGDayConverter(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return GDayValue.makeGDayValue(input, this.getConversionRules());
        }
    }

    public static class StringToGMonthDay extends StringConverter {
        public StringToGMonthDay(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return GMonthDayValue.makeGMonthDayValue(input, this.getConversionRules());
        }
    }

    public static class StringToGYear extends StringConverter {
        public StringToGYear(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return GYearValue.makeGYearValue(input, this.getConversionRules());
        }
    }

    public static class StringToGYearMonth extends StringConverter {
        public StringToGYearMonth(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return GYearMonthValue.makeGYearMonthValue(input, this.getConversionRules());
        }
    }

    public static class StringToGMonth extends StringConverter {
        public StringToGMonth(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return GMonthValue.makeGMonthValue(input, this.getConversionRules());
        }
    }

    public static class StringToDate extends StringConverter {
        public StringToDate(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return DateValue.makeDateValue(input, this.getConversionRules());
        }
    }

    public static class StringToDateTime extends StringConverter {
        public StringToDateTime(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            return DateTimeValue.makeDateTimeValue(input, this.getConversionRules());
        }
    }

    public static class StringToYearMonthDuration extends StringConverter {
        public StringToYearMonthDuration() {
        }

        public ConversionResult convertString(CharSequence input) {
            return YearMonthDurationValue.makeYearMonthDurationValue(input);
        }
    }

    public static class StringToDayTimeDuration extends StringConverter {
        public StringToDayTimeDuration() {
        }

        public ConversionResult convertString(CharSequence input) {
            return DayTimeDurationValue.makeDayTimeDurationValue(input);
        }
    }

    public static class StringToDuration extends StringConverter {
        public StringToDuration() {
        }

        public ConversionResult convertString(CharSequence input) {
            return DurationValue.makeDuration(input);
        }
    }

    public static class StringToIntegerSubtype extends StringConverter {
        BuiltInAtomicType targetType;

        public StringToIntegerSubtype(BuiltInAtomicType targetType) {
            this.targetType = targetType;
        }

        public ConversionResult convertString(CharSequence input) {
            ConversionResult iv = IntegerValue.stringToInteger(input);
            boolean ok;
            if(iv instanceof Int64Value) {
                ok = IntegerValue.checkRange(((Int64Value)iv).longValue(), this.targetType);
                return (ConversionResult)(ok?((Int64Value)iv).copyAsSubType(this.targetType):new ValidationFailure("Integer value is out of range for type " + this.targetType.toString()));
            } else if(iv instanceof BigIntegerValue) {
                ok = IntegerValue.checkBigRange(((BigIntegerValue)iv).asBigInteger(), this.targetType);
                if(ok) {
                    ((BigIntegerValue)iv).setTypeLabel(this.targetType);
                    return iv;
                } else {
                    return new ValidationFailure("Integer value is out of range for type " + this.targetType.toString());
                }
            } else {
                assert iv instanceof ValidationFailure;

                return iv;
            }
        }
    }

    public static class StringToInteger extends StringConverter {
        public StringToInteger() {
        }

        public ConversionResult convert(AtomicValue input) {
            return IntegerValue.stringToInteger(input.getStringValueCS());
        }

        public ConversionResult convertString(CharSequence input) {
            return IntegerValue.stringToInteger(input);
        }

        public ValidationFailure validate(CharSequence input) {
            return IntegerValue.castableAsInteger(input);
        }
    }

    public static class StringToDecimal extends StringConverter {
        public StringToDecimal() {
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                return new DecimalValue(new BigDecimal(input.toString()));
            } catch (NumberFormatException var3) {
                return new ValidationFailure("Cannot convert string to decimal: " + Err.wrap(input.toString(), 4));
            }
        }

        public ValidationFailure validate(CharSequence input) {
            return DecimalValue.castableAsDecimal(input)?null:new ValidationFailure("Cannot convert string to decimal: " + input.toString());
        }
    }

    public static class StringToDouble extends StringConverter {
        net.sf.saxon.type.StringToDouble worker;

        public StringToDouble(ConversionRules rules) {
            super(rules);
            this.worker = rules.getStringToDoubleConverter();
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                double dble = this.worker.stringToNumber(input);
                return new DoubleValue(dble);
            } catch (NumberFormatException var4) {
                return new ValidationFailure("Cannot convert string to double: " + Err.wrap(input.toString(), 4));
            }
        }
    }

    public static class StringToFloat extends StringConverter {
        public StringToFloat(ConversionRules rules) {
            super(rules);
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                float flt = (float)this.getConversionRules().getStringToDoubleConverter().stringToNumber(input);
                return new FloatValue(flt);
            } catch (NumberFormatException var4) {
                ValidationFailure ve = new ValidationFailure("Cannot convert string to float: " + input.toString());
                ve.setErrorCode("FORG0001");
                return ve;
            }
        }
    }

    public static class StringToDerivedStringSubtype extends StringConverter {
        AtomicType targetType;
        StringConverter builtInValidator;
        int whitespaceAction;

        public StringToDerivedStringSubtype(ConversionRules rules, AtomicType targetType) {
            super(rules);
            this.targetType = targetType;
            this.whitespaceAction = targetType.getWhitespaceAction();
            this.builtInValidator = rules.getStringConverter((AtomicType)targetType.getBuiltInBaseType());
        }

        public ConversionResult convertString(CharSequence input) {
            CharSequence cs = Whitespace.applyWhitespaceNormalization(this.whitespaceAction, input);
            ValidationFailure f = this.builtInValidator.validate(cs);
            if(f != null) {
                return f;
            } else {
                try {
                    cs = this.targetType.preprocess(cs);
                } catch (ValidationException var5) {
                    return new ValidationFailure(var5);
                }

                StringValue sv = new StringValue(cs);
                f = this.targetType.validate(sv, cs, this.getConversionRules());
                if(f == null) {
                    sv.setTypeLabel(this.targetType);
                    return sv;
                } else {
                    return f;
                }
            }
        }
    }

    public static class StringToStringSubtype extends StringConverter {
        AtomicType targetType;
        int whitespaceAction;

        public StringToStringSubtype(ConversionRules rules, AtomicType targetType) {
            super(rules);
            this.targetType = targetType;
            this.whitespaceAction = targetType.getWhitespaceAction();
        }

        public ConversionResult convertString(CharSequence input) {
            CharSequence cs = Whitespace.applyWhitespaceNormalization(this.whitespaceAction, input);

            try {
                cs = this.targetType.preprocess(cs);
            } catch (ValidationException var5) {
                return new ValidationFailure(var5);
            }

            StringValue sv = new StringValue(cs);
            ValidationFailure f = this.targetType.validate(sv, cs, this.getConversionRules());
            if(f == null) {
                sv.setTypeLabel(this.targetType);
                return sv;
            } else {
                return f;
            }
        }

        public ValidationFailure validate(CharSequence input) {
            CharSequence cs = Whitespace.applyWhitespaceNormalization(this.whitespaceAction, input);
            return this.targetType.validate(new StringValue(cs), cs, this.getConversionRules());
        }
    }

    public static class StringToName extends StringConverter.StringToNCName {
        private static final Pattern colon = Pattern.compile(":");

        public StringToName(ConversionRules rules) {
            super(rules, BuiltInAtomicType.NAME);
        }

        public ConversionResult convertString(CharSequence input) {
            ValidationFailure vf = this.validate(input);
            return (ConversionResult)(vf == null?new StringValue(Whitespace.trimWhitespace(input), BuiltInAtomicType.NAME):vf);
        }

        public ValidationFailure validate(CharSequence input) {
            CharSequence trimmed = Whitespace.trimWhitespace(input);
            if(this.checker.isValidNCName(trimmed)) {
                return null;
            } else {
                FastStringBuffer buff = new FastStringBuffer(trimmed.length());
                buff.append(trimmed);

                for(int i = 0; i < buff.length(); ++i) {
                    if(buff.charAt(i) == 58) {
                        buff.setCharAt(i, '_');
                    }
                }

                if(this.checker.isValidNCName(buff)) {
                    return null;
                } else {
                    return new ValidationFailure("The value '" + trimmed + "' is not a valid xs:Name");
                }
            }
        }
    }

    public static class StringToNMTOKEN extends StringConverter {
        NameChecker checker;

        public StringToNMTOKEN(ConversionRules rules) {
            super(rules);
            this.checker = rules.getNameChecker();
        }

        public ConversionResult convertString(CharSequence input) {
            CharSequence trimmed = Whitespace.trimWhitespace(input);
            return (ConversionResult)(this.checker.isValidNmtoken(trimmed)?new StringValue(trimmed, BuiltInAtomicType.NMTOKEN):new ValidationFailure("The value '" + input + "' is not a valid xs:NMTOKEN"));
        }

        public ValidationFailure validate(CharSequence input) {
            return this.checker.isValidNmtoken(Whitespace.trimWhitespace(input))?null:new ValidationFailure("The value '" + input + "' is not a valid xs:NMTOKEN");
        }
    }

    public static class StringToNCName extends StringConverter {
        NameChecker checker;
        AtomicType targetType;

        public StringToNCName(ConversionRules rules, AtomicType targetType) {
            super(rules);
            this.checker = rules.getNameChecker();
            this.targetType = targetType;
        }

        public ConversionResult convertString(CharSequence input) {
            CharSequence trimmed = Whitespace.trimWhitespace(input);
            return (ConversionResult)(this.checker.isValidNCName(trimmed)?new StringValue(trimmed, this.targetType):new ValidationFailure("The value '" + input + "' is not a valid " + this.targetType.getDisplayName()));
        }

        public ValidationFailure validate(CharSequence input) {
            return this.checker.isValidNCName(Whitespace.trimWhitespace(input))?null:new ValidationFailure("The value '" + input + "' is not a valid " + this.targetType.getDisplayName());
        }
    }

    public static class StringToLanguage extends StringConverter {
        private static final Pattern regex = Pattern.compile("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*");

        public StringToLanguage() {
        }

        public ConversionResult convertString(CharSequence input) {
            CharSequence trimmed = Whitespace.trimWhitespace(input);
            return (ConversionResult)(!regex.matcher(trimmed).matches()?new ValidationFailure("The value '" + input + "' is not a valid xs:language"):new StringValue(trimmed, BuiltInAtomicType.LANGUAGE));
        }

        public ValidationFailure validate(CharSequence input) {
            return regex.matcher(Whitespace.trimWhitespace(input)).matches()?null:new ValidationFailure("The value '" + input + "' is not a valid xs:language");
        }
    }

    public static class StringToToken extends StringConverter {
        public StringToToken() {
        }

        public ConversionResult convertString(CharSequence input) {
            return new StringValue(Whitespace.collapseWhitespace(input), BuiltInAtomicType.TOKEN);
        }

        public ValidationFailure validate(CharSequence input) {
            return null;
        }

        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    public static class StringToNormalizedString extends StringConverter {
        public StringToNormalizedString() {
        }

        public ConversionResult convertString(CharSequence input) {
            return new StringValue(Whitespace.normalizeWhitespace(input), BuiltInAtomicType.NORMALIZED_STRING);
        }

        public ValidationFailure validate(CharSequence input) {
            return null;
        }

        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    public static class StringToUntypedAtomic extends StringConverter {
        public StringToUntypedAtomic() {
        }

        public ConversionResult convert(AtomicValue input) {
            return new UntypedAtomicValue(input.getStringValueCS());
        }

        public ConversionResult convertString(CharSequence input) {
            return new UntypedAtomicValue(input);
        }

        public ValidationFailure validate(CharSequence input) {
            return null;
        }

        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    public static class StringToString extends StringConverter {
        public StringToString() {
        }

        public ConversionResult convert(AtomicValue input) {
            return new StringValue(input.getStringValueCS());
        }

        public ConversionResult convertString(CharSequence input) {
            return new StringValue(input);
        }

        public ValidationFailure validate(CharSequence input) {
            return null;
        }

        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    public static class StringToNonStringDerivedType extends StringConverter {
        private StringConverter phaseOne;
        private DownCastingConverter phaseTwo;

        public StringToNonStringDerivedType(StringConverter phaseOne, DownCastingConverter phaseTwo) {
            this.phaseOne = phaseOne;
            this.phaseTwo = phaseTwo;
        }

        public void setNamespaceResolver(NamespaceResolver resolver) {
            this.phaseOne.setNamespaceResolver(resolver);
            this.phaseTwo.setNamespaceResolver(resolver);
        }

        public ConversionResult convert(AtomicValue input) {
            CharSequence in = input.getStringValueCS();

            try {
                in = this.phaseTwo.getTargetType().preprocess(in);
            } catch (ValidationException var4) {
                return new ValidationFailure(var4);
            }

            ConversionResult temp = this.phaseOne.convertString(in);
            return temp instanceof ValidationFailure?temp:this.phaseTwo.convert((AtomicValue)temp, in);
        }

        public ConversionResult convertString(CharSequence input) {
            try {
                input = this.phaseTwo.getTargetType().preprocess(input);
            } catch (ValidationException var3) {
                return new ValidationFailure(var3);
            }

            ConversionResult temp = this.phaseOne.convertString(input);
            return temp instanceof ValidationFailure?temp:this.phaseTwo.convert((AtomicValue)temp, input);
        }
    }
}
