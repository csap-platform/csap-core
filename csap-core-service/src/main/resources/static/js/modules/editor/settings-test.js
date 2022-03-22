console.log( `loading imports` );

import "../libs/csap-modules.js";


import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


import utils from "../browser/utils.js"

import jsonForms from "../editor/json-forms.js";
import settingsEdit from "../browser/projects/env-editor/settings-editor.js";


_dom.onReady( function () {


    _dom.logHead( "Module loaded" );
    jsonForms.setUtils( utils );

    $( '#showButton' ).click( function () {
        // alertify.notify("Getting clusters") ;
        let params = {
            lifeToEdit: "dev"
        }
        $.get( settingsEditUrl,
            params,
            settingsEdit.showSettingsDialog,
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

    settingsEdit.configureForTest();

}
)


