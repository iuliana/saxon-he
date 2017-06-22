////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.TypeCheckerEnvironment;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * An ItemType representing the type function(). Subtypes represent function items with more specific
 * type signatures.
 *
 * <p>Note that although this class has a singleton instance representing the type <code>function(*)</code>,
 * there are also likely to be instances of subclasses representing more specific function types.</p>
 */
public class AnyFunctionType implements FunctionItemType {

    /*@NotNull*/ public final static AnyFunctionType ANY_FUNCTION = new AnyFunctionType();

    public static SequenceType SINGLE_FUNCTION =
            SequenceType.makeSequenceType(ANY_FUNCTION, StaticProperty.EXACTLY_ONE);

    /**
     * Get the singular instance of this type (Note however that subtypes of this type
     * may have any number of instances)
     * @return the singular instance of this type
     */

    /*@NotNull*/ public static AnyFunctionType getInstance() {
        return ANY_FUNCTION;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */

    public boolean isPlainType() {
        return false;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    public boolean isMapType() {
        return false;
    }

    /**
     * Get the argument types of the function
     * @return the argument types, as an array of SequenceTypes, or null if this is the generic function
     * type function(*)
     */
    /*@Nullable*/ public SequenceType[] getArgumentTypes() {
        return null;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item    The item to be tested
     * @param context the XPath dynamic evaluation context
     * @return true if the item is an instance of this type; false otherwise
     */
    public boolean matches(Item item, /*@NotNull*/ XPathContext context) {
        return matchesItem(item, false, context.getConfiguration());
    }

    /**
     * Test whether a given item conforms to this type
     * @param item              The item to be tested
     * @param allowURIPromotion if a URI value is to be treated as a string
     * @param config            The Saxon configuration
     * @return true if the item is an instance of this type; false otherwise
     */

    public boolean matchesItem(Item item, boolean allowURIPromotion, Configuration config) {
        return (item instanceof FunctionItem);
    }

    /**
     * Get the type from which this item type is derived by restriction. This
     * is the supertype in the XPath type heirarchy, as distinct from the Schema
     * base type: this means that the supertype of xs:boolean is xs:anyAtomicType,
     * whose supertype is item() (rather than xs:anySimpleType).
     * <p/>
     * In fact the concept of "supertype" is not really well-defined, because the types
     * form a lattice rather than a hierarchy. The only real requirement on this function
     * is that it returns a type that strictly subsumes this type, ideally as narrowly
     * as possible.
     * @param th the type hierarchy cache
     * @return the supertype, or null if this type is item()
     */

    public ItemType getSuperType(TypeHierarchy th) {
        return AnyItemType.getInstance();
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types. For function items it is the singular
     * instance FunctionItemType.getInstance().
     */

    /*@NotNull*/ public final ItemType getPrimitiveItemType() {
        return ANY_FUNCTION;
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    public final int getPrimitiveType() {
        return Type.FUNCTION;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */

    public String toString() {
        return "function(*)";
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     * @return the item type of the atomic values that will be produced when an item
     *         of this type is atomized
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        return null;
    }

    /**
     * Ask whether values of this type are atomizable
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        return false;
    }

    /**
     * Determine the relationship of one function item type to another
     * @return for example {@link TypeHierarchy#SUBSUMES}, {@link TypeHierarchy#SAME_TYPE}
     */

    public int relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == this) {
            return TypeHierarchy.SAME_TYPE;
        } else {
            return TypeHierarchy.SUBSUMES;
        }
    }

    /**
     * Create an expression whose effect is to apply function coercion to coerce a function from this type to another type
     * @param exp the expression that delivers the supplied sequence of function items (the ones in need of coercion)
     * @param role information for use in diagnostics
     * @param visitor the expression visitor, supplies context information
     * @return the coerced function, a function that calls the original function after checking the parameters
     */

    public Expression makeFunctionSequenceCoercer(Expression exp, RoleLocator role, TypeCheckerEnvironment visitor)
    throws XPathException {
        return exp;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        // no action
    }
    
    /**
     * Get the result type
     * @return the result type
     */

    public SequenceType getResultType() {
        return SequenceType.ANY_SEQUENCE;
    }

    public double getDefaultPriority() {
        return 0.5;
    }
}

