/*
 * Functions to read a list of Connections communities
 */

function startReadCommunities() {

	var vwCommunitiesById:NotesView = null;
	var urlCommunities:String = "/communities/service/atom/communities/my";
	
	try {

		dBar.debug("start", "communities sync");
		
		showProgress("Started communities sync");
		
		session.setConvertMime(false);		//do not convert mime to rich text
		
		vwCommunitiesById = database.getView("vCommunitiesById");
		
		//read (new) communities from Connections
		showProgress("Retrieving communities");
		processCommunitiesFeed( applicationScope.get("connectionsServerUrl") + urlCommunities, vwCommunitiesById);
		
		dBar.debug("Synchronization completed", "communities sync" );

		showProgress("Communities update complete");
		
		vwCommunitiesById.refresh();
		
		dBar.debug("finished", "communities sync");
		
	} catch (e) {
		dBar.error(e, "communities sync");
	} finally {
		recycleObjects( vwCommunitiesById);
		
		session.setConvertMime(true);
	}
	
}

//send a request to the specified url ("targetUrl") to retrieve an (Atom) feed
//and parses that feed to retrieve the community entries in it
function processCommunitiesFeed( targetUrl:String, vwItemsById:NotesView) {
	
	var connection:java.net.HttpURLConnection = null;
	
	try {
		
		var pageNr = requestScope.get("pageNr") || 0;
		pageNr++;
		requestScope.put("pageNr", pageNr);

		if (pageNr>10) {		//proces max 10 pages in this application
			return;
		}
		
		dBar.debug("processing communities feed at " + targetUrl);
		dBar.debug("processing page " + pageNr);
		
		//get the communities feed connection
		connection = getAuthenticatedConnection( targetUrl );
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		connection.setRequestMethod("GET");
	    connection.connect();
    
		var statusCode:int = connection.getResponseCode();
        
        if (statusCode == 200) {		//OK
        	
        	dBar.debug("page " + pageNr + " of feed retrieved Ok (status code 200)");
        	
        	showProgress("Communities feed retrieved OK, reading page " + pageNr);
    		
        	//storeData("popular feed", connection.getInputStream() );
        	
        	//parse the retrieved feed
        	var parsedXml:org.w3c.dom.Document = XMLParser.getXMLDocument( connection.getInputStream() );
        	var feedNode:DOMElement = parsedXml.getDocumentElement();
        	
        	//get total number of communities in feed
        	var totalResults = XMLParser.getNodeValue(feedNode, "opensearch:totalResults", null);
        	dBar.debug("total communities in this feed (all pages): " + totalResults);
        	
        	var entryNodeList:DOMNodeList = feedNode.getElementsByTagName("entry");
        	dBar.debug("processing " + entryNodeList.getLength() + " communities from page " + pageNr);
        	
        	//loop through all communities (entry nodes) in the feed
        	for (var i=0; i<entryNodeList.getLength(); i++) {

        		var entryNode = entryNodeList.item(i);
        		
                if(entryNode.getNodeType() == DOMNode.ELEMENT_NODE){
                	processCommunityEntry( entryNode, vwItemsById);
                }
        		
        	}
        	
        	//check if the feed contains a "next page" link and process it
        	var nextPageLink:String = getNextPageLink(feedNode, null);

        	if (!isEmpty(nextPageLink)) {
        		
        		dBar.debug("found the next page link: " + nextPageLink);
        		
        		connection.disconnect();
        		
        		//recursive call to process the next page of the feed
        		processCommunitiesFeed( nextPageLink, vwItemsById);
        		
        	} else {
        		
        		showProgress("All pages read");

        	}
        	
        } else {
        	
        	dBar.error("feed could not be retrieved, http status code: " + statusCode, "processCommunitiesFeed");
        }
	} catch (e) {
		dBar.error(e, "processCommunitiesFeed");
	} finally {
		
		if (connection != null) { connection.disconnect(); }
	}
}

/*
 * processes a community 'entry' in an Atom feed, returns the ID from the Atom feed
 * 
 * entryNode : node in the Atom feed
 * vwCommunitiesById: view used to find existing local community documents
 */

function processCommunityEntry( entryNode, vwCommunitiesById:NotesView) :String {
	
	var docCommunity:NotesDocument = null;
	var id:String = null;

	try {
	
		//retrieve id from feed
		id = XMLParser.getNodeValue(entryNode, "snx:communityUuid", null);
		var published:String 	= XMLParser.getNodeValue(entryNode, "published", null)
		var updated:String 		= XMLParser.getNodeValue(entryNode, "updated", null)
	  	
	  	//check if a local community document exists
	  	docCommunity = vwCommunitiesById.getDocumentByKey(id, true);
		
	  	if (docCommunity != null) {
	  		
	  		//check if current user is listed as a member of this community
	  		var	members = docCommunity.getItemValue("members")
	  		
	  		if ( @IsNotMember(sessionScope.get("currentUser"), members ) ) {
	  			members.add( sessionScope.get("currentUser"));
	  			docCommunity.replaceItemValue("members", members);
	  			docCommunity.save();
	  		}
	  		
	  		//to check wether this community was updated we can simply compare the
			//'updated' value (as string) with the stored value

	  		if ( docCommunity.getItemValueString("updated").equals(updated) ) {
	  			return id;
	  		}
	  	}
	
	 	//retrieve information from the entry
		var title:String		= XMLParser.getNodeValue(entryNode, "title", null);
	    var description:String 	= XMLParser.getNodeValue(entryNode, "summary", null);
	  	var href:String			= getLinkNodeValue(entryNode, "alternate");
	  	
	  	var publishedDat:Date	= parseRFC3339Date(published);
		var updatedDat:Date 	= parseRFC3339Date(updated);
		
		if (docCommunity == null ) {			//create new community document
		    	
			docCommunity = database.createDocument();
			docCommunity.replaceItemValue("form", "fCommunity" );
			docCommunity.replaceItemValue("id", id );
			docCommunity.replaceItemValue("members", sessionScope.get("currentUser") );
	
		}
	    
	    docCommunity.replaceItemValue("title", title);
		docCommunity.replaceItemValue("link", href);
	    docCommunity.replaceItemValue("description", description);
	    docCommunity.replaceItemValue("published", published	);		//published date as rfc3339 string
	    docCommunity.replaceItemValue("updated", updated	);		//published date as rfc3339 string
	    
	    if (updatedDat) {
	    	docCommunity.replaceItemValue("updatedDat", session.createDateTime(updatedDat));
	    } else {
	    	docCommunity.replaceItemValue("updatedDat", "");
	    }
	    if (publishedDat) {
	  	  	docCommunity.replaceItemValue("publishedDat", session.createDateTime(publishedDat));
	    } else {
	    	docCommunity.replaceItemValue("publishedDat", null);	
	    }
	    
	    docCommunity.save();
	    
	    dBar.debug("community " + title + " created/updated");
	
	} catch (e) {
		dBar.error(e, "processCommunityEntry");
	} finally {
		
		recycleObjects( docCommunity);
		
	}
	return id;
	
}