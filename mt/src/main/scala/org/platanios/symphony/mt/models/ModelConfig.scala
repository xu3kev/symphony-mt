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

package org.platanios.symphony.mt.models

import org.platanios.symphony.mt.Language
import org.platanios.symphony.mt.models.ModelConfig.{LogConfig, OptConfig}
import org.platanios.symphony.mt.models.helpers.decoders.{LengthPenalty, NoLengthPenalty}
import org.platanios.symphony.mt.models.pivoting.{NoPivot, Pivot}
import org.platanios.tensorflow.api.ops.training.optimizers.{GradientDescent, Optimizer}

/**
  * @author Emmanouil Antonios Platanios
  */
case class ModelConfig(
    pivot: Pivot = NoPivot,
    // Optimizer Configuration
    optConfig: OptConfig,
    // Logging Configuration
    logConfig: LogConfig,
    // Decoder Configuration
    beamWidth: Int,
    lengthPenalty: LengthPenalty = NoLengthPenalty,
    maxDecodingLengthFactor: Float = 2.0f,
    // Other Options
    labelSmoothing: Float = 0.0f,
    timeMajor: Boolean = false,
    summarySteps: Int = 100,
    checkpointSteps: Int = 1000,
    trainIdentityTranslations: Boolean = false,
    // The following is to allow training in one direction only (for a language pair).
    languagePairs: Set[(Language, Language)] = Set.empty,
    evalLanguagePairs: Set[(Language, Language)] = Set.empty)

object ModelConfig {
  case class OptConfig(
      maxGradNorm: Option[Float] = None,
      optimizer: Optimizer = GradientDescent(1.0f, learningRateSummaryTag = "LearningRate"),
      colocateGradientsWithOps: Boolean = true)

  case class LogConfig(
      logLossSteps: Int = 100,
      logEvalSteps: Int = 1000,
      launchTensorBoard: Boolean = false,
      tensorBoardConfig: (String, Int) = ("localhost", 6006))
}