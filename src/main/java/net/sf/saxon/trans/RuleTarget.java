////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.trace.ExpressionPresenter;

import java.io.Serializable;

/**
 * The target of a rule, typically a Template.
 */
public interface RuleTarget extends Serializable {

    /**
     * Output diagnostic explanation to an ExpressionPresenter
     */

    public void explain(ExpressionPresenter presenter);
}

