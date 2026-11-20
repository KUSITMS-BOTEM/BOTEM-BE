plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
	id("org.jetbrains.kotlinx.kover") version "0.9.11"
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	implementation("io.sentry:sentry-spring-boot-4-starter:8.63.0")
	implementation("io.sentry:sentry-logback:8.63.0")
	implementation("software.amazon.awssdk:s3:2.57.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("org.flywaydb:flyway-mysql")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testRuntimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	systemProperty("spring.profiles.active", "test")
}

// 실제 MySQL(Testcontainers)이 필요한 마이그레이션 검증은 일반 테스트에서 빼고 별도 작업으로 실행한다.
// CI는 마이그레이션·엔티티·빌드 설정이 바뀐 PR에서만 migrationTest를 돌린다
tasks.test {
	useJUnitPlatform {
		excludeTags("migration", "erd")
	}
}

val migrationTest by tasks.registering(Test::class) {
	description = "Docker의 MySQL에 Flyway 마이그레이션을 적용하고 엔티티 매핑을 검증합니다."
	group = "verification"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("migration")
	}
}

// README의 ERD를 실제 MySQL 스키마로 다시 쓴다. 파일을 고치는 작업이라 결과를 캐시하지 않는다
val generateErd by tasks.registering(Test::class) {
	description = "Docker의 MySQL에 마이그레이션을 적용한 스키마로 README의 ERD를 갱신합니다."
	group = "documentation"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	useJUnitPlatform {
		includeTags("erd")
	}
	outputs.upToDateWhen { false }
}

tasks.jar {
	enabled = false
}

kover {
	currentProject {
		instrumentation {
			// 커버리지 리포트가 Docker가 필요한 migrationTest를 끌어오지 않도록 제외한다
			disabledForTestTasks.addAll("migrationTest", "generateErd")
		}
	}
	reports {
		filters {
			excludes {
				classes("com.botem.BotemApplication*")
				packages("com.botem.global.config", "*.dto", "*.dto.*")
			}
		}
		verify {
			rule {
				minBound(90)
			}
		}
	}
}
