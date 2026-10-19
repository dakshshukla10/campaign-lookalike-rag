package io.github.dakshshukla.lookalike.support;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Full application context against the shared pgvector and Redis containers. */
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestContainersConfig.class)
public @interface IntegrationTest {
}
