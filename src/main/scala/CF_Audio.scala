import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.{SparkConf, SparkContext}

object CF_Audio {

//  val userMusicDataPath = "/audio/user_artist_data_small.txt"
//  val musicNameDataPath = "/audio/artist_data_small.txt"
//  val musicAliasDataPath = "/audio/artist_alias_small.txt"

  val userMusicDataPath = "/Users/yjkim/dev/git/mobigen/Project1/user_artist_data_small.txt"
  val musicNameDataPath = "/Users/yjkim/dev/git/mobigen/Project1/artist_data_small.txt"
  val musicAliasDataPath = "/Users/yjkim/dev/git/mobigen/Project1/artist_alias_small.txt"

  val conf = new SparkConf().setAppName("CollaborativeFiltering").setMaster("local[*]")
  val sc = new SparkContext(conf)

  def main(args: Array[String]): Unit = {

    val rawUserArtistData = sc.textFile(userMusicDataPath)
    val rawArtistData = sc.textFile(musicNameDataPath)
    val rawArtistAlias = sc.textFile(musicAliasDataPath)

    val artistById = rawArtistData.flatMap(line => {
      val (id, name) = line.span(_ != '\t')
      if (name.isEmpty) {
        None
      } else {
        try {
          Some((id.toInt, name.trim))
        } catch {
          case e: NumberFormatException => None
        }
      }
    })

    println("========== artistById count: %s".format(artistById.count()))

    Thread.sleep(1000)

    val artistAlias = rawArtistAlias.flatMap(line => {
      val tokens = line.split('\t')
      if (tokens(0).isEmpty) {
        None
      } else {
        Some((tokens(0).toInt, tokens(1).toInt))
      }
    }).collectAsMap()

    println("========== artistAlias count: %s".format(artistAlias.size))

    Thread.sleep(1000)

    val bArtistAlias = sc.broadcast(artistAlias)
    val trainData = rawUserArtistData.map(line => {
      val Array(userId, artistId, count) = line.split(' ').map(_.toInt)
      val finalArtistId = bArtistAlias.value.getOrElse(artistId, artistId)
      Rating(userId, finalArtistId, count)
    }).cache()

    println("========== trainData collect")
    trainData.collect()

    Thread.sleep(1000)

    val model = ALS.trainImplicit(trainData, 10, 5, 0.01, 1.0)

    val rawArtistsForUser = rawUserArtistData.map(_.split(' ')).filter({
      case Array(user, _, _) => user.toInt == 2093760
    })
    rawArtistsForUser.collect()

    Thread.sleep(1000)

    val existingProducts = rawArtistsForUser.map({
      case Array(_, artist, _) => artist.toInt
    }).collect.toSet

    artistById.filter({
      case (id, name) => existingProducts.contains(id)
    }).values.collect().foreach(println)

//    val recommendations = model.recommendProducts(2093760, 5)
//    recommendations.foreach(println)
//
//    val recommendedProductIDs = recommendations.map(_.product).toSet
//
//    artistById.filter({
//      case (id,name) => recommendedProductIDs.contains(id)
//    }).values.collect().foreach(println)
  }
}
