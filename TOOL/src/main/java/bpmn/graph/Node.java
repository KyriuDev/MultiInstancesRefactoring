package bpmn.graph;

import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import other.Utils;
import bpmn.types.process.BpmnProcessObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Nodes are either traditional BPMN objects (tasks, gateways, ...)
 * or sequence flows.
 */
public class Node
{
    public static final boolean GRAPH_DUMP = false;
    private final BpmnProcessObject bpmnObject;
    private final Set<Node> childNodes;
    private final Set<Node> parentNodes;
    private final String id;

    public Node(BpmnProcessObject bpmnObject)
    {
        this.bpmnObject = bpmnObject;
        this.childNodes = new HashSet<>();
        this.parentNodes = new HashSet<>();
        this.id = Utils.generateRandomIdentifier();

        if (bpmnObject == null)
        {
            throw new AssertionError();
        }
    }

    //Public methods

    public BpmnProcessObject bpmnObject()
    {
        return this.bpmnObject;
    }

    public void addChild(Node node)
    {
        this.childNodes.add(node);
    }

    public void addParent(Node node)
    {
        this.parentNodes.add(node);
    }

    public void removeChildren(Node node)
    {
        this.childNodes.remove(node);
    }

    public void removeParent(Node node)
    {
        this.parentNodes.remove(node);
    }

    public Set<Node> childNodes()
    {
        return this.childNodes;
    }

    public Set<Node> parentNodes()
    {
        return this.parentNodes;
    }

    public boolean hasChilds()
    {
        return !this.childNodes.isEmpty();
    }

    public boolean hasParents()
    {
        return !this.parentNodes.isEmpty();
    }

    public boolean hasChild(final Node node)
    {
        return this.childNodes.contains(node);
    }

    public boolean hasParent(final Node node)
    {
        return this.parentNodes.contains(node);
    }

    public String stringify(int depth,
                            ArrayList<Node> nodesAlreadyPrinted)
    {
        StringBuilder tabBuilder = new StringBuilder();
        tabBuilder.append("    ".repeat(Math.max(0, depth)));

        final StringBuilder builder = new StringBuilder();

        if (nodesAlreadyPrinted.contains(this))
        {
            builder
                    .append(tabBuilder)
                    .append("- Node \"")
                    .append(this.bpmnObject.id())
                    .append("\" (")
                    .append(this.bpmnObject.name())
                    .append(")")
                    .append("\" (end of loop)\n")
            ;
        }
        else
        {
            nodesAlreadyPrinted.add(this);

            builder
                    .append(tabBuilder)
                    .append("- Node ")
                    .append("\"")
                    .append(this.bpmnObject.id())
                    .append("\" ")
                    .append(this.bpmnObject instanceof Gateway ? this.bpmnObject.type() == BpmnProcessType.EXCLUSIVE_GATEWAY ? "EXCLUSIVE" : "PARALLEL" : "")
                    .append(" (")
                    .append(this.bpmnObject.name())
                    .append(") executes with probability ")
                    .append(this.bpmnObject.probability())
                    .append(" and has ")
                    .append(this.childNodes.size() == 0 ? "no" : this.childNodes.size())
                    .append(" child:\n");

            final ArrayList<ArrayList<Node>> visitedNodes = new ArrayList<>();
            visitedNodes.add(nodesAlreadyPrinted);

            for (int i = 1; i < this.childNodes.size(); i++)
            {
                visitedNodes.add(new ArrayList<>(nodesAlreadyPrinted));
            }

            int i = 0;

            for (Node child : this.childNodes)
            {
                builder.append(child.stringify(depth + 1, visitedNodes.get(i++)));
            }
        }

        if (GRAPH_DUMP)
        {
            try {
                Files.write(Paths.get("/home/quentin/Bureau/test.txt"), builder.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return builder.toString();
    }

    public String stringifyRevert(int depth,
                                  ArrayList<Node> nodesAlreadyPrinted)
    {
        StringBuilder tabBuilder = new StringBuilder();
        tabBuilder.append("    ".repeat(Math.max(0, depth)));

        final StringBuilder builder = new StringBuilder();

        if (nodesAlreadyPrinted.contains(this))
        {
            builder
                    .append(tabBuilder)
                    .append("- Node \"")
                    .append(this.bpmnObject.id())
                    .append("\" (")
                    .append(this.bpmnObject.name())
                    .append(")")
                    .append("\" (end of loop)\n")
            ;
        }
        else
        {
            nodesAlreadyPrinted.add(this);

            builder
                    .append(tabBuilder)
                    .append("- Node ")
                    .append("\"")
                    .append(this.bpmnObject.id())
                    .append("\" (")
                    .append(this.bpmnObject.name())
                    .append(") executes with probability ")
                    .append(this.bpmnObject.probability())
                    .append(" and has ")
                    .append(this.parentNodes.size() == 0 ? "no" : this.parentNodes.size())
                    .append(" parents:\n");

            final ArrayList<ArrayList<Node>> visitedNodes = new ArrayList<>();
            visitedNodes.add(nodesAlreadyPrinted);

            for (int i = 1; i < this.parentNodes.size(); i++)
            {
                visitedNodes.add(new ArrayList<>(nodesAlreadyPrinted));
            }

            int i = 0;

            for (Node parent : this.parentNodes)
            {
                builder.append(parent.stringifyRevert(depth + 1, visitedNodes.get(i++)));
            }
        }

        if (GRAPH_DUMP)
        {
            try {
                Files.write(Paths.get("/home/quentin/Bureau/test.txt"), builder.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return builder.toString();
    }

    public boolean isInLoop()
    {
        return this.isAncestorOf(this);
    }

    public boolean hasSuccessor(Node n)
    {
        return this.isAncestorOf(n);
    }

    public boolean hasAncestor(Node n)
    {
        return this.isSuccessorOf(n);
    }

    public boolean isAncestorOf(Node n)
    {
        return isAncestorOf(n, new HashSet<>());
    }

    public boolean isSuccessorOf(Node n)
    {
        return isSuccessorOf(n, new HashSet<>());
    }

    /**
     * Used to know whether the current node (this)
     * is an ancestor of the Node n in parameter.
     *
     * @param n the node to be found
     * @param visitedChild the list of already visited child (recursion breaker)
     * @return true if the Node on which this function has been called is an ancestor of Node n, false otherwise
     */
    private boolean isAncestorOf(Node n,
                                 Set<Node> visitedChild)
    {
        if (this.childNodes.contains(n))
        {
            return true;
        }

        if (visitedChild.contains(this))
        {
            return false;
        }

        visitedChild.add(this);

        for (Node child : this.childNodes)
        {
            final boolean childIsParent = child.isAncestorOf(n, visitedChild);

            if (childIsParent)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Used to know whether the current node (this)
     * is a successor of the Node n in parameter.
     *
     * @param n the node to be found
     * @param visitedParents the list of already visited child (recursion breaker)
     * @return true if the Node on which this function has been called is a successor of Node n, false otherwise
     */
    private boolean isSuccessorOf(Node n,
                                  Set<Node> visitedParents)
    {
        if (this.parentNodes.contains(n))
        {
            return true;
        }

        if (visitedParents.contains(this))
        {
            return false;
        }

        visitedParents.add(this);

        for (Node parent : this.parentNodes)
        {
            final boolean parentIsChild = parent.isSuccessorOf(n, visitedParents);

            if (parentIsChild)
            {
                return true;
            }
        }

        return false;
    }

    public Node weakCopy()
    {
        return new Node(this.bpmnObject);
    }

    public Node deepCopy()
    {
        return new Node(this.bpmnObject.copy());
    }

    public void removeParents()
    {
        this.parentNodes.clear();
    }

    public void removeChildren()
    {
        this.childNodes.clear();
    }

    public boolean canEscapeLoop()
    {
        if (!this.isInLoop())
        {
            return true;
        }

        for (Node parent : this.parentNodes)
        {
            if (!parent.isInLoop())
            {
                return true;
            }
        }

        return false;
    }

    //Overrides

    @Override
    public int hashCode()
    {
        return this.bpmnObject.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Node))
        {
            return false;
        }

        return this.bpmnObject.equals(((Node) o).bpmnObject);
    }
}
