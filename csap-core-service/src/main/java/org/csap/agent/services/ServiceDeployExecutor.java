package org.csap.agent.services ;

import java.io.File ;
import java.util.concurrent.ArrayBlockingQueue ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.Future ;
import java.util.concurrent.ThreadPoolExecutor ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;
import java.util.concurrent.locks.Condition ;
import java.util.concurrent.locks.Lock ;
import java.util.concurrent.locks.ReentrantLock ;
import java.util.stream.Collectors ;

import org.apache.commons.lang3.concurrent.BasicThreadFactory ;
import org.csap.agent.model.Application ;
import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.annotation.JsonIgnore ;
import com.fasterxml.jackson.annotation.JsonProperty ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

public class ServiceDeployExecutor extends ThreadPoolExecutor {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private boolean isPaused = false ;
	private boolean pausedBeforeActivity = false ;
	private Lock pauseLock = new ReentrantLock( ) ;
	private Condition unpaused = pauseLock.newCondition( ) ;
	private String neverCancelServiceName ;

	private File monitorFile ;

	final static int MAX_JOBS = 1000 ;

	volatile BlockingQueue<DeployOperation> pendingOperations = new ArrayBlockingQueue<>( MAX_JOBS ) ;

	ObjectMapper jsonMapper ;

	public ServiceDeployExecutor ( ObjectMapper jsonMapper, File monitorFile, String neverCancelServiceName ) {

		super( 1, 1, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>( MAX_JOBS ),
				new BasicThreadFactory.Builder( )
						.namingPattern( "CsapServiceDeployment-%d" )
						.daemon( true )
						.priority( Thread.MAX_PRIORITY )
						.build( ) ) ;

		this.jsonMapper = jsonMapper ;
		this.monitorFile = monitorFile ;
		this.neverCancelServiceName = neverCancelServiceName ;

	}

	class DeployOperation {

		@JsonIgnore
		Future<?> operationTask ;

		@JsonProperty
		String userid, serviceName, type ;

		public DeployOperation ( Future<?> operationTask, String userid, String serviceName, String type ) {

			this.operationTask = operationTask ;
			this.userid = userid ;
			this.serviceName = serviceName ;
			this.type = type ;

		}

		@Override
		public String toString ( ) {

			return userid + " " + type + " " + serviceName ;

		}

		@Override
		public boolean equals ( Object o ) {

			if ( o == this ) {

				return true ;

			}

			if ( ! ( o instanceof DeployOperation ) ) {

				return false ;

			}

			DeployOperation c = (DeployOperation) o ;
			return this.serviceName.equals( c.serviceName ) ;

		}

	}

	synchronized public void addOperation ( Runnable operation , String userid , String serviceName , String type ) {

		if ( monitorFile.exists( ) ) {

			pause( ) ; // use case: added via filesystem - before any deployments

		}

		if ( ( getActiveCount( ) == 0 )
				&& ( getQueue( ).size( ) == 0 )
				&& ( pendingOperations.size( ) > 0 ) ) {

			logger.warn( "removing dangling operations: {} ", pendingOperationsSummary( ) ) ;
			pendingOperations.removeAll( pendingOperations ) ;

		}

		Future<?> operationTask = super.submit( operation ) ;

		pendingOperations.add( new DeployOperation( operationTask, userid, serviceName, type ) ) ;

		return ;

	}

	int pendingOperationsCount ( ) {

		return pendingOperations.size( ) ;

	}

	String pendingOperationsSummary ( ) {

		var summary = pendingOperations.stream( )
				.map( DeployOperation::toString )
				.collect( Collectors.joining( ", " ) ) ;

		return summary ;

	}

	synchronized ObjectNode cancelRemaining ( ) {

		var cancelResults = jsonMapper.createObjectNode( ) ;

		AtomicInteger totalOperationsActive = new AtomicInteger( 0 ) ;
		pendingOperations.stream( )
				.filter( deployment -> ! neverCancelServiceName.equals( deployment.serviceName ) )
				.forEach( deployment -> {

					var wasCancelled = false ;

					if ( ( totalOperationsActive.incrementAndGet( ) > getActiveCount( ) )
							|| ( isPaused && pausedBeforeActivity ) ) {

						wasCancelled = deployment.operationTask.cancel( false ) ;

					}

					;
					cancelResults.put( deployment.serviceName, wasCancelled ) ;

				} ) ;

		CSAP.asStreamHandleNulls( cancelResults )
				.filter( serviceName -> cancelResults.path( serviceName ).asBoolean( ) )
				.forEach( serviceName -> {

					pendingOperations.remove( new DeployOperation( null, null, serviceName, null ) ) ;

				} ) ;
		;

		// this removes the cancelled jobs
		purge( ) ;

		return cancelResults ;

	}

	ArrayNode pendingOperations ( ) {

		var pendingOps = jsonMapper.convertValue(
				pendingOperations,
				ArrayNode.class ) ;

		return pendingOps ;

	}

	boolean isServiceQueued ( String service ) {

		return pendingOperations.stream( )
				.filter( operation -> operation.serviceName.equals( service ) )
				.findFirst( )
				.isPresent( ) ;

	}

	int getOpsQueued ( ) {

		int jobsActive = getQueue( ).size( ) + getActiveCount( ) ;

		return jobsActive ;

	}

	@Override
	protected void beforeExecute ( Thread t , Runnable r ) {

		super.beforeExecute( t, r ) ;

		if ( Application.isRunningOnDesktop( ) ) {

			try {

				TimeUnit.SECONDS.sleep( 1 ) ;

			} catch ( Exception e ) {

				logger.info( "Timer interrupted: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		pauseLock.lock( ) ;

		try {

			while ( isPaused ) {

				unpaused.await( ) ;

			}

		} catch ( InterruptedException e ) {

			t.interrupt( ) ;

		} finally {

			pauseLock.unlock( ) ;

		}

	}

	@Override
	synchronized protected void afterExecute ( Runnable r , Throwable t ) {

		try {

			pendingOperations.remove( ) ;

		} catch ( Exception e ) {

			logger.info( "Failed removing operation: {}", CSAP.buildCsapStack( e ) ) ;

		}

		if ( monitorFile.exists( ) ) {

			pause( ) ;

		}

		super.afterExecute( r, t ) ;

	}

	synchronized public void pause ( ) {

		pauseLock.lock( ) ;

		try {

			isPaused = true ;
			pausedBeforeActivity = false ;

			if ( getActiveCount( ) == 0 ) {

				pausedBeforeActivity = true ;

			}

		} finally {

			pauseLock.unlock( ) ;

		}

	}

	public void resume ( ) {

		if ( monitorFile.exists( ) ) {

			monitorFile.delete( ) ;

		}

		pauseLock.lock( ) ;

		try {

			isPaused = false ;
			unpaused.signal( ) ;

		} finally {

			pauseLock.unlock( ) ;

		}

	}

	public boolean isPaused ( ) {

		return isPaused ;

	}
}
