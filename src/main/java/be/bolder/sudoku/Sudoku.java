package be.bolder.sudoku;

import org.neo4j.graphdb.*;
import org.neo4j.kernel.EmbeddedGraphDatabase;

import java.io.*;
import java.util.Stack;

/**
 * Solve Sudoku as a graph coloring problem using Neo4j
 *
 * @author Stefan Plantikow <stefan.plantikow@googlemail.com>
 *
 */
public class Sudoku {

    public static enum FieldNode implements RelationshipType { IS_A }
    public static enum ChromaticBorder implements RelationshipType { EDGE }

    private final GraphDatabaseService graphDb;
    private final long topNodeId;

    /**
     * Calls parseProblem on file and builds the internal sudoku grid
     *
     * @param aGraphDb neo4j instance
     * @param file to read sudoku from
     * @throws IOException if the file cannot be read properly
     */
    public Sudoku(final GraphDatabaseService aGraphDb, final File file) throws IOException {
        this(aGraphDb, parseProblem(file));
    }

    /**
     *
     * @param aGraphDb eo4j instance
     * @param problem sudoku problem indexed by (row, col), 1-based constraints, i.e. 0 means unspecified
     */
    public Sudoku(final GraphDatabaseService aGraphDb, final int[][] problem) {
        graphDb = aGraphDb;
        final Transaction tx = graphDb.beginTx();
        try {
            // Build top node
            final Node topNode = graphDb.createNode();

            // Build core sudoku grid
            final Node[][] nodes = buildNodeGrid(graphDb, problem, topNode);

            // Create borders
            buildNodeBorders(nodes);

            topNodeId = topNode.getId();
            tx.success();
        }
        finally { tx.finish(); }
    }

    /**
     * Construct 9x9 sudoku grid
     */
    private Node[][] buildNodeGrid(GraphDatabaseService graphDb, int[][] problem, Node topNode) {
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
                topNode.createRelationshipTo(node, FieldNode.IS_A);
            }
        }
        return nodes;
    }

    /**
     * Construct edges for solving sudoku as a graph coloring problem
     */
    private void buildNodeBorders(Node[][] nodes) {
        // Could use bidirectional edges but this works as well and is easier to understand
        for (int xCol = 0; xCol < 9; xCol++)
            for (int xRow = 0; xRow < 9; xRow++)
                for (int iCol = 0; iCol < 9; iCol++)
                    for (int iRow = 0; iRow < 9; iRow++)
                        if ((xCol != iCol) || (xRow != iRow))
                            if ((xCol == iCol) ||
                                (xRow == iRow) ||
                                (((xCol / 3) == (iCol / 3)) && ((xRow / 3) == (iRow / 3)))) {
                                nodes[xRow][xCol].createRelationshipTo(nodes[iRow][iCol], ChromaticBorder.EDGE);
                            }
    }

    /**
     * @return true, if the underlying sudoku grid has been solved already
     */
    public boolean isSolved() {
        final Transaction tx = graphDb.beginTx();
        try {
            final Node topNode   = graphDb.getNodeById(topNodeId);
            final boolean result = topNode.hasProperty("SOLVED");
            tx.success();
            return result;
        }
        finally { tx.finish(); }
    }

    /**
     * Solve underlying sudoku grid; does nothing if sudoku has already been solved
     */
    public void solve() {
        final Transaction tx = graphDb.beginTx();
        try {
            final Node topNode  = graphDb.getNodeById(topNodeId);
            final boolean solved = graphDb.getNodeById(topNodeId).hasProperty("SOLVED");

            if (solved) {
                tx.success();
                return;
            }

            final Stack<Node> work = new Stack<Node>();
            for (Relationship fieldRel : topNode.getRelationships(FieldNode.IS_A, Direction.OUTGOING)) {
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
                    for (Relationship border : node.getRelationships(ChromaticBorder.EDGE, Direction.OUTGOING)) {
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
            topNode.setProperty("SOLVED", true);
            tx.success();
        }
        finally { tx.finish(); }
    }

    private int getValue(Node node) {
        return (Integer) node.getProperty("VALUE");
    }

    private void setValue(Node node, int value) {
        node.setProperty("VALUE", value);
    }


    /**
     * @return sudoku solution
     * @thros IllegalStateException if sudoku has not yet been solved
     */
    public int[][] getSolution() {
        final Transaction tx = graphDb.beginTx();
        try {
            // Build top node
            final Node topNode = graphDb.getNodeById(topNodeId);
            if (! topNode.hasProperty("SOLVED"))
                throw new IllegalStateException("Please solve the sudoku first");

            int[][] solution = getSolution_(topNode);
            tx.success();
            return solution;
        }
        finally { tx.finish(); }
    }

    private int[][] getSolution_(Node topNode) {
        final int[][] solution = new int[9][];
        for (int i = 0; i < 9; i++)
            solution[i] = new int[9];

        for (Relationship rel : topNode.getRelationships(FieldNode.IS_A, Direction.OUTGOING)) {
            final Node node = rel.getEndNode();
            solution[(Integer)node.getProperty("ROW")][(Integer)node.getProperty("COL")] = getValue(node);
        }
        return solution;
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

    public String toString() {
        final Transaction tx = graphDb.beginTx();
        try {
            final Node topNode = graphDb.getNodeById(topNodeId);
            if (topNode.hasProperty("SOLVED")) {
                final StringBuffer buf = new StringBuffer();
                formatSolution(buf, getSolution_(topNode));
                tx.success();
                return buf.toString();
            } else {
                return "Unsolved Sudoku";
            }
        }
        finally { tx.finish(); }
    }

    public static void formatSolution(StringBuffer buffer, int[][] solution) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                buffer.append(solution[row][col]);
                buffer.append(' ');
            }
            buffer.append('\n');
        }
    }

    public static void main(String[] args) throws IOException {
        final GraphDatabaseService graphDb = new EmbeddedGraphDatabase(args[0]);
        final Sudoku sudoku = new Sudoku(graphDb, new File(args[1]));
        sudoku.solve();
        System.out.println(sudoku);
    }
}