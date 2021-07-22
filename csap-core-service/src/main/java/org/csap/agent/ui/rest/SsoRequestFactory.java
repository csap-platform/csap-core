package org.csap.agent.ui.rest ;

import java.io.IOException ;
import java.net.HttpURLConnection ;

import org.springframework.http.client.SimpleClientHttpRequestFactory ;

// Simple helper that supports SSO tokens for forwarding requests
public class SsoRequestFactory extends SimpleClientHttpRequestFactory {

	String cookieHeaderValue ;
	int timeoutInMs ;

	public SsoRequestFactory ( String cookieHeaderValue, int timeoutInMs ) {

		this.cookieHeaderValue = cookieHeaderValue ;
		this.timeoutInMs = timeoutInMs ;

		setReadTimeout( timeoutInMs ) ;
		setConnectTimeout( 25000 ) ;
		setBufferRequestBody( false ) ;

	}

	@Override
	protected void prepareConnection (
										HttpURLConnection connection ,
										String httpMethod )
		throws IOException {

		// if ( logger.isDebugEnabled())
		// logger.debug("Add SSO" + cookieHeaderValue + " timeout: " +
		// timeoutInMs);
		connection.setRequestProperty( "Cookie", cookieHeaderValue ) ;
		// connection.setDoOutput(dooutput)

		super.prepareConnection( connection, httpMethod ) ;

	}

	public void setSSO ( String SSO ) {

		this.cookieHeaderValue = SSO ;

	}

}