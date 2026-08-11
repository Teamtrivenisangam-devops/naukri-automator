pipeline {
    agent any
    triggers { githubPush() }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '15'))
    }

    environment {
        JAVA_HOME = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot'
        PATH = "${JAVA_HOME}\\bin;${env.PATH}"
        AZ_STORAGE_ACCOUNT = 'naukristorage7291'
        AZ_CONTAINER = 'smcont'
        BASE_VERSION = ''
        RELEASE_VERSION = ''
    }

    stages {

       stage('Checkout') {
    steps {
        git(
            branch: 'main',
            url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git',
            credentialsId: 'github-credentials'
        )
    }
}

        stage('Skip CI Check') {
            steps {
                script {
                    def lastMsg = bat(script: '@git log -1 --pretty=%%B', returnStdout: true).trim()
                    if (lastMsg.contains('[skip ci]')) {
                        currentBuild.result = 'NOT_BUILT'
                        error("Skipping build: release/tag commit -> ${lastMsg}")
                    }
                }
            }
        }

        stage('Preflight') {
            steps {
                bat 'java -version && mvn -version && node -v && npm -v && az version'
            }
        }

        stage('Determine Base Version') {
            steps {
                script {
                    def tagOutput = bat(script: '@git tag --sort=-v:refname --list "v*"', returnStdout: true).trim()
                    def latestTag = tagOutput ? tagOutput.readLines()[0].trim() : ''

                    def baseVersion = latestTag
                        ? latestTag.replaceFirst('^v', '')
                        : bat(
                            script: 'mvn -f backend\\pom.xml help:evaluate -Dexpression=project.version -q -B -DforceStdout',
                            returnStdout: true
                          ).trim().readLines()[-1].trim().replace('-SNAPSHOT', '')

                    def parts = baseVersion.tokenize('.')
                    if (parts.size() != 3) error "Invalid base version format: ${baseVersion} (expected major.minor.patch)"

                    env.BASE_VERSION = baseVersion
                    env.RELEASE_VERSION = "${parts[0].toInteger()}.${parts[1].toInteger()}.${parts[2].toInteger() + 1}"
                    echo "Base version: ${env.BASE_VERSION} -> Candidate release: ${env.RELEASE_VERSION}"
                }
            }
        }

        stage('Build & Verify Backend') {
            steps {
                bat 'mvn -f backend\\pom.xml clean package -DskipTests -Dmaven.test.skip=true'
                powershell 'if (-not (Test-Path "$env:WORKSPACE\\backend\\target\\naukri-be.jar")) { throw "Backend JAR not found" }'
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    bat 'npm ci && npm run build'
                }
            }
        }

        stage('Build & Verify Electron') {
            steps {
                powershell '''
                    $global:LASTEXITCODE = 0
                    & "$env:WORKSPACE\\build\\build.ps1" -Variant Ship
                    if ($LASTEXITCODE) { exit $LASTEXITCODE }

                    $exeFiles = Get-ChildItem "$env:WORKSPACE\\dist" -Filter "*.exe" -File
                    if ($exeFiles.Count -eq 0) { throw "EXE not found after Electron build" }
                '''
            }
        }

        stage('Prepare Artifacts') {
            steps {
                powershell '''
                    $releaseDir = "$env:WORKSPACE\\release-artifacts"
                    if (Test-Path $releaseDir) { Remove-Item $releaseDir -Recurse -Force }
                    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

                    Copy-Item "$env:WORKSPACE\\backend\\target\\naukri-be.jar" "$releaseDir\\naukri-be-$env:RELEASE_VERSION.jar"

                    Get-ChildItem "$env:WORKSPACE\\dist" -Filter "*.exe" -File | ForEach-Object {
                        $newName = [System.IO.Path]::GetFileNameWithoutExtension($_.Name) + "-$env:RELEASE_VERSION.exe"
                        Copy-Item $_.FullName "$releaseDir\\$newName"
                    }

                    New-Item -ItemType File -Path "$releaseDir\\.marker" -Force | Out-Null
                '''
            }
        }

        stage('Azure Login & Upload') {
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
                        az login --service-principal -u %AZ_CLIENT_ID% -p %AZ_CLIENT_SECRET% --tenant %AZ_TENANT_ID% 1>NUL
                        az account set --subscription %AZ_SUBSCRIPTION_ID%

                        az storage container create --name %AZ_CONTAINER% --account-name %AZ_STORAGE_ACCOUNT% --auth-mode login 1>NUL

                        az storage blob upload-batch ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --destination %AZ_CONTAINER%/%RELEASE_VERSION% ^
                        --source release-artifacts ^
                        --auth-mode login
                    '''
                }
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
                archiveArtifacts(artifacts: 'release-artifacts/*', fingerprint: true)
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
        }
        failure {
            echo "Build failed before release. Base version ${env.BASE_VERSION} remains next candidate."
        }
        unstable {
            echo "Build marked unstable. See ${env.BUILD_URL}console for details."
        }
    }
}
