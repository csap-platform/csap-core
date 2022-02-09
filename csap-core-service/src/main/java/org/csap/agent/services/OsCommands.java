package org.csap.agent.services ;

import java.util.Arrays ;
import java.util.List ;
import java.util.regex.Matcher ;
import java.util.stream.Collectors ;

import org.csap.agent.CsapConstants ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

@ConfigurationProperties ( CsapConstants.CONFIGURATION_PREFIX + ".os-commands" )
public class OsCommands {

	private static List<String> NOT_INITIALIZED = Arrays.asList( "Not", "Initialized" ) ;

	private List<String> processStatus = NOT_INITIALIZED ;
	private List<String> systemProcessMetrics = NOT_INITIALIZED ;
	private List<String> systemNetworkDevices = NOT_INITIALIZED ;
	private List<String> systemNetworkPorts = NOT_INITIALIZED ;
	private List<String> systemNetworkListenPorts = NOT_INITIALIZED ;

	private List<String> systemDiskWithRateOnly = NOT_INITIALIZED ;
	private List<String> systemDiskWithUtilization = NOT_INITIALIZED ;

	private List<String> criPs = NOT_INITIALIZED ;
	private List<String> criPidReport = NOT_INITIALIZED ;
	private List<String> criInspect = NOT_INITIALIZED ;

	private List<String> diskUsageSystem = NOT_INITIALIZED ;
	private List<String> diskUsageAbout = NOT_INITIALIZED ;
	private List<String> diskUsageCsap = NOT_INITIALIZED ;

	private List<String> systemNetworkStats = NOT_INITIALIZED ;
	private List<String> systemPackages = NOT_INITIALIZED ;
	private List<String> systemPackageDetails = NOT_INITIALIZED ;
	private List<String> nfsMountLocation = NOT_INITIALIZED ;
	private List<String> systemServices = NOT_INITIALIZED ;
	private List<String> systemServiceListing = NOT_INITIALIZED ;
	private List<String> systemServiceDetails = NOT_INITIALIZED ;

	private List<String> serviceDiskIo = NOT_INITIALIZED ;
	private List<String> serviceDiskUsage = NOT_INITIALIZED ;
	private List<String> serviceDiskUsageDf = NOT_INITIALIZED ;
	private List<String> serviceSockets = NOT_INITIALIZED ;
	private List<String> serviceSocketsDocker = NOT_INITIALIZED ;

	private List<String> fileReadPermissions = NOT_INITIALIZED ;

	private List<String> serviceJobsDiskClean = NOT_INITIALIZED ;

	private List<String> infraTestDisk = NOT_INITIALIZED ;
	private List<String> infraTestCpu = NOT_INITIALIZED ;

	private List<String> dockerImageExport = NOT_INITIALIZED ;
	private List<String> dockerImageLoad = NOT_INITIALIZED ;
	private List<String> dockerSocketStats = NOT_INITIALIZED ;

	private List<String> dockerContainerPids = NOT_INITIALIZED ;

	private List<String> govcDatastoreList = NOT_INITIALIZED ;

	private List<String> govcDatastoreInfo = NOT_INITIALIZED ;

	private List<String> govcDatastoreLs = NOT_INITIALIZED ;

	private List<String> govcDatastoreRecurse = NOT_INITIALIZED ;
	private List<String> govcVmList = NOT_INITIALIZED ;
	private List<String> govcVmFind = NOT_INITIALIZED ;
	private List<String> govcVmInfo = NOT_INITIALIZED ;

	public final static String LINE_SEPARATOR = "\n" ;

	List<String> toList ( String item ) {

		return Arrays.asList( item.split( LINE_SEPARATOR ) ) ;

	}

	public static String asScript ( List<String> lines ) {

		return lines.stream( ).collect( Collectors.joining( "\n" ) ) ;

	}

	public List<String> getProcessStatus ( ) {

		return processStatus ;

	}

	public void setProcessStatus ( String processStatus ) {

		this.processStatus = toList( processStatus ) ;

	}

	@Override
	public String toString ( ) {

		return "OsCommands [processStatus=" + processStatus + ", systemProcessMetrics=" + systemProcessMetrics
				+ ", systemNetworkDevices="
				+ systemNetworkDevices + ", systemNetworkPorts=" + systemNetworkPorts + ", systemDiskWithRateOnly="
				+ systemDiskWithRateOnly
				+ ", systemDiskWithUtilization=" + systemDiskWithUtilization + ", diskUsageSystem=" + diskUsageSystem
				+ ", systemNetworkStats=" + systemNetworkStats + ", systemPackages=" + systemPackages
				+ ", systemPackageDetails="
				+ systemPackageDetails + ", systemServices=" + systemServices + ", systemServiceListing="
				+ systemServiceListing
				+ ", systemServiceDetails=" + systemServiceDetails + ", serviceDiskIo=" + serviceDiskIo
				+ ", serviceDiskUsage="
				+ serviceDiskUsage + ", serviceDiskUsageDf=" + serviceDiskUsageDf + ", serviceSockets=" + serviceSockets
				+ ", serviceSocketsDocker=" + serviceSocketsDocker + ", fileReadPermissions=" + fileReadPermissions
				+ ", infraTestDisk="
				+ infraTestDisk + ", infraTestCpu=" + infraTestCpu + ", dockerImageExport=" + dockerImageExport
				+ ", dockerImageLoad="
				+ dockerImageLoad + ", dockerSocketStats=" + dockerSocketStats + ", dockerContainerPids="
				+ dockerContainerPids
				+ ", govcDatastoreList=" + govcDatastoreList + "]" ;

	}

	public List<String> getDiskUsageSystem ( ) {

		return diskUsageSystem ;

	}

	public void setDiskUsageSystem ( String diskUsageSystem ) {

		this.diskUsageSystem = toList( diskUsageSystem ) ;

	}

	public List<String> getDiskUsageAbout ( ) {

		return diskUsageAbout ;

	}

	public void setDiskUsageAbout ( String diskUsageAbout ) {

		this.diskUsageAbout = toList( diskUsageAbout ) ;

	}

	public List<String> getDiskUsageCsap ( ) {

		return diskUsageCsap ;

	}

	public void setDiskUsageCsap ( String diskUsageCsap ) {

		this.diskUsageCsap = toList( diskUsageCsap ) ;

	}

	public List<String> getGovcDatastoreList ( ) {

		return govcDatastoreList ;

	}

	public void setGovcDatastoreList ( String govcDatastoreList ) {

		this.govcDatastoreList = toList( govcDatastoreList ) ;

	}

	public List<String> getGovcVmList ( String path ) {

		return govcVmList
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$path" ), path ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcVmList ( String govcVmList ) {

		this.govcVmList = toList( govcVmList ) ;

	}

	public List<String> getGovcVmFind ( String vmFilter ) {

		return govcVmFind
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$vmFilter" ), vmFilter ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcVmFind ( String govcVmFind ) {

		this.govcVmFind = toList( govcVmFind ) ;

	}

	public List<String> getGovcVmInfo ( String vmPath ) {

		return govcVmInfo
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$path" ), vmPath ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcVmInfo ( String govcVmInfo ) {

		this.govcVmInfo = toList( govcVmInfo ) ;

	}

	public List<String> getGovcDatastoreInfo ( String datastore ) {

		return govcDatastoreInfo
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$datastore" ), datastore ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcDatastoreInfo ( String govcDatastoreInfo ) {

		this.govcDatastoreInfo = toList( govcDatastoreInfo ) ;

	}

	public List<String> getGovcDatastoreLs ( String datastore , String path ) {

		return govcDatastoreLs
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$datastore" ), datastore ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$path" ), path ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcDatastoreLs ( String govcDatastoreLs ) {

		this.govcDatastoreLs = toList( govcDatastoreLs ) ;

	}

	public List<String> getGovcDatastoreRecurse ( String datastore ) {

		return govcDatastoreRecurse
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$datastore" ), datastore ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setGovcDatastoreRecurse ( String govcDatastoreRecurse ) {

		this.govcDatastoreRecurse = toList( govcDatastoreRecurse ) ;

	}

	public List<String> getFileReadPermissions ( String user , String file ) {

		return fileReadPermissions
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$user" ), user ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$file" ), file ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setFileReadPermissions ( String fileReadPermissions ) {

		this.fileReadPermissions = toList( fileReadPermissions ) ;

	}

	public List<String> getServiceDiskIo ( ) {

		return serviceDiskIo ;

	}

	public void setServiceDiskIo ( String serviceDiskIo ) {

		this.serviceDiskIo = toList( serviceDiskIo ) ;

	}

	public List<String> getServiceSockets ( ) {

		return serviceSockets ;

	}

	public void setServiceSockets ( String serviceSockets ) {

		this.serviceSockets = toList( serviceSockets ) ;

	}

	public List<String> getServiceSocketsDocker ( String pid ) {

		return serviceSocketsDocker
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$pid" ), pid ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setServiceSocketsDocker ( String serviceSocketsDocker ) {

		this.serviceSocketsDocker = toList( serviceSocketsDocker ) ;

	}

	public List<String> getDockerImageExport ( String destination , String imageName ) {

		return dockerImageExport
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$destination" ), destination ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$imageName" ), imageName ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setDockerImageExport ( String dockerImageExport ) {

		this.dockerImageExport = toList( dockerImageExport ) ;

	}

	public List<String> getDockerImageLoad ( String sourceTar ) {

		return dockerImageLoad
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$sourceTar" ), sourceTar ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setDockerImageLoad ( String dockerImageLoad ) {

		this.dockerImageLoad = toList( dockerImageLoad ) ;

	}

	public List<String> getDockerSocketStats ( String pid ) {

		return dockerSocketStats
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$pid" ), pid ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setDockerSocketStats ( String dockerSocketStats ) {

		this.dockerSocketStats = toList( dockerSocketStats ) ;

	}

	public List<String> getDockerContainerPids ( ) {

		return dockerContainerPids ;

	}

	public void setDockerContainerPids ( String script ) {

		this.dockerContainerPids = toList( script ) ;

	}

	// public List<String> getSystemNetworkStats () { return systemNetworkStats ; }
	public List<String> getSystemNetworkStats ( String interfacePattern ) {

		return systemNetworkStats
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement(
						"$interfacePattern" ),
						interfacePattern.substring( 0, interfacePattern.length( ) - 1 ) ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setSystemNetworkStats ( String systemNetworkStats ) {

		this.systemNetworkStats = toList( systemNetworkStats ) ;

	}

	public List<String> getSystemPackages ( ) {

		return systemPackages ;

	}

	public void setSystemPackages ( String systemPackages ) {

		this.systemPackages = toList( systemPackages ) ;

	}

	public List<String> getNfsMountLocation ( String mountSource ) {

		return nfsMountLocation
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$mountSource" ), mountSource ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setDiskNfsMountLocation ( String nfsMountLocation ) {

		this.nfsMountLocation = toList( nfsMountLocation ) ;

	}

	public List<String> getSystemPackageDetails ( String packageName ) {

		return systemPackageDetails
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$package" ), packageName ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setSystemPackageDetails ( String systemPackageDetails ) {

		this.systemPackageDetails = toList( systemPackageDetails ) ;

	}

	public List<String> getSystemServices ( ) {

		return systemServices ;

	}

	public void setSystemServices ( String systemServices ) {

		this.systemServices = toList( systemServices ) ;

	}

	public List<String> getSystemServiceDetails ( String serviceName ) {

		return systemServiceDetails
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$serviceName" ), serviceName ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setSystemServiceDetails ( String systemServiceDetails ) {

		this.systemServiceDetails = toList( systemServiceDetails ) ;

	}

	public List<String> getServiceDiskUsage ( String servicePaths ) {

		return serviceDiskUsage
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$servicePaths" ), servicePaths ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setServiceDiskUsage ( String serviceDiskUsage ) {

		this.serviceDiskUsage = toList( serviceDiskUsage ) ;

	}

	public List<String> getServiceDiskUsageDf ( ) {

		return serviceDiskUsageDf ;

	}

	public void setServiceDiskUsageDf ( String serviceDiskUsageDf ) {

		this.serviceDiskUsageDf = toList( serviceDiskUsageDf ) ;

	}

	public List<String> getInfraTestDisk ( String blockSize , String numBlocks ) {

		return infraTestDisk
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$blockSize" ), blockSize ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$numBlocks" ), numBlocks ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setInfraTestDisk ( String infraTestDisk ) {

		this.infraTestDisk = toList( infraTestDisk ) ;

	}

	public List<String> getInfraTestCpu ( String numLoops ) {

		return infraTestCpu
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$numLoops" ), numLoops ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setInfraTestCpu ( String infraTestCpu ) {

		this.infraTestCpu = toList( infraTestCpu ) ;

	}

	public List<String> getSystemDiskWithUtilization ( ) {

		return systemDiskWithUtilization ;

	}

	public void setSystemDiskWithUtilization ( String systemDiskWithUtilization ) {

		this.systemDiskWithUtilization = toList( systemDiskWithUtilization ) ;

	}

	public List<String> getSystemDiskWithRateOnly ( ) {

		return systemDiskWithRateOnly ;

	}

	public void setSystemDiskWithRateOnly ( String systemDiskWithRateOnly ) {

		this.systemDiskWithRateOnly = toList( systemDiskWithRateOnly ) ;

	}

	public List<String> getSystemServiceListing ( String staging ) {

		return systemServiceListing
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$$platform" ), staging ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setSystemServiceListing ( String systemServiceListing ) {

		this.systemServiceListing = toList( systemServiceListing ) ;

	}

	public List<String> getSystemProcessMetrics ( int seconds ) {

		return systemProcessMetrics
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$seconds" ), Integer.toString( seconds ) ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setSystemProcessMetrics ( String systemProcessMetrics ) {

		this.systemProcessMetrics = toList( systemProcessMetrics ) ;

	}

	public List<String> getSystemNetworkDevices ( ) {

		return systemNetworkDevices ;

	}

	public void setSystemNetworkDevices ( String systemNetworkDevices ) {

		this.systemNetworkDevices = toList( systemNetworkDevices ) ;

	}

	public List<String> getSystemNetworkPorts ( ) {

		return systemNetworkPorts ;

	}

	public void setSystemNetworkPorts ( String systemNetworkPorts ) {

		this.systemNetworkPorts = toList( systemNetworkPorts ) ;

	}

	public List<String> getSystemNetworkListenPorts ( ) {

		return systemNetworkListenPorts ;

	}

	public void setSystemNetworkListenPorts ( String systemNetworkListenPorts ) {

		this.systemNetworkListenPorts = toList( systemNetworkListenPorts ) ;

	}

	public List<String> getServiceJobsDiskClean (
													String jobPath ,
													int maxDepth ,
													int numDays ,
													boolean pruneByFolder ,
													boolean runPrune ) {

		return serviceJobsDiskClean
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$jobPath" ), jobPath ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$maxDepth" ), Integer.toString( maxDepth ) ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$numDays" ), Integer.toString( numDays ) ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$pruneByFolder" ), Boolean.toString(
						pruneByFolder ) ) )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$runPrune" ), Boolean.toString( runPrune ) ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setServiceJobsDiskClean ( String serviceJobsDiskClean ) {

		this.serviceJobsDiskClean = toList( serviceJobsDiskClean ) ;

	}

	public List<String> getCriPs ( ) {

		return criPs ;

	}

	public void setCriPs ( String crictlPs ) {

		this.criPs = toList( crictlPs ) ;

	}

	public List<String> getCriInspect ( String id ) {

		return criInspect
				.stream( )
				.map( line -> line.replaceAll( Matcher.quoteReplacement( "$id" ), id ) )
				.collect( Collectors.toList( ) ) ;

	}

	public void setCriInspect ( String crictlInspect ) {

		this.criInspect = toList( crictlInspect ) ;

	}

	public List<String> getCriPidReport ( ) {

		return criPidReport ;

	}

	public void setCriPidReport ( String criPidReport ) {

		this.criPidReport = toList( criPidReport ) ;

	}

}
