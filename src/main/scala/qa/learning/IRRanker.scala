package qa.learning

import sys.process._
import java.io.File
import java.util.UUID
import qa.input._
import qa.ir._
import scala.collection.JavaConverters._
import org.apache.commons.io.{ FileUtils, FilenameUtils }
import com.typesafe.config.ConfigFactory

// IR model for reranking
// Assumes svm_rank is in the path
class IRRanker extends Ranker{

  // File with the weights of svm_rank
  var modelFile:File = null

  // Runs svm_classify and resorts the list with the new order
  def rerank(list:Seq[DataPoint]):Seq[DataPoint] = list

  // Generates features out of the IR query
  def createFeatureVector(question:String,
       choice:String, queryRes:QueryResult):Seq[Double] = Seq(1, 2, 3, 10)

  // Trains svm_rank with this questions given this index
  def train(questions:Seq[Question], index:IRIndex, outputFile:Option[File] = None){
    // Writes an svm_rank training file
    modelFile = outputFile match {
      case Some(f) => f
      case None => File.createTempFile(UUID.randomUUID.toString, "model")
    }

    // Generate a svm_rank_train file from the questions
    val trainingLines:Seq[String] = questions2svmRankLines(questions, index)

    // Write them to a training file
    val trainingFile = File.createTempFile(UUID.randomUUID.toString, "train")
    FileUtils.writeLines(trainingFile, trainingLines.asJavaCollection)

    // Call svm_rank_train
    val exitCode = s"svm_rank_train -c 3 ${trainingFile.getCanonicalPath} ${modelFile.getCanonicalPath}".!

    if(exitCode != 0){
      throw new RuntimeException("Error running svm_rank_train!!")
    }
  }

  // "Loads" the model from a file
  def load(file:File){
    this.modelFile = file
  }

  // Creates a sequence of lines in svm_rank file from a seq of questions
  private def questions2svmRankLines(q:Seq[Question], index:IRIndex) = {
    val ret = for((question, id) <- q.zipWithIndex) yield {

        val choice = question.rightChoice match {
          case Some(i) => i
          case None => 0 // In case this is testing data
        }

        val points = makeDataPoint(question, index)

        for(point <- points) yield {
          val target = if(point.answerChoice == choice) 2 else 1

          val sb = new StringBuilder(s"$target qid:$id")

          for((feature, j) <- point.features.zipWithIndex){
            sb ++= s" ${j+1}:$feature"
          }

          sb.toString
        }
    }
    ret.flatten
  }

}

// Entry point to train svm_rank
class TrainIRRanker extends App {

  val config =
      if (args.size < 2) ConfigFactory.load()
      else ConfigFactory.parseFile(new File(args(1))).resolve()

  val outFile = new File(args(0))

  val ranker = new IRRanker
  println(s"Training the IR Ranker and storing the model in ${args(0)}")

  println("Loading training data...")
  val reader = new InputReader(new File(config.getString("trainingFile")))

  println("Loading IR Index...")
  val index = new WikipediaIndex("simple_wiki", config)

  println("Training...")
  ranker.train(reader.questions, index, Some(outFile))

  println("Done.")
}
