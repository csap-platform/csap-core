package org.csap.alerts ;

public enum CsapGlobalId {

	// general use
	EXCEPTION( "exception" ), NULL_EXCEPTION( "nullPointer" ),

	// csap related
	HEALTH_REPORT( "health.report-all" ), HEALTH_REPORT_PASS( "health.report-pass" ), HEALTH_REPORT_FAIL(
			"health.report-fail" ),
	UNDEFINED_ALERTS( "health.report-alert-undefined" );

	public String id ;

	private final static String PREFIX = "csap." ;

	private CsapGlobalId ( String id ) {

		this.id = PREFIX + id ;

	}
}
