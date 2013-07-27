//load the global application configuration
function loadAppConfig(forceUpdate:boolean) {
	
	var vwConfig:NotesView = null;
	var docConfig:NotesDocument = null;

	try {
		
		if (!applicationScope.containsKey("configLoaded") || forceUpdate ) {
			//(re)load application config
			
			vwConfig = database.getView("vConfig")
			docConfig = vwConfig.getFirstDocument();
			
			var configLoaded = false;
			
			while (null != docConfig && !configLoaded) {
				
				if (docConfig.getItemValueString("isActive").equals("true")) {
					
					applicationScope.put("hasGlobalConfig", true);
					applicationScope.put("connectionsServerUrl", docConfig.getItemValueString("connectionsServerUrl") );
					applicationScope.put("connectionsServerDescription", docConfig.getItemValueString("connectionsServerDescription") );
					applicationScope.put("tempFolder", docConfig.getItemValueString("tempFolder") );
					
					applicationScope.put("activeConfigId", docConfig.getItemValueString("id"));
					
					applicationScope.put("dbPath", "/" + database.getFilePath().replace("\\", "/"));
					
					applicationScope.put("configUnid", docConfig.getUniversalID() );
					
					configLoaded = true;
					
				}
				
				docConfig = vwConfig.getNextDocument(docConfig);
			}
			
			applicationScope.put("configLoaded", true);
			
		}
	} catch (e) {
		dBar.error(e, "loadAppConfig");
	} finally {
		recycleObjects( docConfig, vwConfig);
	}
}

//load the configuration for a specific user
function loadUserConfig( forceUpdate:boolean) {
	
	var vwUsers:NotesView = null;
	var docUser:NotesDocument = null;

	var currentUser:String = @UserName();

	try {
	
		if (currentUser != sessionScope.get("currentUser") || forceUpdate ) {
			//(re) load session config
			
			//retrieve settings from user's configuration
			vwUsers = database.getView("vUsers");
			docUser = vwUsers.getDocumentByKey( currentUser, true);
			
			sessionScope.put("photoUrl", "/.ibmxspres/domino" + applicationScope.dbPath + "/assets/avatars/avatar2.png" );
			
			if (null != docUser) {
				
				sessionScope.put("hasUserConfig", true);
				sessionScope.put("userProfileUnid", docUser.getUniversalID());
				sessionScope.put("connectionsUsername", docUser.getItemValueString("connectionsUsername"));
				sessionScope.put("connectionsPassword", docUser.getItemValueString("connectionsPassword"));
				
				var photoFileName = docUser.getItemValueString("photoFileName");
				
				if (photoFileName.length>0) {
					sessionScope.put("photoUrl", "/.ibmxspres/domino" + applicationScope.dbPath + "/0/" + userProfileUnid + "/$file/" + photoFileName );	

				}
				
			} else {
				
				sessionScope.put("hasUserConfig", false);
				
			}
			
			sessionScope.put("currentUser", @UserName());
			sessionScope.put("isAdmin", context.getUser().getRoles().contains("[admin]") );
			
		}
	} catch (e) {
		dBar.error(e, "loadUserConfig");
	} finally {
		recycleObjects( docUser, vwUsers);
	}
}

//add a message to the facesContext (will be displayed on screen)
function addUserMessage( message:String) {	
	facesContext.addMessage( null, new javax.faces.application.FacesMessage( message ) );
}

//retrieve the base64 encoded username/ password string that can be used to set
//HTTP basic authentication on an HTTP connection 
function getBasicAuthenticationValue() {
	
	var authString:String = sessionScope.get("connectionsUsername") + ":" + sessionScope.get("connectionsPassword");
	//dBar.debug("auth string: " + authString);
	
	var authEncBytes = javax.xml.bind.DatatypeConverter.printBase64Binary(authString.getBytes());
	//dBar.debug("Base64 encoded auth string: " + authEncBytes);
	
	return "Basic " + authEncBytes;
}

//retrieve the href attribute of a link node in a Bookmarks entry
function getLinkNodeValue( entryNode, relType:String, prefix:String, attribute:String) {
	
	prefix = (arguments.length>=3 ? prefix : "");
	attribute = (arguments.length>=4 ? attribute : "href");
	
	var linkNodeList:DOMNodeList = entryNode.getElementsByTagName( prefix + "link" );

	for (var i=0; i<linkNodeList.getLength(); i++) {
	
		var linkNode:DOMElement = linkNodeList.item(i);
	
		if (relType == null && (linkNode.getAttribute("rel")==null || linkNode.getAttribute("rel").length==0)) {
			return linkNode.getAttribute( attribute );
		} else if (linkNode.getAttribute("rel")==relType) {
			return linkNode.getAttribute( attribute );
		}
	}
	
	return null;
}

//check feed for a 'next page' link and return it if found
function getNextPageLink( feedNode, prefix:String ) : String {
	
	var linkNodeList:DOMNodeList = feedNode.getElementsByTagName(prefix + "link");
	
	//loop through all bookmarks in the feed
	for (var i=0; i<linkNodeList.getLength(); i++) {
		var linkNode = linkNodeList.item(i);
		
		var rel:String = linkNode.getAttribute("rel");
		
		if (!isEmpty(rel) && rel.equals("next")) {
			return linkNode.getAttribute("href");
		}
		
	}
	return null;
}

//debugging function that can store the response of a connection in a local document
function storeData( name:String, inputStream ) {
	 
	//read the retrieved data
    var result = [];
    var line:String = null;
    
    var bufReader  = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
    
    while ((line = bufReader.readLine()) != null) {
    	result.push(line);
    }
      
    var doc = database.createDocument();
    doc.replaceItemValue("form", "feedData");
    doc.replaceItemValue("name", name);
    doc.replaceItemValue("xml", result.join(""));
    doc.save();
    
    return result.join("");
	
}


//couple of helper function to parse XML documents/ streams
var XMLParser = {
	
	//parse an XML document from an inputstream
	getXMLDocument : function( inputStream ) {
	
		try {
			//create parser factory and document builder
			var domFactory:javax.xml.parsers.DocumentBuilderFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(true);
			domFactory.setValidating(false);
			domFactory.setIgnoringComments(false);
			domFactory.setIgnoringElementContentWhitespace(true);
	
			domFactory.setCoalescing(true);		//Coalescing specifies that the parser produced by this code will convert 
												//CDATA nodes to Text nodes and append it to the adjacent (if any) text node.
												//By default the value of this is set to false.
			
			var docBuilder:javax.xml.parsers.DocumentBuilder = domFactory.newDocumentBuilder();
			//docBuilder.setErrorHandler(null);
			//docBuilder.setEntityResolver(null);
		
			//parse the inputstream and return the result
			return docBuilder.parse(inputStream);
		} catch (e) {
			dBar.error(e);
			return null;
		}
	
	},
	
	//retrieve the value (or value of an attribute) of a node with the specified node name
	//or null if the node wasn't found
	getNodeValue : function( parentNode, nodeName:String, attribute:String) {
		
		var result:String = null;
		
		try {
			
			var nodeList:DOMNodeList = parentNode.getElementsByTagName( nodeName );
		
			if (nodeList != null && nodeList.getLength()>0) {
		
				var targetNode:DOMElement = nodeList.item(0);
		
				if (attribute != null) {
					result = targetNode.getAttribute(attribute);
				} else {
					result = targetNode.getTextContent();
				}
			}
		} catch (e) {
			dBar.error(e, "XMLParser.getNodeValue");
		}
		
		return result;
	}
	
}

//read the inputstream of a connection and return it as a JSON object 
function getResponseAsJson( inputStream) {
	
	try {
	
		var result = [];
	    var line:String = null;

	    var bufReader  = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
	    
	    while ((line = bufReader.readLine()) != null) {
	    	result.push(line);
	    }
	    
	    var jsonResult = fromJson( '{"values":' + result.join("") + '}' ).values; 

	    return jsonResult;
	    
	} catch (e) {
		dBar.error(e, "getResponseAsJson");
		return null;
	}    
	    
}

//return a connection to a remote resource, including authorization parameters
function getAuthenticatedConnection( targetUrl:String, addSessionCookies:boolean ) {
	
	addSessionCookies = (arguments.length>=2 ? addSessionCookies : false);
	
	//retrieve connection session settings we might need 
	if (!getConnectionsSessionSettings() ) {
		dBar.error("cannot retrieve Connections session settings");
	}
	
	//open the connection to the specified url
	var url:java.net.URL = new java.net.URL(targetUrl);
	var connection:java.net.HttpURLConnection = url.openConnection();

	connection.setRequestProperty("Authorization", getBasicAuthenticationValue());
	connection.setReadTimeout(30000);
	
	//add Connections cookies
	if (addSessionCookies) {
		var cookies = sessionScope.get("connectionsCookies"); 
		for (var i=0; i<cookies.size(); i++) {
			connection.addRequestProperty("Cookie", cookies[i].split(";", 2)[0]);
		}
	}

	return connection;
}

/* 
 * 
 * retrieve the current user's Connections user id and store it in the sessionScope
 * we need the userid in the url to retrieve a Nonce key:
 * {{url}}/files/form/opensocial/people/{{userId}}/@self
 * 
 
 * Some Connections requests appear to require session cookies to be sent along with the request 
 * (just adding a Basic Authentication header doesn't seem sufficient)
 *
 * This function is used to retrieve a list of the session cookies that can be sent along with API request.
 * The list of cookies is cached in the sessionScope for a limited amount of time.
 * 
 * The url used to retrieve session cookies is the profiles service document
 */
function getConnectionsSessionSettings() {
	
	var connection:java.net.HttpURLConnection = null;
	var success = false;
	
	try {
		
		var cacheMinutes = 15;		//number of minutes the cached session cookies will remain valid
		var targetUrl:String = applicationScope.get("connectionsServerUrl") + "/profiles/atom/profileService.do";
		
		//check if local list of settings has expired
		if (sessionScope.containsKey("connectionsSettingsRetrievedAt")) {
			
			var now = (new java.util.Date()).getTime();
			var retrieved = sessionScope.get("connectionsSettingsRetrievedAt").getTime();
			
			if ( (now - retrieved) > (cacheMinutes *60*1000) ) {		
				sessionScope.remove("connectionsCookies");
				sessionScope.remove("connectionsSettingsRetrievedAt");
			}

		}
		
		if ( !sessionScope.containsKey("connectionsSettingsRetrievedAt") ) {
			
			dBar.debug("retrieving Connections session settings");
			
			//don't use getAuthenticatedConnection() here or we'll enter a loop
			var url:java.net.URL = new java.net.URL(targetUrl);
			connection = url.openConnection();
			connection.setRequestProperty("Authorization", getBasicAuthenticationValue());
			connection.setReadTimeout(5000);
			connection.connect();
		    
		    var statusCode:int = connection.getResponseCode();
			
			if (statusCode != 200 ) {
				dBar.error("could not retrieve session settings from " + targetUrl + " (response code: " + statusCode + ")");
				return;
			}
			
			 //retrieve the user's bookmarks url from the service document
			var parsedXml:org.w3c.dom.Document = XMLParser.getXMLDocument(connection.getInputStream());
		    
		    if (parsedXml == null ) {
		    	dBar.error("could not parse XML document");
		    	return false;
		    }
		    
		    var serviceNode:DOMElement = parsedXml.getDocumentElement();
			var workspaceNode:DOMElement = serviceNode.getElementsByTagName("workspace").item(0);
			var collectionNode:DOMElement = workspaceNode.getElementsByTagName("collection").item(0);
		    
		    sessionScope.put( "connectionsUserId", XMLParser.getNodeValue(collectionNode, "snx:userid", null) );
		    
	        cookies = connection.getHeaderFields().get("Set-Cookie");
	        sessionScope.put("connectionsCookies", cookies);
	        
	        sessionScope.put("connectionsSettingsRetrievedAt", new Date() );
		}
		
		success = true;
				       
	} catch (e) {
		
		dBar.error(e);
		
	
	} finally {
		
		if (connection != null) { connection.disconnect(); }
	}
	
	return success;
		
}

//shared sync function to remotely delete one or more items that were
//marked for deletion locally
function deleteItems( dcItems:NotesDocumentCollection ) {
	
	var numDelete = 0;

	try {
		
		numDelete = dcItems.getCount();
		
		dBar.debug("found " + numDelete + " items to delete", "delete items");
	
		if (numDelete>0) {
			var docTemp:NotesDocument = null;
			
			//loop through items to delete and remove them in Connections as well as the local database
			var doc:NotesDocument = dcItems.getFirstDocument();
			while (null != doc) {
				docTemp = dcItems.getNextDocument(doc);
				
				deleteItem(doc);
				
				doc.recycle();
				doc = docTemp;
			}
			
			dBar.debug("finished", "delete items");
		}
		
	} catch (e) {
		dBar.error(e, "delete items");
	} finally {
		recycleObjects(dcItems );
	}
	
	return numDelete;
	
}

//remotely deletes an individual item
function deleteItem(doc:NotesDocument) {
	
	var connection:java.net.HttpURLConnection = null;

	try {
		
		dBar.debug("deleting item \"" + doc.getItemValueString("title") + "\" (local unid " + doc.getUniversalID() + ")", "delete item");
		
		connection = getAuthenticatedConnection( doc.getItemValueString("linkEdit") );
		connection.setRequestMethod("DELETE");
		connection.setDoOutput(true);
		connection.connect();
		
		var statusCode:int = connection.getResponseCode();
		
		switch (statusCode) {
		
		case 204:		//ok
			
			dBar.debug("response ok (204)", "delete item");
			doc.remove(true);
			
			break;
		case 400:
			dBar.error("bad request (status code 400)", "delete item");
			break;
		case 403:
			dBar.error("forbidden (status code 403)", "delete item");
			break;
		case 404:
			dBar.debug("item not found (anymore) in Connections (status code 404)", "delete item");
			doc.remove(true);
			break;
		default:
			dBar.error("unknown response (status code " + statusCode + ")", "delete item");
			break;
		}
		
	} catch (e) {
		dBar.error(e, "delete item");
	} finally {
		
		if (connection != null) { connection.disconnect(); }
	}
	
}



//recycle a set of Domino objects passed through the function arguments
function recycleObjects() {
	for(var i=0; i<arguments.length; i++ ) {
		
		if ( arguments[i] != null ) {
			if ( arguments[i] instanceof lotus.domino.Base) {
                try {
                    arguments[i].recycle();
                } catch (ne) { }
            }
			
		}
	}
}

//retrieve a Java Date object based on a DateTime value and cleansup the DateTime object
function getJavaDate( dt:NotesDateTime) :Date {
	var date:Date = null;

	if (dt != null) {
		try {
			date = dt.toJavaDate();
		} catch (e) {
			//do nothing
		} finally {
			recycleObjects(dt);
		}
	}
	return date;
}


//check if a string is empty / null
function isEmpty( s:String ) : boolean {

	if (s==null) { return true; }
	if (s.length==0) { return true; }

}

//check if a pager should be visible (has more than 1 page).
function isPagerVisible( pager:com.ibm.xsp.component.xp.XspPager) : boolean {
    var state:com.ibm.xsp.component.UIPager.PagerState = pager.createPagerState();
    return state.getRowCount() > state.getRows(); 
}

//used to show a message to the browser while performing a sync
function showProgress( message:String ) {
	sessionScope.put("progressMessage", message);
}

//dates in ATOM feeds are written as strings in RFC 3339 format
//this function parses that string and return a Date object
function parseRFC3339Date( dateString:String) :Date {

	var result:Date = null;
	
	try {
		if ( isEmpty(dateString)) {
			return null;
		}
		
		//parse the date
		if (dateString.indexOf("T")==-1) {
			dBar.error("invalid date string: " + dateString);
			return null;
		}
			
		var date:String = @Left(dateString, "T");
		var time:String = @Right(dateString, "T");
		
		var year = @Word(date, "-", 1);
		var month = @Word(date, "-", 2);
		var day = @Word(date, "-", 3);
		
		var zone:String = null;
		var add:boolean = false;
		
		if( @Ends(dateString, "Z") ) {		//no time-zone specified: time is in UTC
			time = @Left(time, "Z");
		} else if (time.indexOf("-")>-1){
			zone = @Right(time, "-");
			time = @Left(time, "-");
			add = true;
		} else if (time.indexOf("+")>-1) {
			zone = @Right(time, "+");
			time = @Left(time, "+");
			add = false;
		}
		
		var hour:int = @Word(time, ":", 1);
		var minute:int = @Word(time, ":", 2);
		var seconds = @Word(time, ":", 3);
		var ms:int = 0;
		
		if (seconds.indexOf(".")>-1) {		//contains ms
			
			ms = @Word(seconds, ".", 2);
			seconds = @Word(seconds, ".", 1);

		}
		
		//set time
		var result:Date = new Date().UTC(year, month-1, day, hour, minute, seconds, ms);
		
		if (zone != null) {		//adjust time for zone difference
			
			var zoneHour = @TextToNumber(@Word(zone, ":", 1));
			var zoneMinute = @TextToNumber(@Word(zone, ":", 2));
			
			if (zoneHour>0 ) {			//convert to ms
				zoneHour = zoneHour * 60 * 60 * 1000;	
			}
			if (zoneMinute>0) {
				zoneHour = zoneMinute * 60 * 1000;
			}
			
			if (add) {
				result.setTime( result.getTime() + zoneHour + zoneMinute);
			} else {
				result.setTime( result.getTime() - zoneHour - zoneMinute);
			}
		}
		
		//dBar.debug("return date: " + result.toLocaleString());
		
	} catch (e) {
		dBar.error(e);
	}
	
	return result;

}

//returns a 'readable' size based on a bytesize
function getReadableSize(bytes) {
	var kbSize = bytes/1024;

	if (kbSize < 1024) {
		return @Text( kbSize, "F,0") + " KB";
	} else {
		var mbSize = kbSize / 1024
		return @Text( mbSize, "F,1") + " MB";
	}
}