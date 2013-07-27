/*
 * Creates a sorted object representing all menu options. The object
 * isn't hierarchical, but grouped by parent option.
 */

function getMenuOptions() {

	//create the menu based on the folders in the application
	try {
		
		if ( !sessionScope.containsKey("menu") ) {
		
			dBar.debug("process menu");
			
			var vwMenu = database.getView("unpMenuItems");
			var nav = vwMenu.createViewNav();
			
			var unsortedOptions = [];
			
			//read all menu items
			var veMenuItem = nav.getFirst();
			while (null != veMenuItem) {
			
				var colValues = veMenuItem.getColumnValues();
				
				var pos = colValues.get(2);
				var unid = colValues.get(3);
				
				var item = {
					"label" : colValues.get(0),
					"folderId" : colValues.get(1),
					"id" : unid,
					"entryId" : colValues.get(4)
				};
				
				if (pos ==2) {
					item.isMain = true;
				} else if (pos == 3) {
					item.isMain = false;
					item.submenu = "sub";
				} else if (pos==4) {
					item.isMain = false;
					item.submenu = "sub-sub";
					item.ajaxloadid = "results";
					item.ajaxtargetid = "results";
					item.page = "/UnpMediaSet.xsp?documentId=" + unid;
					
				}

				unsortedOptions.push(item);
				
				var veTmp = nav.getNext();
				veMenuItem.recycle();
				veMenuItem = veTmp;
			}
			
			
			//sort and group the unsorted menuoptions
			var menuOptions = [];
			
			var numOptions = unsortedOptions.length;
			
			for (var i=0; i<numOptions; i++) {
				
				var item = unsortedOptions[i];
				
				if (item.isMain) {
					menuOptions.push( item );
					
					//get all sub options
					var sub = getOptionsByFolder( unsortedOptions, numOptions, item.entryId );
					for (var j=0; j<sub.length; j++) {
						menuOptions.push( sub[j] );
						
						//get all subsub options
						var subSub = getOptionsByFolder( unsortedOptions, numOptions, sub[j].entryId );
						
						for (var k=0; k<subSub.length; k++) {
							menuOptions.push( subSub[k] );	
						}
						
					}
				}
				
			}
		
			sessionScope.menu = menuOptions;
			return menuOptions;
		} else {
			
			return sessionScope.menu;		
		}
		
	} catch (e) {
		dBar.error(e);
	}
}

/*
 * Create an hierarchical object representing the entire menu.
 * 
 * This function first loops through the unpMenuItems view to retrieve all folders. It then creates
 * a hierarchical object containing all options and its children. Max. 3 levels are supported.
 * 
 * This function looks a lot like the one above, but until it is decided that the navigator can work
 * with this new structure, I'll just keep both.
  */

function getMenuOptionsHierchical() {
	

	
	try {
		
		//if ( !sessionScope.containsKey("menu") ) {
		
			dBar.debug("process menu");
			
			var vwMenu = database.getView("unpMenuItems");
			var nav = vwMenu.createViewNav();
			
			var mainMenuOptions = [];
			var subMenuOptions = [];
			var page = "/UnpMediaSet.xsp";
			
			//read all menu items
			var veMenuItem = nav.getFirst();
			while (null != veMenuItem) {
			
				var colValues = veMenuItem.getColumnValues();
				
				var pos = colValues.get(2);
				var unid = colValues.get(3);
				
				var item = {
					"label" : colValues.get(0),
					"folderId" : colValues.get(1),
					"id" : unid,
					"entryId" : colValues.get(4),
					"subMenu" : [],
					"hasSubMenu" : false
				};
				
				if (pos ==2) {
					
					mainMenuOptions.push(item);
					
				} else if (pos == 3) {
					
					item.submenu = "sub";
					subMenuOptions.push(item);
					
				} else if (pos==4) {
					
					item.submenu = "sub-sub";
					item.ajaxloadid = "results";
					item.ajaxtargetid = "results";
					item.page = page + "?documentId=" + unid;
					
					subMenuOptions.push(item);
				}
				
				var veTmp = nav.getNext();
				veMenuItem.recycle();
				veMenuItem = veTmp;
			}
			
			//group the unsorted menuoptions
			var menuOptions = [];
			
			var numSubMenuOptions = subMenuOptions.length;
			
			for (var i=0; i<mainMenuOptions.length; i++) {
				
				var item = mainMenuOptions[i];
				
				var subMenu = [];
				
				//get sub options
				var sub = getOptionsByFolder( subMenuOptions, numSubMenuOptions, item.entryId );
				
				//get sub-sub options
				for (var j=0; j<sub.length; j++) {
					
					var current = sub[j];
					current.subMenu = getOptionsByFolder( subMenuOptions, numSubMenuOptions, current.entryId );
					current.hasSubMenu = (current.subMenu.length > 0);
					
					subMenu.push( current);
				}
				
				
				item.subMenu = subMenu;
				item.hasSubMenu = (subMenu.length > 0);
				
				menuOptions.push( item );
				
			}
		
			sessionScope.menu = menuOptions;
			return menuOptions;
		//} else {
			
		//	return sessionScope.menu;		
		//}
		
	} catch (e) {
		dBar.error(e);
	}
}

function getOptionsByFolder( unsortedOptions, numOptions, folderId) {
	var res = [];
	
	for (var i=0; i<numOptions; i++) {
		if (unsortedOptions[i].folderId == folderId) {
			res.push(unsortedOptions[i]);
		}
	}
	return res;
}
