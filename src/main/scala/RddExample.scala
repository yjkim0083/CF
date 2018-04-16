import org.apache.spark.{SparkConf, SparkContext, TaskContext}

object RddExample extends App {

  val conf = new SparkConf().setMaster("local[*]").setAppName("RddExample")
  val sc = new SparkContext(conf)

  def myfunc[T](iter: Iterator[T]): Iterator[(T, T)] = {
    val tc = TaskContext.get()
    println("Partition ID: %s, Attempt ID: %s".format(tc.partitionId(), tc.taskAttemptId()))

    var res = List[(T,T)]()
    var pre = iter.next()

    while(iter.hasNext) {
      val cur = iter.next()
      res   .::= (pre, cur)
      pre = cur
    }
    res.iterator
  }

  val rdd = sc.parallelize(List(1,2,3,4,5,6,7,8,9), 3)
  val mapPartitionRdd = rdd.mapPartitions(myfunc _).collect

  mapPartitionRdd.foreach(println)

//  def myfunc2(index: Int, iter: Iterator[Int]): Iterator[String] = {
//    println("Partition ID: %s".format(index))
//    iter.toList.map(x => index + "," + x).iterator
//  }

//  val mapPartitionsWithIndexRdd = rdd.mapPartitionsWithIndex(myfunc2).collect()
//  mapPartitionsWithIndexRdd.foreach(println)
}
