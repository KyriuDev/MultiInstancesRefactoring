package resources;

import other.Utils;
import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.*;
import other.Pair;

import java.util.*;

public class Optimizer
{
	private static final double THRESHOLD = 0.01d; //Stop when node has less than 1% chance of execution
	private final int interval;
	private final Graph graph;
	private final GlobalResourceSet globalResourceSet;
	private final ResourcePool minimalResourcePool;
	private final int iat;
	private HashMap<Integer, ResourcePool> resourcesPerInstantOfTime;

	public Optimizer(final ArrayList<BpmnProcessObject> objects,
					 final Graph graph,
					 final GlobalResourceSet globalResourceSet,
					 final int iat)
	{
		this.interval = 1;
		this.graph = graph;
		this.globalResourceSet = globalResourceSet;
		this.iat = iat;
		this.minimalResourcePool = this.computeMinimalPool(objects);
		this.resourcesPerInstantOfTime = new HashMap<>();

		if (this.iat == 0)
		{
			//In this case, our computation makes no sense because all processes execute at the
			//exact same time. To compute the optimal in this case, we need the number of processes
			//executing. Then, we would multiply it by the optimal pool for 1 process.
			throw new IllegalStateException("For an IAT = 0, we would need the total number of processes executing to perform the computation.");
		}
	}

	public int computeProcessExecutionTime()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = this.computeTasksPerInstantOfTime();
		return Utils.max(tasksPerInstantOfTime.keySet()) + 1;
	}

	/**
	 * This function computes the average percentage of use of each resource according
	 * to the lifecycle of the process.
	 *
	 * @param resourcesPerInstantOfTime a map containing an execution time as key and the resources used at this time as value
	 * @param optimalPool the optimal pool of resources computed for the given process
	 * @return a map containing a resource as key, and its average percentage of use as value
	 */
	public HashMap<Resource, Double> computeGlobalResourceUsageOverTime(final HashMap<Integer, ResourcePool> resourcesPerInstantOfTime,
																		final ResourcePool optimalPool)
	{
		final HashMap<Resource, Double> resourceUsage = new HashMap<>();

		ResourcePool currentPool;
		int interval = 0;
		int nbIterations = 0;

		//We iterate over each resource at each given instant of time
		while ((currentPool = resourcesPerInstantOfTime.get(interval)) != null)
		{
			interval += this.interval;
			nbIterations++;

			//We iterate over all the resources at the given time
			for (Resource resource : optimalPool.resources())
			{
				final int currentValue = currentPool.getUsageOf(resource);
				final int maxValue = optimalPool.getUsageOf(resource);
				final double percentageOfUse = (double) currentValue / (double) maxValue;

				if (resourceUsage.containsKey(resource))
				{
					resourceUsage.put(resource, resourceUsage.get(resource) + percentageOfUse);
				}
				else
				{
					resourceUsage.put(resource, percentageOfUse);
				}
			}
		}

		//At this point, we have a resource associated to a sum of percentages of use,
		//so we divide this value by the number of intervals considered (here nbIterations)
		for (Resource resource : this.globalResourceSet.resourcesSet())
		{
			resourceUsage.put(resource, (resourceUsage.get(resource) / (double) nbIterations) * 100);
		}

		return resourceUsage;
	}

	/**
	 * This function computes the maximal pool of resources needed to execute 1 instance
	 * of the current process without waiting for any given resource.
	 * Here, maximal means that above this number of resources, no change on the AET will
	 * be observed.
	 *
	 * @return a resource pool containing the maximal number of instances of each resource
	 */
	public ResourcePool computeMaximalPoolForOneProcess()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = this.computeTasksPerInstantOfTime();
		final HashMap<Integer, ResourcePool> resourcesPerInstantOfTime = this.computeResourcePoolsPerInstantOfTime(tasksPerInstantOfTime, false);

		return this.computeOptimalPool(resourcesPerInstantOfTime);
	}

	/**
	 * This function computes the optimal (~ minimal) pool of resources needed to execute 1 instance
	 * of the current process without waiting for any given resource.
	 *
	 * @return a resource pool containing the optimal number of instances of each resource
	 */
	public ResourcePool computeOptimalPoolForOneProcess()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = this.computeTasksPerInstantOfTime();
		this.resourcesPerInstantOfTime = this.computeResourcePoolsPerInstantOfTime(tasksPerInstantOfTime, true);

		return this.computeOptimalPool(this.resourcesPerInstantOfTime);
	}

	public Double computeAverageUsageOf(final Resource resource)
	{
		int usage = 0;
		final int executionTime = Utils.max(this.resourcesPerInstantOfTime.keySet()) + 1;

		for (int time : this.resourcesPerInstantOfTime.keySet())
		{
			final ResourcePool resourcePool = this.resourcesPerInstantOfTime.get(time);
			usage += resourcePool.getUsageOf(resource);
		}

		return ((double) usage) / ((double) executionTime);
	}

	/**
	 * This function computes the maximal pool of resources needed to execute n instances
	 * of the current process without waiting for any given resource.
	 * Here, maximal means that above this number of resources, no change on the AET will
	 * be observed.
	 *
	 * @return a resource pool containing the maximal number of instances of each resource
	 */
	public ResourcePool computeMaximalPoolForNProcesses()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = this.computeTasksPerInstantOfTime();
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> multiProcTasksPerInstantOfTime = this.computeMultiProcTasksPerInstantOfTime(tasksPerInstantOfTime);
		final HashMap<Integer, ResourcePool> resourcesPerInstantOfTime = this.computeResourcePoolsPerInstantOfTime(multiProcTasksPerInstantOfTime, false);

		return this.computeOptimalPool(resourcesPerInstantOfTime);
	}

	/**
	 * This function computes the optimal (~ minimal) pool of resources needed to execute n instances of
	 * the current process without waiting for any given resource.
	 *
	 * @return a resource pool containing the optimal number of instances of each resource
	 */
	public ResourcePool computeOptimalPoolForNProcesses()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = this.computeTasksPerInstantOfTime();
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> multiProcTasksPerInstantOfTime = this.computeMultiProcTasksPerInstantOfTime(tasksPerInstantOfTime);
		this.resourcesPerInstantOfTime = this.computeResourcePoolsPerInstantOfTime(multiProcTasksPerInstantOfTime, true);

		return this.computeOptimalPool(this.resourcesPerInstantOfTime);
	}

	public HashMap<Resource, Integer> computeProcessAbsorbance(final ResourcePool realPool)
	{
		final HashMap<Resource, Integer> absorbanceMap = new HashMap<>();
		final HashMap<Resource, Integer> exceedingUsage = new HashMap<>();
		final HashMap<Resource, Integer> recedingUsage = new HashMap<>();

		for (int time : this.resourcesPerInstantOfTime.keySet())
		{
			final ResourcePool currentPool = this.resourcesPerInstantOfTime.get(time);

			for (Resource resource : globalResourceSet.resourcesSet())
			{
				final int currentUsage = currentPool.getUsageOf(resource);
				final int maxUsage = realPool.getUsageOf(resource);

				if (currentUsage > maxUsage)
				{
					//We are overusing the resource
					final int overuse = currentUsage - maxUsage;
					final int totalOveruse = exceedingUsage.getOrDefault(resource, 0) + overuse;
					exceedingUsage.put(resource, totalOveruse);
				}
				else if (currentUsage < maxUsage)
				{
					//We are underusing the resource
					final int underuse = maxUsage - currentUsage;
					final int totalUnderuse = recedingUsage.getOrDefault(resource, 0) + underuse;
					recedingUsage.put(resource, totalUnderuse);
				}
			}
		}

		for (Resource resource : exceedingUsage.keySet())
		{
			final int totalOveruse = exceedingUsage.get(resource);
			final int totalUnderuse = recedingUsage.getOrDefault(resource, 1);
			final int absorbance = (int) (((double) totalOveruse / ((double) totalUnderuse * (double) 10)) * 100);
			absorbanceMap.put(resource, absorbance);
		}

		return absorbanceMap;
	}

	//Private methods

	/**
	 * This function uses the execution flow of the given BPMN process to compute
	 * the set of tasks executed at any given time t of the execution flow for
	 * any number of instances of the process such that there exists at least
	 * one process instance which competes for resources against
	 * (ceil(get/iat) - 1) * 2 other instances
	 *
	 * @param tasksPerInstantOfTime the tasks executing at any instant t of the execution
	 * @return a map containing execution times as keys, and the tasks executing at this given time as values
	 */
	private HashMap<Integer, ArrayList<Pair<Task, Double>>> computeMultiProcTasksPerInstantOfTime(final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime)
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> multiProcTasksPerInstantOfTime = new HashMap<>();
		final int get = this.computeGET(tasksPerInstantOfTime);

		for (int interval = 0; interval < get; interval += this.interval)
		{
			final ArrayList<Pair<Task, Double>> multiProcTasksAtCurrentTime = new ArrayList<>();
			final int firstProcessConsidered = (int) Math.ceil(((float) get / (float) this.iat)) - 1;

			for (int procIndex = firstProcessConsidered; procIndex >= -firstProcessConsidered; procIndex--)
			{
				final int execTimeCurrentProc = procIndex * this.iat + interval;

				if (execTimeCurrentProc >= 0
					&& execTimeCurrentProc < get)
				{
					multiProcTasksAtCurrentTime.addAll(tasksPerInstantOfTime.get(execTimeCurrentProc));
				}
			}

			multiProcTasksPerInstantOfTime.put(interval, multiProcTasksAtCurrentTime);
		}

		this.correctTasks(multiProcTasksPerInstantOfTime, true);

		return multiProcTasksPerInstantOfTime;
	}

	/**
	 * This function iterates over the tasks per instant of time to compute the resources
	 * needed per instant of time.
	 *
	 * @param tasksPerInstantOfTime the tasks executing at every instant of time
	 * @param considerProbabilities whether we should or not consider the probabilities of execution of the tasks
	 * @return a map with execution time as key, and resource pool as value
	 */
	private HashMap<Integer, ResourcePool> computeResourcePoolsPerInstantOfTime(final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime,
																				final boolean considerProbabilities)
	{
		final HashMap<Integer, ResourcePool> resourcePoolsPerInstantOfTime = new HashMap<>();

		for (Integer executionTime : tasksPerInstantOfTime.keySet())
		{
			final ArrayList<Pair<Task, Double>> tasksExecutingTimeAtCurrentTime = tasksPerInstantOfTime.get(executionTime);
			final ArrayList<Pair<Resource, Double>> resourceUsageAtCurrentTime = new ArrayList<>();

			for (Pair<Task, Double> taskWithProbability : tasksExecutingTimeAtCurrentTime)
			{
				final Task currentTask = taskWithProbability.first();
				final Double currentProbability = taskWithProbability.second();

				for (Resource resource : currentTask.resourceUsage().resources())
				{
					final double currentResourceUsage = (considerProbabilities ? currentProbability : 1d) * currentTask.resourceUsage().getUsageOf(resource);
					final Pair<Resource, Double> resourceWithInstances = this.getPairFromResource(resourceUsageAtCurrentTime, resource);

					if (resourceWithInstances == null)
					{
						final Pair<Resource, Double> newResource = new Pair<>(resource, currentResourceUsage);
						resourceUsageAtCurrentTime.add(newResource);
					}
					else
					{
						resourceWithInstances.setSecond(currentResourceUsage + resourceWithInstances.second());
					}
				}
			}

			final ResourcePool resourcePool = new ResourcePool();

			for (Pair<Resource, Double> currentResource : resourceUsageAtCurrentTime)
			{
				resourcePool.addResource(currentResource.first(), (int) Math.ceil(currentResource.second()));
			}

			resourcePoolsPerInstantOfTime.put(executionTime, resourcePool);
		}

		//this.printResourcePoolsPerInstantOfTime(resourcePoolsPerInstantOfTime);

		return resourcePoolsPerInstantOfTime;
	}

	/**
	 * This function traverses the BPMN process to compute the list (here a hashmap) of
	 * tasks executing at any given time t (discrete time --> interval is the PGCD of
	 * the execution time of all the tasks) along with their probability of execution
	 *
	 * @return a hashmap with the execution time as key and the list of tasks executing at this time as value
	 */
	private HashMap<Integer, ArrayList<Pair<Task, Double>>> computeTasksPerInstantOfTime()
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime = new HashMap<>();
		this.computeTasksPerInstantOfTimeRec(this.graph.initialNode(), this.graph.initialNode().bpmnObject().probability(), 0, tasksPerInstantOfTime);
		this.correctTasks(tasksPerInstantOfTime, false);
		return tasksPerInstantOfTime;
	}

	/**
	 * This function recursively traverses the BPMN process to compute the list of tasks
	 * executing at any given time t (discrete time --> interval is the PGCD of
	 * the execution time of all the tasks) along with their probability of execution
	 *
	 * @param currentNode the node being processed
	 * @param currentProbability the probability of the previous node
	 * @param currentExecutionTime the time at which the current node starts executing
	 * @param tasksPerInstant the hashmap containing an execution time as key, and a list of tasks and probability as value
	 * <p>
	 * NB: In case of loops, this function may generate for some entries of the map ArrayLists containing pairs with
	 *     the same task but different probabilities. After executing this function, we need to standardize the
	 *     ArrayLists by keeping only 1 of these pair, with form Pair<Task, sum(Double)>.
	 *     An example can be seen in BUG-1 Calcul pool optimal by considering a duration of 40 for F and building the
	 *     hashmap manually.
	 */
	private void computeTasksPerInstantOfTimeRec(final Node currentNode,
												 final double currentProbability,
												 final int currentExecutionTime,
												 final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstant)
	{
		if (currentProbability < THRESHOLD)
		{
			//Note that this behaviour may induce fragmented loops, for which certain tasks
			//belong to the HashMap of tasks at instant t, and others note (see BUG-1 Calcul pool optimal)
			return;
		}

		final double nextProbability;
		final int nextExecutionTime;

		if (currentNode.bpmnObject() instanceof Task)
		{
			//Current node is task, add it to each entry of the map that belongs to the interval
			// [currentExecutionTime ; currentExecutionTime + task.duration()[
			final Pair<Task, Double> pair = new Pair<>((Task) currentNode.bpmnObject(), currentProbability);

			for (int i = currentExecutionTime; i < currentExecutionTime + ((Task) currentNode.bpmnObject()).duration(); i += this.interval)
			{
				if (tasksPerInstant.containsKey(i))
				{
					//If the current execution time already exists, add the pair to the list of
					//tasks executing at this instant, if it does not already belong to it
					final ArrayList<Pair<Task, Double>> tasksList = tasksPerInstant.get(i);

					if (!this.listStronglyContainsPair(tasksList, pair))
					{
						tasksList.add(pair);
					}
				}
				else
				{
					//If the current execution time does not exist yet, create it and
					//initialize it with the current task
					final ArrayList<Pair<Task, Double>> tasksList = new ArrayList<>();
					tasksList.add(pair);
					tasksPerInstant.put(i, tasksList);
				}
			}

			nextProbability = currentProbability;
			nextExecutionTime = currentExecutionTime + ((Task) currentNode.bpmnObject()).duration();
		}
		else if (currentNode.bpmnObject() instanceof Gateway)
		{
			//The current node is a gateway

			if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
			{
				//The current node is a parallel gateway

				if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
				{
					//The current node is a split parallel gateway
					final Node correspondingMergeGateway = this.getCorrespondingMergeGateway(currentNode, currentNode, new HashSet<>());

					if (correspondingMergeGateway == null)
					{
						throw new IllegalStateException("Gateway |" + currentNode.bpmnObject().id() + "| has no corresponding merge gateway.");
					}

					//We compute all the possible execution times of the corresponding parallel merge gateway
					this.computeParallelGatewaySynchronizationTimes(currentNode, correspondingMergeGateway);
					//((Gateway) correspondingMergeGateway.bpmnObject()).setSplitStartTime(currentExecutionTime);
					nextExecutionTime = currentExecutionTime;
					nextProbability = currentProbability;

					final Node mergeGatewayChild = correspondingMergeGateway.childNodes().iterator().next();
					final HashMap<Integer, Double> pathsExecutionTimes = ((Gateway) correspondingMergeGateway.bpmnObject()).parallelPathsExecutionTimes();

					//We call the function recursively on the child of the merge gateway with one possible execution time
					for (Integer executionTime : pathsExecutionTimes.keySet())
					{
						final int currentChildExecutionTime = currentExecutionTime + executionTime;
						final double currentChildProbability = currentProbability * pathsExecutionTimes.get(executionTime);
						this.computeTasksPerInstantOfTimeRec(mergeGatewayChild, currentChildProbability, currentChildExecutionTime, tasksPerInstant);
					}
				}
				else
				{
					//The current node is a join parallel gateway, so we break the recursion
					//because it has already been pursued when we reached its corresponding
					//split gateway
					return;
				}
			}
			else
			{
				//The current node is a non-parallel gateway
				nextExecutionTime = currentExecutionTime;
				nextProbability = currentProbability;
			}
		}
		else if (currentNode.bpmnObject() instanceof SequenceFlow)
		{
			//The current node is a sequence flow
			nextProbability = currentProbability * currentNode.bpmnObject().probability();
			nextExecutionTime = currentExecutionTime;
		}
		else
		{
			//The current node is not a task, nor a gateway, nor a sequence flow
			nextProbability = currentProbability;
			nextExecutionTime = currentExecutionTime;
		}

		//Call the function recursively on all of its child
		for (Node child : currentNode.childNodes())
		{
			this.computeTasksPerInstantOfTimeRec(child, nextProbability, nextExecutionTime, tasksPerInstant);
		}
	}

	/**
	 * Given any parallel split gateway -- assuming that its corresponding merge gateway
	 * exists and can be reached --, this function returns the list (here, a hashmap) of
	 * all possible time of execution of its corresponding merge gateway, along with their
	 * corresponding probability of execution.
	 *
	 * @param parallelSplitGateway the split gateway to analyze
	 * @return a hashmap containing the timestamp of execution of the merge gateway as key, and the probability as value
	 */
	private HashMap<Integer, Double> computeParallelGatewaySynchronizationTimes(final Node parallelSplitGateway,
																				final Node correspondingMergeGateway)
	{
		final HashMap<Integer, Double> executionTimes = ((Gateway) correspondingMergeGateway.bpmnObject()).parallelPathsExecutionTimes();

		if (executionTimes.isEmpty())
		{
			final ArrayList<HashMap<Integer, Double>> temporaryMaps = new ArrayList<>();

			for (Node splitChild : parallelSplitGateway.childNodes())
			{
				final Pair<Integer, Double> freshPair = new Pair<>(0, 1d);
				ArrayList<Pair<Integer, Double>> childPathsList = new ArrayList<>();
				childPathsList.add(freshPair);
				this.computePathExecutionTimes(splitChild, correspondingMergeGateway, freshPair, childPathsList);
				final HashMap<Integer, Double> temporaryMap = new HashMap<>();
				temporaryMaps.add(temporaryMap);

				//We create the map corresponding to the list of pairs by merging the pairs with same
				//execution times if needed. We also remove the pairs where probability < THRESHOLD
				for (Iterator<Pair<Integer, Double>> iterator = childPathsList.iterator(); iterator.hasNext(); )
				{
					final Pair<Integer, Double> childPath = iterator.next();
					final int executionTime = childPath.first();
					final double probability = childPath.second();

					if (probability < THRESHOLD)
					{
						iterator.remove();
					}
					else
					{
						if (temporaryMap.containsKey(executionTime))
						{
							temporaryMap.put(executionTime, temporaryMap.get(executionTime) + probability);
						}
						else
						{
							temporaryMap.put(executionTime, probability);
						}
					}
				}
			}

			executionTimes.putAll(this.mergeHashMapsV2(temporaryMaps));
		}

		return executionTimes;
	}

	/**
	 * Given the initial node of an outgoing path of any parallel gateway, this function
	 * recursively computes the list (hashmap here) of all possible execution times of
	 * this path, along with the probability of each path of being executed.
	 *
	 * @param currentNode the current node being visited
	 * @param mergeGateway the merge gateway indicating this end of the path
	 * @param currentPair the current couple of probability/execution time
	 * @param pairList the global list of pairs probability/execution time
	 * <p>
	 * NB: Note that in this method, we do not break the recursion stack when encountering an already
	 *     seen node, but only when we reach a probability of execution that is strictly lower than
	 *     the threshold fixed in the global variable THRESHOLD. Thus, we assume that the probability
	 *     of execution of a loop is strictly lower than 1. Otherwise, we will prevent unbounded
	 *     recursion.
	 * NB2: For now, we assume that only tasks have a duration (implies that flows, gateways, etc. have a
	 *      duration of 0).
	 */
	private void computePathExecutionTimes(final Node currentNode,
										   final Node mergeGateway,
										   final Pair<Integer, Double> currentPair,
										   final ArrayList<Pair<Integer, Double>> pairList)
	{
		final int currentExecutionTime = currentPair.first();
		final double currentProbability = currentPair.second();

		//Recursion break condition
		if (currentProbability < THRESHOLD)
		{
			return;
		}

		final int newExecutionTime;
		final double newProbability;
		final boolean breakRecursion;

		if (currentNode.bpmnObject() instanceof Task)
		{
			//We found a task --> add its duration to the duration of the current path
			newExecutionTime = currentExecutionTime + ((Task) currentNode.bpmnObject()).duration();
			newProbability = currentProbability;
			breakRecursion = false;
		}
		else if (currentNode.equals(mergeGateway))
		{
			//We reached the merge gateway of our parallel gateway --> break recursion
			newProbability = currentProbability;
			newExecutionTime = currentExecutionTime;
			breakRecursion = true;
		}
		else if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY)
		{
			if (((Gateway) currentNode.bpmnObject()).isSplitGateway())
			{
				//Parallel split gateway --> Compute execution times and jump to the corresponding merge gateway
				final Node currentMergeGateway = this.getCorrespondingMergeGateway(currentNode, currentNode, new HashSet<>());

				if (currentMergeGateway == null)
				{
					throw new IllegalStateException("No corresponding parallel merge gateway found for split parallel gateway |" + currentNode.bpmnObject().id() + "|.");
				}

				final HashMap<Integer, Double> parallelExecutionTimes = this.computeParallelGatewaySynchronizationTimes(currentNode, currentMergeGateway);
				int i = 0;

				for (Integer executionTime : parallelExecutionTimes.keySet())
				{
					final double pathProbability = parallelExecutionTimes.get(executionTime);
					final int nextExecutionTime = currentExecutionTime + executionTime;
					final double nextProbability = currentProbability * pathProbability;
					final Pair<Integer, Double> nextPair;

					if (i == 0)
					{
						nextPair = currentPair;
						currentPair.setFirst(nextExecutionTime);
						currentPair.setSecond(nextProbability);
					}
					else
					{
						nextPair = new Pair<>(nextExecutionTime, nextProbability);
						pairList.add(nextPair);
					}

					i++;

					this.computePathExecutionTimes(currentMergeGateway, mergeGateway, nextPair, pairList);
				}

				return;
			}

			newExecutionTime = currentExecutionTime;
			newProbability = currentProbability;
			breakRecursion = false;
		}
		else if (currentNode.bpmnObject() instanceof SequenceFlow)
		{
			//We found a sequence flow --> multiply the current probability by the one of the sequence flow
			breakRecursion = false;
			newExecutionTime = currentExecutionTime;
			newProbability = currentNode.bpmnObject().probability() * currentProbability;
		}
		else
		{
			//We found another element --> do nothing
			breakRecursion = false;
			newExecutionTime = currentExecutionTime;
			newProbability = currentProbability;
		}

		if (breakRecursion)
		{
			return;
		}

		//Modify the values of the current pair by the new ones
		currentPair.setFirst(newExecutionTime);
		currentPair.setSecond(newProbability);

		//Create a copy of the current pair for each child of the current node
		final ArrayList<Pair<Integer, Double>> temporaryPairs = new ArrayList<>();
		temporaryPairs.add(currentPair);

		for (int i = 1;  i < currentNode.childNodes().size(); i++)
		{
			temporaryPairs.add(currentPair.copy());
		}

		int i = 0;

		//Call the function recursively
		for (Node child : currentNode.childNodes())
		{
			final Pair<Integer, Double> childPair;

			if (i == 0)
			{
				childPair = currentPair;
			}
			else
			{
				childPair = temporaryPairs.get(i);
				pairList.add(childPair);
			}

			this.computePathExecutionTimes(child, mergeGateway, childPair, pairList);
			i++;
		}
	}

	/**
	 * The idea of this function is to merge the different possible execution times
	 * of the different path of a parallel gateway. Given a list of hashmaps (1 per
	 * path of the parallel gateway), the function will generate a single hashmap
	 * where the different execution times have been merged properly, along with
	 * their probability of execution.
	 *
	 * @param hashmaps the list of hashmaps to merge
	 * @return a single merged hashmap containing as key the execution time and as value the probability of execution
	 */
	private HashMap<Integer, Double> mergeHashMapsV2(final ArrayList<HashMap<Integer, Double>> hashmaps)
	{
		final HashMap<Integer, Double> finalMap = new HashMap<>();

		//Compute the starting point of the function and put it in max
		int max = 0;

		for (HashMap<Integer, Double> map : hashmaps)
		{
			int min = Integer.MAX_VALUE;

			for (Integer executionTime : map.keySet())
			{
				if (executionTime < min)
				{
					min = executionTime;
				}
			}

			if (min > max)
			{
				max = min;
			}
		}

		//Iterate
		int candidate = max;

		while (candidate != -1)
		{
			double globalProba = 0d;
			//This ArrayList contains the list of potential candidates
			//for the next iteration. Once the current iteration finishes
			//the next candidate is picked among the potential ones by
			//taking min(potentialCandidates)
			ArrayList<Integer> potentialCandidates = new ArrayList<>();
			boolean candidatesFound = false;

			//Iterate over all hashmaps
			for (int i = 0; i < hashmaps.size(); i++)
			{
				final HashMap<Integer, Double> mainHashmap = hashmaps.get(i);

				//Consider the current hashmap as pivot if it contains the current candidate
				if (mainHashmap.containsKey(candidate))
				{
					//Contains the probability of having an execution time <= candidate for the current hashmap
					double pivotProba = mainHashmap.get(candidate);

					//Iterate over all hashmaps again
					for (int j = 0; j < hashmaps.size(); j++)
					{
						int nextCandidate = Integer.MAX_VALUE;
						double localProba = 0d;
						final HashMap<Integer, Double> secondaryHashmap = hashmaps.get(j);

						//Iterate over all keys of the current hashmap
						for (Integer executionTime : secondaryHashmap.keySet())
						{
							//If the two hashmaps considered are not the same, compute probabilities
							//To avoid redundancy in the computation, we check whether the current hashmap
							//is before or after the current pivot hashmap. If it is on the left, it has
							//already been used as pivot, so we do not sum the probability of the candidate
							//itself for the current hashmap. If it is on the right, it has not already been
							//used as pivot, so we sum the probability of the candidate itself.
							if (i < j)
							{
								if (executionTime <= candidate)
								{
									localProba += secondaryHashmap.get(executionTime);
								}
							}
							else if (i > j)
							{
								if (executionTime < candidate)
								{
									localProba += secondaryHashmap.get(executionTime);
								}
							}

							//We look for the next candidates. First if avoid to redo the computations.
							if (!candidatesFound)
							{
								//If the current execution is greater than the candidate, and
								//lower than the current next candidate, then it becomes the
								//current next candidate
								if (executionTime > candidate
									&& executionTime < nextCandidate)
								{
									nextCandidate = executionTime;
								}
							}
						}

						//Add the current next candidate only if it is valid
						if (nextCandidate != Integer.MAX_VALUE)
						{
							potentialCandidates.add(nextCandidate);
						}

						if (i != j)
						{
							//Otherwise we multiply by 0 for i == j
							pivotProba *= localProba;
						}
					}

					candidatesFound = true;
					globalProba += pivotProba;
				}
			}

			finalMap.put(candidate, globalProba);
			candidate = potentialCandidates.isEmpty() ? -1 : Collections.min(potentialCandidates);
			potentialCandidates.clear();
		}

		return finalMap;
	}

	/**
	 * Given any split gateway of the BPMN process, this method recursively
	 * looks for its corresponding merge gateway.
	 *
	 * @param splitGateway the parallel split gateway for which we want to find the merge gateway
	 * @param currentNode the node being currently verified
	 * @param visitedNodes the set of already visited nodes (loop breaker)
	 * @return the corresponding merge gateway if found, null otherwise
	 */
	private Node getCorrespondingMergeGateway(final Node splitGateway,
											  final Node currentNode,
											  final Set<Node> visitedNodes)
	{
		if (visitedNodes.contains(currentNode))
		{
			return null;
		}

		visitedNodes.add(currentNode);

		if (currentNode.bpmnObject().type() == BpmnProcessType.PARALLEL_GATEWAY
			&& ((Gateway) currentNode.bpmnObject()).isMergeGateway())
		{
			//We found a merge gateway, check if it corresponds to our split gateway
			boolean childMatches = true;

			for (Node splitChild : splitGateway.childNodes())
			{
				if (!splitChild.isAncestorOf(currentNode))
				{
					childMatches = false;
					break;
				}
			}

			if (childMatches)
			{
				//Each child of the split gateway is ancestor of the current merge gateway
				return currentNode;
			}
		}

		for (Node child : currentNode.childNodes())
		{
			final Node correspondingMergeGateway = this.getCorrespondingMergeGateway(splitGateway, child, visitedNodes);

			if (correspondingMergeGateway != null)
			{
				return correspondingMergeGateway;
			}
		}

		return null;
	}

	/**
	 * This function computes the PGCD of the execution time of all the tasks
	 * executed in the current BPMN process and the IAT.
	 *
	 * @param objects the list of objects forming the BPMN process
	 * @return the PGCD of the execution time of the tasks (integer)
	 */
	private int computeInterval(final ArrayList<BpmnProcessObject> objects)
	{
		int currentPGCD = 0;

		for (BpmnProcessObject object : objects)
		{
			if (object instanceof Task)
			{
				if (currentPGCD == 0)
				{
					currentPGCD = ((Task) object).duration();
				}
				else
				{
					currentPGCD = pgcd(currentPGCD, ((Task) object).duration());
				}
			}
		}

		return pgcd(currentPGCD, this.iat);
	}

	/**
	 * This function computes the PGCD of two integers, using the Euclidian technic
	 *
	 * @param a the first integer
	 * @param b the second integer
	 * @return the PGCD of a and b
	 */
	private int pgcd(int a, int b)
	{
		if (b == 0)
		{
			return a;
		}

		return this.pgcd(b, a%b);
	}

	/**
	 * This function verifies whether the given Pair<Task, Double> belongs to
	 * the list of Pair<Task, Double>.
	 *
	 * @param list the list of pairs
	 * @param pair the pair to check
	 * @return true if the pair belongs to the list, false otherwise
	 */
	private boolean listStronglyContainsPair(final ArrayList<Pair<Task, Double>> list,
											 final Pair<Task, Double> pair)
	{
		for (Pair<Task, Double> currentPair : list)
		{
			if (pair.first().equals(currentPair.first())
				&& pair.second().equals(currentPair.second()))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * This function loops over a list of Pair<Task, Double> to compare the current pair
	 * to the one passed as parameter and return it if they have both the same task in
	 * Pair.first()
	 *
	 * @param list the list of pairs to check
	 * @param pair the pair to find equivalent
	 * @return a pair "equivalent" to the one passed as argument if found, null otherwise
	 */
	private Pair<Task, Double> getWeakEquivalentTaskPair(final ArrayList<Pair<Task, Double>> list,
														 final Pair<Task, Double> pair)
	{
		for (Pair<Task, Double> currentPair : list)
		{
			if (pair.first().equals(currentPair.first()))
			{
				return currentPair;
			}
		}

		return null;
	}

	/**
	 * This function loops over a list of Pair<Resource, Double> and returns the current pair
	 * if its resource equals the resource passed as parameter.
	 *
	 * @param list the list of pairs to check
	 * @param resource the resource of the desired pair
	 * @return a pair containing the resource passed as parameter if found, null otherwise
	 */
	private Pair<Resource, Double> getPairFromResource(final ArrayList<Pair<Resource, Double>> list,
													   final Resource resource)
	{
		for (Pair<Resource, Double> currentPair : list)
		{
			if (resource.equals(currentPair.first()))
			{
				return currentPair;
			}
		}

		return null;
	}

	/**
	 * This function iterates over the hashmap of tasks execution over time and identifies for each
	 * map key entries of type Pair<Task, Double1> / Pair<Task, Double2>.
	 * When such entries are found, only one of them is kept as Pair<Task, Double1 + Double2>.
	 *
	 * @param tasks the hashmap containing the tasks executing at each instant of time
	 * @param multiInstances a boolean value telling whether we are correcting tasks in the single instance
	 *                       case of in the multi instances case. For single instance, probability of a task
	 *                       should never exceed 1.
	 */
	private void correctTasks(final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasks,
							  final boolean multiInstances)
	{
		final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksCopy = new HashMap<>(tasks);
		tasks.clear();

		for (Integer executionTime : tasksCopy.keySet())
		{
			final ArrayList<Pair<Task, Double>> tasksAtCurrentTime = tasksCopy.get(executionTime);
			final ArrayList<Pair<Task, Double>> correctedTasksAtCurrentTime = new ArrayList<>();

			for (Pair<Task, Double> currentTask : tasksAtCurrentTime)
			{
				final Pair<Task, Double> duplicate = getWeakEquivalentTaskPair(correctedTasksAtCurrentTime, currentTask);

				if (duplicate == null)
				{
					//No mergeable pair has been seen for the current pair yet
					correctedTasksAtCurrentTime.add(currentTask.copy());
				}
				else
				{
					//Merge is possible --> duplicate takes sum of both probabilities
					final double newProbability = duplicate.second() + currentTask.second();
					duplicate.setSecond(newProbability);
				}
			}

			tasks.put(executionTime, correctedTasksAtCurrentTime);
		}
	}

	/**
	 * This function computes a pool of resources containing the maximum number of
	 * replicas of each resource for any given time t of the process execution
	 *
	 * @param resourcePools a list of resource pools [R1, R2, ..., Rn]
	 * @return a resource pool containing the maximal usage of each resource
	 */
	private ResourcePool computeOptimalPool(HashMap<Integer, ResourcePool> resourcePools)
	{
		final ResourcePool optimalPool = new ResourcePool();

		for (Resource resource : this.globalResourceSet.resourcesSet())
		{
			int currentResourceMax = 0;

			for (ResourcePool resourcePool : resourcePools.values())
			{
				final int replicas = resourcePool.getUsageOf(resource);

				if (replicas > currentResourceMax)
				{
					currentResourceMax = replicas;
				}
			}

			//We need at least the number of resource needed to execute each task independently
			optimalPool.addResource(resource, Math.max(currentResourceMax, this.minimalResourcePool.getUsageOf(resource)));
		}

		return optimalPool;
	}

	/**
	 * This functions computes the Global Execution Time (GET) of the process,
	 * which is the (worst) execution time of the process.
	 *
	 * @param tasksPerInstantOfTime a map containing execution times as keys, and tasks with probability as value
	 * @return the maximum value of the execution times + the interval
	 */
	private int computeGET(final HashMap<Integer, ArrayList<Pair<Task, Double>>> tasksPerInstantOfTime)
	{
		return Collections.max(tasksPerInstantOfTime.keySet()) + this.interval;
	}

	/**
	 * This function computes the minimal pool of resources needed by the process to
	 * execute. In other terms, it returns the minimum number of replicas of each
	 * resource such that each task can execute independently.
	 * Note that being below this number of replicas prevent the process to execute
	 * properly, so we consider that this case never happens.
	 *
	 * @param objects the list of all BPMN objects belonging to the process
	 * @return a resource pool that is minimal with regard to the tasks
	 */
	private ResourcePool computeMinimalPool(final ArrayList<BpmnProcessObject> objects)
	{
		final ResourcePool minimalPool = new ResourcePool();

		for (Resource resource : this.globalResourceSet.resourcesSet())
		{
			int minReplicaResource = 0;

			for (BpmnProcessObject object : objects)
			{
				if (object instanceof Task)
				{
					final int nbReplicasCurrentTask = ((Task) object).resourceUsage().getUsageOf(resource);

					if (nbReplicasCurrentTask > minReplicaResource)
					{
						minReplicaResource = nbReplicasCurrentTask;
					}
				}
			}

			minimalPool.addResource(resource, minReplicaResource);
		}

		return minimalPool;
	}

	//Debugging

	/**
	 * Debugging function used to print properly all the possible execution times
	 * of a parallel gateway, along with their probability of execution.
	 *
	 * @param hashMap a map containing execution time as key and a probability as value
	 * @param parallelSplitGateway the parallel split gateway considered
	 */
	private void printParallelGatewayExecutionTimes(final HashMap<Integer, Double> hashMap,
													final Node parallelSplitGateway)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("\nParallel gateway |")
				.append(parallelSplitGateway == null ? "fake one" : parallelSplitGateway.bpmnObject().id())
				.append("| can execute in the following times:");

		for (Map.Entry<Integer, Double> keyValue : hashMap.entrySet())
		{
			builder.append("\n	- ").append(keyValue.getKey()).append(" UT with probability ").append(keyValue.getValue());
		}
	}
}
