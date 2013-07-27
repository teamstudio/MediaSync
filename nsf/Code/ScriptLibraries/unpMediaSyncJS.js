/*
 * short/ long tap implementation
 * 
 * usage: add 2 events to the event you want to use a long press on:
 * - onclick with an event.preventDefault()  call
 * - on touch end: call the onTouchEnd() function below with 2 callback functions: the first for
 * the click event, the 2nd for the long press event
 * 
 */
var touchStart;
var touchMoved = false;
var touchDelay = 600		//ms

window.addEventListener('touchstart',function(event) {
	touchStart = new Date().getTime(); 
	touchMoved = false;        
},false);

window.addEventListener('touchmove',function(event) {
	touchMoved = true;
},false);


function touchEnd( clickCallBack, longPressCallBack ) {
	var touchedFor = (new Date().getTime()) - touchStart;
	
	if (touchMoved) {
		return;
	} else if( touchedFor > touchDelay ) {
		longPressCallBack.call(event);  
	} else {
		clickCallBack.call(event);
	}
    
}