package org.csap.agent.model ;

import java.util.HashMap ;
import java.util.LinkedHashMap ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;

public enum ClusterType {

	// k8 and later
	SIMPLE("simple", "Simple: host assignment"),
	MODJK("modjk", "Apache httpd: host with optional modjk clustering"),
	KUBERNETES("kubernetes", "Kubernetes Services"),
	KUBERNETES_PROVIDER("kubernetes-provider", "Kubernetes Provider"),

	// legacy
	ENTERPRISE("multiVm", "(legacy) Enterprise Loadbalancing"),
	SHARED_NOTHING("singleVmPartition", "(legacy)  Shared Nothing"),
	MULTI_SHARED_NOTHING("multiVmPartition", "(legacy)  Shared Nothing (Multi host)"),
	unknown("unknown", "Unknown");

	static final Logger logger = LoggerFactory.getLogger( ClusterType.class ) ;
	public static final String CLUSTER_TYPE = "clusterType" ;

	private String json ;
	private String description ;

	private ClusterType ( String json, String description ) {

		this.json = json ;
		this.description = description ;

	}

	public String getJson ( ) {

		return json ;

	}

	public String getDescription ( ) {

		return description ;

	}

	public static ClusterType getPartitionType ( JsonNode node ) {

		return getPartitionType( node, "description not provided" ) ;

	}

	public static ClusterType getPartitionType ( JsonNode node , String description ) {

		if ( ! node.at( "/type" ).isMissingNode( ) ) {

			// new format
			String nodeType = node.at( "/type" ).asText( ) ;

			for ( ClusterType type : values( ) ) {

				if ( type.getJson( ).equals( nodeType ) ) {

					return type ;

				}

			}

		}

		if ( logger.isDebugEnabled( ) ) {

			logger.warn( "Legacy cluster definition: '{}' - '{}', update: {} {}", node, description,
					CSAP.buildCsapStack( new Exception( "invokation path for locating" ) ) ) ;

		} else {

			logger.warn( "Unable to determine type - definition: '{}' - description: '{}' ", node, description ) ;

		}

		return ClusterType.unknown ;

	}

	public static HashMap<String, String> clusterEntries ( ) {

		HashMap<String, String> clusterMap = new LinkedHashMap<>( ) ;

		for ( ClusterType type : values( ) ) {

			if ( type == unknown )
				continue ;
			clusterMap.put( type.getJson( ), type.getDescription( ) ) ;

		}

		return clusterMap ;

	}
}
