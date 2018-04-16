import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object CF2 {

  val userMovieDataPath = "/cf-table/user-video-cnt-wtime.txt"
  val movieNameDataPath = "/cf-table/video-title.txt"

  val conf = new SparkConf().setAppName("CollaborativeFiltering").setMaster("yarn")
  val sc = new SparkContext(conf)

  def main(args: Array[String]): Unit = {

    val userMovieData = sc.textFile(userMovieDataPath)
    val movieData = sc.textFile(movieNameDataPath)

    val allData = userMovieData.map { line =>
      val Array(userId, movieId, count, time) = line.split('\t').map(_.toInt)
      Rating(userId, movieId, count)
    }

    val Array(trainData, cvData) = allData.randomSplit(Array(0.9, 0.1))
//    trainData.cache()
//    cvData.cache()

    val allItemIDs = allData.map(_.product).distinct().collect()
    val bAllItemIDs = sc.broadcast(allItemIDs)

    val model = ALS.trainImplicit(trainData, 20, 5, 0.01, 1.0)

//    val auc = areaUnderCurve(cvData, bAllItemIDs, model.predict)
//    println("==================================================")
//    println(s"[AUC]: $auc")
//    println("==================================================")

    val evaluations =
      for(rank <- Array(10, 50); lambda <- Array(1.0, 0.0001); alpha <- Array(1.0, 40.0))
        yield {
          val model = ALS.trainImplicit(trainData, rank, 10, lambda, alpha)
          val auc = areaUnderCurve(cvData, bAllItemIDs, model.predict)
          ((rank, lambda, alpha), auc)
        }

    evaluations.sortBy(_._2).reverse.foreach(println)
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
