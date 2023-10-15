package org.csap.helpers ;

import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.concurrent.TimeUnit ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.CacheControl ;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry ;
import org.springframework.web.servlet.resource.VersionResourceResolver ;

public class CsapMvc {
	static final Logger logger = LoggerFactory.getLogger( CsapMvc.class ) ;

	//
	// add -DtestCache to force cache on desktop
	//

	static public void addResourceHandlers ( ResourceHandlerRegistry registry, boolean productionCaching ) {

		// use browser test tools to disable cache versus programatic

//		if ( ! CsapApplication.isCsapFolderSet( )
//				&& System.getProperty( "testCache" ) == null ) {
//
//			logger.warn( CsapApplication.testHeader(  "Caching DISABLED " ) );
//			return ; // when disabled in yaml
//			// ONE_YEAR_SECONDS = 0;
//			// return;
//
//		} else {
//
//			logger.info( "Web caching enabled" ) ;
//
//		}

		//
		// requireJs uses relative paths to ensure
		//
//		var versionForJsModules = "start" + System.currentTimeMillis( ) ;
		var versionForJsModules = LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH-mm-ss" ) ) ;
		VersionResourceResolver versionResolver = new VersionResourceResolver( )

				//
				// note that the match pattern is ANT - unlike resource handler path matching
				// css path could be added - but generally not needed "/*.css"
				//
				.addFixedVersionStrategy(
						versionForJsModules,
						"/**/modules/**/*.js" )

				.addContentVersionStrategy( "/**" ) ;

		//
		// Add in expiration and versionResolver
		//

		var oneYear = CacheControl.maxAge( 365, TimeUnit.DAYS ) ;

		//
		// all static content
		//
		registry
				.addResourceHandler( "/**" )
				.addResourceLocations(
						"classpath:/static/",
						"classpath:/public/" )
				.setCacheControl( oneYear ) ;

		//
		// all webjars - note version paths eliminate need for cache blowout
		//
		registry
				.addResourceHandler( "/webjars/**" )
				.addResourceLocations(
						"classpath:/META-INF/resources/webjars/" )
				.setCacheControl( oneYear ) ;

		//
		// css and jss files stored without version requir cache blowout
		//
		registry
				.addResourceHandler( "/css/**", "/js/**" )

				.addResourceLocations(
						"classpath:/static/css/",
						"classpath:/public/css/",
						"classpath:/static/js/",
						"classpath:/public/js/" )

				.setCacheControl( oneYear )

				// generate cache blowing strategy file-asdfasfsdfs.css
				.resourceChain( productionCaching )
				.addResolver( versionResolver ) ;

//		registry
//			.addResourceHandler( "/images/**" )
//			.addResourceLocations(
//					"classpath:/static/images/", "classpath:/public/images/" )
//			.setCacheControl( CacheControl.maxAge( 365, TimeUnit.DAYS ) );
//		
//
//		registry
//			.addResourceHandler( "favicon.ico" )
//			.addResourceLocations(
//					 "classpath:/public/" )
//			.setCacheControl( CacheControl.maxAge( 365, TimeUnit.DAYS ) );

	}

}
