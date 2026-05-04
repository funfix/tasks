.PHONY: build

build:
	./gradlew build

dependency-updates:
	./gradlew dependencyUpdates \
		-Drevision=release \
		-DoutputFormatter=html \
		--refresh-dependencies && \
		open build/dependencyUpdates/report.html

dependency-updates-ci:
	@set -e; \
	./gradlew dependencyUpdates --no-parallel -Drevision=release -DoutputFormatter=html --refresh-dependencies; \
	if [ -f build.sbt ]; then \
		mkdir -p ~/.sbt/1.0/plugins; \
		test -f ~/.sbt/1.0/plugins/sbt-updates.sbt || echo 'addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")' > ~/.sbt/1.0/plugins/sbt-updates.sbt; \
		sbt ";dependencyUpdatesReport ;reload plugins; dependencyUpdatesReport"; \
	fi

update-gradle:
	./gradlew wrapper --gradle-version latest

test-watch:
	./gradlew -t check

test-coverage:
	./gradlew clean build jacocoTestReport koverHtmlReportJvm
	open tasks-jvm/build/reports/jacoco/test/html/index.html
