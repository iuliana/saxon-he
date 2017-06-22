////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.SourceLocator;

/**
 * An abstraction of ExpressionVisitor that provides the minimal set of services needed by
 * the TypeChecker; used to allow run-time type-checking of higher order function arguments without
 * retaining the whole static context
 */
public interface TypeCheckerEnvironment {

    public Configuration getConfiguration();

    public void issueWarning(String message, SourceLocator locator);

    public CollationMap getCollationMap();

    public XPathContext makeDynamicContext();

    public Expression simplify(Expression exp) throws XPathException;

    public Expression typeCheck(Expression exp, ExpressionVisitor.ContextItemType contextItemType) throws XPathException;
}

