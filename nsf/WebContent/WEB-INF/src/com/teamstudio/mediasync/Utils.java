package com.teamstudio.mediasync;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import lotus.domino.Base;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.Item;
import lotus.domino.NotesException;

import com.ibm.xsp.extlib.util.ExtLibUtil;

import eu.linqed.debugtoolbar.DebugToolbar;

public class Utils {
	
	//add an info message to the current facesContext
	public static void addInfoMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_INFO, message, message) );
	}
	//add an error message to the current facesContext
	public static void addErrorMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_ERROR, message, message) );
	}
	//add a warning  message to the current facesContext
	public static void addWarningMessage( String message) {
		FacesContext.getCurrentInstance().addMessage(null, 
			new FacesMessage( FacesMessage.SEVERITY_WARN, message, message) );
	}

	public static void showProgress( String message ) {
		
		Map<String, Object> sessionScope = ExtLibUtil.getSessionScope();
		sessionScope.put("progressMessage", message);
		
	}
	
	public static Date readDate( Document doc, String itemName) {
		
		Item itDate = null;
		Date result = null;
		
		try {
		
			itDate = doc.getFirstItem(itemName);
			
			if (itDate != null) {
				if (itDate.getDateTimeValue() != null) {
					result = itDate.getDateTimeValue().toJavaDate();
				}
			}
		
		} catch (NotesException e) {
			DebugToolbar.get().error(e);
		} finally {
			Utils.recycle(itDate);
		}
		
		return result;
		
	}
	
	//utility function to store a java.util.Date object in a document field (and recycle the DateTime object afterwards)
	public static void setDate( Document doc, String itemName, Date date) {
		
		if (date==null) {
			return;
		}
		
		DateTime dt = null;
		
		try {
			
			dt = doc.getParentDatabase().getParent().createDateTime(date);
			doc.replaceItemValue(itemName, dt);
			
		} catch (NotesException e) {
			DebugToolbar.get().error(e);
		} finally {
			Utils.recycle(dt);
		}
		
	}
	
	//recycle all objects in the arguments (in that order)
	public static void recycle(Object... dominoObjects) {
	    for (Object dominoObject : dominoObjects) {
	        if (null != dominoObject) {
	            if (dominoObject instanceof Base) {
	                try {
	                    ((Base)dominoObject).recycle();
	                } catch (NotesException ne) {
	                    
	                }
	            }
	        }
	    }
	}
	
	public static String getReadableSize( long bytes ) {
		
		if (bytes < 1024) {
			return bytes + " bytes";
		} else {
			double kbSize = bytes / 1024;

			
			if (kbSize < 1024) {
				//0 decimals
				return  Math.round(kbSize*1)/1 + " kB";
			} else {
				//1 decimal
				double mbSize = kbSize / 1024;
				NumberFormat df = new DecimalFormat("#0.0");
				return df.format(mbSize) + " MB";	
			}
		}
		
	}
	
	//returns an existing file folder in the OS's temp folder
	public static File getTempDir() {
		
		String tempDir = System.getProperty("java.io.tmpdir");
		String dirSep = System.getProperty("file.separator");
		
		//check for trailing slash: add if needed
		if ( !(tempDir.endsWith("/") || tempDir.endsWith("\\")) ) {
			tempDir = tempDir + dirSep;
		}
		
		//create temp folder inside the tempDir
		char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < 5; i++) {
		    char c = chars[random.nextInt(chars.length)];
		    sb.append(c);
		}
		
		tempDir += sb.toString()  + dirSep;
		
		File f = new File(tempDir);
		f.mkdir();
		
		return f;
	}
	
	//clean up a directory (and all contents)
	public static boolean removeDirectory(File target) {
		
		if (target == null) {
			return false;
		}
		if (!target.exists()) {
			return true;
		}
		if (!target.isDirectory()) {
			return false;
		}

		String[] list = target.list();

		// Some JVMs return null for File.list() when the directory is empty.
		if (list != null) {
			
			for (int i = 0; i < list.length; i++) {
				File entry = new File(target, list[i]);

				if (entry.isDirectory()) {
					if (!removeDirectory(entry))
						return false;
				} else {
					if (!entry.delete()) {
						return false;
					}
				}
			}
		}

		return target.delete();
	}
	
}
