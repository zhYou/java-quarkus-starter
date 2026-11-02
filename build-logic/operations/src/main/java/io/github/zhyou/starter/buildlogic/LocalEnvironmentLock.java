package io.github.zhyou.starter.buildlogic;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/** Serializes changes to this workspace's local environment within one invocation. */
public abstract class LocalEnvironmentLock implements BuildService<BuildServiceParameters.None> {}
