package test.scenario_1_container ;

import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.Profile ;

@Profile ( Csap_Application_With_AD_Security.AD_TEST_PROFILE )
@Configuration
@ConfigurationProperties ( prefix = "test.variables.active-dir.settings" )
public class Settings_active_dir {

	public String DEFAULT = "injectedFromHOMEDIR" ;
	private String user = DEFAULT ;
	private String pass = DEFAULT ;
	private String urls = DEFAULT ;
	private String domain = DEFAULT ;

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

	public String getDomain ( ) {

		return domain ;

	}

	public void setDomain ( String domain ) {

		this.domain = domain ;

	}

	@Override
	public String toString ( ) {

		return "ActiveDirectorySettings [user=" + user + ", pass=" + pass + ", urls=" + urls + ", domain=" + domain
				+ "]" ;

	}

	public static final String[] SETUP_NOTES = {
			"Add your test ids to $HOME/csap/" + Csap_Application_With_AD_Security.AD_TEST_PROFILE + ".yml",
			"test.variables.ldap.user=<your ldap user. eg. peter.nightingale>",
			"test.variables.ldap.pass=<your ldap pass>",
			"", "Skipping tests"
	} ;
}
