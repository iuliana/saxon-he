////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;

import java.util.HashMap;

/**
 * This class represents a collection of parameter values for use in schema validation;
 * it defines values for the parameters declared using the saxon:param XSD extension.
 *
 * The implementation is just a HashMap; giving the class a name helps type safety.
 */

public class ValidationParams extends HashMap<StructuredQName, Sequence> {

    public ValidationParams() {
        super(20);
    }

}

