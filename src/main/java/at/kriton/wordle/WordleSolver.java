package at.kriton.wordle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A solver for the game wordle.
 * 
 * The solver keeps book of all words that are in the solution space and
 * determines a best guess via the method {@link #nextGuess()}. The answer (the
 * {@link Match}) of a guess reduces the solution space by removing all words
 * that do not correspond to the match (via
 * {@link #applyAnswer(String, Match)}).
 * 
 * @see <a href="http://wordle.at">wordle.at</a>
 */
public class WordleSolver {
	private final int length;
	private final int answersCount;

	private final List<String> dictionary;
	private final SortedSet<String> solutions;

	/**
	 * A match represents how well a certain word <em>w</em> matches the solution <em>s</em>.
	 * Every letter of <em>w</em> can either be at the correct position (indicated by the character {@link #correctPosition g}),
	 * be contained in <em>s</em> but at another position (indicated by the character {@link #wrongPosition y}),
	 * or not be part of <em>s</em> at all (indicated by the character {@link #notContained x}).
	 * 
	 * If the solution is `LEBEN` and the word is `GERNE` the match would be represented by:
	 * <pre>
	 *  L E B E N
	 *  ---------
	 *  G E R N E
	 *  =========
	 *  x g x y y
	 * </pre>
	 */
	public class Match {
		public static final char notContained = 'x';
		public static final char wrongPosition = 'y';
		public static final char correctPosition = 'g';

		private final Pattern validRepresentation = Pattern.compile("[" + notContained + wrongPosition + correctPosition + "]{" + length + "}");

		/**
		 * The internalRepresentation corresponds to a stringRepresentation of a match as follows.
		 * If the stringRepresentation is for instance `xyygx` the internal Representation is 42:
		 * <pre>
		 *  |    x    |    y    |    y    |    g    |    x    |   --> xyygx
		 *  ===================================================
		 *  | 3^4 * 0 | 3^3 * 1 | 3^2 * 1 | 3^1 * 2 | 3^0 * 0 |   --> 42
		 * </pre>
		 */
		private final int internalRepresentation;

		private Match(String representation) {
			if (!isValidRepresentation(representation)) {
				throw new IllegalArgumentException("representation must be a string of 'x', 'y' or 'g' s and must have a valid length");
			}
			this.internalRepresentation = toInternalRepresentation(representation);
		}

		private Match(int internalRepresentation) {
			this.internalRepresentation = internalRepresentation;
		}

		@Override
		public String toString() {
			return fromInternalRepresentation(internalRepresentation);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Match && ((Match) o).internalRepresentation == this.internalRepresentation;
		}

		@Override
		public int hashCode() {
			return internalRepresentation;
		}

		private int toInternalRepresentation(String representation) {
			assert isValidRepresentation(representation);

			int internalRepresentation = 0;
			for (char c : representation.toCharArray()) {
				internalRepresentation *= 3;
				if (c == correctPosition) {
					internalRepresentation += 2;
				} else if (c == wrongPosition) {
					internalRepresentation += 1;
				}
			}
			return internalRepresentation;
		}

		private String fromInternalRepresentation(int internalRepresentation) {
			assert internalRepresentation < Math.pow(internalRepresentation, length);

			StringBuilder representation = new StringBuilder();

			for (int i = 0; i < length; i++) {
				if (internalRepresentation % 3 == 0) {
					representation.append(notContained);
				} else if (internalRepresentation % 3 == 1) {
					representation.append(wrongPosition);
				} else {
					representation.append(correctPosition);
				}
				internalRepresentation /= 3;
			}
			return representation.reverse().toString();
		}

		private boolean isValidRepresentation(String s) {
			return validRepresentation.matcher(s).matches();
		}
	}

	public WordleSolver(Collection<String> dictionary) {
		assert dictionaryValid(dictionary)
				: "all words must be a composition of the same number of uppercase letters (A-Z)";

		this.length = dictionary.stream().findFirst().map(String::length).orElse(0);

		this.answersCount = (int) Math.pow(3, length);

		this.dictionary = new ArrayList<>(dictionary);
		this.solutions = new TreeSet<>(dictionary);
	}

	/**
	 * Finds the best guess of this solver for the current solution space
	 */
	public String nextGuess() {
		int minimax = Integer.MAX_VALUE;
		String nextGuess = "";
		boolean nextGuessContainedInSolutions = false;

		/* Every word factorizes the solution space by the match with this word.
		 * (see #getFactorizations(String))
		 * E.g.: if the solution space is [ LEBEN, RESTE, KAMEL, BEBEN ] and the
		 * word is TRUEB the factorization would be:
		 * 
		 * xxxgy: [LEBEN, BEBEN]
		 * xyxyx: [RESTE]
		 * xxxgx: [KAMEL]
		 * 
		 * The word TRUEB would get a score of 2 for the current solution space
		 * (= the size of the biggest group [LEBEN, BEBEN]).
		 * 
		 * The next guess is a word with the lowest score.
		 */
		for (String word : dictionary) {
			int[] factorization = new int[answersCount];
			for (String possibleSolution : solutions) {
				factorization[match(word, possibleSolution)]++;
			}

			int max = 0;
			for (int f : factorization) {
				max = Math.max(f, max);
			}
			if (max < minimax) {
				minimax = max;
				nextGuess = word;
			} else if (!nextGuessContainedInSolutions && max == minimax) {
				// if there is already a word with this score,
				// prefer words that could be solutions
				if (nextGuessContainedInSolutions = solutions.contains(word)) {
					nextGuess = word;
				}
			}
		}
		return nextGuess;
	}

	public Map<Match, SortedSet<String>> getFactorizations(String guess) {
		Map<Match, SortedSet<String>> factorization = new LinkedHashMap<>();
		for (int i = 0; i < answersCount; i++) {
			factorization.put(new Match(i), new TreeSet<>());
		}

		for (String s : solutions) {
			factorization.compute(new Match(match(guess, s)), (key, value) -> {
				value.add(s);
				return value;
			});
		}

		return factorization;
	}

	/**
	 * Updates the solution space of this solver by applying the guess and its match.
	 */
	public void applyAnswer(String guess, Match answer) {
		solutions.removeIf(word -> match(guess, word) != answer.internalRepresentation);
	}

	/**
	 * @return the current solution space of this solver
	 */
	public Set<String> getSolutions() {
		return solutions;
	}

	public Match toMatch(String matchRepresentation) {
		return new Match(matchRepresentation);
	}

	/**
	 * Finds the representation of a {@link Match} between a `guess` and a `word`.
	 */
	private int match(String guess, String word) {
		char[] wordAsChar = word.toCharArray();
		char[] guessAsChar = guess.toCharArray();

		boolean[] matchAt = new boolean[length];
		boolean[] containedAt = new boolean[length];

		int resultMatching = 0;
		for (int i = 0; i < length; i++) {
			resultMatching *= 3;
			if (containedAt[i] = matchAt[i] = guessAsChar[i] == wordAsChar[i]) {
				resultMatching += 2;
			}
		}

		int resultContaining = 0;
		for (int i = 0; i < length; i++) {
			resultContaining *= 3;
			if (matchAt[i]) {
				continue;
			}
			for (int j = 0; j < length; j++) {
				if (containedAt[j]) {
					continue;
				}
				if (containedAt[j] = guessAsChar[i] == wordAsChar[j]) {
					resultContaining += 1;
					break;
				}
			}
		}

		return resultMatching + resultContaining;
	}

	private static boolean dictionaryValid(Collection<String> dictionary) {
		int length = dictionary.stream().findFirst().map(String::length).orElse(0);
		Pattern validWord = Pattern.compile("[A-Z]{" + length + "}");
		return dictionary.stream().allMatch(word -> validWord.matcher(word).matches());
	}
}
