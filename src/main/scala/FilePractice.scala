import scala.io.Source

object FilePractice {

  val userMovieDataPath = "/Users/yjkim/user-video-cnt-wtime.txt"
  //val movieNameDataPath = "/cf-table/video-title"

  def main(args: Array[String]): Unit = {

    val userMovieData = Source.fromFile(userMovieDataPath)
    val lines: Iterator[String] = userMovieData.getLines()


  }
}
