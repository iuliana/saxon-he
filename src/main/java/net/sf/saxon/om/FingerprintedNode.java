////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * This is a marker interface used to identify nodes that contain a namepool fingerprint. Although all nodes
 * are capable of returning a fingerprint, some (notably DOM, XOM, and JDOM nodes) need to calculate it on demand.
 * A node that implements this interface indicates that obtaining the fingerprint for use in name comparisons
 * is more efficient than using the URI and local name.
 */

public interface FingerprintedNode {
    /**
     * Get the value of the attribute with a given fingerprint.
     * @param fp the fingerprint of the required attribute
     * @return the string value of the attribute if present, or null if absent
     */
    public String getAttributeValue(int fp);
}

