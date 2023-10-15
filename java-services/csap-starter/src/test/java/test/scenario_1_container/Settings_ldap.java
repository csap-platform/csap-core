package test.scenario_1_container ;

import org.csap.security.config.CsapSecurityProvider ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Profile ;

@Configuration
@Profile ( Csap_Application_With_LDAP.PROFILE_NAME )
@ConfigurationProperties ( prefix = "test.variables.ldap.settings" )
@ConditionalOnProperty ( name = "csap.security.provider.type" , havingValue = CsapSecurityProvider.LDAP )
public class Settings_ldap {

	public String DEFAULT = "injectedFromHOMEDIR" ;
	private String user = DEFAULT ;
	private String pass = DEFAULT ;
	private String urls = DEFAULT ;
	private String email = DEFAULT ;

	public boolean isConfigured ( ) {

		return ! user.equals( DEFAULT ) ;

	}

	//
	// config properties
	//
	public String getUser ( ) {

		return user ;

	}

	public void setUser ( String user ) {

		this.user = user ;

	}

	public String getPass ( ) {

		return pass ;

	}

	public void setPass ( String pass ) {

		this.pass = pass ;

	}

	public String getUrls ( ) {

		return urls ;

	}

	public void setUrls ( String urls ) {

		this.urls = urls ;

	}

	@Override
	public String toString ( ) {

		return this.getClass( ).getSimpleName( ) + " [user=" + user + ", pass=" + pass + ", urls=" + urls + "]" ;

	}

	public String getEmail ( ) {

		return email ;

	}

	public void setEmail ( String email ) {

		this.email = email ;

	}

	public static final String[] SETUP_NOTES = {
			"Add your test ids to .spring-boot-devtools.properties",
			"test.variables.ldap.user=<your ldap user. eg. peter.nightingale>",
			"test.variables.ldap.pass=<your ldap pass>",
			"", "Exiting tests"
	} ;
}
