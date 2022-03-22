

console.log( `loading imports` );

import "../libs/csap-modules.js";


import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


import utils from "../browser/utils.js"

import jsonForms from "../editor/json-forms.js";
import clusterEditDialog from "../browser/projects/env-editor/cluster-edit.js";


_dom.onReady( function () {


    _dom.logHead( "Module loaded" );

    jsonForms.setUtils( utils );



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
    clusterEditDialog.configureForTest();
}

)


