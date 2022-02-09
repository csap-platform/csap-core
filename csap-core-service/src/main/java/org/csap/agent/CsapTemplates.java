package org.csap.agent ;

import java.io.File ;
import java.io.IOException ;

import org.csap.helpers.CSAP ;
import org.slf4j.LoggerFactory ;
import org.springframework.core.io.ClassPathResource ;

public enum CsapTemplates {
	shell_scripts("shell-scripts"),

	kubernetes_yaml("kubernetes-yaml"),

	default_service_definitions("default-service-definitions.json"),

	search("search-template.sh"),

	helmDeploy("helm-deploy.sh"),

	nagios_config("nagios-config.cfg"), nagios_result("nagios-result.xml"),

	project_template("new-project.json"),
	edit_service("edit-service.json"),
	demoAutoPlay("demo-auto-play.yaml"),

	test_host_collection("host-collection.json"),
	test_service_os_collection("service-os-collection.json"),
	test_service_jmx_collection("service-jmx-collection.json"),
	test_host_status_collection("host-status.json"),;

	ClassPathResource theResource ;
	String key ;

	private CsapTemplates ( String path ) {

		key = path ;
		theResource = new ClassPathResource( "csap-templates/" + path ) ;

	}

	public String getKey ( ) {

		return key ;

	};

	public File getFile ( ) {

		try {

			return theResource.getFile( ) ;

		} catch ( IOException e ) {

			LoggerFactory.getLogger( CsapTemplates.class ).warn( "Failed loading: {}, {}", theResource, CSAP
					.buildCsapStack( e ) ) ;

		}

		return null ;

	}

}
