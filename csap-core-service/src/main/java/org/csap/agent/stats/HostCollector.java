package org.csap.agent.stats ;

import java.util.Iterator ;
import java.util.Random ;
import java.util.TimeZone ;
import java.util.concurrent.Executors ;
import java.util.concurrent.ScheduledExecutorService ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.CsapApis ;
import org.csap.agent.integrations.CsapEvents ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public abstract class HostCollector {

	final Logger logger = LoggerFactory.getLogger( HostCollector.class ) ;

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	final private int inMemoryCacheSize ;

	public int getInMemoryCacheSize ( ) {

		return inMemoryCacheSize ;

	}

	public static String ALL_SERVICES = "all" ;

	public static final int SHOW_ALL_DATA = -1 ;

	public final static String DATA_JSON = "data" ;
	public final static String ATTRIBUTES_JSON = "attributes" ;

	protected int publicationInterval ;

	protected CsapApis csapApis = null ;

	// convert to minutes
	public final static int TIME_ZONE_OFFSET = TimeZone.getDefault( ).getRawOffset( ) / 1000 / 60 / 60 ;

	protected int collectionIntervalSeconds ;

	public HostCollector ( CsapApis csapApis,
			int intervalSeconds, boolean publishSummaryAndPerformHeartBeat ) {

		this.inMemoryCacheSize = csapApis.application( ).rootProjectEnvSettings( ).getInMemoryCacheSize( ) ;

		this.collectionIntervalSeconds = intervalSeconds ;

		this.csapApis = csapApis ;

		this.publishSummaryAndPerformHeartBeat = publishSummaryAndPerformHeartBeat ;
		this.publicationInterval = csapApis.application( ).rootProjectEnvSettings( ).getMetricsUploadSeconds(
				intervalSeconds ) ;
		this.SIZE_OF_REPORT_CACHE = 24 * 60 * 60 / publicationInterval ;
		double pubHours = publicationInterval / 3600.0 ;
		double collectMinutes = intervalSeconds / 60.0 ;

		logger.info( "Collector '{}', collection: {} minutes, publication: {} hours",
				this.getClass( ).getSimpleName( ), collectMinutes, pubHours ) ;

	}

	public abstract ObjectNode buildCollectionReport (
														boolean isUpdateSummary ,
														String[] serviceNameArray ,
														int requestedSampleSize ,
														int skipFirstItems ,
														String... customArgs ) ;

	public int getIterationsBetweenUploads ( ) {

		int iterationsBetweenAuditUploads = publicationInterval / collectionIntervalSeconds ;

		// Always a minimum of 1
		if ( iterationsBetweenAuditUploads == 0 )
			iterationsBetweenAuditUploads = 1 ;

		return iterationsBetweenAuditUploads ;

	}

	protected abstract String uploadMetrics ( int iterationsBetweenAuditUploads ) ;

	// seed defaults to System.currentTimeMillis(), which is generally good
	// enough to spread upload requests
	Random rg = new Random( ) ;

	public void resetCollectionCount ( ) {

		logger.debug( "Reseting {}", metricsCollectedSinceLastUpload ) ;
		metricsCollectedSinceLastUpload = 0 ;

	}

	public int getCollectionCount ( ) {

		return metricsCollectedSinceLastUpload ;

	}

	private int incrementCollectionCounter ( ) {

		metricsCollectedSinceLastUpload++ ;

		logger.debug( "increasing {}", metricsCollectedSinceLastUpload ) ;
		return metricsCollectedSinceLastUpload ;

	}

	// private ReentrantLock uploadLock = new ReentrantLock();

	public String uploadMetricsNow ( ) {

		if ( metricsCollectedSinceLastUpload == 0 ) {

			logger.debug( "No items in collection" ) ;
			return "NoItemsToUpload" ;

		}

		String uploadResult = uploadMetrics( metricsCollectedSinceLastUpload ) ;
		resetCollectionCount( ) ;
		return uploadResult ;

	}

	volatile private boolean keepRunning = true ;

	public boolean isKeepRunning ( ) {

		return this.keepRunning ;

	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown ( ) {

		logger.debug( "*************** Shutting down  **********************" ) ;
		this.keepRunning = false ;

		if ( scheduledExecutorService != null ) {

			logger.info( "Shutting down collection for {} interval: {}", this.getClass( ).getSimpleName( ),
					getCollectionIntervalSeconds( ) ) ;
			// try {
			// scheduledExecutorService.awaitTermination( 5, TimeUnit.SECONDS )
			// ;
			// } catch (InterruptedException e) {
			// logger.error( "Shut service down" );
			// }
			scheduledExecutorService.shutdown( ) ;

		}

	}

	volatile private int metricsCollectedSinceLastUpload = 0 ;

	// used for event publication
	public final static String METRICS_EVENT = "/csap/metrics" ;

	protected boolean peformUploadIfNeeded ( ) {

		int collectionCount = incrementCollectionCounter( ) ;

		if ( publicationInterval < 1000 ) {

			logger.warn( "COLLECTION_INTERVAL_SECONDS is low: " + publicationInterval ) ;

		}

		// Hook to avoid lots of concurrent requests hitting
		// service at same time.
		// eg. many agents on many host will be started at same
		// time.
		boolean isUploaded = false ;
		int maximumDelaySeconds = 60 ;
		if ( collectionIntervalSeconds < 65 )
			maximumDelaySeconds = collectionIntervalSeconds - 5 ;

		// logger.info( "Added new Entry for interval: " + intervalSeconds +
		// " lastAddedElementIndex: " + lastAddedElementIndex );

		if ( collectionCount >= getIterationsBetweenUploads( ) ) {

			int waitSeconds = rg.nextInt( maximumDelaySeconds ) ;

			try {

				Thread.sleep( waitSeconds * 1000 ) ;

			} catch ( InterruptedException e ) {

				logger.error( "Failed to Upload Metrics", e ) ;

			}

			uploadMetrics( getIterationsBetweenUploads( ) ) ;
			isUploaded = true ;

			resetCollectionCount( ) ;

		}

		return isUploaded ;

	}

	private ScheduledExecutorService scheduledExecutorService = null ;

	protected void scheduleCollection ( Runnable collector ) {

		// Thread commandThread = new Thread( this );
		// commandThread.start();
		String scheduleName = collector.getClass( ).getSimpleName( ) + "_" + collectionIntervalSeconds ;
		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder( )

				.namingPattern( scheduleName + "-%d" )
				.daemon( true )
				.priority( Thread.NORM_PRIORITY )
				.build( ) ;
		// Single collection thread
		scheduledExecutorService = Executors
				.newScheduledThreadPool( 1, schedFactory ) ;
		int initialSleep = 10 ;

		if ( this.collectionIntervalSeconds >= 60 ) {

			initialSleep += 30 + rg.nextInt( 30 ) ;

		}

		scheduledExecutorService
				.scheduleAtFixedRate( collector, initialSleep, collectionIntervalSeconds, TimeUnit.SECONDS ) ;

		logger.debug( "Adding Job: {}", scheduleName ) ;

	}

	final int SIZE_OF_REPORT_CACHE ;
	private boolean publishSummaryAndPerformHeartBeat = false ;

	public boolean isPublishSummaryAndPerformHeartBeat ( ) {

		return publishSummaryAndPerformHeartBeat ;

	}

	public void setPublishSummaryAndPerformHeartBeat ( boolean publishSummaryAndPerformHeartBeat ) {

		this.publishSummaryAndPerformHeartBeat = publishSummaryAndPerformHeartBeat ;

	}

	volatile ArrayNode summary24HourCache = jacksonMapper.createArrayNode( ) ;
	volatile ArrayNode summary24HourApplicationCache = jacksonMapper.createArrayNode( ) ;

	public JsonNode testSummaryReport ( boolean isSecondary ) {

		return buildSummaryReport( isSecondary ) ;

	}

	protected JsonNode buildSummaryReport ( boolean isSecondary ) {

		// Step 1 - build map with total for services
		ObjectNode summaryTotalJson = jacksonMapper.createObjectNode( ) ;

		ArrayNode collectionData = summary24HourCache ;

		if ( isSecondary ) {

			collectionData = summary24HourApplicationCache ;

		}

		logger.debug( "** intervalReports size: {}, isSecondary: {}", collectionData.size( ), isSecondary ) ;

		var numReports = 0 ;

		for ( JsonNode intervalReport : collectionData ) {

			numReports++ ;

			Iterator<String> fields = intervalReport.fieldNames( ) ;

			while ( fields.hasNext( ) ) {

				String field = fields.next( ) ;

				ObjectNode serviceInterval = (ObjectNode) intervalReport.path( field ) ;

				if ( ! summaryTotalJson.has( field ) ) {

					summaryTotalJson.putObject( field ) ;

				}

				ObjectNode serviceSummaryNode = (ObjectNode) summaryTotalJson.path( field ) ;

				Iterator<String> subFields = serviceInterval.fieldNames( ) ;

				while ( subFields.hasNext( ) ) {

					String subField = subFields.next( ) ;

					if ( field.contains( "csap-test-k8s-service" ) && subField.endsWith( "CsapMean" ) ) {

						logger.debug( "field:{} metric: {}, interval: {} , summary: {}",
								field, subField, serviceInterval.path( subField ).asInt( ), serviceSummaryNode.path(
										subField ).asInt( ) ) ;

					}

					;
					addItemToTotals( serviceInterval, serviceSummaryNode, subField ) ;

				}

			}

		}

		// Step 2 convert to mongo aggregation friendly array
		ArrayNode summaryArray = jacksonMapper.createArrayNode( ) ;
		Iterator<String> serviceNames = summaryTotalJson.fieldNames( ) ;
		var meanReports = numReports ;

		while ( serviceNames.hasNext( ) ) {

			String serviceName = serviceNames.next( ) ;
			ObjectNode serviceItem = summaryArray.addObject( ) ;
			serviceItem.put( "serviceName", serviceName ) ;

			ObjectNode serviceData = (ObjectNode) summaryTotalJson.path( serviceName ) ;
			serviceItem.setAll( serviceData ) ;

			// handle mean calculation
			CSAP.asStreamHandleNulls( serviceItem )
					.filter( fieldName -> fieldName.endsWith( "Mean" ) )
					.forEach( fieldName -> {

						serviceItem.put( fieldName, CSAP.roundIt( serviceItem.path( fieldName ).asDouble( )
								/ meanReports, 1 ) ) ;

					} ) ;

			if ( serviceName.contains( "csap-test-k8s-service" ) ) {

				logger.debug( "field:{} mongoReport: {}",
						serviceName, serviceItem ) ;

			}

			;

		}

		logger.debug( "** Report: {}", summaryArray ) ;

		return summaryArray ;

	}

	/**
	 * jackson apis do not store longs natively...so we need to iterate over data
	 * types.
	 * 
	 * @param itemJson
	 * @param summaryJson
	 * @param fieldName
	 */
	protected void addItemToTotals ( ObjectNode itemJson , ObjectNode summaryJson , String fieldName ) {

		logger.debug( "fieldName: {} int: {}, long: {}", fieldName, itemJson.path( fieldName ).isInt( ),
				itemJson.path( fieldName ).isLong( ) ) ;

		if ( ! summaryJson.has( fieldName ) || fieldName.endsWith( "Avg" ) ) {

			if ( itemJson.path( fieldName ).isInt( ) || itemJson.path( fieldName ).isLong( ) ) {

				summaryJson.put( fieldName,
						itemJson.path( fieldName ).asLong( ) ) ;

			} else {

				summaryJson.put( fieldName,
						itemJson.path( fieldName ).asDouble( ) ) ;

			}

		} else {

			if ( itemJson.path( fieldName ).isInt( ) || itemJson.path( fieldName ).isLong( ) ) {

				summaryJson.put( fieldName,
						itemJson.path( fieldName ).asLong( )
								+ summaryJson.path( fieldName ).asLong( ) ) ;

			} else {

				summaryJson.put( fieldName,
						itemJson.path( fieldName ).asDouble( ) + summaryJson.path( fieldName ).asDouble( ) ) ;

			}

		}

	}

	protected void publishSummaryReport ( String source ) {

		publishSummaryReport( source, false ) ;

	}

	protected void publishSummaryReport ( String source , boolean isSecondary ) {

		if ( isPublishSummaryAndPerformHeartBeat( ) ) {

			ObjectNode summaryReport = jacksonMapper.createObjectNode( ) ;
			summaryReport.set( "summary", buildSummaryReport( isSecondary ) ) ;

			csapApis.events( ).publishEvent( CsapEvents.CSAP_REPORTS_CATEGORY + "/" + source + "/daily",
					"Summary Report",
					null,
					summaryReport ) ;

			// if ( csapApis.application().isJunit() ) {
			// logger.info( "source: {}, summaryReport: {}", source, CSAP.jsonPrint(
			// summaryReport ) ) ;
			// }
		}

	}

	// updateSummary is NOT done if UI is doing a request for data. It IS done
	// when
	// a publish is being done by collector thread.
	protected void addSummary ( ObjectNode summaryReport , boolean isUpdateSummary ) {

		if ( ! isUpdateSummary || ! publishSummaryAndPerformHeartBeat ) {

			return ;

		}

		if ( summary24HourCache.size( ) == 0 ) {

			csapApis.application( ).loadCollectionCacheFromDisk( summary24HourCache, this.getClass( )
					.getSimpleName( ) ) ;

		}

		logger.debug( "size: {}, max: {}, adding: {}", summary24HourCache.size( ), SIZE_OF_REPORT_CACHE,
				summaryReport ) ;

		summary24HourCache.insert( 0, summaryReport ) ;
		if ( summary24HourCache.size( ) > SIZE_OF_REPORT_CACHE )
			summary24HourCache.remove( summary24HourCache.size( ) - 1 ) ;

		if ( csapApis.isShutdown( ) ) {

			csapApis.application( ).flushCollectionCacheToDisk( summary24HourCache, this.getClass( )
					.getSimpleName( ) ) ;

		}

	}

	// Special hook for the double collection in VmApplicationCollector
	protected void addApplicationSummary ( ObjectNode summaryNode , boolean isUpdateSummary ) {

		// many more items are being added here.......
		if ( ! isUpdateSummary || ! publishSummaryAndPerformHeartBeat )
			return ;

		if ( summary24HourApplicationCache.size( ) == 0 ) {

			csapApis.application( ).loadCollectionCacheFromDisk( summary24HourApplicationCache, this.getClass( )
					.getSimpleName( )
					+ "_Secondary" ) ;

		}

		logger.debug( "size: {}, max: {}, adding: {}", summary24HourCache.size( ), SIZE_OF_REPORT_CACHE, summaryNode ) ;

		summary24HourApplicationCache.insert( 0, summaryNode ) ;

		if ( summary24HourApplicationCache.size( ) > SIZE_OF_REPORT_CACHE ) {//

			logger.debug( "Removing item from application cache" ) ;
			summary24HourApplicationCache.remove( summary24HourApplicationCache.size( ) - 1 ) ;

		}

		if ( csapApis.isShutdown( ) ) {

			csapApis.application( )
					.flushCollectionCacheToDisk( summary24HourApplicationCache, this.getClass( ).getSimpleName( )
							+ "_Secondary" ) ;

		}

	}

	public int getCollectionIntervalSeconds ( ) {

		return collectionIntervalSeconds ;

	}

	public void setCollectionIntervalSeconds ( int intervalSeconds ) {

		this.collectionIntervalSeconds = intervalSeconds ;

	}
}
