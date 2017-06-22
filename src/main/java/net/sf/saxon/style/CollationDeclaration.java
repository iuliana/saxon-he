////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.lib.StringCollator;

/**
 * Interface implemented by SaxonCollation, which is not available in Saxon-HE
 */
public interface CollationDeclaration {

    public String getCollationName();

    /**
     * Get the collator defined by this collation declaration
     * @return the StringCollator
     */

    public StringCollator getCollator();
    
}

