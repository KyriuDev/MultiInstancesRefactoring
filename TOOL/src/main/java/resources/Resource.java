package resources;

public class Resource
{
	private final String name;


	public Resource(final String name)
	{
		this.name = name;
	}

	public String name()
	{
		return this.name;
	}

	//Override

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Resource))
		{
			return false;
		}

		return this.name.equals(((Resource) o).name);
	}

	@Override
	public int hashCode()
	{
		int hash = 7;

		for (int i = 0; i < this.name.length(); i++)
		{
			hash = hash * 31 + this.name.charAt(i);
		}

		return hash;
	}
}
