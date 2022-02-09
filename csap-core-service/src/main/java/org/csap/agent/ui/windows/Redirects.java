package org.csap.agent.ui.windows ;

import javax.servlet.http.HttpServletRequest ;

import org.apache.commons.logging.Log ;
import org.apache.commons.logging.LogFactory ;
import org.csap.agent.CsapConstants ;
import org.csap.agent.model.Application ;
import org.springframework.beans.factory.annotation.Autowired ;
import org.springframework.web.bind.annotation.RequestMapping ;
import org.springframework.web.bind.annotation.RequestParam ;
import org.springframework.web.bind.annotation.RestController ;
import org.springframework.web.servlet.ModelAndView ;
import org.springframework.web.servlet.view.RedirectView ;

@RestController
@RequestMapping ( CsapConstants.BASE_URL )
public class Redirects {

	protected final Log logger = LogFactory.getLog( getClass( ) ) ;

	@Autowired
	Application csapApp ;

	@RequestMapping ( "/screencast" )
	public ModelAndView screencastLogin (
											@RequestParam ( value = "item" , required = false ) String item ,
											@RequestParam ( value = "wiki" , required = false ) String wiki ) {

		RedirectView redirectView = new RedirectView( ) ;
		redirectView.setContextRelative( false ) ;
		redirectView.setPropagateQueryParams( true ) ;
		redirectView
				.setUrl( csapApp.rootProjectEnvSettings( ).getCsapAnalyticsServerRootUrl( ) + "/"
						+ CsapConstants.ADMIN_NAME
						+ "/viewScreencast" ) ;
		ModelAndView mav = new ModelAndView( redirectView ) ;

		return mav ;

	}

	@RequestMapping ( "/CsAgent/{somePaths:.+}" )
	public ModelAndView csAgent_legacy_redirect ( HttpServletRequest request ) {

		RedirectView redirectView = new RedirectView( ) ;
		redirectView.setContextRelative( false ) ;
		redirectView.setPropagateQueryParams( true ) ;

		var legacyUrl = request.getRequestURL( ).toString( ) ;
		redirectView.setUrl( legacyUrl.replaceFirst( "/CsAgent", "" ) ) ;
		ModelAndView mav = new ModelAndView( redirectView ) ;

		return mav ;

	}

	/**
	 * Suppor for logins
	 * 
	 * @param item
	 * @param wiki
	 * @return
	 */
	@RequestMapping ( "/ssoLogin" )
	public ModelAndView ssoLogin (
									@RequestParam ( value = "ref" , required = false ) String refUrl ) {

		RedirectView redirectView = new RedirectView( ) ;
		redirectView.setContextRelative( false ) ;
		redirectView.setPropagateQueryParams( true ) ;
		redirectView.setUrl( refUrl ) ;
		ModelAndView mav = new ModelAndView( redirectView ) ;

		return mav ;

	}

	@RequestMapping ( "/defaultLocation" )
	public ModelAndView defaultLocation ( ) {

		// redirect untile IWE is updated with new location
		return new ModelAndView( "redirect:/" ) ;

	}

	@RequestMapping ( "/edit/application" )
	public ModelAndView editLegacy ( ) {

		return new ModelAndView( "redirect:/app-browser#projects-tab,editor" ) ;

	}

	@RequestMapping ( CsapConstants.MAINHOSTS_URL )
	public ModelAndView hostsLegacy ( ) {

		return new ModelAndView( "redirect:/app-browser#hosts-tab,cpu" ) ;

	}

	@RequestMapping ( "/os/HostDashboard" )
	public ModelAndView dashLegacy ( ) {

		return new ModelAndView( "redirect:/app-browser" ) ;

	}

	@RequestMapping ( "/os/commands" )
	public ModelAndView commandsLegacy ( ) {

		return new ModelAndView( "redirect:/app-browser" ) ;

	}
}
