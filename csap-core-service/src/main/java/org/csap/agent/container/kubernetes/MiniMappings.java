package org.csap.agent.container.kubernetes ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Date ;
import java.util.List ;
import java.util.Map ;
import java.util.stream.Collectors ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;
import com.fasterxml.jackson.annotation.JsonProperty ;

public class MiniMappings {
	final static Logger logger = LoggerFactory.getLogger( MiniMappings.class ) ;

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class SimplePodListing {

		public List<SimplePod> items ;

		@Override
		public String toString ( ) {

			return "PodTinyListing" + items.toString( ) ;

		}

		public List<SimplePod> getItems ( ) {

			return items ;

		}

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class SimplePod {

		String name ;

		@JsonProperty ( "metadata" )
		public void unpackMetaData ( Map<String, Object> metadata ) {

			name = (String) metadata.get( "name" ) ;

		}

		@Override
		public String toString ( ) {

			return name ;

		}

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class PodContainerNamesListing {

		public List<PodContainerNames> items ;

		@Override
		public String toString ( ) {

			return "PodContainerNamesListing: " + items.toString( ) ;

		}

		public List<PodContainerNames> getItems ( ) {

			return items ;

		}

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class PodContainerNames {

		String name ;
		String namespace ;
		List<String> containerNames = new ArrayList<>( ) ;

		@JsonProperty ( "metadata" )
		public void unpackMetaData ( Map<String, Object> metadata ) {

			name = (String) metadata.get( "name" ) ;
			namespace = (String) metadata.get( "namespace" ) ;

		}

		@JsonProperty ( "spec" )
		public void unpackSpec ( Map<String, Object> spec ) {

			try {

				var containers = spec.get( "containers" ) ;

				if ( containers != null
						&& containers instanceof List ) {

					var containerObjectList = (List<Object>) containers ;

					containerNames = containerObjectList.stream( )
							.filter( container -> container instanceof Map )
							.map( container -> (Map<String, Object>) container )
							.filter( container -> container.containsKey( "name" ) )
							.map( container -> {

								var name = (String) container.get( "name" ) ;
								return name ;

							} )
							.collect( Collectors.toList( ) ) ;

					logger.debug( "containerNames: {}", containerNames ) ;

				}

			} catch ( Exception e ) {

				logger.warn( "Failed to get containerNames: {} {}", toString( ), CSAP.buildCsapStack( e ) ) ;

			}

		}

		@Override
		public String toString ( ) {

			return "\n\t pod: " + name + " namespace: " + namespace + " containerNames: " + containerNames ;

		}

	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class EventListing {

		public List<Event> items ;

		@Override
		public String toString ( ) {

			return "EventListing" + items.toString( ) ;

		}

	}
	// String now = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "MM:dd
	// HH:mm:ss" ) ) ;

	static DateTimeFormatter mmddFormatter = DateTimeFormatter.ofPattern( "MMM dd HH:mm:ss" ) ;

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class Event {

		public String message ;
		public String reload ;
		public int count ;
		public String type ;
		public Date eventTime ;
		public LocalDateTime firstTimestamp ;
		public LocalDateTime lastTimestamp ;

		@JsonProperty ( "metadata" )
		public void unpackMetaData ( Map<String, Object> metadata ) {

			name = (String) metadata.get( "name" ) ;

			namespace = (String) metadata.get( "namespace" ) ;

		}

		String name ;
		String namespace ;

		@JsonProperty ( "involvedObject" )
		public void unpackInvolvedObject ( Map<String, Object> metadata ) {

			involvedKind = (String) metadata.get( "kind" ) ;

			involvedName = (String) metadata.get( "name" ) ;

		}

		String involvedKind ;
		String involvedName ;

		@Override
		public String toString ( ) {

			return "\n\nEvent [message=" + message + ", reload=" + reload + ", count=" + count + ", type=" + type
					+ ", eventTime=" + eventTime + ", firstTimestamp=" + firstTimestamp.format( mmddFormatter )
					+ ", lastTimestamp="
					+ lastTimestamp.format( mmddFormatter ) + ", name=" + name + ", namespace=" + namespace
					+ ", involvedKind=" + involvedKind
					+ ", involvedName=" + involvedName + "]" ;

		}

	}

}
