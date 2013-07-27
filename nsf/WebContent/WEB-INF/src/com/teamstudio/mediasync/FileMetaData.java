package com.teamstudio.mediasync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import com.ibm.commons.util.StringUtil;

import eu.linqed.debugtoolbar.DebugToolbar;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.View;

public class FileMetaData {
	
	private String unid;
	private String id;
	private String title;
	private String description;
	private Vector<String> tags;
	private Vector<String> folderTree;
	private String attachmentName;
	
	private String version;
	
	private Date updated;
	private String createdBy;
	
	private String fileId;
	private String fileUnid;
	
	public static final String TYPE_DOC = "docx";
	public static final String TYPE_PDF = "pdf";
	public static final String TYPE_PPT = "pptx";
	public static final String TYPE_SPREADSHEET = "xlsx";
	public static final String TYPE_IMAGE = "jpg";
	public static final String TYPE_VIDEO = "mov";
	public static final String TYPE_OTHER = "other";
	
	private static final String ITEM_FILES = "attachment";
	
	private Vector<String> year;
	private Vector<String> language;
	
	private String category;
	private Vector<String> categoriesSupported;
	
	private Vector<String> languagesSupported;
	
	private String configId;
	
	private static final String CATEGORY_DEFAULT = "Other";

	public FileMetaData(String id) {
		this.id = id;
		
		categoriesSupported = new Vector<String>();
		categoriesSupported.add("brochures");
		categoriesSupported.add("datasheets");
		categoriesSupported.add("presentations");
		categoriesSupported.add("videos");
		
		languagesSupported = new Vector<String>();
		languagesSupported.add("dutch");
		languagesSupported.add("english");
		languagesSupported.add("german");
		
		year = new Vector<String>();
		language = new Vector<String>();

	}
	
	public boolean isUpToDate( View vEntries, long entryUpdated ) {
		
		Document doc =null;
		
		try {
			
			doc = vEntries.getDocumentByKey( configId + "-" + id, true);

			if (null != doc) {	//existing document: check if it's up to date
				
				unid = doc.getUniversalID();
				
				Double u  = doc.getItemValueDouble("cnxUpdated");
				long docUpdated = u.longValue();
				
				if (docUpdated > 0 && docUpdated == entryUpdated ) {
					return true;
				}
				
			}
			
		} catch (NotesException e) {
			
			DebugToolbar.get().error(e);
			
		} finally {
			
			Utils.recycle(doc);
			
		}
		
		return false;
		
		
	}
	
	public void saveAndEmbed( View vEntries, File tempDir, String targetFileUrl, Long fileSize, HttpClient httpClient ) {
		
		Document docFileMetadata = null;
		Database dbTarget = null;
		
		try {
			
			DebugToolbar.get().debug("process file (id: " + id + ", title: " + title + ", updated: " + updated.toString() + ")" );

			dbTarget = vEntries.getParent();
			
			//retrieve metadata document
			if (unid != null) {
				docFileMetadata = dbTarget.getDocumentByUNID(unid);
			} else {
				docFileMetadata = vEntries.getDocumentByKey( configId + "-" + id, true);
			}
			
			//save all settings to metadata document
			if (docFileMetadata == null) {
				
				//metadata doesn't exist yet
				docFileMetadata = dbTarget.createDocument();
				docFileMetadata.replaceItemValue("form", "fFileMetadata");
				docFileMetadata.replaceItemValue("id", id);
				docFileMetadata.replaceItemValue("configId", configId);
				
			} else {		//existing metadata doc
				
				fileId = docFileMetadata.getItemValueString("fileId");		//id of the related file document
				
			}
			
			unid = docFileMetadata.getUniversalID();
			
			String ext = attachmentName.substring( attachmentName.lastIndexOf(".") + 1).toLowerCase();
			String type = "";
			
			ArrayList<String> extDoc = new ArrayList<String>(Arrays.asList( "doc,docx,odt,pages".split(",") ) );
			ArrayList<String> extSpreadsheet = new ArrayList<String>(Arrays.asList( "xls,xlsx,odf,numbers".split(",") ) );
			ArrayList<String> extPdf = new ArrayList<String>(Arrays.asList( "pdf".split(",") ) );
			ArrayList<String> extPresentation = new ArrayList<String>(Arrays.asList( "ppt,odp,otp,sdd,sxi,pot,potx,pps,ppsx,pptx,key,keynote".split(",") ) );
			ArrayList<String> extImage = new ArrayList<String>(Arrays.asList( "jpg,jpeg,png,bmp,gif".split(",") ) );
			ArrayList<String> extVideo = new ArrayList<String>(Arrays.asList( "mov,avi,mpeg,mp2,mp4,mkv".split(",") ) );
			
			if (extDoc.contains(ext) ) {
				type = TYPE_DOC;
			} else if (extSpreadsheet.contains(ext) ) {
				type = TYPE_SPREADSHEET;
			} else if (extPdf.contains(ext) ) {
				type = TYPE_PDF;
			} else if (extPresentation.contains(ext) ) {
				type = TYPE_PPT;
			} else if (extImage.contains(ext) ) {
				type = TYPE_IMAGE;
			} else if (extVideo.contains(ext) ) {
				type = TYPE_VIDEO;
			} else {
				type = TYPE_OTHER;
			}
			
			docFileMetadata.replaceItemValue("type", type);
			
			docFileMetadata.replaceItemValue("title", title);
			docFileMetadata.replaceItemValue("description", description);
			
			//store created & created by
			Utils.setDate(docFileMetadata, "dateCreated", updated);
			docFileMetadata.replaceItemValue("createdBy", createdBy);
						
			docFileMetadata.replaceItemValue("cnxUpdated", updated.getTime() );		//store in ms (long)
			
			//store id of the folder that contains this file
			docFileMetadata.replaceItemValue("folderId", folderTree.lastElement() );
			
			//add current entry id to foldertree
			Vector<String> foldTreeNew = new Vector<String>();
			foldTreeNew.addAll(folderTree);
			foldTreeNew.add(id);
			
			docFileMetadata.replaceItemValue("folderTree", foldTreeNew );
			
			//parse metadata/ tags
			parseTags(docFileMetadata);
			
			//store information on file
			docFileMetadata.replaceItemValue("attachmentName", attachmentName);
			docFileMetadata.replaceItemValue("attachmentSize", fileSize);
			docFileMetadata.replaceItemValue("attachmentUrlExt", targetFileUrl);		//external url
			
			
			DebugToolbar.get().debug("(metadata updated, check if file needs to be downloaded...)");
			
	 	 	//mark the file as a 'local' file
			docFileMetadata.replaceItemValue("isLocal", "true");
			
			//save and embed the related file in a 'file' document
			boolean savedFile = downloadAndEmbedFile(dbTarget, tempDir.getAbsolutePath() + File.separator, targetFileUrl, fileSize, httpClient);
			
			docFileMetadata.replaceItemValue("fileId", fileId);
			docFileMetadata.replaceItemValue("fileUnid", fileUnid);
			docFileMetadata.replaceItemValue("attachmentUrl", fileUnid + "/$file/" + java.net.URLEncoder.encode( attachmentName, "UTF-8") );
			
			docFileMetadata.save();
			
		} catch (Exception e) {
	
			DebugToolbar.get().error(e);

		} finally {
			
			Utils.recycle(docFileMetadata);
			
		}
		
	}
	
	//extract the required metadata from the tags
	private void parseTags( Document docFileMetadata ) throws NotesException {
	
		for (String tag : tags) {
			
			if ( tag.startsWith("20") ) {		//must be a year
				this.year.add(tag);
			} else if (categoriesSupported.contains( tag.toLowerCase() )) {
				category = properCase(tag);
			} else if (languagesSupported.contains( tag.toLowerCase() )) {
				language.add(properCase(tag));
			}

		}
		
		//use default category if not set
		if ( StringUtil.isEmpty(category) ) {
			category = CATEGORY_DEFAULT;
		}
		
		docFileMetadata.replaceItemValue("tags", tags);
		docFileMetadata.replaceItemValue("year", year);
		docFileMetadata.replaceItemValue("language", language);
		docFileMetadata.replaceItemValue("category", category);

		
	}
	
	private static String properCase(String in) {
		
		if (in.length()>0) {
			return in.substring(0, 1).toUpperCase() + in.substring(1);
		}
		
		return in;
	}
		

	/*
	 * Conditionally downloads a file from a target URL to the specified local attachment Path / attachment name and
	 * embeds it into the specified document (if changed)
	 */
	private boolean downloadAndEmbedFile( Database dbTarget, String filePath, String downloadUrl, long downloadSize, HttpClient httpClient ) {
		
		RichTextItem rtFiles = null;
		InputStream inputStream = null;
		
		HttpMethod method = null;
		
		Document docFile = null;
		View vFilesById = null;
		
		boolean saved = false;
		
		try {
			
			vFilesById = dbTarget.getView("vFilesById");
			
			// get the file document
			if ( StringUtil.isNotEmpty(fileId) ) {
				
				docFile = vFilesById.getDocumentByKey(fileId, true);
				
			}
			
			//temp: find by filename
			//if (docFile==null) {
				//DebugToolbar.get().debug("find by filename...");
				//docFile = vFilesById.getParent().getView("tmpbyfilename").getDocumentByKey(attachmentName, true);
			//}
			
			if (docFile == null ) {		//create new file document
				
				docFile = dbTarget.createDocument();
				docFile.replaceItemValue("form", "fFile");

				fileUnid = docFile.getUniversalID();
				fileId = "f" + fileUnid.toLowerCase();
				
				docFile.replaceItemValue("id", fileId);
				
			} else {
				
				fileId = docFile.getItemValueString("id");
				fileUnid = docFile.getUniversalID();
				
			}
			
			//download file from Connections without logging (so the file's metadata doesn't get updated)
			downloadUrl += "?logDownload=false";

			method = new GetMethod(downloadUrl);
			method.setDoAuthentication(true);
			
			DebugToolbar.get().debug("check if " + attachmentName + " needs to be downloaded (from " + downloadUrl + ")", "download file");
		
			//if the file was downloaded previously, we have a ETag and/or last modified date
			//in that case, the ETag/ modified is added to the download 'GET' request to
			//only download the file if it has changed
			String eTag = docFile.getItemValueString("cnxETag");
			if ( StringUtil.isNotEmpty( eTag ) ) {
				DebugToolbar.get().debug("added etag: " + eTag);
				method.setRequestHeader("If-None-Match", eTag);
			}
			
			String modified = docFile.getItemValueString("cnxModifiedDat");
			if ( StringUtil.isNotEmpty( modified ) ) {
				method.setRequestHeader("If-Modified-Since", modified);
			}
			
			httpClient.executeMethod(method);
			
			int statusCode = method.getStatusCode();
			
			inputStream = method.getResponseBodyAsStream();
			
			if (statusCode == 200 ) {		//ok
				
				Utils.showProgress("Downloading " + attachmentName + " (" + Utils.getReadableSize(downloadSize) + ")" );
				
				DebugToolbar.get().debug("Downloading " + attachmentName + " (" + Utils.getReadableSize(downloadSize) + ")" );
				DebugToolbar.get().debug("(from " + downloadUrl + ")");
				
				DebugToolbar.get().debug("Response OK, store file in: " + filePath + attachmentName, "download file");

				byte[] byteArray = new byte[1024];
				int bytesRead;
				
				FileOutputStream fos = new FileOutputStream( filePath + attachmentName);
				      
				while ((bytesRead = inputStream.read( byteArray )) != -1) {
					fos.write( byteArray, 0, bytesRead);
				}
				
				fos.flush();
				fos.close();
				inputStream.close();
				
				//download ok: embed the file
		     	if (docFile.hasItem(ITEM_FILES) ) {
		 	 		//remove existing attachments
		 	 		//(handles the case that the file was updated in connections)
		 	 		docFile.removeItem(ITEM_FILES);
		 	 	}
		 	 
		 	 	//embed the downloaded file in the document
		 	 	rtFiles = docFile.createRichTextItem(ITEM_FILES);
		 	 	rtFiles.embedObject(lotus.domino.local.EmbeddedObject.EMBED_ATTACHMENT, "", filePath + attachmentName, null);
		 	 	
		 	 	//store/update the etag/ modified date
		 	 	Header eTagHeader = method.getResponseHeader("ETag");
		 	 	
		 	 	if (eTagHeader != null) {
			 	 	DebugToolbar.get().debug("- eTag in response: " + eTagHeader.getValue());
			 	 	docFile.replaceItemValue( "cnxETag", eTagHeader.getValue());
		 	 	}
		 	 	
		 	 	Header modifiedHeader = method.getResponseHeader("Last-Modified");
		 	 	
		 	 	if (modifiedHeader != null) {
		 	 		docFile.replaceItemValue( "cnxModified", modifiedHeader.getValue());
		 	 		DebugToolbar.get().debug("- last-modified in response: " + modifiedHeader.getValue() );
		 	 	}
		 	 	
		 	 	//TODO: using the version information we should be able to determine if we actually
		 	 	//need to download a file... 
		 	 	docFile.replaceItemValue("cnxVersion", version);
		 	 	
		 	 	docFile.save();
		 	 	saved = true;
	 	 	
		 	 	//remove the downloaded file from the file system
		 	 	File f = new java.io.File( filePath + attachmentName);
		 	 	f.delete();
				
			} else if (statusCode == 304) {			//not modified
				
				DebugToolbar.get().debug("file not modified", "download file");
				
			} else if (statusCode == 401 ) {		//not authorized
				
				DebugToolbar.get().error("error while downloading file: user not authorized", "download file");
				
			} else if (statusCode == 403 ) {		//forbidden
				
				DebugToolbar.get().error("error while downloading file: forbidden", "download file");
				
			} else if (statusCode == 404 ) {		//not found
				
				DebugToolbar.get().error("error while downloading file: file not found", "download file");
				
			} else {		//unknown response
				
				DebugToolbar.get().error("error while downloading file, status code: " + statusCode, "download file");

			}

		} catch (Exception e) {
	 
			DebugToolbar.get().error(e);
			
		} finally {
			
			if (method != null) { method.releaseConnection(); }
			if (inputStream != null) { 
				
				try {
					inputStream.close();
				} catch (IOException e) { } 
			
			}
			
			Utils.recycle(rtFiles, docFile, vFilesById);
			
		}
		
		return saved;
		
	}
	
	public void setId( String to) {
		this.id = to;
	}
	public void setConfigId( String to) {
		this.configId = to;
	}
	public void setTitle(String title) {
		attachmentName = title;
		this.title = title.substring(0, title.lastIndexOf(".") ) ;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	public void setTags(Vector<String> tags) {
		this.tags = tags;
	}
	public void setUpdated(Date updated) {
		this.updated = updated;
	}
	public void setCreatedBy(String to) {
		this.createdBy = to;
	}
	public void setFolderTree(Vector<String> folderTree) {
		this.folderTree = folderTree;
	}
	
	public void setVersion( String to) {
		this.version = to;
	}
	
	
}
