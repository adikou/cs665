package qa.input

import scala.collection.JavaConverters._
import java.io.File
import org.apache.commons.io.FileUtils

// Reads the questions from a file
// THIS MUST BE A VALIDATION OR TEST FILE
// NOT THE TRAINING FILE (it has one more column)

class InputReader(file: File) {
  // Read the lines from the file
  val lines = FileUtils.readLines(file).asScala.drop(1) // get rid of the header

  def escapeString(s: String, chars: String = "/\\+-&&||!(){}[]^\"~*?:"): String = {
    chars match {
      case "" => s
      case _ => escapeString(s.replace(s"${chars(0)}", s" "), chars.drop(1))
    }
  }

  def questions: Seq[Question] = lines map {
    line =>
      // Escape special characters
      val t = escapeString(line).split('\t')
      t.length match {
        case 6 =>
          Question(t(0).toInt, t(1), Seq(Answer(t(2), 0), Answer(t(3), 0), Answer(t(4), 0), Answer(t(5), 0)))
        case 7 =>
          val choice = t(2) match {
            case "A" => 0
            case "B" => 1
            case "C" => 2
            case "D" => 3
          }
          Question(t(0).toInt, t(1), Seq(Answer(t(3), 0), Answer(t(4), 0), Answer(t(5), 0), Answer(t(6), 0)), Some(choice))
        case num =>
          throw new RuntimeException(s"${file.getName} has $num columns")
      }

  }

  def qTypeQuestions: Seq[Question] = lines map {
    line =>
      // Escape special characters
      val t = escapeString(line).split('\t')
      t.length match {
        case 7 =>
          //id:Int, question:String, choices:Seq[Answer], rightChoice:Option[Int] = None
          val choice = t(0).toInt
          Question(-1, t(1), Seq(Answer(t(3), 0), Answer(t(4), 0), Answer(t(5), 0), Answer(t(6), 0)), Some(choice))
        case num =>
          throw new RuntimeException(s"${file.getName} has $num columns")
      }

  }
}
