package net.sf.saxon.expr;

import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.StringValue;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by iuliana.cosmina on 4/6/17.
 */
public class ValueComparisonTest {

	@Mock
	XPathContext xpathCtx;

	@Mock StringCollator collator;

	@Rule
	public MockitoRule context = MockitoJUnit.rule();

	@Test
	public void compareParathesesWithBoolean() throws XPathException {
		AtomicValue v0 = new StringValue("()");
		int op = 50;
		AtomicValue v1 = BooleanValue.get(false);
		AtomicComparer comparer = new GenericAtomicComparer(collator, xpathCtx);
		boolean checkTypes = true;
		assertTrue(ValueComparison.compare(v0, op, v1, comparer, checkTypes));
	}

	@Test(expected = XPathException.class)
	public void compareParathesesWithNumber() throws XPathException {
		AtomicValue v0 = new StringValue("()");
		int op = 50;
		AtomicValue v1 = new DoubleValue(0);
		AtomicComparer comparer = new GenericAtomicComparer(collator, xpathCtx);
		boolean checkTypes = true;
		assertTrue(ValueComparison.compare(v0, op, v1, comparer, checkTypes));
	}

	@Test
	public void compareTrueWithFalse() throws XPathException {
		AtomicValue v0 = BooleanValue.get(true);
		int op = 50;
		AtomicValue v1 = BooleanValue.get(false);
		AtomicComparer comparer = new GenericAtomicComparer(collator, xpathCtx);
		boolean checkTypes = true;
		assertFalse(ValueComparison.compare(v0, op, v1, comparer, checkTypes));
	}

	@Test(expected = XPathException.class)
	public void compareRandomStringStringWithFalse() throws XPathException {
		AtomicValue v0 = new StringValue("aaa");
		int op = 50;
		AtomicValue v1 = BooleanValue.get(false);
		AtomicComparer comparer = new GenericAtomicComparer(collator, xpathCtx);
		boolean checkTypes = true;
		assertFalse(ValueComparison.compare(v0, op, v1, comparer, checkTypes));
	}

	@Test(expected = NullPointerException.class)
	public void compareNullStringStringWithFalse() throws XPathException {
		AtomicValue v0 = null;
		int op = 50;
		AtomicValue v1 = BooleanValue.get(false);
		AtomicComparer comparer = new GenericAtomicComparer(collator, xpathCtx);
		boolean checkTypes = true;
		assertFalse(ValueComparison.compare(v0, op, v1, comparer, checkTypes));
	}
}
