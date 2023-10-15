package org.sample.input.http.ui.rest ;

import java.text.DecimalFormat ;
import java.util.List ;

import javax.inject.Inject ;

import org.apache.commons.dbcp2.BasicDataSource ;
import org.csap.helpers.CSAP ;
import org.sample.Csap_Tester_Application ;
import org.sample.DatabaseSettings ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.jdbc.core.JdbcTemplate ;
import org.springframework.jdbc.core.RowMapper ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RestController ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

@RestController
@RequestMapping ( Csap_Tester_Application.DB_MONITOR_URL )
public class PostgressMonitoring {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	static final public String DB_STATS_URL = "/postgress" ;

	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	@Inject
	DatabaseSettings databaseSettings ;

	@Inject
	BasicDataSource helloDataSource ;

	@Autowired
	JdbcTemplate jdbcTemplate ;

	@GetMapping ( DB_STATS_URL )
	public JsonNode getDbStatistics ( ) {

		ObjectNode performanceData = jsonMapper.createObjectNode( ) ;
		performanceData.put( "request", "perform collection" ) ;

		ObjectNode settings = jsonMapper.convertValue( databaseSettings, ObjectNode.class ) ;

		performanceData.set( "settings", settings ) ;

		settings.put( "password", "masked" ) ;

		if ( settings.get( "driverClassName" ).asText( ).contains( "postgres" ) &&
				databaseSettings.getMonitoringSql( ) != null ) {

			addSizeMetrics( performanceData ) ;

			performanceData.set( "statsByUser", buildStatsForDatabases( ) ) ;

			performanceData.set( "indexByTable", buildStats_TopIndexes( ) ) ;

			performanceData.set( "heapCache", buildStats_heapCache( ) ) ;

			performanceData.set( "indexCache", buildStats_indexCache( ) ) ;

		} else {

			performanceData.put( "request",
					"perform collection. Error: not possible - verify sql profile is enabled" ) ;

		}

		return performanceData ;

	}

	private void addSizeMetrics ( ObjectNode performanceData ) {

		Long tableSize = jdbcTemplate.queryForObject(
				databaseSettings.getMonitoringSql( ).getTableSize( ),
				new Object[] {}, Long.class ) ;

		performanceData.put( "tableSizeInKb", tableSize.longValue( ) / 1024 ) ;

		Long indexSize = jdbcTemplate.queryForObject(
				databaseSettings.getMonitoringSql( ).getIndexSize( ),
				new Object[] {}, Long.class ) ;

		performanceData.put( "indexSizeInKb", indexSize.longValue( ) / 1024 ) ;

	}

	private ObjectNode buildStats_indexCache ( ) {

		ObjectNode indexCacheStats ;

		try {

			indexCacheStats = jdbcTemplate.queryForObject(
					databaseSettings.getMonitoringSql( ).getCacheIndex( ),
					( rs , rowNum ) -> {

						ObjectNode perfRow = buildCacheHitStats(
								rs.getLong( "reads" ),
								rs.getLong( "hits" ),
								rs.getDouble( "ratio" ) ) ;

						return perfRow ;

					} ) ;

		} catch ( Exception e ) {

			indexCacheStats = buildCacheHitStats( -1, -1, 0.0 ) ;
			indexCacheStats.put( "error", true ) ;
			logger.warn( "Failed querying {} reason: {}",
					databaseSettings.getMonitoringSql( ).getCacheIndex( ),
					CSAP.buildFilteredStack( e, "org.sample" ) ) ;

		}

		return indexCacheStats ;

	}

	private ObjectNode buildStats_heapCache ( ) {

		ObjectNode heapCacheStats ;

		try {

			heapCacheStats = jdbcTemplate.queryForObject(
					databaseSettings.getMonitoringSql( ).getCacheHeap( ),
					( rs , rowNum ) -> {

						ObjectNode perfRow = buildCacheHitStats(
								rs.getLong( "reads" ),
								rs.getLong( "hits" ),
								rs.getDouble( "ratio" ) ) ;

						return perfRow ;

					} ) ;

		} catch ( Exception e ) {

			heapCacheStats = buildCacheHitStats( -1, -1, 0.0 ) ;
			heapCacheStats.put( "error", true ) ;
			logger.warn( "Failed querying {} reason: {}",
					databaseSettings.getMonitoringSql( ).getCacheHeap( ),
					CSAP.buildFilteredStack( e, "org.sample" ) ) ;

		}

		return heapCacheStats ;

	}

	private ObjectNode buildStatsForDatabases ( ) {

		ObjectNode statistics = jsonMapper.createObjectNode( ) ;

		RowMapper<ObjectNode> rowMapper = //
				( rs , rowNum ) -> {

					ObjectNode databaseStats = jsonMapper.createObjectNode( ) ;

					databaseStats.put( "tablespace", rs.getString( "datName" ) ) ;
					databaseStats.put( "querys", rs.getLong( "tup_fetched" ) ) ;
					databaseStats.put( "inserts", rs.getLong( "tup_inserted" ) ) ;
					databaseStats.put( "updates", rs.getLong( "tup_updated" ) ) ;
					databaseStats.put( "deletes", rs.getLong( "tup_deleted" ) ) ;
					databaseStats.put( "transactionsCommitted", rs.getLong( "xact_commit" ) ) ;
					databaseStats.put( "transactionsRolledBack", rs.getLong( "xact_rollback" ) ) ;

					logger.debug( "Adding: {}", CSAP.jsonPrint( databaseStats ) ) ;

					return databaseStats ;

				} ;

		List<ObjectNode> dbPerformanceRows = jdbcTemplate.query( databaseSettings.getMonitoringSql( ).getStats( ),
				rowMapper ) ;

		// filter
		String filterPattern = databaseSettings.getMonitoringSql( ).getDbFilterPattern( ) ;

		ObjectNode allDbs = statistics.putObject( "all" ) ;
		dbPerformanceRows.stream( ).forEach( dbStats -> {

			dbStats.fieldNames( ).forEachRemaining( fieldName -> {

				if ( ! fieldName.equals( "tablespace" ) ) {

					long existingStat = allDbs.path( fieldName ).asLong( 0 ) ;
					allDbs.put( fieldName, dbStats.path( fieldName ).asLong( ) + existingStat ) ;

				}

			} ) ;

			if ( dbStats.has( "tablespace" ) && dbStats.path( "tablespace" ).asText( ).matches( filterPattern ) ) {

				statistics.set(
						dbStats.get( "tablespace" ).asText( ),
						dbStats ) ;

			}

		} ) ;

		return statistics ;

	}

	private ObjectNode buildStats_TopIndexes ( ) {

		ObjectNode topIndexStatistics = jsonMapper.createObjectNode( ) ;
		RowMapper<ObjectNode> rowMapper = //
				( rs , rowNum ) -> {

					ObjectNode perfRow = jsonMapper.createObjectNode( ) ;

					perfRow.put( "table", rs.getString( "relname" ) ) ;
					perfRow.put( "indexPercentUsed", rs.getLong( "percent_of_times_index_used" ) ) ;
					perfRow.put( "rowCount", rs.getLong( "rows_in_table" ) ) ;

					return perfRow ;

				} ;

		List<ObjectNode> items = jdbcTemplate.query(
				databaseSettings.getMonitoringSql( ).getIndexUse( ), rowMapper ) ;

		items.stream( ).forEach( tableJson -> {

			topIndexStatistics.set(
					tableJson.get( "table" ).asText( ),
					tableJson ) ;

		} ) ;

		return topIndexStatistics ;

	}

	DecimalFormat decimalFormat2Places = new DecimalFormat( "###.####" ) ;

	private ObjectNode buildCacheHitStats (
											long read ,
											long hit ,
											Double ratio ) {

		ObjectNode perfRow = jsonMapper.createObjectNode( ) ;

		perfRow.put( "reads", read ) ;
		perfRow.put( "hits", hit ) ;
		perfRow.put( "ratio", ratio ) ;
		return perfRow ;

	}
}
