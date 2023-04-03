package refactoring.exceptions;

public class BadDependencyException extends Exception
{
	public BadDependencyException()
	{
		super();
	}

	public BadDependencyException(final String message)
	{
		super(message);
	}
}
