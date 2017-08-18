/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.service;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.service.compinstance.ComponentInstance;
import org.apache.hadoop.yarn.service.provider.ProviderService;
import org.apache.hadoop.yarn.service.provider.ProviderFactory;
import org.apache.slider.api.resource.Application;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.core.launch.AbstractLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContainerLaunchService extends AbstractService{

  protected static final Logger LOG =
      LoggerFactory.getLogger(ContainerLaunchService.class);

  private ExecutorService executorService;
  private SliderFileSystem fs;

  public ContainerLaunchService(SliderFileSystem fs) {
    super(ContainerLaunchService.class.getName());
    this.fs = fs;
  }

  @Override
  public void serviceInit(Configuration conf) throws Exception {
    executorService = Executors.newCachedThreadPool();
    super.serviceInit(conf);
  }

  @Override
  protected void serviceStop() throws Exception {
    if (executorService != null) {
      executorService.shutdownNow();
    }
    super.serviceStop();
  }

  public void launchCompInstance(Application application,
      ComponentInstance instance, Container container) {
    ContainerLauncher launcher =
        new ContainerLauncher(application, instance, container);
    executorService.execute(launcher);
  }

  private class ContainerLauncher implements Runnable {
    public final Container container;
    public final Application application;
    public ComponentInstance instance;

    public ContainerLauncher(
        Application application,
        ComponentInstance instance, Container container) {
      this.container = container;
      this.application = application;
      this.instance = instance;
    }

    @Override public void run() {
      org.apache.slider.api.resource.Component compSpec = instance.getCompSpec();
      ProviderService provider = ProviderFactory.getProviderService(
          compSpec.getArtifact());
      AbstractLauncher launcher = new AbstractLauncher(fs, null);
      try {
        provider.buildContainerLaunchContext(launcher, application,
            instance, fs, getConfig());
        instance.getComponent().getScheduler().getNmClient()
            .startContainerAsync(container,
                launcher.completeContainerLaunch());
      } catch (Exception e) {
        LOG.error(instance.getCompInstanceId()
            + ": Failed to launch container. ", e);

      }
    }
  }
}