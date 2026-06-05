pipeline {

    agent any

    // ── Tool names must match exactly what you set in
    //    Jenkins → Manage Jenkins → Global Tool Configuration
    tools {
        jdk   'jdk17'
        maven 'maven3'
    }

    environment {
        // ── DockerHub ──────────────────────────────────────────────
        // Add credentials in Jenkins → Manage Credentials
        // Kind: "Username with password", ID: "dockerhub-creds"
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        DOCKERHUB_USERNAME    = 'reddy177'           // ← change to your DockerHub username
        IMAGE_NAME            = "${DOCKERHUB_USERNAME}/annadaan"
        IMAGE_TAG             = "${BUILD_NUMBER}"

        // ── SonarQube ──────────────────────────────────────────────
        // Add in Jenkins → Manage Jenkins → Configure System → SonarQube servers
        // Name it "SonarQube-Server"
        SONAR_PROJECT_KEY = 'annadaan-freefood'

        // ── App ────────────────────────────────────────────────────
        APP_NAME          = 'annadaan-app'
        CONTAINER_PORT    = '8080'
        HOST_PORT         = '8080'
        WAR_NAME          = 'freefood-app.war'
    }

    options {
        // Keep last 5 builds only
        buildDiscarder(logRotator(numToKeepStr: '5'))
        // Fail if pipeline hangs for more than 30 minutes
        timeout(time: 30, unit: 'MINUTES')
        // Don't run the same pipeline concurrently
        disableConcurrentBuilds()
        // Add timestamps to console output
        timestamps()
    }

    stages {

        // ── 1. CHECKOUT ───────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo '📥 Checking out source code...'
                checkout scm
                sh 'echo "Branch: $GIT_BRANCH  Commit: $GIT_COMMIT"'
            }
        }

        // ── 2. COMPILE ────────────────────────────────────────────
        stage('Compile') {
            steps {
                echo '🔨 Compiling Java source...'
                sh 'mvn clean compile -q'
            }
        }

        // ── 3. UNIT TESTS ─────────────────────────────────────────
        stage('Test') {
            steps {
                echo '🧪 Running unit tests...'
                sh 'mvn test'
            }
            post {
                always {
                    // Publish JUnit test results
                    junit testResults: '**/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── 4. CODE COVERAGE (JaCoCo) ─────────────────────────────
        stage('Code Coverage') {
            steps {
                echo '📊 Generating JaCoCo coverage report...'
                sh 'mvn jacoco:report'
            }
            post {
                always {
                    jacoco(
                        execPattern:    '**/target/jacoco.exec',
                        classPattern:   '**/target/classes',
                        sourcePattern:  '**/src/main/java',
                        exclusionPattern: '**/model/**,**/util/DistanceUtil*'
                    )
                }
            }
        }

        // ── 5. SONARQUBE ANALYSIS ─────────────────────────────────
        stage('SonarQube Analysis') {
            steps {
                echo '🔍 Running SonarQube static analysis...'
                withSonarQubeEnv('SonarQube-Server') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                            -Dsonar.projectName='AnnaDaan Free Food India' \
                            -Dsonar.projectVersion=${BUILD_NUMBER} \
                            -Dsonar.java.coveragePlugin=jacoco \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    """
                }
            }
        }

        // ── 6. SONARQUBE QUALITY GATE ─────────────────────────────
        stage('Quality Gate') {
            steps {
                echo '🚦 Waiting for SonarQube Quality Gate...'
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── 7. OWASP DEPENDENCY CHECK ─────────────────────────────
        stage('OWASP Dependency Check') {
            steps {
                echo '🛡️  Scanning dependencies for CVEs...'
                dependencyCheck(
                    additionalArguments: """
                        --scan ./
                        --format XML
                        --format HTML
                        --out target/dependency-check-report
                        --project "${APP_NAME}"
                    """,
                    odcInstallation: 'DC'   // must match Global Tool Config name
                )
            }
            post {
                always {
                    dependencyCheckPublisher(
                        pattern: 'target/dependency-check-report/dependency-check-report.xml'
                    )
                }
            }
        }

        // ── 8. PACKAGE (WAR) ──────────────────────────────────────
        stage('Package') {
            steps {
                echo '📦 Building WAR file...'
                sh 'mvn package -DskipTests -q'
                sh "ls -lh target/${WAR_NAME}"
            }
            post {
                success {
                    archiveArtifacts artifacts: "target/${WAR_NAME}",
                                     fingerprint: true,
                                     allowEmptyArchive: false
                }
            }
        }

        // ── 9. DOCKER BUILD ───────────────────────────────────────
        stage('Docker Build') {
            steps {
                echo "🐳 Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}"
                sh """
                    docker build \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        -t ${IMAGE_NAME}:latest \
                        --label "build.number=${BUILD_NUMBER}" \
                        --label "git.commit=${GIT_COMMIT}" \
                        --label "build.date=\$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
                        .
                """
            }
        }

        // ── 10. DOCKER PUSH ───────────────────────────────────────
        stage('Docker Push') {
            steps {
                echo "⬆️  Pushing to DockerHub: ${IMAGE_NAME}"
                sh """
                    echo "${DOCKERHUB_CREDENTIALS_PSW}" | \
                        docker login -u "${DOCKERHUB_CREDENTIALS_USR}" --password-stdin

                    docker push ${IMAGE_NAME}:${IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                """
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        // ── 11. DEPLOY CONTAINER ──────────────────────────────────
        stage('Deploy') {
            steps {
                echo "🚀 Deploying ${IMAGE_NAME}:latest on port ${HOST_PORT}..."
                sh """
                    # Stop and remove old container if running
                    docker stop ${APP_NAME} || true
                    docker rm   ${APP_NAME} || true

                    # Pull latest and run
                    docker run -d \
                        --name  ${APP_NAME} \
                        -p ${HOST_PORT}:${CONTAINER_PORT} \
                        -v annadaan-data:/data/freefood \
                        -e JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8" \
                        --restart unless-stopped \
                        ${IMAGE_NAME}:latest

                    echo "✅ Container started. Waiting for health check..."
                    sleep 20

                    # Verify the container is still running
                    docker ps | grep ${APP_NAME}
                """
            }
        }

        // ── 12. SMOKE TEST ────────────────────────────────────────
        stage('Smoke Test') {
            steps {
                echo '💨 Running smoke test against deployed container...'
                sh """
                    # Retry up to 5 times with 10s gap (Tomcat takes ~15-20s to start)
                    for i in 1 2 3 4 5; do
                        HTTP_CODE=\$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${HOST_PORT}/ || echo "000")
                        echo "Attempt \$i — HTTP \$HTTP_CODE"
                        if [ "\$HTTP_CODE" = "200" ]; then
                            echo "✅ Smoke test passed!"
                            exit 0
                        fi
                        sleep 10
                    done
                    echo "❌ Smoke test failed after 5 attempts"
                    exit 1
                """
            }
        }

        // ── 13. CLEANUP OLD IMAGES ────────────────────────────────
        stage('Cleanup') {
            steps {
                echo '🧹 Removing dangling Docker images...'
                sh 'docker image prune -f || true'
                // Remove all but the last 3 tagged images for this app
                sh """
                    docker images ${IMAGE_NAME} --format '{{.Tag}}' | \
                        grep -v latest | sort -rn | tail -n +4 | \
                        xargs -I{} docker rmi ${IMAGE_NAME}:{} || true
                """
            }
        }

    } // end stages

    // ── POST ACTIONS ──────────────────────────────────────────────
    post {

        success {
            echo """
            ╔══════════════════════════════════════╗
            ║  ✅  Build #${BUILD_NUMBER} SUCCESS   ║
            ║  App: http://\$(hostname):${HOST_PORT} ║
            ╚══════════════════════════════════════╝
            """
        }

        failure {
            echo """
            ╔══════════════════════════════════════╗
            ║  ❌  Build #${BUILD_NUMBER} FAILED    ║
            ║  Check console output above           ║
            ╚══════════════════════════════════════╝
            """
            // Stop the broken container so the old one can be restarted manually
            sh "docker stop ${APP_NAME} || true"
        }

        always {
            echo '📋 Collecting build artifacts...'
            // Clean workspace to free disk space
            cleanWs(
                cleanWhenSuccess:  true,
                cleanWhenFailure:  false,   // keep on failure for debugging
                cleanWhenAborted:  true
            )
        }

    }

} // end pipeline
