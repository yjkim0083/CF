import org.apache.spark.mllib.recommendation._
import org.apache.spark.{SparkConf, SparkContext}

object CF {

  val userMovieDataPath = "/cf-table/user-video-cnt-wtime.txt"
  val movieNameDataPath = "/cf-table/video-title.txt"

  val conf = new SparkConf().setAppName("CollaborativeFiltering").setMaster("yarn")
  val sc = new SparkContext(conf)

  def main(args: Array[String]): Unit = {

    val userMovieData = sc.textFile(userMovieDataPath)
    val movieData = sc.textFile(movieNameDataPath)

    val movieById = movieData.flatMap(line => {
      val (id, name) = line.span(_ != '\t')
      if(name.isEmpty) {
        None
      } else {
        try {
          Some((id.toInt, name.trim))
        } catch {
          case e: NumberFormatException => None
        }
      }
    })

    val trainData = userMovieData.map( line => {
      val Array(userId, movieId, count, time) = line.split('\t').map(_.toInt)
      Rating(userId, movieId, count)
    }).cache()

    // model
    val model = ALS.trainImplicit(trainData, 10, 5, 0.01, 1.0)

    // 5225200
    val movieForUser = userMovieData.map(_.split('\t')).filter({
      case Array(user,_,_,_) => user.toInt == 5225200
    })

    val existingProducts = movieForUser.map({
      case Array(_,movie,_,_) => movie.toInt
    }).collect().toSet

    movieById.filter({
      case (id,name) => existingProducts.contains(id)
    }).values.collect().foreach(println)

    println("===============================================")
    val recommendations = model.recommendProducts(5225200, 10)
    recommendations.foreach(println)
    println("===============================================")

    val recommendedProductIDs = recommendations.map(_.product).toSet

    println("===============================================")
    movieById.filter({
      case (id, name) => recommendedProductIDs.contains(id)
    }).values.collect().foreach(println)
    println("===============================================")
  }
}
