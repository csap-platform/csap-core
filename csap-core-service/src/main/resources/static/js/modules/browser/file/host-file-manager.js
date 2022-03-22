
console.log( `loading imports` );


import "../../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../../utils/all-utils.js";


import FileManager from "./FileManager.js";

import utils from "../utils.js";


_dom.onReady( function () {

    let appScope = new file_manager_standalone( globalThis.settings );

    // _dialogs.loading( "start up" );

    //appScope.initialize();


} );

function file_manager_standalone() {

    
    _dom.logSection( `Building filemanager` );

    const $container = $( "div.csap-file-browser" );




    // decodeURIComponent( results[1].replace( /\+/g, " " ) ) whil 

    let fileManager = new FileManager(
        CSAP_HOST_NAME,
        $container,
        FM_SETTINGS.user,
        utils.getParameterByName( "serviceName" ),
        utils.getParameterByName( "quickview" ),
        FM_SETTINGS.folder,
        IS_ADMIN );

    fileManager.configureDockerContainers( FM_SETTINGS.container, FM_SETTINGS.docker );

    fileManager.initialize();



}

