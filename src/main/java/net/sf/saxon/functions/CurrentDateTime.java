////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DateTimeValue;

/**
* This class implements the XPath 2.0 functions
 * current-date(), current-time(), and current-dateTime(), as
 * well as the function implicit-timezone(). The value that is required
 * is inferred from the type of result required.
*/


public class CurrentDateTime extends SystemFunctionCall implements Callable {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
    * Determine the dependencies
    */

    public int getIntrinsicDependencies() {
        // current date/time is part of the context, but it is fixed for a transformation, so
        // we don't need to manage it as a dependency: expressions using it can be freely
        // rearranged
       return StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
    }

    /**
    * Evaluate in a general context
    */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        final DateTimeValue dt = DateTimeValue.getCurrentDateTime(context);
        final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        final int targetType = getItemType(th).getPrimitiveType();
        switch (targetType) {
            case StandardNames.XS_DATE_TIME:
                return dt;
            case StandardNames.XS_DATE:
                return Converter.DATE_TIME_TO_DATE.convert(dt).asAtomic();
            case StandardNames.XS_TIME:
                return Converter.DATE_TIME_TO_TIME.convert(dt).asAtomic();
            case StandardNames.XS_DAY_TIME_DURATION:
            case StandardNames.XS_DURATION:
                return dt.getComponent(Component.TIMEZONE);
            default:
                throw new IllegalArgumentException("Wrong target type for current date/time");
        }
    }

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		return evaluateItem(context);
	}

}

