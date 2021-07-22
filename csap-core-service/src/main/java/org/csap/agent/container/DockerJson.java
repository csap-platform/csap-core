package org.csap.agent.container ;

public enum DockerJson {

	containerName("containerName"), imageName("image"),

	socketNamespace("socketNamespace"),

	// processMatching
	k8ContainerSuffix("-container"),

	// spec deployments
	isSkipSpecGeneration("deployment-files-use"), deploymentFileNames("deployment-file-names"),

	run("run"),

	command("command"), entryPoint("entryPoint"), workingDirectory("workingDirectory"),

	create_persistent("createPersistent"), // used with network and volumes

	network("network"), network_name("name"), network_driver("driver"),
	privatePort("PrivatePort"), publicPort("PublicPort"), protocol("protocol"),

	volumes("volumes"), volume_host_path("hostPath"),

	restartPolicy("restartPolicy"), runUser("runUser"),

	versionCommand("versionCommand"),

	portMappings("portMappings"),

	limits("limits"),

	environmentVariables("environmentVariables"),

	defaultJavaDocker("defaultJavaDocker"),

	hostJavaDocker("hostJavaDocker"),

	// pull
	pull_complete("isComplete"),

	// response messages
	response_plain_text("plainText"),
	response_yaml("response-yaml"),
	response_json("response-json"),
	response_shell("response-sh"),
	response_start_results("startResults"),
	response_info("info"), errorReason("reason"), error("error"), warning("warning"),
	response_volume_list("volumeList"),
	response_volume_create("volumeCreate"),
	response_network_list("networkList"),
	response_network_create("networkCreate"),

	// listings
	list_label("label"),
	list_attributes("attributes"),

	// runtime navigation
	list_folderUrl("folderUrl"), list_folderPath("path"),

	// locators
	locator("locator"), value("value"), podName("podName"), podNamespace("podNamespace"), containerWildCard("*"),
	containerCount("container-count"),
	aggregateContainers("aggregateContainers");

	private String key ;

	public String json ( ) {

		return key ;

	}

	public String jpath ( ) {

		return "/" + key ;

	}

	private DockerJson ( String key ) {

		this.key = key ;

	}
}
