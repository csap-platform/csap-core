package org.csap ;

import java.lang.annotation.Documented ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.Inherited ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;
import java.lang.annotation.Target ;

import org.csap.integations.CsapBootConfig ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.CsapPerformance ;
import org.csap.integations.CsapSecurityConfiguration ;
import org.csap.integations.CsapServiceLocator ;
import org.csap.integations.CsapWebServerConfig ;
import org.jasypt.spring31.properties.EncryptablePropertySourcesPlaceholderConfigurer ;
import org.springframework.beans.factory.support.BeanNameGenerator ;
import org.springframework.boot.SpringApplication ;
import org.springframework.boot.SpringBootConfiguration ;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration ;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter ;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration ;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration ;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration ;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration ;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration ;
import org.springframework.boot.context.TypeExcludeFilter ;
import org.springframework.context.annotation.AnnotationBeanNameGenerator ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.ComponentScan ;
import org.springframework.context.annotation.ComponentScan.Filter ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.FilterType ;
import org.springframework.context.annotation.Import ;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer ;
import org.springframework.core.annotation.AliasFor ;
import org.springframework.scheduling.annotation.EnableScheduling ;

/**
 * 
 * CSAP Boot - provides enterprise integration by importing
 * {@link CsapBootConfig }.
 * 
 * Integrations are configured using application.yml to selectively enable and
 * configure integrations. Refer to
 * <a href="https://github.com/csap-platform/csap-starter">Code Samples</a> for
 * configuration examples. <br>
 * Integrations include:
 * 
 * <ul>
 * <li>{@link CsapInformation }: provides CSAP configuration (jvmName, ports,
 * etc) and Servlet/Header/Cookie information</li>
 * 
 * <li>{@link CsapEncryptableProperties }: provides a fully configured
 * {@link EncryptablePropertySourcesPlaceholderConfigurer}. Note Spring only
 * allows a single instance of {@link PropertySourcesPlaceholderConfigurer}.
 * Configured to use CSAP env variables for algorithm and key when deployed in
 * labs.</li>
 * 
 * <li>{@link CsapPerformance }: provides full {@link org.javasimon.Stopwatch
 * JavaSimon} integration. Any spring services annotated with @
 * {@link CsapMonitor} will be included in metrics, along with any jdbc or
 * monitored urls.</li>
 * 
 * <li>{@link CsapSecurityConfiguration }: provides a complete security solution
 * based on configurable settings driven from active directory. Includes SSO
 * across jvms, extensible ACLs, login pages, ...</li>
 * 
 * <li>{@link CsapServiceLocator }: provides client side loadbalancing,
 * including service lookup, and multiple strategies (round-robin, least busy,
 * ...)</li>
 * 
 * <li>{@link CsapWebServerConfig }: provides appliance loadbalancing using
 * Apache ModJk and Httpd.</li>
 * 
 * <li>CSAP Opensouce UI Framework: provides extensive set of the most common
 * javascript and css components available Note: CSAP BootUtils provides either
 * js/css directly or via <a href="http://www.webjars.org/">webjars</a> along
 * with html templates that can be included directly into client projects.</li>
 * 
 * </ul>
 * 
 * <br/>
 * <br/>
 * 
 * 
 * Spring Supplied:<br/>
 * Indicates a {@link Configuration configuration} class that declares one or
 * more {@link Bean @Bean} methods and also triggers
 * {@link EnableAutoConfiguration auto-configuration} and {@link ComponentScan
 * component scanning}. This is a convenience annotation that is equivalent to
 * declaring {@code @Configuration}, {@code @EnableAutoConfiguration} and
 * {@code @ComponentScan}.
 *
 *
 *
 */
@Target ( ElementType.TYPE )
@Retention ( RetentionPolicy.RUNTIME )
@Documented
@Inherited
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableScheduling
@ComponentScan ( excludeFilters = {
		@Filter ( type = FilterType.CUSTOM , classes = TypeExcludeFilter.class ),
		@Filter ( type = FilterType.CUSTOM , classes = AutoConfigurationExcludeFilter.class )
} )
@Import ( CsapBootConfig.class )
public @interface CsapBootApplication {
	/**
	 * Exclude specific auto-configuration classes such that they will never be
	 * applied.
	 * 
	 * @return the classes to exclude
	 */
	@AliasFor ( annotation = EnableAutoConfiguration.class )
	// Class<?>[] exclude() default {};
	Class<?>[] exclude() default {
			SecurityAutoConfiguration.class,
			ManagementWebSecurityAutoConfiguration.class,
			LdapAutoConfiguration.class,
			OAuth2ClientAutoConfiguration.class,
			OAuth2ResourceServerAutoConfiguration.class
	};

	/**
	 * Exclude specific auto-configuration class names such that they will never be
	 * applied.
	 * 
	 * @return the class names to exclude
	 * @since 1.3.0
	 */
	@AliasFor ( annotation = EnableAutoConfiguration.class )
	String[] excludeName() default {};

	/**
	 * Base packages to scan for annotated components. Use
	 * {@link #scanBasePackageClasses} for a type-safe alternative to String-based
	 * package names.
	 * <p>
	 * <strong>Note:</strong> this setting is an alias for
	 * {@link ComponentScan @ComponentScan} only. It has no effect on
	 * {@code @Entity} scanning or Spring Data {@link Repository} scanning. For
	 * those you should add
	 * {@link org.springframework.boot.autoconfigure.domain.EntityScan @EntityScan}
	 * and {@code @Enable...Repositories} annotations.
	 * 
	 * @return base packages to scan
	 * @since 1.3.0
	 */
	@AliasFor ( annotation = ComponentScan.class , attribute = "basePackages" )
	String[] scanBasePackages() default {};

	/**
	 * Type-safe alternative to {@link #scanBasePackages} for specifying the
	 * packages to scan for annotated components. The package of each class
	 * specified will be scanned.
	 * <p>
	 * Consider creating a special no-op marker class or interface in each package
	 * that serves no purpose other than being referenced by this attribute.
	 * <p>
	 * <strong>Note:</strong> this setting is an alias for
	 * {@link ComponentScan @ComponentScan} only. It has no effect on
	 * {@code @Entity} scanning or Spring Data {@link Repository} scanning. For
	 * those you should add
	 * {@link org.springframework.boot.autoconfigure.domain.EntityScan @EntityScan}
	 * and {@code @Enable...Repositories} annotations.
	 * 
	 * @return base packages to scan
	 * @since 1.3.0
	 */
	@AliasFor ( annotation = ComponentScan.class , attribute = "basePackageClasses" )
	Class<?>[] scanBasePackageClasses() default {};

	/**
	 * The {@link BeanNameGenerator} class to be used for naming detected components
	 * within the Spring container.
	 * <p>
	 * The default value of the {@link BeanNameGenerator} interface itself indicates
	 * that the scanner used to process this {@code @SpringBootApplication}
	 * annotation should use its inherited bean name generator, e.g. the default
	 * {@link AnnotationBeanNameGenerator} or any custom instance supplied to the
	 * application context at bootstrap time.
	 * 
	 * @return {@link BeanNameGenerator} to use
	 * @see SpringApplication#setBeanNameGenerator(BeanNameGenerator)
	 * @since 2.3.0
	 */
	@AliasFor ( annotation = ComponentScan.class , attribute = "nameGenerator" )
	Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;

	/**
	 * Specify whether {@link Bean @Bean} methods should get proxied in order to
	 * enforce bean lifecycle behavior, e.g. to return shared singleton bean
	 * instances even in case of direct {@code @Bean} method calls in user code.
	 * This feature requires method interception, implemented through a
	 * runtime-generated CGLIB subclass which comes with limitations such as the
	 * configuration class and its methods not being allowed to declare
	 * {@code final}.
	 * <p>
	 * The default is {@code true}, allowing for 'inter-bean references' within the
	 * configuration class as well as for external calls to this configuration's
	 * {@code @Bean} methods, e.g. from another configuration class. If this is not
	 * needed since each of this particular configuration's {@code @Bean} methods is
	 * self-contained and designed as a plain factory method for container use,
	 * switch this flag to {@code false} in order to avoid CGLIB subclass
	 * processing.
	 * <p>
	 * Turning off bean method interception effectively processes {@code @Bean}
	 * methods individually like when declared on non-{@code @Configuration}
	 * classes, a.k.a. "@Bean Lite Mode" (see {@link Bean @Bean's javadoc}). It is
	 * therefore behaviorally equivalent to removing the {@code @Configuration}
	 * stereotype.
	 * 
	 * @since 2.2
	 * @return whether to proxy {@code @Bean} methods
	 */
	@AliasFor ( annotation = Configuration.class )
	boolean proxyBeanMethods() default true;

}
