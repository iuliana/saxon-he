////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

/**
 * Pull event representing the end of an element node
 */
public class EndElementEvent implements PullEvent {

    private final static EndElementEvent THE_INSTANCE = new EndElementEvent();

    public static EndElementEvent getInstance() {
        return THE_INSTANCE;
    }

    private EndElementEvent() {
    }


}
