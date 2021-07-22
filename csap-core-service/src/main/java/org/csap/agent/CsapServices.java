package org.csap.agent ;

public enum CsapServices {

	agent(CsapCore.AGENT_NAME, 8011), admin(CsapCore.ADMIN_NAME, 8021), csap_verify("csap-verify-service", 7011),
	linux("csap-package-linux", 0), java("csap-package-java", 0),

	docker("docker", 4243), dockerDemo("csap-demo-nginx", 0),

	kubelet("kubelet", 8014),
	kubernetesDashboard("kubernetes-dashboard", 0),
	calicoNode("calico-node", 0),

	tomcat("csap-package-tomcat", 0), tomcatDemo("csap-demo-tomcat", 0),

	mpMonitor("csap-demo-mp-monitor", 0);

	String id ;
	int port ;

	private CsapServices ( String id, int port ) {

		this.id = id ;
		this.port = port ;

	}

	public String id ( ) {

		return id ;

	}

	public String idWithPort ( ) {

		return id + "_" + port ;

	}
}
