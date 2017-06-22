////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * This exception occurs when an attempt is made to dereference a reference from one
 * schema component to another, if the target of the reference cannot be found. Note that
 * an unresolved reference is not necessarily an error: a schema containing unresolved
 * references may be used for validation, provided the components containing the
 * unresolved references are not actually used.
 */

public abstract class UnresolvedReferenceException extends RuntimeException {

    public UnresolvedReferenceException(String ref) {
        super(ref);
    }
}
