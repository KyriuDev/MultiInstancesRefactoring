package other;

public class Pair<T,U>
{
	private T first;
	private U second;

	public Pair(T first,
				U second)
	{
		this.first = first;
		this.second = second;
	}

	public T first()
	{
		return this.first;
	}

	public U second()
	{
		return this.second;
	}

	public void setFirst(T first)
	{
		this.first = first;
	}

	public void setSecond(U second)
	{
		this.second = second;
	}

	public Pair<T,U> copy()
	{
		return new Pair<>(this.first, this.second);
	}
}