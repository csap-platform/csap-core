package org.csap.security.config ;

public class CookieSettings {

	private String name = "CSAP_SSO" ;

	private String token = "DefaultKey" ;

	private int expireSeconds = 24 * 60 * 60 ;

	public String getName ( ) {

		return name ;

	}

	public void setName ( String name ) {

		this.name = name ;

	}

	public String getToken ( ) {

		return token ;

	}

	public void setToken ( String token ) {

		this.token = token ;

	}

	public int getExpireSeconds ( ) {

		return expireSeconds ;

	}

	public void setExpireSeconds ( int expireSeconds ) {

		this.expireSeconds = expireSeconds ;

	}

}
