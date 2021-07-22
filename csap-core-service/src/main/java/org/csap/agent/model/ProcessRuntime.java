package org.csap.agent.model ;

import java.util.Arrays ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public enum ProcessRuntime {

	os("os", "Process Monitor", "images/16x16/sysMon.png"),

	springboot("SpringBoot", "Spring Boot Project", "images/boot.png"),

	tomcat7("tomcat7.x", "Tomcat", "images/tomcat7.png"),
	tomcat8("tomcat8.x", "Tomcat", "images/tomcat8.png"), tomcat85("tomcat8-5.x", "Tomcat", "images/tomcat8.png"),
	tomcat9("tomcat9.x", "Tomcat", "images/tomcatLarge.png"),

	script("script", "", "/images/32x32/newFolder.png"),
	docker("docker", "", "images/32x32/computer.png"),

	unregistered("unregistered", "", "images/32x32/computer.png"),

	csap_api("csap-api", "CSAP Wrapper Project", "images/32x32/generic.png"),

	// csap_api( "csap-api", "CSAP Api Project", "images/32x32/generic.png" ),

	unknown("unknown", "Unknown", "images/32x32/process.png"),
	;

	private final String id ;
	private final String title ;
	private final String adminImage ;

	private ProcessRuntime ( String id, String title, String adminImage ) {

		this.id = id ;
		this.title = title ;
		this.adminImage = adminImage ;

	}

	private static String[] javaServers = {
			springboot.getId( ),
			tomcat7.getId( ),
			tomcat8.getId( ),
			tomcat85.getId( ),
			tomcat9.getId( )
	} ;

	public boolean isJava ( ) {

		return Arrays.stream( javaServers ).anyMatch( id::equals ) ;

	}

	public static boolean isJavaServer ( String server ) {

		ProcessRuntime runtime = findById( server ) ;

		return Arrays.stream( javaServers ).anyMatch( runtime.getId( )::equals ) ;

	}

	public static String[] javaServers ( ) {

		return javaServers ;

	}

	static Logger logger = LoggerFactory.getLogger( Runtime.class ) ;

	public static ProcessRuntime findById ( String id ) {

		for ( ProcessRuntime runtime : ProcessRuntime.values( ) ) {

			if ( runtime.id.equals( id ) )
				return runtime ;

		}

		return ProcessRuntime.unknown ;

	}

	public String getId ( ) {

		return id ;

	}

	public String getTitle ( ) {

		return title ;

	}

	public String getAdminImage ( ) {

		return adminImage ;

	}

}
