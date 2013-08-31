package yg.blatt;

import java.util.List;
import java.util.Set;

public interface TypesLexicon {

   Set<Short> allPossibleTags();
	List<Short> possibleNumericTagsForWord(String word);

	List<Short> possibleNumericTagsForWord(String word, int loc);
}
