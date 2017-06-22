////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceCodeInjector;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This class represents a query module, and includes information about the static context of the query module.
 * The class is intended for internal Saxon use. User settings that affect the static context are made in the
 * StaticQueryContext object, and those settings are copied to each QueryModule when the query module is compiled.
 */

public class QueryModule implements StaticContext {
    private boolean isMainModule;
    private Configuration config;
    /*@Nullable*/ private StaticQueryContext userQueryContext;
    private QueryModule topModule;
    /*@Nullable*/ private URI locationURI;
    private String baseURI;
    /*@Nullable*/ private String moduleNamespace; // null only if isMainModule is false
    private HashMap<String, String> explicitPrologNamespaces;
    private Stack<ActiveNamespace> activeNamespaces;
    private HashMap<StructuredQName, GlobalVariable> variables;
    // global variables declared in this module
    private HashMap<StructuredQName, GlobalVariable> libraryVariables;
    // all global variables defined in library modules
    // defined only on the top-level module
    private HashMap<StructuredQName, UndeclaredVariable> undeclaredVariables;
    /*@Nullable*/ private HashSet<String> importedSchemata;    // The schema target namespaces imported into this module
    private HashMap<String, HashSet<String>> loadedSchemata;
    // For the top-level module only, all imported schemas for all modules,
    // Key is the targetNamespace, value is the set of absolutized location URIs
    /*@Nullable*/ private Executable executable;
    /*@Nullable*/ private List<QueryModule> importers;  // A list of QueryModule objects representing the modules that import this one,
    // Null for the main module
    // This is needed *only* to implement the rules banning cyclic imports
    private FunctionLibraryList functionLibraryList;
    private XQueryFunctionLibrary globalFunctionLibrary;      // used only on a top-level module
    private int localFunctionLibraryNr;
    private int importedFunctionLibraryNr;
    private int unboundFunctionLibraryNr;
    private Set<String> importedModuleNamespaces;
    private boolean inheritNamespaces = true;
    private boolean preserveNamespaces = true;
    private int constructionMode = Validation.PRESERVE;
    private String defaultFunctionNamespace;
    private String defaultElementNamespace;
    private boolean preserveSpace = false;
    private boolean defaultEmptyLeast = true;
    /*@Nullable*/ private String defaultCollationName;
    private int revalidationMode = Validation.SKIP;
    private boolean isUpdating = false;
    private DecimalValue languageVersion = DecimalValue.ONE;
    private ItemType requiredContextItemType = AnyItemType.getInstance(); // must be the same for all modules
    /*@Nullable*/ private DecimalFormatManager decimalFormatManager = null;   // used only in XQuery 3.0
    private CodeInjector codeInjector;
    private Map<LanguageFeature, SourceLocator> featuresUsed = new HashMap<LanguageFeature, SourceLocator>();
    private Set<LanguageFeature> featuresProhibited = new HashSet<LanguageFeature>();
    private Set<LanguageFeature> featuresRequired = new HashSet<LanguageFeature>();

    /**
     * Create a QueryModule for a main module, copying the data that has been set up in a
     * StaticQueryContext object
     * @param sqc the StaticQueryContext object from which this module is initialized
     * @throws XPathException if information supplied is invalid
     */

    public QueryModule(/*@NotNull*/ StaticQueryContext sqc) throws XPathException {
        config = sqc.getConfiguration();
        isMainModule = true;
        topModule = this;
        activeNamespaces = new Stack<ActiveNamespace>();
        baseURI = sqc.getBaseURI();
        try {
            locationURI = (baseURI == null ? null : new URI(baseURI));
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid location URI: " + baseURI);
        }
        executable = null;
        importers = null;
        init(sqc);
        for (Iterator<GlobalVariable> vars = sqc.iterateDeclaredGlobalVariables(); vars.hasNext();) {
            declareVariable(vars.next());
        }
    }

    /**
     * Create a QueryModule for a library module.
     * @param config   the Saxon configuration
     * @param importer the module that imported this module. This may be null, in the case where
     *                 the library module is being imported into an XSLT stylesheet
     */

    public QueryModule(Configuration config, /*@Nullable*/ QueryModule importer) {
        this.config = config;
        importers = null;
        if (importer == null) {
            topModule = this;
        } else {
            topModule = importer.topModule;
            userQueryContext = importer.userQueryContext;
            importers = new ArrayList<QueryModule>(2);
            importers.add(importer);
        }
        init(userQueryContext);
        activeNamespaces = new Stack<ActiveNamespace>();
        executable = null;
    }

    /**
     * Initialize data from a user-supplied StaticQueryContext object
     * @param sqc the user-supplied StaticQueryContext. Null if this is a library module imported
     *            into XSLT.
     */

    private void init(/*@Nullable*/ StaticQueryContext sqc) {
        //reset();
        userQueryContext = sqc;
        variables = new HashMap<StructuredQName, GlobalVariable>(10);
        undeclaredVariables = new HashMap<StructuredQName, UndeclaredVariable>(5);
        if (isTopLevelModule()) {
            libraryVariables = new HashMap<StructuredQName, GlobalVariable>(10);
        }
        importedSchemata = null;
        importedModuleNamespaces = new HashSet<String>(5);
        moduleNamespace = null;
        activeNamespaces = new Stack<ActiveNamespace>();

        explicitPrologNamespaces = new HashMap<String, String>(10);
        if (sqc != null) {
            //executable = sqc.getExecutable();
            inheritNamespaces = sqc.isInheritNamespaces();
            preserveNamespaces = sqc.isPreserveNamespaces();
            preserveSpace = sqc.isPreserveBoundarySpace();
            defaultEmptyLeast = sqc.isEmptyLeast();
            defaultFunctionNamespace = sqc.getDefaultFunctionNamespace();
            defaultElementNamespace = sqc.getDefaultElementNamespace();
            defaultCollationName = sqc.getDefaultCollationName();
            constructionMode = sqc.getConstructionMode();
            if (constructionMode == Validation.PRESERVE && !sqc.isSchemaAware()) {
                // if not schema-aware, generate untyped output by default
                constructionMode = Validation.STRIP;
            }
            requiredContextItemType = sqc.getRequiredContextItemType();
            isUpdating = sqc.isUpdatingEnabled();
            languageVersion = sqc.getLanguageVersion();
            codeInjector = sqc.getCodeInjector();
            //allowTypedNodes = sqc.isAllowTypedNodes();
        }
        initializeFunctionLibraries(sqc);
    }

    /**
     * Supporting method to load an imported library module.
     * Used also by saxon:import-query in XSLT.
     * <p/>
     * This method is intended for internal use only.
     * @param baseURI      The base URI and location URI of the module
     * @param executable   The Executable
     * @param importer     The importing query module (used to check for cycles). This is null
     *                     when loading a query module from XSLT.
     * @param query        The text of the query, after decoding and normalizing line endings
     * @param namespaceURI namespace of the query module to be loaded
     * @param allowCycles  True if cycles of module imports (disallowed by the spec) are to be permitted
     * @return The StaticQueryContext representing the loaded query module
     * @throws XPathException if an error occurs
     */

    /*@NotNull*/ public static QueryModule makeQueryModule(
            String baseURI, /*@NotNull*/ Executable executable, /*@NotNull*/ QueryModule importer,
            String query, String namespaceURI, boolean allowCycles) throws XPathException {
        Configuration config = executable.getConfiguration();
        QueryModule module = new QueryModule(config, importer);
        try {
            module.setLocationURI(new URI(baseURI));
        } catch (URISyntaxException e) {
            throw new XPathException("Invalid location URI " + baseURI, e);
        }
        module.setBaseURI(baseURI);
        module.setExecutable(executable);
        module.setModuleNamespace(namespaceURI);

        executable.addQueryLibraryModule(module);
        QueryParser qp = (QueryParser)config.newExpressionParser(
                "XQ", importer.isUpdating(), importer.getLanguageVersion());
        if (importer.getCodeInjector() != null) {
            qp.setCodeInjector(importer.getCodeInjector());
        } else if (config.isCompileWithTracing()) {
            qp.setCodeInjector(new TraceCodeInjector());
        }
        qp.setDisableCycleChecks(allowCycles);
        qp.parseLibraryModule(query, module);

        String namespace = module.getModuleNamespace();
        if (namespace == null) {
            XPathException err = new XPathException("Imported module must be a library module");
            err.setErrorCode("XQST0059");
            err.setIsStaticError(true);
            throw err;
        }
        if (!namespace.equals(namespaceURI)) {
            XPathException err = new XPathException("Imported module's namespace does not match requested namespace");
            err.setErrorCode("XQST0059");
            err.setIsStaticError(true);
            throw err;
        }

        for (Map.Entry<LanguageFeature, SourceLocator> entry : module.getFeaturesUsed().entrySet()) {
            if (importer.getFeaturesProhibited().contains(entry.getKey())) {
                XPathException err = new XPathException("Imported module uses feature " +
                        entry.getKey().getName().getLocalPart() +
                        ", which is prohibited in the importing module", entry.getValue());
                err.setErrorCode("XQST0120");
                throw err;
            }
            importer.getFeaturesUsed().put(entry.getKey(), entry.getValue());
        }

        return module;
    }

    /**
     * Reset function libraries
     * @param sqc The static query context set up by the caller
     */

    private void initializeFunctionLibraries(/*@Nullable*/ StaticQueryContext sqc) {
        Configuration config = getConfiguration();
        if (isTopLevelModule()) {
            globalFunctionLibrary = new XQueryFunctionLibrary(config);
        }

        int functionSet = StandardFunction.CORE;
        if (isUpdating()) {
            functionSet |= StandardFunction.XQUPDATE;
        }
        if (getLanguageVersion().equals(DecimalValue.THREE)) {
            functionSet |= StandardFunction.XPATH30;
        }

        functionLibraryList = new FunctionLibraryList();
        functionLibraryList.addFunctionLibrary(
                SystemFunctionLibrary.getSystemFunctionLibrary(functionSet));
        functionLibraryList.addFunctionLibrary(config.getVendorFunctionLibrary());
        functionLibraryList.addFunctionLibrary(new ConstructorFunctionLibrary(config));

        localFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new XQueryFunctionLibrary(config));

        importedFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new ImportedFunctionLibrary(this, getTopLevelModule().getGlobalFunctionLibrary()));

        if (sqc != null && sqc.getExtensionFunctionLibrary() != null) {
            functionLibraryList.addFunctionLibrary(sqc.getExtensionFunctionLibrary());
        }

        functionLibraryList.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(functionLibraryList);

        unboundFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new UnboundFunctionLibrary());
    }

    /**
     * Get the Saxon Configuration
     * @return the Saxon Configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the NamePool used for compiling expressions
     * @return the name pool
     */

    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Test whether this is a "top-level" module. This is true for a main module and also for a
     * module directly imported into an XSLT stylesheet. It may also be true in future for independently-compiled
     * modules
     * @return true if this is top-level module
     */

    public boolean isTopLevelModule() {
        return this == topModule;
    }

    /**
     * Set whether this is a "Main" module, in the sense of the XQuery language specification
     * @param main true if this is a main module, false if it is a library module
     */

    public void setIsMainModule(boolean main) {
        isMainModule = main;
    }

    /**
     * Ask whether this is a "main" module, in the sense of the XQuery language specification
     * @return true if this is a main module, false if it is a library model
     */

    public boolean isMainModule() {
        return isMainModule;
    }

    /**
     * Check whether this module is allowed to import a module with namespace N. Note that before
     * calling this we have already handled the exception case where a module imports another in the same
     * namespace (this is the only case where cycles are allowed, though as a late change to the spec they
     * are no longer useful, since they cannot depend on each other cyclically)
     * @param namespace the namespace to be tested
     * @return true if the import is permitted
     */

    public boolean mayImportModule(/*@NotNull*/ String namespace) {
        if (namespace.equals(moduleNamespace)) {
            return false;
        }
        if (importers == null) {
            return true;
        }
        for (QueryModule importer : importers) {
            if (!importer.mayImportModule(namespace)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ask whether expressions compiled under this static context are schema-aware.
     * They must be schema-aware if the expression is to handle typed (validated) nodes
     *
     * @return true if expressions are schema-aware
     */
    public boolean isSchemaAware() {
        return executable.isSchemaAware();
    }

    /**
     * Set the namespace inheritance mode
     * @param inherit true if namespaces are inherited, false if not
     * @since 8.4
     */

    public void setInheritNamespaces(boolean inherit) {
        inheritNamespaces = inherit;
    }

    /**
     * Get the namespace inheritance mode
     * @return true if namespaces are inherited, false if not
     * @since 8.4
     */

    public boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

    /**
     * Set the namespace copy mode
     * @param inherit true if namespaces are preserved, false if not
     */

    public void setPreserveNamespaces(boolean inherit) {
        preserveNamespaces = inherit;
    }

    /**
     * Get the namespace copy mode
     * @return true if namespaces are preserved, false if not
     */

    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    /**
     * Set the construction mode for this module
     * @param mode one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     */

    public void setConstructionMode(int mode) {
        constructionMode = mode;
    }

    /**
     * Get the current construction mode
     * @return one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     */

    public int getConstructionMode() {
        return constructionMode;
    }

    /**
     * Set the policy for preserving boundary space
     * @param preserve true if boundary space is to be preserved, false if it is to be stripped
     */

    public void setPreserveBoundarySpace(boolean preserve) {
        preserveSpace = preserve;
    }

    /**
     * Ask whether the policy for boundary space is "preserve" or "strip"
     * @return true if the policy is to preserve boundary space, false if it is to strip it
     */

    public boolean isPreserveBoundarySpace() {
        return preserveSpace;
    }

    /**
     * Set the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     * @param least true if the empty sequence is considered less than any other value (the default),
     *              false if it is considered greater than any other value
     */

    public void setEmptyLeast(boolean least) {
        defaultEmptyLeast = least;
    }

    /**
     * Ask what is the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     * @return true if the empty sequence is considered less than any other value (the default),
     *         false if it is considered greater than any other value
     */

    public boolean isEmptyLeast() {
        return defaultEmptyLeast;
    }


    /**
     * Get the function library object that holds details of global functions
     * @return the library of global functions
     */

    public XQueryFunctionLibrary getGlobalFunctionLibrary() {
        return globalFunctionLibrary;
    }

    /**
     * Get the function library object that holds details of imported functions
     * @return the library of imported functions
     */

    /*@NotNull*/ public ImportedFunctionLibrary getImportedFunctionLibrary() {
        return (ImportedFunctionLibrary)functionLibraryList.get(importedFunctionLibraryNr);
    }

    /**
     * Register that this module imports a particular module namespace
     * <p>This method is intended for internal use.</p>
     * @param uri the URI of the imported namespace.
     */

    public void addImportedNamespace(String uri) {
        if (importedModuleNamespaces == null) {
            importedModuleNamespaces = new HashSet<String>(5);
        }
        importedModuleNamespaces.add(uri);
        getImportedFunctionLibrary().addImportedNamespace(uri);
    }

    /**
     * Ask whether this module directly imports a particular namespace
     * <p>This method is intended for internal use.</p>
     * @param uri the URI of the possibly-imported namespace.
     * @return true if the schema for the namespace has been imported
     */

    public boolean importsNamespace(String uri) {
        return importedModuleNamespaces != null &&
                importedModuleNamespaces.contains(uri);
    }

    /**
     * Get the QueryModule for the top-level module. This will normally be a main module,
     * but in the case of saxon:import-query it will be the library module that is imported into
     * the stylesheet
     * @return the StaticQueryContext object associated with the top level module
     */

    public QueryModule getTopLevelModule() {
        return topModule;
    }

    /**
     * Get the Executable, an object representing the compiled query and its environment.
     * <p/>
     * This method is intended for internal use only.
     * @return the Executable
     */

    /*@Nullable*/ public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the executable.
     * <p/>
     * This method is intended for internal use only.
     * @param executable the Executable
     */

    public void setExecutable(Executable executable) {
        this.executable = executable;
//        if (!executable.isSchemaAware()) {
//            constructionMode = Validation.STRIP;
//        }
    }

    /**
     * Get the StaticQueryContext object containing options set up by the user
     * @return the user-created StaticQueryContext object
     */

    /*@Nullable*/ public StaticQueryContext getUserQueryContext() {
        return userQueryContext;
    }

    /**
     * Get the LocationMap, an data structure used to identify the location of compiled expressions within
     * the query source text.
     * <p/>
     * This method is intended for internal use only.
     * @return the LocationMap
     */

    public LocationMap getLocationMap() {
        return executable.getLocationMap();
    }

    /**
     * Set the namespace for a library module.
     * <p/>
     * This method is for internal use only.
     * @param uri the module namespace URI of the library module. Null is allowed only
     * for a main module, not for a library module.
     */

    public void setModuleNamespace(/*@Nullable*/ String uri) {
        moduleNamespace = uri;
    }

    /**
     * Get the namespace of the current library module.
     * <p/>
     * This method is intended primarily for internal use.
     * @return the module namespace, or null if this is a main module
     */

    /*@Nullable*/ public String getModuleNamespace() {
        return moduleNamespace;
    }

    /**
     * Set the location URI for a module
     * @param uri the location URI
     */

    public void setLocationURI(URI uri) {
        locationURI = uri;
    }

    /**
     * Get the location URI for a module
     * @return the location URI
     */

    /*@Nullable*/ public URI getLocationURI() {
        return locationURI;
    }

    /**
     * Get the System ID for a module
     * @return the location URI
     */

    /*@Nullable*/ public String getSystemId() {
        return (locationURI == null ? null : locationURI.toString());
    }

    /**
     * Set the base URI for a module
     * @param uri the base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
     * Get the base URI for a module
     * @return the base URI
     */

    public String getBaseURI() {
        return baseURI;
    }


    /**
     * Get the stack frame map for global variables.
     * <p/>
     * This method is intended for internal use.
     * @return the stack frame map (a SlotManager) for global variables.
     */

    public SlotManager getGlobalStackFrameMap() {
        return executable.getGlobalVariableMap();
    }

    /**
     * Declare a global variable. A variable must normally be declared before an expression referring
     * to it is compiled, but there are exceptions where a set of modules in the same namespace
     * import each other cyclically. Global variables are normally declared in the Query Prolog, but
     * they can also be predeclared using the Java API. All global variables are held in the QueryModule
     * for the main module. The fact that a global variable is present therefore does not mean that it
     * is visible: there are two additional conditions (a) the module namespace must be imported into the
     * module where the reference appears, and (b) the declaration must not be in the same module and textually
     * after the reference.
     * <p/>
     * <p>Note that the same VariableDeclaration object cannot be used with more than one query.  This is because
     * the VariableDeclaration is modified internally to hold a list of references to all the places where
     * the variable is used.</p>
     * @param var the Variable declaration being declared
     * @throws XPathException if a static error is detected
     */

    public void declareVariable(/*@NotNull*/ GlobalVariable var) throws XPathException {
        StructuredQName key = var.getVariableQName();
        if (variables.get(key) != null) {
            GlobalVariable old = variables.get(key);
            if (old == var || old.getUltimateOriginalVariable() == var.getUltimateOriginalVariable()) {
                // do nothing
            } else {
                String oldloc = " (see line " + old.getLineNumber();
                String oldSysId = old.getSystemId();
                if (oldSysId != null &&
                        !oldSysId.equals(var.getSystemId())) {
                    oldloc += " in module ((GlobalVariableDefinition)old).getSystemId()";
                }
                oldloc += ")";
                XPathException err = new XPathException("Duplicate definition of global variable "
                        + var.getVariableQName().getDisplayName()
                        + oldloc);
                err.setErrorCode("XQST0049");
                err.setIsStaticError(true);
                ExpressionLocation loc = new ExpressionLocation();
                loc.setLineNumber(var.getLineNumber());
                loc.setSystemId(var.getSystemId());
                err.setLocator(loc);
                throw err;
            }
        }
        variables.put(key, var);

        final HashMap<StructuredQName, GlobalVariable> libVars = getTopLevelModule().libraryVariables;
        GlobalVariable old = libVars.get(key);
        if (old == null || old == var) {
            // do nothing
        } else {
            XPathException err = new XPathException("Duplicate definition of global variable "
                    + var.getVariableQName().getDisplayName()
                    + " (see line " + old.getLineNumber() + " in module " + old.getSystemId() + ')');
            err.setErrorCode("XQST0049");
            err.setIsStaticError(true);
            ExpressionLocation loc = new ExpressionLocation();
            loc.setLineNumber(var.getLineNumber());
            loc.setSystemId(var.getSystemId());
            err.setLocator(loc);
        throw err;
        }

        if (!isMainModule()) {
            libVars.put(key, var);
        }
    }

    /**
     * Get all global variables declared in or imported into this module
     * @return an iterator over the global variables
     */

    public Iterator<GlobalVariable> getGlobalVariables() {
        return libraryVariables.values().iterator();
    }

    /**
     * Fixup all references to global variables.
     * <p/>
     * This method is for internal use by the Query Parser only.
     * @param globalVariableMap a SlotManager that holds details of the assignment of slots to global variables.
     * @param contextItemVar the variable holding the context item
     * @return a list containing the global variable definitions.
     * @throws XPathException if compiling a global variable definition fails
     */

    public List<GlobalVariable> fixupGlobalVariables(
                SlotManager globalVariableMap, GlobalVariable contextItemVar) throws XPathException {
        List<GlobalVariable> varDefinitions = new ArrayList<GlobalVariable>(20);
        List<Iterator<GlobalVariable>> iters = new ArrayList<Iterator<GlobalVariable>>();
        iters.add(variables.values().iterator());
        iters.add(libraryVariables.values().iterator());



        for (Iterator<GlobalVariable> iter : iters) {
            while (iter.hasNext()) {
                GlobalVariable var = iter.next();
                if (!varDefinitions.contains(var)) {
                    int slot = globalVariableMap.allocateSlotNumber(var.getVariableQName());
                    var.compile(getExecutable(), slot);
                    varDefinitions.add(var);
                }
                Expression select = var.getSelectExpression();
                if (select != null) {
                    boolean changed = false;
                    // eliminate dependencies on the focus
                    if (select instanceof Position) {
                        select = Literal.makeLiteral(IntegerValue.PLUS_ONE);
                        changed = true;
                    } else if (select instanceof Last) {
                        select = Literal.makeLiteral(IntegerValue.PLUS_ONE);
                        changed = true;
                    } else if (select instanceof ContextItemExpression) {
                        select = new VariableReference(contextItemVar);
                        changed = true;
                    } else if (select instanceof AxisExpression) {
                        select = new SimpleStepExpression(new VariableReference(contextItemVar), select);
                        changed = true;
                    } else if(select instanceof ParentNodeExpression || select instanceof RootExpression) {
                        select = new SlashExpression(new VariableReference(contextItemVar), select);
                        changed = true;
                    } else {
                        int dependencies = select.getDependencies();
                        if ((dependencies & (StaticProperty.DEPENDS_ON_CONTEXT_ITEM | StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT)) != 0) {
                            changed = ExpressionTool.factorOutDot(select, contextItemVar);
                        }
                        if ((dependencies & StaticProperty.DEPENDS_ON_POSITION) != 0) {
                            ExpressionTool.ExpressionSelector selector = new ExpressionTool.ExpressionSelector() {
                                public boolean matches(Expression exp) {
                                    return exp instanceof Position;
                                }
                            };
                            changed = ExpressionTool.replaceSelectedSubexpressions(select, selector, Literal.makeLiteral(IntegerValue.PLUS_ONE));
                        }
                        if ((dependencies & StaticProperty.DEPENDS_ON_LAST) != 0) {
                            ExpressionTool.ExpressionSelector selector = new ExpressionTool.ExpressionSelector() {
                                public boolean matches(Expression exp) {
                                    return exp instanceof Last;
                                }
                            };
                            changed = ExpressionTool.replaceSelectedSubexpressions(select, selector, Literal.makeLiteral(IntegerValue.PLUS_ONE));
                        }
                    }
                    if (changed) {
                        var.setSelectExpression(select);
                    }
                }
            }
        }
        return varDefinitions;
    }

    /**
     * Look for module cycles. This is a restriction introduced in the PR specification because of
     * difficulties in defining the formal semantics.
     * <p/>
     * <p>[Definition: A module M1 directly depends on another module M2 (different from M1) if a
     * variable or function declared in M1 depends on a variable or function declared in M2.]
     * It is a static error [err:XQST0093] to import a module M1 if there exists a sequence
     * of modules M1 ... Mi ... M1 such that each module directly depends on the next module
     * in the sequence (informally, if M1 depends on itself through some chain of module dependencies.)</p>
     * @param referees   a Stack containing the chain of module import references leading to this
     *                   module
     * @param lineNumber used for diagnostics
     * @throws net.sf.saxon.trans.XPathException if cycles are found
     */
    public void lookForModuleCycles(/*@NotNull*/ Stack<QueryModule> referees, int lineNumber) throws XPathException {
        if (referees.contains(this)) {
            int s = referees.indexOf(this);
            referees.push(this);
            StringBuilder message = new StringBuilder("Circular dependency between modules. ");
            for (int i = s; i < referees.size() - 1; i++) {
                QueryModule next = referees.get(i + 1);
                if (i == s) {
					message.append("Module ").append(getSystemId()).append(" references module ")
						.append(next.getSystemId());
                } else {
                    message.append(", which references module ").append(next.getSystemId());
                }
            }
            message.append('.');
            XPathException err = new XPathException(message.toString());
            err.setErrorCode("XQST0093");
            err.setIsStaticError(true);
            ExpressionLocation loc = new ExpressionLocation();
            loc.setSystemId(getSystemId());
            loc.setLineNumber(lineNumber);
            err.setLocator(loc);
            throw err;
        } else {
            referees.push(this);
            Iterator<GlobalVariable> viter = getModuleVariables();
            while (viter.hasNext()) {
                GlobalVariable gv = viter.next();
                //GlobalVariable gvc = gv.getCompiledVariable(); // will be null if the global variable is unreferenced
                Expression select = gv.getSelectExpression();
                if (select != null) {
                    List<Binding> list = new ArrayList<>(10);
                    ExpressionTool.gatherReferencedVariables(select, list);
                    for (Binding b : list) {
                        if (b instanceof GlobalVariable) {
                            String uri = ((GlobalVariable) b).getSystemId();
                            StructuredQName qName = b.getVariableQName();
                            boolean synthetic = NamespaceConstant.SAXON.equals(qName.getURI()) && "gg".equals(qName.getPrefix());
                            if (!synthetic && uri!=null && !uri.equals(getSystemId())) {
                                QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                                if (sqc != null) {
                                    sqc.lookForModuleCycles(referees, ((GlobalVariable) b).getLineNumber());
                                }
                            }
                        }
                    }
                    List<UserFunction> fList = new ArrayList<>(5);
                    ExpressionTool.gatherCalledFunctions(select, fList);
                    for (UserFunction f : fList) {
                        String uri = f.getSystemId();
                        if (uri!=null && !uri.equals(getSystemId())) {
                            QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                            if (sqc != null) {
                                sqc.lookForModuleCycles(referees, f.getLineNumber());
                            }
                        }
                    }
                }
            }
            Iterator<XQueryFunction> fiter = getLocalFunctionLibrary().getFunctionDefinitions();
            while (fiter.hasNext()) {
                XQueryFunction gf = fiter.next();

                Expression body = gf.getUserFunction().getBody();
                if (body != null) {
                    List<Binding> vList = new ArrayList<>(10);
                    ExpressionTool.gatherReferencedVariables(body, vList);
                    for (Binding b : vList) {
                        if (b instanceof GlobalVariable) {
                            String uri = ((GlobalVariable) b).getSystemId();
                            StructuredQName qName = b.getVariableQName();
                            boolean synthetic = NamespaceConstant.SAXON.equals(qName.getURI()) && "gg".equals(qName.getPrefix());
                            if (!synthetic && uri!=null && !uri.equals(getSystemId())) {
                                QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                                if (sqc != null) {
                                    sqc.lookForModuleCycles(referees, ((GlobalVariable) b).getLineNumber());
                                }
                            }
                        }
                    }
                    List<UserFunction> fList = new ArrayList<>(10);
                    ExpressionTool.gatherCalledFunctions(body, fList);
                    for (UserFunction f : fList) {
                        String uri = f.getSystemId();
                        if (uri!=null && !uri.equals(getSystemId())) {
                            QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                            if (sqc != null) {
                                sqc.lookForModuleCycles(referees, f.getLineNumber());
                            }
                        }
                    }
                }
            }
            referees.pop();
        }
    }

    /**
     * Get global variables declared in this module
     * @return an Iterator whose items are GlobalVariable objects
     */

    public Iterator<GlobalVariable> getModuleVariables() {
        return variables.values().iterator();
    }

    /**
     * Check for circular definitions of global variables.
     * <p>This method is intended for internal use</p>
     * @param compiledVars a list of {@link GlobalVariable} objects to be checked
     * @param globalFunctionLibrary the library of global functions
     * @throws net.sf.saxon.trans.XPathException if a circularity is found
     */

    public void checkForCircularities(/*@NotNull*/ List<GlobalVariable> compiledVars, /*@NotNull*/ XQueryFunctionLibrary globalFunctionLibrary) throws XPathException {
        Iterator<GlobalVariable> iter = compiledVars.iterator();
        Stack<Container> stack = null;
        while (iter.hasNext()) {
            if (stack == null) {
                stack = new Stack<Container>();
            }
            GlobalVariable gv = iter.next();
            if (gv != null) {
                gv.lookForCycles(stack, globalFunctionLibrary);
            }
        }
    }


    /**
     * Perform type checking on global variables.
     * <p>This method is intended for internal use</p>
     * @param compiledVars a list of {@link GlobalVariable} objects to be checked
     * @throws net.sf.saxon.trans.XPathException if a type error occurs
     */

    public void typeCheckGlobalVariables(/*@NotNull*/ List<GlobalVariable> compiledVars) throws XPathException {
        ExpressionVisitor visitor = ExpressionVisitor.make(this, getExecutable());
        for (GlobalVariable compiledVar : compiledVars) {
            compiledVar.typeCheck(visitor);
        }
    }

    /**
     * Bind a variable used in a query to the expression in which it is declared.
     * <p/>
     * This method is provided for use by the XQuery parser, and it should not be called by the user of
     * the API, or overridden, unless variables are to be declared using a mechanism other than the
     * declareVariable method of this class.
     * @param qName the name of the variable to be bound
     * @return a VariableReference object representing a reference to a variable on the abstract syntac rtee of
     *         the query.
     */

    /*@NotNull*/ public Expression bindVariable(/*@NotNull*/ StructuredQName qName) throws XPathException {
        GlobalVariable var = variables.get(qName);
        if (var == null) {
            String uri = qName.getURI();
            if ((uri.equals("") && isMainModule()) || uri.equals(moduleNamespace) || importsNamespace(uri)) {
                QueryModule main = getTopLevelModule();
                var = main.libraryVariables.get(qName);
                if (var == null) {
                    // If the namespace has been imported there's the possibility that
                    // the variable declaration hasn't yet been read, because of the limited provision
                    // for cyclic imports. In XQuery 3.0 forwards references are more generally allowed.
                    if (getLanguageVersion().equals(DecimalValue.THREE)) {
                        UndeclaredVariable uvar = undeclaredVariables.get(qName);
                        if (uvar != null) {
                            // second or subsequent reference to the as-yet-undeclared variable
                            VariableReference ref = new VariableReference();
                            uvar.registerReference(ref);
                            return ref;
                        } else {
                            // first reference to the as-yet-undeclared variable
                            uvar = new UndeclaredVariable();
                            uvar.setVariableQName(qName);
                            VariableReference ref = new VariableReference();
                            uvar.registerReference(ref);
                            undeclaredVariables.put(qName, uvar);
                            return ref;
                        }
//                        UndeclaredVariable uvar = new UndeclaredVariable();
//                        uvar.setVariableQName(qName);
//                        VariableReference ref = new VariableReference();
//                        uvar.registerReference(ref);
//                        undeclaredVariables.put(qName, uvar);
//                        return ref;
                    } else {
                        XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " has not been declared");
                        err.setErrorCode("XPST0008");
                        err.setIsStaticError(true);
                        throw err;
                    }
                } else {
                    if (var.isPrivate()) {
                        XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " is private");
                        err.setErrorCode("XPST0008");
                        err.setIsStaticError(true);
                        throw err;
                    }
                    checkImportedType(var.getRequiredType(), var);
                }
            } else {
                // If the namespace hasn't been imported then we might as well throw the error right away
                XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " has not been declared");
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            }
        } else {
            if (var.isPrivate() && !var.getSystemId().equals(getSystemId())) {
                XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " is private");
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            }
        }
        VariableReference vref = new VariableReference();
        var.registerReference(vref);
        return vref;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context (that is, the functions available in this query module).
     * <p/>
     * This method is provided for use by advanced applications.
     * The details of the interface are subject to change.
     * @return the FunctionLibrary used. For XQuery, this will always be a FunctionLibraryList.
     * @see net.sf.saxon.functions.FunctionLibraryList
     */

    public FunctionLibrary getFunctionLibrary() {
        return functionLibraryList;
    }

    /**
     * Get the functions declared locally within this module
     * @return a FunctionLibrary object containing the function declarations
     */

    /*@NotNull*/ public XQueryFunctionLibrary getLocalFunctionLibrary() {
        return (XQueryFunctionLibrary)functionLibraryList.get(localFunctionLibraryNr);
    }

    /**
     * Register a user-defined XQuery function.
     * <p/>
     * This method is intended for internal use only.
     * @param function the function being declared
     * @throws net.sf.saxon.trans.XPathException if an error occurs, for example
     * a duplicate function name
     */

    public void declareFunction(/*@NotNull*/ XQueryFunction function) throws XPathException {
        Configuration config = getConfiguration();
        if (function.getNumberOfArguments() == 1) {
            StructuredQName name = function.getFunctionName();
            int fingerprint = config.getNamePool().getFingerprint(name.getURI(), name.getLocalPart());
            if (fingerprint != -1) {
                SchemaType t = config.getSchemaType(fingerprint);
                if (t != null && t.isAtomicType()) {
                    XPathException err = new XPathException("Function name " + function.getDisplayName() +
                            " clashes with the name of the constructor function for an atomic type");
                    err.setErrorCode("XQST0034");
                    err.setIsStaticError(true);
                    throw err;
                }
            }
        }
        XQueryFunctionLibrary local = getLocalFunctionLibrary();
        local.declareFunction(function);
        //if (!function.isPrivate()) {
        QueryModule main = getTopLevelModule();
        main.globalFunctionLibrary.declareFunction(function);
        //}
    }

    /**
     * Bind function calls that could not be bound when first encountered. These
     * will either be forwards references to functions declared later in the same query module,
     * or in modules that are being imported recursively, or errors.
     * <p/>
     * This method is for internal use only.
     * @throws net.sf.saxon.trans.XPathException
     *          if a function call refers to a function that has
     *          not been declared
     */

    public void bindUnboundFunctionCalls() throws XPathException {
        UnboundFunctionLibrary lib = (UnboundFunctionLibrary)functionLibraryList.get(unboundFunctionLibraryNr);
        lib.bindUnboundFunctionReferences(functionLibraryList, getConfiguration());
    }

    /**
     * Fixup all references to global functions. This method is called
     * on completion of query parsing. Each XQueryFunction is required to
     * bind all references to that function to the object representing the run-time
     * executable code of the function.
     * <p/>
     * This method is for internal use only. It is called only on the StaticQueryContext for the main
     * query body (not for library modules).
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public void fixupGlobalFunctions() throws XPathException {
        globalFunctionLibrary.fixupGlobalFunctions(this);
    }

    /**
     * Optimize the body of all global functions.
     * <p/>
     * This method is for internal use only. It is called only on the StaticQueryContext for the main
     * query body (not for library modules).
     * @throws net.sf.saxon.trans.XPathException if an error occurs during optimization
     */

    public void optimizeGlobalFunctions() throws XPathException {
        globalFunctionLibrary.optimizeGlobalFunctions();
    }


    /**
     * Output "explain" information about each declared function.
     * <p/>
     * This method is intended primarily for internal use.
     * @param out the expression presenter used to display the output
     */

    public void explainGlobalFunctions(ExpressionPresenter out) {
        globalFunctionLibrary.explainGlobalFunctions(out);
    }

    /**
     * Get the function with a given name and arity. This method is provided so that XQuery functions
     * can be called directly from a Java application. Note that there is no type checking or conversion
     * of arguments when this is done: the arguments must be provided in exactly the form that the function
     * signature declares them.
     * @param uri       the uri of the function name
     * @param localName the local part of the function name
     * @param arity     the number of arguments.
     * @return the user-defined function, or null if no function with the given name and arity can be located
     * @since 8.4
     */

    public UserFunction getUserDefinedFunction(String uri, String localName, int arity) {
        return globalFunctionLibrary.getUserDefinedFunction(uri, localName, arity);
    }

    /**
     * Bind unbound variables (these are typically variables that reference another module
     * participating in a same-namespace cycle, since local forwards references are not allowed)
     * @throws net.sf.saxon.trans.XPathException if an error occurs, for example if the
     * variable reference cannot be resolved or if the variable is private
     */

    public void bindUnboundVariables() throws XPathException {
        for (UndeclaredVariable uv : undeclaredVariables.values()) {
            StructuredQName qName = uv.getVariableQName();
            GlobalVariable var = variables.get(qName);
            if (var == null) {
                String uri = qName.getURI();
                if (importsNamespace(uri)) {
                    QueryModule main = getTopLevelModule();
                    var = main.libraryVariables.get(qName);
                }
            }
            if (var == null) {
                XPathException err = new XPathException("Unresolved reference to variable $" +
                        uv.getVariableQName().getDisplayName());
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            } else if (var.isPrivate() && !var.getSystemId().equals(getSystemId())) {
                XPathException err = new XPathException("Cannot reference a private variable in a different module");
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            } else {
                checkImportedType(var.getRequiredType(), var);
                uv.transferReferences(var);
            }
        }
    }

    /**
     * Add an imported schema to this static context. A query module can reference
     * types in a schema provided two conditions are satisfied: the schema containing those
     * types has been loaded into the Configuration, and the target namespace has been imported
     * by this query module. This method achieves the second of these conditions. It does not
     * cause the schema to be loaded.
     * <p/>
     * @param targetNamespace The target namespace of the schema to be added
     * @param baseURI         The base URI against which the locationURIs are to be absolutized
     * @param locationURIs    a list of strings containing the absolutized URIs of the "location hints" supplied
     *                        for this schema
     * @since 8.4
     */

    public void addImportedSchema(String targetNamespace, String baseURI, /*@NotNull*/ List<String> locationURIs) {
        if (importedSchemata == null) {
            importedSchemata = new HashSet<String>(5);
        }
        importedSchemata.add(targetNamespace);
        HashMap<String, HashSet<String>> loadedSchemata = getTopLevelModule().loadedSchemata;
        if (loadedSchemata == null) {
            loadedSchemata = new HashMap<String, HashSet<String>>(5);
            getTopLevelModule().loadedSchemata = loadedSchemata;
        }
        HashSet<String> entries = loadedSchemata.get(targetNamespace);
        if (entries == null) {
            entries = new HashSet<String>(locationURIs.size());
            loadedSchemata.put(targetNamespace, entries);
        }
        for (String relative : locationURIs) {
            try {
                URI abs = ResolveURI.makeAbsolute(relative, baseURI);
                entries.add(abs.toString());
            } catch (URISyntaxException e) {
                // ignore the URI if it's not valid
            }
        }
    }

    /**
     * For the top-level module only, get all the schema modules imported anywhere in the query.
     * @return a Map whose key is the target namespace of a set of schema documents, and whose
     *         value is a Set containing the absolutized location URIs ("hints") of the locations from
     *         which those schema documents were loaded, as strings.
     */

    public Map<String, HashSet<String>> getAllImportedSchemata() {
        return loadedSchemata;
    }

    /**
     * Get the schema for a given namespace, if it has been imported
     * @param namespace The namespace of the required schema. Supply "" for
     *                  a no-namespace schema.
     * @return The schema if found, or null if not found.
     * @since 8.4
     */

    public boolean isImportedSchema(String namespace) {
        return importedSchemata != null && importedSchemata.contains(namespace);
    }

    /**
     * Get the set of imported schemas
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    /*@Nullable*/ public Set<String> getImportedSchemaNamespaces() {
        if (importedSchemata == null) {
            return Collections.EMPTY_SET;
        } else {
            return importedSchemata;
        }
    }

    /**
     * Specify that typed input nodes are or are not allowed
     * @param option true if typed input nodes are allowed
     */

//    public void setAllowTypedNodes(boolean option) {
//        if (!isTopLevelModule()) {
//            throw new UnsupportedOperationException("Not a top-level module");
//        }
//        allowTypedNodes = Boolean.valueOf(option);
//    }

    /**
     * Ask whether all nodes processed by this query will be untyped.
     * It is then not necessary to generate code that handles typed input nodes.
     * @return true if the query is to be compiled to permit typed nodes to
     * appear in the input; false if not; null if no value has been specified.
     * @since 9.2
     */

//    public Boolean isAllowTypedNodes() {
//        return getTopLevelModule().allowTypedNodes;
//    }


    /**
     * Report a fatal error in the query (via the registered ErrorListener)
     * @param err the error to be signalled
     */

    public void reportFatalError(/*@NotNull*/ XPathException err) {
        if (!err.hasBeenReported()) {
            try {
                if (userQueryContext == null) {
                    config.getErrorListener().fatalError(err);
                } else {
                    userQueryContext.getErrorListener().fatalError(err);
                }
            } catch (TransformerException e) {
                // ignore secondary errors
            }
            err.setHasBeenReported(true);
        }
    }

    /**
     * Check that all the types used in the signature of an imported function
     * are available in the module of the caller of the function
     * @param fd the declaration of the imported function
     * @throws XPathException if an error is found
     */

    public void checkImportedFunctionSignature(/*@NotNull*/ XQueryFunction fd) throws XPathException {
        checkImportedType(fd.getResultType(), fd);
        for (int a = 0; a < fd.getNumberOfArguments(); a++) {
            SequenceType argType = fd.getArgumentTypes()[a];
            checkImportedType(argType, fd);
        }
    }

    /**
     * Check that a SequenceType used in the definition of an imported variable or function
     * is available in the importing module
     * @param importedType the type that is to be checked
     * @param declaration  the containing query or function definition
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is fonnd.
     */

    public void checkImportedType(/*@NotNull*/ SequenceType importedType, /*@NotNull*/ final Declaration declaration)
            throws XPathException {
        ItemType type = importedType.getPrimaryType();
        type.visitNamedSchemaComponents(new SchemaComponentVisitor() {
            public void visitSchemaComponent(/*@NotNull*/ SchemaComponent component) throws XPathException {
                if (component instanceof SchemaDeclaration) {
                    int f = ((SchemaDeclaration)component).getFingerprint();
                    checkSchemaNamespaceImported(f, declaration);
                } else if (component instanceof SchemaType) {
                    int f = ((SchemaType)component).getFingerprint();
                    checkSchemaNamespaceImported(f, declaration);
                }
            }
        });
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     * @return a dynamic context object
     */

    /*@NotNull*/ public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration(), userQueryContext.getCollationMap());
    }


    /**
     * Get a named collation.
     * @param name The name of the required collation. Supply null to get the default collation.
     * @return the collation; or null if the required collation is not found.
     */

    public StringCollator getCollation(/*@Nullable*/ String name) {
        if (name == null) {
            name = defaultCollationName;
        }
        return userQueryContext.getCollation(name);
    }

    /**
     * Get the name of the default collation.
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    /*@Nullable*/ public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Set the name of the default collation
     * @param collation the URI of the default collation
     * @throws NullPointerException if the supplied collation name is null
     */

    public void setDefaultCollationName(/*@Nullable*/ String collation) {
        if (collation == null) {
            throw new NullPointerException("collationName");
        }
        defaultCollationName = collation;
    }

    public Map<LanguageFeature, SourceLocator> getFeaturesUsed() {
        return featuresUsed;
    }

    public Set<LanguageFeature> getFeaturesProhibited() {
        return featuresProhibited;
    }

    public Set<LanguageFeature> getFeaturesRequired() {
        return featuresRequired;
    }

    public void useFeature(LanguageFeature feature, String construct, SourceLocator locator) throws XPathException {
        if (featuresProhibited.contains(feature)) {
            XPathException err = new XPathException("Use of " + construct +
                    " is disallowed because the " + feature.getName().getLocalPart() + " has been prohibited", "XQST0128");
            err.setLocator(locator);
            throw err;
        }
        featuresUsed.put(feature, locator);
        if (feature.getParent() != null) {
            useFeature(feature.getParent(), construct, locator);
        }
    }

    /**
     * Register a namespace that is explicitly declared in the prolog of the query module.
     * @param prefix The namespace prefix. Must not be null.
     * @param uri    The namespace URI. Must not be null. The value "" (zero-length string) is used
     *               to undeclare a namespace; it is not an error if there is no existing binding for
     *               the namespace prefix.
     * @throws net.sf.saxon.trans.XPathException if the declaration is invalid
     */

    public void declarePrologNamespace(/*@Nullable*/ String prefix, /*@Nullable*/ String uri) throws XPathException {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declarePrologNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declarePrologNamespace()");
        }
        if ((prefix.equals("xml") != uri.equals(NamespaceConstant.XML))) {
            XPathException err = new XPathException("Invalid declaration of the XML namespace");
            err.setErrorCode("XQST0070");
            err.setIsStaticError(true);
            throw err;
        }
        if (explicitPrologNamespaces.get(prefix) != null) {
            XPathException err = new XPathException("Duplicate declaration of namespace prefix \"" + prefix + '"');
            err.setErrorCode("XQST0033");
            err.setIsStaticError(true);
            throw err;
        } else {
            explicitPrologNamespaces.put(prefix, uri);
        }
    }

    /**
     * Declare an active namespace, that is, a namespace which as well as affecting the static
     * context of the query, will also be copied to the result tree when element constructors
     * are evaluated. When searching for a prefix-URI binding, active namespaces are searched
     * first, then passive namespaces. Active namespaces are later undeclared (in reverse sequence)
     * using {@link #undeclareNamespace()}.
     * <p/>
     * This method is intended for internal use only.
     * @param prefix the namespace prefix
     * @param uri    the namespace URI. If this is equal to "", the namespace is "undeclared" within the
     * scope of this declaration.
     */

    public void declareActiveNamespace(/*@Nullable*/ String prefix, /*@Nullable*/ String uri) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declareActiveNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declareActiveNamespace()");
        }

        ActiveNamespace entry = new ActiveNamespace();
        entry.prefix = prefix;
        entry.uri = uri;
        activeNamespaces.push(entry);

//         if (prefix.length() == 0) {
//             defaultElementNamespace = uri;
//         }

    }

    /**
     * Undeclare the most recently-declared active namespace. This method is called
     * when a namespace declaration goes out of scope (while processing an element end tag).
     * It is NOT called when an XML 1.1-style namespace undeclaration is encountered.
     * <p/>
     * This method is intended for internal use only.
     * @see #declareActiveNamespace(String, String)
     */

    public void undeclareNamespace() {
        activeNamespaces.pop();
    }


    /**
     * Get the URI for a prefix.
     * This method is used by the XQuery parser to resolve namespace prefixes.
     * <p/>
     * This method is intended primarily for internal use.
     * @param prefix The prefix
     * @return the corresponding namespace URI
     * @throws net.sf.saxon.trans.XPathException
     *          (with error code XPST0081)
     *          if the prefix has not been declared
     */

    /*@Nullable*/ public String getURIForPrefix(/*@NotNull*/ String prefix) throws XPathException {
        String uri = checkURIForPrefix(prefix);
        if (uri == null) {
            XPathException err = new XPathException("Prefix " + prefix + " has not been declared");
            err.setErrorCode("XPST0081");
            err.setIsStaticError(true);
            throw err;
        }
        return uri;
    }

    /**
     * Get the URI for a prefix if there is one, return null if not.
     * This method is used by the XQuery parser to resolve namespace prefixes.
     * <p/>
     * This method is intended primarily for internal use.
     * @param prefix The prefix. Supply "" to obtain the default namespace for elements and types.
     * @return the corresponding namespace URI, or null if the prefix has not
     *         been declared. If the prefix is "" and the default namespace is the non-namespace,
     *         return "".
     */

    /*@Nullable*/ public String checkURIForPrefix(/*@NotNull*/ String prefix) {
        // Search the active namespaces first, then the passive ones.
        if (activeNamespaces != null) {
            for (int i = activeNamespaces.size() - 1; i >= 0; i--) {
                if ((activeNamespaces.get(i)).prefix.equals(prefix)) {
                    String uri = (activeNamespaces.get(i)).uri;
                    if (uri.equals("") && !prefix.equals("")) {
                        // the namespace is undeclared
                        return null;
                    }
                    return uri;
                }
            }
        }
        if (prefix.length() == 0) {
            return defaultElementNamespace;
        }
        String uri = explicitPrologNamespaces.get(prefix);
        if (uri != null) {
            // A zero-length URI means the prefix was undeclared in the prolog, and we mustn't look elsewhere
            return (uri.length() == 0 ? null : uri);
        }

        if (userQueryContext != null) {
            uri = userQueryContext.getNamespaceForPrefix(prefix);
            if (uri != null) {
                return uri;
            }
            NamespaceResolver externalResolver = userQueryContext.getExternalNamespaceResolver();
            if (externalResolver != null) {
                return externalResolver.getURIForPrefix(prefix, true);
            }
        }
        return null;
    }


    /**
     * Get the default XPath namespace for elements and types. Note that this is not necessarily
     * the default namespace declared in the query prolog; within an expression, it may change in response
     * to namespace declarations on element constructors.
     * @return the default namespace, or {@link NamespaceConstant#NULL} for the non-namespace
     */

    /*@Nullable*/ public String getDefaultElementNamespace() {
        return checkURIForPrefix("");
    }

    /**
     * Set the default element namespace as declared in the query prolog
     * @param uri the default namespace for elements and types
     */

    public void setDefaultElementNamespace(String uri) {
        defaultElementNamespace = uri;
    }

    /**
     * Get the default function namespace
     * @return the default namespace for function names
     */

    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the default function namespace
     * @param uri the default namespace for functions
     */

    public void setDefaultFunctionNamespace(String uri) {
        defaultFunctionNamespace = uri;
    }

    /**
     * Set the revalidation mode. This is used only if XQuery Updates are in use, in other cases
     * the value is ignored.
     * @param mode the revalidation mode. This must be one of {@link Validation#STRICT},
     *             {@link Validation#LAX}, or {@link Validation#SKIP}
     */

    public void setRevalidationMode(int mode) {
        if (mode == Validation.STRICT || mode == Validation.LAX || mode == Validation.SKIP) {
            revalidationMode = mode;
        } else {
            throw new IllegalArgumentException("Invalid mode " + mode);
        }
    }

    /**
     * Get the revalidation mode. This is used only if XQuery Updates are in use, in other cases
     * the value is ignored.
     * @return the revalidation mode. This will be one of {@link Validation#STRICT},
     *         {@link Validation#LAX}, or {@link Validation#SKIP}
     */

    public int getRevalidationMode() {
        return revalidationMode;
    }

    /**
     * Get an array containing the namespace codes of all active namespaces.
     * <p/>
     * This method is for internal use only.
     * @return an array of namespace codes. A namespace code is an int that holds a prefix code in the
     *         top half and a uri code in the bottom half.
     */

    /*@NotNull*/ public NamespaceBinding[] getActiveNamespaceCodes() {
        // TODO: the result of this method includes undeclarations
        if (activeNamespaces == null) {
            return NamespaceBinding.EMPTY_ARRAY;
        }
        NamespaceBinding[] nsBindings = new NamespaceBinding[activeNamespaces.size()];
        int used = 0;
        HashSet<String> prefixes = new HashSet<String>(10);
        for (int n = activeNamespaces.size() - 1; n >= 0; n--) {
            ActiveNamespace an = activeNamespaces.get(n);
            if (!prefixes.contains(an.prefix)) {
                prefixes.add(an.prefix);
                nsBindings[used++] = new NamespaceBinding(an.prefix, an.uri);
            }
        }
        if (used < nsBindings.length) {
            NamespaceBinding[] nb2 = new NamespaceBinding[used];
            System.arraycopy(nsBindings, 0, nb2, 0, used);
            nsBindings = nb2;
        }
        return nsBindings;
    }

    /**
     * Get a copy of the Namespace Context. This method is used internally
     * by the query parser when a construct is encountered that needs
     * to save the namespace context for use at run-time. Note that unlike other implementations of
     * StaticContext, the state of the QueryModule changes as the query is parsed, with different namespaces
     * in scope at different times. It's therefore necessary to compute the whole namespace context each time.
     * <p/>
     * This method is for internal use only.
     */

    /*@NotNull*/ public NamespaceResolver getNamespaceResolver() {
        List<NamespaceBinding> externalNamespaceCodes = null;
        NamespaceResolver externalResolver = userQueryContext.getExternalNamespaceResolver();
        if (externalResolver != null) {
            externalNamespaceCodes = new ArrayList<NamespaceBinding>();
            Iterator iter = externalResolver.iteratePrefixes();
            while (iter.hasNext()) {
                String prefix = (String)iter.next();
                String uri = externalResolver.getURIForPrefix(prefix, true);
                NamespaceBinding nscode = new NamespaceBinding(prefix, uri);
                externalNamespaceCodes.add(nscode);
            }
        }
        HashMap<String, String> userDeclaredNamespaces = userQueryContext.getUserDeclaredNamespaces();
        List<NamespaceBinding> nsBindings = new ArrayList<NamespaceBinding>();

        for (Map.Entry<String, String> e : userDeclaredNamespaces.entrySet()) {
            nsBindings.add(new NamespaceBinding(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, String> e : explicitPrologNamespaces.entrySet()) {
            nsBindings.add(new NamespaceBinding(e.getKey(), e.getValue()));
        }
        if (defaultElementNamespace.length() != 0) {
            nsBindings.add(new NamespaceBinding("", defaultElementNamespace));
        }
        nsBindings.addAll(Arrays.asList(getActiveNamespaceCodes()));

        if (externalNamespaceCodes != null) {
            for (NamespaceBinding externalNamespaceCode : externalNamespaceCodes) {
                nsBindings.add(externalNamespaceCode);
            }
        }

        return new SavedNamespaceContext(nsBindings);
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     * @return the required type of the context item. Note that this is the same for all modules.
     * @since 9.3
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     * @return the decimal format manager for this static context, or null if named decimal
     *         formats are not supported in this environment.
     */

    /*@Nullable*/ public DecimalFormatManager getDecimalFormatManager() {
        if (decimalFormatManager == null) {
            decimalFormatManager = new DecimalFormatManager();
        }
        return decimalFormatManager;
    }

    /**
     * Issue a compile-time warning. This method is used during XQuery expression compilation to
     * output warning conditions.
     * <p/>
     * This method is intended for internal use only.
     */

    public void issueWarning(String s, SourceLocator locator) {
        XPathException err = new XPathException(s);
        err.setLocator(locator);
        try {
            getConfiguration().getErrorListener().warning(err);
        } catch (TransformerException e) {
            // ignore any error thrown
        }
    }

    /**
     * Get the line number of the expression within that container.
     * Used to construct error messages. This method is provided to satisfy the StaticContext interface,
     * but the value is meaningful only for XPath expressions within a document such as a stylesheet.
     * @return -1 always
     */

    public int getLineNumber() {
        return -1;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     * @return false; XPath 1.0 compatibility mode is not supported in XQuery
     * @since 8.4
     */

    public boolean isInBackwardsCompatibleMode() {
        return false;
    }

    /**
     * Determine whether a built-in type is available in this context. This method caters for differences
     * between host languages as to which set of types are built in.
     * @param type the supposedly built-in type. This will always be a type in the
     *             XS or XDT namespace.
     * @return true if this type can be used in this static context
     */

    public boolean isAllowedBuiltInType(/*@NotNull*/ BuiltInAtomicType type) {
        return type.getFingerprint() != StandardNames.XS_DATE_TIME_STAMP ||
                config.getXsdVersion() == Configuration.XSD11;
    }

    /**
     * Set whether the query is allowed to be updating
     * @param updating true if the query may use the XQuery Update facility (requires Saxon-EE)
     * @since 9.1
     */

    public void setUpdating(boolean updating) {
        isUpdating = updating;
    }

    /**
     * Ask whether the query is allowed to be updating
     * @return true if the query is allowed to use the XQuery Update facility
     * @since 9.1
     */

    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * Get the language version supported
     * @return the language version (currently "1.0" or "3.0": "1.1" is sometimes accepted in place of "3.0")
     * @since 9.2; changed in 9.3 to return a DecimalValue instead of a String
     */

    public DecimalValue getLanguageVersion() {
        return languageVersion;
    }

    /**
     * Get the XPath language level supported
     * @return 3.0 if XQuery 3.0 is enabled, or 2.0 otherwise
     */

    public DecimalValue getXPathLanguageLevel() {
        return DecimalValue.THREE.equals(languageVersion) ? DecimalValue.THREE : DecimalValue.TWO;
    }

    /**
     * Get the CodeInjector if one is in use
     * @return the code injector if there is one
     */

    public CodeInjector getCodeInjector() {
        return codeInjector;
    }


    /**
     * Check that the namespace of a given name is the namespace of an imported schema
     * @param fingerprint the fingerprint of the "given name"
     * @param declaration the declaration of the variable or function that has this given name
     * @throws net.sf.saxon.trans.XPathException
     *          (error XQST0036) if the namespace is not present in a schema
     *          imported by the importing query module
     */

    private void checkSchemaNamespaceImported(int fingerprint, /*@NotNull*/ Declaration declaration)
            throws XPathException {
    	NamePool namePool = getNamePool();
        String uri = namePool.getURI(fingerprint);
        if (!uri.equals(NamespaceConstant.SCHEMA) && !uri.equals(NamespaceConstant.ANONYMOUS) &&
                !uri.equals(NamespaceConstant.JAVA_TYPE) && !isImportedSchema(uri)) {
			StringBuilder msg = new StringBuilder("Schema component ").append(namePool.getDisplayName(fingerprint))
				.append(" used in ");
            if (declaration instanceof GlobalVariable) {
				msg.append("declaration of imported variable ")
					.append(((GlobalVariable) declaration).getVariableQName().getDisplayName());
            } else {
				msg.append("signature of imported function ").append(((XQueryFunction) declaration).getDisplayName());
            }
            msg.append(" is not declared in any schema imported by ");
            String module = getModuleNamespace();
            if (module == null) {
                msg.append("the main query module");
            } else {
                msg.append("query module ").append(module);
            }
            XPathException err = new XPathException(msg.toString());
            err.setErrorCode("XQST0036");
            err.setIsStaticError(true);
            err.setLocator(declaration);
            throw err;
        }
    }

    /**
     * Inner class containing information about an active namespace entry
     */

    private static class ActiveNamespace {
        /*@Nullable*/ public String prefix;
        /*@Nullable*/ public String uri;
    }
}

