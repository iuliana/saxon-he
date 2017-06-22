////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import java.io.Serializable;

/**
 * This class (which has one instance per Configuration) is used to allocate unique document
 * numbers. It's a separate class so that it can act as a monitor for synchronization
 */
public class DocumentNumberAllocator implements Serializable {

    // Changed to a log in Saxon 9.4, because a user reported an int overflowing
    // on a system that had been in live operation for several months. The effect wasn't fatal,
    // but could cause incorrect node identity tests.

    private long nextDocumentNumber = 0;

    /**
     * Allocate a unique document number
     * @return a unique document number
     */

    public synchronized long allocateDocumentNumber() {
        return nextDocumentNumber++;
    }
}

