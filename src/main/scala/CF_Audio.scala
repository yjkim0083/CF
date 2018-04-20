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

    val model = ALS.trainImplicit(trainData, 10, 5, 0.01, 1.0)

    //res32: Array[(Int, String)] =
    // Array(
    // (1001440,0.5518826842308044, 0.392406702041626, 0.17953625321388245, -0.2173195481300354, -0.009500909596681595, 0.4799310564994812, -0.25928589701652527, 0.025516510009765625, -0.29204070568084717, -0.4323274791240692),
    // (1007308,-0.24808329343795776, 0.38650116324424744, -0.16161811351776123, -0.2690929174423218, -0.0362609438598156, -0.4382878839969635, -0.38192984461784363, -0.4828607738018036, -0.2145417183637619, 0.310880184173584),
    // (1021940,-0.18205277621746063, 0.01867692731320858, -0.12258598953485489, 0.4066945016384125, -0.004737786948680878, 0.07673570513725281, -0.3939237892627716, -0.3368104100227356, -0.08418044447898865, 0.17686977982521057))

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
