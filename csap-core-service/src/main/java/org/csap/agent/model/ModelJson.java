package org.csap.agent.model ;

public enum ModelJson {

	httpCollectionUrl("httpCollectionUrl"), healthCollectionUrl("healthCollectionUrl"), javaCollectionUrl(
			"javaCollectionUrl"),

	// shortcut for csap collection - appends
	csapMicroUrl("csapMicroUrl"),
	csapMicroSuffix("/csap/metrics/micrometers?aggregate=true&encode=true&tagFilter=csap-collection"),

	//
	config("config"), patternMatch("patternMatch"), json("JSON");

	private String key ;

	public String jpath ( ) {

		return key ;

	}

	public String cpath ( ) {

		return "/" + config.jpath( ) + "/" + key ;

	}

	private ModelJson ( String key ) {

		this.key = key ;

	}
}
