////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.om.NamePool;

import java.io.PrintStream;

/**
 *  This class provides a diagnostic wrapper for the real NamePool. There are no formal interfaces
 *  to exploit it, but it can be patched into a system by use of setNamePool() on the Configuration,
 *  and its effect is to trace entry to selected methods, notably those that are synchronized, for
 *  diagnostic analysis.
 */
public class DiagnosticNamePool extends NamePool {

    public PrintStream printStream = System.err;

    public synchronized short allocateCodeForURI(String uri) {
        printStream.println("allocateCodeForURI");
        return super.allocateCodeForURI(uri);
    }

    public synchronized int allocate(String prefix, String uri, String localName) {
        printStream.println("allocate " + prefix + " : " + uri + " : " + localName);
        return super.allocate(prefix, uri, localName);
    }

}
