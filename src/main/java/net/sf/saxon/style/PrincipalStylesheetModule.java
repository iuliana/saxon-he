////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.CollationMap;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.RuleManager;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.TransformerException;
import java.util.*;

/**
 * Represents the stylesheet module at the root of the import tree, that is, the module
 * that includes or imports all the others. Note that this object is present at compile time only,
 * unlike the Executable, which also exists at run-time.
 */
public class PrincipalStylesheetModule extends StylesheetModule {

    // diagnostic switch to control output of timing information
    private final static boolean TIMING = false;

    private PreparedStylesheet preparedStylesheet;

    // table of imported schemas. The members of this set are strings holding the target namespace.
    private HashSet<String> schemaIndex = new HashSet<String>(10);

    // table of functions imported from XQuery library modules
    private XQueryFunctionLibrary queryFunctions;

    // library of functions that are in-scope for XPath expressions in this stylesheet
    private FunctionLibraryList functionLibrary;

    // version attribute on xsl:stylesheet element of principal stylesheet module
    private String version;

    // index of global variables and parameters, by StructuredQName
    // (overridden variables are excluded).
    // Used at compile-time only, except for debugging
    private HashMap<StructuredQName, Declaration> globalVariableIndex =
            new HashMap<StructuredQName, Declaration>(20);

    // table of named templates. Key is the integer fingerprint of the template name;
    // value is the XSLTemplate object in the source stylesheet.
    private HashMap<StructuredQName, Declaration> templateIndex =
            new HashMap<StructuredQName, Declaration>(20);

    // Table of named stylesheet functions. A two level lookup, using first the arity and then
    // the expanded name of the function.
    private IntHashMap<HashMap<StructuredQName, Declaration>> functionIndex =
            new IntHashMap<HashMap<StructuredQName, Declaration>>(8);

    // map for allocating unique numbers to local parameter names. Key is a
    // StructuredQName; value is a boxed int.
    /*@Nullable*/ private HashMap<StructuredQName, Integer> localParameterNumbers = null;


    // namespace aliases. This information is needed at compile-time only
    private int numberOfAliases = 0;
    private List<Declaration> namespaceAliasList = new ArrayList<Declaration>(5);
    private HashMap<String, NamespaceBinding> namespaceAliasMap;
    private Set<String> aliasResultUriSet;
    //private short[] aliasSCodes;
    //private int[] aliasNCodes;

    // flag: true if there's an xsl:result-document that uses a dynamic format
    private boolean needsDynamicOutputProperties = false;

    // count of the maximum number of local variables in xsl:template match patterns
    private int largestPatternStackFrame = 0;

    // cache of stylesheet documents. Note that multiple imports of the same URI
    // lead to the stylesheet tree being reused
    private HashMap<DocumentURI, XSLStylesheet> moduleCache = new HashMap<DocumentURI, XSLStylesheet>(4);

    public PrincipalStylesheetModule(XSLStylesheet sourceElement, int precedence) {
        super(sourceElement, precedence);
    }

    public void setPreparedStylesheet(PreparedStylesheet preparedStylesheet) {
        this.preparedStylesheet = preparedStylesheet;
    }

    public PreparedStylesheet getPreparedStylesheet() {
        return preparedStylesheet;
    }

    /*@NotNull*/
    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return this;
    }

    /**
     * Create the function library
     * @return the resulting function library
     */

    public FunctionLibraryList createFunctionLibrary(CompilerInfo info) {
        Configuration config = getPreparedStylesheet().getConfiguration();
        FunctionLibraryList functionLibrary = new FunctionLibraryList();
        int functionSet = StandardFunction.CORE | StandardFunction.XSLT;
        if ("3.0".equals(getVersion())) {
            functionSet |= (StandardFunction.XSLT30 | StandardFunction.XPATH30);
        }
        functionLibrary.addFunctionLibrary(
                SystemFunctionLibrary.getSystemFunctionLibrary(functionSet));
        functionLibrary.addFunctionLibrary(
                new StylesheetFunctionLibrary(this, true));
        functionLibrary.addFunctionLibrary(
                config.getVendorFunctionLibrary());
        functionLibrary.addFunctionLibrary(
                new ConstructorFunctionLibrary(config));
        if (info.getExtensionFunctionLibrary() != null) {
            functionLibrary.addFunctionLibrary(info.getExtensionFunctionLibrary());
        }
        queryFunctions = new XQueryFunctionLibrary(config);
        functionLibrary.addFunctionLibrary(queryFunctions);
        functionLibrary.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(functionLibrary);
        functionLibrary.addFunctionLibrary(
                new StylesheetFunctionLibrary(this, false));
        if (getPreparedStylesheet().getAccumulatorManager() != null) {
            // TODO: think about where in the library list this function library should be placed
            functionLibrary.addFunctionLibrary(getPreparedStylesheet().getAccumulatorManager().getAccumulatorFunctionLibrary());
        }
        return (this.functionLibrary = functionLibrary);
    }

    /**
     * Get the function library. Available only on the principal stylesheet module
     * @return the function library
     */

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }


    /**
     * Declare an imported XQuery function
     * @param function the imported function
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public void declareXQueryFunction(XQueryFunction function) throws XPathException {
        queryFunctions.declareFunction(function);
    }

    /**
     * Add a module to the cache
     * @param key the key to be used (based on the absolute URI)
     * @param module the stylesheet document tree corresponding to this absolute URI
     */

    public void putStylesheetDocument(DocumentURI key, XSLStylesheet module) {
        moduleCache.put(key, module);
    }

    /**
     * Get a module from the cache
     * @param key the key to be used (based on the absolute URI)
     * @return the stylesheet document tree corresponding to this absolute URI

     */

    public XSLStylesheet getStylesheetDocument(DocumentURI key) {
        XSLStylesheet sheet = moduleCache.get(key);
        if (sheet != null) {
            TransformerException warning = new TransformerException(
                    "Stylesheet module " + key + " is included or imported more than once. " +
                            "This is permitted, but may lead to errors or unexpected behavior");
            getPreparedStylesheet().reportWarning(warning);
        }
        return sheet;
    }

    /**
     * Preprocess does all the processing possible before the source document is available.
     * It is done once per stylesheet, so the stylesheet can be reused for multiple source
     * documents. The method is called only on the XSLStylesheet element representing the
     * principal stylesheet module
     * @throws net.sf.saxon.trans.XPathException if errors are found in the stylesheet
     */

    public void preprocess() throws XPathException {

        // process any xsl:include and xsl:import elements
        net.sf.saxon.trans.Timer timer;
        if (TIMING) {
            timer = new net.sf.saxon.trans.Timer();
        }

        spliceIncludes();

        if (TIMING) {
            timer.report("spliceIncludes");
        }

        // build indexes for selected top-level elements

        buildIndexes();

        if (TIMING) {
            timer.report("buildIndexes");
        }

        // check for use of schema-aware constructs

        checkForSchemaAwareness();

        if (TIMING) {
            timer.report("checkForSchemaAwareness");
        }

        // process the attributes of every node in the tree

        processAllAttributes();

        if (TIMING) {
            timer.report("processAllAttributes");
        }
        // collect any namespace aliases

        collectNamespaceAliases();

        if (TIMING) {
            timer.report("collectNamespaceAliases");
        }

        // fix up references from XPath expressions to variables and functions, for static typing

        for (Declaration decl : topLevel) {
            StyleElement inst = decl.getSourceElement();
            if (!inst.isActionCompleted(StyleElement.ACTION_FIXUP)) {
                inst.setActionCompleted(StyleElement.ACTION_FIXUP);
//                if (inst instanceof XSLVariableDeclaration) {
//                    System.err.println("Fixup global variable " + ((XSLVariableDeclaration)inst).getVariableQName());
//                }
                inst.fixupReferences();
            }
        }

        if (TIMING) {
            timer.report("fixupReferences");
        }
        // Validate the whole logical style sheet (i.e. with included and imported sheets)

        XSLStylesheet top = getSourceElement();
        setInputTypeAnnotations(top.getInputTypeAnnotationsAttribute());
        Declaration decl = new Declaration(this, top);
        if (!top.isActionCompleted(StyleElement.ACTION_VALIDATE)) {
            top.setActionCompleted(StyleElement.ACTION_VALIDATE);
            top.validate(decl);
            for (Declaration d : topLevel) {
                d.getSourceElement().validateSubtree(d);
            }
        }

        if (TIMING) {
            timer.report("validate");
            timer.reportCumulative("total preprocess");
        }
    }

    /**
     * Build indexes for selected top-level declarations
     * @throws net.sf.saxon.trans.XPathException if errors are detected
     */

    private void buildIndexes() throws XPathException {
        // Scan the declarations in reverse order, that is, highest precedence first
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            Declaration decl = topLevel.get(i);
            decl.getSourceElement().index(decl, this);
        }
        // Now seal all the schemas that have been imported to guarantee consistency with instance documents
        Configuration config = getPreparedStylesheet().getConfiguration();
        for (String ns : schemaIndex) {
            config.sealNamespace(ns);
        }
    }

    /**
     * Process the attributes of every node in the stylesheet
     * @throws net.sf.saxon.trans.XPathException if static errors are found in the stylesheet
     */

    public void processAllAttributes() throws XPathException {
        getSourceElement().processDefaultCollationAttribute();
        getSourceElement().prepareAttributes();
        for (Declaration decl : topLevel) {
            StyleElement inst = decl.getSourceElement();
            if (!inst.isActionCompleted(StyleElement.ACTION_PROCESS_ATTRIBUTES)) {
                inst.setActionCompleted(StyleElement.ACTION_PROCESS_ATTRIBUTES);
                try {
                    inst.processAllAttributes();
                } catch (XPathException err) {
                    decl.getSourceElement().compileError(err);
                }
            }
        }
    }

    /**
     * Add a stylesheet function to the index
     * @param decl The declaration wrapping an XSLFunction object
     * @throws XPathException if errors are found
     */
    protected void indexFunction(Declaration decl) throws XPathException {
        XSLFunction function = (XSLFunction)decl.getSourceElement();
        StructuredQName qName = function.getObjectName();
        int arity = function.getNumberOfArguments();

        // see if there is already a named function with this precedence
        Declaration other = getFunctionDeclaration(qName, arity);
        if (other == null) {
            // this is the first
            putFunction(decl);
        } else {
            // check the precedences
            int thisPrecedence = decl.getPrecedence();
            int otherPrecedence = other.getPrecedence();
            if (thisPrecedence == otherPrecedence) {
                StyleElement f2 = other.getSourceElement();
                if (decl.getSourceElement() == f2) {
                     function.compileError(
                             "Function " + qName.getDisplayName() + " is declared more than once " +
                             "(caused by including the containing module more than once)",
                             "XTSE0770");
                } else {
                    function.compileError("Duplicate function declaration (see line " +
                            f2.getLineNumber() + " of " + f2.getSystemId() + ')', "XTSE0770");
                }
            } else if (thisPrecedence < otherPrecedence) {
                //
            } else {
                // can't happen, but we'll play safe
                putFunction(decl);
            }
        }
    }

    protected Declaration getFunctionDeclaration(StructuredQName name, int arity) {
        HashMap<StructuredQName, Declaration> m = functionIndex.get(arity);
        return (m == null ? null : m.get(name));
    }

    /**
     * Get the function with a given name and arity
     * @param name the name of the function
     * @param arity the arity of the function, or -1 if any arity will do
     * @return the requested function, or null if none can be found
     */

    protected XSLFunction getFunction(StructuredQName name, int arity) {
        if (arity == -1) {
            // supports the single-argument function-available() function
            for (IntIterator arities = functionIndex.keyIterator(); arities.hasNext();) {
                int a = arities.next();
                Declaration decl = getFunctionDeclaration(name, a);
                if (decl != null) {
                    return (XSLFunction)decl.getSourceElement();
                }
            }
            return null;
        } else {
            Declaration decl = getFunctionDeclaration(name, arity);
            return (decl == null ? null : (XSLFunction)decl.getSourceElement());
        }
    }

    protected void putFunction(Declaration decl) {
        XSLFunction function = (XSLFunction)decl.getSourceElement();
        StructuredQName qName = function.getObjectName();
        int arity = function.getNumberOfArguments();
        HashMap<StructuredQName, Declaration> m = functionIndex.get(arity);
        if (m == null) {
            m = new HashMap<StructuredQName, Declaration>();
            functionIndex.put(arity, m);
        }
        m.put(qName, decl);
    }


    /**
     * Index a global xsl:variable or xsl:param element
     * @param decl The Declaration referencing the XSLVariable or XSLParam element
     * @throws XPathException if an error occurs
     */

    protected void indexVariableDeclaration(Declaration decl) throws XPathException {
        XSLGlobalVariable var = (XSLGlobalVariable)decl.getSourceElement();
        StructuredQName qName = var.getSourceBinding().getVariableQName();
        if (qName != null) {
            // see if there is already a global variable with this precedence
            Declaration other = globalVariableIndex.get(qName);
            if (other == null) {
                // this is the first
                globalVariableIndex.put(qName, decl);
            } else {
                // check the precedences
                int thisPrecedence = decl.getPrecedence();
                int otherPrecedence = other.getPrecedence();
                if (thisPrecedence == otherPrecedence) {
                    StyleElement v2 = other.getSourceElement();
                    if (v2 == var) {
                        var.compileError(
                                 "Global variable " + qName.getDisplayName() + " is declared more than once " +
                                 "(caused by including the containing module more than once)",
                                 "XTSE0630");
                    } else {
                        var.compileError("Duplicate global variable declaration (see line " +
                                v2.getLineNumber() + " of " + v2.getSystemId() + ')', "XTSE0630");
                    }
                } else if (thisPrecedence < otherPrecedence && var != other.getSourceElement()) {
                    var.setRedundant(true);
                } else if (var != other.getSourceElement()) {
                    ((XSLGlobalVariable)other.getSourceElement()).setRedundant(true);
                    globalVariableIndex.put(qName, decl);
                }
            }
        }
    }

    /**
     * Get the global variable or parameter with a given name (taking
     * precedence rules into account)
     * @param qName name of the global variable or parameter
     * @return the variable declaration, or null if it does not exist
     */

    public SourceBinding getGlobalVariable(StructuredQName qName) {
        Declaration decl = globalVariableIndex.get(qName);
        return (decl == null ? null : ((XSLGlobalVariable)decl.getSourceElement()).getSourceBinding());
    }

    /**
     * Allocate a unique number to a local parameter name. This should only be called on the principal
     * stylesheet module.
     * @param qName the local parameter name
     * @return an integer that uniquely identifies this parameter name within the stylesheet
     */

    public int allocateUniqueParameterNumber(StructuredQName qName) {
        HashMap<StructuredQName, Integer> params = localParameterNumbers;
        if (params == null) {
            localParameterNumbers = new HashMap<StructuredQName, Integer>(50);
            params = localParameterNumbers;
        }
        Integer x = params.get(qName);
        if (x == null) {
            x = params.size();
            params.put(qName, x);
        }
        return x;
    }

    /**
     * Add a named template to the index
     * @param decl the declaration of the Template object
     * @throws XPathException if an error occurs
     */
    protected void indexNamedTemplate(Declaration decl) throws XPathException {
        XSLTemplate template = (XSLTemplate)decl.getSourceElement();
        StructuredQName qName = template.getTemplateName();
        if (qName != null) {
            // see if there is already a named template with this precedence
            Declaration other = templateIndex.get(qName);
            if (other == null) {
                // this is the first
                templateIndex.put(qName, decl);
                getPreparedStylesheet().putNamedTemplate(qName, template.getCompiledTemplate());
            } else {
                // check the precedences
                int thisPrecedence = decl.getPrecedence();
                int otherPrecedence = other.getPrecedence();
                if (thisPrecedence == otherPrecedence) {
                    StyleElement t2 = other.getSourceElement();
                    template.compileError("Duplicate named template (see line " +
                            t2.getLineNumber() + " of " + t2.getSystemId() + ')', "XTSE0660");
                } else if (thisPrecedence < otherPrecedence) {
                    //template.setRedundantNamedTemplate();
                } else {
                    // can't happen, but we'll play safe
                    //other.setRedundantNamedTemplate();
                    templateIndex.put(qName, decl);
                    getPreparedStylesheet().putNamedTemplate(qName, template.getCompiledTemplate());
                }
            }
        }
    }

    /**
     * Get the named template with a given name
     * @param name the name of the required template
     * @return the template with the given name, if there is one, or null otherwise. If there
     * are several templates with the same name, the one with highest import precedence
     * is returned.
     */

    public XSLTemplate getNamedTemplate(StructuredQName name) {
        Declaration decl = templateIndex.get(name);
        return (decl == null ? null : (XSLTemplate)decl.getSourceElement());
    }


    /**
     * Check for schema-awareness.
     * Typed input nodes are recognized if and only if the stylesheet contains an import-schema declaration.
     */

    private void checkForSchemaAwareness() {
        Executable exec = getPreparedStylesheet();
        if (!exec.isSchemaAware() && exec.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
            for (Declaration decl : topLevel) {
                StyleElement node = decl.getSourceElement();
                if (node instanceof XSLImportSchema) {
                    exec.setSchemaAware(true);
                    return;
                }
            }
        }
    }


    protected void addNamespaceAlias(Declaration node) {
        namespaceAliasList.add(node);
        numberOfAliases++;
    }

    /**
     * Get the declared namespace alias for a given namespace URI code if there is one.
     * If there is more than one, we get the last.
     * @param uri The uri used in the stylesheet.
     * @return The namespace binding to be used (prefix and uri): return null
     * if no alias is defined
     */

    protected NamespaceBinding getNamespaceAlias(String uri) {
        return namespaceAliasMap.get(uri);
    }

    /**
     * Determine if a namespace is included in the result-prefix of a namespace-alias
     * @param uri the namespace URI
     * @return true if an xsl:namespace-alias has been defined for this namespace URI
     */

    protected boolean isAliasResultNamespace(String uri) {
        return aliasResultUriSet.contains(uri);
    }

    /**
     * Collect any namespace aliases
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    private void collectNamespaceAliases() throws XPathException {
        namespaceAliasMap = new HashMap<String, NamespaceBinding>(numberOfAliases);
        aliasResultUriSet = new HashSet<String>(numberOfAliases);
        HashSet<String> aliasesAtThisPrecedence = new HashSet<String>();
        int currentPrecedence = -1;
        // Note that we are processing the list in reverse stylesheet order,
        // that is, highest precedence first.
        for (int i = 0; i < numberOfAliases; i++) {
            Declaration decl = namespaceAliasList.get(i);
            XSLNamespaceAlias xna = (XSLNamespaceAlias)decl.getSourceElement();
            String scode = xna.getStylesheetURI();
            NamespaceBinding resultBinding = xna.getResultNamespaceBinding();
            int prec = decl.getPrecedence();

            // check that there isn't a conflict with another xsl:namespace-alias
            // at the same precedence

            if (currentPrecedence != prec) {
                currentPrecedence = prec;
                aliasesAtThisPrecedence.clear();
                //precedenceBoundary = i;
            }
            if (aliasesAtThisPrecedence.contains(scode)) {
                if (!namespaceAliasMap.get(scode).getURI().equals(resultBinding.getURI())) {
                    xna.compileError("More than one alias is defined for the same namespace", "XTSE0810");
                }
            }
            if (namespaceAliasMap.get(scode) == null) {
                namespaceAliasMap.put(scode, resultBinding);
                aliasResultUriSet.add(resultBinding.getURI());
            }
            aliasesAtThisPrecedence.add(scode);
        }
        namespaceAliasList = null;  // throw it in the garbage
    }

    protected boolean hasNamespaceAliases() {
        return numberOfAliases > 0;
    }

    /**
     * Get the collation map
     * @return the CollationMap
     */

    public CollationMap getCollationMap() {
        return getPreparedStylesheet().getCollationTable();
    }

    /**
     * Register a named collation (actually a StringCollator)
     * @param name the name of the collation
     * @param collation the StringCollator that implements this collation
     */

    public void setCollation(String name, StringCollator collation) {
        Executable exec = getPreparedStylesheet();
        if (exec.getCollationTable() == null) {
            exec.setCollationMap(new CollationMap(exec.getConfiguration()));
        }
        exec.getCollationTable().setNamedCollation(name, collation);
    }

    /**
     * Find a named collation. Note this method should only be used at compile-time, before declarations
     * have been pre-processed. After that time, use getCollation().
     * @param name identifies the name of the collation required
     * @param baseURI the base URI to be used for resolving the collation name if it is relative
     * @return null if the collation is not found
     */

    public StringCollator findCollation(String name, String baseURI) {

        Executable exec = getPreparedStylesheet();
        Configuration config = exec.getConfiguration();

        if (name.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
            return CodepointCollator.getInstance();
        }

        // First try to find it in the table

        StringCollator c = null;

        if (exec.getCollationTable() != null) {
            c = exec.getCollationTable().getNamedCollation(name);
        }
        if (c != null) return c;

        // At compile-time, the collation might not yet be in the table. So search for it
        // Search for a matching collation name, starting at the end in case of duplicates.
        // this also ensures we get the one with highest import precedence.
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            Declaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof CollationDeclaration) {
                CollationDeclaration t = (CollationDeclaration)decl.getSourceElement();
                if (t.getCollationName().equals(name)) {
                    return t.getCollator();
                }
            }
        }

        return config.getCollationURIResolver().resolve(name, baseURI, config);
    }

    /**
     * Create an output properties object representing the xsl:output elements in the stylesheet.
     * @param formatQName The name of the output format required. If set to null, gathers
     * information for the unnamed output format
     * @return the Properties object containing the details of the specified output format
     * @throws XPathException if a named output format does not exist in
     * the stylesheet
     */

    public Properties gatherOutputProperties(/*@Nullable*/ StructuredQName formatQName) throws XPathException {
        boolean found = (formatQName == null);
        Configuration config = getPreparedStylesheet().getConfiguration();
        Properties details = new Properties(config.getDefaultSerializationProperties());
        HashMap precedences = new HashMap(10);
        for (int i = topLevel.size()-1; i >= 0; i--) {
            Declaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof XSLOutput) {
                XSLOutput xo = (XSLOutput)decl.getSourceElement();
                if (formatQName == null
                        ? xo.getFormatQName() == null
                        : formatQName.equals(xo.getFormatQName())) {
                    found = true;
                    xo.gatherOutputProperties(details, precedences, decl.getPrecedence());
                }
            }
        }
        if (!found) {
            compileError("Requested output format " + formatQName.getDisplayName() +
                    " has not been defined", "XTDE1460");
        }
        return details;
    }



    /**
     * Compile the stylesheet to create an executable.
     * @throws net.sf.saxon.trans.XPathException if compilation fails for any reason
     */

    public void compileStylesheet() throws XPathException {

        try {

            net.sf.saxon.trans.Timer timer;
            if (TIMING) {
                timer = new net.sf.saxon.trans.Timer();
            }

            PreparedStylesheet pss = getPreparedStylesheet();
            Configuration config = pss.getConfiguration();

            // If any XQuery functions were imported, fix up all function calls
            // registered against these functions.
            try {
                Iterator qf = queryFunctions.getFunctionDefinitions();
                while (qf.hasNext()) {
                    XQueryFunction f = (XQueryFunction) qf.next();
                    f.fixupReferences();
                }
            } catch (XPathException e) {
                compileError(e);
            }

            if (TIMING) {
                timer.report("fixup Query functions");
            }

            // Register template rules with the rule manager

            for (Declaration decl : topLevel) {
                StyleElement snode = decl.getSourceElement();
                if (snode instanceof XSLTemplate) {
                    ((XSLTemplate) snode).register(decl);
                }
            }

            if (TIMING) {
                timer.report("register templates");
            }


            // Call compile method for each top-level object in the stylesheet
            // Note, some declarations (templates) need to be compiled repeatedly if the module
            // is imported repeatedly; others (variables, functions) do not

            for (Declaration decl : topLevel) {
                StyleElement snode = decl.getSourceElement();
                if (!snode.isActionCompleted(StyleElement.ACTION_COMPILE)) {
                    snode.setActionCompleted(StyleElement.ACTION_COMPILE);
                    snode.compileDeclaration(pss, decl);
                }
            }

            if (TIMING) {
                timer.report("compile top-level objects");
            }

            // Call type-check method for each user-defined function in the stylesheet. This is no longer
            // done during the optimize step, to avoid functions being inlined before they are type-checked.

//            for (int i = 0; i < topLevel.size(); i++) {
//                NodeInfo node = (NodeInfo) topLevel.get(i);
//                if (node instanceof XSLFunction) {
//                    ((XSLFunction) node).typeCheckBody();
//                }
//            }

            for (IntIterator arities = functionIndex.keyIterator(); arities.hasNext();) {
                for (Declaration decl : functionIndex.get(arities.next()).values()) {
                    StyleElement node = decl.getSourceElement();
                    if (!node.isActionCompleted(StyleElement.ACTION_TYPECHECK)) {
                        node.setActionCompleted(StyleElement.ACTION_TYPECHECK);
                        ((XSLFunction) node).typeCheckBody();
                    }
                }
            }

            if (TIMING) {
                timer.report("typeCheck functions");
            }

            if (getPreparedStylesheet().getErrorCount() > 0) {
                // not much point carrying on
                return;
            }

            // Call optimize method for each top-level object in the stylesheet
            // But for functions, do it only for those of highest precedence.

            for (Declaration decl : topLevel) {
                StyleElement node = decl.getSourceElement();
                if (node instanceof StylesheetProcedure && !(node instanceof XSLFunction) &&
                        !node.isActionCompleted(StyleElement.ACTION_OPTIMIZE)) {
                    node.setActionCompleted(StyleElement.ACTION_OPTIMIZE);
                    ((StylesheetProcedure) node).optimize(decl);
                }
            }

            // optimize functions that aren't overridden

            for (IntIterator arities = functionIndex.keyIterator(); arities.hasNext();) {
                for (Declaration decl : functionIndex.get(arities.next()).values()) {
                    StyleElement node = decl.getSourceElement();
                    if (!node.isActionCompleted(StyleElement.ACTION_OPTIMIZE)) {
                        node.setActionCompleted(StyleElement.ACTION_OPTIMIZE);
                        ((StylesheetProcedure) node).optimize(decl);
                    }
                }
            }

            if (TIMING) {
                timer.report("optimize");
            }

            if (config.isTiming() && config.isGenerateByteCode(Configuration.XSLT)) {
                config.getStandardErrorOutput().println("Generating byte code...");
            }

            pss.setStripsWhitespace(stripsWhitespace());

            Properties props = gatherOutputProperties(null);
            props.setProperty(SaxonOutputKeys.STYLESHEET_VERSION, getVersion());
            pss.setDefaultOutputProperties(props);

            // handle named output formats for use at run-time
            HashSet<StructuredQName> outputNames = new HashSet<StructuredQName>(5);
            for (Declaration decl : topLevel) {
                if (decl.getSourceElement() instanceof XSLOutput) {
                    XSLOutput out = (XSLOutput) decl.getSourceElement();
                    StructuredQName qName = out.getFormatQName();
                    if (qName != null) {
                        outputNames.add(qName);
                    }
                }
            }
            if (outputNames.isEmpty()) {
                if (needsDynamicOutputProperties) {
                    compileError("The stylesheet contains xsl:result-document instructions that calculate the output " +
                            "format name at run-time, but there are no named xsl:output declarations", "XTDE1460");
                }
            } else {
                for (StructuredQName qName : outputNames) {
                    Properties oprops = gatherOutputProperties(qName);
                    if (needsDynamicOutputProperties) {
                        pss.setOutputProperties(qName, oprops);
                    }
                }
            }

            pss.setPatternSlotSpace(largestPatternStackFrame);
            pss.setStripsInputTypeAnnotations(getInputTypeAnnotations() == XSLStylesheet.ANNOTATION_STRIP);

            // Build the index of named character maps

            for (Declaration decl : topLevel) {
                if (decl.getSourceElement() instanceof XSLCharacterMap) {
                    XSLCharacterMap t = (XSLCharacterMap) decl.getSourceElement();
                    if (!t.isRedundant()) {
                        StructuredQName qn = t.getCharacterMapName();
                        IntHashMap<String> charMap = new IntHashMap<String>();
                        t.assemble(charMap);
                        CharacterMap map = new CharacterMap(charMap);
                        if (pss.getCharacterMapIndex() == null) {
                            pss.setCharacterMapIndex(new CharacterMapIndex());
                        }
                        pss.getCharacterMapIndex().putCharacterMap(qn, map);
                    }
                }
            }

            if (TIMING) {
                timer.report("miscellanea");
            }

            // Check consistency of decimal formats

            pss.getDecimalFormatManager().checkConsistency();

            // Finish off the lists of template rules

            RuleManager ruleManager = getPreparedStylesheet().getRuleManager();
            ruleManager.computeRankings();
            ruleManager.invertStreamableTemplates(config.obtainOptimizer());

            if (TIMING) {
                timer.report("build template rule tables");
            }

            // Build a run-time function library. This supports the use of function-available()
            // with a dynamic argument, and extensions such as saxon:evaluate(). The run-time
            // function library differs from the compile-time function library in that both
            // the StylesheetFunctionLibrary's on the library list are replaced by equivalent
            // ExecutableFunctionLibrary's. This is to prevent the retaining of run-time links
            // to the stylesheet document tree.

            ExecutableFunctionLibrary overriding = new ExecutableFunctionLibrary(config);
            ExecutableFunctionLibrary underriding = new ExecutableFunctionLibrary(config);

            for (Declaration decl : topLevel) {
                if (decl.getSourceElement() instanceof XSLFunction) {
                    XSLFunction func = (XSLFunction) decl.getSourceElement();
                    if (func.isOverriding()) {
                        overriding.addFunction(func.getCompiledFunction());
                    } else {
                        underriding.addFunction(func.getCompiledFunction());
                    }
                }
            }

            FunctionLibraryList libraryList = new FunctionLibraryList();
            for (FunctionLibrary lib : functionLibrary.getLibraryList()) {
                if (lib instanceof StylesheetFunctionLibrary) {
                    if (((StylesheetFunctionLibrary) lib).isOverriding()) {
                        libraryList.addFunctionLibrary(overriding);
                    } else {
                        libraryList.addFunctionLibrary(underriding);
                    }
                } else {
                    libraryList.addFunctionLibrary(lib);
                }
            }
            pss.setFunctionLibrary(libraryList);

            if (TIMING) {
                timer.report("build runtime function tables");
                timer.reportCumulative("total compile phase");
            }

        } catch (RuntimeException err) {
        // if syntax errors were reported earlier, then exceptions may occur during this phase
        // due to inconsistency of data structures. We can ignore these exceptions as they
        // will go away when the user corrects the stylesheet
            if (getPreparedStylesheet().getErrorCount() == 0) {
                // rethrow the exception
                throw err;
            }
        }

    }

    /**
     * Get an imported schema with a given namespace
     * @param targetNamespace The target namespace of the required schema.
     * Supply an empty string for the default namespace
     * @return the required Schema, or null if no such schema has been imported
     */

    protected boolean isImportedSchema(String targetNamespace) {
        return schemaIndex.contains(targetNamespace);
    }

    protected void addImportedSchema(String targetNamespace) {
        schemaIndex.add(targetNamespace);
    }

    protected HashSet<String> getImportedSchemaTable() {
        return schemaIndex;
    }

    /**
     * Get the list of attribute-set declarations associated with a given QName.
     * This is used for xsl:element, xsl:copy, xsl:attribute-set, and on literal
     * result elements
     *
     * @param name  the name of the required attribute set
     * @param list a list to hold the list of XSLAttributeSet elements in the stylesheet tree.
     * @return true if any declarations were found and added to the list; false if none were found
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    protected boolean getAttributeSets(StructuredQName name, List<Declaration> list)
            throws XPathException {

        boolean found = false;

        // search for the named attribute set, using all of them if there are several with the
        // same name

        for (Declaration decl : topLevel) {
            if (decl.getSourceElement() instanceof XSLAttributeSet) {
                XSLAttributeSet t = (XSLAttributeSet) decl.getSourceElement();
                if (t.getAttributeSetName().equals(name)) {
                    t.incrementReferenceCount();
                    list.add(decl);
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Determine whether this stylesheet does any whitespace stripping
     * @return true if this stylesheet strips whitespace from source documents
     */

    public boolean stripsWhitespace() {
        for (Declaration aTopLevel : topLevel) {
            NodeInfo s = aTopLevel.getSourceElement();
            if (s.getFingerprint() == StandardNames.XSL_STRIP_SPACE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the value of the version attribute on the xsl:stylesheet element of the
     * principal stylesheet module
     * @param version the value of the version attribute
     */

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get the value of the version attribute on the xsl:stylesheet element of the
     * principal stylesheet module
     * @return the value of the version attribute
     */

    public String getVersion() {
        return version;
    }

    /**
     * Say that this stylesheet needs dynamic output properties
     * @param b true if this stylesheet needs dynamic output properties
     */

    public void setNeedsDynamicOutputProperties(boolean b) {
        needsDynamicOutputProperties = b;
    }

    /**
     * Get a character map, identified by the fingerprint of its name.
     * Search backwards through the stylesheet.
     * @param name The character map name being sought
     * @return the identified character map, or null if not found
     */

    public Declaration getCharacterMap(StructuredQName name) {
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            Declaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof XSLCharacterMap) {
                XSLCharacterMap t = (XSLCharacterMap) decl.getSourceElement();
                if (t.getCharacterMapName().equals(name)) {
                    return decl;
                }
            }
        }
        return null;
    }


    /**
     * Ensure there is enough space for local variables or parameters when evaluating the match pattern of
     * template rules
     * @param n the number of slots to be allocated
     */

    public void allocatePatternSlots(int n) {
        if (n > largestPatternStackFrame) {
            largestPatternStackFrame = n;
        }
    }



    /**
     * Compile time error, specifying an error code
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     * @throws XPathException unconditionally
     */

    protected void compileError(String message, String errorCode) throws XPathException {
        XPathException tce = new XPathException(message);
        tce.setErrorCode(errorCode);
        compileError(tce);
    }

    /**
     * Report an error with diagnostic information
     * @param error contains information about the error
     * @throws XPathException unconditionally, after reporting the error to the ErrorListener
     */

    protected void compileError(XPathException error)
            throws XPathException {
        error.setIsStaticError(true);
        PreparedStylesheet pss = getPreparedStylesheet();
        try {
            if (pss == null) {
                // it is null before the stylesheet has been fully built
                throw error;
            } else {
                pss.reportError(error);
            }
        } catch (TransformerException err2) {
            throw XPathException.makeXPathException(err2);
        }
    }

}

