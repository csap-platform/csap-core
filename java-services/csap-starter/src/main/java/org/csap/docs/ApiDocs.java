package org.csap.docs ;

import java.lang.reflect.Method ;
import java.lang.reflect.Parameter ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.Map ;
import java.util.stream.Collectors ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.http.MediaType ;
import org.springframework.web.bind.annotation.PathVariable ;
import org.springframework.web.bind.annotation.RequestParam ;

public class ApiDocs {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	private boolean deprecated = false ;

	private String javaMethodName ;
	private String javaReturnName ;

	private String httpMethod ;
	private String[] apiParams ;

	private ArrayList<String> notes ;
	private String[] linkTests = {} ;
	private String[] paths = {
			"/"
	} ;
	private String[] httpGetParams = {} ;
	private String[] httpPostParams = {} ;
	private String[] fileParams = {} ;
	private String[] produces = {
			MediaType.APPLICATION_JSON_VALUE
	} ;
	private String[] DEFAULT_HTML = {
			MediaType.TEXT_HTML_VALUE
	} ;

	private String baseUrl = "" ;

	public ApiDocs ( String httpMethod, boolean isController, String[] methodValues, String[] methodProduces,
			Method reflectMethod,
			String baseUrl ) {

		super( ) ;

		this.httpMethod = httpMethod ;

		if ( isController ) {

			this.produces = DEFAULT_HTML ;

		}

		this.baseUrl = baseUrl ;

		if ( methodValues != null
				&& methodValues.length > 0 ) {

			this.paths = methodValues ;

			for ( int i = 0; i < paths.length; i++ ) {

				paths[i] = paths[i].replaceAll( ":.\\+", "" ) ;

			}

		}

		if ( methodProduces != null
				&& methodProduces.length > 0 ) {

			this.produces = methodProduces ;

		}

		List<String> springParams = new ArrayList<>( ) ;
		List<String> reqParams = Arrays.asList( reflectMethod.getParameters( ) ).stream( )
				.filter( p -> p.isAnnotationPresent( RequestParam.class ) )
				.map( p -> {

					RequestParam r = p.getAnnotation( RequestParam.class ) ;
					String desc = r.value( ) ;

					if ( desc.length( ) == 0 ) {

						desc = p.getName( ) ;

					}

					if ( r.required( ) ) {

						desc += "*" ;

					} else {

						// desc += "(o)" ;
					}

					return desc ;

				} )
				.collect( Collectors.toList( ) ) ;

		List<String> pathVars = Arrays.asList( reflectMethod.getParameters( ) ).stream( )
				.filter( param -> param.isAnnotationPresent( PathVariable.class ) )
				.map( param -> {

					String[] choices = new String[2] ;
					choices[0] = ( (PathVariable) param.getAnnotation( PathVariable.class ) ).value( ) ;
					choices[1] = param.getName( ) ;
					return choices ;

				} )
				.map( choices -> {

					if ( choices[0].length( ) == 0 ) {

						return choices[1] ;

					}

					return choices[0] ;

				} )
				.collect( Collectors.toList( ) ) ;

		springParams.addAll( reqParams ) ;

		springParams.addAll( pathVars ) ;

		//
		List<String> javaMethodParameters = Arrays.asList( reflectMethod.getParameters( ) ).stream( )
				.map( Parameter::getName )
				.filter( s -> ( ! springParams.contains( s ) && ! springParams.contains( s + "*" ) ) )
				.map( s -> s + "" )
				.collect( Collectors.toList( ) ) ;

		springParams.addAll( javaMethodParameters ) ;

		apiParams = springParams.toArray( new String[0] ) ;
		javaMethodName = reflectMethod.getName( ) ;
		javaReturnName = reflectMethod.getReturnType( ).getName( ) ;

	}

	public ApiDocs ( ) {

	}

	/**
	 * Selective over ride the spring request mapping
	 *
	 * @param csapDoc
	 */
	public void update ( CsapDoc csapDoc ) {

		if ( csapDoc.linkPaths( ).length > 0 ) {

			this.paths = csapDoc.linkPaths( ) ;

		}

		if ( csapDoc.linkTests( ).length > 0 ) {

			this.linkTests = csapDoc.linkTests( ) ;

		}

		if ( csapDoc.baseUrl( ).length( ) > 0 ) {

			this.baseUrl = csapDoc.baseUrl( ) ;

		}

		notes = new ArrayList<>( ) ;

		if ( csapDoc.notes( ).length > 0 ) {

			for ( int i = 0; i < csapDoc.notes( ).length; i++ ) {

				String updated = csapDoc.notes( )[i].replaceAll( CsapDoc.INDENT, "<span class='indent'></span>" ) ;
				notes.add( updated ) ;

			}

		} else {

			notes.add( "<no notes found - add @CsapDoc>" ) ;

		}

		if ( csapDoc.produces( ).length > 0 ) {

			this.produces = csapDoc.produces( ) ;

		}

		this.httpGetParams = csapDoc.linkGetParams( ) ;

		if ( httpGetParams.length > 0 ) {

			var getParams = Arrays.asList( httpGetParams ) ;
			logger.debug( "Found get parameters: {}", getParams ) ;
			// all are literals for thymeleaf - add single quotes
			var paramsWithQuotes = new String[httpGetParams.length] ;

			for ( var i = 0; i < httpGetParams.length; i++ ) {

				var nameValues = httpGetParams[i].split( "," ) ;
				var quotedParams = new StringBuilder( ) ;

				for ( var j = 0; j < nameValues.length; j++ ) {

					var nameValue = nameValues[j].split( "=", 2 ) ;

					if ( quotedParams.length( ) > 0 ) {

						quotedParams.append( "," ) ;

					}

					if ( nameValue[1].contains( "'" ) ) {

						// in case already quoted
						quotedParams.append( nameValues[j] ) ;

					} else {

						// autoquote all parameters for thymeleaf resolution
						quotedParams.append( nameValue[0] + "='" + nameValue[1] + "'" ) ;

					}

				}

				paramsWithQuotes[i] = quotedParams.toString( ) ;

			}

			httpGetParams = paramsWithQuotes ;

		}

		this.httpPostParams = csapDoc.linkPostParams( ) ;
		this.fileParams = csapDoc.fileParams( ) ;

	}

	public boolean isForm ( ) {

		return form ;

	}

	public void setForm ( boolean form ) {

		this.form = form ;

	}

	private boolean form = false ;

	public String getParamList ( ) {

		StringBuilder results = new StringBuilder( ) ;

		if ( getHttpGetParams( ).length > 0 ) {

			for ( String param : getHttpGetParams( ) ) {

				results.append( param ) ;
				results.append( "," ) ;

			}

			results.deleteCharAt( results.length( ) - 1 ) ;

		}

		logger.debug( "params: {}, paths: {}", results.toString( ), Arrays.asList( getPaths( ) ) ) ;

		return results.toString( ) ;

	}

	public class Link {

		public String url ;
		public String text ;
		public String params ;
		public Map<String, String> postParams ;
		public List<String> fileParams ;

	}

	public List<Link> getTests ( ) {

		List<Link> links = new ArrayList<>( ) ;

		if ( getLinkTests( ).length == 0 ) {

			for ( int i = 0; i < getPaths( ).length; i++ ) {

				Link l = new Link( ) ;
				l.url = baseUrl + getPaths( )[i] ;
				l.text = l.url ;
				l.text = l.text.replaceAll( "/", "<wbr/>/" ) ;

				if ( getHttpGetParams( ).length > i ) {

					l.params = getHttpGetParams( )[i] ;

				}

				links.add( l ) ;

			}

		} else {

			for ( int i = 0; i < getLinkTests( ).length; i++ ) {

				try {

					Link link = new Link( ) ;

					link.url = baseUrl + getPaths( )[0] ;

					if ( getLinkTests( ).length > i ) {

//						link.text	= link.url + " (" + getLinkTests()[i] + ")" ;
						link.text = "Test " + getLinkTests( )[i] + ":" ;
						link.text = link.text.replaceAll( "/", "<wbr/>/" ) ;

					}

					if ( getHttpGetParams( ).length > i ) {

						link.params = getHttpGetParams( )[i] ;

					}

					if ( getHttpPostParams( ).length > i ) {

						link.postParams = new LinkedHashMap<String, String>( ) ;
						String[] postArray = getHttpPostParams( )[i].split( "," ) ;

						for ( String nameValue : postArray ) {

							String[] items = nameValue.split( "=" ) ;
							link.postParams.put( items[0].trim( ), items[1].trim( ) ) ;

							if ( items[1].trim( ).equals( "blank" ) ) {

								link.postParams.put( items[0], "" ) ;

							}

						}

					}

					if ( getFileParams( ).length > i ) {

						link.fileParams = new ArrayList<String>( ) ;
						String[] filePostArray = getFileParams( )[i].split( "," ) ;

						for ( String name : filePostArray ) {

							link.fileParams.add( name.trim( ) ) ;

						}

					}

					links.add( link ) ;

				} catch ( Exception e ) {

					logger.warn( "Failed parsing doc", e ) ;

				}

			}

		}

		return links ;

	}

	@Override
	public String toString ( ) {

		return "\n\nApiDocs [deprecated=" + deprecated + ", javaMethodName=" + javaMethodName + ", javaReturnName="
				+ javaReturnName + ", httpMethod=" + httpMethod + ", apiParams=" + Arrays.toString( apiParams )
				+ ", notes=" + notes + ", linkTests=" + Arrays.toString( linkTests ) + ", paths=" + Arrays.toString(
						paths ) + ", httpGetParams=" + Arrays.toString( httpGetParams ) + ", httpPostParams=" + Arrays
								.toString( httpPostParams ) + ", fileParams=" + Arrays.toString( fileParams )
				+ ", produces=" + Arrays.toString( produces ) + ", DEFAULT_HTML=" + Arrays.toString( DEFAULT_HTML )
				+ ", baseUrl=" + baseUrl + ", form=" + form + "]" ;

	}

	/**
	 * @return the url
	 */
	public String[] getPaths ( ) {

		return paths ;

	}

	public String getFirstPath ( ) {

		if ( getPaths( ).length > 0 ) {

			return getPaths( )[0].replaceAll( "/", "<wbr/>/" ) ;

		} else {

			return "path-not-found" ;

		}

	}

	/**
	 * @param paths the url to set
	 */
	public void setPaths ( String[] paths ) {

		this.setPaths( paths ) ;

	}

	/**
	 * @return the produces
	 */
	public String[] getProduces ( ) {

		return produces ;

	}

	public String getProducesHtml ( ) {

		return Arrays.asList( getProduces( ) ).toString( ) ;

	}

	/**
	 * @param produces the produces to set
	 */
	public void setProduces ( String[] produces ) {

		this.setProduces( produces ) ;

	}

	/**
	 * @param params the params to set
	 */
	public void setParams ( String[] params ) {

		this.setParams( params ) ;

	}

	/**
	 * @return the javaMethodName
	 */
	public String getJavaMethodName ( ) {

		return javaMethodName ;

	}

	/**
	 * @param javaMethodName the javaMethodName to set
	 */
	public void setJavaMethodName ( String javaMethodName ) {

		this.javaMethodName = javaMethodName ;

	}

	/**
	 * @return the javaParamNames
	 */
	public String[] getApiParams ( ) {

		return apiParams ;

	}

	/**
	 * @param apiParams the javaParamNames to set
	 */
	public void setApiParams ( String[] apiParams ) {

		this.apiParams = apiParams ;

	}

	/**
	 * @return the notes
	 */
	public List<String> getNotes ( ) {

		return notes ;

	}

	/**
	 * @return the linkText
	 */
	public String[] getLinkTests ( ) {

		return linkTests ;

	}

	/**
	 * @param linkTests the linkText to set
	 */
	public void setLinkTests ( String[] linkTests ) {

		this.linkTests = linkTests ;

	}

	/**
	 * @return the httpGetParams
	 */
	public String[] getHttpGetParams ( ) {

		return httpGetParams ;

	}

	/**
	 * @param httpGetParams the httpGetParams to set
	 */
	public void setHttpGetParams ( String[] httpGetParams ) {

		this.httpGetParams = httpGetParams ;

	}

	/**
	 * @return the httpPostParams
	 */
	public String[] getHttpPostParams ( ) {

		return httpPostParams ;

	}

	/**
	 * @param httpPostParams the httpPostParams to set
	 */
	public void setHttpPostParams ( String[] httpPostParams ) {

		this.httpPostParams = httpPostParams ;

	}

	public String[] getFileParams ( ) {

		return fileParams ;

	}

	public void setFileParams ( String[] fileParams ) {

		this.fileParams = fileParams ;

	}

	public boolean isDeprecated ( ) {

		return deprecated ;

	}

	public void setDeprecated ( boolean deprecated ) {

		this.deprecated = deprecated ;

	}

	public String getHttpMethod ( ) {

		return httpMethod ;

	}

	public void setHttpMethod ( String httpMethod ) {

		this.httpMethod = httpMethod ;

	}

	public String getJavaReturnName ( ) {

		return javaReturnName ;

	}

	public void setJavaReturnName ( String javaReturnName ) {

		this.javaReturnName = javaReturnName ;

	}

}
