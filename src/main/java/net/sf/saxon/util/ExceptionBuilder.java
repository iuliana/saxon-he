package net.sf.saxon.util;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.SourceLocator;

/**
 * Created by iuliana.cosmina on 5/16/17.
 */
public class ExceptionBuilder {

	/**
	 * For classes like {@link net.sf.saxon.expr.parser.TypeChecker}
	 * @param message
	 * @param typeError
	 * @param code
	 * @param location
	 * @return
	 */
	public static XPathException newEx(String message, boolean typeError, String code, SourceLocator location) {
		XPathException err = new XPathException(message);
		err.setErrorCode(code);
		err.setIsTypeError(typeError);
		err.setLocator(location);
		return err;
	}

	/**
	 * For classes like {@link net.sf.saxon.functions.Sum}
	 * @param message
	 * @param code
	 * @param context
	 * @param location
	 * @return
	 */
	public static XPathException newEx(String message, String code, XPathContext context, SourceLocator location) {
		XPathException err = new XPathException(message);
		err.setErrorCode(code);
		err.setXPathContext(context);
		err.setLocator(location);
		return err;
	}

	/**
	 * For classes like {{@link net.sf.saxon.expr.parser.ExpressionTool}}
	 * @param message
	 * @param typeError
	 * @param code
	 * @param context
	 * @return
	 */
	public static XPathException newEx(String message, boolean typeError, String code, XPathContext context) {
		XPathException err = new XPathException(message);
		err.setErrorCode(code);
		err.setXPathContext(context);
		err.setIsTypeError(typeError);
		return err;
	}

	/**
	 * For classes extending {@link Expression}
	 * @param message
	 * @param typeError
	 * @param code
	 * @param expression
	 * @return
	 */
	public static XPathException newEx(String message, boolean typeError, String code, Expression expression) {
		XPathException err = new XPathException(message);
		err.setIsTypeError(typeError);
		err.setErrorCode(code);
		err.setLocator(expression);
		return err;
	}

	/**
	 * For classes extending {@link net.sf.saxon.expr.parser.ExpressionTool}
	 * @param message
	 * @param typeError
	 * @param code
	 * @return
	 */
	public static XPathException newEx(String message, boolean typeError, String code){
		XPathException err = new XPathException(message);
		err.setErrorCode(code);
		err.setIsTypeError(typeError);
		return err;
	}

}
