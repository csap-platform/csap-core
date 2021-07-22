package org.csap.agent.model ;

public enum DefinitionConstants {

	// definition top level
	definition("definition"), projectsFolderName("projects"), projectsExtracted("projects/extracted"),
	serviceResourcesFolder("resources"),

	// templates
	pathToTemplate("path-to-template"),

	// cluster constants
	clusterType("type"), clusterNotes("notes"), clusterTemplate("template");

	private String description ;

	private DefinitionConstants ( String jsonToken ) {

		this.description = jsonToken ;

	}

	public String key ( ) {

		return description ;

	}

}
