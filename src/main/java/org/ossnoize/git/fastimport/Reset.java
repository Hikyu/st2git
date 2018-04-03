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

public class Reset implements FastImportObject {

    private final String ref;
    private final DataRef committish;

    public Reset(String ref, DataRef committish) {
        this.ref = ref;
        this.committish = committish;
    }

	@Override
	public void writeTo(OutputStream out) throws IOException {
		out.write("reset ".getBytes("UTF-8"));
        out.write(ref.getBytes("UTF-8"));
        out.write('\n');
        if(null != committish) {
            out.write("from ".getBytes("UTF-8"));
            committish.writeTo(out);
            out.write('\n');
        }
		out.flush();
	}

}
