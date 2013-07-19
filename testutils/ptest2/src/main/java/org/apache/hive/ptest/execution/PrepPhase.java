/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hive.ptest.execution;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class PrepPhase extends Phase {
  private final File mScratchDir;
  private final File mPatchFile;

  public PrepPhase(ImmutableList<HostExecutor> hostExecutors,
      LocalCommandFactory localCommandFactory,
      ImmutableMap<String, String> templateDefaults,
      File scratchDir, File patchFile, Logger logger) {
    super(hostExecutors, localCommandFactory, templateDefaults, logger);
    this.mScratchDir = scratchDir;
    this.mPatchFile = patchFile;
  }
  @Override
public void execute() throws Exception {
    long prepStart = System.currentTimeMillis();
    execLocally("mkdir -p $workingDir/scratch");
    execInstances("mkdir -p $localDir/$instanceName/logs $localDir/$instanceName/maven $localDir/$instanceName/scratch");
    execInstances("mkdir -p $localDir/$instanceName/ivy $localDir/$instanceName/${repositoryName}-source");
    if(mPatchFile != null) {
      File smartApplyPatch = new File(mScratchDir, "smart-apply-patch.sh");
      PrintWriter writer = new PrintWriter(smartApplyPatch);
      try {
        writer.write(Templates.readResource("smart-apply-patch.sh"));
        if(writer.checkError()) {
          throw new IOException("Error writing to " + smartApplyPatch);
        }
      } finally {
        writer.close();
      }
      execLocally("cp -f " + mPatchFile.getPath() + " $workingDir/scratch/build.patch");
    }
    long start;
    long elapsedTime;
      // source prep
    start = System.currentTimeMillis();
    File sourcePrepScript = new File(mScratchDir, "source-prep.sh");
    Templates.writeTemplateResult("source-prep.vm", sourcePrepScript, getTemplateDefaults());
    execLocally("bash " + sourcePrepScript.getPath());
    logger.debug("Deleting " + sourcePrepScript + ": " + sourcePrepScript.delete());
    elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
        TimeUnit.MILLISECONDS);
    logger.info("PERF: source prep took " + elapsedTime + " minutes");
    // rsync source
    start = System.currentTimeMillis();
    rsyncFromLocalToRemoteInstances("$workingDir/${repositoryName}-source", "$localDir/$instanceName/");
    elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
        TimeUnit.MILLISECONDS);
    logger.info("PERF: rsync source took " + elapsedTime + " minutes");
    start = System.currentTimeMillis();
    rsyncFromLocalToRemoteInstances("$workingDir/maven", "$localDir/$instanceName/");
    elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
        TimeUnit.MILLISECONDS);
    logger.info("PERF: rsync maven took " + elapsedTime + " minutes");
    start = System.currentTimeMillis();
    rsyncFromLocalToRemoteInstances("$workingDir/ivy", "$localDir/$instanceName/");
    elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
        TimeUnit.MILLISECONDS);
    logger.info("PERF: rsync ivy took " + elapsedTime + " minutes");
    elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - prepStart),
        TimeUnit.MILLISECONDS);
    logger.info("PERF: prep phase took " + elapsedTime + " minutes");
  }
}