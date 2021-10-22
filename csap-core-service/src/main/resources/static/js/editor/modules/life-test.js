require.config( {
    paths: {
        jsYaml: baseUrl + "webjars/js-yaml/3.14.0/dist",
        ace: baseUrl + "webjars/ace-builds/1.4.11/src",
        browser: "../../app-browser/modules",
        projects: "../../app-browser/modules/projects",
        editor: "../../editor/modules"
    },
} ) ;


require( ["browser/projects/env-editor/life-editor", "editor/json-forms", "browser/utils"], function ( lifeEdit, jsonForms, utils ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		
		initialize();
	} );


	function initialize() {
		
		
        CsapCommon.configureCsapAlertify();
		jsonForms.setUtils( utils ) ;

		$( '#updateServiceButton' ).click( function () {
			serviceEdit.updateServiceDefinition();
		} );
		$( '#validateServiceButton' ).click( function () {
			serviceEdit.validateServiceDefinition();
		} );
		
		$( '#showServiceDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			$.get( serviceEditUrl,
					serviceEdit.showServiceDialog,
					'html' );
			return false;
		} );
		
		 $( ".releasePackage" ).selectmenu( {
			change: function () {
				lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"), $(this).val() ) 
			}
		} );
		// for testing page without launching dialog
                let _activeProjectFunction = function() {
                    return $("select.releasePackage").val() ;
                } ;
                let refreshFunction = null;
                let _launchMenuFunction = null;
                
                utils.initialize ( refreshFunction, _launchMenuFunction, _activeProjectFunction ) ;
		lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"),  $( ".releasePackage" ).val()) ;
	}

} );


