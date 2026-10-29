package io.github.zhyou.starter.tests.cli;

import io.github.zhyou.starter.testing.CitusResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;

@Tag("sharding")
@QuarkusTest
@QuarkusTestResource(value = CitusResource.class, restrictToAnnotatedClass = true)
class CitusCliTest extends CliOrdersContract {}
