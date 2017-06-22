////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.Location;
import net.sf.saxon.type.Type;

/**
 *  A built-in set of template rules that ignores the current node and does an apply-templates
 *  to its children.
 */
public class ShallowSkipRuleSet implements BuiltInRuleSet {

    private static ShallowSkipRuleSet THE_INSTANCE = new ShallowSkipRuleSet();

    /**
     * Get the singleton instance of this class
     * @return the singleton instance
     */

    public static ShallowSkipRuleSet getInstance() {
        return THE_INSTANCE;
    }

    private ShallowSkipRuleSet() {}

    /**
     * Perform the built-in template action for a given item.
     * @param item         the item to be processed
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     *                     the built-in template to be invoked
     * @throws XPathException
     *          if any dynamic error occurs
     */

    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, /*@NotNull*/ XPathContext context,
                        int locationId) throws XPathException {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)item;
            switch(node.getNodeKind()) {
                case Type.DOCUMENT:
                case Type.ELEMENT:
                    SequenceIterator iter = node.iterateAxis(AxisInfo.CHILD);
                    XPathContextMajor c2 = context.newContext();
                    c2.setOriginatingConstructType(Location.BUILT_IN_TEMPLATE);
                    c2.setCurrentIterator(iter);
                    TailCall tc = c2.getCurrentMode().applyTemplates(parameters, tunnelParams, c2, locationId);
                    while (tc != null) {
                        tc = tc.processLeavingTail();
                    }
                    return;
                case Type.TEXT:
                case Type.ATTRIBUTE:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                case Type.NAMESPACE:
                    // no action
            }
        } else {
            // no action (e.g. for atomic values and function items
        }
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */
    public int getDefaultAction(int nodeKind) {
        switch (nodeKind) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                return SHALLOW_SKIP;
            default:
                return DEEP_SKIP;
        }
    }
}

