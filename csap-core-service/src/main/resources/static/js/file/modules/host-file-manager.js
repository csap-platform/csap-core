// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    paths: {
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        // CRITICAL: use relative paths to leverage spring boot VersionResourceResolver
        browser: "../../app-browser/modules",
        editor: "../../editor/modules"
    },
    packages: []

} );

require( ["FileManager", "browser/utils"], function ( FileManager, utils ) {

    console.log( "\n\n ************** _main: loaded  *** \n\n" );
    
    const $container = $("div.csap-file-browser") ;
    
     
    

    $( document ).ready( function () {
        
        CsapCommon.configureCsapAlertify() ;
        
        // decodeURIComponent( results[1].replace( /\+/g, " " ) ) whil 
        
        let fileManager = new FileManager( 
                CSAP_HOST_NAME ,
                $container, 
                FM_SETTINGS.user, 
                utils.getParameterByName( "serviceName" ), 
                utils.getParameterByName( "quickview" ) ,
                FM_SETTINGS.folder,
                IS_ADMIN ) ;
                
         fileManager.configureDockerContainers( FM_SETTINGS.container, FM_SETTINGS.docker) ;
                
         fileManager.initialize() ;
                
    } );

} );


jQuery.each( ["put", "delete"], function ( i, method ) {
    jQuery[ method ] = function ( url, data, callback, type ) {
        if ( jQuery.isFunction( data ) ) {
            type = type || callback;
            callback = data;
            data = undefined;
        }

        return jQuery.ajax( {
            url: url,
            type: method,
            dataType: type,
            data: data,
            success: callback
        } );
    };
} );
