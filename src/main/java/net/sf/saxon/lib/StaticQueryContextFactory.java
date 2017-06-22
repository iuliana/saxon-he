////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.query.StaticQueryContext;

/**
 * Factory class for creating a customized instance of StaticQueryContext
 */

public class StaticQueryContextFactory {

    /**
     * Create a new instance of StaticQueryContext
     * @param config the configuration
     * @return the new StaticQueryContext instance
     */

    public StaticQueryContext newStaticQueryContext(Configuration config) {
        return new StaticQueryContext(config, false);
    }
}
