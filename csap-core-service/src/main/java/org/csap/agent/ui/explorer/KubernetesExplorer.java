package org.csap.agent.ui.explorer ;

import java.io.File ;
import java.io.IOException ;
import java.util.ArrayList ;
import java.util.List ;
import java.util.regex.Matcher ;

import javax.inject.Inject ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.csap.agent.CsapTemplate ;
import org.csap.agent.container.DockerJson ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.security.CsapUser ;
import org.csap.security.config.CsapSecuritySettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.http.MediaType ;
import org.springframework.util.LinkedMultiValueMap ;
import org.springframework.util.MultiValueMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestBody ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( KubernetesExplorer.EXPLORER_URL + "/kubernetes" )
public class KubernetesExplorer {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	public final static String EXPLORER_URL = "/explorer" ;

	@Inject
	public KubernetesExplorer (
			Application csapApp,
			OsManager osManager,
			CsapEvents csapEventClient,
			ObjectMapper jsonMapper ) {

		this.csapApp = csapApp ;
		this.osManager = osManager ;
		this.csapEventClient = csapEventClient ;
		this.jsonMapper = jsonMapper ;

	}

	Application csapApp ;
	OsManager osManager ;

	ObjectMapper jsonMapper ;

	@Autowired ( required = false )
	CsapSecuritySettings securitySettings ;

	@Autowired ( required = false )
	KubernetesIntegration kubernetes ;

	CsapEvents csapEventClient ;

	@GetMapping ( "/events" )
	public JsonNode events ( String namespace , int maxEvents ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.eventListing( namespace, maxEvents ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No events found" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/api/providers" )
	public JsonNode apiProviders ( ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.apiProviders( ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No providers found" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/api/provider/resources" )
	public JsonNode apiProviderResources ( String path ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.apiProviderResourceTypes_listing( path ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No resources types" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/api/secrets" )
	public JsonNode apiSecrets ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).secretListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No secrets" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/api/resource" )
	public JsonNode apiResource ( String path , String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		var namespaceTarget = "" ;

		if ( StringUtils.isNotEmpty( namespace ) && ! namespace.equals( "all" ) ) {

			namespaceTarget = "/namespaces/" + namespace ;

		}

		var apiPathWithUserNamespace = path.replaceAll(
				"NAMESPACE",
				Matcher.quoteReplacement( namespaceTarget ) ) ;

		ArrayNode listing = kubernetes.apiResource_listing( apiPathWithUserNamespace, path, 500 ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No resources types" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/api/resources" )
	public JsonNode apiResources ( String path ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.apiResourceType_listing( true ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No resource types found" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/configMaps" )
	public JsonNode configMaps ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).configMapListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No config maps deployed" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/pods" )
	public JsonNode pods ( String namespace , String podName ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).podCsapListing( namespace, podName ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No pods deployed" ) ;

		}

		return listing ;

	}

	@DeleteMapping ( "/pods/{namespace}/{podName}" )
	public ObjectNode pod_delete (
									@PathVariable String namespace ,
									@PathVariable String podName )
		throws Exception {

		issueAudit( "deleting pod: " + podName + " in " + namespace, null ) ;

		return kubernetes.podDelete( podName, namespace ) ;

	}

	@GetMapping ( value = "/pods/logs/{namespace}/{podName}/{containerName}" , produces = MediaType.TEXT_HTML_VALUE )
	public void containerTailStream (
										HttpServletResponse response ,
										@PathVariable String namespace ,
										@PathVariable String podName ,
										@PathVariable String containerName ,
										@RequestParam ( defaultValue = "0" ) int numberOfLines ,
										String since ,
										boolean previous )
		throws Exception {

		issueAudit( "Downloading logs: " + containerName + " in pod " + podName + " in " + namespace, null ) ;

		kubernetes.podLogStream( response, namespace, podName, containerName, numberOfLines, since, previous ) ;

	}

	@GetMapping ( "/pods/logs/{namespace}/{podName}" )
	public ObjectNode podContainerTail (
											@PathVariable String namespace ,
											@PathVariable String podName ,
											String containerName ,
											@RequestParam ( defaultValue = "false" ) boolean previousTerminated ,
											@RequestParam ( defaultValue = "500" ) int numberOfLines ,
											@RequestParam ( defaultValue = "0" ) int since )
		throws Exception {

		return kubernetes.podContainerTail(
				namespace, podName, containerName,
				previousTerminated, numberOfLines, since ) ;

	}

	@GetMapping ( "/configuration" )
	public JsonNode configuration ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.buildSystemSummaryListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No volumes defined" ) ;

		}

		return listing ;

	}

	private ArrayNode build_not_configured_listing ( ) {

		ArrayNode listing = jsonMapper.createArrayNode( ) ;
		ObjectNode item = listing.addObject( ) ;
		item.put( DockerJson.list_label.json( ), DockerJson.error.json( ) + "Kubernetes not configured" ) ;
		item.put( "folder", false ) ;
		item.put( DockerJson.error.json( ), "Kubernetes not configured" ) ;
		return listing ;

	}

	private void issueAudit ( String commandDesc , String details ) {

		csapEventClient.publishUserEvent( "kubernetes",
				securitySettings.getRoles( ).getUserIdFromContext( ),
				commandDesc, details ) ;

	}

	@GetMapping ( "/template/{name}" )
	public String template_load ( @PathVariable String name )
		throws IOException {

		File scriptsFolder = CsapTemplate.kubernetes_yaml.getFile( ) ;
		File script = new File( scriptsFolder, name ) ;
		logger.info( "reading: {}", script.getCanonicalPath( ) ) ;
		return FileUtils.readFileToString( script ) ;

	}

	@PostMapping ( "/api" )
	public ObjectNode api_create (
									String yaml ) {

		issueAudit( "api - create", yaml ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		result.put( "error", "api - create not implemented" ) ;
		result.put( "source", yaml ) ;

		return result ;

	}

	@PutMapping ( "/api" )
	public ObjectNode api_update (
									String yaml ) {

		issueAudit( "api - update", yaml ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		result.put( "error", "api - update not implemented" ) ;
		result.put( "source", yaml ) ;

		return result ;

	}

	@DeleteMapping ( value = "/api" , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	public ObjectNode api_delete ( @RequestBody MultiValueMap<String, String> formParams ) {

		issueAudit( "api - delete", formParams.getFirst( "yaml" ) ) ;

		ObjectNode result = jsonMapper.createObjectNode( ) ;

		result.put( "error", "api - delete not implemented" ) ;
		result.put( "source", formParams.getFirst( "yaml" ) ) ;

		return result ;

	}

	@PostMapping ( "/cli" )
	public ObjectNode cli_create (
									String yaml )
		throws IOException {

		issueAudit( "kubectl - create", yaml ) ;

		File yamlFile = csapApp.createYamlFile( "-create-", yaml, securitySettings.getRoles( )
				.getUserIdFromContext( ) ) ;

		String command = "create --save-config -f " + yamlFile.getAbsolutePath( ) ;

		return osManager.kubernetesCli( command, DockerJson.response_shell ) ;

	}

	@PutMapping ( "/cli" )
	public ObjectNode cli_update (
									String yaml )
		throws IOException {

		issueAudit( "kubectl - apply", yaml ) ;

		logger.info( "Apply: {}", yaml ) ;

		File yamlFile = csapApp.createYamlFile( "-apply-", yaml, securitySettings.getRoles( )
				.getUserIdFromContext( ) ) ;

		String command = "apply -f " + yamlFile.getAbsolutePath( ) ;

		return osManager.kubernetesCli( command, DockerJson.response_shell ) ;

	}

	// @PostMapping ( value = "/cli" , consumes =
	// MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	// public ObjectNode cli_delete ( @RequestBody MultiValueMap<String, String>
	// formParams )
	// @PostMapping ( "/cliDelete" )
	// public ObjectNode cli_delete ( String yaml )
	// throws IOException {

	@DeleteMapping ( value = "/cli" , consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
	public ObjectNode cli_delete (
									@RequestParam String yaml ,
									@RequestParam boolean force )
		throws IOException {

		// var yaml = formParams.getFirst( "yaml" ) ;

		issueAudit( "kubectl - delete", yaml ) ;

		logger.info( "deleting: {} ", yaml ) ;

		File yamlFile = csapApp.createYamlFile( "-delete-", yaml,
				securitySettings.getRoles( ).getUserIdFromContext( ) ) ;

		String command = "delete -f " + yamlFile.getAbsolutePath( ) ;

		if ( force ) {

			command += " --grace-period=0 --force " ;

		}

		return osManager.kubernetesCli( command, DockerJson.response_shell ) ;

	}

	public static String CSAP_PATH = "/csap/" ;

	@GetMapping ( "/cli/info/{documentType}" )
	public JsonNode cli_get_by_link (
										@PathVariable String documentType ,
										@RequestParam String resourcePath ,
										String project )
		throws Exception {

		if ( csapApp.isAdminProfile( ) ) {

			var allHostReport = csapApp.healthManager( ).build_host_report( project ) ;

			var kubernetesHostOptional = CSAP.jsonStream( allHostReport )
					.filter( hostReport -> hostReport.findParent( "kubernetes" ) != null )
					.map( hostReport -> hostReport.path( "name" ).asText( ) )
					.findFirst( ) ;

			if ( kubernetesHostOptional.isPresent( ) ) {

				var hostName = kubernetesHostOptional.get( ) ;
				MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>( ) ;

				urlVariables.set( "resourcePath", resourcePath ) ;
				urlVariables.set( "apiUser", CsapUser.currentUsersID( ) ) ;

				String url = KubernetesExplorer.EXPLORER_URL + "/kubernetes/cli/info/" + documentType ;
				List<String> hosts = new ArrayList<>( ) ;
				hosts.add( hostName ) ;

				logger.info( "hitting: {}, hosts: {}, urlVariables: {} ", url, hosts, urlVariables ) ;

				JsonNode remoteCall = csapApp.getOsManager( ).getServiceManager( ).remoteAgentsGet(
						hosts,
						url,
						urlVariables ) ;

				return remoteCall.path( hostName ) ;

			} else {

				logger.warn( "Failed to locate kubernetes host" ) ;
				return jsonMapper.createObjectNode( ) ;

			}

		}

		String command = kubernetes.buildCliCommand( documentType, resourcePath ) ;

		issueAudit( "kubectl cli: " + resourcePath, command ) ;

		return osManager.kubernetesCli( command, DockerJson.response_yaml ) ;
		// kubectl get -o=yaml deployment.extensions/peter
		// or kubectl run my-cool-app —-image=me/my-cool-app:v1 -o yaml --dry-run >
		// my-cool-app.yaml

	}

	@GetMapping ( "/cli/{namespace}/{type}/{source}" )
	public ObjectNode cli_get (
								@PathVariable String namespace ,
								@PathVariable String type ,
								@PathVariable String source ) {

		String resourceLocator = type + " " + source ;
		// if ( type.equals( "deployment" ) ) {
		// resourceLocator = "deployment.extensions/" + source ;
		// }

		String command = "--namespace=" + namespace + " get -o=yaml " + resourceLocator ;

		issueAudit( "kubectl - get " + type, command ) ;

		return osManager.kubernetesCli( command, DockerJson.response_yaml ) ;
		// kubectl get -o=yaml deployment.extensions/peter
		// or kubectl run my-cool-app —-image=me/my-cool-app:v1 -o yaml --dry-run >
		// my-cool-app.yaml

	}

	@GetMapping ( "/pod/describe/{namespace}/{pod}" )
	public ObjectNode pod_describe (
										@PathVariable String namespace ,
										@PathVariable String pod )
		throws IOException {

		issueAudit( "kubectl - describe", pod ) ;

		String command = "describe pods " + pod + " --namespace=" + namespace ;

		return osManager.kubernetesCli( command, DockerJson.response_yaml ) ;

	}

	@GetMapping ( "/deployment/describe/{namespace}/{deploy}" )
	public ObjectNode deploy_describe (
										@PathVariable String namespace ,
										@PathVariable String deploy )
		throws IOException {

		issueAudit( "kubectl - describe", deploy ) ;

		String command = "describe deployments " + deploy + " --namespace=" + namespace ;

		return osManager.kubernetesCli( command, DockerJson.response_yaml ) ;

	}

	@GetMapping ( "/node/describe/{node}" )
	public ObjectNode node_describe (
										@PathVariable String node )
		throws IOException {

		issueAudit( "kubectl - describe", node ) ;

		String command = "describe node " + node ;

		return osManager.kubernetesCli( command, DockerJson.response_yaml ) ;

	}

	@GetMapping ( "/endpoints" )
	public JsonNode endpoint_list ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).endpointListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No Endpoints" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/daemonSets" )
	public JsonNode daemonSetList ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).daemonSetListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No Daemon Sets" ) ;

		}

		return listing ;

	}

	@DeleteMapping ( "/daemonSets/{namespace}/{name}" )
	public ObjectNode daemonSet_delete (
											@PathVariable String namespace ,
											@PathVariable String name )
		throws Exception {

		issueAudit( "deleting service: " + name + " in " + namespace, null ) ;

		return kubernetes.daemonSetDelete( name, namespace ) ;

	}

	@GetMapping ( "/statefulSets" )
	public JsonNode statefulSetList ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).statefulSetListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No Stateful Sets" ) ;

		}

		return listing ;

	}

	@DeleteMapping ( "/statefulSets/{namespace}/{name}" )
	public ObjectNode statefulSet_delete (
											@PathVariable String namespace ,
											@PathVariable String name )
		throws Exception {

		issueAudit( "deleting service: " + name + " in " + namespace, null ) ;

		return kubernetes.statefulSetDelete( name, namespace ) ;

	}

	@GetMapping ( "/deployments" )
	public JsonNode deploymentsList ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).deploymentListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No deployments" ) ;

		}

		return listing ;

	}

	@PostMapping ( "/deployments" )
	public ObjectNode deploymentsAdd (
										String namespace ,
										String serviceType ,
										String ingressPath ,
										String ingressPort ,
										String ingressHost ,
										String ingressAnnotations ,
										String name ,
										String image ,
										String command ,
										String entry ,
										String nodeSelectors ,
										String annotations ,
										String labelsByType ,
										String resources ,
										String readinessProbe ,
										String livenessProbe ,
										int replicas ,
										String workingDirectory ,
										String network ,
										String restartPolicy ,
										String runUser ,
										String ports ,
										String volumes ,
										String environmentVariables ,
										String limits ,
										boolean addCsapTools )
		throws Exception {

		issueAudit( "creating deployment: " + name + " in " + namespace + " from image: " + image, null ) ;

		String k8Command = entry ;

		if ( StringUtils.isNotEmpty( command ) ) {

			k8Command = command ;

		}

		String k8Args = "" ;

		return kubernetes.specBuilder( ).deploymentCreate(
				name, namespace,
				image,
				nodeSelectors, annotations, labelsByType,
				resources,
				readinessProbe, livenessProbe,
				replicas,
				serviceType, ingressPath, ingressPort, ingressHost, ingressAnnotations,
				k8Command, k8Args, workingDirectory,
				network, restartPolicy, runUser,
				ports, volumes, environmentVariables,
				limits,
				addCsapTools ) ;

	}

	@DeleteMapping ( "/deployments/{namespace}/{name}/{deleteService}/{deleteIngress}" )
	public ObjectNode deploymentsDelete (
											@PathVariable String name ,
											@PathVariable String namespace ,
											@PathVariable boolean deleteService ,
											@PathVariable boolean deleteIngress )
		throws Exception {

		issueAudit( "deleting deployment: " + name
				+ " from namespace: " + namespace
				+ " deleteService: " + deleteService
				+ " deleteIngress: " + deleteIngress,
				null ) ;

		return kubernetes.specBuilder( ).deploymentDelete( name, namespace, deleteService, deleteIngress ) ;

	}

	@DeleteMapping ( "/persistent-volume-claims/{namespace}/{name}" )
	public ObjectNode volumeClaims_get_delete (
												@PathVariable String namespace ,
												@PathVariable String name )
		throws Exception {

		issueAudit( "deleting persistent volume claim: " + name + " in " + namespace, null ) ;

		return kubernetes.specBuilder( ).persistentVolumeClaimDelete( name, namespace ) ;

	}

	@GetMapping ( "/persistent-volume-claims" )
	public JsonNode volumeClaims_get ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).persistentVolumeClaimListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No claims found deployed" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/jobs" )
	public JsonNode jobs_get ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).jobAndCronJobListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No services deployed" ) ;

		}

		return listing ;

	}

	@GetMapping ( "/services" )
	public JsonNode services_get ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).serviceAndIngressListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No services deployed" ) ;

		}

		return listing ;

	}

	@PostMapping ( "/services" )
	public ObjectNode services_create (
										String serviceType ,
										String targetName ,
										String serviceName ,
										String namespace ,
										String portDefinition ,
										String labelsByType )
		throws Exception {

		issueAudit( "creating service - target: " + targetName
				+ " using: " + serviceName + " in " + namespace
				+ " from portDefinition: " + portDefinition,
				null ) ;

		return kubernetes.specBuilder( ).serviceCreate( serviceType, targetName, serviceName, namespace, portDefinition,
				labelsByType ) ;

	}

	@DeleteMapping ( "/services/{namespace}/{name}" )
	public ObjectNode services_delete (
										@PathVariable String namespace ,
										@PathVariable String name )
		throws Exception {

		issueAudit( "deleting service: " + name + " in " + namespace, null ) ;

		return kubernetes.specBuilder( ).serviceDelete( name, namespace ) ;

	}

	@DeleteMapping ( "/ingresses/{namespace}/{name}" )
	public JsonNode ingresses_delete (
										@PathVariable String namespace ,
										@PathVariable String name )
		throws Exception {

		issueAudit( "deleting ingress: " + name + " in " + namespace, null ) ;

		return kubernetes.specBuilder( ).ingressDelete( name, namespace ) ;

	}

	@GetMapping ( "/replicaSets" )
	public JsonNode replicaSets ( String namespace ) {

		if ( ! csapApp.isKubernetesInstalledAndActive( ) )
			return build_not_configured_listing( ) ;

		ArrayNode listing = kubernetes.listingsBuilder( ).replicaSetListing( namespace ) ;

		if ( listing.size( ) == 0 ) {

			ObjectNode msg = listing.addObject( ) ;
			msg.put( DockerJson.error.json( ), "No replicas" ) ;

		}

		return listing ;

	}

	@DeleteMapping ( "/replicaSets/{namespace}/{name}" )
	public ObjectNode replicaSet_delete (
											@PathVariable String namespace ,
											@PathVariable String name )
		throws Exception {

		issueAudit( "deleting service: " + name + " in " + namespace, null ) ;

		return kubernetes.specBuilder( ).replicaSetDelete( name, namespace ) ;

	}
}
