package org.csap.alerts ;

import java.lang.annotation.Documented ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;
import java.lang.annotation.Target ;
import java.util.concurrent.TimeUnit ;

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
public @interface ServiceAgreement {

	String id() default "CLASSNAME";

	long occurencesMin() default Long.MIN_VALUE;

	long occurencesMax() default Long.MAX_VALUE;

	long maxTime() default Long.MAX_VALUE;

	TimeUnit maxTimeUnit() default TimeUnit.MILLISECONDS;

	long meanTime() default Long.MAX_VALUE;

	TimeUnit meanTimeUnit() default TimeUnit.MILLISECONDS;

}
