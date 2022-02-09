/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.api ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.stream.Collectors ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.CsapMonitor ;
import org.csap.agent.ApplicationConfiguration ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.CsapTemplates ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.HealthManager ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.services.ServiceCommands ;
import org.csap.agent.services.ServiceOsManager ;
import org.csap.agent.ui.editor.DefinitionRequests ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.security.CsapUser ;
import org.csap.security.SpringAuthCachingFilter ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Qualifier ;
import org.springframework.http.MediaType ;
import org.springframework.ldap.NameNotFoundException ;
import org.springframework.ldap.core.LdapTemplate ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.client.RestTemplate ;

import com.fasterxml.jackson.core.JsonGenerationException ;
import com.fasterxml.jackson.databind.JsonMappingException ;
import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
@RestController
@CsapMonitor ( prefix = "api.application" )
@RequestMapping ( {
		CsapConstants.API_URL + "/application"
} )
@CsapDoc ( title = "/api/application/*: apis for querying data aggregated across all hosts." , type = CsapDoc.PUBLIC , notes = {
		"CSAP Application Performance APis provide access to the runtime data aggregated across all hosts.",
		"For direct access to CSAP performance collections - refer to <a class='simple' href='class?clazz=org.csap.agent.input.http.api.Runtime_Host'>Host Apis</a> ",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />",
		"Note: unless otherwise stated - these apis can only be executed on CSAP Admin Service instances. Typically: https://yourApp/admin"
} )
public class ApplicationApi {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	CsapEvents csapEventClient ;

	ServiceCommands serviceCommands ;

	private CsapApis csapApis ;
	private ObjectNode managerError ;

	public ApplicationApi ( CsapApis csapApis, CsapEvents csapEventClient, ServiceCommands serviceCommands ) {

		this.csapApis = csapApis ;
		this.csapEventClient = csapEventClient ;
		this.serviceCommands = serviceCommands ;

	}

	{

		managerError = jacksonMapper.createObjectNode( ) ;
		managerError.put( "error", "Only permitted on admin nodes" ) ;

	}

	@CsapDoc ( notes = {
			"Validates the application definition, returning any errors or warnings. ",
			"Optionally specify useCurrent for the definition and release package to find any warning in the ",
			"currently loaded definition."
	} , linkTests = {
			"definition validate"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "definition=useCurrent," + CsapConstants.PROJECT_PARAMETER + "=useCurrent"
	} )
	@PostMapping ( value = "/definition/validate" )
	public ObjectNode definitionValidate (
											@RequestParam ( "definition" ) String definition ,
											@RequestParam ( value = CsapConstants.PROJECT_PARAMETER ) String csapProjectName ,
											@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
											@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass )
		throws Exception {

		logger.info( "validate by user: {}", userid ) ;

		if ( definition.equals( "useCurrent" ) ) {

			definition = csapApis.application( ).getRootProject( ).getSourceDefinition( ).toString( ) ;

		}

		if ( csapProjectName.equals( "useCurrent" ) ) {

			csapProjectName = csapApis.application( ).getRootProject( ).getName( ) ;

		}

		logger.debug( "{} model contains: {}", csapProjectName, definition ) ;

		return csapApis.application( ).checkDefinitionForParsingIssues( definition, csapProjectName ) ;

	}

	@Autowired ( required = false )
	DefinitionRequests definitionRequests ;

	@CsapDoc ( notes = {
			"Reloads the Application definition from source control system",
			"Note: if run on agent - only agent will reload. If run on Admin - all hosts will be reloaded"
	} , linkTests = {
			"def reload"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "branch=trunk"
	} )
	@PostMapping ( value = "/definition/reload" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String definitionReload (
										@RequestParam ( value = "branch" ) String branch ,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
										HttpServletResponse response )
		throws Exception {

		logger.info( "Reloading by user: {}", userid ) ;

		String results = definitionRequests
				.application_reload( userid, inputPass, branch, "HostCommand" )
				.get( C7.response_plain_text.val( ) )
				.asText( ) ;

		return results ;

	}

	// @CsapDoc ( notes = "Summary of Application health. Optional params include
	// alertLevel (eg 1.5 = 150% of setting) and project"
	// )
	@CsapDoc ( notes = {
			"Application Health. By default a summary listing of any health issues is shown",
			"optional parameter: alertLevel - 1.5 = 150% of setting in definition",
			"optional parameter: includeDetails - default false. ",
			"optional parameter: project - filter by csap project"
	} , //
			linkTests = {
					"summary",
					"details",
					"project"
			} , //
			linkGetParams = {
					"alertLevel=0.8,details=false",
					"alertLevel=0.8,details=true",
					"alertLevel=0.8,details=true,project=changeMe"
			} )

	@GetMapping ( "/health" )
	public ObjectNode health (
								@RequestParam ( defaultValue = "0.99" ) double alertLevel ,
								@RequestParam ( defaultValue = "true" ) boolean details ,
								@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		ObjectNode healthJson = jacksonMapper.createObjectNode( ) ;

		Project requestedPackage = csapApis.application( ).getProject( csapProjectName ) ;

		var includeKubernetesCheck = false ;

		if ( csapApis.application( ).isAdminProfile( ) ) {

			includeKubernetesCheck = true ; // detects k8s crashed processes

		}

		ObjectNode reportNode = csapApis.application( ).healthManager( ).build_health_report( alertLevel,
				includeKubernetesCheck,
				requestedPackage ) ;

		if ( reportNode.path( "summary" ).size( ) == 0 ) {

			healthJson.put( "Healthy", true ) ;

		} else {

			healthJson.put( "Healthy", false ) ;

			if ( details ) {

				healthJson.set( HealthManager.HEALTH_DETAILS, reportNode.path( HealthManager.HEALTH_DETAILS ) ) ;

			} else {

				healthJson.set( HealthManager.HEALTH_SUMMARY, reportNode.path( HealthManager.HEALTH_SUMMARY ) ) ;

			}

		}

		return healthJson ;

	}

	private ObjectNode getAllServices ( ) {

		ObjectNode response = jacksonMapper.createObjectNode( ) ;

		response.put( "info", "add service name to url" ) ;
		response.set( "availableServices", jacksonMapper.convertValue(
				csapApis.application( ).getActiveProject( ).getAllPackagesModel( ).getServiceNamesInLifecycle( ),
				ArrayNode.class ) ) ;
		return response ;

	}

	private ObjectNode getAllPackages ( String specifiedPackage ) {

		ObjectNode response = jacksonMapper.createObjectNode( ) ;

		response.put( "info", "invalid project: " + specifiedPackage ) ;
		response.set( "projects", jacksonMapper.convertValue(
				csapApis.application( ).getPackageNames( ),
				ArrayNode.class ) ) ;
		return response ;

	}

	@CsapDoc ( notes = {
			"get the last collected health report for services support health report api",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} )
	@GetMapping ( value = "/health/reports" )
	public JsonNode serviceHealthReports (
											@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		return csapApis.application( ).getHostStatusManager( ).getAllAlerts( ) ;

	}

	@CsapDoc ( notes = {
			"Application Collection: the latest runtime collection from agent(s)",
			"Optional Parameter: summary - default true. show the summarized collect results or the full",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"collection summary",
			"collection full",
			"project Filter"
	} , linkGetParams = {
			"summary=true", "summary=false", "project=changeMe"
	} )
	@GetMapping ( value = "/collection" )
	public JsonNode collection (
									@RequestParam ( defaultValue = "true" ) boolean summary ,
									@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		if ( csapApis.application( ).isAdminProfile( ) ) {

			if ( summary ) {

//				var x = jacksonMapper.createObjectNode( ) ;
//				x.get( "missing" ).asBoolean( ) ; //cause exception
				return csapApis.application( ).getHostStatusManager( ).buildCollectionSummaryReport( model ) ;

			} else {

				Map<String, ObjectNode> runtimeSummary = csapApis.application( ).getHostStatusManager( ).hostsRuntime(
						model
								.getHostsCurrentLc( ) ) ;
				return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class ) ;

			}

		} else {

			Map<String, ObjectNode> hostToAgentReport = new HashMap<>( ) ;
			hostToAgentReport.put( csapApis.application( ).getCsapHostName( ), (ObjectNode) csapApis.osManager( )
					.getHostRuntime( ) ) ;

			if ( summary ) {

				return csapApis.application( ).healthManager( ).buildCollectionSummaryReport( hostToAgentReport,
						csapProjectName ) ;

			} else {

				return jacksonMapper.convertValue( hostToAgentReport, ObjectNode.class ) ;

			}

		}

	}

	@Inject
	ServiceOsManager serviceManager ;

	@CsapDoc ( notes = {
			"Deployment backlog on host(s)",
			"Optional Parameter: project may be specified to filter results. Default is all packages",
			"Optional Parameter: isTotal will return only the total"
	} , linkTests = {
			"All Projects",
			"total only",
			"project Filter"
	} , linkGetParams = {
			"no=filter", "isTotal=true", "project=changeMe"
	} )
	@GetMapping ( value = "/deployment/backlog" )
	public JsonNode deploymentBacklog (
										HttpServletRequest request ,
										@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName ,
										boolean isTotal )
		throws Exception {

		var summary = jacksonMapper.createObjectNode( ) ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			var agentBacklog = serviceManager.getJobStatus( ) ;

			if ( isTotal ) {

				summary.put( "backlog", agentBacklog.path( "queue" ).size( ) ) ;
				return summary ;

			} else {

				return agentBacklog ;

			}

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;
		var adminBacklogReport = csapApis.application( ).getHostStatusManager( ).hostsBacklog( model
				.getHostsCurrentLc( ), request
						.getContextPath( ) + "/api/application/service/jobs/" ) ;

		JsonNode result ;

		if ( isTotal ) {

			// force update to get latest count
			csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null ) ;
			var totalBacklogCount = CSAP.jsonStream( adminBacklogReport )
					.map( hostReport -> (ObjectNode) hostReport )
					.mapToInt( hostReport -> hostReport.path( "total-backlog" ).asInt( ) )
					.sum( ) ;
			summary.put( "backlog", totalBacklogCount ) ;
			result = summary ;

		} else {

			result = adminBacklogReport ;

		}

		return result ;

	}

	@CsapDoc ( notes = {
			"Get the number of jobs (start, stop, deploy) queued for execution."
	} , linkTests = {
			"Show Jobs"
	} )
	@GetMapping ( "/service/jobs/{host}" )
	public JsonNode serviceJobStatus ( @PathVariable String host )
		throws Exception {

		JsonNode hostJobReport ;
		;

		if ( csapApis.application( ).isAdminProfile( ) ) {

			var requestParameters = new LinkedMultiValueMap<String, String>( ) ;
			var hostJobReports = serviceManager.remoteAgentsGet( List.of( host ), "/api/agent/service/jobs",
					requestParameters ) ;

			var remoteReport = hostJobReports.path( host ) ;

			if ( remoteReport.has( "host" ) ) {

				hostJobReport = remoteReport ;

			} else {

				var errorReport = jacksonMapper.createObjectNode( ) ;

				errorReport.put( "host", host ) ;
				errorReport.put( "connectionFailure", true ) ;
				errorReport.putArray( "queue" ) ;

				errorReport.set( "error", remoteReport ) ;

				hostJobReport = errorReport ;

			}

		} else {

			hostJobReport = serviceManager.getJobStatus( ) ;

		}

		return hostJobReport ;

	}

	@CsapDoc ( notes = {
			"the latest runtime collection from agents, filted by host attributes",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"project Filter"
	} , linkGetParams = {
			"no=filter", "project=changeMe"
	} )
	@GetMapping ( value = "/collection/hosts" )
	public JsonNode collectionHosts (
										@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		Map<String, ObjectNode> runtimeSummary = csapApis.application( ).getHostStatusManager( ).hostsInfo( model
				.getHostsCurrentLc( ) ) ;

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class ) ;

	}

	public final static String SERVICES_COLLECTION_URI = "/collection/hosts/services" ;

	@CsapDoc ( notes = {
			"the latest runtime collection from agents, filted by service attributes",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"project Filter"
	} , linkGetParams = {
			"no=filter", "project=changeMe"
	} )
	@GetMapping ( value = SERVICES_COLLECTION_URI )
	public JsonNode collectionHostsServices (
												@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		return csapApis.application( ).getHostStatusManager( ).serviceCollectionReport( model.getHostsCurrentLc( ),
				null, null ) ;

	}

	@CsapDoc ( notes = {
			"the latest service related data from agents, filtered by serviceName",
			"param: serviceNameFilter path parameter matches against serviceName_port. Note: wildCards are supported eg. csap-agent.*",
			"Optional: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All Packages, filtered by csap-agent",
			"project Filter"
	} , linkGetParams = {
			"serviceNameFilter=csap-agent", "project=changeMe,serviceNameFilter=csap-agent"
	} )
	@GetMapping ( value = "/collection/hosts/services/{serviceNameFilter}" )
	public JsonNode collectionHostsServicesFiltered (
														@PathVariable ( "serviceNameFilter" ) String serviceNameFilter ,
														@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		return csapApis.application( ).getHostStatusManager( ).serviceCollectionReport( model.getHostsCurrentLc( ),
				serviceNameFilter,
				null ) ;

	}

	@CsapDoc ( notes = {
			"the latest service related data from agents, filtered by serviceName and attribute",
			"param: serviceNameFilter path parameter matches against serviceName_port. Note: wildCards are supported eg. csap-agent.*",
			"param: attributeName path parameter will only return specified attribute: running, threadCount, topCpu, fileCount, socketCount,...",
			"Optional: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All Packages, filtered by csap-agent threadCount",
			"project Filter"
	} , linkGetParams = {
			"serviceNameFilter=csap-agent,attributeName=threadCount",
			"project=changeMe,serviceNameFilter=csap-agent,attributeName=threadCount"
	} )
	@GetMapping ( value = "/collection/hosts/services/{serviceNameFilter}/{attributeName}" )
	public JsonNode collectionHostsServicesAttributeFiltered (
																@PathVariable ( "serviceNameFilter" ) String serviceNameFilter ,
																@PathVariable ( "attributeName" ) String attributeName ,
																@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		return csapApis.application( ).getHostStatusManager( ).serviceCollectionReport( model.getHostsCurrentLc( ),
				serviceNameFilter,
				attributeName ) ;

	}

	@CsapDoc ( notes = {
			"the number of running instances as of the latest collection",
			"path param: serviceNameFilter - matches against serviceName_port. Note: wildCards are supported eg. csap-agent.*",
			"request param: blocking - a synchronous blocking request to all hosts to refresh collections",
			"Optional: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All Packages, running csap-agent count",
			"project Filter"
	} , linkGetParams = {
			"serviceNameFilter=csap-agent,blocking=true",
			"project=changeMe,serviceNameFilter=csap-agent"
	} )
	@GetMapping ( value = "/services/running/{serviceNameFilter}" )
	public long servicesRunning (
									@PathVariable ( "serviceNameFilter" ) String serviceNameFilter ,
									boolean blocking ,
									@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return -1 ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return -1 ;

		}

		Project requestedProject = csapApis.application( ).getProject( csapProjectName ) ;

		if ( blocking ) {

			csapApis.application( ).getHostStatusManager( ).refreshAndWaitForComplete( null ) ;

		}

		var report = csapApis.application( ).getHostStatusManager( ).serviceCollectionReport( requestedProject
				.getHostsCurrentLc( ),
				serviceNameFilter,
				"running" ) ;

		var runningFields = report.findValues( "running" ) ;
		logger.debug( "runningFields: {}", runningFields ) ;

		var numRunning = runningFields.stream( )
				.filter( running -> running.path( 0 ).asBoolean( ) )
				.count( ) ;

		return numRunning ;

	}

	@Autowired
	ApplicationConfiguration coreService ;

	@GetMapping ( "/scorecard/versions" )
	public JsonNode scoreCardVersions ( ) {

		ObjectNode scoreCardVersions = jacksonMapper.createObjectNode( ) ;

		scoreCardVersions.put( "csap", coreService.getMinVersionCsap( ) ) ;
		scoreCardVersions.put( "kubelet", coreService.getMinVersionKubelet( ) ) ;
		scoreCardVersions.put( C7.definitionSettings.val( ), coreService.getMinVersionDocker( ) ) ;

		return scoreCardVersions ;

	}

	@CsapDoc ( notes = {
			"the latest cpu related data from agents",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"project Filter"
	} , linkGetParams = {
			"no=filter", "project=changeMe"
	} )
	@GetMapping ( value = "/collection/hosts/cpu" )
	public JsonNode collectionHostsCpu (
											@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		Map<String, ObjectNode> runtimeSummary = csapApis.application( ).getHostStatusManager( ).hostsCpuInfo( model
				.getHostsCurrentLc( ) ) ;

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class ) ;

	}

	@CsapDoc ( notes = {
			"get the last collected values for Application Summary Statistcs",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"project Filter"
	} , linkGetParams = {
			"no=filter", "project=changeMe"
	} )
	@GetMapping ( value = "/realTimeMeters" )
	public JsonNode realTimeMeters (
										@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName )
		throws Exception {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		ArrayNode metersJson = jacksonMapper.createArrayNode( ) ;

		metersJson = csapApis.application( ).rootProjectEnvSettings( ).getRealTimeMeters( ).deepCopy( ) ;
		Project model = csapApis.application( ).getProject( csapProjectName ) ;

		csapApis.application( ).getHostStatusManager( ).updateRealTimeMeters( metersJson, model.getHostsCurrentLc( ),
				null ) ;

		return metersJson ;

	}

	@CsapDoc ( notes = "Retrieves the url with the lowest cpu load, but is in service" , linkTests = {
			CsapConstants.AGENT_NAME,
			"list"
	} , linkGetParams = {
			"serviceName=csap-agent"
	} )
	@GetMapping ( "/service/url/lowestLoad/{serviceName}" )
	public JsonNode serviceUrlWithLowLoad (
											@PathVariable ( "serviceName" ) String serviceName ) {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		return urlByLowestResource( serviceName, "/hostStats/cpuLoad" ) ;

	}

	@CsapDoc ( notes = "Retrieves the url with the lowest cpu usage, but is in service" , linkTests = {
			CsapConstants.AGENT_NAME,
			"list"
	} , linkGetParams = {
			"serviceName=csap-agent"
	} )
	@GetMapping ( value = "/service/url/lowestCpu/{serviceName}" )
	public JsonNode serviceUrlWithLowCpu (
											@PathVariable ( "serviceName" ) String serviceName ) {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		return urlByLowestResource( serviceName, "/hostStats/cpu" ) ;

	}
	//
	// @GET
	// @Path("/service/url/low/{serviceName}")
	// @Produces(MediaType.APPLICATION_JSON)

	@CsapDoc ( notes = {
			"For specified service, retrieves urls for active instances",
			"Lowest CPU load, and lowest CPU activity urls will also be noted "
	} , linkTests = {
			CsapConstants.AGENT_NAME,
			"list"
	} , linkGetParams = {
			"serviceName=csap-agent"
	} )
	@GetMapping ( value = "/service/url/low/{serviceName}" )
	public JsonNode serviceUrlsWithLowItems (
												@PathVariable ( "serviceName" ) String serviceName ) {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		ObjectNode urlNode = jacksonMapper.createObjectNode( ) ;
		urlNode.put( "lowCpu", urlByLowestResource( serviceName, "/hostStats/cpu" ).at( "/url" ).asText( ) ) ;
		urlNode.put( "lowLoad", urlByLowestResource( serviceName, "/hostStats/cpuLoad" ).at( "/url" ).asText( ) ) ;
		urlNode.set( "active", serviceUrlsFilteredByActiveState( serviceName, Application.ALL_PACKAGES ) ) ;

		return urlNode ;

	}

	@CsapDoc ( notes = {
			"get service urls for the specified service that are marked as running.",
			"Optional Parameter: project may be specified to filter results. Default is all packages"
	} , linkTests = {
			CsapConstants.AGENT_NAME,
			"csap-agent with project",
			"list services"
	} , linkGetParams = {
			"serviceName=csap-agent",
			"serviceName=csap-agent,project=changeMe"
	} )
	@GetMapping ( value = "/service/urls/active/{serviceName}" )
	public JsonNode serviceUrlsFilteredByActiveState (
														@PathVariable ( "serviceName" ) String serviceName ,
														@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , required = false , defaultValue = Application.ALL_PACKAGES ) String csapProjectName ) {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( serviceName.equals( "{serviceName}" ) ) {

			return getAllServices( ) ;

		}

		if ( ! csapApis.application( ).getPackageNames( ).contains( csapProjectName )
				&& ! csapProjectName.equals( Application.ALL_PACKAGES ) ) {

			return getAllPackages( csapProjectName ) ;

		}

		List<String> urls = csapApis.application( )
				.getActiveServiceInstances( csapProjectName, serviceName )
				.map( serviceInstance -> serviceInstance.getUrl( ) )
				.collect( Collectors.toList( ) ) ;

		return jacksonMapper.convertValue( urls, ArrayNode.class ) ;

	}

	//
	private ObjectNode urlByLowestResource ( String serviceName , String attributePath ) {

		List<String> hosts = csapApis.application( )
				.getServiceInstances( Application.ALL_PACKAGES, serviceName )
				.filter( csapApis.application( )::isRunningInLatestCollection )
				.map( serviceInstance -> serviceInstance.getHostName( ) )
				.collect( Collectors.toList( ) ) ;

		if ( hosts.size( ) == 0 ) {

			logger.debug( "No inservice hosts for service found: {}", serviceName ) ;
			throw new RuntimeException( "No inservice hosts for service found: " + serviceName ) ;

		}

		logger.debug( "{} with service running", hosts ) ;

		String lowHost = csapApis.application( ).findHostWithLowestAttribute( hosts, attributePath ) ;

		String lowUrl = csapApis.application( )
				.getServiceInstances( Application.ALL_PACKAGES, serviceName )
				.filter( serviceInstance -> serviceInstance.getHostName( ).equals( lowHost ) )
				.findFirst( )
				.map( serviceInstance -> serviceInstance.getUrl( ) )
				.get( ) ;

		ObjectNode response = jacksonMapper.createObjectNode( ) ;
		response.put( "selector", attributePath ) ;
		response.put( "url", lowUrl ) ;

		return response ;

	}

	@Autowired ( required = false )
	LdapTemplate ldapTemplate ;

	@Autowired ( required = false )
	CsapSecuritySettings csapSecuritySettings ;

	@SuppressWarnings ( "unchecked" )
	@CsapDoc ( notes = "Get identity attributes for userid. Optional request parameters: full (shows more data)" , linkTests = {
			"someDeveloper", "someDeveloper:full", "someDeveloper:jsonp"
	} , linkGetParams = {
			"userid=someDeveloper",
			"userid=someDeveloper,full=full",
			"userid=someDeveloper,callback=myTestFunction"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE,
			"application/javascript"
	} )
	@GetMapping ( "/access/{userid}" )
	public JsonNode userAccess (
									@PathVariable ( "userid" ) String userid ,
									@RequestParam ( value = "full" , required = false , defaultValue = "false" ) String full )
		throws Exception {

		if ( userid.equalsIgnoreCase( "default" ) ) {

			userid = CsapUser.getContextUser( ) ;

		}

		ObjectNode userInfo ;
		// userInfo = findUsingLdap( userid, full, userInfo ) ;

		String restUrl = csapApis.application( ).rootProjectEnvSettings( )
				.getEventApiUrl( ) + "/latest?&category="
				+ CsapEvents.CSAP_ACCESS_CATEGORY + "/" + userid ;

		try {

			userInfo = csapEventsService.getForObject( restUrl, ObjectNode.class ) ;
			userInfo.put( "source", restUrl ) ;

		} catch ( Exception e ) {

			userInfo = jacksonMapper.createObjectNode( ) ;
			userInfo.put( "error", "Did not find user" ) ;
			logger.error( "Failed getting user info from url: {} {}", restUrl, CSAP.buildCsapStack( e ) ) ;
			userInfo.put( "url", restUrl ) ;

		}

		return userInfo ;

	}

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService ;

	private ObjectNode findUsingLdap ( String userid , String full , ObjectNode userInfo ) {

		try {

			// CsapUser csapUser = (CsapUser) ldapTemplate.lookup(dn, new
			// CsapUser() );
			long start = System.currentTimeMillis( ) ;

			// Attribute filter cuts call time in half, and data size by 90%.
			// Default is to use the filter
			CsapUser csapUser ;

			if ( full.equals( "false" ) ) {

				logger.debug( "Doing  CsapUser.PRIMARY_ATTRIBUTES  retrieval" ) ;

				try {

					csapUser = (CsapUser) ldapTemplate.lookup(
							csapSecuritySettings.getRealUserDn( userid ), CsapUser.PRIMARY_ATTRIBUTES,
							new CsapUser( ) ) ;

				} catch ( NameNotFoundException e ) {

					// Try generic tree as well
					csapUser = (CsapUser) ldapTemplate.lookup(
							csapSecuritySettings.getProvider( ).getGenericUserDn( userid ),
							CsapUser.PRIMARY_ATTRIBUTES,
							new CsapUser( ) ) ;

				}

				userInfo = jacksonMapper.convertValue( csapUser, ObjectNode.class ) ;

			} else {

				logger.debug( "Doing a full attribute retrieval" ) ;

				try {

					csapUser = (CsapUser) ldapTemplate.lookup(
							csapSecuritySettings.getRealUserDn( userid ),
							new CsapUser( ) ) ;

				} catch ( NameNotFoundException e ) {

					logger.debug( "Looking on the generic tree" ) ;
					csapUser = (CsapUser) ldapTemplate.lookup(
							csapSecuritySettings.getProvider( ).getGenericUserDn( userid ),
							new CsapUser( ) ) ;

				}

				userInfo = jacksonMapper.convertValue( csapUser, ObjectNode.class ) ;

			}

			long len = System.currentTimeMillis( ) - start ;
			logger.debug( "Person: time {} csapUser: \n\t{}", len, csapUser.toString( ) ) ;

		} catch ( Exception e ) {

			userInfo.put( "error", "Did not find user: " + e.getMessage( ) ) ;
			logger.error( "Failed LDAP {}", CSAP.buildCsapStack( e ) ) ;

		}

		return userInfo ;

	}

	@SuppressWarnings ( "unchecked" )
	@CsapDoc ( notes = "Get the full names for specified array of userids, with optional support for JSONP" , linkTests = {
			"users",
			"users:jsonp"
	} , linkGetParams = {
			"userid=someDeveloper,userid=nonExist",
			"userid=user-id-1,userid=user-id-2,callback=myFunctionCall"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE,
			"application/javascript"
	} )
	@GetMapping ( "/userNames" )
	public JsonNode userNames (
								@RequestParam ( value = "userid" , required = true ) List<String> userids ,
								@RequestParam ( value = "callback" , required = false , defaultValue = "false" ) String callback )
		throws Exception {

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		userids.forEach( userid -> {

			CsapUser csapUser ;

			try {

				csapUser = (CsapUser) ldapTemplate.lookup(
						csapSecuritySettings.getRealUserDn( userid ),
						CsapUser.PRIMARY_ATTRIBUTES,
						new CsapUser( ) ) ;

				resultNode.put( userid, csapUser.getFullName( ) ) ;

			} catch ( Exception e ) {

				resultNode.put( userid, "Failed to retrieve from directory" ) ;

			}

		} ) ;

		logger.debug( "List of resultNode: {}", resultNode.toString( ) ) ;

		return resultNode ;

	}

	public static final String USERID_PASS_PARAMS = "userid=your-user-id,pass=blank," ;

	// look for all hosts with matching cluster name
	private List<String> findHosts ( String projectName , String simpleClusterName , ServiceInstance serviceInstance ) {

		// String cluster = csapApis.application().getCsapHostEnvironmentName() +
		// ProjectLoader.ENVIRONMENT_CLUSTER_DELIMITER + simpleClusterName ;

		List<String> hosts = csapApis.application( ).getProject( projectName )
				.getClustersToHostMapInCurrentLifecycle( )
				.get( simpleClusterName ) ;

		logger.debug( "{} serviceName_port: {}, \n {}", serviceInstance.toSummaryString( ), hosts,
				csapApis.application( ).getProject( projectName ).getLifeClusterToHostMap( ) ) ;

		if ( serviceInstance.is_cluster_kubernetes( ) ) {

			hosts = List.of( serviceInstance.getKubernetesPrimaryMaster( ) ) ;

		}

		if ( hosts == null ) {

			hosts = List.of( ) ;

		}

		// logger.info("aggregateList: " + aggregateList);
		return hosts ;

	}

	@CsapDoc ( notes = {
			"/autoplay: api for applying csap autoplay files",
			"Note: Password may optionally be encypted. ",
			"Parameter: content - contents of csap-auto-play.yaml",
			"Parameter: isApply - default is test only. setting to true will apply changes to all hosts"
	} , linkTests = {
			"SimpleDemo"
	} , linkPaths = {
			"/autoplay"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "isApply=false,content=demo"
	} )
	@PostMapping ( "/autoplay" )
	public ObjectNode autoplay (
									String content ,
									boolean isApply ,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
									HttpServletRequest request )
		throws Exception {

		csapEventClient.publishUserEvent( CsapEvents.CSAP_OS_CATEGORY + "/autoplay",
				userid, "API: autoplay", "" ) ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( StringUtils.isEmpty( content ) || content.equals( "demo" ) ) {

			content = Application.readFile( CsapTemplates.demoAutoPlay.getFile( ) ) ;

		}

		ObjectNode security = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;

		var tempFolder = csapApis.application( ).csapPlatformTemp( ) ;
		var tempAutoPlay = new File( tempFolder, userid + "-auto-play.yaml" ) ;
		FileUtils.writeStringToFile( tempAutoPlay, content ) ;

		var filePath = tempAutoPlay.getAbsolutePath( ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			filePath = csapApis.application( ).stripWindowsPaths( filePath ) ;

		}

		ObjectNode autoPlayResults = definitionRequests.auto_play( isApply, filePath, request ) ;

		autoPlayResults.set( "security", security ) ;

		// wait for reloads on all hosts
		// try {
		// TimeUnit.SECONDS.sleep( 2 );
		// csapApis.application().getHostStatusManager().refreshAndWaitForComplete( null
		// ) ;
		// TimeUnit.SECONDS.sleep( 2 );
		// csapApis.application().getHostStatusManager().refreshAndWaitForComplete( null
		// ) ;
		// } catch ( Exception e ) {
		// logger.warn( "Failed to refresh host statuses" );
		// }

		return autoPlayResults ;

	}

	@CsapDoc ( notes = {
			"/service/stop: api for stopping specified service",
			"Note: Password may optionally be encypted. ",
			"Optional: apiStop - default false. if true will use simple stop of service (non kubernetes only)",
			"Optional: cluster - defaults to 1st cluster, the set of hosts to stop the service on ",
			"Optional: clean -  omit or leave blank to not delete files",
			"Optional: keepLogs -  omit or leave blank to not delete files"
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			"/service/stop"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName=csap-verify-service"
	} )
	@PostMapping ( "/service/stop" )
	public ObjectNode serviceStop (
									boolean apiStop ,
									@RequestParam ( "serviceName" ) String serviceName ,
									@RequestParam ( defaultValue = "" ) String cluster ,
									@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , defaultValue = "" ) String project ,
									@RequestParam ( defaultValue = "" ) String clean ,
									@RequestParam ( defaultValue = "" ) String keepLogs ,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
									HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		csapEventClient.publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + serviceName,
				userid, "API: stop", "Cluster: " + cluster + " clean:" + clean ) ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( StringUtils.isEmpty( project ) ) {

			project = csapApis.application( ).getActiveProjectName( ) ;

		}

		ObjectNode security = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;

		ObjectNode stopResultReport = jacksonMapper.createObjectNode( ) ;
		var serviceInstances = csapApis.application( ).serviceInstancesByName( project, serviceName ) ;

		if ( serviceInstances == null ) {

			stopResultReport.put( "info", "serviceName not found: '" + serviceName + "'" ) ;

		} else {

			if ( StringUtils.isEmpty( cluster ) ) {

				cluster = serviceInstances.get( 0 ).getCluster( ) ;

				stopResultReport
						.put( "info", "cluster was empty  setting to first found: '" + cluster + "'" ) ;

			}

			List<String> hosts = findHosts( project, cluster, serviceInstances.get( 0 ) ) ;

			if ( hosts.size( ) > 0 ) {

				ArrayList<String> services = new ArrayList<>( ) ;
				services.add( serviceName ) ;

				if ( apiStop ) {

					stopResultReport = serviceCommands.stopRemoteRequests(
							userid, services,
							hosts,
							userid, inputPass ) ;

				} else {

					stopResultReport = serviceCommands.killRemoteRequests(
							userid,
							services, hosts, clean, keepLogs,
							userid, inputPass ) ;

				}

			} else {

				stopResultReport
						.put( "error",
								"cluster did not resolve to any hosts: " + cluster ) ;

				stopResultReport
						.set( "available", csapApis.application( )
								.buildClusterReportForAllPackagesInActiveLifecycle( ) ) ;

			}

		}

		stopResultReport.set( "security", security ) ;

		return stopResultReport ;

	}

	@CsapDoc ( notes = {
			"/service/start: api for starting specified service",
			"Note: Password may optionally be encypted. ",
			"Parameter: cluster - the set of hosts to start the service on ",
			"Optional: clean - omit or leave blank to not delete files",
			"Optional: keepLogs -  omit or leave blank to not delete files",
			"Optional: deployId - start will only be issued IF deployment id specified was successful."
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			AgentApi.START_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName=csap-verify-service,cluster=base-os,commandArguments=blank,hotDeploy=blank,startClean=blank,startNoDeploy=blank,deployId=blank"
	} )
	@PostMapping ( AgentApi.START_SERVICES_URL )
	public ObjectNode serviceStart (
										@RequestParam ( "serviceName" ) String serviceName ,
										@RequestParam ( defaultValue = "" ) String cluster ,
										@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , defaultValue = "" ) String project ,

										@RequestParam ( required = false ) String commandArguments ,
										@RequestParam ( required = false ) String hotDeploy ,
										@RequestParam ( required = false ) String startClean ,
										@RequestParam ( required = false ) String noDeploy ,
										String deployId ,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass ,
										HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( StringUtils.isEmpty( project ) ) {

			project = csapApis.application( ).getActiveProjectName( ) ;

		}

		csapEventClient.publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + serviceName,
				apiUserid, "API: start", "Cluster: " + cluster + " commandArguments:" + commandArguments
						+ " hotDeploy: " + hotDeploy
						+ " startClean: " + startClean + " noDeploy:" + noDeploy ) ;

		ObjectNode startResult = jacksonMapper.createObjectNode( ) ;

		var serviceInstances = csapApis.application( ).serviceInstancesByName( project, serviceName ) ;

		if ( serviceInstances == null ) {

			startResult.put( "info", "serviceName not found: '" + serviceName + "'" ) ;

		} else {

			if ( StringUtils.isEmpty( cluster ) ) {

				cluster = serviceInstances.get( 0 ).getCluster( ) ;

				startResult
						.put( "info", "cluster was empty  setting to first found: '" + cluster + "'" ) ;

			}

			List<String> hosts = findHosts( project, cluster, serviceInstances.get( 0 ) ) ;

			if ( hosts.size( ) > 0 ) {

				ArrayList<String> services = new ArrayList<>( ) ;
				services.add( serviceName ) ;

				startResult = serviceCommands.startRemoteRequests(
						apiUserid, services, hosts,
						commandArguments,
						hotDeploy, startClean, noDeploy,
						apiUserid, apiPass, null ) ;

			} else {

				startResult
						.put( "error",
								"cluster did not resolve to any hosts: " + cluster ) ;

				startResult
						.set( "available", csapApis.application( )
								.buildClusterReportForAllPackagesInActiveLifecycle( ) ) ;

			}

		}

		ObjectNode security = (ObjectNode) request
				.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;
		startResult.set( "security", security ) ;

		return startResult ;

	}

	@CsapDoc ( notes = {
			"/service/deploy/{serviceName_port}: api for starting specified service",
			"Parameter: cluster - the set of hosts to deploy the service on ",
			"Optional: mavenId - the name of the artifact to build eg. org.csap:BootEnterprise:1.0.27:jar . 'default' will use version from application definition. ",
			"Optional: performStart - defaults to true. Setting to false will skip the stop and start. ",
			"Notes: ensure artifact is in repository prior to initiating."
					+ " stop/clean should be run prior to deploy to remove files from previous deployment."
					+ " Depending on the size of the artifact being deployed, time will vary from 30s to several minutes. "
					+ " Clustered deployments will be much faster then deploying host by host",
			"Note: Password may optionally be encypted. "
	} , linkTests = {
			"csap-verify-service"
	} , linkPaths = {
			"/service/deploy"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName=csap-verify-service,performStart=true,mavenId=default,cluster=base-os"
	} )
	@PostMapping ( "/service/deploy" )
	public ObjectNode serviceDeploy (
										@RequestParam ( "serviceName" ) String serviceName ,
										@RequestParam ( defaultValue = "" ) String cluster ,
										@RequestParam ( value = CsapConstants.PROJECT_PARAMETER , defaultValue = "" ) String project ,

										@RequestParam ( defaultValue = ServiceOsManager.MAVEN_DEFAULT_BUILD ) String mavenId ,
										@RequestParam ( defaultValue = "false" ) boolean performStart ,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid ,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass ,
										HttpServletRequest request )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		csapEventClient.publishUserEvent( CsapEvents.CSAP_USER_SERVICE_CATEGORY + "/" + serviceName,
				apiUserid, "API: deploy", "Cluster: " + cluster + " performStart" + performStart + " Maven: "
						+ mavenId ) ;

		if ( ! csapApis.application( ).isAdminProfile( ) ) {

			return managerError ;

		}

		if ( StringUtils.isEmpty( project ) ) {

			project = csapApis.application( ).getActiveProjectName( ) ;

		}

		ObjectNode deployStatus = jacksonMapper.createObjectNode( ) ;

		if ( StringUtils.isEmpty( cluster ) ) {

			var serviceInstance = csapApis.application( ).findFirstServiceInstanceInLifecycle( serviceName ) ;

			if ( serviceInstance != null ) {

				cluster = serviceInstance.getCluster( ) ;

				deployStatus
						.put( "info", "cluster was empty  setting to first found: '" + cluster + "'" ) ;

			} else {

				deployStatus
						.put( "info", "serviceName not found: '" + serviceName + "'" ) ;

			}

		}

		var serviceInstances = csapApis.application( ).serviceInstancesByName( project, serviceName ) ;

		if ( serviceInstances == null ) {

			deployStatus.put( "info", "serviceName not found: '" + serviceName + "'" ) ;

		} else {

			List<String> hosts = findHosts( project, cluster, serviceInstances.get( 0 ) ) ;

			if ( hosts.size( ) > 0 ) {

				if ( serviceCommands == null ) {

					deployStatus.put( "test-mode", true ) ;
					deployStatus.set( "hosts", jacksonMapper.convertValue( hosts, ArrayNode.class ) ) ;
					return deployStatus ;

				}

				ArrayList<String> services = new ArrayList<>( ) ;
				services.add( serviceName ) ;
				String deployId = "ms" + System.currentTimeMillis( ) ;
				deployStatus = serviceCommands.deployRemoteRequests(
						deployId,
						apiUserid, services, hosts,
						apiUserid, "notUsedByApi", "notUsedByApi",
						null, null, mavenId, null,
						null, apiUserid, apiPass ) ;

				deployStatus.put( "deployId", deployId ) ;

				if ( performStart ) {

					JsonNode startResults = serviceCommands.startRemoteRequests(
							apiUserid, services, hosts,
							null,
							null, null, null,
							apiUserid, apiPass, deployId ) ;

					logger.debug( "start results: {} ", startResults ) ;
					deployStatus.set( "startResults", startResults ) ;

				}

			} else {

				deployStatus
						.put( "error",
								"cluster did not resolve to any hosts: " + cluster ) ;

				deployStatus
						.set( "available", csapApis.application( )
								.buildClusterReportForAllPackagesInActiveLifecycle( ) ) ;

			}

		}

		if ( request != null ) {

			ObjectNode security = (ObjectNode) request
					.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE ) ;
			deployStatus.set( "security", security ) ;

		}

		return deployStatus ;

	}

	@CsapDoc ( notes = {
			"Uses active in past 60 minutes"
	} , linkTests = "default" )
	@GetMapping ( AgentApi.USERS_URL )
	public ArrayNode usersActive ( )
		throws JsonGenerationException ,
		JsonMappingException ,
		IOException {

		return csapApis.application( ).getActiveUsers( ).allAdminUsers( ) ;

	}
}
