package resources;

import other.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ResourcePoolParser
{
	private ResourcePoolParser()
	{

	}

	public static ResourcePool parse(final File resourceFile) throws FileNotFoundException
	{
		if (!resourceFile.isFile())
		{
			throw new IllegalStateException("Path |" + resourceFile.getPath() + "| does not correspond to a valid file.");
		}

		final ResourcePool resourcePool = new ResourcePool();
		final Scanner scanner = new Scanner(resourceFile);

		while (scanner.hasNext())
		{
			final String line = scanner.nextLine();
			parseLine(line, resourcePool);
		}

		return resourcePool;
	}

	//Private methods

	private static void parseLine(final String line,
								  final ResourcePool resourcePool)
	{
		if (!line.startsWith("//"))
		{
			if (line.charAt(0) != '-')
			{
				throw new IllegalStateException("Line |" + line + "| is badly formed.");
			}

			final StringBuilder nbReplicasBuilder = new StringBuilder();
			final StringBuilder resourceNameBuilder = new StringBuilder();
			boolean buildingNbReplicas = true;
			boolean buildingName = false;

			for (char c : line.substring(1).toCharArray())
			{
				if (Utils.isAnInt(c))
				{
					if (buildingNbReplicas)
					{
						nbReplicasBuilder.append(c);
					}
					else if (buildingName)
					{
						resourceNameBuilder.append(c);
					}
				}
				else
				{
					if (c != ' ')
					{
						buildingNbReplicas = false;
						buildingName = true;
					}

					resourceNameBuilder.append(c);
				}
			}

			final String nbReplicasStr = nbReplicasBuilder.toString().trim();

			if (!Utils.isAnInt(nbReplicasStr))
			{
				throw new IllegalStateException("Number of replicas should be an integer value. Got |" + nbReplicasStr + "|.");
			}

			final String nameStr = resourceNameBuilder.toString().trim();

			if (Utils.isAnInt(nameStr.charAt(0))
				|| nameStr.contains(" "))
			{
				throw new IllegalStateException("Resource name should not be an integer value nor start with an integer nor contain spaces. Got |" + nameStr + "|.");
			}

			resourcePool.addResource(new Resource(nameStr), Integer.parseInt(nbReplicasStr));
		}
	}
}
