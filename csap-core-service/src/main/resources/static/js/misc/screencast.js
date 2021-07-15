
$(document).ready(function() {

	CsapCommon.configureCsapAlertify();
	var screencast = new Screencast();
	screencast.appInit();

});

function Screencast() {

	// note the public method
	this.appInit = function() {
		console.log("Init");

		$("#vidSize").change(function() {

			let selectVal = $("#vidSize").val();
			$("#theVideo").attr("height", selectVal );
			
		});

	};

}
