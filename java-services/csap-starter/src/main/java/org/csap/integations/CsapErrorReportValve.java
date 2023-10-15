package org.csap.integations ;

import java.io.IOException ;
import java.io.Writer ;
import java.util.regex.Matcher ;

import org.apache.catalina.connector.Request ;
import org.apache.catalina.connector.Response ;
import org.apache.catalina.valves.ErrorReportValve ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.HttpStatus ;

public class CsapErrorReportValve extends ErrorReportValve {
	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private String errorTemplate ;

	public CsapErrorReportValve ( String errorTemplate ) {

		this.errorTemplate = errorTemplate ;

	}

	protected void report ( Request request , Response response , Throwable throwable ) {

		int statusCode = response.getStatus( ) ;

		try {

			try {

				response.setContentType( "text/html" ) ;
				response.setCharacterEncoding( "utf-8" ) ;

			} catch ( Throwable t ) {

				logger.warn( "failed {}", CSAP.buildCsapStack( t ) ) ;

			}

			Writer writer = response.getReporter( ) ;

			if ( writer != null ) {

				var codeDetails = HttpStatus.valueOf( statusCode ) ;

				writer.write( errorTemplate.replaceAll(
						Matcher.quoteReplacement( "$message" ),
						"Http Response: " + codeDetails ) ) ;
				response.finishResponse( ) ;

			}

		} catch ( IOException e ) {

			// Ignore
		} catch ( IllegalStateException e ) {

			// Ignore
		}

		// int statusCode = response.getStatus();
		//
		// String message = Escape.htmlElementContent(response.getMessage());
		// if (message == null) {
		// if (throwable != null) {
		// String exceptionMessage = throwable.getMessage();
		// if (exceptionMessage != null && exceptionMessage.length() > 0) {
		// message = Escape.htmlElementContent((new
		// Scanner(exceptionMessage)).nextLine());
		// }
		// }
		// if (message == null) {
		// message = "";
		// }
		// }
		//
		// // Do nothing if there is no reason phrase for the specified status code and
		// // no error message provided
		// String reason = null;
		// String description = null;
		// StringManager smClient = StringManager.getManager(
		// Constants.Package, request.getLocales());
		// response.setLocale(smClient.getLocale());
		// try {
		// reason = smClient.getString("http." + statusCode + ".reason");
		// description = smClient.getString("http." + statusCode + ".desc");
		// } catch (Throwable t) {
		// ExceptionUtils.handleThrowable(t);
		// }
		// if (reason == null || description == null) {
		// if (message.isEmpty()) {
		// return;
		// } else {
		// reason = smClient.getString("errorReportValve.unknownReason");
		// description = smClient.getString("errorReportValve.noDescription");
		// }
		// }
		//
		// StringBuilder sb = new StringBuilder();
		//
		// sb.append( "no content" ) ;

	}

}
