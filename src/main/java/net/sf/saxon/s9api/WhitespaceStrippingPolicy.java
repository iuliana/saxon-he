////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.value.Whitespace;

/**
 * WhitespaceStrippingPolicy is class defining the possible policies for handling
 * whitespace text nodes in a source document.
 */

public class WhitespaceStrippingPolicy {

    private int policy;
    private SpaceStrippingRule stripperRules;

    /**
     * The value NONE indicates that all whitespace text nodes are retained
     */
    public static final WhitespaceStrippingPolicy NONE = new WhitespaceStrippingPolicy(Whitespace.NONE);

    /**
     * The value IGNORABLE indicates that whitespace text nodes in element-only content are
     * discarded. Content is element-only if it is defined by a schema or DTD definition that
     * does not allow mixed or PCDATA content.
     */
    public static final WhitespaceStrippingPolicy IGNORABLE = new WhitespaceStrippingPolicy(Whitespace.IGNORABLE);

    /**
     * The value ALL indicates that all whitespace-only text nodes are discarded.
     */
    public static final WhitespaceStrippingPolicy ALL = new WhitespaceStrippingPolicy(Whitespace.ALL);

    /**
     * UNSPECIFIED means that no other value has been specifically requested.
     */
    public static final WhitespaceStrippingPolicy UNSPECIFIED = new WhitespaceStrippingPolicy(Whitespace.UNSPECIFIED);

    private WhitespaceStrippingPolicy(int policy) {
        this.policy = policy;
    }

    /**
     * Create a WhitespaceStrippingPolicy based on the xsl:strip-space and xsl:preserve-space declarations
     * in a given XSLT stylesheet
     * @param executable the stylesheet containing the xsl:strip-space and xsl:preserve-space declarations
     */
    
    protected WhitespaceStrippingPolicy (Executable executable) {
        policy = Whitespace.XSLT;
        stripperRules = executable.getStripperRules();
    }

    protected int ordinal() {
        return policy;
    }

    /*@NotNull*/ protected FilterFactory makeStripper() {
        return new FilterFactory() {
            public ProxyReceiver makeFilter(Receiver next) {
                return new Stripper(stripperRules, next);
            }
        };
    }
}

