package org.sample ;

import javax.inject.Inject ;
import javax.jms.ConnectionFactory ;

import org.apache.activemq.ActiveMQConnectionFactory ;
import org.aspectj.lang.ProceedingJoinPoint ;
import org.aspectj.lang.annotation.Around ;
import org.aspectj.lang.annotation.Aspect ;
import org.aspectj.lang.annotation.Pointcut ;
import org.csap.helpers.CSAP ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.sample.input.jms.SimpleJms ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.beans.factory.annotation.Value ;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.context.annotation.Bean ;
import org.springframework.context.annotation.Configuration ;
import org.springframework.context.annotation.EnableAspectJAutoProxy ;
import org.springframework.context.annotation.PropertySource ;
import org.springframework.jms.annotation.EnableJms ;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory ;
import org.springframework.jms.connection.SingleConnectionFactory ;
import org.springframework.jms.core.JmsTemplate ;
import org.springframework.jms.listener.DefaultMessageListenerContainer ;

@Configuration
@EnableJms
@Aspect
@EnableAspectJAutoProxy ( proxyTargetClass = true )
@ConditionalOnProperty ( "my-service-configuration.jms.enabled" )
@ConfigurationProperties ( prefix = "my-service-configuration.jms" )
@PropertySource ( SimpleJms.JMS_PROPERTY_FILE )
public class JmsConfig {

	private Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	// common config set in application.yml

	private boolean enabled = true ;
	private String serverUrl = "useApplication.ymlToConfigure" ;
	private int maxMessagesPerTask = -1 ;
	private long receiveTimeout = -5000 ;
	private String concurrency = "-1" ; // number of threads

	@Value ( SimpleJms.SIMPLE_Q )
	private String simpleQueueName = "willbeReplaced" ;

	@Value ( SimpleJms.SCENARIO_Q )
	private String scenarioQueueName = "willbeReplaced" ;

	@Bean ( SimpleJms.FACTORY )
	public DefaultJmsListenerContainerFactory defaultJmsListenerContainerFactory ( ) {

		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory( ) ;
		factory.setConnectionFactory( getJmsListenersConnectionFactory( ) ) ;

		factory.setConcurrency( concurrency ) ;
		factory.setSessionTransacted( true ) ;
		factory.setCacheLevel( DefaultMessageListenerContainer.CACHE_CONSUMER ) ;
		factory.setMaxMessagesPerTask( maxMessagesPerTask ) ;
		factory.setAutoStartup( true ) ;
		factory.setReceiveTimeout( receiveTimeout ) ;

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "@JmsListener Settings" ) ;
		builder.append( CSAP.padLine( "serverUrl" ) + serverUrl ) ;
		builder.append( CSAP.padLine( "q name" ) + scenarioQueueName ) ;
		builder.append( CSAP.padLine( "concurrency" ) + concurrency ) ;
		builder.append( CSAP.padLine( "maxMessagesPerTask" ) + maxMessagesPerTask ) ;
		builder.append( CSAP.padLine( "clientId" ) + "<host>-JmsListeners" ) ;
		builder.append( CSAP.padLine( "receiveTimeout" ) + receiveTimeout ) ;

		logger.warn( builder.toString( ) ) ;

		return factory ;

	}

	@Bean
	public ConnectionFactory getJmsListenersConnectionFactory ( ) {

		ActiveMQConnectionFactory connectionFactory = null ;
		connectionFactory = new ActiveMQConnectionFactory( ) ;
		connectionFactory.setBrokerURL( serverUrl ) ;
		String clientId = csapInformation.getName( ) + "-" + csapInformation.getHostName( ) + "-JmsListeners" ;
		// connectionFactory.setClientID( clientId );
		connectionFactory.setClientIDPrefix( clientId ) ;
		return connectionFactory ;

	}

	//
	// The old way

	public String getServerUrl ( ) {

		return serverUrl ;

	}

	public void setServerUrl ( String serverUrl ) {

		this.serverUrl = serverUrl ;

	}

	@Inject
	CsapInformation csapInformation ;

	/**
	 * 
	 * JMS Message Sender
	 * 
	 * @return
	 */
	@Bean ( name = "sendMsgJmsTemplate" )
	public JmsTemplate getJmsTemplateForMessageSend ( ) {

		logger.debug( "Initializing get jms template " ) ;
		JmsTemplate jmsTemplate = new JmsTemplate( ) ;
		jmsTemplate.setConnectionFactory( getSenderFactory( ) ) ;
		jmsTemplate.setDefaultDestinationName( getScenarioQueueName( ) ) ;

		jmsTemplate.afterPropertiesSet( ) ;

		printStartupMessage( jmsTemplate ) ;

		return jmsTemplate ;

	}

	// 1.3.1 will allow qualifier on listener, so we can make a bean and NOT leak
	// connections on desktop
	// @Bean
	public ConnectionFactory getSenderFactory ( ) {

		SingleConnectionFactory connectionFactory = new SingleConnectionFactory( ) ;
		connectionFactory.setReconnectOnException( true ) ;
		connectionFactory
				.setClientId( csapInformation.getName( ) + "-" + csapInformation.getHostName( ) + "-JMS-SENDER"
						+ System.currentTimeMillis( ) ) ;

		connectionFactory.setTargetConnectionFactory( getJmsListenersConnectionFactory( ) ) ;

		connectionFactory.afterPropertiesSet( ) ;
		return connectionFactory ;

	}

	private void printStartupMessage ( JmsTemplate jmsTemplate ) {

		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "JMS send template settings" ) ;
		builder.append( CSAP.padLine( "Connection Factory" )
				+ jmsTemplate.getConnectionFactory( ).getClass( ).getCanonicalName( ) ) ;
		builder.append( CSAP.padLine( "default Destination" ) + jmsTemplate.getDefaultDestinationName( ) ) ;
		builder.append( CSAP.padLine( "priority" ) + jmsTemplate.getPriority( ) ) ;
		builder.append( CSAP.padLine( "Session Ack Mode" ) + jmsTemplate.getSessionAcknowledgeMode( ) ) ;
		builder.append( CSAP.padLine( "Time to live" ) + jmsTemplate.getTimeToLive( ) ) ;

		logger.warn( builder.toString( ) ) ;

	}

	public String getBrokerInfo ( ) {

		String brokerInfo = "ActiveMQ" ;
		ActiveMQConnectionFactory amq = (ActiveMQConnectionFactory) getJmsListenersConnectionFactory( ) ;
		brokerInfo += " url: " + amq.getBrokerURL( ) ;

		return brokerInfo ;

	}

	// Java Simon Registration
	public static final String JMS_PC = "within(org.sample.input.jms.*Jms*)" ;

	@Autowired
	CsapMeterUtilities microMeterHelper ;

	@Pointcut ( JMS_PC )
	private void jmsPC ( ) {

	};

	@Around ( "jmsPC()" )
	public Object jmsAdvice ( ProceedingJoinPoint pjp )
		throws Throwable {

		return microMeterHelper.timedExecution( pjp, "csap.jms." ) ;

	}

	public long getReceiveTimeout ( ) {

		return receiveTimeout ;

	}

	public void setReceiveTimeout ( long receiveTimeout ) {

		this.receiveTimeout = receiveTimeout ;

	}

	public String getConcurrency ( ) {

		return concurrency ;

	}

	public void setConcurrency ( String concurrency ) {

		this.concurrency = concurrency ;

	}

	public String getSimpleQueueName ( ) {

		return simpleQueueName ;

	}

	public void setSimpleQueueName ( String simpleQueueName ) {

		this.simpleQueueName = simpleQueueName ;

	}

	public String getScenarioQueueName ( ) {

		return scenarioQueueName ;

	}

	public void setScenarioQueueName ( String scenarioQueueName ) {

		this.scenarioQueueName = scenarioQueueName ;

	}

	public int getMaxMessagesPerTask ( ) {

		return maxMessagesPerTask ;

	}

	public void setMaxMessagesPerTask ( int maxMessagesPerTask ) {

		this.maxMessagesPerTask = maxMessagesPerTask ;

	}

	public boolean isEnabled ( ) {

		return enabled ;

	}

	public void setEnabled ( boolean enabled ) {

		this.enabled = enabled ;

	}
}
