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

package org.platanios.symphony.mt.models.parameters

import org.platanios.symphony.mt.Language
import org.platanios.symphony.mt.models.{Decoding, Encoding, Stage}
import org.platanios.symphony.mt.vocabulary.Vocabulary
import org.platanios.tensorflow.api._

import scala.collection.mutable

/**
  * @author Emmanouil Antonios Platanios
  */
class LanguageEmbeddingsManager protected (
    val languageEmbeddingsSize: Int,
    override val wordEmbeddingsSize: Int,
    override val mergedWordEmbeddings: Boolean = false,
    override val mergedWordProjections: Boolean = false,
    override val sharedWordEmbeddings: Boolean = false,
    override val variableInitializer: tf.VariableInitializer = null
) extends ParameterManager(
  wordEmbeddingsSize, mergedWordEmbeddings, mergedWordProjections, sharedWordEmbeddings, variableInitializer) {
  protected val languageEmbeddings: mutable.Map[Graph, Output]                      = mutable.Map.empty
  protected val parameters        : mutable.Map[Graph, mutable.Map[String, Output]] = mutable.Map.empty

  override protected def removeGraph(graph: Graph): Unit = {
    super.removeGraph(graph)
    languageEmbeddings -= graph
    parameters -= graph
  }

  override def initialize(languages: Seq[(Language, Vocabulary)]): Unit = {
    tf.createWithVariableScope("ParameterManager") {
      super.initialize(languages)
      val graph = currentGraph
      if (!languageEmbeddings.contains(graph)) {
        languageEmbeddings += graph -> {
          val embeddingsInitializer = tf.RandomUniformInitializer(-0.1f, 0.1f)
          tf.variable(
            "LanguageEmbeddings", FLOAT32, Shape(languages.length, languageEmbeddingsSize),
            initializer = embeddingsInitializer).value
        }
      }
    }
  }

  override def get(
      name: String,
      dataType: DataType,
      shape: Shape,
      variableInitializer: tf.VariableInitializer = variableInitializer,
      variableReuse: tf.VariableReuse = tf.ReuseOrCreateNewVariable
  )(implicit stage: Stage): Output = {
    tf.createWithVariableScope("ParameterManager") {
      val graph = currentGraph
      val variableScopeName = tf.currentVariableScope.name
      val fullName = if (variableScopeName != null && variableScopeName != "") s"$variableScopeName/$name" else name

      def create(): Output = tf.createWithVariableScope(name) {
        val language = stage match {
          case Encoding => context.get._1
          case Decoding => context.get._2
        }
        val embedding = languageEmbeddings(graph).gather(language).reshape(Shape(1, -1))
        val weights = tf.variable("Dense/Weights", FLOAT32, Shape(languageEmbeddingsSize, shape.numElements.toInt))
        val bias = tf.variable("Dense/Bias", FLOAT32, Shape(shape.numElements.toInt))
        val parameters = tf.linear(embedding, weights, bias, "Dense")
        parameters.cast(dataType).reshape(shape)
      }

      variableReuse match {
        case tf.ReuseExistingVariableOnly => parameters.getOrElseUpdate(graph, mutable.Map.empty)(fullName)
        case tf.CreateNewVariableOnly =>
          // TODO: Kind of hacky.
          val created = create()
          parameters.getOrElseUpdate(graph, mutable.Map.empty) += created.op.inputs(0).name -> created
          created
        case tf.ReuseOrCreateNewVariable =>
          parameters
              .getOrElseUpdate(graph, mutable.Map.empty)
              .getOrElseUpdate(fullName, create())
      }
    }
  }
}

object LanguageEmbeddingsManager {
  def apply(
      languageEmbeddingsSize: Int,
      wordEmbeddingsSize: Int,
      mergedWordEmbeddings: Boolean = false,
      mergedWordProjections: Boolean = false,
      sharedWordEmbeddings: Boolean = false,
      variableInitializer: tf.VariableInitializer = null
  ): LanguageEmbeddingsManager = {
    new LanguageEmbeddingsManager(
      languageEmbeddingsSize,
      wordEmbeddingsSize,
      mergedWordEmbeddings,
      mergedWordProjections,
      sharedWordEmbeddings,
      variableInitializer)
  }
}