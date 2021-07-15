package org.csap.agent.services ;

import java.util.List ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class OsProcess {

	final static Logger logger = LoggerFactory.getLogger( OsProcess.class ) ;

	// pcpu,rss,vsz,nlwp,ruser,pid,nice,ppid,args

	public static OsProcess builder ( List<String> fields ) {

		OsProcess p = null ;

		p = new OsProcess( ) ;
		p.setCpu( fields.get( 0 ) ) ;
		p.setRssMemory( fields.get( 1 ) ) ;
		p.setVirtualMemory( fields.get( 2 ) ) ;
		p.setThreads( fields.get( 3 ) ) ;
		p.setUser( fields.get( 4 ) ) ;
		p.setPid( fields.get( 5 ) ) ;
		p.setPriority( fields.get( 6 ) ) ;
		p.setParentPid( fields.get( 7 ) ) ;
		p.setParameters( fields.get( 8 ) ) ;

		return p ;

	}

	private String cpu ;
	private String rssMemory ;
	private String virtualMemory ;
	private String threads ;
	private String user ;
	private String pid ;
	private String priority ;
	private String parentPid ;
	private String parameters ;

	private boolean matched = false ;
	private boolean namespaceMatched = false ;

	public String getCpu ( ) {

		return cpu ;

	}

	public void setCpu ( String cpu ) {

		this.cpu = cpu ;

	}

	public String getRssMemory ( ) {

		return rssMemory ;

	}

	public void setRssMemory ( String rssMemory ) {

		this.rssMemory = rssMemory ;

	}

	public String getVirtualMemory ( ) {

		return virtualMemory ;

	}

	public void setVirtualMemory ( String virtualMemory ) {

		this.virtualMemory = virtualMemory ;

	}

	public String getThreads ( ) {

		return threads ;

	}

	public void setThreads ( String threads ) {

		this.threads = threads ;

	}

	public String getUser ( ) {

		return user ;

	}

	public void setUser ( String user ) {

		this.user = user ;

	}

	public String getPid ( ) {

		return pid ;

	}

	public void setPid ( String processId ) {

		this.pid = processId ;

	}

	public String getPriority ( ) {

		return priority ;

	}

	public void setPriority ( String priority ) {

		this.priority = priority ;

	}

	public String getParentPid ( ) {

		return parentPid ;

	}

	public void setParentPid ( String parentId ) {

		this.parentPid = parentId ;

	}

	public String getParameters ( ) {

		return parameters ;

	}

	public void setParameters ( String parameters ) {

		this.parameters = parameters ;

	}

	@Override
	public String toString ( ) {

		return "OsProcess [cpu=" + cpu + ", rssMemory=" + rssMemory + ", virtualMemory=" + virtualMemory + ", threads="
				+ threads
				+ ", user=" + user + ", processId=" + pid + ", priority=" + priority + ", parentId=" + parentPid
				+ ", parameters="
				+ parameters + "]" ;

	}

	public boolean isNotMatched ( ) {

		return ! isMatched( ) ;

	}

	public boolean isMatched ( ) {

		return matched ;

	}

	public void setMatched ( boolean matched ) {

		this.matched = matched ;

	}

	public boolean isNotNamespaceMatched ( ) {

		return ! isNamespaceMatched( ) ;

	}

	public boolean isNamespaceMatched ( ) {

		return namespaceMatched ;

	}

	public void setNamespaceMatched ( boolean namespaceMatched ) {

		this.namespaceMatched = namespaceMatched ;

	}
}
