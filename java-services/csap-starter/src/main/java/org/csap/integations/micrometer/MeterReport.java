package org.csap.integations.micrometer ;

import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Iterator ;
import java.util.List ;
import java.util.Optional ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Stream ;
import java.util.stream.StreamSupport ;

import javax.servlet.http.HttpServletRequest ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.alerts.AlertFields ;
import org.csap.alerts.AlertsController ;
import org.csap.helpers.CSAP ;
import org.csap.integations.micrometer.CsapMicroMeter.HealthReport ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.web.bind.annotation.CrossOrigin ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.Meter ;
import io.micrometer.core.instrument.MeterRegistry ;
import io.micrometer.core.instrument.Tag ;
import io.micrometer.core.instrument.Timer ;
import io.micrometer.core.instrument.distribution.CountAtBucket ;
import io.micrometer.core.instrument.distribution.HistogramSnapshot ;
import io.micrometer.core.instrument.distribution.ValueAtPercentile ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

@RestController
@RequestMapping ( CsapMicroMeter.BASE_URI )
public class MeterReport {

	public static final String SIMPLE_VALUE = "simple-value" ;

	static final String CONVERT_TO_MB = "tomcat.global.sent.*|jvm.*memory.*|jvm.gc.max.data.size.*|jvm.gc.live.data.size.*" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private static final List<String> AGGREGATE_CATEGORIES = List.of(
			"http.server.requests",
			"cache.",
			"jvm.memory.used",
			"jvm.memory.max",
			"jvm.memory.committed",
			"tomcat.",
			"log4j2.events" ) ;

	// @Autowired
	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	MeterRegistry microMeterRegistry ;

	@Autowired
	public MeterReport ( SimpleMeterRegistry microMeterRegistry ) {

		this.microMeterRegistry = microMeterRegistry ;

	}

	@Autowired
	HealthReport healthReport ;

	AlertsController alertController ;

	public ObjectNode buildSimple (
									String nameFilter ,
									List<String> tagFilter ,
									int precision ,
									boolean aggregate ) {

		return build( nameFilter, tagFilter, precision, aggregate, false, false, false, false, 0, null ) ;

	}

	@CrossOrigin
	@GetMapping ( value = "/metrics/micrometers" )
	public ObjectNode build (
								@RequestParam ( required = false ) String nameFilter ,
								@RequestParam ( required = false ) List<String> tagFilter ,
								@RequestParam ( defaultValue = "2" ) int precision ,
								@RequestParam ( defaultValue = "false" ) boolean aggregate ,
								@RequestParam ( defaultValue = "false" ) boolean details ,
								@RequestParam ( defaultValue = "false" ) boolean encode ,

								// Support for podProxy
								@RequestParam ( defaultValue = "false" ) boolean alertDefinition ,
								@RequestParam ( defaultValue = "false" ) boolean alertReport ,
								@RequestParam ( defaultValue = "0" ) int hours ,

								HttpServletRequest request ) {

		// Support for podProxy
		if ( alertController != null && alertReport ) {

			return alertController.report( hours, 0 ) ;

		}

		if ( alertController != null && alertDefinition ) {

			var defObject = jacksonMapper.createObjectNode( ) ;
			defObject.set( "definitions", alertController.definitions( ) ) ;
			return defObject ;

		}

		ObjectNode meterReport = healthReport.buildAlertReport( request ) ;

		addMeters( meterReport,
				nameFilter,
				tagFilter,
				precision,
				details,
				encode ) ;

		if ( ! aggregate ) {

			return meterReport ;

		}

		return buildSummaryReport( precision, meterReport ) ;

	}

	public void deleteMeterForTests ( String startsWithPattern ) {

		microMeterRegistry
				.getMeters( )
				.stream( )
				.filter( meter -> meter.getId( ).getName( ).startsWith( startsWithPattern ) )
				.forEach( meter -> {

					logger.debug( "{} interfaces: {}", meter.getId( ).getName( ), Arrays.asList( meter.getClass( )
							.getInterfaces( ) ) ) ;
					microMeterRegistry.remove( meter ) ;

				} ) ;

	}

	private void addMeters (
								ObjectNode meterReport ,
								String nameFilter ,
								List<String> tagFilter ,
								int precision ,
								boolean details ,
								boolean encode ) {

		microMeterRegistry.getMeters( ).stream( )
				.filter( meter -> {

					if ( StringUtils.isNotEmpty( nameFilter ) && ! meter.getId( ).getName( ).matches(
							nameFilter ) ) {

						return false ;

					}

					return true ;

				} )
				.filter( meter -> {

					if ( tagFilter == null ) {

						return true ;

					}

					// // bug in tag listener seems to miss some tags
					//
					// if ( tagFilter.contains( CSAP_COLLECTION_TAG ) ) {
					// Optional<String> alwaysCollectForCsap = ALWAYS_COLLECTED_PREFIXES.stream()
					// .filter( alwaysPrefix -> meter.getId().getName().startsWith( alwaysPrefix ) )
					// .findFirst() ;
					// if (alwaysCollectForCsap.isPresent()) {
					// return true;
					// }
					// }

					Optional<String> meterMatch = tagFilter.stream( )
							.filter( filterTag -> {

								Optional<Tag> matchedMeter = meter.getId( ).getTags( ).stream( )
										.filter( meterTag -> meterTag.getKey( ).matches( filterTag ) || meterTag
												.getValue( ).matches( filterTag ) )
										.findFirst( ) ;
								// !aggregateId.contains( tag )
								return matchedMeter.isPresent( ) ;

							} )
							.findFirst( ) ;

					return meterMatch.isPresent( ) ;

				} )
				.forEach( meter -> {

					boolean hideCsapTag = tagFilter != null && tagFilter.contains(
							CsapMicroRegistryConfiguration.CSAP_COLLECTION_TAG ) ;

					String aggregateId = CsapMeterUtilities.buildMicroMeterId( meter, hideCsapTag, encode ) ;

					addMicroMeter( meterReport, meter, aggregateId, precision, details ) ;

				} ) ;

	}

	public static Stream<String> asStreamHandleNulls ( ObjectNode jsonTree ) {

		// handle empty lists
		if ( jsonTree == null ) {

			return ( new ArrayList<String>( ) ).stream( ) ;

		}

		return asStream( jsonTree.fieldNames( ) ) ;

	}

	public static <T> Stream<T> asStream ( Iterator<T> sourceIterator ) {

		return asStream( sourceIterator, false ) ;

	}

	public static <T> Stream<T> asStream ( Iterator<T> sourceIterator , boolean parallel ) {

		Iterable<T> iterable = ( ) -> sourceIterator ;
		return StreamSupport.stream( iterable.spliterator( ), parallel ) ;

	}

	private ObjectNode buildSummaryReport ( int precision , ObjectNode meterReport ) {

		ObjectNode summaryReport = jacksonMapper.createObjectNode( ) ;

		logger.debug( "precision: {}", precision ) ;

		//
		// Add ALL non matches
		//
		asStreamHandleNulls( meterReport )
				.filter( collectedName -> {

					Optional<String> filteredCategory = AGGREGATE_CATEGORIES.stream( )
							.filter( filter -> collectedName.startsWith( filter ) )
							.findFirst( ) ;
					return filteredCategory.isEmpty( ) ;

				} )
				.forEach( name -> summaryReport.set( name, meterReport.get( name ) ) ) ;

		//
		// Aggregate ALL matches together for consolidated output
		//
		asStreamHandleNulls( meterReport )
				.filter( collectedName -> {

					Optional<String> filteredCategory = AGGREGATE_CATEGORIES.stream( )
							.filter( filter -> collectedName.startsWith( filter ) )
							.findFirst( ) ;
					return filteredCategory.isPresent( ) ;

				} )
				.forEach( meterName -> {

					String summaryName = meterName ;

					if ( summaryName.contains( "[" ) ) {

						summaryName = meterName.substring( 0, meterName.indexOf( "[" ) ) ;

					}

					if ( summaryName.startsWith( "jvm.memory" ) ) {

						if ( meterName.contains( "area=nonheap" ) ) {

							summaryName = "csap.nonheap." + summaryName ;

						} else {

							summaryName = "csap.heap." + summaryName ;

						}

					}

					String reportName = summaryName ;

					JsonNode metricData = summaryReport.path( reportName ) ;

					logger.debug( "reportName: {}, meterName: {}, metricData: {}, \n summaryReport: {}",
							reportName, meterName, metricData, summaryReport ) ;

					// if ( meterName.startsWith( "log4j" ) ) {
					// logger.info( "reportName: {}, meterName: {}, metricData: {}, missing: {}, \n
					// summaryReport: {}",
					// reportName, meterName, metricData, metricData.isMissingNode(), summaryReport
					// ) ;
					// }

					if ( metricData.isMissingNode( ) ) {

						// insert the first item
						summaryReport.set( reportName, meterReport.get( meterName ) ) ;

					} else {
						// add the second.

						if ( metricData.isObject( ) ) {

							asStreamHandleNulls( (ObjectNode) metricData )
									.forEach( metricField -> {

										try {

											var total = meterReport.path( meterName ).path( metricField )
													.asDouble( )
													+ summaryReport.path( reportName ).path( metricField )
															.asDouble( ) ;

											if ( metricField.startsWith( "total" ) || metricField.equals(
													AlertFields.count.jsonKey( ) ) ) {

												( (ObjectNode) summaryReport.path( reportName ) ).put( metricField,
														CSAP.roundIt( total, precision ) ) ;

											} else {

												// other fields are invalid
												( (ObjectNode) summaryReport.path( reportName ) ).put( metricField,
														-1 ) ;

											}

										} catch ( Exception e ) {

											logger.warn( "Failed parsing: {}, {}", metricField, e ) ;

										}

									} ) ;

						} else {

							// add the value node:
							var total = meterReport.get( meterName ).asDouble( )
									+ summaryReport.path( reportName ).asDouble( ) ;
							summaryReport.put( reportName, CSAP.roundIt( total, precision ) ) ;

							// ((ObjectNode) summaryReport.path( reportName )).put( metricField, total ) ;
						}

					}

				} ) ;

		// handle major/minor gc
		asStreamHandleNulls( meterReport )
				.filter( collectedName -> collectedName.startsWith( "jvm.gc.pause" ) )
				.forEach( meterName -> {

					var summaryName = "csap.jvm.gc.pause." ;
					var isMinor = true ;

					if ( meterName.contains( "major" ) ) {

						isMinor = false ;
						summaryName += "major" ;

					} else {

						summaryName += "minor" ;

					}

					JsonNode summaryMetricData = summaryReport.path( summaryName ) ;

					logger.debug(
							"\n summaryName: {}, \n summaryMetricData: {}, \n\n meterName: {},  \n meterReport: {}",
							summaryName, summaryMetricData, meterName, meterReport.path( meterName ) ) ;

					if ( summaryMetricData.isMissingNode( ) ) {

						// need to clone to preserve orginal for jvm stats
						ObjectNode cloneReport = jacksonMapper.createObjectNode( ) ;
						cloneReport.setAll( (ObjectNode) meterReport.path( meterName ) ) ;
						summaryReport.set( summaryName, cloneReport ) ;

					} else {
						// add the second.

						if ( summaryMetricData.isObject( ) ) {

							var nameForStream = summaryName ;
							asStreamHandleNulls( (ObjectNode) summaryMetricData )
									.forEach( metricField -> {

										try {

											var total = meterReport.path( meterName ).path( metricField )
													.asDouble( )
													+ summaryReport.path( nameForStream ).path( metricField )
															.asDouble( ) ;

											if ( metricField.startsWith( "total" ) || metricField.equals(
													AlertFields.count.jsonKey( ) ) ) {

												( (ObjectNode) summaryReport.path( nameForStream ) ).put(
														metricField,
														CSAP.roundIt( total, precision ) ) ;

											} else {

												// other fields are invalid
												( (ObjectNode) summaryReport.path( nameForStream ) ).put(
														metricField, -1 ) ;

											}

										} catch ( Exception e ) {

											logger.warn( "Failed parsing: {}, {}", metricField, e ) ;

										}

									} ) ;

						} else {

							// add the value node:
							var total = meterReport.get( meterName ).asDouble( )
									+ summaryReport.path( summaryName ).asDouble( ) ;
							summaryReport.put( summaryName, CSAP.roundIt( total, precision ) ) ;

							// ((ObjectNode) summaryReport.path( reportName )).put( metricField, total ) ;
						}

					}

				} ) ;

		return summaryReport ;

	}

	public void addMicroMeter (
								ObjectNode allMetersReport ,
								Meter meter ,
								String aggregateId ,
								int precision ,
								boolean details ) {

		if ( meter == null ) {

			logger.debug( "meter is null - likely not invoked yet" ) ;
			return ;

		}

		var meterReport = buildMeterReport( meter, precision, details ) ;

		if ( meterReport.has( SIMPLE_VALUE ) ) {

			allMetersReport.put( aggregateId, meterReport.path( SIMPLE_VALUE ).asDouble( ) ) ;

		} else {

			allMetersReport.set( aggregateId, meterReport ) ;

		}

	}

	public ObjectNode buildMeterReport (
											Meter meter ,
											int precision ,
											boolean details ) {

		ObjectNode meterReport = jacksonMapper.createObjectNode( ) ;

		if ( details ) {

			ObjectNode detailsReport = meterReport.putObject( "details" ) ;
			detailsReport.put( "description", meter.getId( ).getDescription( ) ) ;
			ObjectNode tags = detailsReport.putObject( "tags" ) ;
			meter.getId( ).getTags( ).stream( ).forEach( tag -> {

				tags.put( tag.getKey( ), tag.getValue( ) ) ;

			} ) ;

		}

		var meterSearch = microMeterRegistry.find( meter.getId( ).getName( ) ) ;

		if ( meterSearch == null ) {

			logger.warn( "Failed to locate meter: {}", meter.getId( ).getName( ) ) ;
			meterReport.put( SIMPLE_VALUE, -1 ) ;
			return meterReport ;

		}

		Timer meterTimer = meterSearch.tags( meter.getId( ).getTags( ) ).timer( ) ;

		if ( meterTimer != null ) {

			// snapshots ensure consistency
			HistogramSnapshot snapShot = meterTimer.takeSnapshot( ) ;

			meterReport.put( AlertFields.count.jsonKey( ), snapShot.count( ) ) ;
			meterReport.put( AlertFields.meanMs.jsonKey( ), CSAP.roundIt( snapShot.mean( TimeUnit.MILLISECONDS ),
					precision ) ) ;
			meterReport.put( AlertFields.totalMs.jsonKey( ), CSAP.roundIt( snapShot.total( TimeUnit.MILLISECONDS ),
					precision ) ) ;

			meterReport.put( AlertFields.bucketMaxMs.jsonKey( ), CSAP.roundIt( snapShot.max(
					TimeUnit.MILLISECONDS ), precision ) ) ;

			for ( ValueAtPercentile valueAtPercentile : snapShot.percentileValues( ) ) {

				meterReport.put( "bucket-" + valueAtPercentile.percentile( ) + "-ms",
						CSAP.roundIt( valueAtPercentile.value( TimeUnit.MILLISECONDS ), precision ) ) ;

			}

			CountAtBucket[] bucketCounts = snapShot.histogramCounts( ) ;

			if ( bucketCounts.length > 0 ) {
				// for ( int i = 0; i < bucketCounts.length; i++ ) { measurements.put( "bucket-"
				// + i, bucketCounts[i].count() ) ; }

				meterReport.put( "buckets", bucketCounts.length ) ;

			}

		} else {

			int numberOfFields = 0 ;

			for ( Iterator<?> i = meter.measure( ).iterator( ); i.hasNext( ); ) {

				i.next( ) ;
				if ( numberOfFields++ > 1 )
					break ;

			}

			if ( numberOfFields == 1 ) {

				double value = meter.measure( ).iterator( ).next( ).getValue( ) ;

				if ( meter.getId( ).getName( ).matches( CONVERT_TO_MB ) ) {

					double newVal = value / CsapMeterUtilities.BYTES_IN_MB ;
					// logger.info( "converted {} bytes to mb: {}", value, newVal );
					value = newVal ;

				}

				meterReport.put( SIMPLE_VALUE, CSAP.roundIt( value, precision ) ) ;

			} else {

				meter.measure( ).forEach( measurement -> {

					// ObjectNode collectedItem = measurements.addObject() ;
					var measureName = measurement.getStatistic( ).name( ).toLowerCase( ) ;

					if ( measureName.equals( "total_time" ) ) {

						measureName = AlertFields.totalMs.jsonKey( ) ;

					}

					meterReport.put( measureName,
							CSAP.roundIt( measurement.getValue( ), precision ) ) ;

					// measurement.get
				} ) ;

			}

		}

		logger.debug( "meterReport: {}", CSAP.jsonPrint( meterReport ) ) ;

		return meterReport ;

	}

	public HealthReport getHealthReport ( ) {

		return healthReport ;

	}

	public void setHealthReport ( HealthReport healthReport ) {

		this.healthReport = healthReport ;

	}

	public void setAlertController ( AlertsController alertController ) {

		this.alertController = alertController ;

	}

}
