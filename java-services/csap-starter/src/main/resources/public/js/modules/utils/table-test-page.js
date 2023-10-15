

import "../libs/csap-modules.js";
import "../utils/table-test-page.js" ;


import { _dialogs, _dom, _utils, _net } from "./all-utils.js";




_dom.onReady( function () {


	_dom.logSection( `numbering table columns and registering clicks externally...` );

	_utils.prefixColumnEntryWithNumbers( $( "body.test-page table" ) );

	
	_dialogs.loadingComplete();

	
	$( "body.test-page table a.csap-link" ).click( function ( e ) {
		e.preventDefault();
		let url = $( this ).attr( 'href' );
		window.open( url, '_blank' );
	} );

	

	$( "body.test-page table h3" ).each( function ( e ) {
		let $title = $( this );

		let titleName = $title.text();
		let titleId = titleName.replace( /\s+/g, '' );;

		$title.attr( "id", titleId );

		let $indexLink = jQuery( '<a/>', {
			class: "csap-link",
			href: `#${ titleId }`,
			text: titleName
		} );

		$( "body.test-page #index" ).append( $indexLink );
	}) ;


} );