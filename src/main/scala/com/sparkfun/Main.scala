package com.sparkfun

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SaveMode

import org.slf4j.LoggerFactory

object Main {

  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("Create machine learning template ...")

    // val InputTable = args(0)
    // val OutputTable = args(1)
    var outTable=""

    val conf = new SparkConf()
      .setAppName("sparkfun")
      .set("spark.sql.crossJoin.enabled", "true")
      .set("spark.hive.mapred.supports.subdirectories", "true")
      .set("spark.hadoop.mapreduce.input.fileinputformat.input.dir.recursive", "true")
      .set("spark.sql.shuffle.partitions", "3000")
      .set("hive.exec.dynamic.partition", "true")
      .set("hive.exec.dynamic.partition.mode", "nonstrict")

    val session = SparkSession
      .builder
      .config(conf)
      .enableHiveSupport
      .getOrCreate

    val trainObj = new Training(session)

    val df = trainObj.importData()   
    val encoded = trainObj.encodeData(df)
    val filled = trainObj.fixData(encoded)
    trainObj.train_test(filled) 

    logger.info("Complete.")

  }
}