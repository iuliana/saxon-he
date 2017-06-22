////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

/**
 * Pull event representing the end of a document
 */
public class EndDocumentEvent implements PullEvent {

    private final static EndDocumentEvent THE_INSTANCE = new EndDocumentEvent();

    public static EndDocumentEvent getInstance() {
        return THE_INSTANCE;
    }

    private EndDocumentEvent() {
    }


}
