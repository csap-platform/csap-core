package org.csap.agent.linux ;

public class HostStatus {

	public String getUsrCpuStatus ( ) {

		return usrCpuStatus ;

	}

	public void setUsrCpuStatus ( String usrCpuStatus ) {

		this.usrCpuStatus = usrCpuStatus ;

	}

	public String getSysCpuStatus ( ) {

		return sysCpuStatus ;

	}

	public void setSysCpuStatus ( String sysCpuStatus ) {

		this.sysCpuStatus = sysCpuStatus ;

	}

	private String usrCpuStatus = "" ;
	private String sysCpuStatus = "" ;

}
