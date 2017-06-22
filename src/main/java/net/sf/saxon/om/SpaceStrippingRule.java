////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.trans.XPathException;

/**
 * Interface for deciding whether a particular element is to have whitespace text nodes stripped
 */

public interface SpaceStrippingRule {

    /**
    * Decide whether an element is in the set of white-space preserving element types
    *
     *
     * @param nodeName Identifies the name of the element whose whitespace is to
      * be preserved
      * @return {@link net.sf.saxon.event.Stripper#ALWAYS_PRESERVE} if the element is in the set of white-space preserving
     *  element types, {@link net.sf.saxon.event.Stripper#ALWAYS_STRIP} if the element is to be stripped regardless of the
     * xml:space setting, and {@link net.sf.saxon.event.Stripper#STRIP_DEFAULT} otherwise
     * @throws net.sf.saxon.trans.XPathException if the rules are ambiguous and ambiguities are to be
     * reported as errors
    */

    public abstract byte isSpacePreserving(NodeName nodeName) throws XPathException;

}

