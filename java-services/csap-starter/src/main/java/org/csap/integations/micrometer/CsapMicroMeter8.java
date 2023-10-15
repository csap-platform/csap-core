package org.csap.integations.micrometer ;

//package org.csap.integations ;
//
//import java.math.BigDecimal ;
//import java.math.RoundingMode ;
//import java.net.InetAddress ;
//import java.time.Duration ;
//import java.time.LocalDateTime ;
//import java.time.format.DateTimeFormatter ;
//import java.util.ArrayList ;
//import java.util.Arrays ;
//import java.util.Iterator ;
//import java.util.List ;
//import java.util.Optional ;
//import java.util.concurrent.TimeUnit ;
//import java.util.stream.Collectors ;
//import java.util.stream.Stream ;
//import java.util.stream.StreamSupport ;
//
//import javax.servlet.http.HttpServletRequest ;
//
//import org.apache.commons.lang3.StringUtils ;
//import org.aspectj.lang.ProceedingJoinPoint ;
//import org.csap.helpers.CSAP ;
//import org.slf4j.Logger ;
//import org.slf4j.LoggerFactory ;
//import org.springframework.beans.factory.annotation.Autowired ;
//import org.springframework.context.annotation.Bean ;
//import org.springframework.context.annotation.Configuration ;
//import org.springframework.scheduling.annotation.Scheduled ;
//import org.springframework.stereotype.Component ;
//import org.springframework.web.bind.annotation.CrossOrigin ;
//import org.springframework.web.bind.annotation.GetMapping ;
//import org.springframework.web.bind.annotation.RequestMapping ;
//import org.springframework.web.bind.annotation.RequestParam ;
//import org.springframework.web.bind.annotation.RestController ;
//
//import com.fasterxml.jackson.databind.JsonNode ;
//import com.fasterxml.jackson.databind.ObjectMapper ;
//import com.fasterxml.jackson.databind.node.ArrayNode ;
//import com.fasterxml.jackson.databind.node.ObjectNode ;
//
//import io.micrometer.core.aop.TimedAspect ;
//import io.micrometer.core.instrument.Counter ;
//import io.micrometer.core.instrument.Meter ;
//import io.micrometer.core.instrument.Tag ;
//import io.micrometer.core.instrument.Timer ;
//import io.micrometer.core.instrument.config.MeterFilter ;
//import io.micrometer.core.instrument.distribution.CountAtBucket ;
//import io.micrometer.core.instrument.distribution.DistributionStatisticConfig ;
//import io.micrometer.core.instrument.distribution.HistogramSnapshot ;
//import io.micrometer.core.instrument.distribution.ValueAtPercentile ;
//import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;
//
////@Configuration
//public class CsapMicroMeter8 {
//
//	final Logger						logger							= LoggerFactory.getLogger( getClass() ) ;
//
//	public static final String			CSAP_COLLECTION_TAG				= "csap-collection" ;
//
//	private static final String			BASE_URI						= "/csap8" ;
//	private static final Duration		HISTOGRAM_EXPIRY				= Duration.ofSeconds( 60 ) ;
//	private static final Duration		STEP							= Duration.ofSeconds( 30 ) ;
//
//	private static final String			CONVERT_TO_MB					= "tomcat.global.sent.*|jvm.*memory.*|jvm.gc.max.data.size.*|jvm.gc.live.data.size.*" ;
//
//	private static final List<String>	PRUNE_TAGS_FOR_NAME_PREFIXES	= Arrays.asList(
//		"csap." ) ;
//
//	private static final List<String>	NEVER_COLLECTED_PREFIXES		= Arrays.asList(
//		"process.start.time" ) ;
//
//	private static final List<String>	NEVER_COLLECTED_SUFFIXES		= Arrays.asList(
//		".percentile" ) ;
//
//	private static final List<String>	NEVER_COLLECTED_URIS			= Arrays.asList(
//		"/swagger" ) ;
//
//	private static final List<String>	ALWAYS_COLLECTED_PREFIXES		= Arrays.asList(
//		// "http.server.requests", // lots of overhead: use csap instead on targeted urls
//		"csap.",
//		"cache.",
//		"jvm.",
//		"process.",
//		"system.",
//		"tomcat.",
//		"log4j2.events" ) ;
//
//	private static final List<String>	AGGREGATE_CATEGORIES			= Arrays.asList(
//		"http.server.requests",
//		"cache.",
//		"jvm.memory.used",
//		"jvm.memory.max",
//		"jvm.memory.committed",
//		"tomcat.",
//		"log4j2.events" ) ;
//
//
//	//
//	// By default, micrometer @Timed is ONLY supported in SpringMvc: @RestController, etc.
//	// - TimedAspect enables registration in other classes: @JmsListener, etc.
//	//
//	@Bean
//	public TimedAspect timedAspect ( SimpleMeterRegistry registry ) { return new TimedAspect( registry ) ; }
//
//	@Bean
//	public SimpleMeterRegistry buildCsapRegistry () {
//
//		SimpleMeterRegistry simple = new SimpleMeterRegistry() ;
//
//		simple.config()
//
//			// .commonTags( "host", hostId, "service", serviceId )
//
//			// remove unwatched uris
//			.meterFilter( MeterFilter.deny( id -> {
//
//				Optional<String> neverCollect = NEVER_COLLECTED_URIS.stream()
//					.filter( alwaysPrefix -> id.getName().startsWith( alwaysPrefix ) )
//					.findFirst() ;
//
//				return neverCollect.isPresent() ;
//			} ) )
//
//			// remove name prefixes
//			.meterFilter( MeterFilter.deny( id -> {
//
//				Optional<String> neverCollect = NEVER_COLLECTED_PREFIXES.stream()
//					.filter( neverPrefix -> id.getName().startsWith( neverPrefix ) )
//					.findFirst() ;
//
//				return neverCollect.isPresent() ;
//			} ) )
//
//			// remove name suffixes
//			.meterFilter( MeterFilter.deny( id -> {
//
//				Optional<String> neverCollect = NEVER_COLLECTED_SUFFIXES.stream()
//					.filter( neverPrefix -> id.getName().endsWith( neverPrefix ) )
//					.findFirst() ;
//
//				return neverCollect.isPresent() ;
//			} ) )
//
//
//			// remove tags for some names for readability & conciseness
//			.meterFilter( new MeterFilter() {
//				@Override
//				public Meter.Id map ( Meter.Id id ) {
//
//					Optional<String> pruneTags = PRUNE_TAGS_FOR_NAME_PREFIXES.stream()
//						.filter( prunePrefix -> id.getName().startsWith( prunePrefix ) )
//						.findFirst() ;
//
//					if ( pruneTags.isPresent() ) {
//						List<Tag> tags = new ArrayList<>() ;
//						return id.replaceTags( tags ) ;
//					}
//					return id ;
//				}
//
//			} )
//			// add csap collection tags used to limit meters reported
//			.meterFilter( new MeterFilter() {
//				@Override
//				public Meter.Id map ( Meter.Id id ) {
//
//					Optional<String> alwaysCollect = ALWAYS_COLLECTED_PREFIXES.stream()
//						.filter( alwaysPrefix -> id.getName().startsWith( alwaysPrefix ) )
//						.findFirst() ;
//
//					if ( alwaysCollect.isPresent() ) {
//						return id.withTag( Tag.of( CSAP_COLLECTION_TAG, "true" ) ) ;
//					}
//					return id ;
//				}
//
//			} )
//
//			// add 50% and 95% intervals, and max value support
//			.meterFilter( new MeterFilter() {
//				@Override
//				public DistributionStatisticConfig configure ( Meter.Id id, DistributionStatisticConfig config ) {
//
//					return DistributionStatisticConfig.builder()
//						// .percentilesHistogram( true )
//						.percentiles( 0.50, 0.95 )
//						.expiry( HISTOGRAM_EXPIRY )
//						.bufferLength( (int) (HISTOGRAM_EXPIRY.toMillis() / STEP.toMillis()) )
//						.build()
//						.merge( config ) ;
//
//				}
//
//			} ) ;
//		return simple ;
//	}
//
//	@RestController
//	@RequestMapping ( BASE_URI )
//	static public class HealthReport {
//
//		final Logger					logger			= LoggerFactory.getLogger( getClass() ) ;
//
//		@Autowired
//		ObjectMapper					jacksonMapper	= new ObjectMapper() ;
//
//		@Autowired
//		SimpleMeterRegistry				microMeterRegistry ;
//
//		private volatile ArrayNode	errors			= jacksonMapper.createArrayNode() ;
//		private volatile ArrayNode	offLineErrors	= jacksonMapper.createArrayNode() ;
//
//		public enum Report {
//			name( "health-report" ),
//			source( "source" ),
//			undefined( "undefined" ), pending( "pendingFirstInterval" ),
//			healthy( "isHealthy" ),
//			collectionCount( "collectionCount" ), 
//			errors( "errors" ), id("id"), type("type"), description("description"),
//			lastCollected( "lastCollected" );
//
//			public String json ;
//
//			private Report( String jsonField ) { this.json = jsonField ; }
//		}
//
//		private final long SECONDS = 1000 ;
//
//		@Scheduled ( initialDelay = 10 * SECONDS , fixedDelay = 30 * SECONDS )
//		public void update_health_status () {
//
//			// Create a worker store....
//			ArrayNode localErrors = jacksonMapper.createArrayNode() ;
//
//			//
//			// Put all health monitoring calls here.
//			// Note: this is @Scheduled to NEVER block collection thread
//			//
//			// eg. if (db.runQuery() == null ) localErrors.add("DB Query Failed") ;
//			// eg. if ( isMyAppFailingByCallingManyMethods() ) localErrors.add("App Summary check Failed") ;
//
//			// offLineErrors cleared/set by external thread(s)
//			if ( getOffLineErrors().size() > 0 ) {
//				localErrors.addAll( getOffLineErrors() ) ;
//			}
//
//			// Finally update for subsequent reports
//			setErrors( localErrors ) ;
//
//		}
//		
//		/**
//		 * 
//		 * @param errorId: used for throttling content
//		 * @param type: general classification (short)
//		 * @param message: longer
//		 * @return
//		 */
//		public ObjectNode buildError(String errorId, String type, String message) {
//			 ObjectNode error = jacksonMapper.createObjectNode() ;
//			 error.put( Report.id.json, errorId ) ;
//			 error.put( Report.type.json, type ) ;
//			 error.put( Report.description.json, message ) ; 
//			 return error ;
//		}
//
//		@GetMapping ( "/health/report" )
//		public ObjectNode build ( HttpServletRequest request ) {
//
//			ObjectNode	healthReport	= jacksonMapper.createObjectNode() ;
//			ObjectNode	latestReport	= healthReport.putObject( Report.name.json ) ;
//
//			Counter		reportCount		= microMeterRegistry.counter( getClass().getSimpleName() + ".count" ) ;
//			reportCount.increment() ;
//			latestReport.put( Report.collectionCount.json, reportCount.count() ) ;
//
//			latestReport.put( Report.lastCollected.json, getTimestamp() ) ;
//
//			latestReport.put( Report.healthy.json, errors.size() == 0 ) ;
//
//			// latestReport.putArray( Report.undefined.json );
//			// latestReport.putArray( Report.pending.json );
//			latestReport.set( Report.errors.json, errors ) ;
//			latestReport.set( Report.source.json, buildSourceReport( request ) ) ;
//
//			return healthReport ;
//		}
//
//		public String getTimestamp () { return LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss, MMM d" ) ) ; }
//
//		@GetMapping ( "/health/test/add" )
//		public ObjectNode addTestError ( HttpServletRequest request ) {
//			
//			offLineErrors.add( buildError( "test.id", "test-demo", getTimestamp() + " Added demo issue" )) ;
//			update_health_status() ;
//			return build( request ) ;
//			
//		}
//
//		@GetMapping ( "/health/test/clear" )
//		public ObjectNode clear ( HttpServletRequest request ) {
//			getOffLineErrors().removeAll() ;
//			update_health_status() ;
//			return build( request ) ;
//		}
//
//		public ArrayNode getErrors () { return errors ; }
//
//		public void setErrors ( ArrayNode errors ) { this.errors = errors ; }
//
//		private ObjectNode buildSourceReport ( HttpServletRequest request ) {
//			ObjectNode source = jacksonMapper.createObjectNode() ;
//			source.put( "collected-at", LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss, MMMM d uuuu" ) ) ) ;
//			try {
//				source.put( "hostName", InetAddress.getLocalHost().getHostName() ) ;
//			} catch ( Exception e1 ) {}
//
//			if ( request != null ) {
//
//				String req = request.getRequestURL().toString() ;
//				if ( request.getQueryString() != null ) {
//					req = request.getRequestURL().toString() + "?" + request.getQueryString() ;
//				}
//				source.put( "url-requested", req ) ;
//				source.put( "sample-params", request.getRequestURL().toString()
//						+ "?aggregate=true&nameFilter=jvm.*&details=false&precision=2&tagFilter=state&tagFilter="
//						+ CSAP_COLLECTION_TAG ) ;
//				source.put( "sample-http", request.getRequestURL().toString()
//						+ "?nameFilter=http.server.*&details=true&encode=true&precision=2&" ) ;
//
//			}
//
//			return source ;
//		}
//
//		public ArrayNode getOffLineErrors () { return offLineErrors ; }
//
//		public void setOffLineErrors ( ArrayNode offLineErrors ) { this.offLineErrors = offLineErrors ; }
//
//	}
//
//	@RestController
//	@RequestMapping ( BASE_URI )
//	static public class MeterReport {
//
//		final Logger		logger			= LoggerFactory.getLogger( getClass() ) ;
//
//		// @Autowired
//		ObjectMapper		jacksonMapper	= new ObjectMapper() ;
//
//		@Autowired
//		SimpleMeterRegistry	microMeterRegistry ;
//
//		@Autowired
//		HealthReport		healthReport ;
//
//		@CrossOrigin
//		@GetMapping ( value = "/metrics/micrometers" )
//		public ObjectNode build (
//									@RequestParam ( required = false ) String nameFilter,
//									@RequestParam ( required = false ) List<String> tagFilter,
//									@RequestParam ( defaultValue = "2" ) int precision,
//									@RequestParam ( defaultValue = "false" ) boolean aggregate,
//									@RequestParam ( defaultValue = "false" ) boolean details,
//									@RequestParam ( defaultValue = "false" ) boolean encode,
//									HttpServletRequest request ) {
//
//			ObjectNode meterReport = healthReport.build( request ) ;
//
//			addMeters( meterReport,
//				nameFilter,
//				tagFilter,
//				precision,
//				details,
//				encode ) ;
//
//			if ( !aggregate ) {
//				return meterReport ;
//			}
//
//			return buildSummaryReport( precision, meterReport ) ;
//		}
//
//		public void deleteMeterForTests ( String startsWithPattern ) {
//
//			microMeterRegistry
//				.getMeters()
//				.stream()
//				.filter( meter -> meter.getId().getName().startsWith( startsWithPattern ) )
//				.forEach( meter -> {
//
//					logger.debug( "{} interfaces: {}", meter.getId().getName(), Arrays.asList( meter.getClass().getInterfaces() ) ) ;
//					microMeterRegistry.remove( meter ) ;
//
//				} ) ;
//
//		}
//
//		private void addMeters (	ObjectNode meterReport,
//									String nameFilter,
//									List<String> tagFilter,
//									int precision,
//									boolean details,
//									boolean encode ) {
//
//			microMeterRegistry.getMeters().stream()
//				.filter( meter -> {
//					if ( StringUtils.isNotEmpty( nameFilter ) && !meter.getId().getName().matches( nameFilter ) ) {
//						return false ;
//					}
//					return true ;
//				} )
//				.filter( meter -> {
//					if ( tagFilter == null ) {
//						return true ;
//					}
//
//					// // bug in tag listener seems to miss some tags
//					//
//					// if ( tagFilter.contains( CSAP_COLLECTION_TAG ) ) {
//					// Optional<String> alwaysCollectForCsap = ALWAYS_COLLECTED_PREFIXES.stream()
//					// .filter( alwaysPrefix -> meter.getId().getName().startsWith( alwaysPrefix ) )
//					// .findFirst() ;
//					// if (alwaysCollectForCsap.isPresent()) {
//					// return true;
//					// }
//					// }
//
//					Optional<String> meterMatch = tagFilter.stream()
//						.filter( filterTag -> {
//							Optional<Tag> matchedMeter = meter.getId().getTags().stream()
//								.filter( meterTag -> meterTag.getKey().matches( filterTag ) || meterTag.getValue().matches( filterTag ) )
//								.findFirst() ;
//							// !aggregateId.contains( tag )
//							return matchedMeter.isPresent() ;
//						} )
//						.findFirst() ;
//
//					return meterMatch.isPresent() ;
//				} )
//				.forEach( meter -> {
//
//					boolean hideCsapTag = tagFilter != null && tagFilter.contains( CSAP_COLLECTION_TAG ) ;
//
//					String aggregateId = Helpers.buildMicroMeterId( meter, hideCsapTag, encode ) ;
//
//					addMicroMeter( meterReport, meter, aggregateId, precision, details ) ;
//
//				} ) ;
//
//		}
//
//		public static Stream<String> asStreamHandleNulls ( ObjectNode jsonTree ) {
//
//			// handle empty lists
//			if ( jsonTree == null ) {
//				return (new ArrayList<String>()).stream() ;
//			}
//
//			return asStream( jsonTree.fieldNames() ) ;
//		}
//
//		public static <T> Stream<T> asStream ( Iterator<T> sourceIterator ) { return asStream( sourceIterator, false ) ; }
//
//		public static <T> Stream<T> asStream ( Iterator<T> sourceIterator, boolean parallel ) {
//			Iterable<T> iterable = () -> sourceIterator ;
//			return StreamSupport.stream( iterable.spliterator(), parallel ) ;
//		}
//
//		private ObjectNode buildSummaryReport ( int precision, ObjectNode meterReport ) {
//			ObjectNode summaryReport = jacksonMapper.createObjectNode() ;
//
//			//
//			// Add ALL non matches
//			//
//			asStreamHandleNulls( meterReport )
//				.filter( collectedName -> {
//					Optional<String> filteredCategory = AGGREGATE_CATEGORIES.stream()
//						.filter( filter -> collectedName.startsWith( filter ) )
//						.findFirst() ;
//					return !filteredCategory.isPresent() ;
//				} )
//				.forEach( name -> summaryReport.set( name, meterReport.get( name ) ) ) ;
//
//			//
//			// Aggregate ALL matches together for consolidated output
//			//
//			asStreamHandleNulls( meterReport )
//				.filter( collectedName -> {
//					Optional<String> filteredCategory = AGGREGATE_CATEGORIES.stream()
//						.filter( filter -> collectedName.startsWith( filter ) )
//						.findFirst() ;
//					return filteredCategory.isPresent() ;
//				} )
//				.forEach( meterName -> {
//
//					String summaryName = meterName ;
//					if ( summaryName.contains( "[" ) ) {
//						summaryName = meterName.substring( 0, meterName.indexOf( "[" ) ) ;
//					}
//
//					if ( summaryName.startsWith("jvm.memory") ) {
//						if (meterName.contains( "area=nonheap" )) {
//							summaryName="csap.nonheap." + summaryName;
//						} else {
//							summaryName="csap.heap." + summaryName;
//						}
//					}
//					String	reportName	= summaryName ;
//
//					JsonNode metricData	= summaryReport.path( reportName ) ;
//
//					logger.debug( "reportName: {}, meterName: {}, metricData: {}, \n summaryReport: {}",
//						reportName, meterName, metricData, summaryReport ) ;
//
//					// if ( meterName.startsWith( "log4j" ) ) {
//					// logger.info( "reportName: {}, meterName: {}, metricData: {}, missing: {}, \n summaryReport: {}",
//					// reportName, meterName, metricData, metricData.isMissingNode(), summaryReport ) ;
//					// }
//
//					if ( metricData.isMissingNode() ) {
//						// insert the first item
//						summaryReport.set( reportName, meterReport.get( meterName ) ) ;
//
//					} else {
//						// add the second.
//
//						if ( metricData.isObject() ) {
//
//							asStreamHandleNulls( (ObjectNode) metricData )
//								.forEach( metricField -> {
//									try {
//										double total = meterReport.path( meterName ).path( metricField ).asDouble()
//												+ summaryReport.path( reportName ).path( metricField ).asDouble() ;
//										((ObjectNode) summaryReport.path( reportName )).put( metricField, roundIt( total, precision ) ) ;
//									} catch ( Exception e ) {
//										logger.warn( "Failed parsing: {}, {}", metricField, e ) ;
//									}
//								} ) ;
//						} else {
//							// add the value node:
//							double total = meterReport.get( meterName ).asDouble()
//									+ summaryReport.path( reportName ).asDouble() ;
//							summaryReport.put( reportName, roundIt( total, precision ) ) ;
//							// ((ObjectNode) summaryReport.path( reportName )).put( metricField, total ) ;
//						}
//					}
//				} ) ;
//			
//			// handle major/minor gc
//			asStreamHandleNulls( meterReport )
//				.filter( collectedName -> collectedName.startsWith( "jvm.gc.pause" ) )
//				.forEach( meterName -> {
//
//					String summaryName = "csap.jvm.gc.pause." ;
//					boolean isMinor = true ;
//					if ( meterName.contains( "major" ) ) {
//						isMinor = false ;
//						summaryName +="major" ;
//					} else {
//						summaryName +="minor" ;
//					}
//					
//
//					JsonNode summaryMetricData	= summaryReport.path( summaryName ) ;
//
//					logger.debug( "\n summaryName: {}, \n summaryMetricData: {}, \n\n meterName: {},  \n meterReport: {}",
//						summaryName, summaryMetricData, meterName,  meterReport.path( meterName ) ) ;
//
//					if ( summaryMetricData.isMissingNode() ) {
//						// need to clone to preserve orginal for jvm stats
//						ObjectNode cloneReport = jacksonMapper.createObjectNode() ;
//						cloneReport.setAll( (ObjectNode) meterReport.path( meterName )  ) ;
//						summaryReport.set( summaryName, cloneReport ) ;
//
//					} else {
//						// add the second.
//
//						if ( summaryMetricData.isObject() ) {
//							String nameForStream = summaryName ;
//							asStreamHandleNulls( (ObjectNode) summaryMetricData )
//								.forEach( metricField -> {
//									try {
//										double total = meterReport.path( meterName ).path( metricField ).asDouble()
//												+ summaryReport.path( nameForStream ).path( metricField ).asDouble() ;
//										
//										if ( metricField.startsWith( "total" ) || metricField.equals( "count" )) {
//											((ObjectNode) summaryReport.path( nameForStream )).put( metricField,
//												CSAP.roundIt( total, precision ) ) ;
//										} else {
//											// other fields are invalid
//											((ObjectNode) summaryReport.path( nameForStream )).put( metricField, -1 ) ;
//										}
//									} catch ( Exception e ) {
//										logger.warn( "Failed parsing: {}, {}", metricField, e ) ;
//									}
//								} ) ;
//						} else {
//							// add the value node:
//							double total = meterReport.get( meterName ).asDouble()
//									+ summaryReport.path( summaryName ).asDouble() ;
//							summaryReport.put( summaryName, CSAP.roundIt( total, precision ) ) ;
//							// ((ObjectNode) summaryReport.path( reportName )).put( metricField, total ) ;
//						}
//					}
//				} ) ;
//
//			return summaryReport ;
//		}
//
//		private double roundIt ( double toBeTruncated, int precision ) {
//
//			double result = 0 ;
//			
//			try {
//				result = BigDecimal.valueOf( toBeTruncated )
//					.setScale( precision, RoundingMode.HALF_UP )
//					.doubleValue() ;
//			} catch ( Exception e ) {
//				logger.debug( "Failed to convert: {}, {} {}", toBeTruncated, precision, e);
//			}
//
//			return result ;
//		}
//
//		private void addMicroMeter (	ObjectNode meterReport,
//										Meter meter,
//										String aggregateId,
//										int precision,
//										boolean details ) {
//
//			ObjectNode measurements = meterReport.putObject( aggregateId ) ;
//
//			if ( details ) {
//				ObjectNode detailsReport = measurements.putObject( "details" ) ;
//				detailsReport.put( "description", meter.getId().getDescription() ) ;
//				ObjectNode tags = detailsReport.putObject( "tags" ) ;
//				meter.getId().getTags().stream().forEach( tag -> {
//					tags.put( tag.getKey(), tag.getValue() ) ;
//				} ) ;
//			}
//
//			Timer meterTimer = microMeterRegistry.find( meter.getId().getName() ).tags( meter.getId().getTags() ).timer() ;
//			if ( meterTimer != null ) {
//
//				// snapshots ensure consistency
//				HistogramSnapshot snapShot = meterTimer.takeSnapshot() ;
//
//				measurements.put( "count", snapShot.count() ) ;
//				measurements.put( "mean-ms", roundIt( snapShot.mean( TimeUnit.MILLISECONDS ), precision ) ) ;
//				measurements.put( "total-ms", roundIt( snapShot.total( TimeUnit.MILLISECONDS ), precision ) ) ;
//
//				measurements.put( "bucket-max-ms", roundIt( snapShot.max( TimeUnit.MILLISECONDS ), precision ) ) ;
//				for ( ValueAtPercentile valueAtPercentile : snapShot.percentileValues() ) {
//					measurements.put( "bucket-" + valueAtPercentile.percentile() + "-ms",
//						roundIt( valueAtPercentile.value( TimeUnit.MILLISECONDS ), precision ) ) ;
//				}
//
//				CountAtBucket[] bucketCounts = snapShot.histogramCounts() ;
//
//				if ( bucketCounts.length > 0 ) {
//					// for ( int i = 0; i < bucketCounts.length; i++ ) { measurements.put( "bucket-" + i, bucketCounts[i].count() ) ; }
//
//					measurements.put( "buckets", bucketCounts.length ) ;
//				}
//
//			} else {
//
//				int numberOfFields = 0 ;
//				for ( Iterator<?> i = meter.measure().iterator(); i.hasNext(); ) {
//					i.next() ;
//					if ( numberOfFields++ > 1 )
//						break ;
//				}
//
//				if ( numberOfFields == 1 ) {
//					double value = meter.measure().iterator().next().getValue() ;
//					if ( meter.getId().getName().matches( CONVERT_TO_MB ) ) {
//						double newVal = value / Helpers.BYTES_IN_MB ;
//						// logger.info( "converted {} bytes to mb: {}", value, newVal );
//						value = newVal ;
//					}
//					meterReport.put( aggregateId,
//						roundIt( value, precision ) ) ;
//
//				} else {
//					meter.measure().forEach( measurement -> {
//						// ObjectNode collectedItem = measurements.addObject() ;
//						String measureName = measurement.getStatistic().name().toLowerCase() ;
//						if ( measureName.equals( "total_time" ) ) {
//							measureName = "total-ms" ;
//						}
//						measurements.put( measureName,
//							roundIt( measurement.getValue(), precision ) ) ;
//						// measurement.get
//					} ) ;
//				}
//
//				logger.debug( "aggregateId: {},  value: {}", aggregateId, meterReport.path( aggregateId ).toString() ) ;
//
//			}
//		}
//
//	}
//
//	@Component
//	static public class Helpers {
//		
//
//		static final double BYTES_IN_MB = 1024 * 1024 ;
//		
//		@Autowired
//		SimpleMeterRegistry simpleMeterRegistry ;
//		
//		public Object timedExecution ( ProceedingJoinPoint pjp, String desc )
//				throws Throwable {
//	
//			String			metricName	= desc + pjp.getTarget().getClass().getSimpleName()
//					+ "." + pjp.getSignature().getName()  ;
//	
//			// Timer timer = Metrics.globalRegistry.timer( timerId, new ArrayList<>() ) ;
//			Timer.Sample	sample		= Timer.start( simpleMeterRegistry ) ;
//	
//			try {
//				return pjp.proceed() ;
//			} catch ( Exception ex ) {
//				// exceptionClass = ex.getClass().getSimpleName();
//				throw ex ;
//			} finally {
//				try {
//					sample.stop( Timer.builder( metricName )
//						.description( "executeMicroMeter timed" )
//						.register( simpleMeterRegistry ) ) ;
//				} catch ( Exception e ) {
//					// ignoring on purpose
//				}
//			}
//	
//		}
//		
//		public MeterFilter addCsapCollectionTag(String meterNamePattern) {
//			return new MeterFilter() {
//				@Override
//				public Meter.Id map ( Meter.Id id ) {
//
//					if ( id.getName().matches( meterNamePattern )) {
//						return id.withTag( Tag.of( CsapMicroMeter.CSAP_COLLECTION_TAG, "true" ) ) ;
//					}
//					return id ;
//				} 
//			};
//		}
//		
//
//
//		static public String buildMicroMeterId ( Meter meter, boolean isHideCsapTag, boolean encode ) {
//
//			StringBuilder id = new StringBuilder( meter.getId().getName() ) ;
//
//			if ( meter.getId().getName().matches( CONVERT_TO_MB ) ) {
//				id.append( ".mb" ) ;
//			}
//			List<Tag>	tags	= meter.getId().getTags() ;
//			String		tagInfo	= "" ;
//			if ( !tags.isEmpty() ) {
//				tagInfo = tags.stream()
//					.filter( tag -> {
//						// exception=None,method=GET,outcome=SUCCESS,status=200,uri=/**/*.css
//						if ( tag.getKey().equals( "exception" ) && tag.getValue().equals( "None" ) )
//							return false ;
//						if ( isHideCsapTag && tag.getKey().equals( CSAP_COLLECTION_TAG ) )
//							return false ;
//						if ( tag.getKey().equals( "method" ) && tag.getValue().equals( "GET" ) )
//							return false ;
//						if ( tag.getKey().equals( "outcome" ) && tag.getValue().equals( "SUCCESS" ) )
//							return false ;
//						if ( tag.getKey().equals( "outcome" ) && tag.getValue().equals( "REDIRECTION" ) )
//							return false ;
//						if ( tag.getKey().equals( "status" ) && tag.getValue().equals( "200" ) )
//							return false ;
//						return true ;
//					} )
//					.map( tag -> {
//						if ( tag.getKey().equals( "uri" ) || tag.getKey().equals( "name" ) )
//							return tag.getValue() ;
//						return tag.getKey() + "=" + tag.getValue() ;
//					} )
//					.collect( Collectors.joining( "," ) ) ;
//
//			}
//
//			if ( tagInfo.length() > 0 ) {
//				id.append( "[" ).append( tagInfo ).append( "]" ) ;// + tagInfo + "]" ;
//			}
//
//			String result = id.toString() ;
//			if ( encode ) {
//				result = result.replaceAll( "/", "_" ) ;
//			}
//
//			return result ;
//		}
//
//	}
//
//}
