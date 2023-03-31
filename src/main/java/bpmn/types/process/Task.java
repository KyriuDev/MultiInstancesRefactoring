package bpmn.types.process;

import resources.ResourcePool;
import resources.Resource;

public class Task extends BpmnProcessObject
{
    private Boolean autoStoreVariables;
    private ResourcePool resourceUsage;
    private final int duration;

    public Task(String id,
                BpmnProcessType type,
                final int duration)
    {
        super(type, id);
        this.resourceUsage = new ResourcePool();
        this.duration = duration;
    }

    public void setAutoStoreVariables(Boolean b)
    {
        this.autoStoreVariables = b;
    }

    public Boolean autoStoreVariables()
    {
        return this.autoStoreVariables;
    }

    public void addResource(final Resource resource,
                            final int nbReplicas)
    {
        this.resourceUsage.addResource(resource, nbReplicas);
    }

    public void setResourcePool(final ResourcePool resourcePool)
    {
        this.resourceUsage = resourcePool;
    }

    public ResourcePool resourceUsage()
    {
        return this.resourceUsage;
    }

    public int duration()
    {
        return this.duration;
    }

    @Override
    public BpmnProcessObject copy()
    {
        final Task duplicate = new Task(BpmnProcessFactory.generateID(this), this.type, this.duration);
        duplicate.setAutoStoreVariables(this.autoStoreVariables);
        duplicate.setName(this.name());
        duplicate.setResourcePool(this.resourceUsage);

        return duplicate;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        builder
                .append("Task \"")
                .append(id)
                .append("\" has the following flows:\n");

        for (SequenceFlow flow : this.flows().keySet())
        {
            builder
                    .append("- Flow \"")
                    .append(flow.id)
                    .append(" (")
                    .append(this.flows().get(flow).toString())
                    .append(" flow)");
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Task)
        {
            return ((Task) o).id().equals(this.id);
        }

        return false;
    }
}
