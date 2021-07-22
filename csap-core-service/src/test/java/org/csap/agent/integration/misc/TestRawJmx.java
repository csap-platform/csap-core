package org.csap.agent.integration.misc ;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.logging.Level ;
import java.util.logging.Logger ;

import javax.management.ObjectName ;
import javax.management.openmbean.CompositeData ;
import javax.management.remote.JMXConnector ;
import javax.management.remote.JMXConnectorFactory ;
import javax.management.remote.JMXServiceURL ;

/**
 *
 * @author someDeveloper
 */
public class TestRawJmx {
	static Logger logger = Logger.getLogger( TestRawJmx.class.getName( ) ) ;

	public static void main ( String[] args ) {

		try {

			String host = "localhost" ;
			host = "csseredapp-dev-03" ;
			String port = "8356" ; // port = "8356";
			String mbeanName = "java.lang:type=Memory" ;
			String attributeName = "HeapMemoryUsage" ;

			JMXServiceURL jmxUrl = new JMXServiceURL(
					"service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi" ) ;
			// jmxUrl = new JMXServiceURL(
			// "service:jmx:rmi://localhost/jndi/rmi://" + host + ":" + port +
			// "/jmxrmi" );

			logger.info( "Target: " + jmxUrl ) ;

			JMXConnector jmxConnection = JMXConnectorFactory.connect( jmxUrl ) ;

			logger.info( "Got connections" ) ;

			CompositeData resultData = (CompositeData) jmxConnection.getMBeanServerConnection( )
					.getAttribute( new ObjectName( mbeanName ), attributeName ) ;

			logger.log( Level.INFO, "Got mbean: heapUsed: {0}", resultData.get( "used" ) ) ;

			Thread.sleep( 5000 ) ;

		} catch ( Exception ex ) {

			logger.log( Level.SEVERE, "Failed connection", ex ) ;

		}

	}

}
