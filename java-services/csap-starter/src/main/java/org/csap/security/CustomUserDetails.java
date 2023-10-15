package org.csap.security ;

import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;

import javax.naming.InvalidNameException ;
import javax.naming.NamingEnumeration ;
import javax.naming.directory.Attribute ;
import javax.naming.directory.Attributes ;
import javax.naming.ldap.LdapName ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.ldap.core.DirContextAdapter ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.ldap.LdapUtils ;
import org.springframework.security.ldap.userdetails.LdapUserDetails ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl ;
import org.springframework.util.Assert ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ObjectNode ;

/**
 * List of attributes is availabe using your ldap browser
 * 
 * @author pnightin
 *
 */
public class CustomUserDetails extends LdapUserDetailsImpl {
	static final Logger logger = LoggerFactory.getLogger( CustomUserDetails.class ) ;
	/**
	 * 
	 */
	private static final long serialVersionUID = 7009791352292197412L ;
	private String usersHost ;
	private String title ;
	private String mail ;
	private String description ;
	private String telephoneNumber ;
	private List<String> cn = new ArrayList<String>( ) ;
	private Attributes allBindAttributes = null ;

	public String getPassword ( ) {

		logger.debug( "password is not stored, instead token is used to search and retrieve" ) ;
		return "NotUsed" ;

	}

	protected CustomUserDetails ( ) {

	}

	public String getUsersHost ( ) {

		return usersHost ;

	}

	public String getMail ( ) {

		return mail ;

	}

	public String getTitle ( ) {

		return title ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	// public Attributes getAllAttributesInConfigFile() {
	// return allAttributesInConfigFile;
	// }
	public JsonNode getAllBindAttributes ( ) {

		return CustomUserDetails.getDetails( allBindAttributes, jacksonMapper ) ;

	}

	static int numAttributeWarnings = 0 ;

	static public JsonNode getDetails ( Attributes allFoundAttributes , ObjectMapper jacksonMapper ) {

		ObjectNode attibutesJson = jacksonMapper.createObjectNode( ) ;

		for ( String attributeId : Collections.list( allFoundAttributes.getIDs( ) ) ) {

			Attribute attribute = allFoundAttributes.get( attributeId ) ;
			// builder.append("\n" + StringUtils.leftPad(id, 20) + "\t size: " +
			// attribute.size());

			// ArrayNode itemJSON = attibutesJson.putArray( id ) ;
			String attributeValue = "" ;

			try {

				NamingEnumeration<?> attributeValues = attribute.getAll( ) ;

				while ( attributeValues.hasMore( ) ) {

					var item = attributeValues.next( ) ;

					if ( item instanceof String ) {

						String value = (String) item ;

						// logger.info( "type: {}", value.getClass().getName() ) ;
						// Attribute childAttribute = attributes.get( attributeId ) ;
						if ( attributeValue.length( ) != 0 ) {

							attributeValue += "," ;

						}

						attributeValue += handleOptionalCn( value ) ;

					} else {

						if ( numAttributeWarnings++ < 5 ) {

							logger.warn( "Failed attribute processing: {}, {}", attributeId, item.getClass( ) ) ;

						}

					}

				}

			} catch ( Exception e ) {

				logger.error( "Failed to get item", CSAP.buildCsapStack( e ) ) ;

			}

			attibutesJson.put( attributeId, attributeValue ) ;

		}

		if ( logger.isDebugEnabled( ) ) {

			logger.debug( "User Attributes{}, {} ", CSAP.jsonPrint( attibutesJson ), CSAP.buildCsapStack( new Exception(
					"location" ) ) ) ;

		}

		return attibutesJson ;

	}

	public static String handleOptionalCn ( String input ) {

		var result = input ;

		try {

			if ( input.toLowerCase( ).startsWith( "cn=" ) ) {

				LdapName dnEmtry = new LdapName( input ) ;
				result = org.springframework.ldap.support.LdapUtils.getStringValue( dnEmtry, "cn" ) ;

			}

		} catch ( InvalidNameException e ) {

			logger.warn( "Unable to convert to ldap cn: {}", input, CSAP.buildCsapStack( e ) ) ;

		}

		return result ;

	}

	public String[] getCn ( ) {

		return cn.toArray( new String[cn.size( )] ) ;

	}

	public String getDescription ( ) {

		return description ;

	}

	public String getTelephoneNumber ( ) {

		return telephoneNumber ;

	}

	protected void populateContext ( DirContextAdapter adapter ) {

		// logger.info( "populating " );
		adapter.setAttributeValue( "usersHost", getUsersHost( ) ) ;
		adapter.setAttributeValue( "mail", getMail( ) ) ;
		adapter.setAttributeValue( "title", getTitle( ) ) ;
		// adapter.setAttributeValue("all", getAllAttributesInConfigFile());
		adapter.setAttributeValues( "cn", getCn( ) ) ;
		adapter.setAttributeValue( "description", getDescription( ) ) ;
		adapter.setAttributeValue( "telephoneNumber", getTelephoneNumber( ) ) ;

		if ( getPassword( ) != null ) {

			adapter.setAttributeValue( "userPassword", getPassword( ) ) ;

		}

		adapter.setAttributeValues( "objectclass", new String[] {
				"top", "person"
		} ) ;
		this.populateContext( adapter ) ;

	}

	public static class Essence extends LdapUserDetailsImpl.Essence {

		public Essence ( ) {

		}

		public Essence ( DirContextOperations ctx ) {

			super( ctx ) ;
			setCn( ctx.getStringAttributes( "cn" ) ) ;
			setUsersHost( ctx.getStringAttribute( "usersHosts" ) ) ;
			setMail( ctx.getStringAttribute( "mail" ) ) ;
			setTitle( ctx.getStringAttribute( "title" ) ) ;
			setDescription( ctx.getStringAttribute( "description" ) ) ;
			setTelephoneNumber( ctx.getStringAttribute( "telephoneNumber" ) ) ;
			Object passo = ctx.getObjectAttribute( "userPassword" ) ;

			if ( passo != null ) {

				String password = LdapUtils.convertPasswordToString( passo ) ;
				setPassword( password ) ;

			}

			// Here is some magic. Password is not in attributes by policy. Instead we use
			// an equally hard
			// to guess attribute.
			setPassword( ctx.getStringAttribute( "pwdLastSet" ) ) ;

			logger.debug( "attributes: {}", ctx.getAttributes( ) ) ;
			setAllAttributes( ctx.getAttributes( ) ) ;

		}

		public Essence ( CustomUserDetails copyMe ) {

			super( copyMe ) ;
			setMail( copyMe.mail ) ;
			setTitle( copyMe.title ) ;
			setUsersHost( copyMe.usersHost ) ;
			setDescription( copyMe.getDescription( ) ) ;
			setTelephoneNumber( copyMe.getTelephoneNumber( ) ) ;
			// setAllAttributes(copyMe.getAllAttributesInConfigFile());
			( (CustomUserDetails) instance ).cn = new ArrayList<String>( copyMe.cn ) ;

		}

		protected LdapUserDetailsImpl createTarget ( ) {

			return new CustomUserDetails( ) ;

		}

		public void setMail ( String mail ) {

			( (CustomUserDetails) instance ).mail = mail ;

		}

		public void setTitle ( String title ) {

			( (CustomUserDetails) instance ).title = title ;

		}

		public void setUsersHost ( String usersHost ) {

			( (CustomUserDetails) instance ).usersHost = usersHost ;

		}

		public void setAllAttributes ( Attributes allAttributes ) {

			( (CustomUserDetails) instance ).allBindAttributes = allAttributes ;

		}

		public void setCn ( String[] cn ) {

			( (CustomUserDetails) instance ).cn = Arrays.asList( cn ) ;

		}

		public void addCn ( String value ) {

			( (CustomUserDetails) instance ).cn.add( value ) ;

		}

		public void setTelephoneNumber ( String tel ) {

			( (CustomUserDetails) instance ).telephoneNumber = tel ;

		}

		public void setDescription ( String desc ) {

			( (CustomUserDetails) instance ).description = desc ;

		}

		public LdapUserDetails createUserDetails ( ) {

			CustomUserDetails p = (CustomUserDetails) super.createUserDetails( ) ;
			// Assert.hasLength(p.sn);
			Assert.notNull( p.cn ) ;
			Assert.notEmpty( p.cn ) ;
			// TODO: Check contents for null entries
			return p ;

		}
	}

}
