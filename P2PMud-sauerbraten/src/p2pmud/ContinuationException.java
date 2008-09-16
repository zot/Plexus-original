package p2pmud;

import java.util.List;

public class ContinuationException extends Exception {
	public List<?> results;

	public ContinuationException(String message, List<?> results) {
		super(message);
		this.results = results;
	}
	public ContinuationException(Exception cause, List<?> results) {
		super(cause);
		this.results = results;
	}
}
