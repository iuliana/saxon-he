////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * Interface representing a simple type of variety List
 */

public interface ListType extends SimpleType {
    
    /**
     * Returns the simpleType of the items in this ListType. This method assumes that the
     * item type has been fully resolved
     * @return the simpleType of the items in this ListType.
     * @throws IllegalStateException if the item type has not been fully resolved
    */

    /*@NotNull*/
    SimpleType getItemType();
}
