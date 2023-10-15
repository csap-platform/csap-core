package org.csap.helpers ;

import java.util.ArrayList ;
import java.util.List ;
import java.util.concurrent.TimeUnit ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class CsapSimpleCache {

	static final Logger logger = LoggerFactory.getLogger( CsapSimpleCache.class ) ;

	public final static long SECOND_IN_MS = 1000 ;
	public final static long MINUTE_IN_MS = 60 * SECOND_IN_MS ;
	public final static long HOUR_IN_MS = 60 * MINUTE_IN_MS ;

	protected CsapSimpleCache ( ) {

	}

	static private List<CsapSimpleCache> cacheReferences = new ArrayList<>( ) ;

	volatile long lastRefreshMs = 0 ; // default to immediated refresh
	long maxAgeInMs = 0 ;
	String className ;
	String description ;
	Object cachedObject = null ;

	static synchronized public CsapSimpleCache builder (
															long duration ,
															TimeUnit unit ,
															Class clazz ,
															String description ) {

		CsapSimpleCache cache = new CsapSimpleCache( ) ;

		cache.setMaxAgeInMs( unit.toMillis( duration ) ) ;
		cache.className = clazz.getCanonicalName( ) ;
		cache.description = description ;

		cacheReferences.add( cache ) ;

		return cache ;

	}

	public String getMaxAgeFormatted ( ) {

		String formatted = TimeUnit.MILLISECONDS.toSeconds( getMaxAgeInMs( ) ) + " seconds" ;
		long minutes = TimeUnit.MILLISECONDS.toMinutes( getMaxAgeInMs( ) ) ;

		if ( minutes > 0 ) {

			formatted = minutes + " minutes" ;

		}

		return formatted ;

	}

	public String getCurrentAgeFormatted ( ) {

		if ( getLastRefreshMs( ) == 0 )
			return "Not Initialized" ;
		String currentAge = TimeUnit.MILLISECONDS.toSeconds( getCurrentAge( ) ) + " seconds" ;
		long minutes = TimeUnit.MILLISECONDS.toMinutes( getCurrentAge( ) ) ;

		if ( minutes > 0 ) {

			currentAge = minutes + " minutes" ;

		}

		return currentAge ;

	}

	public long getCurrentAge ( ) {

		long now = System.currentTimeMillis( ) ;
		return now - getLastRefreshMs( ) ;

	}

	public void expireNow ( ) {

		this.lastRefreshMs = 0 ;

	}

	public boolean isExpired ( ) {

		if ( getCurrentAge( ) > getMaxAgeInMs( ) )
			return true ;
		return false ;

	}

	public void reset ( ) {

		lastRefreshMs = System.currentTimeMillis( ) ;

	}

	public void reset ( Object p ) {

		cachedObject = p ;
		lastRefreshMs = System.currentTimeMillis( ) ;

	}

	public long getLastRefreshMs ( ) {

		return lastRefreshMs ;

	}

//	public void setLastRefreshMs ( long lastRefresh ) {
//		this.lastRefreshMs = lastRefresh;
//	}

	public long getMaxAgeInMs ( ) {

		return maxAgeInMs ;

	}

	public void setMaxAgeInMs ( long maxAge ) {

		this.maxAgeInMs = maxAge ;

	}

	public Object getCachedObject ( ) {

		return cachedObject ;

	}

	public void setCachedObject ( Object cachedObject ) {

		this.cachedObject = cachedObject ;

	}

	public String getClassName ( ) {

		return className ;

	}

	public String getDescription ( ) {

		return description ;

	}

	public static List<CsapSimpleCache> getCacheReferences ( ) {

		return cacheReferences ;

	}

}
