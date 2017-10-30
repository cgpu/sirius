package de.unijena.bioinf.FragmentationTreeConstruction.ftheuristics;

import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.ms.ft.FGraph;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.ft.Fragment;
import de.unijena.bioinf.ChemistryBase.ms.ft.Loss;

import java.util.*;

public class ExtendedCriticalPathHeuristic {

    protected static final boolean STOP_EARLY = true;

    protected FGraph graph;

    protected BitSet usedColors;
    protected Loss[] usedEdges;
    protected int numberOfSelectedEdges;
    protected ArrayList<Loss> selectableEdges;
    protected double[] criticalPaths;

    public ExtendedCriticalPathHeuristic(FGraph graph) {
        this.graph = graph;
        this.usedColors = new BitSet(graph.maxColor()+1);
        this.usedEdges = new Loss[graph.maxColor()+1];
        this.numberOfSelectedEdges = 0;
        this.selectableEdges = new ArrayList(graph.maxColor()+1);
        this.criticalPaths = new double[graph.numberOfVertices()];
        if (graph.getRoot().getOutDegree()==1) {
            // just add this edge
            usedEdges[numberOfSelectedEdges++] = graph.getRoot().getOutgoingEdge(0);
            //System.out.println("ADD \"\" WITH WEIGHT " + graph.getRoot().getOutgoingEdge(0).getWeight() );
            addSeletableEdgesFor(graph.getRoot().getChildren(0));
        } else {
            addSeletableEdgesFor(graph.getRoot());
        }
        Arrays.fill(criticalPaths, Double.NaN);
    }

    protected void invalidateColor(int color) {
        final Fragment pseudoFragment = new Fragment(0,null);
        pseudoFragment.setColor(color);
        int searchKey = Collections.binarySearch(graph.getFragments(), pseudoFragment,new Comparator<Fragment>() {
            @Override
            public int compare(Fragment o1, Fragment o2) {
                return o1.getColor()-o2.getColor();
            }
        });
        if (searchKey < 0) {
            searchKey = -(searchKey-1);
        } else {
            while (searchKey< graph.numberOfVertices() && graph.getFragmentAt(searchKey).getColor() == color)
                ++searchKey;
        }
        Arrays.fill(criticalPaths, 0, searchKey, Double.NaN);
    }

    public FTree solve() {
        while (findCriticalPaths()) {

        }
        relocateAll();
        return buildSolution();
    }

    public FTree buildSolution() {
        if (numberOfSelectedEdges==0) {
            Fragment bestFrag = null;
            for (Fragment f : graph.getRoot().getChildren()) {
                if (bestFrag==null || bestFrag.getIncomingEdge().getWeight() < f.getIncomingEdge().getWeight() ) {
                    bestFrag = f;
                }
            }
            final FTree t = new FTree(bestFrag.getFormula());
            t.setTreeWeight(bestFrag.getIncomingEdge().getWeight());
            return t;
        }
        final FTree tree = new FTree(usedEdges[0].getTarget().getFormula());
        final HashMap<MolecularFormula, Fragment> fragmentsByFormula = new HashMap<>();
        fragmentsByFormula.put(tree.getRoot().getFormula(), tree.getRoot());
        for (int i=1; i < numberOfSelectedEdges; ++i) {
            final Fragment f = tree.addFragment(fragmentsByFormula.get(usedEdges[i].getSource().getFormula()), usedEdges[i].getTarget().getFormula());
            f.getIncomingEdge().setWeight(usedEdges[i].getWeight());
            fragmentsByFormula.put(f.getFormula(), f);
        }
        double score = 0d;
        for (int i=0; i < numberOfSelectedEdges; ++i) {
            score += usedEdges[i].getWeight();
        }
        tree.setTreeWeight(score);
        return tree;
    }

    /*
     SIMPLE CASE: Graph is layered (i.e. no isotope peaks!)
     */
    public boolean findCriticalPaths() {
        //System.out.println(".....");
        //Arrays.fill(criticalPaths, Double.NaN);
        double bestPathScore = 0d;
        Loss bestLoss = null;
        for (Loss l : selectableEdges) {
            final double criticalScore = recomputeCriticalScore(l.getTarget().getVertexId())+l.getWeight();
            if (criticalScore > bestPathScore) {
                bestPathScore = criticalScore;
                bestLoss = l;
            }
        }
        int maxColor = -1;
        if (bestLoss!=null)
            maxColor = backtrackBestPath(bestLoss,maxColor);
        selectableEdges.clear();
        for (int i=0, n=numberOfSelectedEdges; i < n; ++i) {
            addSeletableEdgesFor(usedEdges[i].getTarget());
        }

        if (maxColor >= 0) invalidateColor(maxColor);
        return bestLoss!=null;
    }

    private int backtrackBestPath(Loss loss,int maxColor) {
        usedEdges[numberOfSelectedEdges++] = loss;
        //System.out.println("ADD " + loss + " WITH WEIGHT " + loss.getWeight() );
        assert usedColors.get(loss.getTarget().getColor())==false;
        usedColors.set(loss.getTarget().getColor());
        final Fragment u = loss.getTarget();
        maxColor = Math.max(u.getColor(), maxColor);
        double bestWeight = criticalPaths[u.getVertexId()];
        if (bestWeight+loss.getWeight()<=0 || STOP_EARLY) {
            return maxColor;
        }
        double heighestWeight = 0d;
        Loss bestLoss = null;
        for (int i=0, n = u.getOutDegree(); i < n; ++i) {
            final Loss uv = u.getOutgoingEdge(i);
            final double weight = criticalPaths[uv.getTarget().getVertexId()] + uv.getWeight();
            if (Double.isNaN(weight)) continue;
            if (weight>=bestWeight) {
                bestLoss = uv; break;
            } else if (weight >= heighestWeight)  {
                heighestWeight = weight; bestLoss = uv;
            }
        }
        if (bestLoss!=null)
            return backtrackBestPath(bestLoss, maxColor);
        return maxColor;
    }

    private double recomputeCriticalScore(int vertexId) {
        if (!Double.isNaN(criticalPaths[vertexId]))
            return criticalPaths[vertexId];
        final Fragment u = graph.getFragmentAt(vertexId);
        criticalPaths[vertexId] = 0d;
        for (int i=0, n = u.getOutDegree(); i < n; ++i) {
            final Loss uv = u.getOutgoingEdge(i);
            if (!usedColors.get(uv.getTarget().getColor())) {
                final double weight = recomputeCriticalScore(uv.getTarget().getVertexId()) + uv.getWeight();
                criticalPaths[vertexId] = Math.max(criticalPaths[vertexId], weight);
            }
        }
        return criticalPaths[vertexId];
    }


    private void addSeletableEdgesFor(Fragment root) {
        for (int i=0, n = root.getOutDegree(); i < n; ++i) {
            final Loss l = root.getOutgoingEdge(i);
            if (!usedColors.get(l.getTarget().getColor())) {
                selectableEdges.add(l);
            }
        }
    }

    protected BitSet usedFragments;
    protected void relocateAll() {
        //double score=0d;
        this.usedFragments = new BitSet(graph.numberOfVertices());
        for (int l=0; l < numberOfSelectedEdges; ++l) {
            usedFragments.set(usedEdges[l].getTarget().getVertexId());
            //score += usedEdges[l].getWeight();
        }
        //int c = 0;
        for (int l=0; l < numberOfSelectedEdges; ++l) {
            //if (relocate(l)) ++c;
            relocate(l);
        }
        Arrays.sort(usedEdges, 0, numberOfSelectedEdges, new Comparator<Loss>() {
            @Override
            public int compare(Loss o1, Loss o2) {
                return o1.getTarget().getColor() - o2.getTarget().getColor();
            }
        });
        /*
        for (int l=0; l < numberOfSelectedEdges; ++l) {
            score -= usedEdges[l].getWeight();
        }
        */
        //System.err.println(c + " => " + (-score));

    }

    protected boolean relocate(int lossId) {
        Loss uv = usedEdges[lossId];
        final Fragment v = uv.getTarget();
        for (int l=0; l < v.getInDegree(); ++l) {
            final Loss x = v.getIncomingEdge(l);
            if (x.getWeight() > uv.getWeight() && usedFragments.get(x.getSource().getVertexId())) {
                uv = x;
            }
        }
        if (usedEdges[lossId]!=uv) {
            usedEdges[lossId] = uv;
            return true;
        } else return false;
    }



}
