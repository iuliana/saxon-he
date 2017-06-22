////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.om.Item;

import java.io.Serializable;

/**
 * Defines a set of built-in template rules (rules for use when no user-defined template
 * rules match a given node)
 */
public interface BuiltInRuleSet extends Serializable {

    /**
     * Perform the built-in template action for a given item.
     *
     * @param item the item to be processed
     * @param parameters the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param context the dynamic evaluation context
     * @param locationId location of the instruction (apply-templates, apply-imports etc) that caused
     * the built-in template to be invoked
     * @throws XPathException if any dynamic error occurs
     */

    public void process( Item item,
                         ParameterSet parameters,
                         ParameterSet tunnelParams,
                         XPathContext context,
                         int locationId) throws XPathException;

    /**
     * Get the default action for unmatched nodes
     * @param nodeKind the node kind
     * @return the default action for unmatched element nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */

    public int getDefaultAction(int nodeKind);

    public static final int DEEP_COPY = 1;
    public static final int DEEP_SKIP = 3;
    public static final int FAIL = 4;
    public static final int SHALLOW_COPY = 5;
    public static final int SHALLOW_SKIP = 6;
    public static final int TEXT_COPY = 7;

}

