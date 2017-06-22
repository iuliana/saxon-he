////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.sort.LRUCache;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NotationSet;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.type.*;

import java.io.Serializable;

/**
 * This class defines a set of rules for converting between different atomic types. It handles the variations
 * that arise between different versions of the W3C specifications, for example the changes in Name syntax
 * between XML 1.0 and XML 1.1, the introduction of "+INF" as a permitted xs:double value in XSD 1.1, and so on.
 *
 * <p>It is possible to nominate a customized <code>ConversionRules</code> object at the level of the
 * {@link net.sf.saxon.Configuration}, either by instantiating this class and changing the properties, or
 * by subclassing.</p>
 *
 * @since 9.3
 *
 * @see net.sf.saxon.Configuration#setConversionRules(ConversionRules)
 */

public class ConversionRules implements Serializable {

    private NameChecker nameChecker;
    private StringToDouble stringToDouble;
    private NotationSet notationSet; // may be null
    private URIChecker uriChecker;
    private boolean allowYearZero;

    // These two tables need to be synchronised to make the caching thread-safe
    private LRUCache<Integer, Converter> converterCache =
            new LRUCache<Integer, Converter>(100, true);
    private LRUCache<Integer, StringConverter> stringConverterCache =
            new LRUCache<Integer, StringConverter>(100, true);


    public ConversionRules() {

    }

    /**
     * Create a copy of these conversion rules.
     * @return a copy of the rules. The cache of converters is NOT copied (because changes to the conversion rules would
     * invalidate the cache)
     */

    public ConversionRules copy() {
        ConversionRules cr = new ConversionRules();
        copyTo(cr);
        return cr;
    }

    /**
     * Create a copy of these conversion rules.
     * @param cr a ConversionRules object which will be updated to hold a copy of the rules.
     * The cache of converters is NOT copied (because changes to the conversion rules would
     * invalidate the cache)
     */

    public void copyTo(ConversionRules cr) {
        cr.nameChecker = nameChecker;
        cr.stringToDouble = stringToDouble;
        cr.notationSet = notationSet;
        cr.uriChecker = uriChecker;
        cr.allowYearZero = allowYearZero;
        cr.converterCache.clear();
        cr.stringConverterCache.clear();
    }

    /**
     * Set the class that will be used to check whether XML names are valid.
     * @param checker the class to be used for checking names. There are variants of this
     * class depending on which version/edition of the XML specification is in use.
     */

    public void setNameChecker(NameChecker checker) {
        this.nameChecker = checker;
    }

    /**
     * Get the class that will be used to check whether XML names are valid.
     * @return the class to be used for checking names. There are variants of this
     * class depending on which version/edition of the XML specification is in use.
     */

    public NameChecker getNameChecker() {
        return nameChecker;
    }



    /**
     * Set the converter that will be used for converting strings to doubles and floats.
     * @param converter the converter to be used. There are two converters in regular use:
     * they differ only in whether the lexical value "+INF" is recognized as a representation of
     * positive infinity.
     */

    public void setStringToDoubleConverter(StringToDouble converter) {
        this.stringToDouble = converter;
    }

    /**
     * Get the converter that will be used for converting strings to doubles and floats.
     * @return the converter to be used. There are two converters in regular use:
     * they differ only in whether the lexical value "+INF" is recognized as a representation of
     * positive infinity.
     */

    public StringToDouble getStringToDoubleConverter() {
        return stringToDouble;
    }

    /**
     * Specify the set of notations that are accepted by xs:NOTATION and its subclasses. This is to
     * support the rule that for a notation to be valid, it must be declared in an xs:notation declaration
     * in the schema
     * @param notations the set of notations that are recognized; or null, to indicate that all notation
     * names are accepted
     */

    public void setNotationSet(/*@Nullable*/ NotationSet notations) {
        this.notationSet = notations;
    }

    /**
     * Ask whether a given notation is accepted by xs:NOTATION and its subclasses. This is to
     * support the rule that for a notation to be valid, it must be declared in an xs:notation declaration
     * in the schema
     * @param uri the namespace URI of the notation
     * @param local the local part of the name of the notation
     * @return true if the notation is in the set of recognized notation names
     */


    public boolean isDeclaredNotation(String uri, String local) {
        //noinspection SimplifiableIfStatement
        if (notationSet == null) {
            return true;    // in the absence of a known configuration, treat all notations as valid
        } else {
            return notationSet.isDeclaredNotation(uri, local);
        }
    }

    /**
     * Set the class to be used for checking URI values. By default, no checking takes place.
     * @param checker an object to be used for checking URIs; or null if any string is accepted as an anyURI value
     */

    public void setURIChecker(URIChecker checker) {
        this.uriChecker = checker;
    }

    /**
     * Ask whether a string is a valid instance of xs:anyURI according to the rules
     * defined by the current URIChecker
     * @param string the string to be checked against the rules for URIs
     * @return true if the string represents a valid xs:anyURI value
     */

    public boolean isValidURI(CharSequence string) {
        return uriChecker == null || uriChecker.isValidURI(string);
    }

    /**
     * Say whether year zero is permitted in dates. By default it is not permitted when XSD 1.0 is in use,
     * but it is permitted when XSD 1.1 is used.
     * @param allowed true if year zero is permitted
     */

    public void setAllowYearZero(boolean allowed) {
        allowYearZero = allowed;
    }

    /**
     * Ask whether  year zero is permitted in dates. By default it is not permitted when XSD 1.0 is in use,
     * but it is permitted when XSD 1.1 is used.
     * @return true if year zero is permitted
     */

    public boolean isAllowYearZero() {
        return allowYearZero;
    }

    /**
     * Get a Converter for a given pair of atomic types. These can be primitive types,
     * derived types, or user-defined types. The converter implements the casting rules.
     * @param source the source type
     * @param target the target type
     * @return a Converter if conversion between the two types is possible; or null otherwise
     */

    /*@Nullable*/ public Converter getConverter(AtomicType source, AtomicType target) {
        // For a lookup key, use the primitive type of the source type (always 10 bits) and the
        // fingerprint of the target type (20 bits)
        int key = (source.getPrimitiveType() << 20) | target.getFingerprint();
        Converter converter = converterCache.get(key);
        if (converter == null) {
            converter = makeConverter(source, target);
            if (converter != null) {
                converterCache.put(key, converter);
            } else {
                return null;
            }
        }
        return converter;
    }

    /**
     * Create a converter that handles conversion from one primitive type to another.
     * <p/>
     * <p>This method is intended for internal use only. The approved way to get a converter is using the
     * factory method {@link net.sf.saxon.lib.ConversionRules#getConverter(net.sf.saxon.type.AtomicType, net.sf.saxon.type.AtomicType)}}</p>
     *
     * @param sourceType the type of the value to be converted
     * @param targetType the type of the result of the conversion
     * @return the converter if one is available; or null if no conversion is possible
     */

    /*@Nullable*/ private Converter makeConverter(/*@NotNull*/ AtomicType sourceType, /*@NotNull*/ AtomicType targetType) {
        if (sourceType == targetType) {
            return StringConverter.IdentityConverter.THE_INSTANCE;
        }

        int tt = targetType.getFingerprint();
        int tp = targetType.getPrimitiveType();
        int st = sourceType.getPrimitiveType();

        if ((st == StandardNames.XS_STRING || st == StandardNames.XS_UNTYPED_ATOMIC) &&
                (tp == StandardNames.XS_STRING || tp == StandardNames.XS_UNTYPED_ATOMIC)) {
            return makeStringConverter(targetType);
        }

        if (!(targetType.isPrimitiveType())) {
            AtomicType primTarget = (AtomicType) targetType.getPrimitiveItemType();
            if (sourceType == primTarget) {
                return new Converter.DownCastingConverter(targetType, this);
            } else if (st == StandardNames.XS_STRING || st == StandardNames.XS_UNTYPED_ATOMIC) {
                return makeStringConverter(targetType);
            } else {
                Converter stageOne = makeConverter(sourceType, primTarget);
                if (stageOne == null) {
                    return null;
                }
                Converter stageTwo = new Converter.DownCastingConverter(targetType, this);
                return new Converter.TwoPhaseConverter(stageOne, stageTwo);
            }
        }


        if (st == tt) {
            // we are casting between subtypes of the same primitive type.
            // TODO: (optimization) check whether the targetType is a supertype of the source type, in which case
            // it's a simple upcast. (Unfortunately, we don't have the TypeHierarchy available)
            Converter upcast = new Converter.UpCastingConverter((AtomicType) sourceType.getPrimitiveItemType());
            Converter downcast = new Converter.DownCastingConverter(targetType, this);
            return new Converter.TwoPhaseConverter(upcast, downcast);
        }

        switch (tt) {
            case StandardNames.XS_UNTYPED_ATOMIC:
                return Converter.TO_UNTYPED_ATOMIC;
            case StandardNames.XS_STRING:
                return Converter.TO_STRING;
            case StandardNames.XS_FLOAT:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToFloat(this);
                    case StandardNames.XS_DOUBLE:
                    case StandardNames.XS_DECIMAL:
                        //case StandardNames.XS_PRECISION_DECIMAL:
                    case StandardNames.XS_INTEGER:
                    case StandardNames.XS_NUMERIC:
                        return Converter.NUMERIC_TO_FLOAT;
                    case StandardNames.XS_BOOLEAN:
                        return Converter.BOOLEAN_TO_FLOAT;
                    default:
                        return null;
                }
            case StandardNames.XS_DOUBLE:
            case StandardNames.XS_NUMERIC:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToDouble(this);
                    case StandardNames.XS_FLOAT:
                    case StandardNames.XS_DECIMAL:
                        //case StandardNames.XS_PRECISION_DECIMAL:
                    case StandardNames.XS_INTEGER:
                    case StandardNames.XS_NUMERIC:
                        return Converter.NUMERIC_TO_DOUBLE;
                    case StandardNames.XS_BOOLEAN:
                        return Converter.BOOLEAN_TO_DOUBLE;
                    default:
                        return null;
                }
            case StandardNames.XS_DECIMAL:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_DECIMAL;
                    case StandardNames.XS_FLOAT:
                        return Converter.FLOAT_TO_DECIMAL;
                    case StandardNames.XS_DOUBLE:
                        return Converter.DOUBLE_TO_DECIMAL;
//                    case StandardNames.XS_PRECISION_DECIMAL:
//                        return PRECISION_DECIMAL_TO_DECIMAL;
                    case StandardNames.XS_INTEGER:
                        return Converter.INTEGER_TO_DECIMAL;
                    case StandardNames.XS_NUMERIC:
                        return Converter.NUMERIC_TO_DECIMAL;
                    case StandardNames.XS_BOOLEAN:
                        return Converter.BOOLEAN_TO_DECIMAL;
                    default:
                        return null;
                }

            case StandardNames.XS_INTEGER:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_INTEGER;
                    case StandardNames.XS_FLOAT:
                        return Converter.FLOAT_TO_INTEGER;
                    case StandardNames.XS_DOUBLE:
                        return Converter.DOUBLE_TO_INTEGER;
                    case StandardNames.XS_DECIMAL:
                        //case StandardNames.XS_PRECISION_DECIMAL:
                        return Converter.DECIMAL_TO_INTEGER;
                    case StandardNames.XS_NUMERIC:
                        return Converter.NUMERIC_TO_INTEGER;
                    case StandardNames.XS_BOOLEAN:
                        return Converter.BOOLEAN_TO_INTEGER;
                    default:
                        return null;
                }
            case StandardNames.XS_DURATION:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_DURATION;
                    case StandardNames.XS_DAY_TIME_DURATION:
                    case StandardNames.XS_YEAR_MONTH_DURATION:
                        return new Converter.UpCastingConverter(BuiltInAtomicType.DURATION);
                    default:
                        return null;
                }
            case StandardNames.XS_YEAR_MONTH_DURATION:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_YEAR_MONTH_DURATION;
                    case StandardNames.XS_DURATION:
                    case StandardNames.XS_DAY_TIME_DURATION:
                        return Converter.DURATION_TO_YEAR_MONTH_DURATION;
                    default:
                        return null;
                }
            case StandardNames.XS_DAY_TIME_DURATION:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_DAY_TIME_DURATION;
                    case StandardNames.XS_DURATION:
                    case StandardNames.XS_YEAR_MONTH_DURATION:
                        return Converter.DURATION_TO_DAY_TIME_DURATION;
                    default:
                        return null;
                }
            case StandardNames.XS_DATE_TIME:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToDateTime(this);
                    case StandardNames.XS_DATE:
                        return Converter.DATE_TO_DATE_TIME;
                    default:
                        return null;
                }
            case StandardNames.XS_TIME:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_TIME;
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_TIME;
                    default:
                        return null;
                }
            case StandardNames.XS_DATE:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToDate(this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_DATE;
                    default:
                        return null;
                }
            case StandardNames.XS_G_YEAR_MONTH:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToGYearMonth(this);
                    case StandardNames.XS_DATE:
                        return Converter.TwoPhaseConverter.makeTwoPhaseConverter(
                                BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME, BuiltInAtomicType.G_YEAR_MONTH, this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_G_YEAR_MONTH;
                    default:
                        return null;
                }
            case StandardNames.XS_G_YEAR:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToGYear(this);
                    case StandardNames.XS_DATE:
                        return Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                BuiltInAtomicType.G_YEAR, this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_G_YEAR;
                    default:
                        return null;
                }
            case StandardNames.XS_G_MONTH_DAY:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToGMonthDay(this);
                    case StandardNames.XS_DATE:
                        return Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                BuiltInAtomicType.G_MONTH_DAY, this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_G_MONTH_DAY;
                    default:
                        return null;
                }
            case StandardNames.XS_G_DAY:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToGDayConverter(this);
                    case StandardNames.XS_DATE:
                        return Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                BuiltInAtomicType.G_DAY, this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_G_DAY;
                    default:
                        return null;
                }
            case StandardNames.XS_G_MONTH:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToGMonth(this);
                    case StandardNames.XS_DATE:
                        return Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                BuiltInAtomicType.G_MONTH, this);
                    case StandardNames.XS_DATE_TIME:
                        return Converter.DATE_TIME_TO_G_MONTH;
                    default:
                        return null;
                }
            case StandardNames.XS_BOOLEAN:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_BOOLEAN;
                    case StandardNames.XS_FLOAT:
                    case StandardNames.XS_DOUBLE:
                    case StandardNames.XS_DECIMAL:
                        // case StandardNames.XS_PRECISION_DECIMAL:
                    case StandardNames.XS_INTEGER:
                    case StandardNames.XS_NUMERIC:
                        return Converter.NUMERIC_TO_BOOLEAN;
                    default:
                        return null;
                }
            case StandardNames.XS_BASE64_BINARY:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_BASE64_BINARY;
                    case StandardNames.XS_HEX_BINARY:
                        return Converter.HEX_BINARY_TO_BASE64_BINARY;
                    default:
                        return null;
                }
            case StandardNames.XS_HEX_BINARY:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_HEX_BINARY;
                    case StandardNames.XS_BASE64_BINARY:
                        return Converter.BASE64_BINARY_TO_HEX_BINARY;
                    default:
                        return null;
                }
            case StandardNames.XS_ANY_URI:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToAnyURI(this);
                    default:
                        return null;
                }
            case StandardNames.XS_QNAME:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToQName(this);
                    case StandardNames.XS_NOTATION:
                        return Converter.NOTATION_TO_QNAME;
                    default:
                        return null;
                }
            case StandardNames.XS_NOTATION:
                switch (st) {
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_STRING:
                        return new StringConverter.StringToNotation(this);
                    case StandardNames.XS_QNAME:
                        return Converter.QNAME_TO_NOTATION;
                    default:
                        return null;
                }

            case StandardNames.XS_ANY_ATOMIC_TYPE:
                return Converter.IDENTITY_CONVERTER;

            default:
                throw new IllegalArgumentException("Unknown primitive type " + tt);
        }
    }

    /**
     * Get a Converter that converts from strings to a given atomic type. These can be primitive types,
     * derived types, or user-defined types. The converter implements the casting rules.
     * @param target the target type
     * @return a Converter if conversion between the two types is possible; or null otherwise
     */

    public StringConverter getStringConverter(AtomicType target) {
        int key = target.getFingerprint();
        StringConverter converter = stringConverterCache.get(key);
        if (converter == null) {
            converter = makeStringConverter(target);
            stringConverterCache.put(key, converter);
        }
        return converter;
    }

    /**
     * Static factory method to get a StringConverter for a specific target type
     * @param targetType the target type of the conversion
     * @return a StringConverter that can be used to convert strings to the target type, or to
     * validate strings against the target type
     */

    /*@NotNull*/ private StringConverter makeStringConverter(/*@NotNull*/ final AtomicType targetType) {

        int tt = targetType.getPrimitiveType();
        if (targetType.isBuiltInType()) {
            if (tt == StandardNames.XS_STRING) {
                switch (targetType.getFingerprint()) {
                    case StandardNames.XS_STRING:
                        return StringConverter.STRING_TO_STRING;
                    case StandardNames.XS_NORMALIZED_STRING:
                        return StringConverter.STRING_TO_NORMALIZED_STRING;
                    case StandardNames.XS_TOKEN:
                        return StringConverter.STRING_TO_TOKEN;
                    case StandardNames.XS_LANGUAGE:
                        return StringConverter.STRING_TO_LANGUAGE;
                    case StandardNames.XS_NAME:
                        return new StringConverter.StringToName(this);
                    case StandardNames.XS_NCNAME:
                    case StandardNames.XS_ID:
                    case StandardNames.XS_IDREF:
                    case StandardNames.XS_ENTITY:
                        return new StringConverter.StringToNCName(this, targetType);
                    case StandardNames.XS_NMTOKEN:
                        return new StringConverter.StringToNMTOKEN(this);
                    default:
                        throw new AssertionError("Unknown built-in subtype of xs:string");

                }
            } else if (tt == StandardNames.XS_UNTYPED_ATOMIC) {
                return StringConverter.STRING_TO_UNTYPED_ATOMIC;
            } else if (targetType.isPrimitiveType()) {
                // converter to built-in types unrelated to xs:string
                Converter converter = getConverter(BuiltInAtomicType.STRING, targetType);
                assert converter != null;
                return (StringConverter)converter;
            } else if (tt == StandardNames.XS_INTEGER) {
                return new StringConverter.StringToIntegerSubtype((BuiltInAtomicType)targetType);
            } else {
                switch (targetType.getFingerprint()) {
                    case StandardNames.XS_DAY_TIME_DURATION:
                        return StringConverter.STRING_TO_DAY_TIME_DURATION;
                    case StandardNames.XS_YEAR_MONTH_DURATION:
                        return StringConverter.STRING_TO_YEAR_MONTH_DURATION;
                    case StandardNames.XS_DATE_TIME_STAMP:
                        StringConverter first = new StringConverter.StringToDateTime(this);
                        Converter.DownCastingConverter second = new Converter.DownCastingConverter(targetType, this);
                        return new StringConverter.StringToNonStringDerivedType(first, second);
                    default:
                        throw new AssertionError("Unknown built in type " + targetType.toString());
                }
            }
        } else {
            if (tt == StandardNames.XS_STRING) {
                if (targetType.getBuiltInBaseType() == BuiltInAtomicType.STRING) {
                    // converter to user-defined subtypes of xs:string
                    return new StringConverter.StringToStringSubtype(this, targetType);
                } else {
                    // converter to user-defined subtypes of built-in subtypes of xs:string
                    return new StringConverter.StringToDerivedStringSubtype(this, targetType);
                }
            } else {
                // converter to user-defined types derived from types other than xs:string
                StringConverter first = getStringConverter((AtomicType) targetType.getPrimitiveItemType());
                Converter.DownCastingConverter second = new Converter.DownCastingConverter(targetType, this);
                return new StringConverter.StringToNonStringDerivedType(first, second);
            }
        }

    }

}

