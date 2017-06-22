////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.type.ItemType;

/**
  * A ItemTypePattern is a pattern that consists simply of an ItemType. In the past the ItemType
  * was always a NodeTest, but XSLT 3.0 introduces the ability for a pattern to also match atomic
  * values.
  * @author Michael H. Kay
  */

public class ItemTypePattern extends Pattern {

    private ItemType itemType;
    private double priority = Double.NaN;

    /**
     * Create an ItemTypePattern that matches all items of a given type
     * @param test the type that the items must satisfy for the pattern to match
     */

    public ItemTypePattern(ItemType test) {
        itemType = test;
    }

    /**
     * Set a priority to override the default priority. This is used when the pattern is written in a complex
     * form such as a[true()] justifying a priority of 0.5, but then simplifies down to an ItemTypePattern
     * @param priority the priority to be used if no explicit priority is given in the template rule
     */

    public void setPriority(double priority) {
        this.priority = priority;
    }


    /**
    * Determine whether this Pattern matches the given Node. This is the main external interface
    * for matching patterns: it sets current() to the node being tested
    *
     *
     *
     * @param item The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param context The context in which the match is to take place. Only relevant if the pattern
        * uses variables, or contains calls on functions such as document() or key(). Not used (and can be
         * set to null) in the case of patterns that are NodeTests
        * @return true if the node matches the Pattern, false otherwise
    */

    public boolean matches(Item item, XPathContext context) {
        return itemType.matches(item, context);
    }

    /**
    * Get a NodeTest that all the nodes matching this pattern must satisfy
    */

    public ItemType getItemType() {
        return itemType;
    }

    /**
    * Determine the default priority of this item type test when used on its own as a Pattern
    */

    public final double getDefaultPriority() {
    	return (Double.isNaN(priority) ? itemType.getDefaultPriority() : priority);
    }


    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE. For patterns that
     * do not match nodes, return -1.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    public int getNodeKind() {
        if (itemType instanceof NodeTest) {
            return itemType.getPrimitiveType();
        } else {
            return -1;
        }
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints
     */

    public int getFingerprint() {
        if (itemType instanceof NodeTest) {
            return ((NodeTest)itemType).getFingerprint();
        } else {
            return -1;
        }
    }

    /**
     * Display the pattern for diagnostics
     */

    public String toString() {
        return itemType.toString();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return (other instanceof ItemTypePattern) &&
                ((ItemTypePattern)other).itemType.equals(itemType);
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        return 0x7aeffea8 ^ itemType.hashCode();
    }



}

