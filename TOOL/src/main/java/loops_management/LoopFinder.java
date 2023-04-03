package loops_management;

import bpmn.graph.Graph;
import bpmn.graph.Node;
import bpmn.types.process.BpmnProcessObject;
import bpmn.types.process.BpmnProcessType;
import bpmn.types.process.Gateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
;

public class LoopFinder
{
    private static final Logger logger = LoggerFactory.getLogger(LoopFinder.class);
    private final Graph fullGraph;
    private final ArrayList<Loop> loops;
    private final ArrayList<ArrayList<String>> taskNamesLoops;

    public LoopFinder(final Graph fullGraph)
    {
        this.fullGraph = fullGraph;
        this.loops = new ArrayList<>();
        this.taskNamesLoops = new ArrayList<>();
    }

    public void findLoops()
    {
        this.findLoopsV3(this.fullGraph.weakCopy().initialNode());
    }

    public ArrayList<Loop> loops()
    {
        return this.loops;
    }

    public ArrayList<ArrayList<String>> taskNamesLoops()
    {
        return this.taskNamesLoops;
    }

    /*public Graph graphWithoutCycle()
    {
        final Graph graphCopy = this.fullGraph.weakCopy();
        this.removeCyclesIn(graphCopy);
        return graphCopy;
    }*/

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("LoopFinder found ")
                .append(this.loops.size())
                .append(" loops:\n\n");

        for (Graph loop : this.loops)
        {
            builder.append("Current loop:\n\n")
                    .append(loop.toString())
                    .append("\n");
        }

        return builder.toString();
    }

    //We may have nested loops
    public ArrayList<Loop> findLoopsContaining(Node n)
    {
        if (n == null)
        {
            return null;
        }

        return this.findLoopsContaining(n.bpmnObject().id());
    }

    public ArrayList<Loop> findLoopsContaining(BpmnProcessObject object)
    {
        if (object == null)
        {
            return null;
        }

        return this.findLoopsContaining(object.id());
    }

    public ArrayList<Loop> findLoopsContaining(String id)
    {
        final HashSet<Loop> loops = new HashSet<>();

        if (id == null)
        {
            return null;
        }

        for (Loop loop : this.loops)
        {
            if (loop.hasNodeOfId(id))
            {
                loops.add(loop);
            }
        }

        return new ArrayList<>(loops);
    }

    public Loop findOuterLoopOf(Node n)
    {
        if (n == null)
        {
            return null;
        }

        return this.findOuterLoopOf(n.bpmnObject().id());
    }

    public Loop findOuterLoopOf(BpmnProcessObject object)
    {
        if (object == null)
        {
            return null;
        }

        return this.findOuterLoopOf(object.id());
    }

    /**
     * This function returns the most outer loop containing the node of id "id".
     * The result of this function may be different from the result of function
     * findLoopOf(Node n) if and only if the node of id "id" belongs to nested loops.
     * In this case, it returns the loop containing the wanted node that contains all other loops
     * containing this node.
     *
     * @param id the id of the node to look for
     * @return the most external loop containing the wanted node
     */
    public Loop findOuterLoopOf(String id)
    {
        final ArrayList<Loop> loopsContainingId = this.findLoopsContaining(id);

        if (loopsContainingId == null)
        {
            return null;
        }

        if (loopsContainingId.size() == 1)
        {
            return loopsContainingId.get(0);
        }

        for (Loop loop1 : loopsContainingId)
        {
            boolean loopContainsOtherLoops = true;

            for (Loop loop2 : loopsContainingId)
            {
                if (!loop1.hasNode(loop2.initialNode()))
                {
                    loopContainsOtherLoops = false;
                    break;
                }
            }

            if (loopContainsOtherLoops)
            {
                return loop1;
            }
        }

        throw new IllegalStateException("No outer loop found for node |" + id + "|.");
    }

    public Loop findInnerLoopOf(Node n)
    {
        if (n == null)
        {
            return null;
        }

        return this.findInnerLoopOf(n.bpmnObject().id());
    }

    public Loop findInnerLoopOf(BpmnProcessObject object)
    {
        if (object == null)
        {
            return null;
        }

        return this.findInnerLoopOf(object.id());
    }

    /**
     * This function returns the most inner loop of the node of id "id".
     * The result of this function may be different from the result of function
     * findLoopOf(Node n) if and only if the node of id "id" belongs to nested loops.
     * In this case, it returns the loop containing the node of id "id" that does not contain
     * any other loop containing the node of id "id".
     *
     * @param id the id of the node to look for
     * @return the most internal loop containing the node of id "id"
     */
    public Loop findInnerLoopOf(String id)
    {
        final ArrayList<Loop> loopsContainingId = this.findLoopsContaining(id);

        if (loopsContainingId == null)
        {
            return null;
        }

        if (loopsContainingId.size() == 1)
        {
            return loopsContainingId.get(0);
        }

        for (Loop loop1 : loopsContainingId)
        {
            boolean loopContainsNoOtherLoop = true;

            for (Loop loop2 : loopsContainingId)
            {
                if (!loop1.equals(loop2)
                    && loop1.hasNode(loop2.initialNode()))
                {
                    loopContainsNoOtherLoop = false;
                    break;
                }
            }

            if (loopContainsNoOtherLoop)
            {
                return loop1;
            }
        }

        throw new IllegalStateException("No inner loop found for node |" + id + "|.");
    }

    public Loop findLoopOf(Node n)
    {
        if (n == null)
        {
            return null;
        }

        return this.findLoopOf(n.bpmnObject().id());
    }

    public Loop findLoopOf(BpmnProcessObject object)
    {
        if (object == null)
        {
            return null;
        }

        return this.findLoopOf(object.id());
    }

    public Loop findLoopOf(String s)
    {
        if (s == null)
        {
            return null;
        }

        for (Loop loop : this.loops)
        {
            if (loop.hasNodeOfId(s))
            {
                return loop;
            }
        }

        return null;
    }

    public Node getLastNode(Graph loop)
    {
        return getLastNode(loop.initialNode(), new ArrayList<>());
    }

    public boolean nodeIsInLoop(Node n)
    {
        return this.findLoopOf(n) != null;
    }

    public boolean nodeIsALoopEntryPoint(Node n)
    {
        for (final Loop loop : this.loops)
        {
            if (loop.initialNode().equals(n))
            {
                return true;
            }
        }

        return false;
    }

    public boolean nodeIsInLoop(BpmnProcessObject o)
    {
        return this.findLoopOf(o) != null;
    }

    public boolean nodeIsInLoop(String s)
    {
        return this.findLoopOf(s) != null;
    }

    public void addLoop(final Loop loop)
    {
        this.loops.add(loop);
    }

    //Private methods

    private Node getLastNode(Node currentNode,
                             ArrayList<Node> visitedNodes)
    {
        if (!currentNode.hasChilds())
        {
            return currentNode;
        }

        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        for (Node child : currentNode.childNodes())
        {
            Node childIsLastNode = getLastNode(child, visitedNodes);

            if (childIsLastNode != null)
            {
                return childIsLastNode;
            }
        }

        return null;
    }

    /**
     * We assume that a correct loop has exactly 1 entry point and 1 exit point.
     * Then we do:
     *      1- Find loop exit node
     *      2- Remove entry node out-of-loop parents & exit node out-of-loop children
     *      3- Copy subgraph and store it as loop
     *      4- Remove entry node child and exit node parent (needed for nested loops)
     *      5- Call recursively on entry node child + exit node child
     */
    private void findLoopsV3(final Node currentNode)
    {
        if (currentNode.isInLoop())
        {
            //We found a new loop -> current node should be an exclusive merge gateway
            if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
                && ((Gateway) currentNode.bpmnObject()).isMergeGateway())
            {
                //1- Find loop exit node
                final String entryPointID = currentNode.bpmnObject().id();
                final String exitPointID;
                final Node loopExitNode = this.findLoopExitNode(currentNode, currentNode, new HashSet<>());

                if (loopExitNode == null)
                {
                    throw new IllegalStateException("No exit node found for loop starting with Node |"
                            + currentNode.bpmnObject().id() + "|!");
                }

                exitPointID = loopExitNode.bpmnObject().id();

                //2- Cut entry node out-of-loop parents and exit node out-of-loop children
                final ArrayList<Node> exitFlows = new ArrayList<>(loopExitNode.childNodes());

                //Remove all flows going out of the loop from the exit node child
                loopExitNode.childNodes().removeIf(childNode -> !childNode.isAncestorOf(currentNode));

                //Remove all flows coming from out of the loop
                currentNode.parentNodes().removeIf(parentNode -> !parentNode.isSuccessorOf(currentNode));

                //3- At this point, loop should be isolated from the rest of graph, so we can copy it directly
                final Loop copiedLoop = new Loop(new Graph(currentNode).weakCopy().initialNode());
                copiedLoop.setEntryPoint(copiedLoop.getNodeFromID(entryPointID));
                copiedLoop.setExitPoint(copiedLoop.getNodeFromID(exitPointID));
                this.loops.add(copiedLoop);

                //4- Remove entry node child and exit node parent
                final Node entryNodeChild = currentNode.childNodes().iterator().next();
                currentNode.removeChildren();
                loopExitNode.removeParents();

                //5- Recursive call on entry node child + exit node child
                this.findLoopsV3(entryNodeChild);

                for (Node exitChild : exitFlows)
                {
                    this.findLoopsV3(exitChild);
                }
            }
            else
            {
                throw new IllegalStateException("We did not enter the loop by its entry point but by Node |"
                            + currentNode.bpmnObject().id() + "|!");
            }
        }
        else
        {
            for (Node child : currentNode.childNodes())
            {
                this.findLoopsV3(child);
            }
        }
    }

    private Node findLoopExitNode(final Node entryNode,
                                  final Node currentNode,
                                  final HashSet<Node> visitedNodes)
    {
        if (visitedNodes.contains(currentNode))
        {
            return null;
        }

        visitedNodes.add(currentNode);

        if (currentNode.bpmnObject().type() == BpmnProcessType.EXCLUSIVE_GATEWAY
            && ((Gateway) currentNode.bpmnObject()).isSplitGateway())
        {
            for (Node child : currentNode.childNodes())
            {
                if (!child.hasSuccessor(entryNode))
                {
                    //If one of the child of the current exclusive split gateway can not reach the entry node,
                    //then the current exclusive split gateway is the loop exit node
                    return currentNode;
                }
            }
        }

        for (Node child : currentNode.childNodes())
        {
            final Node exitNode = this.findLoopExitNode(entryNode, child, visitedNodes);

            if (exitNode != null)
            {
                return exitNode;
            }
        }

        return null;
    }

    private void removeCyclesIn(final Graph graph)
    {
        for (Loop loop : this.loops)
        {
            final Node loopEntryPoint = graph.getNodeFromID(loop.entryPoint().bpmnObject().id());

            for (Iterator<Node> iterator = loopEntryPoint.parentNodes().iterator(); iterator.hasNext(); )
            {
                final Node parent = iterator.next();

                if (loop.hasNode(parent))
                {
                    iterator.remove();
                    parent.removeChildren(loopEntryPoint);
                }
            }
        }
    }
}
