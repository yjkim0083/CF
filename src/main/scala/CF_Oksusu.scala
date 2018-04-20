import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object CF_Oksusu {

  val conf = new SparkConf().setAppName("CollaborativeFiltering").setMaster("local[*]")
  val sc = new SparkContext(conf)
  sc.setLogLevel("WARN")

  // path의 경로를 읽어 RDD[String] 형태로 리턴
  def readTextFile(path: String): RDD[String] = {
    sc.textFile(path)
  }

  def main(args: Array[String]): Unit = {
    // user, contents, time
    val userMovieData = readTextFile("/Users/yjkim/oksusu_cf/user-video-wtime-length-cnt.txt")
    // contents, content_name
    val movieData = readTextFile("/Users/yjkim/oksusu_cf/video-title.txt")

    // movieId, movieName 형태의 RDD로 구성
    val artistById = movieData.flatMap(line => {
      val tokens = line.split('\t')
      if(tokens.length == 1) {
        None
      } else {
        try {
          Some((tokens(0).toInt, tokens(1).trim))
        } catch {
          case e: NumberFormatException => None
        }
      }
    })

    // userId, movieId, time 형태의 RDD 구성
    val allData = userMovieData.map { line =>
      val Array(userId, movieId, time) = line.split('\t').map(_.toDouble)
      Rating(userId.toInt, movieId.toInt, time * 10)
    }.cache()

    // 전체 데이터중 90%는 traindata, 10%는 testdata
    val Array(trainData, cvData) = allData.randomSplit(Array(0.9, 0.1))
    trainData.cache()
    cvData.cache()

    val allItemIDs = allData.map(_.product).distinct().collect()

    val bAllItemIDs = sc.broadcast(allItemIDs)

    val evaluations =
      for(rank <- Array(10); lambda <- Array(0.05); alpha <- Array(0.01))

        yield {
          println("time: %s, rank: %s, lambda: %s, alpha: %s".format(DateTime.now, rank, lambda, alpha))
          val model = ALS.trainImplicit(trainData, rank, 10, lambda, alpha)
          val auc = areaUnderCurve(cvData, bAllItemIDs, model.predict)
          val rmse = getRmse(allData, model.predict)
          println("time: %s, rank: %s, lambda: %s, alpha: %s, auc: %s, rmse: %s".format(DateTime.now, rank, lambda, alpha, auc, rmse))
          ((rank, lambda, alpha), auc, rmse)
        }

    evaluations.sortBy(_._2).reverse.foreach(println)

//    // model
//    val model = ALS.trainImplicit(trainData, 50, 10, 1.0, 40.0)
//
//    // auc 계산
//    val auc = areaUnderCurve(cvData, bAllItemIDs, model.predict)
//
//    println("================================")
//    println("[AUC]: %s".format(auc))
//    println("================================")

//    // 추천 테스트
//    val rawArtistsForUser = userMovieData.map(_.split('\t')).filter({
//      case Array(user,_,_,_) => user.toInt == 7027440
//    })
//
//    val existingProducts = rawArtistsForUser.map({
//      case Array(_,artist,_,_) => artist.toInt
//    }).collect.toSet
//
//    println("================================")
//    println("user content")
//    artistById.filter({
//      case (id, name) => existingProducts.contains(id)
//    }).values.collect().foreach(println)
//    println("================================")
//
//    println("================================")
//    println("recommend content")
//    val recommendations = model.recommendProducts(7027440, 10)
//    recommendations.foreach(println)
//
//    val recommendedProductIDs = recommendations.map(_.product).toSet
//
//    println("================================")
//    artistById.filter({
//      case (id, name) => recommendedProductIDs.contains(id)
//    }).values.collect().foreach(println)
//    println("================================")

  }

  def getRmse(allData: RDD[Rating], predictFunction: (RDD[(Int,Int)] => RDD[Rating])): Double = {
    // Evaluate the model on rating data
    val usersProducts = allData.map { case Rating(user, product, rate) =>
      (user, product)
    }
    println("=====================")
    println("userProducts ========")
    usersProducts.take(10).foreach(println)

    val predictions =
      predictFunction(usersProducts).map { case Rating(user, product, rate) =>
        ((user, product), rate)
      }

    println("=====================")
    println("predictions ========")
    predictions.take(10).foreach(println)

    val ratesAndPreds = allData.map { case Rating(user, product, rate) =>
      ((user, product), rate)
    }.join(predictions)

    println("=====================")
    println("ratesAndPreds ========")
    ratesAndPreds.take(10).foreach(println)

    val MSE = ratesAndPreds.map { case ((user, product), (r1, r2)) =>
      val err = (r1 - r2)
      err * err
    }.mean()

    println("MSE => %s".format(MSE))
    println("RMSE => %s".format(Math.sqrt(MSE)))
    Math.sqrt(MSE)
  }

  // 각 사용자별로 AUC를 계산하고, 평균 AUC를 반환하는 함수.
  def areaUnderCurve(positiveData: RDD[Rating], bAllItemIDs: Broadcast[Array[Int]], predictFunction: (RDD[(Int,Int)] => RDD[Rating])) = {

    // Positive로 판단되는 결과들, 즉 전체 데이터에서 Cross-validation을 하기 위해 남겨둔
    // 10%의 데이터를 이용하여 Positive한 데이터로 저장한다.
    val positiveUserProducts = positiveData.map(r => (r.user, r.product))

    // Positive 데이터에서 (사용자, 영화ID) 별로 각각의 쌍에 대한 예측치를 계산하고,
    // 그 결과를 사용자별로 그룹화 한다.
    val positivePredictions = predictFunction(positiveUserProducts).groupBy(_.user)

    // 각 사용자에 대한 Negative 데이터(전체 데이터 - Positive 데이터)를 생성한다.
    // 전체 데이터 셋에서 Positive 데이터를 제외한 아이템 중 무작위로 선택한다.
    val negativeUserProducts = positiveUserProducts.groupByKey().mapPartitions {
      // 각 파티션에 대해서 수행한다.
      userIDAndPositiveItemIDs => {
        // 각 파티션 별로 난수 생성기를 초기화
        val random = new Random()
        val allItemIDs = bAllItemIDs.value

        userIDAndPositiveItemIDs.map {
          case (userId, positiveIDs) =>
            val posItemIDSet = positiveIDs.toSet
            val negative = new ArrayBuffer[Int]()
            var i = 0

            // Positive 아이템의 갯수를 벗어나지 않도록하는 범위 내에서
            // 모든 아이템 중 무작위로 아이템을 선택하여
            // Positive 아이템이 아니라면 Negative 아이템으로 간주한다.
            while(i < allItemIDs.size && negative.size < posItemIDSet.size) {
              val itemID = allItemIDs(random.nextInt(allItemIDs.size))
              if(!posItemIDSet.contains(itemID)) {
                negative += itemID
              }
              i += 1
            }
            // (사용자 아이디, Negative 아이템 아이디)의 쌍을 반환한다.
            negative.map(itemID => (userId, itemID))
        }
      }
    }.flatMap(t => t)

    // Nagative 아이템(영화)에 대한 예측치를 계산.
    val negativePredictions = predictFunction(negativeUserProducts).groupBy(_.user)

    // 각 사용자별로 Positive 아이템과 Negative 아이템을 Join 한다.
    positivePredictions.join(negativePredictions).values.map {
      case (positiveRatings, negativeRatings) =>
        // AUC는 무작위로 선별된(처음에 10%를 무작위로 분리하였으므로) Positive 아이템의 Score가
        // 무작위로 선별된(negativeUserProducts를 구할 때 무작위로 선택하였으므로..) Negative 아이템의 Score 보다
        // 높을 확률을 나타낸다. 이때, 모든 Positive 아이템과 Negative 아이템의 쌍을 비교하여 그 비율을 계산한다.
        var correct = 0L
        var total = 0L

        // 모든 Positive 아이템과 Negative 아이템의 쌍에 대해
        for (positive <- positiveRatings; negative <- negativeRatings) {
          // positive 아이템의 예측치가 Negative 아이템의 예측치보다 높다면 옳은 추천 결과
          if(positive.rating > negative.rating) {
            correct += 1
          }
          total += 1
        }
        // 전체 쌍에서 옳은 추천 결과의 비율을 이용한 각 사용자별 AUC 계산
        correct.toDouble / total
    }.mean()
  }
}
