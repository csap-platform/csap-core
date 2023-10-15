package org.csap.alerts ;

import org.csap.integations.micrometer.CsapMicroRegistryConfiguration ;

public enum AlertFields {
	undefined( "undefined" ), pending( "pendingFirstInterval" ),
	healthy( "isHealthy" ),
	uptime( "uptime-seconds" ),
	collectionCount( "collectionCount" ), limitsExceeded( "limitsExceeded" ),
	lastCollected( "lastCollected" ),

	bucketMaxMs( "bucket-max-ms" ),
	bucketMeanMs( "bucket-" + CsapMicroRegistryConfiguration.MEAN_PERCENTILE + "-ms" ),
	totalMs( "total-ms" ),
	meanMs( "mean-ms" ),
	count( "count" ),

	;

//	public static final String	BUCKET_MAX_MS	= "bucket-max-ms" ;
//	public static final String	BUCKET_MEAN_MS	= "bucket-" + MEAN_PERCENTILE + "-ms" ;
//
//	public static final String	TOTAL_MS		= "total-ms" ;
//
//	public static final String	MEAN_MS			= "mean-ms" ;
//
//	public static final String	COUNT			= "count" ;
	public String json ;

	private AlertFields ( String jsonField ) {

		this.json = jsonField ;

	}

	public String jsonKey ( ) {

		return json ;

	}
}