import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.recommendation.{ALS, Rating}

object SparkML {

  val conf = new SparkConf().setAppName("SparkML").setMaster("local[*]")
  val sc = new SparkContext(conf)

  def main(arg: Array[String]): Unit = {
    // Load and parse the data
    val data = sc.textFile("/Users/yjkim/user-video-cnt-wtime.txt")
    val ratings = data.map(_.split('\t') match { case Array(user, item, rate, time) =>
      Rating(user.toInt, item.toInt, rate.toDouble)
    })

    // Build the recommendation model using ALS
    val rank = 50
    val numIterations = 10
    val lambda = 1.0
    val alpha = 40.0
    val model = ALS.trainImplicit(ratings, rank, numIterations, lambda, alpha)

    // Evaluate the model on rating data
    val usersProducts = ratings.map { case Rating(user, product, rate) =>
      (user, product)
    }
    val predictions =
      model.predict(usersProducts).map { case Rating(user, product, rate) =>
        ((user, product), rate)
      }
    val ratesAndPreds = ratings.map { case Rating(user, product, rate) =>
      ((user, product), rate)
    }.join(predictions)

    val MSE = ratesAndPreds.map { case ((user, product), (r1, r2)) =>
      val err = (r1 - r2)
      err * err
    }.mean()
    println("Mean Squared Error = " + MSE)
    println("Root Mean Squared Error = " + Math.sqrt(MSE))

    // Save and load model
    model.save(sc, "/home/oksusu/yjkim/model/myCollaborativeFilter_user1")
    // model.save(sc, "/Users/yjkim/cf/myCollaborativeFilter_user1")
  }
}
