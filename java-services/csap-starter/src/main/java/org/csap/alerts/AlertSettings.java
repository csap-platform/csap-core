package org.csap.alerts ;

import java.util.ArrayList ;
import java.util.List ;
import java.util.Map ;
import java.util.Optional ;
import java.util.concurrent.TimeUnit ;
import java.util.stream.Collectors ;

import javax.annotation.PostConstruct ;

import org.csap.helpers.CSAP ;
import org.csap.integations.CsapPerformance.CustomHealth ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

@ConfigurationProperties ( prefix = "csap.performance.alert" )
public class AlertSettings {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private List<AlertInstance> alertDefinitions = new ArrayList<>( ) ;
	private boolean showMoreLogs = false ;
	private int rememberCount = 1000 ;

	public NotifySettings notify = new NotifySettings( ) ;
	public ThrottleSettings throttle = new ThrottleSettings( ) ;
	public ReportSettings report = new ReportSettings( ) ;

	/**
	 * @return the alertLimits
	 */
	public List<AlertInstance> getAlertDefinitions ( ) {

		return alertDefinitions ;

	}

	public List<ObjectNode> getAllAlertDefinitions ( ) {

		List<ObjectNode> defs = getAllAlertInstances( )
				.stream( )
				.map( AlertInstance::toJson )
				.collect( Collectors.toList( ) ) ;

		return defs ;

	}

	@Autowired
	ApplicationContext springContext ;

	@PostConstruct
	public void initialize ( ) {

		logger.debug( "Updating units of limits" ) ;

		for ( AlertInstance alertLimit : getAlertDefinitions( ) ) {

			logger.debug( "Checking: {}", alertLimit ) ;
			alertLimit.updateTimersFromUnits( ) ;

		}

		allAlertInstances = new ArrayList<>( ) ;
		allAlertInstances.addAll( getAlertDefinitions( ) ) ;

		// look for custom component implementations
		if ( springContext != null ) {

			Map<String, CustomHealth> customHealthMap = springContext.getBeansOfType( CustomHealth.class ) ;

			if ( ! customHealthMap.isEmpty( ) ) {

				logger.debug( "Custom Health applied" ) ;

				for ( CustomHealth component : customHealthMap.values( ) ) {

					allAlertInstances.add( new AlertInstance( component.getComponentName( ) + ".failed", 0,
							component ) ) ;

				}

			}

		}

	}

	// both custom and standard limits
	static List<AlertInstance> allAlertInstances ;

	public List<AlertInstance> getAllAlertInstances ( ) {

		return allAlertInstances ;

	}

	public Optional<AlertInstance> findAlertForComponent ( String id ) {

		Optional<AlertInstance> match = getAllAlertInstances( ).stream( )
				.filter( alert -> alert.getId( ).startsWith( id ) )
				.findFirst( ) ;

		return match ;

	}

	public String updateLimitEnabled ( String id , boolean enabled , String userid ) {

		logger.debug( "Updating units of limits" ) ;

		Optional<AlertInstance> match = getAllAlertInstances( ).stream( )
				.filter( alert -> id.equals( alert.getId( ) ) )
				.findFirst( ) ;

		String result ;

		if ( match.isPresent( ) ) {

			logger.debug( "{} is being set to enabled: {} ", id, enabled ) ;
			result = id + " enabled updated to: " + enabled ;
			match.get( ).setEnabled( enabled ) ;
			match.get( ).setUserid( userid ) ;

		} else {

			result = id + " not found" ;
			logger.warn( "{} not located ", id ) ;

		}

		return result ;

	}

	public boolean isDebug ( ) {

		return showMoreLogs ;

	}

	public void setDebug ( boolean debug ) {

		this.showMoreLogs = debug ;

	}

	/**
	 * @param alertLimits the alertLimits to set
	 */
	public void setLimits ( List<AlertInstance> alertLimits ) {

		this.alertDefinitions = alertLimits ;
		initialize( ) ;

	}

	public NotifySettings getNotify ( ) {

		return notify ;

	}

	public String toString ( ) {

		return getReport( )
				+ CSAP.padLine( "In-Memory cache size" )
				+ getRememberCount( )
				+ getThrottle( )
				+ CSAP.padLine( "Email" )
				+ getNotify( )
				+ CSAP.padLine( "Limits" )
				+ getAlertDefinitions( ) ;

	}

	public int getRememberCount ( ) {

		return rememberCount ;

	}

	public void setRememberCount ( int rememberCount ) {

		this.rememberCount = rememberCount ;

	}

	public class NotifySettings {
		private String addresses = "disabled" ;

		public String[] getEmails ( ) {

			if ( addresses.equals( "disabled" ) || getEmailHost( ).toLowerCase( ).contains( "yourcompany" ) ) {

				return null ;

			}

			return addresses.split( "," ) ;

		}

		private String emailHost = "outbound.yourCompany.com" ;
		private int emailMaxAlerts = 50 ;
		private int emailPort = 25 ;
		private int emailTimeOutMs = 300 ;
		private int frequency = 4 ;

		public int getFrequency ( ) {

			return frequency ;

		}

		private String timeUnit = TimeUnit.HOURS.name( ) ;

		public String getTimeUnit ( ) {

			return timeUnit ;

		}

		public String toString ( ) {

			return "Addresses: " + addresses + ",  Frequency: " + frequency + " " + timeUnit
					+ CSAP.padLine( "server" ) + getEmailHost( ) + ":" + getEmailPort( ) + " max: "
					+ getEmailTimeOutMs( ) + "ms" ;

		}

		public void setFrequency ( int frequency ) {

			this.frequency = frequency ;

		}

		public void setTimeUnit ( String timeUnit ) {

			this.timeUnit = timeUnit ;

		}

		public String getAddresses ( ) {

			return addresses ;

		}

		public void setAddresses ( String addresses ) {

			this.addresses = addresses ;

		}

		public String getEmailHost ( ) {

			return emailHost ;

		}

		public void setEmailHost ( String emailHost ) {

			this.emailHost = emailHost ;

		}

		public int getEmailPort ( ) {

			return emailPort ;

		}

		public void setEmailPort ( int emailPort ) {

			this.emailPort = emailPort ;

		}

		public int getEmailTimeOutMs ( ) {

			return emailTimeOutMs ;

		}

		public void setEmailTimeOutMs ( int emailTimeOut ) {

			this.emailTimeOutMs = emailTimeOut ;

		}

		public int getEmailMaxAlerts ( ) {

			return emailMaxAlerts ;

		}

		public void setEmailMaxAlerts ( int emailMaxAlerts ) {

			this.emailMaxAlerts = emailMaxAlerts ;

		}
	}

	public class ReportSettings {
		private int frequency = 30 ;
		private String timeUnit = "SECONDS" ;

		public long getIntervalSeconds ( ) {

			long numSeconds = CSAP.parseTimeUnit( getTimeUnit( ), TimeUnit.SECONDS ).toSeconds( getFrequency( ) ) ;
			return numSeconds ;

		}

		public int getFrequency ( ) {

			return frequency ;

		}

		public void setFrequency ( int frequency ) {

			this.frequency = frequency ;

		}

		public String getTimeUnit ( ) {

			return timeUnit ;

		}

		public void setTimeUnit ( String timeUnit ) {

			this.timeUnit = timeUnit ;

		}

		public String toString ( ) {

			return "Report interval: " + frequency + " " + timeUnit ;

		}
	}

	public class ThrottleSettings {
		private int count = 5 ;
		private int frequency = 1 ;
		private String timeUnit = "HOURS" ;

		/**
		 * Limit the number of alerts recorded. Default is 1 hour; the total number of
		 * instances will be recorded along with the latest timestamp.
		 * 
		 * @param frequency
		 */
		public int getFrequency ( ) {

			return frequency ;

		}

		public void setFrequency ( int throttleTime ) {

			this.frequency = throttleTime ;

		}

		public String getTimeUnit ( ) {

			return timeUnit ;

		}

		public void setTimeUnit ( String throttleUnit ) {

			this.timeUnit = throttleUnit ;

		}

		public int getCount ( ) {

			return count ;

		}

		public void setCount ( int throttleCount ) {

			this.count = throttleCount ;

		}

		public String toString ( ) {

			return CSAP.padLine( "Throttle" ) + "maximum: " + count + " per: " + frequency + " " + timeUnit ;

		}

	}

	public ThrottleSettings getThrottle ( ) {

		return throttle ;

	}

	public void setThrottle ( ThrottleSettings throttle ) {

		this.throttle = throttle ;

	}

	public ReportSettings getReport ( ) {

		return report ;

	}

	public void setReport ( ReportSettings report ) {

		this.report = report ;

	}

}
