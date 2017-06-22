////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.value.StringValue;

/**
 * Subclass of Literal used specifically for string literals, as this is a common case
 */
public class StringLiteral extends Literal {

    /**
     * Create a StringLiteral that wraps a StringValue
     * @param value the StringValue
     */

    public StringLiteral(StringValue value) {
        super(value);
    }

    /**
     * Create a StringLiteral that wraps any CharSequence (including, of course, a String)
     * @param value the CharSequence to be wrapped
     */

    public StringLiteral(CharSequence value) {
        super(StringValue.makeStringValue(value));
    }

    /**
     * Get the string represented by this StringLiteral
     * @return the underlying string
     */

    public String getStringValue() {
        //noinspection RedundantCast
        return ((StringValue)getValue()).getStringValue();
    }

    /*@NotNull*/
    public Expression copy() {
        StringLiteral stringLiteral = new StringLiteral((StringValue)getValue());
        ExpressionTool.copyLocationInfo(this, stringLiteral);
        return stringLiteral;
    }
}

