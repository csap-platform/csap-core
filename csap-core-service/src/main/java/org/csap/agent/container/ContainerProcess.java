package org.csap.agent.container ;

import java.util.List ;
import java.util.stream.Collectors ;

import org.csap.agent.model.ServiceInstance ;

import com.fasterxml.jackson.annotation.JsonIgnore ;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties ;

@JsonIgnoreProperties ( ignoreUnknown = true )
public class ContainerProcess {

	private String pid ;

	// really: for non kubernetes - this is the container name. For kubernetes -
	// this is the kubernetes label
	private String matchName ;

	private boolean isInDefinition = false ;
	private boolean nameSpaceMatched = false ;

	private String containerName ;

	private String podName ;
	private String podIp ;

	private String podNamespace ;

	public String getPid ( ) {

		return pid ;

	}

	public void setPid ( String processId ) {

		this.pid = processId ;

	}

	public String getContainerName ( ) {

		return containerName ;

	}

	public void setContainerName ( String containerName ) {

		this.containerName = containerName ;

	}

	public String getPodName ( ) {

		return podName ;

	}

	public void setPodName ( String podName ) {

		this.podName = podName ;

	}

	public String getMatchName ( ) {

		return matchName ;

	}

	public void setMatchName ( String processMatch ) {

		this.matchName = processMatch ;

	}

	@Override
	public String toString ( ) {

		return "ContainerProcess [pid=" + pid + ", matchName=" + matchName + ", containerName=" + containerName
				+ ", podName=" + podName + "]" ;

	}

	@JsonIgnore
	public boolean isNotInDefinition ( ) {

		return ! isInDefinition ;

	}

	public boolean isInDefinition ( ) {

		return isInDefinition ;

	}

	public void setInDefinition ( boolean foundMatch ) {

		this.isInDefinition = foundMatch ;

	}

	static public List<String> buildUnregisteredSummaryReport ( List<ContainerProcess> containers ) {

		List<String> discoveredContainers = containers.stream( )
				.filter( ContainerProcess::isNotInDefinition )
				.map( ContainerProcess::buildContainerSummaryCsv )
				.collect( Collectors.toList( ) ) ;
		return discoveredContainers ;

	}

	/**
	 * 
	 * @see ServiceInstance#buildUnregistered(String, String)
	 * 
	 * @return
	 */
	private String buildContainerSummaryCsv ( ) {

		String summaryCsv = getMatchName( ) ;

		summaryCsv += "," + getContainerName( ) ;
		summaryCsv += "," + getPodName( ) ;
		summaryCsv += "," + getPodNamespace( ) ;

		return summaryCsv ;

	}

	public String getPodNamespace ( ) {

		return podNamespace ;

	}

	public void setPodNamespace ( String podNamespace ) {

		this.podNamespace = podNamespace ;

	}

	public String getPodIp ( ) {

		return podIp ;

	}

	public void setPodIp ( String podIp ) {

		this.podIp = podIp ;

	}

	@JsonIgnore

	public boolean isNotNamespaceMatched ( ) {

		return ! isNameSpaceMatched( ) ;

	}

	public boolean isNameSpaceMatched ( ) {

		return nameSpaceMatched ;

	}

	public void setNameSpaceMatched ( boolean nameSpaceMatched ) {

		this.nameSpaceMatched = nameSpaceMatched ;

	}

	// static public List<String> findUnregisteredContainersAndPods (
	// List<ContainerProcess> containers) {
	// List<String> discoveredContainers = containers.stream()
	// .filter( ContainerProcess::isNotInDefinition )
	// .map( ContainerProcess::getMatchNameAndPodIfPresent )
	// .collect( Collectors.toList() );
	// return discoveredContainers;
	// }

}
