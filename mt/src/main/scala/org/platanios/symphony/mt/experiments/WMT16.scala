/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.symphony.mt.experiments

import org.platanios.symphony.mt.{Environment, Language}
import org.platanios.symphony.mt.Language.{English, German}
import org.platanios.symphony.mt.data._
import org.platanios.symphony.mt.data.loaders.WMT16DatasetLoader
import org.platanios.symphony.mt.data.processors.{MosesCleaner, MosesTokenizer}
import org.platanios.symphony.mt.models.{Model, ParameterManager, RNNModel}
import org.platanios.symphony.mt.models.rnn._
import org.platanios.symphony.mt.models.rnn.attention.BahdanauRNNAttention
import org.platanios.symphony.mt.vocabulary.{SimpleVocabularyGenerator, Vocabulary}
import org.platanios.tensorflow.api._

import java.nio.file.{Path, Paths}

/**
  * @author Emmanouil Antonios Platanios
  */
object WMT16 extends App {
  val workingDir: Path = Paths.get("temp").resolve("wmt16-gnmt")

  val languagePairs: Set[(Language, Language)] = Set((English, German))

  val dataConfig = DataConfig(
    workingDir = Paths.get("temp").resolve("data"),
    loaderTokenizer = MosesTokenizer(),
    loaderCleaner = MosesCleaner(1, 80),
    loaderVocab = GeneratedVocabulary(SimpleVocabularyGenerator(50000, -1, bufferSize = 8192)),
    numBuckets = 5,
    srcMaxLength = 80,
    tgtMaxLength = 80)

  val (datasets, languages): (Seq[FileParallelDataset], Seq[(Language, Vocabulary)]) = {
    loadDatasets(languagePairs.toSeq.map(l => WMT16DatasetLoader(l._1, l._2, dataConfig)), Some(workingDir))
  }

  val env = Environment(
    workingDir = workingDir.resolve(languages.mkString("-")),
    allowSoftPlacement = true,
    logDevicePlacement = false,
    gpuAllowMemoryGrowth = false,
    useXLA = false,
    numGPUs = 1,
    parallelIterations = 32,
    swapMemory = true,
    randomSeed = Some(10))

  val optConfig = Model.OptConfig(
    maxGradNorm = 100.0f,
    optimizer = tf.train.AMSGrad(learningRateSummaryTag = "LearningRate"))

  val logConfig = Model.LogConfig(
    logLossSteps = 100,
    logEvalSteps = 5000,
    launchTensorBoard = true)

  val model = RNNModel(
    name = "Model",
    languages = languages,
    dataConfig = dataConfig,
    config = RNNModel.Config(
      env,
      ParameterManager(
        wordEmbeddingsSize = 1024,
        variableInitializer = tf.VarianceScalingInitializer(
          1.0f,
          tf.VarianceScalingInitializer.FanAverageScalingMode,
          tf.VarianceScalingInitializer.UniformDistribution)),
      GNMTEncoder(
        cell = BasicLSTM(forgetBias = 1.0f),
        numUnits = 1024,
        numBiLayers = 1,
        numUniLayers = 3,
        numUniResLayers = 2,
        dropout = Some(0.2f)),
      GNMTDecoder(
        cell = BasicLSTM(forgetBias = 1.0f),
        numUnits = 1024,
        numLayers = 1 + 3, // Number of encoder bidirectional and unidirectional layers
        numResLayers = 2,
        attention = BahdanauRNNAttention(normalized = true),
        dropout = Some(0.2f),
        useNewAttention = true),
      labelSmoothing = 0.0f,
      timeMajor = true,
      beamWidth = 10,
      lengthPenaltyWeight = 1.0f),
    optConfig = optConfig,
    logConfig = logConfig,
    // TODO: !!! Find a way to set the number of buckets to 1.
    evalDatasets = datasets.flatMap(d => Seq(
      // ("WMT16/newstest2013", d.filterTags(WMT16DatasetLoader.NewsTest2013)),
      // ("WMT16/newstest2014", d.filterTags(WMT16DatasetLoader.NewsTest2014)),
      ("WMT16/newstest2015", d.filterTags(WMT16DatasetLoader.NewsTest2015)))
      //("WMT16/newstest2016", d.filterTags(WMT16DatasetLoader.NewsTest2016)))
    ))

  model.train(datasets.map(_.filterTypes(Train)), tf.learn.StopCriteria.steps(340000))
}
