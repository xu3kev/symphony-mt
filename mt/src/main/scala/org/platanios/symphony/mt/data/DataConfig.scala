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

package org.platanios.symphony.mt.data

import org.platanios.symphony.mt.vocabulary._

import java.nio.file.{Path, Paths}

/**
  * @author Emmanouil Antonios Platanios
  */
case class DataConfig(
    // Loader
    workingDir: Path = Paths.get("working_dir"),
    loaderBufferSize: Int = 8192,
    loaderExtractTGZ: Boolean = true,
    loaderConvertSGMToText: Boolean = true,
    loaderTokenize: Boolean = false,
    loaderSentenceLengthBounds: Option[(Int, Int)] = None,
    loaderMergeVocabs: Boolean = false,
    // Corpus
    trainBatchSize: Int = 128,
    inferBatchSize: Int = 32,
    evaluateBatchSize: Int = 32,
    numBuckets: Int = 5,
    srcMaxLength: Int = 50,
    tgtMaxLength: Int = 50,
    srcReverse: Boolean = false,
    bufferSize: Long = -1L,
    dropCount: Int = 0,
    numShards: Int = 1,
    shardIndex: Int = 0,
    numParallelCalls: Int = 4,
    // Vocabulary
    vocabGenerator: VocabularyGenerator = SimpleVocabularyGenerator(50000, -1, 8192),
    beginOfSequenceToken: String = Vocabulary.BEGIN_OF_SEQUENCE_TOKEN,
    endOfSequenceToken: String = Vocabulary.END_OF_SEQUENCE_TOKEN,
    unknownToken: String = Vocabulary.UNKNOWN_TOKEN)
