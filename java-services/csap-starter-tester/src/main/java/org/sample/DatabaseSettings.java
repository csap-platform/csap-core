package org.sample ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.context.properties.ConfigurationProperties ;

@ConfigurationProperties ( prefix = "my-service-configuration.db" )
public class DatabaseSettings {

	private String driverClassName ;

	private long idleEvictionMs = 3000 ;
	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;
	private int maxActive = 10 ;

	private int maxIdle = 10 ;
	private String password ;
	private String url ;
	private String username ;

	private MonitoringSql monitoringSql ;

	public String getDriverClassName ( ) {

		return driverClassName ;

	}

	public long getIdleEvictionMs ( ) {

		return idleEvictionMs ;

	}

	public int getMaxActive ( ) {

		return maxActive ;

	}

	public int getMaxIdle ( ) {

		return maxIdle ;

	}

	public String getPassword ( ) {

		return password ;

	}

	public String getUrl ( ) {

		return url ;

	}

	public String getUsername ( ) {

		return username ;

	}

	public void setDriverClassName ( String driverClassName ) {

		this.driverClassName = driverClassName ;

	}

	public void setIdleEvictionMs ( long idleEvictionMs ) {

		this.idleEvictionMs = idleEvictionMs ;

	}

	public void setMaxActive ( int maxActive ) {

		this.maxActive = maxActive ;

	}

	public void setMaxIdle ( int maxIdle ) {

		this.maxIdle = maxIdle ;

	}

	public void setPassword ( String password ) {

		if ( password.equals( "CHANGE_ME" ) ) {

			logger.error(
					"specified password is {}. Set the dbPass environment variable or update application.yml file",
					password ) ;
			System.exit( 99 ) ;

		}

		this.password = password ;

	}

	public void setUrl ( String url ) {

		this.url = url ;

	}

	public void setUsername ( String username ) {

		this.username = username ;

	}

	public static class MonitoringSql {

		String dbFilterPattern ;
		String tableSize ;
		String indexSize ;
		String stats ;

		String cacheHeap ;
		String cacheIndex ;

		String indexUse ;

		public String getTableSize ( ) {

			return tableSize ;

		}

		public void setTableSize ( String tableSize ) {

			this.tableSize = tableSize ;

		}

		public String getIndexSize ( ) {

			return indexSize ;

		}

		public void setIndexSize ( String indexSize ) {

			this.indexSize = indexSize ;

		}

		public String getStats ( ) {

			return stats ;

		}

		public void setStats ( String stats ) {

			this.stats = stats ;

		}

		public String getCacheHeap ( ) {

			return cacheHeap ;

		}

		public void setCacheHeap ( String cacheHeap ) {

			this.cacheHeap = cacheHeap ;

		}

		public String getCacheIndex ( ) {

			return cacheIndex ;

		}

		public void setCacheIndex ( String cacheIndex ) {

			this.cacheIndex = cacheIndex ;

		}

		public String getIndexUse ( ) {

			return indexUse ;

		}

		public void setIndexUse ( String indexUse ) {

			this.indexUse = indexUse ;

		}

		public String getDbFilterPattern ( ) {

			return dbFilterPattern ;

		}

		public void setDbFilterPattern ( String dbFilterPattern ) {

			this.dbFilterPattern = dbFilterPattern ;

		}

	}

	public MonitoringSql getMonitoringSql ( ) {

		return monitoringSql ;

	}

	public void setMonitoringSql ( MonitoringSql monitoringSql ) {

		this.monitoringSql = monitoringSql ;

	}

}
