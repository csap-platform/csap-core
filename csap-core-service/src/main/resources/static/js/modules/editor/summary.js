$(document).ready(function () {

    appInit();

});

function appInit() {
	
	$('.project').change(function() {
		
		var url="summary?project=" + $(this).val() ;
		// console.log("Going to " + url) ;
		window.location.href=url;
		return true; // prevents link
	});
	
    $('.editLimitsButton').click(function () {
		var pkg = $('.project').val() ;
		
		var targetUrl="editor?path=" + $(this).data("editorpath") +"&project="+ pkg  ;
		CsapCommon.openWindowSafely(targetUrl ,   "_blank");
		return false ;
    });

	
    $('.viewDataButton').click(function () {
		var pkg = $('.project').val() ;
		
		var targetUrl= $(this).data("url") +"&project="+ pkg  ;
		CsapCommon.openWindowSafely(targetUrl ,   "_blank");
		return false ;
    });


}