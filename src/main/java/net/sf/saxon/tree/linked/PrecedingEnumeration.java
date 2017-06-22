////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;

final class PrecedingEnumeration extends TreeEnumeration {

    /*@Nullable*/ NodeImpl nextAncestor;
    
    public PrecedingEnumeration(/*@NotNull*/ NodeImpl node, NodeTest nodeTest) {
        super(node, nodeTest);

        // we need to avoid returning ancestors of the starting node
        nextAncestor = (NodeImpl)node.getParent();
        advance();
    }


    /**
    * Special code to skip the ancestors of the start node
    */

    protected boolean conforms(/*@Nullable*/ NodeImpl node) {
        // ASSERT: we'll never test the root node, because it's always
        // an ancestor, so nextAncestor will never be null.
        if (node!=null) {
            if (node.isSameNodeInfo(nextAncestor)) {
                nextAncestor = (NodeImpl)nextAncestor.getParent();
                return false;
            }
        }
        return super.conforms(node);
    }

    protected void step() {
        next = next.getPreviousInDocument();
    }

    /**
    * Get another enumeration of the same nodes
    */
    
    /*@NotNull*/ public AxisIterator getAnother() {
        return new PrecedingEnumeration(start, nodeTest);
    }

}

