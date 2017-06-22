////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.trans.XPathException;

/**
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: 08/02/2013
 * Time: 10:46
 * To change this template use File | Settings | File Templates.
 */
public interface PatternParser {

    /**
     * Parse a string representing an XSLT pattern
     * @param pattern the pattern expressed as a String
     * @param env     the static context for the pattern
     * @return a Pattern object representing the result of parsing
     * @throws net.sf.saxon.trans.XPathException if the pattern contains a syntax error
     */

    public Pattern parsePattern(String pattern, StaticContext env) throws XPathException;


}

