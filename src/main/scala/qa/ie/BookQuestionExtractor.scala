package qa.ie

import java.io.File
import io.Source
import scala.util.Random
import scala.util.{Try,Success,Failure}
import edu.arizona.sista.processors.fastnlp.FastNLPProcessor
import edu.arizona.sista.processors.{Document, Sentence}

/** Enumerates the documents over a "Becky's" book text file
 *  @Author Enrique Noriega <enoriega@email.arizona.edu>
 */
object BookQuestionExtractor extends App{
    val filePath = args(0)

    val proc = new FastNLPProcessor(withDiscourse=false)

    val k = 5

    // Filter for questions and incomplete sentences
    val marks = Set("?", ":", ";")

    // Parse the lines and annotate the docs
    val rawLines:Seq[String] = Source.fromFile(filePath).getLines.toSeq

    val lines = rawLines.filter{
      line =>
        val last = line.takeRight(1)
        !marks.contains(last)
    }



    val paragraphs:Map[String, Document] = lines.map{
      l =>
        val tokens = l.split("\t")
        (tokens(0), tokens(1))
    }.groupBy{
      case (id, txt) =>
        val key = id.split('.').dropRight(1).mkString(".")
        key
    }.mapValues{
      tuples =>
        val sentences = tuples.map(_._2)
        val doc = proc.annotateFromSentences(sentences)
        doc
    }


    val d = paragraphs.values.toList

    val questions:Map[(Document, Int), ArtificialQA] = d.flatMap{
      doc =>
      try{
        doc.sentences.zipWithIndex map {
          case (sen, ix) =>
            (doc, ix) -> makeQuestion(sen, ix, doc)
        }
      } catch{
        case _:Throwable => Seq()
      }
    }.filter{
      case (ix, q) =>
        q match {
          case n:NoQA => false
          case _ => true
        }
    }.toMap

    // Add alternatives (incorrect answers for each artificial qa)
    def range(i:Int, k:Int = 5) = (i-k to i-1).filter(_ >= 0) ++ (i+1 to i+k)
    val questionsWithAlternatives:Iterable[ArtificialQA] = for(((d, i), qa) <- questions) yield {
      val alternatives = Random.shuffle(range(i, 10).map{
        j =>
          questions.lift((d, j))
      }.collect{case Some(a) => a}).take(3).map(_.answer).filter(_.toLowerCase != qa.answer.toLowerCase).toSeq
      ArtificialQA(qa.qtype, qa.question, qa.answer, alternatives)
    }

    for(question <- questionsWithAlternatives){
      println(s"${question.question}\t${question.answer}\t" + question.alternatives.mkString("\t"))
    }

    def makeQuestion(sen:Sentence, index:Int, context:Document):ArtificialQA = {

      val stopLemmas = Set("figure", "table", "example", "chapter")

      sen.stanfordBasicDependencies match {
        case Some(deps) =>
          val root:Int = deps.roots.head // I assume there's a single root
          val tags:Seq[String] = sen.tags match { case Some(t) => t; case None => Seq()}
          val lemmas:Seq[String] = sen.lemmas match { case Some(t) => t; case None => Seq()}
          // If the root is a verb, go ahead
          if (tags(root).startsWith("VB")){

            val allEdges = deps.outgoingEdges
            val edges = allEdges(root)

            // Find the subject
            val subj = edges filter (e => e._2 == "nsubj")
            if(subj.length == 1){
                // Build the question
                val verb = sen.lemmas.get(root)
                val words = sen.words
                val qType = s"NNP"

                val answerNums = expand(subj(0)._1, allEdges)

                val answerTags = answerNums.map(tags).toSet
                val filteredAnswerTags = answerNums.filter{
                  i =>
                    !stopLemmas.contains(lemmas(i).toLowerCase)
                }.map(tags)

                // Ignore those that only contain a preposition or a determiner
                val filtered = Set("PRP", "DT")
                if(answerTags.size == 1 && filtered.contains(answerTags.head)){
                  new NoQA
                }
                // Filter out those that have answer stop words
                else if(!filteredAnswerTags.map(_(0)).toSet.contains('N')){
                  new NoQA
                }
                else{
                  // Filter out all those questions that don't have a noun and a verb
                  val answerNumsSet = answerNums.toSet
                  val questionNums = (0 to words.size - 1).filter(!answerNumsSet.contains(_))

                  val f = questionNums.map(tags(_)(0)).toSet

                  if(f.contains('N') && f.contains('V')){

                    val answer = answerNums.sorted.map(words).mkString(" ")

                    val question = (0 to words.size - 1).map{
                      ix =>
                        if(!answerNumsSet.contains(ix))
                          words(ix)
                        else
                          List.fill(words(ix).size)("_").mkString

                    }.mkString(" ")

                    // TODO: Add justification
                    val justification = ""//justify(sen, index, context)


                    ArtificialQA(qType, question, answer)


                  }
                  else{
                    new NoQA
                  }

                }

            }
            else
              new NoQA
          }
          else
            new NoQA
        case None => new NoQA
      }
    }

    def expand(ix:Int, edges:Array[Array[(Int, String)]]):Seq[Int] = {
      def helper(ix:Int, edges:Array[Array[(Int, String)]]):Seq[Int] = {
        // Select the nodes that traverse the tree using inorder walk
        val e = edges(ix).sortWith(_._1 < _._1)

        if(e.length  == 0)
          Seq(ix)
        else{
          val pre = e.filter(_._1 < ix) flatMap {
            x:(Int, String) =>
              helper(x._1, edges)
          }

          val post = e.filter(_._1 > ix) flatMap {
            x:(Int, String) =>
              helper(x._1, edges)
          }
          pre ++ Seq(ix) ++ post
        }
      }

      val nums = helper(ix, edges)
      nums
    }
}