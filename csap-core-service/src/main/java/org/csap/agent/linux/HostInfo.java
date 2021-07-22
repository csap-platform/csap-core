package org.csap.agent.linux ;

public class HostInfo {

	private String name ;

	private String restUrl ;

	public HostInfo ( String name, String restUrl ) {

		super( ) ;

		this.name = name ;
		this.restUrl = restUrl ;

	}

	public String getName ( ) {

		return name ;

	}

	public String getRestUrl ( ) {

		return restUrl ;

	}

	public void setName ( String name ) {

		this.name = name ;

	}

	public void setRestUrl ( String restUrl ) {

		this.restUrl = restUrl ;

	}

	@Override
	public String toString ( ) {

		return "HostInfo [ name=" + name + ", restUrl=" + restUrl + "]" ;

	}

	@Override
	public boolean equals ( Object obj ) {

		// TODO Auto-generated method stub
		// logger.info("___ name: " + name + " obj" + obj.toString());
		return name.equals( ( (HostInfo) obj ).getName( ) ) ;

	}

}
