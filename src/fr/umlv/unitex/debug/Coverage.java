/*
 * Unitex
 *
 * Copyright (C) 2001-2011 Université Paris-Est Marne-la-Vallée <unitex@univ-mlv.fr>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA.
 *
 */

package fr.umlv.unitex.debug;

import java.util.ArrayList;

import fr.umlv.unitex.io.GraphIO;


public class Coverage {

	int[][] infos;
	
	public Coverage(DebugInfos d) {
		infos=new int[d.graphNames.size()][];
		for (int i=0;i<infos.length;i++) {
			GraphIO gio=d.getGraphIO(i+1);
			/* +1 because the store in cell #0 the total
			 * number of matched box for the graph */
			int n=(gio==null)?0:gio.boxes.size()+1;
			infos[i]=new int[n];
		}
	}

	
	/**
	 * This method counts the times each box of each graph is used in a match.
	 */
	public static Coverage computeCoverageInfos(DebugInfos d) {
		Coverage c=new Coverage(d);
		ArrayList<DebugDetails> details=new ArrayList<DebugDetails>();
		for (int i=0;i<d.lines.size();i++) {
			d.getMatchDetails(i,details);
			if (details.size()==0) {
				/* If there is a problem, we return */
				return null;
			}
			for (DebugDetails item:details) {
				c.infos[item.graph-1][item.box+1]++;
				c.infos[item.graph-1][0]++;
			}
		}
		return c;
	}


	public int getGraphCounter(int graph) {
		return infos[graph-1][0];
	}
	

	public int getBoxCounter(int graph,int box) {
		return infos[graph-1][box+1];
	}

}