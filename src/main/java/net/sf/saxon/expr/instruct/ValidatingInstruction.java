////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.type.SchemaType;

import javax.xml.transform.SourceLocator;

/**
 * Interface implemented by instructions that have validation and type attributes
 */
public interface ValidatingInstruction extends SourceLocator {

    /*@Nullable*/ SchemaType getSchemaType();

    int getValidationAction();
}

