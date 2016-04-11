package qa.paths

import scalax.collection.mutable.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.WkLkDiEdge
import scalax.collection.edge.Implicits._
import scala.collection.mutable._
import com.typesafe.config.{ConfigFactory, Config}
import java.io.File
import java.util.NoSuchElementException
import edu.arizona.sista.embeddings.word2vec.Word2Vec
import edu.arizona.sista.processors.fastnlp.FastNLPProcessor
import edu.arizona.sista.processors.Sentence
import edu.smu.tspell.wordnet._
import qa.input._

object GraphUtils {
  val config = ConfigFactory.load()

  System.setProperty(config.getString("wordnet.db_dir_prop"), 
                     config.getString("wordnet.db_dir"))

  val db = WordNetDatabase.getFileInstance
  val w2v = new Word2Vec(config.getString("w2v.cwb"), None)
  val proc = new FastNLPProcessor()

  def getNode(G: Graph[Node, WkLkDiEdge], s: Node): Option[G.NodeT] = {
    try {
      Some(G get s)
    } catch {
      case e: NoSuchElementException => None
    }
  }

  def getNodes(G: Graph[Node, WkLkDiEdge], word: String, 
    tag: String): Set[G.NodeT] = {
    val pos = tag match {
      case "NN" => SynsetType.NOUN
    }

    val nodes = db.getSynsets(word, pos).foldLeft(Set[G.NodeT]())(
      (nodes, synset) => {
        val node = getNode(G, Node(synset, tag))
        node match {
          case Some(node) => nodes + node
          case None => nodes
        }
      })

    nodes
  }

  def wordSet(sentences: Seq[Sentence]) = {
    sentences.foldLeft(Array[String]())(
    (wordSet, sentence) => {
      val tags = sentence.tags.get
      val words = sentence.words
      val nv = ArrayBuffer[String]()
      
      for(i <- 0 until tags.length)
        if(tags(i).matches("NN(.*)"))// || tags(i).matches("VB(.*)")) 
          nv += words(i)

      wordSet ++ nv
      })
  }


  def annotate(text: String) = proc.annotate(text)

  def nounToSynset(set: Array[NounSynset]) = set.map(_.asInstanceOf[Synset])

  def topKSynsets(ref: Seq[String], sets: Array[Synset], k: Int) = {
    sets.map(s => {
      val defn = wordSet(annotate(s.getDefinition).sentences)
      (s, w2v.sanitizedTextSimilarity(ref.toList, defn.toList))
      
      }).sortWith(_._2 > _._2).slice(0, k).map(_._1)
  }

  /**
   * Returns a mutable graph with pos_tag_annotated synset of words and 
   * key-weighted, key-labeled edges.
   */

  def mkGraph(words: Seq[String]): Graph[Node, WkLkDiEdge] = {
    
    val tags = Array("NN", "VB", "JJ", "RB")
    val nounLinks = Array("hypernym", "hyponym", "instanceHypernym", 
      "instanceHyponym", "memberHolonym", "memberMeronym", "partHolonym",
      "partMeronym", "substanceHolonym", "substanceMeronym")
    val verbLinks = Array("hypernym", "troponym", "verbGroup")
    val adjLinks = Array("similar", "related")
    
    val G: Graph[Node, WkLkDiEdge] = Graph()
    
    // Add NounSynsets
    words.foreach(word => {
      val nounSynsets = db.getSynsets(word, SynsetType.NOUN)
      val topKsets = topKSynsets(words, nounSynsets, 1)
        .map(_.asInstanceOf[NounSynset])
      topKsets.foreach(set => { 
          val s = Node(set, "NN")
          G += s
          
          populateGraph(G, s, "NN", words)
      }) 
    })

    // Add VerbSynsets


    // Add AdjectiveSynsets


    // Add AdverbSynsets

    G
  }

  def addEdges(G: Graph[Node, WkLkDiEdge])(
    Q: Queue[G.NodeT],
    u: G.NodeT,
    set: Array[Synset],
    link: String,
    tag: String) = {

    set.foreach(s => {
      val v = Node(s, tag)
      G += ((Node(u.synset, tag) ~%#+#> v)(0, link))
      if(link equals "hypernym")
        G += ((v ~%#+#> (Node(u.synset, tag)))(0, "hyponym"))
      if(v.color == Node.WHITE) {
        v.color = Node.GRAY
        Q.enqueue(getNode(G, v).get)
      }
      //println(s"Graph size : ${G.order}, ${G.graphSize}")
    })
  }

  def populateGraph(G: Graph[Node, WkLkDiEdge],
    s: Node,
    tag: String,
    words: Seq[String]) = {
    
    val Q = Queue[G.NodeT]()
    val sNode = getNode(G, s).get
    sNode.color = Node.GRAY
    Q.enqueue(sNode)
    
    while(!Q.isEmpty) {
      val u = Q.dequeue()
      tag match {
        case "NN" =>
          val nouns = u.synset.asInstanceOf[NounSynset]
          val hypernyms = nounToSynset(nouns.getHypernyms)
          val topKsets = topKSynsets(words, hypernyms, 1)
          addEdges(G)(Q, u, topKsets, "hypernym", tag)
          u.color = Node.BLACK
      }
    }
  }  

  def genAllPaths(G: Graph[Node, WkLkDiEdge], v: Node, t: Node) = {
    val paths = Queue[Path]()
    def genAllPathsHelper(G: Graph[Node, WkLkDiEdge], p: Path,
      visited: Set[Node], v: Node, t: Node): Unit = {
      
      val u = p.top
      u match {
        case Some(u) => val uE = getNode(G,v).get.edges.find(_._2.equals(u)).get
                        p.push(v, Some((uE.label.toString, uE.weight)))
        case None => p.push(v, None)
      }

      
      visited += v

      if(v == t) paths += new Path().copy(p.elems)
      else {
        getNode(G, v).get.outNeighbors.foreach(w => 
          if(!visited.contains(w)) genAllPathsHelper(G, p, visited, w, t))
      }

      p.pop
      visited -= v
    }

    genAllPathsHelper(G, new Path, Set[Node](), v, t)
    paths
  }
}