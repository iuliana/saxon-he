////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;

final class ChildEnumeration extends TreeEnumeration {

    public ChildEnumeration(NodeImpl node, NodeTest nodeTest) {
        super(node, nodeTest);
        next = node.getFirstChild();
        while (!conforms(next)) {
            step();
        }
    }

    protected void step() {
        next = next.getNextSibling();
    }

    /**
    * Get another enumeration of the same nodes
    */

    /*@NotNull*/ public AxisIterator getAnother() {
        return new ChildEnumeration(start, nodeTest);
    }
}

