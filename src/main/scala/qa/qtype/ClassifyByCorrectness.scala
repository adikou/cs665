package qa.qtype

import java.io._

import edu.arizona.sista.utils.StringUtils
import qa.voting.{Ranker, Voter}
import qa.input._

/**
  * Created by bsharp on 2/19/16.
  */


object ClassifyByCorrectness {

  def main(args:Array[String]): Unit = {
    val props = StringUtils.argsToProperties(args)

    // 1. Load in Questions (Dev)
    //val questionsFile = "/data/nlp/corpora/AriResources/Kaggle/training_set.506dv.tsv"
    val questionsFile = "/data/nlp/corpora/AriResources/Kaggle/training_set.qtype.1000dv.tsv"
    val questions = new InputReader(new File(questionsFile)).questions

    // 2. Load in the results of the ranker (on the same questions)
    val qidLexicon = Voter.buildQIDLexicon(questions)
    //val rankerFile = "/home/bsharp/kaggle/rankers/ir_en_wiki.out"
    val rankerFile = "/home/bsharp/kaggle/rankers/qType/ir_en_wiki.tr1500-dv1000.out"
    val ranker = new Ranker(Voter.parseTSV(rankerFile, qidLexicon), "ranker")

    // 3. Score the questions and label accordingly
    val labels = new Array[Int](questions.length)
    for (qid <- questions.indices) {
      val scores = ranker.scores(qid)
      val maxScore = scores.max
      val correct = questions(qid).rightChoice.getOrElse(0)
      // TODO: how to handle ties? Here I am calling ties good enough...!
      val label:Int = if (scores(correct) == maxScore) 1 else 0
      labels(qid) = label
    }

    // 4. Store the classified questions
    val outputFile = "/home/bsharp/kaggle/qtype/questions_1000_byIREngWiki_mar5.txt"
    val pw = new PrintWriter(outputFile)
    for (qid <- questions.indices) {
      pw.println(labels(qid) + "\t" + questions(qid).toString())
    }
    pw.close()

  }

}
