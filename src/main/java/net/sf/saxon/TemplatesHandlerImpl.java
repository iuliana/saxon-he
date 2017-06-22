////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.CommentStripper;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.event.StartTagBuffer;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.om.StylesheetSpaceStrippingRule;
import net.sf.saxon.style.StyleNodeFactory;
import net.sf.saxon.style.UseWhenFilter;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import org.xml.sax.Locator;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TemplatesHandler;


/**
  * <b>TemplatesHandlerImpl</b> implements the javax.xml.transform.sax.TemplatesHandler
  * interface. It acts as a ContentHandler which receives a stream of
  * SAX events representing a stylesheet, and returns a Templates object that
  * represents the compiled form of this stylesheet.
  * @author Michael H. Kay
  */

public class TemplatesHandlerImpl extends ReceivingContentHandler implements TemplatesHandler {

    private LinkedTreeBuilder builder;
    private StyleNodeFactory nodeFactory;
    private Templates templates;
    private String systemId;

    /**
     * Create a TemplatesHandlerImpl and initialise variables. The constructor is protected, because
     * the Filter should be created using newTemplatesHandler() in the SAXTransformerFactory
     * class
     * @param config the Saxon configuration
    */

    protected TemplatesHandlerImpl(Configuration config) {

        setPipelineConfiguration(config.makePipelineConfiguration());

        nodeFactory = config.makeStyleNodeFactory();

        builder = new LinkedTreeBuilder(getPipelineConfiguration());
        builder.setNodeFactory(nodeFactory);
        builder.setLineNumbering(true);

        PreparedStylesheet sheet = new PreparedStylesheet(config, config.getDefaultXsltCompilerInfo());

        UseWhenFilter useWhenFilter = new UseWhenFilter(sheet, builder);

        StartTagBuffer startTagBuffer = new StartTagBuffer(useWhenFilter);
        useWhenFilter.setStartTagBuffer(startTagBuffer);

        StylesheetSpaceStrippingRule rule = new StylesheetSpaceStrippingRule(config.getNamePool());
        Stripper styleStripper = new Stripper(rule, startTagBuffer);
        CommentStripper commentStripper = new CommentStripper(styleStripper);
        setReceiver(commentStripper);

    }

    /**
    * Get the Templates object to be used for a transformation
    */

    /*@Nullable*/ public Templates getTemplates() {
        if (templates==null) {
            DocumentImpl doc = (DocumentImpl)builder.getCurrentRoot();
            builder.reset();
            if (doc==null) {
                return null;
            }

            final Configuration config = getConfiguration();
            CompilerInfo info = new CompilerInfo(config.getDefaultXsltCompilerInfo());
            info.setXsltVersion(nodeFactory.getXsltProcessorVersion());
            PreparedStylesheet sheet = new PreparedStylesheet(config, info);

            try {
                sheet.setStylesheetDocument(doc);
                templates = sheet;
            } catch (XPathException tce) {
                if (!tce.hasBeenReported()) {
                    try {
                        info.getErrorListener().fatalError(tce);
                    } catch (TransformerException e2) {
                        //
                    }
                }
                // don't know why we aren't allowed to just throw it!
                throw new IllegalStateException(tce.getMessage());
            }
        }

        return templates;
    }

    /**
     * Set the SystemId of the document. Note that if this method is called, any locator supplied
     * to the setDocumentLocator() method is ignored. This also means that no line number information
     * will be available.
     * @param url the system ID (base URI) of the stylesheet document, which will be used in any error
     * reporting and also for resolving relative URIs in xsl:include and xsl:import. It will also form
     * the static base URI in the static context of XPath expressions.
    */

    public void setSystemId(String url) {
        systemId = url;
        builder.setSystemId(url);
        super.setDocumentLocator(new Locator() {
            public int getColumnNumber() {
                return -1;
            }

            public int getLineNumber() {
                return -1;
            }

            /*@Nullable*/ public String getPublicId() {
                return null;
            }

            public String getSystemId() {
                return systemId;
            }
        });
    }

    /**
    * Callback interface for SAX: not for application use
    */

    public void setDocumentLocator (final Locator locator) {
        // If the user has called setSystemId(), we use that system ID in preference to this one,
        // which probably comes from the XML parser possibly via some chain of SAX filters
        if (systemId == null) {
            super.setDocumentLocator(locator);
        }
    }

    /**
    * Get the systemId of the document
    */

    public String getSystemId() {
        return systemId;
    }


}

