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
package org.ossnoize.git.fastimport;

import java.io.IOException;
import java.io.OutputStream;

public class MarkID implements DataRef {

	/**
	 * Mark ID 0 is reserved so start at 1
	 */
	private static long MarkID = 1;
	public static MarkID getNextMarkID() {
		if(MarkID <= 0) {
			throw new Error("Mark has wrapped around");
		}
		return new MarkID(MarkID++);
	}

	private String Id;
	
	private MarkID(long id) {
		Id = ":" + id;
	}
	
	@Override
	public void writeTo(OutputStream out) throws IOException {
		out.write(Id.getBytes());
	}
	
	@Override
	public String toString() {
		return Id;
	}

	@Override
	public String getId() {
		return Id;
	}

}
