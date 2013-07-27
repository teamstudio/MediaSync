XSP.submitLatency = 60000; //timeout limit increased to 60 seconds

var xProgress= {
		
	updateInterval : 750, 		//update interval in ms
		
	progressXAgentPath : window.location.href.substring(0, window.location.href.indexOf(".nsf")+4) + "/getProgress.xsp",
	
	timerId : null,
	targetNode : null,
	targetNodeId : null,
	
	loadingImage : null,
	messageArea : null,
	
	start : function() {
	
		this.targetNode = dojo.byId(this.targetNodeId);
		
		this.loadingImage = dojo.query("img", this.targetNode)[0];
		this.messageArea = dojo.query("span", this.targetNode)[0];
		
		//show the progress area
		this.messageArea.innerHTML = "";
		dojo.style( this.targetNode, "opacity", "1");
		dojo.style( this.targetNode, "display", "");
				
		this.timerId = setInterval( dojo.hitch(xProgress, "update"), this.updateInterval);
	
	},
	
	stop : function() {
		
		if (this.timerId != null) {
			clearInterval(this.timerId);
			this.timerId = null;
		}
		
		//hide the progress area (after 2 seconds)
		setTimeout( dojo.hitch(xProgress, "hideProgressNode"), 600);
		
	},

	hideProgressNode : function() {
		
		dojo.style( this.targetNode, "opacity", "1");
		dojo.fadeOut({node: this.targetNode}).play();
	},
	
	update : function() {
		dojo.xhrGet({
			url: this.progressXAgentPath,
			handleAs: "json",
			load: dojo.hitch(xProgress, "dataLoadSuccess"),
			error: dojo.hitch(xProgress, "dataLoadError")
		});
	},
	
	dataLoadSuccess : function(data) {
		this.messageArea.innerHTML = data.progressMessage;
	},
	
	dataLoadError : function(error) {
		this.messageArea.innerHTML = "Error while showing progress: " + error;
	}
	
}