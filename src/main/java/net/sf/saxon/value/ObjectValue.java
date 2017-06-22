////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.AbstractItem;
import net.sf.saxon.type.ExternalObjectType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;


/**
 * An XPath value that encapsulates a Java object. Such a value can only be constructed from
 * Java code either in a Java extension function, or in the calling application.
 *
 * <p>Until Saxon 9.4, ObjectValue was a subclass of AtomicValue, and external Java objects were
 * considered to be atomic. From 9.5, this has been changed so that an ObjectValue is another
 * kind of Item: a fourth kind, at the same level as nodes, atomic values, and function items.</p>
 *
 * <p>In the same way as ObjectValue is a wrapper around a Java instance object, the type of the
 * value is a wrapper around the corresponding Java class. These types have subtype-supertype
 * relationships that correspond to the Java class hierarchy. The top type in this branch of
 * the class hierarchy, which wraps the Java class "Object", has item() as its superclass.
 * These types can be referred to in the SequenceType syntax using QNames of the form
 * jt:full.package.name.ClassName, where the convetional prefix jt maps to the namespace
 * URI http://saxon.sf.net/java-type.</p>
 *
 * <p>An ObjectValue is no longer permitted to wrap a Java null. An empty sequence should be
 * used in this situation.</p>
 *
 * <p>The effective boolean value of a wrapped Java object, like that of a node, is always true.</p>
 *
 * <p>Atomizing a wrapped Java object is not allowed (fails with a dynamic error). The string()
 * function calls the wrapped object's toString() method, unless the wrapped object is null,
 * in which case it returns the zero-length string.</p>
*/

public class ObjectValue extends AbstractItem {

    /*@NotNull*/ private Object value;

    /**
    * Constructor
    * @param object the object to be encapsulated
    */

    public ObjectValue(/*@NotNull*/ Object object) {
        if (object == null) {
            throw new NullPointerException("External object cannot wrap a Java null");
        }
        value = object;
    }

    /**
     * Get the value of the item as a string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     * <p/>
     * If the calling code can handle any CharSequence, the method {@link #getStringValueCS} should
     * be used. If the caller requires a string, this method is preferred.
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValueCS
     * @since 8.4
     */
    public String getStringValue() {
        return value.toString();
    }

    /**
     * Get the string value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String. The method satisfies the rule that
     * <code>X.getStringValueCS().toString()</code> returns a string that is equal to
     * <code>X.getStringValue()</code>.
     * <p/>
     * Note that two CharSequence values of different types should not be compared using equals(), and
     * for the same reason they should not be used as a key in a hash table.
     * <p/>
     * If the calling code can handle any CharSequence, this method should
     * be used. If the caller requires a string, the {@link #getStringValue} method is preferred.
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValue
     * @since 8.4
     */
    public CharSequence getStringValueCS() {
        return value.toString();
    }

    /**
     * Determine the data type of the items in the expression, if possible
     *
     * @param th The TypeHierarchy.
     * @return for the default implementation: AnyItemType (not known)
     */

    /*@NotNull*/
    public ItemType getItemType(/*@Nullable*/ TypeHierarchy th) {
        return new ExternalObjectType(value.getClass());
    }

    /**
     * Display the type name for use in error messages
     * @return the type name. This will be in the form "java-type:" followed by the full class name of the
     * wrapped Java object.
     */

    /*@NotNull*/ public String displayTypeName() {
        return "java-type:" + value.getClass().getName();
    }

    /**
     * Get the effective boolean value of the value
     * @return true (always)
     */
    public boolean effectiveBooleanValue() {
        return true;
    }

    /**
     * Get the encapsulated object
     * @return the Java object that this external object wraps
    */

    public Object getObject() {
        return value;
    }

    /**
     * Determine if two ObjectValues are equal
     * @return true if the other object is also a wrapped Java object and if the two wrapped objects compare
     * equal using the Java equals() method.
    */

    public boolean equals(/*@NotNull*/ Object other) {
        if (other instanceof ObjectValue) {
            Object o = ((ObjectValue)other).value;
            return value.equals(o);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return value.hashCode();
    }



}

