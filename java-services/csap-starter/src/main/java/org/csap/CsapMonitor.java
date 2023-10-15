package org.csap ;

import java.lang.annotation.Documented ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;
import java.lang.annotation.Target ;

/**
 * 
 * CSAP will monitor any spring bean with @CsapMonitor
 * 
 * @author pnightin
 *
 */
@Target ( ElementType.TYPE )
@Retention ( RetentionPolicy.RUNTIME )
@Documented
public @interface CsapMonitor {

	public static String CLASS_NAME = "className" ;
	public static String CLASS_NAME_WITH_PACKAGE = "classNameWithPackage" ;

	// className or classNameWithPackage or Custom
	String prefix() default CLASS_NAME;

	// method name will be added
}
