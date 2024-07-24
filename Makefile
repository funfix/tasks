dependency-updates:
	./gradlew dependencyUpdates \
		-Drevision=release \
		-DoutputFormatter=html \
		--refresh-dependencies && \
		open build/dependencyUpdates/report.html

update-gradle:
	./gradlew wrapper --gradle-version latest

test-watch:
	./gradlew -t -i check

test-coverage:
	./gradlew clean build jacocoTestReport koverHtmlReportJvm
	open tasks-jvm/build/reports/jacoco/test/html/index.html
	open ./tasks-kotlin/build/reports/kover/htmlJvm/index.html
