/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ExecuteCloudFormationChangeSetTaskSpec extends Specification {


  @Subject
//  def autoAcceptCloudFormationChangeSetTask = new ExecuteCloudFormationChangeSetTaskSpec();
  def executeCloudFormationChangeSetTask = new ExecuteCloudFormationChangeSetTaskSpec();

  @Unroll
  def "should finish successfully unless is a replacement and it's configured to fail"(){
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'cloudProvider': 'aws',
      'isChangeSet': isChangeSet,
      'failOnChangeSetWithReplacement': failOnChangeSetWithReplacement,
      'changeSetName': 'changeSetName'
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def outputs = [
      changeSets: [
        [
          name: 'notThisChangeSet',
          changes: [
            [
              resourceChange: [
                replacement: notThisChangeSetReplacement
              ]
            ]
          ]
        ],
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: thisChangeSetChange01Replacement
              ]
            ],
            [
              resourceChange: [
                replacement: thisChangeSetChange02Replacement
              ]
            ]
          ]
        ]
      ]
    ]
    stage.setOutputs(outputs)

    when:
    def result = executeCloudFormationChangeSetTask.execute(stage)
//    def result = autoAcceptCloudFormationChangeSetTask.execute(stage)

    then:
      result.status == expectedResult

    where:
    isChangeSet | failOnChangeSetWithReplacement | notThisChangeSetReplacement  | thisChangeSetChange01Replacement | thisChangeSetChange02Replacement || expectedResult
    false       | true                           | null                         | null                             | null                             || ExecutionStatus.SUCCEEDED
    true        | false                          | "true"                       | "true"                           | "true"                           || ExecutionStatus.SUCCEEDED
    true        | true                           | "false"                      | "false"                          | "false"                          || ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "should fail when is a replacement and is set to fail"(){
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'cloudProvider': 'aws',
      'isChangeSet': isChangeSet,
      'failOnChangeSetWithReplacement': failOnChangeSetWithReplacement,
      'changeSetName': 'changeSetName'
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def outputs = [
      changeSets: [
        [
          name: 'notThisChangeSet',
          changes: [
            [
              resourceChange: [
                replacement: notThisChangeSetReplacement
              ]
            ]
          ]
        ],
        [
          name: 'changeSetName',
          changes: [
            [
              resourceChange: [
                replacement: thisChangeSetChange01Replacement
              ]
            ],
            [
              resourceChange: [
                replacement: thisChangeSetChange02Replacement
              ]
            ]
          ]
        ]
      ]
    ]
    stage.setOutputs(outputs)

    when:
//    def result = autoAcceptCloudFormationChangeSetTask.execute(stage)
    def result = executeCloudFormationChangeSetTask.execute(stage)

    then:
    thrown(expectedException)

    where:
    isChangeSet | failOnChangeSetWithReplacement | notThisChangeSetReplacement  | thisChangeSetChange01Replacement | thisChangeSetChange02Replacement || expectedException
    true        | true                           | "false"                      | "true"                           | "false"                          || RuntimeException
  }
}
