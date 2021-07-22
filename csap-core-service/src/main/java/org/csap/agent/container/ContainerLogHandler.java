package org.csap.agent.container ;

import java.io.IOException ;
import java.io.Writer ;
import java.util.ArrayList ;
import java.util.List ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.github.dockerjava.api.async.ResultCallback.Adapter ;
import com.github.dockerjava.api.model.Frame ;

public class ContainerLogHandler extends Adapter<Frame> {
	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	protected final StringBuffer logBuffer = new StringBuffer( ) ;

	List<Frame> collectedFrames = new ArrayList<Frame>( ) ;

	boolean collectFrames = false ;
	boolean collectLog = true ;

	Writer writer = null ;

	public ContainerLogHandler ( ) {

		this( false ) ;

	}

	public ContainerLogHandler ( boolean collectFrames ) {

		this.collectFrames = collectFrames ;

	}

	public ContainerLogHandler ( Writer writer ) {

		this.writer = writer ;
		this.collectLog = false ;

	}

	@Override
	public void onNext ( Frame frame ) {

		if ( collectFrames )
			collectedFrames.add( frame ) ;

		// logger.info( "Frame size: {}", frame.getPayload().length );

		if ( collectLog )
			logBuffer.append( new String( frame.getPayload( ) ) ) ;

		if ( writer != null ) {

			try {

				writer.write( new String( frame.getPayload( ) ) ) ;

			} catch ( IOException e ) {

				logger.info( "Failed getting frame: {}", CSAP.buildCsapStack( e ) ) ;

			}

		}

		// logger.info( "payload: {}", new String( frame.getPayload() ));
	}

	@Override
	public String toString ( ) {

		return logBuffer.toString( ) ;

	}

	public List<Frame> getCollectedFrames ( ) {

		return collectedFrames ;

	}
}