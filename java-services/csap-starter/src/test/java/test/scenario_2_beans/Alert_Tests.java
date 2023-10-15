package test.scenario_2_beans ;

import static org.assertj.core.api.Assertions.assertThat ;

import java.util.ArrayList ;
import java.util.List ;
import java.util.Properties ;
import java.util.concurrent.TimeUnit ;

import org.apache.commons.lang3.StringUtils ;
import org.csap.alerts.AlertFields ;
import org.csap.alerts.AlertInstance ;
import org.csap.alerts.AlertProcessor ;
import org.csap.alerts.AlertSettings ;
import org.csap.helpers.CsapApplication ;
import org.csap.integations.CsapInformation ;
import org.csap.integations.micrometer.CsapMeterUtilities ;
import org.csap.integations.micrometer.CsapMicroMeter ;
import org.csap.integations.micrometer.MeterReport ;
import org.junit.jupiter.api.BeforeAll ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.TestInstance ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.boot.SpringBootConfiguration ;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration ;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration ;
import org.springframework.boot.context.properties.ConfigurationProperties ;
import org.springframework.boot.test.context.SpringBootTest ;
import org.springframework.mail.javamail.JavaMailSenderImpl ;
import org.springframework.mail.javamail.MimeMessageHelper ;
import org.springframework.test.annotation.DirtiesContext ;
import org.springframework.test.context.ActiveProfiles ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry ;

@SpringBootTest ( classes = Alert_Tests.Bare_Application.class )
@ConfigurationProperties ( prefix = "test.mail" )
@DirtiesContext
@ActiveProfiles ( "junit" )
@TestInstance ( TestInstance.Lifecycle.PER_CLASS )
public class Alert_Tests {

	final static private Logger logger = LoggerFactory.getLogger( Alert_Tests.class ) ;

	@SpringBootConfiguration
	@ImportAutoConfiguration ( classes = {
			PropertyPlaceholderAutoConfiguration.class,
			ConfigurationPropertiesAutoConfiguration.class
	} )
	public static class Bare_Application {
	}

	private String toAddress ;
	private String fromAddress ;
	private String host ;
	private int port ;

	ObjectMapper jsonMapper = new ObjectMapper( ) ;

	AlertProcessor alertProcessor ;

	MeterReport meterReport ;

	CsapMeterUtilities helpers = new CsapMeterUtilities( null ) ;

	@BeforeAll
	public void beforeAll ( )
		throws Exception {

		CsapApplication.initialize( logger.getName( ) ) ;

		SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry( ) ;

		helpers.setSimpleMeterRegistry( simpleMeterRegistry ) ;

		meterReport = new MeterReport( simpleMeterRegistry ) ;
		meterReport.setHealthReport( new CsapMicroMeter.HealthReport( simpleMeterRegistry, jsonMapper ) ) ;

		AlertSettings alertSettings = new AlertSettings( ) ;

		alertProcessor = new AlertProcessor( alertSettings, new CsapInformation( ), meterReport, helpers ) ;

	}

	@Test
	public void verify_alert_mail_template ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		String text = alertProcessor.buildEmailText( ) ;

		logger.debug( "Text: {}", text ) ;

		assertThat( text )
				.as( "template text" )
				.contains( "<title>CSAP Notification</title>", "<td class=\"val\">default_" ) ;

	}

	@Test
	public void verify_alert_min ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		AlertInstance testMinAlert = new AlertInstance( "csap.test.min", 10 ) ;
		var alertDefinitions = alertProcessor.getSettings( ) ;

		alertDefinitions.setLimits( List.of( testMinAlert ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		runTimer( testMinAlert.getId( ), 7 ) ;
		alertProcessor.collectAlertSample( testMinAlert ) ;

		logger.info( "testMinAlert: {}", testMinAlert ) ;

		assertThat( testMinAlert.getLatestSample( ).path( "total-ms" ).asLong( ) )
				.as( "template text" )
				.isGreaterThan( 5 ) ;

		runTimer( testMinAlert.getId( ), 10 ) ;
		alertProcessor.collectAlertSample( testMinAlert ) ;

		assertThat( testMinAlert.getLatestSample( ).path( "total-ms" ).asLong( ) )
				.as( "template text" )
				.isGreaterThan( 10 ) ;

		logger.info( "testMinAlert: {}", testMinAlert ) ;

		ObjectNode healthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, testMinAlert ) ;

		logger.info( "healthReport: {}", healthReport ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( healthReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.isEqualTo( " Collected: 1, Limit: 10" ) ;

	}

	@Test
	public void verify_alert_max_count_for_gauge ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		AlertInstance testMaxAlert = new AlertInstance( "csap.test.gauge.max", 1, 1 ) ;
		alertDefinitions.setLimits( List.of( testMaxAlert ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		var testList = new ArrayList<String>( ) ;

		testList.add( "1" ) ;
		helpers.addGauge( testMaxAlert.getId( ), testList, List::size ) ;
		alertProcessor.collectAlertSample( testMaxAlert ) ;
		logger.info( "testMaxAlert: {}", testMaxAlert ) ;

		ObjectNode passedHealthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( passedHealthReport, testMaxAlert ) ;
		logger.info( "passedHealthReport: {}", passedHealthReport ) ;
		assertThat( passedHealthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isTrue( ) ;

		testList.add( "1" ) ;
		testList.add( "1" ) ;
		testList.add( "1" ) ;
		//
		alertProcessor.collectAlertSample( testMaxAlert ) ;
		logger.debug( "testMaxAlert: {}", testMaxAlert ) ;
		assertThat( testMaxAlert.getLatestSample( ).path( AlertFields.count.jsonKey( ) ).asLong( ) )
				.isEqualTo( testList.size( ) ) ;

		ObjectNode failedReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( failedReport, testMaxAlert ) ;
		logger.info( "failedReport: {}", failedReport ) ;
		assertThat( failedReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;
		//
		assertThat( failedReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.isEqualTo( " Collected: " + testList.size( ) + ", Limit: 1" ) ;

	}

	@Test
	public void verify_alert_max_count_for_counter ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		AlertInstance testMaxAlert = new AlertInstance( "csap.test.counter.max", 1, 1 ) ;
		alertDefinitions.setLimits( List.of( testMaxAlert ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		helpers.incrementCounter( testMaxAlert.getId( ) ) ;
		alertProcessor.collectAlertSample( testMaxAlert ) ;
		logger.info( "testMaxAlert: {}", testMaxAlert ) ;

		ObjectNode passedHealthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( passedHealthReport, testMaxAlert ) ;
		logger.info( "passedHealthReport: {}", passedHealthReport ) ;
		assertThat( passedHealthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isTrue( ) ;

		helpers.incrementCounter( testMaxAlert.getId( ) ) ;
		helpers.incrementCounter( testMaxAlert.getId( ) ) ;
		helpers.incrementCounter( testMaxAlert.getId( ) ) ;

		alertProcessor.collectAlertSample( testMaxAlert ) ;
		logger.info( "testMaxAlert: {}", testMaxAlert ) ;
		assertThat( testMaxAlert.getLatestSample( ).path( AlertFields.count.jsonKey( ) ).asLong( ) )
				.isEqualTo( 4 ) ;

		ObjectNode failedReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( failedReport, testMaxAlert ) ;
		logger.info( "failedReport: {}", failedReport ) ;
		assertThat( failedReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( failedReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.isEqualTo( " Collected: 3, Limit: 1" ) ;

	}

	@Test
	public void verify_alert_max_count_for_timer ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		AlertInstance testMaxAlert = new AlertInstance( "csap.test.max", 1, 1 ) ;
		alertDefinitions.setLimits( List.of( testMaxAlert ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		runTimer( testMaxAlert.getId( ), 7 ) ;
		alertProcessor.collectAlertSample( testMaxAlert ) ;

		logger.info( "testMinAlert: {}", testMaxAlert ) ;

		assertThat( testMaxAlert.getLatestSample( ).path( "total-ms" ).asLong( ) )
				.as( "template text" )
				.isGreaterThan( 5 ) ;

		runTimer( testMaxAlert.getId( ), 10 ) ;
		runTimer( testMaxAlert.getId( ), 20 ) ;
		alertProcessor.collectAlertSample( testMaxAlert ) ;

		assertThat( testMaxAlert.getLatestSample( ).path( "total-ms" ).asLong( ) )
				.as( "template text" )
				.isGreaterThan( 35 ) ;

		logger.info( "testMaxAlert: {}", testMaxAlert ) ;

		ObjectNode healthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, testMaxAlert ) ;

		logger.info( "healthReport: {}", healthReport ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( healthReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.isEqualTo( " Collected: 2, Limit: 1" ) ;

	}

	@Test
	public void verify_alert_time_mean ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		var testMeanLimit = new AlertInstance( "csap.test.mean", 1, 1, 50, 500 ) ;
		alertDefinitions.setLimits( List.of( testMeanLimit ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		runTimer( testMeanLimit.getId( ), 3 ) ;
		alertProcessor.collectAlertSample( testMeanLimit ) ;

		logger.info( "testMeanLimit: {}", testMeanLimit ) ;

		ObjectNode healthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, testMeanLimit ) ;

		logger.info( "healthReport: {}", healthReport ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isTrue( ) ;

		// run with lower limit
		var testMeanLimitExceeded = new AlertInstance( "csap.test.mean-exceeded", 1, 10, 11, 500 ) ;
		alertDefinitions.setLimits( List.of( testMeanLimitExceeded ) ) ;
		runTimer( testMeanLimitExceeded.getId( ), 10 ) ;
		alertProcessor.collectAlertSample( testMeanLimitExceeded ) ;

		runTimer( testMeanLimitExceeded.getId( ), 20 ) ;
		runTimer( testMeanLimitExceeded.getId( ), 30 ) ;
		alertProcessor.collectAlertSample( testMeanLimitExceeded ) ;

		logger.info( " latest: {} \n\t previous: {}", testMeanLimitExceeded.getLatestSample( ), testMeanLimitExceeded
				.getPreviousSample( ) ) ;
		ObjectNode meanFailedReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( meanFailedReport, testMeanLimitExceeded ) ;

		logger.info( "meanFailedReport: {}", meanFailedReport ) ;

		assertThat( meanFailedReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( meanFailedReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/collected" ).asInt( ) )
				.as( "mean math is correct" )
				.isLessThan( 100 ) ;

		assertThat( meanFailedReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.contains( "Limit: 11" ) ;

	}

	@Test
	public void verify_alert_time_max ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		var timeMaxLimit = new AlertInstance( "csap.test.max.ok", 1, 1, 50, 50 ) ;
		alertDefinitions.setLimits( List.of( timeMaxLimit ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		runTimer( timeMaxLimit.getId( ), 9 ) ;
		alertProcessor.collectAlertSample( timeMaxLimit ) ;

		logger.info( "timeMaxLimit: {}", timeMaxLimit ) ;

		ObjectNode healthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, timeMaxLimit ) ;

		logger.info( "healthReport: {}", healthReport ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isTrue( ) ;

		// run with lower limit
		var testMaxLimitExceeded = new AlertInstance( "csap.test.max.fail", 1, 1, 500, 9 ) ;
		alertDefinitions.setLimits( List.of( testMaxLimitExceeded ) ) ;
		runTimer( testMaxLimitExceeded.getId( ), 21 ) ;
		alertProcessor.collectAlertSample( testMaxLimitExceeded ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, testMaxLimitExceeded ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( healthReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" ).asText( ) )
				.as( "template text" )
				.contains( "Limit: 9" ) ;

	}

	@Test
	public void verify_alert_time_max_in_mid ( )
		throws Exception {

		logger.info( CsapApplication.testHeader( ) ) ;

		var alertDefinitions = alertProcessor.getSettings( ) ;
		var maxInMidLimit = new AlertInstance( "csap.test.max.mid", 1, 1, 50, 50 ) ;
		alertDefinitions.setLimits( List.of( maxInMidLimit ) ) ;

		logger.info( "alertDefinitions: {}", alertDefinitions ) ;

		runTimer( maxInMidLimit.getId( ), 9 ) ;
		alertProcessor.collectAlertSample( maxInMidLimit ) ;

		logger.info( "timeMaxLimit: {}", maxInMidLimit ) ;

		ObjectNode healthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( healthReport, maxInMidLimit ) ;

		logger.info( "healthReport: {}", healthReport ) ;

		assertThat( healthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isTrue( ) ;

		//
		// Do NOT wait for intervals if latest taken already exceeds
		//

		runTimer( maxInMidLimit.getId( ), 1 ) ;
		runTimer( maxInMidLimit.getId( ), 1 ) ;
		runTimer( maxInMidLimit.getId( ), 1 ) ;
		ObjectNode maxInMidHealthReport = alertProcessor.createEmptyHealthReport( ) ;
		alertProcessor.checkAlertInstanceForThresholds( maxInMidHealthReport, maxInMidLimit ) ;

		logger.info( "healthReport: {}", maxInMidHealthReport ) ;

		assertThat( maxInMidHealthReport.path( AlertFields.healthy.jsonKey( ) ).asBoolean( ) )
				.as( "is healthy" )
				.isFalse( ) ;

		assertThat( maxInMidHealthReport.path( AlertFields.limitsExceeded.jsonKey( ) ).at( "/0/description" )
				.asText( ) )
						.as( "template text" )
						.isEqualTo( " Collected: 3, Limit: 1" ) ;

	}

	private void runTimer ( String timerId , int duration )
		throws InterruptedException {

		var timerSample = helpers.startTimer( ) ;
		TimeUnit.MILLISECONDS.sleep( duration ) ;
		helpers.stopTimer( timerSample, timerId ) ;

	}

	@Test
	public void verify_mail_send ( ) {

		logger.info( CsapApplication.testHeader( ) ) ;

		if ( StringUtils.isEmpty( getToAddress( ) ) ) {

			logger.info( "Skipping test - add application.company to home dir" ) ;
			return ;

		}

		String text = alertProcessor.buildEmailText( ) ;

		logger.debug( "Text: {}", text ) ;
		assertThat( text )
				.as( "Spring Bean count" )
				.contains( "<title>CSAP Notification</title>", "<td class=\"val\">default_" ) ;

		JavaMailSenderImpl sender = new JavaMailSenderImpl( ) ;
		Properties properties = new Properties( ) ;
		// properties.put("mail.smtp.auth", auth);
		properties.put( "mail.smtp.timeout", 1000 ) ;
		properties.put( "mail.smtp.connectiontimeout", 1000 ) ;
		// properties.put("mail.smtp.starttls.enable", starttls);

		sender.setJavaMailProperties( properties ) ;
		sender.setHost( getHost( ) ) ;
		sender.setPort( getPort( ) ) ;

		// SimpleMailMessage simpleMail = new SimpleMailMessage();
		// simpleMail.setFrom("csap@yourcompanyinc.com");
		// simpleMail.setTo("peter.nightingale@yourcompanyinc.com");
		// simpleMail.setSubject("Simple");
		// simpleMail.setText(text);

		// sender.send( simpleMail );

		sender.send( mimeMessage -> {

			MimeMessageHelper messageHelper = new MimeMessageHelper( mimeMessage, true, "UTF-8" ) ;
			messageHelper.setTo( getToAddress( ) ) ;
			// messageHelper.setCc( CsapUser.currentUsersEmailAddress()
			// );
			messageHelper.setFrom( getFromAddress( ) ) ;
			messageHelper.setSubject( "CSAP Junit Notification" ) ;
			messageHelper.setText( text, true ) ;

			// messageHelper.addAttachment( "report.json",
			// new ByteArrayResource( jacksonMapper
			// .writerWithDefaultPrettyPrinter()
			// .writeValueAsString( healthReport )
			// .getBytes() ) );
		} ) ;

	}

	public String getToAddress ( ) {

		return toAddress ;

	}

	public void setToAddress ( String emailAddress ) {

		this.toAddress = emailAddress ;

	}

	public String getHost ( ) {

		return host ;

	}

	public void setHost ( String host ) {

		this.host = host ;

	}

	public int getPort ( ) {

		return port ;

	}

	public void setPort ( int port ) {

		this.port = port ;

	}

	public String getFromAddress ( ) {

		return fromAddress ;

	}

	public void setFromAddress ( String fromAddress ) {

		this.fromAddress = fromAddress ;

	}

}
