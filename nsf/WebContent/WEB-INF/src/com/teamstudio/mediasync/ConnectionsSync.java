package com.teamstudio.mediasync;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.Response.ResponseType;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;

import eu.linqed.debugtoolbar.DebugToolbar;

public class ConnectionsSync {

	String server;
	String protocol = "https://";
	String communityId;
	
	String bindUserName;
	String bindPassword;
	
	String rootFolder;
	
	private static String LIBRARY_WIDGETDEF = "Library";

	String TERM_FOLDER = "folder";
	String TERM_DOCUMENT = "document";
	
	private File tempDir;

	private static Abdera abdera = null;
	private static HttpClient httpClient = null;
	
	private ArrayList<String> processedIds;
	
	private transient View vEntries = null;
	private transient DebugToolbar dBar;
	
	private int numFilesProcessed;
	private int numFoldersProcessed;
	
	String configId;
	
	private static int DEBUG_MAX_FILES_DOWNLOAD = 0;

	public ConnectionsSync() {
		
	}

	/*
	 * Steps in synchronizing with a library:
	 * - in a specific community:
	 * - get the Widgets feed ( {{server}}/communities/service/atom/community/widgets?communityUuid={{communityId}} )
	 * - find the library widget (generator = Social%20ECM%20Documents%3A%20P8 or component id= 27E2DDE1-52E8-4508-B2C7-90AD2B2FD4CA;3731B0F6-5F22-4510-8FC1-5D612C4B8A5C
	 * - get the widgetProperty rootFeed
	 * - that feed is the start of the library (and contains folders and files)
	 */
	public void sync() {

		Utils.showProgress("Start Connections synchronization");
		
		getDBar().debug("start");

		processedIds = new ArrayList<String>();		//List of ids of all entries (files + folders) that were synced. We use this to (in the end) cleanup items
		
		try {
			
			tempDir = Utils.getTempDir();
			
			Database dbCurrent = ExtLibUtil.getCurrentDatabase();
			
			//read configuration
			View vwConfig = dbCurrent.getView("vConfig");
			
			//get the first (and only) active configuration
			lotus.domino.Document docConfig = vwConfig.getFirstDocument();
			
			boolean configLoaded = false;
			
			while (null != docConfig && !configLoaded) {
				
				if (docConfig.getItemValueString("isActive").equals("true") ) {
					
					server = docConfig.getItemValueString("connectionsServerUrl");
					bindUserName = docConfig.getItemValueString("connectionsBindUsername");
					bindPassword = docConfig.getItemValueString("connectionsBindPassword");
					communityId = docConfig.getItemValueString("connectionsCommunityId");
					
					
					configId = docConfig.getItemValueString("id");
					
					rootFolder = docConfig.getItemValueString("libraryRootFolder");
				
					configLoaded = true;
					
					getDBar().debug("Settings:");
					getDBar().debug("- server: " + server);
					getDBar().debug("- user: " + bindUserName);
					getDBar().debug("- community id: " + communityId);
					getDBar().debug("- config id: " + configId);
					
				}
				
				lotus.domino.Document docTemp = vwConfig.getNextDocument(docConfig);
				docConfig.recycle();
				docConfig = docTemp;
			}
			
			Utils.recycle(vwConfig);
			
			if (StringUtil.isEmpty(server)) {
				
				Utils.addWarningMessage("Synchronization not performed: no active configuration found");
				return;
				
			}
			
			if (rootFolder.length()==0) {
				getDBar().debug("process all folders");
			} else {
				getDBar().debug("process root folder " + rootFolder + " only");
			}
			
			vEntries = dbCurrent.getView("vEntriesById");
			
			Abdera abdera = getAbderaInstance();
			
			AbderaClient client = new AbderaClient(abdera);
			
			//enable preemptive authentication (always send auth. header - don't wait until server requests it)
			client.usePreemptiveAuthentication(true);

			// Default trust manager provider registered for port 443
			//AbderaClient.registerTrustManager();

			// add credentials
			getDBar().debug("adding basic credentials for " + protocol + server + ", username: " + bindUserName);
			client.addCredentials(protocol + server, null, "basic", new UsernamePasswordCredentials(bindUserName, bindPassword));
			
			//String communityWidgetUrl = protocol + server + "/communities/service/atom/community/widgets?communityUuid=" + communityId;

			//first get a list of the widgets in a specific community
			ArrayList<String> libraryFeeds = getLibraryComponentIds(client, communityId);
			
			if (libraryFeeds.size()==0) {
				Utils.showProgress("No library feeds found in community");
				return;
			}
			
 			
			Vector<String> folderTree = new Vector<String>();
			folderTree.add("root");
			
			//process all libraries in this environment
			for (String libraryFeed : libraryFeeds ) {
				
				if (rootFolder.length()>0) {
					
					String url = libraryFeed;
					
					//get the library feed and find the root folder we need to process
					getDBar().debug("process library feed: " + url);
					Utils.showProgress("Process library");

					if (url.indexOf(protocol + server) != 0) {
						url = protocol + server + url;
					}
					
					ClientResponse resp = client.get(url);
					
					if (resp.getType() == ResponseType.SUCCESS) {
						
						Document<Feed> doc = resp.getDocument();
						Feed feed = doc.getRoot();
						List<Entry> entries = feed.getEntries();
						
						for (Entry entry : entries) {
							
							String entryTitle = entry.getTitle();
							String entryId = getLocalId(entry.getId().toString() );

							getDBar().debug( "> title: " + entryTitle + ", id: " + entryId);
							Utils.showProgress("Process library entry " + entryTitle );
							processedIds.add(entryId);
							
							List<Category> categories = entry.getCategories("tag:ibm.com,2006:td/type");
							
							if (categories.size()>0) {
								Category firstCat = categories.get(0);

								if (firstCat.getTerm().equals(TERM_FOLDER) && rootFolder.equals( entryTitle) ) {		//folder
				
									processLibraryFeed(client, entry.getContentSrc().getPath(), folderTree );
								}	
							}

						}
						
						getDBar().debug("release connection");
						resp.release();

					} else {
						// there was an error
						
						getDBar().error("Error occurred: " + resp.getType() + "and: " + resp.getStatusText() + " and:" + resp.getUri() + " and:"
										+ resp.getServerDate());
						
						getDBar().debug("release connection");
						resp.release();

					}
					
				} else {
				
					processLibraryFeed(client, libraryFeed, folderTree);
					
				}
				
			}
			
			//cleanup resources used by abdera client			
			client.teardown();
			
			//remove files deleted in Connections
			removeDeletedFiles();
			
			//clean up: remove temporary folder on server
			Utils.removeDirectory(tempDir);

			getDBar().debug("Sync completed (folders: " + numFoldersProcessed + ", files: " + numFilesProcessed + ")");
			
			Utils.showProgress("Connections synchronization completed");

		} catch (Exception e) {
			getDBar().error(e);

		} finally {
			
			Utils.recycle(vEntries);
		}

	}
	
	private void removeDeletedFiles() {
		
		try {
			getDBar().debug("Start removing deleted files");
			Utils.showProgress("Remove deleted entries");
			
			vEntries.refresh();
			vEntries.setAutoUpdate(false);
			
			View vFilesById = vEntries.getParent().getView("vFilesById");
			vFilesById.refresh();
			vFilesById.setAutoUpdate(false);
			
			ViewEntryCollection vec = vEntries.getAllEntries();
			
			//getDBar().debug("processed ids contains " + processedIds.size() + " entries");
			
			ViewEntry ve = vec.getFirstEntry();
			while (null != ve) {

				String id = (String) ve.getColumnValues().get(0);
				
				//String[] ids = id.split("-");
				
				int pos = id.indexOf("-");
				String entryConfigId = id.substring(0, pos);
				String entryId = id.substring( pos+1);
				//getDBar().debug("find with (" + entryConfigId + ") and (" + entryId + ")");
				
				//remove if this file belongs to the current library and isn't in any (processed) feed anymore
				//if ( ids[0].equals(configId) && !processedIds.contains( ids[1] )) {
				if ( entryConfigId.equals(configId) && !processedIds.contains( entryId )) {
					
					//getDBar().debug("check " + id);
					getDBar().debug("- entry " + entryId + " not in feed anymore: delete");
					
					//this entry is not in the feed anymore: remove it
					lotus.domino.Document doc = ve.getDocument();
					
					String type = ( doc.getItemValueString("form").equals("fFolder") ? "folder" : "file");
					String title = doc.getItemValueString("title");
					
					getDBar().info("- removing deleted " + type + ", title: " + title + ", id: " + id);
					
					if (type.equals("file") ) {
						
						//find related file document
						String fileId = doc.getItemValueString("fileId");
						
						lotus.domino.Document docFile = vFilesById.getDocumentByKey(fileId, true);
						
						if (null != docFile) {
							
							docFile.remove(true);
							Utils.recycle(docFile);
							getDBar().info("- file document NOT removed");
							
						}
						
						
					}
					
					doc.remove(true);
					Utils.recycle(doc);
					
				}

				//TODO: implement for folders (and remove all files in that folder then)
					
				ViewEntry veTmp = vec.getNextEntry(ve);
				ve.recycle();
				ve = veTmp;
			}
		} catch (NotesException e) {
			
			getDBar().error(e);
		}
		

	}
	
	/*
	 * Retrieve a (Connections) community's widget feed and retrieves the URL's to feeds of all libraries
	 * contained in the community (snx:widgetDefId = Library)
	 */
	private ArrayList<String> getLibraryComponentIds(AbderaClient client, String communityId) {
		
		Utils.showProgress("Retrieving library feed");
		getDBar().debug("get community widgets feed...");
		
		ArrayList<String> libraryFeeds = new ArrayList<String>();
		
		String communityWidgetUrl = protocol + server + "/communities/service/atom/community/widgets?communityUuid=" + communityId;
		
		getDBar().debug("Community widget URL: " + communityWidgetUrl);
		
		ClientResponse resp = client.get(communityWidgetUrl );
		
		if (resp.getType() == ResponseType.SUCCESS) {
			
			Document<Feed> doc = resp.getDocument();
			Feed feed = doc.getRoot();
			
			List<Entry> entries = feed.getEntries();
			
			Utils.showProgress("Processing " + entries.size() + " entries");
			
			
			
			//loop through all widgets and find the Library widget
			for (Entry entry : feed.getEntries()) {
				
				List<Element> elements = entry.getElements();
				
				String rootFeed = "";
				String widgetDefId = "";
				
	            for (Element el : elements) {
	            	
	            	String localName = el.getQName().getLocalPart();
	            	
	            	//get the root feed
	            	if( localName.equals("widgetProperty") ) {
	            		
	            		String key = el.getAttributeValue("key");
	            		
	            		if (key.equals("rootFeed") ) {
	            			rootFeed = el.getText();
	            		}
	            		
	            	} else if ( localName.equals("widgetDefId") ) {
	            		
	            		//get the widgetDefId
	            		widgetDefId = el.getText();
	            		//getDBar().debug("widgetDefId: " + widgetDefId);
	            		
	            		
	            	}

	            }
	            
	            if (widgetDefId.equalsIgnoreCase( LIBRARY_WIDGETDEF ) ) {
	            	
        			getDBar().debug("- library feed found at " + rootFeed);
        			libraryFeeds.add(rootFeed);
        			
        		}
				
			}
			
			getDBar().debug("release connection");
			resp.release();
			
		} else if (resp.getType() == ResponseType.SERVER_ERROR) {
			
			getDBar().error("server error, status: " + resp.getStatus() + ", text: " + resp.getStatusText() );
			
			getDBar().debug("release connection");
			resp.release();
						
		} else {
			
			getDBar().error("response: " + resp.getType());
			getDBar().error("status: " + resp.getStatus() + ", text: " + resp.getStatusText() );
			
			getDBar().debug("release connection");
			resp.release();
			
		}
		
		getDBar().debug("finished processing library feed for community " + communityId);
		getDBar().debug("(found " + libraryFeeds.size() + " library feeds)");
		
		return libraryFeeds;
		
	}

	private void processLibraryFeed(AbderaClient client, String url, Vector<String> folderTree ) {

		getDBar().debug("process library feed: " + url);
		Utils.showProgress("Process library");

		if (url.indexOf(protocol + server) != 0) {
			url = protocol + server + url;
		}
		
		//we also want to have the tags
		if (url.indexOf("includeTags") == -1 ) {
			url += "?includeTags=true";
		}
		
		ClientResponse resp = client.get(url);
		
		if (resp.getType() == ResponseType.SUCCESS) {

			Document<Feed> doc = resp.getDocument();
			Feed feed = doc.getRoot();
			List<Entry> entries = feed.getEntries();
			
			Utils.showProgress("Processing " + entries.size() + " entries");
			
			for (Entry entry : entries) {
				
				String entryTitle = entry.getTitle();
				String entryId = getLocalId(entry.getId().toString() );

				getDBar().debug( "> title: " + entryTitle + ", id: " + entryId);
				Utils.showProgress("Process library entry " + entryTitle );
				
				processedIds.add(entryId);

				List<Category> categories = entry.getCategories("tag:ibm.com,2006:td/type");
				
				if (categories.size()>0) {
					Category firstCat = categories.get(0);

					if (firstCat.getTerm().equals(TERM_FOLDER)) {		//folder
	
						// create a folder document in the current database for this folder
						createFolder(entry, entryId, folderTree );
						
						// process the entries of the folder						
						Vector<String> tmp = new Vector<String>();
						tmp.addAll(folderTree);
						tmp.add( entryId );
						
						processLibraryFeed(client, entry.getContentSrc().getPath(), tmp );
					
					} else if (firstCat.getTerm().equals(TERM_DOCUMENT)) {		//file
								
						createFile(entry, entryId, folderTree );
	
					}
				}
				
				if (DEBUG_MAX_FILES_DOWNLOAD > 0 && numFilesProcessed >= DEBUG_MAX_FILES_DOWNLOAD) {
					getDBar().warn("abort further processing: 1 file downloaded");
					return;
				}

			}
			
			getDBar().debug("release connection");
			resp.release();

		} else {
			// there was an error
			getDBar().error("error while processing library feed: " + resp.getType() + "and: " + resp.getStatusText() + " and:" + resp.getUri() + " and:"
							+ resp.getServerDate());
			
			getDBar().debug("release connection");
			resp.release();

		}

	}
	
	//create a local folder document based on an entry in the Atom feed
	private void createFolder(Entry entry, String entryId, Vector<String> folderTree) {
		
		numFoldersProcessed++;
		
		lotus.domino.Document docFolder = null;
		
		try {
			
			Date entryUpdated = entry.getUpdated();
			
			docFolder = vEntries.getDocumentByKey(configId + "-" + entryId, true);
			
			if (docFolder == null) {
				
				docFolder = vEntries.getParent().createDocument();
				docFolder.replaceItemValue("form", "fFolder");
				docFolder.replaceItemValue("id", entryId);
				docFolder.replaceItemValue("configId", configId);
				
				getDBar().debug("create folder (id=" + entryId + ", title=" + entry.getTitle() + ", updated: " + entryUpdated + ")");
				
			} else {
				
				//check if folder is up to date
				Double u  = docFolder.getItemValueDouble("cnxUpdated");
				long docUpdated = u.longValue();
					
				if (docUpdated > 0 && docUpdated == entryUpdated.getTime() ) {
					getDBar().debug("folder is up to date");
					return;
				}
				
				getDBar().debug("update folder (id: " + entryId + ", title: " + entry.getTitle() + ", updated: " + entryUpdated + ")");
				
			}
			
			docFolder.replaceItemValue("folderId", folderTree.lastElement() );
			
			docFolder.replaceItemValue("cnxUpdated", entryUpdated.getTime() );		//store ms (long)
			
			//add current entry id to foldertree
			Vector<String> tmp = new Vector<String>();
			tmp.addAll(folderTree);
			tmp.add(entryId);
			
			docFolder.replaceItemValue("folderTree", tmp );
			docFolder.replaceItemValue("title", entry.getTitle());
			docFolder.replaceItemValue("description", entry.getSummary());
			docFolder.replaceItemValue("tags", getTags(entry));
			
			docFolder.save();
			
		} catch (NotesException e) {
			getDBar().error(e);
		} finally {
			
			Utils.recycle(docFolder);
		}
	
		

	}
	
	//create a local file document based on an entry in the Atom feed
	private void createFile(Entry entry, String entryId, Vector<String> folderTree) {
		
		numFilesProcessed++;
		
		try {
			
			String title = entry.getTitle();
			
			Date entryUpdated = entry.getUpdated();
			
			FileMetaData fmd = new FileMetaData(entryId);
			fmd.setConfigId(configId);
			
			if ( fmd.isUpToDate(vEntries, entryUpdated.getTime() )) {
				
				//TODO: change of tags (from view) is not reflected in doc properties!
				getDBar().info("file " + title + " is up to date...");
				return; 
				
			}
			
			fmd.setTitle(title);
			fmd.setDescription( entry.getSummary() );
			fmd.setUpdated( entryUpdated );
			fmd.setTags( getTags(entry) );
			fmd.setCreatedBy( entry.getAuthor().getName() );
			
			fmd.setVersion( entry.getAttributeValue("td:versionLabel") );
			
			fmd.setFolderTree(folderTree);
			
			//download the file: get the <link> where rel="edit-media"
			String fileLink = entry.getEditMediaLink().getHref().toString();
			Long fileSize = entry.getEnclosureLink().getLength();
			
			fmd.saveAndEmbed( vEntries, tempDir, fileLink, fileSize, getHttpClient() );
				
		} catch (Exception e) {
			getDBar().error(e);
		}

	}
	
	//retrieve the local part of an entry id (withouth the namespaces)
	private String getLocalId( String id) {
		String localId = java.net.URLDecoder.decode(id);
		return localId.substring( localId.indexOf("{")+1, localId.length()-1);
	}
	
	//read the 'tags' for an entry
	private Vector<String> getTags( Entry entry ) {
		
		List<Category> categories = entry.getCategories();
		Vector<String> tags = new Vector<String>();
		for (Category cat : categories) {
			if ( !cat.getLabel().equals("document") ) {
				tags.add(cat.getLabel());
			}
		}
		
		return tags;
	}
	
	
	private synchronized Abdera getAbderaInstance() {

		if (abdera == null) {
			abdera = new Abdera();
		}

		return abdera;
	}
	
	private synchronized DebugToolbar getDBar() {
		
		if (dBar == null) {
			dBar = DebugToolbar.get();
		}
		return dBar;
		
	}
	
	private synchronized HttpClient getHttpClient() {

		if (httpClient == null) {
			
			httpClient = new HttpClient();
			
			httpClient.getParams().setAuthenticationPreemptive(true);
			
			Credentials defaultcreds = new UsernamePasswordCredentials(bindUserName, bindPassword);
			httpClient.getState().setCredentials(new AuthScope(server, 443, AuthScope.ANY_REALM), defaultcreds);
			
		}

		return httpClient;
	}

}
