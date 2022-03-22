import _dom from "../utils/dom-utils.js";


const windowEvents = window_events();

export default windowEvents


function window_events() {
    
    _dom.logHead( "Module loaded" );


	 let windowResizeTimer;

	 $( window ).resize( function () {

		  clearTimeout( windowResizeTimer );
		  windowResizeTimer = setTimeout( function () {
				resizeComponents()
		  }, 200 );
	 } );

	 return {
		  resizeComponents: function () {

				resizeComponents()
		  }
	 }

	 function resizeComponents() {

		  let displayHeight = $( window ).outerHeight( true )
					 - $( "header" ).outerHeight( true ) - $( "#vmSummary" ).outerHeight( true );
		  // - $(".ui-tabs-nav").outerHeight( true)
		  // - $("#reportOptions").outerHeight( true) ;

		  let targetHeight = Math.floor( displayHeight - 200 );

		  if ( $( "#isShowJmx" ).is( ":visible" ) )
				targetHeight = targetHeight - $( "#isShowJmx" ).outerHeight( true )
		  //	
//		 console.log("targetHeight: " + targetHeight + " displayHeight: " +
//		 displayHeight + " outer: " + $(window).outerHeight( true)
//		   + " header: " + $("header").outerHeight( true) ) ;

		  setTableHeight( $( "#vmTable" ), targetHeight );
		  setTableHeight( $( "#serviceSummaryTable" ), targetHeight );
		  setTableHeight( $( "#serviceDetailTable" ), targetHeight );
		  setTableHeight( $( "#jmxSummaryTable" ), targetHeight );
		  setTableHeight( $( "#jmxDetailTable" ), targetHeight );
		  setTableHeight( $( "#jmxCustomTable" ), targetHeight );
		  setTableHeight( $( "#useridTable" ), targetHeight );

	 }

	 function setTableHeight(tableObject, targetHeight) {

//		  $( "tbody", tableObject ).css( "height", targetHeight );
//		  let rowCount = $( "tr", tableObject ).length;
//		  let rowHeight = $( "thead", tableObject ).outerHeight( true ) - 8;
//		  let measuredHeight = Math.floor( rowCount * rowHeight );
//
//		  // console.log("Row Height: " + measuredHeight + " targetHeight: " +
//		  // targetHeight) ;
//		  if ( (measuredHeight) < targetHeight ) {
//				// Compress table so it does not steal space
//				// console.log("Setting summary height to auto, measuredHeight: " +
//				// measuredHeight + "targetHeight: " + targetHeight) ;
//				$( "tbody", tableObject ).css( "height", "auto" );
//		  }
	 }

} 