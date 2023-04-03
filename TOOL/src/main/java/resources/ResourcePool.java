package resources;

import java.util.*;

public class ResourcePool
{
	private final Map<Resource, Integer> pool;

	public ResourcePool()
	{
		this.pool = new HashMap<>();
	}

	public void addResource(final Resource resource,
							final int nbReplicas)
	{
		if (this.pool.containsKey(resource))
		{
			throw new IllegalStateException("Resource pool already contains resource " + resource.name());
		}

		this.pool.put(resource, nbReplicas);
	}

	public int getUsageOf(final Resource resource)
	{
		return pool.getOrDefault(resource, 0);
	}

	public Set<Resource> resources()
	{
		return this.pool.keySet();
	}

	/**
	 * This function compares two resource pools between them.
	 * A resource pool is said to be:
	 * 	- included in another resource pool if: each resource belonging to the second pool has a higher
	 * 		number of replicas than the first pool
	 *  - equal to another resource pool if: each resource belonging to the second pool has the exact
	 *  	same number of replicas than the first pool (note that if some resources belonging to the
	 *  	first pool do not belong to the second pool, pools will still be considered equal)
	 *  - not included in another resource pool if: at least one resource belonging to the second pool has
	 *  	a smaller number of replicas in the first pool
	 *
	 * @param resourcePool the second pool
	 * @return -1 if the first pool is included in the second, 0 if they are equal, 1 if the first pool is not
	 * 			included in the second pool
	 */
	public int compareTo(final ResourcePool resourcePool)
	{
		boolean equals = true;
		boolean included = true;

		for (Resource resource : resourcePool.resources())
		{
			final int secondReplicas = resourcePool.getUsageOf(resource);
			final int firstReplicas = this.getUsageOf(resource);

			if (firstReplicas > secondReplicas)
			{
				included = false;
				equals = false;
				break;
			}
			else if (firstReplicas < secondReplicas)
			{
				equals = false;
			}
		}

		return equals ? 0 : included ? -1 : 1;
	}

	public boolean isNotIncludedIn(final ResourcePool resourcePool)
	{
		return this.compareTo(resourcePool) == 1;
	}

	//Override

	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder("\n\nThis resource pool contains the following resources:");

		for (Resource resource : this.resources())
		{
			final int usage = this.pool.get(resource);

			builder.append("\n    - \"").append(resource.name()).append("\" needs ").append(usage).append(" replicas.");
		}

		return builder.toString();
	}
}
