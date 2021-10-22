/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.stats ;

import org.csap.helpers.CsapApplication ;

import com.fasterxml.jackson.databind.JsonNode ;

/**
 *
 * @author someDeveloper
 */
public enum MetricCategory {

	osShared(CsapApplication.COLLECTION_HOST),
	osProcess(CsapApplication.COLLECTION_OS_PROCESS),
	application(CsapApplication.COLLECTION_APPLICATION),
	java(CsapApplication.COLLECTION_JAVA),
	notDefined("not defined"),

	hostCount("host-count"),

	divideBy("divideBy");

	private MetricCategory ( String value ) {

		this.value = value ;

	}

	public String value ;

	private static MetricCategory fromValue ( String parsedValue ) {

		for ( MetricCategory val : MetricCategory.values( ) ) {

			if ( val.value.equals( parsedValue ) ) {

				return val ;

			}

		}

		return notDefined ;

	}

	public static MetricCategory parse ( JsonNode realTimeMeter ) {

		if ( realTimeMeter.has( "id" ) ) {

			String id = realTimeMeter.get( "id" ).asText( ) ;
			// logger.debug( "realTimeDef {}", realTimeDef );
			String[] ids = id.split( "\\." ) ;
			return MetricCategory.fromValue( ids[ 0 ] ) ;

		} else {

			return notDefined ;

		}

	}

	public String json ( ) {

		return value ;

	}

	public String serviceName ( JsonNode realTimeMeter ) {

		String[] ids = realTimeMeter.get( "id" ).asText( ).split( "\\." ) ;
		// MetricCategories performanceCategory = MetricCategories.fromValue(
		// ids[0] );

		String serviceName = null ;

		switch ( this ) {

		case java:
			String[] attributes = ids[ 1 ].split( "_" ) ;
			serviceName = attributes[ 1 ] ;
			break ;

		case osProcess:
			String[] pattr = ids[ 1 ].split( "_" ) ;
			serviceName = pattr[ 1 ] ;
			break ;

		case application:
			serviceName = ids[ 1 ] ;
			break ;

		}

		return serviceName ;

	}

	public static boolean isAllServices ( String service ) {

		if ( service == null )
			return false ;
		return service.toLowerCase( ).equals( "all" ) ;

	}
}
