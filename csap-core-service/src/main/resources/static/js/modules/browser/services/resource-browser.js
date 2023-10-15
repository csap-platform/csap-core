// define( [ "browser/utils", "file/FileManager", "ace/ace", "ace/ext-modelist", "services/instances" ], function ( utils, FileManager, aceEditor, aceModeList, instances ) {

//     console.log( "Module loaded" ) ;



import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


import instances from "./instances.js"

import FileManager from "../file/FileManager.js"

const resourceBrowser = resource_browser();

export default resourceBrowser


function resource_browser() {

    _dom.logHead( "Module loaded" );


    let fileManager ;

    let lastServiceName ;

    const $projectPanel = utils.findContent( "#services-tab-resources" ) ;

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            return getFileBrowser( $menuContent, forceHostRefresh, menuPath ) ;

        }

    } ;



    function getFileBrowser( $menuContent, forceHostRefresh, menuPath ) {


        let serviceName = instances.getSelectedService() ;
        if ( serviceName != lastServiceName ) {
            forceHostRefresh = true ;
        }
        lastServiceName = serviceName ;


        if ( !forceHostRefresh && ( fileManager != null ) ) {
            return ;
        }

        let location = "__platform__\/definition" ;
        //location ="__root__" ;

        if ( utils.adminHost() == "localhost" ) {
            location = "__root__/aaa" ;
            location = "__home__/git/yourcompany-desktop-definition" ;
        }


        location += `/resources/${ serviceName }`



        let $contentLoaded = new $.Deferred() ;

        let parameters = {
            quickview: "CSAP Application Definition",
            fromFolder: "__platform__/definition"
        } ;


        $projectPanel.hide() ;
        utils.loading( `Loading summary for ${  utils.getActiveProject( false ) }` ) ;
        //targetUrl + "&callback=?",  
        let summaryUrl = `${ FILE_URL }/FileManager` ;
        console.log( `loading: ${summaryUrl}` ) ;


        $.get( summaryUrl,
                parameters,
                function ( lifeDialogHtml ) {
                    let $summaryContent = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-browser' ) ;

                    console.log( ` content: `, $summaryContent[0] ) ;
                    $projectPanel.html( $summaryContent ) ;

                    //utils.loadingComplete() ;
                    $projectPanel.show() ;

                    fileManager = new FileManager(
                            utils.adminHost(),
                            $projectPanel,
                            utils.getCsapUser(),
                            "",
                            `Application Definition Resources: ${ serviceName } `,
                            location,
                            true ) ;

                    fileManager.info() ;

                    fileManager.initialize() ;

                    //fileManager = new FileManager( $fileTree ) ;

                    $contentLoaded.resolve() ;
                },
                'text' ) ;


        return $contentLoaded ;


    }


}

