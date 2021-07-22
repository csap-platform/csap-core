package org.csap.agent.container.kubernetes ;

import java.io.IOException ;
import java.util.ArrayList ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

import io.kubernetes.client.openapi.ApiClient ;
import io.kubernetes.client.openapi.ApiException ;
import io.kubernetes.client.openapi.Pair ;
import okhttp3.Call ;
import okhttp3.Response ;
//import com.squareup.okhttp.Call ;
//import com.squareup.okhttp.Response ;
//
//import io.kubernetes.client.ApiClient ;
//import io.kubernetes.client.Pair ;
//import io.kubernetes.client.ProgressRequestBody ;

public class ApiDirect {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	public JsonNode getJson ( String path , ApiClient apiClient , ObjectMapper jsonMapper )
		throws Exception {

		var responseString = getRawResponse( apiClient, path, null ) ;

		var nodeInfo = jsonMapper.readTree( responseString ) ;

		logger.debug( "response: {}", CSAP.jsonPrint( nodeInfo ) ) ;

		return nodeInfo ;

	}

	boolean lastSuccess = false ;

	public String getRawResponse ( ApiClient apiClient , String path , List<Pair> localVarQueryParams )
		throws ApiException ,
		IOException {

		Map<String, String> localVarHeaderParams = new HashMap<>( ) ;
		Map<String, String> localCookieParams = new HashMap<>( ) ;
		String[] localVarAuthNames = new String[] {
				"BearerToken"
		} ;
		Map<String, Object> localVarFormParams = new HashMap<>( ) ;
		Object localVarPostBody = null ;

		if ( localVarQueryParams == null ) {

			localVarQueryParams = new ArrayList<>( ) ;
			localVarQueryParams.addAll( apiClient.parameterToPair( "pretty", "false" ) ) ;
			localVarQueryParams.addAll( apiClient.parameterToPair( "timeoutSeconds", "5" ) ) ;

		}

		List<Pair> localVarCollectionQueryParams = new ArrayList<>( ) ;

		final String[] localVarAccepts = {
				"application/json", "application/yaml", "application/vnd.kubernetes.protobuf",
				"application/json;stream=watch",
				"application/vnd.kubernetes.protobuf;stream=watch"
		} ;
		final String localVarAccept = apiClient.selectHeaderAccept( localVarAccepts ) ;
		if ( localVarAccept != null )
			localVarHeaderParams.put( "Accept", localVarAccept ) ;

		final String[] localVarContentTypes = {
				"*/*"
		} ;
		final String localVarContentType = apiClient.selectHeaderContentType( localVarContentTypes ) ;
		localVarHeaderParams.put( "Content-Type", localVarContentType ) ;

//		ProgressRequestBody.ProgressRequestListener progressRequestListener = null ;
		// ApiCallback<V1ConfigMapList> = ....
		// apiClient.setDebugging( false ) ;

		Call nodeCall = apiClient.buildCall( path, "GET", localVarQueryParams,
				localVarCollectionQueryParams, localVarPostBody,
				localVarHeaderParams, localCookieParams, localVarFormParams, localVarAuthNames, null ) ;

		Response nodeResponse = nodeCall.execute( ) ;

		// apiClient.setDebugging( false ) ;
		var responseString = nodeResponse.body( ).string( ) ;

		lastSuccess = nodeResponse.isSuccessful( ) ;
		logger.debug( "response: {}", responseString ) ;
		return responseString ;

	}

	public boolean isLastSuccess ( ) {

		return lastSuccess ;

	}

	public void setLastSuccess ( boolean lastSuccess ) {

		this.lastSuccess = lastSuccess ;

	}

}
