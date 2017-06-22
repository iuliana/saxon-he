////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.value.Whitespace;

import java.util.Collections;
import java.util.Set;


/**
 * This class has a singleton instance which represents the XML Schema 1.1 built-in type xs:error
 */

public final class ErrorType implements UnionType, PlainType {

    /*@NotNull*/ private static ErrorType theInstance = new ErrorType();

    /**
     * Private constructor
     */
    private ErrorType() {
    }

    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */

    /*@NotNull*/ public String getName() {
        return "error";
    }

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     *         of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */

    public String getTargetNamespace() {
        return NamespaceConstant.SCHEMA;
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */
    public String getEQName() {
        return "Q{" + NamespaceConstant.SCHEMA + "}error";
    }

    public boolean containsListType() {
        return false;
    }

    public Set<PlainType> getPlainMemberTypes() {
        return Collections.emptySet();
    }

    /**
     * Return true if this is an external object type, that is, a Saxon-defined type for external
     * Java or .NET objects
     */

    public boolean isExternalType() {
        return false;
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     */

    public boolean isBuiltInType() {
        return true;
    }

    /**
     * Get the redefinition level. This is zero for a component that has not been redefined;
     * for a redefinition of a level-0 component, it is 1; for a redefinition of a level-N
     * component, it is N+1. This concept is used to support the notion of "pervasive" redefinition:
     * if a component is redefined at several levels, the top level wins, but it is an error to have
     * two versions of the component at the same redefinition level.
     * @return the redefinition level
     */

    public int getRedefinitionLevel() {
        return 0;
    }

    /**
     * Get the URI of the schema document containing the definition of this type
     * @return null for a built-in type
     */

    /*@Nullable*/ public String getSystemId() {
        return null;
    }

    /**
     * Get the singular instance of this class
     * @return the singular object representing xs:anyType
     */

    /*@NotNull*/ public static ErrorType getInstance() {
        return theInstance;
    }

    /**
     * Get the validation status - always valid
     */
    public int getValidationStatus()  {
        return VALIDATED;
    }

    /**
     * Get the base type
     * @return AnyType
     */

    /*@NotNull*/ public SchemaType getBaseType() {
        return AnySimpleType.getInstance();
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * @return the base type.
     */

    /*@NotNull*/ public SchemaType getKnownBaseType() throws IllegalStateException {
        return getBaseType();
    }

    /**
     * Test whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */

    public boolean isComplexType() {
        return false;
    }

    /**
     * Test whether this SchemaType is a simple type
     * @return true if this SchemaType is a simple type
     */

    public boolean isSimpleType() {
        return true;
    }

    /**
     * Get the fingerprint of the name of this type
     * @return the fingerprint.
     */

    public int getFingerprint() {
        return StandardNames.XS_ERROR;
    }



    /**
     * Get the namecode of the name of this type. This includes the prefix from the original
     * type declaration: in the case of built-in types, there may be a conventional prefix
     * or there may be no prefix.
     */

    public int getNameCode() {
        return StandardNames.XS_ERROR;
    }

    /**
     * Get a description of this type for use in diagnostics
     * @return the string "xs:anyType"
     */

    /*@NotNull*/ public String getDescription() {
        return "xs:error";
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type
     */

    /*@NotNull*/ public String getDisplayName() {
        return "xs:error";
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     */

    public boolean isSameType(SchemaType other) {
        return (other instanceof ErrorType);
    }

    /**
     * Get the typed value of a node that is annotated with this schema type.
     * @param node the node whose typed value is required
     * @return the typed value.
     * @since 8.5
     */

    /*@NotNull*/ public AtomicSequence atomize(/*@NotNull*/ NodeInfo node) {
        return new UntypedAtomicValue(node.getStringValueCS());
    }

     /**
     * Check that this type is validly derived from a given type
     *
     * @param type  the type from which this type is derived
     * @param block the derivations that are blocked by the relevant element declaration
     * @throws net.sf.saxon.type.SchemaException
     *          if the derivation is not allowed
     */

    public void checkTypeDerivationIsOK(/*@NotNull*/ SchemaType type, int block) throws SchemaException {
        if (type == this || type == AnySimpleType.getInstance()) {
            return;
        }
        throw new SchemaException("Type xs:error is not validly derived from " + type.getDescription());
    }

    /**
     * Test whether this Simple Type is an atomic type
     * @return false, this is not (necessarily) an atomic type
     */

    public boolean isAtomicType() {
        return false;
    }

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list.
     */

    public boolean isIdType() {
        return false;
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    public boolean isIdRefType() {
        return false;
    }

    public boolean isAnonymousType() {
        return false;
    }


    /**
     * Determine whether this is a list type
     * @return false (it isn't a list type)
     */
    public boolean isListType() {
        return false;
    }

    /**
     * Determin whether this is a union type
     * @return true (this is a union type with no members)
     */
    public boolean isUnionType() {
        return true;
    }

    /**
     * Get the built-in ancestor of this type in the type hierarchy
     * @return xs:anySimpleType
     */
    /*@NotNull*/ public SchemaType getBuiltInBaseType() {
        return AnySimpleType.getInstance();
    }

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type
     *
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules
     * @return an iterator over the atomic sequence comprising the typed value. The objects
     *         returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue}
     * @throws ValidationException if the supplied value is not in the lexical space of the data type (which is
     * always true for this type)
     */

    /*@NotNull*/ public AtomicSequence getTypedValue(CharSequence value, NamespaceResolver resolver, ConversionRules rules) throws ValidationException {
        throw new ValidationException("Cast to xs:error always fails");
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     * @param value the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     * is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     * content will throw an UnsupportedOperationException.
     * @param rules
     * @return null if validation succeeds (which it never does for this implementation)
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     * resolver is supplied
     */
    /*@NotNull*/ public ValidationFailure validateContent(/*@NotNull*/ CharSequence value, NamespaceResolver nsResolver, /*@NotNull*/ ConversionRules rules) {
        return new ValidationFailure("No content is ever valid against the type xs:error");
    }

    /**
     * Test whether this type represents namespace-sensitive content
     * @return false
     */
    public boolean isNamespaceSensitive() {
        return false;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-signnificant
     * integer with fields such as {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST} and {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}
     *
     * @return the value of the 'block' attribute for this type
     */

    public int getBlock() {
        return 0;
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_RESTRICTION}
     */

    public int getDerivationMethod() {
        return SchemaType.DERIVATION_RESTRICTION;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST}
     * @return true if this kind of derivation is allowed
     */

    public boolean allowsDerivation(int derivation) {
        return false;
    }

    /**
     * Get the types of derivation that are not permitted, by virtue of the "final" property.
     *
     * @return the types of derivation that are not permitted, as a bit-significant integer
     *         containing bits such as {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}
     */
    public int getFinalProhibitions() {
        return SchemaType.DERIVATION_EXTENSION | SchemaType.DERIVATION_RESTRICTION | SchemaType.DERIVATION_LIST |
                SchemaType.DERIVATION_UNION;
    }

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#PRESERVE}, {@link net.sf.saxon.value.Whitespace#COLLAPSE},
     *         {@link net.sf.saxon.value.Whitespace#REPLACE}.
     */

    public int getWhitespaceAction() {
        return Whitespace.COLLAPSE;
    }

    /**
     * Analyze an expression to see whether the expression is capable of delivering a value of this
     * type.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @param env
     */

    public void analyzeContentExpression(Expression expression, int kind, StaticContext env) throws XPathException {
        throw new XPathException("No expression can ever return a value of type xs:error");
    }

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     */

    public CharSequence preprocess(CharSequence input) {
        return input;
    }

    /**
     * Reverse any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess. This is called when converting a value of this type to
     * a string
     * @param input the value to be postprocessed: this is the "ordinary" result of converting
     *              the value to a string
     * @return the value after postprocessing
     */

    public CharSequence postprocess(CharSequence input) throws ValidationException {
        return input;
    }

    public boolean isPlainType() {
        return true;
    }

    public boolean matches(Item item, XPathContext context) {
        return false;
    }

    public boolean matchesItem(Item item, boolean allowURIPromotion, Configuration config) {
        return false;
    }

    public ItemType getSuperType(TypeHierarchy th) {
        return AnyItemType.getInstance();
    }

    public ItemType getPrimitiveItemType() {
        return this;
    }

    public int getPrimitiveType() {
        return Type.ITEM;
    }

    public double getDefaultPriority() {
        return -1000;
    }

    public PlainType getAtomizedItemType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    public boolean isAtomizable() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public SequenceType getResultTypeOfCast() {
        return SequenceType.EMPTY_SEQUENCE;
    }

    public String toString() {
        return "xs:error";
    }


}

