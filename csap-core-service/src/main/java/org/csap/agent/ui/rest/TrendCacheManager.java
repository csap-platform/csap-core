/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.ui.rest ;

import java.util.ArrayList ;
import java.util.Collections ;
import java.util.Comparator ;
import java.util.List ;
import java.util.concurrent.Callable ;
import java.util.concurrent.ExecutionException ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.PriorityBlockingQueue ;
import java.util.concurrent.RunnableFuture ;
import java.util.concurrent.ThreadPoolExecutor ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.TimeoutException ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.stereotype.Service ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 *
 * @author someDeveloper
 */
@Service
public class TrendCacheManager {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	@Autowired
	TrendCache trendCache ;

	// loading cache consumes a lot of CPU. Only load the initial
	public boolean isInitialLoad ( ObjectNode trendReport ) {

		if ( trendReport.has( NEEDS_LOAD ) ) {

			return true ;

		}

		return false ;

	}

	public final static String NEEDS_LOAD = "needsLoad" ;
	public final static String HASH = "reportHash" ;
	public final static String LAST_UPDATE_TOKEN = "lastUpdateMs" ;
	private final long CACHE_15MINUTE_REFRESH_INTERVAL = 1000 * 60 * 15 ; // hourly
	private final long CACHE_1HOUR_REFRESH_INTERVAL = 1000 * 60 * 60 ; // hourly
	private final long CACHE_24HOURS_REFRESH_INTERVAL = 1000 * 60 * 60 * 24 ; // Daily
	// private final long CACHE_1HOUR_REFRESH_INTERVAL = 1000 * 15; // hourly
	// private final long CACHE_24HOURS_REFRESH_INTERVAL = 1000 * 60; // Daily

	public boolean isRefreshNeeded ( ObjectNode trendReport , int numDays ) {

		logger.debug( "TrendReport: {}", trendReport ) ;

		long limit = CACHE_1HOUR_REFRESH_INTERVAL ;

		if ( numDays > 16 ) {

			limit = CACHE_24HOURS_REFRESH_INTERVAL ;

			// limit = 5000;
		} else if ( numDays <= 1 ) {

			limit = CACHE_15MINUTE_REFRESH_INTERVAL ;

		}

		if ( System.currentTimeMillis( ) - trendReport.get( LAST_UPDATE_TOKEN ).asLong( ) > limit ) {

			if ( trendReport.get( LAST_UPDATE_TOKEN ).asInt( 0 ) != 0 ) {

				trendReport.put( LAST_UPDATE_TOKEN, -1 ) ;

			}

			Integer jobHash = trendReport.get( HASH ).asInt( ) ;

			if ( activeJobs.contains( jobHash ) ) {

				logger.debug( "*** Trend request already in progress: {} ", jobHash ) ;
				return false ;

			} else {

				return true ;

			}

		}

		return false ;

	}

	BasicThreadFactory trendThreadFactory = new BasicThreadFactory.Builder( )
			.namingPattern( "CsapTrendThread-%d" )
			.daemon( true )
			.priority( Thread.MAX_PRIORITY )
			.build( ) ;

	private List<Integer> activeJobs = Collections.synchronizedList( new ArrayList<Integer>( ) ) ;

	static public int MAX_JOBS_ALLOWED = 1000 ; // Post
												// restart
												// -
												// this
												// can
												// get
												// quite
												// large
	// Bounding traffic to analtyics services. During startup - we queue a lot
	// of jobs
	volatile PriorityBlockingQueue<Runnable> trendRequestQueue = new PriorityBlockingQueue<>( MAX_JOBS_ALLOWED,
			new PriorityFutureComparator( ) ) ;

	// final private ExecutorService trendExecutor = new ThreadPoolExecutor( 5,
	// 5,
	// 0L, TimeUnit.MILLISECONDS,
	// trendRequestQueue, trendThreadFactory );
	final private ExecutorService trendExecutor = //
			new ThreadPoolExecutor( 5, 5,
					0L, TimeUnit.MILLISECONDS,
					trendRequestQueue, trendThreadFactory ) {

				@Override
				protected <T> RunnableFuture<T> newTaskFor ( Callable<T> callable ) {

					RunnableFuture<T> newTaskFor = super.newTaskFor( callable ) ;
					return new PriorityFuture<T>( newTaskFor,
							( (TrendCallable) callable ).getPriority( ) ) ;

				}
			} ;

	public void updateInBackground ( String restUrl , int numDays , String timerName ) {

		if ( restUrl.contains( "testNull" ) ) {

			// test stack trace, etc.
			logger.info( "Throwing an exception to verify error handling" ) ;
			boolean throwException = restUrl.contains( "triggerNullPointer" ) ;

		}

		try {

			TrendCallable trendingJob = new TrendCallable( restUrl, numDays, timerName ) ;

			if ( activeJobs.size( ) < ( MAX_JOBS_ALLOWED - 10 ) ) {

				trendExecutor.submit( trendingJob ) ;
				activeJobs.add( TrendCache.buildReportHash( restUrl ) ) ;

			} else {

				logger.warn(
						"{} skipped as current job size {} is close to overflowing max allowed: {}",
						timerName, activeJobs.size( ), MAX_JOBS_ALLOWED ) ;

			}

		} catch ( Exception e ) {

			logger.warn( "Failed to submit trending job: {}", e.getMessage( ) ) ;

		}

	}

	class TrendCallable implements Callable<Long> {

		private int priority ;
		private String restUrl ;
		private String timerName ;

		public TrendCallable ( String restUrl, int priority, String timerName ) {

			this.priority = priority ;
			this.restUrl = restUrl ;
			this.timerName = timerName ;

		}

		public Long call ( )
			throws Exception {

			Integer jobHash = TrendCache.buildReportHash( restUrl ) ;

			try {

				logger.debug( "Q length: {}, Active Jobs: {},  trend request Start: {}, hash {}",
						trendRequestQueue.size( ), activeJobs.size( ), restUrl, jobHash ) ;

				try {

					ObjectNode result = trendCache.update( restUrl, timerName ) ;

					if ( activeJobs.contains( jobHash ) ) {

						activeJobs.remove( jobHash ) ;

					} else {

						logger.warn( "Did not find hash for {}", restUrl ) ;

					}

					logger.debug( "trend request completed: {}, hash {}, Active Jobs: {}",
							result, jobHash, activeJobs.size( ) ) ;
					logger.debug( "{} trend request completed: numDays: {},  hash {}, Active Jobs: {}",
							timerName, getPriority( ), jobHash, activeJobs.size( ) ) ;

				} catch ( Throwable e ) {

					logger.error( "request failed: {}", restUrl, e ) ;

				}

			} catch ( Throwable e ) {

				logger.error( "{} request failed", restUrl, e ) ;

			}

			return 1L ;

		}

		public int getPriority ( ) {

			return priority ;

		}
	}

	class PriorityFutureComparator implements Comparator<Runnable> {

		public int compare ( Runnable o1 , Runnable o2 ) {

			if ( o1 == null && o2 == null ) {

				return 0 ;

			} else if ( o1 == null ) {

				return -1 ;

			} else if ( o2 == null ) {

				return 1 ;

			} else {

				int p1 = ( (PriorityFuture<?>) o1 ).getPriority( ) ;
				int p2 = ( (PriorityFuture<?>) o2 ).getPriority( ) ;

				return p1 > p2 ? 1 : ( p1 == p2 ? 0 : -1 ) ; // higher is better
				// lower is better
				// return p1 < p2 ? 1 : (p1 == p2 ? 0 : -1);

			}

		}
	}

	class PriorityFuture<T> implements RunnableFuture<T> {

		private RunnableFuture<T> src ;
		private int priority ;

		public PriorityFuture ( RunnableFuture<T> other, int priority ) {

			this.src = other ;
			this.priority = priority ;

		}

		public int getPriority ( ) {

			return priority ;

		}

		public boolean cancel ( boolean mayInterruptIfRunning ) {

			return src.cancel( mayInterruptIfRunning ) ;

		}

		public boolean isCancelled ( ) {

			return src.isCancelled( ) ;

		}

		public boolean isDone ( ) {

			return src.isDone( ) ;

		}

		public T get ( )
			throws InterruptedException ,
			ExecutionException {

			return src.get( ) ;

		}

		public T get ( long timeout , TimeUnit unit )
			throws InterruptedException ,
			ExecutionException ,
			TimeoutException {

			return src.get( ) ;

		}

		public void run ( ) {

			src.run( ) ;

		}
	}

}
