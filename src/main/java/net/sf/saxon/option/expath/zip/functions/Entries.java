////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.expath.zip.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.option.expath.zip.library.SaxonTreeBuilder;
import net.sf.saxon.option.expath.zip.library.ZipFacade;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.SequenceType;


/**
 * The implementation of the entries, a function of the EXPath zip module
 *  Returns a zip:file element that describes the hierarchical structure
 *  of the ZIP file identified by $href in terms of directories and ZIP entries
 * */
public class Entries extends ExtensionFunctionDefinition {
    private final static StructuredQName name =
            new StructuredQName("", NamespaceConstant.EXPATH_ZIP, "entries");

    @Override
    public StructuredQName getFunctionQName() {
        return name;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_ANY_URI};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.ELEMENT, StaticProperty.EXACTLY_ONE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            private String baseURI;
            @Override
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

                SaxonTreeBuilder builder = new SaxonTreeBuilder(context);
                AnyURIValue href = (AnyURIValue)arguments[0].head();
                assert href != null;
                ZipFacade zip = new ZipFacade(baseURI);
                zip.entries(href.getStringValue(), builder);
                return builder.getRoot();
            }

            @Override
            public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
                if (baseURI == null) {
                    baseURI = context.getBaseURI();
                }
            }
        };
    }
}

