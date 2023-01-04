package at.kriton.wordle;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.regex.Pattern;

public class Application {
	private static final Pattern FIVE_UPPERCASE_LETTERS = Pattern.compile("[A-Z]{5}");

	public static void main(String[] args) throws Exception {
		List<String> words = readDictionary();

		WordleSolver solver = new WordleSolver(words);

		try (Scanner scanner = new Scanner(System.in)) {
			while (solver.getSolutions().size() > 1) {
				String bestGuess = solver.nextGuess();
				System.out.format("Best guess: %s%n", bestGuess);

				String guess = readNextGuess(scanner, bestGuess);
				// printFactorizations(solver, guess);

				applyAnswer(solver, scanner, guess);
			}

			System.out.println(
					solver.getSolutions().stream().findAny()
						.map(solution -> String.format("Solution is: %s%n", solution))
						.orElse("No solution found"));
		}
	}

	private static void applyAnswer(WordleSolver solver, Scanner scanner, String guess) {
		boolean answerRead = false;
		while (!answerRead) {
			try {
				System.out.print("Answer: ");
				solver.applyAnswer(guess, solver.toMatch(scanner.nextLine()));
				answerRead = true;
				System.out.format("Solutions:%n\t%s%n", solver.getSolutions());
			} catch (IllegalArgumentException ex) {
				System.out.format(
						"Only strings of length 5 consisting of the following characters are valid:%n" +
						"'x' ... charactor not contained in solution%n" +
						"'y' ... charactor contained in solution but not at this position%n" +
						"'g' ... character at this position in solution%n");
			}
		}
	}

	private static String readNextGuess(Scanner scanner, String bestGuess) {
		String guess;
		boolean guessValid = true;

		do {
			guess = bestGuess;
			System.out.format("Use this guess (leave empty for '%s') or else: ", bestGuess);

			String guessInput = scanner.nextLine();
			if (!isBlank(guessInput)) {
				guess = guessInput;
			}

			guessValid = FIVE_UPPERCASE_LETTERS.matcher(guess).matches();
			if (!guessValid) {
				System.out.format("'%s' ist not a valid word%n", guess);
			}
		} while (!guessValid);

		return guess;
	}

	private static List<String> readDictionary() throws IOException {
		List<String> words;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				Application.class.getClassLoader().getResourceAsStream("5-letter-words-de.txt")))) {
			words = reader.lines().collect(toList());
		}
		return words;
	}

	private static void printFactorizations(WordleSolver solver, String guess) {
		Map<WordleSolver.Match, SortedSet<String>> factorizations = solver.getFactorizations(guess);
		factorizations.entrySet().removeIf(e -> e.getValue().isEmpty());
		String formatedFactorizations = factorizations.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(joining(lineSeparator()));
		System.out.format("Factorizations for %s:%n%s%n", guess, formatedFactorizations);
	}

	private static boolean isBlank(String s) {
		return Objects.toString(s, "").trim().isEmpty();
	}

}
