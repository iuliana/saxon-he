package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.StringValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by iuliana.cosmina on 4/6/17.
 */
public class ExistsTest {

	@Mock
	XPathContext xpathCtx;
	@Mock
	Expression expression;
	@Mock
	LookaheadIterator iter;

	int count = 0;
	int sv =0;

	@Rule
	public MockitoRule context = MockitoJUnit.rule();

	private Exists existsFct;


	@Before
	public void setUp() throws Exception {
		existsFct = new Exists();
		count = 0;
		Expression[] expressions = new Expression[1];
		expressions[0] = expression;
		existsFct.setArguments(expressions);
		when(expression.iterate(any(XPathContext.class))).thenReturn(iter);
	}


	@Test
	public void effectiveBooleanValueParantheseTest() throws Exception {
		StringValue sv = new StringValue("()");
		when(iter.getProperties()).thenReturn(4);
		when(iter.hasNext()).then(invocation -> count++ <= 2);

		when(iter.next()).thenReturn(sv);

		boolean result = existsFct.effectiveBooleanValue(xpathCtx);

		verify(iter, times(1)).close();
		assertFalse(result);
	}

	@Test
	public void effectiveBooleanValueNullTest() throws Exception {
		StringValue sv = null;
		when(iter.getProperties()).thenReturn(4);
		when(iter.hasNext()).then(invocation -> count++ <= 2);

		when(iter.next()).thenReturn(sv);

		boolean result = existsFct.effectiveBooleanValue(xpathCtx);

		verify(iter, times(1)).close();
		assertFalse(result);
	}

	@Test
	public void effectiveBooleanValuMultipleItemsOneTest() throws Exception {
		final StringValue[] arr = new StringValue[3];
		arr[0] = new StringValue("()");
		arr[1] = new StringValue("a");
		arr[2] = null;

		when(iter.getProperties()).thenReturn(4);
		when(iter.hasNext()).then(invocation -> count++ <= 3);
		when(iter.next()).thenAnswer(invocationOnMock -> arr[sv++]);

		boolean result = existsFct.effectiveBooleanValue(xpathCtx);

		verify(iter, times(1)).close();
		assertTrue(result);
	}


	@Test
	public void effectiveBooleanValueVarTest() throws Exception {
		StringValue sv = new StringValue("a");
		when(iter.getProperties()).thenReturn(4);
		when(iter.hasNext()).then(invocation -> count++ <= 2);

		when(iter.next()).thenReturn(sv);

		boolean result = existsFct.effectiveBooleanValue(xpathCtx);

		verify(iter, times(1)).close();
		assertTrue(result);
	}

}
