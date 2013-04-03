import spark.SparkContext
import spark.SparkContext._
import spark.RDD
import java.util.Random
import scala.util.control.Breaks._
import scala.runtime.ScalaRunTime._

object DSDR {
  val epsilon = 0.00 //set by stat significance threshold
  val DATA_DIRECTORY = "data";
  val blocksize = 250;
  
  def line2vector(line: Array[String]):Array[Double] =
  {
     return line.map(value => value.toDouble)
  }

  def create_random_array(size: Long):Array[Double]=
  {
  var list = new Array[Double](size.toInt)
	val random = new Random()
	for (i <- 0 until size.toInt)
	{
	    list(i) = random.nextDouble() 
	}
	return list
  }

  def scale_and_regularize(kv: Tuple2[Tuple2[Int, Int], Double], D:Array[Double],regularization: Int):Tuple2[Tuple2[Int,Int],Double]=
  {
	return new Tuple2(kv._1,kv._2*D(kv._1._1)*D(kv._1._2))
  }

  def matrix_vector_multiplication(matrix: RDD[((Int, Int), Double)], vector: Array[Double]):Array[Double]= {
        val output_vector = matrix.map(kv => new Tuple2(kv._1._1,vector(kv._1._2)*kv._2)).reduceByKey((a, b) => a + b).sortByKey().map(kv => kv._2).collect()
	return normalize(output_vector)
  }

  def normalize(vector: Array[Double]):Array[Double] = {
	val output_vector = new Array[Double](vector.length)
	var sum = 0.0
        for(i <- 0 to output_vector.length-1)
        {
                sum = sum + vector(i)*vector(i)
        }

        for(i <- 0 to output_vector.length-1)
        {
                output_vector(i) = vector(i)/math.sqrt(sum)
        }
        return output_vector
  }

  def vector_2_rdd(vector: Array[Double],sc: SparkContext):RDD[(Int,Double)] = {
	val output_vector = new Array[(Int,Double)](vector.length)
	for(i <- 0 to output_vector.length-1)
        {
                output_vector(i) = new Tuple2(i,vector(i))
        }
	return sc.parallelize(output_vector)
  }

  def rdd_2_vector(rdd: RDD[(Int,Double)]):Array[Double] = {
	return rdd.sortByKey().map(kv=>kv._2).collect()
  }

  def matrix_vector_lambda(matrix: RDD[((Int, Int), Double)], vector: Array[Double]):Double = {
      val output = matrix.map(kv => new Tuple2(kv._1._1,vector(kv._1._2)*kv._2)).reduceByKey((a, b) => a + b).sortByKey().map(kv => kv._2/vector(kv._1)).reduce(_ + _)
      return output/vector.length
  }

  def dyadic_projection(matrix:RDD[((Int,Int),Double)],vector:Array[Double], lambda:Double):RDD[((Int,Int),Double)] = {
        return matrix.map(kv => new Tuple2(kv._1,kv._2-lambda*vector(kv._1._2)*vector(kv._1._1)))
  }

  def eigenvector_list(matrix:RDD[((Int,Int),Double)], N:Long, sc:SparkContext):Array[RDD[(Int,Double)]] =
  {
        var result = new Array[RDD[(Int,Double)]](N.toInt)
        var working_rdd = matrix

	var lambda = 0.0
	var one_step_lambda = 0.0
	var two_step_lambda = 0.0

	var count = 0

	breakable {while(count < N)
        {
		println("[DSDR Clustering] Calculating eigenvector: " + count)
                val eigentuple = dominant_eigenvector(working_rdd,N,sc)
		val ev = eigentuple._1
		
		two_step_lambda = one_step_lambda
		one_step_lambda = lambda
                lambda = eigentuple._2 

		println("[DSDR Clustering] Calculated eigenvalue: " + lambda)
                result(count) = vector_2_rdd(ev,sc) 
                working_rdd = dyadic_projection(working_rdd,ev,lambda)
                working_rdd.cache()
	
		if (count == 2) break	

		count = count + 1
        }}
	println("[DSDR Clustering] Stopping conditions met at k=" + count)
        return result.slice(0,count)
  }

  def dominant_eigenvector(matrix:RDD[((Int,Int),Double)], N:Long, sc: SparkContext):Tuple2[Array[Double],Double] = {
        var vector = create_random_array(N.toInt)       

	var prev_lambda = 1.0
	var lambda = 0.0

	var count = 0
	while (count < 50 && (math.abs(prev_lambda - lambda) > .00001))
        {
                vector = matrix_vector_multiplication(matrix,vector)
		prev_lambda = lambda
                lambda = matrix_vector_lambda(matrix,vector)
		count = count + 1
                sc.broadcast(vector)
        }

	println("[DSDR] Eigenvector calculated after " + count + " iterations")
        return new Tuple2(vector,lambda)
  }

  def allocate_and_initialize(d: Double, n:Int):Array[Double] = {
	val coordinate_array = new Array[Double](n)
	coordinate_array(0) = d
	return coordinate_array
  }

  def merge(c:Array[Double], d: Double, k: Int):Array[Double] = {
	c(k) = d
	return c	
  }

  def multi_join(rdd_set: Array[RDD[(Int,Double)]]):RDD[(Int,Array[Double])] = {
	val n = rdd_set.length
	var rdd = rdd_set(0).map(kv => new Tuple2(kv._1,allocate_and_initialize(kv._2,n)))
	for(i <- 1 until n)
	{
		rdd = rdd.join(rdd_set(i)).map(kv => new Tuple2(kv._1,merge(kv._2._1,kv._2._2,i))) 
	}
	return rdd
  }


  def main(args: Array[String]) {
    println("[DSDR] Initialized SparkContext")
    val start = System.currentTimeMillis

    //load the file
   //data acquisition
    val matrix_blocks = (new File(DATA_DIRECTORY)).listFiles(); //fetch all matrix blocks
    val sc = new SparkContext("local[4]", "LaplacianEigenmap"); //create a spark context
    val block_matrix_rdd = sc.parallelize(matrix_blocks); //create an rdd of block matrix files
    val full_matrix_rdd = block_matrix_rdd.cartesian(block_matrix_rdd)//distributed cartesian product

    //parallel weight computation
    W = full_matrix_rdd.flatMap(block => cov_kernel(block)) //calculate cov matrix, return values in kv pairs
	W.cache()
    println("[DSDR] Caclulated Affinity Matrix W")

    //create the D^-1 matrix, and make the result read only
    val diagonal_row_sum = W.map(kv => new Tuple2(kv._1._1,kv._2)).reduceByKey(_ + _).map(kv => new Tuple2(kv._1,1.0/math.sqrt(kv._2))).sortByKey().map(kv=> kv._2).collect()
    println("[DSDR] Calculated Normalization Matrix D^-1")
    sc.broadcast(diagonal_row_sum)

    //restructure the matrix based on rows, and normalize based on column sums
    val transformed_laplacian = affinity_matrix.map(kv => scale_and_regularize(kv,diagonal_row_sum,2))
    transformed_laplacian.cache()
    println("[DSDR] Normalized the entries and set RDD to cache in memory")

    val vectors = eigenvector_list(transformed_laplacian,size,sc)
    multi_join(vectors).sortByKey().map(kv => new Tuple2(kv._1,stringOf(normalize(kv._2)))).saveAsTextFile("coordinates")
    println((System.currentTimeMillis-start)/1000.0)
  }
}
