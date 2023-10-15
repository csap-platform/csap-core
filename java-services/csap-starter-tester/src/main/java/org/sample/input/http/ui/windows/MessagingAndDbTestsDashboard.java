package org.sample.input.http.ui.windows ;

import java.net.MalformedURLException ;
import java.net.URL ;
import java.time.LocalDateTime ;
import java.time.format.DateTimeFormatter ;
import java.util.ArrayList ;
import java.util.Date ;
import java.util.HashMap ;
import java.util.Hashtable ;
import java.util.Map ;

import javax.inject.Inject ;
import javax.jms.JMSException ;
import javax.jms.Message ;
import javax.jms.ObjectMessage ;
import javax.jms.TextMessage ;
import javax.management.MBeanServerConnection ;
import javax.management.ObjectName ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpSession ;
import javax.ws.rs.core.MediaType ;

import org.csap.docs.CsapDoc ;
import org.csap.helpers.CSAP ;
import org.sample.Csap_Tester_Application ;
import org.sample.input.http.ui.rest.TestObjectForMessaging ;
import org.sample.jpa.DemoEvent ;
import org.sample.jpa.DemoManager ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.context.MessageSource ;
import org.springframework.jms.JmsException ;
import org.springframework.jms.core.JmsTemplate ;
import org.springframework.jms.core.MessagePostProcessor ;
import org.springframework.jmx.support.MBeanServerConnectionFactoryBean ;
import org.springframework.stereotype.Controller ;
import org.springframework.ui.Model ;
import org.springframework.web.bind.annotation.GetMapping ;
import org.springframework.web.bind.annotation.PostMapping ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.client.RestTemplate ;
import org.springframework.web.servlet.ModelAndView ;

import com.fasterxml.jackson.databind.ObjectMapper ;

import io.micrometer.core.annotation.Timed ;

@Controller
@RequestMapping ( Csap_Tester_Application.SPRINGAPP_URL )
@CsapDoc ( title = "Messaging and Database Dashboard" , type = CsapDoc.PUBLIC , notes = {
		"Insert and remove messages, including the ability to perform and time bulk operations",
		"<a class='csap-link' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
} )
public class MessagingAndDbTestsDashboard {

	Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@Inject
	public MessagingAndDbTestsDashboard (
			DemoManager dao,
			MessageSource message,
			RestTemplate aTrivialRestSampleId ) {

		this.demoDataService = dao ;
		this.messages = message ;
		this.simpleRestTemplate = aTrivialRestSampleId ;

	}

	private DemoManager demoDataService ;
	private MessageSource messages ;
	private RestTemplate simpleRestTemplate = null ;

	@Autowired ( required = false )
	private JmsTemplate jmsTemplate ;

	final static String DEFAULT_VIEW = "db-and-jms" ;

	@Timed ( value = "csap.ui.page.jms-db-test" , description = "JMS and DB Test page" )
	@GetMapping ( "/spring" )
	public String showSpringApp ( Model springViewModel ) {

		setCommonAttributes( springViewModel.asMap( ) ) ;

		return DEFAULT_VIEW ;

	}

	private void setCommonAttributes ( Map<String, Object> map ) {

		map.put( "DataToInsertIntoDb",
				LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) ) ;

		map.put( "testToken", DemoManager.TEST_TOKEN ) ;

	}

	/**
	 * Wrapper to send JMS
	 * 
	 * @return
	 */
	@RequestMapping ( "/sendMessage" )
	public String sendMessage (
								@RequestParam String message ,
								HttpSession session ,
								Model springViewModel ) {

		logger.debug( "Sending Message +++++++++++" ) ;

		String result = "JMS is disabled. Updated configuration files to enable." ;
		session.setAttribute( "lastMessage", message ) ;

		if ( jmsTemplate != null ) {

			try {

				jmsTemplate.convertAndSend( message ) ;
				logger.debug( "Getting item List +++++++++++" ) ;
				result = "Message Sent: (max Results is 20)" + new Date( ) + "\n"
						+ demoDataService.findUsingJpql( DemoManager.TEST_TOKEN, 20 ) ;

			} catch ( JmsException e ) {

				logger.error( "Failed to send message", e ) ;
				result = "Failed to send Message: " + e.getMessage( ) ;

			}

		}

		springViewModel.addAttribute( "result", result ) ;

		setCommonAttributes( springViewModel.asMap( ) ) ;

		return DEFAULT_VIEW ;

	}

	@RequestMapping ( "/sendManyMessages" )
	public ModelAndView sendManyMessages (
											@RequestParam String message ,
											@RequestParam ( value = "count" , defaultValue = "1" , required = false ) int numMessages ,
											@RequestParam ( value = "payloadPadding" , defaultValue = "none" , required = false ) String payloadPadding ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		logger.debug( "Sending Message +++++++++++" ) ;

		String result = "JMS is disabled. Updated configuration files to enable." ;

		if ( jmsTemplate == null ) {

		} else {

			StringBuilder sb = new StringBuilder( message ) ;

			if ( ! payloadPadding.equals( "none" ) ) {

				int padLength = 1000 ;
				if ( payloadPadding.equals( "10kb" ) )
					padLength = 10000 ;
				if ( payloadPadding.equals( "100kb" ) )
					padLength = 100000 ;
				if ( payloadPadding.equals( "1mb" ) )
					padLength = 1000000 ;
				if ( payloadPadding.equals( "3mb" ) )
					padLength = 3000000 ;
				if ( payloadPadding.equals( "5mb" ) )
					padLength = 5000000 ;
				if ( payloadPadding.equals( "10mb" ) )
					padLength = 10000000 ;

				for ( int i = message.length( ); i < padLength; i++ ) {

					sb.append( "_" ) ;

				}

			}

			for ( int i = 0; i < numMessages; i++ ) {

				jmsTemplate.convertAndSend( i + " of " + numMessages
						+ ", Payload: " + sb.toString( ) ) ;

			}
			// logger.info("Getting item List +++++++++++");
			// String result = "Message Sent: " + new Date() + "\n"
			// + testDao.showScheduleItems(SAMPLE_QUERY);

			result = "Sent: " + numMessages + " messages, use show test data or view Logs to validate" ;

		}

		modelMapAndView.getModelMap( ).addAttribute( "result", result ) ;
		modelMapAndView.setViewName( DEFAULT_VIEW ) ;

		setCommonAttributes( modelMapAndView.getModel( ) ) ;

		return modelMapAndView ;

	}

	@RequestMapping ( "/sendObjectMessage" )
	public ModelAndView sendObjectMessage ( ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		logger.info( "Got here" ) ;
		TestObjectForMessaging testObject = new TestObjectForMessaging( ) ;
		testObject.setName( "My Name" ) ;
		testObject.setAge( new Integer( 99 ) ) ;
		testObject.setNotTransfered( "Should not be passed" ) ;
		// Sample that includes some JMS properties as well
		// convert and send will check if this is a serializable - if so it will
		// use a JMS object message
		String result = "Sending java object graphs over JMS induces tight coupling between endpoints (should be"
				+ " avoided if possible).\n" ;
		logger.error( result ) ;
		jmsTemplate.convertAndSend( (Object) testObject,
				new MessagePostProcessor( ) {

					@Override
					public Message postProcessMessage ( Message message )
						throws JMSException {

						if ( message instanceof ObjectMessage )
							logger.error( "Object Message created" ) ;
						if ( message instanceof TextMessage )
							logger.error( "Text Message created" ) ;
						message.setStringProperty( "TestJmsProperty",
								"TestJmsPropertyValue" ) ;
						return message ;

					}

				} ) ;

		modelMapAndView.getModelMap( ).addAttribute( "result",
				result + "Message Sent: " + new Date( ) ) ;

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		setCommonAttributes( modelMapAndView.getModel( ) ) ;
		return modelMapAndView ;

	}

	@RequestMapping ( value = "/showTestData" , produces = MediaType.TEXT_HTML )
	public ModelAndView showTestDataHtml (
											@RequestParam ( defaultValue = "no" ) String memoryLeak ,
											@RequestParam ( defaultValue = DemoManager.TEST_TOKEN ) String filter ,
											HttpServletRequest request ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		logger.debug( "Showing test data later" ) ;
		long start = System.currentTimeMillis( ) ;
		// logger.info("Got here: "
		// + messages.getMessage("billboard_label", null, null));

		StringBuilder result = new StringBuilder(
				"\nNumber of test data in DB: "
						+ Long.toString( demoDataService.getCountJpql( filter ) ) ) ;

		// for (int i = 0; i<10;i++) {
		result.append( "\n Result from JPA (maxResults is 20):\n:"
				+ demoDataService.findUsingJpql( filter, 20 ) ) ;
		// }

		long time = ( System.currentTimeMillis( ) - start ) ;
		logger.debug( "\n\n\t *** JPA Query time: " + time + "ms" ) ;

		modelMapAndView.getModelMap( ).addAttribute( "result", result.toString( ) ) ;

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		if ( memoryLeak.contains( "yes" ) ) {

			sampleMemoryLeakMethodPushingStringsOnAMap( ) ;

		}

		setCommonAttributes( modelMapAndView.getModel( ) ) ;

		return modelMapAndView ;

	}

	@PostMapping ( "/removeTestData" )
	public ModelAndView removeTestData (
											@RequestParam ( value = "filter" , defaultValue = DemoManager.TEST_TOKEN , required = false ) String filter ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		logger.info( "Got here - deleting test data" ) ;

		// String results = testDao
		// .removeTestData("select j from JobSchedule j where j.eventDescription
		// like '%My test%'");

		String results = demoDataService.removeBulkDataJpql( filter ) ;

		modelMapAndView.getModelMap( ).addAttribute( "result", results ) ;

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		setCommonAttributes( modelMapAndView.getModel( ) ) ;
		return modelMapAndView ;

	}

	@RequestMapping ( "/testSessionMessage" )
	public String testSessionData ( Model model , HttpSession session ) {

		String testMessage = "" ;

		if ( session != null ) {

			if ( session.getAttribute( "testMessage" ) != null ) {

				testMessage = (String) session.getAttribute( "testMessage" ) ;

			}

		}

		String results = testMessage + "AbraCaDabra ..." ;
		model.addAttribute( "result", results ) ;
		session.setAttribute( "testMessage", results ) ;
		return DEFAULT_VIEW ;

	}

	private Hashtable<String, ArrayList<String>> testMemoryLeak = new Hashtable<String, ArrayList<String>>( ) ;

	/**
	 * This is for demo purposes - showing JMeter and Memory leak tools
	 */
	private void sampleMemoryLeakMethodPushingStringsOnAMap ( ) {

		logger.warn( "Running in Memory leak mode" ) ;

		// Simple loop to demo memory leaks
		for ( int j = 0; j < 50; j++ ) {

			ArrayList<String> d = new ArrayList<String>( ) ;

			for ( int i = 0; i < 500; i++ ) {

				d.add( "Some dummy String" + i ) ;

			}

			testMemoryLeak.put( System.currentTimeMillis( ) + "_" + j, d ) ;

		}

	}

	// @Autowired // Leaving commented out from spring for now as it is a remote
	// example requiring conn is up.
	// @Qualifier("jmxClient")
	MBeanServerConnection mbeanConn ;

	/**
	 * 
	 * 
	 */
	@RequestMapping ( "/showJmxData" )
	public ModelAndView showJmxData ( ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;
		String serviceUrl = "service:jmx:rmi://csapdb-dev01.yourcompany.com/jndi/rmi://csapdb-dev01.yourcompany.com:8326/jmxrmi" ;

		// Confirm using JConsole, defined in DemoJmxService
		String mbeanName = "org.csap:application=sample,name=DemoJmxService" ;
		String attribute = "CurrentMillis" ;
		String operationName = "toUpperCase" ;

		String result = "JMX Results: \n\t serviceUrl: " + serviceUrl ;
		logger.info( "targeted jmx" + serviceUrl ) ;

		// This should be wired ahead of time in spring config, lets do it
		// dynamically for now
		try {

			// Ideally - you create a pool of connections at startup to your
			// resource.
			// In this case - persistent connections is not required , so it is
			// done dynamically
			MBeanServerConnectionFactoryBean jmxFactory = new MBeanServerConnectionFactoryBean( ) ;
			jmxFactory.setServiceUrl( serviceUrl ) ;
			jmxFactory.afterPropertiesSet( ) ;
			mbeanConn = jmxFactory.getObject( ) ;
			result += mbeanConn.getMBeanCount( ) ;
			result += "\n\njmx attribute get \"CurrentMillis\": \t" ;
			result += mbeanConn.getAttribute( new ObjectName( mbeanName ),
					attribute ) ;

			Map<String, Object> userData = new HashMap<String, Object>( ) ;
			Map<String, String> nameStruct = new HashMap<String, String>( ) ;
			nameStruct.put( "first", "TestUser" ) ;
			nameStruct.put( "last", "TestLastName" ) ;
			userData.put( "name", nameStruct ) ;
			userData.put( "verified", Boolean.FALSE ) ;
			userData.put( "userImage", "Rm9vYmFyIQ==" ) ;
			ObjectMapper mapper = new ObjectMapper( ) ;
			// Push a unmarshalled JSONObject onto the param list
			Object[] params = {
					new String( mapper.writeValueAsString( userData ) )
			} ;
			String[] signature = {
					new String( "java.lang.String" )
			} ;
			Object jmxOperationResult = mbeanConn.invoke( new ObjectName( mbeanName ),
					operationName, params, signature ) ;
			result += "\n\njmx operation invoke \"toUpperCase\":\n" + jmxOperationResult.toString( ) ;

		} catch ( Exception e ) {

			logger.error( "Failed to get count", e ) ;
			result += "Confirm the following are connectable:"
					+ "\n serviceUrl: " + serviceUrl + "\n mbeanName: "
					+ mbeanName + "\n attribute" + attribute + "\n Exception: "
					+ e.getMessage( ) ;

		}

		modelMapAndView.getModelMap( ).addAttribute( "result", result ) ;

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		setCommonAttributes( modelMapAndView.getModel( ) ) ;
		return modelMapAndView ;

	}

	@RequestMapping ( "/showTestDataFromREST" )
	public ModelAndView showTestDataFromREST ( HttpServletRequest request ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		String restTargetUrl = request.getRequestURL( ).toString( ) ;

		try {

			URL baseUrl = new URL( request.getRequestURL( ).toString( ) ) ;
			URL url = new URL( baseUrl, "../jersey/simpleSpringRest" ) ;
			restTargetUrl = url.toString( ) ;

		} catch ( MalformedURLException e ) {

			logger.error( "Failed to build url" ) ;

		}

		logger.info( "For demo purpose only - we will use the input url: "
				+ restTargetUrl ) ;

		String result = "Result from REST,JPA:\n:"
				+ simpleRestTemplate.getForObject( restTargetUrl, String.class ) ;

		modelMapAndView.getModelMap( ).addAttribute( "result", result ) ;

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		setCommonAttributes( modelMapAndView.getModel( ) ) ;
		return modelMapAndView ;

	}

	@PostMapping ( "/addTestData" )
	public ModelAndView addTestData (
										@RequestParam String message ) {

		ModelAndView modelMapAndView = new ModelAndView( ) ;

		logger.debug( "Got here" ) ;
		DemoEvent jobScheduleInput = new DemoEvent( ) ;
		jobScheduleInput.setDemoField( "test Jndi name" ) ;
		// jobScheduleInput.setScheduleObjid(System.currentTimeMillis()); //
		// Never provide this as it is generated
		jobScheduleInput.setCategory( DemoManager.TEST_TOKEN ) ;
		jobScheduleInput.setDescription( "Spring Consumer ======> "
				+ DemoManager.TEST_TOKEN + " String: " + message ) ;

		try {

			jobScheduleInput = demoDataService.addSchedule( jobScheduleInput ) ;
			logger.info( "Added with ID " + jobScheduleInput ) ;
			modelMapAndView.getModelMap( ).addAttribute( "result", jobScheduleInput ) ;

		} catch ( Exception e ) {

			String reason = CSAP.buildFilteredStack( e, "sample" ) ;
			modelMapAndView.getModelMap( ).addAttribute( "result", "Failed to insert due to: " + reason ) ;
			logger.error( "Failed to insert: {}, {}" + jobScheduleInput.toString( ), reason ) ;

		}

		modelMapAndView.setViewName( DEFAULT_VIEW ) ; // overload jsp to handle
														// multiple results

		setCommonAttributes( modelMapAndView.getModel( ) ) ;

		return modelMapAndView ;

	}

}
