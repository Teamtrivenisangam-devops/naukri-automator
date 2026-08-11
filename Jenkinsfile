pipeline {
    agent any

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(
            logRotator(
                numToKeepStr: '15',
                artifactNumToKeepStr: '15'
            )
        )
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

        // =========================================================
        // 1. CHECKOUT
        // =========================================================
        stage('Checkout') {
            steps {
                git(
                    branch: 'main',
                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git',
                    credentialsId: 'github-credentials'
                )
            }
        }

        // =========================================================
        // 2. SKIP CI CHECK
        // =========================================================
        stage('Skip CI Check') {
            steps {
                script {
                    def lastMsg = bat(
                        script: '@git log -1 --pretty=%%B',
                        returnStdout: true
                    ).trim()

                    if (lastMsg.contains('[skip ci]')) {
                        currentBuild.result = 'NOT_BUILT'

                        error(
                            "Skipping build: release/tag commit -> ${lastMsg}"
                        )
                    }
                }
            }
        }

        // =========================================================
        // 3. PREFLIGHT
        // =========================================================
        stage('Preflight') {
            steps {
                bat '''
                    echo Checking Java
                    java -version

                    echo Checking Maven
                    mvn -version

                    echo Checking Node.js
                    node -v

                    echo Checking npm
                    npm -v

                    echo Checking Azure CLI
                    az version
                '''
            }
        }

        // =========================================================
        // 4. DETERMINE VERSION
        // =========================================================
        stage('Determine Base Version') {
            steps {
                script {

                    def tagOutput = bat(
                        script: '@git tag --sort=-v:refname --list "v*"',
                        returnStdout: true
                    ).trim()

                    def latestTag = tagOutput
                        ? tagOutput.readLines()[0].trim()
                        : ''

                    def baseVersion

                    if (latestTag) {

                        baseVersion = latestTag
                            .replaceFirst('^v', '')

                    } else {

                        baseVersion = bat(
                            script:
                                'mvn -f backend\\pom.xml ' +
                                'help:evaluate ' +
                                '-Dexpression=project.version ' +
                                '-q -B -DforceStdout',
                            returnStdout: true
                        )
                        .trim()
                        .readLines()[-1]
                        .trim()
                        .replace('-SNAPSHOT', '')
                    }

                    def parts = baseVersion.tokenize('.')

                    if (parts.size() != 3) {
                        error(
                            "Invalid base version format: " +
                            "${baseVersion} " +
                            "(expected major.minor.patch)"
                        )
                    }

                    env.BASE_VERSION = baseVersion

                    env.RELEASE_VERSION =
                        "${parts[0].toInteger()}." +
                        "${parts[1].toInteger()}." +
                        "${parts[2].toInteger() + 1}"

                    echo "Base version: ${env.BASE_VERSION}"
                    echo "Candidate release: ${env.RELEASE_VERSION}"
                }
            }
        }

        // =========================================================
        // 5. BUILD BACKEND
        // =========================================================
        stage('Build & Verify Backend') {
            steps {

                bat '''
                    mvn -f backend\\pom.xml clean package ^
                    -DskipTests ^
                    -Dmaven.test.skip=true
                '''

                powershell '''
                    $jarPath =
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar"

                    if (-not (Test-Path $jarPath)) {
                        throw "Backend JAR not found: $jarPath"
                    }

                    Write-Host "Backend JAR found:"
                    Write-Host $jarPath
                '''
            }
        }

        // =========================================================
        // 6. BUILD FRONTEND
        // =========================================================
        stage('Build Frontend') {
            steps {

                dir('frontend') {

                    bat '''
                        npm ci
                        npm run build
                    '''
                }
            }
        }

        // =========================================================
        // 7. BUILD ELECTRON
        // =========================================================
        stage('Build & Verify Electron') {
            steps {

                powershell '''
                    $global:LASTEXITCODE = 0

                    & "$env:WORKSPACE\\build\\build.ps1" -Variant Ship

                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }

                    $exeFiles =
                        Get-ChildItem `
                        "$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File

                    if ($exeFiles.Count -eq 0) {
                        throw "EXE not found after Electron build"
                    }

                    Write-Host "Electron EXE files found:"

                    $exeFiles | ForEach-Object {
                        Write-Host $_.FullName
                    }
                '''
            }
        }

        // =========================================================
        // 8. PREPARE RELEASE ARTIFACTS
        // =========================================================
        stage('Prepare Artifacts') {
            steps {

                powershell '''
                    $releaseDir =
                        "$env:WORKSPACE\\release-artifacts"

                    if (Test-Path $releaseDir) {
                        Remove-Item `
                            $releaseDir `
                            -Recurse `
                            -Force
                    }

                    New-Item `
                        -ItemType Directory `
                        -Path $releaseDir `
                        -Force |
                        Out-Null

                    # Backend JAR
                    Copy-Item `
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar" `
                        "$releaseDir\\naukri-be-$env:RELEASE_VERSION.jar"

                    # Electron EXE
                    Get-ChildItem `
                        "$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File |
                        ForEach-Object {

                            $newName =
                                [System.IO.Path]::GetFileNameWithoutExtension(
                                    $_.Name
                                ) +
                                "-$env:RELEASE_VERSION.exe"

                            Copy-Item `
                                $_.FullName `
                                "$releaseDir\\$newName"
                        }

                    # Marker file
                    New-Item `
                        -ItemType File `
                        -Path "$releaseDir\\.marker" `
                        -Force |
                        Out-Null

                    Write-Host "Release artifacts:"
                    Get-ChildItem $releaseDir
                '''
            }
        }

        // =========================================================
        // 9. AZURE LOGIN AND UPLOAD
        // =========================================================
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
                        echo Logging into Azure...

                        az login ^
                            --service-principal ^
                            -u %AZ_CLIENT_ID% ^
                            -p %AZ_CLIENT_SECRET% ^
                            --tenant %AZ_TENANT_ID% ^
                            1>NUL

                        echo Setting Azure subscription...

                        az account set ^
                            --subscription %AZ_SUBSCRIPTION_ID%

                        echo Creating storage container if required...

                        az storage container create ^
                            --name %AZ_CONTAINER% ^
                            --account-name %AZ_STORAGE_ACCOUNT% ^
                            --auth-mode login ^
                            1>NUL

                        echo Uploading release artifacts...

                        az storage blob upload-batch ^
                            --account-name %AZ_STORAGE_ACCOUNT% ^
                            --destination %AZ_CONTAINER%/%RELEASE_VERSION% ^
                            --source release-artifacts ^
                            --auth-mode login

                        echo Azure upload completed.
                    '''
                }
            }
        }

        // =========================================================
        // 10. TAG RELEASE
        // =========================================================
        stage('Tag Release') {
            steps {

                withCredentials([
                    gitUsernamePassword(
                        credentialsId: 'github-credentials',
                        gitToolName: 'Default'
                    )
                ]) {

                    bat '''
                        echo Creating Git release tag...

                        git config user.name "Jenkins CI"
                        git config user.email "jenkins@company.com"

                        git tag -a ^
                            v%RELEASE_VERSION% ^
                            -m "chore: release v%RELEASE_VERSION% [skip ci]"

                        echo Pushing Git tag to GitHub...

                        git push origin v%RELEASE_VERSION%

                        echo Git tag pushed successfully.
                    '''
                }
            }
        }

        // =========================================================
        // 11. ARCHIVE
        // =========================================================
        stage('Archive') {
            steps {

                archiveArtifacts(
                    artifacts: 'release-artifacts/*',
                    fingerprint: true
                )
            }
        }
    }

    // =============================================================
    // POST ACTIONS
    // =============================================================
    post {

        always {

            bat '''
                az logout || exit 0
            '''

            cleanWs()
        }

        success {

            echo """
            ==========================================
            RELEASE SUCCESSFUL
            ==========================================
            Base Version    : ${env.BASE_VERSION}
            Release Version : ${env.RELEASE_VERSION}
            ==========================================
            """
        }

        failure {

            echo """
            ==========================================
            BUILD FAILED
            ==========================================
            Base Version    : ${env.BASE_VERSION}
            Next candidate remains based on this version.
            ==========================================
            """
        }

        unstable {

            echo """
            ==========================================
            BUILD UNSTABLE
            ==========================================
            See:
            ${env.BUILD_URL}console
            ==========================================
            """
        }
    }
}
```
