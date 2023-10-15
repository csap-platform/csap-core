package org.csap.security ;

import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.List ;

import javax.naming.directory.Attributes ;

import org.springframework.ldap.core.DirContextAdapter ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.ldap.LdapUtils ;
import org.springframework.security.ldap.userdetails.LdapUserDetails ;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl ;

import com.fasterxml.jackson.databind.JsonNode ;
import com.fasterxml.jackson.databind.ObjectMapper ;

/**
 * List of attributes is availabe using your ldap browser
 * 
 * @author pnightin
 *
 */
public class ActiveDirectoryUserDetails extends LdapUserDetailsImpl {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7009791352292197412L ;
	private String department ;
	private String mail ;
	private String description ;
	private String telephoneNumber ;
	private List<String> cn = new ArrayList<String>( ) ;
	private Attributes allBindAttributes = null ;

	protected ActiveDirectoryUserDetails ( ) {

	}

	public String getDepartment ( ) {

		return department ;

	}

	public String getMail ( ) {

		return mail ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	// @JsonIgnore
	public JsonNode getAllBindAttributes ( ) {

		return CustomUserDetails.getDetails( allBindAttributes, jacksonMapper ) ;

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

		adapter.setAttributeValue( "usersHost", getDepartment( ) ) ;
		adapter.setAttributeValue( "mail", getMail( ) ) ;
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
			setDepartment( ctx.getStringAttribute( "department" ) ) ;
			setMail( ctx.getStringAttribute( "mail" ) ) ;
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
			setAllAttributes( ctx.getAttributes( ) ) ;

		}

		public Essence ( ActiveDirectoryUserDetails copyMe ) {

			super( copyMe ) ;
			setMail( copyMe.mail ) ;
			setDepartment( copyMe.department ) ;
			setDescription( copyMe.getDescription( ) ) ;
			setTelephoneNumber( copyMe.getTelephoneNumber( ) ) ;
			// setAllAttributes(copyMe.getAllAttributesInConfigFile());
			( (ActiveDirectoryUserDetails) instance ).cn = new ArrayList<String>( copyMe.cn ) ;

		}

		protected LdapUserDetailsImpl createTarget ( ) {

			return new ActiveDirectoryUserDetails( ) ;

		}

		public void setMail ( String mail ) {

			( (ActiveDirectoryUserDetails) instance ).mail = mail ;

		}

		public void setDepartment ( String department ) {

			( (ActiveDirectoryUserDetails) instance ).department = department ;

		}

		public void setAllAttributes ( Attributes attr ) {

			( (ActiveDirectoryUserDetails) instance ).allBindAttributes = attr ;

		}

		public void setCn ( String[] cn ) {

			( (ActiveDirectoryUserDetails) instance ).cn = Arrays.asList( cn ) ;

		}

		public void addCn ( String value ) {

			( (ActiveDirectoryUserDetails) instance ).cn.add( value ) ;

		}

		public void setTelephoneNumber ( String tel ) {

			( (ActiveDirectoryUserDetails) instance ).telephoneNumber = tel ;

		}

		public void setDescription ( String desc ) {

			( (ActiveDirectoryUserDetails) instance ).description = desc ;

		}

		public LdapUserDetails createUserDetails ( ) {

			ActiveDirectoryUserDetails p = (ActiveDirectoryUserDetails) super.createUserDetails( ) ;
			// Assert.hasLength(p.sn);
//            Assert.notNull(p.cn);
//            Assert.notEmpty(p.cn);
			// TODO: Check contents for null entries
			return p ;

		}
	}

}
