/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent ;

import java.util.function.Predicate ;
import java.util.stream.Stream ;
import java.util.stream.StreamSupport ;

import org.apache.commons.lang3.StringUtils ;

import com.fasterxml.jackson.core.JsonProcessingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 *
 * Constants shared in multiple packages or components
 * 
 * 
 * @author someDeveloper
 */
public class CsapCore {

	public static final int CSAP_MODEL_LOAD_ORDER = 1 ;
	public static final int CSAP_SERVICE_STATE_LOAD_ORDER = 2 ;
	public static final int CSAP_UI_LOAD_ORDER = 99 ;

	public static final String SAME_LOCATION = "sameLocation" ;

	public static final String AUTO_START_DISABLED_FILE = "csap-auto-start-disabled" ;
	public static final String AUTO_PLAY_FILE = "csap-auto-play.yaml" ;

	public static final String CONFIG_PARSE_ERROR = "__ERROR: " ;
	public static final String CONFIG_PARSE_WARN = "__WARN: " ;
	public static final String MISSING_SERVICE_MESSAGE = "Did not find service. " ;

	private CsapCore ( ) {

	}; // constants

	public static final String EVENTS_STUB_FOLDER = "event-stub/" ;
	public static final String EVENTS_DISABLED = "events-disabled" ;

	public static final String DOCKER_REPOSITORY = "$dockerRepository" ;

	// user defined template variables
	public static final String CSAP_USER_PREFIX = "csap_def_" ;
	public static final String CSAP_LEGACY_PREFIX = CSAP_USER_PREFIX ;

	// system defined variables
	public static final String CSAP_VARIABLE_PREFIX = "$$" ;
	public static final String KUBERNETES_INGRESS = CSAP_VARIABLE_PREFIX + "ingress_host" ;

	public static final String SYSTEM_PREFIX = CSAP_VARIABLE_PREFIX + "csap-" ;
	public static final String CSAP_BASE = SYSTEM_PREFIX + "base" ;
	public static final String CSAP_WORKING = SYSTEM_PREFIX + "working" ;
	public static final String CSAP_ENV = SYSTEM_PREFIX + "environment" ;
	public static final String SERVICE_HOSTS = SYSTEM_PREFIX + "hosts:" ;

	public static final String CSAP_AGENT_URL = SYSTEM_PREFIX + "agent-url" ;
	// public static final String CSAP_AGENT_URL = INSTALL + "agent-url" ;

	public static final String KUBERNETES = CSAP_VARIABLE_PREFIX + "kubernetes-" ;
	public static final String K8_DASHBOARD = KUBERNETES + "dashboard" ;
	public static final String K8_NODE_PORT = KUBERNETES + "nodeport" ;
	public static final String K8_INGRESS = KUBERNETES + "ingress" ;
	public static final String K8_CONFIG = KUBERNETES + "config" ;
	public static final String K8_POD_IP = KUBERNETES + "pod-ip" ;

	public static final String SERVICE = CSAP_VARIABLE_PREFIX + "service-" ;
	public static final String SERVICE_ENV = SERVICE + "environment" ;
	public static final String CSAP_DEF_WORKING = SERVICE + "working" ;
	public static final String CSAP_DEF_LOGS = SERVICE + "logs" ;
	public static final String CSAP_DEF_PARAMETERS = SERVICE + "parameters" ;
	public static final String SEARCH_RESOURCES = "SEARCH_FOR_RESOURCE:" ;
	public static final String CSAP_DEF_HOST = SERVICE + "host" ;
	public static final String CSAP_DEF_FQDN_HOST = SERVICE + "fqdn-host" ;

	public static final String CSAP_DEF_REPLICA = SERVICE + "replica-count" ;
	public static final String CSAP_DEF_NAMESPACE = SERVICE + "namespace" ;
	public static final String CSAP_DEF_IMAGE = SERVICE + "image" ;

	public static final String CSAP_DEF_PORT = SERVICE + "primary-port" ;
	public static final String CSAP_DEF_AJP_PORT = SERVICE + "ajp-port" ;
	public static final String CSAP_DEF_JMX_PORT = SERVICE + "jmx-port" ;
	public static final String CSAP_DEF_NAME = SERVICE + "name" ;
	public static final String CSAP_DEF_INSTANCE = SERVICE + "instance" ;
	public static final String CSAP_DEF_CONTEXT = SERVICE + "context" ;
	public static final String CSAP_DEF_RESOURCE = SERVICE + "resources" ;

	public static final String JMX_PARAMETER = "-DcsapJmxPort=" ;
	public static final String DOCKER_JAVA_PARAMETER = "-DcsapDockerJava" ;

	public static final String ADMIN_NAME = "csap-admin" ;

	public static final String AGENT_NAME = "csap-agent" ;

	// legacy - should replace with name
	public static final String AGENT_ID = AGENT_NAME + "_8011" ;

	public static final String DEFAULT_DOMAIN = "yourorg.org" ;
	public static final String PROJECT_PARAMETER = "project" ;
	public static final String ALL_PACKAGES = "All Packages" ;
	public static final String ROLES = "ROLES" ;
	public static final String HOST_PARAM = "hostName" ;
	public static final String SERVICE_PORT_PARAM = "serviceName" ;
	public static final String SERVICE_NOPORT_PARAM = "service" ;

	public static final long ONE_SECOND_MS = 1000 ;
	public static final long ONE_MINUTE_MS = 60 * 1000 ;

	public static final long MB_FROM_BYTES = 1024 * 1024 * 1 ;

	public static Stream<JsonNode> jsonStream ( JsonNode node ) {

		return StreamSupport.stream( node.spliterator( ), false ) ;

	}

	private static final ObjectMapper _jsonMapper = new ObjectMapper( ) ;

	public static String jsonPrint ( JsonNode j ) {

		try {

			return _jsonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( j ) ;

		} catch ( JsonProcessingException e ) {

			// TODO Auto-generated catch block
			e.printStackTrace( ) ;

		}

		return "FAILED_TO_PARSE" ;

	}

	public static String jsonPrint ( ObjectMapper jacksonMapper , JsonNode j )
		throws JsonProcessingException {

		return jacksonMapper.writerWithDefaultPrettyPrinter( ).writeValueAsString( j ) ;

	}

	public static <T> Predicate<T> not ( Predicate<T> t ) {

		return t.negate( ) ;

	}

	public static String pad ( String input ) {

		return StringUtils.rightPad( input, 25 ) ;

	}

	final static String REPLACE_SPACES = "\\s+" ;

	public static String singleSpace ( String input ) {

		return input.trim( ).replaceAll( REPLACE_SPACES, " " ) ;

	}

	public static String getDiskWithUnit ( long size ) {

		var sizeWithUnits = size + " bytes" ;

		if ( size > CsapCore.MB_FROM_BYTES ) {

			var mb = size / CsapCore.MB_FROM_BYTES ;
			sizeWithUnits = mb + " MB" ;

			if ( mb > 1024 ) {

				var gb = mb / 1024 ;
				sizeWithUnits = gb + " GB" ;

			}

		}

		return sizeWithUnits ;

	}

}
