package io.github.zhyou.starter.tests.cli;

import io.github.zhyou.starter.testing.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class CliOrdersTest extends CliOrdersContract {}
