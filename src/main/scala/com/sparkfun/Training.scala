package com.sparkfun

import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator  
import org.apache.spark.mllib.evaluation.MulticlassMetrics  
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics  
import org.apache.spark.ml.classification.RandomForestClassifier  
import org.apache.spark.ml.tuning.{ParamGridBuilder, TrainValidationSplit, CrossValidator}  
import org.apache.spark.ml.feature.{VectorAssembler, StringIndexer, OneHotEncoderEstimator}  
import org.apache.spark.ml.linalg.Vectors  
import org.apache.spark.ml.Pipeline  

import org.slf4j.LoggerFactory

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{SparkSession, DataFrame}


class Training(session: SparkSession) {
    import session.implicits._
    private val LOG = LoggerFactory.getLogger(getClass())
    val filePath = "./data/mooc.csv"

    def importData(): DataFrame = {
        LOG.info("Import csv.")
        val data = session.read.option("header","true")
            .option("inferSchema","true")
            .format("csv")
            .load(filePath)
        
        val df = (data.select(data("certified").as("label"), 
            $"registered", $"viewed", $"explored", 
            $"final_cc_cname_DI", $"gender", $"nevents", 
            $"ndays_act", $"nplay_video", $"nchapters", $"nforum_posts"))
        
        return df 
    }
    
    def encodeData(df:DataFrame): DataFrame = {
        LOG.info("One Hot Encoding.")
        // string indexing
        val indexer1 = new StringIndexer()
            .setInputCol("final_cc_cname_DI")
            .setOutputCol("countryIndex")
            .setHandleInvalid("keep") 

        val indexed1 = indexer1.fit(df).transform(df)

        val indexer2 = new StringIndexer()
            .setInputCol("gender")
            .setOutputCol("genderIndex")
            .setHandleInvalid("keep")
        val indexed2 = indexer2.fit(indexed1).transform(indexed1)

        // one hot encoding
        val encoder = new OneHotEncoderEstimator()
            .setInputCols(Array("countryIndex", "genderIndex"))
            .setOutputCols(Array("countryVec", "genderVec"))

        val encoded = encoder.fit(indexed2).transform(indexed2)

        return encoded 

    }

    def fixData(encoded:DataFrame): DataFrame = {
        LOG.info("Fill nan.")
        val nanEvents = encoded.groupBy("nevents").count().orderBy($"count".desc)
        // for (line <- nanEvents){
        //     println(line)
        // }
        nanEvents.show(false)

        // define medians
        val neventsMedianArray = encoded.stat.approxQuantile("nevents", Array(0.5), 0)
        val neventsMedian = neventsMedianArray(0)

        val ndays_actMedianArray = encoded.stat.approxQuantile("ndays_act", Array(0.5), 0)
        val ndays_actMedian = ndays_actMedianArray(0)

        val nplay_videoMedianArray = encoded.stat.approxQuantile("nplay_video", Array(0.5), 0)
        val nplay_videoMedian = nplay_videoMedianArray(0)

        val nchaptersMedianArray = encoded.stat.approxQuantile("nchapters", Array(0.5), 0)
        val nchaptersMedian = nchaptersMedianArray(0)

        // replace 
        val filled = encoded.na.fill(Map(
        "nevents" -> neventsMedian, 
        "ndays_act" -> ndays_actMedian, 
        "nplay_video" -> nplay_videoMedian, 
        "nchapters" -> nchaptersMedian))
        
        return filled 

    }

    def train_test(filled: DataFrame): Unit = {
        LOG.info("Training...")
        LOG.info("1. Split Train vs Test.")
        // Set the input columns as the features we want to use
        val assembler = (new VectorAssembler().setInputCols(Array(
        "viewed", "explored", "nevents", "ndays_act", "nplay_video", 
        "nchapters", "nforum_posts", "countryVec", "genderVec")).
        setOutputCol("features"))

        // Transform the DataFrame
        val output = assembler.transform(filled).select($"label",$"features")


        // Splitting the data by create an array of the training and test data
        val Array(training, test) = output.select("label","features").
                                    randomSplit(Array(0.7, 0.3), seed = 12345)
                                    
        
        LOG.info("2. Choose a classifer.")
        // create the model
        val rf = new RandomForestClassifier()

        // create the param grid
        val paramGrid = new ParamGridBuilder().
        addGrid(rf.numTrees,Array(20,50,100)).
        build()

        // create cross val object, define scoring metric
        LOG.info("3. Cross validating.")
        val cv = new CrossValidator().
        setEstimator(rf).
        setEvaluator(new MulticlassClassificationEvaluator().setMetricName("weightedRecall")).
        setEstimatorParamMaps(paramGrid).
        setNumFolds(3).
        setParallelism(2)

        // You can then treat this object as the model and use fit on it.
        val model = cv.fit(training)

        LOG.info("Testing...")
        val results = model.transform(test).select("features", "label", "prediction")


        val predictionAndLabels = results.
            select($"prediction",$"label").
            as[(Double, Double)].
            rdd
            
            
        // Instantiate a new metrics objects
        val bMetrics = new BinaryClassificationMetrics(predictionAndLabels)
        val mMetrics = new MulticlassMetrics(predictionAndLabels)
        val labels = mMetrics.labels

        // Print out the Confusion matrix
        println("Confusion matrix:")
        println(mMetrics.confusionMatrix)


        // Precision by label
        labels.foreach { l =>
        println(s"Precision($l) = " + mMetrics.precision(l))
        }

        // Recall by label
        labels.foreach { l =>
        println(s"Recall($l) = " + mMetrics.recall(l))
        }

        // False positive rate by label
        labels.foreach { l =>
        println(s"FPR($l) = " + mMetrics.falsePositiveRate(l))
        }

        // F-measure by label
        labels.foreach { l =>
        println(s"F1-Score($l) = " + mMetrics.fMeasure(l))
        }


        // Precision by threshold
        val precision = bMetrics.precisionByThreshold
        precision.foreach { case (t, p) =>
        println(s"Threshold: $t, Precision: $p")
        }

        // Recall by threshold
        val recall = bMetrics.recallByThreshold
        recall.foreach { case (t, r) =>
        println(s"Threshold: $t, Recall: $r")
        }

        // Precision-Recall Curve
        val PRC = bMetrics.pr

        // F-measure
        val f1Score = bMetrics.fMeasureByThreshold
        f1Score.foreach { case (t, f) =>
        println(s"Threshold: $t, F-score: $f, Beta = 1")
        }

        val beta = 0.5
        val fScore = bMetrics.fMeasureByThreshold(beta)
        f1Score.foreach { case (t, f) =>
        println(s"Threshold: $t, F-score: $f, Beta = 0.5")
        }

        // AUPRC
        val auPRC = bMetrics.areaUnderPR
        println("Area under precision-recall curve = " + auPRC)

        // Compute thresholds used in ROC and PR curves
        val thresholds = precision.map(_._1)

        // ROC Curve
        val roc = bMetrics.roc

        // AUROC
        val auROC = bMetrics.areaUnderROC
        println("Area under ROC = " + auROC)

    }
}