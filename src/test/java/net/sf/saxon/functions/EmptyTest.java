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
import static org.mockito.Mockito.*;


/**
 * Created by iuliana.grajdeanu on 3/8/2017.
 */
public class EmptyTest {

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

    private Empty emptyFct;

    @Before
    public void setUp() throws Exception {
        emptyFct = new Empty();
        count = 0;
        Expression[] expressions = new Expression[1];
        expressions[0] = expression;
        emptyFct.setArguments(expressions);
        when(expression.iterate(any(XPathContext.class))).thenReturn(iter);
    }


    @Test
    public void effectiveBooleanValueParantheseTest() throws Exception {
        StringValue sv = new StringValue("()");
        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> count++ <= 2);

        when(iter.next()).thenReturn(sv);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertTrue(result);
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

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertFalse(result);
    }

    @Test
    public void effectiveBooleanValuMultipleItemsTwoTest() throws Exception {
        final StringValue[] arr = new StringValue[3];
        arr[0] = null;
        arr[1] = new StringValue("()");
        arr[2] = new StringValue("a");

        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> count++ <= 3);
        when(iter.next()).thenAnswer(invocationOnMock -> arr[sv++]);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertFalse(result);
    }

    @Test
    public void effectiveBooleanValuMultipleItemsThreeTest() throws Exception {
        final StringValue[] arr = new StringValue[2];
        arr[0] = null;
        arr[1] = new StringValue("()");

        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> count++ <= 2);
        when(iter.next()).thenAnswer(invocationOnMock -> arr[sv++]);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertTrue(result);
    }

    @Test
    public void effectiveBooleanValueNullTest() throws Exception {
        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> count++ <= 2);
        when(iter.next()).thenReturn(null);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertTrue(result);
    }

    /**
     * Negative test not null/ not parantheses - value not in scope
     *
     * @throws Exception
     */
    @Test
    public void effectiveBooleanValueNotInScope() throws Exception {
        StringValue sv = new StringValue("a");
        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> count++ <= 2);
        when(iter.next()).thenReturn(sv);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertFalse(result);
    }

    /**
     * Negative test - empty iterator
     *
     * @throws Exception
     */
    @Test
    public void effectiveBooleanValueEmptyIterator() throws Exception {
        when(iter.getProperties()).thenReturn(4);
        when(iter.hasNext()).then(invocation -> false);

        boolean result = emptyFct.effectiveBooleanValue(xpathCtx);

        verify(iter, times(1)).close();
        assertTrue(result);
    }

}
