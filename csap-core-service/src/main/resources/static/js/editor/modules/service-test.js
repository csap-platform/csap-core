require.config( {
    paths: {
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        browser: "../../app-browser/modules",
        projects: "../../app-browser/modules/projects",
        editor: "../../editor/modules"
    },
} ) ;

require( [ "browser/projects/env-editor/service-edit", "editor/json-forms", "browser/utils" ], function ( serviceEdit, jsonForms, utils ) {

    console.log( "\n\n ************** _main: loaded *** \n\n" ) ;

    // Shared variables need to be visible prior to scope

    $( document ).ready( function () {
        initialize() ;
    } ) ;


    function initialize() {
        CsapCommon.configureCsapAlertify() ;
        
        jsonForms.setUtils( utils ) ;

        $( '#showServiceDialog' ).click( function () {
            // alertify.notify("Getting clusters") ;
            var params = {
                serviceName: serviceName,
                hostName: hostName
            }
            $.get( serviceEditUrl,
                    params,
                    function ( dialogHtml ) {
                        serviceEdit.showServiceDialog( serviceName, dialogHtml )
                    },
                    'html' ) ;
            return false ;
        } ) ;

        // for testing page without launching dialog
        let _activeProjectFunction = function () {
            return $( "select.releasePackage" ).val() ;
        } ;
        let refreshFunction = null ;
        let _launchMenuFunction = null ;

        utils.initialize( refreshFunction, _launchMenuFunction, _activeProjectFunction ) ;

        serviceEdit.configureForTest() ;

    }

} ) ;


