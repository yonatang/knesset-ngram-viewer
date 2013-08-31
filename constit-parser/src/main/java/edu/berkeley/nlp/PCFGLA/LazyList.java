/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class LazyList {
	// klist
	List<HyperEdge> sortedBegining;
	// lhuang: berkeley in-house MAX-HEAP
	PriorityQueue<HyperEdge> fringe;
	int nSorted, nFringe;
	boolean[] grammarTags;
	// double bestScore;

	// lhuang
	static boolean forest = false;

	public LazyList(boolean[] tags) {
		// lhuang: k-best list (already extracted)
		sortedBegining = new ArrayList<HyperEdge>();
		// lhuang: the frontier
		fringe = new PriorityQueue<HyperEdge>();
		nSorted = 0;
		nFringe = 0;
		grammarTags = tags;
		// bestScore = Double.NEGATIVE_INFINITY;
	}

	public int sortedListSize() {
		return nSorted;
	}

	public void addToFringe(HyperEdge el) {
		// lhuang: score is the merit for ordering: the bigger the better
		//System.out.println("add to fringe:" +Numberer.getGlobalNumberer("tags").object(el.parentState));
		fringe.add(el, el.score);
		// lhuang: print hyperedge
		if (forest)
			System.out.print(el);
		nFringe++;
	}

	// public void addToSorted(HyperEdge el){
	// sortedBegining.add(el);
	// nSorted++;
	// }

	public HyperEdge getKbest(int k) {
		if (k > nSorted) {
			System.out.println("Don't have this element yet");
			return null;
			// } else if (k==nSorted&&(k==0||!fringe.hasNext())){
			// return null;
		} else if (k == nSorted) { // extract the next best
			expandNextBest();
		}
		if (k == nSorted)
			return null;
		//System.out.println("kbest:" + Numberer.getGlobalNumberer("tags").object(sortedBegining.get(k).parentState));
		return sortedBegining.get(k);
	}

	// lhuang: algorithm 3 code here?
	public void expandNextBest() {
		while (fringe.hasNext()) {
			HyperEdge edge = fringe.next();
			boolean isNew = true;

			// lhuang: duplicates
			for (HyperEdge alreadyIn : sortedBegining) {
				//if (alreadyIn.equals(edge)) {
				//	//System.out.println("already there");
				//	isNew = false;
				//	break;
				//}
				if (alreadyIn.differsInPOSatMost(edge, grammarTags)) {
					isNew = false;
					break;
				}
			}
			if (isNew) {
				sortedBegining.add(edge);
				//System.out.println("added edge:" + edge.str());
				nFringe--;
				nSorted++;
				break;
			}
		}
	}

}
