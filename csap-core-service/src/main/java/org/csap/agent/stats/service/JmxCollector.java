package org.csap.agent.stats.service ;

import java.io.IOException ;
import java.io.InterruptedIOException ;
import java.lang.management.ManagementFactory ;
import java.net.SocketTimeoutException ;
import java.util.HashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.concurrent.Callable ;
import java.util.concurrent.Executors ;
import java.util.concurrent.Future ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;
import java.util.stream.Stream ;

import javax.management.MBeanServer ;
import javax.management.MBeanServerConnection ;
import javax.management.remote.JMXConnector ;
import javax.management.remote.JMXConnectorFactory ;
import javax.management.remote.JMXServiceURL ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.apache.commons.lang3.text.WordUtils ;
import org.csap.agent.CsapApis ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.model.Application ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.model.ServiceInstance ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;

public class JmxCollector {

	final Logger logger = LoggerFactory.getLogger( JmxCollector.class ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	CsapApis csapApis ;
	private ServiceCollector serviceCollector ;

	private JmxCommonCollector javaCommonCollector = new JmxCommonCollector( ) ;
	private JmxCustomCollector javaCustomCollector ;

	public JmxCollector ( CsapApis csapApis, ServiceCollector serviceCollector ) {

		this.csapApis = csapApis ;

		this.serviceCollector = serviceCollector ;
		javaCustomCollector = new JmxCustomCollector( serviceCollector, csapApis ) ;

	}

	private int numberOfCollectionAttempts = 0 ;

	public int getNumberOfCollectionAttempts ( ) {

		return numberOfCollectionAttempts ;

	}

	long collectionStart = 0 ;

	public void collectServicesOnHost ( ) {

		collectionStart = System.currentTimeMillis( ) ;

		// Java GC can very occasionally prevent collection
		// retries are limited to avoid impacting subsequent collections
		numberOfCollectionAttempts = 0 ;
		var timer = csapApis.metrics( ).startTimer( ) ;

		Stream<ServiceInstance> serviceToCollectStream = csapApis.application( )
				.getActiveProject( )
				.getServicesOnHost( csapApis.application( ).getCsapHostName( ) ) ;

		numberOfCollectionAttempts = 0 ;

		while ( true ) {

			numberOfCollectionAttempts++ ; // Strictly for tracking

			List<ServiceInstance> failedToCollectInstances = serviceToCollectStream
					.filter( ServiceInstance::isJavaJmxCollectionEnabled )
					.filter( instance -> ! instance.isHttpCollectionEnabled( ) )
					.filter( this::collectJmxDataForService )
					.collect( Collectors.toList( ) ) ;

			if ( failedToCollectInstances.size( ) > 0 ) {

				csapApis.metrics( ).incrementCounter( "collect-java.connection-retry" ) ;
				serviceToCollectStream = failedToCollectInstances.stream( ) ;

				try {

					logger.info(
							"*jmxCollectionFailure item count: {} ,  attempts taken: {}, sleeping for 1s and trying again.",
							failedToCollectInstances.size( ), numberOfCollectionAttempts ) ;
					Thread.sleep( 1000 ) ;

				} catch ( InterruptedException e ) {

					logger.debug( "jmxRetry faile", e ) ;

				}

			} else {

				break ;

			}

		}

		csapApis.metrics( ).stopTimer( timer, "csap.collect-jmx" ) ;

	}

	/**
	 * GC will cause connection failures; retry until
	 * MAX_TIME_TO_COLLECT_INTERVAL_MS
	 *
	 * @param serviceInstance
	 * @return
	 */
	public boolean collectJmxDataForService ( ServiceInstance serviceInstance ) {

		boolean isIgnoreCollectionFailure = true ;

		if ( System.currentTimeMillis( ) - collectionStart > serviceCollector.getMaxCollectionMillis( ) ) {

			isIgnoreCollectionFailure = false ;
			logger.warn( "Final collection attempt due to collection time is longer then {} seconds",
					serviceCollector.getMaxCollectionMillis( ) / 1000 ) ;

		}

		return ! executeJmxCollection( serviceInstance, isIgnoreCollectionFailure ) ;

	}

	private boolean executeJmxCollection ( ServiceInstance serviceInstance , boolean isIgnoreCollectionFailure ) {

		String serviceNamePort = serviceInstance.getServiceName_Port( ) ;

		logger.debug( "{} Getting JMX Metrics, isIgnoreCollectionFailure: {}, custom metrics is: {}",
				serviceNamePort, isIgnoreCollectionFailure, serviceInstance.getServiceMeters( ) ) ;

		var serviceTimer = csapApis.metrics( ).startTimer( ) ;

		// default value is 0 - gets updated if service is online
		ServiceCollectionResults applicationResults = new ServiceCollectionResults( serviceInstance,
				serviceCollector.getInMemoryCacheSize( ) ) ;

		serviceInstance
				.getServiceMeters( )
				.stream( )
				.filter( meter -> ! meter.getMeterType( ).isHttp( ) )
				.forEach( serviceMeter -> {

					applicationResults.addCustomResultLong( serviceMeter.getCollectionId( ), 0l ) ;

				} ) ;

		// Only connect to active instances
		if ( ! serviceInstance.getDefaultContainer( ).isRunning( )
				&& ! serviceInstance.isRemoteCollection( )
				&& ! csapApis.application( ).isJunit( ) ) {

			logger.debug( "{} is inactive, skipping collection", serviceNamePort ) ;
			serviceInstance.getDefaultContainer( ).setHealthReportCollected( null ) ;

		} else {

			logger.debug( "{} is active, starting JMX collection ", serviceNamePort ) ;

			// String opHost = csapApis.application().getCsapHostName();
			String jmxHost = "localhost" ;

			if ( csapApis.application( ).isAllowRemoteJmx( ) ) {

				jmxHost = csapApis.application( ).getHostFqdn( ) ;

			}

			String jmxPort = serviceInstance.getJmxPort( ) ;

			if ( Application.isRunningOnDesktop( ) ) {

				jmxHost = ServiceCollector.TEST_HOST ;

				if ( serviceCollector.isShowWarnings( ) ) {

					logger.warn( "JmxConnection using " + jmxHost + " For desktop testing" ) ;

				}

			}

			if ( serviceInstance.isRemoteCollection( ) ) {

				jmxHost = serviceInstance.getCollectHost( ) ;
				jmxPort = serviceInstance.getCollectPort( ) ;

				if ( ! serviceCollector.getServiceRemoteHost( ).containsKey( serviceNamePort ) ) {

					serviceCollector.getServiceRemoteHost( ).put( serviceNamePort, jmxHost ) ;

				}

			}

			long maxJmxCollectionMs = csapApis.application( )
					.rootProjectEnvSettings( )
					.getMaxJmxCollectionMs( ) ;

			if ( serviceCollector.getTestServiceTimeout( ) > 0
					&& serviceInstance.getServiceName_Port( ).equals( serviceCollector.getTestServiceTimeoutName( ) )
					&& getNumberOfCollectionAttempts( ) < serviceCollector.getTestNumRetries( ) ) {

				maxJmxCollectionMs = serviceCollector.getTestServiceTimeout( ) ;

			}

			// String serviceUrl = "service:jmx:rmi://" + opHost
			// + "/jndi/rmi://" + opHost + ":" + opPort + "/jmxrmi";
			String serviceUrl = "service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi" ;
			JMXConnector connector = null ;

			try {
				// REF. http://wiki.apache.org/tomcat/FAQ/Monitoring

				MBeanServerConnection mbeanConn = null ;

				// keepRunning is set to false in junits
				if ( serviceInstance
						.getName( )
						.equals( CsapConstants.AGENT_NAME )
						&& serviceCollector.isKeepRunning( ) ) {

					// optimize local collect
					mbeanConn = (MBeanServer) ManagementFactory.getPlatformMBeanServer( ) ;

				} else {

					var connectionTimer = csapApis.metrics( ).startTimer( ) ;
					// connector = JMXConnectorFactory.connect(new
					// JMXServiceURL(serviceUrl));

					// Add a max of 2 retries to handle GC collections
					try {

						var connectionOptions = new HashMap<String, Object>( ) ;
						var credentials = new String[] {
								"csap",
								"csap"
						} ;

						if ( csapApis.application( ).getCsapCoreService( ) != null
								&& csapApis.application( ).getCsapCoreService( ).isJmxAuthentication( ) ) {

							credentials = new String[] {
									csapApis.application( ).getCsapCoreService( ).getJmxUser( ),
									csapApis.application( ).getCsapCoreService( ).getJmxPass( )
							} ;

						}

						connectionOptions.put( JMXConnector.CREDENTIALS, credentials ) ;
						connector = connectWithTimeout(
								new JMXServiceURL( serviceUrl ),
								maxJmxCollectionMs, TimeUnit.MILLISECONDS,
								connectionOptions ) ;

					} catch ( Exception connectException ) {

						csapApis.metrics( ).incrementCounter( "collect-jmx." +
								serviceInstance.getName( ) + ".connect" + ".failures" ) ;

						logger.warn( "\n *** Failed connecting to: {} using: {}, due to: {}",
								serviceInstance.getName( ),
								serviceUrl,
								connectException.getClass( ).getName( ) ) ;

						logger.debug( "Reason: {}", CSAP.buildCsapStack( connectException ) ) ;

						if ( isIgnoreCollectionFailure ) {

							return false ; // try agains will occur

						} else {

							// too many attempts will cause timers to overlap.
							throw connectException ;

						}

					} finally {

						csapApis.metrics( ).stopTimer( connectionTimer, "collect-jmx.connect-" + serviceInstance
								.getName( ) ) ;

					}

					mbeanConn = connector.getMBeanServerConnection( ) ;

				}

				// NOTE: We use a single connection to collect first the
				// JMX
				// info, then optionally any custom attributes
				javaCommonCollector.collect( mbeanConn, applicationResults ) ;

				if ( serviceInstance.hasServiceMeters( ) ) {

					javaCustomCollector.collect( mbeanConn, applicationResults ) ;

				}

				logger.debug( "\n ******* App Collection Complete: {} \n{}\n", serviceInstance.getServiceName_Port( ),
						WordUtils.wrap( applicationResults.toString( ), 200 ) ) ;

			} catch ( Exception e ) {

				csapApis.metrics( ).incrementCounter( "csap.collect-jmx.failures" ) ;
				csapApis.metrics( ).incrementCounter( "collect-jmx.failures." + serviceInstance.getName( ) ) ;

				logger.debug( "{} Failed to get jmx data for service", serviceNamePort, e ) ;

				if ( serviceCollector.isShowWarnings( ) ) {

					String reason = e.getMessage( ) ;

					if ( reason != null && reason.length( ) > 60 ) {

						reason = e
								.getClass( )
								.getName( ) ;

					}

					logger.warn( "\n **** Failed to collect {} for service {}\n Reason: {}, Cause: {}",
							"commmon attributes", serviceNamePort, reason, e.getCause( ) ) ;

				}

				// resultNode.put("error", "Failed to invoke JMX"
				// + Application.getCustomStackTrace(e));
			} finally {

				try {

					if ( connector != null ) {

						logger.debug( "{} Closing JMX connection id: {}", serviceInstance.getServiceName_Port( ),
								connector.getConnectionId( ) ) ;
						connector.close( ) ;
						logger.debug( "{} --Closed JMX connection", serviceInstance.getServiceName_Port( ) ) ;

					}

				} catch ( Exception e ) {

					logger.debug( "Failed closing connection: {} due to: {}", serviceNamePort,
							CSAP.buildCsapStack( e ) ) ;

					if ( ! e
							.getMessage( )
							.contains( "Not connected" ) ) {

						logger.error(
								"Failed closing connection for: " + serviceNamePort + " reason: " + e.getMessage( ) ) ;

					}

				}

			}

		}

		applicationResults.add_results_to_java_collection(
				serviceCollector.getServiceToJavaMetrics( ),
				serviceInstance.getName( ) ) ;

		applicationResults.add_results_to_application_collection(
				serviceCollector.getServiceToAppMetrics( ),
				serviceInstance.getName( ) ) ;

		if ( serviceCollector.isPublishSummaryAndPerformHeartBeat( ) || serviceCollector.isTestHeartBeat( ) ) {

			// update instance settings for availability checks
			serviceInstance.getDefaultContainer( ).setNumTomcatConns( applicationResults.getHttpConn( ) ) ;
			serviceInstance.getDefaultContainer( ).setJvmThreadCount( applicationResults.getJvmThreadCount( ) ) ;

			if ( serviceInstance.isApplicationHealthMeter( ) ) {

				serviceInstance.getDefaultContainer( )
						.setJmxHeartbeatMs( applicationResults.getCustomResult( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) ;

			}

			serviceCollector.getLastCollectedResults( ).put( serviceInstance.getServiceName_Port( ),
					applicationResults ) ;

		}

		csapApis.metrics( ).stopTimer( serviceTimer, "collect-jmx." + serviceInstance.getName( ) ) ;

		return true ; // results were recorded.

	}

	private BasicThreadFactory jmxConnectionMonitorFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapJmxMonitor-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build( ) ;

	ScheduledExecutorService jmxConnectionMonitorExecutor = Executors.newScheduledThreadPool( 1,
			jmxConnectionMonitorFactory ) ;

	/**
	 * Hack needed in order to get JMX timeouts
	 * http://weblogs.java.net/blog/emcmanus /archive/2007/05/making_a_jmx_co.html
	 *
	 * Total timeout = 2 x params. First to connect, then for calls
	 *
	 * @param url
	 * @param requestTimeout
	 * @param requestUnit
	 * @return
	 * @throws IOException
	 */
	private JMXConnector connectWithTimeout (
												final JMXServiceURL url ,
												long requestTimeout ,
												TimeUnit requestUnit ,
												Map<String, Object> connectionOptions )
		throws IOException {

		logger.debug( "xxx Attempting connection to: {} with timeout : {} ms", url, requestTimeout ) ;

		Future<Object> connectionResults = jmxConnectionMonitorExecutor.submit( //
				new Callable<Object>( ) {
					public Object call ( ) {

						try {

							Map<String, Object> environment = new HashMap<String, Object>( ) ;

							// Disable client checks - java 9 hides the variable. It can
							// be set
							// jmx.remote.x.client.connection.check.period
							// environment.put( EnvHelp.CLIENT_CONNECTION_CHECK_PERIOD,
							// 0 );
							environment.put( "jmx.remote.x.client.connection.check.period", 0l ) ;
							environment.putAll( connectionOptions ) ;

							logger.debug( "xxx connectionOptions {}", environment ) ;

							JMXConnector connector = JMXConnectorFactory.connect( url, environment ) ;
							return connector ;

						} catch ( Throwable t ) {

							return t ;

						}

					}
				} ) ;

		Object result ;

		try {

			result = connectionResults.get( requestTimeout, requestUnit ) ;

		} catch ( Exception e ) {

			throw initCause( new InterruptedIOException( e.getMessage( ) ), e ) ;

		} finally {

			// jmxConnectionMonitorExecutor.shutdown();
			connectionResults.cancel( true ) ;

		}

		if ( result == null ) {

			throw new SocketTimeoutException( "xxx Connect timed out: " + url ) ;

		}

		if ( result instanceof JMXConnector ) {

			final JMXConnector connector = (JMXConnector) result ;
			@SuppressWarnings ( "unused" )
			Future<Object> closeResults = jmxConnectionMonitorExecutor.schedule( ( new Callable<Object>( ) {
				public Object call ( ) {

					Object result = "" ;

					try {

						logger.debug( "xxx Closing connection: " + url ) ;

						connector.close( ) ;

					} catch ( Throwable t ) {

						logger.error( "xxx Failed to close connection", t ) ;

					}

					return result ;

				}
			} ), requestTimeout, requestUnit ) ;

			return (JMXConnector) result ;

		}

		try {

			throw (Throwable) result ;

		} catch ( IOException e ) {

			throw e ;

		} catch ( RuntimeException e ) {

			throw e ;

		} catch ( Error e ) {

			throw e ;

		} catch ( Throwable e ) {

			// In principle this can't happen but we wrap it anyway
			throw new IOException( e.toString( ), e ) ;

		}

	}

	private static <T extends Throwable> T initCause (
														T wrapper ,
														Throwable wrapped ) {

		wrapper.initCause( wrapped ) ;
		return wrapper ;

	}

}
