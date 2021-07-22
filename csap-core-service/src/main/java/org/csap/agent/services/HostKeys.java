package org.csap.agent.services ;

import org.csap.agent.model.ContainerState ;

public enum HostKeys {

	lastCollected("lastCollected"), hostStats("hostStats"),
	unregisteredServices("unregisteredServices"),
	services("services"), containers("containers"), healthReportCollected("healthReportCollected"),
	numberSamplesAveraged("numberSamplesAveraged"),

	host_status("hostStatus");

	public String jsonId ;

	private HostKeys ( String jsonId ) {

		this.jsonId = jsonId ;

	}

	public static String servicesJsonPath ( String serviceId ) {

		return "/" + services.jsonId + "/" + serviceId ;

	}

	public static String serviceMetricJsonPath ( String serviceId , int containerIndex , String metric ) {

		return "/" + services.jsonId + "/" + serviceId + "/" + ContainerState.JSON_KEY + "/" + containerIndex + "/"
				+ metric ;

	}

	public String json ( ) {

		return jsonId ;

	}

	static public String healthCollectedJsonPath ( ) {

		return "/healthReportCollected/" ;

	}

}
