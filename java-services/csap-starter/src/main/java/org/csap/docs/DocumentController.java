package org.csap.docs ;

import java.lang.reflect.Method ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Comparator ;
import java.util.HashMap ;
import java.util.SortedSet ;
import java.util.TreeMap ;
import java.util.TreeSet ;
import java.util.function.Predicate ;

import org.csap.helpers.CSAP ;
import org.csap.integations.CsapInformation ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.aop.support.AopUtils ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.ApplicationContext ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.ModelMap ;
import org.springframework.web.bind.annotation.DeleteMapping ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.PutMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;

/**
 *
 *
 * @see BasicErrorController for configurable urls
 * @author pnightin
 */
@Controller ( "CsapDocController" )
@ConditionalOnProperty ( "csap.documentation.enabled" )
@ConfigurationProperties ( prefix = "csap.documentation" )
@RequestMapping ( "${csap.baseContext:/csap}" + "/docs" )
@CsapDoc ( title = "CSAP REST Document Generator" , type = CsapDoc.OTHER , notes = {
		"Generates documentation and test links for any class annotated with @Controller or @RestController",
		"Usage: Optionally add @CsapDoc to class or method declarations to customize the output",
		"@CsapDoc( ",
		CsapDoc.INDENT + "title=\"Your Api Page Title\"" + CsapDoc.INDENT + "// only applies to class declarations",
		CsapDoc.INDENT + "notes={\"description line 1\", \"description line 2\", ... }, ",
		CsapDoc.INDENT + "linkTests={ \"test1\", \"test2\", ...  } " + CsapDoc.INDENT
				+ "// Optional: test names for links",
		CsapDoc.INDENT + "linkPostParams={ \"name1=value1,name2=value2\" }" + CsapDoc.INDENT
				+ " // Optional: request parameters for posts",
		CsapDoc.INDENT + "</span>linkGetParams={ \"name1=value1,name2=value2\" }" + CsapDoc.INDENT
				+ " // Optional: request parameters for gets",
		")",
		"@RequestMapping(\"/some/url\")   : or @GetMapping, @PostMapping",
		"public JsonNode yourJavaMethod () {...}"

} )
public class DocumentController {

	final Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Override
	public String toString ( ) {

		StringBuilder infoBuilder = new StringBuilder( ) ;
		infoBuilder.append( "\n csap.documentation:" ) ;
		infoBuilder.append( CSAP.padLine( "url" ) + csapInformation.getDocUrl( ) ) ;
		infoBuilder.append( "\n" ) ;
		return infoBuilder.toString( ) ;

	}

	@Autowired
	CsapInformation csapInformation ;

	@Autowired
	private ApplicationContext applicationContext ;

	@RequestMapping ( "/nav" )
	@CsapDoc ( notes = "Generates document index pages" , baseUrl = "/csap/docs" )
	public String generateDocumentNavigation (
												ModelMap modelMap ) {

		TreeMap<String, HashMap<String, String>> publicDocs = new TreeMap<>( ) ;
		addDocumentsWithAnnotation( CsapDoc.class, publicDocs,
				( Class c ) -> {

					if ( c.isAnnotationPresent( CsapDoc.class ) ) {

						CsapDoc csapDoc = (CsapDoc) c.getAnnotation( CsapDoc.class ) ;

						if ( csapDoc.type( ).equals( CsapDoc.PUBLIC ) ) {

							return true ;

						}

					}

					return false ;

				} ) ;

		modelMap.addAttribute( "publicCsapDocs", publicDocs ) ;

		TreeMap<String, HashMap<String, String>> privateCsapDocs = new TreeMap<>( ) ;
		addDocumentsWithAnnotation( CsapDoc.class, privateCsapDocs,
				( Class c ) -> {

					if ( c.isAnnotationPresent( CsapDoc.class ) ) {

						CsapDoc csapDoc = (CsapDoc) c.getAnnotation( CsapDoc.class ) ;

						if ( ! csapDoc.type( ).equals( CsapDoc.PUBLIC )
								&& ! csapDoc.type( ).equals( CsapDoc.OTHER ) ) {

							return true ;

						}

					}

					return false ;

				} ) ;

		modelMap.addAttribute( "privateCsapDocs", privateCsapDocs ) ;

		TreeMap<String, HashMap<String, String>> otherDocs = new TreeMap<>( ) ;

		addDocumentsWithAnnotation(
				Controller.class, otherDocs,
				( Class c ) -> {

					if ( ! privateCsapDocs.containsKey( c.getSimpleName( ) )
							&& ! publicDocs.containsKey( c.getSimpleName( ) ) ) {

						return true ;

					}

					return false ;

				} ) ;

		modelMap.addAttribute( "otherDocs", otherDocs ) ;
		modelMap.addAttribute( "baseUrl", csapInformation.getDocUrl( ) ) ;

		return "csap/docs/navigator" ;

	}

	@SuppressWarnings ( {
			"rawtypes", "unchecked"
	} )
	private void addDocumentsWithAnnotation (
												Class targetAnnotation ,
												TreeMap<String, HashMap<String, String>> docs ,
												Predicate<Class> documentFilter ) {
		// add rest controllers

		try {

			applicationContext
					.getBeansWithAnnotation( targetAnnotation )
					.values( ).stream( )
					.map( springBean -> AopUtils.getTargetClass( springBean ) )
					.filter( documentFilter )
					.forEach( rawClass2 -> {

						// Class rawClass = AopUtils.getTargetClass( springBean );
						Class rawClass = (Class) rawClass2 ;
						HashMap<String, String> classDoc = new HashMap( ) ;

						classDoc.put( "name", rawClass.getSimpleName( ) ) ;

						classDoc.put( "package", rawClass.getPackage( ).getName( ).replaceAll( "\\.", "<wbr/>." ) ) ;

						// classDoc.put( "clazz", rawClass.getCanonicalName() );
						classDoc.put( "clazz", rawClass.getTypeName( ) ) ;

						classDoc.put( "type", "UI" ) ;

						if ( rawClass.isAnnotationPresent( RestController.class ) ) {

							classDoc.put( "type", "REST" ) ;

						}

						String notes = "Add @CsapDoc overview" ;

						if ( rawClass.isAnnotationPresent( CsapDoc.class ) ) {

							CsapDoc csapDoc = (CsapDoc) rawClass.getAnnotation( CsapDoc.class ) ;

							if ( csapDoc.title( ).length( ) > 0 ) {

								notes = csapDoc.title( ) ;

							}

							if ( csapDoc.notes( ).length > 0 ) {

								if ( notes.startsWith( "Add" ) ) {

									notes = "" ;

								} else {

									notes += "<br>" ;

								}

								notes += csapDoc.notes( )[0] ;

							}

						}

						classDoc.put( "notes", notes ) ;
						docs.put( rawClass.getCanonicalName( ), classDoc ) ;

					} ) ;

		} catch ( Exception e ) {

			logger.warn( "Failed building @CsapDoc", e ) ;
			logger.debug( "Reason", e ) ;

		}

	}

	@RequestMapping ( "/class" )
	@CsapDoc ( notes = "Generates documentation and tests for the specified class" , baseUrl = "/csap/docs" , linkTests = "DocumentController" , linkGetParams = "clazz=org.csap.docs.DocumentController" )
	public String generateClassDocs (
										@RequestParam ( "clazz" ) String className ,
										ModelMap modelMap ) {

		ArrayList<ApiDocs> docs = new ArrayList<>( ) ;

		try {

			// Class<AgentResource> obj = AgentResource.class;
			Class theClass = Class.forName( className ) ;
			String baseUrl = "" ;

			if ( theClass.isAnnotationPresent( RequestMapping.class ) ) {

				RequestMapping reqMap = (RequestMapping) theClass.getAnnotation( RequestMapping.class ) ;

				if ( reqMap.value( ).length > 0 ) {

					baseUrl = reqMap.value( )[0] ;

					if ( baseUrl.equals( "/" ) ) {

						baseUrl = "" ;

					}

				}

			}

			// logger.info( "baseUrl: {}", baseUrl );

			modelMap.addAttribute( "baseUrl", baseUrl ) ;

			String title = theClass.getSimpleName( ) ;
			String[] overview = {
					"Add @CsapDoc overview"
			} ;

			if ( theClass.isAnnotationPresent( CsapDoc.class ) ) {

				CsapDoc csapDoc = (CsapDoc) theClass.getAnnotation( CsapDoc.class ) ;

				if ( csapDoc.title( ).length( ) > 0 ) {

					title = csapDoc.title( ) ;

				}

				if ( csapDoc.notes( ).length > 0 ) {

					overview = csapDoc.notes( ) ;

					for ( int i = 0; i < overview.length; i++ ) {

						overview[i] = overview[i].replaceAll( CsapDoc.CSAP_BASE, csapInformation.getHttpContext( ) ) ;
						overview[i] = overview[i].replaceAll( CsapDoc.INDENT, "<span class='indent'></span>" ) ;

					}

				}

			}

			boolean isController = false ;

			if ( theClass.isAnnotationPresent( Controller.class ) ) {

				isController = true ;

			}

			modelMap.addAttribute( "title", title ) ;
			modelMap.addAttribute( "overview", overview ) ;

			SortedSet<Method> sortedPublicMethods = new TreeSet<Method>( new MethodComparator( ) ) ;
			sortedPublicMethods.addAll( Arrays.asList( theClass.getMethods( ) ) ) ;

			for ( Method method : sortedPublicMethods ) {

				if ( method.isAnnotationPresent( RequestMapping.class ) ||
						method.isAnnotationPresent( GetMapping.class ) ||
						method.isAnnotationPresent( PostMapping.class ) ||
						method.isAnnotationPresent( PutMapping.class ) ||
						method.isAnnotationPresent( DeleteMapping.class ) ) {

					String[] vals = null ;
					String[] prods = null ;

					String httpMethod = "GET POST" ;

					if ( method.isAnnotationPresent( RequestMapping.class ) ) {

						RequestMapping reqMap = (RequestMapping) method.getAnnotation( RequestMapping.class ) ;
						vals = reqMap.value( ) ;
						prods = reqMap.produces( ) ;

					} else if ( method.isAnnotationPresent( GetMapping.class ) ) {

						httpMethod = "GET" ;
						GetMapping reqMap = (GetMapping) method.getAnnotation( GetMapping.class ) ;
						vals = reqMap.value( ) ;
						prods = reqMap.produces( ) ;

					} else if ( method.isAnnotationPresent( PostMapping.class ) ) {

						httpMethod = "POST" ;
						PostMapping reqMap = (PostMapping) method.getAnnotation( PostMapping.class ) ;
						vals = reqMap.value( ) ;
						prods = reqMap.produces( ) ;

					} else if ( method.isAnnotationPresent( DeleteMapping.class ) ) {

						httpMethod = "DELETE" ;
						DeleteMapping reqMap = (DeleteMapping) method.getAnnotation( DeleteMapping.class ) ;
						vals = reqMap.value( ) ;
						prods = reqMap.produces( ) ;

					} else if ( method.isAnnotationPresent( PutMapping.class ) ) {

						httpMethod = "PUT" ;
						PutMapping reqMap = (PutMapping) method.getAnnotation( PutMapping.class ) ;
						vals = reqMap.value( ) ;
						prods = reqMap.produces( ) ;

					}

					ApiDocs apiDocs = new ApiDocs( httpMethod, isController, vals, prods, method, baseUrl ) ;
					docs.add( apiDocs ) ;

					if ( method.isAnnotationPresent( Deprecated.class ) ) {

						apiDocs.setDeprecated( true ) ;

					}

					if ( method.isAnnotationPresent( CsapDoc.class ) ) {

						CsapDoc csapDoc = (CsapDoc) method.getAnnotation( CsapDoc.class ) ;
						apiDocs.update( csapDoc ) ;

					}

				}

			}

			modelMap.addAttribute( "apiDocs", docs ) ;

			logger.debug( "docs {}", docs ) ;

		} catch ( ClassNotFoundException ex ) {

			logger.info( "Failed to find class: {}, {}", className, CSAP.buildCsapStack( ex ) ) ;
			modelMap.addAttribute( "error", "Failed to find: " + className ) ;

		}

		return "csap/docs/classTemplate" ;

	}

	private static class MethodComparator implements Comparator<Method> {

		public int compare ( Method m1 , Method m2 ) {

			String m1Compare = m1.getName( ) ;

			if ( m1.isAnnotationPresent( RequestMapping.class ) ) {

				RequestMapping reqMap = (RequestMapping) m1.getAnnotation( RequestMapping.class ) ;

				try {

					m1Compare = reqMap.value( )[0] ;

				} catch ( Exception e ) {

				}

				;

			}

			String m2Compare = m2.getName( ) ;

			if ( m2.isAnnotationPresent( RequestMapping.class ) ) {

				RequestMapping reqMap = (RequestMapping) m2.getAnnotation( RequestMapping.class ) ;

				try {

					m2Compare = reqMap.value( )[0] ;

				} catch ( Exception e ) {

				}

				;

			}

			return ( m1Compare.compareTo( m2Compare ) ) ;

		}

	}

}
