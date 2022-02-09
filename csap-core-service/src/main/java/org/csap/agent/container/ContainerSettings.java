package org.csap.agent.container ;

import org.csap.agent.CsapConstants ;
import org.csap.helpers.CSAP ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

@ConfigurationProperties ( CsapConstants.CONFIGURATION_PREFIX + ".docker" )
public class ContainerSettings {
	private String url ;

	private boolean enabled = false ;

	private String templateRepository = "docker.io" ;

	public String getTemplateRepository ( ) {

		return templateRepository ;

	}

	public void setTemplateRepository ( String templateRepository ) {

		this.templateRepository = templateRepository ;

	}

	private int connectionPool = 1 ;

	public int getReadTimeoutSeconds ( ) {

		return readTimeoutSeconds ;

	}

	public void setReadTimeoutSeconds ( int readTimeoutSeconds ) {

		this.readTimeoutSeconds = readTimeoutSeconds ;

	}

	public int getConnectionTimeoutSeconds ( ) {

		return connectionTimeoutSeconds ;

	}

	public void setConnectionTimeoutSeconds ( int connectionTimeoutSeconds ) {

		this.connectionTimeoutSeconds = connectionTimeoutSeconds ;

	}

	private int readTimeoutSeconds = 10 ;
	private int connectionTimeoutSeconds = 10 ;

	public String getUrl ( ) {

		return url ;

	}

	public void setUrl ( String url ) {

		this.url = url ;

	}

	public int getConnectionPool ( ) {

		return connectionPool ;

	}

	public void setConnectionPool ( int connectionPool ) {

		this.connectionPool = connectionPool ;

	}

	@Override
	public String toString ( ) {

		return CSAP.buildDescription(
				this.getClass( ).getName( ),
				"enabled", enabled,
				"url", url,
				"templateRepository", templateRepository,
				"connectionPool", connectionPool,
				"connectionTimeoutSeconds", connectionTimeoutSeconds,
				"readTimeoutSeconds", readTimeoutSeconds ) ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	public void setEnabled ( boolean enabled ) {

		this.enabled = enabled ;

	}

}
