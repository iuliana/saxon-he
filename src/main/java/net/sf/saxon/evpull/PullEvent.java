////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

/**
 * A PullEvent is one of the following:
 *
 * <ul>
 * <li>An item (that is, a node or an atomic value)</li>
 * <li>A startElement, endElement, startDocument, or endDocument event</li>
 * <li>An EventIterator, representing a sequence of PullEvents</li>
 * </ul>
 */

public interface PullEvent {
}

