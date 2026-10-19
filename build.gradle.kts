plugins {
	java
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.dakshshukla"
version = "0.1.0-SNAPSHOT"
description = "RAG service that finds lookalike past ad campaigns to forecast a new campaign"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:1.1.8")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
	implementation("org.springframework.ai:spring-ai-starter-model-openai")

	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.apache.commons:commons-csv:1.12.0")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	maxHeapSize = "512m"
}

// The eval gate needs the containers and takes a few seconds more, so it has its own task and
// `check` runs both.
tasks.test {
	useJUnitPlatform {
		excludeTags("eval")
	}
}

val eval by tasks.registering(Test::class) {
	group = "verification"
	description = "Run the labelled briefs through the pipeline and fail if a metric drops below eval/baseline.json"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("eval")
	}
	testLogging {
		showStandardStreams = true
	}
	systemProperty("eval.updateBaseline", providers.gradleProperty("evalUpdateBaseline").orElse("false").get())
	outputs.upToDateWhen { false }
	shouldRunAfter(tasks.test)
}

tasks.check {
	dependsOn(eval)
}

// Loads data/sample_campaigns.csv into Postgres and the vector store, then exits.
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("ingest") {
	group = "application"
	description = "Load the sample campaigns into Postgres and pgvector (needs docker compose up)"
	mainClass = "io.github.dakshshukla.lookalike.CampaignLookalikeRagApplication"
	classpath = sourceSets["main"].runtimeClasspath
	args("--lookalike.ingest.path=data/sample_campaigns.csv", "--spring.main.web-application-type=none")
}
