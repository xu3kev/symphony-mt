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

package org.platanios.symphony.mt.models.transformer

import org.platanios.symphony.mt.models._
import org.platanios.symphony.mt.models.Utilities._
import org.platanios.symphony.mt.models.attention._
import org.platanios.symphony.mt.models.helpers.{Common, PadRemover}
import org.platanios.tensorflow.api._

/** Transformer encoder.
  *
  * This encoder takes as input a source sequence in some language and returns a tuple containing:
  *   - '''Output:''' Outputs (for each time step) of the transformer.
  *   - '''State:''' Empty sequence
  *
  * @author Emmanouil Antonios Platanios
  */
class BasicTransformerEncoder[T: TF : IsHalfOrFloatOrDouble](
    val numUnits: Int,
    val numLayers: Int,
    val useSelfAttentionProximityBias: Boolean = false,
    val positionEmbeddings: PositionEmbeddings = FixedSinusoidPositionEmbeddings(1.0f, 1e4f),
    val postPositionEmbeddingsDropout: Float = 0.1f,
    val usePadRemover: Boolean = true,
    val layerPreprocessors: Seq[LayerProcessor] = Seq(
      Normalize(LayerNormalization(), 1e-6f)),
    val layerPostprocessors: Seq[LayerProcessor] = Seq(
      Dropout(0.9f, broadcastAxes = Set(1)),
      AddResidualConnection),
    val attentionKeysDepth: Int = 0,
    val attentionValuesDepth: Int = 0,
    val attentionNumHeads: Int = 8,
    val encoderSelfAttention: Attention = DotProductAttention(0.1f, Set.empty, "DotProductAttention"),
    val feedForwardLayer: FeedForwardLayer = DenseReLUDenseFeedForwardLayer(2048, 512, 0.0f, Set.empty, "FeedForward")
) extends TransformerEncoder[T]() {
  override def apply(sequences: Sequences[Int])(implicit context: Context): EncodedSequences[T] = {
    // `embeddedSequences.sequences` has shape [BatchSize, MaxLength, WordEmbeddingSize]
    // `embeddedSequence.lengths` has shape [BatchSize]
    val embeddedSequences = maybeTransposeInputSequences(embedSequences(sequences))
    val embeddedSequencesMaxLength = tf.shape(embeddedSequences.sequences).slice(1)

    // Perform some pre-processing to the token embeddings sequence.
    val padding = tf.sequenceMask(embeddedSequences.lengths, embeddedSequencesMaxLength, name = "Padding").toFloat
    val attentionBias = Attention.attentionBiasIgnorePadding(padding)
    var encoderSelfAttentionBias = attentionBias
    if (useSelfAttentionProximityBias)
      encoderSelfAttentionBias += Attention.attentionBiasProximal(embeddedSequencesMaxLength)
    var encoderInput = positionEmbeddings.addTo(embeddedSequences.sequences)
    if (context.mode.isTraining)
      encoderInput = tf.dropout(encoderInput, 1.0f - postPositionEmbeddingsDropout)

    // Add the multi-head attention and the feed-forward layers.
    val padRemover = if (usePadRemover && !Common.isOnTPU) Some(PadRemover(padding)) else None
    var x = encoderInput
    var y = x
    (0 until numLayers).foreach(layer => {
      tf.variableScope(s"Layer$layer") {
        tf.variableScope("SelfAttention") {
          val queryAntecedent = LayerProcessor.layerPreprocess(x, layerPreprocessors)
          y = Attention.multiHeadAttention(
            queryAntecedent = queryAntecedent,
            memoryAntecedent = queryAntecedent,
            bias = encoderSelfAttentionBias,
            totalKeysDepth = attentionKeysDepth,
            totalValuesDepth = attentionValuesDepth,
            outputsDepth = numUnits,
            numHeads = attentionNumHeads,
            attention = encoderSelfAttention,
            name = "MultiHeadAttention")
          x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors)
        }
        tf.variableScope("FeedForward") {
          val xx = LayerProcessor.layerPreprocess(x, layerPreprocessors)
          y = feedForwardLayer(xx.toFloat, padRemover)
          x = LayerProcessor.layerPostprocess(x, y, layerPostprocessors)
        }
      }
    })

    // If normalization is done during layer preprocessing, then it should also be done on the output, since the output
    // can grow very large, being the sum of a whole stack of unnormalized layer outputs.
    val encoderOutput = LayerProcessor.layerPreprocess(x, layerPreprocessors)

    EncodedSequences(encoderOutput.castTo[T], embeddedSequences.lengths)
  }
}