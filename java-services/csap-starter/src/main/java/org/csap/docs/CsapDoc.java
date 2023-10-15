/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.docs ;

import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;
import java.lang.annotation.Target ;

/**
 *
 * Note that all attributes are arrays to support multiple test links
 * 
 * 
 * 
 * @author pnightin
 */
@Target ( {
		ElementType.METHOD, ElementType.TYPE
} )
@Retention ( RetentionPolicy.RUNTIME )
public @interface CsapDoc {

	String title() default "";

	/**
	 * only needed when parameterized root urls are registered
	 * 
	 * @return
	 */
	String baseUrl() default "";

	// Used to demark API navigator
	public final String PUBLIC = "public" ;
	public final String PRIVATE = "private" ;
	public final String OTHER = "other" ;

	public final static String CSAP_BASE = "CSAP_BASE" ;
	public final static String INDENT = "-i-" ;

	String type() default "";

	String[] linkPaths() default {};

	String[] linkTests() default {};

	String[] linkGetParams() default {};

	String[] linkPostParams() default {};

	String[] fileParams() default {};

	String[] produces() default {};

	/**
	 * Use in the form of "param1=value1,param2=value2,..."
	 * 
	 * @return
	 */

	String[] notes();

}
