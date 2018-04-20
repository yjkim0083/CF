import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

object CF_Oksusu_DF {

  val conf = new SparkConf().setAppName("CF_Oksusu_DF").setMaster("local[*]")
  val sc = new SparkContext(conf)
  sc.setLogLevel("WARN")
  val sqlContext = new SQLContext(sc)

  def main(args: Array[String]): Unit = {

    // user, contents, time
    //val rawUserContentsData = sc.textFile("/Users/yjkim/oksusu_cf/user-video-wtime-length-cnt.txt")
    val rawUserContentsData = sc.textFile("/Users/yjkim/oksusu_cf/CLIP_cnt.txt")
    // contents, content_name
    val rawContentsData = sc.textFile("/Users/yjkim/oksusu_cf/video-title.txt")

    import sqlContext.implicits._

    val trainData = rawUserContentsData.flatMap { line =>
      val Array(_userId, _contentId, _time) = line.split(',')
      if(_userId.isEmpty) {
        None
      } else {
        try {
          Some((_userId.toInt, _contentId.toInt, _time.toInt))
        } catch {
          case _ : NumberFormatException => None
        }
      }
    }.toDF("user", "content", "time")
    trainData.cache

    println("=====================================")
    println("### train data")
    trainData.show

    val artistById = rawContentsData.flatMap { line =>
      val tokens = line.split('\t')
      if (tokens.length == 2) {
        try {
          Some((tokens(0).toInt, tokens(1).trim))
        } catch {
          case e: NumberFormatException => None
        }
      } else {
        None
      }
    }.toDF("id", "name")

    val model = new ALS().
      setSeed(Random.nextLong()).
      setImplicitPrefs(true).
      setRank(10).
      setRegParam(0.01).
      setAlpha(40.0).
      setMaxIter(10).
      setUserCol("user").
      setItemCol("content").
      setRatingCol("time").
      setPredictionCol("prediction").
      fit(trainData)

    //model.write.overwrite().save("/Users/yjkim/oksusu_cf/model")

    model.userFactors.show(truncate = false)
    model.itemFactors.show(truncate = false)

    val userId = 15547745 //args(0).toInt
    val toRecommend = model.itemFactors.select($"id".as("content")).withColumn("user", lit(userId))
    println("=====================================")
    println("### toRecommend data")
    toRecommend.show

    val topRecommendations = makeRecommendations(model, userId, 10)
    println("=====================================")
    println("### topRecommend")
    topRecommendations.collect().foreach(println)

    val recommendedArtistIDS = topRecommendations.select("content").as[Int].collect()

    println("=====================================")
    println("### view data")
    val viewContentIDS = trainData.select("content").filter("user == '15547745'").as[Int].collect()
    artistById.filter($"id".isin(viewContentIDS:_*)).collect().foreach(println)

    println("### intersect recommendedArtistIDS data")
    val intersectRecommendArtistIDS = recommendedArtistIDS.intersect(viewContentIDS)
    artistById.filter($"id".isin(intersectRecommendArtistIDS:_*)).collect().foreach(println)

    println("### diff recommendedArtistIDS data")
    val realRecommendArtistIDS = recommendedArtistIDS.diff(viewContentIDS)
    artistById.filter($"id".isin(realRecommendArtistIDS:_*)).collect().foreach(println)
  }

  def makeRecommendations(model: ALSModel, userId: Int, howMany: Int): DataFrame = {
    import sqlContext.implicits._

    val toRecommend = model.itemFactors.select($"id".as("content")).withColumn("user", lit(userId))
    model.transform(toRecommend).select("content", "prediction")
      .orderBy($"prediction".desc)
      .limit(howMany)
  }
}
