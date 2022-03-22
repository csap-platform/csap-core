
console.log( `loading imports` );

import "../libs/csap-modules.js";


import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


import utils from "../browser/utils.js"

import jsonForms from "../editor/json-forms.js";
import lifeEdit from "../browser/projects/env-editor/life-editor.js";


_dom.onReady( function () {

    let appScope = new life_test(  );

    // _dialogs.loading( "start up" );

    appScope.initialize();


} );

function life_test() {

	 this.initialize = function() {
		
		jsonForms.setUtils( utils ) ;

		$( '#updateServiceButton' ).click( function () {
			serviceEdit.updateServiceDefinition();
		} );
		$( '#validateServiceButton' ).click( function () {
			serviceEdit.validateServiceDefinition();
		} );
		
		$( '#showServiceDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			$.get( serviceEditUrl,
					serviceEdit.showServiceDialog,
					'html' );
			return false;
		} );
		
		 $( ".releasePackage" ).selectmenu( {
			change: function () {
				lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"), $(this).val() ) 
			}
		} );
		// for testing page without launching dialog
                let _activeProjectFunction = function() {
                    return $("select.releasePackage").val() ;
                } ;
                let refreshFunction = null;
                let _launchMenuFunction = null;
                
                utils.initialize ( refreshFunction, _launchMenuFunction, _activeProjectFunction ) ;
		lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"),  $( ".releasePackage" ).val()) ;
	}

}


