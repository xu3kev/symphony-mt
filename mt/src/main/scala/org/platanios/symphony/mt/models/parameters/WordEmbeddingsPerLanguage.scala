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
import org.platanios.symphony.mt.vocabulary.Vocabulary
import org.platanios.tensorflow.api._

import scala.collection.mutable

/**
  * @author Emmanouil Antonios Platanios
  */
class WordEmbeddingsPerLanguage protected (
    override val embeddingsSize: Int
) extends WordEmbeddingsType {
  override type T = tf.TensorArray

  override def createStringToIndexLookupTable(languages: Seq[(Language, Vocabulary)]): Output = {
    val tables = languages.map(l => l._2.stringToIndexLookupTable(name = l._1.name))
    tf.stack(tables.map(_.handle))
  }

  override def createIndexToStringLookupTable(languages: Seq[(Language, Vocabulary)]): Output = {
    val tables = languages.map(l => l._2.indexToStringLookupTable(name = l._1.name))
    tf.stack(tables.map(_.handle))
  }

  override def createWordEmbeddings(languages: Seq[(Language, Vocabulary)]): tf.TensorArray = {
    val embeddingsInitializer = tf.RandomUniformInitializer(-0.1f, 0.1f)
    val tensorArray = tf.TensorArray.create(
      size = languages.size,
      dataType = FLOAT32,
      dynamicSize = false,
      clearAfterRead = false,
      elementShape = Shape(-1, embeddingsSize))
    languages.zipWithIndex.foldLeft(tensorArray) {
      case (ta, (language, index)) =>
        ta.write(index, tf.variable(
          language._1.name, FLOAT32, Shape(language._2.size, embeddingsSize), embeddingsInitializer).value)
    }
  }

  override def lookupTable(lookupTable: Output, languageId: Output): Output = {
    lookupTable.gather(languageId)
  }

  override def embeddingLookup(
      embeddingTables: tf.TensorArray,
      languageIds: Seq[Output],
      languageId: Output,
      keys: Output,
      context: Option[(Output, Output)]
  ): Output = {
    embeddingTables.read(languageId).gather(keys)
  }

  override def projectionToWords(
      languages: Seq[(Language, Vocabulary)],
      languageIds: Seq[Output],
      projectionsToWords: mutable.Map[Int, tf.TensorArray],
      inputSize: Int,
      languageId: Output
  ): Output = {
    projectionsToWords
        .getOrElseUpdate(inputSize, {
          val weightsInitializer = tf.RandomUniformInitializer(-0.1f, 0.1f)
          val tensorArray = tf.TensorArray.create(
            size = languages.size,
            dataType = FLOAT32,
            dynamicSize = false,
            clearAfterRead = false,
            elementShape = Shape(inputSize, -1))
          languages.zipWithIndex.foldLeft(tensorArray) {
            case (ta, (language, index)) =>
              ta.write(index, tf.variable(
                s"${language._1.name}/OutWeights", FLOAT32, Shape(inputSize, language._2.size),
                weightsInitializer).value)
          }
        }).read(languageId)
  }
}

object WordEmbeddingsPerLanguage {
  def apply(
      embeddingsSize: Int
  ): WordEmbeddingsPerLanguage = {
    new WordEmbeddingsPerLanguage(embeddingsSize)
  }
}