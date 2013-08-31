<?xml version="1.0" encoding="utf-8" ?>
<%@page import="idd.nlp.ok.viewer.DataBean"%>
<%@page language="java" contentType="text/html; charset=utf-8"	pageEncoding="utf-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" dir="rtl">
<head>
<link rel="shortcut icon" href="favicon.ico" type="image/ico">
<link rel="stylesheet" href="http://code.jquery.com/ui/1.10.3/themes/smoothness/jquery-ui.css" />
<style type="text/css">
 @font-face { 
   font-family: 'Alef'; 
   src: url('font/Alef-bold.eot');
   src: url('font/Alef-bold.woff') format('woff'), url('font/Alef-bold.ttf') format('truetype'), url('font/Alef-bold.svg#alefbold') format('svg'); 
   font-weight: bold; 
   font-style: normal; 
 }@font-face { 
   font-family: 'Alef'; 
   src: url('font/Alef-regular.eot');
   src: url('font/Alef-regular.woff') format('woff'), url('font/Alef-regular.ttf') format('truetype'), url('font/Alef-regular.svg#alefregular') format('svg');
   font-weight: normal; 
   font-style: normal; 
 } 
 .clearfix:after {
	content: ".";
	display: block;
	clear: both;
	visibility: hidden;
	line-height: 0;
	height: 0;
}
 
.clearfix {
	display: inline-block;
}
html,body {
	height: 100%;
	margin: 0px;
	padding: 0px;
	font-family: Alef, Arial;
	font-size: 16px;
	background-color: rgb(239, 239, 239);
	color: rgb(70, 69, 69);
	
}

.ui-widget {
	font-size: 1em;
}

#form-fields {
	
}

#div-ngrams {
	width: 100%;
	overflow: hidden;
	margin-bottom: 6px;
}

#div-date-range input {
	margin-top: -2px;
}

#div-fields {
	border-bottom: 2px black solid;
	height: 90px;
	margin-bottom: 20px;
}


.container {
	margin-left: auto;
	margin-right: auto;
	width: 700px ;
}

input:focus { 
    outline:none;
    border-color:#9ecaed;
    box-shadow:0 0 10px #9ecaed;
}
input {
	font-family: Alef,Arial;
}

#input-ngrams {
	width: 70%;
	font-size: 1.2em;
	font-weight: bolder;
	padding: 10px;
	border:thin black solid;
	margin-bottom: 5px;
	margin-right:5px;
	margin-top:5px;
	border-radius: 19px;
	-webkit-border-radius: 19px;
	-moz-border-radius: 19px;
}
.watermark {
	color: #999 !important;
}

.bold {
	font-weight: bolder;
}

#chartWrapper {
margin-top:10px;
border: 1px solid #e6e6e6;
-webkit-border-radius: 5px;
-moz-border-radius: 5px;
border-radius: 5px;
-webkit-box-shadow: 0 1px 1px rgba(0,0,0,.075);
-moz-box-shadow: 0 1px 1px rgba(0,0,0,.075);
box-shadow: 0 1px 1px rgba(0,0,0,.075);
background-color: #FFF;
padding:1px;
}
</style>
 
 
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<script type="text/javascript" src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
<script src="https://ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min.js"></script>
<script src="js/purl.js"></script>
<script src="js/jquery.watermark.min.js"></script>
<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript">
	google.load('visualization', '1.0', {'packages':['corechart', 'annotatedtimeline']});
	function cssResize(){
		var width=$(window).width();
		var containerWidth=400;
		if (width>1200){
			containerWidth=1170;
		} else if (width>1100){
			containerWidth=940;
		} else if (width>900){
			containerWidth=724;
		} else if (width>800){
			containerWidth=700;
		} else if (width>720){
			containerWidth=600;
		} else if (width>620){
			containerWidth=500;
		}
		$(".container").css("width",containerWidth);
	}
	
	$(function() {
		cssResize();
		$(window).resize(function(){
			if (window.redraw)
				redraw();
			cssResize();
		});
		$("input[name='type']").filter(
				"input[value='" + $.url().param("type") + "']").prop("checked",
				"checked");
		$("input[name='res']").filter(
				"input[value='" + $.url().param("res") + "']").prop("checked",
				"checked");
		$("#input-ngrams").val($.url().param("ngrams"));
		$("#input-ngrams").watermark("NGrams");
		$("#range-date-from").val($.url().param("range-from"));
		$("#range-date-to").val($.url().param("range-to"));
		$("#check-non-party").prop("checked",$.url().param("non-party")=="on");
		
		$("#range-date-from").datepicker({ dateFormat: "yy-mm-dd",autoSize:true, maxDate:0, isRTL: true});
		$("#range-date-from").watermark("מתאריך");
		$("#range-date-to").watermark("עד תאריך");
		$("#range-date-to").datepicker({ dateFormat: "yy-mm-dd",autoSize:true, maxDate:0, isRTL: true });
		
		$("input[name='type']").change(function() {
			if ($("#type-date").prop("checked")) {
				$("#div-date-res").show();
				$("#div-date-range").hide();
				$("#div-non-party").hide();
			} else if ($("#type-party").prop("checked")) {
				$("#div-date-res").hide();
				$("#div-date-range").show();
				$("#div-non-party").show();
			} else if ($("#type-person").prop("checked")) {
				$("#div-date-res").hide();
				$("#div-date-range").show();
				$("#div-non-party").hide();
			}
		}).change();
		$("#input-ngrams").focus();
	});
</script>

	<title>Knesset NGram viewer</title>
</head>

<body>
	<header class="container">
	<div id="div-fields">
		<form id="form-fields" >
			<div id="div-ngrams">
				<span><input id="input-ngrams" name="ngrams" type="search" results="5"/></span>
				<input id="input-submit" name="submit" type="submit" value="חפש" class="bold"/>
			</div>
			
			<div id="div-options">
				<span class="bold">חיתוך לפי</span> 
				<input id="type-date" name="type" type="radio" value="date" checked="checked" />
				<label for="type-date">תאריך</label>
				<input id="type-party" name="type" type="radio" value="party" /><label for="type-party">מפלגה</label> 
				<input id="type-person" name="type" type="radio" value="person" /><label for="type-person">אדם</label>
				&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
				<span id="div-date-res">
					<span class="bold">רזולוציה</span> <input id="type-date-res-year" name="res" type="radio" value="year" />
					<label for="type-date-res-year">שנה</label>
					<input id="type-date-res-month" name="res" type="radio" value="month" />
					<label for="type-date-res-month">חודש</label> 
					<input id="type-date-res-week" name="res" type="radio" value="week" checked="checked" />
					<label for="type-date-res-week">שבוע</label>
					<input id="type-date-res-day" name="res" type="radio" value="day" />
					<label for="type-date-res-day">יום</label>
				</span>
				<span id="div-date-range">
					<span class="bold">תאריכים</span> <input id="range-date-from" name="range-from" type="text"  autocomplete="off" />&nbsp;-&nbsp; 
					<input id="range-date-to" name="range-to" type="text" autocomplete="off" />
					<span id="div-non-party">
						<input id="check-non-party" name="non-party" type="checkbox" /> <label for="check-non-party">כולל לא-מפלגות</label>
					</span>
				</span>
			</div>
		</form>
	</div>
	</header>
	<script type="text/javascript">
		$(function(){
		    google.setOnLoadCallback(drawChart);
		    var rawData=<%=DataBean.instance.getDataJson(request)%>;
	    	function recalcChartSize(){
	    		$("#chart").height($(window).height()-$("#form-fields").height()-50);
// 	    		$("#chart").width()
	    	}
	    	
	    	function drawChart() {
	    		var commonOptions={legend: { position: 'top'}, 
	    				fontName: "Alef,Arial",
	    				tooltip: { isHtml: true },
	    				chartArea: {top: 50, left:100,width: "85%",height:"80%" }, 
	    				fontSize: 16, 
	    				backgroundColor:"#fff"};
<%
String chartType=request.getParameter("type");
if ("date".equals(chartType)) { %>
 				var chart = new google.visualization.LineChart(document.getElementById('chart'));
				var data=new google.visualization.DataTable();
				for (var i=0;i<rawData.length;i++){
					var row=rawData[i];
					if (i==0){
						for (var j=0;j<row.length;j++){
							if (j==0){
								data.addColumn('date','תאריך');
							} else {
								data.addColumn('number',row[j]);
							}
						}
					} else {
						data.addRow(row);
					}
				}
				// Format the data columns.
				var formatter = new google.visualization.NumberFormat({pattern:'0.0000######%'});
				for (var col = 1; col < data.getNumberOfColumns(); col++) {
				  formatter.format(data, col);
				}
				// Set general chart options.
				var options = $.extend(commonOptions,{focusTarget: 'category', 
					 
					hAxis: { format: '####', gridlines: { count: -1 } }, 
					vAxis: {
				      format: '0.00########%',
				      gridlines: { count: 6 },
				      minValue: 0,
				      baselineColor: 'black'
				    }
				});

				function selectHandler() {
				    chart.setSelection(null);
				}
				  // Draw the chart.
				google.visualization.events.addListener(chart, 'select', selectHandler);
		    	window.redraw=function(){
		    		recalcChartSize();
		    		chart.draw(data,options);
		    	};
		    	redraw();
		    	
<% } else if ("person".equals(chartType)){%>
				var data = google.visualization.arrayToDataTable(rawData);
				var chart=new google.visualization.BarChart(document.getElementById('chart'));
				commonOptions.chartArea.left=180;
				var options=$.extend(commonOptions,{vAxis: {title: "אדם"}, hAxis: {title: "ספירה"}});
				window.redraw=function(){
					recalcChartSize();
					if ($("#chart").height()/data.getNumberOfRows()<22) {
						$("#chart").height(data.getNumberOfRows()*22);
					}
					chart.draw(data,options);
				};
				redraw();
<% } else if ("party".equals(chartType)) { %>
				var data = google.visualization.arrayToDataTable(rawData);
				var chart=new google.visualization.ColumnChart(document.getElementById('chart'));
				var options=$.extend(commonOptions,{vAxis: {title: "ספירה"}, hAxis: {title: "מפלגה"}});
				window.redraw=function(){
					recalcChartSize();
					chart.draw(data, options);
				};
				redraw();
<% } %>
	    	}
		});
	</script>
	<div id="chartWrapper" class="container">
	<div id="chart" >
	</div>
	</div>
	<footer class="container">
	<div dir="ltr" style="font-size:12px">
		<span style="margin-right:20px;">&copy; <a href="mailto:yonatan.graber@gmail.com">Yonatan Graber</a> 2013</span>
		<span>Final project for the course 3523-NLP 2013, Dr. Reut Tsarfaty, <a href="http://portal.idc.ac.il/he/schools/cs/home/Pages/homepage.aspx">The Efi Arazi School of Computer Science</a>, IDC Herzliya</span>
		
	</div>
	</footer>
</body>
</html>
