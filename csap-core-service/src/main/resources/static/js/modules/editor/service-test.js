
console.log( `loading imports` );

import "../libs/csap-modules.js";


import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


import utils from "../browser/utils.js"

import jsonForms from "../editor/json-forms.js";
import serviceEdit from "../browser/projects/env-editor/service-edit.js";


_dom.onReady( function () {


    _dom.logHead( "Module loaded" );
    jsonForms.setUtils( utils );

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
            'html' );
        return false;
    } );

    // for testing page without launching dialog
    let _activeProjectFunction = function () {
        return $( "select.releasePackage" ).val();
    };
    let refreshFunction = null;
    let _launchMenuFunction = null;

    utils.initialize( refreshFunction, _launchMenuFunction, _activeProjectFunction );

    serviceEdit.configureForTest();

})


