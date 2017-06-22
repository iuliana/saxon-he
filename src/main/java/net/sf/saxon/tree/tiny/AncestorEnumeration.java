////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.AxisIteratorImpl;

/**
* This class enumerates the ancestor:: or ancestor-or-self:: axes,
* starting at a given node. The start node will never be the root.
*/

final class AncestorEnumeration extends AxisIteratorImpl {

    private TinyNodeImpl startNode;
    private NodeTest test;
    private boolean includeSelf;

    public AncestorEnumeration(TinyNodeImpl node, NodeTest nodeTest, boolean includeSelf) {
        test = nodeTest;
        startNode = node;
        this.includeSelf = includeSelf;
        current = startNode;
    }

    /*@Nullable*/ public NodeInfo next() {
        if (position <= 0) {
            if (position < 0) {
                return null;
            }
            if (position==0 && includeSelf && test.matches(startNode)) {
                current = startNode;
                position = 1;
                return current;
            }
        }
        assert current != null;
        NodeInfo node = current.getParent();
        while (node != null && !test.matches(node)) {
            node = node.getParent();
        }
        current = node;
        if (node == null) {
            position = -1;
        } else {
            position++;
        }
        return current;
    }

    /**
    * Get another enumeration of the same nodes
    */

    /*@NotNull*/ public AxisIterator getAnother() {
        return new AncestorEnumeration(startNode, test, includeSelf);
    }

}

