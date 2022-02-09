package org.csap.agent ;

import java.io.File ;
import java.net.URI ;
import java.net.URISyntaxException ;

import org.csap.agent.container.C7 ;
import org.csap.agent.container.ContainerIntegration ;
import org.csap.agent.container.kubernetes.CrioIntegraton ;
import org.csap.agent.container.kubernetes.KubernetesIntegration ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.agent.model.Application ;
import org.csap.agent.services.OsManager ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.event.ContextClosedEvent ;
import org.springframework.context.event.EventListener ;
import org.springframework.stereotype.Service ;

import com.fasterxml.jackson.databind.ObjectMapper ;

@Service
public class CsapApis {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	// used to avoid cyclic injections
	private static CsapApis _instance = null ;

	@Autowired ( required = false )
	KubernetesIntegration kubernetesIntegration = null ;

	@Autowired ( required = false )
	ContainerIntegration containerIntegration = null ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;
	CrioIntegraton crioIntegraton = null ;

	//
	// Constructor injected
	//
	Application csapApplication ;
	OsManager osManager ;
	CsapMeterUtilities metricUtilities ;
	CsapEvents csapEvents ;

	public CsapApis (
			Application csapApplication,
			OsManager osManager,
			CsapMeterUtilities metricUtilities,
			CsapEvents csapEvents ) {

		logger.info( CsapApplication.highlightHeader( "Building CsapApis" ) ) ;

		_instance = this ;
		this.csapApplication = csapApplication ;
		this.osManager = osManager ;
		this.metricUtilities = metricUtilities ;
		this.csapEvents = csapEvents ;
		crioIntegraton = new CrioIntegraton( this, jacksonMapper ) ;

	}

	@EventListener ( {
			ContextClosedEvent.class
	} )
	public void onSpringShutdownEvent (
										ContextClosedEvent event ) {

		// shutdownInProgress = true ;

		try {

			logger.info( CsapApplication.highlightHeader( "initiating shutdown" ) ) ;

			shutdownInProgress = true ;

			application( ).shutdown( ) ;
			
			osManager( ).shutDown( );

			events( ).flushEvents( ) ;
			shutdown = true ;

			logger.info( CsapApplication.header( "shutdown complete" ) ) ;

		} catch ( Exception e ) {

			logger.info( CSAP.buildCsapStack( e ) ) ;
			logger.info( "", e ) ;

		}

	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	private boolean shutdown = false ;

	public boolean isShutdown ( ) {

		return shutdown ;

	}

	volatile boolean shutdownInProgress ;

	public boolean isShutdownInProgress ( ) {

		return shutdownInProgress ;

	}

	// @PreDestroy
	public void shutdown ( ) {

		// Not invoked early enough

	}

	public Application application ( ) {

		return csapApplication ;

	}

	public OsManager osManager ( ) {

		return osManager ;

	}

	public CsapEvents events ( ) {

		return csapEvents ;

	}

	public static CsapApis getInstance ( ) { // used rarely - prefer injection

		return _instance ;

	}

	// Junits only
	public static void setInstance ( CsapApis csapApis ) {

		_instance = csapApis ;

	}

	public KubernetesIntegration kubernetes ( ) {

		return kubernetesIntegration ;

	}

	public void setKubernetesIntegration ( KubernetesIntegration kubernetesIntegration ) {

		this.kubernetesIntegration = kubernetesIntegration ;

	}

	public ContainerIntegration containerIntegration ( ) {

		return containerIntegration ;

	}

	public void setContainerIntegration ( ContainerIntegration dockerIntegration ) {

		this.containerIntegration = dockerIntegration ;

	}

	public boolean isCrioInstalledAndActive ( ) {

		if ( csapApplication.isJunit( )
				|| ( csapApplication.isDesktopHost( )
						&& containerIntegration( ).getSettings( ).getUrl( ).contains( "podman" ) ) ) {

			return true ;

		}

		return csapApplication.is_service_running( "crio" ) ;

	}

	public String containerHostConfigured ( ) throws URISyntaxException {

		var containerUrl = containerIntegration( ).getSettings( ).getUrl( ) ;
		var uri = new URI( containerUrl ) ;
		var dockerHost = uri.getHost( ) ;
		return dockerHost ;

	}

	boolean isContainerProviderDeployInProgress = false ;

	boolean isContainerProviderDeployInProgress ( ) {

		return isContainerProviderDeployInProgress ;

	}

	public void setContainerProviderDeployInProgress ( boolean isDockerDeployInProgress ) {

		this.isContainerProviderDeployInProgress = isDockerDeployInProgress ;

	}

	public boolean isContainerProviderInstalledAndActive ( ) {

		if ( containerIntegration( ) == null
				|| containerIntegration( ).getDockerClient( ) == null
				|| isContainerProviderDeployInProgress ) {

			return false ;

		}

		if ( csapApplication.is_service_running( C7.dockerService.val( ) )
				|| csapApplication.is_service_running( C7.podmanService.val( ) ) ) {

			return true ;

		}

		// docker started outside of csap instance: agent in container or agent in
		// monitor mode
		if ( dockerSocket.exists( )
				&& dockerSocket.canRead( )
				&& dockerSocket.canWrite( ) ) {

			logger.debug( "found dockerd running" ) ;
			return true ;

		}

		return false ;

	}

	public CrioIntegraton crio ( ) {

		return crioIntegraton ;

	}

	private File crioConf = new File( "/etc/crio/crio.conf" ) ;

	public boolean doesCrioConfExist ( ) {

		return crioConf.exists( ) ;

	}

	private File podManStorage = new File( "/var/lib/containers" ) ;

	private File dockerSocket = new File( "/var/run/docker.sock" ) ;

	public boolean isKubernetesInstalledAndActive ( ) {

		if ( ! csapApplication.isApplicationLoaded( ) ) {

			return false ;

		}

		if ( csapApplication.isAgentProfile( ) ) {

			if ( kubernetes( ) == null ) {

				return false ;

			} else if ( doesCrioConfExist( ) ) {

				// no op
			} else if ( ! isContainerProviderInstalledAndActive( ) ) {

				return false ;

			}

			return csapApplication.is_service_running( kubernetes( ).getDiscoveredServiceName( ) ) ;

		} else {

			return false ;

		}

	}

	public CsapMeterUtilities metrics ( ) {

		return metricUtilities ;

	}

	public void setMetricUtilities ( CsapMeterUtilities metricUtilities ) {

		this.metricUtilities = metricUtilities ;

	}

	@Override
	public String toString ( ) {

		return CSAP.buildDescription( this.getClass( ).getName( ),
				"kubernetes", kubernetes( ) ) ;

	}

}
