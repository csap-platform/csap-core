// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    paths: {
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",

        // CRITICAL: use relative paths to leverage spring boot VersionResourceResolver
        browser: "../../app-browser/modules",
        file: "../../file/modules",
        editor: "../../editor/modules"
    },
    packages: [ ]
} ) ;


// "ace/ext-modelist" "../../../testMode"
require( [ "browser/utils", "file/file-monitor", "file/log-formatters", "ace/ace", "ace/ext-modelist" ], function ( utils, fileMonitor, logFormatters, aceEditor, aceModeListLoader ) {

    console.log( "\n\n ************** _main: loaded *** \n\n" ) ;

    $( document ).ready( function () {
        initialize() ;
    } ) ;

    function initialize() {

        fileMonitor.show( $( "body" ), CSAP_HOST_NAME, utils.getParameterByName( "serviceName" ) ) ;

    }


} ) ;