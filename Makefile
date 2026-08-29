.PHONY: build check-all

build:
	./gradlew build

check-all:
	./gradlew check

dependency-updates:
	./gradlew dependencyUpdates \
		-Drevision=release \
		-DoutputFormatter=html \
		--refresh-dependencies && \
		open build/dependencyUpdates/report.html

dependency-updates-ci:
	./gradlew dependencyUpdates --no-parallel -Drevision=release -DoutputFormatter=html --refresh-dependencies

update-gradle:
	./gradlew wrapper --gradle-version latest

test-watch:
	./gradlew -t check

test-coverage:
	./gradlew clean build jacocoTestReport koverHtmlReportJvm
	open tasks-jvm/build/reports/jacoco/test/html/index.html
