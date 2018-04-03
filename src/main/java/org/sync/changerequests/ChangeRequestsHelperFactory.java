/*****************************************************************************
    This file is part of Git-Starteam.

    Git-Starteam is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Git-Starteam is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Git-Starteam.  If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package org.sync.changerequests;

import com.starbase.starteam.View;

public class ChangeRequestsHelperFactory {

	private static ChangeRequestsHelperFactory instance = null;
	
	private ChangeRequestsHelper helper;
	
	private ChangeRequestsHelperFactory(){
		
	}
	
	public static ChangeRequestsHelperFactory getFactory(){
		if(instance == null){
			instance = new ChangeRequestsHelperFactory();
		}
		
		return instance;
	}
	
	
	public ChangeRequestsHelper createHelper(View viewOfOperation) {
		if(helper == null){
			helper = new ChangeRequestsHelper(viewOfOperation);
		}
		
		return helper;
	}
}
