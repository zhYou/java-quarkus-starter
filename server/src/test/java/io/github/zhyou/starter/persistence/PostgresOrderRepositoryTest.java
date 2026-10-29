package io.github.zhyou.starter.tests.persistence;

import io.github.zhyou.starter.testing.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
public class PostgresOrderRepositoryTest extends OrderRepositoryContract {}
