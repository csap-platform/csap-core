package org.csap.integations ;

import java.io.File ;
import java.io.IOException ;

import org.apache.commons.io.FileUtils ;
import org.csap.helpers.CSAP ;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor ;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.core.env.Environment ;

@Configuration ( "CsapEncryptableProperties" )
@ConditionalOnProperty ( "csap.encryption.enabled" )
@ConfigurationProperties ( prefix = "csap.encryption" )
public class CsapEncryptionConfiguration {

	public final static String ENV_VARIABLE = "CSAP_ID" ;
	public final static String ALGORITHM_ENV_VARIABLE = "CSAP_ALGORITHM" ;

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private String token = "willBeOverwritten" ;
	private String algorithm = "PBEWITHMD5ANDTRIPLEDES" ;

	static StringBuilder builder = new StringBuilder( "\n csap.encryption: " ) ;

	public String toString ( ) {

		return builder.toString( ) ;

	}

	@Autowired
	public CsapEncryptionConfiguration ( Environment env ) {

		this.env = env ;

	}

	Environment env ;

	/**
	 * warnings will be output if input is not encoded
	 * 
	 * @param input
	 * @return
	 */
	public String decodeIfPossible ( String input , Logger sourceLogger ) {

		String result = input ;

		try {

			result = encryptorWithCsapOverrideKey( ).decrypt( input ) ;

		} catch ( EncryptionOperationNotPossibleException e ) {

			String encoded = encryptorWithCsapOverrideKey( ).encrypt( input ) ;
			// sourceLogger.warn( );
			sourceLogger.warn( "Password is not encrypted, update to: '{}', {}", encoded, CSAP.buildCsapStack(
					new Exception( "source for call:" ) ) ) ;

		}

		return result ;

	}

	@Bean
	public StandardPBEStringEncryptor encryptorWithCsapOverrideKey ( ) {

		StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor( ) ;

		logger.debug( "Building encrypter" ) ;

		builder.append( CSAP.padLine( "Default algorithm and key" )
				+ "loaded from csap.encryption in application.yaml." ) ;
		builder.append( CSAP.padLine( "Files" )
				+ "/etc/csap.token and $HOME/etc/csap.token, and then env vars will be checked for custom settings." ) ;
		builder.append( CSAP.padLine( "token" ) ) ;

		File key = new File( "/etc/csap.token" ) ;

		if ( ! key.exists( ) ) {

			// choice 3 - use home dir on systems with restriced access
			key = new File( System.getenv( "HOME" ) + "/etc/csap.token" ) ;

		}

		builder.append( "source:" ) ;

		if ( key.exists( ) && key.isFile( ) && key.canRead( ) ) {

			try {

				setToken( FileUtils.readFileToString( key ) ) ;
				builder.append( "file: " + key.getAbsolutePath( ) ) ;

				// logger.warn( "Setting token from file: " +
				// key.getAbsolutePath() );
			} catch ( IOException e1 ) {

				logger.error( "Failed to read key file: {}", key.getAbsolutePath( ), e1 ) ;

			}

		} else if ( env.getProperty( ENV_VARIABLE ) != null ) {

			builder.append( "environment variable: " + ENV_VARIABLE ) ;
			// logger.warn( "Setting token from file: " + ENV_VARIABLE );
			setToken( env.getProperty( ENV_VARIABLE ) ) ;

		} else {

			builder.append( "csap-starter-default" ) ;

		}

		builder.append( ", value: *MASKED*, length: " + getToken( ).length( ) ) ;

		logger.debug( builder.toString( ) ) ;
		encryptor.setPassword( getToken( ) ) ;

		// Use same steps for algorithm
		builder.append( CSAP.padLine( "algorithm" ) ) ;

		if ( env.getProperty( ALGORITHM_ENV_VARIABLE ) != null ) {

			encryptor.setAlgorithm( env.getProperty( ALGORITHM_ENV_VARIABLE ) ) ;
			builder.append( getAlgorithm( ) ) ;
			builder.append( "\tEnv variable used: " + ALGORITHM_ENV_VARIABLE ) ;

		} else {

			encryptor.setAlgorithm( getAlgorithm( ) ) ;
			builder.append( getAlgorithm( ) ) ;

		}

		builder.append( "\n" ) ;

		logger.debug( builder.toString( ) ) ;

		return encryptor ;

	}

	public String getToken ( ) {

		return token ;

	}

	public void setToken ( String token ) {

		this.token = token ;

	}

	public String getAlgorithm ( ) {

		return algorithm ;

	}

	public void setAlgorithm ( String algorithm ) {

		this.algorithm = algorithm ;

	}
}
