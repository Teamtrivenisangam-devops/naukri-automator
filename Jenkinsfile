pipeline {
    agent any

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))
        skipDefaultCheckout(false)
    }

    environment {
        JAVA_HOME = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot'
        PATH = "${JAVA_HOME}\\bin;${env.PATH}"

        AZ_STORAGE_ACCOUNT = 'naukristorage7291'
        AZ_CONTAINER = 'smcont'

        NOTIFY_EMAIL = 'teamtrivenisangam2026@gmail.com'

        BASE_VERSION = ''
        RELEASE_VERSION = ''
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git'
            }
        }

        stage('Skip CI Check') {
            steps {
                script {
                    def lastMsg = bat(
                        script: '@git log -1 --pretty=%%B',
                        returnStdout: true
                    ).trim()

                    if (lastMsg.contains('[skip ci]')) {
                        currentBuild.result = 'NOT_BUILT'
                        error("Skipping build: last commit is a release/tag commit -> ${lastMsg}")
                    }
                }
            }
        }

        stage('Preflight') {
            steps {
                bat '''
                    java -version
                    mvn -version
                    node -v
                    npm -v
                    az version
                '''
            }
        }

        stage('Determine Base Version') {
            steps {
                script {
                    // Prefer the latest git tag as source of truth (avoids races / pom drift)
                    def tagOutput = bat(
                        script: '@git tag --sort=-v:refname --list "v*"',
                        returnStdout: true
                    ).trim()

                    def latestTag = tagOutput ? tagOutput.readLines()[0].trim() : ''

                    String baseVersion
                    if (latestTag) {
                        baseVersion = latestTag.replaceFirst('^v', '')
                    } else {
                        // Fall back to pom.xml version on the very first release
                        def pomVersion = bat(
                            script: '''
                                mvn -f backend\\pom.xml help:evaluate ^
                                -Dexpression=project.version ^
                                -q -B ^
                                -DforceStdout
                            ''',
                            returnStdout: true
                        ).trim()
                        baseVersion = pomVersion.readLines()[-1].trim().replace('-SNAPSHOT', '')
                    }

                    def parts = baseVersion.tokenize('.')
                    if (parts.size() != 3) {
                        error "Invalid base version format: ${baseVersion} (expected major.minor.patch)"
                    }

                    env.BASE_VERSION = baseVersion

                    def major = parts[0].toInteger()
                    def minor = parts[1].toInteger()
                    def patch = parts[2].toInteger() + 1

                    env.RELEASE_VERSION = "${major}.${minor}.${patch}"

                    echo "Base version: ${env.BASE_VERSION}  ->  Candidate release: ${env.RELEASE_VERSION}"
                }
            }
        }

        stage('Check Version Not Already Released') {
            steps {
                script {
                    def exists = bat(
                        script: '''
                            @az storage blob exists ^
                            --account-name %AZ_STORAGE_ACCOUNT% ^
                            --container-name %AZ_CONTAINER% ^
                            --name %RELEASE_VERSION%/.marker ^
                            --auth-mode login ^
                            --query exists -o tsv
                        ''',
                        returnStdout: true
                    ).trim()

                    if (exists == 'true') {
                        error "Version ${env.RELEASE_VERSION} already exists in blob storage. Aborting to avoid overwrite."
                    }
                }
            }
        }

        stage('Build Backend') {
            steps {
                bat '''
                    mvn -f backend\\pom.xml clean package -DskipTests -Dmaven.test.skip=true
                '''
            }
        }

        stage('Verify Backend Artifact') {
            steps {
                powershell '''
                    if (-not (Test-Path "$env:WORKSPACE\\backend\\target\\naukri-be.jar")) {
                        throw "Backend JAR not found after build"
                    }
                '''
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    bat 'npm ci'
                    bat 'npm run build'
                }
            }
        }

        stage('Build Electron') {
            steps {
                powershell '''
                    & "$env:WORKSPACE\\build\\build.ps1" -Variant Ship

                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('Verify Electron Artifact') {
            steps {
                powershell '''
                    $exeFiles = Get-ChildItem "$env:WORKSPACE\\dist" -Filter "*.exe" -File

                    if ($exeFiles.Count -eq 0) {
                        throw "EXE not found after Electron build"
                    }
                '''
            }
        }

        stage('Prepare Artifacts') {
            steps {
                powershell '''
                    $releaseDir = "$env:WORKSPACE\\release-artifacts"

                    if (Test-Path $releaseDir) {
                        Remove-Item $releaseDir -Recurse -Force
                    }

                    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

                    Copy-Item `
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar" `
                        "$releaseDir\\naukri-be-$env:RELEASE_VERSION.jar"

                    $exeFiles = Get-ChildItem `
                        "$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File

                    foreach ($exe in $exeFiles) {
                        $newName =
                            [System.IO.Path]::GetFileNameWithoutExtension($exe.Name) +
                            "-$env:RELEASE_VERSION.exe"

                        Copy-Item `
                            $exe.FullName `
                            "$releaseDir\\$newName"
                    }

                    # marker file used by "Check Version Not Already Released" on future builds
                    New-Item -ItemType File -Path "$releaseDir\\.marker" -Force | Out-Null
                '''
            }
        }

        stage('Azure Login') {
            steps {
                withCredentials([
                    azureServicePrincipal(
                        credentialsId: 'azure-sp-naukri',
                        subscriptionIdVariable: 'AZ_SUBSCRIPTION_ID',
                        clientIdVariable: 'AZ_CLIENT_ID',
                        clientSecretVariable: 'AZ_CLIENT_SECRET',
                        tenantIdVariable: 'AZ_TENANT_ID'
                    )
                ]) {
                    bat '''
                        az login --service-principal ^
                        -u %AZ_CLIENT_ID% ^
                        -p %AZ_CLIENT_SECRET% ^
                        --tenant %AZ_TENANT_ID% ^
                        1>NUL

                        az account set --subscription %AZ_SUBSCRIPTION_ID%
                    '''
                }
            }
        }

        stage('Upload to Azure Blob') {
            steps {
                bat '''
                    az storage container create ^
                    --name %AZ_CONTAINER% ^
                    --account-name %AZ_STORAGE_ACCOUNT% ^
                    --auth-mode login ^
                    1>NUL

                    az storage blob upload-batch ^
                    --account-name %AZ_STORAGE_ACCOUNT% ^
                    --destination %AZ_CONTAINER%/%RELEASE_VERSION% ^
                    --source release-artifacts ^
                    --auth-mode login
                '''
                // NOTE: no --overwrite. If this fails because blobs already exist,
                // that's the safety net working as intended — investigate rather than force.
            }
        }

        stage('Tag Release') {
            steps {
                bat '''
                    git config user.name "Jenkins CI"
                    git config user.email "jenkins@company.com"

                    git tag -a v%RELEASE_VERSION% -m "chore: release v%RELEASE_VERSION% [skip ci]"
                    git push origin v%RELEASE_VERSION%
                '''
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts(
                    artifacts: 'release-artifacts/*',
                    fingerprint: true
                )
            }
        }
    }

    post {
        always {
            bat 'az logout || exit 0'
            cleanWs()
        }
        success {
            echo "Released version ${env.RELEASE_VERSION} (base was ${env.BASE_VERSION})"
            emailext(
                to: "${env.NOTIFY_EMAIL}",
                subject: "SUCCESS: naukri-automator release v${env.RELEASE_VERSION} (build #${env.BUILD_NUMBER})",
                body: """\
Build succeeded and was released.

Job:             ${env.JOB_NAME}
Build number:    ${env.BUILD_NUMBER}
Base version:    ${env.BASE_VERSION}
Released version: ${env.RELEASE_VERSION}
Blob path:       ${env.AZ_CONTAINER}/${env.RELEASE_VERSION}
Build URL:       ${env.BUILD_URL}
""",
                mimeType: 'text/plain'
            )
        }
        failure {
            echo "Build failed before reaching a release. Base version ${env.BASE_VERSION} remains the next candidate."
            emailext(
                to: "${env.NOTIFY_EMAIL}",
                subject: "FAILED: naukri-automator build #${env.BUILD_NUMBER}",
                body: """\
Build failed.

Job:             ${env.JOB_NAME}
Build number:    ${env.BUILD_NUMBER}
Base version:    ${env.BASE_VERSION}
Candidate version was: ${env.RELEASE_VERSION}
Build URL:       ${env.BUILD_URL}
Console log:     ${env.BUILD_URL}console
""",
                mimeType: 'text/plain'
            )
        }
        unstable {
            emailext(
                to: "${env.NOTIFY_EMAIL}",
                subject: "UNSTABLE: naukri-automator build #${env.BUILD_NUMBER}",
                body: "Build marked unstable. See ${env.BUILD_URL}console for details.",
                mimeType: 'text/plain'
            )
        }
    }
}
