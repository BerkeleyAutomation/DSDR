import spark._
import SparkContext._

import collection.mutable.HashMap
import scala.io.Source

import breeze.linalg._
import breeze.plot._

object DSDR {
    val INPUT_FILE = "hdfs://ip-10-170-35-172.ec2.internal:9000/user/root/data/docword.pubmed.txt"
    val INPUT_FILE_VOCAB = "/mnt/vocab.pubmed.txt"
    val NUM_FEATURES = 1000
    val APPROXIMATION_FACTOR = 0.95

    def countWords(doc:List[Int]):List[(Int,Int)] = {
	return doc.map(word => (word,1))
    }

    def buildDictionary(docRDD:RDD[List[Int]]): HashMap[Int,Int] = {
         val wordCount = docRDD.flatMap(countWords).reduceByKey(_+_).map(flip => (flip._2,flip._1)).sortByKey(false).take(NUM_FEATURES)
         val dictionary = new HashMap[Int,Int]()
         for(i <- 0 until NUM_FEATURES) {
            val key = wordCount(i)._2
            dictionary += key -> i
         }
         return dictionary
    } 

    def parseUCI(word: Iterator[String]): Iterator[List[Int]] = {
         var documentList = List[List[Int]]()
         var currentDocument = 0
         var currentDocumentText = Set[Int]()

         for (line <- word) {
             val fields = line.split(' ')
             if (fields.length == 3) {
                if (fields(0).toInt != currentDocument) {
                   documentList = currentDocumentText.toList :: documentList 
                   currentDocumentText = Set[Int]()
                   currentDocument = fields(0).toInt
                }
                currentDocumentText += fields(1).toInt
             }
         }
         return documentList.iterator
    }

    def weightMatrix(doc:List[Int], dictionary: HashMap[Int,Int]):List[(Int,Int)] = {
        var tupleList = List[Int]() 
        var tupleList2 = List[(Int,Int)]() 
        for (word <- doc) {
	    if(dictionary.contains(word)) {
                  tupleList = dictionary(word) :: tupleList
            }
        }
       
        for (i <- 0 until tupleList.length) {
            for(j <- 0 until tupleList.length) {
		if (math.random > APPROXIMATION_FACTOR && i != j)
                   tupleList2 = (tupleList(i),tupleList(j)) :: tupleList2 
		else if (i == j)
                   tupleList2 = (tupleList(i),tupleList(j)) :: tupleList2 
            }
        }

        return tupleList2
    }

    def breezifyRDD(graph:RDD[((Int,Int),Int)]):DenseMatrix[Double] = {
        var diagonal = graph.filter(v => v._1._1 == v._1._2).collect()
        var normalization = new HashMap[Int,Double]()
        for (element <- diagonal)
            normalization += element._1._1 -> (element._2 + 0.0)

        var matrix = DenseMatrix.zeros[Double](NUM_FEATURES,NUM_FEATURES)
        val matrixRDD = graph.collect()
        for(element <- matrixRDD)
        {
            if (element._1._1 != element._1._2) {
		if(normalization.contains(element._1._1))
	            matrix(element._1._1,element._1._2) = (element._2 + 0.0)/normalization(element._1._1)
		else
	            matrix(element._1._1,element._1._2) = (element._2 + 0.0)
            }
        }

        return matrix
    }

    def reorth(vectors:List[DenseVector[Double]]):List[DenseVector[Double]] = {
        var newVectors = List[DenseVector[Double]]()
	for(vector <- 0 until vectors.length) {
             if (vector == 0)
	        newVectors = (vectors(vector) / norm(vectors(vector))) :: newVectors
             else if (vector == 1) {
	        val newVector1 = vectors(vector) - newVectors(vector-1)*(vectors(vector) dot newVectors(vector-1))
                newVectors = newVector1 / norm(newVector1) :: newVectors 
             }
	     else {
	        val newVector2 = vectors(vector) - newVectors(vector-1)*(vectors(vector) dot newVectors(vector-1)) - newVectors(vector-2)*(vectors(vector) dot newVectors(vector-2))
                newVectors = newVector2 / norm(newVector2) :: newVectors
             }
        }
        return newVectors
    }

    def eigenvectorDecomposition(nLaplacian:DenseMatrix[Double]):(DenseVector[Double],DenseVector[Double]) = {
        var simultVectors = List(DenseVector.rand(NUM_FEATURES),DenseVector.rand(NUM_FEATURES), DenseVector.rand(NUM_FEATURES))
        for (i <- 0 until 200) {
           var update = List[DenseVector[Double]]()
           for(vector <- 0 until simultVectors.length) {
	       update = (nLaplacian * simultVectors(vector)) :: update
           }
           simultVectors = reorth(update)
        }
        return (simultVectors(1),simultVectors(2))
    }
 
    def UCIAnalysis(vocabFileName:String, dictionary: HashMap[Int,Int], result: (DenseVector[Double],DenseVector[Double])) = {
         //val f2 = Figure()
         val x = result._1
         val y = result._2
         val reverseLookup = HashMap[Int,String]()
         //f2.subplot(0) += plot(x,y,'.')
         var count = 1
         for(line <- Source.fromFile(vocabFileName).getLines()) {
              reverseLookup(count) = line
	      count = count + 1
         }

         for(i <- dictionary.keySet) {
             println(reverseLookup(i) + " " + result._1(dictionary(i)) + " " + result._2(dictionary(i)) + " " + dictionary(i))
         }
    }

    def main(args: Array[String]) {
         println("[DSDR] Program Started")
         val sc = new SparkContext("spark://ec2-50-16-142-22.compute-1.amazonaws.com:7077", "DSDR","/root/spark/",List("/root/dsdr/target/Distributed-Spectral-Dimensionality-Reduction-assembly-0.1.jar"))
         //val sc = new SparkContext("local", "DSDR","/root/spark/",List("/root/dsdr/target/Distributed-Spectral-Dimensionality-Reduction-assembly-0.1.jar"))
         val textData = sc.textFile(INPUT_FILE)
         val dictionary = buildDictionary(textData.mapPartitions(parseUCI))
         println("[DSDR] Dictionary Built")
         val graph = textData.mapPartitions(parseUCI).flatMap(doc => weightMatrix(doc,dictionary)).map(tup => (tup, 1)).reduceByKey(_ + _)
         
         val result = eigenvectorDecomposition(breezifyRDD(graph))
	 UCIAnalysis(INPUT_FILE_VOCAB,dictionary,result)
    }

}
