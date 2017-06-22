////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.exslt;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
* This class implements extension functions in the
* http://exslt.org/common namespace. <p>
*/



public abstract class Common  {

    /**
     * Class is not instantiated
     */
    private Common() {
    }

    /**
    * Convert a result tree fragment to a node-set. This is a hangover from XSLT 1.0;
    * it is implemented as a no-op.
    */

    public static Sequence nodeSet(Sequence frag) {
        return frag;
    }

    /**
    * Return the type of the supplied value: "sequence", "string", "number", "boolean",
    * "external". (EXSLT spec not yet modified to cater for XPath 2.0 data model)
    */

    public static String objectType(/*@NotNull*/ XPathContext context, Sequence value) {
        final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        ItemType type = SequenceTool.getItemType(value, th);
        if (th.isSubType(type, AnyNodeTest.getInstance())) {
            return "node-set";
        } else if (th.isSubType(type, BuiltInAtomicType.STRING)) {
            return "string";
        } else if (th.isSubType(type, BuiltInAtomicType.NUMERIC)) {
            return "number";
        } else if (th.isSubType(type, BuiltInAtomicType.BOOLEAN)) {
            return "boolean";
        } else {
            return type.toString();
        }
    }


}

