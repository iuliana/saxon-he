////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;

import java.io.Serializable;

/**
 * A Comparer used for comparing nodes in document order. This
 * comparer is used when there is no guarantee that the nodes being compared
 * come from the same document
 *
 * @author Michael H. Kay
 *
 */

public final class GlobalOrderComparer implements ItemOrderComparer, Serializable {

    private static GlobalOrderComparer instance = new GlobalOrderComparer();

    /**
    * Get an instance of a GlobalOrderComparer. The class maintains no state
    * so this returns the same instance every time.
    */

    public static GlobalOrderComparer getInstance() {
        return instance;
    }

    public int compare(Item a, /*@NotNull*/ Item b) {
        if (a==b) {
            return 0;
        }
        long d1 = ((NodeInfo)a).getDocumentNumber();
        long d2 = ((NodeInfo)b).getDocumentNumber();
        if (d1 == d2) {
            return ((NodeInfo)a).compareOrder(((NodeInfo)b));
        }
        return Long.signum(d1 - d2);
    }
}

