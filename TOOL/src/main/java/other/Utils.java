package other;

import bpmn.graph.Node;
import refactoring.Cluster;
import refactoring.dependencies.EnhancedChoice;
import refactoring.dependencies.EnhancedLoop;
import refactoring.dependencies.EnhancedNode;
import refactoring.dependencies.EnhancedType;
import refactoring.partial_order_to_bpmn.AbstractNode;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class Utils
{
    private Utils()
    {

    }

    public static String nanoSecToReadable(final long nanoseconds)
    {
        final DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.CEILING);

        if (nanoseconds > 1000000000)
        {
            //More than 1sec
            return df.format((double) nanoseconds / (double) 1000000000) + "s";
        }
        else if (nanoseconds > 1000000)
        {
            //More than 1ms
            return df.format((double) nanoseconds / (double) 1000000) + "ms";
        }
        else if (nanoseconds > 1000)
        {
            //More than 1µs
            return df.format((double) nanoseconds / (double) 1000) + "µs";
        }
        else
        {
            //Value in nanoseconds
            return df.format((double) nanoseconds) + "ns";
        }
    }

    public static String protectString(String s)
    {
        return s.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;");
    }

    public static String quoteString(String s)
    {
        return "\"" + s + "\"";
    }

    public static String quoteString(int i)
    {
        return "\"" + i + "\"";
    }

    public static boolean isAnInt(final String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static boolean isAnInt(final char c)
    {
        try
        {
            Integer.parseInt(String.valueOf(c));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static String generateRandomIdentifier()
    {
        return Utils.generateRandomIdentifier(30);
    }

    public static String generateRandomIdentifier(final int length)
    {
        final StringBuilder builder = new StringBuilder();
        final Random random = new Random();

        for (int i = 0; i < length; i++)
        {
            final char c;

            //CAPS
            if (random.nextBoolean())
            {
                c = (char) (random.nextInt(25) + 65 + 1); //Exclusive upper bound
            }
            //NON CAPS
            else
            {
                c = (char) (random.nextInt(25) + 97 + 1); //Exclusive upper bound
            }

            builder.append(c);
        }

        return builder.toString();
    }

    public static double sumDoubles(final Collection<Double> doubles)
    {
        double sum = 0d;

        for (Double d : doubles)
        {
            sum += d;
        }

        return sum;
    }

    public static int max(final Collection<Integer> integers)
    {
        int max = Integer.MIN_VALUE;

        if (integers.isEmpty())
        {
            return max;
        }

        for (Integer integer : integers)
        {
            if (integer > max)
            {
                max = integer;
            }
        }

        return max;
    }

    public static String printPaths(final Collection<ArrayList<Node>> paths)
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("The following paths were found:\n");

        for (ArrayList<Node> path : paths)
        {
            builder.append("     - ");

            for (Node node : path)
            {
                builder.append(node.bpmnObject().id())
                        .append(" - ");
            }

            builder.append("\n");
        }

        return builder.toString();
    }

    /**
     * In this method, we want to compute all the combinations of elements of a set.
     * For example, getCombinationsOf([1,2,3,4]) returns [[1], [2], [3], [1,2], [1,3],
     * [2,3], [1,2,3]].
     *
     * @param elements the elements to combine
     * @return the list of all possible combinations of the elements
     * @param <T> any type
     */
    public static <T> Collection<Collection<T>> getCombinationsOf(Collection<T> elements)
    {
        final Collection<Collection<T>> combinations = new ArrayList<>();
        final ArrayList<T> sortedElements = new ArrayList<>(elements);

        Utils.getCombinationsOf(combinations, new ArrayList<>(), sortedElements);

        return combinations;
    }

    /**
     * In this method, we want all the combinations of the elements of the list respecting
     * the order in which they are in the list, meaning that if we have elements [1,2,3],
     * the combination [1,3] is not possible because it does not respect the order for 2.
     *
     * @param elements the elements to combine
     * @return the list of all ordered combinations
     * @param <T> any element
     */
    public static <T> List<List<T>> getOrderedCombinationsOf(List<T> elements)
    {
        final List<List<T>> combinations = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++)
        {
            for (int j = i; j < elements.size(); j++)
            {
                final List<T> combination = new ArrayList<>();

                for (int index = i; index <= j; index++)
                {
                    combination.add(elements.get(index));
                }

                combinations.add(combination);
            }
        }

        return combinations;
    }

    //Private methods

    public static <T> void getCombinationsOf(Collection<Collection<T>> allCombinations,
                                             Collection<T> currentCombination,
                                             List<T> remainingElements)
    {
        if (remainingElements.isEmpty())
        {
            return;
        }

        for (int i = 0; i < remainingElements.size(); i++)
        {
            final List<T> newRemainingElements = new ArrayList<>(remainingElements);

            int toRemove = 0;

            //Avoid duplicates
            while (toRemove < i)
            {
                newRemainingElements.remove(0);
                toRemove++;
            }

            final List<T> currentCombinationCopy = new ArrayList<>(currentCombination);
            currentCombinationCopy.add(newRemainingElements.remove(0));
            allCombinations.add(currentCombinationCopy);

            Utils.getCombinationsOf(allCombinations, currentCombinationCopy, newRemainingElements);
        }
    }

    public boolean listContainsAbstractNode(final Collection<AbstractNode> list,
                                            final AbstractNode node)
    {
        for (AbstractNode abstractNode : list)
        {
            if (abstractNode.id().equals(node.id()))
            {
                return true;
            }
        }

        return false;
    }

    public static void unprocessClusters(final Cluster currentCluster)
    {
        currentCluster.unprocess();

        for (EnhancedNode enhancedNode : currentCluster.elements())
        {
            if (enhancedNode.type() == EnhancedType.CHOICE)
            {
                final EnhancedChoice enhancedChoice = (EnhancedChoice) enhancedNode;

                for (Cluster subCluster : enhancedChoice.clusters())
                {
                    Utils.unprocessClusters(subCluster);
                }
            }
            else if (enhancedNode.type() == EnhancedType.LOOP)
            {
                final EnhancedLoop enhancedLoop = (EnhancedLoop) enhancedNode;
                Utils.unprocessClusters(enhancedLoop.entryToExitCluster());
                Utils.unprocessClusters(enhancedLoop.exitToEntryCluster());
            }
        }
    }
}
