////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
* An xsl:sort element in the stylesheet. <br>
*/

public class XSLSort extends XSLSortOrMergeKey {



    public void validate(Declaration decl) throws XPathException {
       
    	super.validate(decl);
        stable      = typeCheck("stable", stable);
        sortKeyDefinition.setStable(stable);
       
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction). Default implementation returns Type.ITEM, indicating
     * that we don't know, it might be anything. Returns null in the case of an element
     * such as xsl:sort or xsl:variable that can appear in a sequence constructor but
     * contributes nothing to the result sequence.
     * @return the item type returned
     */

    /*@Nullable*/ protected ItemType getReturnedItemType() {
        return null;
    }


    public Expression getStable() {
        return stable;
    }


}

