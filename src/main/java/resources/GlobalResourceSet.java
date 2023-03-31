package resources;

import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.Task;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class GlobalResourceSet
{
	private final LinkedHashSet<Resource> resourcesSet;
	private final ArrayList<BpmnProcessObject> objects;

	public GlobalResourceSet(final ArrayList<BpmnProcessObject> objects)
	{
		this.objects = objects;
		this.resourcesSet = new LinkedHashSet<>();
	}

	public void computeGlobalResources()
	{
		for (BpmnProcessObject object : objects)
		{
			if (object instanceof Task)
			{
				resourcesSet.addAll(((Task) object).resourceUsage().resources());
			}
		}
	}

	public LinkedHashSet<Resource> resourcesSet()
	{
		return this.resourcesSet;
	}
}
