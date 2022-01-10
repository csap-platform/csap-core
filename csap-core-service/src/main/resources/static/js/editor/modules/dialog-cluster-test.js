require.config( {
    paths: {
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        browser: "../../app-browser/modules",
        projects: "../../app-browser/modules/projects",
        editor: "../../editor/modules"
    },
} ) ;

require( [ "browser/projects/env-editor/cluster-edit", "editor/json-forms", "browser/utils" ], function ( clusterEditDialog, jsonForms, utils ) {

    console.log( "\n\n ************** _main: loaded *** \n\n" ) ;

    // Shared variables need to be visible prior to scope

    $( document ).ready( function () {
        initialize() ;
    } ) ;


    function initialize() {
        CsapCommon.configureCsapAlertify() ;

        jsonForms.setUtils( utils ) ;



        $( '#showButton' ).click( function () {
            // alertify.notify("Getting clusters") ;

            // $( ".releasePackage" ).val(), $( ".lifeSelection" ).val(),$( "#dialogClusterSelect" ).val()
            var params = {
                releasePackage: $( ".releasePackage" ).val(),
                lifeEdit: $( ".lifeSelection" ).val(),
                clusterName: $( "#dialogClusterSelect" ).val()
            }


            $.get( clusterEditUrl,
                    params,
                    clusterEditDialog.show,
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
        clusterEditDialog.configureForTest() ;
    }

} ) ;


