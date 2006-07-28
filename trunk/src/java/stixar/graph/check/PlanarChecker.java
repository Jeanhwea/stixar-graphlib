package stixar.graph.check;

import stixar.graph.UGraph;
import stixar.graph.Node;
import stixar.graph.Edge;

import stixar.graph.BasicDigraph;
import stixar.graph.BasicNode;
import stixar.graph.BasicEdge;

import stixar.graph.attr.NodeMap;
import stixar.graph.attr.EdgeMap;

import stixar.graph.search.DFS;
import stixar.graph.order.NodeOrder;

import stixar.util.ListCell;
import stixar.util.CList;
import java.util.ArrayList;

/**
   Implementation of the Boyer and Myrvold linear time
   planarity checker. 
   <p>
   A planarity checker determines whether or not the vertices an undirected graph 
   can be mapped to points in the plane such that the edges do not cross.  
   </p>
   <br></br>Reference: <a href="http://jgaa.info/accepted/2004/BoyerMyrvold2004.8.3.pdf">
   On the cutting edge: Simplified O(n) Planarity by Edge addition</a>.
*/
/*
   Opinion: of all the linear planarity checkers, this is the only one
   with a clear presentation.  The Tarjan & Hopcroft (TH) algorithm has an
   unambiguous but quite dense presentation in Melhorn's text (though the embedding
   phase was revised in a later paper, stating the presentation was incorrect 
   in the text for the embedding phase).  The Shih and Hsu (SH)
   algorithm is somehow widely accepted to be simpler than TH algorithm, 
   but all explanations I have found are either full of ambiguities (such 
   as the original manuscript) or fail to actually give an algorithm that
   works without significant proofs and extensions to be done by the reader
   (lecture notes from a GA tech math person).  As SH algorithm is reportedly
   simpler than the others (PQ trees, PC Trees, etc), I was scared off from
   looking more closely at the others, though it may be just the presentations
   rather than the algorithms I find less than clear.
 */
public class PlanarChecker
    implements UGraphChecker
{


    protected static class NodeInfo
    {
        Node node;
        // dfs discover time.
        int dfsNum;
        // edge leading to dfs parent.
        Edge parent;
        // least dfs numbered vertex reachable from a descendant of
        // this vertex.
        int lowPoint;
        //
        // of all adjacent back edges, this points to the one with 
        // the smallest dfs number.
        //
        int leastAncestor;
        //
        // face edge lists, instead of distinguishing left and
        // right, they are just 1 or 2 since the algorithm 
        // allows a dynamic sortof of orientiation for iteratiing
        // over edges of a face.
        //
        ListCell<Edge> faceCell1;
        ListCell<Edge> faceCell2;
        //
        // used in walkUp/walkDown for identifying pertinent subgraphs
        // and merging an edge.
        boolean backEdgeFlag;
        boolean visited;

        //
        // linked list of dfs children edges, in order
        // of increasing lowPoint
        //
        CList<NodeInfo> dfsSepChildren;
        // 
        // pointer into above list for this node's parent.
        //
        ListCell<NodeInfo> sepCell;
    }

    protected static class EdgeInfo
    {
        Edge edge;
    }
    
    protected NodeMap<NodeInfo> niMap;
    protected EdgeMap<EdgeInfo> eiMap;
    protected BasicDigraph embedding;

    public boolean check(UGraph g)
    {
        /*
          Euler's formula quick check based on size of graph,
          and lack of embedding into K3,3 or K5 based on nodesize.
         */
        int nsz = g.nodeSize();
        if (nsz <= 4)
            return true;
        if (g.edgeSize() > 3 * nsz + 6)
            return false;
        /*
          OK, create embedding
        */
        
        /*
          The rest of the algorithm..
         */
        NodeOrder dfsOrder = init(g);
        dfsOrder.reverse();
        CList<Edge> dfsChildren = new CList<Edge>();
        CList<Edge> downBackEdges = new CList<Edge>();
        for (Node n : g.nodes(dfsOrder)) {
            NodeInfo ni = n.get(niMap);

            for (Edge e = n.out(); e != null; e = e.next()) {
                Node t = e.target();
                NodeInfo ti = t.get(niMap);
                if (ti.parent == e) {
                    dfsChildren.add(e);
                    embed(e);
                } else if (ti.dfsNum > ni.dfsNum) {
                    downBackEdges.add(e);
                }
            }

            for (Edge e : downBackEdges) {
                walkUp(e);
            }
            for (Edge e : dfsChildren) {
                walkDown(e);
            }
            for (Edge e : downBackEdges) {
                
            }
            dfsChildren.clear();
            downBackEdges.clear();
        }
        return false;
    }

    protected void walkUp(Edge e)
    {
    }

    protected void walkDown(Edge e)
    {
    }

    protected void embed(Edge e)
    {
    }

    protected NodeOrder init(UGraph g)
    {
        //
        // bucket-sort nodes by lowpoint, init the buckets and hand it
        // to the dfs visitor.
        //
        int nsz = g.nodeSize();
        ArrayList<CList<NodeInfo>> sepArray = new ArrayList<CList<NodeInfo>>(nsz);
        for (int i=0; i<nsz; ++i) {
            sepArray.add(null);
        }
        //
        // collect low points and least ancestors and do lowpoint sort.
        //
        DFSInit ivis = new DFSInit(sepArray);
        DFS dfs = new DFS(g, ivis);
        dfs.run();
        
        //
        // construct dfsSepChildren based on lowpoint sort.
        //
        for (CList<NodeInfo> nList: sepArray) {
            if (nList != null) {
                for (NodeInfo ni : nList) {
                    if (ni.parent != null) {
                        NodeInfo pi = ni.parent.source().get(niMap);
                        ni.sepCell = pi.dfsSepChildren.append(ni);
                    }
                }
            }
        }
        // maybe help jvm gc
        sepArray.clear();
        return dfs.order();
    }

    protected class DFSInit extends DFS.Visitor
    {
        protected int dfsNum;
        protected ArrayList<CList<NodeInfo>> sepArray;

        DFSInit(ArrayList<CList<NodeInfo>> sA)
        {
            dfsNum = 0;
            sepArray = sA;
        }

        public void discover(Node n)
        {
            NodeInfo ni = n.get(niMap);
            ni.lowPoint = dfsNum;
            ni.dfsNum = dfsNum;
            ni.leastAncestor = dfsNum++;
        }

        public void treeEdge(Edge e)
        {
            NodeInfo ni = e.target().get(niMap);
            ni.parent = e;
        }

        public void backEdge(Edge e)
        {
            NodeInfo si = e.target().get(niMap);
            NodeInfo ti = e.target().get(niMap);
            if (ti.dfsNum < si.leastAncestor)
                si.leastAncestor = ti.dfsNum;
        }

        public void finish(Node n)
        {
            NodeInfo ni = n.get(niMap);
            if (ni.parent != null) {
                NodeInfo pi = ni.parent.source().get(niMap);
                if (ni.lowPoint < pi.lowPoint) 
                    pi.lowPoint = ni.lowPoint;
            }
            CList<NodeInfo> niList = sepArray.get(ni.lowPoint);
            if (niList == null) {
                niList = new CList<NodeInfo>();
                sepArray.set(ni.lowPoint, niList);
            }
            niList.add(ni);
        }
    }

}
