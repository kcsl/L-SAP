package com.kcsl.lsap.core;

import java.util.Comparator;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class NodeSourceCorrespondenceSorter implements Comparator<Node> {

	@Override
	public int compare(Node n1, Node n2) {
		SourceCorrespondence sc1 = (SourceCorrespondence) n1.getAttr(XCSG.sourceCorrespondence);
		SourceCorrespondence sc2 = (SourceCorrespondence) n2.getAttr(XCSG.sourceCorrespondence);
		String path1 = sc1.sourceFile.getLocation().toOSString();
		String path2 = sc2.sourceFile.getLocation().toOSString();
		int fileComparison = path1.compareTo(path2);
		if(fileComparison == 0){
			int startComparison = Integer.compare(sc1.offset, sc2.offset);
			if(startComparison == 0){
				int lengthComparison = Integer.compare(sc1.length, sc2.length);
				return lengthComparison;
			} else {
				return startComparison;
			}
		} else {
			return fileComparison;
		}
	}

}
