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
	./gradlew clean jvmTest koverHtmlReportJvm
	open ./tasks-core/build/reports/kover/htmlJvm/index.html
	open ./tasks-kotlin/build/reports/kover/htmlJvm/index.html
