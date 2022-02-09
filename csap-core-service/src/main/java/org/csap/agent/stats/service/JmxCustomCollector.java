package org.csap.agent.stats.service ;

import java.util.concurrent.TimeUnit ;

import javax.management.MBeanServerConnection ;
import javax.management.ObjectName ;

import org.csap.agent.CsapApis ;
import org.csap.agent.model.ServiceAlertsEnum ;
import org.csap.agent.stats.ServiceCollector ;
import org.csap.helpers.CSAP ;
import org.csap.helpers.CsapApplication ;
//import org.javasimon.CounterSample ;
//import org.javasimon.SimonManager ;
//import org.javasimon.Split ;
//import org.javasimon.StopwatchSample ;
//import org.javasimon.jmx.SimonManagerMXBean ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class JmxCustomCollector {

	public static final String TOMCAT_SERVLET_CONTEXT_TOKEN = "__CONTEXT__" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode( ) ;

	private ServiceCollector serviceCollector ;

	CsapApis csapApis ;

	public JmxCustomCollector ( ServiceCollector serviceCollector, CsapApis csapApis ) {

		this.serviceCollector = serviceCollector ;

		this.csapApis = csapApis ;

	}

	/**
	 *
	 * Each JVM can optionally specify additional attributes to collect
	 *
	 * @param instance
	 * @param serviceNamePort
	 * @param collectionResults
	 * @param mbeanConn
	 */
	public void collect (
							MBeanServerConnection mbeanConn ,
							ServiceCollectionResults collectionResults ) {

		// System.err.println( "\n\n xxx logging issues\n\n " );
		logger.debug( "\n\n ============================ Getting JMX Custom Metrics for {} \n\n",
				collectionResults.getServiceInstance( ).getServiceName_Port( ) ) ;

		collectionResults
				.getServiceInstance( )
				.getServiceMeters( )
				.stream( )
				.filter( meter -> ! meter.getMeterType( ).isHttp( ) )
				.forEach( serviceMeter -> {

					Object attributeCollected = 0 ;
					var jmxAttributeTimer = csapApis.metrics( ).startTimer( ) ;

					boolean isCollectionSuccesful = false ;

					try {

						logger.debug( "serviceMeter: {}", serviceMeter ) ;

						if ( serviceMeter.getMeterType( ).isMbean( ) ) {

							attributeCollected = collectCustomMbean( serviceMeter,
									collectionResults, mbeanConn ) ;

							// } else if ( serviceMeter.getMeterType().isSimon() ) {
							//
							// attributeCollected = collectCustomSimon( serviceMeter, simonMgrMxBean,
							// mbeanConn, collectionResults ) ;

						} else {

							logger.warn( "Unexpected meter type: {}", serviceMeter.toString( ) ) ;
							throw new Exception( "Unknown metric type" ) ;

						}

						isCollectionSuccesful = true ;

					} catch ( Throwable e ) {

						if ( ! serviceMeter.isIgnoreErrors( ) ) {

							// SLA will monitor counts
							csapApis.metrics( ).incrementCounter( "csap.collect-jmx.service.failures" ) ;

							csapApis.metrics( ).incrementCounter( "collect-jmx.service.failures."
									+ collectionResults.getServiceInstance( ).getName( ) ) ;

							csapApis.metrics( ).incrementCounter( "collect-jmx.service-failures." +
									collectionResults.getServiceInstance( ).getName( )
									+ "-" + serviceMeter.getCollectionId( ) ) ;

							if ( serviceCollector.isShowWarnings( ) ) {

								String reason = e.getMessage( ) ;

								if ( reason != null && reason.length( ) > 60 ) {

									reason = e
											.getClass( )
											.getName( ) ;

								}

								logger.warn( CsapApplication.header(
										"Failed to collect {} for service {}\n Reason: {}, Cause: {}" ),
										serviceMeter.getCollectionId( ), collectionResults.getServiceInstance( )
												.getServiceName_Port( ), reason,
										e.getCause( ) ) ;

								logger.debug( "{}", CSAP.buildCsapStack( e ) ) ;

							}

						}

						logger.debug( "{} Failed getting custom metrics for: {}, reason: {}",
								collectionResults.getServiceInstance( ).getServiceName_Port( ),
								serviceMeter.getCollectionId( ),
								CSAP.buildCsapStack( e ) ) ;

					} finally {

						long resultLong = -1l ;

						if ( attributeCollected instanceof Long ) {

							resultLong = (Long) attributeCollected ;

						} else if ( attributeCollected instanceof Integer ) {

							resultLong = (Integer) attributeCollected ;

						} else if ( attributeCollected instanceof Double ) {

							Double d = (Double) attributeCollected ;
							d = d * serviceMeter.getMultiplyBy( ) ;

							if ( serviceMeter.getCollectionId( ).equals( "SystemCpuLoad" )
									|| serviceMeter.getCollectionId( ).equals( "ProcessCpuLoad" ) ) {

								logger.debug( "Adding multiple by for cpu values: {}", serviceMeter
										.getCollectionId( ) ) ;
								d = d * 100 ;

							} else if ( d < 1 ) {

								logger.debug( "{}: Multiplying {} by 1000 to store. Add divideBy 1000",
										collectionResults.getServiceInstance( ).getServiceName_Port( ),
										serviceMeter.getCollectionId( ) ) ;
								d = d * 1000 ;

							}

							resultLong = Math.round( d ) ;

						} else if ( attributeCollected instanceof Boolean ) {

							logger.debug( "Got a boolean result" ) ;
							Boolean b = (Boolean) attributeCollected ;

							if ( b ) {

								resultLong = 1 ;

							} else {

								resultLong = 0 ;

							}

						}

						logger.debug( "{} metric: {} , jmxResultObject: {} , resultLong: {}",
								collectionResults.getServiceInstance( ).getName( ),
								serviceMeter.getCollectionId( ), attributeCollected, resultLong ) ;

						if ( serviceMeter.getCollectionId( ).equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) {

							// for hearbeats, store the time IF it has passed
							if ( resultLong == 1 ) {

								var nanos = csapApis.metrics( ).stopTimer( jmxAttributeTimer,
										"collect-jmx.service-attribute" ) ;
								resultLong = TimeUnit.NANOSECONDS.toMillis( nanos ) ;

								// some apps return very quickly due to not actually
								// implementing. return 1 if that happens
								if ( resultLong == 0 ) {

									resultLong = 1 ; // minimum of 1 to indicate success

								} // for checks.

							}

							collectionResults
									.getServiceInstance( )
									.getDefaultContainer( ).setJmxHeartbeatMs( resultLong ) ;

						}

						if ( ! ( attributeCollected instanceof Double ) ) {

							resultLong = resultLong * serviceMeter.getMultiplyBy( ) ;

						}

						resultLong = Math.round( resultLong / serviceMeter.getDivideBy( serviceCollector
								.getCollectionIntervalSeconds( ) ) ) ;

						// simon delta is handled in simon collection
						if ( serviceMeter.isDelta( ) ) {

							long last = resultLong ;
							String key = collectionResults.getServiceInstance( ).getServiceName_Port( ) + serviceMeter
									.getCollectionId( ) ;

							if ( deltaLastCollected.has( key ) && isCollectionSuccesful ) {

								resultLong = resultLong - deltaLastCollected.get( key ).asLong( ) ;

								if ( resultLong < 0 ) {

									resultLong = 0 ;

								}

							} else {

								resultLong = 0 ;

							}

							// Only update the delta when collection is successful;
							// otherwise leave last collected in place
							if ( isCollectionSuccesful ) {

								deltaLastCollected.put( key, last ) ;

							}

						}

						logger.debug( "\n\n{} ====> metricId: {}, resultLong: {} \n\n",
								collectionResults.getServiceInstance( ).getName( ), serviceMeter.getCollectionId( ),
								resultLong ) ;
						collectionResults.addCustomResultLong( serviceMeter.getCollectionId( ), resultLong ) ;

					}

				} ) ;

	}

	private Object collectCustomMbean (
										ServiceMeter serviceMeter ,
										ServiceCollectionResults jmxResults ,
										MBeanServerConnection mbeanConn )
		throws Exception {

		Object jmxResultObject = 0 ;
		String mbeanNameCustom = serviceMeter.getMbeanName( ) ;

		if ( mbeanNameCustom.contains( TOMCAT_SERVLET_CONTEXT_TOKEN ) ) {

			// Some servlet metrics require version string in name
			// logger.info("****** version: " +
			// jmxResults.getInstanceConfig().getMavenVersion());
			String version = jmxResults
					.getServiceInstance( )
					.getMavenVersion( ) ;

			if ( jmxResults
					.getServiceInstance( )
					.isScmDeployed( ) ) {

				version = jmxResults
						.getServiceInstance( )
						.getScmVersion( ) ;
				version = version.split( " " )[0] ; // first word of
				// scm
				// scmVersion=3.5.6-SNAPSHOT
				// Source build
				// by ...

			}

			// WARNING: version must be updated when testing.
			String serviceContext = "//localhost/" + jmxResults
					.getServiceInstance( )
					.getContext( ) ;
			// if ( !jmxResults.getServiceInstance().is_springboot_server() ) {
			// serviceContext += "##" + version ;
			// }
			mbeanNameCustom = mbeanNameCustom.replaceAll( TOMCAT_SERVLET_CONTEXT_TOKEN, serviceContext ) ;
			logger.debug( "Using custom name: {} ", mbeanNameCustom ) ;

		}

		String mbeanAttributeName = serviceMeter.getMbeanAttribute( ) ;

		if ( mbeanAttributeName.equals( "SystemCpuLoad" ) ) {

			// Reuse already collected values (load is stateful)
			jmxResultObject = Long.valueOf( serviceCollector.getCollected_HostCpu( ).get( 0 ).asLong( ) ) ;

		} else if ( mbeanAttributeName.equals( "ProcessCpuLoad" ) ) {

			// Reuse already collected values
			jmxResultObject = Long.valueOf( jmxResults.getCpuPercent( ) ) ;

		} else if ( serviceMeter.getCollectionId( ).equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT )
				&& ! serviceCollector.isPublishSummaryAndPerformHeartBeat( ) && ! serviceCollector
						.isTestHeartBeat( ) ) {

			// special case to avoid double heartbeats
			// reUse collected value from earlier interval.
			jmxResultObject = Long.valueOf( jmxResults.getServiceInstance( ).getDefaultContainer( )
					.getJmxHeartbeatMs( ) ) ;

		} else {

			logger.debug( "Collecting mbean: {}, attribute: {}", mbeanNameCustom, mbeanAttributeName ) ;
			jmxResultObject = mbeanConn.getAttribute( new ObjectName( mbeanNameCustom ),
					mbeanAttributeName ) ;

		}

		logger.debug( "Result for {} is: {}", mbeanAttributeName, jmxResultObject ) ;
		return jmxResultObject ;

	}

}
