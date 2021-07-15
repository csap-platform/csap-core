$(document).ready(
	function() {
		
		CsapCommon.configureCsapAlertify();
		$('#currentTimeButton').click(getTime);

		CsapCommon.labelTableRows($("table"))

		$('#themeTablesButton').click(
			function() {

				$("table").tablesorter({
					theme: 'metro-dark'
				});

				$("table").removeClass("simple").css("width", "80em")
					.css("margin", "2em");
			});

		$("table a.csap-link").click(function(e) {
			e.preventDefault();
			var url = $(this).attr('href');
			window.open(url, '_blank');
		});



		$("table th:first-child div.title").each(function(e) {
			let $title = $(this);
			
			let titleName=$title.text() ;
			let titleId=titleName.replace(/\s+/g, ''); ;
			
			$title.attr("id", titleId);

			let $indexLink = jQuery('<a/>', {
				class: "csap-link",
				href: `#${titleId}`,
				text: titleName
			});

			$("#index").append($indexLink) ;
		});

	});

function getTime() {

	$('body').css('cursor', 'wait');
	$.get("currentTime",
		function(data) {
			// alertify.alert(data) ;
			alertify.dismissAll();
			alertify.csapWarning('<pre style="font-size: 0.8em">' + data
				+ "</pre>")
			$('body').css('cursor', 'default');
		}, 'text' // I expect a JSON response
	);

}