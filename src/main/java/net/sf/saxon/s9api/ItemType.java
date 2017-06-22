////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Name10Checker;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;

/**
 * An item type, as defined in the XPath/XQuery specifications.
 *
 * <p>This class contains a number of static constant fields
 * referring to instances that represent simple item types, such as
 * <code>item()</code>, <code>node()</code>, and <code>xs:anyAtomicType</code>. These named types are currently
 * based on the definitions in XSD 1.0 and XML 1.0. They may be changed in a future version to be based
 * on a later version.</p>
 *
 * <p>More complicated item types, especially those that are dependent on information in a schema,
 * are available using factory methods on the {@link ItemTypeFactory} object. The factory methods can
 * also be used to create variants of the types that use the rules given in the XML 1.1 and/or XSD 1.1 specifications.</p>
 */
public abstract class ItemType {

    private static ConversionRules defaultConversionRules = new ConversionRules();

    static {
        defaultConversionRules.setNameChecker(Name10Checker.getInstance());
        defaultConversionRules.setStringToDoubleConverter(StringToDouble.getInstance());
        defaultConversionRules.setNotationSet(null);
        defaultConversionRules.setURIChecker(StandardURIChecker.getInstance());
    }

    /**
     * ItemType representing the type item(), that is, any item at all
     */
    public static final ItemType ANY_ITEM = new ItemType() {

        public ConversionRules getConversionRules() {
            return defaultConversionRules;
        }

        public boolean matches(XdmItem item) {
            return true;
        }

        public boolean subsumes(ItemType other) {
            return true;
        }

        public net.sf.saxon.type.ItemType getUnderlyingItemType() {
            return AnyItemType.getInstance();
        }
    };

    /**
     * ItemType representing the type node(), that is, any node
     */
    public static final ItemType ANY_NODE = new ItemType() {

        public ConversionRules getConversionRules() {
            return defaultConversionRules;
        }

        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof NodeInfo;
        }

        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof NodeTest;
        }

        public net.sf.saxon.type.ItemType getUnderlyingItemType() {
            return AnyNodeTest.getInstance();
        }
    };

    /**
     * ItemType representing the type xs:anyAtomicType, that is, any atomic value
     */
    public static final ItemType ANY_ATOMIC_VALUE = new ItemType() {

        public ConversionRules getConversionRules() {
            return defaultConversionRules;
        }

        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof AtomicValue;
        }

        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof AtomicType;
        }

        public net.sf.saxon.type.ItemType getUnderlyingItemType() {
            return BuiltInAtomicType.ANY_ATOMIC;
        }
    };

    static class BuiltInAtomicItemType extends ItemType {

        private BuiltInAtomicType underlyingType;
        private ConversionRules conversionRules;

        public BuiltInAtomicItemType(BuiltInAtomicType underlyingType, ConversionRules conversionRules) {
            this.underlyingType = underlyingType;
            this.conversionRules = conversionRules;
        }

        public static BuiltInAtomicItemType makeVariant(BuiltInAtomicItemType type, ConversionRules conversionRules) {
            // TODO: allocate from a pool
            return new BuiltInAtomicItemType(type.underlyingType, conversionRules);
        }

        public ConversionRules getConversionRules() {
            return conversionRules;
        }

        public boolean matches(XdmItem item) {
            Item value = (Item)item.getUnderlyingValue();
            if (!(value instanceof AtomicValue)) {
                return false;
            }
            AtomicType type = ((AtomicValue) value).getItemType();
            return subsumesUnderlyingType(type);
        }

        public boolean subsumes(ItemType other) {
            net.sf.saxon.type.ItemType otherType = other.getUnderlyingItemType();
            if (!(otherType.isPlainType())) {
                return false;
            }
            AtomicType type = (AtomicType)otherType;
            return subsumesUnderlyingType(type);
        }

        private boolean subsumesUnderlyingType(AtomicType type) {
			BuiltInAtomicType builtIn = (type instanceof BuiltInAtomicType
				? ((BuiltInAtomicType) type)
				: (BuiltInAtomicType) type.getBuiltInBaseType());
            while (true) {
                if (builtIn.isSameType(underlyingType)) {
                    return true;
                }
                SchemaType base = builtIn.getBaseType();
                if (!(base instanceof BuiltInAtomicType)) {
                    return false;
				}
				builtIn = (BuiltInAtomicType) base;
            }
        }

        public net.sf.saxon.type.ItemType getUnderlyingItemType() {
            return underlyingType;
        }
    }

    /**
     * A Saxon-specific item type representing the base type of double, float, and decimal
     */
    public static final ItemType NUMERIC = new BuiltInAtomicItemType(BuiltInAtomicType.NUMERIC, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:string
     */
    public static final ItemType STRING = new BuiltInAtomicItemType(BuiltInAtomicType.STRING, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:boolean
     */
    public static final ItemType BOOLEAN = new BuiltInAtomicItemType(BuiltInAtomicType.BOOLEAN, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:duration
     */
	public static final ItemType DURATION = new BuiltInAtomicItemType(BuiltInAtomicType.DURATION,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:dateTime
     */
	public static final ItemType DATE_TIME = new BuiltInAtomicItemType(BuiltInAtomicType.DATE_TIME,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:date
     */
    public static final ItemType DATE = new BuiltInAtomicItemType(BuiltInAtomicType.DATE, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:time
     */
    public static final ItemType TIME = new BuiltInAtomicItemType(BuiltInAtomicType.TIME, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gYearMonth
     */
	public static final ItemType G_YEAR_MONTH = new BuiltInAtomicItemType(BuiltInAtomicType.G_YEAR_MONTH,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gMonth
     */
    public static final ItemType G_MONTH = new BuiltInAtomicItemType(BuiltInAtomicType.G_MONTH, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gMonthDay
     */
	public static final ItemType G_MONTH_DAY = new BuiltInAtomicItemType(BuiltInAtomicType.G_MONTH_DAY,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gYear
     */
    public static final ItemType G_YEAR = new BuiltInAtomicItemType(BuiltInAtomicType.G_YEAR, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gDay
     */
    public static final ItemType G_DAY = new BuiltInAtomicItemType(BuiltInAtomicType.G_DAY, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:hexBinary
     */
	public static final ItemType HEX_BINARY = new BuiltInAtomicItemType(BuiltInAtomicType.HEX_BINARY,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:base64Binary
     */
	public static final ItemType BASE64_BINARY = new BuiltInAtomicItemType(BuiltInAtomicType.BASE64_BINARY,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:anyURI
     */
    public static final ItemType ANY_URI = new BuiltInAtomicItemType(BuiltInAtomicType.ANY_URI, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:QName
     */
    public static final ItemType QNAME = new BuiltInAtomicItemType(BuiltInAtomicType.QNAME, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:NOTATION
     */
	public static final ItemType NOTATION = new BuiltInAtomicItemType(BuiltInAtomicType.NOTATION,
		defaultConversionRules);

    /**
     * ItemType representing the XPath-defined type xs:untypedAtomic
     */
	public static final ItemType UNTYPED_ATOMIC = new BuiltInAtomicItemType(BuiltInAtomicType.UNTYPED_ATOMIC,
		defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:decimal
     */
    public static final ItemType DECIMAL = new BuiltInAtomicItemType(BuiltInAtomicType.DECIMAL, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:float
     */
    public static final ItemType FLOAT = new BuiltInAtomicItemType(BuiltInAtomicType.FLOAT, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:double
     */
    public static final ItemType DOUBLE = new BuiltInAtomicItemType(BuiltInAtomicType.DOUBLE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:integer
     */
    public static final ItemType INTEGER = new BuiltInAtomicItemType(BuiltInAtomicType.INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:nonPositiveInteger
     */
	public static final ItemType NON_POSITIVE_INTEGER = new BuiltInAtomicItemType(
		BuiltInAtomicType.NON_POSITIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:negativeInteger
     */
	public static final ItemType NEGATIVE_INTEGER = new BuiltInAtomicItemType(BuiltInAtomicType.NEGATIVE_INTEGER,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:long
     */
    public static final ItemType LONG = new BuiltInAtomicItemType(BuiltInAtomicType.LONG, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:int
     */
    public static final ItemType INT = new BuiltInAtomicItemType(BuiltInAtomicType.INT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:short
     */
    public static final ItemType SHORT = new BuiltInAtomicItemType(BuiltInAtomicType.SHORT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:byte
     */
    public static final ItemType BYTE = new BuiltInAtomicItemType(BuiltInAtomicType.BYTE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:nonNegativeInteger
     */
	public static final ItemType NON_NEGATIVE_INTEGER = new BuiltInAtomicItemType(
		BuiltInAtomicType.NON_NEGATIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:positiveInteger
     */
	public static final ItemType POSITIVE_INTEGER = new BuiltInAtomicItemType(BuiltInAtomicType.POSITIVE_INTEGER,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedLong
     */
	public static final ItemType UNSIGNED_LONG = new BuiltInAtomicItemType(BuiltInAtomicType.UNSIGNED_LONG,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedInt
     */
	public static final ItemType UNSIGNED_INT = new BuiltInAtomicItemType(BuiltInAtomicType.UNSIGNED_INT,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedShort
     */
	public static final ItemType UNSIGNED_SHORT = new BuiltInAtomicItemType(BuiltInAtomicType.UNSIGNED_SHORT,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedByte
     */
	public static final ItemType UNSIGNED_BYTE = new BuiltInAtomicItemType(BuiltInAtomicType.UNSIGNED_BYTE,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:yearMonthDuration
     */
	public static final ItemType YEAR_MONTH_DURATION = new BuiltInAtomicItemType(BuiltInAtomicType.YEAR_MONTH_DURATION,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:dayTimeDuration
     */
	public static final ItemType DAY_TIME_DURATION = new BuiltInAtomicItemType(BuiltInAtomicType.DAY_TIME_DURATION,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:normalizedString
     */
	public static final ItemType NORMALIZED_STRING = new BuiltInAtomicItemType(BuiltInAtomicType.NORMALIZED_STRING,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:token
     */
    public static final ItemType TOKEN = new BuiltInAtomicItemType(BuiltInAtomicType.TOKEN, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:language
     */
	public static final ItemType LANGUAGE = new BuiltInAtomicItemType(BuiltInAtomicType.LANGUAGE,
		defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:Name
     */
    public static final ItemType NAME = new BuiltInAtomicItemType(BuiltInAtomicType.NAME, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:NMTOKEN
     */
    public static final ItemType NMTOKEN = new BuiltInAtomicItemType(BuiltInAtomicType.NMTOKEN, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:NCName
     */
    public static final ItemType NCNAME = new BuiltInAtomicItemType(BuiltInAtomicType.NCNAME, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:ID
     */
    public static final ItemType ID = new BuiltInAtomicItemType(BuiltInAtomicType.ID, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:IDREF
     */
    public static final ItemType IDREF = new BuiltInAtomicItemType(BuiltInAtomicType.IDREF, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:ENTITY
     */
    public static final ItemType ENTITY = new BuiltInAtomicItemType(BuiltInAtomicType.ENTITY, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:dateTimeStamp
     * (introduced in XSD 1.1)
     */
	public static final ItemType DATE_TIME_STAMP = new BuiltInAtomicItemType(BuiltInAtomicType.DATE_TIME_STAMP,
		defaultConversionRules);

    /**
     * Get the conversion rules implemented by this type. The conversion rules reflect variations
     * between different versions of the W3C specifications, for example XSD 1.1 allows "+INF" as
     * a lexical representation of xs:double, while XSD 1.0 does not.
     * @return the conversion rules
     */
    public abstract ConversionRules getConversionRules();

    /**
     * Determine whether this item type matches a given item.
     *
     * @param item the item to be tested against this item type
     * @return true if the item matches this item type, false if it does not match.
     */
    public abstract boolean matches(XdmItem item);

    /**
     * Determine whether this ItemType subsumes another ItemType. Specifically,
     * <code>A.subsumes(B) is true if every value that matches the ItemType B also matches
     * the ItemType A.
     * @param other the other ItemType
     * @return true if this ItemType subsumes the other ItemType. This includes the case where A and B
     * represent the same ItemType.
     * @since 9.1
     */
    public abstract boolean subsumes(ItemType other);

    /**
     * Method to get the underlying Saxon implementation object
     *
     * <p>This gives access to Saxon methods that may change from one release to another.</p>
     * @return the underlying Saxon implementation object
     */
    public abstract net.sf.saxon.type.ItemType getUnderlyingItemType();

    /**
     * Test whether two ItemType objects represent the same type
     * @param other the other ItemType object
     * @return true if the other object is an ItemType representing the same type
     * @since 9.5
     */
    public final boolean equals(Object other) {
		return other instanceof ItemType && getUnderlyingItemType().equals(((ItemType) other).getUnderlyingItemType());
    }
    /**
     * Get a hash code with semantics corresponding to the equals() method
     * @return the hash code
     * @since 9.5
     */
    public final int hashCode() {
        return getUnderlyingItemType().hashCode();
    }
}
