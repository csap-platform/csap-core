// define( [ "browser/utils", "file/FileManager", "ace/ace", "ace/ext-modelist" ], function ( utils, FileManager, aceEditor, aceModeList ) {

//     console.log( "Module loaded" ) ;



import _dom from "../../utils/dom-utils.js";

import utils from "../utils.js"
import FileManager from "../file/FileManager.js"



const fileBrowser = projects_fileBrowser();

export default fileBrowser


function projects_fileBrowser() {

    _dom.logHead( "Module loaded" );


    let fileManager;

    const $projectPanel = utils.findContent( "#projects-tab-files" );

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            return getFileBrowser( $menuContent, forceHostRefresh, menuPath );

        }

    };



    function getFileBrowser( $menuContent, forceHostRefresh, menuPath ) {


        if ( !forceHostRefresh && ( fileManager != null ) ) {
            return;
        }

        let location = "__platform__\/definition";
        //location ="__root__" ;

        if ( utils.adminHost() == "localhost" ) {
            location = "__root__/aaa";
            location = "__home__/git/***REMOVED***-desktop-definition";
        }



        let $contentLoaded = new $.Deferred();

        let parameters = {
            quickview: "CSAP Application Definition",
            fromFolder: "__platform__/definition"
        };


        $projectPanel.hide();
        utils.loading( `Loading summary for ${ utils.getActiveProject( false ) }` );
        //targetUrl + "&callback=?",  
        let summaryUrl = `${ FILE_URL }/FileManager`;
        console.log( `loading: ${ summaryUrl }` );


        $.get( summaryUrl,
            parameters,
            function ( lifeDialogHtml ) {
                let $summaryContent = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-browser' );

                console.log( ` content: `, $summaryContent[ 0 ] );
                $projectPanel.html( $summaryContent );

                //utils.loadingComplete() ;
                $projectPanel.show();

                //
                fileManager = new FileManager(
                    utils.adminHost(),
                    $projectPanel,
                    utils.getCsapUser(),
                    "",
                    "Application Definition",
                    location,
                    true );

                fileManager.info();


                fileManager.initialize();

                //fileManager = new FileManager( $fileTree ) ;

                $contentLoaded.resolve();
            },
            'text' );


        return $contentLoaded;


    }


}

