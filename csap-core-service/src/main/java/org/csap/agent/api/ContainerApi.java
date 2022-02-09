package org.csap.agent.api ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.regex.Matcher ;
import java.util.regex.Pattern ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.CsapMonitor ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.container.C7 ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.csap.security.SpringAuthCachingFilter ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.annotation.Profile ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@Profile ( "agent" )
@RestController

@CsapMonitor ( prefix = "api.agent" )

@RequestMapping ( CsapConstants.API_AGENT_URL )

@CsapDoc ( title = "/api/container/*: apis for querying data collected by management agent." , type = CsapDoc.PUBLIC , notes = {
		"CSAP container apis provide access to both docker and kubernetes plugins",
		"Note: unless otherwise stated - these apis can only be executed CSAP hosts running kubernetes"
} )
public class ContainerApi {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	ObjectMapper jsonMapper ;
	CsapApis csapApis ;

	@Autowired
	public ContainerApi (
			CsapApis csapApis,
			ObjectMapper jsonMapper ) {

		this.csapApis = csapApis ;
		this.jsonMapper = jsonMapper ;

	}

	@CsapDoc ( notes = {
			"gets namespace listing in specified namespace",
			"Note: agent apis only"
	} , linkTests = {
			"all namespaces", "kube-system namespace"
	} , linkGetParams = {
			"namespace=all",
			"namespace=kube-system"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( "/kubernetes/namespaces" )
	public ArrayNode namespaces ( String namespace )
		throws Exception {

		ArrayNode namespaces = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			namespaces = csapApis.kubernetes( ).namespaceInfo( namespace ) ;

		}

		if ( namespaces.size( ) == 0 ) {

			ObjectNode msg = namespaces.addObject( ) ;
			msg.put( C7.error.val( ), "No pods deployed" ) ;

		}

		return namespaces ;

	}

	public final static String NODE_REPORT_URL = "/kubernetes/nodes" ;

	@CsapDoc ( notes = {
			"gets csap node report: aggregates metrics, allocation, and definition sources",
			"Note: agent apis only"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( NODE_REPORT_URL )
	public ArrayNode nodeReport ( String namespace )
		throws Exception {

		var nodeReport = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			nodeReport = csapApis.kubernetes( ).reportsBuilder( ).nodeReports( ) ;

		}

		if ( nodeReport.size( ) == 0 ) {

			ObjectNode msg = nodeReport.addObject( ) ;
			msg.put( C7.error.val( ), "No pods deployed" ) ;

		}

		return nodeReport ;

	}

	public final static String POD_NAMESPACE_REPORT_URL = "/pod/report/namespaces" ;

	@CsapDoc ( notes = {
			"gets namespace report showing pods started,stopped,pending,restarts",
			"Note: agent apis only"
	} , linkTests = {
			"all namespaces", "kube-system namespace"
	} , linkGetParams = {
			"namespace=all",
			"namespace=kube-system"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( POD_NAMESPACE_REPORT_URL )
	public ArrayNode podNamespaceReport ( String namespace )
		throws Exception {

		ArrayNode namespaceReport = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			namespaceReport = csapApis.kubernetes( ).reportsBuilder( ).podNamespaceSummaryReport(
					null ) ;

		}

		if ( namespaceReport.size( ) == 0 ) {

			ObjectNode msg = namespaceReport.addObject( ) ;
			msg.put( C7.error.val( ), "No pods deployed" ) ;

		}

		return namespaceReport ;

	}

	@CsapDoc ( notes = {
			"gets job listing in specified namespace",
			"Note: agent apis only"
	} , linkTests = {
			"all namespaces", "kube-system namespace"
	} , linkGetParams = {
			"namespace=all",
			"namespace=kube-system"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( "/kubernetes/jobs" )
	public ArrayNode jobs ( String namespace )
		throws Exception {

		ArrayNode jobListing = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			jobListing = csapApis.kubernetes( ).listingsBuilder( ).jobListing( namespace ) ;

		}

		if ( jobListing.size( ) == 0 ) {

			ObjectNode msg = jobListing.addObject( ) ;
			msg.put( C7.error.val( ), "No jobs deployed" ) ;

		}

		return jobListing ;

	}

	@CsapDoc ( notes = {
			"gets deployment listing in specified namespace",
			"Note: agent apis only"
	} , linkTests = {
			"all namespaces", "kube-system namespace"
	} , linkGetParams = {
			"namespace=all",
			"namespace=kube-system"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( "/kubernetes/deployments" )
	public ArrayNode deployments ( String namespace )
		throws Exception {

		ArrayNode deploymentListing = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			deploymentListing = csapApis.kubernetes( ).listingsBuilder( ).deploymentListing(
					namespace ) ;

		}

		if ( deploymentListing.size( ) == 0 ) {

			ObjectNode msg = deploymentListing.addObject( ) ;
			msg.put( C7.error.val( ), "No deployments" ) ;

		}

		return deploymentListing ;

	}

	@CsapDoc ( notes = {
			"gets pod listing in specified namespace",
			"Note: agent apis only"
	} , linkTests = {
			"all namespaces", "kube-system namespace"
	} , linkGetParams = {
			"namespace=all",
			"namespace=kube-system"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE
	} )
	@GetMapping ( "/kubernetes/pods" )
	public ArrayNode pods ( String namespace )
		throws Exception {

		ArrayNode podListing = jsonMapper.createArrayNode( ) ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			podListing = csapApis.kubernetes( ).podRawReports( namespace, null ) ;

		}

		if ( podListing.size( ) == 0 ) {

			ObjectNode msg = podListing.addObject( ) ;
			msg.put( C7.error.val( ), "No pods deployed" ) ;

		}

		return podListing ;

	}

	@CsapDoc ( notes = {
			"gets pod logs in specified namespace",
			"Notes:",
			"numberOfLines is max 1000",
			"if findFirst=true, then first pod in namespace starting with podName will be used"
	} , linkTests = {
			"kube-system calico-node 10 find first",
			"kube-system calico-nodeXXX 10 podName"
	} , linkGetParams = {
			"namespace=kube-system,podName=calico-node,numberOfLines=10,findFirst=true",
			"namespace=kube-system,podName=calico-node-asdfsadf-w43r5345-345,numberOfLines=10"
	} , produces = {
			MediaType.TEXT_PLAIN_VALUE
	} )
	@GetMapping ( value = "/kubernetes/pod/logs" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String podLogs (
							String podName ,
							String namespace ,
							int numberOfLines ,
							boolean findFirst )
		throws Exception {

		var logs = "not available" ;

		if ( csapApis.isKubernetesInstalledAndActive( ) ) {

			logs = csapApis.kubernetes( ).podLogs( namespace, podName, numberOfLines, findFirst ) ;

		}

		return logs ;

	}

	@PostMapping ( "/kubernetes/specification" )
	@CsapDoc ( notes = {
			"Create specification(s) delimited according to valid kubernetes yaml spec",
			"Note: agent api only on kubernetes hosts"
	} , linkTests = {
			"create a spec"
	} , linkPostParams = {
			AgentApi.USERID_PASS_PARAMS
					+ "yaml=yourkubernetesSpec"
	} )
	public ObjectNode specification_create (
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
												String yaml )
		throws IOException {

		issueAudit( userid, "create", yaml ) ;

		File yamlFile = csapApis.application( ).createYamlFile( "-create-", yaml, userid ) ;

		String command = "create --save-config -f " + yamlFile.getAbsolutePath( ) ;

		return csapApis.osManager( ).kubernetesCli( command, C7.response_shell ) ;

	}

	@PutMapping ( "/kubernetes/specification" )
	@CsapDoc ( notes = {
			"Update specification(s) delimited according to valid kubernetes yaml spec",
			"Note: agent api only on kubernetes hosts",
			"required parameters are application/x-www-form-urlencoded:",
			"parameter: userid - required to access and track requests, either unique id or LDAP/OATH user",
			"parameter: pass - either a agent token or associated password",
			"parameter: yaml - specification(s) to be updated",
			"<a class='csap-link' target='_blank' href='../../swagger-ui.html#/container-api'>test page</a>",
	} , linkTests = {
			"update a spec"
	} , linkPostParams = {
			AgentApi.USERID_PASS_PARAMS
					+ "yaml=yourkubernetesSpec"
	} )
	public ObjectNode specification_update (
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid ,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ,
												String yaml )
		throws IOException {

		issueAudit( userid, "apply", yaml ) ;

		logger.info( "Apply: {}", yaml ) ;

		File yamlFile = csapApis.application( ).createYamlFile( "-apply-", yaml, userid ) ;

		String command = "apply -f " + yamlFile.getAbsolutePath( ) ;

		return csapApis.osManager( ).kubernetesCli( command, C7.response_shell ) ;

	}

	// , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
	@DeleteMapping ( value = "/kubernetes/specification" )
	@CsapDoc ( notes = {
			"delete specification(s) delimited according to valid kubernetes yaml spec",
			"Note: agent api only on kubernetes hosts",
			"required parameters are application/x-www-form-urlencoded:",
			"parameter: userid - required to access and track requests, either unique id or LDAP/OATH user",
			"parameter: pass - either a agent token or associated password",
			"parameter: yaml - specification(s) to be deleted",
			"<a class='csap-link' target='_blank' href='../../swagger-ui.html#/container-api'>test page</a>",
	} , linkTests = {
			"application/x-www-form-urlencoded"
	} )
	public ObjectNode specification_delete ( String userid , String pass , String yaml )
		throws IOException {

		var result = specification_delete_alt( userid, "", yaml ) ;

		return result ;

	}

	// @PostMapping ( "/kubernetes/specification/delete" )
	// @CsapDoc ( notes = {
	// "delete specification(s) delimited according to valid kubernetes yaml spec",
	// "Note: agent api only on kubernetes hosts"
	// } , linkTests = {
	// "delete a spec"
	// } , linkPostParams = {
	// AgentApi.USERID_PASS_PARAMS
	// + "yaml=yourkubernetesSpec"
	// } )
	public ObjectNode specification_delete_alt (
													String userid ,
													String pass ,
													String yaml )
		throws IOException {

		issueAudit( userid, "delete", yaml ) ;

		logger.info( "deleting: {} ", yaml ) ;

		File yamlFile = csapApis.application( ).createYamlFile( "-delete-", yaml,
				userid ) ;

		String command = "delete -f " + yamlFile.getAbsolutePath( ) ;

		return csapApis.osManager( ).kubernetesCli( command, C7.response_shell ) ;

	}

	public void issueAudit ( String user , String commandDesc , String yaml ) {

		var summary = buildSummary( yaml ) ;
		var kind = summary.split( "," )[0] ;

		try {

			if ( kind.equals( "Job" ) ) {

				var commandFromYaml = buildJobYamlSumary( yaml ) ;
				logger.debug( "commandFromYaml: {}", commandFromYaml ) ;

				csapApis.events( ).publishEvent(
						CsapEvents.CSAP_SYSTEM_SERVICE_CATEGORY + "/kubernetes/api/job",
						commandDesc + ": " + summary,
						commandFromYaml ) ;

			} else {

				csapApis.events( ).publishUserEvent(
						"kubernetes/api/" + kind,
						user,
						commandDesc + ": " + summary, yaml ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed parsing yaml: {}", CSAP.buildCsapStack( e ) ) ;

		}

	}

	public String buildJobYamlSumary ( String yaml ) {

		Matcher yamlKeywordMatcher = yamlCommand.matcher( yaml ) ;

		var firstName = "" ;
		var firstCommand = "" ;

		while ( yamlKeywordMatcher.find( ) ) {

			if ( yamlKeywordMatcher.group( 1 ).equals( "name" )
					&& StringUtils.isEmpty( firstName ) ) {

				firstName = yamlKeywordMatcher.group( 2 ) ;

			}

			if ( yamlKeywordMatcher.group( 1 ).equals( "command" )
					&& StringUtils.isEmpty( firstCommand ) ) {

				firstCommand = yamlKeywordMatcher.group( 2 ) ;

			}

			// commandFromYaml = yamlKeywordMatcher.group( 1 ) + ": " +
			// yamlKeywordMatcher.group( 2 ) ;
			// logger.info( "commandFromYaml: {}", commandFromYaml );
		}

		var commandFromYaml = "\n name: " + firstName + "\n command: " + firstCommand ;
		return commandFromYaml ;

	}

//	private static Pattern yamlDescriptors = Pattern.compile( "(?<=(namespace|kind): )(\\S+)" ) ;
	private static Pattern yamlDescriptors = Pattern.compile( "(?<=(namespace|kind): )(.*)" ) ;
	private static Pattern yamlCommand = Pattern.compile( "(?<=(command|name): )(.*)" ) ;

	public String buildSummary ( String yaml ) {

		Matcher yamlKeywordMatcher = yamlDescriptors.matcher( yaml ) ;

		var matches = new ArrayList<String>( ) ;

		var firstKind = "" ;
		var firstNamespace = "" ;

		var numKinds = 0 ;

		while ( yamlKeywordMatcher.find( ) ) {

			matches.add( yamlKeywordMatcher.group( 1 ) + ":   " + yamlKeywordMatcher.group( 2 ) ) ;

			if ( yamlKeywordMatcher.group( 1 ).equals( "kind" ) ) {

				numKinds++ ;

				if ( StringUtils.isEmpty( firstKind ) ) {

					firstKind = yamlKeywordMatcher.group( 2 ) ;

				}

			}

			if ( yamlKeywordMatcher.group( 1 ).equals( "namespace" )
					&& StringUtils.isEmpty( firstNamespace ) ) {

				firstNamespace = yamlKeywordMatcher.group( 2 ) ;

			}

		}

		logger.debug( "matches: {}", matches ) ;

		var docMatches = "" ;

		if ( numKinds > 1 ) {

			docMatches = " (" + numKinds + " documents" + ")" ;

		}

		return firstKind + ", namespace: " + firstNamespace + docMatches ;

	}

}
