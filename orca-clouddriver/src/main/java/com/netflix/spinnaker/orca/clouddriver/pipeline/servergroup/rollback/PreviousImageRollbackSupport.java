/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup.BuildInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreviousImageRollbackSupport {
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final ObjectMapper objectMapper;
  private final CloudDriverService cloudDriverService;
  private final FeaturesService featuresService;
  private final RetrySupport retrySupport;

  public PreviousImageRollbackSupport(
      ObjectMapper objectMapper,
      CloudDriverService cloudDriverService,
      FeaturesService featuresService,
      RetrySupport retrySupport) {
    this.objectMapper =
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.cloudDriverService = cloudDriverService;
    this.featuresService = featuresService;
    this.retrySupport = retrySupport;
  }

  public ImageDetails getImageDetailsFromEntityTags(
      String cloudProvider, String credentials, String region, String serverGroupName) {
    List<EntityTags> entityTags = null;

    try {
      entityTags =
          retrySupport.retry(
              () -> {
                if (!featuresService.areEntityTagsAvailable()) {
                  return Collections.emptyList();
                }

                return cloudDriverService.getEntityTags(
                    cloudProvider, "serverGroup", serverGroupName, credentials, region);
              },
              15,
              2000,
              false);
    } catch (Exception e) {
      log.warn("Unable to fetch entity tags, reason: {}", e.getMessage());
    }

    if (entityTags != null && entityTags.size() > 1) {
      // this should _not_ happen
      String id =
          String.format(
              "%s:serverGroup:%s:%s:%s", cloudProvider, serverGroupName, credentials, region);
      throw new IllegalStateException("More than one set of entity tags found for " + id);
    }

    if (entityTags == null || entityTags.isEmpty()) {
      return null;
    }

    List<EntityTags.Tag> tags = entityTags.get(0).tags;
    PreviousServerGroup previousServerGroup =
        tags.stream()
            .filter(t -> "spinnaker:metadata".equalsIgnoreCase(t.name))
            .map(t -> (Map<String, Object>) ((Map) t.value).get("previousServerGroup"))
            .filter(Objects::nonNull)
            .map(m -> objectMapper.convertValue(m, PreviousServerGroup.class))
            .findFirst()
            .orElse(null);

    if (previousServerGroup == null || previousServerGroup.imageName == null) {
      return null;
    }

    return new ImageDetails(
        previousServerGroup.imageId,
        previousServerGroup.imageName,
        previousServerGroup.getBuildNumber());
  }

  public static class ImageDetails {
    private final String imageId;
    private final String imageName;
    private final String buildNumber;

    ImageDetails(String imageId, String imageName, String buildNumber) {
      this.imageId = imageId;
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }

    public String getImageId() {
      return imageId;
    }

    public String getImageName() {
      return imageName;
    }

    public String getBuildNumber() {
      return buildNumber;
    }
  }

  static class PreviousServerGroup {
    public String imageId;
    public String imageName;
    public BuildInfo buildInfo;

    String getBuildNumber() {
      return (buildInfo == null || buildInfo.jenkins == null) ? null : buildInfo.jenkins.number;
    }
  }
}
