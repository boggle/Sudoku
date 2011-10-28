package be.bolder.sudoku;

import org.neo4j.graphdb.*;
import org.neo4j.kernel.EmbeddedGraphDatabase;

import java.io.*;
import java.util.Stack;

public class Sudoku {

    public static enum EdgeType implements RelationshipType {
        NODE,  // top node -> field node
        BORDER // field node border for coloring problem
    }

    private static int getValue(Node node) {
        return (Integer) node.getProperty("VALUE");
    }

    private static void setValue(Node node, int value) {
        node.setProperty("VALUE", value);
    }

    public static long buildSudokuGrid(GraphDatabaseService graphDb, int[][] problem) {
        final Transaction tx = graphDb.beginTx();
        try {
            // Build top node
            final Node topNode = graphDb.createNode();

            // Build core sudoku grid
            final Node[][] nodes = buildNodeGrid(graphDb, problem, topNode);

            // Create borders
            buildNodeBorders(nodes);

            final long topId = topNode.getId();
            tx.success();
            return topId;
        }
        finally { tx.finish(); }
    }

    private static void buildNodeBorders(Node[][] nodes) {
        // Could use bidirectional edges but this works as well and is easier to understand
        for (int xCol = 0; xCol < 9; xCol++)
            for (int xRow = 0; xRow < 9; xRow++)
                for (int iCol = 0; iCol < 9; iCol++)
                    for (int iRow = 0; iRow < 9; iRow++)
                        if ((xCol != iCol) || (xRow != iRow))
                            if ((xCol == iCol) ||
                                (xRow == iRow) ||
                                (((xCol / 3) == (iCol / 3)) && ((xRow / 3) == (iRow / 3)))) {
                                nodes[xRow][xCol].createRelationshipTo(nodes[iRow][iCol], EdgeType.BORDER);
                            }
    }

    private static Node[][] buildNodeGrid(GraphDatabaseService graphDb, int[][] problem, Node topNode) {
        final Node[][] nodes = new Node[9][9];

        for (int row = 0; row < 9; row++) {
            nodes[row] = new Node[9];
            for (int col = 0; col < 9; col++) {
                final Node node = graphDb.createNode();
                // Initial color assignment
                setValue(node, problem[row][col]);
                nodes[row][col] = node;
                // Store row and col to be able to retrieve sudoku grid by traversing from top node
                node.setProperty("ROW", row);
                node.setProperty("COL", col);
                topNode.createRelationshipTo(node, EdgeType.NODE);
            }
        }
        return nodes;
    }

    public static void solveSudokuGrid(GraphDatabaseService graphDb, long topNodeId) {
        final Transaction tx = graphDb.beginTx();
        try {
            final Node topNode = graphDb.getNodeById(topNodeId);

            final Stack<Node> work = new Stack<Node>();
            for (Relationship fieldRel : topNode.getRelationships(EdgeType.NODE, Direction.OUTGOING)) {
                final Node fieldNode = fieldRel.getEndNode();
                /* Skip preassigned nodes */
                if (getValue(fieldNode) == 0)
                    work.push(fieldNode);
            }

            // For backtracking previous assignments
            final Stack<Node> assignments = new Stack<Node>();

            workLoop: while (! work.empty()) {
                final Node node = work.pop();
                /* Find next free color */
                colorLoop: for(int color = getValue(node) + 1; color < 10; color++) {
                    for (Relationship border : node.getRelationships(EdgeType.BORDER, Direction.OUTGOING)) {
                        int otherColor = getValue(border.getOtherNode(node));
                        if (otherColor == color)
                            continue colorLoop;
                    }
                    /* Found? push assignment for backtracking and continue */
                    setValue(node, color);
                    assignments.push(node);
                    continue workLoop;
                }
                /* Not found? remove assignment and track back */
                setValue(node, 0);
                work.push(node);
                if (assignments.empty())
                    throw new IllegalArgumentException("No solution found");
                else
                    work.push(assignments.pop());
            }
            tx.success();
        }
        finally { tx.finish(); }
    }


    public static int[][] parseProblem(File file) throws IOException {
        int[][] data = new int[9][9];

        FileInputStream fileIn = new FileInputStream(file);
        DataInputStream dataIn = new DataInputStream(fileIn);
        InputStreamReader reader = new InputStreamReader(dataIn);
        BufferedReader bufReader = new BufferedReader(reader);
        try {
            String line = bufReader.readLine().trim();
            assert("9,9".equals(line));
            while ((line = bufReader.readLine()) != null) {
                String[] comps = line.split(",");
                int row = Integer.parseInt(comps[0]);
                int col = Integer.parseInt(comps[1]);
                int val = Integer.parseInt(comps[2]);
                data[row][col] = val;

            }
        }
        finally {
            bufReader.close();
            reader.close();
            dataIn.close();
            fileIn.close();
        }

        return data;
    }

    public static int[][] extractSolution(GraphDatabaseService graphDb, long topNodeId) {
        final Transaction tx = graphDb.beginTx();
        try {

            // Build top node
            final Node topNode = graphDb.getNodeById(topNodeId);
            final int[][] solution = new int[9][];
            for (int i = 0; i < 9; i++)
                solution[i] = new int[9];

            for (Relationship rel : topNode.getRelationships(EdgeType.NODE, Direction.OUTGOING)) {
                final Node node = rel.getEndNode();
                solution[(Integer)node.getProperty("ROW")][(Integer)node.getProperty("COL")] = getValue(node);
            }

            tx.success();
            return solution;
        }
        finally { tx.finish(); }    }


    public static void main(String[] args) throws IOException {
        final int[][] problem = parseProblem(new File(args[1]));
        final GraphDatabaseService graphDb = new EmbeddedGraphDatabase(args[0]);
        long topNodeId = buildSudokuGrid(graphDb, problem);
        solveSudokuGrid(graphDb, topNodeId);
        final int[][] solution = extractSolution(graphDb, topNodeId);

        // Finally, print it
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                System.out.print(solution[row][col]);
                System.out.print(' ');
            }
            System.out.print("\n");
        }
    }

}