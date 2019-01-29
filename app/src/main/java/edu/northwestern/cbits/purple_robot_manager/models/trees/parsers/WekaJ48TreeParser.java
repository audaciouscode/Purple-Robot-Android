package edu.northwestern.cbits.purple_robot_manager.models.trees.parsers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import edu.northwestern.cbits.purple_robot_manager.models.trees.BranchNode;
import edu.northwestern.cbits.purple_robot_manager.models.trees.BranchNode.Condition;
import edu.northwestern.cbits.purple_robot_manager.models.trees.BranchNode.Operation;
import edu.northwestern.cbits.purple_robot_manager.models.trees.LeafNode;
import edu.northwestern.cbits.purple_robot_manager.models.trees.TreeNode;
import edu.northwestern.cbits.purple_robot_manager.models.trees.TreeNode.TreeNodeException;

/**
 * Implements a parser for the GraphViz format generated by Weka's J48 learner.
 * 
 * Example model:
 * 
 * <pre>
 * {@code
 * digraph J48Tree {
 * N0 [label=\"wifiaccesspointsprobe_current_ssid\" ]
 * N0->N1 [label=\"= ?\"]
 * N1 [label=\"alone (0.0)\" shape=box style=filled ]
 * N0->N2 [label=\"= home\"]
 * N2 [label=\"alone (8.41/2.0)\" shape=box style=filled ]
 * N0->N3 [label=\"= 0x\"]
 * N3 [label=\"robothealthprobe_cpu_usage\" ]
 * N3->N4 [label=\"<= 0.142857\"]
 * N4 [label=\"wifiaccesspointsprobe_access_point_count\" ]
 * N4->N5 [label=\"<= 17\"]
 * N5 [label=\"acquaintances (2.1/1.1)\" shape=box style=filled ]
 * N4->N6 [label=\"> 17\"]
 * N6 [label=\"strangers (2.1/0.1)\" shape=box style=filled ]
 * N3->N7 [label=\"> 0.142857\"]
 * N7 [label=\"alone (5.26/2.0)\" shape=box style=filled ]
 * N0->N8 [label=\"= blerg\"]
 * N8 [label=\"partner (1.05/0.05)\" shape=box style=filled ]
 * N0->N9 [label=\"= northwestern\"]
 * N9 [label=\"runningsoftwareproberunning_tasks_running_tasks_package_name\" ]
 * N9->N10 [label=\"= ?\"]
 * N10 [label=\"acquaintances (0.0)\" shape=box style=filled ]
 * N9->N11 [label=\"= comcbitsmobilyze_pro\"]
 * N11 [label=\"acquaintances (2.1/0.1)\" shape=box style=filled ]
 * N9->N12 [label=\"= comandroidlauncher\"]
 * N12 [label=\"alone (3.15/1.0)\" shape=box style=filled ]
 * N9->N13 [label=\"= edunorthwesterncbitspurple_robot_manager\"]
 * N13 [label=\"acquaintances (16.82/7.82)\" shape=box style=filled ]
 * }
 * </pre>
 */

public class WekaJ48TreeParser extends TreeNodeParser
{
    private static final String NUM_INSTANCES = "num_instances";
    private static final String NUM_INCORRECT = "num_incorrect";

    ArrayList<String> _lines = new ArrayList<>();

    /**
     * Traditional Main method for testing the classes from the desktop
     * environment. Runs a few tests of the tree above.
     * 
     * @param args
     *            Not used.
     */

    public static void main(String[] args)
    {
        try
        {
            TreeNode node = TreeNodeParser
                    .parseString("digraph J48Tree {\nN0 [label=\"wifiaccesspointsprobe_current_ssid\" ]\nN0->N1 [label=\"= ?\"]\nN1 [label=\"alone (0.0)\" shape=box style=filled ]\nN0->N2 [label=\"= home\"]\nN2 [label=\"alone (8.41/2.0)\" shape=box style=filled ]\nN0->N3 [label=\"= 0x\"]\nN3 [label=\"robothealthprobe_cpu_usage\" ]\nN3->N4 [label=\"<= 0.142857\"]\nN4 [label=\"wifiaccesspointsprobe_access_point_count\" ]\nN4->N5 [label=\"<= 17\"]\nN5 [label=\"acquaintances (2.1/1.1)\" shape=box style=filled ]\nN4->N6 [label=\"> 17\"]\nN6 [label=\"strangers (2.1/0.1)\" shape=box style=filled ]\nN3->N7 [label=\"> 0.142857\"]\nN7 [label=\"alone (5.26/2.0)\" shape=box style=filled ]\nN0->N8 [label=\"= blerg\"]\nN8 [label=\"partner (1.05/0.05)\" shape=box style=filled ]\nN0->N9 [label=\"= northwestern\"]\nN9 [label=\"runningsoftwareproberunning_tasks_running_tasks_package_name\" ]\nN9->N10 [label=\"= ?\"]\nN10 [label=\"acquaintances (0.0)\" shape=box style=filled ]\nN9->N11 [label=\"= comcbitsmobilyze_pro\"]\nN11 [label=\"acquaintances (2.1/0.1)\" shape=box style=filled ]\nN9->N12 [label=\"= comandroidlauncher\"]\nN12 [label=\"alone (3.15/1.0)\" shape=box style=filled ]\nN9->N13 [label=\"= edunorthwesterncbitspurple_robot_manager\"]\nN13 [label=\"acquaintances (16.82/7.82)\" shape=box style=filled ]\n}\n");
            System.out.println(node.toString(0));

            HashMap<String, Object> world = new HashMap<>();

            Map<String, Object> prediction = node.fetchPrediction(world);
            System.out.println("Expect alone. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

            world.put("robothealthprobe_cpu_usage", 0.1);

            prediction = node.fetchPrediction(world);
            System.out.println("Expect alone. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

            world.put("wifiaccesspointsprobe_current_ssid", "blerg");
            prediction = node.fetchPrediction(world);
            System.out.println("Expect partner. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

            world.put("wifiaccesspointsprobe_current_ssid", "0x");
            world.put("wifiaccesspointsprobe_access_point_count", (double) 20);
            prediction = node.fetchPrediction(world);
            System.out.println("Expect strangers. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

            world.put("wifiaccesspointsprobe_current_ssid", "northwestern");
            prediction = node.fetchPrediction(world);
            System.out.println("Expect acquaintances. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

            world.put("runningsoftwareproberunning_tasks_running_tasks_package_name", "comandroidlauncher");
            prediction = node.fetchPrediction(world);
            System.out.println("Expect alone. Got " + prediction.get(LeafNode.PREDICTION) + " // "
                    + prediction.get(LeafNode.ACCURACY) + ".");

        }
        catch (ParserNotFound | TreeNodeException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Parses the provided content and returns the accompanying decision tree.
     * 
     * @see edu.northwestern.cbits.purple_robot_manager.models.trees.parsers.TreeNodeParser#parse(java.lang.String)
     */

    public TreeNode parse(String content) throws TreeNodeException
    {
        // Extract strings that have meaningful content. Examples:
        // N0 [label=\"wifiaccesspointsprobe_current_ssid\" ]
        // N0->N1 [label=\"= ?\"]

        for (String line : content.split("\\r?\\n"))
        {
            if (line.startsWith("digraph J48Tree"))
            {
                // Start
            }
            else if (line.startsWith("}"))
            {
                // End
            }
            else
                this._lines.add(line.trim());
        }

        // The root of the Weka decision trees is always labelled "N0".

        return this.treeForNode("N0");
    }

    /**
     * Recursively generates a decision tree based on the node ID. This function
     * scans the content lines containing information relevant to the node
     * specified and constructs the appropriate node (branch or leaf).
     * 
     * @param id
     *            Node ID to be turned into a TreeNode.
     * 
     * @return TreeNode encoded with the relevant node details, including
     *         descendants.
     * 
     * @throws TreeNodeException
     *             Thrown on errors constructing the node.
     */

    private TreeNode treeForNode(String id) throws TreeNodeException
    {
        for (String line : this._lines)
        {
            if (line.startsWith(id + " [label="))
            {
                if (line.contains("shape=box"))
                {
                    // This line contains a leaf node. Build one...

                    return this.leafNodeForLine(line);
                }
                else
                {
                    // Branch node...

                    BranchNode branch = new BranchNode();

                    // Split the line by quote tokens...

                    String[] tokens = line.split("\\\"");

                    String feature = tokens[1];

                    for (String edgeLine : this._lines)
                    {
                        // Is this an edge we care about?

                        if (edgeLine.startsWith(id + "->"))
                        {
                            // It is. Replace irrelevant parts of the string
                            // with tokenizable components.

                            edgeLine = edgeLine.replace(id + "->", "");
                            edgeLine = edgeLine.replace(" [label=\"", "|");
                            edgeLine = edgeLine.replace(" ", "|");
                            edgeLine = edgeLine.replace("\"]", "|");

                            // Split on tokenizable component.

                            String[] edgeTokens = edgeLine.split("\\|");

                            // Get the destination node of the this edge.

                            String nextId = edgeTokens[0];

                            // Create a tree node for the destination node.

                            TreeNode nextNode = this.treeForNode(nextId);

                            // Get the test line components.
                            String comparison = edgeTokens[1];
                            String value = edgeTokens[2];

                            if ("=".equals(comparison))
                            {
                                if ("?".equals(value))
                                {
                                    // Represents missing data. Associate the
                                    // destination node with a low-priority
                                    // default catch-all.

                                    branch.addCondition(Operation.DEFAULT, feature, value, Condition.LOWEST_PRIORITY,
                                            nextNode);
                                }
                                else
                                {
                                    // Looking for a specific value. Associate
                                    // the
                                    // node with a normal priority "equals"
                                    // condition.

                                    branch.addCondition(Operation.EQUALS, feature, value, Condition.DEFAULT_PRIORITY,
                                            nextNode);
                                }
                            }
                            else
                            {
                                // Associate the node with the relevant numeric
                                // comparison.

                                if ("<=".equals(comparison))
                                    branch.addCondition(Operation.LESS_THAN_OR_EQUAL_TO, feature,
                                            Double.valueOf(value), Condition.DEFAULT_PRIORITY, nextNode);
                                else if (">".equals(comparison))
                                    branch.addCondition(Operation.MORE_THAN, feature, Double.valueOf(value),
                                            Condition.DEFAULT_PRIORITY, nextNode);
                                else if (">=".equals(comparison))
                                    branch.addCondition(Operation.MORE_THAN_OR_EQUAL_TO, feature,
                                            Double.valueOf(value), Condition.DEFAULT_PRIORITY, nextNode);
                                else if ("<".equals(comparison))
                                    branch.addCondition(Operation.LESS_THAN, feature, Double.valueOf(value),
                                            Condition.DEFAULT_PRIORITY, nextNode);
                            }
                        }
                    }

                    return branch;
                }
            }
        }

        throw new TreeNode.TreeNodeException("Unable to find definition for node with ID '" + id + "'.");
    }

    /**
     * Constructs a LeafNode from a terminal node containing no children.
     * 
     * @param line
     *            Line representing the leaf node.
     * 
     * @return LeafNode that returns the prediction found in the provided line.
     */

    private TreeNode leafNodeForLine(String line)
    {
        // Extract the label components: prediction + accuracy information.

        String[] tokens = line.split("\\\"");

        String label = tokens[1];
        String[] labelTokens = label.split(" \\(");

        HashMap<String, Object> prediction = new HashMap<>();
        prediction.put(LeafNode.PREDICTION, labelTokens[0]);

        // Calculate the accuracy information.

        String remainder = labelTokens[1].substring(0, labelTokens[1].length() - 1);

        double accuracy = 1.0;
        double coverage = 0;
        double incorrect = 0;

        if (remainder.contains("/"))
        {
            // We have some instances that the tree has misclassified here in
            // the past.

            String[] remainderTokens = remainder.split("/");

            coverage = Double.parseDouble(remainderTokens[0]);
            incorrect = Double.parseDouble(remainderTokens[1]);

            accuracy = (coverage - incorrect) / coverage;
        }
        else
        {
            // Node is 100% accurate. Let's just count the number of instances
            // covered by the node.

            coverage = Double.parseDouble(remainder);
        }

        // Add mandatory key and values.

        prediction.put(LeafNode.PREDICTION, labelTokens[0]);
        prediction.put(LeafNode.ACCURACY, accuracy);

        // Add additional format-specific metadata.

        prediction.put(WekaJ48TreeParser.NUM_INSTANCES, coverage);
        prediction.put(WekaJ48TreeParser.NUM_INCORRECT, incorrect);

        return new LeafNode(prediction);
    }
}