////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.XSLTStaticContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

/**
* This class supports the XSLT element-available and function-available functions.
*/

public class ElementAvailable extends Available implements Callable {

    /**
    * preEvaluate: this method uses the static context to do early evaluation of the function
    * if the argument is known (which is the normal case)
     * @param visitor the expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        String lexicalQName = ((Literal)argument[0]).getValue().getStringValue();
        StaticContext env = visitor.getStaticContext();
        boolean b = ((XSLTStaticContext)env).isElementAvailable(lexicalQName);
        return Literal.makeLiteral(BooleanValue.get(b));
    }

    /**
     * Run-time evaluation.
    */

    public BooleanValue evaluateItem(/*@NotNull*/ XPathContext context) throws XPathException {
        AtomicValue av1 = (AtomicValue)argument[0].evaluateItem(context);
        StringValue nameValue = (StringValue)av1;
        String lexicalName = nameValue.getStringValue();
        boolean b = isElementAvailable(lexicalName, context);
        return BooleanValue.get(b);

    }

    /**
     * Determine at run-time whether a particular instruction is available. Returns true
     * only in the case of XSLT instructions and Saxon extension instructions; returns false
     * for user-defined extension instructions
     * @param lexicalName the lexical QName of the element
     * @param context the XPath evaluation context
     * @return true if the instruction is available, in the sense of the XSLT element-available() function
    */

    private boolean isElementAvailable(String lexicalName, XPathContext context) throws XPathException {

        // This is horribly inefficient. But hopefully it's hardly ever executed, because there
        // is very little point calling element-available() with a dynamically-constructed argument.
        // And the inefficiency is only incurred once, on the first call.

        // Note: this requires the compile-time classes to be available at run-time; it will need
        // changing if we ever want to build a run-time JAR file.

        StructuredQName qName;
        try {
            if (lexicalName.indexOf(':') < 0) {
                String uri = nsContext.getURIForPrefix("", true);
                qName = new StructuredQName("", uri, lexicalName);
            } else {
                boolean is30 = context.getController().getExecutable().isAllowXPath30();
                qName = StructuredQName.fromLexicalQName(lexicalName,
                    false, is30, context.getConfiguration().getNameChecker(),
                    nsContext);
            }
        } catch (XPathException e) {
            e.setErrorCode("XTDE1440");
            e.setLocator(this);
            e.setXPathContext(context);
            throw(e);
        }

        try {
            PreparedStylesheet pss = (PreparedStylesheet)context.getController().getExecutable();
            return pss.getStyleNodeFactory().isElementAvailable(qName.getURI(), qName.getLocalPart());
        } catch (Exception err) {
            //err.printStackTrace();
            return false;
        }
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        String lexicalQName = arguments[0].head().getStringValue();
        boolean b = isElementAvailable(lexicalQName, context);
        return BooleanValue.get(b);
    }
}

