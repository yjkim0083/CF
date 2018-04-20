
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

object LastFM {

  val conf = new SparkConf().setAppName("LastFM").setMaster("local[*]")
  val sc = new SparkContext(conf)
  sc.setLogLevel("WARN")
  val sqlContext = new SQLContext(sc)

  def main(args: Array[String]): Unit = {

//    // user, artist, cnt
//    val rawUserArtistData = sc.textFile("hdfs:///lastfm/user_artist_data.txt")
//    // artist, artist_name
//    val rawArtistData = sc.textFile("hdfs:///lastfm/artist_data.txt")
//    // artist
//    val rawArtistAlias = sc.textFile("hdfs:///lastfm/artist_alias.txt")

    // user, artist, cnt
    val rawUserArtistData = sc.textFile("/Users/yjkim/lastFM/user_artist_data.txt")
    // artist, artist_name
    val rawArtistData = sc.textFile("/Users/yjkim/lastFM/artist_data.txt")
    // artist
    val rawArtistAlias = sc.textFile("/Users/yjkim/lastFM/artist_alias.txt")

    import sqlContext.implicits._

    val artistById = rawArtistData.flatMap { line =>
      val (id, name) = line.span(_ != '\t')
      if(name.isEmpty) {
        None
      } else {
        try {
          Some((id.toInt, name.trim))
        } catch {
          case _ : NumberFormatException => None
        }
      }
    }.toDF("id", "name")

    val artistAlias = rawArtistAlias.flatMap { line =>
      val Array(artist, alias) = line.split('\t')
      if(artist.isEmpty) {
        None
      } else {
        Some((artist.toInt, alias.toInt))
      }
    }.collect().toMap

    val bArtistAlias = sc.broadcast(artistAlias)
    val trainData = buildCounts(rawUserArtistData, bArtistAlias)
    trainData.cache

    println("=====================================")
    println("### train data")
    trainData.show

    val model = new ALS().
      setSeed(Random.nextLong()).
      setImplicitPrefs(true).
      setRank(10).
      setRegParam(0.01).
      setAlpha(1.0).
      setMaxIter(5).
      setUserCol("user").
      setItemCol("artist").
      setRatingCol("count").
      setPredictionCol("prediction").
      fit(trainData)

    model.write.overwrite().save("/Users/yjkim/lastFM/model")

    model.userFactors.show(truncate = false)
    model.itemFactors.show(truncate = false)

    val userId = 2093760 //args(0).toInt
    val toRecommend = model.itemFactors.select($"id".as("artist")).withColumn("user", lit(userId))
    println("=====================================")
    println("### toRecommend data")
    toRecommend.show



    val topRecommendations = makeRecommendations(model, userId, 10)
    val recommendedArtistIDS = topRecommendations.select("artist").as[Int].collect()
    println("=====================================")
    println("### listenArtist data")
    val listenArtistIDS = trainData.select("artist").filter("user == '2093760'").as[Int].collect()
    artistById.filter($"id".isin(listenArtistIDS:_*)).collect().foreach(println)
    println("### intersect recommendedArtistIDS data")
    val intersectRecommendArtistIDS = recommendedArtistIDS.intersect(listenArtistIDS)
    artistById.filter($"id".isin(intersectRecommendArtistIDS:_*)).collect().foreach(println)
    println("### diff recommendedArtistIDS data")
    val realRecommendArtistIDS = recommendedArtistIDS.diff(listenArtistIDS)
    artistById.filter($"id".isin(realRecommendArtistIDS:_*)).collect().foreach(println)

  }

  def buildCounts(rawUserArtistData: RDD[String], bArtistAlias: Broadcast[Map[Int, Int]]): DataFrame = {
    import sqlContext.implicits._

    rawUserArtistData.map { line =>
      val Array(userID, artistID, count) = line.split(' ').map(_.toInt)
      val finalArtistID = bArtistAlias.value.getOrElse(artistID, artistID)
      (userID, finalArtistID, count)
    }.toDF("user", "artist", "count")
  }

  def makeRecommendations(model: ALSModel, userId: Int, howMany: Int): DataFrame = {
    import sqlContext.implicits._

    val toRecommend = model.itemFactors.select($"id".as("artist")).withColumn("user", lit(userId))
    model.transform(toRecommend).select("artist", "prediction")
        .orderBy($"prediction".desc)
        .limit(howMany)
  }

}
