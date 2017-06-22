////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

/**
 * Information about a sub-expression and its relationship to the parent expression
 */

public class SubExpressionInfo {

    public Expression expression;
    public boolean hasSameFocus;
    public boolean isEvaluatedRepeatedly;
    public int syntacticContext;

    public SubExpressionInfo(Expression child, boolean hasSameFocus, boolean isEvaluatedRepeatedly, int syntacticContext) {
        this.expression = child;
        this.hasSameFocus = hasSameFocus;
        this.isEvaluatedRepeatedly = isEvaluatedRepeatedly;
        this.syntacticContext = syntacticContext;

    }

}

