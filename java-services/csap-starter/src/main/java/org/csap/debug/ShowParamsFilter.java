package org.csap.debug ;

import java.io.IOException ;
import java.util.Collections ;

import javax.servlet.Filter ;
import javax.servlet.FilterChain ;
import javax.servlet.FilterConfig ;
import javax.servlet.ServletException ;
import javax.servlet.ServletRequest ;
import javax.servlet.ServletResponse ;
import javax.servlet.http.HttpServletRequest ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

//@WebFilter(filterName = "ShowParamsFilter", urlPatterns = { "/*" }, description = "CS-AP Test Filter", initParams = { @WebInitParam(name = "placeHolder", value = "HelloWorld") })
public class ShowParamsFilter implements Filter {

	final static Logger logger = LoggerFactory.getLogger( ShowParamsFilter.class ) ;

	public final static String SHOW_HEADERS_PARAM = "showHeaders" ;

	private FilterConfig _filterConfig ;

	boolean isShowHeaders = false ;

	public void init ( FilterConfig filterConfig ) throws ServletException {

		_filterConfig = filterConfig ;

		if ( _filterConfig.getInitParameter( SHOW_HEADERS_PARAM ).equalsIgnoreCase( Boolean.toString( true ) ) ) {

			isShowHeaders = true ;

		}

		logger.debug( "\n\n =============== CS_AP Params Filter: initialized ============\n"
				+ "Urls matching will have params logged. \n\n showing Headers: ", isShowHeaders ) ;

	}

	private static final String SEPARATOR = "\n ________________________________________________________" ;

	public void doFilter (
							ServletRequest req ,
							ServletResponse res ,
							FilterChain chain )
		throws ServletException ,
		IOException {

		HttpServletRequest httpRequest = (HttpServletRequest) req ;
		StringBuilder builder = new StringBuilder( SEPARATOR ) ;

		if ( isShowHeaders ) {

			builder.append( "\n *Headers: " ) ;

			for ( String name : Collections.list( httpRequest.getHeaderNames( ) ) ) {

				builder.append( "\n\t" + name + "\t\t" + httpRequest.getHeader( name ) ) ;

			}

			builder.append( SEPARATOR ) ;

		}

		builder.append( "\n *Http Parameters: " ) ;

		for ( String name : Collections.list( httpRequest.getParameterNames( ) ) ) {

			builder.append( "\n\t" + name + "\t\t"
					+ httpRequest.getParameter( name ) ) ;

		}

		builder.append( SEPARATOR ) ;
		logger.info( builder.toString( ) ) ;

		chain.doFilter( req, res ) ;

		logger.debug( "Complete" ) ;

		// PrintWriter out = res.getWriter();
		// out.print( _filterConfig.getInitParameter("placeHolder"));
	}

	public void destroy ( ) {

		// destroy
	}
}
