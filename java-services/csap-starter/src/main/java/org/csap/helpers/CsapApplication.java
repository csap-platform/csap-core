package org.csap.helpers ;

import java.io.File ;
import java.util.List ;
import java.util.regex.Pattern ;

import org.apache.commons.io.FileUtils ;
import org.apache.commons.lang3.StringUtils ;
import org.apache.logging.log4j.LogManager ;
import org.apache.logging.log4j.core.LoggerContext ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.SpringApplication ;
import org.springframework.core.io.ClassPathResource ;
import org.springframework.core.io.FileSystemResource ;
import org.springframework.core.io.Resource ;

public class CsapApplication {

	static String LOG4J2_TEST_YML = "log4j2-junit.yml" ;

	final static private Logger logger = LoggerFactory.getLogger( CsapApplication.class ) ;

	public static final String CSAP_INSTALL_VARIABLE = "CSAP_FOLDER" ;

	// csap-data publication keys shared between events and agent
	public static final String COLLECTION_APPLICATION = "application" ;
	public static final String COLLECTION_OS_PROCESS = "os-process" ;
	public static final String COLLECTION_HOST = "host" ;
	public static final String COLLECTION_JAVA = "java" ;

	public final static String OVERWRITTEN = "set by profile yml file" ;

	final static String DEFAULT_CSAP_CONFIGURATION = System.getProperty( "user.home" ) + "/csap/" ;

	final public static String DEFINITION_FOLDER_NAME = "definition" ;

	final public static String LINE = "\n" ;

	public static String TC_HEAD = LINE + "\n\t Test Start" + LINE ;

	public static String SETUP_HEAD = LINE + "\n\t Test Setup" + LINE ;;

	volatile static boolean is_csap_initialize_run_once = false ;

	static File csapInstallationFolder ;
	static File junitFolder ;

	//
	// - main method invoked when running springboot application
	//
	public static void run ( Class<?> clazz , String[] args ) {

		var profileActive = System.getProperty( "spring.profiles.active" ) ;

		// triggers default log4j.yml (json mode) to be loaded; post startup - others
		// may override.
		logger.info( highlightHeader( "system property spring.profiles.active: '{}'" ), profileActive ) ;

		if ( profileActive == null ) {

			LOG4J2_TEST_YML = "log4j2-desktop.yml" ; // ansi colors, readable
			initialize( " -Dspring.profiles.active not set - assuming desktop mode" ) ;

		} else {

			add_CSAP_path_profiles( ) ;

		}

		var context = SpringApplication.run( clazz, args ) ;

		logger.debug( "activeProfiles: '{}'", List.of( context.getEnvironment( ).getActiveProfiles( ) ) ) ;

	}

	public static String header ( String message ) {

		return CsapApplication.LINE + "\n " + message + CsapApplication.LINE ;

	}

	public static String testHeader ( String message ) {

		return header( arrowMessage( CSAP.pad( "   Test:" ) + message ) ) ;

	}

	public static String highlightHeader ( String message ) {

		return header( arrowMessage( "   " + message ) ) ;

	}

	public static String arrowMessage ( String message ) {

		return "\n*\n**\n***\n****\n*****" + message + "\n****\n***\n**\n*" ;

	}

	public static String testHeader ( ) {

		var fullSourceClass = Thread.currentThread( ).getStackTrace( )[2].getClassName( ) ;

		var fullSourceClassArray = fullSourceClass.split( Pattern.quote( "." ) ) ;

		var content = CSAP.pad( "  Test:" )
				+ fullSourceClassArray[fullSourceClassArray.length - 1] + "."
				+ Thread.currentThread( ).getStackTrace( )[2].getMethodName( ) ;

		return header( arrowMessage( content ) ) ;

	}

	/**
	 * Junit setup - initiallizes log4j, but note every class is including - so a
	 * guard is setup to prevent reloading of log4j
	 * 
	 * @param description
	 */
	public static void initialize ( String description ) {

		// boot dev tools does a double start to inject into the path
		// System.out.println( CSAP.buildFilteredStack( new Exception( "CSAP CAll Path"
		// ), "." ) ) ;

		if ( ! is_csap_initialize_run_once ) {

			is_csap_initialize_run_once = true ;

			// Configure in prps file
			// CSAP.setLogToDebug( ConfigFileApplicationListener.class.getName()
			// );

			var startMessage = new StringBuilder( CSAP_INSTALL_VARIABLE + " not set - docker,desktop,junit mode" ) ;
			startMessage.append( CSAP.padLine( "Working Directory" ) + System.getProperty( "user.dir" ) ) ;
			startMessage.append( CSAP.padLine( "source location" )
					+ CsapApplication.class.getProtectionDomain( ).getCodeSource( ).getLocation( ).getFile( ) ) ;
			System.out.println( CsapApplication.header( startMessage.toString( ) ) ) ;

			loadLogConfiguration( LOG4J2_TEST_YML ) ;

			add_CSAP_path_profiles( ) ;

			// File testFolder = new File( "target/junit" );
			// logger.info( "Deleting: {}", testFolder.getAbsolutePath() );
			// FileUtils.deleteQuietly( testFolder );

			// https://logging.apache.org/log4j/2.0/faq.html
			logger.info( description ) ;

		}

	}

	private static void loadLogConfiguration ( String logLocation ) {

		var bootstrapMessage = new StringBuilder( ) ;

		Resource junitLogConfigResource = new ClassPathResource( logLocation ) ;
		var testLocation = new File( logLocation ) ;

		if ( testLocation.exists( ) ) {

			junitLogConfigResource = new FileSystemResource( logLocation ) ;

		}

		// System.out.println("Env Vars: " + System.getenv( )) ;
		var envOs = System.getenv( "OS" ) ;
		// System.out.println( "envOs: " + envOs ) ;

		//
		// Support for desktop ide sessions with ansi enabled, custom settings, etc.
		//
		Resource ideLogResource = new ClassPathResource( "ide-" + logLocation ) ;
		System.out.println( CSAP.buildDescription( "log4j configuration file checks",
				"envOs", envOs,
				"default", junitLogConfigResource,
				"exists", junitLogConfigResource.exists( ),
				"ide", ideLogResource,
				"exists", ideLogResource.exists( ) ) + "\n\n" ) ;

		if ( StringUtils.isNotEmpty( envOs )
				&& envOs.equals( "Windows_NT" ) ) {

			if ( ideLogResource.exists( ) ) {

				junitLogConfigResource = ideLogResource ;

			}

		}

		try {

			var logConfigFile = junitLogConfigResource.getURL( ) ;

			if ( logConfigFile == null ) {

				System.out.println( "ERROR: Failed to find log configuration file in classpath: "
						+ junitLogConfigResource ) ;

				System.exit( 99 ) ;

			}

			bootstrapMessage.append( CSAP.padNoLine( "log file" ) + junitLogConfigResource ) ;
			bootstrapMessage.append( CSAP.padLine( "found" ) + logConfigFile.toURI( ).getPath( ) ) ;

			LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext( false ) ;
			// this will force a reconfiguration
			context.setConfigLocation( logConfigFile.toURI( ) ) ;
			logger.info( bootstrapMessage.toString( ) ) ;

			// System.out.println( bootstrapMessage.toString( ) + "\n\n" ) ;
		} catch ( Exception e ) {

			System.out.println( "ERROR: Failed to resolve path: " + junitLogConfigResource
					+ "\n " + CSAP.buildCsapStack( e ) ) ;
			System.exit( 99 ) ;

		}

		// Now dump nicely formatted classpath.
		// sbuf.append( "\n\n ====== JVM Classpath is: \n"
		// + WordUtils.wrap( System.getProperty( "java.class.path"
		// ).replaceAll( ";", " " ), 140 ) );
	}

	public static void add_CSAP_path_profiles ( ) {

		var springboot_additional_profile_path = DEFAULT_CSAP_CONFIGURATION ;

		if ( System.getenv( "CSAP_LOG4J" ) != null ) {

			loadLogConfiguration( System.getenv( "CSAP_LOG4J" ) ) ;

		}

		if ( System.getProperty( "localhostSettingsFolder" ) != null ) {

			springboot_additional_profile_path = System.getProperty( "localhostSettingsFolder" ) ;

		}

		if ( isCsapFolderSet( ) ) {

			springboot_additional_profile_path = System.getenv( CSAP_INSTALL_VARIABLE )
					+ "/" + DEFINITION_FOLDER_NAME + "/" ;

		} else {

			csapInstallationFolder = new File( System.getProperty( "user.dir" ) + "/target/csap-platform" ) ;

			if ( csapInstallationFolder.exists( ) ) {

				junitFolder = new File( System.getProperty( "user.dir" ) + "/target/junit" ) ;

				logger.warn( "'" + CSAP_INSTALL_VARIABLE + "'"
						+ " environment variable not found: running in desktop mode"
						+ CSAP.padLine( "Cleaning up folders from previous runs" )
						+ CSAP.padLog( "csapInstallationFolder" )
						+ CSAP.padLog( "junitFolder" ),
						csapInstallationFolder,
						junitFolder ) ;

				FileUtils.deleteQuietly( csapInstallationFolder ) ;
				FileUtils.deleteQuietly( junitFolder ) ;

			}

		}

		if ( ! springboot_additional_profile_path.endsWith( "/" ) ) {

			logger.error( "spring.config.location '{}' MUST end with a trailing '/' , exiting",
					springboot_additional_profile_path ) ;

			// System.exit( 99 );
		}

		logger.debug( "springboot_additional_profile_path: {}", springboot_additional_profile_path ) ;

		File externalConfiguration = new File( springboot_additional_profile_path ) ;

		if ( ! externalConfiguration.exists( ) ) {

			externalConfiguration.mkdirs( ) ;

		}

		if ( externalConfiguration.isDirectory( ) ) {

			logger.warn( "Updating system property"
					+ CSAP.padLine( "spring.config.location" ) + externalConfiguration.toPath( ).toUri( ) ) ;

			// spring.config.location
			System.setProperty( "spring.config.additional-location", externalConfiguration.toPath( ).toUri( )
					.toString( ) ) ;

		} else {

			logger.warn( CsapApplication.header( "Location is not a folder: '{}'."
					+ "\n\t no additional application-<profile>.yml file(s) will be loaded" ),
					externalConfiguration.toPath( ).toUri( ) ) ;

		}

	}

	public static boolean isCsapFolderSet ( ) {

		if ( System.getenv( CSAP_INSTALL_VARIABLE ) == null ) {

			return false ;

		}

		return true ;

	}

	public static File getJunitFolder ( ) {

		return junitFolder ;

	}

}
