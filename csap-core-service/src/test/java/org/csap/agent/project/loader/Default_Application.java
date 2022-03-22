/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.project.loader ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.io.File ;
import java.util.List ;

import org.csap.agent.CsapBareTest ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.TestServices ;
import org.csap.agent.container.C7 ;
import org.csap.agent.linux.ServiceJobRunner ;
import org.csap.agent.model.ProcessRuntime ;
import org.csap.agent.model.Project ;
import org.csap.agent.model.ProjectLoader ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;

class Default_Application extends CsapBareTest {

	String definitionPath = "/definitions/_default-definition.json" ;
	File testDefinition = new File( getClass( ).getResource( definitionPath ).getPath( ) ) ;

	@BeforeAll
	void beforeAll ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		CSAP.setLogToInfo( Project.class.getName( ) ) ;
		assertThat( getApplication( ).loadDefinitionForJunits( false, testDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue( ) ;
		

		getOsManager( ).wait_for_initial_process_status_scan( 10 ) ;

		CSAP.setLogToInfo( Project.class.getName( ) ) ;

	}

	String csapPackageName = "default-definition-package" ;

	@Test
	void verify_namespace_monitoring ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var clusters = getApplication( ).getClustersInLifecycle( csapPackageName ) ;

		logger.info( CSAP.buildDescription( "namespace monitors", "clusters", clusters ) ) ;

		assertThat( clusters ).contains( "dev:" + ProjectLoader.NAMESPACE_MONITORS ) ;

		var defaultNamespaceMonitor = service( getApplication( ).getProjectLoader( ).getNsMonitorName( "default" ) ) ;

		logger.info( defaultNamespaceMonitor.details( ) ) ;

		var clusterToServices = getApplication( ).getRootProject( ).getClustersToServicesMapInCurrentLifecycle( ) ;

		logger.info( "clusterToServices: {}", clusterToServices ) ;

		assertThat( clusterToServices.get( ProjectLoader.NAMESPACE_MONITORS ).toString( ) ).contains(
				defaultNamespaceMonitor.getName( ) ) ;

	}

	@Test
	void verify_application_apis ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		// assertThat( getApplication().getName() ).isEqualTo(
		// "default-definition-application" ) ;

		assertThat( getApplication( ).getActiveProjectName( ) ).isEqualTo( csapPackageName ) ;

		assertThat( getApplication( )
				.getClustersInLifecycle( csapPackageName ) )
						.containsExactly( "dev:base-os",
								"dev:csap-demo",
								"dev:csap-management",
								"dev:kubernetes-provider",
								"dev:kubernetes-system",
								"dev:namespace-monitors" ) ;

		assertThat( getApplication( ).getServiceInstanceCurrentHost( TestServices.agent.idWithPort( ) ) )
				.isNotNull( ) ;
		

		assertThat( getApplication( ).getServiceInstanceCurrentHost( TestServices.agent.idWithPort( ) ).toString( ) )
				.isEqualTo( CsapConstants.AGENT_NAME + "_8011 on host: localhost cluster: base-os containers: 1" ) ;

	}

	@Test
	void verify_lifecycle_settings ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( getRootProjectSettings( ).getNumberWorkerThreads( ) ).isEqualTo( 3 ) ;

		assertThat( getRootProjectSettings( ).getAdminToAgentTimeoutSeconds( ) ).isEqualTo( 6 ) ;

		assertThat( getRootProjectSettings( ).getInfraTests( ).getCpuIntervalMinutes( ) ).isEqualTo( 30 ) ;

	}

	List<String> clusterHosts ( String clustername ) {

		var clusters = getApplication( ).getActiveProject( ).getClustersToHostMapInCurrentLifecycle( ) ;
		logger.info( "{} cluster has hosts: {}", clustername, clusters ) ;

		return clusters.get( clustername ) ;

	}

	@Test
	void verify_clusters ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		assertThat( clusterHosts( "base-os" ) ).containsExactly( "localhost" ) ;

		assertThat( clusterHosts( "kubernetes-provider" ) ).containsExactly( "localhost" ) ;

	}

	ServiceInstance service ( String name ) {

		return getApplication( ).findServiceByNameOnCurrentHost( name ) ;

	}

	@Test
	void verify_service_runtimes ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		//
		// Core
		//

		assertThat( service( TestServices.agent.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.springboot ) ;

		assertThat( service( TestServices.admin.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.springboot ) ;

		assertThat( service( TestServices.linux.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.csap_api ) ;

		assertThat( service( TestServices.java.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.csap_api ) ;

		//
		// docker
		//
		assertThat( service( TestServices.docker.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.csap_api ) ;

		assertThat( service( TestServices.dockerDemo.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.docker ) ;
		assertThat( service( TestServices.dockerDemo.id( ) ).is_cluster_kubernetes( ) ).isFalse( ) ;

		//
		// kubernetes
		//
		assertThat( service( TestServices.calicoNode.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.os ) ;

		assertThat( service( TestServices.csap_verify.id( ) ).getProcessRuntime( ) ).isEqualTo(
				ProcessRuntime.springboot ) ;

		assertThat( service( TestServices.kubelet.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.csap_api ) ;

		assertThat( service( TestServices.calicoNode.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.os ) ;

		assertThat( service( TestServices.kubernetesDashboard.id( ) ).getProcessRuntime( ) ).isEqualTo(
				ProcessRuntime.docker ) ;
		assertThat( service( TestServices.kubernetesDashboard.id( ) ).is_cluster_kubernetes( ) ).isTrue( ) ;

		//
		// tomcat
		//
		assertThat( service( TestServices.tomcat.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.csap_api ) ;
		assertThat( service( TestServices.tomcatDemo.id( ) ).getProcessRuntime( ) )
				.isEqualTo( ProcessRuntime.tomcat9 ) ;
		//
		// os monitor mpstat
		//
		assertThat( service( TestServices.mpMonitor.id( ) ).getProcessRuntime( ) ).isEqualTo( ProcessRuntime.os ) ;
		assertThat( service( TestServices.mpMonitor.id( ) ).getAppDirectory( ) ).isEqualTo( "/dev" ) ;
		assertThat( service( TestServices.mpMonitor.id( ) ).getPropDirectory( ) ).isEqualTo( "/aaa" ) ;

	}

	@Test
	void verify_nginx_jobs ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		var nginxInstance = service( "ingress-nginx" ) ;

		logger.info( "service: \n\t{} \n\t jobs: {}", nginxInstance, nginxInstance.getJobs( ) ) ;

		assertThat( nginxInstance.getJobs( ).size( ) )
				.as( "number of jobs" )
				.isGreaterThanOrEqualTo( 3 ) ;

		var firstScript = nginxInstance.getJobs( ).get( 0 ).getScript( ) ;

		assertThat( nginxInstance.getJobs( ).toString( ).contains( CsapConstants.CSAP_VARIABLE_PREFIX ) )
				.as( "job variables resolved" )
				.isFalse( ) ;

		assertThat( firstScript ).contains( "namespace: default" ) ;

	}

	@Test
	void verify_agent_jobs ( ) throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance agentInstance = service( CsapConstants.AGENT_NAME ) ;

		var jobs = agentInstance.getJobsDefinition( ) ;

		logger.info( "agentInstance: \n\t{} \n\t jobs: {}\n\n definition: {}", agentInstance, agentInstance.getJobs( ),
				CSAP.jsonPrint( jobs ) ) ;

		assertThat( agentInstance.getJobs( ).size( ) )
				.as( "number of jobs" )
				.isEqualTo( 8 ) ;

		assertThat( agentInstance.getJobs( ).get( 0 ).getEnvironmentFilters( ) )
				.as( "lifecycle filters" )
				.hasSize( 1 )
				.contains( ".*" ) ;

		var foundOsUpdates = false ;

		for ( var job : agentInstance.getJobs( ) ) {

			if ( job.getDescription( ).contains( "Update Operating System" ) ) {

				foundOsUpdates = true ;
				assertThat( job.getParameters( ) ).contains( "showUpdatesOnly,true,set to false to update the os" ) ;

			}

		}

		assertThat( foundOsUpdates ).isTrue( ) ;

	}

	@Test
	void verify_docker_jobs ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		ServiceInstance dockerInstance = getApplication( ).findServiceByNameOnCurrentHost( C7.dockerService
				.val( ) ) ;

		logger.info( "dockerInstance: \n\t{} \n\t jobs: {}", dockerInstance, dockerInstance.getJobs( ).get( 0 ) ) ;

		assertThat( dockerInstance.getJobs( ) )
				.hasSizeGreaterThanOrEqualTo( 2 ) ;

		var dockerJobSearch = dockerInstance.getJobs( ).stream( )
				.filter( job -> job.getFrequency( ).equals( "daily" ) )
				.findFirst( ) ;

		assertThat( dockerJobSearch ).isPresent( ) ;

		var dockerJob = dockerJobSearch.get( ) ;

		assertThat( dockerJob.getEnvironmentFilters( ) )
				.as( "lifecycle filters" )
				.hasSize( 1 )
				.contains( "dev.*" ) ;

		ServiceJobRunner jobRunner = new ServiceJobRunner( getCsapApis( ) ) ;

		dockerJob.setFrequency( ServiceJobRunner.Event.hourly.json( ) ) ;

		var jobsUsingMatchedLifecycle = jobRunner.activeServiceJobEntries( dockerInstance )
				.map( jobEntry -> jobEntry.getKey( ).getDescription( ) )
				.findFirst( ) ;
		logger.info( "jobsUsingNonMatchedLifecycle: ", jobsUsingMatchedLifecycle ) ;
		assertThat( jobsUsingMatchedLifecycle ).isPresent( ) ;

		dockerJob.setEnvironmentFilters( List.of( "non-matching-lifecycle" ) ) ;
		var jobsUsingNonMatchedLifecycle = jobRunner.activeServiceJobEntries( dockerInstance )
				.map( jobEntry -> jobEntry.getKey( ).getDescription( ) )
				.findFirst( ) ;
		logger.info( "jobsUsingNonMatchedLifecycle: ", jobsUsingNonMatchedLifecycle ) ;
		assertThat( jobsUsingNonMatchedLifecycle ).isEmpty( ) ;

		// var jobResults = jobRunner.runJobUsingDescription( dockerInstance,
		// dockerJob.getDescription() ) ;
		// logger.info( "jobResults: {}", jobResults );
		// assertThat( jobResults ).isEqualTo( "not able to run" ) ;

		// var noMatchResults = jobRunner.runJobUsingDescription( dockerInstance,
		// dockerJob.getDescription() ) ;
		// logger.info( "noMatchResults: {}", noMatchResults );
		// assertThat( noMatchResults ).contains( "Did not find matching job" ) ;

		jobRunner.shutdown( ) ;

	}

}
