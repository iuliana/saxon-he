////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.HashMap;

import org.apache.log4j.Logger;

/**
 * This class contains static data tables defining the properties of standard functions. "Standard functions"
 * here means the XPath 2.0 functions, the XSLT 2.0 functions, and a few selected extension functions
 * which need special recognition.
 */
public abstract class StandardFunction {

	private static final Logger LOGGER = Logger.getLogger(StandardFunction.class);
	
    public static final Sequence EMPTY = EmptySequence.getInstance();

    /**
     * Categories of functions, bit significant
     */
    public static final int CORE = 1;
    public static final int XSLT = 2;
    public static final int USE_WHEN = 4;
    public static final int XQUPDATE = 8;
    public static final int XPATH30 = 16;
    public static final int INTERNAL = 32;
    public static final int XSLT30 = 64;

    /**
     * Local names used for cardinality values
     */
    public static final int ONE = StaticProperty.ALLOWS_ONE;
    public static final int OPT = StaticProperty.ALLOWS_ZERO_OR_ONE;
    public static final int STAR = StaticProperty.ALLOWS_ZERO_OR_MORE;
    public static final int PLUS = StaticProperty.ALLOWS_ONE_OR_MORE;

    /**
     * Classification of function arguments for serialization purposes; note that values must not conflict
     * with bit settings used for cardinalities
     */
    public static final int LOOK = 1 << 24;   // = syntactic context INSPECTION
    public static final int TAKE = 1 << 25;   // = syntactic context NODE-VALUE (implicit when type is atomic)
    public static final int GIVE = 1 << 26;   // = syntactic context INHERIT (node is included in function result)
    public static final int WALK = 1 << 27;   // = syntactic context NAVIGATE (function navigates from this node)

    private static HashMap<String, Entry> functionTable = new HashMap<>(200);

    public static final int AS_ARG0 = 1;          // Result has same type as first argument
    public static final int AS_PRIM_ARG0 = 2;     // Result has same primitive type as first argument
    public static final int FOCUS = 4;            // Depends on focus
    public static final int BASE = 8;             // Depends on base URI
    public static final int NS = 16;              // Depends on namespace context
    public static final int DCOLL = 32;           // Depends on default collation

    public static final int DEPENDS_ON_STATIC_CONTEXT = BASE | NS | DCOLL;
    
    /**
     * This class is never instantiated
     */
    private StandardFunction() {
    }

    /**
     * Register a system function in the table of function details.
     *
     * @param name                the function name
     * @param implementationClass the class used to implement the function
     * @param opcode              identifies the function when a single class implements several functions
     * @param minArguments        the minimum number of arguments required
     * @param maxArguments        the maximum number of arguments allowed
     * @param itemType            the item type of the result of the function
     * @param cardinality         the cardinality of the result of the function
     * @param applicability       the host languages (and versions thereof) in which this function is available
     * @param properties
     * @return the entry describing the function. The entry is incomplete, it does not yet contain information
     *         about the function arguments.
     */
	public static Entry register(String name, Class implementationClass, int opcode, int minArguments, int maxArguments,
		ItemType itemType, int cardinality, int applicability, int properties) {
		Entry e = makeEntry(name, implementationClass, opcode, minArguments, maxArguments, itemType, cardinality,
			applicability, properties);
		functionTable.put(name, e);
		return e;
	}

    /**
     * Make a table entry describing the signature of a function, with a reference to the implementation class.
     *
     * @param name                the function name
     * @param implementationClass the class used to implement the function
     * @param opcode              identifies the function when a single class implements several functions
     * @param minArguments        the minimum number of arguments required
     * @param maxArguments        the maximum number of arguments allowed
     * @param itemType            the item type of the result of the function
     * @param cardinality         the cardinality of the result of the function
     * @param applicability       the host languages (and versions of) in which this function is available
     * @return the entry describing the function. The entry is incomplete, it does not yet contain information
     *         about the function arguments.
     */
	public static Entry makeEntry(String name, Class implementationClass, int opcode, int minArguments,
		int maxArguments, ItemType itemType, int cardinality, int applicability, int properties) {
        Entry e = new Entry();
        int hash = name.indexOf('#');
        e.name = hash < 0 ? name : name.substring(0, hash);
        e.implementationClass = implementationClass;
        e.opcode = opcode;
        e.minArguments = minArguments;
        e.maxArguments = maxArguments;
        e.itemType = itemType;
        e.cardinality = cardinality;
        e.applicability = applicability;
        e.properties = properties;
        if (maxArguments > 100) {
            // special case for concat()
            e.argumentTypes = new SequenceType[1];
            e.resultIfEmpty = new AtomicValue[1];
            e.syntacticContext = new int[1];
        } else {
            e.argumentTypes = new SequenceType[maxArguments];
            e.resultIfEmpty = new Sequence[maxArguments];
            e.syntacticContext = new int[maxArguments];
        }
        return e;
    }

    static {
        Entry e;
		register("abs", Abs.class, 0, 1, 1, BuiltInAtomicType.NUMERIC, OPT, CORE, AS_PRIM_ARG0)
			.arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY);
		
		register("adjust-date-to-timezone", Adjust.class, 0, 1, 2, BuiltInAtomicType.DATE, OPT, CORE, 0)
			.arg(0, BuiltInAtomicType.DATE, OPT, EMPTY).arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);
		
		register("adjust-dateTime-to-timezone", Adjust.class, 0, 1, 2, BuiltInAtomicType.DATE_TIME, OPT, CORE, 0)
			.arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY).arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);
		
		register("adjust-time-to-timezone", Adjust.class, 0, 1, 2, BuiltInAtomicType.TIME, OPT, CORE, 0)
			.arg(0, BuiltInAtomicType.TIME, OPT, EMPTY).arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);
		
		register("avg", Average.class, 0, 1, 1, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, 0)
			// can't say "same as first argument" because the avg of a set of integers is decimal
			.arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);
		
		register("base-uri", BaseURI.class, 0, 0, 1, BuiltInAtomicType.ANY_URI, OPT, CORE, BASE)
			.arg(0, Type.NODE_TYPE, OPT | LOOK, EMPTY);
		
		register("boolean", BooleanFn.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
			.arg(0, Type.ITEM_TYPE, STAR | LOOK, null);
		
		register("ceiling", Ceiling.class, 0, 1, 1, BuiltInAtomicType.NUMERIC, OPT, CORE, AS_PRIM_ARG0)
			.arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY);
		
		register("codepoint-equal", CodepointEqual.class, 0, 2, 2, BuiltInAtomicType.BOOLEAN, OPT, CORE, 0)
			.arg(0, BuiltInAtomicType.STRING, OPT, EMPTY).arg(1, BuiltInAtomicType.STRING, OPT, EMPTY);
		
		register("codepoints-to-string", CodepointsToString.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
			.arg(0, BuiltInAtomicType.INTEGER, STAR, null);
		
		register("collection", Collection.class, 0, 0, 1, Type.NODE_TYPE, STAR, CORE, BASE)
			.arg(0, BuiltInAtomicType.STRING, OPT, null);
		
		register("compare#2", Compare.class, 0, 2, 2, BuiltInAtomicType.INTEGER, OPT, CORE, DCOLL)
			.arg(0, BuiltInAtomicType.STRING, OPT, EMPTY).arg(1, BuiltInAtomicType.STRING, OPT, EMPTY);
		
		register("compare#3", Compare.class, 0, 3, 3, BuiltInAtomicType.INTEGER, OPT, CORE, BASE)
			.arg(0, BuiltInAtomicType.STRING, OPT, EMPTY).arg(1, BuiltInAtomicType.STRING, OPT, EMPTY)
			.arg(2, BuiltInAtomicType.STRING, ONE, null);
		
		register("concat", Concat.class, 0, 2, Integer.MAX_VALUE, BuiltInAtomicType.STRING, ONE, CORE, 0)
			.arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null);
		// Note, this has a variable number of arguments so it is treated specially
		
		register("contains#2", Contains.class, 0, 2, 2, BuiltInAtomicType.BOOLEAN, ONE, CORE, DCOLL)
			.arg(0, BuiltInAtomicType.STRING, OPT, null).arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);
		
		register("contains#3", Contains.class, 0, 3, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, BASE)
			.arg(0, BuiltInAtomicType.STRING, OPT, null).arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
			.arg(2, BuiltInAtomicType.STRING, ONE, null);
		
		register("count", Count.class, 0, 1, 1, BuiltInAtomicType.INTEGER, ONE, CORE, 0)
			.arg(0, Type.ITEM_TYPE, STAR | LOOK, Int64Value.ZERO);
		
		register("current", Current.class, 0, 0, 0, Type.ITEM_TYPE, ONE, XSLT, 0);
		
		register("current-date", CurrentDateTime.class, 0, 0, 0, BuiltInAtomicType.DATE, ONE, CORE, 0);
		
		register("current-dateTime", CurrentDateTime.class, 0, 0, 0, BuiltInAtomicType.DATE_TIME, ONE, CORE, 0);
		
		register("current-time", CurrentDateTime.class, 0, 0, 0, BuiltInAtomicType.TIME, ONE, CORE, 0);
		
		register("current-group", CurrentGroup.class, 0, 0, 0, Type.ITEM_TYPE, STAR, XSLT, 0);
		
		register("current-grouping-key", CurrentGroupingKey.class, 0, 0, 0, BuiltInAtomicType.ANY_ATOMIC, OPT, XSLT, 0);
		
		register("data#0", Data.class, 0, 0, 0, BuiltInAtomicType.ANY_ATOMIC, STAR, XPATH30, FOCUS);
		
		register("data#1", Data.class, 0, 1, 1, BuiltInAtomicType.ANY_ATOMIC, STAR, CORE, 0)
			.arg(0, Type.ITEM_TYPE, STAR | TAKE, EMPTY);
		
		register("dateTime", DateTimeConstructor.class, 0, 2, 2, BuiltInAtomicType.DATE_TIME, OPT, CORE, 0)
			.arg(0, BuiltInAtomicType.DATE, OPT, EMPTY).arg(1, BuiltInAtomicType.TIME, OPT, EMPTY);
		
		register("day-from-date", Component.class, (Component.DAY << 16) + StandardNames.XS_DATE, 1, 1,
			BuiltInAtomicType.INTEGER, OPT, CORE, 0).arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);
		
		register("day-from-dateTime", Component.class, (Component.DAY << 16) + StandardNames.XS_DATE_TIME, 1, 1,
			BuiltInAtomicType.INTEGER, OPT, CORE, 0).arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);
		
		register("days-from-duration", Component.class, (Component.DAY << 16) + StandardNames.XS_DURATION, 1, 1,
			BuiltInAtomicType.INTEGER, OPT, CORE, 0).arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);
		
		register("deep-equal#2", DeepEqual.class, 0, 2, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, DCOLL)
			.arg(0, Type.ITEM_TYPE, STAR, null).arg(1, Type.ITEM_TYPE, STAR, null);
		
		register("deep-equal#3", DeepEqual.class, 0, 2, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, BASE)
			.arg(0, Type.ITEM_TYPE, STAR, null).arg(1, Type.ITEM_TYPE, STAR, null)
			.arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("default-collation", DefaultCollation.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, CORE, DCOLL);

		register("distinct-values#1", DistinctValues.class, 0, 1, 2, BuiltInAtomicType.ANY_ATOMIC, STAR, CORE, DCOLL)
			.arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);
        
		register("distinct-values#2", DistinctValues.class, 0, 1, 2, BuiltInAtomicType.ANY_ATOMIC, STAR, CORE, BASE)
			.arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY).arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("doc", Doc.class, 0, 1, 1, NodeKindTest.DOCUMENT, OPT, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("doc-available", DocAvailable.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE);

        register("document", DocumentFn.class, 0, 1, 2, Type.NODE_TYPE, STAR, XSLT, BASE)
                .arg(0, Type.ITEM_TYPE, STAR, null).arg(1, Type.NODE_TYPE, ONE, null);

        register("document-uri#0", DocumentUriFn.class, 0, 0, 0, BuiltInAtomicType.ANY_URI, OPT, XPATH30, FOCUS);

        register("document-uri#1", DocumentUriFn.class, 0, 1, 1, BuiltInAtomicType.ANY_URI, OPT, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, EMPTY);

        register("empty", Empty.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | LOOK, BooleanValue.TRUE);

        register("ends-with#2", EndsWith.class, 0, 2, 2, BuiltInAtomicType.BOOLEAN, ONE, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null).arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);

        register("ends-with#3", EndsWith.class, 0, 3, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null).arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("element-available", ElementAvailable.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("element-with-id#1", Id.class, Id.ELEMENT_WITH_ID, 1, 1, NodeKindTest.ELEMENT, STAR, CORE, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("element-with-id#2", Id.class, Id.ELEMENT_WITH_ID, 2, 2, NodeKindTest.ELEMENT, STAR, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY).arg(1, Type.NODE_TYPE, ONE, null);

        register("encode-for-uri", EscapeURI.class, EscapeURI.ENCODE_FOR_URI, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("escape-html-uri", EscapeURI.class, EscapeURI.HTML_URI, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("error", Error.class, 0, 0, 3, Type.ITEM_TYPE, OPT, CORE, 0)
                // The return type is chosen so that use of the error() function will never give a static type error,
                // on the basis that item()? overlaps every other type, and it's almost impossible to make any
                // unwarranted inferences from it, except perhaps count(error()) lt 2.
                .arg(0, BuiltInAtomicType.QNAME, OPT, null).arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR, null);

        register("exactly-one", TreatFn.class, ONE, 1, 1, Type.ITEM_TYPE, ONE, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, ONE | GIVE, null);
        // because we don't do draconian static type checking, we can do the work in the argument type checking code

        register("false", False.class, 0, 0, 0, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0);

        register("exists", Exists.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | LOOK, BooleanValue.FALSE);

        register("floor", Floor.class, 0, 1, 1, BuiltInAtomicType.NUMERIC, OPT, CORE, AS_PRIM_ARG0)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY);

        register("format-date", FormatDate.class, StandardNames.XS_DATE, 2, 5, BuiltInAtomicType.STRING,
                OPT, XSLT | XPATH30, 0).arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null).arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null).arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-dateTime", FormatDate.class, StandardNames.XS_DATE_TIME, 2, 5, BuiltInAtomicType.STRING,
                OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-number#2", FormatNumber.class, 0, 2, 2, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-number#3", FormatNumber.class, 0, 3, 3, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, NS)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null);

        register("format-time", FormatDate.class, StandardNames.XS_TIME, 2, 5, BuiltInAtomicType.STRING,
                OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("function-available", FunctionAvailable.class, 0, 1, 2, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("generate-id#0", GenerateId.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, FOCUS);

        register("generate-id#1", GenerateId.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, StringValue.EMPTY_STRING);

        register("hours-from-dateTime", Component.class, (Component.HOURS << 16) + StandardNames.XS_DATE_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("hours-from-duration", Component.class, (Component.HOURS << 16) + StandardNames.XS_DURATION, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("hours-from-time", Component.class, (Component.HOURS << 16) + StandardNames.XS_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("id#1", Id.class, Id.ID, 1, 1, NodeKindTest.ELEMENT, STAR, CORE, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("id#2", Id.class, Id.ID, 2, 2, NodeKindTest.ELEMENT, STAR, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("idref#1", Idref.class, 0, 1, 1, Type.NODE_TYPE, STAR, CORE, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("idref#2", Idref.class, 0, 2, 2, Type.NODE_TYPE, STAR, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("implicit-timezone", CurrentDateTime.class, 0, 0, 0, BuiltInAtomicType.DAY_TIME_DURATION, ONE, CORE, 0);

        register("in-scope-prefixes", InScopePrefixes.class, 0, 1, 1, BuiltInAtomicType.STRING, STAR, CORE, 0)
                .arg(0, NodeKindTest.ELEMENT, ONE | LOOK, null);

        register("index-of#2", IndexOf.class, 0, 2, 2, BuiltInAtomicType.INTEGER, STAR, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null);

        register("index-of#3", IndexOf.class, 0, 3, 3, BuiltInAtomicType.INTEGER, STAR, CORE, BASE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("insert-before", Insert.class, 0, 3, 3, Type.ITEM_TYPE, STAR, CORE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | GIVE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR | GIVE, null);

        register("iri-to-uri", EscapeURI.class, EscapeURI.IRI_TO_URI, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("key#2", KeyFn.class, 0, 2, 2, Type.NODE_TYPE, STAR, XSLT, FOCUS | NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("key#3", KeyFn.class, 0, 3, 3, Type.NODE_TYPE, STAR, XSLT, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(2, Type.NODE_TYPE, ONE, null);

        register("lang#1", Lang.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("lang#2", Lang.class, 0, 2, 2, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, Type.NODE_TYPE, ONE | LOOK, null);

        register("last", Last.class, 0, 0, 0, BuiltInAtomicType.INTEGER, ONE, CORE, FOCUS);

        register("local-name#0", LocalNameFn.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, CORE, FOCUS);

        register("local-name#1", LocalNameFn.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, StringValue.EMPTY_STRING);

        register("local-name-from-QName", Component.class, (Component.LOCALNAME << 16) + StandardNames.XS_QNAME, 1, 1,
                BuiltInAtomicType.NCNAME, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("lower-case", LowerCase.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("matches", Matches.class, 0, 2, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("max#1", Max.class, Minimax.MAX, 1, 1, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("max#2", Max.class, Minimax.MAX, 2, 2, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, BASE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("min#1", Min.class, Minimax.MIN, 1, 1, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("min#2", Min.class, Minimax.MIN, 2, 2, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, BASE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("minutes-from-dateTime", Component.class, (Component.MINUTES << 16) + StandardNames.XS_DATE_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("minutes-from-duration", Component.class, (Component.MINUTES << 16) + StandardNames.XS_DURATION, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("minutes-from-time", Component.class, (Component.MINUTES << 16) + StandardNames.XS_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("month-from-date", Component.class, (Component.MONTH << 16) + StandardNames.XS_DATE, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("month-from-dateTime", Component.class, (Component.MONTH << 16) + StandardNames.XS_DATE_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("months-from-duration", Component.class, (Component.MONTH << 16) + StandardNames.XS_DURATION, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("name#0", NameFn.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, CORE, FOCUS);

        register("name#1", NameFn.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, StringValue.EMPTY_STRING);

        register("namespace-uri#0", NamespaceUriFn.class, 0, 0, 0, BuiltInAtomicType.ANY_URI, ONE, CORE, FOCUS);

        register("namespace-uri#1", NamespaceUriFn.class, 0, 1, 1, BuiltInAtomicType.ANY_URI, ONE, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, StringValue.EMPTY_STRING);

        register("namespace-uri-for-prefix", NamespaceForPrefix.class, 0, 2, 2, BuiltInAtomicType.ANY_URI, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, NodeKindTest.ELEMENT, ONE | LOOK, null);

        register("namespace-uri-from-QName", Component.class, (Component.NAMESPACE << 16) + StandardNames.XS_QNAME, 1, 1, BuiltInAtomicType.ANY_URI, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("nilled#0", Nilled.class, 0, 0, 0, BuiltInAtomicType.BOOLEAN, OPT, XPATH30, FOCUS);

        register("nilled#1", Nilled.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, OPT, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, EMPTY);

        register("node-name#0", NodeNameFn.class, 0, 0, 0, BuiltInAtomicType.QNAME, OPT, XPATH30, FOCUS);

        register("node-name#1", NodeNameFn.class, 0, 1, 1, BuiltInAtomicType.QNAME, OPT, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | LOOK, EMPTY);

        register("not", NotFn.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | LOOK, BooleanValue.TRUE);

        register("normalize-space#0", NormalizeSpace_0.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, CORE, FOCUS);

        register("normalize-space#1", NormalizeSpace_1.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("normalize-unicode", NormalizeUnicode.class, 0, 1, 2, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("number#0", NumberFn.class, 0, 0, 0, BuiltInAtomicType.DOUBLE, ONE, CORE, FOCUS);

        register("number#1", NumberFn.class, 0, 1, 1, BuiltInAtomicType.DOUBLE, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, DoubleValue.NaN);

        register("one-or-more", TreatFn.class, PLUS, 1, 1, Type.ITEM_TYPE, PLUS, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, PLUS | GIVE, null);
        // because we don't do draconian static type checking, we can do the work in the argument type checking code

        register("position", Position.class, 0, 0, 0, BuiltInAtomicType.INTEGER, ONE, CORE, FOCUS);

        register("prefix-from-QName", Component.class, (Component.PREFIX << 16) + StandardNames.XS_QNAME, 1, 1, BuiltInAtomicType.NCNAME, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("put", Put.class, 0, 2, 2, AnyItemType.getInstance(), OPT, XQUPDATE, 0)
                .arg(0, Type.NODE_TYPE, ONE, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("QName", QNameFn.class, 0, 2, 2, BuiltInAtomicType.QNAME, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("regex-group", RegexGroup.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, XSLT, 0)
                .arg(0, BuiltInAtomicType.INTEGER, ONE, null);

        register("remove", Remove.class, 0, 2, 2, Type.ITEM_TYPE, STAR, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, STAR | GIVE, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("replace", Replace.class, 0, 3, 4, BuiltInAtomicType.STRING,
                ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null)
                .arg(3, BuiltInAtomicType.STRING, ONE, null);

        register("resolve-QName", ResolveQName.class, 0, 2, 2, BuiltInAtomicType.QNAME, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, NodeKindTest.ELEMENT, ONE | LOOK, null);

        register("resolve-uri#1", ResolveURI.class, 0, 1, 1, BuiltInAtomicType.ANY_URI, OPT, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("resolve-uri#2", ResolveURI.class, 0, 2, 2, BuiltInAtomicType.ANY_URI, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("reverse", Reverse.class, 0, 1, 1, Type.ITEM_TYPE, STAR, CORE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | GIVE, EMPTY);

        register("root#0", Root.class, 0, 0, 0, Type.NODE_TYPE, OPT, CORE, FOCUS);

        register("root#1", Root.class, 0, 1, 1, Type.NODE_TYPE, OPT, CORE, 0)
                .arg(0, Type.NODE_TYPE, OPT | WALK, EMPTY);

        register("round#1", Round.class, 0, 1, 1, BuiltInAtomicType.NUMERIC, OPT, CORE, AS_PRIM_ARG0)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY);

        register("round#2", Round.class, 0, 2, 2, BuiltInAtomicType.NUMERIC, OPT, XPATH30, AS_PRIM_ARG0)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("round-half-to-even", RoundHalfToEven.class, 0, 1, 2, BuiltInAtomicType.NUMERIC, OPT, CORE, AS_PRIM_ARG0)
                .arg(0, BuiltInAtomicType.NUMERIC, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("seconds-from-dateTime", Component.class, (Component.SECONDS << 16) + StandardNames.XS_DATE_TIME, 1, 1, BuiltInAtomicType.DECIMAL, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("seconds-from-duration", Component.class, (Component.SECONDS << 16) + StandardNames.XS_DURATION, 1, 1, BuiltInAtomicType.DECIMAL, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("seconds-from-time", Component.class, (Component.SECONDS << 16) + StandardNames.XS_TIME, 1, 1, BuiltInAtomicType.DECIMAL, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("starts-with#2", StartsWith.class, 0, 2, 2, BuiltInAtomicType.BOOLEAN, ONE, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);

        register("starts-with#3", StartsWith.class, 0, 3, 3, BuiltInAtomicType.BOOLEAN, ONE, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("static-base-uri", StaticBaseURI.class, 0, 0, 0, BuiltInAtomicType.ANY_URI, OPT, CORE, BASE);

        register("string#0", StringFn.class, 0, 0, 0, BuiltInAtomicType.STRING, ONE, CORE, FOCUS);

        register("string#1", StringFn.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, Type.ITEM_TYPE, OPT | TAKE, StringValue.EMPTY_STRING);

        register("string-length#0", StringLength.class, 0, 0, 0, BuiltInAtomicType.INTEGER, ONE, CORE, FOCUS);

        register("string-length#1", StringLength.class, 0, 1, 1, BuiltInAtomicType.INTEGER, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("string-join#1", StringJoin.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, XPATH30, 0)
                .arg(0, BuiltInAtomicType.STRING, STAR, StringValue.EMPTY_STRING);

        register("string-join#2", StringJoin.class, 0, 2, 2, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, STAR, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("string-to-codepoints", StringToCodepoints.class, 0, 1, 1, BuiltInAtomicType.INTEGER, STAR, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("subsequence", Subsequence.class, 0, 2, 3, Type.ITEM_TYPE, STAR, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, STAR | GIVE, EMPTY)
                .arg(1, BuiltInAtomicType.NUMERIC, ONE, null)
                .arg(2, BuiltInAtomicType.NUMERIC, ONE, null);

        register("substring", Substring.class, 0, 2, 3, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.NUMERIC, ONE, null)
                .arg(2, BuiltInAtomicType.NUMERIC, ONE, null);

        register("substring-after#2", SubstringAfter.class, 0, 2, 2, BuiltInAtomicType.STRING, ONE, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null);

        register("substring-after#3", SubstringAfter.class, 0, 3, 3, BuiltInAtomicType.STRING, ONE, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("substring-before#2", SubstringBefore.class, 0, 2, 2, BuiltInAtomicType.STRING, ONE, CORE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("substring-before#3", SubstringBefore.class, 0, 3, 3, BuiltInAtomicType.STRING, ONE, CORE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("sum", Sum.class, 0, 1, 2, BuiltInAtomicType.ANY_ATOMIC, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, OPT, null);

        register("system-property", SystemProperty.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, XSLT | USE_WHEN, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("timezone-from-date", Component.class, (Component.TIMEZONE << 16) + StandardNames.XS_DATE, 1, 1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("timezone-from-dateTime", Component.class, (Component.TIMEZONE << 16) + StandardNames.XS_DATE_TIME, 1, 1,
                BuiltInAtomicType.DAY_TIME_DURATION, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("timezone-from-time", Component.class, (Component.TIMEZONE << 16) + StandardNames.XS_TIME, 1, 1,
                BuiltInAtomicType.DAY_TIME_DURATION, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("trace", Trace.class, 0, 2, 2, Type.ITEM_TYPE, STAR, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, STAR, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

//            register("true", BooleanFn.class, BooleanFn.TRUE, 0, 0, BuiltInAtomicType.BOOLEAN,
//                    UNIT, CORE)

        register("translate", Translate.class, 0, 3, 3, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("true", True.class, 0, 0, 0, BuiltInAtomicType.BOOLEAN, ONE, CORE, 0);

        register("tokenize", Tokenize.class, 0, 2, 3, BuiltInAtomicType.STRING, STAR, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("type-available", TypeAvailable.class, 0, 1, 1, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unordered", Unordered.class, 0, 1, 1, Type.ITEM_TYPE, STAR, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, STAR | GIVE, EMPTY);

        register("upper-case", UpperCase.class, 0, 1, 1, BuiltInAtomicType.STRING, ONE, CORE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("unparsed-entity-uri", UnparsedEntity.class, UnparsedEntity.URI, 1, 1, BuiltInAtomicType.ANY_URI, ONE, XSLT, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        // internal version of unparsed-entity-uri with second argument representing the current document
        register("unparsed-entity-uri_9999_", UnparsedEntity.class, UnparsedEntity.URI, 2, 2,
                BuiltInAtomicType.STRING, ONE, INTERNAL, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);
        // it must actually be a document node, but there's a non-standard error code

        register("unparsed-entity-public-id", UnparsedEntity.class, UnparsedEntity.PUBLIC_ID, 1, 1, BuiltInAtomicType.STRING, ONE, XSLT, FOCUS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        // internal version of unparsed-entity-public-id with second argument representing the current document
        register("unparsed-entity-public-id_9999_", UnparsedEntity.class, UnparsedEntity.PUBLIC_ID, 2, 2,
                BuiltInAtomicType.STRING, ONE, INTERNAL, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);
        // it must actually be a document node, but there's a non-standard error code

        register("unparsed-text", UnparsedText.class, 0, 1, 2,
                BuiltInAtomicType.STRING, OPT, XSLT | XPATH30, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-available", UnparsedTextAvailable.class, 0, 1, 2,
                BuiltInAtomicType.BOOLEAN, ONE, XSLT | XPATH30, BASE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("year-from-date", Component.class, (Component.YEAR << 16) + StandardNames.XS_DATE, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("year-from-dateTime", Component.class, (Component.YEAR << 16) + StandardNames.XS_DATE_TIME, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("years-from-duration", Component.class, (Component.YEAR << 16) + StandardNames.XS_DURATION, 1, 1,
                BuiltInAtomicType.INTEGER, OPT, CORE, 0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("zero-or-one", TreatFn.class, OPT, 1, 1, Type.ITEM_TYPE, OPT, CORE, AS_ARG0)
                .arg(0, Type.ITEM_TYPE, OPT | GIVE, null);
        // because we don't do draconian static type checking, we can do the work in the argument type checking code
    }


    /**
     * Get the table entry for the function with a given name
     *
     * @param name  the name of the function. This may be an unprefixed local-name for functions in the
     *              system namespace, or may use the conventional prefix "saxon:" in the case of Saxon extension functions
     *              that are specially recognized
     * @param arity the number of arguments of the function, or -1 if any arity will do
     * @return if the function name is known, an Entry containing information about the function. Otherwise,
     *         null
     */
    public static Entry getFunction(String name, int arity) {
        if (arity == -1) {
            for (int i = 0; i < 10; i++) {
                Entry e = getFunction(name, i);
                if (e != null) {
                    return e;
                }
            }
            return null;
        }
        // try first for an entry of the form name#arity
        Entry e = functionTable.get(name + '#' + arity);
        if (e != null) {
            return e;
        }
        // try for a generic entry
        e = functionTable.get(name);
        if (e != null && e.minArguments <= arity && e.maxArguments >= arity) {
            return e;
        }
        return null;
    }

    /**
     * An entry in the table describing the properties of a function
     */
    public static class Entry implements java.io.Serializable {
    	
        /**
         * The name of the function: a local name in the case of functions in the standard library, or a
         * name with the conventional prefix "saxon:" in the case of Saxon extension functions
         */
        public String name;
        
        /**
         * The class containing the implementation of this function (always a subclass of SystemFunction)
         */
        public Class implementationClass;
        
        /**
         * Some classes support more than one function. In these cases the particular function is defined
         * by an integer opcode, whose meaning is local to the implementation class.
         */
        public int opcode;
        
        /**
         * The minimum number of arguments required
         */
        public int minArguments;
        
        /**
         * The maximum number of arguments permitted
         */
        public int maxArguments;
        
        /**
         * The item type of the result of the function
         */
        public ItemType itemType;
        
        /**
         * The cardinality of the result of the function
         */
        public int cardinality;
        
        /**
         * Flags indicating which host languages the function is applicable to
         */
        public int applicability;
        
        /**
         * The syntactic context of each argument for the purposes of streamability analysis
         */
        public int[] syntacticContext;
        
        /**
         * An array holding the types of the arguments to the function
         */
        public SequenceType[] argumentTypes;
        
        /**
         * An array holding, for each declared argument, the value that is to be returned if an empty sequence
         * as the value of this argument allows the result to be determined irrespective of the values of the
         * other arguments; null if there is no such calculation possible
         */ 
        public Sequence[] resultIfEmpty;
        
        /**
         * Any additional properties. Currently one bit is defined: SAME_AS_FIRST_ARGUMENT indicates that
         * the result type is the same as the type of the first argument
         */
        public int properties;

        /**
         * Add information to a function entry about the argument types of the function
         *
         * @param a             the position of the argument, counting from zero
         * @param type          the item type of the argument
         * @param options       the cardinality and usage of the argument
         * @param resultIfEmpty the value returned by the function if an empty sequence appears as the value
         *                      of this argument, in the case when this result is unaffected by any other arguments. 
         *                      Supply null if this does not apply.
         * @return this entry (to allow chaining)
         */
        public Entry arg(int a, ItemType type, int options, Sequence resultIfEmpty) {
            int cardinality = options & StaticProperty.CARDINALITY_MASK;
            int syntacticContext = Expression.NAVIGATION_CONTEXT;
            if ((options & TAKE) != 0 || type instanceof AtomicType) {
                syntacticContext = Expression.NODE_VALUE_CONTEXT;
            } else if ((options & GIVE) != 0) {
                syntacticContext = Expression.INHERITED_CONTEXT;
            } else if ((options & LOOK) != 0) {
                syntacticContext = Expression.INSPECTION_CONTEXT;
            }
            try {
                this.argumentTypes[a] = SequenceType.makeSequenceType(type, cardinality);
                this.resultIfEmpty[a] = resultIfEmpty;
                this.syntacticContext[a] = syntacticContext;
            } catch (ArrayIndexOutOfBoundsException err) {
            	LOGGER.error("Internal Saxon error: Can't set argument " + a + " of " + name, err);
            }
            return this;
        }
    }
}
