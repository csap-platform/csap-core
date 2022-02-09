package org.csap.agent ;

import org.csap.agent.container.C7 ;

public enum TestServices {

	agent(CsapConstants.AGENT_NAME, 8011), admin(CsapConstants.ADMIN_NAME, 8021), csap_verify("csap-verify-service",
			7011),
	linux("csap-package-linux", 0), java("csap-package-java", 0),

	docker(C7.dockerService.val( ), 4243), dockerDemo("csap-demo-nginx", 0),

	kubelet("kubelet", 8014),
	kubernetesDashboard("kubernetes-dashboard", 0),
	calicoNode("calico-node", 0),

	tomcat("csap-package-tomcat", 0), tomcatDemo("csap-demo-tomcat", 0),

	mpMonitor("csap-demo-mp-monitor", 0);

	String id ;
	int port ;

	private TestServices ( String id, int port ) {

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
